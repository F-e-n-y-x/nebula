package io.github.fenyx.nebula.engine.framegen

/** Why frame generation isn't running for a stream. */
enum class FramegenOffReason(val message: String) {
    DISABLED("Frame generation is off."),
    NO_ENGINE("Frame generation needs Lossless.dll. Import it in Settings › Frame generation."),
    UNSUPPORTED("This device can't run frame generation."),
    NOT_TESTED("Frame generation is waiting for its device check. Run it in Settings › Frame generation."),
    SELF_TEST_FAILED("Frame generation failed its device check on this device."),
    FPS_TOO_HIGH("Frame generation needs a stream of 60 fps or less (it doubles the rate)."),
    RESOLUTION_TOO_LARGE("The stream resolution is too large for frame generation."),
    THERMAL("Frame generation turned off: the device was too hot or too slow."),
    USER_PAUSED("Frame generation is paused."),
}

/** What the native pipeline is told for one stream; mirrors V+'s FramegenRuntimeConfig. */
data class FramegenRuntime(
    val dllPath: String,
    val inputFps: Int,
    val presentationFps: Int,
    val adaptive: Boolean,
    /** Adaptive fill without regular doubling (stream fps above 60). */
    val adaptiveOnly: Boolean,
    val internalWidth: Int,
    val presentMode: Int,
    val slowThresholdMs: Int,
    val presentQueueMax: Int,
    val flowScale: Float,
    val performanceMode: Boolean,
    val thermalGuard: Boolean,
)

sealed interface FramegenPlan {
    data class Off(val reason: FramegenOffReason) : FramegenPlan
    data class On(val runtime: FramegenRuntime) : FramegenPlan
}

/**
 * Decides whether a stream uses frame generation. Same rules as V+'s FramegenRuntimePlanner:
 * regular doubling needs a stream of at most 60 fps, "weak-network fill" works at any rate,
 * and the capture is limited to 2560x2560 pixels. Nebula adds the self-test gate.
 */
object FramegenPlanner {
    const val MAX_DOUBLING_INPUT_FPS = 60
    const val MAX_CAPTURE_PIXELS = 2560L * 2560L
    const val DEFAULT_PRESENT_QUEUE_MAX = 2

    fun plan(
        config: FramegenConfig,
        width: Int,
        height: Int,
        inputFps: Int,
        nativeAvailable: Boolean,
        selfTest: SelfTestVerdict,
    ): FramegenPlan {
        if (!config.enabled && !config.adaptive) return FramegenPlan.Off(FramegenOffReason.DISABLED)
        if (!nativeAvailable) return FramegenPlan.Off(FramegenOffReason.UNSUPPORTED)
        val dll = config.dllPath ?: return FramegenPlan.Off(FramegenOffReason.NO_ENGINE)
        when (selfTest) {
            SelfTestVerdict.PASSED, SelfTestVerdict.SLOW -> Unit
            SelfTestVerdict.NOT_RUN -> return FramegenPlan.Off(FramegenOffReason.NOT_TESTED)
            SelfTestVerdict.UNSUPPORTED -> return FramegenPlan.Off(FramegenOffReason.UNSUPPORTED)
            SelfTestVerdict.FAILED -> return FramegenPlan.Off(FramegenOffReason.SELF_TEST_FAILED)
        }
        if (width <= 0 || height <= 0 || width.toLong() * height.toLong() > MAX_CAPTURE_PIXELS) {
            return FramegenPlan.Off(FramegenOffReason.RESOLUTION_TOO_LARGE)
        }
        val regular = config.enabled && inputFps in 1..MAX_DOUBLING_INPUT_FPS
        if (!regular && !config.adaptive) return FramegenPlan.Off(FramegenOffReason.FPS_TOO_HIGH)
        return FramegenPlan.On(
            FramegenRuntime(
                dllPath = dll,
                inputFps = inputFps,
                presentationFps = if (regular) inputFps * config.multiplier else inputFps,
                adaptive = config.adaptive,
                adaptiveOnly = config.adaptive && !regular,
                internalWidth = config.internalWidth(width),
                presentMode = if (config.presentRealFirst) 1 else 0,
                slowThresholdMs = config.slowThresholdMs,
                presentQueueMax = DEFAULT_PRESENT_QUEUE_MAX,
                flowScale = config.flowScale,
                performanceMode = config.performanceMode,
                thermalGuard = config.thermalGuard,
            ),
        )
    }

    /** The display rate a stream wants: doubled while regular frame generation will run. */
    fun presentationFps(plan: FramegenPlan, inputFps: Int): Int =
        (plan as? FramegenPlan.On)?.runtime?.presentationFps ?: inputFps
}
