package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.input.Finger
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Everything that reaches the PC. */
private class Pc : RemoteInput {
    data class Pad(val buttons: Int, val lt: Int, val rt: Int, val lx: Int, val ly: Int, val rx: Int, val ry: Int) {
        val atRest get() = buttons == 0 && lt == 0 && rt == 0 && lx == 0 && ly == 0 && rx == 0 && ry == 0
    }
    val pads = mutableListOf<Pad>()
    val pad: Pad get() = pads.lastOrNull() ?: Pad(0, 0, 0, 0, 0, 0, 0)
    var mouseX = 0
    var mouseY = 0
    var clicks = 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = Unit
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) { mouseX += dx; mouseY += dy }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) { clicks++ }
    override fun scroll(amount: Int) = Unit
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        pads += Pad(buttons, lt, rt, lx, ly, rx, ry)
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

/** The stream's trackpad layer: records what fingers it was given. */
private class Trackpad : BackgroundTouches {
    val events = mutableListOf<String>()
    var maxFingers = 0
    val seen = mutableSetOf<Int>()
    override fun down(f: Finger, t: Long) { events += "down ${f.id}"; seen += f.id; maxFingers = maxOf(maxFingers, 1) }
    override fun pointerDown(f: Finger, all: List<Finger>, t: Long) { events += "pointerDown ${f.id}"; seen += all.map { it.id }; maxFingers = maxOf(maxFingers, all.size) }
    override fun move(all: List<Finger>, t: Long) { events += "move ${all.map { it.id }}"; seen += all.map { it.id }; maxFingers = maxOf(maxFingers, all.size) }
    override fun pointerUp(f: Finger, remaining: List<Finger>, t: Long) { events += "pointerUp ${f.id}" }
    override fun up(f: Finger, t: Long) { events += "up ${f.id}" }
    override fun cancel() { events += "cancel" }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TouchRouterTest {
    // A 1000 × 500 px area at density 1: left stick bottom left, RT top right, A button, camera zone right half.
    private val area = RRect(0f, 0f, 1000f, 500f)
    private val ls = ControlElement("ls", ElementKind.STICK, 0.2f, 0.7f, 120f, 120f, stick = StickOutput.LEFT, deadzone = 0f)
    private val rt = ControlElement("rt", ElementKind.TRIGGER, 0.9f, 0.12f, 68f, 42f, bindings = listOf(Binding.Trigger(Side.RIGHT)))
    private val a = ControlElement("a", ElementKind.BUTTON, 0.88f, 0.75f, 52f, 52f, bindings = listOf(Binding.Pad(PadFlags.A)))
    private val zone = newElement(ElementKind.ZONE, "cam").copy(zone = ZoneType.CAMERA_MOUSE, x = 0.75f, y = 0.4f, width = 0.5f, height = 0.4f)

    private class Rig(scope: TestScope, background: OutsideTouch, look: LookOutput, elements: List<ControlElement>, area: RRect) {
        val pc = Pc()
        val pad = Trackpad()
        val input = ControlsInput({ pc }, scope.backgroundScope)
        val router = TouchRouter(input, background = { pad }).apply { layout = RouterLayout.of(elements, area, 1f, background, look) }
    }

    private fun TestScope.rig(background: OutsideTouch = OutsideTouch.TRACKPAD, look: LookOutput = LookOutput.MOUSE, elements: List<ControlElement> = listOf(zone, ls, rt, a)) =
        Rig(this, background, look, elements, area)

    private fun centre(e: ControlElement) = RouterLayout.place(e, area, 1f).let { it.cx to it.cy }

    @Test
    fun `a background finger next to the stick is the trackpad's only finger`() = runTest {
        val r = rig()
        val (sx, sy) = centre(ls)
        r.router.down(0, sx, sy, 0)
        r.router.down(1, 500f, 20f, 1) // top middle: nothing there
        r.router.move(listOf(Finger(0, sx - 60f, sy), Finger(1, 520f, 30f)), 16)
        assertEquals(listOf("down 1", "move [1]"), r.pad.events)
        assertEquals(setOf(1), r.pad.seen)
        assertEquals(-ControlsInput.AXIS_MAX, r.pc.pad.lx)
    }

    @Test
    fun `stick and RT added after a background finger never reach the trackpad or click`() = runTest {
        val r = rig()
        r.router.down(5, 500f, 20f, 0)
        val (sx, sy) = centre(ls); val (tx, ty) = centre(rt)
        r.router.down(6, sx, sy, 1)
        r.router.down(7, tx, ty, 2)
        r.router.move(listOf(Finger(5, 500f, 20f), Finger(6, sx + 30f, sy), Finger(7, tx, ty)), 16)
        assertEquals(1, r.pad.maxFingers)
        assertEquals(setOf(5), r.pad.seen)
        assertEquals(0, r.pc.clicks)
        assertEquals(255, r.pc.pad.rt)
    }

    @Test
    fun `fingers are tracked by pointer id, not index`() = runTest {
        val r = rig()
        val (sx, sy) = centre(ls); val (tx, ty) = centre(rt); val (zx, zy) = centre(zone)
        r.router.down(0, sx, sy, 0)
        r.router.down(1, zx, zy, 1)
        r.router.down(2, tx, ty, 2)
        r.router.up(0, 3)
        // After id 0 lifts, id 1 is at index 0: it must still drive the zone, id 2 still holds RT.
        r.router.move(listOf(Finger(1, zx + 40f, zy), Finger(2, tx, ty)), 20)
        assertTrue("zone looked: ${r.pc.mouseX}", r.pc.mouseX > 0)
        assertEquals(0, r.pc.pad.lx)
        assertEquals(255, r.pc.pad.rt)
    }

    @Test
    fun `cancel releases every owner at once`() = runTest {
        val r = rig()
        val (sx, sy) = centre(ls); val (tx, ty) = centre(rt); val (ax, ay) = centre(a)
        r.router.down(0, sx - 50f, sy, 0)
        r.router.down(1, tx, ty, 1)
        r.router.down(2, ax, ay, 2)
        assertFalse(r.pc.pad.atRest)
        r.router.cancel(3)
        assertTrue("all zero after cancel: ${r.pc.pad}", r.pc.pad.atRest)
        assertEquals(0, r.router.fingerCount)
    }

    @Test
    fun `a finger missing from a move is released`() = runTest {
        val r = rig()
        val (ax, ay) = centre(a); val (sx, sy) = centre(ls)
        r.router.down(0, ax, ay, 0)
        r.router.down(1, sx, sy, 0)
        r.router.move(listOf(Finger(1, sx, sy)), 100) // id 0's UP was lost
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 10); runCurrent()
        assertEquals(0, r.pc.pad.buttons)
        assertEquals(1, r.router.fingerCount)
    }

