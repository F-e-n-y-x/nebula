package io.github.f_e_n_y_x.nebula.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.LiveResolutionSwitcher
import io.github.f_e_n_y_x.nebula.domain.Orientation
import io.github.f_e_n_y_x.nebula.domain.Portrait
import io.github.f_e_n_y_x.nebula.domain.PortraitStreaming
import io.github.f_e_n_y_x.nebula.domain.RotationFollower
import io.github.f_e_n_y_x.nebula.domain.orientation
import io.github.f_e_n_y_x.nebula.domain.SortLibrary
import io.github.f_e_n_y_x.nebula.domain.SwitchOutcome
import io.github.f_e_n_y_x.nebula.domain.SwitchState
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameDetails
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.LibraryOptions
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun <T> kotlinx.coroutines.flow.Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

class HostsViewModel(private val c: AppContainer) : ViewModel() {
    val hosts: StateFlow<List<Host>> = c.hosts.observeHosts().stateIn(this, emptyList())
    private val _searching = MutableStateFlow(false)
    val searching = _searching.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { discover() }

    fun discover() {
        if (_searching.value) return
        viewModelScope.launch {
            _searching.value = true
            runCatching { c.hosts.discover() }
            _searching.value = false
        }
    }

    fun addManual(address: String) = viewModelScope.launch {
        c.hosts.addManual(address).onFailure { _message.value = it.message }
    }

    fun wake(hostId: String) = viewModelScope.launch {
        c.hosts.wake(hostId).onFailure { _message.value = it.message }
    }

    fun select(hostId: String) = viewModelScope.launch { c.prefs.setLastHost(hostId) }
    fun clearMessage() { _message.value = null }
}

class PairViewModel(private val c: AppContainer, val hostId: String) : ViewModel() {
    val host: StateFlow<Host?> = c.hosts.observeHosts().map { l -> l.firstOrNull { it.id == hostId } }.stateIn(this, null)
    private val _state = MutableStateFlow<PairingState>(PairingState.Connecting)
    val state = _state.asStateFlow()
    private var job: Job? = null
    private val _pairingAs = MutableStateFlow(c.hosts.pairingAs())
    /** The name this device pairs under; editable on the pairing screen. */
    val pairingAs = _pairingAs.asStateFlow()
    private val _renamedLate = MutableStateFlow(false)
    /** True when the name changed after the host already had the request (it keeps the old one). */
    val renamedLate = _renamedLate.asStateFlow()

    init { start() }

    fun renameDevice(name: String) {
        c.hosts.setPairingDeviceName(name)
        val next = c.hosts.pairingAs()
        if (next != _pairingAs.value && _state.value is PairingState.ShowPin) _renamedLate.value = true
        _pairingAs.value = next
    }

    fun start() {
        job?.cancel()
        _renamedLate.value = false
        job = viewModelScope.launch {
            c.hosts.pair(hostId).collect {
                _state.value = it
                if (it is PairingState.Paired) c.prefs.setLastHost(hostId)
            }
        }
    }
}

data class LibraryUi(
    val host: Host? = null,
    val games: List<Game> = emptyList(),
    val focused: Game? = null,
    val focusedMode: DisplayMode = DisplayMode.VIRTUAL,
    val settings: StreamSettings = StreamSettings(),
    val loading: Boolean = true,
    val options: LibraryOptions = LibraryOptions(),
)

/** Data saver on a metered network: no hero art (the smaller poster/header stands in). */
private fun Game.forNetwork(saveData: Boolean) = if (saveData) copy(art = art.copy(hero = null)) else this

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val c: AppContainer, val hostId: String) : ViewModel() {
    private val focusedId = MutableStateFlow<String?>(null)

    private val options = c.prefs.libraryOptions
    private val games = combine(c.library.observeGames(hostId), options) { list, o ->
        val save = o.dataSaver && c.isMetered()
        SortLibrary(list).map { it.forNetwork(save) }
    }
    private val host = c.hosts.observeHosts().map { l -> l.firstOrNull { it.id == hostId } }

    private val focusedGame = combine(games, focusedId) { list, id -> list.firstOrNull { it.id == id } ?: list.firstOrNull() }

    private val mode = focusedGame.flatMapLatest { g -> if (g == null) flowOf(DisplayMode.VIRTUAL) else c.resolvePlayMode(g) }

    val ui: StateFlow<LibraryUi> =
        combine(combine(host, games, focusedGame, ::Triple), mode, c.prefs.streamSettings, options) { (h, g, f), m, s, o ->
            LibraryUi(h, g, f, m, s, loading = false, options = o)
        }.stateIn(this, LibraryUi())

    fun focus(game: Game) { focusedId.value = game.id }
}

