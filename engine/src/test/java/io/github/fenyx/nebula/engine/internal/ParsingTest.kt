package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import io.github.fenyx.nebula.engine.AddressKind
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.HostState
import io.github.fenyx.nebula.engine.NovaCapabilities
import io.github.fenyx.nebula.engine.NovaDisplayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostAddressTest {
    private fun parse(raw: String) = parseHostAddress(raw)?.let { it.address to it.port }

    @Test fun `bare host gets the default port`() = assertEquals("192.168.1.5" to 47989, parse("192.168.1.5"))
    @Test fun `host with port`() = assertEquals("desk.local" to 48010, parse(" desk.local:48010 "))
    @Test fun `bracketed ipv6 with port`() = assertEquals("fe80::1" to 48010, parse("[fe80::1]:48010"))
    @Test fun `bracketed ipv6 without port`() = assertEquals("fe80::1" to 47989, parse("[fe80::1]"))
    @Test fun `bare ipv6`() = assertEquals("fe80::1" to 47989, parse("fe80::1"))
    @Test fun `scheme and trailing slash are ignored`() = assertEquals("desk" to 47989, parse("http://desk/"))

    @Test fun `rejects malformed input`() {
        listOf("", "  ", "desk:0", "desk:70000", "desk:abc", "[]", "[fe80::1]x", "my desk").forEach {
            assertNull("expected null for '$it'", parse(it))
        }
    }
}

class NovaApiTest {
    @Test fun `capabilities require the nova flag`() {
        assertNull(NovaApi.parseCapabilities("""{"version":"1"}"""))
        assertEquals(
            NovaCapabilities("0.4.0", setOf("apps", "art")),
            NovaApi.parseCapabilities("""{"nova":true,"version":"0.4.0","features":["apps","art"]}"""),
        )
    }

    @Test fun `parses app entries`() {
        val apps = NovaApi.parseApps(
            """[{"id":"steam-570","appid":881,"name":"Dota 2","running":true,
                "has":{"poster":true,"hero":true,"logo":false},"last_played":1700000000,
                "playtime_s":3600,"mode_default":"virtual"},
               {"id":"desk","name":"Desktop"}]""",
        )
        val dota = apps[0]
        assertEquals("steam-570", dota.id)
        assertEquals(881, dota.gameStreamId)
        assertTrue(dota.running)
        assertEquals(setOf(ArtKind.POSTER, ArtKind.HERO), dota.art)
        assertEquals(1_700_000_000L, dota.lastPlayed)
        assertEquals(3600L, dota.playtimeSeconds)
        assertEquals(NovaDisplayMode.VIRTUAL, dota.modeDefault)

        val desktop = apps[1]
        assertNull(desktop.gameStreamId)
        assertTrue(desktop.art.isEmpty())
        assertNull(desktop.lastPlayed)
        assertNull(desktop.modeDefault)
    }

    @Test fun `parses details`() {
        val d = NovaApi.parseDetails(
            """{"description":"MOBA","genres":["Strategy"],"developer":"Valve","publisher":"",
                "screenshots":["apps/steam-570/screenshots/0"],"metacritic":90,
                "last_session":{"device":"Pixel","resolution":"1920x1080","fps":60,"codec":"hevc"}}""",
        )
        assertEquals("MOBA", d.description)
        assertEquals(listOf("Strategy"), d.genres)
        assertNull(d.publisher)
        assertEquals(90, d.metacritic)
        assertEquals("nova/v1/apps/steam-570/screenshots/0", NovaApi.screenshot(d.screenshots[0]))
        assertEquals(60, d.lastSession?.fps)
        assertNull(d.releaseDate)
    }

    @Test fun `builds endpoint paths`() {
        assertEquals("nova/v1/apps/steam-570/art/hero", NovaApi.art("steam-570", ArtKind.HERO))
        assertEquals("nova/v1/apps/steam-570/details", NovaApi.details("steam-570"))
        assertEquals("nova/v1/apps/steam-570/profile", NovaApi.profile("steam-570"))
        assertEquals("nova/v1/x", NovaApi.screenshot("/nova/v1/x"))
    }
}

