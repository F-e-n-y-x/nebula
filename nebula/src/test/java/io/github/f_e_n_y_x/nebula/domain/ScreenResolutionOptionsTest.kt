package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The live picker's "For this screen" sizes: exact aspect, even sides, multiples of 8 when close. */
class ScreenResolutionOptionsTest {
    private fun sizes(w: Int, h: Int) = ResolutionOptions.forScreen(Resolution(w, h)).map { it.label to "${it.resolution.width}x${it.resolution.height}" }

    @Test fun s25UltraLandscape() {
        assertEquals(
            listOf("Native" to "3120x1440", "90%" to "2808x1296", "80%" to "2496x1152", "75%" to "2340x1080", "67%" to "2080x960", "50%" to "1560x720"),
            sizes(3120, 1440),
        )
    }

    @Test fun portraitFollowsTheScreen() {
        assertEquals(
            listOf("Native" to "1440x3120", "90%" to "1296x2808", "80%" to "1152x2496", "75%" to "1080x2340", "67%" to "960x2080", "50%" to "720x1560"),
            sizes(1440, 3120),
        )
    }

    @Test fun everySizeKeepsTheAspectAndIsEven() {
        listOf(3120 to 1440, 2340 to 1080, 2400 to 1080, 2560 to 1600, 1920 to 1080, 1080 to 2340).forEach { (w, h) ->
            ResolutionOptions.forScreen(Resolution(w, h)).forEach { o ->
                val r = o.resolution
                assertTrue("$w×$h → $r even", r.width % 2 == 0 && r.height % 2 == 0)
                assertEquals("$w×$h → $r aspect", w.toLong() * r.height, h.toLong() * r.width)
            }
        }
    }

    @Test fun coarseRatioStaysEvenAndCloseToTheAspect() {
        // Fold inner display 2208×1768 is 276:221 × 8, so exact copies are 12.5% apart; the
        // in-between steps round each side to even instead.
        ResolutionOptions.forScreen(Resolution(2208, 1768)).forEach { o ->
            val r = o.resolution
            assertTrue("$r even", r.width % 2 == 0 && r.height % 2 == 0)
            assertEquals(2208.0 / 1768, r.width.toDouble() / r.height, 0.005)
        }
    }

    @Test fun prefersMultiplesOfEightWithinOnePercent() {
        // 1920×1080 at 90%: 1728×972 is exact; 972 isn't a multiple of 8 and no 8-aligned 16:9 size is within 1%.
        assertEquals(Resolution(1728, 972), ResolutionOptions.scaled(Resolution(1920, 1080), 0.9))
        // 2560×1600 (8:5 × 320) at 90%: 2304×1440, both multiples of 8.
        assertEquals(Resolution(2304, 1440), ResolutionOptions.scaled(Resolution(2560, 1600), 0.9))
    }

    @Test fun oddRatioFallsBackToNearestEven() {
        // 2001×1001 reduces to itself; no exact even multiple exists, so round each side to even.
        val r = ResolutionOptions.scaled(Resolution(2001, 1001), 0.5)
        assertEquals(Resolution(1000, 500), r)
    }

    @Test fun standardDropsNativeAndWhatTheScreenListShows() {
        val screen = ResolutionOptions.forScreen(Resolution(2340, 1080))
        val std = ResolutionOptions.standard(screen, Resolution(2340, 1080), listOf(Resolution(2340, 1080), Resolution(1600, 900)), showLow = false, current = Resolution(1920, 1080))
        assertEquals(listOf("720p", "1080p", "1440p", "4K", "Custom"), std.map { it.label })
        assertTrue(std.none { it.kind == ResolutionOption.Kind.NATIVE })
        assertTrue(std.none { o -> screen.any { it.resolution == o.resolution } })
    }

    @Test fun tinyScreensSkipUnstreamableSizes() {
        assertTrue(ResolutionOptions.forScreen(Resolution(640, 360)).all { minOf(it.resolution.width, it.resolution.height) >= ResolutionOptions.MIN_HEIGHT })
        assertEquals(emptyList<ResolutionOption>(), ResolutionOptions.forScreen(Resolution(0, 0)))
    }
}
