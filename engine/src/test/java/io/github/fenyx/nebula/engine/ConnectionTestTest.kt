package io.github.fenyx.nebula.engine

import com.limelight.nvstream.http.NvHTTP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

class ConnectionTestTest {
    private val phone = DisplaySpec(1080, 1920, 120)

    @Test fun `fast clean link suggests native resolution at full frame rate`() {
        val r = ConnectionAdvisor.result(rttMs = 4.0, jitterMs = 1.0, lossPercent = 0.0, throughputMbps = 100.0, currentKbps = null, display = phone, duringStream = false)
        assertEquals(ConnectionQuality.EXCELLENT, r.quality)
        val s = r.suggestion!!
        assertEquals(1920, s.width)
        assertEquals(1080, s.height)
        assertEquals(120, s.fps)
        assertTrue(s.nativeResolution)
        // 65 % of 100 Mbps, capped at twice what 1080p120 needs (28 Mbps).
        assertEquals(56_000, s.bitrateKbps)
    }

    @Test fun `slow link scales the resolution down`() {
        val s = ConnectionAdvisor.suggest(ConnectionAdvisor.bitrateKbps(20.0, null, 1.0, 0.0), phone)!!
        assertEquals(1440, s.width)
        assertEquals(810, s.height)
        assertEquals(60, s.fps)
        assertFalse(s.nativeResolution)
        assertEquals(13_000, s.bitrateKbps)
    }

    @Test fun `loss and jitter lower the bitrate and the rating`() {
        assertEquals(65_000, ConnectionAdvisor.bitrateKbps(100.0, null, 1.0, 0.0))
        assertEquals(55_000, ConnectionAdvisor.bitrateKbps(100.0, null, 1.0, 1.0))
        assertEquals(45_500, ConnectionAdvisor.bitrateKbps(100.0, null, 1.0, 3.0))
        assertEquals(32_500, ConnectionAdvisor.bitrateKbps(100.0, null, 1.0, 8.0))
        assertEquals(55_000, ConnectionAdvisor.bitrateKbps(100.0, null, 25.0, 0.0))
        assertEquals(ConnectionQuality.FAIR, ConnectionAdvisor.quality(100.0, 4.0, 1.0, 3.0))
        assertEquals(ConnectionQuality.POOR, ConnectionAdvisor.quality(100.0, 4.0, 1.0, 6.0))
        assertEquals(ConnectionQuality.GOOD, ConnectionAdvisor.quality(100.0, 4.0, 20.0, 0.0))
        assertEquals(ConnectionQuality.POOR, ConnectionAdvisor.quality(10.0, 4.0, 1.0, 0.0))
        // Unknown loss (Foundation host) is not held against the link.
        assertEquals(ConnectionQuality.EXCELLENT, ConnectionAdvisor.quality(100.0, 4.0, 1.0, null))
    }

    @Test fun `too little bandwidth suggests nothing`() {
        assertNull(ConnectionAdvisor.bitrateKbps(0.5, null, 1.0, 0.0))
        assertNull(ConnectionAdvisor.bitrateKbps(null, null, 1.0, 0.0))
        assertNull(ConnectionAdvisor.suggest(null, phone))
    }

    @Test fun `during a stream the current bitrate is the base`() {
        val r = ConnectionAdvisor.result(8.0, 2.0, 0.0, null, 30_000, phone, duringStream = true)
        assertTrue(r.duringStream)
        assertNull(r.throughputMbps)
        assertEquals(ConnectionQuality.EXCELLENT, r.quality)
        assertEquals(30_000, r.suggestion!!.bitrateKbps)
        assertEquals(21_000, ConnectionAdvisor.bitrateKbps(null, 30_000, 2.0, 3.0))
        assertEquals(ConnectionQuality.FAIR, ConnectionAdvisor.quality(null, 60.0, 2.0, 0.0))
    }

