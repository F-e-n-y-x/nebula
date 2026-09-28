package io.github.f_e_n_y_x.nebula.domain.model

/** A streaming host (Nova, Sunshine or GameStream) seen on the network or added by hand. */
data class Host(
    val id: String,
    val name: String,
    val address: String,
    val status: HostStatus,
    val paired: Boolean,
    val isNova: Boolean,
    val gpu: String? = null,
    val version: String? = null,
    val runningGameId: String? = null,
)

enum class HostStatus { ONLINE, STREAMING, OFFLINE, UNKNOWN }

enum class GameKind { GAME, DESKTOP, APP }

/** Artwork references (URLs or asset URIs); any may be missing. */
data class GameArt(
    val poster: String? = null,
    val hero: String? = null,
    val logo: String? = null,
    val icon: String? = null,
    val header: String? = null,
)

data class Game(
    val id: String,
    val hostId: String,
    val name: String,
    val kind: GameKind,
    val art: GameArt,
    val running: Boolean = false,
    val lastPlayedEpochS: Long? = null,
    val playtimeS: Long = 0,
    val genres: List<String> = emptyList(),
    val developer: String? = null,
    val releaseYear: String? = null,
    val hostDefaultMode: DisplayMode? = null,
)

data class LastSession(val device: String, val resolution: String, val fps: Int, val codec: String)

data class GameDetails(
    val gameId: String,
    val description: String?,
    val genres: List<String>,
    val developer: String?,
    val publisher: String?,
    val releaseDate: String?,
    val metacritic: Int?,
    val screenshots: List<String>,
    val lastSession: LastSession?,
)

/** Where the game runs on the host: its own virtual screen at this device's resolution, or the real desktop. */
enum class DisplayMode { VIRTUAL, MIRROR }

enum class VideoCodec { AUTO, HEVC, H264, AV1 }

data class Resolution(val width: Int, val height: Int) {
    override fun toString() = "${width}×$height"

    companion object {
        /** 0×0 means "match this device's screen". */
        val Native = Resolution(0, 0)
    }
}

data class StreamSettings(
    val resolution: Resolution = Resolution.Native,
    val fps: Int = 60,
    val bitrateKbps: Int = 30_000,
    val codec: VideoCodec = VideoCodec.AUTO,
    val defaultMode: DisplayMode = DisplayMode.VIRTUAL,
)

/** How the library shows games, and how much artwork it keeps. */
data class LibraryOptions(
    val showDetails: Boolean = true,
    val showPlaytime: Boolean = true,
    /** On metered networks, skip hero art and load screenshots only on request. */
    val dataSaver: Boolean = false,
    val cacheLimitMb: Int = 512,
)

sealed interface PairingState {
    data object Connecting : PairingState
    /** Enter [pin] on the host (Nova web UI → Pair a device). */
    data class ShowPin(val pin: String) : PairingState
    data object Paired : PairingState
    data class Failed(val reason: String) : PairingState
}

sealed interface StreamState {
    data object Starting : StreamState
    data class Live(val stats: StreamStats) : StreamState
    data class Ended(val reason: String?) : StreamState
    data class Failed(val reason: String) : StreamState
}

data class StreamStats(
    val resolution: String,
    val fps: Int,
    val bitrateMbps: Float,
    val latencyMs: Float,
    val codec: String,
    val width: Int = 0,
    val height: Int = 0,
    val receivedFps: Float = 0f,
    val lossPercent: Float = 0f,
    val hostMs: Float = 0f,
    val networkMs: Float = 0f,
    val decodeMs: Float = 0f,
    val renderMs: Float = 0f,
    val decoder: String = "",
    val hdr: Boolean = false,
    /** Frame generation and upscaling; null when the stream doesn't report them (demo, V+). */
    val post: PostProcessStats? = null,
)
