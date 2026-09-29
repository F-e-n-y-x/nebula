package io.github.f_e_n_y_x.nebula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.NowPlaying
import io.github.f_e_n_y_x.nebula.domain.StopGame
import io.github.f_e_n_y_x.nebula.domain.StopKind
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.RunningGame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The "Now playing" card for one host: asks it what runs every 30 s while a screen shows the card
 * (collection stops in the background, and a fresh answer arrives as soon as the app returns).
 * [games] is the screen's own game list, so no second app-list poll starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(private val c: AppContainer, val hostId: String, games: Flow<List<Game>>) : ViewModel() {
    private val refresh = MutableStateFlow(0)

    private val running: Flow<Result<RunningGame?>> = refresh.flatMapLatest {
        flow {
            while (true) {
                emit(c.hosts.running(hostId))
                delay(POLL_MS)
            }
        }
    }

    private val host = c.hosts.observeHosts().map { l -> l.firstOrNull { it.id == hostId } }.distinctUntilChanged()

    /** A session just quit from the card: hidden at once, even if the PC is slow to say so. */
    private val quitSession = MutableStateFlow<String?>(null)

    /** Null while nothing runs, or the PC can't be asked. */
    val nowPlaying: StateFlow<NowPlaying?> = combine(host, running, games.distinctUntilChanged(), quitSession) { h, r, g, quit ->
        if (r.isFailure) null else c.nowPlaying.resolve(h, r.getOrNull(), g)?.takeIf { it.sessionKey != quit }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), null)

    private val _quitting = MutableStateFlow(false)
    val quitting: StateFlow<Boolean> = _quitting.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off results for a toast. */
    val messages = _messages.receiveAsFlow()

    fun refreshNow() { refresh.value++ }

    /** Quit (after the card's confirmation): `/cancel`, then ask the PC again. */
    fun quit() {
        val np = nowPlaying.value ?: return
        if (_quitting.value) return
        viewModelScope.launch {
            _quitting.value = true
            val outcome = StopGame.run(c.hosts, np.hostId, np.hostName, np.gameName)
            if (outcome.kind == StopKind.STOPPED || outcome.kind == StopKind.ALREADY_CLOSED) quitSession.value = np.sessionKey
            if (outcome.kind != StopKind.STOPPED) _messages.send(outcome.message)
            _quitting.value = false
            refreshNow()
        }
    }

    private companion object {
        const val POLL_MS = 30_000L
        /** Short, so leaving the screen or the app stops polling almost at once. */
        const val STOP_AFTER_MS = 1_000L
    }
}
