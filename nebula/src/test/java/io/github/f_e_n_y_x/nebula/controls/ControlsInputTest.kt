package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder : RemoteInput {
    val events = mutableListOf<String>()
    var buttons = 0
    var lt = 0
    var lx = 0
    var ly = 0
    var arrived = 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { events += "vk $vk ${if (down) "down" else "up"} m$modifiers" }
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) { events += "move $dx $dy" }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) { events += "$button ${if (down) "down" else "up"}" }
    override fun scroll(amount: Int) { events += "scroll $amount" }
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = false
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        this.buttons = buttons; this.lt = lt; this.lx = lx; this.ly = ly
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) { arrived++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ControlsInputTest {
    private val out = Recorder()

    @Test
    fun holdAndToggleButtons() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        val a = newElement(ElementKind.BUTTON, "a")
        input.elementDown(a)
        assertEquals(PadFlags.A, out.buttons)
        assertEquals(1, out.arrived)
        input.elementUp(a)
        assertEquals(0, out.buttons)

        val t = a.copy(id = "t", mode = PressMode.TOGGLE, bindings = listOf(Binding.Pad(PadFlags.B)))
        input.elementDown(t); input.elementUp(t)
        assertEquals(PadFlags.B, out.buttons)
        assertEquals(setOf("t"), input.latched.value)
        input.elementDown(t); input.elementUp(t)
        assertEquals(0, out.buttons)
        assertTrue(input.latched.value.isEmpty())
        assertEquals(1, out.arrived)
    }

    @Test
    fun sharedBindingsReleaseOnlyWhenBothLetGo() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        val ctrl = ControlElement("k", ElementKind.BUTTON, 0f, 0f, 50f, 50f, bindings = listOf(Binding.Key(0xA2)))
        val combo = ControlElement("c", ElementKind.COMBO, 0f, 0f, 50f, 50f, bindings = listOf(Binding.Key(0xA2), Binding.Key(0x43)))
        input.elementDown(ctrl)
        input.elementDown(combo)
        input.elementUp(ctrl)
        assertEquals(listOf("vk 162 down m2", "vk 67 down m2"), out.events)
        input.elementUp(combo)
        assertEquals("vk 162 up m0", out.events.last())
        assertEquals(1, out.events.count { it == "vk 162 up m0" })
    }

    @Test
    fun keyStickAndDpadPressDirections() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        val s = newElement(ElementKind.STICK, "s").copy(stick = StickOutput.KEYS, bindings = listOf(0x57, 0x53, 0x41, 0x44).map { Binding.Key(it) }, deadzone = 0.2f)
        input.stick(s, 0.1f, 0.1f)
        assertTrue(out.events.isEmpty())
        input.stick(s, 0.7f, 0.7f) // up-right
        assertEquals(setOf("vk 87 down m0", "vk 68 down m0"), out.events.toSet())
        out.events.clear()
        input.stick(s, 0.9f, 0f) // right only: W released
        assertEquals(listOf("vk 87 up m0"), out.events)
        input.stick(s, 0f, 0f)
        assertEquals("vk 68 up m0", out.events.last())

        val d = newElement(ElementKind.DPAD, "d")
        input.dpad(d, setOf(0, 2))
        assertEquals(PadFlags.UP or PadFlags.LEFT, out.buttons)
        input.dpad(d, emptySet())
        assertEquals(0, out.buttons)
    }

    @Test
    fun analogStickUsesDeadzoneAndFullRange() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        val s = newElement(ElementKind.STICK, "s").copy(deadzone = 0.1f)
        input.stick(s, 0.05f, 0f)
        assertEquals(0, out.lx)
        input.stick(s, 1f, 0f)
        assertEquals(ControlsInput.AXIS_MAX, out.lx)
        input.stick(s, 0f, -1f)
        assertEquals(-ControlsInput.AXIS_MAX, out.ly)
        input.click(s)
        assertEquals(0, out.buttons) // L3 pressed and released
    }

    @Test
    fun macroPlaysStepsInOrder() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        val m = newElement(ElementKind.MACRO, "m").copy(steps = listOf(MacroStep(Binding.Key(0x51), 50, 20), MacroStep(Binding.Mouse(MouseKey.LEFT), 30, 0)))
        input.elementDown(m)
        input.elementDown(m) // a second tap while playing is ignored
        advanceTimeBy(10)
        assertEquals(listOf("vk 81 down m0"), out.events)
        advanceTimeBy(1000) // macros run in backgroundScope, which advanceUntilIdle skips
        assertEquals(listOf("vk 81 down m0", "vk 81 up m0", "LEFT down", "LEFT up"), out.events)
    }

    @Test
    fun releaseAllLetsGoOfEverything() = runTest {
        val input = ControlsInput({ out }, backgroundScope)
        input.elementDown(newElement(ElementKind.TRIGGER, "lt").copy(bindings = listOf(Binding.Trigger(Side.LEFT))))
        input.elementDown(ControlElement("k", ElementKind.BUTTON, 0f, 0f, 50f, 50f, mode = PressMode.TOGGLE, bindings = listOf(Binding.Key(0x20))))
        assertEquals(255, out.lt)
        input.releaseAll()
        assertEquals(0, out.lt)
        assertEquals("vk 32 up m0", out.events.last())
        assertTrue(input.latched.value.isEmpty())
    }
}
