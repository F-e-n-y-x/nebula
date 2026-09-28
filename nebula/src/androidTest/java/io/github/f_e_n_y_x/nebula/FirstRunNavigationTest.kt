package io.github.f_e_n_y_x.nebula

import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First-run setup against the demo host with every PC unpaired (`start=firstrun`): onboarding →
 * Find my PC → pair → "Paired". The owner's dev6 bug: pressing Back there instead of "Open library"
 * left the Library tab dead. Works on phone (bottom bar) and tablet/TV (rail): both expose the
 * sections as Role.Tab nodes carrying the label.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("start", "firstrun")
        scenario = ActivityScenario.launch(intent)
    }

    @After fun close() = scenario.close()

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    private fun waitFor(text: String, ms: Long = 10_000) = compose.waitUntil(ms) { shown(text) }

    private fun back() {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun finishing(): Boolean { var f = false; scenario.onActivity { f = it.isFinishing }; return f }

    private fun tab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun openTab(label: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(tab(label)).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(tab(label)).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun assertOnLibrary() {
        waitFor("Details")
        assertTrue("library content", shown("Play"))
        assertTrue("Library tab selected", compose.onAllNodes(tab("Library") and isSelected()).fetchSemanticsNodes().isNotEmpty())
        assertFalse(finishing())
    }

    /** Onboarding → Hosts → pair "atom" → the "Paired with atom" screen. */
    private fun completeSetup() {
        waitFor("Find my PC")
        compose.onAllNodes(hasText("Find my PC")).onFirst().performClick()
        waitFor("atom")
        compose.onAllNodes(hasText("atom")).onFirst().performClick()
        waitFor("Open library", ms = 20_000)
    }

    @Test fun backFromSetupCompleteThenLibraryTabShowsLibrary() {
        completeSetup()
        back()
        assertFalse("Back must not leave the app from setup-complete", finishing())
        openTab("Library")
        assertOnLibrary()
    }

    @Test fun backFromSetupCompleteLandsOnLibrary() {
        completeSetup()
        back()
        assertOnLibrary()
    }

    @Test fun everyTabWorksAfterSetupWithoutOpenLibrary() {
        completeSetup()
        back()
        openTab("Hosts")
        waitFor("Add a PC by address")
        openTab("Library")
        assertOnLibrary()
        openTab("Settings")
        waitFor("Audio & microphone")
        openTab("Library")
        assertOnLibrary()
        // Back from a section root returns to the new home, and from there leaves the app.
        openTab("Hosts")
        waitFor("Add a PC by address")
        back()
        assertOnLibrary()
        back()
        assertTrue(finishing())
    }
}
