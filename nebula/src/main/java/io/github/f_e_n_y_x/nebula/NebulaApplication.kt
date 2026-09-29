package io.github.f_e_n_y_x.nebula

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.f_e_n_y_x.nebula.data.PreferencesStore
import io.github.f_e_n_y_x.nebula.data.demo.DemoHost
import io.github.f_e_n_y_x.nebula.data.engine.EngineArtFetcher
import io.github.f_e_n_y_x.nebula.data.engine.EngineHostRepository
import io.github.f_e_n_y_x.nebula.data.engine.EngineLibraryRepository
import io.github.f_e_n_y_x.nebula.data.engine.EnginePreferencesRepository
import io.github.f_e_n_y_x.nebula.data.engine.EngineStreamRepository
import android.net.ConnectivityManager
import io.github.f_e_n_y_x.nebula.data.engine.EngineArtworkRepository
import io.github.f_e_n_y_x.nebula.domain.ArtworkRepository
import io.github.f_e_n_y_x.nebula.domain.HostRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import io.github.f_e_n_y_x.nebula.domain.LibraryRepository
import io.github.f_e_n_y_x.nebula.domain.Orientation
import io.github.f_e_n_y_x.nebula.domain.PreferencesRepository
import io.github.f_e_n_y_x.nebula.domain.ResolvePlayModeUseCase
import io.github.f_e_n_y_x.nebula.domain.StreamRepository
import io.github.f_e_n_y_x.nebula.ui.screens.deviceResolution
import io.github.fenyx.nebula.engine.NebulaEngine

/**
 * Manual dependency graph: small enough that a DI framework would only add build time. Debug builds
 * that carry the demo fixtures run against the demo host; everything else talks to real PCs
 * through the engine.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    private val demo = DemoHost(context)
    val isDemo: Boolean = demo.isAvailable

    /** Null in the demo build, so screenshots never touch the network or the native core. */
    val engine: NebulaEngine? = if (isDemo) null else NebulaEngine.create(context)

    private val local = PreferencesStore(context)
    private val screen = { deviceResolution(context) }

    /** This device's panel size in landscape: what "match this device" streams at. */
    fun deviceResolution(): Pair<Int, Int> = screen()

    /** Which way up the device is held right now. */
    fun deviceOrientation(): Orientation =
        if (app.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) Orientation.PORTRAIT else Orientation.LANDSCAPE

    val prefs: PreferencesRepository = engine?.let { EnginePreferencesRepository(it, local, screen) } ?: local
    val hosts: HostRepository = engine?.let { EngineHostRepository(it) } ?: demo.hostRepository
    val library: LibraryRepository = engine?.let { EngineLibraryRepository(it) } ?: demo.libraryRepository
    /** The last few streams' summaries (Settings → Diagnostics). */
    val sessionHistory = io.github.f_e_n_y_x.nebula.diagnostics.SessionHistory(context)
    val stream: StreamRepository = io.github.f_e_n_y_x.nebula.diagnostics.RecordingStreamRepository(
        engine?.let { EngineStreamRepository(it, screen, context) } ?: demo.streamRepository, sessionHistory::add,
    )
    val artwork: ArtworkRepository = engine?.let { EngineArtworkRepository(it) } ?: demo.artworkRepository
    /** Per-game presets on this device (Game settings on a game's page). */
    val presets: io.github.f_e_n_y_x.nebula.domain.GamePresetRepository = io.github.f_e_n_y_x.nebula.data.GamePresetStore(context)
    /** A game preset's frame generation and upscaler choices, in force while its stream runs. */
    val postProcessing: io.github.f_e_n_y_x.nebula.presets.PostProcessingOverlay = io.github.f_e_n_y_x.nebula.presets.PostProcessingOverlay.create(context)
    val resolvePlayMode = ResolvePlayModeUseCase(prefs, presets)

    /**
     * Ends a game preset's frame generation / upscaler overlay (after a stream, or at start after
     * a crash) and keeps what was changed during the stream for that game.
     */
    fun finishPostProcessing(token: String? = null) {
        // Restored at once (a new stream may start right after); the game's preset is updated after.
        val done = runCatching { postProcessing.finish(token) }.getOrNull() ?: return
        if (done.changedDuringStream.isEmpty() || done.hostId.isEmpty() || done.gameId.isEmpty()) return
        background.launch {
            presets.update(done.hostId, done.gameId) { io.github.f_e_n_y_x.nebula.presets.PostProcessingOverlay.keepChanges(it, done.changedDuringStream) }
        }
    }

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Pinned games, per host, stored on this device only. */
    val favourites: io.github.f_e_n_y_x.nebula.domain.FavouritesRepository = io.github.f_e_n_y_x.nebula.data.FavouritesStore(context)

    /** Debug QA only: the demo PC starts asleep so Play shows the wake flow. */
    fun debugPutDemoHostToSleep() { if (isDemo) demo.putToSleep() }

    /** Home style and the local recent-games list behind Continue rows and quick connect. */
    val launcher: io.github.f_e_n_y_x.nebula.domain.LauncherRepository = io.github.f_e_n_y_x.nebula.data.LauncherStore(context)
    /** Shortcuts, widget, Quick Settings tile and TV Watch Next, kept in step with [launcher]. */
    val quick = io.github.f_e_n_y_x.nebula.quick.QuickConnect(context, launcher, hosts, library)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** Debug QA only: the demo host forgets its pairings so first-run setup can be replayed. */
    fun demoFirstRun() { if (isDemo) demo.forgetPairings() }

    @Volatile private var streaming = false

    /** Games left running on paired PCs: the home card and the "running on your PC" notification. */
    val nowPlaying = io.github.f_e_n_y_x.nebula.nowplaying.NowPlayingCenter(app, hosts, library, streamActive = { streaming })

    /** A stream went live: keep the process in the foreground with an ongoing notification. */
    fun onStreamLive(title: String) {
        streaming = true
        StreamKeepAliveService.start(app, title)
    }

    fun onStreamEnded() {
        StreamKeepAliveService.stop(app)
        if (!streaming) return
        streaming = false
        // Left the app with the game still running (grace period over, Disconnect from the shade).
        nowPlaying.onStreamEnded()
    }

    /** True on mobile data and other metered links, where data saver applies. */
    fun isMetered(): Boolean = connectivity?.isActiveNetworkMetered == true

    init {
        io.github.f_e_n_y_x.nebula.diagnostics.StickCalibrations.install(app)
        // A stream that ended with the process (crash, force stop) left its game's overlay in place.
        finishPostProcessing()
        // Keep the engine's cache limit in step with the setting.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            prefs.libraryOptions.map { it.cacheLimitMb }.distinctUntilChanged().collect { artwork.setLimit(it * 1024L * 1024L) }
        }
        quick.start(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

class NebulaApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // The frame generation device check runs in its own process and needs none of the app.
        if (io.github.f_e_n_y_x.nebula.framegen.FramegenAppSetup.isSelfTestProcess()) return
        container = AppContainer(this)
        io.github.f_e_n_y_x.nebula.framegen.FramegenAppSetup.onAppStart(this)
        // Foreground / background for the whole app (not per activity, so rotation doesn't count).
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) = container.nowPlaying.onAppForeground()
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = container.nowPlaying.onAppBackground()
        })
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { container.engine?.let { add(EngineArtFetcher.Factory(it)) } }
            .build()
}

val Context.container: AppContainer get() = (applicationContext as NebulaApplication).container
