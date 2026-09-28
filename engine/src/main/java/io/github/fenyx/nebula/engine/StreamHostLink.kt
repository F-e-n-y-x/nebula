package io.github.fenyx.nebula.engine

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.limelight.LimeLog
import com.limelight.binding.audio.MicrophoneConfig
import com.limelight.binding.audio.MicrophoneInitialStateStore
import com.limelight.binding.audio.MicrophoneStream
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.ClipboardSyncManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.MicrophoneInitialState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether clipboard sync runs for this stream. */
enum class ClipboardSync {
    /** Both clipboard settings are off. */
    OFF,
    /** The settings are on but the host didn't advertise clipboard sync (RTSP feature flags 0x04/0x08). */
    UNSUPPORTED,
    /** The host has clipboard sync off, or this device lacks its `clipboard` permission. */
    NOT_ALLOWED,
    ACTIVE,
}

/** Result of [StreamHostLink.sendClipboardNow]. */
enum class ClipboardSend { SENT, EMPTY, NOT_SYNCING }

/**
 * The parts of a stream that link this device to the host beyond audio, video and input: the
 * microphone (V+ [MicrophoneStream], sent to Nova's "Nova Mic" source) and two-way clipboard sync
 * (V+ [ClipboardSyncManager], control message 0x5508 plus the blob routes).
 *
 * Owned by a [StreamSession]; available as [StreamSession.link] once the stream starts. Call its
 * methods on the main thread.
 */