data class DetailsUi(
    val game: Game? = null,
    val details: GameDetails? = null,
    val mode: DisplayMode = DisplayMode.VIRTUAL,
    val remembered: Boolean = false,
    val settings: StreamSettings = StreamSettings(),
    val options: LibraryOptions = LibraryOptions(),
    /** Data saver is on and the network is metered: screenshots wait until asked for. */
    val saveData: Boolean = false,
    val refreshing: Boolean = false,
    /** Bumped after a refresh so artwork is requested again. */
    val artVersion: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DetailsViewModel(private val c: AppContainer, val hostId: String, val gameId: String) : ViewModel() {
    private val details = MutableStateFlow<GameDetails?>(null)
    private val refreshing = MutableStateFlow(false)
    private val artVersion = MutableStateFlow(0)
    private val options = c.prefs.libraryOptions
    private val game = combine(c.library.observeGames(hostId), options) { l, o ->
        l.firstOrNull { it.id == gameId }?.forNetwork(o.dataSaver && c.isMetered())
    }

    val ui: StateFlow<DetailsUi> = combine(
        combine(game, details, ::Pair),
        game.flatMapLatest { g -> if (g == null) flowOf(DisplayMode.VIRTUAL) else c.resolvePlayMode(g) },
        combine(c.prefs.modeFor(hostId, gameId), c.prefs.streamSettings, c.prefs.videoModeFor(hostId, gameId)) { m, st, v ->
            // A size saved for this game from the stream menu wins over Settings.
            m to (v?.let { st.copy(resolution = it.resolution, fps = it.fps) } ?: st)
        },
        options,
        combine(refreshing, artVersion, ::Pair),
    ) { (g, d), m, (remembered, s), o, (r, v) ->
        DetailsUi(g, d, m, remembered != null, s, o, o.dataSaver && c.isMetered(), r, v)
    }.stateIn(this, DetailsUi())

    init {
        viewModelScope.launch { details.value = runCatching { c.library.details(hostId, gameId) }.getOrNull() }
    }

    fun remember(mode: DisplayMode) = viewModelScope.launch { c.prefs.setMode(hostId, gameId, mode) }

    /** Re-fetches this game's details and artwork from the host. */
    fun refresh(onArtCleared: () -> Unit = {}) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            runCatching { c.artwork.refreshGame(hostId, gameId) }
            runCatching { c.library.details(hostId, gameId) }.getOrNull()?.let { details.value = it }
            onArtCleared()
            artVersion.value++
            refreshing.value = false
        }
    }
}

/** Result of the last live resolution change, for a short message on the stream screen. */
data class SwitchNote(val text: String, val ok: Boolean)

class StreamViewModel(private val c: AppContainer, val hostId: String, val gameId: String, val mode: DisplayMode) : ViewModel() {
    private val _state = MutableStateFlow<StreamState>(StreamState.Starting)
    val state = _state.asStateFlow()
    private val _game = MutableStateFlow<Game?>(null)
    val game = _game.asStateFlow()
    private val loaded = viewModelScope.async {
        runCatching { c.library.observeGames(hostId).first() }.getOrDefault(emptyList()).firstOrNull { it.id == gameId }
            .also { _game.value = it }
    }
    private var session: Job? = null

    /** The bitrate the stream was started with, then whatever the menu last applied (kbps). */
    private val _bitrateKbps = MutableStateFlow(0)
    val bitrateKbps = _bitrateKbps.asStateFlow()
    /** Result of the last bitrate change: null while idle, else a short message. */
    private val _bitrateNote = MutableStateFlow<String?>(null)
    val bitrateNote = _bitrateNote.asStateFlow()

