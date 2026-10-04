package io.github.f_e_n_y_x.nebula.data.engine

import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what reaches the host. */
private class PadRecorder : RemoteInput {
    data class State(val controller: Int, val mask: Int, val buttons: Int, val lt: Int, val rt: Int, val lx: Int, val ly: Int, val rx: Int, val ry: Int)

    val arrivals = mutableListOf<Int>()
    val arrivalCaps = mutableListOf<Int>()
    val arrivalTypes = mutableListOf<Byte>()
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
        arrivals += controller
        arrivalCaps += capabilities.toInt()
        arrivalTypes += type
    }
}

class GamepadAxesTest {
    private val xboxAndroid = setOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )
    private val xpadStyle = setOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )

    @Test
    fun `standard Android pads use Z and RZ for the right stick and LTRIGGER and RTRIGGER`() {
        val a = GamepadAxes.forAxes(xboxAndroid)
        assertEquals(MotionEvent.AXIS_X, a.leftX)
        assertEquals(MotionEvent.AXIS_Y, a.leftY)
        assertEquals(MotionEvent.AXIS_Z, a.rightX)
        assertEquals(MotionEvent.AXIS_RZ, a.rightY)
        assertEquals(MotionEvent.AXIS_LTRIGGER, a.leftTrigger)
        assertEquals(MotionEvent.AXIS_RTRIGGER, a.rightTrigger)
    }

    @Test
    fun `brake and gas are triggers when there are no trigger axes`() {
        val a = GamepadAxes.forAxes(setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS))
        assertEquals(MotionEvent.AXIS_BRAKE, a.leftTrigger)
        assertEquals(MotionEvent.AXIS_GAS, a.rightTrigger)
        assertEquals(MotionEvent.AXIS_Z, a.rightX)
    }

    @Test
    fun `xpad style pads use RX and RY for the right stick and Z and RZ as idle-negative triggers`() {
        val a = GamepadAxes.forAxes(xpadStyle)
        assertEquals(MotionEvent.AXIS_RX, a.rightX)
        assertEquals(MotionEvent.AXIS_RY, a.rightY)
        assertEquals(MotionEvent.AXIS_Z, a.leftTrigger)
        assertEquals(MotionEvent.AXIS_RZ, a.rightTrigger)
        assertTrue(a.triggersIdleNegative)
        // Released triggers read -1 on these axes and must not count as pressed.
        val released = a.read({ if (it == MotionEvent.AXIS_Z || it == MotionEvent.AXIS_RZ) -1f else 0f }, 0.07f)
        assertEquals(0, released.lt)
        assertEquals(0, released.rt)
    }

    @Test
    fun `old DualShock 4 driver puts the triggers on RX and RY`() {
        val a = GamepadAxes.forAxes(xpadStyle, nonStandardDualShock4 = true)
        assertEquals(MotionEvent.AXIS_RX, a.leftTrigger)
        assertEquals(MotionEvent.AXIS_RY, a.rightTrigger)
        assertEquals(MotionEvent.AXIS_Z, a.rightX)
        assertEquals(MotionEvent.AXIS_RZ, a.rightY)
    }

    @Test
    fun `radial deadzone keeps a pure horizontal push`() {
        val a = GamepadAxes.forAxes(xboxAndroid)
        val v = a.read({ if (it == MotionEvent.AXIS_X) -1f else 0f }, 0.07f)
        assertEquals(-32767, v.lx)
        assertEquals(0, v.ly)
        val inside = a.read({ if (it == MotionEvent.AXIS_X) 0.05f else if (it == MotionEvent.AXIS_Y) 0.04f else 0f }, 0.07f)
        assertEquals(0, inside.lx)
    }
}

