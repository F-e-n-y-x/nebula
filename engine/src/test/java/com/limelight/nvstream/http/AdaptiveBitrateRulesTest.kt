package com.limelight.nvstream.http

import com.limelight.nvstream.http.AdaptiveBitrateService.AbrStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveBitrateRulesTest {
    private fun stats(loss: Float) = AbrStats(packetLoss = loss, rttMs = 10, decodeFps = 60f, droppedFrames = 0)

    @Test fun `presets match Foundation`() {
        assertEquals(6000 to 40000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_BALANCED, 20000, 0, 0))
        assertEquals(10000 to 30000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_QUALITY, 20000, 0, 0))
        assertEquals(2000 to 24000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_LOW_LATENCY, 20000, 0, 0))
        assertEquals(3000 to 10000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_BALANCED, 5000, 0, 0))
    }

    @Test fun `user bounds replace the preset and never invert`() {
        assertEquals(8000 to 50000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_BALANCED, 20000, 8000, 50000))
        assertEquals(6000 to 50000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_BALANCED, 20000, 0, 50000))
        assertEquals(12000 to 12000, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_BALANCED, 20000, 30000, 12000))
        assertEquals(500 to 500, AdaptiveBitrateService.resolveRange(AdaptiveBitrateService.MODE_LOW_LATENCY, 0, 100, 100))
    }

    @Test fun `heavy loss steps down at once`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        val step = c.tick(stats(6f), 20000, 10_000)!!
        assertEquals(14000, step.bitrateKbps)
        assertEquals("loss=6.0% emergency", step.reason)
        c.applied(step, 10_000)
        // Cooldown: the next report 1 s later waits.
        assertNull(c.tick(stats(6f), 14000, 11_000))
        assertEquals(9800, c.tick(stats(6f), 14000, 12_000)!!.bitrateKbps)
    }

    @Test fun `moderate loss must be sustained`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        assertNull(c.tick(stats(3f), 20000, 10_000))
        assertEquals(18000, c.tick(stats(3f), 20000, 11_000)!!.bitrateKbps)
    }

    @Test fun `mild loss holds, then steps after four reports`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        repeat(3) { assertNull(c.tick(stats(1f), 20000, 10_000L + it * 1000)) }
        assertEquals(19000, c.tick(stats(1f), 20000, 13_000)!!.bitrateKbps)
    }

    @Test fun `climbs after clean reports, gently after a drop`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        repeat(4) { assertNull(c.tick(stats(0f), 20000, 10_000L + it * 1000)) }
        val up = c.tick(stats(0f), 20000, 14_000)!!
        assertEquals(21000, up.bitrateKbps)
        assertEquals("stable 5s probe", up.reason)

        val d = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        d.applied(d.tick(stats(6f), 20000, 10_000)!!, 10_000)
        repeat(4) { assertNull(d.tick(stats(0f), 14000, 12_000L + it * 1000)) }
        assertEquals(14280, d.tick(stats(0f), 14000, 16_000)!!.bitrateKbps)
    }

    @Test fun `quality mode climbs sooner`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_QUALITY, 10000, 30000)
        repeat(2) { assertNull(c.tick(stats(0f), 20000, 10_000L + it * 1000)) }
        assertEquals(21000, c.tick(stats(0f), 20000, 12_000)!!.bitrateKbps)
    }

    @Test fun `stays inside the range`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 20500)
        repeat(4) { c.tick(stats(0f), 20000, 10_000L + it * 1000) }
        assertEquals(20500, c.tick(stats(0f), 20000, 14_000)!!.bitrateKbps)
        val atMax = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 20000)
        repeat(10) { assertNull(atMax.tick(stats(0f), 20000, 10_000L + it * 1000)) }
        val floor = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        assertNull(floor.tick(stats(9f), 6000, 10_000))
    }

    @Test fun `a manual change restarts the runs`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        repeat(4) { c.tick(stats(0f), 20000, 10_000L + it * 1000) }
        c.reset(14_000)
        assertNull(c.tick(stats(0f), 25000, 15_000))
        repeat(4) { assertNull(c.tick(stats(0f), 25000, 16_000L + it * 1000)) }
        assertEquals(26250, c.tick(stats(0f), 25000, 20_000)!!.bitrateKbps)
    }

    @Test fun `non-finite loss counts as clean`() {
        val c = LocalAbrController(AdaptiveBitrateService.MODE_BALANCED, 6000, 40000)
        assertNull(c.tick(stats(Float.NaN), 20000, 10_000))
    }

    @Test fun `parses Nova's probe capabilities`() {
        // Body served by Nova's GET /api/network/capabilities.
        val caps = NvHTTP.parseProbeCapabilities(
            """{"version":1,"features":["bandwidth-probe-v1","tcp-loss-v1"],"bandwidthProbe":{"version":1,"endpoint":"/api/network/probe","minBytes":65536,"maxBytes":4194304,"cooldownMs":5000,"resultEndpoint":"/api/network/probe/result"}}""",
        )
        assertEquals("/api/network/probe", caps.endpoint)
        assertEquals(65536L, caps.minBytes)
        assertEquals(4194304L, caps.maxBytes)
    }

    @Test fun `rejects bad probe capabilities`() {
        fun reason(body: String) = runCatching { NvHTTP.parseProbeCapabilities(body) }.exceptionOrNull() as NvHTTP.NetworkProbeException
        assertEquals("unsupported_version", reason("""{"version":0,"bandwidthProbe":{"version":1,"endpoint":"/api/network/probe","minBytes":1,"maxBytes":2}}""").reason)
        assertEquals("invalid_capabilities", reason("""{"version":1,"bandwidthProbe":{"version":1,"endpoint":"/elsewhere","minBytes":1,"maxBytes":2}}""").reason)
        assertEquals("invalid_capabilities", reason("""{"version":1,"bandwidthProbe":{"version":1,"endpoint":"/api/network/probe","minBytes":5,"maxBytes":2}}""").reason)
        assertEquals("invalid_capabilities", reason("not json").reason)
    }
}
