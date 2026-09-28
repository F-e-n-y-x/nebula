package io.github.f_e_n_y_x.nebula.data.engine

import android.app.Activity
import android.view.SurfaceHolder
import io.github.f_e_n_y_x.nebula.data.PreferencesStore
import io.github.f_e_n_y_x.nebula.domain.ArtworkRepository
import io.github.f_e_n_y_x.nebula.domain.HostRepository
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
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.CodecPreference
import io.github.fenyx.nebula.engine.HostApp
import io.github.fenyx.nebula.engine.HostState
import io.github.fenyx.nebula.engine.InputBridge
import io.github.fenyx.nebula.engine.NebulaEngine
import io.github.fenyx.nebula.engine.NovaDisplayMode
import io.github.fenyx.nebula.engine.PairingFailure
import io.github.fenyx.nebula.engine.StreamEndReason
import io.github.fenyx.nebula.engine.StreamListener
import io.github.fenyx.nebula.engine.StreamSession
import kotlinx.coroutines.Dispatchers
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

class EngineHostRepository(private val engine: NebulaEngine) : HostRepository {
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

    override fun startWatching() = engine.startDiscovery()
    override fun stopWatching() = engine.stopDiscovery()
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
    runningGameId = runningAppId?.toString(),
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
) : StreamRepository {
    private val current = kotlinx.coroutines.flow.MutableStateFlow<StreamSession?>(null)
    private var session: StreamSession?
        get() = current.value
        set(value) { current.value = value }

    /** Input for the live stream, or null when nothing is connected. */
    val input: InputBridge? get() = session?.takeIf { it.isConnected }?.input

    override val remoteInput = io.github.f_e_n_y_x.nebula.input.BridgeInput { input }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val backgrounded: Flow<Boolean> = current.flatMapLatest { it?.backgrounded ?: kotlinx.coroutines.flow.flowOf(false) }

    override fun reattach(target: StreamTarget) {
        val surface = target as? SurfaceStreamTarget ?: return
        session?.attachSurface(surface.holder)
    }

    override suspend fun setBitrate(kbps: Int): Boolean = session?.setBitrate(kbps) ?: false

    override fun setFramegenPaused(paused: Boolean, force: Boolean): Boolean = session?.setFramegenPaused(paused, force) ?: false

    override fun refreshUpscaler() {
        session?.refreshUpscaler()
    }

    override fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState> = callbackFlow {
        val surface = target as? SurfaceStreamTarget ?: error("The engine streams into a SurfaceStreamTarget")
        send(StreamState.Starting)
        val (w, h) = settings.resolution.orDevice(deviceResolution)
        val request = engine.preferences.defaultRequest().copy(
            width = w, height = h, fps = settings.fps, bitrateKbps = settings.bitrateKbps, codec = settings.codec.toEngine(),
            novaDisplay = if (mode == DisplayMode.MIRROR) NovaDisplayMode.MIRROR else NovaDisplayMode.VIRTUAL,
        )
        val listener = object : StreamListener {
            override fun onConnected() {
                val s = session ?: return
                launch {
                    combine(s.stats, s.framegen, s.upscaling) { st, fg, up -> st.toDomain().copy(post = io.github.f_e_n_y_x.nebula.framegen.postProcessStats(fg, up)) }
                        .collect { trySend(StreamState.Live(it)) }
                }
            }

            override fun onFramegenEvent(event: io.github.fenyx.nebula.engine.framegen.FramegenEvent) =
                io.github.f_e_n_y_x.nebula.framegen.FramegenAppSetup.onEvent(surface.activity, event)

            override fun onStageFailed(stage: String, errorCode: Int) {
                trySend(StreamState.Failed("Couldn't start the stream: $stage failed (error $errorCode)."))
            }

            override fun onEnded(reason: StreamEndReason, errorCode: Int) {
                trySend(
                    when (reason) {
                        StreamEndReason.USER_QUIT -> StreamState.Ended(null)
                        StreamEndReason.HOST_ENDED -> StreamState.Ended("The PC ended the stream.")
                        StreamEndReason.DISCONNECTED -> StreamState.Ended("The connection to the PC was lost.")
                        StreamEndReason.ERROR -> StreamState.Failed("The stream stopped with error $errorCode.")
                    },
                )
                session = null
                channel.close()
            }
        }
        // startStream must run on the main thread with a live surface.
        withContext(Dispatchers.Main.immediate) {
            runCatching { engine.startStream(surface.activity, game.hostId, game.id, request, surface.holder, listener) }
                .onSuccess { session = it }
                .onFailure { trySend(StreamState.Failed(it.message ?: "Couldn't start the stream.")); channel.close() }
        }
        awaitClose { session?.disconnect(); session = null }
    }

    override fun stop(quitApp: Boolean) {
        val s = session ?: return
        if (quitApp) s.quit() else s.disconnect()
    }
}

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
        lossPercent = lossPercent,
        hostMs = latency.hostMs,
        networkMs = latency.networkMs,
        decodeMs = latency.decodeMs,
        renderMs = latency.renderMs,
        decoder = decoder.orEmpty(),
        hdr = hdr,
    )
}
