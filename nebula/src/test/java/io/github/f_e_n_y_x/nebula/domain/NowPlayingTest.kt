package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.RunningGame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElapsedTest {
    @Test fun `formats minutes, hours and days`() {
        assertEquals("< 1 m", Elapsed.format(0))
        assertEquals("< 1 m", Elapsed.format(59))
        assertEquals("1 m", Elapsed.format(60))
        assertEquals("45 m", Elapsed.format(45 * 60 + 30))
        assertEquals("1 h", Elapsed.format(3_600))
        assertEquals("1 h 20 m", Elapsed.format(80 * 60))
        assertEquals("23 h 59 m", Elapsed.format(86_399))
        assertEquals("1 d", Elapsed.format(86_400))
        assertEquals("2 d 3 h", Elapsed.format(2 * 86_400 + 3 * 3_600 + 59))
        assertEquals("< 1 m", Elapsed.format(-5)) // clock skew never shows a negative time
    }

    private val np = NowPlaying("h", "atom", "gta5", "GTA V", sinceEpochS = 1_000, exact = true)

    @Test fun `card and notification text`() {
        val now = 1_000L + 80 * 60
        assertEquals("Running on atom · 1 h 20 m", Elapsed.cardLine(np, now))
        assertEquals("GTA V is running on atom — 1 h 20 m. Close it to save resources?", Elapsed.notificationText(np, now))
    }

    @Test fun `a locally tracked start is a lower bound and hidden at first`() {
        val local = np.copy(exact = false)
        assertEquals("Running on atom", Elapsed.cardLine(local, 1_030))
        assertEquals("GTA V is running on atom. Close it to save resources?", Elapsed.notificationText(local, 1_030))
        assertEquals("Running on atom · at least 1 h 20 m", Elapsed.cardLine(local, 1_000L + 80 * 60))
    }
}

class NowPlayingResolverTest {
    private val host = Host("h", "atom", "10.0.0.2", HostStatus.STREAMING, paired = true, isNova = true)
    private val gta = Game("881", "h", "GTA V", GameKind.GAME, GameArt(poster = "p.png"))
    private val games = listOf(gta, Game("7", "h", "Desktop", GameKind.DESKTOP, GameArt()))
    private val kv = MapKeyValueStore()
    private val since = RunningSince(kv)

    @Test fun `nova running response gives the exact start and display`() {
        val np = NowPlayingResolver.resolve(host, RunningGame("881", "GTA V", 5_000, DisplayMode.MIRROR, 0), games, since, 9_000)!!
        assertEquals("881", np.gameId)
        assertEquals("GTA V", np.gameName)
        assertEquals("p.png", np.art.poster)
        assertEquals(5_000L, np.sinceEpochS)
        assertTrue(np.exact)
        assertEquals(DisplayMode.MIRROR, np.display)
        assertTrue(kv.map.isEmpty()) // nothing tracked locally
    }

    @Test fun `currentgame fallback tracks the first sighting locally`() {
        val first = NowPlayingResolver.resolve(host, RunningGame("881"), games, since, 9_000)!!
        assertEquals(9_000L, first.sinceEpochS)
        assertFalse(first.exact)
        assertNull(first.display)
        // Later polls keep the first time; the session key stays the same.
        val later = NowPlayingResolver.resolve(host, RunningGame("881"), games, since, 12_000)!!
        assertEquals(9_000L, later.sinceEpochS)
        assertEquals(first.sessionKey, later.sessionKey)
        // Another game: a new start.
        assertEquals(13_000L, NowPlayingResolver.resolve(host, RunningGame("7"), games, since, 13_000)!!.sinceEpochS)
    }

    @Test fun `nothing running clears the tracked start, so a restart is a new session`() {
        val a = NowPlayingResolver.resolve(host, RunningGame("881"), games, since, 9_000)!!
        assertNull(NowPlayingResolver.resolve(host, null, games, since, 10_000))
        val b = NowPlayingResolver.resolve(host, RunningGame("881"), games, since, 11_000)!!
        assertEquals(11_000L, b.sinceEpochS)
        assertTrue(a.sessionKey != b.sessionKey)
    }

    @Test fun `matches by name when the id is unknown, keeps an unknown game by id`() {
        assertEquals("881", NowPlayingResolver.resolve(host, RunningGame(null, "gta v", 1), games, since, 2)!!.gameId)
        val stranger = NowPlayingResolver.resolve(host, RunningGame("99", null, 1), games, since, 2)!!
        assertEquals("99", stranger.gameId)
        assertEquals("A game", stranger.gameName)
        assertNull(NowPlayingResolver.resolve(host, RunningGame(null, "Unknown", 1), games, since, 2))
    }

    @Test fun `unpaired or missing hosts show no card`() {
        assertNull(NowPlayingResolver.resolve(host.copy(paired = false), RunningGame("881", sinceEpochS = 1), games, since, 2))
        assertNull(NowPlayingResolver.resolve(null, RunningGame("881", sinceEpochS = 1), games, since, 2))
    }
}

class RunningNotificationPolicyTest {
    private val np = NowPlaying("h", "atom", "881", "GTA V", sinceEpochS = 100, exact = true)

    private fun decide(
        running: NowPlaying? = np, enabled: Boolean = true, permitted: Boolean = true, visible: Boolean = false,
        streaming: Boolean = false, suppressed: Boolean = false, dismissed: Boolean = false, posted: PostedNotification? = null,
    ) = RunningNotificationPolicy.decide(enabled, permitted, visible, streaming, running, suppressed, dismissed, posted)

