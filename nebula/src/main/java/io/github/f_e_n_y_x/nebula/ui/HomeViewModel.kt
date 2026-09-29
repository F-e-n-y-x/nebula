package io.github.f_e_n_y_x.nebula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.ContinueItem
import io.github.f_e_n_y_x.nebula.domain.HomeStyle
import io.github.f_e_n_y_x.nebula.domain.RecentGames
import io.github.f_e_n_y_x.nebula.domain.SortLibrary
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.LibraryOptions
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the Shelf and TV homes draw. Rows are computed once here, not in composition. */
data class HomeUi(
    val host: Host? = null,
    val hosts: List<Host> = emptyList(),
    /** Library order (running, recent games, then desktops). */
    val games: List<Game> = emptyList(),
    /** Stable A–Z order for "All games". */
    val allGames: List<Game> = emptyList(),
    val continueItems: List<ContinueItem> = emptyList(),
    val recentlyPlayed: List<Game> = emptyList(),
    val focused: Game? = null,
    val focusedMode: DisplayMode = DisplayMode.VIRTUAL,
    val settings: StreamSettings = StreamSettings(),
    val options: LibraryOptions = LibraryOptions(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer, val hostId: String) : ViewModel() {
    private val focusedId = MutableStateFlow<String?>(null)
    private val options = c.prefs.libraryOptions

    private val games = combine(c.library.observeGames(hostId), options) { list, o ->
        val saveData = o.dataSaver && c.isMetered()
        SortLibrary(list).map { if (saveData) it.copy(art = it.art.copy(hero = null)) else it }
    }
    private val hosts = c.hosts.observeHosts()

    private val rows = combine(games, c.launcher.recents) { list, recents ->
        val cont = RecentGames.continueRow(recents, list, hostId, CONTINUE_LIMIT)
        Triple(list, cont, RecentGames.recentlyPlayed(list, cont.map { it.game.id }.toSet(), RECENT_LIMIT))
    }

    private val focused = combine(rows, focusedId) { (list, cont, _), id ->
        list.firstOrNull { it.id == id } ?: cont.firstOrNull()?.game ?: list.firstOrNull()
    }

    private val mode = focused.flatMapLatest { g -> if (g == null) flowOf(DisplayMode.VIRTUAL) else c.resolvePlayMode(g) }

    val ui: StateFlow<HomeUi> = combine(
        combine(hosts, rows, ::Pair),
        combine(focused, mode, ::Pair),
        c.prefs.streamSettings,
        options,
    ) { (h, r), (f, m), s, o ->
        val (list, cont, recent) = r
        HomeUi(
            host = h.firstOrNull { it.id == hostId },
            hosts = h.sortedWith(compareByDescending<Host> { it.id == hostId }.thenByDescending { it.paired }.thenBy { it.name.lowercase() }),
            games = list, allGames = RecentGames.allGames(list), continueItems = cont, recentlyPlayed = recent,
            focused = f, focusedMode = m, settings = s, options = o, loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    fun focus(game: Game) { focusedId.value = game.id }

    /** The mode "Play" uses for [game] right now (remembered choice, host default, then settings). */
    fun play(game: Game, onReady: (DisplayMode) -> Unit) {
        viewModelScope.launch { onReady(c.resolvePlayMode(game).first()) }
    }

    /** Switches the home to another paired host. */
    fun selectHost(host: Host) {
        viewModelScope.launch { c.prefs.setLastHost(host.id) }
    }

    companion object {
        const val CONTINUE_LIMIT = 6
        const val RECENT_LIMIT = 12
    }
}

/** The home style setting, for Settings → Library. */
class HomeStyleViewModel(private val c: AppContainer) : ViewModel() {
    val style: StateFlow<HomeStyle> = c.launcher.homeStyle.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStyle.AUTO)
    fun set(style: HomeStyle) = viewModelScope.launch { c.launcher.setHomeStyle(style) }
}
