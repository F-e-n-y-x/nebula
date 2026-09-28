package io.github.f_e_n_y_x.nebula.settings

import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsOverlaySettingsTest {
    @Test
    fun `defaults and dev4 migration`() {
        val d = StatsOverlaySettings.read(emptyMap<String, Any>())
        assertEquals(false, d.enabled)
        assertEquals(listOf(StatMetric.FPS, StatMetric.TOTAL_LATENCY, StatMetric.BITRATE), d.metrics)
        assertEquals(StatPosition.TOP_LEFT, d.position)
        assertEquals(StatLayout.LINE, d.layout)
        assertEquals(DEFAULT_OVERLAY_OPACITY, d.opacity)
        // dev4's "Full" detail becomes every metric.
        assertEquals(StatPreset.NERD.metrics, StatsOverlaySettings.read(mapOf("nebula_perf_overlay_detail" to "full")).metrics)
    }

    @Test
    fun `reads V+ values and keeps menu order`() {
        val s = StatsOverlaySettings.read(
            mapOf(
                "checkbox_enable_perf_overlay" to true,
                "perf_overlay_display_items" to setOf("packet_loss", "render_fps", "resolution", "unknown_v_plus_item"),
                "list_perf_overlay_position" to "bottom_right",
                "list_perf_overlay_orientation" to "vertical",
                "nebula_perf_overlay_size" to "large",
                OVERLAY_OPACITY_KEY to 55,
            ),
        )
        assertEquals(listOf(StatMetric.FPS, StatMetric.PACKET_LOSS, StatMetric.RESOLUTION), s.metrics)
        assertEquals(StatPosition.BOTTOM_RIGHT, s.position)
        assertEquals(StatLayout.CARD, s.layout)
        assertEquals(StatSize.LARGE, s.size)
        assertEquals(55, s.opacity)
        assertTrue(s.enabled)
        // V+'s horizontal "top" is one of Nebula's slots too.
        assertEquals(StatPosition.TOP, StatsOverlaySettings.read(mapOf("list_perf_overlay_position" to "top")).position)
    }

    @Test
    fun `enough metrics, presets and positions`() {
        assertTrue(StatMetric.entries.size >= 12)
        assertEquals(3, StatPreset.entries.size)
        assertEquals(8, StatPosition.entries.size)
        assertEquals(StatMetric.entries.size, StatMetric.entries.map { it.id }.toSet().size)
        assertEquals(StatPreset.GAMER, StatsOverlaySettings(metrics = StatPreset.GAMER.metrics.reversed()).preset)
        assertNull(StatsOverlaySettings(metrics = listOf(StatMetric.CODEC)).preset)
    }

    @Test
    fun `dragging snaps to the nearest corner or edge`() {
        assertEquals(StatPosition.TOP_LEFT, StatPosition.snap(0.1f, 0.1f))
        assertEquals(StatPosition.TOP, StatPosition.snap(0.5f, 0.05f))
        assertEquals(StatPosition.BOTTOM_RIGHT, StatPosition.snap(0.95f, 0.9f))
        assertEquals(StatPosition.RIGHT, StatPosition.snap(0.9f, 0.5f))
        // The middle of the screen isn't a slot: nearest edge wins.
        assertEquals(StatPosition.TOP, StatPosition.snap(0.5f, 0.4f))
        assertEquals(StatPosition.LEFT, StatPosition.snap(0.36f, 0.5f))
        StatPosition.entries.forEach { assertEquals(it, StatPosition.at(it.col, it.row)) }
    }

    @Test
    fun `formats every metric`() {
        val x = StreamStats("1920×1080", 120, 48.4f, 7.25f, "HEVC HDR", hostFps = 119.6f, onePercentLowFps = 101f, jitterMs = 2f, lossPercent = 0.25f, networkMs = 3f, decoder = "c2.qti.hevc.decoder")
        StatMetric.entries.forEach { assertTrue(it.name, it.format(x, 80).isNotBlank()) }
        assertEquals("120 fps", StatMetric.FPS.inline(x))
        assertEquals("7.3 ms", StatMetric.TOTAL_LATENCY.format(x))
        assertEquals("48 Mbps", StatMetric.BITRATE.format(x))
        assertEquals("120", StatMetric.HOST_FPS.format(x))
        assertEquals("80%", StatMetric.BATTERY.format(x, 80))
        assertEquals("—", StatMetric.BATTERY.format(x, null))
        assertEquals("qti.hevc", StatMetric.DECODER.format(x))
        assertEquals("qti.hevc.low_latency", shortDecoder("c2.qti.hevc.decoder.low_latency"))
        assertEquals("—", shortDecoder(""))
    }
}
