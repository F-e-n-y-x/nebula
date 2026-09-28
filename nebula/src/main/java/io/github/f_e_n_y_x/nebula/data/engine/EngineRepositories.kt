package io.github.f_e_n_y_x.nebula.data.engine

import android.app.Activity
import android.view.SurfaceHolder
import io.github.f_e_n_y_x.nebula.data.PreferencesStore
import io.github.f_e_n_y_x.nebula.domain.ArtworkRepository
import io.github.f_e_n_y_x.nebula.domain.HostGating
import io.github.f_e_n_y_x.nebula.domain.HostRepository
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardMode
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardSendResult
import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import io.github.fenyx.nebula.engine.ClipboardSend
import io.github.fenyx.nebula.engine.ClipboardSync
import io.github.fenyx.nebula.engine.HostRefusedException
import io.github.fenyx.nebula.engine.HostCommand as EngineHostCommand
import io.github.f_e_n_y_x.nebula.domain.LibraryRepository
import io.github.f_e_n_y_x.nebula.domain.PreferencesRepository
import io.github.f_e_n_y_x.nebula.domain.StreamRepository
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameDetails
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.LastSession
import io.github.f_e_n_y_x.nebula.domain.model.PairingAs
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.domain.model.AbrInfo
import io.github.f_e_n_y_x.nebula.domain.model.AbrSource
import io.github.f_e_n_y_x.nebula.domain.model.ConnectionReport
import io.github.f_e_n_y_x.nebula.domain.model.ConnectionTestError
import io.github.f_e_n_y_x.nebula.domain.model.LinkQuality
import io.github.f_e_n_y_x.nebula.domain.model.SuggestedSettings
import io.github.fenyx.nebula.engine.AbrMode
import io.github.fenyx.nebula.engine.AbrState
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.ConnectionQuality
import io.github.fenyx.nebula.engine.ConnectionTestException
import io.github.fenyx.nebula.engine.ConnectionTestFailure
import io.github.fenyx.nebula.engine.ConnectionTestResult
import io.github.fenyx.nebula.engine.DisplaySpec
import io.github.fenyx.nebula.engine.CodecPreference
import io.github.fenyx.nebula.engine.HostApp
import io.github.fenyx.nebula.engine.HostState
import io.github.fenyx.nebula.engine.InputBridge
import io.github.fenyx.nebula.engine.NebulaEngine
import io.github.fenyx.nebula.engine.NovaDisplayMode
import io.github.fenyx.nebula.engine.PairingFailure
import io.github.fenyx.nebula.engine.StreamEndReason
import io.github.fenyx.nebula.engine.StreamListener
import io.github.fenyx.nebula.engine.StreamRequest
import io.github.fenyx.nebula.engine.StreamSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import io.github.fenyx.nebula.engine.Host as EngineHost
import io.github.fenyx.nebula.engine.PairingState as EnginePairing
import io.github.fenyx.nebula.engine.StreamStats as EngineStats

/** The video target the stream screen hands to [EngineStreamRepository]. */
class SurfaceStreamTarget(val activity: Activity, val holder: SurfaceHolder) : StreamTarget

/** This device's screen for connection-test suggestions: width, height and highest refresh rate. */
typealias DeviceDisplay = () -> Triple<Int, Int, Int>

private fun DeviceDisplay.spec() = invoke().let { (w, h, fps) -> DisplaySpec(w, h, fps) }

class EngineHostRepository(private val engine: NebulaEngine, private val display: DeviceDisplay = { Triple(1920, 1080, 60) }) : HostRepository {
    override fun observeHosts(): Flow<List<Host>> = engine.hosts.map { list -> list.map { it.toDomain() } }

    override suspend fun discover() {
        engine.startDiscovery()
        coroutineScope { engine.hosts.value.map { h -> async { runCatching { engine.refresh(h.id) } } }.awaitAll() }
    }

    override suspend fun addManual(address: String): Result<Host> =
        runCatching { engine.addHostManually(address.trim()).toDomain() }
            .recoverCatching { throw IllegalStateException(if (it is IllegalArgumentException) "That doesn't look like an address." else "No PC answered at $address.") }

