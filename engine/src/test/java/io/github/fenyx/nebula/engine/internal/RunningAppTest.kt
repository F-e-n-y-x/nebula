package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.fenyx.nebula.engine.HostRefusedException
import io.github.fenyx.nebula.engine.NovaDisplayMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.fenyx.nebula.engine.HostApp
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RunningAppTest {
    private val backend = FakeBackend()
    private val store = FakeStore()

    private fun TestScope.repo() = HostRepository(backend, store, null, backgroundScope, StandardTestDispatcher(testScheduler))

    private suspend fun HostRepository.addPaired(): String {
        val id = addManually("192.168.1.5").id
        backend.pairOutcome = PairOutcome(PairState.PAIRED, "nebula-phone", FakeCert())
        pair(id, null).toList()
        return id
    }

    @Test fun `parses the running document`() {
        val r = NovaApi.parseRunning(
            """{"running":true,"app":{"id":881,"name":"GTA V","index":3},"since":1790000000,"display":"virtual","connected_clients":0}""",
        )!!
        assertEquals("881", r.appId)
        assertNull(r.novaId)
        assertEquals("GTA V", r.name)
        assertEquals(1_790_000_000L, r.sinceEpochS)
        assertEquals(NovaDisplayMode.VIRTUAL, r.display)
        assertEquals(0, r.connectedClients)
        assertTrue(r.fromNova)
    }

    @Test fun `a nova id, a missing start time and nothing running`() {
        val r = NovaApi.parseRunning("""{"running":true,"app":{"id":"steam-271590","name":"GTA V"},"display":"mirror"}""")!!
        assertNull(r.appId)
        assertEquals("steam-271590", r.novaId)
        assertNull(r.sinceEpochS)
        assertNull(r.connectedClients)
        assertEquals(NovaDisplayMode.MIRROR, r.display)
        assertNull(NovaApi.parseRunning("""{"running":false,"app":null}"""))
        assertNull(NovaApi.parseRunning("""{"running":false}"""))
        assertNull(NovaApi.parseRunning(""))
    }

    @Test fun `hosts with the running feature answer from nova and map nova ids`() = runTest {
        backend.apps = listOf(NvApp("GTA V", 881, false))
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"1","features":["apps","running"]}""",
            NovaApi.APPS to """[{"id":"steam-271590","appid":881,"name":"GTA V"}]""",
            NovaApi.RUNNING to """{"running":true,"app":{"id":"steam-271590","name":"GTA V"},"since":1790000000,"display":"virtual"}""",
        )
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)
        repo.loadApps(id)
        val r = repo.running(id)!!
        assertEquals("881", r.appId)
        assertEquals(1_790_000_000L, r.sinceEpochS)
    }

    @Test fun `other hosts fall back to currentgame`() = runTest {
        backend.runningGameId = 881
        // Nova without the feature: /nova/v1/running is never asked.
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"1","features":["apps"]}""",
            NovaApi.RUNNING to """{"running":true,"app":{"id":1,"name":"wrong"}}""",
        )
        val repo = repo()
        val id = repo.addPaired()
        val r = repo.running(id)!!
        assertEquals("881", r.appId)
        assertNull(r.sinceEpochS)
        assertFalse(r.fromNova)
        backend.runningGameId = 0
        assertNull(repo.running(id))
    }

    @Test(expected = IOException::class)
    fun `an unreachable host throws`() = runTest {
        val repo = repo()
        val id = repo.addPaired()
        backend.online = false
        repo.running(id)
    }

    @Test fun `quit closes the game and reports refusals`() = runTest {
        backend.runningGameId = 881
        val repo = repo()
        val id = repo.addPaired()
        repo.quitApp(id)
        assertEquals(1, backend.quits)
        assertNull(repo.running(id))

        backend.quitResult = false
        val refused = runCatching { repo.quitApp(id) }.exceptionOrNull()
        assertTrue(refused is HostRefusedException)
    }

    @Test fun `tracked is optional and defaults to true`() {
        assertTrue(NovaApi.parseRunning("""{"running":true,"app":{"id":881,"name":"GTA V"}}""")!!.tracked)
        assertTrue(NovaApi.parseRunning("""{"running":true,"app":{"id":881},"tracked":null}""")!!.tracked)
        assertFalse(NovaApi.parseRunning("""{"running":true,"app":{"id":881},"tracked":false}""")!!.tracked)
    }

    private fun app(id: String, running: Boolean) = HostApp(id = id, name = id, running = running, novaId = null, availableArt = emptySet(), hdrSupported = false, lastPlayed = null, playtimeSeconds = null, modeDefault = null)

    @Test fun `a newer mark corrects the app list's running flags`() {
        val list = HostRepository.PolledApps(listOf(app("1", true), app("2", false)), ok = true, atNs = 100)
        // Older word: the list wins.
        assertEquals(listOf(true, false), HostRepository.withRunning(list, HostRepository.RunningMark(null, HostRepository.RunningMark.State.NONE, 50)).map { it.running })
        // Newer "nothing runs" (a quit, a fresh /nova/v1/running) clears it at once.
        assertEquals(listOf(false, false), HostRepository.withRunning(list, HostRepository.RunningMark(null, HostRepository.RunningMark.State.NONE, 150)).map { it.running })
        // Newer "app 2 runs" moves it.
        assertEquals(listOf(false, true), HostRepository.withRunning(list, HostRepository.RunningMark("2", HostRepository.RunningMark.State.RUNNING, 150)).map { it.running })
        // Newer "unreachable": nothing is shown running.
        assertEquals(listOf(false, false), HostRepository.withRunning(list, HostRepository.RunningMark(null, HostRepository.RunningMark.State.UNKNOWN, 150)).map { it.running })
        // A failed fetch keeps the list but not its running flag.
        assertEquals(listOf(false, false), HostRepository.withRunning(list.copy(ok = false), null).map { it.running })
    }

    @Test fun `quit and a fresh nothing-runs clear the open app list at once`() = runTest {
        backend.apps = listOf(NvApp("GTA V", 881, false))
        backend.runningGameId = 881
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"1","features":["apps","running"]}""",
            NovaApi.APPS to """[{"id":"steam-271590","appid":881,"name":"GTA V"}]""",
            NovaApi.RUNNING to """{"running":true,"app":{"id":881,"name":"GTA V"},"since":1790000000}""",
        )
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { repo.apps(id).collect { l -> seen += l.single().running } }
        testScheduler.runCurrent()
        assertEquals(listOf(true), seen)

        // The game exits by itself: the next /nova/v1/running answer clears the list without waiting for its poll.
        backend.novaJson = backend.novaJson + (NovaApi.RUNNING to """{"running":false,"app":null}""")
        assertNull(repo.running(id))
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), seen)
        assertNull(repo.hosts.value.single().runningAppId)

        // Started again, then quit from Nebula.
        backend.novaJson = backend.novaJson + (NovaApi.RUNNING to """{"running":true,"app":{"id":881,"name":"GTA V"}}""")
        repo.running(id)
        testScheduler.runCurrent()
        repo.quitApp(id)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false, true, false), seen)
    }

    @Test fun `an unreachable host shows nothing running in its app list`() = runTest {
        backend.apps = listOf(NvApp("GTA V", 881, false))
        backend.runningGameId = 881
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)
        val seen = mutableListOf<Boolean>()
        backgroundScope.launch { repo.apps(id).collect { l -> seen += l.single().running } }
        testScheduler.runCurrent()
        assertEquals(listOf(true), seen)
        // The PC stops answering: the list stays (cached) but no longer says the game runs.
        backend.online = false
        repo.refresh(id)
        testScheduler.runCurrent()
        assertEquals(listOf(true, false), seen)
    }
}
