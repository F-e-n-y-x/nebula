package io.github.f_e_n_y_x.nebula

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * System Back (and the TV remote / gamepad B, which arrive the same way) walks back through the
 * app and only leaves it from the library. Runs against the debug demo host on a phone in portrait.
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun openTab(label: String) {
        waitFor(label)
        compose.onAllNodesWithText(label).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun assertOnLibrary() {
        waitFor("Details")
        assertTrue(shown("Play"))
        assertFalse("details closed", shown("Desktop (Mirror)") && !shown("Details"))
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun backFromDetailsReturnsToLibrary() {
        waitFor("Details")
        compose.onAllNodesWithText("Details").onFirst().performClick()
        waitFor("Desktop (Mirror)")
        back()
        assertOnLibrary()
    }

    @Test fun backFromSettingsReturnsToLibrary() {
        openTab("Settings")
        waitFor("Display & audio")
        back()
        assertOnLibrary()
    }

    @Test fun backFromHostsReturnsToLibrary() {
        openTab("Hosts")
        waitFor("Add a PC by address")
        back()
        assertOnLibrary()
    }

    @Test fun backFromSettingsSectionReturnsToSettingsListThenLibrary() {
        openTab("Settings")
        waitFor("Display & audio")
        compose.onAllNodesWithText("Stream").onFirst().performClick()
        waitFor("Frame rate")
        back()
        waitFor("Display & audio")
        assertFalse("still in the section", shown("Frame rate"))
        back()
        assertOnLibrary()
    }

    @Test fun backFromLibraryLeavesTheApp() {
        assertOnLibrary()
        back()
        assertTrue(compose.activity.isFinishing)
    }
}