    val backgrounded: StateFlow<Boolean> = c.stream.backgrounded.stateIn(this, false)

    /** Live resolution changes; created with the stream's starting mode. */
    private var switcher: LiveResolutionSwitcher? = null
    private val _switch = MutableStateFlow<SwitchState?>(null)
    /** Null until the stream has started; see [LiveResolutionSwitcher]. */
    val switchState: StateFlow<SwitchState?> = _switch.asStateFlow()
    private val _switchNote = MutableStateFlow<SwitchNote?>(null)
    val switchNote: StateFlow<SwitchNote?> = _switchNote.asStateFlow()
    /** The size and frame rate saved for this game ("Use for this game from now on"), if any. */
    val gameVideoMode: StateFlow<VideoMode?> = c.prefs.videoModeFor(hostId, gameId).stateIn(this, null)
    private val _startMode = MutableStateFlow<VideoMode?>(null)
    /** The mode the stream started at; the stream's orientation follows it, not live switches. */
    val startMode: StateFlow<VideoMode?> = _startMode.asStateFlow()
    /** Settings' default resolution and frame rate, for the picker's "current" hints. */
    val settings: StateFlow<StreamSettings> = c.prefs.streamSettings.stateIn(this, StreamSettings())

    /**
     * Starts the stream into [target] the first time; later calls (a new surface after the app
     * comes back from the background) move the running stream onto it.
     */
    fun attach(target: StreamTarget) {
        if (session != null) {
            c.stream.reattach(target)
            return
        }
        session = viewModelScope.launch {
            val g = loaded.await()
            if (g == null) {
                _state.value = StreamState.Failed("This game is no longer on the host.")
                return@launch
            }
            c.prefs.setMode(hostId, gameId, mode)
            val base = c.prefs.streamSettings.first()
            val initial = Portrait.startMode(
                startingMode(base, c.prefs.videoModeFor(hostId, gameId).first(), c.deviceResolution()),
                base.portraitStreaming, c.deviceOrientation(),
            )
            _startMode.value = initial
            val settings = base.copy(resolution = initial.resolution, fps = initial.fps)
            _bitrateKbps.value = settings.bitrateKbps
            val sw = LiveResolutionSwitcher(initial, reconnect = { c.stream.switchMode(it) }, clock = SystemClock::elapsedRealtime)
            switcher = sw
            viewModelScope.launch { sw.state.collect { _switch.value = it } }
            var announced = false
            c.stream.start(g, mode, settings, target).collect {
                _state.value = it
                if (it is StreamState.Live && !announced) {
                    announced = true
                    c.onStreamLive(g.name)
                }
                if (it is StreamState.Ended || it is StreamState.Failed) c.onStreamEnded()
            }
        }
    }

    /**
     * Reconnects at [target] in place (same game, same Virtual / Mirror mode). [rememberForGame]
     * saves it for this game once it works; turning it off forgets a saved one.
     */
    fun changeResolution(target: VideoMode, rememberForGame: Boolean) {
        val sw = switcher ?: return
        if (_state.value !is StreamState.Live || sw.state.value.busy) return
        viewModelScope.launch {
            _switchNote.value = null
            val outcome = sw.switchTo(target)
            when (outcome) {
                is SwitchOutcome.Switched -> {
                    Log.i(TAG, "Live resolution: ${target.label} in ${outcome.elapsedMs} ms")
                    _switchNote.value = SwitchNote("Now streaming at ${target.label} · switched in %.1f s".format(outcome.elapsedMs / 1000f), ok = true)
                    rememberChoice(target, rememberForGame)
                }
                SwitchOutcome.Unchanged -> rememberChoice(target, rememberForGame)
                is SwitchOutcome.RolledBack -> {
                    Log.w(TAG, "Live resolution: ${target.label} failed (${outcome.reason}); back at ${outcome.restored.label} after ${outcome.elapsedMs} ms")
                    _switchNote.value = SwitchNote("Couldn't switch to ${target.label}. ${outcome.reason} Back at ${outcome.restored.label}.", ok = false)
                }
                is SwitchOutcome.Lost -> {
                    Log.w(TAG, "Live resolution: stream lost: ${outcome.reason}")
                    _switch.value = sw.state.value
                    _state.value = StreamState.Failed("Couldn't change the resolution. ${outcome.reason}")
                    session?.cancel()
                    c.onStreamEnded()
                }
                SwitchOutcome.Busy -> Unit
            }
        }
    }

