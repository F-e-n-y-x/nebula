package io.github.f_e_n_y_x.nebula.data.engine

import android.view.KeyEvent
import android.view.MotionEvent
import io.github.f_e_n_y_x.nebula.controls.Binding
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.OnScreenPad
import io.github.f_e_n_y_x.nebula.controls.PadFlags
import io.github.f_e_n_y_x.nebula.controls.PadMixer
import io.github.f_e_n_y_x.nebula.controls.Side
import io.github.f_e_n_y_x.nebula.controls.StickOutput
import io.github.f_e_n_y_x.nebula.input.GyroAim
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What reaches the PC, after the whole StreamScreen chain: ControlsInput → GamepadMapper → PadMixer. */
private class Wire : RemoteInput {
    data class Arrival(val controller: Int, val mask: Int)
    data class State(val controller: Int, val mask: Int, val buttons: Int, val lt: Int, val rt: Int, val lx: Int, val ly: Int, val rx: Int, val ry: Int) {
        val atRest: Boolean get() = buttons == 0 && lt == 0 && rt == 0 && lx == 0 && ly == 0 && rx == 0 && ry == 0
    }
    val arrivals = mutableListOf<Arrival>()
    val states = mutableListOf<State>()
    val last: State get() = states.last()

    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = Unit
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) = Unit
    override fun scroll(amount: Int) = Unit
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        states += State(controller, activeMask, buttons, lt, rt, lx, ly, rx, ry)
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) {
        arrivals += Arrival(controller, activeMask)
    }
}

