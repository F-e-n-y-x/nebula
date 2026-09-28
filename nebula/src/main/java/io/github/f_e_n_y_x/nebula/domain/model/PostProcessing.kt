package io.github.f_e_n_y_x.nebula.domain.model

/** Frame generation and upscaling in the running stream, for the overlay and the stream menu. */
data class PostProcessStats(
    val framegen: FramegenState = FramegenState.OFF,
    /** Frames shown per second (generated included); 0 when frame generation isn't generating. */
    val presentedFps: Float = 0f,
    /** Frames decoded per second going into frame generation. */
    val inputFps: Float = 0f,
    val targetFps: Int = 0,
    val multiplier: Int = 2,
    val lsfgMs: Int = 0,
    /** Estimated latency frame generation adds, ms and input frames. */
    val addedLatencyMs: Float = 0f,
    val addedLatencyFrames: Float = 0f,
    val model: String = "",
    /** Why it's off or paused, when it is. */
    val note: String? = null,
    /** Long-session numbers ("10:02 · 99% ≥115 fps · min 109"). */
    val soak: String? = null,
    /** Upscaler running now ("off", "sharpen", "sgsr1", "fsr1"), its label and draw time. */
    val upscaler: String = "off",
    val upscalerLabel: String = "Off",
    val upscaleMs: Float = 0f,
    val upscaleOut: String = "",
) {
    /** Frame generation is set up for this stream, so the quick toggle can pause and resume it. */
    val framegenArmed: Boolean get() = framegen != FramegenState.OFF
}

enum class FramegenState { OFF, STARTING, ACTIVE, PAUSED, AUTO_OFF }
