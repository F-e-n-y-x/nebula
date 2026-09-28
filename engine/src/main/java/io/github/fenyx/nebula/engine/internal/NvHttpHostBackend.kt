package io.github.fenyx.nebula.engine.internal

import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import io.github.fenyx.nebula.engine.HostRefusedException
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.wol.WakeOnLanSender
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** GameStream/Nova over NvHTTP, authenticated with this device's client certificate. */
class NvHttpHostBackend(
    private val uniqueId: () -> String,
    private val clientName: () -> String,
    private val crypto: LimelightCryptoProvider,
) : HostBackend {

    private val pollExecutor = Executors.newCachedThreadPool { r ->
        Thread(r, "nebula-poll").apply { isDaemon = true }
    }

    private fun http(details: ComputerDetails, address: ComputerDetails.AddressTuple? = null): NvHTTP {
        val target = address ?: details.activeAddress ?: throw IOException("No active address for ${details.name}")
        val reusePort = details.httpsPort != 0 && target == details.activeAddress
        return NvHTTP(
            target,
            if (reusePort) details.httpsPort else 0,
            uniqueId(),
            clientName(),
            details.serverCert,
            crypto,
        )
    }

    override fun poll(details: ComputerDetails): ComputerDetails? {
        val candidates = listOfNotNull(
            details.localAddress, details.manualAddress, details.remoteAddress,
            details.ipv6Address.takeUnless { details.ipv6Disabled },
        ).distinct()
        if (candidates.isEmpty()) return null

        val completion = ExecutorCompletionService<ComputerDetails?>(pollExecutor)
        val futures = candidates.map { address ->
            completion.submit(Callable { pollAddress(details, address) })
        }
        try {
            repeat(candidates.size) {
                val result = completion.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS)?.get() ?: return@repeat
                return result
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            futures.forEach { it.cancel(true) }
        }
        return null
    }

    private fun pollAddress(details: ComputerDetails, address: ComputerDetails.AddressTuple): ComputerDetails? = try {
        val likelyOnline = details.state == ComputerDetails.State.ONLINE && address == details.activeAddress
        val fresh = http(details, address).getComputerDetails(likelyOnline)
        when {
            fresh.uuid == null -> null
            details.uuid != null && details.uuid != fresh.uuid -> null // a different PC answered
            else -> fresh.also { it.activeAddress = address }
        }
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        null
    } catch (e: Exception) {
        null
    }

    override fun generatePin(): String = PairingManager.generatePinString()

    override fun isPaired(details: ComputerDetails): Boolean =
        http(details).getPairState() == PairingManager.PairState.PAIRED

    override fun pair(details: ComputerDetails, pin: String): PairOutcome {
        val http = http(details)
        val result = http.pairingManager.pair(http.getServerInfo(true), pin)
        return PairOutcome(result.state, result.pairName, http.pairingManager.pairedCert)
    }

    override fun unpair(details: ComputerDetails) {
        http(details).unpair()
    }

    override fun appList(details: ComputerDetails): List<NvApp> = http(details).getAppList()

    override fun novaJson(details: ComputerDetails, path: String): String? = try {
        http(details).novaGet(path).use { it.string() }
    } catch (e: FileNotFoundException) {
        null
    }

    override fun novaBytes(details: ComputerDetails, path: String): ByteArray? = try {
        http(details).novaGet(path).use { it.bytes() }
    } catch (e: FileNotFoundException) {
        null
    }

    override fun boxArt(details: ComputerDetails, app: NvApp): ByteArray? = try {
        http(details).getBoxArt(app).use { it.readBytes() }
    } catch (e: Exception) {
        LimeLog.info("Box art unavailable for ${app.appName}: ${e.message}")
        null
    }

    override fun wake(details: ComputerDetails) {
        WakeOnLanSender.sendWolPacket(details)
    }

    override fun pcSleep(details: ComputerDetails): Boolean = refusalsMapped { http(details).pcSleep() }

    override fun superCmd(details: ComputerDetails, cmdId: String): Boolean =
        refusalsMapped { http(details).sendSuperCmd(java.net.URLEncoder.encode(cmdId, "UTF-8")) }

    /** 401/403 (no permission) and 503 (busy) become [HostRefusedException]; a 404 means the host lacks the route. */
    private inline fun refusalsMapped(block: () -> Boolean): Boolean = try {
        block()
    } catch (e: HostHttpResponseException) {
        when (e.getErrorCode()) {
            401, 403 -> throw HostRefusedException(e.getErrorMessage().ifBlank { "Not allowed" }, e.getErrorCode())
            503, 409 -> throw HostRefusedException(e.getErrorMessage().ifBlank { "The PC is busy" }, e.getErrorCode())
            400, 404 -> throw HostRefusedException(e.getErrorMessage().ifBlank { "This PC doesn't support that" }, e.getErrorCode())
            else -> throw e
        }
    } catch (e: FileNotFoundException) {
        throw HostRefusedException("This PC doesn't support that", 404)
    }

    private companion object {
        const val POLL_TIMEOUT_MS = 8_000L
    }
}