class GamepadMapperTest {
    private val out = PadRecorder()
    private val devices = mutableMapOf<Int, ControllerTraits>()
    private val layouts = mutableMapOf<Int, Set<Int>>()
    /** Devices unplugged in the test (still known by traits until removed). */
    private val unplugged = mutableSetOf<Int>()
    private var caps: (Int, Int) -> Int = { _, _ -> GamepadMapper.DEFAULT_CAPS }
    private var hook: (Int, Boolean) -> Boolean = { _, _ -> false }
    private val mapper = GamepadMapper(
        input = { out },
        onMenu = {},
        traitsOf = { devices[it] },
        axesOf = { GamepadAxes.forAxes(layouts[it] ?: emptySet()) },
        capabilities = { id, index -> caps(id, index) },
        attached = { devices.filter { (id, t) -> id !in unplugged && t.isController && t.hasSticks }.keys.sorted() },
        keyHook = { k, d -> hook(k, d) },
    )

    private fun pad(id: Int, axes: Set<Int>, vendor: Int = 0x045e, product: Int = 0x0b13) {
        devices[id] = ControllerTraits(id, isVirtual = false, hasSticks = true, hasGamepadButtons = true, vendorId = vendor, productId = product)
        layouts[id] = axes
    }

    private fun move(id: Int, vararg values: Pair<Int, Float>) {
        val m = values.toMap()
        assertTrue(mapper.onMotion(id, devices[id]?.let { it.isController && it.hasSticks } == true) { m[it] ?: restValue(id, it) })
    }

    /** Where an axis rests: idle-negative trigger axes sit at -1. */
    private fun restValue(id: Int, axis: Int): Float {
        val l = GamepadAxes.forAxes(layouts[id] ?: emptySet())
        return if (l.triggersIdleNegative && (axis == l.leftTrigger || axis == l.rightTrigger)) -1f else 0f
    }

    private fun press(id: Int, key: Int, down: Boolean = true) =
        mapper.onKey(id, key, if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, devices[id]?.isController == true)

