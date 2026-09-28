package io.github.f_e_n_y_x.nebula.diagnostics

import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccumulatorTest {
    private fun stats(fps: Int, lat: Float, loss: Float = 0f, mbps: Float = 30f) =
        StreamStats("2560×1440", fps, mbps, lat, "HEVC", width = 2560, height = 1440, lossPercent = loss, hostMs = 2f, networkMs = 3f, decodeMs = 1f, decoder = "c2.qti.hevc.decoder.low_latency")

    @Test
    fun `averages, minimum, p95 and duration from connect to end`() {
        val a = SessionAccumulator(startedAtMs = 5L, startMono = 0L, game = "GTA V", hostId = "h", mode = "Virtual display")
        // Connected 800 ms after start, then 100 one-second samples.
        for (i in 0 until 100) a.add(stats(fps = if (i == 50) 30 else 120, lat = if (i >= 95) 20f else 6f, loss = if (i == 10) 4f else 0f), 800L + i * 1000L)
        val s = a.finish(nowMono = 800L + 100_000L, endReason = null, failed = false)
        assertEquals(800L, s.connectMs)
        assertEquals(100L, s.durationS)
        assertEquals(30f, s.minFps, 0f)
        assertEquals((99 * 120 + 30) / 100f, s.avgFps, 0.01f)
        assertEquals(6f * 0.95f + 20f * 0.05f, s.avgLatencyMs, 0.01f)
        assertEquals("p95 lands in the 6 ms bin", 6.5f, s.p95LatencyMs, 0.01f)
        assertEquals(4f, s.maxLossPercent, 0f)
        assertEquals(0.04f, s.avgLossPercent, 1e-4f)
        assertEquals("c2.qti.hevc.decoder.low_latency", s.decoder)
        assertEquals(100, s.samples)
        assertNull(s.endReason)
    }

    @Test
    fun `a failed start has no samples and zero duration`() {
        val a = SessionAccumulator(0L, 0L, "Wukong", "h", "Mirror")
        val s = a.finish(3_000L, "Couldn't start the stream: RTSP handshake failed (error -1).", failed = true)
        assertTrue(s.failed)
        assertEquals(0L, s.durationS)
        assertNull(s.connectMs)
        assertTrue(s.describe().contains("Failed"))
    }

    @Test
    fun `resolution changes are counted and described`() {
        val a = SessionAccumulator(0L, 0L, "Desktop", "h", "Virtual display")
        a.add(stats(60, 5f), 500L)
        a.resolutionChanged()
        val s = a.finish(2_000L, "The PC ended the stream.", false)
        assertEquals(1, s.resolutionChanges)
        assertTrue(s.describe().contains("1 live resolution change"))
        assertTrue(s.describe().contains("Ended: The PC ended the stream."))
    }

    @Test
    fun `formatDuration picks sensible units`() {
        assertEquals("42 s", formatDuration(42))
        assertEquals("3 min 5 s", formatDuration(185))
        assertEquals("2 h 1 min", formatDuration(7_260))
    }
}
