package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WakeHostTest {
    private class Fake(val upAfterMs: Long?, val sendFails: Boolean = false) {
        var sends = 0
        val sendTimes = mutableListOf<Long>()
    }

    private fun TestScope.wake(f: Fake, timeoutMs: Long = 60_000) = WakeHost(
        send = {
            f.sends++
            f.sendTimes += currentTime
            if (f.sendFails) Result.failure(IllegalStateException("No MAC address")) else Result.success(Unit)
        },
        poll = { if (f.upAfterMs != null && currentTime >= f.upAfterMs) HostStatus.ONLINE else HostStatus.OFFLINE },
        clock = { currentTime },
        timeoutMs = timeoutMs,
    )

    @Test fun `wakes, waits and reports online`() = runTest {
        val f = Fake(upAfterMs = 9_000)
        val states = wake(f).run().toList()
        assertEquals(WakeState.Sending, states.first())
        assertEquals(WakeState.Online, states.last())
        val waits = states.filterIsInstance<WakeState.Waiting>()
        assertEquals(listOf(0, 2, 4, 6, 8), waits.map { it.elapsedS })
        assertTrue(waits.all { it.timeoutS == 60 })
        // Re-sent once 6 s in, in case the NIC missed the first burst.
        assertEquals(listOf(0L, 6_000L), f.sendTimes)
    }

    @Test fun `times out when the host never answers`() = runTest {
        val f = Fake(upAfterMs = null)
        val states = wake(f, timeoutMs = 20_000).run().toList()
        assertEquals(WakeState.TimedOut(20), states.last())
        assertEquals(20_000L, currentTime)
        assertEquals(4, f.sends) // 0, 6, 12, 18 s
        assertTrue(states.none { it == WakeState.Online })
    }

    @Test fun `a host that is already up is seen at the first poll`() = runTest {
        val states = wake(Fake(upAfterMs = 0)).run().toList()
        assertEquals(listOf(WakeState.Sending, WakeState.Waiting(0, 60), WakeState.Online), states)
    }

    @Test fun `no MAC fails at once without polling`() = runTest {
        val f = Fake(upAfterMs = 0, sendFails = true)
        val states = wake(f).run().toList()
        assertEquals(listOf(WakeState.Sending, WakeState.Failed("No MAC address")), states)
        assertEquals(0L, currentTime)
    }

    @Test fun `a streaming host counts as up`() = runTest {
        val states = WakeHost(send = { Result.success(Unit) }, poll = { HostStatus.STREAMING }, clock = { currentTime }).run().toList()
        assertEquals(WakeState.Online, states.last())
        assertTrue(states.last().finished)
    }
}
