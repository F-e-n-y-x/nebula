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
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.domain.model.LastSession
import io.github.f_e_n_y_x.nebula.domain.model.PairingState
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
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
            Host(HOST_ID, "atom", "192.168.10.10", HostStatus.ONLINE, paired = true, isNova = true, gpu = "GeForce GTX 1080 Ti", version = "Nova 0.2"),
            Host("demo-deck", "living-room", "192.168.10.24", HostStatus.OFFLINE, paired = false, isNova = false, gpu = null, version = "Sunshine"),
        ),
    )

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
        override suspend fun wake(hostId: String): Result<Unit> {
            delay(1_500)
            hosts.update { list -> list.map { if (it.id == hostId) it.copy(status = HostStatus.ONLINE) else it } }
            return Result.success(Unit)
        }
    }

    val libraryRepository = object : LibraryRepository {
        override fun observeGames(hostId: String): Flow<List<Game>> =
            hosts.map { if (hostId == HOST_ID) entries.map { it.game } else emptyList() }
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

    val streamRepository = object : StreamRepository {
        override fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState> = flow {
            emit(StreamState.Starting)
            delay(1_200)
            val res = if (mode == DisplayMode.MIRROR) "1920×1080" else "2340×1080"
            var t = 0
            while (true) {
                val (w, h) = if (mode == DisplayMode.MIRROR) 1920 to 1080 else 2340 to 1080
                emit(
                    StreamState.Live(
                        StreamStats(
                            res, if (mode == DisplayMode.MIRROR) 60 else 120, 28f + (t % 5), 5.6f + (t % 3) * 0.3f, "HEVC",
                            width = w, height = h, receivedFps = if (mode == DisplayMode.MIRROR) 60f else 120f, lossPercent = 0.1f * (t % 2),
                            hostMs = 1.8f, networkMs = 2.1f + (t % 3) * 0.3f, decodeMs = 1.2f, renderMs = 0.5f, decoder = "c2.android.hevc.decoder",
                            post = demoPost(t),
                        ),
                    ),
                )
                t++
                delay(1_000)
            }
        }
        override fun stop(quitApp: Boolean) = Unit

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
            return true
        }
    }

    companion object {
        const val HOST_ID = "demo-atom"
    }
}
