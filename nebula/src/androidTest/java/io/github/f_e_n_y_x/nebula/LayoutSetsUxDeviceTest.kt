package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.ProfileGroup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * dev24 on the demo build: Browse layouts shows GTA V as one card and adds its set; the editor's
 * Profiles list shows the set as one item; the New set flow makes a set whose layouts all get a
 * switch; the stream menu's section rail scrolls to a section. Screenshots (dev24-*.png) land in
 * the app's external files dir. Browse needs the network (the public library); without it that
 * part is skipped, the rest still runs.
 */
@RunWith(AndroidJUnit4::class)
class LayoutSetsUxDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val instr = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val store get() = ControlsStore.get(ctx)

    @Before fun setUp() {
        ctx.getSharedPreferences(ctx.packageName + "_preferences", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("checkbox_show_onscreen_controls", true)
            .putString("nebula_gyro_mode", "off")
            .commit()
        clean()
    }

    @After fun tearDown() {
        scenario?.close()
        clean()
    }

    private fun clean() = store.update { l ->
        var x = l.setDefault(ControlsProfile.STANDARD_ID)
        for (s in x.data.sets) x = x.deleteSet(s.id, withLayouts = true)
        for (p in x.data.profiles) x = x.delete(p.id)
        x
    }

    private fun launch(start: String) {
        scenario = ActivityScenario.launch(Intent(ctx, MainActivity::class.java).putExtra("start", start))
    }

    private fun exists(tag: String) = runCatching { compose.onNodeWithTag(tag).fetchSemanticsNode() }.isSuccess
    private fun waitTag(tag: String, ms: Long = 15_000) = compose.waitUntil(ms) { exists(tag) }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick(); compose.waitForIdle() }
    private fun clickNoScroll(tag: String) { compose.onNodeWithTag(tag).performClick(); compose.waitForIdle() }

    private fun shot(name: String) {
        compose.waitForIdle()
        SystemClock.sleep(600)
        val bmp = instr.uiAutomation.takeScreenshot() ?: return
        val dir = ctx.getExternalFilesDir(null) ?: return
        File(dir, "dev24-$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openProfiles() {
        if (!exists("browse-layouts")) clickNoScroll("editor-profiles")
        waitTag("browse-layouts")
    }

    @Test fun browseProfilesAndNewSet() {
        launch("controls:gta5")
        waitTag("editor-profiles", 20_000)
        openProfiles()

        // ---- Browse layouts: one card for GTA V; get its controller set.
        click("browse-layouts")
        val gtaCard = "library-card:steam:271590"
        val online = runCatching { compose.waitUntil(30_000) { exists(gtaCard) } }.isSuccess
        if (online) {
            assertEquals("one card for GTA V", 1, compose.onAllNodes(hasTestTag(gtaCard)).fetchSemanticsNodes().size)
            compose.onNodeWithTag(gtaCard).performScrollTo()
            shot("browse-gta-card")
            click("variant:gta-v/touch-sets-keyboard-mouse")
            shot("browse-gta-card-keyboard")
            click("variant:gta-v/touch-controls")
            shot("browse-gta-card-older")
            click("variant:gta-v/touch-sets-controller")
            click("library-get:steam:271590")
            waitTag("layout-add", 30_000)
            shot("browse-gta-preview")
            clickNoScroll("layout-add")
            compose.waitUntil(10_000) { store.data.value.sets.isNotEmpty() }
            val set = store.data.value.sets.single()
            assertEquals(5, set.members.size)

            // ---- The profiles list: GTA V is one item; its layouts aren't listed loose.
            openProfiles()
            waitTag("group:${set.id}")
            compose.onNodeWithTag("group:${set.id}").performScrollTo()
            shot("profiles-grouped-set")
            for (m in set.members) assertTrue("$m listed loose", !exists("profile:$m"))
        } else {
            compose.onNodeWithTag("layout-browser").fetchSemanticsNode()
            instr.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            compose.waitForIdle()
            openProfiles()
        }

        // ---- New set: name, a new layout, a copy, Standard moved in (copied); then create.
        click("editor-new-set")
        waitTag("new-set-flow")
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("set-name"))).performTextInput("Racing")
        click("set-add-new")
        click("set-add-copy")
        waitTag("pick-profile")
        compose.onAllNodes(hasTestTag("pick:${ControlsProfile.STANDARD_ID}")).onFirst().performClick()
        compose.waitForIdle()
        click("set-add-existing")
        waitTag("pick-profile")
        compose.onAllNodes(hasTestTag("pick:${ControlsProfile.STANDARD_ID}")).onFirst().performClick()
        compose.waitForIdle()
        click("draft-layout:1:start")
        compose.onNodeWithTag("draft-layout:0").performScrollTo()
        shot("new-set-flow")
        compose.onNodeWithTag("create-set").performScrollTo()
        shot("new-set-flow-bottom")
        click("create-set")
        compose.waitUntil(10_000) { store.data.value.sets.any { it.name == "Racing" } }
        val racing = store.library().sets.first { it.name == "Racing" }
        assertEquals(3, racing.members.size)
        val lib = store.library()
        for (id in racing.members) {
            val p = lib.find(id)!!
            assertEquals("${p.name} has one switch", 1, p.landscape.count { it.kind == ElementKind.SWITCH })
        }
        assertEquals(racing.members[1], racing.startId())

        openProfiles()
        waitTag("group:${racing.id}")
        compose.onNodeWithTag("group:${racing.id}").performScrollTo()
        shot("profiles-two-sets")
        val groups = store.library().groups()
        assertEquals(store.library().sets.size, groups.count { it is ProfileGroup.SetItem })
    }

    @Test fun streamMenuRail() {
        // A set for the game, so the Controls section shows it grouped.
        store.update { l ->
            val (withSet, set) = l.newSet("GTA V", listOf(
                io.github.f_e_n_y_x.nebula.controls.SetSource.Blank("On foot"),
                io.github.f_e_n_y_x.nebula.controls.SetSource.Blank("Vehicle"),
                io.github.f_e_n_y_x.nebula.controls.SetSource.Blank("Aircraft"),
            ))!!
            withSet.setDefault(set.id)
        }
        launch("stream:gta5")
        compose.waitUntil(20_000) { exists("osc:switch") }
        SystemClock.sleep(1500)
        scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitTag("menu-rail")
        shot("rail-landscape")
        click("rail:controls")
        SystemClock.sleep(900)
        shot("rail-landscape-controls")
        assertTrue(exists("menu-set-layouts"))
        click("rail:framegen")
        SystemClock.sleep(900)
        shot("rail-landscape-framegen")
        click("rail:end")
        SystemClock.sleep(900)
        shot("rail-landscape-end")

        // Portrait: rotate the stream from the menu, then open the menu again (a bottom sheet).
        click("rail:display")
        SystemClock.sleep(900)
        compose.onAllNodes(hasText("Rotate to portrait")).onFirst().performScrollTo().performClick()
        var portrait = false
        runCatching {
            compose.waitUntil(15_000) {
                scenario!!.onActivity { portrait = it.window.decorView.height > it.window.decorView.width }
                portrait
            }
        }
        SystemClock.sleep(2500)
        if (!exists("menu-rail")) scenario!!.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitTag("menu-rail")
        shot("rail-portrait")
        click("rail:network")
        SystemClock.sleep(900)
        shot("rail-portrait-network")
        click("rail:controls")
        SystemClock.sleep(900)
        shot("rail-portrait-controls")
    }
}