    override fun pair(hostId: String): Flow<PairingState> {
        var pin: String? = null
        return engine.pair(hostId).map { st ->
            when (st) {
                is EnginePairing.GeneratingPin -> PairingState.ShowPin(st.pin).also { pin = st.pin }
                EnginePairing.WaitingForHost -> pin?.let { PairingState.ShowPin(it) } ?: PairingState.Connecting
                EnginePairing.Paired -> PairingState.Paired
                is EnginePairing.Failed -> PairingState.Failed(
                    when (st.reason) {
                        PairingFailure.WRONG_PIN -> "The PIN didn't match. Try again and enter the new PIN."
                        PairingFailure.HOST_BUSY -> "The PC is streaming to another device. End that stream first."
                        PairingFailure.ALREADY_IN_PROGRESS -> "The PC is already pairing with another device."
                        PairingFailure.UNREACHABLE -> "Couldn't reach the PC. Check that it's on and on the same network."
                        PairingFailure.UNKNOWN_HOST -> "This PC is no longer in the list."
                        PairingFailure.FAILED -> st.message ?: "Pairing failed."
                    },
                )
            }
        }.catch { emit(PairingState.Failed(it.message ?: "Pairing failed.")) }
    }

    override suspend fun wake(hostId: String): Result<Unit> =
        if (engine.wake(hostId)) Result.success(Unit) else Result.failure(IllegalStateException("This PC hasn't shared a network address for Wake-on-LAN yet."))

    override suspend fun refresh(hostId: String): Host? = runCatching { engine.refresh(hostId)?.toDomain() }.getOrNull()

    override suspend fun sleep(hostId: String): Result<Unit> =
        runCatching { engine.sleepHost(hostId) }.recoverCatching { throw IllegalStateException(hostActionMessage(it, sleep = true)) }

    override suspend fun running(hostId: String): Result<io.github.f_e_n_y_x.nebula.domain.model.RunningGame?> =
        runCatching {
            engine.running(hostId)?.let { r ->
                io.github.f_e_n_y_x.nebula.domain.model.RunningGame(
                    gameId = r.appId, name = r.name, sinceEpochS = r.sinceEpochS,
                    display = when (r.display) {
                        NovaDisplayMode.VIRTUAL -> DisplayMode.VIRTUAL
                        NovaDisplayMode.MIRROR -> DisplayMode.MIRROR
                        null -> null
                    },
                    connectedClients = r.connectedClients,
                    tracked = r.tracked,
                )
            }
        }.recoverCatching { throw IllegalStateException(if (it is java.io.IOException) "Couldn't reach the PC." else it.message ?: "Couldn't ask the PC.") }

    override suspend fun quitApp(hostId: String): Result<Unit> =
        runCatching { engine.quitApp(hostId) }.recoverCatching { throw io.github.f_e_n_y_x.nebula.domain.QuitFailure(
                quitMessage(it),
                unreachable = it is java.io.IOException && it !is HostRefusedException && it !is com.limelight.nvstream.http.HostHttpResponseException,
            ) }

    override suspend fun commands(hostId: String, gameId: String?): HostCommands = withContext(Dispatchers.IO) {
        val host = engine.hosts.value.firstOrNull { it.id == hostId }?.toDomain() ?: return@withContext HostCommands.None
        val listed = if (host.features.commands == Gate.AVAILABLE) runCatching { engine.hostCommands(hostId) }.getOrNull() else null
        val global = listed?.commands.orEmpty()
        // A game's SuperCmds are its own commands followed by the host-wide ones; the Nova list says which is which.
        val app = gameId?.let { id -> runCatching { engine.apps(hostId).first() }.getOrNull()?.firstOrNull { it.id == id } }
        val byId = global.associateBy { it.id }
        val forApp = app?.commands.orEmpty().map { c -> (byId[c.id] ?: c.copy(appNovaId = app?.novaId ?: "app")).toDomain() }
        val visible = HostGating.visibleCommands(host, global.filter { it.appNovaId == null }.map { it.toDomain() }, forApp)
        if (listed?.allowed == false) visible.copy(notAllowed = true) else visible
    }

    override suspend fun runCommand(hostId: String, commandId: String): Result<Unit> =
        runCatching { engine.runHostCommand(hostId, commandId) }.recoverCatching { throw IllegalStateException(hostActionMessage(it, sleep = false)) }

    override fun pairingAs(): PairingAs = engine.pairingIdentity().let { PairingAs(it.displayName, it.deviceName) }

    override fun setPairingDeviceName(name: String) = engine.setPairingDeviceName(name)
    override suspend fun testConnection(hostId: String, onProgress: (Float) -> Unit): Result<ConnectionReport> =
        runConnectionTest { engine.testConnection(hostId, display.spec(), onProgress) }

    override fun startWatching() = engine.startDiscovery()
    override fun stopWatching() = engine.stopDiscovery()
}

