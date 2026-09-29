package io.github.fenyx.nebula.engine.internal

import com.limelight.LimeLog
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import io.github.fenyx.nebula.engine.AppDetails
import io.github.fenyx.nebula.engine.ArtKind
import io.github.fenyx.nebula.engine.Host
import io.github.fenyx.nebula.engine.HostApp
import io.github.fenyx.nebula.engine.HostCommandList
import io.github.fenyx.nebula.engine.HostRefusedException
import io.github.fenyx.nebula.engine.NovaCapabilities
import io.github.fenyx.nebula.engine.PairingFailure
import io.github.fenyx.nebula.engine.PairingState
import io.github.fenyx.nebula.engine.RunningApp
import io.github.fenyx.nebula.engine.NovaFeature
import io.github.fenyx.nebula.engine.HostState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Host list, polling, pairing and app/art loading, independent of Android so it can be tested
 * with a fake [HostBackend] and [HostStore]. [NebulaEngine][io.github.fenyx.nebula.engine.NebulaEngine]
 * is the public face of this class.
 */
class HostRepository(
    private val backend: HostBackend,
    private val store: HostStore,
    private val art: ArtCache?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val pollIntervalMs: Long = 5_000L,
    private val appPollIntervalMs: Long = 10_000L,
) {
    /** Live details by host UUID. Each object is mutated only while holding its own monitor. */
    private val known = ConcurrentHashMap<String, ComputerDetails>()
    /** Nova capabilities by host UUID; a present key with a null value means "probed, not Nova". */
    private val nova = ConcurrentHashMap<String, NovaCapabilities?>()
    /** Last Nova app list per host, used to resolve art and details by GameStream id. */
    private val novaApps = ConcurrentHashMap<String, List<NovaApp>>()
    /** GameStream app id → matched Nova app, per host, from the last merged app list. */
    private val novaByAppId = ConcurrentHashMap<String, Map<String, NovaApp>>()
    private val pairing = ConcurrentHashMap.newKeySet<String>()

    private val _hosts = MutableStateFlow<List<Host>>(emptyList())
    val hosts: StateFlow<List<Host>> = _hosts.asStateFlow()

    private var pollJob: Job? = null

    /** Loads saved hosts; they start in [io.github.fenyx.nebula.engine.HostState.UNKNOWN]. */
    suspend fun load() = withContext(io) {
        store.all().forEach { details ->
            val uuid = details.uuid ?: return@forEach
            details.state = ComputerDetails.State.UNKNOWN
            known.putIfAbsent(uuid, details)
        }
        publish()
    }

    /** Polls every known host now and then every [pollIntervalMs] until [stopPolling]. */
    @Synchronized
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(io) {
            while (isActive) {
                refreshAll()
                delay(pollIntervalMs)
            }
        }
    }

    @Synchronized
    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun details(hostId: String): ComputerDetails? = known[hostId]

    fun pairName(hostId: String): String = store.pairName(hostId)

    suspend fun refreshAll() = coroutineScope {
        known.keys.map { id -> async { refresh(id) } }.awaitAll()
        Unit
    }

    /** Polls one host; returns its updated state, or null if the id is unknown. */
    suspend fun refresh(hostId: String): Host? = withContext(io) {
        val details = known[hostId] ?: return@withContext null
        if (hostId in pairing) return@withContext currentHost(hostId)
        val fresh = backend.poll(details)
        synchronized(details) {
            if (fresh == null) {
                details.state = ComputerDetails.State.OFFLINE
            } else {
                // Keep the pinned cert: serverinfo over HTTP doesn't carry it.
                val cert = details.serverCert
                details.update(fresh)
                details.state = ComputerDetails.State.ONLINE
                if (details.serverCert == null) details.serverCert = cert
            }
        }
        if (fresh != null) probeNova(hostId, details)
        publish()
        currentHost(hostId)
    }

    /**
     * Adds a host by address ("host", "host:port" or "[v6]:port"). Throws [IllegalArgumentException]
     * for unparseable input and [IOException] when nothing answers there.
     */
    suspend fun addManually(address: String): Host = withContext(io) {
        val tuple = parseHostAddress(address) ?: throw IllegalArgumentException("Not a host address: $address")
        val candidate = ComputerDetails().apply { manualAddress = tuple }
        addOrMerge(candidate) ?: throw IOException("No GameStream host answered at $address")
    }

    /** Adds a host found by mDNS. Returns null when it didn't answer a poll. */
    suspend fun addDiscovered(candidate: ComputerDetails): Host? = withContext(io) { addOrMerge(candidate) }

    private suspend fun addOrMerge(candidate: ComputerDetails): Host? {
        val fresh = backend.poll(candidate) ?: return null
        val uuid = fresh.uuid ?: return null
        val details = known.getOrPut(uuid) { candidate }
        synchronized(details) {
            val cert = details.serverCert
            if (details !== candidate) {
                candidate.manualAddress?.let { details.manualAddress = it }
                candidate.localAddress?.let { details.localAddress = it }
                candidate.ipv6Address?.let { details.ipv6Address = it }
            }
            details.update(fresh)
            details.state = ComputerDetails.State.ONLINE
            if (details.serverCert == null) details.serverCert = cert
        }
        store.save(details)
        probeNova(uuid, details)
        publish()
        return currentHost(uuid)
    }

    suspend fun wake(hostId: String): Boolean = withContext(io) {
        val details = known[hostId] ?: return@withContext false
        if (details.macAddress.isNullOrEmpty()) return@withContext false
        try {
            backend.wake(details)
            true
        } catch (e: IOException) {
            LimeLog.warning("Wake-on-LAN failed for ${details.name}: ${e.message}")
            false
        }
    }

    /**
     * Suspends the host (Foundation/Nova `/pcsleep`). Throws [HostRefusedException] when the host
     * refuses (no `power` permission, another stream running) and IOException when unreachable.
     */
    suspend fun sleep(hostId: String) = withContext(io) {
        val details = known[hostId] ?: throw IOException("Unknown host")
        if (details.activeAddress == null) throw IOException("${details.name} isn't reachable")
        if (!backend.pcSleep(details)) throw HostRefusedException("${details.name} didn't accept the sleep request")
    }

    /** Host-wide commands (`/nova/v1/commands`); empty when the host has none or doesn't list them. */
    suspend fun commands(hostId: String): HostCommandList = withContext(io) {
        val details = known[hostId] ?: return@withContext HostCommandList.Empty
        try {
            backend.novaJson(details, NovaApi.COMMANDS)?.let(NovaApi::parseCommands) ?: HostCommandList.Empty
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LimeLog.info("Host command list unavailable for ${details.name}: ${e.message}")
            HostCommandList.Empty
        }
    }

    /** Runs a host command by id. Same errors as [sleep]. */
    suspend fun runCommand(hostId: String, commandId: String) = withContext(io) {
        val details = known[hostId] ?: throw IOException("Unknown host")
        if (details.activeAddress == null) throw IOException("${details.name} isn't reachable")
        if (!backend.superCmd(details, commandId)) throw HostRefusedException("${details.name} didn't run the command")
    }

    /**
     * What runs on the host now, or null when nothing does. Nova hosts with the "running" feature
     * answer /nova/v1/running (start time, display); others fall back to a serverinfo poll's
     * `currentgame`. Throws IOException when the host can't be reached.
     */
    suspend fun running(hostId: String): RunningApp? = withContext(io) {
        val details = known[hostId] ?: throw IOException("Unknown host")
        val caps = nova[hostId]
        if (caps?.has(NovaFeature.RUNNING) == true && details.activeAddress != null) {
            val body = try {
                backend.novaJson(details, NovaApi.RUNNING)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LimeLog.info("Nova running state unavailable for ${details.name}: ${e.message}")
                null
            }
            if (body != null) {
                val r = NovaApi.parseRunning(body) ?: return@withContext null
                // Resolve a Nova-only id to the GameStream id the library uses.
                if (r.appId == null && r.novaId != null) {
                    val gs = novaByAppId[hostId]?.entries?.firstOrNull { it.value.id == r.novaId }?.key
                        ?: novaApps[hostId]?.firstOrNull { it.id == r.novaId }?.gameStreamId?.toString()
                    return@withContext r.copy(appId = gs)
                }
                return@withContext r
            }
        }
        val host = refresh(hostId) ?: throw IOException("Unknown host")
        if (host.state != HostState.ONLINE) throw IOException("${details.name} isn't reachable")
        host.runningAppId?.let { RunningApp(appId = it.toString()) }
    }

    /** Quits the app running on the host (`/cancel`). Same errors as [sleep]. */
    suspend fun quitApp(hostId: String) = withContext(io) {
        val details = known[hostId] ?: throw IOException("Unknown host")
        if (details.activeAddress == null) throw IOException("${details.name} isn't reachable")
        if (!backend.quitApp(details)) throw HostRefusedException("${details.name} didn't close the game")
        synchronized(details) { details.runningGameId = 0 }
        publish()
    }

    /** Tells the host to forget this client (best effort) and drops the pinned certificate. */
    suspend fun unpair(hostId: String) = withContext(io) {
        val details = known[hostId] ?: return@withContext
        try {
            if (details.activeAddress != null) backend.unpair(details)
        } catch (e: IOException) {
            LimeLog.warning("Host-side unpair failed for ${details.name}: ${e.message}")
        }
        synchronized(details) {
            details.serverCert = null
            details.pairState = PairState.NOT_PAIRED
        }
        store.save(details)
        nova.remove(hostId)
        novaApps.remove(hostId)
        novaByAppId.remove(hostId)
        art?.clearHost(hostId)
        publish()
    }

    /** Removes a host from the saved list entirely. */
    suspend fun forget(hostId: String) = withContext(io) {
        val details = known.remove(hostId) ?: return@withContext
        store.delete(details)
        nova.remove(hostId)
        novaApps.remove(hostId)
        novaByAppId.remove(hostId)
        art?.clearHost(hostId)
        publish()
    }

    fun pair(hostId: String, pin: String? = null): Flow<PairingState> = flow {
        val details = known[hostId]
        if (details == null) {
            emit(PairingState.Failed(PairingFailure.UNKNOWN_HOST))
            return@flow
        }
        if (!pairing.add(hostId)) {
            emit(PairingState.Failed(PairingFailure.ALREADY_IN_PROGRESS))
            return@flow
        }
        try {
            if (details.activeAddress == null) {
                val fresh = backend.poll(details)
                if (fresh == null) {
                    emit(PairingState.Failed(PairingFailure.UNREACHABLE))
                    return@flow
                }
                synchronized(details) { details.update(fresh) }
            }
            if (backend.isPaired(details) && details.serverCert != null) {
                markPaired(details, null, null)
                emit(PairingState.Paired)
                return@flow
            }
            val code = pin ?: backend.generatePin()
            emit(PairingState.GeneratingPin(code))
            emit(PairingState.WaitingForHost)
            val outcome = backend.pair(details, code)
            emit(
                when (outcome.state) {
                    PairState.PAIRED -> {
                        markPaired(details, outcome.serverCert, outcome.pairName)
                        PairingState.Paired
                    }
                    PairState.PIN_WRONG -> PairingState.Failed(PairingFailure.WRONG_PIN)
                    PairState.ALREADY_IN_PROGRESS -> PairingState.Failed(PairingFailure.ALREADY_IN_PROGRESS)
                    PairState.FAILED, PairState.NOT_PAIRED -> PairingState.Failed(
                        if (details.runningGameId != 0) PairingFailure.HOST_BUSY else PairingFailure.FAILED,
                    )
                },
            )
        } finally {
            pairing.remove(hostId)
        }
    }.catch { e ->
        if (e is CancellationException) throw e
        emit(
            when (e) {
                is UnknownHostException -> PairingState.Failed(PairingFailure.UNKNOWN_HOST, e.message)
                is IOException -> PairingState.Failed(PairingFailure.UNREACHABLE, e.message)
                else -> PairingState.Failed(PairingFailure.FAILED, e.message)
            },
        )
    }.flowOn(io)

    private fun markPaired(details: ComputerDetails, cert: java.security.cert.X509Certificate?, pairName: String?) {
        val uuid = details.uuid ?: return
        synchronized(details) {
            if (cert != null) details.serverCert = cert
            details.pairState = PairState.PAIRED
        }
        store.save(details)
        if (!pairName.isNullOrEmpty()) store.savePairName(uuid, pairName)
        nova.remove(uuid)
        probeNova(uuid, details) // the client-cert channel works now
        publish()
    }

    /** Emits the host's app list now and after every [appPollIntervalMs] while collected. */
    fun apps(hostId: String): Flow<List<HostApp>> = flow {
        while (currentCoroutineContext().isActive) {
            loadApps(hostId)?.let { emit(it) }
            delay(appPollIntervalMs)
        }
    }.distinctUntilChanged().flowOn(io)

    /** One app-list fetch; null when the host is unknown or the fetch failed. */
    suspend fun loadApps(hostId: String): List<HostApp>? = withContext(io) {
        val details = known[hostId] ?: return@withContext null
        try {
            val gameStream = backend.appList(details)
            val novaList = if (nova[hostId] != null) fetchNovaApps(hostId, details) else emptyList()
            val merged = mergeApps(gameStream, novaList, details.runningGameId)
            val novaById = novaList.associateBy { it.id }
            novaByAppId[hostId] = merged.mapNotNull { app -> app.novaId?.let { novaById[it] }?.let { app.id to it } }.toMap()
            merged
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LimeLog.warning("App list failed for ${details.name}: ${e.message}")
            null
        }
    }

    suspend fun appDetails(hostId: String, appId: String): AppDetails? = withContext(io) {
        val details = known[hostId] ?: return@withContext null
        val novaApp = novaAppFor(hostId, details, appId) ?: return@withContext null
        try {
            backend.novaJson(details, NovaApi.details(novaApp.id))?.let(NovaApi::parseDetails)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LimeLog.info("Nova details unavailable: ${e.message}")
            null
        }
    }

    /**
     * Artwork bytes (PNG/JPEG) for an app: Nova art when the host has it, otherwise GameStream box
     * art for [ArtKind.POSTER]. Cached on disk.
     */
    suspend fun loadArt(hostId: String, appId: String, kind: ArtKind): ByteArray? = withContext(io) {
        val key = ArtCache.key(hostId, "art", appId, kind.wire)
        art?.get(key)?.let { return@withContext it }
        val details = known[hostId] ?: return@withContext null
        val bytes = try {
            val novaApp = novaAppFor(hostId, details, appId)
            val fromNova = if (novaApp != null && kind in novaApp.art) {
                backend.novaBytes(details, NovaApi.art(novaApp.id, kind))
            } else {
                null
            }
            fromNova ?: if (kind == ArtKind.POSTER) {
                backend.boxArt(details, NvApp("", appId.toIntOrNull() ?: return@withContext null, false))
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        bytes?.also { art?.put(key, it) }
    }

    suspend fun loadScreenshot(hostId: String, path: String): ByteArray? = withContext(io) {
        val key = ArtCache.key(hostId, "shot", path)
        art?.get(key)?.let { return@withContext it }
        val details = known[hostId] ?: return@withContext null
        val bytes = try {
            backend.novaBytes(details, NovaApi.screenshot(path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        bytes?.also { art?.put(key, it) }
    }

    private fun novaAppFor(hostId: String, details: ComputerDetails, appId: String): NovaApp? {
        if (nova[hostId] == null) return null
        novaByAppId[hostId]?.get(appId)?.let { return it }
        val list = novaApps[hostId] ?: fetchNovaApps(hostId, details)
        val id = appId.toIntOrNull() ?: return null
        return list.firstOrNull { it.gameStreamId == id }
    }

    private fun fetchNovaApps(hostId: String, details: ComputerDetails): List<NovaApp> {
        val list = try {
            backend.novaJson(details, NovaApi.APPS)?.let(NovaApi::parseApps).orEmpty()
        } catch (e: Exception) {
            LimeLog.info("Nova app list unavailable: ${e.message}")
            return novaApps[hostId].orEmpty()
        }
        novaApps[hostId] = list
        return list
    }

    private fun probeNova(hostId: String, details: ComputerDetails) {
        if (nova.containsKey(hostId) || details.serverCert == null) return
        try {
            nova[hostId] = backend.novaJson(details, NovaApi.CAPABILITIES)?.let(NovaApi::parseCapabilities)
        } catch (e: Exception) {
            // Transient (timeout, TLS hiccup): probe again on the next poll.
            LimeLog.info("Nova capability probe failed for ${details.name}: ${e.message}")
        }
    }

    private fun currentHost(hostId: String): Host? = known[hostId]?.let { d -> synchronized(d) { d.toHost(nova[hostId]) } }

    private fun publish() {
        _hosts.value = known.keys.mapNotNull(::currentHost).sortedBy { it.name.lowercase() }
    }

    companion object {
        /**
         * Joins the GameStream app list with Nova metadata. Nova entries are matched by the
         * GameStream id they report, then by name.
         */
        fun mergeApps(gameStream: List<NvApp>, novaList: List<NovaApp>, runningGameId: Int): List<HostApp> {
            val byId = novaList.filter { it.gameStreamId != null }.associateBy { it.gameStreamId }
            val byName = novaList.associateBy { it.name.lowercase() }
            return gameStream.map { app ->
                val match = byId[app.appId] ?: byName[app.appName.lowercase()]
                HostApp(
                    id = app.appId.toString(),
                    name = app.appName,
                    running = app.appId == runningGameId || match?.running == true,
                    novaId = match?.id,
                    availableArt = match?.art ?: setOf(ArtKind.POSTER),
                    hdrSupported = app.hdrSupported,
                    lastPlayed = match?.lastPlayed,
                    playtimeSeconds = match?.playtimeSeconds,
                    modeDefault = match?.modeDefault,
                    commands = NovaApi.parseSuperCmds(app.cmdList?.toString(), match?.id),
                )
            }
        }
    }
}
