package io.github.f_e_n_y_x.nebula

import android.app.Application
import android.content.Context
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

    val prefs: PreferencesRepository = engine?.let { EnginePreferencesRepository(it, local, screen) } ?: local
    val hosts: HostRepository = engine?.let { EngineHostRepository(it) } ?: demo.hostRepository
    val library: LibraryRepository = engine?.let { EngineLibraryRepository(it) } ?: demo.libraryRepository
    val stream: StreamRepository = engine?.let { EngineStreamRepository(it, screen) } ?: demo.streamRepository
    val artwork: ArtworkRepository = engine?.let { EngineArtworkRepository(it) } ?: demo.artworkRepository
    val resolvePlayMode = ResolvePlayModeUseCase(prefs)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** A stream went live: keep the process in the foreground with an ongoing notification. */
    fun onStreamLive(title: String) = StreamKeepAliveService.start(app, title)

    fun onStreamEnded() = StreamKeepAliveService.stop(app)

    /** True on mobile data and other metered links, where data saver applies. */
    fun isMetered(): Boolean = connectivity?.isActiveNetworkMetered == true

    init {
        // Keep the engine's cache limit in step with the setting.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            prefs.libraryOptions.map { it.cacheLimitMb }.distinctUntilChanged().collect { artwork.setLimit(it * 1024L * 1024L) }
        }
    }
}

class NebulaApplication : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { container.engine?.let { add(EngineArtFetcher.Factory(it)) } }
            .build()
}

val Context.container: AppContainer get() = (applicationContext as NebulaApplication).container
