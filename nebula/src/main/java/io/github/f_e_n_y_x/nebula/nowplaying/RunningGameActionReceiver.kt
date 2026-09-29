package io.github.f_e_n_y_x.nebula.nowplaying

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.domain.StopGame
import io.github.f_e_n_y_x.nebula.domain.StopKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Stop, Don't notify and swipe-away from the "game running" notification. Not exported: only
 * Nebula's own immutable PendingIntents (sent by the system on our behalf) reach it.
 */
class RunningGameActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val np = RunningGameNotifications.sessionOf(intent) ?: return
        val center = context.container.nowPlaying
        when (intent.action) {
            RunningGameNotifications.ACTION_MUTE -> {
                center.suppression.suppress(np.hostId, np.sessionKey)
                center.notifications.cancel(np.hostId)
            }
            RunningGameNotifications.ACTION_DISMISSED -> center.notifications.markDismissed(np.hostId, np.sessionKey)
            RunningGameNotifications.ACTION_STOP -> {
                center.notifications.showStopping(np)
                val pending = goAsync()
                scope.launch {
                    try {
                        val hosts = context.container.hosts
                        // A cold process loads the saved hosts first. The request itself runs apart so
                        // a PC that never answers can't hold the receiver past its time limit.
                        val work = scope.async {
                            withTimeoutOrNull(HOSTS_TIMEOUT_MS) { hosts.observeHosts().first { l -> l.any { it.id == np.hostId } } }
                            StopGame.run(hosts, np.hostId, np.hostName, np.gameName)
                        }
                        val outcome = withTimeoutOrNull(STOP_TIMEOUT_MS) { work.await() }
                        val now = System.currentTimeMillis() / 1000
                        when (outcome?.kind) {
                            StopKind.STOPPED, StopKind.ALREADY_CLOSED -> {
                                center.since.clear(np.hostId)
                                center.suppression.onRunning(np.hostId, null)
                                center.notifications.showStopped(np, outcome.message)
                            }
                            // Unreachable, refused or timed out: say so and keep Resume / Stop for a retry.
                            else -> center.notifications.showRunning(
                                np, alert = false, nowEpochS = now,
                                error = outcome?.message ?: "${np.hostName} didn't answer in time. ${np.gameName} may still be running.",
                            )
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val HOSTS_TIMEOUT_MS = 3_000L
        /** goAsync allows about 10 s before the system may kill us. */
        const val STOP_TIMEOUT_MS = 9_000L
    }
}
