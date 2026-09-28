package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.HostFeatures
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus

/**
 * Decides which host-linked actions Nebula shows. Nova advertises what it implements in the
 * `features` of GET /nova/v1/capabilities and lists what the calling device may do in
 * `permissions` (phase-1 wire contract). Anything the host doesn't advertise is hidden rather than
 * left to fail; anything it advertises but doesn't allow is shown disabled with how to allow it.
 */
object HostGating {
    const val FEATURE_SLEEP = "pcsleep"
    const val FEATURE_COMMANDS = "commands"
    const val FEATURE_SUPERCMD = "supercmd"
    const val PERMISSION_POWER = "power"
    const val PERMISSION_COMMANDS = "host_commands"

    /** Shown next to an action the host has turned off for this device. */
    const val PERMISSION_HINT = "Enable in Nova → Devices → Permissions"

    /**
     * [features] and [permissions] come from the capabilities document. Null [features] means the
     * host isn't Nova (or hasn't been probed yet); null [permissions] means an older host that
     * doesn't say, so actions are offered and a refusal is reported when it happens.
     */
    fun features(paired: Boolean, features: Set<String>?, permissions: Set<String>?): HostFeatures {
        if (!paired || features == null) return HostFeatures.None
        fun gate(feature: Boolean, permission: String) = when {
            !feature -> Gate.UNSUPPORTED
            permissions != null && permission !in permissions -> Gate.NOT_ALLOWED
            else -> Gate.AVAILABLE
        }
        return HostFeatures(
            sleep = gate(FEATURE_SLEEP in features, PERMISSION_POWER),
            commands = gate(FEATURE_COMMANDS in features || FEATURE_SUPERCMD in features, PERMISSION_COMMANDS),
        )
    }

    fun canWake(paired: Boolean, mac: String?): Boolean =
        paired && !mac.isNullOrBlank() && mac != "00:00:00:00:00:00"

    /** "Sleep PC" is listed while the host answers and has the feature (maybe disabled, see [Gate]). */
    fun sleepGate(host: Host?): Gate = if (host != null && host.status.reachable) host.features.sleep else Gate.UNSUPPORTED

    /** "Wake PC" for a paired host that isn't answering and has a MAC to wake. */
    fun showWake(host: Host): Boolean = host.canWake && !host.status.reachable

    /** Play needs a wake first: the host is paired, known to be down, and wakeable. */
    fun needsWake(host: Host?): Boolean = host != null && host.status == HostStatus.OFFLINE && host.canWake

    /**
     * The commands to list for a stream or game: host-wide ones first, then the game's own, without
     * duplicates by id. [global] is the host's full list (app commands of other games are dropped),
     * [forApp] the game's `SuperCmds` (which on Nova already include the global ones).
     */
    fun visibleCommands(host: Host?, global: List<HostCommand>, forApp: List<HostCommand>): HostCommands {
        if (host == null || !host.paired) return HostCommands.None
        if (host.features.commands == Gate.NOT_ALLOWED) return HostCommands(emptyList(), notAllowed = true)
        val g = if (host.features.commands == Gate.AVAILABLE) global.filter { !it.appScoped } else emptyList()
        val appIds = forApp.map { it.id }.toSet()
        val merged = (g.filterNot { it.id in appIds } + forApp).distinctBy { it.id }
        // Host-wide first, the game's own after, in the host's order within each group.
        return HostCommands(merged.sortedBy { if (it.appScoped) 1 else 0 })
    }

    /** Whether to show a commands entry point at all (disabled with [PERMISSION_HINT] when not allowed). */
    fun commandsGate(host: Host?, known: HostCommands): Gate = when {
        host == null || !host.paired || !host.status.reachable -> Gate.UNSUPPORTED
        host.features.commands == Gate.NOT_ALLOWED || known.notAllowed -> Gate.NOT_ALLOWED
        known.commands.isNotEmpty() -> Gate.AVAILABLE
        else -> Gate.UNSUPPORTED
    }

    private val HostStatus.reachable get() = this == HostStatus.ONLINE || this == HostStatus.STREAMING
}
