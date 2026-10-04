package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.f_e_n_y_x.nebula.controls.Binding
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.ElementShape
import io.github.f_e_n_y_x.nebula.controls.PadFlags
import io.github.f_e_n_y_x.nebula.controls.newElement
import io.github.f_e_n_y_x.nebula.input.LoggingInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-screen controls turned on in the middle of a stream (the menu's quick toggle or the float
 * ball's long press both flip the same preference) must drive the PC exactly like controls that
 * were on from the start. The stream starts with the controls off; real touches go through the
 * window and the assertions read what the demo PC got.
 */
@RunWith(AndroidJUnit4::class)
class OscMidStreamDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private val sp get() = ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var origin = Offset.Zero
    private var setId = ""

    private fun sw(id: String, x: Float) =
        newElement(ElementKind.SWITCH, id, x, 0.08f).copy(width = 110f, height = 40f, shape = ElementShape.PILL)

    private val foot = ControlsProfile(
        "p-midtest-foot", "On foot",
        listOf(ControlElement("a", ElementKind.BUTTON, 0.85f, 0.7f, 64f, 64f, label = "A", bindings = listOf(Binding.Pad(PadFlags.A))), sw("next", 0.5f)),
    )
    private val car = ControlsProfile(
        "p-midtest-car", "Vehicle",
        listOf(ControlElement("b", ElementKind.BUTTON, 0.15f, 0.7f, 64f, 64f, label = "B", bindings = listOf(Binding.Pad(PadFlags.B))), sw("next", 0.5f)),
    )

    @Before fun setUp() {
        sp.edit()
            .putBoolean("checkbox_show_onscreen_controls", false)
            .putBoolean("checkbox_enable_float_ball", true)
            .putString("list_float_ball_long_click_action", "toggle_controls")
            .putString("nebula_gyro_mode", "off")
            .commit()
        ControlsStore.get(ctx).update { lib ->
            val saved = lib.save(foot).first.save(car).first
            val (withSet, set) = saved.createSet("Mid-stream test", listOf(foot.id, car.id))!!
            setId = set.id
            withSet.setDefault(set.id)
        }
        scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra("start", "mirror:gta5"))
        compose.waitUntil(20_000) {
            var landscape = false
            scenario.onActivity { landscape = it.window.decorView.width > it.window.decorView.height }
            landscape && exists("float-ball")
        }
        SystemClock.sleep(1500)
        compose.waitForIdle()
        scenario.onActivity { a ->
            val loc = IntArray(2)
            a.window.decorView.getLocationOnScreen(loc)
            origin = Offset(loc[0].toFloat(), loc[1].toFloat())
        }
        assertFalse("the stream starts without on-screen controls", exists("osc:a"))
    }

    @After fun tearDown() {
        scenario.close()
        sp.edit().putBoolean("checkbox_show_onscreen_controls", false).commit()
        ControlsStore.get(ctx).update { lib ->
            lib.setDefault(ControlsProfile.STANDARD_ID).deleteSet(setId).delete(foot.id).delete(car.id)
        }
    }

    private val demo: LoggingInput get() = ctx.container.stream.remoteInput as LoggingInput
    private fun buttons() = (demo.lastPad ?: IntArray(8))[1]
    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private fun touch(action: Int, at: Offset, downTime: Long) {
        val p = at + origin
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, p.x, p.y, 0)
        e.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        instr.sendPointerSync(e)
        e.recycle()
    }

    /** Presses [tag], checks the PC sees [flag] held, lets go and checks it was released. */
    private fun pressAndCheck(tag: String, flag: Int) {
        val at = bounds(tag).center
        val t = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, at, t)
        SystemClock.sleep(120)
        instr.waitForIdleSync()
        assertEquals("pressing $tag reaches the PC", flag, buttons())
        touch(MotionEvent.ACTION_UP, at, t)
        SystemClock.sleep(150)
        instr.waitForIdleSync()
        assertEquals("letting go of $tag reaches the PC", 0, buttons())
    }

    private fun setControls(on: Boolean) {
        instr.runOnMainSync { sp.edit().putBoolean("checkbox_show_onscreen_controls", on).apply() }
        compose.waitForIdle()
        if (on) compose.waitUntil(3_000) { exists("osc:a") || exists("osc:b") } else compose.waitUntil(3_000) { !exists("osc:a") && !exists("osc:b") }
        SystemClock.sleep(300)
        compose.waitForIdle()
    }

    private fun longPressBall() {
        val at = bounds("float-ball").center
        val t = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, at, t)
        instr.waitForIdleSync()
        // Gesture timeouts run on the test's composition clock: move it past the long-press timeout.
        compose.mainClock.advanceTimeBy(android.view.ViewConfiguration.getLongPressTimeout() + 200L)
        instr.waitForIdleSync()
        touch(MotionEvent.ACTION_UP, at, t)
        instr.waitForIdleSync()
        compose.waitForIdle()
        SystemClock.sleep(300)
        compose.waitForIdle()
    }

    /** The stream menu's "Controls" quick toggle, then Back to close the menu. */
    private fun quickToggleControls() {
        instr.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { exists("quick:controls") }
        compose.onNodeWithTag("quick:controls").performClick()
        compose.waitForIdle()
        instr.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5_000) { !exists("quick:controls") }
        compose.waitForIdle()
        SystemClock.sleep(300)
        compose.waitForIdle()
    }

    @Test fun quickToggleInTheMenuDrivesThePad() {
        quickToggleControls()
        compose.waitUntil(3_000) { exists("osc:a") }
        assertTrue(sp.getBoolean("checkbox_show_onscreen_controls", false))
        pressAndCheck("osc:a", PadFlags.A)
        quickToggleControls()
        compose.waitUntil(3_000) { !exists("osc:a") }
        quickToggleControls()
        compose.waitUntil(3_000) { exists("osc:a") }
        pressAndCheck("osc:a", PadFlags.A)
    }

    @Test fun turnedOnMidStreamDrivesThePad() {
        val announced = demo.arrivals.size
        setControls(true)
        // Player 1 is announced as the controls come up, before the first press.
        assertEquals(announced + 1, demo.arrivals.size)
        pressAndCheck("osc:a", PadFlags.A)
    }

    @Test fun offOnOffOnStillDrivesThePad() {
        setControls(true)
        pressAndCheck("osc:a", PadFlags.A)
        setControls(false)
        setControls(true)
        pressAndCheck("osc:a", PadFlags.A)
    }

    @Test fun layoutSwitchAfterTurningOnMidStream() {
        setControls(true)
        val at = bounds("osc:next").center
        val t = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, at, t)
        SystemClock.sleep(80)
        touch(MotionEvent.ACTION_UP, at, t)
        compose.waitUntil(3_000) { exists("osc:b") }
        SystemClock.sleep(300)
        pressAndCheck("osc:b", PadFlags.B)
    }

    @Test fun floatBallLongPressTogglesAndDrives() {
        longPressBall()
        compose.waitUntil(3_000) { exists("osc:a") }
        pressAndCheck("osc:a", PadFlags.A)
        // A second long press turns them off again, a third back on.
        longPressBall()
        compose.waitUntil(3_000) { !exists("osc:a") }
        assertFalse(sp.getBoolean("checkbox_show_onscreen_controls", true))
        longPressBall()
        compose.waitUntil(3_000) { exists("osc:a") }
        assertTrue(sp.getBoolean("checkbox_show_onscreen_controls", false))
        pressAndCheck("osc:a", PadFlags.A)
    }
}
