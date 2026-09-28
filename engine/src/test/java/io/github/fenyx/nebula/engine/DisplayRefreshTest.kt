package io.github.fenyx.nebula.engine

import io.github.fenyx.nebula.engine.DisplayRefresh.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayRefreshTest {
    // An S25 Ultra-like panel: 120 Hz at QHD+ and FHD+, plus a 96 Hz and 60 Hz mode and another size at 144.
    private val modes = listOf(
        Mode(1440, 3120, 60f), Mode(1440, 3120, 96f), Mode(1440, 3120, 120f),
        Mode(1080, 2340, 60f), Mode(1080, 2340, 96f), Mode(1080, 2340, 119.88f),
        Mode(720, 1560, 144f),
    )

    @Test fun `max refresh of the current size, not the adaptive current rate`() {
        // Adaptive refresh reports 60 while idle; the answer is still 120.
        assertEquals(120, DisplayRefresh.maxHz(modes, 1080, 2340, currentHz = 60f))
        assertEquals(120, DisplayRefresh.maxHz(modes, 1440, 3120, currentHz = 60f))
    }

    @Test fun `modes of other resolutions are ignored`() {
        // The 144 Hz mode is at 720x1560 only.
        assertEquals(120, DisplayRefresh.maxHz(modes, 1440, 3120, currentHz = 120f))
        assertEquals(144, DisplayRefresh.maxHz(modes, 720, 1560, currentHz = 60f))
    }

    @Test fun `size matches in either orientation`() {
        assertEquals(120, DisplayRefresh.maxHz(modes, 2340, 1080, currentHz = 60f))
    }

    @Test fun `no mode of that size falls back to the current rate, then 60`() {
        assertEquals(90, DisplayRefresh.maxHz(modes, 1600, 2560, currentHz = 90.2f))
        assertEquals(60, DisplayRefresh.maxHz(emptyList(), 0, 0, currentHz = null))
        assertEquals(60, DisplayRefresh.maxHz(listOf(Mode(1080, 2340, 60f), Mode(1080, 2340, 48f)), 1080, 2340, 30f))
    }

    @Test fun `frame rate choices put native first without a duplicate`() {
        assertEquals(listOf("Native (120 Hz)" to 120, "30" to 30, "60" to 60, "90" to 90, "144" to 144), DisplayRefresh.frameRateChoices(120))
        assertEquals("Native (165 Hz)" to 165, DisplayRefresh.frameRateChoices(165).first())
        assertEquals(6, DisplayRefresh.frameRateChoices(165).size)
    }
}
