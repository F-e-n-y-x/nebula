package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.input.Finger
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.settings.MotionHold
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The PC side: last pad state, held keys and mouse buttons, mouse travel. */
private class ShooterPc : RemoteInput {
    var buttons = 0; var lt = 0; var rt = 0; var lx = 0; var ly = 0; var rx = 0; var ry = 0
    val keys = HashSet<Int>()
    val mouse = HashSet<MouseButton>()
    var mouseX = 0; var mouseY = 0
    var scrolls = 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { if (down) keys += vk else keys -= vk }
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) { mouseX += dx; mouseY += dy }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) { if (down) mouse += button else mouse -= button }
    override fun scroll(amount: Int) { scrolls++ }
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        this.buttons = buttons; this.lt = lt; this.rt = rt; this.lx = lx; this.ly = ly; this.rx = rx; this.ry = ry
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShooterControlsTest {
    /** A phone-sized controls area at density 1 (px = dp). */
    private val area = RRect(0f, 0f, 780f, 360f)

    private class Rig(scope: TestScope, profile: ControlsProfile, area: RRect) {
        val pc = ShooterPc()
        val input = ControlsInput({ pc }, scope.backgroundScope)
        val router = TouchRouter(input).apply {
            layout = RouterLayout.of(profile.landscape, area, 1f, profile.outside ?: OutsideTouch.LOOK, profile.look ?: LookOutput.MOUSE)
        }
    }

    private fun TestScope.rig(p: ControlsProfile) = Rig(this, p, area)
    private fun at(p: ControlsProfile, id: String) = RouterLayout.place(p.landscape.first { it.id == id }, area, 1f).let { it.cx to it.cy }

    // ---------------------------------------------------------------- left fire, drag-fire

    @Test
    fun `left fire shoots while the right thumb looks`() = runTest {
        val p = DefaultProfiles.gtaTouchControls()
        val r = rig(p)
        // Right thumb: a free spot on the right looks (mouse look in the GTA preset).
        r.router.down(1, 560f, 200f, 0)
        r.router.move(listOf(Finger(1, 580f, 200f)), 16)
        val (fx, fy) = at(p, "fire-left")
        r.router.down(2, fx, fy, 20)
        assertEquals("left fire holds RT", 255, r.pc.rt)
        val before = r.pc.mouseX
        r.router.move(listOf(Finger(1, 640f, 190f), Finger(2, fx, fy)), 36)
        r.router.move(listOf(Finger(1, 700f, 180f), Finger(2, fx, fy)), 52)
        assertTrue("still looking while firing: ${r.pc.mouseX} > $before", r.pc.mouseX > before)
        assertEquals(255, r.pc.rt)
        r.router.up(2, 60); advanceTimeBy(60); runCurrent()
        assertEquals(0, r.pc.rt)
        assertEquals("left fire never aims", 0, r.pc.lt)
    }

    @Test
    fun `the right fire button aims as soon as it is dragged, without losing the first movement`() = runTest {
        val p = DefaultProfiles.gtaTouchControls()
        val r = rig(p)
        val (fx, fy) = at(p, "fire")
        r.router.down(3, fx, fy, 0)
        assertEquals(255, r.pc.rt)
        // Slow drags (no acceleration): 10 dp at mouse look's 2 mickeys/dp, all of it reaches the PC.
        r.router.move(listOf(Finger(3, fx + 10f, fy)), 100)
        assertEquals(20, r.pc.mouseX)
        r.router.move(listOf(Finger(3, fx + 20f, fy)), 200)
        assertEquals(40, r.pc.mouseX)
        assertEquals("still firing", 255, r.pc.rt)
        r.router.up(3, 40); advanceTimeBy(60); runCurrent()
        assertEquals(0, r.pc.rt)
    }

    @Test
    fun `drag-fire turns as fast as the look zone`() = runTest {
        val zone = newElement(ElementKind.ZONE, "cam").copy(zone = ZoneType.CAMERA_MOUSE, sensitivity = 2f, x = 0.75f, width = 0.5f)
        val fire = ControlElement("fire", ElementKind.TRIGGER, 0.8f, 0.5f, 80f, 80f, bindings = listOf(Binding.Trigger(Side.RIGHT)), lookThrough = true, role = ElementRole.FIRE)
        val p = ControlsProfile("p", "p", listOf(zone, fire))
        val viaZone = rig(p).let { r ->
            r.router.down(1, 700f, 60f, 0); r.router.move(listOf(Finger(1, 730f, 60f)), 16); r.pc.mouseX
        }
        val viaFire = rig(p).let { r ->
            val (fx, fy) = at(p, "fire")
            r.router.down(1, fx, fy, 0); r.router.move(listOf(Finger(1, fx + 30f, fy)), 16); r.pc.mouseX
        }
        assertEquals("same gain for the zone and the fire button", viaZone, viaFire)
        assertTrue("the zone's doubled sensitivity applies: $viaFire", viaFire >= 30 * 2 * 2)
    }

    // ---------------------------------------------------------------- auto-sprint and run lock

    private val moveR = TouchRouter.FLOAT_RING_DP

    @Test
    fun `pushing the move stick forward past the ring holds sprint, and only forward`() = runTest {
        val p = DefaultProfiles.touchShooterPad()
        val r = rig(p)
        val x = 170f; val y = 270f
        r.router.down(0, x, y, 0)
        r.router.move(listOf(Finger(0, x, y - moveR * 0.9f)), 16)
        assertEquals("inside the ring: no sprint", 0, r.pc.buttons and PadFlags.LS_CLK)
        assertTrue("pushed forward: ${r.pc.ly}", r.pc.ly > 25000)
        r.router.move(listOf(Finger(0, x, y - moveR * 1.4f)), 32)
        assertTrue("past the ring: L3 held", r.pc.buttons and PadFlags.LS_CLK != 0)
        r.router.move(listOf(Finger(0, x, y - moveR * 0.5f)), 48)
        advanceTimeBy(60); runCurrent()
        assertEquals("back inside: sprint released", 0, r.pc.buttons and PadFlags.LS_CLK)
        r.router.move(listOf(Finger(0, x + moveR * 1.5f, y)), 64)
        advanceTimeBy(60); runCurrent()
        assertEquals("sideways past the ring isn't a sprint", 0, r.pc.buttons and PadFlags.LS_CLK)
        r.router.up(0, 80)
        advanceTimeBy(60); runCurrent()
        assertEquals(0, r.pc.lx); assertEquals(0, r.pc.ly)
    }

    @Test
    fun `dragging up to the lock and letting go keeps running until the stick is touched again`() = runTest {
        val p = DefaultProfiles.touchShooterPad()
        val r = rig(p)
        val x = 170f; val y = 300f
        r.router.down(0, x, y, 0)
        r.router.move(listOf(Finger(0, x, y - moveR * 1.5f)), 16)
        r.router.move(listOf(Finger(0, x + 10f, y - moveR * 2.3f)), 32)
        r.router.up(0, 48)
        advanceTimeBy(200); runCurrent()
        assertEquals(setOf("move"), r.router.runLocked)
        assertEquals("locked: running straight ahead", ControlsInput.AXIS_MAX, r.pc.ly)
        assertEquals(0, r.pc.lx)
        assertTrue("and sprinting", r.pc.buttons and PadFlags.LS_CLK != 0)
        // A tap on the move area stops the run.
        r.router.down(1, 150f, 250f, 400)
        assertTrue(r.router.runLocked.isEmpty())
        r.router.up(1, 420)
        advanceTimeBy(100); runCurrent()
        assertEquals(0, r.pc.ly)
        assertEquals(0, r.pc.buttons and PadFlags.LS_CLK)
    }

    @Test
    fun `letting go below the lock mark doesn't lock`() = runTest {
        val p = DefaultProfiles.touchShooterPad()
        val r = rig(p)
        r.router.down(0, 170f, 300f, 0)
        r.router.move(listOf(Finger(0, 170f, 300f - moveR * 1.6f)), 16)
        r.router.up(0, 32)
        advanceTimeBy(100); runCurrent()
        assertTrue(r.router.runLocked.isEmpty())
        assertEquals(0, r.pc.ly)
        assertEquals(0, r.pc.buttons)
    }

    @Test
    fun `cancel ends a run lock`() = runTest {
        val p = DefaultProfiles.touchShooterPad()
        val r = rig(p)
        r.router.down(0, 170f, 300f, 0)
        r.router.move(listOf(Finger(0, 170f, 300f - moveR * 2.3f)), 16)
        r.router.up(0, 32)
        assertEquals(setOf("move"), r.router.runLocked)
        r.router.cancel(40)
        advanceTimeBy(100); runCurrent()
        assertTrue(r.router.runLocked.isEmpty())
        assertEquals(0, r.pc.ly)
        assertEquals(0, r.pc.buttons)
    }

    @Test
    fun `the keyboard preset's stick is WASD with Shift past the ring`() = runTest {
        val p = DefaultProfiles.touchShooterKbm()
        val r = rig(p)
        r.router.down(0, 170f, 270f, 0)
        r.router.move(listOf(Finger(0, 170f, 270f - moveR * 0.8f)), 16)
        assertEquals(setOf(0x57), r.pc.keys)
        r.router.move(listOf(Finger(0, 170f, 270f - moveR * 1.4f)), 32)
        assertEquals(setOf(0x57, 0xA0), r.pc.keys)
        r.router.up(0, 48)
        assertTrue(r.pc.keys.isEmpty())
    }

    // ---------------------------------------------------------------- ADS modes, gyro gate

    @Test
    fun `mixed ADS latches on a tap and holds on a long press`() = runTest {
        val p = DefaultProfiles.touchShooterPad()
        val r = rig(p)
        val (ax, ay) = at(p, "ads")
        r.router.down(0, ax, ay, 0); r.router.up(0, 80)
        advanceTimeBy(100); runCurrent()
        assertEquals("tapped: stays aimed", 255, r.pc.lt)
        r.router.down(0, ax, ay, 1000); r.router.up(0, 1080)
        advanceTimeBy(100); runCurrent()
        assertEquals("tapped again: back to the hip", 0, r.pc.lt)
        r.router.down(0, ax, ay, 2000)
        assertEquals(255, r.pc.lt)
        r.router.up(0, 2600)
        advanceTimeBy(100); runCurrent()
        assertEquals("held: lets go on lift", 0, r.pc.lt)
    }

    @Test
    fun `hold and toggle ADS modes`() = runTest {
        val hold = ControlElement("ads", ElementKind.TRIGGER, 0.9f, 0.3f, 58f, 58f, mode = PressMode.HOLD, bindings = listOf(Binding.Trigger(Side.LEFT)), role = ElementRole.ADS)
        val toggle = hold.copy(mode = PressMode.TOGGLE)
        for ((e, stays) in listOf(hold to false, toggle to true)) {
            val r = rig(ControlsProfile("p", "p", listOf(e)))
            val (x, y) = RouterLayout.place(e, area, 1f).let { it.cx to it.cy }
            r.router.down(0, x, y, 0); r.router.up(0, 600)
            advanceTimeBy(100); runCurrent()
            assertEquals("${e.mode}", if (stays) 255 else 0, r.pc.lt)
        }
    }

    @Test
    fun `fire and ADS count as aiming for the gyro`() = runTest {
        val p = DefaultProfiles.touchShooterKbm()
        OnScreenAim.active = false
        val r = rig(p)
        assertFalse(OnScreenAim.active)
        val (fx, fy) = at(p, "fire")
        r.router.down(0, fx, fy, 0)
        assertTrue(OnScreenAim.active)
        assertTrue(MotionHold.AIMING.allows(0, 0, aiming = OnScreenAim.active))
        r.router.up(0, 30)
        assertFalse(OnScreenAim.active)
        assertFalse(MotionHold.AIMING.allows(0, 0, aiming = OnScreenAim.active))
        assertTrue("a pulled trigger also counts", MotionHold.AIMING.allows(0, 200))
        val (jx, jy) = at(p, "jump")
        r.router.down(1, jx, jy, 40)
        assertFalse("jump isn't aiming", OnScreenAim.active)
        r.router.cancel(50)
    }

    @Test
    fun `the eye button looks while holding free look`() = runTest {
        val p = DefaultProfiles.touchShooterKbm()
        val r = rig(p)
        val (ex, ey) = at(p, "eye")
        r.router.down(0, ex, ey, 0)
        assertEquals("Alt held", setOf(0xA4), r.pc.keys)
        r.router.move(listOf(Finger(0, ex + 25f, ey)), 16)
        assertTrue(r.pc.mouseX > 0)
        r.router.up(0, 30)
        assertTrue(r.pc.keys.isEmpty())
    }

    // ---------------------------------------------------------------- the presets

    @Test
    fun `GTA V preset uses GTA's controller map`() {
        val l = DefaultProfiles.gtaTouchControls().landscape.associateBy { it.id }
        assertEquals(Binding.Trigger(Side.RIGHT), l.getValue("fire").binding)
        assertEquals(Binding.Trigger(Side.RIGHT), l.getValue("fire-left").binding)
        assertEquals(Binding.Trigger(Side.LEFT), l.getValue("ads").binding)
        assertEquals(Binding.Pad(PadFlags.X), l.getValue("jump").binding)
        assertEquals(Binding.Pad(PadFlags.A), l.getValue("move").sprint)
        assertEquals(Binding.Pad(PadFlags.LS_CLK), l.getValue("crouch").binding)
        assertEquals(Binding.Pad(PadFlags.B), l.getValue("reload").binding)
        assertEquals(Binding.Pad(PadFlags.Y), l.getValue("use").binding)
        assertEquals(Binding.Pad(PadFlags.LB), l.getValue("swap").binding)
        assertTrue(l.values.any { it.binding == Binding.Pad(PadFlags.RB) && it.label == "Cover" })
        assertEquals(LookOutput.MOUSE, DefaultProfiles.gtaTouchControls().look)
    }

    @Test
    fun `controller and keyboard presets`() {
        val pad = DefaultProfiles.touchShooterPad().landscape.associateBy { it.id }
        assertEquals(Binding.Pad(PadFlags.A), pad.getValue("jump").binding)
        assertEquals(Binding.Pad(PadFlags.B), pad.getValue("crouch").binding)
        assertEquals(ElementKind.MACRO, pad.getValue("prone").kind)
        assertEquals(Binding.Pad(PadFlags.LS_CLK), pad.getValue("move").sprint)
        val kbm = DefaultProfiles.touchShooterKbm().landscape.associateBy { it.id }
        assertEquals(Binding.Mouse(MouseKey.LEFT), kbm.getValue("fire").binding)
        assertEquals(Binding.Mouse(MouseKey.RIGHT), kbm.getValue("ads").binding)
        assertEquals(StickOutput.KEYS, kbm.getValue("move").stick)
        assertEquals(listOf(0x20, 0x43, 0x5A, 0x52, 0x46), listOf("jump", "crouch", "prone", "reload", "use").map { (kbm.getValue(it).binding as Binding.Key).vk })
        assertEquals(Binding.Key(0xA0), kbm.getValue("move").sprint)
        assertEquals(PressMode.TOGGLE, kbm.getValue("peek-left").mode)
        assertEquals(LayoutTarget.KBM, LayoutTarget.detect(DefaultProfiles.touchShooterKbm().landscape))
        assertEquals(LayoutTarget.XINPUT, LayoutTarget.detect(DefaultProfiles.touchShooterPad().landscape))
    }

    @Test
    fun `every shooter preset plays with two thumbs and leaves the right side to look`() {
        for (p in listOf(DefaultProfiles.gtaTouchControls(), DefaultProfiles.touchShooterPad(), DefaultProfiles.touchShooterKbm())) {
            val l = p.landscape
            assertEquals(p.name, OutsideTouch.LOOK, p.outside)
            assertTrue(p.name, p.isShooter())
            assertTrue("${p.name}: a right fire that aims", l.any { it.role == ElementRole.FIRE && it.lookThrough && it.x > 0.5f })
            assertTrue("${p.name}: a left fire", l.any { it.role == ElementRole.FIRE && !it.lookThrough && it.x < 0.5f })
            val move = l.single { it.role == ElementRole.MOVE }
            assertTrue(move.runLock); assertTrue(move.sprint != Binding.None)
            // Buttons: inside the area, not overlapping.
            val boxes = l.filter { !it.areaSized }.map { it to RouterLayout.place(it, area, 1f) }
            boxes.forEach { (e, b) -> assertTrue("${p.name}/${e.id} inside", b.left >= 0f && b.top >= 0f && b.right <= area.right && b.bottom <= area.bottom) }
            for (i in boxes.indices) for (j in i + 1 until boxes.size) {
                val (a, ra) = boxes[i]; val (b, rb) = boxes[j]
                val overlap = ra.left < rb.right && rb.left < ra.right && ra.top < rb.bottom && rb.top < ra.bottom
                assertFalse("${p.name}: ${a.id} overlaps ${b.id}", overlap)
            }
            // Most of the right side stays free to look.
            var free = 0; var total = 0
            var x = area.width * TouchRouter.LOOK_SPLIT
            while (x < area.width) {
                var y = 0f
                while (y < area.height) { total++; if (boxes.none { it.second.contains(x, y) }) free++; y += 10f }
                x += 10f
            }
            assertTrue("${p.name}: ${free * 100 / total}% of the right side looks", free * 100 / total >= 60)
        }
    }

    @Test
    fun `presets are listed and GTA V is suggested its touch controls`() {
        val ids = DefaultProfiles.presets().map { it.id }
        assertTrue(ids.containsAll(listOf(ControlsProfile.GTA_TOUCH_CONTROLS_ID, ControlsProfile.TOUCH_SHOOTER_PAD_ID, ControlsProfile.TOUCH_SHOOTER_KBM_ID)))
        assertEquals(ControlsProfile.GTA_TOUCH_CONTROLS_ID, DefaultProfiles.suggestedFor("Grand Theft Auto V"))
        assertNotNull(DefaultProfiles.gtaTouchControls().meta?.game?.steamAppId)
    }

    @Test
    fun `fit scales buttons for a tablet and keeps them on screen`() {
        val l = DefaultProfiles.touchShooterPad().landscape
        val tablet = LayoutFit.fit(l, 1180f, 780f)
        val fire = tablet.first { it.id == "fire" }
        assertEquals(80f * LayoutFit.MAX_SCALE, fire.width, 0.01f)
        val narrow = LayoutFit.fit(l, 560f, 300f)
        narrow.filter { !it.areaSized }.forEach { e ->
            assertTrue(e.id, e.x - e.width / 560f / 2f >= -1e-4f && e.x + e.width / 560f / 2f <= 1f + 1e-4f)
        }
        assertEquals(1f, LayoutFit.scaleFor(780f, 360f), 0.001f)
    }
}