    @Test fun `latency drops the setup sample and averages the changes`() {
        val (rtt, jitter) = ConnectionAdvisor.latency(listOf(50.0, 10.0, 12.0, 14.0))
        assertEquals(12.0, rtt, 1e-9)
        assertEquals(2.0, jitter, 1e-9)
        assertEquals(0.0 to 0.0, ConnectionAdvisor.latency(emptyList()))
        assertEquals(7.0 to 0.0, ConnectionAdvisor.latency(listOf(7.0)))
    }

    @Test fun `parses Nova's probe result`() {
        assertEquals(1.5, ProbeParsing.lossPercent("""{"nonce":"n","sentBytes":4194304,"result":"completed","lossPct":1.5,"retransmits":3}""")!!, 1e-9)
        assertEquals(100.0, ProbeParsing.lossPercent("""{"lossPct":250}""")!!, 1e-9)
        assertNull(ProbeParsing.lossPercent("""{"nonce":"n","result":"completed"}"""))
        assertNull(ProbeParsing.lossPercent("""{"lossPct":-1}"""))
        assertNull(ProbeParsing.lossPercent("""{"lossPct":"x"}"""))
        assertNull(ProbeParsing.lossPercent("not json"))
        assertNull(ProbeParsing.lossPercent(null))
    }

    @Test fun `maps probe errors`() {
        assertEquals(ConnectionTestFailure.STREAM_ACTIVE, ProbeParsing.failureOf(NvHTTP.NetworkProbeException("stream_active")).failure)
        val limited = ProbeParsing.failureOf(NvHTTP.NetworkProbeException("rate_limited", 1500))
        assertEquals(ConnectionTestFailure.RATE_LIMITED, limited.failure)
        assertEquals(1500L, limited.retryAfterMs)
        assertEquals(ConnectionTestFailure.NOT_PAIRED, ProbeParsing.failureOf(NvHTTP.NetworkProbeException("not_paired")).failure)
        assertEquals(ConnectionTestFailure.UNSUPPORTED, ProbeParsing.failureOf(NvHTTP.NetworkProbeException("http_404")).failure)
        assertEquals(ConnectionTestFailure.UNSUPPORTED, ProbeParsing.failureOf(FileNotFoundException()).failure)
        assertEquals(ConnectionTestFailure.OFFLINE, ProbeParsing.failureOf(java.net.ConnectException()).failure)
        assertEquals(ConnectionTestFailure.FAILED, ProbeParsing.failureOf(IllegalStateException()).failure)
    }

    @Test fun `abr settings map to V+ keys`() {
        assertEquals(AbrSettings(), AbrSettings.from(emptyMap<String, Any>()))
        assertEquals(AbrMode.CONSERVATIVE, AbrSettings.from(mapOf(AbrSettings.ENABLED_KEY to true, AbrSettings.MODE_KEY to "lowLatency")).mode)
        assertEquals(AbrMode.AGGRESSIVE, AbrSettings.from(mapOf(AbrSettings.ENABLED_KEY to "true", AbrSettings.MODE_KEY to "quality")).mode)
        assertEquals(AbrMode.BALANCED, AbrSettings.from(mapOf(AbrSettings.ENABLED_KEY to true, AbrSettings.MODE_KEY to "odd")).mode)
        assertEquals(AbrMode.OFF, AbrSettings.from(mapOf(AbrSettings.ENABLED_KEY to false, AbrSettings.MODE_KEY to "quality")).mode)
        val bounded = AbrSettings.from(mapOf(AbrSettings.ENABLED_KEY to true, AbrSettings.MIN_KEY to 30000, AbrSettings.MAX_KEY to "20000"))
        assertEquals(20000, bounded.minKbps)
        assertEquals(20000, bounded.maxKbps)
        assertEquals(0, AbrSettings.from(mapOf(AbrSettings.MIN_KEY to -5)).minKbps)
        assertEquals("lowLatency", AbrMode.CONSERVATIVE.wire)
        assertEquals("quality", AbrMode.AGGRESSIVE.wire)
        assertNull(AbrMode.OFF.wire)
    }
}
