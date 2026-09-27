package io.github.f_e_n_y_x.nebula.ui

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.LiveResolutionSwitcher
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

    init { start() }

    fun start() {
        job?.cancel()
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
            val initial = startingMode(base, c.prefs.videoModeFor(hostId, gameId).first(), c.deviceResolution())
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

    private suspend fun rememberChoice(target: VideoMode, remember: Boolean) {
        when {
            remember -> c.prefs.setVideoMode(hostId, gameId, target)
            gameVideoMode.value != null -> c.prefs.setVideoMode(hostId, gameId, null)
        }
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
