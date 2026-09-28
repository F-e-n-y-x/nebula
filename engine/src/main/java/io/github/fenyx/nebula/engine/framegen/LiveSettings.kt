package io.github.fenyx.nebula.engine.framegen

/**
 * What a frame generation / upscaler settings change does to a running stream.
 *
 * The dev10 crash: the first version rebuilt the decoder on the same SurfaceView (pause, re-arm,
 * resume). The frame generation presenter had connected to that surface as a CPU producer
 * (ANativeWindow_lock), and releasing the native window doesn't disconnect it: the Java Surface
 * is the same object. MediaCodec.configure() on it then failed ("already connected"), the decoder
 * was left null, and resumeProcessing() → start() hit `videoDecoder!!` on the main thread.
 *
 * So a live change never rebuilds the decoder or changes which producer owns the screen:
 * - frame generation set up and still planned: reconfigure the model natively (new LSFG context
 *   on the frame generation thread), same capture, same surface;
 * - frame generation set up but now off (disabled, fps too high…): pause it; fully off next stream;
 * - frame generation not set up but now wanted: from the next stream;
 * - upscaler running: new mode / strength applied per frame (Off becomes a plain copy until the
 *   next stream);
 * - the upscaler turning on while not running: from the next stream.
 * Pure, so the rules are unit-tested.
 */
object LiveSettings {
    enum class Framegen { RECONFIGURE, PAUSE_UNTIL_NEXT_STREAM, NEXT_STREAM, NONE }
    enum class Upscaler { UPDATE, NEXT_STREAM, NONE }

    fun framegen(armed: Boolean, planOn: Boolean): Framegen = when {
        armed && planOn -> Framegen.RECONFIGURE
        armed -> Framegen.PAUSE_UNTIL_NEXT_STREAM
        planOn -> Framegen.NEXT_STREAM
        else -> Framegen.NONE
    }

    fun upscaler(framegenArmed: Boolean, upscalerArmed: Boolean, mode: UpscalerMode): Upscaler = when {
        // The upscaler is off while frame generation is set up; the saved choice waits.
        framegenArmed -> Upscaler.NONE
        // Running: any mode, Off included (a plain bilinear copy), is applied per frame.
        upscalerArmed -> Upscaler.UPDATE
        mode != UpscalerMode.OFF -> Upscaler.NEXT_STREAM
        else -> Upscaler.NONE
    }

    /** Slider drags commit on release; key steps and quick taps settle before one reconfigure. */
    const val DEBOUNCE_MS = 900L

    /**
     * After a reconfigure, generation must resume within [RESTART_TIMEOUT_MS]. If the new LSFG
     * context can't start (init failure), frame generation is paused and the user told, instead
     * of leaving a stalled picture.
     */
    const val RESTART_TIMEOUT_MS = 6_000L

    /** Tracks one reconfigure until generated frames come back, or it times out. */
    class RestartWatch(private val timeoutMs: Long = RESTART_TIMEOUT_MS) {
        @Volatile private var since: Long? = null

        val waiting: Boolean get() = since != null

        fun started(nowMs: Long) { since = nowMs }

        fun cancel() { since = null }

        /** One stats window: true when the restart failed (and the watch ends). */
        fun onWindow(nowMs: Long, generating: Boolean, paused: Boolean): Boolean {
            val t = since ?: return false
            if (generating || paused) { since = null; return false }
            if (nowMs - t < timeoutMs) return false
            since = null
            return true
        }
    }
}
