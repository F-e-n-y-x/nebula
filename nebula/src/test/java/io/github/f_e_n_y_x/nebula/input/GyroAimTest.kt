package io.github.f_e_n_y_x.nebula.input

import io.github.f_e_n_y_x.nebula.data.engine.GyroAssistRouting
import io.github.f_e_n_y_x.nebula.data.engine.GyroFeed
import io.github.f_e_n_y_x.nebula.data.engine.GyroToggle
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.settings.GyroToggleButton
import io.github.f_e_n_y_x.nebula.settings.MotionHold
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.MotionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GyroAimTest {
    /** No smoothing or deadzone, so single samples map directly. */
    private val raw = MotionSettings(mode = GyroMode.RIGHT_STICK, smoothing = 0, deadzone = 0, stickMinimum = 0)

    /** A phone held upright in landscape (ROTATION_90), turning right at [degPerSec]: Android gyro about the device X axis, which points up. */
    private fun uprightPhone(aim: GyroAim, degPerSec: Float, pitchDegPerSec: Float = 0f): Triple<Float, Float, Float> {
        val (ax, ay, az) = Feedback.phoneToController(9.81f, 0f, 0f, 1)
        aim.onAccel(ax, ay, az)
        // Turning right is clockwise from above: negative about "up" (+X here). Aiming up tilts the top
        // edge towards the player: negative about the device Y axis (which points left).
        val (gx, gy, gz) = Feedback.phoneToController(-degPerSec, -pitchDegPerSec, 0f, 1)
        return Triple(gx, gy, gz)
    }

    @Test
    fun `turning right pushes the stick right and aiming up pushes it up, however the phone is held`() {
        val upright = GyroAim()
        val (gx, gy, gz) = uprightPhone(upright, 90f)
        val (x, y) = upright.stick(gx, gy, gz, raw)
        assertTrue("right turn gave x=$x", x > 0)
        assertEquals(0, y)
        assertEquals((0.5f * 32767).toInt(), x)

        val flat = GyroAim()
        // Lying flat, screen up: gravity reads +Z on the device, turning right is negative about Z.
        val (ax, ay, az) = Feedback.phoneToController(0f, 0f, 9.81f, 1)
        flat.onAccel(ax, ay, az)
        val (fx, fy, fz) = Feedback.phoneToController(0f, 0f, -90f, 1)
        assertTrue(flat.stick(fx, fy, fz, raw).first > 0)

        val aimUp = GyroAim()
        val (px, py, pz) = uprightPhone(aimUp, 0f, pitchDegPerSec = 45f)
        val (sx, sy) = aimUp.stick(px, py, pz, raw)
        assertEquals(0, sx)
        assertTrue("aiming up gave y=$sy", sy > 0)
    }

    @Test
    fun `sensitivity per axis, inversion and full-scale clamp`() {
        val aim = GyroAim()
        val (gx, gy, gz) = uprightPhone(aim, 45f, pitchDegPerSec = 45f)
        val base = aim.stick(gx, gy, gz, raw)
        val faster = GyroAim().let { a -> val (x, y, z) = uprightPhone(a, 45f, 45f); a.stick(x, y, z, raw.copy(sensitivityX = 200)) }
        assertEquals((base.first * 2).toFloat(), faster.first.toFloat(), 2f)
        assertEquals(base.second.toFloat(), faster.second.toFloat(), 2f)
        val inverted = GyroAim().let { a -> val (x, y, z) = uprightPhone(a, 45f, 45f); a.stick(x, y, z, raw.copy(invertX = true, invertY = true)) }
        assertEquals(-base.first.toFloat(), inverted.first.toFloat(), 1f)
        assertEquals(-base.second.toFloat(), inverted.second.toFloat(), 1f)
        val huge = GyroAim().let { a -> val (x, y, z) = uprightPhone(a, 2000f); a.stick(x, y, z, raw) }
        assertEquals(32767, huge.first)
    }

    @Test
    fun `deadzone drops tremor and the stick minimum clears the game's deadzone`() {
        val s = raw.copy(deadzone = 5)
        val aim = GyroAim()
        val (gx, gy, gz) = uprightPhone(aim, 3f)
        assertEquals(0 to 0, aim.stick(gx, gy, gz, s))
        val lifted = GyroAim().let { a -> val (x, y, z) = uprightPhone(a, 1f); a.stick(x, y, z, raw.copy(stickMinimum = 20)) }
        assertTrue("tiny turn gave ${lifted.first}", lifted.first >= (0.2f * 32767).toInt())
    }

    @Test
    fun `smoothing eases in`() {
        val s = raw.copy(smoothing = 50)
        val aim = GyroAim()
        val (gx, gy, gz) = uprightPhone(aim, 90f)
        val first = aim.stick(gx, gy, gz, s).first
        val second = aim.stick(gx, gy, gz, s).first
        assertTrue(first in 1 until second)
    }

    @Test
    fun `mouse accumulates sub-pixel movement and moves up when aiming up`() {
        val s = raw.copy(mode = GyroMode.MOUSE)
        val aim = GyroAim()
        val (gx, gy, gz) = uprightPhone(aim, 10f)
        var total = 0
        repeat(200) { total += aim.mouse(gx, gy, gz, 0.005f, s).first }
        // 10 °/s for 1 s at V+'s 800 px/rad.
        assertEquals(10f * GyroAim.MOUSE_PIXELS_PER_DEGREE, total.toFloat(), 1.5f)
        val up = GyroAim()
        val (px, py, pz) = uprightPhone(up, 0f, pitchDegPerSec = 30f)
        assertTrue(up.mouse(px, py, pz, 0.02f, s).second < 0)
    }

    @Test
    fun `mapping modes read the controller gyro first, else this device`() {
        val stick = MotionSettings(mode = GyroMode.RIGHT_STICK)
        assertEquals(GyroFeed.Controller(5), GyroAssistRouting.pick(stick, controllerWithGyro = 5, phoneHasGyro = true))
        assertEquals(GyroFeed.None, GyroAssistRouting.pick(stick, controllerWithGyro = null, phoneHasGyro = true))
        assertEquals(GyroFeed.Phone, GyroAssistRouting.pick(stick.copy(phoneFallback = true), null, true))
        // The owner's setup: this device, always, a controller without a gyro attached.
        val phone = MotionSettings(mode = GyroMode.RIGHT_STICK, source = MotionSource.PHONE, hold = MotionHold.ALWAYS)
        assertEquals(GyroFeed.Phone, GyroAssistRouting.pick(phone, controllerWithGyro = null, phoneHasGyro = true))
        assertEquals(GyroFeed.Phone, GyroAssistRouting.pick(phone, controllerWithGyro = 5, phoneHasGyro = true))
        assertEquals(GyroFeed.None, GyroAssistRouting.pick(phone.copy(mode = GyroMode.PASSTHROUGH), null, true))
        assertEquals(GyroFeed.None, GyroAssistRouting.pick(phone, null, phoneHasGyro = false))
    }

    @Test
    fun `the toggle button flips the gyro and never reaches the PC`() {
        GyroToggle.on = false
        val s = MotionSettings(mode = GyroMode.RIGHT_STICK, hold = MotionHold.TOGGLE, toggleButton = GyroToggleButton.SELECT)
        assertTrue(GyroAssistRouting.onKey(s, GyroToggleButton.SELECT.keyCode, down = true))
        assertTrue(GyroToggle.on)
        assertTrue(GyroAssistRouting.onKey(s, GyroToggleButton.SELECT.keyCode, down = false))
        assertTrue(GyroToggle.on)
        assertTrue(GyroAssistRouting.onKey(s, GyroToggleButton.SELECT.keyCode, down = true))
        assertFalse(GyroToggle.on)
        assertFalse(GyroAssistRouting.onKey(s, GyroToggleButton.R3.keyCode, down = true))
        assertFalse(GyroAssistRouting.onKey(s.copy(hold = MotionHold.ALWAYS), GyroToggleButton.SELECT.keyCode, down = true))
    }
}
