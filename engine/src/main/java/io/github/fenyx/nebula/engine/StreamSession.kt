package io.github.fenyx.nebula.engine

import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.SurfaceHolder
import com.limelight.LimeLog
import com.limelight.binding.audio.SmartAudioRenderer
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.PerfOverlayListener
import com.limelight.binding.video.PerformanceInfo
import com.limelight.nvstream.Ds5HapticsPcmFrame
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.RemoteTextContext
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.BandwidthMeter
import io.github.fenyx.nebula.engine.framegen.FramegenController
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.FramegenEvent
import io.github.fenyx.nebula.engine.framegen.LiveSettings
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import io.github.fenyx.nebula.engine.framegen.FramegenSelfTestRunner
import io.github.fenyx.nebula.engine.framegen.FramegenStatus
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.upscale.UpscalerController
import io.github.fenyx.nebula.engine.upscale.UpscalerStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * A running stream. Created by [NebulaEngine.startStream]; ends when the host ends it, the
 * connection drops, or the app calls [quit] / [disconnect]. [StreamListener] callbacks arrive on
 * the main thread.
 */
class StreamSession internal constructor(
    private val activity: Activity,
    private var holder: SurfaceHolder,
    private val listener: StreamListener,
    initialWidth: Int,
    initialHeight: Int,
    private val background: BackgroundPolicy = BackgroundPolicy(),
) {
    private val main = Handler(Looper.getMainLooper())
    private val bandwidth = BandwidthMeter()
    private val ended = AtomicBoolean(false)
    private lateinit var connection: NvConnection
    private lateinit var decoder: MediaCodecDecoderRenderer
    private lateinit var audio: SmartAudioRenderer
    private var audioPaused = false
    private var videoFps = 0
    private var hdrMode = MoonBridge.HDR_MODE_SDR
    private var hdrFullRange = false

    /** Frame generation (V+'s in-decoder LSFG pipeline); see [framegen] and [setFramegenPaused]. */
    private val framegenController = FramegenController(activity.applicationContext) { e -> onMain { listener.onFramegenEvent(e) } }

    /** Decoder-output upscaling / sharpening; used only when frame generation isn't. */
    private val upscaler = UpscalerController(activity.applicationContext)

    /** What frame generation is doing (presented vs input fps, LSFG ms, auto-off). */
    val framegen: StateFlow<FramegenStatus> get() = framegenController.status

    /** What the decoder-output upscaler is doing. */
    val upscaling: StateFlow<UpscalerStatus> get() = upscaler.status

    private val postPrefs = FramegenDll.prefs(activity.applicationContext)
    private var framegenSettingsChanged = false
    private var upscalerSettingsChanged = false
    private val applyPostProcessing = Runnable { applyChangedSettings() }

    /**
     * Frame generation and upscaler settings changed (stream menu panel, or Settings): apply them
     * to this stream. Held as a field because SharedPreferences keeps listeners weakly.
     */
    private val postPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            in LIVE_FRAMEGEN_KEYS, FramegenKeys.UPSCALER -> {
                if (key == FramegenKeys.UPSCALER) upscalerSettingsChanged = true else framegenSettingsChanged = true
                main.removeCallbacks(applyPostProcessing)
                main.postDelayed(applyPostProcessing, LiveSettings.DEBOUNCE_MS)
            }
            FramegenKeys.UPSCALER_STRENGTH -> upscaler.applyConfig()
        }
    }

    private var haptics: AudioHapticsDriver? = null

    private val _audioHaptics = MutableStateFlow(AudioHapticsConfig())

    /** Audio-to-vibration as applied to this stream; change it live with [setAudioHaptics]. */
    val audioHaptics: StateFlow<AudioHapticsConfig> = _audioHaptics.asStateFlow()

    /** False when the SDK couldn't start on this device (audio haptics then stays off). */
    val audioHapticsAvailable: Boolean get() = haptics != null

    private val _unsupported = MutableStateFlow<Set<HostFeature>>(emptySet())

    /** Host features this stream asked for that Nebula doesn't do yet (see [HostFeature]). */
    val unsupportedHostFeatures: StateFlow<Set<HostFeature>> = _unsupported.asStateFlow()

    /** The app's controllers, so audio haptics can reach them. Set once gamepads are known. */
    var audioHapticsGamepad: AudioHapticsGamepadSink?
        get() = haptics?.gamepad
        set(value) { haptics?.gamepad = value }

    private val _backgrounded = MutableStateFlow(false)

    /**
     * True while the video surface is gone (app in the background, screen off) but the session is
     * kept alive for [BackgroundPolicy.graceMs]. Attaching a surface again resumes it.
     */
    val backgrounded: StateFlow<Boolean> = _backgrounded.asStateFlow()

    private val graceExpired = Runnable {
        LimeLog.info("Background grace period of ${background.graceMs} ms expired; disconnecting")
        disconnect()
    }

    private val _stats = MutableStateFlow(StreamStats(width = initialWidth, height = initialHeight))

    /** Updated about once per second from the decoder. */
    val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    /** Sends keyboard, mouse, touch and gamepad input to the host. */
    lateinit var input: InputBridge
        private set

    /** Microphone and clipboard sync for this stream; set before [start] returns. */
    lateinit var link: StreamHostLink
        private set

    internal var linkConfig: StreamHostLink.Config? = null

    /** True once [link] exists (from the start of the stream on). */
    val isLinkReady: Boolean get() = ::link.isInitialized

    @Volatile
    var isConnected: Boolean = false
        private set

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) = Unit

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = attachSurface(holder)

        override fun surfaceDestroyed(holder: SurfaceHolder) = detachSurface()
    }

    /**
     * Points video at [newHolder] (a new SurfaceView after navigation, or the same one after
     * returning from the background). A paused decoder is rebuilt on it and an IDR frame requested.
     * Call on the main thread with a valid surface.
     */
    fun attachSurface(newHolder: SurfaceHolder) {
        if (ended.get() || newHolder.surface?.isValid != true) return
        if (newHolder !== holder) {
            holder.removeCallback(surfaceCallback)
            holder = newHolder
            newHolder.addCallback(surfaceCallback)
        }
        decoder.setRenderTarget(newHolder)
        if (!_backgrounded.value) {
            framegenController.onOutputSurface(newHolder.surface)
            upscaler.onOutputSurface(newHolder)
        }
        if (_backgrounded.value) {
            main.removeCallbacks(graceExpired)
            LimeLog.info("Surface back; resuming video")
            // The decoder is rebuilt on resume: re-arm the capture path on the new surface first.
            armPostProcessing(newHolder)
            if (!decoder.resumeProcessing()) {
                // Without a decoder there's no picture; end cleanly rather than crash.
                LimeLog.severe("Video couldn't restart on the new surface; disconnecting")
                framegenController.release(decoder)
                upscaler.release(decoder)
                disconnect()
                return
            }
            if (audioPaused) {
                audio.resumeProcessing()
                audioPaused = false
            }
            haptics?.setForeground(true)
            _backgrounded.value = false
            if (::link.isInitialized) link.onBackground(false)
        }
    }

    /**
     * The surface is going away. Within the grace period the network session stays up: video is
     * paused (decoder released), audio too unless [BackgroundPolicy.keepAudio]. Without a grace
     * period, or before the stream connected, this disconnects.
     */
    fun detachSurface() {
        if (ended.get() || _backgrounded.value) return
        if (background.graceMs <= 0 || !isConnected) {
            disconnect()
            return
        }
        LimeLog.info("Surface gone; keeping the session for ${background.graceMs} ms")
        _backgrounded.value = true
        link.onBackground(true)
        if (!background.keepAudio) {
            audio.pauseProcessing()
            audioPaused = true
        }
        decoder.pauseProcessing()
        haptics?.setForeground(false)
        framegenController.release(decoder)
        upscaler.release(decoder)
        main.postDelayed(graceExpired, background.graceMs)
    }

    /** Called by [NebulaEngine.startStream] before [start], so the audio renderer can use it. */
    internal fun useAudioHaptics(driver: AudioHapticsDriver?) {
        haptics = driver
        driver?.let { _audioHaptics.value = it.config }
    }

    internal fun start(
        connection: NvConnection,
        decoder: MediaCodecDecoderRenderer,
        audio: SmartAudioRenderer,
        videoFps: Int,
        hdrMode: Int,
        hdrFullRange: Boolean,
    ) {
        check(holder.surface?.isValid == true) { "startStream needs a created surface; call it from surfaceChanged()" }
        this.connection = connection
        this.decoder = decoder
        this.audio = audio
        this.videoFps = videoFps
        this.hdrMode = hdrMode
        this.hdrFullRange = hdrFullRange
        MoonBridge.setAudioHapticsSessionHandle(haptics?.nativeHandle ?: 0L)
        input = InputBridge(connection)
        link = StreamHostLink(activity.applicationContext, connection, requireNotNull(linkConfig) { "linkConfig" })
        holder.addCallback(surfaceCallback)
        decoder.setRenderTarget(holder)
        framegenController.newSession()
        // Stops a device check still running: it must not share the GPU with a live stream.
        FramegenSelfTestRunner.onStreamStarted(activity)
        postPrefs.registerOnSharedPreferenceChangeListener(postPrefsListener)
        armPostProcessing(holder)
        connection.start(audio, decoder, connectionListener)
    }

    /** Frame generation when it can run, else the upscaler when chosen, else the direct path. */
    private fun armPostProcessing(target: SurfaceHolder) {
        val (w, h) = _stats.value.width to _stats.value.height
        val surface = target.surface ?: return
        if (framegenController.arm(decoder, surface, w, h, videoFps, hdrMode, hdrFullRange)) return
        upscaler.arm(decoder, target, w, h, hdr = hdrMode != MoonBridge.HDR_MODE_SDR)
    }

    /**
     * Stream-menu quick toggle: pauses frame generation (decoded frames only) or resumes it.
     * Resuming after a thermal auto-off needs [force]. False when that was refused.
     */
    fun setFramegenPaused(paused: Boolean, force: Boolean = false): Boolean = framegenController.setPaused(paused, force)

    /** Applies a changed upscaler strength (Settings or stream menu) to the running stream. */
    fun refreshUpscaler() = upscaler.applyConfig()

    /**
     * Applies changed frame generation / upscaler settings to the running stream, per
     * [LiveSettings]: never by rebuilding the decoder or swapping the screen's producer (that
     * crashed dev10). Model and tuning changes reconfigure frame generation natively; on/off
     * changes that would need another producer on the SurfaceView wait for the next stream.
     * Main thread; debounced, so a burst of changes applies once.
     */
    private fun applyChangedSettings() {
        main.removeCallbacks(applyPostProcessing)
        val fgChanged = framegenSettingsChanged
        val upChanged = upscalerSettingsChanged
        framegenSettingsChanged = false
        upscalerSettingsChanged = false
        // Backgrounded: the resume re-arms from the saved settings anyway.
        if (ended.get() || _backgrounded.value || !isConnected || !::decoder.isInitialized) return
        val (w, h) = _stats.value.width to _stats.value.height
        runCatching {
            if (fgChanged) framegenController.applyLive(w, h, videoFps)
            if (upChanged) {
                val mode = UpscalerConfig.from(postPrefs.all).mode
                when (LiveSettings.upscaler(framegenController.isArmed, upscaler.isArmed, mode)) {
                    LiveSettings.Upscaler.UPDATE -> upscaler.applyConfig()
                    LiveSettings.Upscaler.NEXT_STREAM -> listener.onFramegenEvent(FramegenEvent.NextStream("The upscaler change applies from the next stream."))
                    LiveSettings.Upscaler.NONE -> Unit
                }
            }
        }.onFailure { LimeLog.warning("Applying post-processing settings live failed: ${it.message}") }
    }

    /** Ends the stream and asks the host to close the running app. */
    fun quit() {
        if (!beginEnd(StreamEndReason.USER_QUIT)) return
        Thread({ connection.doStopAndQuit() }, "nebula-quit").start()
    }

    /**
     * Ends the stream and leaves the app running on the host, so it can be resumed. [onStopped]
     * runs (on a background thread) once the connection is fully torn down and a new stream may
     * start; right away if the session was already ending.
     */
    fun disconnect(onStopped: (() -> Unit)? = null) {
        if (!beginEnd(StreamEndReason.USER_QUIT)) {
            onStopped?.invoke()
            return
        }
        Thread({
            try {
                connection.stop()
            } finally {
                onStopped?.invoke()
            }
        }, "nebula-stop").start()
    }

    /** Asks the host to scale its desktop UI (text, icons) to [percent]; true once it accepted. */
    suspend fun setDisplayScale(percent: Int): Boolean = suspendCancellableCoroutine { cont ->
        connection.setDisplayScale(percent) { ok -> if (cont.isActive) cont.resume(ok) }
    }

    /** Asks the host to change the video bitrate mid-stream; true once the host accepted it. */
    suspend fun setBitrate(kbps: Int): Boolean = suspendCancellableCoroutine { cont ->
        connection.setBitrate(kbps, object : NvConnection.BitrateAdjustmentCallback {
            override fun onSuccess(newBitrate: Int) {
                if (cont.isActive) cont.resume(true)
            }

            override fun onFailure(errorMessage: String) {
                LimeLog.warning("Bitrate change to $kbps kbps failed: $errorMessage")
                if (cont.isActive) cont.resume(false)
            }
        })
    }

    /**
     * Applies audio-haptics settings to the running stream. Returns false when the change can't
     * apply live: Android's audio-coupled generator (music on a fixed device route) is bound to the
     * audio track, so switching to or from it takes effect on the next stream.
     */
    fun setAudioHaptics(config: AudioHapticsConfig): Boolean {
        val driver = haptics ?: return false
        val live = AudioHapticsPolicy.wantsSystemCoupled(driver.config) == AudioHapticsPolicy.wantsSystemCoupled(config)
        driver.update(config)
        _audioHaptics.value = driver.config
        return live
    }

    /**
     * Tells audio haptics that the host's own rumble is driving this device's vibrator right now,
     * so the audio effect ducks out instead of fighting it.
     */
    fun setGameRumbleOnDevice(active: Boolean) {
        haptics?.deviceDucked = active
    }

    /** Stops the decoder and detaches from the surface once; returns false if already ending. */
    private fun beginEnd(reason: StreamEndReason): Boolean {
        if (ended.getAndSet(true)) return false
        isConnected = false
        main.post {
            main.removeCallbacks(graceExpired)
            holder.removeCallback(surfaceCallback)
            _backgrounded.value = false
            if (::link.isInitialized) link.stop()
        }
        decoder.prepareForStop()
        main.post {
            main.removeCallbacks(applyPostProcessing)
            postPrefs.unregisterOnSharedPreferenceChangeListener(postPrefsListener)
            framegenController.shutdown(decoder)
            upscaler.release(decoder)
            // A device check asked for during the stream runs now.
            FramegenSelfTestRunner.onStreamEnded(activity)
        }
        haptics?.let { h ->
            haptics = null
            MoonBridge.setAudioHapticsSessionHandle(0L)
            runCatching { h.release() }.onFailure { LimeLog.warning("Audio haptics release failed: ${it.message}") }
        }
        if (reason == StreamEndReason.USER_QUIT) {
            main.post { listener.onEnded(StreamEndReason.USER_QUIT, 0) }
        }
        return true
    }

    private fun terminated(reason: StreamEndReason, errorCode: Int) {
        if (!beginEnd(reason)) return
        Thread({ connection.stop() }, "nebula-stop").start()
        main.post { listener.onEnded(reason, errorCode) }
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }

    /** Reports a host request Nebula can't honour yet, once per feature. */
    private fun unsupported(feature: HostFeature) {
        val before = _unsupported.value
        if (feature in before) return
        _unsupported.value = before + feature
        LimeLog.info("Host used ${feature.name}; not supported by Nebula yet (planned for ${feature.plannedFor})")
        onMain { listener.onUnsupportedHostFeature(feature) }
    }

    private val connectionListener = object : NvConnectionListener, PerfOverlayListener {
        override fun stageStarting(stage: String) = onMain { listener.onStageStarting(stage) }
        override fun stageComplete(stage: String) = Unit

        override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
            onMain { listener.onStageFailed(stage, errorCode) }
            terminated(StreamEndReason.ERROR, errorCode)
        }

        override fun connectionStarted() {
            isConnected = true
            onMain {
                if (!ended.get()) link.onConnected()
                listener.onConnected()
            }
        }

        override fun connectionTerminated(errorCode: Int) {
            val reason = if (errorCode == MoonBridge.ML_ERROR_GRACEFUL_TERMINATION) {
                StreamEndReason.HOST_ENDED
            } else {
                StreamEndReason.DISCONNECTED
            }
            terminated(reason, errorCode)
        }

        override fun connectionStatusUpdate(connectionStatus: Int) =
            onMain { listener.onConnectionQuality(connectionStatus == MoonBridge.CONN_STATUS_POOR) }

        override fun displayMessage(message: String) = onMain { listener.onMessage(message, transient = false) }
        override fun displayTransientMessage(message: String) = onMain { listener.onMessage(message, transient = true) }

        override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) =
            onMain {
                listener.onRumble(controllerNumber.toInt(), lowFreqMotor.toInt() and 0xFFFF, highFreqMotor.toInt() and 0xFFFF)
            }

        override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) =
            onMain {
                listener.onRumbleTriggers(controllerNumber.toInt(), leftTrigger.toInt() and 0xFFFF, rightTrigger.toInt() and 0xFFFF)
            }

        override fun setAdaptiveTriggers(
            controllerNumber: Short,
            eventFlags: Byte,
            typeLeft: Byte,
            typeRight: Byte,
            left: ByteArray,
            right: ByteArray,
        ) = unsupported(HostFeature.ADAPTIVE_TRIGGERS)

        override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
            framegenController.configureHdr(if (enabled) hdrMode else MoonBridge.HDR_MODE_SDR, enabled && hdrFullRange)
            decoder.setHdrMode(enabled, hdrMetadata)
            _stats.update { it.copy(hdr = enabled) }
            onMain {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    activity.window.colorMode =
                        if (enabled) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_DEFAULT
                }
                listener.onHdrModeChanged(enabled)
            }
        }

        override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
            val type = MotionType.fromWire(motionType) ?: return
            onMain { listener.onMotionRequest(controllerNumber.toInt(), type, reportRateHz.toInt() and 0xFFFF) }
        }

        override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) =
            onMain { listener.onControllerLed(controllerNumber.toInt(), r.toInt() and 0xFF, g.toInt() and 0xFF, b.toInt() and 0xFF) }

        override fun ds5HapticsPcm(frame: Ds5HapticsPcmFrame) = unsupported(HostFeature.DS5_HAPTICS)

        override fun onResolutionChanged(width: Int, height: Int) {
            _stats.update { it.copy(width = width, height = height) }
            onMain { listener.onResolutionChanged(width, height) }
        }

        override fun onCursorUpdate(
            flags: Int,
            shapeId: Int,
            width: Int,
            height: Int,
            hotspotX: Int,
            hotspotY: Int,
            bgraPixels: ByteArray?,
        ) = unsupported(HostFeature.LOCAL_CURSOR)

        override fun onRemoteTextContext(context: RemoteTextContext) = unsupported(HostFeature.REMOTE_TEXT_CONTEXT)

        override fun onPerfUpdateV(performanceInfo: PerformanceInfo) {
            framegenController.onPerformanceInfo(performanceInfo)
            upscaler.onWindow()
            val mbps = bandwidth.update(MoonBridge.getRtpVideoBytesReceived(), System.nanoTime())
            _stats.update { performanceInfo.toStreamStats(mbps, it) }
        }

        override fun onPerfUpdateWG(performanceInfo: PerformanceInfo) = Unit
        override fun onVideoFrameLoss(framesLost: Int, frameNumber: Int) = framegenController.onFrameLoss(framesLost, frameNumber)
        override fun isPerfOverlayVisible(): Boolean = true
    }

    /** The decoder is built before [start] and reports stats through this. */
    internal val perfListener: PerfOverlayListener get() = connectionListener
}

