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
    /** Host-linked actions this host offers this device (see [io.github.f_e_n_y_x.nebula.domain.HostGating]). */
    val features: HostFeatures = HostFeatures.None,
    /** The host shared a MAC address, so Wake-on-LAN can reach it. */
    val canWake: Boolean = false,
    /** Nova's advertised /nova/v1 features ("motion", "rumble", …); null when unknown (not Nova). */
    val novaFeatures: Set<String>? = null,
) {
    /** True unless a Nova host says it lacks [feature]; other hosts are assumed capable. */
    fun supports(feature: String): Boolean = novaFeatures?.contains(feature) ?: true
    /** True only when the host lists [feature] (opt-in features stay hidden on non-Nova hosts). */
    fun advertises(feature: String): Boolean = novaFeatures?.contains(feature) == true
}

/**
 * What a host says it is running (Nova /nova/v1/running, or serverinfo `currentgame` on other hosts,
 * where only [gameId] is known and [sinceEpochS] is null).
 */
data class RunningGame(
    /** The library id ([Game.id]) when known. */
    val gameId: String?,
    val name: String? = null,
    val sinceEpochS: Long? = null,
    val display: DisplayMode? = null,
    val connectedClients: Int? = null,
    /** False when the PC started something it can't follow (a launcher that handed off): it runs until quit. */
    val tracked: Boolean = true,
)

/** Whether a host-linked action is offered. */
enum class Gate {
    /** The host doesn't implement it (or has it turned off): hide the action. */
    UNSUPPORTED,
    /** The host has it, but not for this device: show it disabled with how to allow it. */
    NOT_ALLOWED,
    AVAILABLE,
}

/** What a host lets this device do beyond streaming (see [io.github.f_e_n_y_x.nebula.domain.HostGating]). */
data class HostFeatures(
    val sleep: Gate = Gate.UNSUPPORTED,
    val commands: Gate = Gate.UNSUPPORTED,
) {
    companion object {
        val None = HostFeatures()
    }
}

/** A command the host's owner set up (restart Steam, mute Discord…). App-scoped ones have [appScoped]. */
data class HostCommand(
    val id: String,
    val name: String,
    val confirm: Boolean = true,
    val appScoped: Boolean = false,
    /** Nova's icon name (terminal, refresh, power, lock, volume, mic-off, monitor, gamepad, stop, play, folder, settings). */
    val icon: String? = null,
    /** App commands run only while their game does. */
    val runnable: Boolean = true,
    val running: Boolean = false,
)

/** A host's commands for one screen, and whether this device may run them. */
data class HostCommands(
    val commands: List<HostCommand>,
    /** The host has commands but this device lacks the `host_commands` permission. */
    val notAllowed: Boolean = false,
) {
    companion object {
        val None = HostCommands(emptyList())
    }
}

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
    /** "Portrait streaming": Off, or follow the device's rotation (the PC gets a portrait mode). */
    val portraitStreaming: io.github.f_e_n_y_x.nebula.domain.PortraitStreaming = io.github.f_e_n_y_x.nebula.domain.PortraitStreaming.OFF,
)

/** How the library shows games, and how much artwork it keeps. */
data class LibraryOptions(
    val showDetails: Boolean = true,
    val showPlaytime: Boolean = true,
    /** On metered networks, skip hero art and load screenshots only on request. */
    val dataSaver: Boolean = false,
    val cacheLimitMb: Int = 512,
)

/**
 * The name this device pairs under. [displayName] is what the host pre-fills ("Nebula from
 * Ayush's S25 Ultra"); [deviceName] is the editable device part.
 */
data class PairingAs(val displayName: String, val deviceName: String)

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
    /** Frames the host sent per second (received plus lost). */
    val hostFps: Float = 0f,
    val onePercentLowFps: Float = 0f,
    val jitterMs: Float = 0f,
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

/** Clipboard sync for the running stream. */
enum class ClipboardMode {
    /** Both clipboard settings are off. */
    OFF,
    /** On in Settings, but this PC doesn't sync the clipboard. */
    UNSUPPORTED,
    /** The PC has clipboard sync off for this device. */
    NOT_ALLOWED,
    SYNCING,
}

enum class ClipboardSendResult { SENT, EMPTY, NOT_SYNCING }

/** Mic and clipboard state of the running stream (engine [StreamHostLink] or the demo). */
data class StreamLink(
    /** "Enable microphone" is on in Settings. */
    val micEnabled: Boolean = false,
    /** Null until connected; false when the host didn't ask for a microphone. */
    val micSupported: Boolean? = null,
    val micLive: Boolean = false,
    val micPaused: Boolean = false,
    /** What "Initial microphone state" asks for at start. */
    val micWantedAtStart: Boolean = false,
    val clipboard: ClipboardMode = ClipboardMode.OFF,
)