/**
 * End to end through the same objects StreamScreen wires together (0.3.0-dev10 regression: two
 * pads on the PC with only the on-screen controls and phone gyro to right stick).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PadPathTest {
    private val wire = Wire()
    private val mixer = PadMixer { wire }
    private val devices = mutableMapOf<Int, ControllerTraits>()
    private val mapper = GamepadMapper(
        input = { mixer },
        onMenu = {},
        traitsOf = { devices[it] },
        axesOf = { GamepadAxes.forAxes(if (devices[it]?.hasSticks == true) XBOX else emptySet()) },
        attached = { devices.filter { (_, t) -> t.isController && t.hasSticks }.keys.sorted() },
    )
    private val osc = object : OnScreenPad {
        override fun state(buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int): Boolean {
            mapper.onScreenState(buttons, lt, rt, lx, ly, rx, ry); return true
        }
        override fun released(): Boolean { mapper.onScreenReleased(); return true }
    }

    private fun button(id: String, b: Binding, kind: ElementKind = ElementKind.BUTTON) =
        ControlElement(id, kind, 0.5f, 0.5f, 60f, 60f, bindings = listOf(b))
    private fun stick(id: String, side: StickOutput) =
        ControlElement(id, ElementKind.STICK, 0.2f, 0.7f, 120f, 120f, stick = side, deadzone = 0f)

    private fun physical(id: Int) {
        devices[id] = ControllerTraits(id, isVirtual = false, hasSticks = true, hasGamepadButtons = true, vendorId = 0x045e, productId = 0x0b13)
    }

    /** A buttons-only phone node (keys from SOURCE_GAMEPAD, no sticks), as some phones expose. */
    private fun phoneButtonsNode(id: Int) {
        devices[id] = ControllerTraits(id, isVirtual = false, hasSticks = false, hasGamepadButtons = true, vendorId = 0x04e8, productId = 0x0001)
    }

    private fun press(id: Int, key: Int, down: Boolean = true) =
        mapper.onKey(id, key, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, devices[id]?.isController == true)

    @Test
    fun `on-screen controls only - one pad, every control reaches it, rest is all zero, gyro merges`() = runTest {
        val input = ControlsInput({ wire }, backgroundScope, pad = { osc })
        val a = button("a", Binding.Pad(PadFlags.A))
        input.elementDown(a)
        assertEquals(listOf(Wire.Arrival(0, 1)), wire.arrivals)
        assertEquals(Wire.State(0, 1, PadFlags.A, 0, 0, 0, 0, 0, 0), wire.last)
        input.elementUp(a)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 1); runCurrent()
        assertTrue(wire.last.atRest)

        val left = stick("ls", StickOutput.LEFT)
        val right = stick("rs", StickOutput.RIGHT)
        input.stick(left, 1f, 0f)
        assertEquals(ControlsInput.AXIS_MAX, wire.last.lx)
        input.stick(left, 0f, 1f)
        assertEquals(0, wire.last.lx); assertEquals(ControlsInput.AXIS_MAX, wire.last.ly)
        input.stick(left, 0f, 0f)
        input.stick(right, -1f, 0f)
        assertEquals(-ControlsInput.AXIS_MAX, wire.last.rx)
        input.stick(right, 0f, -1f)
        assertEquals(0, wire.last.rx); assertEquals(-ControlsInput.AXIS_MAX, wire.last.ry)
        input.stick(right, 0f, 0f)
        assertTrue(wire.last.atRest)

        val lt = button("lt", Binding.Trigger(Side.LEFT), ElementKind.TRIGGER)
        val rt = button("rt", Binding.Trigger(Side.RIGHT), ElementKind.TRIGGER)
        input.elementDown(lt)
        assertEquals(255, wire.last.lt); assertEquals(0, wire.last.rt)
        input.elementUp(lt); advanceTimeBy(ControlsInput.MIN_HOLD_MS + 1); runCurrent(); input.elementDown(rt)
        assertEquals(0, wire.last.lt); assertEquals(255, wire.last.rt)
        input.elementUp(rt)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 1); runCurrent()
        assertTrue(wire.last.atRest)

        // Phone gyro to right stick lands on the same controller and adds to the on-screen stick.
        mapper.gyroStick(null, 5000, -2000)
        assertEquals(Wire.State(0, 1, 0, 0, 0, 0, 0, 5000, -2000), wire.last)
        input.stick(right, 0.5f, 0f)
        assertEquals((0.5f * ControlsInput.AXIS_MAX).toInt() + 5000, wire.last.rx)
        mapper.gyroStick(null, 0, 0)
        input.releaseAll()
        assertTrue(wire.last.atRest)
        assertEquals("one pad for the on-screen controls plus gyro", 1, wire.arrivals.size)
        assertTrue(wire.states.all { it.controller == 0 && it.mask == 1 })
    }

    @Test
    fun `gyro first, then the on-screen controls - still one pad`() = runTest {
        mapper.gyroStick(null, 3000, 0)
        val input = ControlsInput({ wire }, backgroundScope, pad = { osc })
        input.elementDown(button("a", Binding.Pad(PadFlags.A)))
        assertEquals(listOf(Wire.Arrival(0, 1)), wire.arrivals)
        assertEquals(Wire.State(0, 1, PadFlags.A, 0, 0, 0, 0, 3000, 0), wire.last)
    }

    @Test
    fun `a buttons-only phone node never becomes a second pad next to the on-screen controls and gyro`() = runTest {
        // dev10: this node's key at connect took slot 0, then gyro made the phone pad slot 1.
        phoneButtonsNode(3)
        assertTrue(press(3, KeyEvent.KEYCODE_BACK))
        press(3, KeyEvent.KEYCODE_BACK, down = false)
        mapper.gyroStick(null, 4000, 0)
        val input = ControlsInput({ wire }, backgroundScope, pad = { osc })
        input.elementDown(button("a", Binding.Pad(PadFlags.A)))
        assertEquals(1, wire.arrivals.size)
        assertTrue(wire.states.all { it.controller == 0 })

        // The other order: phone pad first, then the node.
        val wire2 = Wire()
        val m2 = GamepadMapper(input = { wire2 }, onMenu = {}, traitsOf = { devices[it] }, attached = { emptyList() })
        m2.gyroStick(null, 4000, 0)
        m2.onKey(3, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, true)
        assertEquals(1, wire2.arrivals.size)
        assertEquals(1, m2.count)
    }

    @Test
    fun `physical pad with on-screen controls hidden and gyro on - one pad, same controller, gyro merges`() {
        physical(7)
        mapper.gyroStick(null, 6000, 0)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        mapper.onMotion(7, true) { if (it == MotionEvent.AXIS_X) 1f else 0f }
        assertEquals(listOf(Wire.Arrival(0, 1)), wire.arrivals)
        assertEquals(Wire.State(0, 1, PadFlags.A, 0, 0, 32767, 0, 6000, 0), wire.last)
        press(7, KeyEvent.KEYCODE_BUTTON_A, down = false)
        mapper.onMotion(7, true) { 0f }
        mapper.gyroStick(null, 0, 0)
        assertTrue("controller at rest, nothing touched", wire.last.atRest)
    }

    @Test
    fun `on-screen controls kept with a controller - still one pad`() = runTest {
        physical(7)
        val input = ControlsInput({ wire }, backgroundScope, pad = { osc })
        input.elementDown(button("b", Binding.Pad(PadFlags.B)))
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(PadFlags.A or PadFlags.B, wire.last.buttons)
        input.releaseAll()
        press(7, KeyEvent.KEYCODE_BUTTON_A, down = false)
        assertEquals(1, wire.arrivals.size)
        assertTrue(wire.last.atRest)
    }

    @Test
    fun `gyro to right stick returns to exactly zero at rest, even with smoothing and a stick minimum`() {
        val aim = GyroAim()
        val s = MotionSettings(mode = GyroMode.RIGHT_STICK, smoothing = 60, stickMinimum = 20, deadzone = 1)
        repeat(20) { aim.stick(40f, 0f, 0f, s) }
        var last = 1 to 1
        repeat(400) { last = aim.stick(0.2f, 0.1f, 0f, s) }
        assertEquals(0 to 0, last)
    }

    /** What handleZone does for a "Camera → stick" zone: drag samples through CameraStick into setStick, then 0 on lift. */
    private fun dragCameraZone(input: ControlsInput, zone: ControlElement) {
        val side = if (zone.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT
        val cam = io.github.f_e_n_y_x.nebula.controls.CameraStick(zone.sensitivity, zone.acceleration, zone.antiDeadzone, zone.invertY)
        var t = 0L
        repeat(10) {
            t += 16
            cam.add(12f, -6f, t)
            val (x, y) = cam.tick(t)
            input.setStick(side, x, y)
        }
    }

    private fun lift(input: ControlsInput, zone: ControlElement) =
        input.setStick(if (zone.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT, 0f, 0f)

    /** The owner's zone: type "Camera → stick", target "Right stick". */
    private val cameraZone = io.github.f_e_n_y_x.nebula.controls.newElement(ElementKind.ZONE, "camera")
        .copy(zone = io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_STICK, stick = StickOutput.RIGHT)

    @Test
    fun `camera zone with only the on-screen controls moves RX and RY of the one pad`() = runTest {
        val input = ControlsInput({ wire }, backgroundScope, pad = { osc })
        dragCameraZone(input, cameraZone)
        assertEquals(listOf(Wire.Arrival(0, 1)), wire.arrivals)
        assertTrue("drag right moved rx=${wire.last.rx}", wire.last.rx > 0)
        assertTrue("drag up moved ry=${wire.last.ry}", wire.last.ry > 0)
        assertEquals(0, wire.last.lx); assertEquals(0, wire.last.ly)
        lift(input, cameraZone)
        assertTrue(wire.last.atRest)
    }

    @Test
    fun `camera zone in keep-with-controller mode still works when no pad has reported`() = runTest {
        // The screen thought a controller was attached (zones only, mixer on) but none has spoken.
        val input = ControlsInput({ wire }, backgroundScope, mixer = { mixer }, pad = { osc })
        dragCameraZone(input, cameraZone)
        assertEquals(1, wire.arrivals.size)
        assertTrue(wire.last.rx > 0)
        lift(input, cameraZone)
        assertTrue(wire.last.atRest)
    }

    @Test
    fun `camera zone steers the physical pad's right stick when one is in hand`() = runTest {
        physical(7)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        val input = ControlsInput({ wire }, backgroundScope, mixer = { mixer }, pad = { osc })
        dragCameraZone(input, cameraZone)
        assertEquals(1, wire.arrivals.size)
        assertEquals(PadFlags.A, wire.last.buttons)
        assertTrue(wire.last.rx > 0)
        lift(input, cameraZone)
        press(7, KeyEvent.KEYCODE_BUTTON_A, down = false)
        assertTrue(wire.last.atRest)
    }

    private companion object {
        val XBOX = setOf(
            MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
        )
    }
}
