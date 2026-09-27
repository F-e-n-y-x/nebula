package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Changing resolution mid-stream.
 *
 * GameStream can't renegotiate the video size of a running session, so a switch is a fast
 * in-place reconnect (the Artemis / Apollo way): end the connection without quitting the game,
 * then immediately /resume the same app at the new width × height @ fps. The host (Nova) resizes
 * its virtual display on /resume; the client builds a fresh decoder for the new size on the same
 * surface. If the new mode doesn't come up, the previous one is resumed instead.
 *
 *   Streaming ──Request(to)──▶ Switching ──Connected──▶ Streaming(to)
 *                                  │
 *                                Failed
 *                                  ▼
 *                              RollingBack ──Connected──▶ Streaming(previous)
 *                                  │
 *                                Failed
 *                                  ▼
 *                                 Lost   (the stream is over; the screen shows why)
 */

/** Where a live resolution change stands. */
sealed interface SwitchState {
    /** Streaming at [mode]; a switch may start. */
    data class Streaming(val mode: VideoMode) : SwitchState

    /** Reconnecting at [to]; [from] is what to go back to if that fails. */
    data class Switching(val from: VideoMode, val to: VideoMode) : SwitchState

    /** [failed] didn't come up ([reason]); reconnecting at the previous mode [to]. */
    data class RollingBack(val failed: VideoMode, val to: VideoMode, val reason: String) : SwitchState

    /** Neither the new nor the previous mode came back. Terminal. */
    data class Lost(val reason: String) : SwitchState

    val busy: Boolean get() = this is Switching || this is RollingBack
}

sealed interface SwitchEvent {
    data class Request(val to: VideoMode) : SwitchEvent

    /** The connection being attempted is live. */
    data object Connected : SwitchEvent

    /** The connection being attempted failed or timed out. */
    data class Failed(val reason: String) : SwitchEvent
}

/** The pure transition table. Events that don't apply to a state leave it unchanged. */
object LiveResolutionMachine {
    fun reduce(state: SwitchState, event: SwitchEvent): SwitchState = when (state) {
        is SwitchState.Streaming -> when (event) {
            is SwitchEvent.Request -> if (event.to == state.mode) state else SwitchState.Switching(state.mode, event.to)
            else -> state
        }
        is SwitchState.Switching -> when (event) {
            SwitchEvent.Connected -> SwitchState.Streaming(state.to)
            is SwitchEvent.Failed -> SwitchState.RollingBack(failed = state.to, to = state.from, reason = event.reason)
            is SwitchEvent.Request -> state
        }
        is SwitchState.RollingBack -> when (event) {
            SwitchEvent.Connected -> SwitchState.Streaming(state.to)
            is SwitchEvent.Failed -> SwitchState.Lost("${state.reason} Going back to ${state.to.label} failed too: ${event.reason}")
            is SwitchEvent.Request -> state
        }
        is SwitchState.Lost -> state
    }
}

/** What a [LiveResolutionSwitcher.switchTo] call ended with. */
sealed interface SwitchOutcome {
    data class Switched(val mode: VideoMode, val elapsedMs: Long) : SwitchOutcome

    /** [attempted] failed for [reason]; the stream is back at [restored]. */
    data class RolledBack(val attempted: VideoMode, val restored: VideoMode, val reason: String, val elapsedMs: Long) : SwitchOutcome

    data class Lost(val reason: String) : SwitchOutcome

    /** Already streaming at that mode; nothing happened. */
    data object Unchanged : SwitchOutcome

    /** A switch is already running (or the stream is lost); the request was ignored. */
    data object Busy : SwitchOutcome
}

/**
 * Runs [LiveResolutionMachine] against a real connection. [reconnect] must end the current
 * connection without quitting the host app and resume it at the given mode, returning once the new
 * connection is live (or failed). Each attempt gets [attemptTimeoutMs]; [clock] is monotonic ms.
 */
class LiveResolutionSwitcher(
    initial: VideoMode,
    private val reconnect: suspend (VideoMode) -> Result<Unit>,
    private val clock: () -> Long,
    private val attemptTimeoutMs: Long = DEFAULT_ATTEMPT_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<SwitchState>(SwitchState.Streaming(initial))
    val state: StateFlow<SwitchState> = _state.asStateFlow()

    /** The mode currently streaming, or null while switching / after the stream was lost. */
    val mode: VideoMode? get() = (state.value as? SwitchState.Streaming)?.mode

    suspend fun switchTo(target: VideoMode): SwitchOutcome {
        val start = clock()
        val before = _state.value as? SwitchState.Streaming ?: return SwitchOutcome.Busy
        if (before.mode == target) return SwitchOutcome.Unchanged
        val switching = LiveResolutionMachine.reduce(before, SwitchEvent.Request(target))
        // Two quick taps: only the first one wins.
        if (!_state.compareAndSet(before, switching)) return SwitchOutcome.Busy

        val first = attempt(target)
        _state.value = LiveResolutionMachine.reduce(switching, first)
        if (first == SwitchEvent.Connected) return SwitchOutcome.Switched(target, clock() - start)

        val rollingBack = _state.value as SwitchState.RollingBack
        val second = attempt(rollingBack.to)
        _state.value = LiveResolutionMachine.reduce(rollingBack, second)
        return when (val end = _state.value) {
            is SwitchState.Streaming -> SwitchOutcome.RolledBack(target, end.mode, rollingBack.reason, clock() - start)
            is SwitchState.Lost -> SwitchOutcome.Lost(end.reason)
            else -> error("Unexpected state after rollback: $end")
        }
    }

    private suspend fun attempt(mode: VideoMode): SwitchEvent {
        val result = try {
            withTimeoutOrNull(attemptTimeoutMs) { reconnect(mode) }
                ?: Result.failure(IllegalStateException("The PC didn't come back within ${attemptTimeoutMs / 1000} s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        return result.fold(
            onSuccess = { SwitchEvent.Connected },
            onFailure = { SwitchEvent.Failed(it.message?.takeIf(String::isNotBlank) ?: "The PC didn't accept ${mode.label}.") },
        )
    }

    companion object {
        const val DEFAULT_ATTEMPT_TIMEOUT_MS = 10_000L
    }
}
