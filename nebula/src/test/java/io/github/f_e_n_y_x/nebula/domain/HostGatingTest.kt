package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostFeatures
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostGatingTest {
    private val phase1Features = setOf("apps", "pcsleep", "commands", "supercmd", "wol", "mic", "clipboard")

    private fun host(status: HostStatus = HostStatus.ONLINE, features: HostFeatures = HostFeatures(Gate.AVAILABLE, Gate.AVAILABLE), paired: Boolean = true, canWake: Boolean = true) =
        Host("h", "atom", "10.0.0.2", status, paired = paired, isNova = true, features = features, canWake = canWake)

    @Test fun `non-Nova or unpaired hosts get nothing`() {
        assertEquals(HostFeatures.None, HostGating.features(paired = true, features = null, permissions = null))
        assertEquals(HostFeatures.None, HostGating.features(paired = false, features = phase1Features, permissions = null))
    }

    @Test fun `advertised and permitted means available`() {
        val f = HostGating.features(true, phase1Features, setOf("power", "host_commands", "clipboard"))
        assertEquals(HostFeatures(sleep = Gate.AVAILABLE, commands = Gate.AVAILABLE), f)
    }

    @Test fun `new permission bits default off on Nova, so actions show as not allowed`() {
        val f = HostGating.features(true, phase1Features, setOf("input_keyboard", "input_mouse", "clipboard", "launch_apps"))
        assertEquals(HostFeatures(sleep = Gate.NOT_ALLOWED, commands = Gate.NOT_ALLOWED), f)
    }

    @Test fun `an older host without a permissions list offers what it advertises`() {
        assertEquals(HostFeatures(Gate.AVAILABLE, Gate.UNSUPPORTED), HostGating.features(true, setOf("apps", "pcsleep"), null))
    }

    @Test fun `features the host leaves out are hidden`() {
        assertEquals(HostFeatures.None, HostGating.features(true, setOf("apps", "art", "details", "display_mode", "bitrate", "sessions"), setOf("power")))
        // supercmd alone (Foundation-style) still counts as having commands.
        assertEquals(Gate.AVAILABLE, HostGating.features(true, setOf("supercmd"), setOf("host_commands")).commands)
    }

    @Test fun `wake needs pairing and a real MAC`() {
        assertTrue(HostGating.canWake(true, "aa:bb:cc:dd:ee:ff"))
        assertFalse(HostGating.canWake(false, "aa:bb:cc:dd:ee:ff"))
        assertFalse(HostGating.canWake(true, "00:00:00:00:00:00"))
        assertFalse(HostGating.canWake(true, null))
    }

    @Test fun `sleep only while reachable, wake only while not`() {
        assertEquals(Gate.AVAILABLE, HostGating.sleepGate(host()))
        assertEquals(Gate.AVAILABLE, HostGating.sleepGate(host(HostStatus.STREAMING)))
        assertEquals(Gate.UNSUPPORTED, HostGating.sleepGate(host(HostStatus.OFFLINE)))
        assertEquals(Gate.NOT_ALLOWED, HostGating.sleepGate(host(features = HostFeatures(Gate.NOT_ALLOWED, Gate.UNSUPPORTED))))
        assertTrue(HostGating.showWake(host(HostStatus.OFFLINE)))
        assertFalse(HostGating.showWake(host(HostStatus.ONLINE)))
        assertTrue(HostGating.needsWake(host(HostStatus.OFFLINE)))
        assertFalse(HostGating.needsWake(host(HostStatus.UNKNOWN)))
        assertFalse(HostGating.needsWake(host(HostStatus.OFFLINE, canWake = false)))
    }

    @Test fun `visible commands put host-wide first and drop duplicates and other games`() {
        val global = listOf(HostCommand("lock", "Lock screen"), HostCommand("other-game", "Other", appScoped = true), HostCommand("steam", "Restart Steam"))
        val forApp = listOf(HostCommand("reset", "Reset graphics", appScoped = true), HostCommand("lock", "Lock screen"))
        val v = HostGating.visibleCommands(host(), global, forApp)
        assertEquals(listOf("steam", "lock", "reset"), v.commands.map { it.id })
        assertFalse(v.notAllowed)
    }

    @Test fun `not allowed hides the list but says so`() {
        val v = HostGating.visibleCommands(host(features = HostFeatures(Gate.AVAILABLE, Gate.NOT_ALLOWED)), listOf(HostCommand("a", "A")), emptyList())
        assertTrue(v.notAllowed)
        assertTrue(v.commands.isEmpty())
        assertEquals(Gate.NOT_ALLOWED, HostGating.commandsGate(host(features = HostFeatures(Gate.AVAILABLE, Gate.NOT_ALLOWED)), v))
    }

    @Test fun `a host without the commands feature can still send a game's SuperCmds`() {
        val h = host(features = HostFeatures.None)
        val v = HostGating.visibleCommands(h, listOf(HostCommand("g", "Global")), listOf(HostCommand("x", "Mine", appScoped = true)))
        assertEquals(listOf("x"), v.commands.map { it.id })
        assertEquals(Gate.AVAILABLE, HostGating.commandsGate(h, v))
        assertEquals(Gate.UNSUPPORTED, HostGating.commandsGate(h, io.github.f_e_n_y_x.nebula.domain.model.HostCommands.None))
        assertEquals(Gate.UNSUPPORTED, HostGating.commandsGate(host(HostStatus.OFFLINE), v))
    }
}