    fun clearSwitchNote() { _switchNote.value = null }

    private val _rotatedFromMenu = MutableStateFlow(false)

    /**
     * The mode the Activity's orientation follows ([Portrait.orientationMode]): the start mode,
     * or the current one when following rotation or after Rotate in the menu.
     */
    val orientationMode: StateFlow<VideoMode?> = combine(_startMode, _switch, settings, _rotatedFromMenu) { start, sw, st, menu ->
        val current = when (sw) {
            is SwitchState.Streaming -> sw.mode
            is SwitchState.Switching -> sw.to
            is SwitchState.RollingBack -> sw.to
            else -> null
        }
        Portrait.orientationMode(st.portraitStreaming, start, current, menu)
    }.stateIn(this, null)

    private val _rotatingTo = MutableStateFlow<VideoMode?>(null)
    /** The mode a rotation is switching to (for the "Rotating…" pill), or null. */
    val rotatingTo: StateFlow<VideoMode?> = _rotatingTo.asStateFlow()

    private val follower = RotationFollower()
    private var followJob: kotlinx.coroutines.Job? = null

    /**
     * "Follow rotation": the screen now faces [orientation] (null while flat or unknown). After it
     * has held for the debounce, the stream is switched to the rotated size.
     */
    fun onScreenOrientation(orientation: Orientation?) {
        if (settings.value.portraitStreaming != PortraitStreaming.FOLLOW_ROTATION) {
            follower.reset()
            return
        }
        follower.observe(orientation, SystemClock.elapsedRealtime())
        scheduleFollow()
    }

    private fun scheduleFollow() {
        followJob?.cancel()
        val due = follower.dueAt() ?: return
        followJob = viewModelScope.launch {
            kotlinx.coroutines.delay((due - SystemClock.elapsedRealtime()).coerceAtLeast(0))
            // Wait for a running switch (and the stream to be live) before deciding.
            val sw = switcher ?: return@launch
            sw.state.first { !it.busy }
            _state.first { it is StreamState.Live }
            val target = follower.decide(SystemClock.elapsedRealtime(), sw.mode, sw.state.value.busy) ?: return@launch
            Log.i(TAG, "Follow rotation: ${sw.mode?.label} → ${target.label}")
            rotateTo(target)
        }
    }

    /**
     * Turns the stream 90° (portrait ↔ landscape) from the menu: a live mode change to the same
     * size with width and height swapped, same frame rate and display mode. The screen turns with it.
     */
    fun rotate() {
        val from = switcher?.mode ?: return
        _rotatedFromMenu.value = true
        rotateTo(Portrait.rotated(from))
    }

    private fun rotateTo(target: VideoMode) {
        val sw = switcher ?: return
        val from = sw.mode ?: return
        if (_state.value !is StreamState.Live || target == from) return
        viewModelScope.launch {
            _switchNote.value = null
            _rotatingTo.value = target
            val outcome = try { sw.switchTo(target) } finally { _rotatingTo.value = null }
            when (outcome) {
                is SwitchOutcome.Switched -> {
                    Log.i(TAG, "Rotate: ${target.label} in ${outcome.elapsedMs} ms")
                    val way = if (target.orientation == Orientation.PORTRAIT) "portrait" else "landscape"
                    _switchNote.value = SwitchNote("Now streaming in $way at ${target.label} · %.1f s".format(outcome.elapsedMs / 1000f), ok = true)
                }
                is SwitchOutcome.RolledBack -> {
                    Log.w(TAG, "Rotate: ${target.label} failed (${outcome.reason}); back at ${outcome.restored.label}")
                    _switchNote.value = SwitchNote("Couldn't rotate to ${target.label}. ${outcome.reason} Back at ${outcome.restored.label}.", ok = false)
                }
                is SwitchOutcome.Lost -> {
                    Log.w(TAG, "Rotate: stream lost: ${outcome.reason}")
                    _switch.value = sw.state.value
                    _state.value = StreamState.Failed("Couldn't rotate the stream. ${outcome.reason}")
                    session?.cancel()
                    c.onStreamEnded()
                }
                SwitchOutcome.Unchanged, SwitchOutcome.Busy -> Unit
            }
            // The device may have been turned again meanwhile.
            scheduleFollow()
        }
    }

