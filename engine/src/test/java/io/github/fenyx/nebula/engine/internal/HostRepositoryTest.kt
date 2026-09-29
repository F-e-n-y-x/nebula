package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.HostState
import io.github.fenyx.nebula.engine.PairingFailure
import io.github.fenyx.nebula.engine.PairingState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.X509Certificate

@OptIn(ExperimentalCoroutinesApi::class)
class HostRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val backend = FakeBackend()
    private val store = FakeStore()

    private fun TestScope.repo(art: ArtCache? = null) = HostRepository(
        backend, store, art, backgroundScope, StandardTestDispatcher(testScheduler),
    )

    /** A cert stand-in; the repository only stores and null-checks it. */
    private val cert: X509Certificate = FakeCert()

    private suspend fun HostRepository.addPaired(): String {
        val id = addManually("192.168.1.5").id
        backend.pairOutcome = PairOutcome(PairState.PAIRED, "nebula-phone", cert)
        pair(id, null).toList()
        return id
    }

    @Test fun `manual add polls, saves and publishes the host`() = runTest {
        val repo = repo()
        val host = repo.addManually("192.168.1.5:47989")

        assertEquals("host-1", host.id)
        assertEquals(HostState.ONLINE, host.state)
        assertFalse(host.paired)
        assertEquals(listOf("host-1"), repo.hosts.value.map { it.id })
        assertEquals("192.168.1.5", store.saved["host-1"]?.manualAddress?.address)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `manual add rejects garbage`() = runTest {
        repo().addManually("not a host")
    }

    @Test(expected = IOException::class)
    fun `manual add fails when nothing answers`() = runTest {
        backend.online = false
        repo().addManually("192.168.1.5")
    }

    @Test fun `saved hosts load as unknown and refresh flips them offline`() = runTest {
        store.saved["host-1"] = ComputerDetails().apply {
            uuid = "host-1"
            name = "Desk"
            manualAddress = ComputerDetails.AddressTuple("10.0.0.2", 47989)
            state = ComputerDetails.State.ONLINE
        }
        val repo = repo()
        repo.load()
        assertEquals(HostState.UNKNOWN, repo.hosts.value.single().state)

        backend.online = false
        assertEquals(HostState.OFFLINE, repo.refresh("host-1")?.state)
        backend.online = true
        assertEquals(HostState.ONLINE, repo.refresh("host-1")?.state)
        assertNull(repo.refresh("nope"))
    }

    @Test fun `pairing emits pin, waits, then pairs and pins the certificate`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id
        backend.pairOutcome = PairOutcome(PairState.PAIRED, "nebula-phone", cert)

        val states = repo.pair(id).toList()

        assertEquals(listOf(PairingState.GeneratingPin("1234"), PairingState.WaitingForHost, PairingState.Paired), states)
        assertEquals(listOf("1234"), backend.pins)
        assertTrue(repo.hosts.value.single().paired)
        assertTrue(store.saved[id]?.serverCert === cert)
        assertEquals("nebula-phone", store.pairName(id))
    }

    @Test fun `pairing uses a caller supplied pin`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id
        val first = repo.pair(id, "9876").first()
        assertEquals(PairingState.GeneratingPin("9876"), first)
    }

    @Test fun `pairing failures map to reasons`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id

        backend.pairOutcome = PairOutcome(PairState.PIN_WRONG, "", null)
        assertEquals(PairingState.Failed(PairingFailure.WRONG_PIN), repo.pair(id).toList().last())

        backend.pairOutcome = PairOutcome(PairState.FAILED, "", null)
        backend.runningGameId = 881
        repo.refresh(id)
        assertEquals(PairingState.Failed(PairingFailure.HOST_BUSY), repo.pair(id).toList().last())

        backend.pairError = UnknownHostException("desk")
        assertEquals(PairingFailure.UNKNOWN_HOST, (repo.pair(id).toList().last() as PairingState.Failed).reason)

        backend.pairError = IOException("reset")
        assertEquals(PairingFailure.UNREACHABLE, (repo.pair(id).toList().last() as PairingState.Failed).reason)

        assertEquals(listOf(PairingState.Failed(PairingFailure.UNKNOWN_HOST)), repo.pair("missing").toList())
        assertFalse(repo.hosts.value.single().paired)
    }

    @Test fun `already paired host skips the pin`() = runTest {
        val repo = repo()
        val id = repo.addPaired()
        assertEquals(listOf(PairingState.Paired), repo.pair(id).toList())
    }

    @Test fun `unpair drops the certificate even when the host is unreachable`() = runTest {
        val repo = repo()
        val id = repo.addPaired()
        backend.online = false

        repo.unpair(id)

        assertFalse(repo.hosts.value.single().paired)
        assertNull(store.saved[id]?.serverCert)
    }

    @Test fun `forget removes the host`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id
        repo.forget(id)
        assertTrue(repo.hosts.value.isEmpty())
        assertTrue(store.saved.isEmpty())
    }

    @Test fun `wake needs a mac address`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id
        assertTrue(repo.wake(id))
        assertEquals(1, backend.woken)
        assertFalse(repo.wake("missing"))
    }

    @Test fun `nova hosts merge metadata into the app list`() = runTest {
        backend.apps = listOf(NvApp("Dota 2", 881, false))
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"1","features":["apps"]}""",
            NovaApi.APPS to """[{"id":"steam-570","appid":881,"name":"Dota 2","has":{"hero":true}}]""",
        )
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)

        assertTrue(repo.hosts.value.single().isNova)
        val apps = repo.apps(id).first()
        assertEquals("steam-570", apps.single().novaId)
        assertEquals(setOf(ArtKind.HERO), apps.single().availableArt)
    }

    @Test fun `game profiles are read and changed through the nova profile api`() = runTest {
        backend.apps = listOf(NvApp("Cyberpunk 2077", 881, false))
        val reply = """{"profile":{"fps_cap":60,"fsr":2,"vkbasalt":false,"vkbasalt_cas":50,"mangohud":false,"bitrate_kbps":40000,"power":"performance"},"launcher":"proton","applies":true,"can_edit":true}"""
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"0.3","features":["apps","app_profiles"]}""",
            NovaApi.APPS to """[{"id":"0123456789abcdef","appid":881,"name":"Cyberpunk 2077"}]""",
            NovaApi.profile("0123456789abcdef") to reply,
        )
        backend.novaPostReplies = mapOf(NovaApi.profile("0123456789abcdef") to reply.replace("\"fps_cap\":60", "\"fps_cap\":90"))
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)
        repo.loadApps(id)

        val p = repo.appProfile(id, "881")!!
        assertEquals(60, p.fpsCap)
        assertEquals(40_000, p.bitrateKbps)
        assertEquals(io.github.fenyx.nebula.engine.HostPowerMode.PERFORMANCE, p.power)

        assertEquals(90, repo.setAppProfile(id, "881", mapOf("fps_cap" to 90)).fpsCap)
        assertEquals(NovaApi.profile("0123456789abcdef") to """{"fps_cap":90}""", backend.posted.single())
        assertNull(repo.appProfile(id, "999"))
    }

    @Test fun `hosts without app_profiles have no game profile`() = runTest {
        backend.apps = listOf(NvApp("Dota 2", 881, false))
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true,"version":"0.2","features":["apps"]}""",
            NovaApi.APPS to """[{"id":"steam-570","appid":881,"name":"Dota 2"}]""",
            NovaApi.profile("steam-570") to """{"profile":{}}""",
        )
        val repo = repo()
        val id = repo.addPaired()
        repo.refresh(id)
        assertNull(repo.appProfile(id, "881"))
    }

    @Test fun `plain hosts are never probed before pairing`() = runTest {
        backend.novaJson = mapOf(NovaApi.CAPABILITIES to """{"nova":true}""")
        val repo = repo()
        repo.addManually("192.168.1.5")
        assertFalse(repo.hosts.value.single().isNova)
    }

    @Test fun `art prefers nova, falls back to box art and is cached on disk`() = runTest {
        backend.apps = listOf(NvApp("Dota 2", 881, false), NvApp("Notepad", 7, false))
        backend.novaJson = mapOf(
            NovaApi.CAPABILITIES to """{"nova":true}""",
            NovaApi.APPS to """[{"id":"steam-570","appid":881,"name":"Dota 2","has":{"hero":true}}]""",
        )
        backend.novaBytes = mapOf(NovaApi.art("steam-570", ArtKind.HERO) to byteArrayOf(1, 2))
        backend.boxArt = byteArrayOf(9)
        val repo = repo(ArtCache(tmp.newFolder("art")))
        val id = repo.addPaired()
        repo.loadApps(id)

        assertArrayEquals(byteArrayOf(1, 2), repo.loadArt(id, "881", ArtKind.HERO))
        assertArrayEquals(byteArrayOf(9), repo.loadArt(id, "7", ArtKind.POSTER))
        assertArrayEquals(byteArrayOf(9), repo.loadArt(id, "7", ArtKind.POSTER))
        assertEquals(1, backend.boxArtCalls)
        assertNull(repo.loadArt(id, "7", ArtKind.LOGO))
    }

    @Test fun `app list failures emit nothing instead of an empty list`() = runTest {
        val repo = repo()
        val id = repo.addManually("192.168.1.5").id
        backend.online = false
        assertNull(repo.loadApps(id))
    }
}
