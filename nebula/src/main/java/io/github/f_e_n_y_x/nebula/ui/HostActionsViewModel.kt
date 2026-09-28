package io.github.f_e_n_y_x.nebula.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.HostGating
import io.github.f_e_n_y_x.nebula.domain.WakeHost
import io.github.f_e_n_y_x.nebula.domain.WakeState
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Host-linked actions for one host (and optionally one game): wake-then-play, Sleep PC and host
 * commands. Used by the hosts list, library, details and stream screens; each keys its own instance.
 */
class HostActionsViewModel(private val c: AppContainer, val hostId: String, private val gameId: String?) : ViewModel() {
    val host: StateFlow<Host?> = c.hosts.observeHosts().map { l -> l.firstOrNull { it.id == hostId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _wake = MutableStateFlow<WakeState?>(null)
    /** Null when no wake is in progress or shown. */
    val wake = _wake.asStateFlow()

    private val _commands = MutableStateFlow<HostCommands?>(null)
    /** Null until [loadCommands] finished. */
    val commands = _commands.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    /** Id of the command running now, [SLEEP] while sleeping, or null. */
    val busy = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off results for a toast. */
    val messages = _messages.receiveAsFlow()

    private var wakeJob: Job? = null
    private var pendingPlay: (() -> Unit)? = null

    /**
     * Runs [onReady] now if the host answers, otherwise wakes it first (showing progress through
     * [wake]) and runs it once the host is up.
     */
    fun playWhenAwake(onReady: () -> Unit) {
        if (!HostGating.needsWake(host.value)) {
            onReady()
            return
        }
        pendingPlay = onReady
        startWake()
    }

    /** Wake without launching anything (hosts list). */
    fun wakeOnly() {
        pendingPlay = null
        startWake()
    }

    private fun startWake() {
        if (wakeJob?.isActive == true) return
        wakeJob = viewModelScope.launch {
            WakeHost(send = { c.hosts.wake(hostId) }, poll = { c.hosts.refresh(hostId)?.status }).run().collect { st ->
                _wake.value = st
                if (st is WakeState.Online) {
                    val go = pendingPlay
                    pendingPlay = null
                    _wake.value = null
                    go?.invoke()
                }
            }
        }
    }

    fun retryWake() {
        wakeJob?.cancel()
        _wake.value = null
        startWake()
    }

    fun cancelWake() {
        wakeJob?.cancel()
        wakeJob = null
        pendingPlay = null
        _wake.value = null
    }

    fun loadCommands(force: Boolean = false) {
        if (_commands.value != null && !force) return
        viewModelScope.launch { _commands.value = runCatching { c.hosts.commands(hostId, gameId) }.getOrDefault(HostCommands.None) }
    }

    fun run(command: HostCommand) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = command.id
            c.hosts.runCommand(hostId, command.id)
                .onSuccess { _messages.send("Started “${command.name}” on ${host.value?.name ?: "your PC"}") }
                .onFailure { _messages.send(it.message ?: "The command didn't run.") }
            _busy.value = null
        }
    }

    /** Puts the host to sleep; [onSlept] runs after the host accepted (the stream screen leaves). */
    fun sleep(onSlept: () -> Unit = {}) {
        if (_busy.value != null) return
        viewModelScope.launch {
            _busy.value = SLEEP
            c.hosts.sleep(hostId)
                .onSuccess {
                    _messages.send("${host.value?.name ?: "Your PC"} is going to sleep")
                    onSlept()
                }
                .onFailure { _messages.send(it.message ?: "The PC didn't go to sleep.") }
            _busy.value = null
        }
    }

    companion object {
        const val SLEEP = "\u0000sleep"
    }
}
