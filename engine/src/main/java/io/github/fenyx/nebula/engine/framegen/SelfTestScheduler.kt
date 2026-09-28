package io.github.fenyx.nebula.engine.framegen

/**
 * Decides when the frame generation device check may run. It never runs during a stream. The
 * benchmark keeps the GPU at full load for several seconds, and translating Lossless.dll takes
 * hundreds of MB. A GPU fault or reset during the check can take the live decoder and frame
 * generation pipeline down with it, which ended streams on an S25 Ultra (dev9.1).
 *
 * - A request during a stream is refused; [runAfterStream] queues it for the end of the stream.
 * - A check still running when a stream starts is stopped and queued for after the stream.
 *
 * Pure (no Android), so the rules are unit-tested. [FramegenSelfTestRunner] owns one.
 */
class SelfTestScheduler {
    /** A check at LSFG's internal size for a [width]x[height] stream. */
    data class Request(val width: Int, val height: Int)

    enum class Outcome {
        /** Start the check now. */
        START,
        /** A check is already running; nothing to do. */
        ALREADY_RUNNING,
        /** A stream is running; the check was not started. */
        REFUSED_STREAMING,
    }

    private var streams = 0

    /** The check to run when the stream ends, if one was asked for. */
    var pending: Request? = null
        private set

    val streaming: Boolean get() = streams > 0

    fun request(running: Boolean): Outcome = when {
        running -> Outcome.ALREADY_RUNNING
        streaming -> Outcome.REFUSED_STREAMING
        else -> Outcome.START
    }

    /** Queues [r] for the end of the stream. False when no stream is running: start it now instead. */
    fun runAfterStream(r: Request): Boolean {
        if (!streaming) return false
        pending = r
        return true
    }

    fun cancelPending() {
        pending = null
    }

    /**
     * A stream starts. [runningCheck] is the check in progress, if any. Returns true when that
     * check must be stopped now. It is queued to run again after the stream.
     */
    fun streamStarted(runningCheck: Request?): Boolean {
        streams++
        if (runningCheck == null) return false
        if (pending == null) pending = runningCheck
        return true
    }

    /** A stream ended. Returns the queued check to start now, or null (none, or another stream still running). */
    fun streamEnded(): Request? {
        streams = (streams - 1).coerceAtLeast(0)
        if (streaming) return null
        return pending.also { pending = null }
    }
}
