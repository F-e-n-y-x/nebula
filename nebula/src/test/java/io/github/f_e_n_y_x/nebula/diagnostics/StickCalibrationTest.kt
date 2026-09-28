package io.github.f_e_n_y_x.nebula.diagnostics

import android.view.MotionEvent
import io.github.f_e_n_y_x.nebula.data.engine.ControllerTraits
import io.github.f_e_n_y_x.nebula.data.engine.GamepadAxes
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlin.math.hypot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickShapeTest {
    private val eps = 1e-4f

    @Test
    fun `inside the deadzone reads exactly zero`() {
        val s = StickShape(deadzone = 0.1f)
        assertEquals(0f to 0f, s.apply(0.07f, 0.07f))
        assertEquals(0f to 0f, s.apply(0f, 0f))
    }

    @Test
    fun `just past the deadzone starts from zero, not from the deadzone radius`() {
        val (x, y) = StickShape(deadzone = 0.2f).apply(0.21f, 0f)
        assertEquals(0.0125f, x, eps) // (0.21 - 0.2) / 0.8
        assertEquals(0f, y, eps)
    }

    @Test
    fun `full push still reaches full scale with a deadzone`() {
        val (x, _) = StickShape(deadzone = 0.2f).apply(1f, 0f)
        assertEquals(1f, x, eps)
    }

    @Test
    fun `outer edge saturates early and keeps direction`() {
        val s = StickShape(deadzone = 0f, outer = 0.8f)
        val (x, y) = s.apply(0.6f, 0.6f) // radius 0.85 > 0.8
        assertEquals(1f, hypot(x, y), eps)
        assertEquals(x, y, eps)
        val (hx, _) = s.apply(0.4f, 0f)
        assertEquals(0.5f, hx, eps)
    }

    @Test
    fun `anti-deadzone makes the smallest push jump past the game's own deadzone`() {
        val s = StickShape(deadzone = 0.1f, antiDeadzone = 0.25f)
        val (x, _) = s.apply(0.1001f, 0f)
        assertEquals(0.25f, x, 1e-3f)
        val (mid, _) = s.apply(0.55f, 0f) // halfway through the travel
        assertEquals(0.625f, mid, eps)
        val (full, _) = s.apply(1f, 0f)
        assertEquals(1f, full, eps)
    }

    @Test
    fun `measured centre is subtracted before the deadzone`() {
        val s = StickShape(deadzone = 0.05f, centerX = 0.12f, centerY = -0.04f)
        assertEquals(0f to 0f, s.apply(0.12f, -0.04f)) // resting on a drifting centre
        val (x, _) = s.apply(0.13f, -0.04f)
        assertEquals(0f, x, eps) // still within 0.05 of the centre
        val (x2, y2) = s.apply(1f, -0.04f)
        assertTrue(x2 > 0.85f)
        assertEquals(0f, y2, eps)
    }

    @Test
    fun `output never leaves the unit range`() {
        val s = StickShape(deadzone = 0f, outer = 0.55f, centerX = -0.3f)
        val (x, y) = s.apply(1f, 1f)
        assertTrue(x in -1f..1f && y in -1f..1f)
    }

    @Test
    fun `sanitize pulls broken values back into a usable shape`() {
        val s = StickShape(deadzone = 0.9f, outer = 0.1f, antiDeadzone = 3f, centerX = Float.NaN, centerY = 2f).sanitized()
        assertEquals(StickShape.MAX_DEADZONE, s.deadzone, eps)
        assertTrue(s.outer >= s.deadzone + StickShape.MIN_TRAVEL - eps)
        assertEquals(StickShape.MAX_ANTI_DEADZONE, s.antiDeadzone, eps)
        assertEquals(0f, s.centerX, eps)
        assertEquals(StickShape.MAX_CENTER, s.centerY, eps)
    }

    @Test
    fun `NaN input reads as centred`() {
        assertEquals(0f to 0f, StickShape().apply(Float.NaN, 0.5f))
    }
}

class StickCalibrationCodecTest {
    @Test
    fun `encode and decode round trip, names with tabs survive`() {
        val c = StickCalibration(
            "abc123", "Pad\twith tab",
            StickShape(0.1f, 0.9f, 0.2f, 0.01f, -0.02f), StickShape(0.12f, 0.95f, 0f, 0f, 0.03f),
        )
        val back = StickCalibration.decode(c.encode())
        assertNotNull(back)
        assertEquals("Pad with tab", back!!.name)
        assertEquals(c.copy(name = "Pad with tab"), back)
    }

    @Test
    fun `decodeAll skips malformed lines`() {
        val good = StickCalibration("d1", "One").encode()
        val list = StickCalibration.decodeAll("$good\ngarbage\n\nd2\tTwo\t1\t2\n")
        assertEquals(listOf("d1"), list.map { it.descriptor })
    }

