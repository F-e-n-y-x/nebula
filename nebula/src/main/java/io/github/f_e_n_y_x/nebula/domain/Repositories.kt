package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameDetails
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardSendResult
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import io.github.f_e_n_y_x.nebula.domain.model.LibraryOptions
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import kotlinx.coroutines.flow.Flow

interface HostRepository {
    fun observeHosts(): Flow<List<Host>>
    /** Look for hosts on the local network; results arrive through [observeHosts]. */
    suspend fun discover()
    suspend fun addManual(address: String): Result<Host>
    fun pair(hostId: String): Flow<PairingState>
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
    /** Changes the bitrate mid-stream; true once the PC accepted it. */
    suspend fun setBitrate(kbps: Int): Boolean = false
    /** Input for the running stream, or null when nothing is connected. */
    val remoteInput: io.github.f_e_n_y_x.nebula.input.RemoteInput? get() = null
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
}