class StreamHostLink internal constructor(
    private val context: Context,
    private val connection: NvConnection,
    private val config: Config,
) {
    internal data class Config(
        val enableMic: Boolean,
        val micInitialState: MicrophoneInitialState,
        val hostId: String?,
        val clipboardText: Boolean,
        val clipboardImage: Boolean,
        /** Nova always advertises clipboard sync; other hosts may support it without saying so. */
        val requireClipboardFlag: Boolean,
        /** The host turned clipboard sync off or doesn't grant this device the `clipboard` permission. */
        val clipboardDenied: Boolean = false,
    )

    private val initialStates = MicrophoneInitialStateStore(context)
    private var mic: MicrophoneStream? = null
    private var clipboardManager: ClipboardSyncManager? = null
    private var resumeMicOnForeground = false
    private var lastClipTimestamp = -1L

    private val _micSupported = MutableStateFlow<Boolean?>(null)

    /**
     * Whether the mic can be used on this stream: null until connected, false when the mic setting
     * is off or the host didn't ask for a microphone.
     */
    val micSupported: StateFlow<Boolean?> = _micSupported.asStateFlow()

    private val _micLive = MutableStateFlow(false)

    /** True while this device's microphone is being sent to the host. */
    val micLive: StateFlow<Boolean> = _micLive.asStateFlow()

    private val _micPausedInBackground = MutableStateFlow(false)

    /** The mic was live and is paused because the app went to the background. */
    val micPausedInBackground: StateFlow<Boolean> = _micPausedInBackground.asStateFlow()

    private val _clipboard = MutableStateFlow(if (config.clipboardText || config.clipboardImage) ClipboardSync.UNSUPPORTED else ClipboardSync.OFF)
    val clipboard: StateFlow<ClipboardSync> = _clipboard.asStateFlow()

    /** The mic setting is on in Settings (the host may still not support it). */
    val micEnabledInSettings: Boolean get() = config.enableMic

    /** What the "Initial microphone state" setting asks for at stream start (per host for "follow last"). */
    fun micWantedAtStart(): Boolean = config.enableMic && config.micInitialState.resolve(initialStates.load(config.hostId))

    fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    internal fun onConnected() {
        _micSupported.value = config.enableMic && runCatching { MoonBridge.isMicrophoneRequested() }.getOrDefault(false)
        startClipboard()
    }

    /**
     * Starts or stops sending the microphone. Returns false (and stays off) without the
     * RECORD_AUDIO permission, when the host didn't ask for a mic, or when capture fails.
     * [remember] stores the choice for "follow last state".
     */
    fun setMicLive(on: Boolean, remember: Boolean = true): Boolean {
        if (!on) {
            mic?.pause()
            _micLive.value = false
            resumeMicOnForeground = false
            _micPausedInBackground.value = false
            if (remember) initialStates.save(config.hostId, false)
            return true
        }
        if (_micSupported.value != true || !hasMicPermission()) return false
        val ok = try {
            val stream = mic ?: newMicStream().also { mic = it }
            when {
                stream.isRunning() -> true
                stream.isInitialized() -> stream.resume()
                else -> stream.start() && stream.isRunning()
            }
        } catch (e: SecurityException) {
            LimeLog.warning("Microphone permission missing: ${e.message}")
            false
        }
        if (!ok) {
            mic?.stop()
            mic = null
        }
        _micLive.value = ok
        if (ok && remember) initialStates.save(config.hostId, true)
        return ok
    }

    private fun newMicStream(): MicrophoneStream {
        MicrophoneConfig.updateBitrateFromConfig(context)
        MicrophoneConfig.updateVolumeProcessingFromConfig(context)
        return MicrophoneStream(connection)
    }

    /**
     * The app left the foreground (or came back). Android only lets an app record while it's
     * visible unless it runs a microphone foreground service, so a live mic pauses in the
     * background and resumes when the stream is visible again.
     */
    internal fun onBackground(background: Boolean) {
        if (background) {
            if (_micLive.value) {
                mic?.pause()
                _micLive.value = false
                resumeMicOnForeground = true
                _micPausedInBackground.value = true
            }
        } else if (resumeMicOnForeground) {
            resumeMicOnForeground = false
            _micPausedInBackground.value = false
            setMicLive(true, remember = false)
        }
    }

    private fun startClipboard() {
        if (!config.clipboardText && !config.clipboardImage) return
        if (config.clipboardDenied) {
            _clipboard.value = ClipboardSync.NOT_ALLOWED
            return
        }
        val flags = runCatching { MoonBridge.getHostFeatureFlags() }.getOrDefault(0)
        val hostText = flags and FF_CLIPBOARD_TEXT != 0
        val hostImage = flags and FF_CLIPBOARD_IMAGE != 0
        val advertised = hostText || hostImage
        if (config.requireClipboardFlag && !advertised) {
            LimeLog.info("Clipboard sync: host didn't advertise it (flags=0x${flags.toString(16)})")
            _clipboard.value = ClipboardSync.UNSUPPORTED
            return
        }
        val text = config.clipboardText && (hostText || !advertised)
        val image = config.clipboardImage && (hostImage || !advertised)
        if (!text && !image) {
            _clipboard.value = ClipboardSync.UNSUPPORTED
            return
        }
        val manager = ClipboardSyncManager(
            context = context,
            syncText = text,
            syncImage = image,
            fileProviderAuthority = context.packageName + CLIPBOARD_PROVIDER_SUFFIX,
            nvHttpProvider = { runCatching { connection.createNvHttp() }.getOrNull() },
        )
        runCatching { manager.start() }
            .onSuccess {
                clipboardManager = manager
                _clipboard.value = ClipboardSync.ACTIVE
                LimeLog.info("Clipboard sync started (text=$text, image=$image)")
            }
            .onFailure { LimeLog.warning("Clipboard sync start failed: ${it.message}") }
    }

    /**
     * Sends what's on this device's clipboard to the host now, even if it was sent before.
     * Call from a user action while the app has focus (Android 10+ only allows reads then).
     */
    fun sendClipboardNow(): ClipboardSend {
        val manager = clipboardManager ?: return ClipboardSend.NOT_SYNCING
        val cm = context.getSystemService(ClipboardManager::class.java)
        if (cm?.hasPrimaryClip() != true) return ClipboardSend.EMPTY
        manager.pushCurrentClip()
        return ClipboardSend.SENT
    }

    /**
     * Android 10+ lets an app read the clipboard only while it has input focus, so changes made in
     * other apps are picked up when the stream regains focus. Only a clip newer than the last one
     * seen is read, so returning to the app doesn't trigger Android's "pasted" notice every time.
     */
    fun onWindowFocusChanged(focused: Boolean) {
        val manager = clipboardManager ?: return
        if (!focused) return
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val stamp = runCatching { cm.primaryClipDescription?.timestamp }.getOrNull() ?: return
        if (stamp == lastClipTimestamp) return
        val first = lastClipTimestamp < 0
        lastClipTimestamp = stamp
        // What was on the clipboard before the stream started stays on this device until asked for.
        if (!first) manager.onFocusGained()
    }

    internal fun stop() {
        mic?.stop()
        mic = null
        _micLive.value = false
        _micPausedInBackground.value = false
        resumeMicOnForeground = false
        clipboardManager?.stop()
        clipboardManager = null
    }

    companion object {
        /** RTSP `x-ss-general.featureFlags` bits (Nova `platform_caps::clipboard_text/image`). */
        const val FF_CLIPBOARD_TEXT = 0x04
        const val FF_CLIPBOARD_IMAGE = 0x08
        /** The app must declare a FileProvider with this authority suffix for inbound images. */
        const val CLIPBOARD_PROVIDER_SUFFIX = ".clipboard_fileprovider"
    }
}
