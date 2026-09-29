package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.graphics.Bitmap
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
import io.github.f_e_n_y_x.nebula.controls.SwitchTarget
import io.github.f_e_n_y_x.nebula.controls.newElement
import io.github.f_e_n_y_x.nebula.input.LoggingInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Layout sets on the demo stream: a switch element changes layout at once and lets go of what
 * was held; a picker switch opens the list. Real touches through the window; assertions read
 * what the demo PC got. Screenshots land in the app's external files dir (osc-sets-*.png).
 */
@RunWith(AndroidJUnit4::class)
class LayoutSetDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var origin = Offset.Zero
    private var setId = ""

    private fun sw(id: String, x: Float, to: SwitchTarget = SwitchTarget.Next) =
        newElement(ElementKind.SWITCH, id, x, 0.08f).copy(width = 110f, height = 40f, shape = ElementShape.PILL, switchTo = to)

    private val foot = ControlsProfile(
        "p-settest-foot", "On foot",
        listOf(
            ControlElement("a", ElementKind.BUTTON, 0.85f, 0.7f, 64f, 64f, label = "A", bindings = listOf(Binding.Pad(PadFlags.A))),
            ControlElement("lbrb", ElementKind.BUTTON, 0.7f, 0.7f, 64f, 64f, label = "LB+RB", bindings = listOf(Binding.Pad(PadFlags.LB), Binding.Pad(PadFlags.RB))),
            sw("next", 0.5f), sw("pick", 0.7f, SwitchTarget.Picker),
        ),
    )
    private val car = ControlsProfile(
        "p-settest-car", "Vehicle",
        listOf(ControlElement("b", ElementKind.BUTTON, 0.15f, 0.7f, 64f, 64f, label = "B", bindings = listOf(Binding.Pad(PadFlags.B))), sw("next", 0.5f), sw("pick", 0.7f, SwitchTarget.Picker)),
    )
    private val plane = ControlsProfile(
        "p-settest-plane", "Aircraft",
        listOf(ControlElement("y", ElementKind.BUTTON, 0.5f, 0.6f, 64f, 64f, label = "Y", bindings = listOf(Binding.Pad(PadFlags.Y))), sw("back", 0.5f, SwitchTarget.Previous)),
    )

    @Before fun setUp() {
        ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("checkbox_show_onscreen_controls", true)
            .putString("nebula_gyro_mode", "off")
            .commit()
        ControlsStore.get(ctx).update { lib ->
            val saved = lib.save(foot).first.save(car).first.save(plane).first
            val (withSet, set) = saved.createSet("Set test", listOf(foot.id, car.id, plane.id))!!
            setId = set.id
            // Aircraft only from the picker.
            withSet.updateSet(set.copy(cycle = listOf(foot.id, car.id))).setDefault(set.id)
        }
        scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra("start", "mirror:gta5"))
        compose.waitUntil(20_000) {
            var landscape = false
            scenario.onActivity { landscape = it.window.decorView.width > it.window.decorView.height }
            landscape && exists("osc:a")
        }
        SystemClock.sleep(1500)
        compose.waitForIdle()
        scenario.onActivity { a ->
            val loc = IntArray(2)
            a.window.decorView.getLocationOnScreen(loc)
            origin = Offset(loc[0].toFloat(), loc[1].toFloat())
        }
    }

    @After fun tearDown() {
        scenario.close()
        ControlsStore.get(ctx).update { lib ->
            lib.setDefault(ControlsProfile.STANDARD_ID).deleteSet(setId).delete(foot.id).delete(car.id).delete(plane.id)
        }
    }

    private val demo: LoggingInput get() = ctx.container.stream.remoteInput as LoggingInput
    private fun buttons() = (demo.lastPad ?: IntArray(8))[1]
    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow

    private var down = 0L
    private val points = sortedMapOf<Int, Offset>()

    private fun send(action: Int, actionPointer: Int = 0) {
        val ids = points.keys.toList()
        val props = ids.map { id -> MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
        val coords = ids.map { id -> MotionEvent.PointerCoords().apply { val p = points.getValue(id) + origin; x = p.x; y = p.y; pressure = 1f; size = 1f } }.toTypedArray()
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

    private fun tap(tag: String) {
        fingerDown(9, bounds(tag).center)
        SystemClock.sleep(60)
        fingerUp(9)
        instr.waitForIdleSync()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        SystemClock.sleep(300)
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val dir = ctx.getExternalFilesDir(null) ?: return
        File(dir, "osc-sets-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun switchChangesLayoutAndReleasesWhatWasHeld() {
        shot("1-on-foot")
        // Hold A and LB+RB, then tap the switch with another finger.
        fingerDown(0, bounds("osc:a").center)
        fingerDown(1, bounds("osc:lbrb").center)
        SystemClock.sleep(120)
        instr.waitForIdleSync()
        assertEquals(PadFlags.A or PadFlags.LB or PadFlags.RB, buttons())
        fingerDown(2, bounds("osc:next").center)
        SystemClock.sleep(80)
        fingerUp(2)
        instr.waitForIdleSync()
        compose.waitUntil(3_000) { exists("osc:b") }
        SystemClock.sleep(200) // the minimum hold and the resend
        assertEquals("switching let go of A and LB+RB", 0, buttons())
        assertTrue(!exists("osc:a"))
        // The fingers still down on the old layout do nothing now, and lifting them sends nothing.
        fingerUp(1); fingerUp(0)
        instr.waitForIdleSync()
        assertEquals(0, buttons())
        shot("2-vehicle")
        // B works on the new layout.
        fingerDown(0, bounds("osc:b").center)
        SystemClock.sleep(80)
        assertEquals(PadFlags.B, buttons())
        fingerUp(0)
        SystemClock.sleep(150)
        // Next again wraps back to On foot (Aircraft is outside the cycle).
        tap("osc:next")
        compose.waitUntil(3_000) { exists("osc:a") }
        assertEquals(0, buttons())
    }

    @Test fun pickerSwitchOpensTheListAndGoesStraightThere() {
        tap("osc:pick")
        compose.waitUntil(3_000) { exists("layout-picker") }
        shot("3-picker")
        compose.onNodeWithTag("pick-layout:${plane.id}").performClick()
        compose.waitUntil(3_000) { exists("osc:y") }
        shot("4-aircraft")
        // Previous from outside the cycle goes to the cycle's last layout.
        tap("osc:back")
        compose.waitUntil(3_000) { exists("osc:b") }
    }
}
