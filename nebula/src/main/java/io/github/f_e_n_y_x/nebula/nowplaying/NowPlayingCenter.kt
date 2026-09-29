package io.github.f_e_n_y_x.nebula.nowplaying

import android.content.Context
import android.content.SharedPreferences
import coil3.request.allowHardware
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.f_e_n_y_x.nebula.domain.HostRepository
import io.github.f_e_n_y_x.nebula.domain.KeyValueStore
import io.github.f_e_n_y_x.nebula.domain.LibraryRepository
import io.github.f_e_n_y_x.nebula.domain.NotifyAction
import io.github.f_e_n_y_x.nebula.domain.NowPlaying
import io.github.f_e_n_y_x.nebula.domain.NowPlayingResolver
import io.github.f_e_n_y_x.nebula.domain.NowPlayingState
import io.github.f_e_n_y_x.nebula.domain.NowPlayingTracker
import io.github.f_e_n_y_x.nebula.domain.RunningNotificationPolicy
import io.github.f_e_n_y_x.nebula.domain.RunningSince
import io.github.f_e_n_y_x.nebula.domain.SessionSuppression
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.RunningGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** A notification's Resume, checked by [NowPlayingCenter.resumeTarget]. */
data class ResumeTarget(val hostId: String, val gameId: String, val mode: DisplayMode?)

/**
 * Games left running on paired PCs: resolves the home card and decides the "running on your PC"
 * notification (shown after the user leaves the app, refreshed by [RunningGameWorker]).
 */
