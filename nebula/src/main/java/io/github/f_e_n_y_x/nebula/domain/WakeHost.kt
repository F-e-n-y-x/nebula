package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Progress of waking a sleeping PC and waiting for it to answer. */
sealed interface WakeState {
    /** Sending the first magic packets. */
    data object Sending : WakeState

    /** Packets sent; polling the host. [elapsedS] of [timeoutS] seconds have passed. */
    data class Waiting(val elapsedS: Int, val timeoutS: Int) : WakeState

    /** The host answers; launch now. */
    data object Online : WakeState

    /** The host didn't answer within the timeout. */
    data class TimedOut(val afterS: Int) : WakeState

    /** Waking isn't possible (no MAC address, no network). */
    data class Failed(val reason: String) : WakeState

    val finished: Boolean get() = this is Online || this is TimedOut || this is Failed
}

/**
 * Wakes a host with Wake-on-LAN and waits until it answers: sends the magic packets, polls the host
 * every [pollMs], re-sends every [resendMs] (a PC in S3 may miss the first burst while its NIC
 * powers up), and gives up after [timeoutMs].
 *
 * [clock] returns elapsed milliseconds (virtual time in tests); a slow poll counts against the
 * timeout.
 */
class WakeHost(
    private val send: suspend () -> Result<Unit>,
    private val poll: suspend () -> HostStatus?,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val pollMs: Long = 2_000,
    private val resendMs: Long = 6_000,
) {
    fun run(): Flow<WakeState> = flow {
        emit(WakeState.Sending)
        val first = send()
        if (first.isFailure) {
            emit(WakeState.Failed(first.exceptionOrNull()?.message ?: "Couldn't send the wake-up signal."))
            return@flow
        }
        val start = clock()
        var lastSend = start
        val timeoutS = (timeoutMs / 1000).toInt()
        while (true) {
            val elapsed = clock() - start
            if (elapsed >= timeoutMs) {
                emit(WakeState.TimedOut(timeoutS))
                return@flow
            }
            emit(WakeState.Waiting((elapsed / 1000).toInt(), timeoutS))
            delay(pollMs)
            if (poll().isUp()) {
                emit(WakeState.Online)
                return@flow
            }
            if (clock() - lastSend >= resendMs) {
                send()
                lastSend = clock()
            }
        }
    }

    private fun HostStatus?.isUp() = this == HostStatus.ONLINE || this == HostStatus.STREAMING

    companion object {
        /** A desktop resuming from suspend usually answers within 10–20 s; cold boot can take a minute. */
        const val DEFAULT_TIMEOUT_MS = 75_000L
    }
}
