package io.github.f_e_n_y_x.nebula.ui

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
import io.github.f_e_n_y_x.nebula.ui.screens.isSquarishScreen
import io.github.f_e_n_y_x.nebula.ui.screens.streamOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stream's requested orientation, as V+'s OrientationManager picks it. */
class StreamOrientationTest {
    @Test fun landscapeStreamTurnsWithTheSensorBetweenLandscapes() {
        // The dev8 bug: this was SCREEN_ORIENTATION_LOCKED, so a stream opened with the phone
        // upright stayed portrait.
        assertEquals(SCREEN_ORIENTATION_USER_LANDSCAPE, streamOrientation(false, 1920, 1080, squarishScreen = false))
        assertEquals(SCREEN_ORIENTATION_USER_LANDSCAPE, streamOrientation(false, 3120, 1440, squarishScreen = false))
    }

    @Test fun unknownModeDefaultsToLandscape() {
        // Before the stream has started (the placeholder StreamScreen passes 1×0).
        assertEquals(SCREEN_ORIENTATION_USER_LANDSCAPE, streamOrientation(false, 1, 0, squarishScreen = false))
    }

    @Test fun portraitStreamStaysPortrait() {
        assertEquals(SCREEN_ORIENTATION_USER_PORTRAIT, streamOrientation(false, 1080, 1920, squarishScreen = false))
    }

    @Test fun followRotationAllowsEveryOrientation() {
        assertEquals(SCREEN_ORIENTATION_FULL_USER, streamOrientation(true, 1920, 1080, squarishScreen = false))
        assertEquals(SCREEN_ORIENTATION_FULL_USER, streamOrientation(true, 1080, 1920, squarishScreen = false))
    }

    @Test fun squareStreamOnSquarishScreenIsFreeUnlessOnScreenControls() {
        assertEquals(SCREEN_ORIENTATION_FULL_USER, streamOrientation(false, 1600, 1600, squarishScreen = true))
        assertEquals(SCREEN_ORIENTATION_USER_LANDSCAPE, streamOrientation(false, 1600, 1600, squarishScreen = true, onscreenControls = true))
        assertEquals(SCREEN_ORIENTATION_USER_LANDSCAPE, streamOrientation(false, 1600, 1600, squarishScreen = false))
    }

    @Test fun squarishMatchesVPlusCutoff() {
        assertTrue(isSquarishScreen(2208, 1768)) // Fold inner display
        assertFalse(isSquarishScreen(3120, 1440)) // S25 Ultra
        assertFalse(isSquarishScreen(0, 0))
    }
}