    private val androidXbox = setOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )
    private val xpad = setOf(
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RX, MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
    )

    /** Each physical control moved alone must move exactly one host field, the right one. */
    private fun assertEachAxisMovesOnlyItsField(layout: Set<Int>, controls: List<Triple<String, Pair<Int, Float>, (PadRecorder.State) -> Int>>) {
        pad(1, layout)
        val fields: List<Pair<String, (PadRecorder.State) -> Int>> = listOf(
            "lt" to { s -> s.lt }, "rt" to { s -> s.rt }, "lx" to { s -> s.lx }, "ly" to { s -> s.ly }, "rx" to { s -> s.rx }, "ry" to { s -> s.ry },
        )
        for ((name, axis, expected) in controls) {
            move(1, axis)
            val s = out.last
            val moved = fields.filter { (_, get) -> get(s) != 0 }.map { it.first }
            assertEquals("$name moved $moved", 1, moved.size)
            assertTrue("$name drove the wrong field ($moved)", expected(s) != 0)
        }
    }

    @Test
    fun `every axis of an Android Xbox layout moves only its own host field`() {
        assertEachAxisMovesOnlyItsField(
            androidXbox,
            listOf(
                Triple("left stick X", MotionEvent.AXIS_X to 1f) { s -> s.lx },
                Triple("left stick Y", MotionEvent.AXIS_Y to -1f) { s -> s.ly },
                Triple("right stick X", MotionEvent.AXIS_Z to 1f) { s -> s.rx },
                Triple("right stick Y", MotionEvent.AXIS_RZ to 1f) { s -> s.ry },
                Triple("left trigger", MotionEvent.AXIS_LTRIGGER to 1f) { s -> s.lt },
                Triple("right trigger", MotionEvent.AXIS_RTRIGGER to 1f) { s -> s.rt },
            ),
        )
        move(1, MotionEvent.AXIS_X to -1f)
        assertEquals(-32767, out.last.lx)
        move(1, MotionEvent.AXIS_Y to -1f)
        assertEquals("up on the stick is positive on the host", 32767, out.last.ly)
    }

    @Test
    fun `every axis of an xpad style layout moves only its own host field`() {
        assertEachAxisMovesOnlyItsField(
            xpad,
            listOf(
                Triple("left stick X", MotionEvent.AXIS_X to 1f) { s -> s.lx },
                Triple("left stick Y", MotionEvent.AXIS_Y to 1f) { s -> s.ly },
                Triple("right stick X", MotionEvent.AXIS_RX to 1f) { s -> s.rx },
                Triple("right stick Y", MotionEvent.AXIS_RY to 1f) { s -> s.ry },
                Triple("left trigger", MotionEvent.AXIS_Z to 1f) { s -> s.lt },
                Triple("right trigger", MotionEvent.AXIS_RZ to 1f) { s -> s.rt },
            ),
        )
    }

    @Test
    fun `digital L2 and R2 keys survive stick motion on pads without trigger axes`() {
        pad(1, setOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ))
        assertTrue(press(1, KeyEvent.KEYCODE_BUTTON_R2))
        move(1, MotionEvent.AXIS_X to 0.5f)
        assertEquals(255, out.last.rt)
        assertEquals(0, out.last.lt)
    }

    @Test
    fun `one physical pad is one host controller in slot 0`() {
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        move(7, MotionEvent.AXIS_X to 1f)
        assertEquals(listOf(0), out.arrivals)
        assertEquals(0, out.last.controller)
        assertEquals(1, out.last.mask)
        assertEquals(1, mapper.count)
    }

    @Test
    fun `unmapped keys and virtual devices never reserve a slot`() {
        devices[3] = ControllerTraits(3, isVirtual = false, hasSticks = false, hasGamepadButtons = true)
        devices[-1] = ControllerTraits(-1, isVirtual = true, hasSticks = false, hasGamepadButtons = true)
        assertEquals(false, press(3, KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(false, press(-1, KeyEvent.KEYCODE_BUTTON_A))
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals("the real pad still gets player 1", listOf(0), out.arrivals)
    }

    @Test
    fun `a buttons-only companion node joins its pad instead of adding a phantom player`() {
        pad(7, androidXbox)
        devices[8] = ControllerTraits(8, isVirtual = false, hasSticks = false, hasGamepadButtons = true, vendorId = 0x045e, productId = 0x0b13)
        move(7, MotionEvent.AXIS_X to 1f)
        assertTrue(press(8, KeyEvent.KEYCODE_BUTTON_START))
        assertEquals(listOf(0), out.arrivals)
        assertEquals(0, out.last.controller)
        assertTrue(out.last.buttons and ControllerPacket.PLAY_FLAG != 0)
        assertEquals(1, mapper.count)
        // Losing the companion node doesn't unplug the pad.
        val before = out.states.size
        mapper.onDeviceRemoved(8)
        assertEquals(before, out.states.size)
        assertEquals(1, mapper.count)
    }

    @Test
    fun `two real pads are two players`() {
        pad(7, androidXbox)
        pad(9, androidXbox, product = 0x0b12)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        press(9, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf(0, 1), out.arrivals)
        assertEquals(3, out.last.mask)
    }

    // ---- Regression: exactly one host controller per physical controller (0.3.0-dev9.1 showed two) ----

    @Test
    fun `a controller plus the on-screen controls is one host controller`() {
        pad(7, androidXbox)
        mapper.onScreenState(ControllerPacket.A_FLAG, 0, 0, 0, 0, 0, 0)
        press(7, KeyEvent.KEYCODE_BUTTON_B)
        mapper.onScreenReleased()
        assertEquals(listOf(0), out.arrivals)
        assertTrue(out.states.all { it.controller == 0 && it.mask == 1 })
        assertEquals(1, mapper.count)
    }

    @Test
    fun `phone gyro to right stick with a controller attached drives that controller`() {
        pad(7, androidXbox)
        mapper.gyroStick(null, 12000, -3000)
        move(7, MotionEvent.AXIS_X to 1f)
        assertEquals(listOf(0), out.arrivals)
        assertEquals(1, mapper.count)
        // Gyro stays on the right stick while the pad moves, and adds to the physical stick with a clamp.
        assertEquals(12000, out.last.rx)
        assertEquals(-3000, out.last.ry)
        move(7, MotionEvent.AXIS_Z to 1f)
        assertEquals(32767, out.last.rx)
        mapper.gyroStick(null, -12000, 0)
        assertEquals(32767 - 12000, out.last.rx)
    }

    @Test
    fun `the phone pad is player 1 only without a controller, and the first controller takes it over`() {
        mapper.onScreenState(ControllerPacket.A_FLAG, 0, 0, 0, 0, 0, 0)
        assertEquals(listOf(0), out.arrivals)
        assertTrue(mapper.hasPhonePad)
        assertEquals(null, mapper.deviceIdFor(0))
        // A controller connects mid-stream and is used: same player, no second arrival.
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf(0), out.arrivals)
        assertEquals(7, mapper.deviceIdFor(0))
        assertEquals(1, mapper.count)
        assertTrue(out.states.all { it.controller == 0 && it.mask == 1 })
    }

    @Test
    fun `a controller taking over the phone pad is re-announced when it needs other capabilities`() {
        val gyroCaps = GamepadMapper.DEFAULT_CAPS or com.limelight.nvstream.jni.MoonBridge.LI_CCAP_GYRO.toInt()
        caps = { id, _ -> if (id == GamepadMapper.PHONE_PAD) GamepadMapper.DEFAULT_CAPS else gyroCaps }
        mapper.announcePlayerOne()
        assertEquals(listOf(0), out.arrivals)
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        // Released (bit cleared) and announced again with the pad's gyro, still as player 1.
        assertTrue(out.states.any { it.controller == 0 && it.mask and 1 == 0 })
        assertEquals(listOf(0, 0), out.arrivals)
        assertEquals(gyroCaps, out.arrivalCaps.last())
        assertEquals(1, out.last.mask)
    }

    @Test
    fun `on-screen controls coming up mid-stream announce player 1 before the first press`() {
        mapper.announcePlayerOne()
        assertEquals(listOf(0), out.arrivals)
        assertTrue(mapper.hasPhonePad)
        // Shown again (menu closed, toggled off and on): nothing new for the host.
        mapper.announcePlayerOne()
        assertEquals(listOf(0), out.arrivals)
        mapper.onScreenState(ControllerPacket.A_FLAG, 0, 0, 0, 0, 0, 0)
        assertEquals(listOf(0), out.arrivals)
        assertEquals(ControllerPacket.A_FLAG, out.last.buttons)
        assertEquals(0, out.last.controller)
    }

    @Test
    fun `a new connection announces the pads again with their next input`() {
        mapper.onScreenState(ControllerPacket.A_FLAG, 0, 0, 0, 0, 0, 0)
        mapper.onScreenReleased()
        assertEquals(listOf(0), out.arrivals)
        mapper.newConnection()
        // Nothing is sent for a pad the new host session doesn't know yet.
        val before = out.states.size
        mapper.releaseAll()
        assertEquals(before, out.states.size)
        mapper.onScreenState(ControllerPacket.B_FLAG, 0, 0, 0, 0, 0, 0)
        assertEquals("announced again, same player", listOf(0, 0), out.arrivals)
        assertEquals(ControllerPacket.B_FLAG, out.last.buttons)
        assertEquals(0, out.last.controller)
        // Controls shown on the new connection announce it before any press.
        mapper.newConnection()
        mapper.announcePlayerOne()
        assertEquals(listOf(0, 0, 0), out.arrivals)
    }

    @Test
    fun `no phone pad is announced while a controller is attached`() {
        pad(7, androidXbox)
        mapper.announcePlayerOne()
        mapper.gyroStick(null, 0, 0)
        assertTrue(out.arrivals.isEmpty())
        assertFalse(mapper.hasPhonePad)
    }

    @Test
    fun `a companion node that speaks before its sticks joins the same pad`() {
        pad(7, androidXbox)
        devices[8] = ControllerTraits(8, isVirtual = false, hasSticks = false, hasGamepadButtons = true, vendorId = 0x045e, productId = 0x0b13)
        // The buttons-only node is pressed first (e.g. a button both nodes report).
        assertTrue(press(8, KeyEvent.KEYCODE_BUTTON_MODE))
        move(7, MotionEvent.AXIS_X to 1f)
        assertEquals(listOf(0), out.arrivals)
        assertEquals(1, mapper.count)
        assertEquals(7, mapper.deviceIdFor(0))
    }

    @Test
    fun `gyro zero never announces a pad and the toggle button is kept from the host`() {
        mapper.gyroStick(null, 0, 0)
        assertTrue(out.arrivals.isEmpty())
        hook = { k, _ -> k == KeyEvent.KEYCODE_BUTTON_THUMBR }
        pad(7, androidXbox)
        assertTrue(press(7, KeyEvent.KEYCODE_BUTTON_THUMBR))
        assertTrue("the toggle press reached the host", out.states.isEmpty())
        press(7, KeyEvent.KEYCODE_BUTTON_THUMBL)
        assertEquals(ControllerPacket.LS_CLK_FLAG, out.last.buttons)
    }

    @Test
    fun `unplugging the only controller frees its slot on the host`() {
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        unplugged += 7
        mapper.onDeviceRemoved(7)
        assertEquals(0, out.last.mask)
        assertEquals(0, mapper.count)
        // The on-screen controls then become the phone pad in slot 0 again.
        mapper.onScreenState(ControllerPacket.A_FLAG, 0, 0, 0, 0, 0, 0)
        assertEquals(listOf(0, 0), out.arrivals)
        assertTrue(mapper.hasPhonePad)
    }

    @Test
    fun `an xpad trigger that has not reported yet is released, not half pressed`() {
        pad(1, xpad)
        // Android reports 0.0 for Z/RZ before their first event: not 50 %.
        assertTrue(mapper.onMotion(1, true) { a -> if (a == MotionEvent.AXIS_X) 0.5f else 0f })
        assertEquals(0, out.last.lt)
        assertEquals(0, out.last.rt)
        // Once RZ has moved it is read normally, 0.0 included.
        mapper.onMotion(1, true) { a -> if (a == MotionEvent.AXIS_RZ) 1f else if (a == MotionEvent.AXIS_Z) -1f else 0f }
        assertEquals(255, out.last.rt)
        mapper.onMotion(1, true) { a -> if (a == MotionEvent.AXIS_RZ) 0f else if (a == MotionEvent.AXIS_Z) -1f else 0f }
        assertTrue("half-pressed RT now reads as half: ${out.last.rt}", out.last.rt in 120..135)
        assertEquals(0, out.last.lt)
    }

    @Test
    fun `pads are announced as Xbox pads unless motion is in use`() {
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(com.limelight.nvstream.jni.MoonBridge.LI_CTYPE_XBOX, out.arrivalTypes.last())
        caps = { _, _ -> GamepadMapper.DEFAULT_CAPS or com.limelight.nvstream.jni.MoonBridge.LI_CCAP_GYRO.toInt() }
        pad(9, androidXbox, product = 0x0b12)
        press(9, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(com.limelight.nvstream.jni.MoonBridge.LI_CTYPE_UNKNOWN, out.arrivalTypes.last())
    }

    @Test
    fun `on-screen RT merges with a physical pad's trigger by maximum`() {
        pad(7, androidXbox)
        mapper.onMotion(7, true) { if (it == MotionEvent.AXIS_RTRIGGER) 0.5f else 0f }
        val half = out.last.rt
        mapper.onScreenState(0, 0, 255, 0, 0, 0, 0)
        assertEquals(255, out.last.rt)
        mapper.onScreenState(0, 0, 0, 0, 0, 0, 0)
        assertEquals(half, out.last.rt)
        assertEquals(1, out.arrivals.size)
    }
}
