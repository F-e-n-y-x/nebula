package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.RunningGame

/**
 * A game left running on a paired PC: the home "Now playing" card and the "running on your PC"
 * notification both show this.
 */
data class NowPlaying(
    val hostId: String,
    val hostName: String,
    val gameId: String,
    val gameName: String,
    val art: GameArt = GameArt(),
    /** When it started (epoch seconds): the host's time, or when Nebula first saw it running. */
    val sinceEpochS: Long,
    /** True when [sinceEpochS] came from the host; false when tracked on this device. */
    val exact: Boolean,
    /** The display it runs on, when the host says; Resume reconnects to the same one. */
    val display: DisplayMode? = null,
    val connectedClients: Int? = null,
    /** False when the PC can't tell when it closes (it counts as running until quit). */
    val tracked: Boolean = true,
) {
    /** One running session: a different game, or the same one started again, is a new session. */
    val sessionKey: String get() = "$hostId|$gameId|$sinceEpochS"

    /** This game on this host, whatever its start time (a quit hides it by this). */
    val gameKey: String get() = "$hostId|$gameId"
}

/** "1 h 20 m" style durations for the card and the notification. */
object Elapsed {
    fun format(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = s / 86_400
        val hours = (s % 86_400) / 3_600
        val minutes = (s % 3_600) / 60
        return when {
            s < 60 -> "< 1 m"
            days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
            hours > 0 -> if (minutes > 0) "$hours h $minutes m" else "$hours h"
            else -> "$minutes m"
        }
    }

    /**
     * How long it has run, or null when unknown: a locally tracked start is only a lower bound, so
     * it reads "at least …" and is left out for the first minute (the host gave no start time).
     */
    fun duration(np: NowPlaying, nowEpochS: Long): String? {
        val secs = nowEpochS - np.sinceEpochS
        return when {
            np.exact -> format(secs)
            secs < 60 -> null
            else -> "at least ${format(secs)}"
        }
    }

    /** The card's status line: "Running on atom · 1 h 20 m". */
    fun cardLine(np: NowPlaying, nowEpochS: Long): String =
        "Running on ${np.hostName}" + (duration(np, nowEpochS)?.let { " · $it" } ?: "")

    /** The stale card's line when the PC doesn't answer: "Last seen running on atom 5 m ago". */
    fun lastSeenLine(np: NowPlaying, seenAtEpochS: Long, nowEpochS: Long): String {
        val ago = nowEpochS - seenAtEpochS
        return "Last seen running on ${np.hostName} " + if (ago < 60) "just now" else "${format(ago)} ago"
    }

    /** The notification's text: "GTA V is running on atom — 1 h 20 m. Close it to save resources?" */
    fun notificationText(np: NowPlaying, nowEpochS: Long): String =
        "${np.gameName} is running on ${np.hostName}" + (duration(np, nowEpochS)?.let { " — $it" } ?: "") + ". Close it to save resources?"
}

/** A tiny string store (SharedPreferences in the app, a map in tests). */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

class MapKeyValueStore : KeyValueStore {
    val map = mutableMapOf<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/**
 * When Nebula first saw a game running on a host that doesn't report a start time (serverinfo
 * `currentgame` only). Persisted, so the notification worker and a restarted app agree.
 */
class RunningSince(private val store: KeyValueStore) {
    fun startOf(hostId: String, gameId: String, nowEpochS: Long): Long {
        val key = KEY + hostId
        val saved = store.get(key)?.split('|', limit = 2)
        if (saved != null && saved.size == 2 && saved[0] == gameId) saved[1].toLongOrNull()?.let { return it }
        store.put(key, "$gameId|$nowEpochS")
        return nowEpochS
    }

    fun clear(hostId: String) = store.put(KEY + hostId, null)

    private companion object { const val KEY = "nowplaying_seen_" }
}

object NowPlayingResolver {
    /**
     * The card for [host] given what it said is running ([running]; null = nothing). Matches the
     * library by id, then by name. Nothing running forgets the locally tracked start time.
     */
    fun resolve(host: Host?, running: RunningGame?, games: List<Game>, since: RunningSince, nowEpochS: Long): NowPlaying? {
        if (host == null || !host.paired) return null
        if (running == null) {
            since.clear(host.id)
            return null
        }
        val game = running.gameId?.let { id -> games.firstOrNull { it.id == id } }
            ?: running.name?.let { n -> games.firstOrNull { it.name.equals(n, ignoreCase = true) } }
        val gameId = game?.id ?: running.gameId ?: return null
        val start = running.sinceEpochS
        return NowPlaying(
            hostId = host.id,
            hostName = host.name,
            gameId = gameId,
            gameName = game?.name ?: running.name ?: "A game",
            art = game?.art ?: GameArt(),
            sinceEpochS = start ?: since.startOf(host.id, gameId, nowEpochS),
            exact = start != null,
            display = running.display,
            connectedClients = running.connectedClients,
            tracked = running.tracked,
        )
    }