class HostMappingTest {
    @Test fun `maps an online paired host`() {
        val d = ComputerDetails().apply {
            uuid = "u1"
            name = "Desk"
            state = ComputerDetails.State.ONLINE
            localAddress = ComputerDetails.AddressTuple("192.168.1.5", 47989)
            manualAddress = ComputerDetails.AddressTuple("192.168.1.5", 47989)
            remoteAddress = ComputerDetails.AddressTuple("203.0.113.9", 47989)
            activeAddress = ComputerDetails.AddressTuple("192.168.1.5", 47989)
            pairState = PairingManager.PairState.PAIRED
            runningGameId = 881
            macAddress = "00:00:00:00:00:00"
        }
        val host = d.toHost(NovaCapabilities("1", emptySet()))
        assertEquals("u1", host.id)
        assertEquals(HostState.ONLINE, host.state)
        assertEquals(listOf(AddressKind.LOCAL, AddressKind.REMOTE), host.addresses.map { it.kind })
        assertEquals(AddressKind.LOCAL, host.activeAddress?.kind)
        assertTrue(host.paired)
        assertTrue(host.isNova)
        assertEquals(881, host.runningAppId)
        assertNull(host.macAddress)
    }

    @Test fun `offline host keeps no running app and unknown pair state falls back to the cert`() {
        val d = ComputerDetails().apply {
            uuid = "u1"
            state = ComputerDetails.State.OFFLINE
            runningGameId = 5
        }
        val host = d.toHost(null)
        assertEquals(HostState.OFFLINE, host.state)
        assertNull(host.runningAppId)
        assertFalse(host.paired)
        assertFalse(host.isNova)
        assertEquals("Unknown host", host.name)
    }
}

class MergeAppsTest {
    @Test fun `matches nova entries by id then by name`() {
        val gs = listOf(NvApp("Dota 2", 881, true), NvApp("Desktop", 1, false), NvApp("Notepad", 7, false))
        val nova = listOf(
            NovaApp("steam-570", 881, "Dota 2 (Steam)", false, setOf(ArtKind.HERO), 5, 10, NovaDisplayMode.MIRROR),
            NovaApp("desk", null, "desktop", true, setOf(ArtKind.ICON), null, null, null),
        )
        val merged = HostRepository.mergeApps(gs, nova, runningGameId = 7)

        assertEquals(listOf("881", "1", "7"), merged.map { it.id })
        assertEquals("steam-570", merged[0].novaId)
        assertEquals("Dota 2", merged[0].name)
        assertTrue(merged[0].hdrSupported)
        assertEquals(NovaDisplayMode.MIRROR, merged[0].modeDefault)
        assertEquals("desk", merged[1].novaId)
        assertTrue(merged[1].running)
        assertTrue(merged[1].isDesktop)
        assertNull(merged[2].novaId)
        assertEquals(setOf(ArtKind.POSTER), merged[2].availableArt)
        assertTrue(merged[2].running)
    }

    @Test fun `profile replies parse with defaults for older hosts`() {
        val full = NovaApi.parseProfile(
            """{"profile":{"fps_cap":72,"fsr":3,"vkbasalt":true,"vkbasalt_cas":80,"mangohud":true,"bitrate_kbps":25000,"power":"balanced"},
               "launcher":"steam","applies":false,"can_edit":false,"limits":{"fps_cap":[0,500],"bitrate_kbps":[1000,300000]}}""",
        )
        assertEquals(72, full.fpsCap)
        assertEquals(3, full.fsr)
        assertTrue(full.vkbasalt)
        assertEquals(80, full.vkbasaltCas)
        assertEquals(25_000, full.bitrateKbps)
        assertEquals(io.github.fenyx.nebula.engine.HostPowerMode.BALANCED, full.power)
        assertEquals("steam", full.launcher)
        assertFalse(full.applies)
        assertFalse(full.canEdit)
        assertEquals(500, full.maxFpsCap)
        assertEquals(1000, full.minBitrateKbps)
        assertEquals(300_000, full.maxBitrateKbps)

        // A host from before the stream settings: no bitrate_kbps, power or limits.
        val old = NovaApi.parseProfile("""{"profile":{"fps_cap":60,"fsr":0,"vkbasalt":false,"vkbasalt_cas":50,"mangohud":false},"launcher":"proton","applies":true,"can_edit":true}""")
        assertEquals(0, old.bitrateKbps)
        assertEquals(io.github.fenyx.nebula.engine.HostPowerMode.DEFAULT, old.power)
        assertEquals(800_000, old.maxBitrateKbps)
        assertFalse(old.isDefault)
        assertTrue(NovaApi.parseProfile("{}").isDefault)
    }

    @Test fun `profile bodies carry only the changed keys`() {
        assertEquals("""{"power":"performance"}""", NovaApi.profileBody(mapOf("power" to io.github.fenyx.nebula.engine.HostPowerMode.PERFORMANCE)))
        assertEquals("""{"bitrate_kbps":0}""", NovaApi.profileBody(mapOf("bitrate_kbps" to 0)))
    }
}
