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
) {
    /** One running session: a different game, or the same one started again, is a new session. */
    val sessionKey: String get() = "$hostId|$gameId|$sinceEpochS"
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