    /** Hosts worth asking: paired and not known to be off. */
    fun candidates(hosts: List<Host>): List<Host> = hosts.filter { it.paired && it.status != HostStatus.OFFLINE }
}

/**
 * "Don't notify" for one running session (host + game + start time). A new session (another game,
 * or the same one started again) notifies normally; the game stopping clears it. Persisted.
 */
class SessionSuppression(private val store: KeyValueStore) {
    fun suppress(np: NowPlaying) = suppress(np.hostId, np.sessionKey)

    fun suppress(hostId: String, sessionKey: String) = store.put(KEY + hostId, sessionKey)

    fun isSuppressed(np: NowPlaying): Boolean = store.get(KEY + np.hostId) == np.sessionKey

    /** Call with each fresh answer for [hostId]: drops a suppression whose session is over. */
    fun onRunning(hostId: String, np: NowPlaying?) {
        val saved = store.get(KEY + hostId) ?: return
        if (np == null || np.sessionKey != saved) store.put(KEY + hostId, null)
    }

    private companion object { const val KEY = "nowplaying_mute_" }
}

/** What the "running on your PC" notification for one host shows now. */
data class PostedNotification(val sessionKey: String, val stopped: Boolean = false)

sealed interface NotifyAction {
    /** Post or update it; [alert] for a new session (updates stay silent). */
    data class Post(val nowPlaying: NowPlaying, val alert: Boolean) : NotifyAction
    data object Remove : NotifyAction
    data object Nothing : NotifyAction
}

/** When the "game running on your PC" notification shows, updates or goes away. */
object RunningNotificationPolicy {
    fun decide(
        enabled: Boolean,
        permitted: Boolean,
        /** Nebula is on screen: the home card says it instead. */
        appVisible: Boolean,
        /** A stream is connected (foreground or kept alive in the background). */
        streamActive: Boolean,
        running: NowPlaying?,
        /** "Don't notify" was chosen for this session. */
        suppressed: Boolean,
        /** Swiped away since the user last left the app: not re-posted until they leave again. */
        dismissed: Boolean,
        posted: PostedNotification?,
    ): NotifyAction {
        val clear = if (posted != null) NotifyAction.Remove else NotifyAction.Nothing
        // A "Stopped" note stays until dismissed; anything else goes once the game has closed.
        if (running == null) return if (posted?.stopped == true) NotifyAction.Nothing else clear
        if (!enabled || !permitted || appVisible || streamActive || suppressed || dismissed) return clear
        return NotifyAction.Post(running, alert = posted == null || posted.stopped || posted.sessionKey != running.sessionKey)
    }
}

/** A failed /cancel. [unreachable] when the PC didn't answer at all (asleep, off, another network). */
class QuitFailure(message: String, val unreachable: Boolean) : IllegalStateException(message)

enum class StopKind { STOPPED, ALREADY_CLOSED, UNREACHABLE, FAILED }

data class StopOutcome(val kind: StopKind, val message: String)

/** The notification's (and the card's) Stop: `/cancel`, with a sentence for every ending. */
object StopGame {
    suspend fun run(hosts: HostRepository, hostId: String, hostName: String, gameName: String): StopOutcome {
        val quit = hosts.quitApp(hostId)
        if (quit.isSuccess) return StopOutcome(StopKind.STOPPED, "Stopped $gameName on $hostName.")
        val e = quit.exceptionOrNull()
        if (e is QuitFailure && e.unreachable) {
            return StopOutcome(StopKind.UNREACHABLE, "Couldn't reach $hostName. It may be asleep or off; $gameName may still be running.")
        }
        // The host answered but refused: maybe the game had already closed.
        val after = hosts.running(hostId)
        if (after.isSuccess && after.getOrNull() == null) {
            return StopOutcome(StopKind.ALREADY_CLOSED, "$gameName had already closed on $hostName.")
        }
        return StopOutcome(StopKind.FAILED, "Couldn't stop $gameName: ${e?.message ?: "the PC refused."}")
    }
}

/** What the Now playing card shows for one host. */
sealed interface NowPlayingView {
    data object Hidden : NowPlayingView
    data class Live(val np: NowPlaying) : NowPlayingView
    /** The PC didn't answer the last time it was asked: what it ran when it last did. */
    data class LastSeen(val np: NowPlaying, val seenAtMs: Long) : NowPlayingView

