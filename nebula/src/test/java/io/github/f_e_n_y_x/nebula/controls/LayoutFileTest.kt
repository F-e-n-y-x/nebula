package io.github.f_e_n_y_x.nebula.controls

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.zip.Deflater

class LayoutFileTest {
    private val presets = listOf(DefaultProfiles.gtaTouchControls(), DefaultProfiles.touchShooterPad(), DefaultProfiles.touchShooterKbm(), DefaultProfiles.gtaTouchGamepad(), DefaultProfiles.standard())

    private fun rejects(text: String, contains: String) {
        try {
            LayoutFile.parse(text)
            fail("accepted: ${text.take(200)}")
        } catch (e: ControlsFormatException) {
            assertTrue("message \"${e.message}\" should mention \"$contains\"", e.message.orEmpty().contains(contains))
        }
    }

    private fun good(): JSONObject = JSONObject(LayoutFile.encode(DefaultProfiles.touchShooterPad()))
    private fun JSONObject.el(i: Int = 1): JSONObject = getJSONArray("landscape").getJSONObject(i)

    // ---------------------------------------------------------------- round trips

    @Test
    fun `every preset survives export and import`() {
        for (p in presets) {
            val back = LayoutFile.parse(LayoutFile.encode(p), "p-x", 5).profile
            assertEquals(p.name, p.landscape, back.landscape)
            assertEquals(p.name, p.portrait, back.portrait)
            assertEquals(p.outside, back.outside)
            assertEquals(p.look, back.look)
            assertEquals("library", back.origin)
            assertEquals("p-x", back.id)
        }
    }

    @Test
    fun `metadata round trips`() {
        val meta = LayoutFile.parse(LayoutFile.encode(DefaultProfiles.gtaTouchControls())).meta
        assertEquals("GTA V · touch controls", meta.name)
        assertEquals(GameRef("Grand Theft Auto V", 271590), meta.game)
        assertEquals(LayoutTarget.XINPUT, meta.target)
        assertEquals(DeviceClass.PHONE, meta.device)
        assertEquals(2.17f, meta.aspect!!, 0.001f)
        assertEquals(listOf("action-adventure", "third-person", "shooter", "controller", "touch-only"), meta.tags)
    }

    @Test
    fun `share codes round trip and small layouts fit a QR code`() {
        for (p in presets) {
            val code = LayoutFile.shareCode(p)
            assertTrue(code.startsWith(LayoutFile.CODE_PREFIX))
            assertEquals(p.landscape, LayoutFile.parse(code).profile.landscape)
        }
        assertTrue("the touch-shooter preset fits one QR code", LayoutFile.fitsQr(LayoutFile.shareCode(DefaultProfiles.touchShooterPad())))
    }

    @Test
    fun `files are compact`() {
        val text = LayoutFile.encode(DefaultProfiles.touchShooterPad(), pretty = false)
        assertFalse("defaults are left out", text.contains("\"keepWithController\":false"))
        assertTrue(text.length < 6000)
    }

    @Test
    fun `the store keeps the new fields`() {
        val p = DefaultProfiles.gtaTouchControls().copy(id = "p-1")
        val back = ProfileJson.decodeStore(ProfileJson.encodeStore(ProfileJson.StoreData(listOf(p)))).profiles.single()
        assertEquals(p, back)
    }

    // ---------------------------------------------------------------- strict validation

    @Test fun `rejects what isn't a layout`() {
        rejects("hello", "isn't a Nebula layout")
        rejects("{\"format\":", "not valid JSON")
        rejects("[1,2]", "not a JSON object")
        rejects("{\"format\":\"nebula.controls.profile\"}", "format")
        rejects(good().put("version", 3).toString(), "newer Nebula")
        rejects(good().put("version", "1").toString(), "no version")
        rejects(good().apply { remove("meta") }.toString(), "meta")
        rejects(good().put("landscape", JSONArray()).toString(), "no controls")
    }

    @Test fun `rejects unknown fields instead of ignoring them`() {
        rejects(good().put("onLoad", "alert(1)").toString(), "unknown field")
        rejects(good().apply { el().put("script", "rm -rf /") }.toString(), "unknown field")
        rejects(good().apply { getJSONObject("meta").put("x", 1) }.toString(), "meta: unknown field")
    }

