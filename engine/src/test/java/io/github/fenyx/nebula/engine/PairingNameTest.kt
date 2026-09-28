package io.github.fenyx.nebula.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingNameTest {
    @Test
    fun deviceNameWithOwnerIsUsedAsIs() {
        assertEquals("Ayush's S25 Ultra", PairingName.deviceName("Ayush's S25 Ultra", "SM-S938B", "samsung"))
        assertEquals("Ayush’s Tab", PairingName.deviceName("  Ayush’s   Tab ", "SM-X710", "samsung"))
        assertEquals("James' Shield", PairingName.deviceName("James' Shield", "SHIELD Android TV", "NVIDIA"))
    }

    @Test
    fun withoutOwnerTheMarketingModelIsSent() {
        assertEquals("Galaxy S25 Ultra", PairingName.deviceName("Galaxy S25 Ultra", "SM-S938B", "samsung"))
        assertEquals("Galaxy S25 Ultra", PairingName.deviceName("Living room phone", "SM-S938U1", "samsung"))
        assertEquals("Galaxy Tab S9", PairingName.deviceName(null, "SM-X716B", "samsung"))
        assertEquals("Pixel 9 Pro", PairingName.deviceName(null, "Pixel 9 Pro", "Google"))
        assertEquals("OnePlus CPH2581", PairingName.deviceName("", "CPH2581", "OnePlus"))
        assertEquals("Samsung SM-A546B", PairingName.deviceName(null, "SM-A546B", "samsung"))
        assertEquals("SHIELD Android TV", PairingName.deviceName(null, "SHIELD Android TV", "NVIDIA"))
    }

    @Test
    fun unknownDevicesStillGetAName() {
        assertEquals("My tablet", PairingName.deviceName("My tablet", null, null))
        assertEquals(PairingName.FALLBACK_DEVICE, PairingName.deviceName(null, null, null))
        assertEquals(PairingName.FALLBACK_DEVICE, PairingName.deviceName(" \t ", "  ", ""))
    }

    @Test
    fun ownerDetection() {
        assertTrue(PairingName.hasOwner("Ayush's S25 Ultra"))
        assertTrue(PairingName.hasOwner("Ayush’s phone"))
        assertTrue(PairingName.hasOwner("phone of Ayush's"))
        assertFalse(PairingName.hasOwner("Galaxy S25 Ultra"))
        assertFalse(PairingName.hasOwner("'s tablet"))
        assertFalse(PairingName.hasOwner("Ayush'sPhone"))
    }

    @Test
    fun displayNameFollowsNovasPattern() {
        assertEquals("Nebula from Ayush's S25 Ultra", PairingName.display("Nebula", "Ayush's S25 Ultra"))
        assertEquals("Nebula from Galaxy S25 Ultra", PairingName.display("Nebula", "Galaxy S25 Ultra"))
        assertEquals("Nebula tablet", PairingName.display("Nebula", "Nebula tablet"))
        assertEquals("Nebula from Nebulae", PairingName.display("Nebula", "Nebulae"))
        assertEquals("Nebula", PairingName.display("Nebula", "  "))
        assertEquals("Nebula from Ayush's S25 Ultra", PairingIdentity("Ayush's S25 Ultra").displayName)
    }

    @Test
    fun cleaningTrimsCollapsesAndCutsOnCodePoints() {
        assertEquals("Zoë's 📱 Phone", PairingName.clean("  Zoë's\t📱\n Phone \u0007"))
        val long = PairingName.clean("x".repeat(100))
        assertEquals(PairingName.MAX_DEVICE_CHARS, long.length)
        val emoji = PairingName.clean("🎮".repeat(60))
        assertEquals(PairingName.MAX_DEVICE_CHARS, emoji.codePointCount(0, emoji.length))
        assertFalse(Character.isHighSurrogate(emoji.last()))
        // A cut that lands on a space doesn't leave it dangling.
        assertEquals("a".repeat(47), PairingName.clean("a".repeat(47) + " bbbb"))
        assertEquals("", PairingName.clean(null))
    }

    @Test
    fun queryCarriesAppVersionAndForm() {
        val params = PairingIdentity("Ayush's S25 Ultra", version = "0.3.0-dev7", form = FormFactor.TABLET).queryParameters()
        assertEquals(
            listOf(
                "devicename" to "Ayush's S25 Ultra",
                "clientapp" to "Nebula",
                "clientver" to "0.3.0-dev7",
                "clientform" to "tablet",
            ),
            params,
        )
        assertEquals(listOf("devicename" to PairingName.FALLBACK_DEVICE, "clientapp" to "Nebula", "clientform" to "tv"),
            PairingIdentity("", form = FormFactor.TV).queryParameters())
    }
}
