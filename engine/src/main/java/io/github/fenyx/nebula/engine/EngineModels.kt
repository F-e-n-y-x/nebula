package io.github.fenyx.nebula.engine

/** Reachability of a host as last seen by the engine's poller. */
enum class HostState { ONLINE, OFFLINE, UNKNOWN }

/** How the engine learned about an address. */
enum class AddressKind { LOCAL, REMOTE, MANUAL, IPV6 }

/** One way to reach a host. */
data class HostAddress(val address: String, val port: Int, val kind: AddressKind)

/** Capabilities a Nova host advertises on GET /nova/v1/capabilities. */
data class NovaCapabilities(val version: String, val features: Set<String>) {
    /** True when the host implements the named /nova/v1 feature (e.g. "apps", "art", "details"). */
    fun has(feature: String): Boolean = feature in features
}

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
    /** Frames per second the host sent, lost ones included. */
    val hostFps: Float = 0f,
    /** 1% low of the rendered frame rate (inverse of the P99 frame interval). */
    val onePercentLowFps: Float = 0f,
    /** Round-trip time variance, a jitter estimate. */
    val jitterMs: Float = 0f,
    val bitrateKbps: Int = 0,
    val latency: LatencyParts = LatencyParts(0f, 0f, 0f, 0f),
    /** Share of frames lost in the last window, 0..100. */
    val lossPercent: Float = 0f,
    val decoder: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val hdr: Boolean = false,
)

/** Controller motion sensor kinds, as the host names them. */
enum class MotionType(internal val wire: Byte) {
    ACCEL(com.limelight.nvstream.jni.MoonBridge.LI_MOTION_TYPE_ACCEL),
    GYRO(com.limelight.nvstream.jni.MoonBridge.LI_MOTION_TYPE_GYRO);

    companion object {
        internal fun fromWire(wire: Byte): MotionType? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Host-to-client features the engine receives but Nebula doesn't implement yet. Each is reported
 * through [StreamListener.onUnsupportedHostFeature] instead of being dropped silently; [plannedFor]
 * is the Nebula release that is meant to add it.
 */
enum class HostFeature(val title: String, val plannedFor: String, val why: String) {
    ADAPTIVE_TRIGGERS("Adaptive triggers", "0.4", "Android has no public API for DualSense trigger effects; they need the USB driver path."),
    LOCAL_CURSOR("Host cursor sync", "0.4", "Drawing the PC's own cursor shape on this device isn't built yet."),
    DS5_HAPTICS("DualSense haptics", "0.4", "Host-authored DualSense haptics need the USB driver path."),
    REMOTE_TEXT_CONTEXT("Keyboard on text focus", "0.4", "Opening the keyboard when a PC text field is focused isn't built yet."),
}

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
    /** Host game rumble for [controller]; motors are 0–65535, 0/0 stops. */
    fun onRumble(controller: Int, lowFreq: Int, highFreq: Int) {}
    /** Impulse-trigger rumble (Xbox One/Series and DualSense triggers); 0–65535 each. */
    fun onRumbleTriggers(controller: Int, left: Int, right: Int) {}
    /**
     * The host wants [type] motion samples from [controller] at [rateHz] (0 = stop). Send them with
     * [InputBridge.motion]; sensors should run only while this is non-zero.
     */
    fun onMotionRequest(controller: Int, type: MotionType, rateHz: Int) {}
    /** The host set the controller's light bar colour. */
    fun onControllerLed(controller: Int, r: Int, g: Int, b: Int) {}
    /**
     * The host used a feature Nebula can't honour yet (see [HostFeature]). Sent once per feature
     * per session, so the UI can say so instead of silently ignoring it.
     */
    fun onUnsupportedHostFeature(feature: HostFeature) {}
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
