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

private const val TAG = "NebulaLinks"

/**
 * Opens validated `nebula://play` links: Library → Details → Stream, so Back from the stream lands on
 * the game. A link never interrupts a running stream, and a link to a host that isn't paired (or a
 * game that isn't in its library) only shows a message.
 */
@Composable
internal fun PlayLinkHandler(container: AppContainer, nav: Navigator, links: Channel<String>?) {
    links ?: return
    val context = LocalContext.current
    LaunchedEffect(links) {
        links.receiveAsFlow().collect { raw ->
            val say = { msg: String -> Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
            val link = DeepLinks.parse(raw)
            if (link == null) {
                Log.w(TAG, "rejected: malformed")
                say(DeepLinks.Rejection.MALFORMED.message)
                return@collect
            }
            val hosts = container.hosts.observeHosts().first()
            val host = when (val m = DeepLinks.resolveHost(link, hosts)) {
                is DeepLinks.HostMatch.Paired -> m.host
                is DeepLinks.HostMatch.Rejected -> {
                    Log.w(TAG, "rejected: ${m.why}")
                    say(m.why.message)
                    return@collect
                }
            }
            if (nav.current is Route.Stream) {
                say("Finish the current stream first.")
                return@collect
            }
            val games = withTimeoutOrNull(8_000) { container.library.observeGames(host.id).first { it.isNotEmpty() } }
            if (games == null) {
                // Paired but not answering: show its library, which explains the state (and can wake it).
                nav.reset(listOf(Route.Library(host.id)))
                say("Couldn't reach ${host.name} to start that game.")
                return@collect
            }
            val game = DeepLinks.resolveGame(link, games)
            if (game == null) {
                Log.w(TAG, "rejected: game '${link.gameRef}' on ${host.id}")
                nav.reset(listOf(Route.Library(host.id)))
                say(DeepLinks.Rejection.UNKNOWN_GAME.message)
                return@collect
            }
            val mode = link.mode ?: container.resolvePlayMode(game).first()
            container.prefs.setLastHost(host.id)
            nav.lastLibrary = Route.Library(host.id)
            nav.reset(listOf(Route.Library(host.id), Route.Details(host.id, game.id), Route.Stream(host.id, game.id, mode)))
        }
    }
}

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
