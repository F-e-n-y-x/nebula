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

    @Test fun `asks github anonymously with the api headers`() {
        api.enqueue(MockResponse.Builder().code(200).body("[]").build())
        val r = client().fetchReleases()
        assertEquals(CheckResult.Ok(emptyList()), r)
        val req = api.takeRequest()
        assertEquals("/repos/F-e-n-y-x/nebula/releases?per_page=20", req.target)
        assertNull(req.headers["Authorization"])
        assertEquals("application/vnd.github+json", req.headers["Accept"])
        assertEquals("2022-11-28", req.headers["X-GitHub-Api-Version"])
    }

    @Test fun `missing repo or errors are never an update`() {
        api.enqueue(MockResponse.Builder().code(404).body("{\"message\":\"Not Found\"}").build())
        assertEquals(CheckResult.NotFound, client().fetchReleases())
        api.enqueue(MockResponse.Builder().code(401).build())
        assertEquals(CheckResult.NotFound, client().fetchReleases())
        api.enqueue(MockResponse.Builder().code(403).addHeader("x-ratelimit-remaining", "0").build())
        assertEquals(CheckResult.RateLimited, client().fetchReleases())
        api.enqueue(MockResponse.Builder().code(500).build())
        assertTrue(client().fetchReleases() is CheckResult.Failed)
    }

    @Test fun `refuses plain http in production mode`() {
        val prod = UpdateClient(apiBase = api.url("/").toString().trimEnd('/'))
        assertTrue(prod.fetchReleases() is CheckResult.Failed)
        assertEquals(0, api.requestCount)
    }

    @Test fun `follows the cross-host redirect to the download`() {
        val payload = ByteArray(5000) { (it % 251).toByte() }
        cdn.enqueue(MockResponse.Builder().code(200).body(okio.Buffer().write(payload)).build())
        api.enqueue(MockResponse.Builder().code(302).addHeader("Location", cdn.url("/signed/nebula.apk").toString()).build())
        val asset = ReleaseAsset(9, "nebula.apk", payload.size.toLong(), null, api.url("/repos/F-e-n-y-x/nebula/releases/assets/9").toString())
        val dest = File(dir, "nebula.apk")
        val hash = client().download(asset, dest)

        assertEquals(sha(payload), hash)
        assertTrue(dest.readBytes().contentEquals(payload))
        val first = api.takeRequest()
        assertNull(first.headers["Authorization"])
        assertEquals("application/octet-stream", first.headers["Accept"])
        assertEquals("/signed/nebula.apk", cdn.takeRequest().target)
    }

    @Test fun `download enforces the size cap and leaves nothing behind`() {
        api.enqueue(MockResponse.Builder().code(200).body("x".repeat(2048)).build())
        val asset = ReleaseAsset(1, "a.apk", -1, null, api.url("/a").toString())
        val dest = File(dir, "a.apk")
        try {
            client().download(asset, dest, maxBytes = 1024)
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
            client().download(asset, File(dir, "a.apk"))
            throw AssertionError("expected an error")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("incomplete"))
        }
    }

    @Test fun `fetches small checksum files`() {
        api.enqueue(MockResponse.Builder().code(200).body("${"c".repeat(64)}  a.apk\n").build())
        val text = client().fetchText(ReleaseAsset(2, "a.apk.sha256", 80, null, api.url("/s").toString()))
        assertEquals("c".repeat(64), Releases.parseChecksumFile(text!!, "a.apk"))
        api.enqueue(MockResponse.Builder().code(200).body("x".repeat(100)).build())
        assertNull(client().fetchText(ReleaseAsset(2, "big", 100, null, api.url("/s").toString()), maxBytes = 10))
    }
}
