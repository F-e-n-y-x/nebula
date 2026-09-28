package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.DefaultProfiles
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.StickOutput
import io.github.f_e_n_y_x.nebula.controls.ZoneType
import io.github.f_e_n_y_x.nebula.controls.newElement
import io.github.f_e_n_y_x.nebula.input.LoggingInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * dev11: the camera zone ("Camera → stick", right stick) did nothing on the phone, alone or with
 * the on-screen left stick held. Real touches, injected through the window like a finger, on the
 * demo stream screen; the assertions read what the gamepad mapper sends to the (demo) PC.
 */
@RunWith(AndroidJUnit4::class)
class ZoneTouchDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var origin = Offset.Zero

    @Before fun setUp() {
        // On-screen controls on, gyro off (the emulator's sensors would add noise).
        ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("checkbox_show_onscreen_controls", true)
            .putString("nebula_gyro_mode", "off")
            // Worst case for leaks: the picture outside the controls is a trackpad.
            .putString("nebula_osc_outside_touch:p-zonetest", "trackpad")
            .commit()
        // The owner's layout: the Standard pad plus a camera zone on the right half.
        val standard = DefaultProfiles.landscape().filterNot { it.id == "rs" }
        val zone = newElement(ElementKind.ZONE, "camera").copy(zone = ZoneType.CAMERA_STICK, stick = StickOutput.RIGHT, y = 0.35f, height = 0.5f)
        val profile = ControlsProfile(id = "p-zonetest", name = "Zone test", landscape = standard + zone)
        ControlsStore.get(ctx).update { lib -> lib.save(profile).first.setDefault(profile.id) }
        val intent = Intent(ctx, MainActivity::class.java).putExtra("start", "mirror:gta5")
        scenario = ActivityScenario.launch(intent)
        compose.waitUntil(20_000) {
            var landscape = false
            scenario.onActivity { landscape = it.window.decorView.width > it.window.decorView.height }
            landscape && runCatching { compose.onNodeWithTag("osc:camera").fetchSemanticsNode() }.isSuccess
        }
        SystemClock.sleep(1500) // let the rotation settle

        compose.waitForIdle()
        scenario.onActivity { a ->
            val loc = IntArray(2)
            a.window.decorView.getLocationOnScreen(loc)
            origin = Offset(loc[0].toFloat(), loc[1].toFloat())
        }
    }

    @After fun tearDown() {
        scenario.close()
        ControlsStore.get(ctx).update { lib -> lib.setDefault(ControlsProfile.STANDARD_ID).delete("p-zonetest") }
    }

    private val demo: LoggingInput get() = ctx.container.stream.remoteInput as LoggingInput
    private fun pad() = demo.lastPad ?: IntArray(8)
    private fun lt() = pad()[2]
    private fun rt() = pad()[3]
    private fun lx() = pad()[4]
    private fun rx() = pad()[6]
    private fun ry() = pad()[7]

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
    private fun Offset.screen() = this + origin

    private var down = 0L
    private val points = sortedMapOf<Int, Offset>()

    /** Sends one event with every finger in [points]; [actionPointer] is the finger going down or up. */
    private fun send(action: Int, actionPointer: Int = 0) {
        val ids = points.keys.toList()
        val props = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = ids.map { id -> MotionEvent.PointerCoords().apply { val p = points.getValue(id).screen(); x = p.x; y = p.y; pressure = 1f; size = 1f } }.toTypedArray()
        val index = ids.indexOf(actionPointer).coerceAtLeast(0)
        val a = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val e = MotionEvent.obtain(down, SystemClock.uptimeMillis(), a, ids.size, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        instr.sendPointerSync(e)
        e.recycle()
    }

    private fun fingerDown(id: Int, at: Offset) {
        points[id] = at
        if (points.size == 1) { down = SystemClock.uptimeMillis(); send(MotionEvent.ACTION_DOWN, id) } else send(MotionEvent.ACTION_POINTER_DOWN, id)
    }

    private fun fingerUp(id: Int) {
        if (points.size == 1) send(MotionEvent.ACTION_UP, id) else send(MotionEvent.ACTION_POINTER_UP, id)
        points.remove(id)
    }

    /** Moves finger [id] by ([dx], [dy]) in [steps] events 16 ms apart; returns the peak right-stick X seen. */
    private fun drag(id: Int, dx: Float, dy: Float, steps: Int = 10, check: () -> Unit = {}): Int {
        var peak = 0
        repeat(steps) {
            points[id] = points.getValue(id) + Offset(dx / steps, dy / steps)
            send(MotionEvent.ACTION_MOVE)
            peak = maxOf(peak, rx())
            check()
            SystemClock.sleep(16)
        }
        return peak
    }

    @Test fun swipeInTheCameraZoneMovesTheRightStick() {
        val z = bounds("osc:camera")
        fingerDown(0, z.center)
        val peak = drag(0, 300f, -150f)
        assertTrue("a swipe right moved rx to $peak", peak > 3000)
        fingerUp(0)
        instr.waitForIdleSync()
        assertEquals("lifting centres the stick", 0, rx())
        assertEquals(0, ry())
        assertEquals("one pad on the PC", 1, demo.arrivals.distinct().size)
    }

    @Test fun holdThenDragInTheCameraZoneMovesTheRightStick() {
        val z = bounds("osc:camera")
        fingerDown(0, z.center)
        SystemClock.sleep(600) // longer than a long-press
        val peak = drag(0, 300f, 0f)
        assertTrue("hold then drag moved rx to $peak", peak > 3000)
        fingerUp(0)
        instr.waitForIdleSync()
        assertEquals(0, rx())
    }

    @Test fun leftStickAndCameraZoneWorkAtTheSameTime() {
        val ls = bounds("osc:ls")
        val z = bounds("osc:camera")
        fingerDown(0, ls.center)
        drag(0, -ls.width / 2, 0f, steps = 4)
        assertTrue("left stick pushed left: lx=${lx()}", lx() < -20000)
        fingerDown(1, z.center)
        var both = false
        drag(1, 300f, 0f) { if (lx() < -20000 && rx() > 3000) both = true }
        assertTrue("lx and rx together (last lx=${lx()} rx=${rx()})", both)
        fingerUp(1)
        instr.waitForIdleSync()
        assertEquals("camera finger lifted", 0, rx())
        assertTrue("left stick still held: lx=${lx()}", lx() < -20000)
        fingerUp(0)
        instr.waitForIdleSync()
        assertEquals(0, lx())
        assertEquals("one pad on the PC", 1, demo.arrivals.distinct().size)
    }


    @Test fun holdingStillInTheCameraZoneSendsNothing() {
        val z = bounds("osc:camera")
        fingerDown(0, z.center)
        repeat(20) { SystemClock.sleep(16); send(MotionEvent.ACTION_MOVE) }
        assertEquals("a finger that doesn't move doesn't turn the camera", 0, rx())
        fingerUp(0)
    }

    @Test fun rightTriggerFiresFullyAndNothingLeaksToTheMouse() {
        val before = demo.pointerEvents.get()
        val rtBox = bounds("osc:rt")
        fingerDown(0, rtBox.center)
        assertEquals("RT pressed", 255, rt())
        assertEquals(0, lt())
        // Also drive the stick and the zone at once: none of it may reach the trackpad.
        val ls = bounds("osc:ls")
        fingerDown(1, ls.center)
        drag(1, ls.width / 3, 0f, steps = 3)
        fingerDown(2, bounds("osc:camera").center)
        drag(2, 200f, 50f, steps = 5)
        fingerUp(2); fingerUp(1)
        assertEquals("RT still held", 255, rt())
        fingerUp(0)
        instr.waitForIdleSync()
        assertEquals("RT released", 0, rt())
        assertEquals(0, lt())
        assertEquals("no mouse or touch reached the PC from on-screen controls", before, demo.pointerEvents.get())
    }

    @Test fun touchesOutsideTheControlsDoNothingByDefault() {
        ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE).edit()
            .remove("nebula_osc_outside_touch:p-zonetest").commit()
        instr.waitForIdleSync()
        SystemClock.sleep(500)
        val before = demo.pointerEvents.get()
        // Top centre of the picture, above the zone: no element there.
        val z = bounds("osc:camera")
        val spot = Offset(z.left - 40f, z.top * 0.5f)
        fingerDown(0, spot)
        points[0] = spot + Offset(80f, 30f); send(MotionEvent.ACTION_MOVE)
        fingerUp(0)
        instr.waitForIdleSync()
        assertEquals("a stray touch on the picture must not click or move the PC mouse", before, demo.pointerEvents.get())
    }
}