class NowPlayingCenter(
    context: Context,
    private val hosts: HostRepository,
    private val library: LibraryRepository,
    /** A stream is connected (on screen or kept alive in the background). */
    private val streamActive: () -> Boolean,
) {
    private val app = context.applicationContext
    private val sp: SharedPreferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val store: KeyValueStore = object : KeyValueStore, RunningGameNotifications.PrefixListing {
        override fun keysWithPrefix(prefix: String) = sp.all.keys.filter { it.startsWith(prefix) }
        override fun get(key: String): String? = sp.getString(key, null)
        override fun put(key: String, value: String?) { sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply() }
    }
    val since = RunningSince(store)
    val suppression = SessionSuppression(store)
    val notifications = RunningGameNotifications(app, store)
    private val mutex = Mutex()

    private val _enabled = MutableStateFlow(sp.getBoolean(KEY_ENABLED, true))
    /** Settings → Notifications: "Games left running on your PC". */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(on: Boolean) {
        sp.edit().putBoolean(KEY_ENABLED, on).apply()
        _enabled.value = on
        if (!on) {
            notifications.cancelAll()
            RunningGameWorker.cancel(app)
        }
    }

    /** Asked once: the rationale before the Android 13+ notification permission. */
    var rationaleShown: Boolean
        get() = sp.getBoolean(KEY_RATIONALE, false)
        set(v) { sp.edit().putBoolean(KEY_RATIONALE, v).apply() }

    private val _pendingResume = MutableStateFlow<ResumeTarget?>(null)
    /** A notification's Resume waiting for the UI to open the stream. */
    val pendingResume: StateFlow<ResumeTarget?> = _pendingResume.asStateFlow()
    fun requestResume(t: ResumeTarget) { _pendingResume.value = t }
    fun consumeResume() { _pendingResume.value = null }

    private fun nowS() = System.currentTimeMillis() / 1000
    private fun nowMs() = System.currentTimeMillis()

    /** Per host: what the home card and the details page show (shared, so a quit on one hides both). */
    private val _states = MutableStateFlow<Map<String, NowPlayingState>>(emptyMap())

    fun state(hostId: String): Flow<NowPlayingState> = _states.map { it[hostId] ?: NowPlayingState() }.distinctUntilChanged()
    fun stateNow(hostId: String): NowPlayingState = _states.value[hostId] ?: NowPlayingState()

    private fun updateState(hostId: String, f: (NowPlayingState) -> NowPlayingState) =
        _states.update { m -> m + (hostId to f(m[hostId] ?: NowPlayingState())) }

    /** An answer from [hostId], asked at [atMs]; [ok] false when it couldn't be reached. */
    fun onAnswer(hostId: String, ok: Boolean, np: NowPlaying?, atMs: Long) = updateState(hostId) { NowPlayingTracker.answer(it, ok, np, atMs) }

    fun onQuitStarted(np: NowPlaying) = updateState(np.hostId) { NowPlayingTracker.quitStarted(it, np.gameKey) }

    /** A quit came back; [stopped] also when the game had already closed. Forgets its local start time. */
    fun onQuitFinished(np: NowPlaying, stopped: Boolean) {
        if (stopped) {
            since.clear(np.hostId)
            suppression.onRunning(np.hostId, null)
        }
        updateState(np.hostId) { NowPlayingTracker.quitFinished(it, np.gameKey, stopped, nowMs()) }
    }

    /** "Quit game" in the stream menu: the card shouldn't show the game on the way back. */
    fun onQuitFromStream(hostId: String) {
        since.clear(hostId)
        updateState(hostId) { NowPlayingTracker.quitFromStream(it, nowMs()) }
    }

    fun appVisible(): Boolean =
        runCatching { ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }.getOrDefault(false)

    /** The card for [host] from what it runs ([running]); updates the session bookkeeping. */
    fun resolve(host: Host?, running: RunningGame?, games: List<Game>): NowPlaying? =
        NowPlayingResolver.resolve(host, running, games, since, nowS()).also { np -> host?.let { suppression.onRunning(it.id, np) } }

    /** Asks [host] what it runs now; failure when it can't be reached. */
    suspend fun check(host: Host): Result<NowPlaying?> {
        val at = nowMs()
        val r = hosts.running(host.id)
        if (r.isFailure) {
            onAnswer(host.id, ok = false, np = null, atMs = at)
            return Result.failure(r.exceptionOrNull()!!)
        }
        val running = r.getOrNull()
        val games = if (running == null) emptyList() else withTimeoutOrNull(GAMES_TIMEOUT_MS) { library.observeGames(host.id).first() }.orEmpty()
        return Result.success(resolve(host, running, games).also { onAnswer(host.id, ok = true, np = it, atMs = at) })
    }

    /** Paired hosts once the saved list has loaded (a cold worker process starts with none). */
    suspend fun pairedHosts(): List<Host> {
        val loaded = withTimeoutOrNull(HOSTS_TIMEOUT_MS) { hosts.observeHosts().first { l -> l.isNotEmpty() } } ?: hosts.observeHosts().first()
        return loaded.filter { it.paired }
    }

    /**
     * Brings every host's notification up to date. Returns true while something may still be
     * running (a game runs, or a host didn't answer and its notification is kept), so the periodic
     * refresh should continue.
     */
    suspend fun refreshNotifications(): Boolean = mutex.withLock {
        var keepWatching = false
        for (h in pairedHosts()) {
            val res = check(h)
            if (res.isFailure) {
                // Asleep or out of reach: leave what is shown and ask again next time.
                if (notifications.posted(h.id) != null) keepWatching = true
                continue
            }
            val np = res.getOrNull()
            if (np != null) keepWatching = true
            apply(h.id, np)
        }
        keepWatching
    }

    private var iconCache: Pair<String, android.graphics.Bitmap?>? = null

    /** The game's poster (or header) for the large icon; loaded once per session, null on failure. */
    suspend fun artFor(np: NowPlaying): android.graphics.Bitmap? {
        iconCache?.takeIf { it.first == np.sessionKey }?.let { return it.second }
        val url = np.art.poster ?: np.art.header ?: np.art.hero
        val bmp = if (url == null) null else withTimeoutOrNull(ART_TIMEOUT_MS) {
            runCatching {
                val req = coil3.request.ImageRequest.Builder(app).data(url).size(ICON_PX, ICON_PX).allowHardware(false).build()
                (coil3.SingletonImageLoader.get(app).execute(req) as? coil3.request.SuccessResult)?.image?.let { img ->
                    (img as? coil3.BitmapImage)?.bitmap
                }
            }.getOrNull()
        }
        iconCache = np.sessionKey to bmp
        return bmp
    }

    private suspend fun apply(hostId: String, np: NowPlaying?) {
        val action = RunningNotificationPolicy.decide(
            enabled = _enabled.value,
            permitted = notifications.permitted(),
            appVisible = appVisible(),
            streamActive = streamActive(),
            running = np,
            suppressed = np != null && suppression.isSuppressed(np),
            dismissed = np != null && notifications.dismissed(hostId) == np.sessionKey,
            posted = notifications.posted(hostId),
        )
        when (action) {
            is NotifyAction.Post -> notifications.showRunning(action.nowPlaying, action.alert, nowS(), icon = artFor(action.nowPlaying))
            NotifyAction.Remove -> notifications.cancel(hostId)
            NotifyAction.Nothing -> Unit
        }
    }

    /** Nebula is on screen: the home card takes over; background refreshes stop. */
    fun onAppForeground() {
        notifications.cancelAll()
        notifications.clearDismissed()
        RunningGameWorker.cancel(app)
    }

    /** The user left Nebula (home, another app, recents swipe). A connected stream notifies once it ends. */
    fun onAppBackground() {
        if (!_enabled.value || streamActive()) return
        RunningGameWorker.schedule(app)
    }

    /** A stream ended (Disconnect, Quit, the background grace period running out). */
    fun onStreamEnded() {
        if (!appVisible()) onAppBackground()
    }

    /** Validates a Resume from our notification: our token, a paired host, sane ids. */
    fun resumeTarget(hostId: String?, gameId: String?, mode: String?, token: String?): ResumeTarget? {
        if (hostId.isNullOrBlank() || gameId.isNullOrBlank() || hostId.length > 128 || gameId.length > 128) return null
        if (token == null || token != notifications.resumeToken()) return null
        return ResumeTarget(hostId, gameId, mode?.let { m -> DisplayMode.entries.firstOrNull { it.name == m } })
    }

    companion object {
        private const val PREFS = "nebula_nowplaying"
        private const val KEY_ENABLED = "notify_running"
        private const val KEY_RATIONALE = "notify_rationale_shown"
        private const val HOSTS_TIMEOUT_MS = 5_000L
        private const val GAMES_TIMEOUT_MS = 10_000L
        private const val ART_TIMEOUT_MS = 5_000L
        private const val ICON_PX = 256
    }
}
