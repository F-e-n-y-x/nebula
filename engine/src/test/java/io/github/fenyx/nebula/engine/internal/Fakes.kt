package io.github.fenyx.nebula.engine.internal

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager
import java.io.IOException

/** In-memory host that answers the way a Sunshine/Nova server would. */
class FakeBackend : HostBackend {
    var online = true
    var uuid = "host-1"
    var name = "Desk"
    var pairedOnHost = false
    var pairOutcome = PairOutcome(PairingManager.PairState.PAIRED, "nebula-phone", null)
    var pairError: Exception? = null
    var apps: List<NvApp> = emptyList()
    var novaJson: Map<String, String> = emptyMap()
    var novaBytes: Map<String, ByteArray> = emptyMap()
    var boxArt: ByteArray? = null
    var runningGameId = 0
    val pins = mutableListOf<String>()
    var boxArtCalls = 0
    var woken = 0

    override fun poll(details: ComputerDetails): ComputerDetails? {
        if (!online) return null
        return ComputerDetails().also {
            it.uuid = uuid
            it.name = name
            it.state = ComputerDetails.State.ONLINE
            it.activeAddress = details.manualAddress ?: details.localAddress ?: details.activeAddress
            it.macAddress = "aa:bb:cc:dd:ee:ff"
            it.runningGameId = runningGameId
            it.pairState = if (pairedOnHost) PairingManager.PairState.PAIRED else PairingManager.PairState.NOT_PAIRED
        }
    }

    override fun generatePin() = "1234"

    override fun isPaired(details: ComputerDetails) = pairedOnHost

    override fun pair(details: ComputerDetails, pin: String): PairOutcome {
        pins += pin
        pairError?.let { throw it }
        if (pairOutcome.state == PairingManager.PairState.PAIRED) pairedOnHost = true
        return pairOutcome
    }

    override fun unpair(details: ComputerDetails) {
        if (!online) throw IOException("offline")
        pairedOnHost = false
    }

    override fun appList(details: ComputerDetails): List<NvApp> {
        if (!online) throw IOException("offline")
        return apps
    }

    override fun novaJson(details: ComputerDetails, path: String) = novaJson[path]

    override fun novaBytes(details: ComputerDetails, path: String) = novaBytes[path]

    /** Replies to POSTs by path; each body sent is recorded in [posted]. */
    var novaPostReplies: Map<String, String> = emptyMap()
    val posted = mutableListOf<Pair<String, String>>()
    var postRefusal: io.github.fenyx.nebula.engine.HostRefusedException? = null

    override fun novaPost(details: ComputerDetails, path: String, json: String): String? {
        posted += path to json
        postRefusal?.let { throw it }
        return novaPostReplies[path]
    }

    override fun boxArt(details: ComputerDetails, app: NvApp): ByteArray? {
        boxArtCalls++
        return boxArt
    }

    override fun wake(details: ComputerDetails) {
        woken++
    }

    var sleepRefusal: String? = null
    var slept = 0
    val commandsRun = mutableListOf<String>()

    override fun pcSleep(details: ComputerDetails): Boolean {
        if (!online) throw IOException("offline")
        sleepRefusal?.let { throw io.github.fenyx.nebula.engine.HostRefusedException(it, 403) }
        slept++
        return true
    }

    var quitResult: Boolean = true
    var quitError: Exception? = null
    var quits = 0

    override fun quitApp(details: ComputerDetails): Boolean {
        if (!online) throw IOException("offline")
        quitError?.let { throw it }
        quits++
        if (quitResult) runningGameId = 0
        return quitResult
    }

    override fun superCmd(details: ComputerDetails, cmdId: String): Boolean {
        if (!online) throw IOException("offline")
        commandsRun += cmdId
        return true
    }
}

class FakeStore : HostStore {
    val saved = linkedMapOf<String, ComputerDetails>()
    val pairNames = mutableMapOf<String, String>()

    override fun all() = saved.values.toList()
    override fun save(details: ComputerDetails) {
        saved[details.uuid!!] = details
    }
    override fun delete(details: ComputerDetails) {
        saved.remove(details.uuid)
    }
    override fun pairName(uuid: String) = pairNames[uuid].orEmpty()
    override fun savePairName(uuid: String, pairName: String) {
        pairNames[uuid] = pairName
    }
}

/** Minimal certificate; the repository only stores it and checks it for null. */
class FakeCert : java.security.cert.X509Certificate() {
    override fun getEncoded() = byteArrayOf(1)
    override fun verify(key: java.security.PublicKey?) = Unit
    override fun verify(key: java.security.PublicKey?, sigProvider: String?) = Unit
    override fun toString() = "FakeCert"
    override fun getPublicKey(): java.security.PublicKey? = null
    override fun checkValidity() = Unit
    override fun checkValidity(date: java.util.Date?) = Unit
    override fun getVersion() = 3
    override fun getSerialNumber(): java.math.BigInteger = java.math.BigInteger.ONE
    override fun getIssuerDN(): java.security.Principal? = null
    override fun getSubjectDN(): java.security.Principal? = null
    override fun getNotBefore() = java.util.Date(0)
    override fun getNotAfter() = java.util.Date(0)
    override fun getTBSCertificate() = byteArrayOf()
    override fun getSignature() = byteArrayOf()
    override fun getSigAlgName() = "none"
    override fun getSigAlgOID() = "0"
    override fun getSigAlgParams(): ByteArray? = null
    override fun getIssuerUniqueID(): BooleanArray? = null
    override fun getSubjectUniqueID(): BooleanArray? = null
    override fun getKeyUsage(): BooleanArray? = null
    override fun getBasicConstraints() = -1
    override fun hasUnsupportedCriticalExtension() = false
    override fun getCriticalExtensionOIDs(): MutableSet<String>? = null
    override fun getNonCriticalExtensionOIDs(): MutableSet<String>? = null
    override fun getExtensionValue(oid: String?): ByteArray? = null
}
