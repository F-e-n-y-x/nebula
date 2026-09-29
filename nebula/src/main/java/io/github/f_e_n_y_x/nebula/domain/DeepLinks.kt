package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.Host
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * `nebula://play/<host>/<app>?display=virtual|mirror`, parsed but not yet trusted. [hostRef] is a host
 * id or a host name, [gameRef] an app id or an app name (so frontends such as Daijisho can use names).
 */
data class PlayLink(val hostRef: String, val gameRef: String, val mode: DisplayMode?)

/**
 * The quick-connect deep link. Anything on the device can send it (the activity is exported), so it
 * is parsed strictly and only ever resolves to a game on a host that is already paired: a link can
 * never start pairing, add a host, or reach a host by address.
 */
object DeepLinks {
    const val SCHEME = "nebula"
    const val ACTION_HOST = "play"
    private const val MAX_LINK = 512
    private const val MAX_SEGMENT = 128

    enum class Rejection(val message: String) {
        MALFORMED("That Nebula link isn't valid."),
        UNKNOWN_HOST("That link points to a PC Nebula doesn't know."),
        HOST_NOT_PAIRED("That link points to a PC that isn't paired. Pair it in Hosts first."),
        AMBIGUOUS_HOST("More than one paired PC has that name. Use the PC's id in the link."),
        UNKNOWN_GAME("That game isn't in the PC's library."),
    }

    sealed interface Resolved {
        data class Ok(val host: Host, val game: Game, val mode: DisplayMode?) : Resolved
        data class Rejected(val why: Rejection) : Resolved
    }

    /** Builds the link Nebula's own shortcuts, tile and widget use (host and app ids, not names). */
    fun build(hostId: String, gameId: String, mode: DisplayMode?): String {
        val q = mode?.let { "?display=" + if (it == DisplayMode.MIRROR) "mirror" else "virtual" } ?: ""
        return "$SCHEME://$ACTION_HOST/${encode(hostId)}/${encode(gameId)}$q"
    }

    /** Returns null for anything that isn't exactly a well-formed play link. */
    fun parse(raw: String?): PlayLink? {
        if (raw.isNullOrBlank() || raw.length > MAX_LINK) return null
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        if (!SCHEME.equals(uri.scheme, ignoreCase = true)) return null
        if (uri.rawUserInfo != null || uri.port != -1) return null
        if (!ACTION_HOST.equals(uri.rawAuthority, ignoreCase = true)) return null
        val segments = (uri.rawPath ?: return null).split('/').filter { it.isNotEmpty() }
        if (segments.size != 2) return null
        val host = decodeSegment(segments[0]) ?: return null
        val game = decodeSegment(segments[1]) ?: return null
        val mode = when (val m = queryParam(uri.rawQuery, "display") ?: queryParam(uri.rawQuery, "mode")) {
            null -> null
            else -> when (m.lowercase()) {
                "virtual" -> DisplayMode.VIRTUAL
                "mirror" -> DisplayMode.MIRROR
                else -> return null
            }
        }
        return PlayLink(host, game, mode)
    }

    sealed interface HostMatch {
        data class Paired(val host: Host) : HostMatch
        data class Rejected(val why: Rejection) : HostMatch
    }

    /** Matches the link's host against the known hosts: an exact id, else a unique paired name. Only a paired host passes. */
    fun resolveHost(link: PlayLink, hosts: List<Host>): HostMatch {
        hosts.firstOrNull { it.id == link.hostRef }?.let {
            return if (it.paired) HostMatch.Paired(it) else HostMatch.Rejected(Rejection.HOST_NOT_PAIRED)
        }
        val named = hosts.filter { it.name.equals(link.hostRef, ignoreCase = true) }
        val paired = named.filter { it.paired }
        return when {
            paired.size == 1 -> HostMatch.Paired(paired[0])
            paired.size > 1 -> HostMatch.Rejected(Rejection.AMBIGUOUS_HOST)
            named.isNotEmpty() -> HostMatch.Rejected(Rejection.HOST_NOT_PAIRED)
            else -> HostMatch.Rejected(Rejection.UNKNOWN_HOST)
        }
    }

    /** Matches the link's app against the host's library: an exact id, else a unique name. */
    fun resolveGame(link: PlayLink, games: List<Game>): Game? =
        games.firstOrNull { it.id == link.gameRef }
            ?: games.filter { it.name.equals(link.gameRef, ignoreCase = true) }.singleOrNull()

    /** The whole check: [gamesFor] is only asked for the library of a host that passed. */
    suspend fun resolve(raw: String?, hosts: List<Host>, gamesFor: suspend (Host) -> List<Game>): Resolved {
        val link = parse(raw) ?: return Resolved.Rejected(Rejection.MALFORMED)
        val host = when (val m = resolveHost(link, hosts)) {
            is HostMatch.Rejected -> return Resolved.Rejected(m.why)
            is HostMatch.Paired -> m.host
        }
        val game = resolveGame(link, gamesFor(host)) ?: return Resolved.Rejected(Rejection.UNKNOWN_GAME)
        return Resolved.Ok(host, game, link.mode)
    }

    private fun decodeSegment(raw: String): String? {
        // '+' is literal in a path; URLDecoder would turn it into a space.
        val s = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), Charsets.UTF_8.name()) }.getOrNull() ?: return null
        if (s.isBlank() || s.length > MAX_SEGMENT || s.any { it.isISOControl() } || s.contains('/')) return null
        return s
    }

    private fun queryParam(rawQuery: String?, name: String): String? {
        rawQuery ?: return null
        return rawQuery.split('&').firstNotNullOfOrNull { pair ->
            val k = pair.substringBefore('=')
            if (k.equals(name, ignoreCase = true)) {
                runCatching { URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name()) }.getOrNull()
            } else null
        }
    }

    private fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name()).replace("+", "%20")
}
