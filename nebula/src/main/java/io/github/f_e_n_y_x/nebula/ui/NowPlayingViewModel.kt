package io.github.f_e_n_y_x.nebula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.NowPlayingTracker
import io.github.f_e_n_y_x.nebula.domain.NowPlayingView
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
import kotlinx.coroutines.flow.channelFlow
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
 * (every 1.5 s for a little while after a quit), and again on every [refreshNow] (screen entry,
 * app resume, the stale card's Refresh). Collection stops in the background. The state lives in
 * [io.github.f_e_n_y_x.nebula.nowplaying.NowPlayingCenter], so the home card, the details page
 * and the notification agree. [games] is the screen's own game list, so no second app-list poll
 * starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(private val c: AppContainer, val hostId: String, games: Flow<List<Game>>) : ViewModel() {
    private val center get() = c.nowPlaying
    private val refresh = MutableStateFlow(0)
    @Volatile private var lastAskedMs = 0L

    /** Each answer with the time it was asked. */
    private data class Answer(val result: Result<RunningGame?>, val atMs: Long)

    private val answers: Flow<Answer> = refresh.flatMapLatest {
        flow {
            while (true) {
                val at = System.currentTimeMillis()
                lastAskedMs = at
                emit(Answer(c.hosts.running(hostId), at))
                delay(NowPlayingTracker.nextPollMs(center.stateNow(hostId), System.currentTimeMillis(), POLL_MS))
            }
        }
    }

    private val host = c.hosts.observeHosts().map { l -> l.firstOrNull { it.id == hostId } }.distinctUntilChanged()

    /** What the card shows: running, "last seen running" when the PC doesn't answer, or nothing. */
    val view: StateFlow<NowPlayingView> = channelFlow {
        launch {
            // A changed game list re-resolves the same answer; its time keeps it from outliving newer ones.
            combine(host, answers, games.distinctUntilChanged()) { h, a, g -> Triple(h, a, g) }.collect { (h, a, g) ->
                if (a.result.isFailure) center.onAnswer(hostId, ok = false, np = null, atMs = a.atMs)
                else center.onAnswer(hostId, ok = true, np = center.resolve(h, a.result.getOrNull(), g), atMs = a.atMs)
            }
        }
        launch {
            // Grace periods and "last seen" ages depend on the time too.
            while (true) {
                send(NowPlayingTracker.view(center.stateNow(hostId), System.currentTimeMillis()))
                delay(TICK_MS)
            }
        }
        center.state(hostId).collect { send(NowPlayingTracker.view(it, System.currentTimeMillis())) }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MS), NowPlayingView.Hidden)

    private val _quitting = MutableStateFlow(false)
    val quitting: StateFlow<Boolean> = _quitting.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off results for a toast. */
    val messages = _messages.receiveAsFlow()

    /** Ask the PC again now (skipped when it was just asked). */
    fun refreshNow(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - lastAskedMs < DEDUPE_MS) return
        refresh.value++
    }

    /**
     * Quit (after the card's confirmation): hidden at once, `/cancel`, then the PC is asked again
     * soon (and every 1.5 s for a few seconds). A refusal brings the card back with the reason.
     */
    fun quit() {
        val np = (view.value as? NowPlayingView.Live)?.np ?: return
        if (_quitting.value) return
        viewModelScope.launch {
            _quitting.value = true
            center.onQuitStarted(np)
            val outcome = StopGame.run(c.hosts, np.hostId, np.hostName, np.gameName)
            val stopped = outcome.kind == StopKind.STOPPED || outcome.kind == StopKind.ALREADY_CLOSED
            center.onQuitFinished(np, stopped)
            if (outcome.kind != StopKind.STOPPED) _messages.send(outcome.message)
            _quitting.value = false
            delay(REFRESH_AFTER_QUIT_MS)
            refreshNow(force = true)
        }
    }

    private companion object {
        const val POLL_MS = 30_000L
        /** Short, so leaving the screen or the app stops polling almost at once. */
        const val STOP_AFTER_MS = 1_000L
        const val TICK_MS = 1_000L
        const val DEDUPE_MS = 1_000L
        const val REFRESH_AFTER_QUIT_MS = 1_500L
    }
}