    private suspend fun rememberChoice(target: VideoMode, remember: Boolean) {
        when {
            remember -> c.prefs.setVideoMode(hostId, gameId, target)
            gameVideoMode.value != null -> c.prefs.setVideoMode(hostId, gameId, null)
        }
    }

    private val _displayScale = MutableStateFlow(100)
    /** The host's desktop UI scale for this stream, in percent (100 until changed). */
    val displayScale: StateFlow<Int> = _displayScale.asStateFlow()
    private var savedScaleApplied = false

    /** Asks the host to scale its desktop UI; a note says how it went. */
    fun setDisplayScale(percent: Int) = viewModelScope.launch {
        val ok = c.stream.setDisplayScale(percent)
        if (ok) _displayScale.value = percent
        _switchNote.value = if (ok) SwitchNote("Desktop scaling $percent%", ok = true) else SwitchNote("The PC didn't change its scaling.", ok = false)
    }

    /** Once per stream: re-applies the scaling saved for this game. */
    fun applySavedDisplayScale(percent: Int) {
        if (savedScaleApplied) return
        savedScaleApplied = true
        if (percent != _displayScale.value) setDisplayScale(percent)
    }

    fun setBitrate(kbps: Int) = viewModelScope.launch {
        _bitrateNote.value = "Applying…"
        val ok = c.stream.setBitrate(kbps)
        if (ok) _bitrateKbps.value = kbps
        _bitrateNote.value = if (ok) "Applied: ${kbps / 1000} Mbps" else "The PC didn't accept the change"
    }

    fun end(quitApp: Boolean = false) {
        c.stream.stop(quitApp)
        c.onStreamEnded()
    }

    override fun onCleared() {
        c.stream.stop(quitApp = false)
        c.onStreamEnded()
    }

    companion object {
        private const val TAG = "Nebula"

        /** The game's saved mode, else Settings' resolution ("this device" resolved) and frame rate. */
        fun startingMode(settings: StreamSettings, forGame: VideoMode?, device: Pair<Int, Int>): VideoMode =
            forGame ?: run {
                val r = settings.resolution
                if (r.width > 0 && r.height > 0) VideoMode(r.width, r.height, settings.fps) else VideoMode(device.first, device.second, settings.fps)
            }
    }
}

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<StreamSettings> = c.prefs.streamSettings.stateIn(this, StreamSettings())
    val options: StateFlow<LibraryOptions> = c.prefs.libraryOptions.stateIn(this, LibraryOptions())
    /** The host "Refresh from host" applies to: the one whose library was opened last. */
    val host: StateFlow<Host?> = combine(c.hosts.observeHosts(), c.prefs.lastHostId) { l, id -> l.firstOrNull { it.id == id } ?: l.firstOrNull { it.paired } }
        .stateIn(this, null)
    private val _cacheUsed = MutableStateFlow<Long?>(null)
    val cacheUsed = _cacheUsed.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    /** What's running right now ("Clearing…", "Refreshing atom…"), or null. */
    val busy = _busy.asStateFlow()

    init { measureCache() }

    fun update(transform: (StreamSettings) -> StreamSettings) = viewModelScope.launch { c.prefs.updateStreamSettings(transform) }
    fun updateOptions(transform: (LibraryOptions) -> LibraryOptions) = viewModelScope.launch {
        c.prefs.updateLibraryOptions(transform)
        measureCache()
    }

    fun measureCache() = viewModelScope.launch { _cacheUsed.value = runCatching { c.artwork.usedBytes() }.getOrNull() }

    fun clearCache() = work("Clearing the cache…") { c.artwork.clear() }

    fun refreshHost() {
        val h = host.value ?: return
        work("Refreshing ${h.name}…") { c.artwork.refreshHost(h.id) }
    }

    private fun work(label: String, block: suspend () -> Unit) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = label
            runCatching { block() }
            _busy.value = null
            measureCache()
        }
    }
}
