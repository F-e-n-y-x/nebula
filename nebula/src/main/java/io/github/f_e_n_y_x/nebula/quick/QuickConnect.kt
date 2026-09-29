package io.github.f_e_n_y_x.nebula.quick

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.glance.appwidget.updateAll
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import io.github.f_e_n_y_x.nebula.MainActivity
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.domain.DeepLinks
import io.github.f_e_n_y_x.nebula.domain.HostRepository
import io.github.f_e_n_y_x.nebula.domain.LauncherRepository
import io.github.f_e_n_y_x.nebula.domain.LibraryRepository
import io.github.f_e_n_y_x.nebula.domain.RecentGames
import io.github.f_e_n_y_x.nebula.domain.RecentPlay
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.ui.theme.isTelevision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps every quick-connect surface in step with the recent-games list: dynamic shortcuts, the
 * home-screen widget, the Quick Settings tile and (on TV) Watch Next. All of them launch the same
 * `nebula://play` link, which MainActivity validates against the paired hosts.
 */
class QuickConnect(
    context: Context,
    private val launcher: LauncherRepository,
    private val hosts: HostRepository,
    private val library: LibraryRepository,
) {
    private val app = context.applicationContext

    /** Recent games on paired hosts, newest first: what every surface shows. */
    val items: Flow<List<RecentPlay>> =
        combine(launcher.recents, hosts.observeHosts()) { r, h -> RecentGames.forQuickConnect(r, h, MAX_ITEMS) }
            .distinctUntilChanged()

    private var scope: CoroutineScope? = null

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            items.debounce(400).collectLatest { publish(it) }
        }
    }

    /** A stream screen opened: record it without tying the work to the screen's lifetime. */
    fun onStreamOpened(hostId: String, gameId: String, mode: DisplayMode) {
        scope?.launch { runCatching { recordLaunch(hostId, gameId, mode) }.onFailure { Log.w(TAG, "record", it) } }
    }

    /** Called when a stream is opened (from the app or a link): the game becomes the newest recent. */
    suspend fun recordLaunch(hostId: String, gameId: String, mode: DisplayMode) {
        val host = hosts.observeHosts().first().firstOrNull { it.id == hostId && it.paired } ?: return
        val game = withTimeoutOrNull(5_000) { library.observeGames(hostId).first { it.isNotEmpty() } }?.firstOrNull { it.id == gameId } ?: return
        launcher.record(
            RecentPlay(
                hostId = hostId, hostName = host.name, gameId = gameId, gameName = game.name, mode = mode,
                playedAtMs = System.currentTimeMillis(), poster = game.art.poster, hero = game.art.hero ?: game.art.header,
            ),
        )
        runCatching { ShortcutManagerCompat.reportShortcutUsed(app, shortcutId(hostId, gameId)) }
    }

    private suspend fun publish(list: List<RecentPlay>) {
        runCatching { publishShortcuts(list.take(MAX_SHORTCUTS)) }.onFailure { Log.w(TAG, "shortcuts", it) }
        runCatching { RecentGamesWidget().updateAll(app) }.onFailure { Log.w(TAG, "widget", it) }
        ResumeTileService.refresh(app)
        if (isTelevision(app)) runCatching { WatchNext.sync(app, list) }.onFailure { Log.w(TAG, "watch next", it) }
    }

    private suspend fun publishShortcuts(list: List<RecentPlay>) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(app).coerceAtMost(MAX_SHORTCUTS)
        val shortcuts = list.take(max).mapIndexed { i, r ->
            val icon = Posters.load(app, r.poster, 288, 432)?.let { IconCompat.createWithAdaptiveBitmap(adaptiveIcon(it)) }
                ?: IconCompat.createWithResource(app, R.mipmap.ic_launcher_nebula)
            ShortcutInfoCompat.Builder(app, shortcutId(r.hostId, r.gameId))
                .setShortLabel(r.gameName.take(24))
                .setLongLabel("${r.gameName} on ${r.hostName}".take(44))
                .setIcon(icon)
                .setIntent(launchIntent(app, r))
                .setRank(i)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(app, shortcuts)
    }

    companion object {
        private const val TAG = "NebulaQuick"
        const val MAX_ITEMS = 6
        const val MAX_SHORTCUTS = 4

        fun shortcutId(hostId: String, gameId: String) = "play:$hostId/$gameId"

        /** An explicit intent to MainActivity carrying the play link (the same link frontends can send). */
        fun launchIntent(context: Context, r: RecentPlay): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(DeepLinks.build(r.hostId, r.gameId, r.mode)), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        /** Opens Nebula normally (surfaces with nothing to resume). */
        fun openAppIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Square adaptive-icon bitmap: the poster centre-cropped to fill the full 108 dp canvas. */
        private fun adaptiveIcon(poster: Bitmap): Bitmap {
            val size = 216
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val side = minOf(poster.width, poster.height)
            val left = (poster.width - side) / 2
            val top = ((poster.height - side) * 0.3f).toInt() // a little above centre: key art, not the rating box
            Canvas(out).drawBitmap(poster, Rect(left, top, left + side, top + side), Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
    }
}

/** Loads artwork into a software bitmap through the app's image loader (which knows the engine's art). */
object Posters {
    suspend fun load(context: Context, url: String?, width: Int, height: Int): Bitmap? {
        url ?: return null
        val request = ImageRequest.Builder(context).data(url).size(width, height).scale(Scale.FILL).allowHardware(false).build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull() as? SuccessResult ?: return null
        return runCatching { result.image.toBitmap() }.getOrNull()
    }
}
