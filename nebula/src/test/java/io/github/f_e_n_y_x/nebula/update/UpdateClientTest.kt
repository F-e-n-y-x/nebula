package io.github.f_e_n_y_x.nebula.update

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateClientTest {
    private val api = MockWebServer()
    private val cdn = MockWebServer()
    private lateinit var dir: File

    @Before fun setUp() {
        api.start()
        cdn.start()
        dir = Files.createTempDirectory("nebula-update-test").toFile()
    }

    @After fun tearDown() {
        api.close()
        cdn.close()
        dir.deleteRecursively()
    }

    private fun client() = UpdateClient(apiBase = api.url("/").toString().trimEnd('/'), allowHttp = true)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun `sends the token and api headers to github`() {
        api.enqueue(MockResponse.Builder().code(200).body("[]").build())
        val r = client().fetchReleases("tok123")
        assertEquals(CheckResult.Ok(emptyList()), r)
        val req = api.takeRequest()
        assertEquals("/repos/F-e-n-y-x/nebula/releases?per_page=20", req.target)
        assertEquals("Bearer tok123", req.headers["Authorization"])
        assertEquals("application/vnd.github+json", req.headers["Accept"])
        assertEquals("2022-11-28", req.headers["X-GitHub-Api-Version"])
    }

    @Test fun `no token means no authorization header`() {
        api.enqueue(MockResponse.Builder().code(200).body("[]").build())
        client().fetchReleases(null)
        assertNull(api.takeRequest().headers["Authorization"])
    }

    @Test fun `private repo without a token is never an update`() {
        api.enqueue(MockResponse.Builder().code(404).body("{\"message\":\"Not Found\"}").build())
        assertEquals(CheckResult.NeedsToken, client().fetchReleases(null))
        api.enqueue(MockResponse.Builder().code(401).build())
        assertEquals(CheckResult.BadToken, client().fetchReleases("bad"))
        api.enqueue(MockResponse.Builder().code(404).build())
        assertEquals(CheckResult.NoAccess, client().fetchReleases("wrong-scope"))
        api.enqueue(MockResponse.Builder().code(403).addHeader("x-ratelimit-remaining", "0").build())
        assertEquals(CheckResult.RateLimited, client().fetchReleases(null))
        api.enqueue(MockResponse.Builder().code(500).build())
        assertTrue(client().fetchReleases("t") is CheckResult.Failed)
    }

    @Test fun `refuses plain http in production mode`() {
        val prod = UpdateClient(apiBase = api.url("/").toString().trimEnd('/'))
        assertTrue(prod.fetchReleases(null) is CheckResult.Failed)
        assertEquals(0, api.requestCount)
    }

    @Test fun `token is not forwarded across a cross-host redirect`() {
        val payload = ByteArray(5000) { (it % 251).toByte() }
        cdn.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(payload)).build())
        api.enqueue(MockResponse.Builder().code(302).addHeader("Location", cdn.url("/signed/nebula.apk").toString()).build())
        val asset = ReleaseAsset(9, "nebula.apk", payload.size.toLong(), null, api.url("/repos/F-e-n-y-x/nebula/releases/assets/9").toString())
        val dest = File(dir, "nebula.apk")
        val hash = client().download(asset, "secret-token", dest)

        assertEquals(sha(payload), hash)
        assertTrue(dest.readBytes().contentEquals(payload))
        val first = api.takeRequest()
        assertEquals("Bearer secret-token", first.headers["Authorization"])
        assertEquals("application/octet-stream", first.headers["Accept"])
        assertNull("Authorization leaked to the CDN host", cdn.takeRequest().headers["Authorization"])
    }

    @Test fun `download enforces the size cap and leaves nothing behind`() {
        api.enqueue(MockResponse.Builder().code(200).body("x".repeat(2048)).build())
        val asset = ReleaseAsset(1, "a.apk", -1, null, api.url("/a").toString())
        val dest = File(dir, "a.apk")
        try {
            client().download(asset, null, dest, maxBytes = 1024)
            throw AssertionError("expected a size error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("larger"))
        }
        assertFalse(dest.exists())
        assertEquals(emptyList<String>(), dir.list()!!.toList())
    }

    @Test fun `download rejects a truncated body`() {
        api.enqueue(MockResponse.Builder().code(200).body("short").build())
        val asset = ReleaseAsset(1, "a.apk", 100, null, api.url("/a").toString())
        try {
            client().download(asset, null, File(dir, "a.apk"))
            throw AssertionError("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
    }

    @Test fun `fetches small checksum files`() {
        api.enqueue(MockResponse.Builder().code(200).body("${"c".repeat(64)}  a.apk\n").build())
        val text = client().fetchText(ReleaseAsset(2, "a.apk.sha256", 80, null, api.url("/s").toString()), null)
        assertEquals("c".repeat(64), Releases.parseChecksumFile(text!!, "a.apk"))
        api.enqueue(MockResponse.Builder().code(200).body("x".repeat(100)).build())
        assertNull(client().fetchText(ReleaseAsset(2, "big", 100, null, api.url("/s").toString()), null, maxBytes = 10))
    }
}
