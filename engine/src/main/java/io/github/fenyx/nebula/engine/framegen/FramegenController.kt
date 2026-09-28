package io.github.fenyx.nebula.engine.framegen

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import com.limelight.LimeLog
import com.limelight.binding.video.MediaCodecDecoderRenderer
import com.limelight.binding.video.PerformanceInfo
import com.limelight.framegen.FramegenCapture
import com.limelight.framegen.FramegenInterceptor
import com.limelight.framegen.FramegenStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger

/** What frame generation is doing in the current stream. */
data class FramegenStatus(
    val state: State = State.OFF,
    /** Why it is off (OFF / AUTO_OFF). */
    val reason: FramegenOffReason? = FramegenOffReason.DISABLED,
    /** Frames shown per second, generated ones included; 0 while off. */
    val presentedFps: Float = 0f,
    /** Decoded frames per second going into LSFG. */
    val inputFps: Float = 0f,
    val targetFps: Int = 0,
    val multiplier: Int = 2,
    /** LSFG time for the last generated frame, and the copy to the screen. */
    val lsfgMs: Int = 0,
    val blitMs: Int = 0,
    /**
     * Estimated latency frame generation adds: half an input frame (the interpolated frame is
     * shown before the real one) plus LSFG and copy time. An estimate, not a measurement.
     */
    val addedLatencyMs: Float = 0f,
    /** "3.1" or "3.1p" (performance), and the flow scale. */
    val model: String = "",
    /** Long-session numbers (time, share of windows at ≥115 fps for 60→120, minimum). */
    val soak: String? = null,
    /** Why the guard turned it off, e.g. "thermal headroom 0.15 left". */
    val autoOffDetail: String? = null,
) {
    enum class State { OFF, STARTING, ACTIVE, PAUSED, AUTO_OFF }

    val generating: Boolean get() = state == State.ACTIVE
    /** Added latency in input frames (the roadmap target is at most 1). */
    val addedLatencyFrames: Float get() = if (inputFps > 1f) addedLatencyMs / (1000f / inputFps) else 0f
}

/** Something the app should tell the user about. */
sealed interface FramegenEvent {
    data class AutoOff(val cause: GuardDecision.Cause, val detail: String) : FramegenEvent
    data class Unavailable(val reason: FramegenOffReason) : FramegenEvent
    /** New settings couldn't start frame generation on this stream; it was paused instead. */
    data object RestartFailed : FramegenEvent
    /** A change takes effect from the next stream (on/off can't switch the screen's producer live). */
    data class NextStream(val detail: String) : FramegenEvent
}

/**
 * Frame generation for one stream, moved out of V+'s Game.kt: plans from the V+ preferences plus
 * Nebula's (multiplier, flow scale, performance model, guard), arms the decoder's ImageReader
 * capture with a prewarmed LSFG context, runs V+'s adaptive controller and stats enricher, and
 * adds the thermal / slow-frame guard and the soak recorder. Owned by StreamSession.
 *
 * Threading: [arm], [onOutputSurface], [release] and [setPaused] on the main thread;
 * [onPerformanceInfo] and [onFrameLoss] on the decoder stats thread.
 */
