package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import io.github.fenyx.nebula.engine.AddressKind
import io.github.fenyx.nebula.engine.Host
import io.github.fenyx.nebula.engine.HostAddress
import io.github.fenyx.nebula.engine.HostState
import io.github.fenyx.nebula.engine.NovaCapabilities

const val DEFAULT_HTTP_PORT = 47989

/**
 * Parses what a user types into "Add host": `host`, `host:port`, `[v6]`, `[v6]:port` or a bare
 * IPv6 literal. Returns null for blank or malformed input.
 */
fun parseHostAddress(raw: String): ComputerDetails.AddressTuple? {
    val input = raw.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
    if (input.isEmpty() || input.any { it.isWhitespace() }) return null

    val (host, portText) = when {
        input.startsWith("[") -> {
            val close = input.indexOf(']')
            if (close <= 1) return null
            val rest = input.substring(close + 1)
            when {
                rest.isEmpty() -> input.substring(1, close) to null
                rest.startsWith(":") -> input.substring(1, close) to rest.substring(1)
                else -> return null
            }
        }
        input.count { it == ':' } > 1 -> input to null // bare IPv6 literal
        ':' in input -> input.substringBefore(':') to input.substringAfter(':')
        else -> input to null
    }
    if (host.isEmpty()) return null
    val port = if (portText == null) DEFAULT_HTTP_PORT else portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return ComputerDetails.AddressTuple(host, port)
}

private fun ComputerDetails.AddressTuple.toHostAddress(kind: AddressKind) = HostAddress(address, port, kind)

/** Maps V+'s mutable [ComputerDetails] into the engine's immutable [Host]. */
fun ComputerDetails.toHost(nova: NovaCapabilities?): Host {
    val addresses = buildList {
        localAddress?.let { add(it.toHostAddress(AddressKind.LOCAL)) }
        manualAddress?.let { add(it.toHostAddress(AddressKind.MANUAL)) }
        remoteAddress?.let { add(it.toHostAddress(AddressKind.REMOTE)) }
        ipv6Address?.let { add(it.toHostAddress(AddressKind.IPV6)) }
    }.distinctBy { it.address to it.port }
    val active = activeAddress?.let { a -> addresses.firstOrNull { it.address == a.address && it.port == a.port } }
    return Host(
        id = uuid.orEmpty(),
        name = name ?: activeAddress?.address ?: "Unknown host",
        addresses = addresses,
        activeAddress = active,
        state = when (state) {
            ComputerDetails.State.ONLINE -> HostState.ONLINE
            ComputerDetails.State.OFFLINE -> HostState.OFFLINE
            else -> HostState.UNKNOWN
        },
        // A pinned server certificate is what makes a host usable, even while it's offline.
        paired = pairState == PairingManager.PairState.PAIRED || (pairState == null && serverCert != null),
        isNova = nova != null,
        novaCapabilities = nova,
        runningAppId = runningGameId.takeIf { it != 0 && state == ComputerDetails.State.ONLINE },
        macAddress = macAddress?.takeIf { it != "00:00:00:00:00:00" },
    )
}
