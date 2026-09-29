package io.github.f_e_n_y_x.nebula.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.data.demo.DemoHost
import io.github.f_e_n_y_x.nebula.domain.DeepLinks
import io.github.f_e_n_y_x.nebula.domain.HomeStyle
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.f_e_n_y_x.nebula.domain.QuickResume
import io.github.f_e_n_y_x.nebula.domain.WakeHost
import io.github.f_e_n_y_x.nebula.domain.WakeState
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.ui.screens.WakeOverlay

private const val TAG = "NebulaLinks"

/** A link on its way: the wake overlay's state while its PC wakes up. */
private class LinkWake(val hostName: String, val gameName: String?) {
    var state by mutableStateOf<WakeState?>(WakeState.Sending)
    /** Retry (true) or Cancel (false) after a failed or timed-out wake. */
    val choice = Channel<Boolean>(Channel.CONFLATED)
}

/**
 * Opens validated quick-connect links: `nebula://play/<host>/<game>` (shortcuts, widget, Watch Next,
 * frontends) and `nebula://resume` (tile, widget, launcher shortcut). Either builds Library →
 * Details → Stream, so Back from the stream lands on the game. A sleeping PC is woken first, with
 * the same overlay as Play in the app. A link never interrupts a running stream, and a link to a
 * host that isn't paired (or a game that isn't in its library) only shows a message.
 */
@Composable
internal fun PlayLinkHandler(container: AppContainer, nav: Navigator, links: Channel<String>?) {
    links ?: return
    val context = LocalContext.current
    var waking by remember { mutableStateOf<LinkWake?>(null) }
    LaunchedEffect(links) {
        links.receiveAsFlow().collect { raw ->
            val say = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            val hosts = container.hosts.observeHosts().first()
            val play = DeepLinks.parse(raw)
            val resume = if (play == null) DeepLinks.parseResume(raw) else null
            if (play == null && resume == null) {
                Log.w(TAG, "rejected: malformed")
                say(DeepLinks.Rejection.MALFORMED.message)
                return@collect
            }
            val recents = container.launcher.recents.first()
            val hostRef = play?.hostRef ?: resume?.hostRef
            val host = if (hostRef != null) {
                when (val m = DeepLinks.resolveHostRef(hostRef, hosts)) {
                    is DeepLinks.HostMatch.Paired -> m.host
                    is DeepLinks.HostMatch.Rejected -> {
                        Log.w(TAG, "rejected: ${m.why}")
                        say(m.why.message)
                        return@collect
                    }
                }
            } else {
                QuickResume.host(recents, hosts, container.prefs.lastHostId.first()) ?: run {
                    say("Pair a PC in Hosts first.")
                    nav.reset(listOf(Route.Hosts))
                    return@collect
                }
            }
            if (nav.current is Route.Stream) {
                say("Finish the current stream first.")
                return@collect
            }
            val gameHint = play?.gameRef ?: recents.filter { it.hostId == host.id }.maxByOrNull { it.playedAtMs }?.gameName

            // Asleep: wake it and wait, like Play in the app. The overlay offers Retry and Cancel.
            if (!isUp(container.hosts.refresh(host.id)?.status ?: host.status)) {
                if (!host.canWake) {
                    nav.reset(listOf(Route.Library(host.id)))
                    say("${host.name} isn't answering.")
                    return@collect
                }
                val w = LinkWake(host.name, gameHint).also { waking = it }
                val awake = try { wakeUntilUp(container, host.id, w) } finally { waking = null }
                if (!awake) {
                    nav.reset(listOf(Route.Library(host.id)))
                    return@collect
                }
            }

            val games = withTimeoutOrNull(8_000) { container.library.observeGames(host.id).first { it.isNotEmpty() } }
            if (games == null) {
                // Paired but not answering: show its library, which explains the state (and can wake it).
                nav.reset(listOf(Route.Library(host.id)))
                say("Couldn't reach ${host.name} to start that game.")
                return@collect
            }
            val (game, linkMode) = if (play != null) {
                val g = DeepLinks.resolveGame(play, games)
                if (g == null) {
                    Log.w(TAG, "rejected: game '${play.gameRef}' on ${host.id}")
                    nav.reset(listOf(Route.Library(host.id)))
                    say(DeepLinks.Rejection.UNKNOWN_GAME.message)
                    return@collect
                }
                g to play.mode
            } else {
                val running = container.nowPlaying.check(host).getOrNull()
                when (val t = QuickResume.target(host.id, running, recents)) {
                    QuickResume.Target.Library -> {
                        nav.reset(listOf(Route.Library(host.id)))
                        return@collect
                    }
                    is QuickResume.Target.Play -> {
                        val g = games.firstOrNull { it.id == t.gameId } ?: run {
                            nav.reset(listOf(Route.Library(host.id)))
                            return@collect
                        }
                        g to t.mode
                    }
                }
            }
            val mode = linkMode ?: container.resolvePlayMode(game).first()
            container.prefs.setLastHost(host.id)
            nav.lastLibrary = Route.Library(host.id)
            nav.reset(listOf(Route.Library(host.id), Route.Details(host.id, game.id), Route.Stream(host.id, game.id, mode)))
        }
    }
    waking?.let { w ->
        WakeOverlay(w.hostName, w.gameName, w.state, onCancel = { w.choice.trySend(false) }, onRetry = { w.choice.trySend(true) })
    }
}