    @Test
    fun `sliding off RT keeps it held until the finger lifts`() = runTest {
        val r = rig()
        val (tx, ty) = centre(rt)
        r.router.down(0, tx, ty, 0)
        r.router.move(listOf(Finger(0, tx - 200f, ty + 100f)), 16)
        assertEquals(255, r.pc.pad.rt)
        r.router.up(0, 200)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 10); runCurrent()
        assertEquals(0, r.pc.pad.rt)
    }

    @Test
    fun `two fingers on one button release it only when both lift`() = runTest {
        val r = rig()
        val (ax, ay) = centre(a)
        r.router.down(0, ax - 5f, ay, 0)
        r.router.down(1, ax + 5f, ay, 1)
        r.router.up(0, 100)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 10); runCurrent()
        assertEquals(PadFlags.A, r.pc.pad.buttons)
        r.router.up(1, 200)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 10); runCurrent()
        assertEquals(0, r.pc.pad.buttons)
    }

    @Test
    fun `fire-and-look RT fires at once and looks as soon as it moves`() = runTest {
        val r = rig(elements = listOf(zone, ls, rt.copy(lookThrough = true), a))
        val (tx, ty) = centre(rt)
        r.router.down(0, tx, ty, 0)
        assertEquals(255, r.pc.pad.rt)
        r.router.move(listOf(Finger(0, tx - 2f, ty)), 16)
        assertEquals("a tap's jitter doesn't look", 0, r.pc.mouseX)
        r.router.move(listOf(Finger(0, tx - 8f, ty)), 24)
        assertTrue("looking after a few dp: ${r.pc.mouseX}", r.pc.mouseX < 0)
        r.router.move(listOf(Finger(0, tx - 14f, ty)), 32)
        r.router.move(listOf(Finger(0, tx - 40f, ty)), 48)
        assertTrue("looking: ${r.pc.mouseX}", r.pc.mouseX < 0)
        assertEquals(255, r.pc.pad.rt)
        r.router.up(0, 64)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 10); runCurrent()
        assertEquals(0, r.pc.pad.rt)
        // Stick look through the same button: the right stick moves, then is zero on lift.
        val s = rig(look = LookOutput.STICK, elements = listOf(ls, rt.copy(lookThrough = true)))
        s.router.down(0, tx, ty, 0)
        var t = 0L
        repeat(10) { t += 16; s.router.move(listOf(Finger(0, tx - 20f - it * 10f, ty)), t); s.router.tick(t) }
        assertTrue("stick look: ${s.pc.pad.rx}", s.pc.pad.rx < -10000)
        s.router.up(0, t + 16)
        assertEquals(0, s.pc.pad.rx)
    }

    @Test
    fun `background Off gives no output, Look looks only on the right`() = runTest {
        val off = rig(OutsideTouch.OFF)
        off.router.down(0, 500f, 20f, 0)
        off.router.move(listOf(Finger(0, 560f, 60f)), 16)
        off.router.up(0, 32)
        assertTrue(off.pad.events.isEmpty())
        assertEquals(0 to 0, off.pc.mouseX to off.pc.mouseY)
        assertTrue(off.pc.pads.isEmpty())
        assertEquals(0, off.pc.clicks)

        val look = rig(OutsideTouch.LOOK, LookOutput.MOUSE, elements = listOf(ls, a))
        look.router.down(0, 300f, 20f, 0) // left of the 45 % split: ignored
        look.router.move(listOf(Finger(0, 360f, 20f)), 16)
        assertEquals(0, look.pc.mouseX)
        look.router.down(1, 700f, 20f, 20)
        look.router.move(listOf(Finger(0, 360f, 20f), Finger(1, 760f, 20f)), 36)
        assertTrue(look.pc.mouseX > 0)
        assertTrue(look.pad.events.isEmpty())
        assertEquals(0, look.pc.clicks)
    }

    @Test
    fun `trackpad gestures count only background fingers`() = runTest {
        val r = rig()
        val (sx, sy) = centre(ls)
        r.router.down(0, sx, sy, 0) // on the stick
        r.router.down(1, 450f, 20f, 1)
        r.router.down(2, 550f, 20f, 2)
        r.router.move(listOf(Finger(0, sx, sy), Finger(1, 450f, 30f), Finger(2, 550f, 30f)), 16)
        assertEquals("three fingers on screen, two on the trackpad", 2, r.pad.maxFingers)
        assertEquals(setOf(1, 2), r.pad.seen)
    }
}

