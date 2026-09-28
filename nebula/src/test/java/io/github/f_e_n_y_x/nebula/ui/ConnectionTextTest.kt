package io.github.f_e_n_y_x.nebula.ui

import io.github.f_e_n_y_x.nebula.data.engine.toDomain
import io.github.f_e_n_y_x.nebula.domain.model.AbrInfo
import io.github.f_e_n_y_x.nebula.domain.model.AbrSource
import io.github.f_e_n_y_x.nebula.domain.model.ConnectionReport
import io.github.f_e_n_y_x.nebula.domain.model.LinkQuality
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.SuggestedSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.ui.screens.ConnectionText
import io.github.fenyx.nebula.engine.ConnectionAdvisor
import io.github.fenyx.nebula.engine.DisplaySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTextTest {
    private val abr = AbrInfo("Balanced", AbrSource.HOST, 6000, 40000, "loss 6.0% (emergency)")

    @Test fun `overlay line shows the target bitrate and who steers it`() {
        assertNull(ConnectionText.bitrateLine(0, abr))
        assertEquals("Target 30 Mbps", ConnectionText.bitrateLine(30_000, null))
        assertEquals("Target 24.5 Mbps · ABR balanced (PC)", ConnectionText.bitrateLine(24_500, abr))
        assertEquals("Target 20 Mbps · ABR balanced (this device)", ConnectionText.bitrateLine(20_000, abr.copy(source = AbrSource.LOCAL)))
        assertEquals("Target 20 Mbps · ABR balanced (starting)", ConnectionText.bitrateLine(20_000, abr.copy(source = AbrSource.CONNECTING)))
    }

    @Test fun `menu text explains the range and the last change`() {
        assertEquals(
            "Adaptive bitrate balanced: Your PC adjusts it, between 6 Mbps and 40 Mbps · last change: loss 6.0% (emergency).",
            ConnectionText.abrDetail(abr),
        )
        assertTrue(ConnectionText.abrDetail(abr.copy(lastReason = null)).endsWith("40 Mbps."))
    }

    @Test fun `report text`() {
        val r = ConnectionReport(6.4, 1.14, null, 182.0, LinkQuality.EXCELLENT, SuggestedSettings(VideoMode(2340, 1080, 120), 60_000, true), duringStream = false)
        assertEquals("Excellent connection. RTT 6 ms, jitter 1.1 ms, 182 Mbps.", ConnectionText.summary(r))
        assertEquals("—", ConnectionText.loss(r))
        assertEquals("2340×1080 at 120 fps, 60 Mbps (this screen)", ConnectionText.suggestion(r.suggestion!!))
        assertTrue(ConnectionText.note(r).contains("doesn't report packet loss"))
        assertTrue(ConnectionText.note(r.copy(duringStream = true, throughputMbps = null)).startsWith("Measured during the stream"))
        assertEquals("Excellent connection. RTT 6 ms, jitter 1.1 ms, loss 0.2%.", ConnectionText.summary(r.copy(lossPercent = 0.2, throughputMbps = null)))
    }

    @Test fun `engine result maps to the domain`() {
        val d = ConnectionAdvisor.result(4.0, 1.0, 0.0, 100.0, null, DisplaySpec(1080, 1920, 120), duringStream = false).toDomain()
        assertEquals(LinkQuality.EXCELLENT, d.quality)
        assertEquals(VideoMode(1920, 1080, 120), d.suggestion!!.mode)
        assertEquals(56_000, d.suggestion!!.bitrateKbps)
    }

    @Test fun `a suggestion becomes stream settings`() {
        val base = StreamSettings(resolution = Resolution(2560, 1440), fps = 60, bitrateKbps = 80_000)
        val native = HostsViewModel.applySuggestion(base, SuggestedSettings(VideoMode(1920, 1080, 120), 56_000, true), 1920 to 1080)
        assertEquals(Resolution.Native, native.resolution)
        assertEquals(120, native.fps)
        assertEquals(56_000, native.bitrateKbps)
        val scaled = HostsViewModel.applySuggestion(base, SuggestedSettings(VideoMode(1440, 810, 60), 13_000, false), 1920 to 1080)
        assertEquals(Resolution(1440, 810), scaled.resolution)
        assertEquals(60, scaled.fps)
    }
}
