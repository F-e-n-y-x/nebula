package io.github.f_e_n_y_x.nebula.data.engine

import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Records what reaches the host. */
private class PadRecorder : RemoteInput {
    data class State(val controller: Int, val mask: Int, val buttons: Int, val lt: Int, val rt: Int, val lx: Int, val ly: Int, val rx: Int, val ry: Int)

    val arrivals = mutableListOf<Int>()
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
    private var osc = false
    private val devices = mutableMapOf<Int, ControllerTraits>()
    private val layouts = mutableMapOf<Int, Set<Int>>()
    private val mapper = GamepadMapper(
        input = { out },
        onMenu = {},
        oscSlot = { osc },
        traitsOf = { devices[it] },
        axesOf = { GamepadAxes.forAxes(layouts[it] ?: emptySet()) },
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

    @Test
    fun `with the on-screen controls kept, pads start at player 2`() {
        osc = true
        pad(7, androidXbox)
        press(7, KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(listOf(1), out.arrivals)
        assertEquals(3, out.last.mask)
    }
}