    @Test fun `rejects out of range and unknown values`() {
        rejects(good().apply { el().put("x", 1.5) }.toString(), "landscape[1].x")
        rejects(good().apply { el().put("y", -0.1) }.toString(), "landscape[1].y")
        rejects(good().apply { el().put("w", 5000) }.toString(), "landscape[1].w")
        rejects(good().apply { el().put("x", "0.5") }.toString(), "must be a number")
        rejects(good().apply { el().put("kind", "webview") }.toString(), "unknown control")
        rejects(good().apply { el().put("bindings", JSONArray().put("pad:999999")) }.toString(), "unknown binding")
        rejects(good().apply { el().put("bindings", JSONArray().put("exec:calc.exe")) }.toString(), "unknown binding")
        rejects(good().apply { el().put("mode", "turbo") }.toString(), "unknown value")
        rejects(good().apply { el().put("lookThrough", "yes") }.toString(), "true or false")
        rejects(good().apply { el().put("tint", "red") }.toString(), "#AARRGGBB")
        rejects(good().apply { el().put("id", "../../etc") }.toString(), "id")
        rejects(good().apply { getJSONObject("settings").put("outside", "keylogger") }.toString(), "settings.outside")
        rejects(good().apply { getJSONObject("meta").put("game", JSONObject().put("name", "X").put("steamAppId", -5)) }.toString(), "steamAppId")
    }

    @Test fun `rejects duplicates, floods and control characters`() {
        rejects(good().apply { el(2).put("id", el(1).getString("id")) }.toString(), "used twice")
        val many = JSONArray().apply { repeat(LayoutFile.MAX_ELEMENTS + 1) { i -> put(JSONObject().put("id", "b$i").put("kind", "button").put("x", 0.5).put("y", 0.5).put("w", 50).put("h", 50)) } }
        rejects(good().put("landscape", many).toString(), "at most ${LayoutFile.MAX_ELEMENTS}")
        rejects(good().apply { el().put("label", "a\u0000b") }.toString(), "control characters")
        rejects(good().apply { el().put("label", "x".repeat(100)) }.toString(), "at most")
        rejects(good().apply { getJSONObject("meta").put("name", "Evil‮exe") }.toString(), "control characters")
        val macro = JSONObject().put("id", "m").put("kind", "macro").put("x", 0.5).put("y", 0.5).put("w", 50).put("h", 50)
            .put("steps", JSONArray().apply { repeat(10) { put(JSONObject().put("binding", "pad:4096").put("holdMs", 5000)) } })
        rejects(good().put("landscape", JSONArray().put(macro)).toString(), "20 seconds")
    }

    @Test fun `rejects files and share codes that are too large`() {
        rejects("{\"format\":\"nebula-layout\",\"pad\":\"" + "x".repeat(LayoutFile.MAX_BYTES) + "\"}", "larger than")
        // A zip bomb: 10 MB of spaces deflates to a few KB.
        val d = Deflater(9); d.setInput(ByteArray(10 * 1024 * 1024) { ' '.code.toByte() }); d.finish()
        val out = ByteArrayOutputStream(); val buf = ByteArray(65536)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        val bomb = LayoutFile.CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        rejects(bomb, "larger than")
        rejects(LayoutFile.CODE_PREFIX + "!!!not base64", "damaged")
    }

    @Test fun `import entry point takes layouts, share codes and old profiles`() {
        val fallback = CrownImport.Basis(2400, 1080, 2.75f)
        val fromFile = ProfileImport.parse(LayoutFile.encode(DefaultProfiles.touchShooterPad()), fallback, "p-1", 0)
        assertEquals(ProfileImport.Source.LAYOUT, fromFile.source)
        assertEquals(LayoutTarget.XINPUT, fromFile.meta?.target)
        val fromCode = ProfileImport.parse(LayoutFile.shareCode(DefaultProfiles.touchShooterKbm()), fallback, "p-2", 0)
        assertEquals(ProfileImport.Source.LAYOUT, fromCode.source)
        val old = ProfileImport.parse(ProfileJson.exportProfile(DefaultProfiles.gtaTouchGamepad()), fallback, "p-3", 0)
        assertEquals(ProfileImport.Source.NEBULA, old.source)
    }

    // ---------------------------------------------------------------- the library index

    private fun index(vararg entries: JSONObject) = JSONObject().put("format", LayoutIndex.FORMAT).put("version", 1).put("updated", "2026-09-29")
        .put("layouts", JSONArray().apply { entries.forEach { put(it) } }).toString()

    private fun entry(id: String, game: String?, path: String = "layouts/genre-shooter/$id.json", tags: List<String> = emptyList()) = JSONObject()
        .put("id", id).put("name", "Layout $id").put("author", "me").put("target", "xinput").put("device", "phone")
        .apply { if (game != null) put("game", JSONObject().put("name", game)) }
        .apply { if (tags.isNotEmpty()) put("tags", JSONArray(tags)) }
        .put("path", path).put("size", 1200).put("sha256", "a".repeat(64)).put("controls", 2)
        .put("preview", JSONArray().put(JSONArray(listOf("zone", 0.2, 0.6, 0.4, 0.7))).put(JSONArray(listOf("button", 0.8, 0.5, 80, 80))))

