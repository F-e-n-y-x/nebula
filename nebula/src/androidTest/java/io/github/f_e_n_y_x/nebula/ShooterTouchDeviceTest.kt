package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.LibrarySource
import io.github.f_e_n_y_x.nebula.controls.PadFlags
import io.github.f_e_n_y_x.nebula.controls.ui.ShooterTutorial
import io.github.f_e_n_y_x.nebula.input.LoggingInput
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Touch-shooter controls on the demo stream (mock host, no PC): real multi-finger touches injected
 * through the window like fingers (the ZoneTouchDeviceTest harness), reading what the pad
 * mapper and mouse send to the demo PC. Screenshots go to the app's files/shots for the QA script.
 */
@RunWith(AndroidJUnit4::class)
class ShooterTouchDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var origin = Offset.Zero
    private val prefs get() = ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE)
    private val libraryUrl: String get() = InstrumentationRegistry.getArguments().getString("libraryUrl") ?: LibrarySource.LAN_SAMPLE

    private fun launch(profileId: String, start: String = "mirror:gta5", tag: String = "osc:fire", tutorialSeen: Boolean = true) {
        prefs.edit()
            .putBoolean("checkbox_show_onscreen_controls", true)
            .putString("nebula_gyro_mode", "off")
            .putBoolean(ShooterTutorial.SEEN_KEY, tutorialSeen)
            .remove("nebula_osc_outside_touch:$profileId")
            .remove("nebula_osc_look_output:$profileId")
            .putString(LibrarySource.PREF_KEY, libraryUrl)
            .commit()
        // The shooter presets are no longer bundled: play them as the user's own copy.
        val id = io.github.f_e_n_y_x.nebula.controls.RetiredPresets.copyOf(profileId)?.let { p ->
            ControlsStore.get(ctx).update { l -> if (l.find(p.id) == null) l.save(p).first else l }
            p.id
        } ?: profileId
        ControlsStore.get(ctx).update { it.setDefault(id) }
        scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra("start", start))
        compose.waitUntil(20_000) {
            var landscape = false
            scenario!!.onActivity { landscape = it.window.decorView.width > it.window.decorView.height }
            landscape && runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
        }
        SystemClock.sleep(1500)
        compose.waitForIdle()
        scenario!!.onActivity { a ->
            val loc = IntArray(2)
            a.window.decorView.getLocationOnScreen(loc)
            origin = Offset(loc[0].toFloat(), loc[1].toFloat())
        }
    }

    @After fun tearDown() {
        scenario?.close()
        ControlsStore.get(ctx).update { lib ->
            var l = lib.setDefault(ControlsProfile.STANDARD_ID)
            l.data.profiles.filter { it.origin == "library" }.forEach { l = l.delete(it.id) }
            l
        }
    }

    private val demo: LoggingInput get() = ctx.container.stream.remoteInput as LoggingInput
    private fun pad() = demo.lastPad ?: IntArray(8)
    private fun buttons() = pad()[1]
    private fun rt() = pad()[3]
    private fun ly() = pad()[5]

    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
    private fun Offset.screen() = this + origin
    private var down = 0L
    private val points = sortedMapOf<Int, Offset>()
    private val density get() = ctx.resources.displayMetrics.density

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

    private fun drag(id: Int, dx: Float, dy: Float, steps: Int = 10, check: () -> Unit = {}) {
        repeat(steps) {
            points[id] = points.getValue(id) + Offset(dx / steps, dy / steps)
            send(MotionEvent.ACTION_MOVE)
            check()
            SystemClock.sleep(16)
        }
    }

    private fun shot(name: String) {
        instr.waitForIdleSync()
        SystemClock.sleep(250)
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        val dir = File(ctx.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "shooter-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ---------------------------------------------------------------- touch

    @Test fun leftFireShootsWhileTheRightThumbLooks() {
        launch(ControlsProfile.GTA_TOUCH_CONTROLS_ID)
        val fire = bounds("osc:fire")
        val eye = bounds("osc:eye")
        // Right thumb on a free spot between the eye and the fire button: looks.
        val look = Offset(fire.left - 60 * density, (eye.bottom + fire.top) / 2)
        fingerDown(0, look)
        drag(0, 60f, 0f, steps = 4)
        val before = demo.pointerEvents.get()
        fingerDown(1, bounds("osc:fire-left").center)
        assertEquals("left fire: RT", 255, rt())
        var lookedWhileFiring = false
        drag(0, 200f, -40f, steps = 10) { if (rt() == 255 && demo.pointerEvents.get() > before) lookedWhileFiring = true }
        shot("left-fire-look")
        assertTrue("the camera turned while RT was held", lookedWhileFiring)
        fingerUp(1); fingerUp(0)
        instr.waitForIdleSync(); SystemClock.sleep(120)
        assertEquals(0, rt())
    }

    @Test fun theRightFireButtonAimsWhenDragged() {
        launch(ControlsProfile.GTA_TOUCH_CONTROLS_ID)
        val fire = bounds("osc:fire")
        val before = demo.pointerEvents.get()
        fingerDown(0, fire.center)
        assertEquals(255, rt())
        drag(0, -8 * density, 0f, steps = 2)
        assertTrue("aiming starts within a few dp", demo.pointerEvents.get() > before)
        drag(0, -150f, -60f, steps = 8)
        shot("drag-fire")
        assertEquals("still firing while aiming", 255, rt())
        fingerUp(0)
        instr.waitForIdleSync(); SystemClock.sleep(120)
        assertEquals(0, rt())
    }

    @Test fun pushingPastTheRingSprints() {
        launch(ControlsProfile.GTA_TOUCH_CONTROLS_ID)
        val move = bounds("osc:move")
        val start = Offset(move.left + move.width * 0.45f, move.bottom - move.height * 0.25f)
        val ring = 64 * density
        fingerDown(0, start)
        drag(0, 0f, -ring * 0.8f, steps = 4)
        assertEquals("inside the ring: no sprint (GTA's A)", 0, buttons() and PadFlags.A)
        assertTrue("moving forward: ly=${ly()}", ly() > 20000)
        drag(0, 0f, -ring * 0.6f, steps = 3)
        assertTrue("past the ring: A held", buttons() and PadFlags.A != 0)
        shot("auto-sprint")
        fingerUp(0)
        instr.waitForIdleSync(); SystemClock.sleep(120)
        assertEquals(0, buttons() and PadFlags.A)
        assertEquals(0, ly())
    }

    @Test fun dragUpLocksTheRun() {
        launch(ControlsProfile.GTA_TOUCH_CONTROLS_ID)
        val move = bounds("osc:move")
        val start = Offset(move.left + move.width * 0.45f, move.bottom - move.height * 0.15f)
        val ring = 64 * density
        fingerDown(0, start)
        drag(0, 0f, -ring * 2.35f, steps = 8)
        shot("run-lock-armed")
        fingerUp(0)
        instr.waitForIdleSync(); SystemClock.sleep(200)
        assertEquals("locked: full forward with no finger", 32766, ly())
        assertTrue("and sprinting", buttons() and PadFlags.A != 0)
        shot("run-locked")
        // Tap the move area to stop.
        fingerDown(0, start); SystemClock.sleep(30); fingerUp(0)
        instr.waitForIdleSync(); SystemClock.sleep(150)
        assertEquals(0, ly())
        assertEquals(0, buttons() and PadFlags.A)
    }

    // ---------------------------------------------------------------- editor: style, tutorial, share, library

    @Test fun importFromTheSampleLibrary() {
        launchEditor()
        compose.onNodeWithContentDescription("Profiles").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("browse-layouts").performScrollTo().performClick()
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("library:genre-shooter/touch-shooter-controller").fetchSemanticsNode() }.isSuccess }
        SystemClock.sleep(500)
        shot("library-browse")
        // The genre filter narrows it to one genre's templates and games.
        compose.onNodeWithTag("genre:shooter").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("library-section:Shooter templates").assertExists()
        shot("library-genre-shooter")
        compose.onNodeWithTag("library:genre-shooter/touch-shooter-controller").performClick()
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithTag("layout-preview").fetchSemanticsNode() }.isSuccess }
        shot("library-preview")
        compose.onNodeWithTag("layout-add").performClick()
        compose.waitForIdle()
        SystemClock.sleep(500)
        val added = ControlsStore.get(ctx).data.value.profiles.filter { it.origin == "library" }
        // The built-in preset has that name already, so the copy is numbered.
        assertEquals(1, added.size)
        assertTrue(added.single().name, added.single().name.startsWith("Touch shooter · controller"))
        assertTrue(added.single().landscape.any { it.id == "fire" && it.lookThrough })
        shot("library-added")
    }

    @Test fun layoutLinkOpensAPreview() {
        val url = libraryUrl.substringBeforeLast('/') + "/layouts/gta-v/touch-controls.json"
        prefs.edit().putBoolean(ShooterTutorial.SEEN_KEY, true).commit()
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("nebula://layout?url=" + Uri.encode(url))).setClass(ctx, MainActivity::class.java)
        scenario = ActivityScenario.launch(view)
        compose.waitUntil(25_000) { runCatching { compose.onNodeWithTag("layout-preview").fetchSemanticsNode() }.isSuccess }
        shot("deep-link-preview")
        compose.onNodeWithTag("layout-add").performClick()
        compose.waitForIdle(); SystemClock.sleep(400)
        assertTrue(ControlsStore.get(ctx).data.value.profiles.any { it.origin == "library" && it.name.startsWith("GTA V · touch controls") })
    }

    @Test fun editorStyleTutorialShareAndQr() {
        launchEditor(tutorialSeen = false)
        compose.onNodeWithContentDescription("Style: start from a layout").performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("style-picker").fetchSemanticsNode() }.isSuccess }
        shot("style-picker")
        // Only Standard is bundled now; the tutorial is still one tap away.
        compose.onNodeWithText("How shooter controls work", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("shooter-tutorial").fetchSemanticsNode() }.isSuccess }
        shot("tutorial")
        compose.onNodeWithTag("tutorial-done").performClick()
        compose.waitForIdle()
        shot("editor-shooter")
        compose.onNodeWithContentDescription("Profiles").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Share", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("share-layout").fetchSemanticsNode() }.isSuccess }
        shot("share")
        compose.onNodeWithText("QR code", useUnmergedTree = true).performScrollTo().performClick()
        compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag("layout-qr").fetchSemanticsNode() }.isSuccess }
        shot("qr")
    }

    private fun launchEditor(tutorialSeen: Boolean = true) {
        prefs.edit()
            .putBoolean(ShooterTutorial.SEEN_KEY, tutorialSeen)
            .putString(LibrarySource.PREF_KEY, libraryUrl)
            .commit()
        ControlsStore.get(ctx).update { it.setDefault(ControlsProfile.STANDARD_ID) }
        scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra("start", "controls"))
        compose.waitUntil(20_000) { runCatching { compose.onNodeWithContentDescription("Style: start from a layout").fetchSemanticsNode() }.isSuccess }
        SystemClock.sleep(800)
    }
}