private fun EngineHostCommand.toDomain() = HostCommand(
    id = id, name = name, confirm = confirm, appScoped = appNovaId != null, icon = icon, runnable = runnable, running = running,
)

/** Turns an engine failure into a sentence for a toast (phase-1 wire contract refusal codes). */
internal fun hostActionMessage(e: Throwable, sleep: Boolean): String = when (e) {
    is HostRefusedException -> when (e.code) {
        401, 403 -> "This device isn't allowed to ${if (sleep) "put the PC to sleep" else "run host commands"}. ${HostGating.PERMISSION_HINT}."
        409 -> if (sleep) "Another device is streaming from this PC, so it stays awake." else "That command is already running."
        404 -> if (sleep) "This PC can't be put to sleep from Nebula." else "The PC doesn't have that command right now (app commands need their game running)."
        503 -> "The PC refused: ${e.message}"
        else -> e.message ?: "The PC refused."
    }
    is java.io.IOException -> "Couldn't reach the PC."
    else -> e.message ?: "Something went wrong."
}

internal fun quitMessage(e: Throwable): String = when (e) {
    is HostRefusedException -> when (e.code) {
        401, 403 -> "This device isn't allowed to close games on the PC."
        599 -> "The PC tried, but the game is still running."
        else -> e.message ?: "The PC refused."
    }
    is java.io.IOException -> "Couldn't reach the PC. It may be asleep or off."
    else -> e.message ?: "Something went wrong."
}

private fun EngineHost.toDomain() = Host(
    id = id,
    name = name,
    address = (activeAddress ?: addresses.firstOrNull())?.address ?: "",
    status = when (state) {
        HostState.ONLINE -> if (runningAppId != null) HostStatus.STREAMING else HostStatus.ONLINE
        HostState.OFFLINE -> HostStatus.OFFLINE
        HostState.UNKNOWN -> HostStatus.UNKNOWN
    },
    paired = paired,
    isNova = isNova,
    version = novaCapabilities?.version?.let { "Nova $it" },
    novaFeatures = novaCapabilities?.features,
    runningGameId = runningAppId?.toString(),
    features = HostGating.features(paired, novaCapabilities?.features, novaCapabilities?.permissions),
    canWake = HostGating.canWake(paired, macAddress),
)

class EngineLibraryRepository(private val engine: NebulaEngine) : LibraryRepository {
    override fun observeGames(hostId: String): Flow<List<Game>> =
        engine.apps(hostId).map { apps -> apps.map { it.toGame(hostId) } }.catch { emit(emptyList()) }

    override suspend fun details(hostId: String, gameId: String): GameDetails? {
        val d = engine.details(hostId, gameId) ?: return null
        return GameDetails(
            gameId = gameId,
            description = d.description,
            genres = d.genres,
            developer = d.developer,
            publisher = d.publisher,
            releaseDate = d.releaseDate,
            metacritic = d.metacritic,
            screenshots = d.screenshots.map { EngineArtFetcher.screenshotUri(hostId, it) },
            lastSession = d.lastSession?.takeIf { it.resolution != null }?.let {
                LastSession(it.device ?: "", it.resolution!!, it.fps ?: 0, it.codec ?: "")
            },
        )
    }
}

private fun HostApp.toGame(hostId: String): Game {
    fun art(kind: ArtKind) = if (kind == ArtKind.POSTER || kind in availableArt) EngineArtFetcher.artUri(hostId, id, kind) else null
    return Game(
        id = id,
        hostId = hostId,
        name = name,
        kind = if (isDesktop) GameKind.DESKTOP else GameKind.GAME,
        art = GameArt(poster = art(ArtKind.POSTER), hero = art(ArtKind.HERO), logo = art(ArtKind.LOGO), icon = art(ArtKind.ICON)),
        running = running,
        lastPlayedEpochS = lastPlayed,
        playtimeS = playtimeSeconds ?: 0,
        hostDefaultMode = when (modeDefault) {
            NovaDisplayMode.VIRTUAL -> DisplayMode.VIRTUAL
            NovaDisplayMode.MIRROR -> DisplayMode.MIRROR
            null -> null
        },
    )
}

/**
 * Stream settings live in the engine's (V+) preferences so both apps and every V+ option agree;
 * "match this device" and the default screen are Nebula-only and stay in [local].
 */
