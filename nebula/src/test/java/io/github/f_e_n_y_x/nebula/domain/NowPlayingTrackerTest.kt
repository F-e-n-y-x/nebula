package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.RunningGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTrackerTest {
    private val gta = NowPlaying("h", "atom", "gta5", "GTA V", sinceEpochS = 1_000, exact = true)
    private val t = NowPlayingTracker

    private fun running(at: Long) = t.answer(NowPlayingState(), ok = true, np = gta, atMs = at)

    @Test fun `a fresh answer shows it, a fresh nothing-runs drops it`() {
        var s = running(10_000)
        assertEquals(NowPlayingView.Live(gta), t.view(s, 10_000))
        s = t.answer(s, ok = true, np = null, atMs = 20_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 20_000))
    }

    @Test fun `an older answer never brings back a game a newer one cleared`() {
        var s = t.answer(running(10_000), ok = true, np = null, atMs = 20_000)
        // The same (old) "running" answer re-resolved when the game list changed.
        s = t.answer(s, ok = true, np = gta, atMs = 10_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 25_000))
    }

    @Test fun `quit hides it at once and restores it when refused`() {
        var s = t.quitStarted(running(10_000), gta.gameKey)
        assertEquals(NowPlayingView.Hidden, t.view(s, 10_100))
        s = t.quitFinished(s, gta.gameKey, stopped = false, nowMs = 11_000)
        assertEquals(NowPlayingView.Live(gta), t.view(s, 11_000))
    }

    @Test fun `after a quit, lagging answers stay hidden, then the PC's word wins`() {
        var s = t.quitStarted(running(10_000), gta.gameKey)
        // An answer asked before the quit came back arrives late: ignored.
        s = t.answer(s, ok = true, np = gta, atMs = 10_500)
        s = t.quitFinished(s, gta.gameKey, stopped = true, nowMs = 12_000)
        s = t.answer(s, ok = true, np = gta, atMs = 11_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 12_100))
        // Asked 1.5 s later and the PC still says so (a game slow to close): hidden during the grace...
        s = t.answer(s, ok = true, np = gta.copy(sinceEpochS = 1_234, exact = false), atMs = 13_500)
        assertEquals(NowPlayingView.Hidden, t.view(s, 13_500))
        // ...and shown once the grace is over, because it really still runs.
        assertTrue(t.view(s, 12_000 + NowPlayingTracker.QUIT_GRACE_MS) is NowPlayingView.Live)
        s = t.answer(s, ok = true, np = null, atMs = 16_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 16_000))
    }

    @Test fun `quit polls fast for a while, then every 30 s`() {
        val s = t.quitFinished(running(10_000), gta.gameKey, stopped = true, nowMs = 12_000)
        assertEquals(NowPlayingTracker.FAST_POLL_MS, t.nextPollMs(s, 13_000, 30_000))
        assertEquals(30_000L, t.nextPollMs(s, 12_000 + NowPlayingTracker.FAST_POLL_WINDOW_MS, 30_000))
        assertEquals(30_000L, t.nextPollMs(NowPlayingState(), 13_000, 30_000))
    }

    @Test fun `quit from the stream menu hides the game on the way back`() {
        val s = t.quitFromStream(running(10_000), nowMs = 12_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 12_000))
        assertEquals(NowPlayingView.Hidden, t.view(t.answer(s, true, gta, 11_000), 12_500))
    }

    @Test fun `an unreachable PC shows last seen, then nothing after a while`() {
        var s = running(10_000)
        s = t.answer(s, ok = false, np = null, atMs = 40_000)
        assertEquals(NowPlayingView.LastSeen(gta, 10_000), t.view(s, 40_000))
        assertEquals(NowPlayingView.Hidden, t.view(s, 10_000 + NowPlayingTracker.LAST_SEEN_MAX_MS))
        // Reachable again: the PC's answer replaces it.
        s = t.answer(s, ok = true, np = null, atMs = 70_000)
        assertEquals(NowPlayingView.Hidden, t.view(s, 70_000))
        // Unreachable with nothing seen: nothing.
        assertEquals(NowPlayingView.Hidden, t.view(t.answer(NowPlayingState(), false, null, 1), 2))
    }

    @Test fun `last seen line`() {
        assertEquals("Last seen running on atom just now", Elapsed.lastSeenLine(gta, 100, 130))
        assertEquals("Last seen running on atom 5 m ago", Elapsed.lastSeenLine(gta, 100, 100 + 5 * 60))
    }

    @Test fun `untracked apps are carried to the card`() {
        val host = io.github.f_e_n_y_x.nebula.domain.model.Host(id = "h", name = "atom", address = "", status = io.github.f_e_n_y_x.nebula.domain.model.HostStatus.ONLINE, paired = true, isNova = true)
        val np = NowPlayingResolver.resolve(host, RunningGame("gta5", "GTA V", 1_000, tracked = false), emptyList(), RunningSince(MapKeyValueStore()), 2_000)!!
        assertEquals(false, np.tracked)
    }
}