    @Test fun `shows a running game after the user left, alerting once`() {
        assertEquals(NotifyAction.Post(np, alert = true), decide())
        // Later refreshes update it silently.
        assertEquals(NotifyAction.Post(np, alert = false), decide(posted = PostedNotification(np.sessionKey)))
        // A different session alerts again.
        assertEquals(NotifyAction.Post(np, alert = true), decide(posted = PostedNotification("h|7|5")))
    }

    @Test fun `never while the app is visible, a stream is connected, or it's turned off`() {
        val posted = PostedNotification(np.sessionKey)
        for (d in listOf(decide(visible = true, posted = posted), decide(streaming = true, posted = posted), decide(enabled = false, posted = posted), decide(permitted = false, posted = posted))) {
            assertEquals(NotifyAction.Remove, d)
        }
        assertEquals(NotifyAction.Nothing, decide(visible = true))
    }

    @Test fun `removed once the game stops, but a Stopped note stays`() {
        assertEquals(NotifyAction.Remove, decide(running = null, posted = PostedNotification(np.sessionKey)))
        assertEquals(NotifyAction.Nothing, decide(running = null))
        assertEquals(NotifyAction.Nothing, decide(running = null, posted = PostedNotification(np.sessionKey, stopped = true)))
    }

    @Test fun `don't notify and swipe-away keep it hidden`() {
        assertEquals(NotifyAction.Nothing, decide(suppressed = true))
        assertEquals(NotifyAction.Remove, decide(suppressed = true, posted = PostedNotification(np.sessionKey)))
        assertEquals(NotifyAction.Nothing, decide(dismissed = true))
    }
}

class SessionSuppressionTest {
    private val kv = MapKeyValueStore()
    private val mute = SessionSuppression(kv)
    private val np = NowPlaying("h", "atom", "881", "GTA V", sinceEpochS = 100, exact = true)

    @Test fun `holds for the same session`() {
        mute.suppress(np)
        mute.onRunning("h", np)
        assertTrue(mute.isSuppressed(np))
        // Persisted: a new instance over the same store (the app was killed) still knows.
        assertTrue(SessionSuppression(kv).isSuppressed(np.copy(gameName = "renamed")))
    }

    @Test fun `a new session shows again`() {
        mute.suppress(np)
        val restarted = np.copy(sinceEpochS = 500)
        val other = np.copy(gameId = "7")
        assertFalse(mute.isSuppressed(restarted))
        assertFalse(mute.isSuppressed(other))
        mute.onRunning("h", restarted)
        assertFalse(mute.isSuppressed(np)) // dropped once the host moved on
    }

    @Test fun `cleared when the game stops`() {
        mute.suppress(np)
        mute.onRunning("h", null)
        assertFalse(mute.isSuppressed(np))
        assertTrue(kv.map.isEmpty())
    }

    @Test fun `other hosts are unaffected`() {
        mute.suppress(np)
        mute.onRunning("other", null)
        assertTrue(mute.isSuppressed(np))
    }
}

class StopGameTest {
    private class FakeHosts(var quit: Result<Unit>, var running: Result<RunningGame?>) : HostRepository {
        var quits = 0
        override fun observeHosts(): Flow<List<Host>> = flowOf(emptyList())
        override suspend fun discover() = Unit
        override suspend fun addManual(address: String): Result<Host> = Result.failure(UnsupportedOperationException())
        override fun pair(hostId: String): Flow<PairingState> = emptyFlow()
        override suspend fun wake(hostId: String): Result<Unit> = Result.success(Unit)
        override suspend fun quitApp(hostId: String): Result<Unit> { quits++; return quit }
        override suspend fun running(hostId: String): Result<RunningGame?> = running
    }

    @Test fun `stopped`() = runTest {
        val hosts = FakeHosts(Result.success(Unit), Result.success(null))
        val out = StopGame.run(hosts, "h", "atom", "GTA V")
        assertEquals(StopKind.STOPPED, out.kind)
        assertEquals("Stopped GTA V on atom.", out.message)
        assertEquals(1, hosts.quits)
    }

    @Test fun `host asleep or unreachable`() = runTest {
        val hosts = FakeHosts(Result.failure(QuitFailure("Couldn't reach the PC.", unreachable = true)), Result.failure(IllegalStateException()))
        val out = StopGame.run(hosts, "h", "atom", "GTA V")
        assertEquals(StopKind.UNREACHABLE, out.kind)
        assertTrue(out.message.startsWith("Couldn't reach atom"))
    }

    @Test fun `refused but already closed`() = runTest {
        val hosts = FakeHosts(Result.failure(QuitFailure("The PC refused.", unreachable = false)), Result.success(null))
        assertEquals(StopKind.ALREADY_CLOSED, StopGame.run(hosts, "h", "atom", "GTA V").kind)
    }

    @Test fun `refused and still running`() = runTest {
        val hosts = FakeHosts(
            Result.failure(QuitFailure("This device isn't allowed to close games on the PC.", unreachable = false)),
            Result.success(RunningGame("881")),
        )
        val out = StopGame.run(hosts, "h", "atom", "GTA V")
        assertEquals(StopKind.FAILED, out.kind)
        assertEquals("Couldn't stop GTA V: This device isn't allowed to close games on the PC.", out.message)
    }
}
