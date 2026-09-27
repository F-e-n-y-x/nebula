package io.github.fenyx.nebula.engine

import com.limelight.binding.video.PerformanceInfo
import com.limelight.binding.video.StreamHdrFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsMappingTest {
    @Test fun `maps a decoder window`() {
        val info = PerformanceInfo().apply {
            decoder = "c2.qti.hevc.decoder.low_latency"
            initialWidth = 2560
            initialHeight = 1440
            renderedFps = 119.5f
            receivedFps = 120f
            rttInfo = (8L shl 32) or 2L
            aveHostProcessingLatency = 3.5f
            decodeTimeMs = 2f
            renderingLatencyMs = 4f
            lostFrameRate = 0.5f
            hdrFormat = StreamHdrFormat.HDR10
        }
        val stats = info.toStreamStats(measuredMbps = 42.5, previous = StreamStats())

        assertEquals(119.5f, stats.fps)
        assertEquals(42_500, stats.bitrateKbps)
        assertEquals(8f, stats.latency.networkMs)
        assertEquals(3.5f + 4f + 2f + 4f, stats.latency.totalMs)
        assertEquals(0.5f, stats.lossPercent)
        assertEquals(2560, stats.width)
        assertTrue(stats.hdr)
    }

    @Test fun `keeps the last bitrate until the meter has a window and scrubs NaN loss`() {
        val previous = StreamStats(bitrateKbps = 20_000, width = 1920, height = 1080, decoder = "avc")
        val stats = PerformanceInfo().apply { lostFrameRate = Float.NaN }.toStreamStats(null, previous)

        assertEquals(20_000, stats.bitrateKbps)
        assertEquals(0f, stats.lossPercent)
        assertEquals(1920, stats.width)
        assertEquals("avc", stats.decoder)
    }

    @Test fun `codec choices map onto V+ format options and back`() {
        CodecPreference.entries.forEach { assertEquals(it, it.toFormatOption().toCodecPreference()) }
    }
}
