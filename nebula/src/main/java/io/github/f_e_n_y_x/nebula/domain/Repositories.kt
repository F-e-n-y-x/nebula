package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameDetails
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardSendResult
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import io.github.f_e_n_y_x.nebula.domain.model.LibraryOptions
import io.github.f_e_n_y_x.nebula.domain.model.PairingAs
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import kotlinx.coroutines.flow.Flow

interface HostRepository {
    fun observeHosts(): Flow<List<Host>>
    /** Look for hosts on the local network; results arrive through [observeHosts]. */
    suspend fun discover()
    suspend fun addManual(address: String): Result<Host>
    fun pair(hostId: String): Flow<PairingState>
    /** The name this device pairs under. */
    fun pairingAs(): PairingAs = PairingAs("Nebula", "")
    /** Sets the device part of the pairing name; blank goes back to the automatic name. */
    fun setPairingDeviceName(name: String) {}
    suspend fun wake(hostId: String): Result<Unit>
    /** Polls one host now; null when unknown. */
    suspend fun refresh(hostId: String): Host? = null
    /** Suspends the PC (Nova `/pcsleep`). Failure messages are ready to show. */
    suspend fun sleep(hostId: String): Result<Unit> = Result.failure(UnsupportedOperationException("This PC can't be put to sleep from Nebula."))
    /**
     * Host commands for [gameId] (its own plus host-wide ones), or host-wide only when null.
     * Filtered by [HostGating.visibleCommands].
     */
    suspend fun commands(hostId: String, gameId: String?): HostCommands = HostCommands.None
    /** Runs a host command; failure messages are ready to show. */
    suspend fun runCommand(hostId: String, commandId: String): Result<Unit> = Result.failure(UnsupportedOperationException("This PC has no commands."))
    /** Keep watching the network for hosts while the app is visible (onStart / onStop). */
    fun startWatching() {}
    fun stopWatching() {}
}

interface LibraryRepository {
    fun observeGames(hostId: String): Flow<List<Game>>
    suspend fun details(hostId: String, gameId: String): GameDetails?
}

interface PreferencesRepository {
    val streamSettings: Flow<StreamSettings>
    suspend fun updateStreamSettings(transform: (StreamSettings) -> StreamSettings)
    /** The mode the user last chose for this game, or null if never chosen. */
    fun modeFor(hostId: String, gameId: String): Flow<DisplayMode?>
    suspend fun setMode(hostId: String, gameId: String, mode: DisplayMode)
    /** The size and frame rate chosen for this game from the stream menu, or null to use Settings. */
    fun videoModeFor(hostId: String, gameId: String): Flow<VideoMode?>
    suspend fun setVideoMode(hostId: String, gameId: String, mode: VideoMode?)
    val libraryOptions: Flow<LibraryOptions>
    suspend fun updateLibraryOptions(transform: (LibraryOptions) -> LibraryOptions)
    val lastHostId: Flow<String?>
    suspend fun setLastHost(hostId: String)
}

/** The artwork cache and re-fetching art and details from a host. */
interface ArtworkRepository {
    suspend fun usedBytes(): Long
    fun setLimit(bytes: Long)
    suspend fun clear()
    /** Drops cached art for every game on [hostId] and asks the host for its list again. */
    suspend fun refreshHost(hostId: String)
    /** Drops [gameId]'s cached art so the next load fetches it fresh. */
    suspend fun refreshGame(hostId: String, gameId: String)
}

/**
 * Where decoded video goes. The UI layer supplies a platform implementation (an Activity plus a
 * created SurfaceHolder); [None] is for repositories that render nothing (the debug demo).
 */
interface StreamTarget {
    data object None : StreamTarget
}

interface StreamRepository {
    fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState>
    /** Ends the stream; [quitApp] also closes the game on the PC instead of leaving it to resume. */
    fun stop(quitApp: Boolean)
    /** Moves a running stream onto a new surface (after the app returns from the background). */
    fun reattach(target: StreamTarget) {}

    /**
     * Bumped when the next connection needs a brand-new video surface; the stream screen recreates
     * its SurfaceView and hands the new one over through [reattach]. A surface a previous connection
     * rendered into can keep a producer attached (frame generation presents with
     * ANativeWindow_lock, which connects it as a CPU producer for good), and a new decoder can't
     * connect to it: the live resolution switch failed to start its video stream on the same view.
     */
    val surfaceGeneration: Flow<Int> get() = kotlinx.coroutines.flow.flowOf(0)
    /**
     * Reconnects the running stream at [mode] without quitting the game on the PC: the connection
     * ends and the same app is resumed at the new size and frame rate (same display mode), into a
     * fresh surface (see [surfaceGeneration]). Returns once the new connection is live. On failure
     * the stream is left disconnected and may be switched again (to roll back); the start() flow
     * stays open meanwhile.
     */
    suspend fun switchMode(mode: VideoMode): Result<Unit> = Result.failure(UnsupportedOperationException("Changing resolution live isn't supported here."))
    /** Asks the PC to scale its desktop UI to [percent] (resolution unchanged); true once accepted. */
    suspend fun setDisplayScale(percent: Int): Boolean = false
    /** Changes the bitrate mid-stream; true once the PC accepted it. */
    suspend fun setBitrate(kbps: Int): Boolean = false
    /** Input for the running stream, or null when nothing is connected. */
    val remoteInput: io.github.f_e_n_y_x.nebula.input.RemoteInput? get() = null
    /**
     * Stream-menu quick toggle for frame generation: pauses (decoded frames only) or resumes it.
     * Resuming after a thermal auto-off needs [force]. False when refused or not available.
     */
    fun setFramegenPaused(paused: Boolean, force: Boolean = false): Boolean = false
    /** Applies changed upscaler settings to the running stream. */
    fun refreshUpscaler() {}
    /** True while the stream is kept alive without a surface (app in the background). */
    val backgrounded: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    /** Mic and clipboard state of the running stream. */
    val link: Flow<StreamLink> get() = kotlinx.coroutines.flow.flowOf(StreamLink())
    /** Starts or stops sending the microphone; false when it couldn't start. */
    fun setMicLive(on: Boolean): Boolean = false
    /** Sends this device's clipboard to the PC now (call from a user action, while focused). */
    fun sendClipboard(): ClipboardSendResult = ClipboardSendResult.NOT_SYNCING
    /** The stream window gained or lost input focus (Android 10+ clipboard reads need focus). */
    fun onWindowFocus(focused: Boolean) {}

    /** Lets rumble, lights and motion find the physical pad behind each host controller. */
    fun bindControllers(lookup: io.github.f_e_n_y_x.nebula.input.ControllerLookup?) {}

    /** LI_CCAP bits to announce for a physical pad (motors, IMU, light bar); null = the mapper's default. */
    fun padCapabilities(deviceId: Int, index: Int): Int? = null

    /** Motion, rumble or audio-haptics settings changed, or a pad came or went. */
    fun refreshFeedback() {}

    /**
     * Applies audio-haptics settings to the running stream; false when the change only takes effect
     * on the next stream (music on a fixed device route uses Android's audio-coupled generator).
     */
    fun applyAudioHaptics(settings: io.github.f_e_n_y_x.nebula.settings.HapticsSettings): Boolean = true

    /** Host features the running stream asked for that Nebula doesn't do yet, by title. */
    val unsupportedFeatures: Flow<List<String>> get() = kotlinx.coroutines.flow.flowOf(emptyList())
}
