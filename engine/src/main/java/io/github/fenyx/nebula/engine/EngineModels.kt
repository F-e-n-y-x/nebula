package io.github.fenyx.nebula.engine

/** Reachability of a host as last seen by the engine's poller. */
enum class HostState { ONLINE, OFFLINE, UNKNOWN }

/** How the engine learned about an address. */
enum class AddressKind { LOCAL, REMOTE, MANUAL, IPV6 }

/** One way to reach a host. */
data class HostAddress(val address: String, val port: Int, val kind: AddressKind)

/**
 * Capabilities a Nova host advertises on GET /nova/v1/capabilities. [permissions] is what this
 * client may do on the host ("power", "host_commands", ...); null when the host doesn't say, in
 * which case actions are offered and a refusal is reported when it happens.
 */
data class NovaCapabilities(
    val version: String,
    val features: Set<String>,
    val permissions: Set<String>? = null,
) {
    /** True when the host implements the named /nova/v1 feature (e.g. "apps", "art", "details"). */
    fun has(feature: String): Boolean = feature in features

    /** False only when the host listed this client's permissions and [permission] isn't among them. */
    fun allows(permission: String): Boolean = permissions?.contains(permission) ?: true
}

/** Feature and permission names used in [NovaCapabilities]. */
object NovaFeature {
    /** GET /pcsleep suspends the host. */
    const val PC_SLEEP = "pcsleep"
    /** GET /nova/v1/commands lists host commands; GET /supercmd?cmdId= runs one. */
    const val COMMANDS = "commands"
    const val SUPER_CMD = "supercmd"
    const val WOL = "wol"
    const val MIC = "mic"
    const val CLIPBOARD = "clipboard"
    const val PERMISSION_POWER = "power"
    const val PERMISSION_CLIPBOARD = "clipboard"
    const val PERMISSION_COMMANDS = "host_commands"
}

/**
 * A named command the host's owner defined (Foundation "super command"). [appNovaId] is null for
 * host-wide commands; per-app ones come from that app's /applist `SuperCmds`.
 */
data class HostCommand(
    val id: String,
    val name: String,
    /** Ask before running it. Hosts that don't say get a confirmation, to be safe. */
    val confirm: Boolean = true,
    val appNovaId: String? = null,
    /** Optional icon name from Nova's set: terminal, refresh, power, lock, volume, mic-off, monitor, gamepad, stop, play, folder, settings. */
    val icon: String? = null,
    /** Nova: app commands run only while their app does. Hosts that don't say are assumed runnable. */
    val runnable: Boolean = true,
    /** Nova: the command is running now (starting it again is refused). */
    val running: Boolean = false,
)

/**
 * GET /nova/v1/commands. [allowed] is false when this device lacks the host's `host_commands`
 * permission (the list is then empty), null when the host didn't say.
 */
data class HostCommandList(val allowed: Boolean?, val commands: List<HostCommand>) {
    companion object {
        val Empty = HostCommandList(null, emptyList())
    }
}

/** The host answered but refused the action (missing permission, busy, disabled in config). */
class HostRefusedException(message: String, val code: Int = 0) : java.io.IOException(message)

/** A saved or discovered streaming host. */
data class Host(
    /** Stable host identity (the host's GameStream UUID). */
    val id: String,
    val name: String,
    val addresses: List<HostAddress>,
    /** Address that answered the last successful poll, if any. */
    val activeAddress: HostAddress?,
    val state: HostState,
    val paired: Boolean,
    /** True once the host answered /nova/v1/capabilities. */
    val isNova: Boolean,
    val novaCapabilities: NovaCapabilities?,
    /** GameStream id of the app running on the host, or null when idle. */
    val runningAppId: Int?,
    val macAddress: String?,
)

/** Artwork variants a host may provide for an app. */
enum class ArtKind(val wire: String) { POSTER("poster"), HERO("hero"), LOGO("logo"), ICON("icon") }

/** Where a launch should run on a Nova host. */
enum class NovaDisplayMode(val wire: String) {
    VIRTUAL("virtual"),
    MIRROR("mirror");

    companion object {
        fun fromWire(value: String?): NovaDisplayMode? = entries.firstOrNull { it.wire == value }
    }
}

/** A game or app exposed by a host. */
data class HostApp(
    /** GameStream app id as a string; pass it back to [NebulaEngine.startStream] and art/details calls. */
    val id: String,
    val name: String,
    val running: Boolean,
    /** Nova's stable app id, when the host is Nova. */
    val novaId: String?,
    /** Art the host can serve for this app; [ArtKind.POSTER] is always attempted (box art fallback). */
    val availableArt: Set<ArtKind>,
    val hdrSupported: Boolean,
    /** Epoch seconds, from Nova. */
    val lastPlayed: Long?,
    val playtimeSeconds: Long?,
    val modeDefault: NovaDisplayMode?,
    /** Commands the host attached to this app (its /applist `SuperCmds`). */
    val commands: List<HostCommand> = emptyList(),
) {
    val isDesktop: Boolean get() = name.equals("Desktop", ignoreCase = true) || name.startsWith("Desktop (")
}

