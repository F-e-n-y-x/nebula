package io.github.f_e_n_y_x.nebula.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutTest {
    private fun layout(style: HomeStyle, tv: Boolean = false, landscape: Boolean = false) = ResolveHomeLayout(style, tv, landscape)

    @Test fun autoIsSpotlightOnTv() {
        assertEquals(HomeLayout.TV_SPOTLIGHT, layout(HomeStyle.AUTO, tv = true, landscape = true))
        // A TV that reports portrait (rotated panels, some emulators) is still a TV.
        assertEquals(HomeLayout.TV_SPOTLIGHT, layout(HomeStyle.AUTO, tv = true, landscape = false))
    }

    @Test fun autoIsSpotlightInLandscape() {
        assertEquals(HomeLayout.SPOTLIGHT_WIDE, layout(HomeStyle.AUTO, landscape = true))
    }

    @Test fun autoIsShelfWhenUpright() {
        assertEquals(HomeLayout.SHELF_PORTRAIT, layout(HomeStyle.AUTO, landscape = false))
    }

    @Test fun autoStyleMatchesTheSettingHelp() {
        assertEquals(HomeStyle.SPOTLIGHT, ResolveHomeLayout.autoStyle(isTv = true, isLandscape = false))
        assertEquals(HomeStyle.SPOTLIGHT, ResolveHomeLayout.autoStyle(isTv = false, isLandscape = true))
        assertEquals(HomeStyle.SHELF, ResolveHomeLayout.autoStyle(isTv = false, isLandscape = false))
    }

    @Test fun explicitSpotlightFollowsTheFormFactor() {
        assertEquals(HomeLayout.TV_SPOTLIGHT, layout(HomeStyle.SPOTLIGHT, tv = true))
        assertEquals(HomeLayout.SPOTLIGHT_WIDE, layout(HomeStyle.SPOTLIGHT, landscape = true))
        assertEquals(HomeLayout.SPOTLIGHT_PORTRAIT, layout(HomeStyle.SPOTLIGHT, landscape = false))
    }

    @Test fun explicitShelfIsWideOnTvAndLandscape() {
        assertEquals(HomeLayout.SHELF_WIDE, layout(HomeStyle.SHELF, tv = true))
        assertEquals(HomeLayout.SHELF_WIDE, layout(HomeStyle.SHELF, landscape = true))
        assertEquals(HomeLayout.SHELF_PORTRAIT, layout(HomeStyle.SHELF))
    }

    @Test fun everyCombinationResolves() {
        for (s in HomeStyle.entries) for (tv in listOf(true, false)) for (land in listOf(true, false)) {
            val l = layout(s, tv, land)
            if (s == HomeStyle.SHELF) assert(l.isShelf) { "$s $tv $land -> $l" }
            if (s == HomeStyle.SPOTLIGHT) assert(!l.isShelf) { "$s $tv $land -> $l" }
        }
    }
}
