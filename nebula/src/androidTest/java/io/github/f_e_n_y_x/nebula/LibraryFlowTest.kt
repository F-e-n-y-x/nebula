package io.github.f_e_n_y_x.nebula

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the debug demo host: library → details → play choices, all reachable. */
@RunWith(AndroidJUnit4::class)
class LibraryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun libraryOpensDetailsWithBothPlayModes() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Details").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Details").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Virtual display")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Desktop (Mirror)").assertIsDisplayed()
        compose.onNodeWithText("Virtual display").assertIsDisplayed()
    }

    @Test fun settingsShowsStreamOptions() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Settings").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Stream").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Stream").onFirst().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Frame rate").fetchSemanticsNodes().isNotEmpty() }
    }
}
