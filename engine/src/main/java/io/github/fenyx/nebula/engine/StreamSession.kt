package io.github.fenyx.nebula.engine

import android.app.Activity
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
        if (_backgrounded.value) {
            main.removeCallbacks(graceExpired)
            LimeLog.info("Surface back; resuming video")
            decoder.resumeProcessing()
            if (audioPaused) {
                audio.resumeProcessing()
                audioPaused = false
            }
            _backgrounded.value = false
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
        if (!background.keepAudio) {
            audio.pauseProcessing()
            audioPaused = true
        }
        decoder.pauseProcessing()
        main.postDelayed(graceExpired, background.graceMs)
    }

    internal fun start(connection: NvConnection, decoder: MediaCodecDecoderRenderer, audio: SmartAudioRenderer) {
        check(holder.surface?.isValid == true) { "startStream needs a created surface; call it from surfaceChanged()" }
        this.connection = connection
        this.decoder = decoder
        this.audio = audio
        input = InputBridge(connection)
        holder.addCallback(surfaceCallback)
        decoder.setRenderTarget(holder)
        connection.start(audio, decoder, connectionListener)
    }

    /** Ends the stream and asks the host to close the running app. */
    fun quit() {
        if (!beginEnd(StreamEndReason.USER_QUIT)) return
        Thread({ connection.doStopAndQuit() }, "nebula-quit").start()
    }

    /** Ends the stream and leaves the app running on the host, so it can be resumed. */
    fun disconnect() {
        if (!beginEnd(StreamEndReason.USER_QUIT)) return
        Thread({ connection.stop() }, "nebula-stop").start()
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

    /** Stops the decoder and detaches from the surface once; returns false if already ending. */
    private fun beginEnd(reason: StreamEndReason): Boolean {
        if (ended.getAndSet(true)) return false
        isConnected = false
        main.post {
            main.removeCallbacks(graceExpired)
            holder.removeCallback(surfaceCallback)
            _backgrounded.value = false
        }
        decoder.prepareForStop()
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

    private val connectionListener = object : NvConnectionListener, PerfOverlayListener {
        override fun stageStarting(stage: String) = onMain { listener.onStageStarting(stage) }
        override fun stageComplete(stage: String) = Unit

        override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
            onMain { listener.onStageFailed(stage, errorCode) }
            terminated(StreamEndReason.ERROR, errorCode)
        }

        override fun connectionStarted() {
            isConnected = true
            onMain { listener.onConnected() }
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

        override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) = Unit
        override fun setAdaptiveTriggers(
            controllerNumber: Short,
            eventFlags: Byte,
            typeLeft: Byte,
            typeRight: Byte,
            left: ByteArray,
            right: ByteArray,
        ) = Unit

        override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
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

        override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) = Unit
        override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) = Unit
        override fun ds5HapticsPcm(frame: Ds5HapticsPcmFrame) = Unit

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
        ) = Unit

        override fun onRemoteTextContext(context: RemoteTextContext) = Unit

        override fun onPerfUpdateV(performanceInfo: PerformanceInfo) {
            val mbps = bandwidth.update(MoonBridge.getRtpVideoBytesReceived(), System.nanoTime())
            _stats.update { performanceInfo.toStreamStats(mbps, it) }
        }

        override fun onPerfUpdateWG(performanceInfo: PerformanceInfo) = Unit
        override fun onVideoFrameLoss(framesLost: Int, frameNumber: Int) = Unit
        override fun isPerfOverlayVisible(): Boolean = true
    }

    /** The decoder is built before [start] and reports stats through this. */
    internal val perfListener: PerfOverlayListener get() = connectionListener
}

/**
 * What happens when the stream's surface goes away. [graceMs] = 0 disconnects at once; otherwise
 * the session waits that long for a new surface. [keepAudio] keeps host audio playing meanwhile.
 */
data class BackgroundPolicy(val graceMs: Long = 60_000, val keepAudio: Boolean = false)

/** Maps a decoder stats window onto [StreamStats]; [measuredMbps] is null until measured. */
internal fun PerformanceInfo.toStreamStats(measuredMbps: Double?, previous: StreamStats): StreamStats = StreamStats(
    fps = renderedFps,
    receivedFps = receivedFps,
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
