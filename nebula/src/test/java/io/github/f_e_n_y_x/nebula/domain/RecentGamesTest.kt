package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentGamesTest {
    private fun play(game: String, at: Long, host: String = "h1", mode: DisplayMode = DisplayMode.VIRTUAL) =
        RecentPlay(host, "old-name", game, game.uppercase(), mode, at)

    private fun game(id: String, played: Long? = null, kind: GameKind = GameKind.GAME, name: String = id) =
        Game(id, "h1", name, kind, GameArt(), lastPlayedEpochS = played)

    @Test fun addPutsNewestFirstAndReplacesTheSameGame() {
        var list = emptyList<RecentPlay>()
        list = RecentGames.add(list, play("a", 1))
        list = RecentGames.add(list, play("b", 2))
        list = RecentGames.add(list, play("a", 3, mode = DisplayMode.MIRROR))
        assertEquals(listOf("a", "b"), list.map { it.gameId })
        assertEquals(DisplayMode.MIRROR, list.first().mode)
    }

    @Test fun sameGameOnAnotherHostIsSeparate() {
        val list = RecentGames.add(listOf(play("a", 1, host = "h1")), play("a", 2, host = "h2"))
        assertEquals(2, list.size)
    }

    @Test fun addIsCapped() {
        var list = emptyList<RecentPlay>()
        repeat(30) { list = RecentGames.add(list, play("g$it", it.toLong())) }
        assertEquals(RecentGames.MAX_STORED, list.size)
        assertEquals("g29", list.first().gameId)
    }

    @Test fun quickConnectOnlyOffersPairedHostsWithCurrentNames() {
        val hosts = listOf(
            Host("h1", "atom", "a", HostStatus.ONLINE, paired = true, isNova = true),
            Host("h2", "den", "b", HostStatus.ONLINE, paired = false, isNova = false),
        )
        val recents = listOf(play("x", 5, host = "h2"), play("a", 4, host = "h1"), play("b", 3, host = "gone"), play("c", 2, host = "h1"))
        val out = RecentGames.forQuickConnect(recents, hosts, limit = 3)
        assertEquals(listOf("a", "c"), out.map { it.gameId })
        assertTrue(out.all { it.hostName == "atom" })
    }

    @Test fun quickConnectRespectsTheLimitAndOrder() {
        val hosts = listOf(Host("h1", "atom", "a", HostStatus.ONLINE, paired = true, isNova = true))
        val recents = listOf(play("old", 1), play("new", 9), play("mid", 5), play("older", 0))
        assertEquals(listOf("new", "mid", "old"), RecentGames.forQuickConnect(recents, hosts, limit = 3).map { it.gameId })
    }

    @Test fun continueRowPutsThisDeviceFirstThenHostHistory() {
        val games = listOf(game("gta", played = 100), game("wukong", played = 300), game("fc5", played = 200), game("never"))
        val recents = listOf(play("fc5", 50_000, mode = DisplayMode.MIRROR), play("other-host-game", 60_000, host = "h9"))
        val row = RecentGames.continueRow(recents, games, "h1", limit = 3)
        assertEquals(listOf("fc5", "wukong", "gta"), row.map { it.game.id })
        assertEquals(DisplayMode.MIRROR, row[0].mode)
        assertEquals(null, row[1].mode)
    }

    @Test fun continueRowSkipsGamesNoLongerInTheLibrary() {
        val row = RecentGames.continueRow(listOf(play("removed", 10), play("gta", 5)), listOf(game("gta")), "h1", limit = 5)
        assertEquals(listOf("gta"), row.map { it.game.id })
    }

    @Test fun recentlyPlayedExcludesContinueAndUnplayed() {
        val games = listOf(game("a", 1), game("b", 3), game("c"), game("d", 2))
        assertEquals(listOf("d", "a"), RecentGames.recentlyPlayed(games, exclude = setOf("b"), limit = 5).map { it.id })
    }

    @Test fun allGamesIsAlphabeticalWithDesktopsLast() {
        val games = listOf(game("z", name = "Zelda"), game("d", kind = GameKind.DESKTOP, name = "Desktop"), game("a", name = "alan wake"))
        assertEquals(listOf("a", "z", "d"), RecentGames.allGames(games).map { it.id })
    }
}