    @Test fun `index parses, groups by genre and game, filters and searches`() {
        val idx = LayoutIndex.parse(
            index(
                entry("a", "GTA V", "layouts/gta-v/a.json", listOf("action-adventure", "third-person", "shooter")),
                entry("b", null, "layouts/genre-shooter/b.json", listOf("shooter", "controller")),
                entry("c", "Apex Legends", "layouts/apex-legends/c.json", listOf("shooter", "first-person")),
                entry("d", "GTA V", "layouts/gta-v/d.json", listOf("action-adventure")),
                entry("e", null, "layouts/genre-racing/e.json", listOf("controller")),
            ),
        )
        assertEquals(5, idx.entries.size)
        assertEquals(listOf("shooter", "racing", "action-adventure"), idx.genres())
        // Templates first (by genre), then games by name.
        assertEquals(listOf("Shooter templates", "Racing templates", "Apex Legends", "GTA V"), idx.browse().map { it.title })
        assertEquals("For any shooter", idx.browse().first().subtitle)
        assertEquals("Shooter · Action-adventure", idx.browse().last().subtitle)
        assertEquals(listOf("Shooter templates", "Apex Legends", "GTA V"), idx.browse(genre = "shooter").map { it.title })
        assertEquals(listOf("a"), idx.browse(genre = "shooter").last().entries.map { it.id })
        assertEquals(listOf("Racing templates"), idx.browse(genre = "racing").map { it.title })
        assertEquals(2, idx.browse("gta").single().entries.size)
        assertEquals(listOf("Racing templates"), idx.browse("racing").map { it.title })
        assertTrue(idx.browse("gta", genre = "racing").isEmpty())
        assertEquals(2, idx.entries.first().preview.size)
        assertEquals(ElementKind.ZONE, idx.entries.first().preview.first().kind)
        assertEquals("https://example.com/lib/layouts/gta-v/x.json", LayoutIndex.resolve("https://example.com/lib/index.json", "layouts/gta-v/x.json"))
    }

    @Test fun `bad index entries are skipped, a bad index fails`() {
        val idx = LayoutIndex.parse(
            index(
                entry("ok", null),
                entry("up", null, path = "layouts/../../secret.json"),
                entry("abs", null, path = "https://evil.example/x.json"),
                entry("ok", null), // duplicate id
                JSONObject().put("id", "nometa").put("path", "layouts/a/b.json").put("size", 10),
            ),
        )
        assertEquals(listOf("ok"), idx.entries.map { it.id })
        assertEquals(4, idx.skipped)
        try { LayoutIndex.parse("{\"format\":\"other\"}"); fail() } catch (e: ControlsFormatException) { }
        try { LayoutIndex.parse("nope"); fail() } catch (e: ControlsFormatException) { }
    }

    @Test fun `the sample library index parses`() {
        val f = java.io.File("/DATA/blue/Projects/Nova-Nebula/designs/nebula-layouts/index.json")
        if (!f.isFile) return // only on the owner's machine
        val idx = LayoutIndex.parse(f.readText())
        assertTrue(idx.entries.size >= 3)
        assertEquals(0, idx.skipped)
        for (e in idx.entries) {
            val file = java.io.File(f.parentFile, e.path)
            val bytes = file.readBytes()
            assertEquals(e.path, e.sha256, LayoutIndex.sha256(bytes))
            LayoutFile.parse(String(bytes))
        }
        assertTrue("shooter" in idx.genres())
        assertEquals("Shooter templates", idx.browse().first().title)
        assertTrue(idx.browse().any { it.title == "Grand Theft Auto V" })
    }

    @Test fun `shared layouts go where the library keeps them`() {
        assertEquals("layouts/genre-shooter/touch-shooter-controller.json", LibraryPaths.pathFor(DefaultProfiles.touchShooterPad().meta!!))
        assertEquals("layouts/genre-shooter/touch-shooter-keyboard-mouse.json", LibraryPaths.pathFor(DefaultProfiles.touchShooterKbm().meta!!))
        assertEquals("layouts/gta-v/touch-controls.json", LibraryPaths.pathFor(DefaultProfiles.gtaTouchControls().meta!!))
        assertEquals("layouts/elden-ring/controller-touch-camera.json", LibraryPaths.pathFor(LayoutMeta("Elden Ring · controller + touch camera", game = GameRef("Elden Ring"))))
        assertEquals("layouts/forza-horizon-5/my-wheel.json", LibraryPaths.pathFor(LayoutMeta("My wheel", game = GameRef("Forza Horizon 5"))))
        assertEquals("layouts/genre-racing/tilt-steering.json", LibraryPaths.pathFor(LayoutMeta("Tilt steering", tags = listOf("gyro", "racing"))))
        assertEquals("layouts/genre-other/my-layout.json", LibraryPaths.pathFor(LayoutMeta("My layout")))
        // Every path passes the index's own check.
        for (m in listOf(DefaultProfiles.touchShooterPad().meta!!, LayoutMeta("x".repeat(40), game = GameRef("y".repeat(60))))) {
            val one = index(entry("p", null, LibraryPaths.pathFor(m)))
            assertEquals(LibraryPaths.pathFor(m), 1, LayoutIndex.parse(one).entries.size)
        }
    }

