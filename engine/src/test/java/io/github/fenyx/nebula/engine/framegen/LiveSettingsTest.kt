package io.github.fenyx.nebula.engine.framegen

import io.github.fenyx.nebula.engine.framegen.LiveSettings.Framegen
import io.github.fenyx.nebula.engine.framegen.LiveSettings.Upscaler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression (dev10 crash): a live settings change must never rebuild the decoder or swap the screen's producer. */
class LiveSettingsTest {
    @Test fun `armed and still planned reconfigures in place`() {
        assertEquals(Framegen.RECONFIGURE, LiveSettings.framegen(armed = true, planOn = true))
    }

    @Test fun `armed but now off pauses until the next stream`() {
        assertEquals(Framegen.PAUSE_UNTIL_NEXT_STREAM, LiveSettings.framegen(armed = true, planOn = false))
    }

    @Test fun `not armed never starts mid-stream`() {
        assertEquals(Framegen.NEXT_STREAM, LiveSettings.framegen(armed = false, planOn = true))
        assertEquals(Framegen.NONE, LiveSettings.framegen(armed = false, planOn = false))
    }

    @Test fun `upscaler changes`() {
        assertEquals(Upscaler.NONE, LiveSettings.upscaler(framegenArmed = true, upscalerArmed = false, mode = UpscalerMode.FSR1))
        assertEquals(Upscaler.UPDATE, LiveSettings.upscaler(false, true, UpscalerMode.SGSR1))
        assertEquals(Upscaler.UPDATE, LiveSettings.upscaler(false, true, UpscalerMode.OFF))
        assertEquals(Upscaler.NEXT_STREAM, LiveSettings.upscaler(false, false, UpscalerMode.SHARPEN))
        assertEquals(Upscaler.NONE, LiveSettings.upscaler(false, false, UpscalerMode.OFF))
    }

    @Test fun `restart watch fails only after the timeout without generated frames`() {
        val w = LiveSettings.RestartWatch(timeoutMs = 6_000)
        assertFalse(w.onWindow(0, generating = false, paused = false)) // not started
        w.started(1_000)
        assertFalse(w.onWindow(3_000, generating = false, paused = false))
        assertTrue(w.waiting)
        assertTrue(w.onWindow(7_100, generating = false, paused = false))
        assertFalse(w.waiting)
        assertFalse(w.onWindow(9_000, generating = false, paused = false)) // fires once
    }

    @Test fun `restart watch ends when frames come back or the user pauses`() {
        val w = LiveSettings.RestartWatch(timeoutMs = 6_000)
        w.started(0)
        assertFalse(w.onWindow(2_000, generating = true, paused = false))
        assertFalse(w.waiting)
        w.started(0)
        assertFalse(w.onWindow(2_000, generating = false, paused = true))
        assertFalse(w.onWindow(10_000, generating = false, paused = false))
        w.started(0)
        w.cancel()
        assertFalse(w.onWindow(10_000, generating = false, paused = false))
    }
}
