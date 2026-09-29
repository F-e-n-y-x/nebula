package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import kotlinx.coroutines.flow.Flow

/** The home screen the user picked in Settings → Library. [AUTO] picks per device and orientation. */
enum class HomeStyle(val label: String) {
    AUTO("Auto"),
    SPOTLIGHT("Spotlight"),
    SHELF("Shelf"),
}

/** What the home screen actually draws, after [HomeStyle.AUTO] and the form factor are resolved. */
enum class HomeLayout {
    /** Compose-for-TV immersive home: hero backdrop and rows (Continue, Recently played, All games, Hosts). */
    TV_SPOTLIGHT,
    /** Spotlight (A) on a landscape phone or tablet: full-bleed hero over one tile row. */
    SPOTLIGHT_WIDE,
    /** Spotlight (A) held upright: hero card over the poster grid. */
    SPOTLIGHT_PORTRAIT,
    /** Shelf (B) on a landscape phone, tablet or TV: rail, continue cards, poster shelf. */
    SHELF_WIDE,
    /** Shelf (B) held upright: host card, continue card, poster grid. */
    SHELF_PORTRAIT,
    ;

    val isShelf get() = this == SHELF_WIDE || this == SHELF_PORTRAIT
}

object ResolveHomeLayout {
    /**
     * What Auto means (owner's decision, 2026-09-27): Spotlight on TV and in landscape, Shelf when the
     * phone or tablet is held upright, where a scrolling shelf fits the tall screen better.
     */
    fun autoStyle(isTv: Boolean, isLandscape: Boolean): HomeStyle =
        if (isTv || isLandscape) HomeStyle.SPOTLIGHT else HomeStyle.SHELF

    operator fun invoke(style: HomeStyle, isTv: Boolean, isLandscape: Boolean): HomeLayout {
        val effective = if (style == HomeStyle.AUTO) autoStyle(isTv, isLandscape) else style
        val wide = isTv || isLandscape
        return when (effective) {
            HomeStyle.SHELF -> if (wide) HomeLayout.SHELF_WIDE else HomeLayout.SHELF_PORTRAIT
            else -> when {
                isTv -> HomeLayout.TV_SPOTLIGHT
                isLandscape -> HomeLayout.SPOTLIGHT_WIDE
                else -> HomeLayout.SPOTLIGHT_PORTRAIT
            }
        }
    }
}

/**
 * A game this device streamed, kept locally so quick-connect surfaces (tile, widget, shortcuts,
 * Watch Next) work before any host answers. Names and art are a snapshot from the launch.
 */
data class RecentPlay(
    val hostId: String,
    val hostName: String,
    val gameId: String,
    val gameName: String,
    val mode: DisplayMode,
    val playedAtMs: Long,
    val poster: String? = null,
    val hero: String? = null,
) {
    val key: String get() = "$hostId/$gameId"
}

/** A tile in a "Continue" row: the game plus how it was last streamed from this device, if it was. */
data class ContinueItem(val game: Game, val mode: DisplayMode?, val playedAtMs: Long?)

interface LauncherRepository {
    val homeStyle: Flow<HomeStyle>
    suspend fun setHomeStyle(style: HomeStyle)
    /** Newest first, at most [RecentGames.MAX_STORED]. */
    val recents: Flow<List<RecentPlay>>
    suspend fun record(play: RecentPlay)
}

/** The recent-games source behind the Continue rows and every quick-connect surface. */
object RecentGames {
    const val MAX_STORED = 12

    /** Adds [play] as the newest entry, replacing an older entry for the same game on the same host. */
    fun add(list: List<RecentPlay>, play: RecentPlay): List<RecentPlay> =
        (listOf(play) + list.filterNot { it.key == play.key })
            .sortedByDescending { it.playedAtMs }
            .take(MAX_STORED)

    /**
     * Recents that quick-connect may offer: only games on hosts that are still paired (a link to an
     * unpaired host would be rejected anyway), with the host's current name.
     */
    fun forQuickConnect(recents: List<RecentPlay>, hosts: List<Host>, limit: Int): List<RecentPlay> {
        val paired = hosts.filter { it.paired }.associateBy { it.id }
        return recents.asSequence()
            .sortedByDescending { it.playedAtMs }
            .mapNotNull { r -> paired[r.hostId]?.let { r.copy(hostName = it.name) } }
            .distinctBy { it.key }
            .take(limit)
            .toList()
    }

    /**
     * The Continue row for [hostId]: games streamed from this device first (newest first, with the
     * mode used), then the host's own recently played games until [limit]. Games no longer in the
     * library are skipped.
     */
    fun continueRow(recents: List<RecentPlay>, games: List<Game>, hostId: String, limit: Int): List<ContinueItem> {
        val byId = games.associateBy { it.id }
        val local = recents.asSequence()
            .filter { it.hostId == hostId }
            .sortedByDescending { it.playedAtMs }
            .mapNotNull { r -> byId[r.gameId]?.let { ContinueItem(it, r.mode, r.playedAtMs) } }
            .distinctBy { it.game.id }
            .toList()
        val seen = local.map { it.game.id }.toSet()
        val fromHost = games.asSequence()
            .filter { it.id !in seen && it.lastPlayedEpochS != null }
            .sortedByDescending { it.lastPlayedEpochS }
            .map { ContinueItem(it, null, null) }
        return (local.asSequence() + fromHost).take(limit).toList()
    }

    /** Games the host says were played, newest first, excluding [exclude] (already in Continue). */
    fun recentlyPlayed(games: List<Game>, exclude: Set<String>, limit: Int): List<Game> =
        games.filter { it.lastPlayedEpochS != null && it.id !in exclude }
            .sortedByDescending { it.lastPlayedEpochS }
            .take(limit)

    /** Every game, desktops last, by name: the stable "All games" order (Continue carries recency). */
    fun allGames(games: List<Game>): List<Game> =
        games.sortedWith(compareBy<Game> { it.kind != GameKind.GAME }.thenBy { it.name.lowercase() })
}

/**
 * What quick connect's "resume" does (tile, widget, launcher shortcut): on the PC of the last game
 * played, resume the game that runs there now, else start the last game played there again.
 */
object QuickResume {
    sealed interface Target {
        /** Start (or reconnect to) [gameId]; [mode] null means "whatever Play would use". */
        data class Play(val gameId: String, val mode: DisplayMode?, val running: Boolean) : Target

        /** Nothing to resume on that PC: open its library. */
        data object Library : Target
    }

    /**
     * The PC to resume on, paired only: the newest recent game's PC, else the last library shown
     * ([lastHostId]), else any paired PC. Null when nothing is paired.
     */
    fun host(recents: List<RecentPlay>, hosts: List<Host>, lastHostId: String?): Host? {
        val paired = hosts.filter { it.paired }
        val byId = paired.associateBy { it.id }
        return recents.sortedByDescending { it.playedAtMs }.firstNotNullOfOrNull { byId[it.hostId] }
            ?: lastHostId?.let { byId[it] }
            ?: paired.firstOrNull()
    }

    /** [running] is what the PC runs now (null when nothing, or it couldn't say). */
    fun target(hostId: String, running: NowPlaying?, recents: List<RecentPlay>): Target {
        if (running != null && running.hostId == hostId) return Target.Play(running.gameId, running.display, running = true)
        val last = recents.filter { it.hostId == hostId }.maxByOrNull { it.playedAtMs } ?: return Target.Library
        return Target.Play(last.gameId, last.mode, running = false)
    }
}