    // ---------------------------------------------------------------- links, URL policy, downloads

    @Test fun `layout links`() {
        assertEquals("https://x.example/a.json", LayoutLink.urlOf("nebula://layout?url=https%3A%2F%2Fx.example%2Fa.json"))
        assertEquals("https://x.example/a.json", LayoutLink.urlOf(LayoutLink.of("https://x.example/a.json")))
        assertNull(LayoutLink.urlOf("nebula://pair?url=https://x"))
        assertNull(LayoutLink.urlOf("https://layout?url=https://x"))
        assertNull(LayoutLink.urlOf("nebula://layout"))
    }

    @Test fun `https only, LAN http only in debug builds`() {
        val release = UrlPolicy(allowLanHttp = false)
        val debug = UrlPolicy(allowLanHttp = true)
        release.check("https://raw.githubusercontent.com/F-e-n-y-x/nebula-layouts/main/index.json")
        for (bad in listOf("http://192.168.10.10:8765/x", "file:///sdcard/x.json", "content://x/y", "javascript:alert(1)", "https://user:pw@host/x", "ftp://x/y")) {
            try { release.check(bad); fail(bad) } catch (e: ControlsFormatException) { }
        }
        debug.check(LibrarySource.LAN_SAMPLE)
        try { debug.check("http://example.com/x"); fail() } catch (e: ControlsFormatException) { }
        assertTrue(UrlPolicy.isPrivateHost("172.20.1.1"))
        assertFalse(UrlPolicy.isPrivateHost("172.32.1.1"))
        assertFalse(UrlPolicy.isPrivateHost("192.168.1.1.evil.com"))
    }

    private val server = MockWebServer()
    @After fun stop() { server.close() }

    @Test fun `the index is cached with its ETag`() {
        server.start()
        val body = index(entry("a", null))
        server.enqueue(MockResponse.Builder().code(200).body(body).addHeader("ETag", "\"v1\"").build())
        server.enqueue(MockResponse.Builder().code(304).build())
        val dir = Files.createTempDirectory("layouts").toFile()
        val f = LayoutFetcher(UrlPolicy(allowLanHttp = true), dir)
        val url = server.url("/index.json").toString()
        val first = f.fetch(url, LayoutIndex.MAX_BYTES, cache = true)
        assertFalse(first.fromCache)
        assertNull(server.takeRequest().headers["If-None-Match"])
        val second = f.fetch(url, LayoutIndex.MAX_BYTES, cache = true)
        assertTrue(second.fromCache)
        assertEquals(body, second.body)
        assertEquals("\"v1\"", server.takeRequest().headers["If-None-Match"])
        // Offline: the cached copy, marked stale.
        server.close()
        val third = f.fetch(url, LayoutIndex.MAX_BYTES, cache = true)
        assertTrue(third.stale)
        dir.deleteRecursively()
    }

    @Test fun `downloads are size capped, hash checked and redirects re-checked`() {
        server.start()
        server.enqueue(MockResponse.Builder().code(200).body("x".repeat(5000)).build())
        val f = LayoutFetcher(UrlPolicy(allowLanHttp = true), null)
        try { f.fetch(server.url("/big").toString(), 1000); fail() } catch (e: ControlsFormatException) { assertTrue(e.message!!.contains("larger")) }
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "http://example.com/evil.json").build())
        try { f.fetch(server.url("/r").toString(), 1000); fail() } catch (e: ControlsFormatException) { assertTrue(e.message!!.contains("https")) }
        val layout = LayoutFile.encode(DefaultProfiles.touchShooterPad())
        server.enqueue(MockResponse.Builder().code(200).body(layout).build())
        val e = LibraryEntry("x", LayoutMeta("x"), "layouts/generic/x.json", layout.length, "0".repeat(64), 1, emptyList())
        try { f.layout(server.url("/index.json").toString(), e, "p", 0); fail() } catch (ex: ControlsFormatException) { assertTrue(ex.message!!.contains("checksum")) }
        server.enqueue(MockResponse.Builder().code(200).body(layout).build())
        val ok = f.layout(server.url("/index.json").toString(), e.copy(sha256 = LayoutIndex.sha256(layout.toByteArray())), "p", 0)
        assertEquals(DefaultProfiles.touchShooterPad().landscape, ok.profile.landscape)
    }
}
