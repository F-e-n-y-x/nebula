package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.ui.screens.playtime
import io.github.f_e_n_y_x.nebula.ui.screens.relativeAgo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DomainTest {
    private fun game(id: String, kind: GameKind = GameKind.GAME, name: String = id, played: Long? = null, running: Boolean = false, hostMode: DisplayMode? = null) =
        Game(id, "h", name, kind, GameArt(), running = running, lastPlayedEpochS = played, hostDefaultMode = hostMode)

    @Test fun rememberedModeWins() {
        val g = game("gta5", hostMode = DisplayMode.MIRROR)
        assertEquals(DisplayMode.VIRTUAL, ResolvePlayModeUseCase.resolve(g, DisplayMode.VIRTUAL, StreamSettings()))
    }

    @Test fun hostDefaultBeatsGlobalDefault() {
        val g = game("gta5", hostMode = DisplayMode.MIRROR)
        assertEquals(DisplayMode.MIRROR, ResolvePlayModeUseCase.resolve(g, null, StreamSettings(defaultMode = DisplayMode.VIRTUAL)))
    }

    @Test fun desktopsFollowTheirName() {
        val mirror = game("d1", GameKind.DESKTOP, "Desktop (Mirror)")
        val virtual = game("d2", GameKind.DESKTOP, "Desktop (Virtual display)")
        assertEquals(DisplayMode.MIRROR, ResolvePlayModeUseCase.resolve(mirror, null, StreamSettings(defaultMode = DisplayMode.VIRTUAL)))
        assertEquals(DisplayMode.VIRTUAL, ResolvePlayModeUseCase.resolve(virtual, null, StreamSettings(defaultMode = DisplayMode.MIRROR)))
    }

    @Test fun gamesUseGlobalDefault() {
        assertEquals(DisplayMode.MIRROR, ResolvePlayModeUseCase.resolve(game("x"), null, StreamSettings(defaultMode = DisplayMode.MIRROR)))
    }

    @Test fun libraryOrderRunningThenGamesByRecencyThenDesktops() {
        val sorted = SortLibrary(
            listOf(
                game("desk", GameKind.DESKTOP, played = 999),
                game("old", played = 10),
                game("new", played = 500),
                game("never"),
                game("live", played = 1, running = true),
            ),
        ).map { it.id }
        assertEquals(listOf("live", "new", "old", "never", "desk"), sorted)
    }

    @Test fun formatting() {
        val now = System.currentTimeMillis() / 1000
        assertEquals("2 days ago", relativeAgo(now - 2 * 86_400 - 5))
        assertEquals("3 h ago", relativeAgo(now - 3 * 3_600 - 5))
        assertNull(relativeAgo(null))
        assertEquals("42 h played", playtime(42 * 3_600))
        assertEquals("15 min played", playtime(900))
        assertNull(playtime(0))
    }
}
