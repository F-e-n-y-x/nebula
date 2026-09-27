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
import io.github.f_e_n_y_x.nebula.domain.HostRepository
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
    private val demo = DemoHost(context)
    val isDemo: Boolean = demo.isAvailable

    /** Null in the demo build, so screenshots never touch the network or the native core. */
    val engine: NebulaEngine? = if (isDemo) null else NebulaEngine.create(context)

    private val local = PreferencesStore(context)
    private val screen = { deviceResolution(context) }

    val prefs: PreferencesRepository = engine?.let { EnginePreferencesRepository(it, local, screen) } ?: local
    val hosts: HostRepository = engine?.let { EngineHostRepository(it) } ?: demo.hostRepository
    val library: LibraryRepository = engine?.let { EngineLibraryRepository(it) } ?: demo.libraryRepository
    val stream: StreamRepository = engine?.let { EngineStreamRepository(it, screen) } ?: demo.streamRepository
    val resolvePlayMode = ResolvePlayModeUseCase(prefs)
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