    val nowPlaying: NowPlaying? get() = when (this) {
        Hidden -> null
        is Live -> np
        is LastSeen -> np
    }
}

/**
 * One host's Now playing bookkeeping (epoch milliseconds). Answers carry the time they were asked,
 * so an old answer (re-resolved when the game list changes, or still in flight during a quit) never
 * brings back a game a newer answer or a quit has cleared.
 */
data class NowPlayingState(
    /** What the newest answer said runs (null = nothing). */
    val live: NowPlaying? = null,
    /** The newest answer came back (false: the PC didn't answer). */
    val reachable: Boolean = true,
    /** When [live] was last confirmed by the PC. */
    val seenAtMs: Long = 0,
    /** Time of the newest answer applied; older ones are ignored. */
    val answerAtMs: Long = 0,
    /** [NowPlaying.gameKey] being quit right now (hidden meanwhile). */
    val quitting: String? = null,
    /** Game just quit: hidden until [hiddenUntilMs] even if a lagging answer still reports it. */
    val hiddenKey: String? = null,
    val hiddenUntilMs: Long = 0,
    /** When the last quit finished (asks the PC again soon after). */
    val quitAtMs: Long = 0,
)

object NowPlayingTracker {
    /** A quit session stays hidden this long even if the PC is slow to say it closed. */
    const val QUIT_GRACE_MS = 3_000L
    /** After a quit, ask the PC every [FAST_POLL_MS] for this long (Proton games take a few seconds). */
    const val FAST_POLL_WINDOW_MS = 12_000L
    const val FAST_POLL_MS = 1_500L
    /** A PC that stopped answering: "last seen running" for at most this long, then nothing. */
    const val LAST_SEEN_MAX_MS = 6 * 3_600_000L

    /** An answer asked at [atMs]: [ok] false when the PC couldn't be reached. */
    fun answer(s: NowPlayingState, ok: Boolean, np: NowPlaying?, atMs: Long): NowPlayingState {
        if (atMs < s.answerAtMs) return s
        return if (ok) {
            s.copy(live = np, reachable = true, seenAtMs = if (np != null) atMs else 0, answerAtMs = atMs)
        } else {
            s.copy(reachable = false, answerAtMs = atMs)
        }
    }

    /** Quit pressed (card, details page, notification): hide it at once. */
    fun quitStarted(s: NowPlayingState, gameKey: String): NowPlayingState = s.copy(quitting = gameKey)

    /**
     * The quit came back. [stopped] (or it had already closed): forget it, ignore answers asked
     * before now, and hide a lagging report of it for [QUIT_GRACE_MS]. Otherwise show it again.
     */
    fun quitFinished(s: NowPlayingState, gameKey: String, stopped: Boolean, nowMs: Long): NowPlayingState =
        if (!stopped) s.copy(quitting = null)
        else s.copy(
            quitting = null,
            live = s.live?.takeIf { it.gameKey != gameKey },
            answerAtMs = maxOf(s.answerAtMs, nowMs),
            hiddenKey = gameKey,
            hiddenUntilMs = nowMs + QUIT_GRACE_MS,
            quitAtMs = nowMs,
        )

    /** Quit from the stream menu: the stream ends and the PC closes the game shortly after. */
    fun quitFromStream(s: NowPlayingState, nowMs: Long): NowPlayingState =
        s.live?.let { quitFinished(s, it.gameKey, stopped = true, nowMs = nowMs) } ?: s.copy(answerAtMs = maxOf(s.answerAtMs, nowMs), quitAtMs = nowMs)

    fun view(s: NowPlayingState, nowMs: Long): NowPlayingView {
        val np = s.live ?: return NowPlayingView.Hidden
        if (np.gameKey == s.quitting) return NowPlayingView.Hidden
        if (np.gameKey == s.hiddenKey && nowMs < s.hiddenUntilMs) return NowPlayingView.Hidden
        if (!s.reachable) {
            return if (nowMs - s.seenAtMs < LAST_SEEN_MAX_MS) NowPlayingView.LastSeen(np, s.seenAtMs) else NowPlayingView.Hidden
        }
        return NowPlayingView.Live(np)
    }

    /** How long to wait before asking the PC again. */
    fun nextPollMs(s: NowPlayingState, nowMs: Long, normalMs: Long): Long =
        if (s.quitAtMs > 0 && nowMs - s.quitAtMs < FAST_POLL_WINDOW_MS) FAST_POLL_MS else normalMs
}
