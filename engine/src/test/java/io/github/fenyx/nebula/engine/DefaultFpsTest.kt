package io.github.fenyx.nebula.engine

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression: a fresh install streamed at V+'s fixed 60 fps on a 120 Hz S25 Ultra. */
class DefaultFpsTest {
    @Test fun `unset frame rate is the native refresh`() {
        assertEquals(120, defaultedFps(stored = false, storedFps = 60) { 120 })
    }

    @Test fun `a frame rate the user set is never replaced`() {
        var asked = false
        assertEquals(120, defaultedFps(stored = true, storedFps = 120) { asked = true; 60 })
        assertEquals(60, defaultedFps(stored = true, storedFps = 60) { 120 })
        assertEquals(false, asked)
    }
}