    @Test
    fun `decode rejects a blank descriptor and bad numbers`() {
        assertNull(StickCalibration.decode("\tname\t0\t1\t0\t0\t0\t0\t1\t0\t0\t0"))
        assertNull(StickCalibration.decode("d\tname\tx\t1\t0\t0\t0\t0\t1\t0\t0\t0"))
    }
}

class CenterSamplerTest {
    @Test
    fun `needs enough samples`() {
        val s = CenterSampler()
        repeat(CenterSampler.MIN_SAMPLES - 1) { s.add(0f, 0f) }
        assertNull(s.result())
        s.add(0f, 0f)
        assertNotNull(s.result())
    }

    @Test
    fun `a drifting stick gets its centre and a deadzone past the jitter`() {
        val s = CenterSampler()
        repeat(60) { i -> s.add(0.10f + if (i % 2 == 0) 0.01f else -0.01f, -0.05f) }
        val r = s.result()!!
        assertEquals(0.10f, r.centerX, 1e-3f)
        assertEquals(-0.05f, r.centerY, 1e-3f)
        assertEquals(0.01f, r.spread, 1e-3f)
        assertTrue(r.suggestedDeadzone >= r.spread + CenterSampler.MARGIN - 1e-3f)
        assertTrue(!r.moved)
    }

    @Test
    fun `a tiny offset is treated as noise and widens the deadzone instead`() {
        val s = CenterSampler()
        repeat(40) { s.add(0.01f, 0f) }
        val r = s.result()!!
        assertEquals(0f, r.centerX, 0f)
        assertEquals(StickShape.DEFAULT_DEADZONE, r.suggestedDeadzone, 1e-3f)
    }

    @Test
    fun `moving the stick while measuring is flagged`() {
        val s = CenterSampler()
        repeat(30) { i -> s.add(if (i < 15) 0f else 0.8f, 0f) }
        assertTrue(s.result()!!.moved)
    }
}

/** The hook: GamepadMapper reads each pad's calibration through GamepadAxes. */
class CalibrationHookTest {
    private val states = mutableListOf<IntArray>()
    private val out = object : RemoteInput {
        override fun key(event: android.view.KeyEvent) = true
        override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = Unit
        override fun text(text: String) = Unit
        override fun move(dx: Int, dy: Int) = Unit
        override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
        override fun button(button: MouseButton, down: Boolean) = Unit
        override fun scroll(amount: Int) = Unit
        override fun scrollHorizontal(amount: Int) = Unit
        override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
        override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
            states += intArrayOf(lx, ly, rx, ry)
        }
        override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
    }
    private val axes = setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER)
    private val mapper = GamepadMapper(
        input = { out }, onMenu = {},
        traitsOf = { ControllerTraits(it, isVirtual = false, hasSticks = true, hasGamepadButtons = true) },
        axesOf = { GamepadAxes.forAxes(axes) },
        attached = { listOf(7) },
    )

    @After
    fun reset() {
        StickCalibrations.setForTest(emptyList())
        StickCalibrations.descriptorOf = { null }
    }

    private fun move(x: Float, y: Float, rx: Float = 0f) = mapper.onMotion(7, true) {
        when (it) { MotionEvent.AXIS_X -> x; MotionEvent.AXIS_Y -> y; MotionEvent.AXIS_Z -> rx; else -> 0f }
    }

    @Test
    fun `without a calibration the global deadzone applies`() {
        StickCalibrations.setForTest(emptyList())
        move(0.05f, 0f)
        assertEquals(0, states.last()[0])
        move(0.5f, 0f)
        assertEquals((0.5f * 32767).toInt(), states.last()[0])
    }

    @Test
    fun `a saved calibration replaces the deadzone for that pad only`() {
        StickCalibrations.setForTest(listOf(StickCalibration("pad-7", "Test", left = StickShape(deadzone = 0.3f), right = StickShape(deadzone = 0f, outer = 0.5f))))
        StickCalibrations.descriptorOf = { if (it == 7) "pad-7" else null }
        move(0.25f, 0f)
        assertEquals("inside the calibrated 30 % deadzone", 0, states.last()[0])
        move(0f, 0f, rx = 0.5f)
        assertEquals("right stick edge at 50 % saturates", 32767, states.last()[2])
    }

    @Test
    fun `the hook can be replaced per mapper`() {
        mapper.calibrationOf = { StickCalibration("x", "x", left = StickShape(deadzone = 0f, antiDeadzone = 0.5f)) }
        move(0.01f, 0f)
        assertTrue(states.last()[0] >= (0.5f * 32767).toInt() - 1)
    }

    @Test
    fun `y stays host-up with a calibration`() {
        mapper.calibrationOf = { StickCalibration("x", "x", left = StickShape(deadzone = 0f)) }
        move(0f, -1f) // pushed up on Android
        assertEquals(32767, states.last()[1])
    }
}