class FramegenController(
    private val context: Context,
    private val onEvent: (FramegenEvent) -> Unit,
) {
    private val surfaceExecutor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "FramegenSurface").apply { isDaemon = true } }
    private val generation = AtomicInteger(0)
    private val adaptive = FramegenAdaptiveController()
    private val power = context.getSystemService(PowerManager::class.java)

    private var capture: FramegenCapture? = null
    private var runtime: FramegenRuntime? = null
    private var guard = ThermalGuard()
    private var soak: SoakRecorder? = null
    private var lastWindowMs = 0L
    @Volatile private var userPaused = false
    @Volatile private var autoOff: GuardDecision.TurnOff? = null
    private val restartWatch = LiveSettings.RestartWatch()

    private val _status = MutableStateFlow(FramegenStatus())
    val status: StateFlow<FramegenStatus> = _status.asStateFlow()

    /** True while decoded frames go through frame generation (even when paused). */
    val isArmed: Boolean get() = capture != null

    fun plan(width: Int, height: Int, fps: Int): FramegenPlan {
        val prefs = FramegenDll.prefs(context)
        val config = FramegenConfig.from(prefs.all)
        val native = FramegenInterceptor.isAvailable()
        val verdict = if (native && config.dllPath != null) FramegenSelfTestRunner.verdict(context) else SelfTestVerdict.NOT_RUN
        return FramegenPlanner.plan(config, width, height, fps, native, verdict)
    }

    /**
     * Arms capture for a stream of [width]x[height] at [fps] presenting on [outputSurface], if the
     * plan says so. The decoder starts on the SurfaceView and switches to the capture after its
     * first frame once LSFG is prewarmed (V+'s delayed switch). Returns true when armed.
     */
    fun arm(
        decoder: MediaCodecDecoderRenderer,
        outputSurface: Surface,
        width: Int,
        height: Int,
        fps: Int,
        hdrMode: Int,
        hdrFullRange: Boolean,
    ): Boolean {
        release(decoder)
        if (autoOff != null) {
            _status.update { it.copy(state = FramegenStatus.State.AUTO_OFF, reason = FramegenOffReason.THERMAL) }
            return false
        }
        val plan = plan(width, height, fps)
        val rt = (plan as? FramegenPlan.On)?.runtime
        if (rt == null) {
            val reason = (plan as FramegenPlan.Off).reason
            _status.value = FramegenStatus(state = FramegenStatus.State.OFF, reason = reason)
            if (reason != FramegenOffReason.DISABLED) onEvent(FramegenEvent.Unavailable(reason))
            return false
        }
        FramegenInterceptor.configureLosslessDllPath(rt.dllPath)
        FramegenInterceptor.configureHdrMode(hdrMode, hdrFullRange)
        FramegenInterceptor.configureLsfgModel(rt.flowScale, rt.performanceMode)
        FramegenInterceptor.setGenerationPaused(userPaused)
        FramegenInterceptor.configureOutputFrameRate(if (userPaused) rt.inputFps else rt.presentationFps)
        FramegenInterceptor.configureTuning(rt.internalWidth, rt.presentMode, rt.slowThresholdMs, rt.presentQueueMax, rt.adaptiveOnly)
        adaptive.configure(
            FramegenAdaptiveController.Config(
                inputFps = rt.inputFps,
                presentationFps = rt.presentationFps,
                adaptiveEnabled = rt.adaptive,
                allowAdaptiveWithoutDoubling = rt.adaptiveOnly,
                internalWidth = rt.internalWidth,
                presentMode = rt.presentMode,
                slowFrameThresholdMs = rt.slowThresholdMs,
                presentQueueMax = rt.presentQueueMax,
            ),
        )
        decoder.setFramegenCaptureSwitchReady(false)
        val cap = FramegenCapture.create(width, height)
        if (cap == null) {
            LimeLog.warning("Framegen: ImageReader capture unavailable; direct decoder output")
            _status.value = FramegenStatus(state = FramegenStatus.State.OFF, reason = FramegenOffReason.UNSUPPORTED)
            return false
        }
        capture = cap
        runtime = rt
        guard = ThermalGuard(enabled = rt.thermalGuard)
        soak = SoakRecorder.forTarget(rt.presentationFps).takeIf { rt.presentationFps > rt.inputFps }
        lastWindowMs = 0L
        decoder.framegenSurface = cap.surface
        val gen = generation.incrementAndGet()
        enqueue {
            val started = SystemClock.uptimeMillis()
            FramegenInterceptor.configureOutputSurface(outputSurface)
            val ok = FramegenInterceptor.prewarmContext(width, height)
            if (generation.get() == gen) decoder.setFramegenCaptureSwitchReady(ok)
            LimeLog.info("Framegen prewarm ok=$ok in ${SystemClock.uptimeMillis() - started} ms")
        }
        _status.value = FramegenStatus(
            state = if (userPaused) FramegenStatus.State.PAUSED else FramegenStatus.State.STARTING,
            reason = if (userPaused) FramegenOffReason.USER_PAUSED else null,
            targetFps = rt.presentationFps,
            multiplier = if (rt.inputFps > 0) rt.presentationFps / rt.inputFps else 1,
            model = modelLabel(rt),
        )
        LimeLog.info("Framegen armed ${width}x$height ${rt.inputFps}->${rt.presentationFps} fps, internal ${rt.internalWidth}, ${modelLabel(rt)}")
        return true
    }

    /** The SurfaceView surface changed under a running capture (same stream). */
    fun onOutputSurface(surface: Surface?) {
        if (capture == null) return
        enqueue { FramegenInterceptor.configureOutputSurface(surface) }
    }

    /** Drops the capture; the decoder must be paused or about to be rebuilt on a direct surface. */
    fun release(decoder: MediaCodecDecoderRenderer?) {
        generation.incrementAndGet()
        restartWatch.cancel()
        val had = capture != null
        capture?.release()
        capture = null
        runtime = null
        adaptive.reset()
        decoder?.setFramegenCaptureSwitchReady(false)
        decoder?.framegenSurface = null
        if (had) enqueue { FramegenInterceptor.configureOutputSurface(null) }
    }

    /** Stops the executor; call when the stream ends. */
    fun shutdown(decoder: MediaCodecDecoderRenderer?) {
        release(decoder)
        surfaceExecutor.shutdown()
    }

    /**
     * Quick toggle while armed: pauses generation instantly (decoded frames are still shown
     * through the pipeline) or resumes it. Not allowed to undo an auto-off without [force].
     */
    fun setPaused(paused: Boolean, force: Boolean = false): Boolean {
        if (autoOff != null && !paused && !force) return false
        if (!paused) autoOff = null
        userPaused = paused
        val rt = runtime ?: return true
        FramegenInterceptor.setGenerationPaused(paused)
        FramegenInterceptor.configureOutputFrameRate(if (paused) rt.inputFps else rt.presentationFps)
        if (!paused) guard.reset()
        _status.update {
            it.copy(
                state = if (paused) FramegenStatus.State.PAUSED else FramegenStatus.State.STARTING,
                reason = if (paused) FramegenOffReason.USER_PAUSED else null,
                autoOffDetail = null,
            )
        }
        return true
    }

    /**
     * Settings changed during the stream ([LiveSettings]). While armed and still planned, the new
     * model and tuning go to the native pipeline and its LSFG context is rebuilt on the frame
     * generation thread, behind the same capture and surface; the decoder is not touched. While
     * armed but no longer planned, generation pauses until the next stream. Main thread.
     */
    fun applyLive(width: Int, height: Int, fps: Int): LiveSettings.Framegen {
        val plan = plan(width, height, fps)
        val action = LiveSettings.framegen(isArmed, plan is FramegenPlan.On)
        when (action) {
            LiveSettings.Framegen.RECONFIGURE -> reconfigure((plan as FramegenPlan.On).runtime, width, height)
            LiveSettings.Framegen.PAUSE_UNTIL_NEXT_STREAM -> {
                setPaused(true)
                onEvent(FramegenEvent.NextStream("Frame generation is paused and fully off from the next stream."))
            }
            LiveSettings.Framegen.NEXT_STREAM -> onEvent(FramegenEvent.NextStream("Frame generation starts with the next stream."))
            LiveSettings.Framegen.NONE -> Unit
        }
        LimeLog.info("Framegen live settings: $action")
        return action
    }

    private fun reconfigure(rt: FramegenRuntime, width: Int, height: Int) {
        val old = runtime ?: return
        runtime = rt
        adaptive.configure(
            FramegenAdaptiveController.Config(
                inputFps = rt.inputFps,
                presentationFps = rt.presentationFps,
                adaptiveEnabled = rt.adaptive,
                allowAdaptiveWithoutDoubling = rt.adaptiveOnly,
                internalWidth = rt.internalWidth,
                presentMode = rt.presentMode,
                slowFrameThresholdMs = rt.slowThresholdMs,
                presentQueueMax = rt.presentQueueMax,
            ),
        )
        guard = ThermalGuard(enabled = rt.thermalGuard)
        val paused = userPaused || autoOff != null
        val modelChanged = old.flowScale != rt.flowScale || old.performanceMode != rt.performanceMode || old.internalWidth != rt.internalWidth
        val gen = generation.get()
        // Same thread as prewarm and the output-surface changes, so they never overlap.
        enqueue {
            if (generation.get() != gen) return@enqueue // released meanwhile
            FramegenInterceptor.configureLsfgModel(rt.flowScale, rt.performanceMode)
            FramegenInterceptor.configureTuning(rt.internalWidth, rt.presentMode, rt.slowThresholdMs, rt.presentQueueMax, rt.adaptiveOnly)
            FramegenInterceptor.configureOutputFrameRate(if (paused) rt.inputFps else rt.presentationFps)
            if (modelChanged) {
                // The native reset stops the presenter and drops the LSFG context under the
                // pipeline lock (frames in flight wait for it); prewarm builds the new one here
                // instead of on the decoder's frame thread.
                val started = SystemClock.uptimeMillis()
                FramegenInterceptor.resetPipeline()
                val ok = FramegenInterceptor.prewarmContext(width, height)
                LimeLog.info("Framegen reconfigured (${modelLabel(rt)}) prewarm ok=$ok in ${SystemClock.uptimeMillis() - started} ms")
            }
        }
        if (modelChanged && !paused) restartWatch.started(SystemClock.elapsedRealtime())
        _status.update { it.copy(targetFps = rt.presentationFps, model = modelLabel(rt), state = if (paused) it.state else FramegenStatus.State.STARTING) }
    }

    /** Resets session-level state (auto-off, user pause) for a new stream. */
    fun newSession() {
        autoOff = null
        userPaused = false
        guard.reset()
    }

    fun configureHdr(mode: Int, fullRange: Boolean) = FramegenInterceptor.configureHdrMode(mode, fullRange)

    fun onFrameLoss(framesLost: Int, frameNumber: Int) {
        if (capture != null) adaptive.onFrameLossEvent(framesLost, frameNumber)
    }

    /** Called about once a second with the decoder's window stats; fills the framegen fields. */
    fun onPerformanceInfo(info: PerformanceInfo) {
        val rt = runtime
        val armed = capture != null && rt != null
        if (armed) adaptive.onPerformanceInfo(info)
        FramegenPerformanceEnricher.update(
            info,
            framegenActive = armed,
            baseFps = rt?.inputFps ?: 0,
            outputFps = adaptive.activePresentationFps.takeIf { it > 0 } ?: rt?.presentationFps ?: 0,
        )
        if (!armed || rt == null) return

        val now = SystemClock.elapsedRealtime()
        val windowMs = if (lastWindowMs == 0L) 1000L else (now - lastWindowMs).coerceIn(1, 5000)
        lastWindowMs = now
        val paused = userPaused || autoOff != null
        val generating = info.framegenFps > 0.5f && info.framegenMode != FramegenStats.MODE_PAUSED
        val presented = if (generating) info.framegenFps else info.renderedFps
        val inputFps = if (info.framegenInputFps > 0f) info.framegenInputFps else info.renderedFps
        val budgetMs = 1000f / rt.inputFps.coerceAtLeast(1)

        if (!paused) {
            if (generating) soak?.onWindow(presented, windowMs)
            val decision = guard.onSample(
                GuardSample(
                    thermalHeadroom = thermalHeadroom(),
                    thermalStatus = thermalStatus(),
                    presentedFps = if (generating) presented else 0f,
                    targetFps = rt.presentationFps,
                    lsfgMs = info.framegenLsfgWaitMs,
                    budgetMs = budgetMs,
                ),
            )
            if (decision is GuardDecision.TurnOff) tripAutoOff(decision, rt)
        }

        if (restartWatch.onWindow(now, generating, paused)) {
            LimeLog.warning("Framegen didn't restart with the new settings within ${LiveSettings.RESTART_TIMEOUT_MS} ms; pausing")
            setPaused(true)
            onEvent(FramegenEvent.RestartFailed)
        }

        val state = when {
            autoOff != null -> FramegenStatus.State.AUTO_OFF
            userPaused -> FramegenStatus.State.PAUSED
            generating -> FramegenStatus.State.ACTIVE
            else -> FramegenStatus.State.STARTING
        }
        _status.update {
            it.copy(
                state = state,
                reason = when (state) {
                    FramegenStatus.State.AUTO_OFF -> FramegenOffReason.THERMAL
                    FramegenStatus.State.PAUSED -> FramegenOffReason.USER_PAUSED
                    else -> null
                },
                presentedFps = presented,
                inputFps = inputFps,
                lsfgMs = info.framegenLsfgWaitMs,
                blitMs = info.framegenBlitMs,
                addedLatencyMs = if (generating) 0.5f * 1000f / inputFps.coerceAtLeast(1f) + info.framegenLsfgWaitMs + info.framegenBlitMs else 0f,
                soak = soak?.takeIf { s -> s.windows > 0 }?.summary(),
            )
        }
        if (soak != null && soak!!.windows > 0 && soak!!.windows % 60 == 0) LimeLog.info("Framegen soak ${soak!!.summary()}")
    }

    private fun tripAutoOff(decision: GuardDecision.TurnOff, rt: FramegenRuntime) {
        autoOff = decision
        FramegenInterceptor.setGenerationPaused(true)
        FramegenInterceptor.configureOutputFrameRate(rt.inputFps)
        LimeLog.warning("Framegen auto-off (${decision.cause}): ${decision.detail}")
        _status.update { it.copy(state = FramegenStatus.State.AUTO_OFF, reason = FramegenOffReason.THERMAL, autoOffDetail = decision.detail) }
        onEvent(FramegenEvent.AutoOff(decision.cause, decision.detail))
    }

    private fun thermalHeadroom(): Float? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) runCatching { power?.getThermalHeadroom(THERMAL_FORECAST_S) }.getOrNull() else null

    private fun thermalStatus(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus ?: -1 else -1

    private fun enqueue(task: () -> Unit) {
        try {
            surfaceExecutor.execute(task)
        } catch (e: RejectedExecutionException) {
            LimeLog.warning("Framegen surface task after shutdown ignored")
        }
    }

    private fun modelLabel(rt: FramegenRuntime) =
        (if (rt.performanceMode) "LSFG 3.1 performance" else "LSFG 3.1") + " · flow " + (rt.flowScale * 100).toInt() + "%"

    companion object {
        /** Seconds ahead getThermalHeadroom() forecasts. */
        const val THERMAL_FORECAST_S = 10
    }
}