/** Minimum hold and resend (osc-touch-research §4). */
@OptIn(ExperimentalCoroutinesApi::class)
class ControlsInputHoldTest {
    private val rt = ControlElement("rt", ElementKind.TRIGGER, 0.9f, 0.1f, 68f, 42f, bindings = listOf(Binding.Trigger(Side.RIGHT)))

    @Test
    fun `a 5 ms RT tap stays at 255 for at least 50 ms`() = runTest {
        val pc = Pc()
        val input = ControlsInput({ pc }, backgroundScope)
        input.elementDown(rt)
        advanceTimeBy(5); input.elementUp(rt)
        assertEquals(255, pc.pad.rt)
        advanceTimeBy(40); runCurrent()
        assertEquals("held through 45 ms", 255, pc.pad.rt)
        advanceTimeBy(10); runCurrent()
        assertEquals("released after 50 ms", 0, pc.pad.rt)
    }

    @Test
    fun `RT pressed during a stick stream shows 255 in a sent packet`() = runTest {
        val pc = Pc()
        val input = ControlsInput({ pc }, backgroundScope)
        val stick = ControlElement("rs", ElementKind.STICK, 0.7f, 0.8f, 100f, 100f, stick = StickOutput.RIGHT, deadzone = 0f)
        repeat(5) { input.stick(stick, 0.1f * it, 0f); advanceTimeBy(4) }
        input.elementDown(rt)
        repeat(5) { input.stick(stick, 0.5f + 0.1f * it, 0f); advanceTimeBy(2) }
        input.elementUp(rt)
        repeat(5) { input.stick(stick, 0.1f * it, 0.2f); advanceTimeBy(4) }
        assertTrue(pc.pads.any { it.rt == 255 && it.rx != 0 })
        advanceTimeBy(100); runCurrent()
        assertEquals(0, pc.pad.rt)
    }

    @Test
    fun `releaseAll during the minimum hold releases at once, and the state is resent`() = runTest {
        val pc = Pc()
        val input = ControlsInput({ pc }, backgroundScope)
        input.elementDown(rt)
        advanceTimeBy(10)
        input.releaseAll()
        assertEquals(0, pc.pad.rt)
        advanceTimeBy(200); runCurrent()
        assertEquals(0, pc.pad.rt)
        // One full-state resend about 100 ms after the last change.
        val n = pc.pads.size
        input.elementDown(rt)
        advanceTimeBy(ControlsInput.RESEND_MS + 5); runCurrent()
        assertEquals(n + 2, pc.pads.size)
        assertEquals(255, pc.pad.rt)
    }
}