/** Rich metadata for an app, fetched from a Nova host. */
data class AppDetails(
    val description: String?,
    val genres: List<String>,
    val developer: String?,
    val publisher: String?,
    val releaseDate: String?,
    /** Relative Nova paths; load each with [NebulaEngine.loadScreenshot]. */
    val screenshots: List<String>,
    val metacritic: Int?,
    val lastPlayed: Long?,
    val playtimeSeconds: Long?,
    val lastSession: LastSession?,
) {
    data class LastSession(val device: String?, val resolution: String?, val fps: Int?, val codec: String?)
}

/** Progress of a pairing attempt. */
sealed interface PairingState {
    /** Enter [pin] on the host (Nova web UI → Pair a device, or Sunshine's PIN page). */
    data class GeneratingPin(val pin: String) : PairingState
    data object WaitingForHost : PairingState
    data object Paired : PairingState
    data class Failed(val reason: PairingFailure, val message: String? = null) : PairingState
}

enum class PairingFailure { WRONG_PIN, HOST_BUSY, ALREADY_IN_PROGRESS, UNREACHABLE, UNKNOWN_HOST, FAILED }

enum class CodecPreference { AUTO, H264, HEVC, AV1 }

enum class AudioLayout { STEREO, SURROUND_51, SURROUND_71, SURROUND_714 }

/** What to stream. Anything not covered here comes from the user's saved V+ preferences. */
data class StreamRequest(
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrateKbps: Int,
    val codec: CodecPreference = CodecPreference.AUTO,
    val hdr: Boolean = false,
    val audio: AudioLayout = AudioLayout.STEREO,
    /** Nova hosts only; null lets the host use the app's default. */
    val novaDisplay: NovaDisplayMode? = null,
    /**
     * Extra V+ StreamConfiguration settings, by name: "enableMic", "playHostAudio", "enableSops",
     * "controlOnly", "useVdd", "touchKeyboard", "customScreenMode" (Int), "maxPacketSize" (Int).
     */
    val extras: Map<String, Any> = emptyMap(),
    /**
     * Only resume the app already running on the host, never launch or quit one. Used to reconnect
     * a live stream at a new size (the host applies the new mode on /resume); fails if nothing is
     * running on the host any more.
     */
    val resumeOnly: Boolean = false,
)

/** Split of the end-to-end latency the client can measure. */
data class LatencyParts(
    /** Host capture+encode time reported by the host per frame. */
    val hostMs: Float,
    /** Network round trip. */
    val networkMs: Float,
    val decodeMs: Float,
    val renderMs: Float,
) {
    val totalMs: Float get() = hostMs + networkMs / 2f + decodeMs + renderMs
}

/** Live statistics for a running stream, updated about once per second. */
data class StreamStats(
    val fps: Float = 0f,
    val receivedFps: Float = 0f,
    val bitrateKbps: Int = 0,
    val latency: LatencyParts = LatencyParts(0f, 0f, 0f, 0f),
    /** Share of frames lost in the last window, 0..100. */
    val lossPercent: Float = 0f,
    val decoder: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val hdr: Boolean = false,
)

/** Why a stream ended. */
enum class StreamEndReason { USER_QUIT, DISCONNECTED, HOST_ENDED, ERROR }

/** Callbacks from a running [StreamSession]; every method is optional. */
interface StreamListener {
    fun onStageStarting(stage: String) {}
    fun onStageFailed(stage: String, errorCode: Int) {}
    fun onConnected() {}
    fun onEnded(reason: StreamEndReason, errorCode: Int) {}
    /** A host or engine message to show the user; [transient] ones fit a toast. */
    fun onMessage(message: String, transient: Boolean) {}
    fun onConnectionQuality(poor: Boolean) {}
    fun onRumble(controller: Int, lowFreq: Int, highFreq: Int) {}
    fun onHdrModeChanged(enabled: Boolean) {}
    fun onResolutionChanged(width: Int, height: Int) {}
}

/** The on-disk artwork cache (posters, heroes, logos, screenshots), shared by every host. */
class ArtCacheControl internal constructor(private val cache: io.github.fenyx.nebula.engine.internal.ArtCache) {
    /** Size limit in bytes; least recently used art is evicted beyond it. */
    var limitBytes: Long
        get() = cache.maxBytes
        set(value) { cache.maxBytes = value }

    /** Bytes currently on disk. Does disk I/O; call off the main thread. */
    fun usedBytes(): Long = cache.sizeBytes()

    fun clear() = cache.clear()

    /** Forgets everything cached for [hostId], so the next load re-downloads it. */
    fun clearHost(hostId: String) = cache.clearHost(hostId)

    /** Forgets [appId]'s artwork on [hostId] (every [ArtKind]). */
    fun clearApp(hostId: String, appId: String) =
        ArtKind.entries.forEach { cache.remove(io.github.fenyx.nebula.engine.internal.ArtCache.key(hostId, "art", appId, it.wire)) }
}