private fun isUp(s: HostStatus?) = s == HostStatus.ONLINE || s == HostStatus.STREAMING

/** Wakes [hostId] and waits; true once it answers, false when the user cancels. */
private suspend fun wakeUntilUp(container: AppContainer, hostId: String, w: LinkWake): Boolean = coroutineScope {
    var answer: Boolean? = null
    while (answer == null) {
        w.state = WakeState.Sending
        val job = async {
            var up = false
            WakeHost(send = { container.hosts.wake(hostId) }, poll = { container.hosts.refresh(hostId)?.status }).run().collect { st ->
                w.state = st
                if (st is WakeState.Online) up = true
            }
            up
        }
        // Cancel works while waiting too; Retry is offered once a wake has ended.
        val outcome = select {
            job.onAwait { if (it) Outcome.UP else Outcome.ENDED }
            w.choice.onReceive { retry -> if (retry) Outcome.RETRY else Outcome.CANCEL }
        }
        when (outcome) {
            Outcome.UP -> answer = true
            Outcome.CANCEL -> { job.cancel(); answer = false }
            Outcome.RETRY -> job.cancel()
            Outcome.ENDED -> if (!w.choice.receive()) answer = false
        }
    }
    answer == true
}

private enum class Outcome { UP, ENDED, RETRY, CANCEL }

/**
 * Debug QA hooks in the `start` extra, stripped before the screen spec:
 * `style:<auto|spotlight|shelf>:<spec>` sets the home style, `seed:<spec>` records three demo games
 * as recently streamed (widget, tile, shortcuts, Continue rows), `pinwidget:<spec>` asks the launcher
 * to pin the recent-games widget, `link:<url>` sends a play link.
 */
internal suspend fun debugLauncherSetup(context: android.content.Context, container: AppContainer, spec: String?, links: Channel<String>?): String? {
    if (!BuildConfig.DEBUG || spec.isNullOrBlank()) return spec
    var rest: String = spec
    while (true) {
        when {
            rest.startsWith("style:") -> {
                val parts = rest.removePrefix("style:").split(':', limit = 2)
                HomeStyle.entries.firstOrNull { it.name.equals(parts[0], true) }?.let { container.launcher.setHomeStyle(it) }
                rest = parts.getOrElse(1) { "" }
            }
            rest.startsWith("seed:") || rest == "seed" -> {
                val h = DemoHost.HOST_ID
                listOf("spiderman2" to DisplayMode.VIRTUAL, "wukong" to DisplayMode.MIRROR, "gta5" to DisplayMode.VIRTUAL).forEach { (g, m) ->
                    container.quick.recordLaunch(h, g, m)
                    delay(5)
                }
                rest = rest.removePrefix("seed").removePrefix(":")
            }
            rest.startsWith("pinwidget:") || rest == "pinwidget" -> {
                val awm = android.appwidget.AppWidgetManager.getInstance(context)
                if (awm.isRequestPinAppWidgetSupported) {
                    awm.requestPinAppWidget(android.content.ComponentName(context, io.github.f_e_n_y_x.nebula.quick.RecentGamesWidgetReceiver::class.java), null, null)
                }
                rest = rest.removePrefix("pinwidget").removePrefix(":")
            }
            rest.startsWith("link:") -> {
                links?.trySend(rest.removePrefix("link:"))
                return null
            }
            else -> return rest.ifBlank { null }
        }
    }
}