class EnginePreferencesRepository(
    private val engine: NebulaEngine,
    private val local: PreferencesStore,
    private val deviceResolution: () -> Pair<Int, Int>,
) : PreferencesRepository by local {
    private val changed = MutableStateFlow(0)

    override val streamSettings: Flow<StreamSettings> = combine(local.streamSettings, changed) { l, _ ->
        val r = engine.preferences.defaultRequest()
        l.copy(
            resolution = if (l.resolution == Resolution.Native) Resolution.Native else Resolution(r.width, r.height),
            fps = r.fps,
            bitrateKbps = r.bitrateKbps,
            codec = when (r.codec) {
                CodecPreference.AUTO -> VideoCodec.AUTO
                CodecPreference.H264 -> VideoCodec.H264
                CodecPreference.HEVC -> VideoCodec.HEVC
                CodecPreference.AV1 -> VideoCodec.AV1
            },
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun updateStreamSettings(transform: (StreamSettings) -> StreamSettings) {
        val next = transform(streamSettings.first())
        local.updateStreamSettings { next }
        withContext(Dispatchers.IO) {
            val (w, h) = next.resolution.orDevice(deviceResolution)
            engine.preferences.save(
                engine.preferences.defaultRequest().copy(width = w, height = h, fps = next.fps, bitrateKbps = next.bitrateKbps, codec = next.codec.toEngine()),
            )
        }
        changed.value++
    }
}

private fun Resolution.orDevice(device: () -> Pair<Int, Int>) = if (width > 0 && height > 0) width to height else device()

private fun VideoCodec.toEngine() = when (this) {
    VideoCodec.AUTO -> CodecPreference.AUTO
    VideoCodec.H264 -> CodecPreference.H264
    VideoCodec.HEVC -> CodecPreference.HEVC
    VideoCodec.AV1 -> CodecPreference.AV1
}

class EngineArtworkRepository(private val engine: NebulaEngine) : ArtworkRepository {
    override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) { engine.artCache.usedBytes() }
    override fun setLimit(bytes: Long) { engine.artCache.limitBytes = bytes }
    override suspend fun clear() = withContext(Dispatchers.IO) { engine.artCache.clear() }

    override suspend fun refreshHost(hostId: String) {
        withContext(Dispatchers.IO) { engine.artCache.clearHost(hostId) }
        engine.refresh(hostId)
    }

    override suspend fun refreshGame(hostId: String, gameId: String) =
        withContext(Dispatchers.IO) { engine.artCache.clearApp(hostId, gameId) }
}

class EngineStreamRepository(
    private val engine: NebulaEngine,
    private val deviceResolution: () -> Pair<Int, Int>,
    context: android.content.Context,
    private val display: DeviceDisplay = { deviceResolution().let { (w, h) -> Triple(w, h, 60) } },
) : StreamRepository {
    private val app = context.applicationContext
    private val legacy = io.github.f_e_n_y_x.nebula.settings.LegacyPrefs(app)
    @Volatile private var lookup: io.github.f_e_n_y_x.nebula.input.ControllerLookup? = null
    private var feedback: ControllerFeedback? = null
    private var motion: MotionForwarder? = null
    private var rotation: () -> Int = { 0 }

    private fun motionSettings() = io.github.f_e_n_y_x.nebula.settings.MotionSettings.read(legacy)
    private fun rumbleSettings() = io.github.f_e_n_y_x.nebula.settings.RumbleSettings.read(legacy.prefs.all)

    private val current = kotlinx.coroutines.flow.MutableStateFlow<StreamSession?>(null)
    private var session: StreamSession?
        get() = current.value
        set(value) { current.value = value }

    /** The stream [start] is running, so a live resolution change can swap its connection. */
    @Volatile
    private var run: Run? = null

    /** Input for the live stream, or null when nothing is connected. */
    val input: InputBridge? get() = session?.takeIf { it.isConnected }?.input

    override val remoteInput = io.github.f_e_n_y_x.nebula.input.BridgeInput { input }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val backgrounded: Flow<Boolean> = current.flatMapLatest { it?.backgrounded ?: kotlinx.coroutines.flow.flowOf(false) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val link: Flow<StreamLink> = current.flatMapLatest { session ->
        val l = session?.takeIf { it.isLinkReady }?.link ?: return@flatMapLatest kotlinx.coroutines.flow.flowOf(StreamLink())
        combine(l.micSupported, l.micLive, l.micPausedInBackground, l.clipboard) { supported, live, paused, clip ->
            StreamLink(
                micEnabled = l.micEnabledInSettings,
                micSupported = supported,
                micLive = live,
                micPaused = paused,
                micWantedAtStart = l.micWantedAtStart(),
                clipboard = when (clip) {
                    ClipboardSync.OFF -> ClipboardMode.OFF
                    ClipboardSync.UNSUPPORTED -> ClipboardMode.UNSUPPORTED
                    ClipboardSync.NOT_ALLOWED -> ClipboardMode.NOT_ALLOWED
                    ClipboardSync.ACTIVE -> ClipboardMode.SYNCING
                },
            )
        }
    }

    override fun setMicLive(on: Boolean): Boolean = session?.takeIf { it.isLinkReady }?.link?.setMicLive(on) ?: false

    override fun sendClipboard(): ClipboardSendResult = when (session?.takeIf { it.isLinkReady }?.link?.sendClipboardNow()) {
        ClipboardSend.SENT -> ClipboardSendResult.SENT
        ClipboardSend.EMPTY -> ClipboardSendResult.EMPTY
        else -> ClipboardSendResult.NOT_SYNCING
    }

    override fun onWindowFocus(focused: Boolean) {
        session?.takeIf { it.isLinkReady }?.link?.onWindowFocusChanged(focused)
    }

    override fun reattach(target: StreamTarget) {
        val surface = target as? SurfaceStreamTarget ?: return
        // A live resolution switch waiting for its fresh surface takes it; otherwise the running
        // session moves onto it (back from the background).
        if (run?.offerSurface(surface) == true) return
        session?.attachSurface(surface.holder)
    }

    private val surfaceGen = kotlinx.coroutines.flow.MutableStateFlow(0)
    override val surfaceGeneration: Flow<Int> = surfaceGen

    override suspend fun setBitrate(kbps: Int): Boolean {
        val ok = session?.setBitrate(kbps) ?: false
        // A later resolution change reconnects at the bitrate the stream has now.
        if (ok) run?.let { it.request = it.request.copy(bitrateKbps = kbps) }
        return ok
    }

    override suspend fun setDisplayScale(percent: Int): Boolean = session?.setDisplayScale(percent) ?: false
    override suspend fun testConnection(): Result<ConnectionReport> {
        val s = session?.takeIf { it.isConnected } ?: return Result.failure(ConnectionTestError("Nothing is streaming."))
        return runConnectionTest { s.testConnection(display.spec()) }
    }

    override suspend fun switchMode(mode: VideoMode): Result<Unit> =
        run?.switchTo(mode) ?: Result.failure(IllegalStateException("Nothing is streaming."))

    override fun setFramegenPaused(paused: Boolean, force: Boolean): Boolean = session?.setFramegenPaused(paused, force) ?: false

    override fun refreshUpscaler() {
        session?.refreshUpscaler()
    }

    override fun bindControllers(lookup: io.github.f_e_n_y_x.nebula.input.ControllerLookup?) {
        this.lookup = lookup
    }

    override fun padCapabilities(deviceId: Int, index: Int): Int {
        val device = android.view.InputDevice.getDevice(deviceId)
        val hw = PadHardware.of(device)
        var caps = hw.capabilities()
        // The phone pad (no controller): this device's vibrator plays the game's rumble.
        if (device == null) caps = caps or com.limelight.nvstream.jni.MoonBridge.LI_CCAP_RUMBLE.toInt()
        // This device's sensors stand in for controller 0 when the pad has none (passthrough only).
        if (index == 0 && !hw.gyro) caps = caps or (motion?.phoneCapabilities() ?: 0)
        return caps
    }

    override fun refreshFeedback() {
        motion?.refresh()
    }

    override fun applyAudioHaptics(settings: io.github.f_e_n_y_x.nebula.settings.HapticsSettings): Boolean =
        session?.setAudioHaptics(settings.toEngine()) ?: true

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val unsupportedFeatures: Flow<List<String>> =
        current.flatMapLatest { s -> s?.unsupportedHostFeatures?.map { set -> set.map { "${it.title} (coming in ${it.plannedFor})" } } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }

    override fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState> = callbackFlow {
        val surface = target as? SurfaceStreamTarget ?: error("The engine streams into a SurfaceStreamTarget")
        send(StreamState.Starting)
        val (w, h) = settings.resolution.orDevice(deviceResolution)
        val request = engine.preferences.defaultRequest().copy(
            width = w, height = h, fps = settings.fps, bitrateKbps = settings.bitrateKbps, codec = settings.codec.toEngine(),
            novaDisplay = if (mode == DisplayMode.MIRROR) NovaDisplayMode.MIRROR else NovaDisplayMode.VIRTUAL,
        )
        val fb = ControllerFeedback(app, { lookup }, ::rumbleSettings, onDeviceRumble = { session?.setGameRumbleOnDevice(it) })
        val mf = MotionForwarder(app, { lookup }, ::motionSettings, rotation = { rotation() }) { c, type, x, y, z -> input?.motion(c, type, x, y, z) }
        feedback = fb
        motion = mf
        @Suppress("DEPRECATION")
        rotation = { surface.activity.windowManager.defaultDisplay.rotation }
        val r = Run(this, game, surface, request, fb, mf)
        run = r
        // startStream must run on the main thread with a live surface.
        withContext(Dispatchers.Main.immediate) {
            runCatching { r.open(request) }
                .onFailure { trySend(StreamState.Failed(it.message ?: "Couldn't start the stream.")); channel.close() }
        }
        awaitClose {
            if (run === r) run = null
            session?.disconnect()
            session = null
            releaseFeedback()
        }
    }

    private fun releaseFeedback() {
        feedback?.release(); feedback = null
        motion?.release(); motion = null
    }

    override fun stop(quitApp: Boolean) {
        val s = session ?: return
        if (quitApp) s.quit() else s.disconnect()
    }

    /**
     * One [start] call: the flow the UI collects outlives the engine sessions behind it, so a
     * resolution change can end one connection and resume the app on a new one without the screen
     * seeing the stream end. Callbacks from a session that is no longer [session] are ignored.
     */
    private inner class Run(
        private val out: ProducerScope<StreamState>,
        private val game: Game,
        @Volatile private var surface: SurfaceStreamTarget,
        @Volatile var request: StreamRequest,
        private val feedback: ControllerFeedback,
        private val motion: MotionForwarder,
    ) {
        private var statsJob: Job? = null

        /** Set while a switch waits for the stream screen's new SurfaceView. */
        @Volatile
        private var surfaceWaiter: CompletableDeferred<SurfaceStreamTarget>? = null

        /** Hands a newly created surface to a switch waiting for one; false when none waits. Main thread. */
        fun offerSurface(target: SurfaceStreamTarget): Boolean {
            val w = surfaceWaiter ?: return false
            if (target.holder.surface?.isValid != true) return false
            surfaceWaiter = null
            return w.complete(target)
        }

        /**
         * Asks the stream screen for a new SurfaceView and waits for it. Keeps the current surface
         * when none arrives in time (a screen without a SurfaceView, e.g. in tests).
         */
        private suspend fun renewSurface() {
            val waiter = CompletableDeferred<SurfaceStreamTarget>()
            withContext(Dispatchers.Main.immediate) {
                surfaceWaiter = waiter
                surfaceGen.value = surfaceGen.value + 1
            }
            val fresh = withTimeoutOrNull(SURFACE_TIMEOUT_MS) { waiter.await() }
            withContext(Dispatchers.Main.immediate) { if (surfaceWaiter === waiter) surfaceWaiter = null }
            if (fresh != null) surface = fresh
            else android.util.Log.w("Nebula", "Live resolution: no new video surface within $SURFACE_TIMEOUT_MS ms; reusing the old one")
        }

        /** Ends [s] and waits (up to [STOP_TIMEOUT_MS]) until its connection is torn down. */
        private suspend fun stopAndWait(s: StreamSession) {
            val stopped = CompletableDeferred<Unit>()
            s.disconnect { stopped.complete(Unit) }
            if (withTimeoutOrNull(STOP_TIMEOUT_MS) { stopped.await() } == null) {
                android.util.Log.w("Nebula", "Live resolution: the old connection took over $STOP_TIMEOUT_MS ms to stop")
            }
        }

        /** Set while a switch waits for its new connection; failures complete it instead of ending the flow. */
        @Volatile
        private var pending: CompletableDeferred<Result<Unit>>? = null

        /** Starts a session for [req] and makes it current. Main thread. */
        fun open(req: StreamRequest): StreamSession {
            var mine: StreamSession? = null
            // Callbacks are posted to the main thread, so they run after `mine` is set below.
            fun isMine() = mine != null && session === mine
            val listener = object : StreamListener {
                override fun onStageStarting(stage: String) {
                    if (isMine()) io.github.f_e_n_y_x.nebula.diagnostics.ConnectionTimeline.shared.stage(stage)
                }

                override fun onConnected() {
                    val s = mine ?: return
                    if (!isMine()) return
                    statsJob?.cancel()
                    statsJob = out.launch {
                        combine(s.stats, s.framegen, s.upscaling) { st, fg, up -> st.toDomain().copy(post = io.github.f_e_n_y_x.nebula.framegen.postProcessStats(fg, up)) }
                            .collect { out.trySend(StreamState.Live(it)) }
                    }
                    s.audioHapticsGamepad = feedback
                    pending?.complete(Result.success(Unit))
                }

                override fun onFramegenEvent(event: io.github.fenyx.nebula.engine.framegen.FramegenEvent) =
                    io.github.f_e_n_y_x.nebula.framegen.FramegenAppSetup.onEvent(surface.activity, event)

                override fun onRumble(controller: Int, lowFreq: Int, highFreq: Int) { if (isMine()) feedback.rumble(controller, lowFreq, highFreq) }
                override fun onRumbleTriggers(controller: Int, left: Int, right: Int) { if (isMine()) feedback.rumbleTriggers(controller, left, right) }
                override fun onControllerLed(controller: Int, r: Int, g: Int, b: Int) { if (isMine()) feedback.setLed(controller, r, g, b) }
                override fun onMotionRequest(controller: Int, type: io.github.fenyx.nebula.engine.MotionType, rateHz: Int) {
                    if (isMine()) motion.onRequest(controller, type, rateHz)
                }

                override fun onStageFailed(stage: String, errorCode: Int) {
                    if (!isMine()) return
                    val why = "Couldn't start the stream: $stage failed (error $errorCode)."
                    val p = pending
                    if (p != null) p.complete(Result.failure(IllegalStateException(why))) else out.trySend(StreamState.Failed(why))
                }

                override fun onEnded(reason: StreamEndReason, errorCode: Int) {
                    if (!isMine()) return
                    session = null
                    statsJob?.cancel()
                    val p = pending
                    if (p != null) {
                        p.complete(Result.failure(IllegalStateException(reason.message(errorCode) ?: "The PC ended the connection.")))
                        return
                    }
                    out.trySend(
                        when (reason) {
                            StreamEndReason.USER_QUIT -> StreamState.Ended(null)
                            StreamEndReason.ERROR -> StreamState.Failed(reason.message(errorCode)!!)
                            else -> StreamState.Ended(reason.message(errorCode))
                        },
                    )
                    releaseFeedback()
                    out.channel.close()
                }
            }
            val s = engine.startStream(surface.activity, game.hostId, game.id, req, surface.holder, listener)
            mine = s
            session = s
            return s
        }

        /**
         * Ends the current connection (the game keeps running on the PC), then resumes the app at
         * [mode] into the same surface. Only /resume is used, so the PC never launches or quits
         * anything; Nova applies the new size to its display when it sees no active session.
         */
        suspend fun switchTo(mode: VideoMode): Result<Unit> {
            val old = withContext(Dispatchers.Main.immediate) {
                session.also {
                    // Retire it first: its "ended" callback must not end the flow.
                    session = null
                    statsJob?.cancel()
                }
            }
            if (old != null) {
                stopAndWait(old)
                // Let the PC see the disconnect before /resume, so it reconfigures the display.
                delay(HOST_SETTLE_MS)
            }
            // Every connection gets a new surface: the old one may still have a producer attached.
            renewSurface()
            val req = request.copy(width = mode.width, height = mode.height, fps = mode.fps, resumeOnly = true)
            val result = CompletableDeferred<Result<Unit>>()
            pending = result
            try {
                withContext(Dispatchers.Main.immediate) {
                    runCatching { open(req) }.onFailure { result.complete(Result.failure(it)) }
                }
                val r = result.await()
                if (r.isSuccess) {
                    request = req
                } else {
                    // Tear the failed attempt down completely before a rollback starts the next one.
                    val failed = withContext(Dispatchers.Main.immediate) { session.also { session = null } }
                    if (failed != null) stopAndWait(failed)
                }
                return r
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Timed out or the screen went away: don't leave a half-open connection behind.
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main.immediate) { session?.disconnect(); session = null }
                throw e
            } finally {
                pending = null
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 3_000L

        /**
         * Nova (Sunshine) only reconfigures its display on /resume when no session is active. The
         * ENet disconnect is sent by the time the old connection has stopped, and /resume follows a
         * serverinfo round trip; this small pause covers a busy host.
         */
        const val HOST_SETTLE_MS = 250L

        /** How long a switch waits for the stream screen's new SurfaceView. */
        const val SURFACE_TIMEOUT_MS = 2_000L
    }
}

private fun StreamEndReason.message(errorCode: Int): String? = when (this) {
    StreamEndReason.USER_QUIT -> null
    StreamEndReason.HOST_ENDED -> "The PC ended the stream."
    StreamEndReason.DISCONNECTED -> "The connection to the PC was lost."
    StreamEndReason.ERROR -> "The stream stopped with error $errorCode."
}

private fun io.github.f_e_n_y_x.nebula.settings.HapticsSettings.toEngine() = io.github.fenyx.nebula.engine.AudioHapticsConfig(
    enabled = enabled,
    strength = strength,
    route = io.github.fenyx.nebula.engine.AudioHapticsRoute.fromWire(route),
    scene = io.github.fenyx.nebula.engine.AudioHapticsScene.fromWire(scene),
)

/** Runs an engine connection test and turns its failures into messages for the user. */
private suspend fun runConnectionTest(block: suspend () -> ConnectionTestResult): Result<ConnectionReport> = try {
    Result.success(block().toDomain())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: ConnectionTestException) {
    Result.failure(ConnectionTestError(e.failure.message(e.retryAfterMs), e.retryAfterMs))
} catch (e: Exception) {
    Result.failure(ConnectionTestError(e.message ?: "The connection test failed."))
}

internal fun ConnectionTestFailure.message(retryAfterMs: Long): String = when (this) {
    ConnectionTestFailure.STREAM_ACTIVE -> "The PC is streaming. The full test runs between streams; use Test connection in the stream menu for a quick check."
    ConnectionTestFailure.RATE_LIMITED -> "Tested a moment ago. Try again in ${((retryAfterMs + 999) / 1000).coerceAtLeast(1)} s."
    ConnectionTestFailure.UNSUPPORTED -> "This PC can't test the connection. Nova and Sunshine Foundation hosts can."
    ConnectionTestFailure.NOT_PAIRED -> "Pair with this PC first."
    ConnectionTestFailure.OFFLINE -> "Couldn't reach the PC."
    ConnectionTestFailure.FAILED -> "The connection test failed. Try again."
}

internal fun ConnectionTestResult.toDomain() = ConnectionReport(
    rttMs = rttMs,
    jitterMs = jitterMs,
    lossPercent = lossPercent,
    throughputMbps = throughputMbps,
    quality = when (quality) {
        ConnectionQuality.EXCELLENT -> LinkQuality.EXCELLENT
        ConnectionQuality.GOOD -> LinkQuality.GOOD
        ConnectionQuality.FAIR -> LinkQuality.FAIR
        ConnectionQuality.POOR -> LinkQuality.POOR
    },
    suggestion = suggestion?.let { SuggestedSettings(VideoMode(it.width, it.height, it.fps), it.bitrateKbps, it.nativeResolution) },
    duringStream = duringStream,
)

internal fun AbrState.toDomain() = AbrInfo(
    mode = when (mode) {
        AbrMode.CONSERVATIVE -> "Conservative"
        AbrMode.AGGRESSIVE -> "Aggressive"
        else -> "Balanced"
    },
    source = when (source) {
        io.github.fenyx.nebula.engine.AbrSource.HOST -> AbrSource.HOST
        io.github.fenyx.nebula.engine.AbrSource.CONNECTING -> AbrSource.CONNECTING
        io.github.fenyx.nebula.engine.AbrSource.LOCAL -> AbrSource.LOCAL
    },
    minKbps = minKbps,
    maxKbps = maxKbps,
    lastReason = lastReason,
)

private fun EngineStats.toDomain(): StreamStats {
    val d = decoder?.lowercase().orEmpty()
    val codec = when {
        "av1" in d || "av01" in d -> "AV1"
        "hevc" in d || "h265" in d -> "HEVC"
        "avc" in d || "h264" in d -> "H.264"
        else -> "—"
    }
    return StreamStats(
        resolution = "${width}×$height",
        fps = fps.roundToInt(),
        bitrateMbps = bitrateKbps / 1000f,
        latencyMs = latency.totalMs,
        codec = if (hdr) "$codec HDR" else codec,
        width = width,
        height = height,
        receivedFps = receivedFps,
        hostFps = hostFps,
        onePercentLowFps = onePercentLowFps,
        jitterMs = jitterMs,
        lossPercent = lossPercent,
        hostMs = latency.hostMs,
        networkMs = latency.networkMs,
        decodeMs = latency.decodeMs,
        renderMs = latency.renderMs,
        decoder = decoder.orEmpty(),
        hdr = hdr,
        targetBitrateKbps = targetBitrateKbps,
        abr = abr?.toDomain(),
    )
}
