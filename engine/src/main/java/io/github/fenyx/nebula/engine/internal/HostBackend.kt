package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import java.security.cert.X509Certificate

/** Result of a pairing exchange with a host. */
data class PairOutcome(
    val state: PairingManager.PairState,
    val pairName: String,
    val serverCert: X509Certificate?,
)

/**
 * Everything the engine asks of the network. [NvHttpHostBackend] is the real GameStream/Nova
 * implementation; tests use fakes. All calls block and must run off the main thread.
 */
interface HostBackend {
    /** Poll every known address of [details]; returns fresh details (with activeAddress) or null if unreachable. */
    fun poll(details: ComputerDetails): ComputerDetails?

    fun generatePin(): String

    /** True when the host already considers this client paired. */
    fun isPaired(details: ComputerDetails): Boolean

    fun pair(details: ComputerDetails, pin: String): PairOutcome

    fun unpair(details: ComputerDetails)

    fun appList(details: ComputerDetails): List<NvApp>

    /** Body of a /nova/v1 JSON endpoint, or null when the host doesn't implement it. */
    fun novaJson(details: ComputerDetails, path: String): String?

    /** Bytes of a /nova/v1 binary endpoint (art, screenshots), or null when absent. */
    fun novaBytes(details: ComputerDetails, path: String): ByteArray?

    fun boxArt(details: ComputerDetails, app: NvApp): ByteArray?

    fun wake(details: ComputerDetails)

    /** GET /pcsleep. True when the host accepted; throws HostRefusedException when it refused. */
    fun pcSleep(details: ComputerDetails): Boolean

    /** GET /supercmd?cmdId=. True when the host ran it; throws HostRefusedException when it refused. */
    fun superCmd(details: ComputerDetails, cmdId: String): Boolean
}