/**
 * What happens when the stream's surface goes away. [graceMs] = 0 disconnects at once; otherwise
 * the session waits that long for a new surface. [keepAudio] keeps host audio playing meanwhile.
 */
/** Frame generation settings applied to a live stream ([LiveSettings]). */
internal val LIVE_FRAMEGEN_KEYS = setOf(
    FramegenKeys.ENABLED, FramegenKeys.ADAPTIVE, FramegenKeys.MULTIPLIER, FramegenKeys.QUALITY_PRESET,
    FramegenKeys.CUSTOM_SCALE, FramegenKeys.FLOW_SCALE, FramegenKeys.PERFORMANCE_MODE, FramegenKeys.SLOW_THRESHOLD_MS,
    FramegenKeys.PRESENT_REAL_FIRST, FramegenKeys.THERMAL_GUARD,
)

data class BackgroundPolicy(val graceMs: Long = 60_000, val keepAudio: Boolean = false)

/** Maps a decoder stats window onto [StreamStats]; [measuredMbps] is null until measured. */
internal fun PerformanceInfo.toStreamStats(measuredMbps: Double?, previous: StreamStats): StreamStats = StreamStats(
    fps = renderedFps,
    receivedFps = receivedFps,
    hostFps = totalFps,
    onePercentLowFps = onePercentLowFps.takeIf { it.isFinite() } ?: 0f,
    jitterMs = (rttInfo and 0xFFFFFFFFL).toFloat(),
    bitrateKbps = measuredMbps?.let { (it * 1000).toInt() } ?: previous.bitrateKbps,
    latency = LatencyParts(
        hostMs = aveHostProcessingLatency,
        networkMs = (rttInfo shr 32).toFloat(),
        decodeMs = decodeTimeMs,
        renderMs = renderingLatencyMs,
    ),
    lossPercent = lostFrameRate.takeIf { it.isFinite() } ?: 0f,
    decoder = decoder ?: previous.decoder,
    width = if (initialWidth > 0) initialWidth else previous.width,
    height = if (initialHeight > 0) initialHeight else previous.height,
    hdr = hdrFormat.isHdr,
)
