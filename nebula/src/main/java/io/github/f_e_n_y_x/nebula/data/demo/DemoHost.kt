package io.github.f_e_n_y_x.nebula.data.demo

import android.content.Context
import io.github.f_e_n_y_x.nebula.domain.ArtworkRepository
import io.github.f_e_n_y_x.nebula.domain.HostRepository
import io.github.f_e_n_y_x.nebula.domain.LibraryRepository
import io.github.f_e_n_y_x.nebula.domain.StreamRepository
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameArt
import io.github.f_e_n_y_x.nebula.domain.model.GameDetails
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardMode
import io.github.f_e_n_y_x.nebula.domain.model.ClipboardSendResult
import io.github.f_e_n_y_x.nebula.domain.model.Gate
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostCommand
import io.github.f_e_n_y_x.nebula.domain.model.HostCommands
import io.github.f_e_n_y_x.nebula.domain.model.HostFeatures
import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.LastSession
import io.github.f_e_n_y_x.nebula.domain.model.PairingAs
import io.github.fenyx.nebula.engine.PairingName
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.RunningGame
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.domain.model.AbrInfo
import io.github.f_e_n_y_x.nebula.domain.model.AbrSource
import io.github.f_e_n_y_x.nebula.domain.model.ConnectionReport
import io.github.f_e_n_y_x.nebula.data.engine.toDomain
import io.github.fenyx.nebula.engine.AbrMode
import io.github.fenyx.nebula.engine.AbrSettings
import io.github.fenyx.nebula.engine.ConnectionAdvisor
import io.github.fenyx.nebula.engine.DisplaySpec
import com.limelight.nvstream.http.AdaptiveBitrateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Debug-only demo host ("atom") backed by fixtures in assets/demo (real public Steam artwork and
 * store details for the owner's library). Release builds ship no demo assets, so [isAvailable] is false.
 */
class DemoHost(private val context: Context) {
    private val base = "file:///android_asset/demo/"

    val isAvailable: Boolean = runCatching { context.assets.list("demo")?.contains("games.json") == true }.getOrDefault(false)

    private val entries: List<Entry> by lazy { load() }

    private data class Entry(val game: Game, val details: GameDetails)

    private val now = System.currentTimeMillis() / 1000

    private fun load(): List<Entry> {
        val json = context.assets.open("demo/games.json").bufferedReader().readText()
        val arr = org.json.JSONArray(json)
        val played = mapOf(
            "gta5" to (2 * 86_400L to 151_200L), "wukong" to (5 * 3_600L to 64_800L),
            "spiderman2" to (4 * 86_400L to 43_200L), "farcry5" to (21 * 86_400L to 30_600L),
        )
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val key = o.getString("key")
            val art = o.getJSONObject("art")
            fun a(k: String) = art.optString(k).takeIf { it.isNotBlank() }?.let { base + it }
            val genres = o.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.getString(it) } } ?: emptyList()
            val release = o.optString("release")
            val (ago, playtime) = played[key] ?: (null to 0L)
            val game = Game(
                id = key, hostId = HOST_ID, name = o.getString("name"), kind = GameKind.GAME,
                art = GameArt(poster = a("poster"), hero = a("hero"), logo = a("logo"), header = a("header")),
                lastPlayedEpochS = ago?.let { now - it }, playtimeS = playtime, genres = genres,
                developer = o.optString("developer").takeIf { it.isNotBlank() },
                releaseYear = Regex("(19|20)\\d{2}").find(release)?.value,
            )
            val shots = o.optJSONArray("screenshots")?.let { s -> (0 until s.length()).map { base + s.getString(it) } } ?: emptyList()
            val details = GameDetails(
                gameId = key, description = o.optString("description").takeIf { it.isNotBlank() }, genres = genres,
                developer = game.developer, publisher = o.optString("publisher").takeIf { it.isNotBlank() },
                releaseDate = release.takeIf { it.isNotBlank() },
                metacritic = if (o.isNull("metacritic")) null else o.optInt("metacritic").takeIf { it > 0 },
                screenshots = shots,
                lastSession = if (key == "gta5" || key == "wukong") LastSession("S25 Ultra", "2340×1080", 120, "HEVC") else null,
            )
            Entry(game, details)
        }
        val desktops = listOf(
            Game("desktop_virtual", HOST_ID, "Desktop (Virtual display)", GameKind.DESKTOP, GameArt(), lastPlayedEpochS = now - 3 * 3_600L),
            Game("desktop_mirror", HOST_ID, "Desktop (Mirror)", GameKind.DESKTOP, GameArt()),
        ).map { Entry(it, GameDetails(it.id, "Stream this PC's desktop.", emptyList(), null, null, null, null, emptyList(), null)) }
        return list + desktops
    }

    private val hosts = MutableStateFlow(
        listOf(
            Host(
                HOST_ID, "atom", "192.168.10.10", HostStatus.ONLINE, paired = true, isNova = true, gpu = "GeForce GTX 1080 Ti", version = "Nova 0.3",
                features = HostFeatures(sleep = Gate.AVAILABLE, commands = Gate.AVAILABLE), canWake = true,
            ),
            Host("demo-deck", "living-room", "192.168.10.24", HostStatus.OFFLINE, paired = false, isNova = false, gpu = null, version = "Sunshine"),
        ),
    )

    /** Debug QA only: every demo PC starts unpaired, like a fresh install (`--es start firstrun`). */
    fun forgetPairings() = hosts.update { list -> list.map { it.copy(paired = false) } }

    val hostRepository = object : HostRepository {
        override fun observeHosts(): Flow<List<Host>> = hosts
        override suspend fun discover() { delay(900) }
        override suspend fun addManual(address: String): Result<Host> {
            val h = Host("manual-$address", address, address, HostStatus.UNKNOWN, paired = false, isNova = false)
            hosts.update { it + h }
            return Result.success(h)
        }
        override fun pair(hostId: String): Flow<PairingState> = flow {
            emit(PairingState.Connecting)
            delay(700)
            emit(PairingState.ShowPin("4827"))
            delay(6_000)
            hosts.update { list -> list.map { if (it.id == hostId) it.copy(paired = true, status = HostStatus.ONLINE) else it } }
            emit(PairingState.Paired)
        }
        private var demoDevice = "Ayush's S25 Ultra"
        override fun pairingAs() = PairingAs(PairingName.display(PairingName.APP, demoDevice), demoDevice)
        override fun setPairingDeviceName(name: String) {
            demoDevice = PairingName.clean(name).ifEmpty { "Ayush's S25 Ultra" }
        }
        /** A made-up but plausible Wi-Fi 6 result, with the same suggestion rules as a real test. */
        override suspend fun testConnection(hostId: String, onProgress: (Float) -> Unit): Result<ConnectionReport> {
            delay(400)
            for (i in 1..10) {
                onProgress(i / 10f)
                delay(120)
            }
            val (w, h) = io.github.f_e_n_y_x.nebula.ui.screens.deviceResolution(context)
            return Result.success(
                ConnectionAdvisor.result(6.4, 1.1, 0.2, 182.0, null, DisplaySpec(w, h, io.github.f_e_n_y_x.nebula.ui.screens.deviceMaxFps(context)), duringStream = false).toDomain(),
            )
        }
        override suspend fun wake(hostId: String): Result<Unit> {
            // A sleeping demo PC "boots" a few seconds after the first magic packet.
            if (wakeAt == 0L) wakeAt = System.currentTimeMillis() + WAKE_DELAY_MS
            return Result.success(Unit)
        }
        override suspend fun refresh(hostId: String): Host? {
            delay(300)
            if (wakeAt != 0L && System.currentTimeMillis() >= wakeAt) {
                wakeAt = 0L
                hosts.update { list -> list.map { if (it.id == hostId) it.copy(status = HostStatus.ONLINE) else it } }
            }
            return hosts.value.firstOrNull { it.id == hostId }
        }
        override suspend fun sleep(hostId: String): Result<Unit> {
            delay(700)
            hosts.update { list -> list.map { if (it.id == hostId) it.copy(status = HostStatus.OFFLINE) else it } }
            return Result.success(Unit)
        }
        override suspend fun commands(hostId: String, gameId: String?): HostCommands {
            delay(250)
            val host = hosts.value.firstOrNull { it.id == hostId }
            val app = if (gameId == "gta5") gtaCommands else emptyList()
            return io.github.f_e_n_y_x.nebula.domain.HostGating.visibleCommands(host, globalCommands, app)
        }
        override suspend fun running(hostId: String): Result<RunningGame?> {
            delay(250)
            val h = hosts.value.firstOrNull { it.id == hostId } ?: return Result.failure(IllegalStateException("This PC is no longer in the list."))
            if (h.status == HostStatus.OFFLINE) return Result.failure(IllegalStateException("Couldn't reach ${h.name}."))
            return Result.success(if (hostId == HOST_ID) running.value else null)
        }
        override suspend fun quitApp(hostId: String): Result<Unit> {
            delay(700)
            if (hosts.value.firstOrNull { it.id == hostId }?.status == HostStatus.OFFLINE) {
                return Result.failure(io.github.f_e_n_y_x.nebula.domain.QuitFailure("Couldn't reach the PC. It may be asleep or off.", unreachable = true))
            }
            if (hostId == HOST_ID) running.value = null
            return Result.success(Unit)
        }
        override suspend fun runCommand(hostId: String, commandId: String): Result<Unit> {
            delay(600)
            return if (commandId == "kill-game") Result.failure(IllegalStateException("That command is already running.")) else Result.success(Unit)
        }
    }

    private var wakeAt = 0L

    /**
     * What the demo PC runs: GTA V on a virtual display since 1 h 20 m ago, like a Nova host with
     * /nova/v1/running. Streams set it; Quit and Stop clear it.
     */
    private val running = MutableStateFlow<RunningGame?>(
        RunningGame("gta5", "Grand Theft Auto V", sinceEpochS = now - 80 * 60, display = DisplayMode.VIRTUAL, connectedClients = 0),
    )

    /** Debug QA: nothing running on the demo PC. */
    fun stopRunning() { running.value = null }

    /** Debug QA: start with atom asleep so Play shows the wake flow. */
    fun putToSleep() {
        hosts.update { list -> list.map { if (it.id == HOST_ID) it.copy(status = HostStatus.OFFLINE) else it } }
    }

    private val globalCommands = listOf(
        HostCommand("restart-steam", "Restart Steam", confirm = true, icon = "refresh"),
        HostCommand("mute-discord", "Mute Discord", confirm = false, icon = "mic-off"),
        HostCommand("lock", "Lock screen", confirm = true, icon = "lock"),
    )
    private val gtaCommands = listOf(
        HostCommand("reset-graphics", "Reset graphics settings", confirm = true, appScoped = true, icon = "settings"),
        HostCommand("kill-game", "Force close GTA V", confirm = true, appScoped = true, icon = "stop"),
    )

    val libraryRepository = object : LibraryRepository {
        override fun observeGames(hostId: String): Flow<List<Game>> =
            kotlinx.coroutines.flow.combine(hosts, running) { _, r ->
                if (hostId == HOST_ID) entries.map { it.game.copy(running = it.game.id == r?.gameId) } else emptyList()
            }
        override suspend fun details(hostId: String, gameId: String): GameDetails? = entries.firstOrNull { it.game.id == gameId }?.details
    }

    /** The demo "cache" is the bundled art: usage is its real size; clearing and refreshing are no-ops. */
    val artworkRepository = object : ArtworkRepository {
        override suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
            context.assets.list("demo")?.sumOf { name -> runCatching { context.assets.openFd("demo/$name").use { it.length } }.getOrDefault(0L) } ?: 0L
        }
        override fun setLimit(bytes: Long) = Unit
        override suspend fun clear() = Unit
        override suspend fun refreshHost(hostId: String) = delay(600)
        override suspend fun refreshGame(hostId: String, gameId: String) = delay(400)
    }

    private val link = MutableStateFlow(StreamLink())

    /**
     * The demo stream reports the size it was asked for, and changes it live like a Nova host: a
     * switch takes about a second and a half; sizes wider than 4K "fail" so the rollback path can be
     * seen without a PC.
     */
    val streamRepository = object : StreamRepository {
        private val demoMode = MutableStateFlow<VideoMode?>(null)
        private val paused = MutableStateFlow(false)
        private val bitrateKbps = MutableStateFlow(0)

        /** Adaptive bitrate as the demo PC would report it: the host steers, with the saved mode. */
        private fun demoAbr(): AbrInfo? {
            val a = AbrSettings.read(io.github.f_e_n_y_x.nebula.settings.LegacyPrefs(context).prefs)
            if (a.mode == AbrMode.OFF) return null
            val (lo, hi) = AdaptiveBitrateService.resolveRange(a.mode.wire!!, bitrateKbps.value, a.minKbps, a.maxKbps)
            return AbrInfo(a.mode.name.lowercase().replaceFirstChar { it.uppercase() }, AbrSource.HOST, lo, hi, "stable, probe")
        }

        override val link: Flow<StreamLink> = this@DemoHost.link
        override fun setMicLive(on: Boolean): Boolean {
            this@DemoHost.link.update { it.copy(micLive = on) }
            return true
        }
        override fun sendClipboard(): ClipboardSendResult {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            return if (cm?.hasPrimaryClip() == true) ClipboardSendResult.SENT else ClipboardSendResult.EMPTY
        }
        override fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState> = flow {
            emit(StreamState.Starting)
            // Launching another game replaces the running one; resuming keeps its start time.
            if (running.value?.gameId != game.id) {
                running.value = RunningGame(game.id, game.name, System.currentTimeMillis() / 1000, mode, 1)
            }
            val r = settings.resolution
            demoMode.value = VideoMode(r.width.takeIf { it > 0 } ?: 2340, r.height.takeIf { it > 0 } ?: 1080, settings.fps)
            paused.value = false
            this@DemoHost.link.value = StreamLink(micEnabled = true)
            bitrateKbps.value = settings.bitrateKbps
            delay(1_200)
            this@DemoHost.link.value = StreamLink(micEnabled = true, micSupported = true, clipboard = ClipboardMode.SYNCING)
            var t = 0
            while (true) {
                if (!paused.value) {
                    val m = demoMode.value ?: break
                    emit(
                        StreamState.Live(
                            StreamStats(
                                "${m.width}×${m.height}", m.fps, 28f + (t % 5), 5.6f + (t % 3) * 0.3f, "HEVC",
                                width = m.width, height = m.height, receivedFps = m.fps.toFloat(),
                                hostFps = m.fps.toFloat(), onePercentLowFps = m.fps * 0.9f - (t % 4), jitterMs = 0.4f + (t % 3) * 0.3f, lossPercent = 0.1f * (t % 2),
                                hostMs = 1.8f, networkMs = 2.1f + (t % 3) * 0.3f, decodeMs = 1.2f, renderMs = 0.5f, decoder = "c2.android.hevc.decoder",
                                post = demoPost(t),
                                targetBitrateKbps = bitrateKbps.value, abr = demoAbr(),
                            ),
                        ),
                    )
                    t++
                }
                delay(if (paused.value) 50 else 1_000)
            }
        }

        override suspend fun switchMode(mode: VideoMode): Result<Unit> {
            paused.value = true
            delay(700) // disconnect
            if (mode.width > 3840) {
                delay(600)
                return Result.failure(IllegalStateException("The PC's encoder doesn't support ${mode.width}×${mode.height}."))
            }
            // What a Nova host would get: /resume?mode=WxHxFPS&nova_orientation=portrait|landscape.
            android.util.Log.i(io.github.f_e_n_y_x.nebula.input.LoggingInput.TAG, "resume mode=${mode.encode()} nova_orientation=${if (mode.height > mode.width) "portrait" else "landscape"}")
            delay(800) // /resume, RTSP and the first frame
            demoMode.value = mode
            paused.value = false
            return Result.success(Unit)
        }

        override fun stop(quitApp: Boolean) {
            this@DemoHost.link.value = StreamLink()
            if (quitApp) running.value = null
        }

        /** Demo only: frame generation as it would look at 60→120 when on in Settings. */
        @Volatile private var fgPaused = false
        override fun setFramegenPaused(paused: Boolean, force: Boolean): Boolean { fgPaused = paused; return true }

        private fun demoPost(t: Int): io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats? {
            val all = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE).all
            val fg = io.github.fenyx.nebula.engine.framegen.FramegenConfig.from(all)
            val up = io.github.fenyx.nebula.engine.framegen.UpscalerConfig.from(all)
            if (!fg.enabled && up.mode == io.github.fenyx.nebula.engine.framegen.UpscalerMode.OFF) return null
            val lsfg = 5 + t % 3
            return io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats(
                framegen = when {
                    !fg.enabled -> io.github.f_e_n_y_x.nebula.domain.model.FramegenState.OFF
                    fgPaused -> io.github.f_e_n_y_x.nebula.domain.model.FramegenState.PAUSED
                    else -> io.github.f_e_n_y_x.nebula.domain.model.FramegenState.ACTIVE
                },
                presentedFps = if (fg.enabled && !fgPaused) 117.6f + (t % 3) * 0.8f else 0f,
                inputFps = 60f, targetFps = 120, multiplier = 2, lsfgMs = lsfg,
                addedLatencyMs = 8.3f + lsfg + 1f, addedLatencyFrames = (8.3f + lsfg + 1f) / 16.7f,
                model = (if (fg.performanceMode) "LSFG 3.1 performance" else "LSFG 3.1") + " · flow ${fg.flowScalePercent}%",
                soak = "%d:%02d · 99%% ≥115 fps · min 113".format(t / 60, t % 60),
                upscaler = if (fg.enabled) "off" else up.mode.id,
                upscalerLabel = up.mode.label, upscaleMs = 1.1f, upscaleOut = "1920×1080 → 2340×1080",
            )
        }
        private val log = io.github.f_e_n_y_x.nebula.input.LoggingInput()
        override val remoteInput: io.github.f_e_n_y_x.nebula.input.RemoteInput get() = log
        override suspend fun setBitrate(kbps: Int): Boolean {
            delay(300)
            android.util.Log.i(io.github.f_e_n_y_x.nebula.input.LoggingInput.TAG, "bitrate $kbps")
            bitrateKbps.value = kbps
            return true
        }
        override suspend fun setDisplayScale(percent: Int): Boolean {
            delay(300)
            android.util.Log.i(io.github.f_e_n_y_x.nebula.input.LoggingInput.TAG, "display scale $percent%")
            return true
        }

        override suspend fun testConnection(): Result<ConnectionReport> {
            delay(700)
            val (w, h) = io.github.f_e_n_y_x.nebula.ui.screens.deviceResolution(context)
            val display = DisplaySpec(w, h, io.github.f_e_n_y_x.nebula.ui.screens.deviceMaxFps(context))
            return Result.success(ConnectionAdvisor.result(7.1, 1.6, 0.1, null, bitrateKbps.value, display, duringStream = true).toDomain())
        }
    }

    companion object {
        const val HOST_ID = "demo-atom"
        private const val WAKE_DELAY_MS = 7_000L
    }
}
