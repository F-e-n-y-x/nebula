package io.github.f_e_n_y_x.nebula.ui

import androidx.compose.runtime.mutableStateListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Top-level navigation rules, without the UI. [paired] stands in for the live paired-host list. */
class NavigatorTest {
    private var paired = emptyList<String>()
    private val stack = mutableStateListOf<Route>(Route.Onboarding)
    private val nav = Navigator(stack) { paired }

    /** What NebulaApp does on each composition: remember the library shown last. */
    private fun compose() { (stack.lastOrNull { it is Route.Library } as? Route.Library)?.let { nav.lastLibrary = it } }

    /** Onboarding → Find my PC → Hosts → pair "atom" → paired. */
    private fun completeSetup() {
        nav.top(Route.Hosts); compose()
        nav.push(Route.Pair("atom")); compose()
        paired = listOf("atom")
    }

    @Test fun libraryTabWorksAfterBackingOutOfSetupComplete() {
        completeSetup()
        assertTrue(nav.back()); compose()
        nav.top(Route.Hosts); compose() // the Hosts tab, then the Library tab
        nav.top(nav.home); compose()
        assertEquals(listOf<Route>(Route.Library("atom")), stack.toList())
    }

    @Test fun backFromSetupCompleteLandsOnTheNewLibrary() {
        completeSetup()
        assertTrue(nav.back()); compose()
        assertEquals(listOf<Route>(Route.Library("atom")), stack.toList())
        assertTrue(nav.isAtHome)
        assertFalse("Back from the library leaves the app", nav.back())
    }

    @Test fun backFromPairBeforePairingJustPops() {
        nav.top(Route.Hosts); nav.push(Route.Pair("atom"))
        assertTrue(nav.back())
        assertEquals(listOf<Route>(Route.Hosts), stack.toList())
        assertTrue("no PC paired: Hosts is home", nav.isAtHome)
    }

    @Test fun sectionRootsReturnToTheLibraryOncePaired() {
        completeSetup(); nav.back(); compose()
        for (section in listOf(Route.Hosts, Route.Settings())) {
            nav.top(section); compose()
            assertFalse(nav.isAtHome)
            assertTrue(nav.back()); compose()
            assertEquals(listOf<Route>(Route.Library("atom")), stack.toList())
        }
    }

    @Test fun libraryTabWithoutAPairedPcOpensHosts() {
        nav.top(Route.Settings())
        nav.top(nav.home)
        assertEquals(listOf<Route>(Route.Hosts), stack.toList())
    }

    @Test fun libraryTabPrefersTheLastLibraryWhileItsPcIsPaired() {
        paired = listOf("atom", "deck")
        nav.top(Route.Library("deck")); compose()
        nav.top(Route.Hosts); compose()
        assertEquals(Route.Library("deck"), nav.home)
        paired = listOf("atom") // "deck" was removed or unpaired
        assertEquals(Route.Library("atom"), nav.home)
        paired = emptyList()
        assertEquals(Route.Hosts, nav.home)
    }
}
