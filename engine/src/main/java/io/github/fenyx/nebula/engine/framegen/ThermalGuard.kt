package io.github.fenyx.nebula.engine.framegen

import java.util.Locale

/**
 * One stats window (about a second) while frame generation runs.
 *
 * [thermalHeadroom] is PowerManager.getThermalHeadroom(): 0 = cool, 1.0 = the device reaches
 * THERMAL_STATUS_SEVERE; NaN or null when the device doesn't report it. [thermalStatus] is
 * PowerManager.THERMAL_STATUS_* (0 = none .. 6 = shutdown), or -1 when unknown.
 */
data class GuardSample(
    val thermalHeadroom: Float?,
    val thermalStatus: Int,
    val presentedFps: Float,
    val targetFps: Int,
    /** LSFG GPU time of the last generated frame. */
    val lsfgMs: Int,
    /** Time LSFG may take per input frame: 1000 / input fps. */
    val budgetMs: Float,
)

sealed interface GuardDecision {
    data object Keep : GuardDecision
    data class TurnOff(val cause: Cause, val detail: String) : GuardDecision
    enum class Cause { THERMAL, SLOW }
}

/**
 * Turns frame generation off for the rest of a session when the phone gets too hot or can't keep
 * up, so a long session degrades to plain streaming instead of throttling.
 *
 * - Thermal: status SEVERE or worse trips at once; a headroom forecast of [HEADROOM_TRIP] or more
 *   (less than 0.2 left before SEVERE) trips after [HOT_WINDOWS] windows in a row.
 * - Slow: after [WARMUP_WINDOWS], presented fps under [SLOW_FPS_SHARE] of the target, or LSFG
 *   over its budget, for [SLOW_WINDOWS] windows in a row.
 *
 * Once tripped it stays tripped until [reset] (a new stream). Not thread-safe; feed it from one thread.
 */
class ThermalGuard(private val enabled: Boolean = true) {
    private var windows = 0
    private var hot = 0
    private var slow = 0

    var tripped: GuardDecision.TurnOff? = null
        private set

    fun reset() {
        windows = 0
        hot = 0
        slow = 0
        tripped = null
    }

    fun onSample(s: GuardSample): GuardDecision {
        tripped?.let { return it }
        if (!enabled) return GuardDecision.Keep
        windows++

        val headroom = s.thermalHeadroom?.takeIf { !it.isNaN() }
        if (s.thermalStatus >= THERMAL_STATUS_SEVERE) {
            return trip(GuardDecision.Cause.THERMAL, "thermal status ${statusName(s.thermalStatus)}")
        }
        hot = if (headroom != null && headroom >= HEADROOM_TRIP) hot + 1 else 0
        if (hot >= HOT_WINDOWS) {
            return trip(GuardDecision.Cause.THERMAL, String.format(Locale.US, "thermal headroom %.2f left", (1f - headroom!!).coerceAtLeast(0f)))
        }

        if (windows > WARMUP_WINDOWS && s.targetFps > 0) {
            val fpsLow = s.presentedFps > 0f && s.presentedFps < s.targetFps * SLOW_FPS_SHARE
            val lsfgSlow = s.lsfgMs > 0 && s.budgetMs > 0f && s.lsfgMs > s.budgetMs
            slow = if (fpsLow || lsfgSlow) slow + 1 else 0
            if (slow >= SLOW_WINDOWS) {
                val why = if (lsfgSlow) "generated frames take ${s.lsfgMs} ms (budget ${s.budgetMs.toInt()} ms)"
                else String.format(Locale.US, "%.0f of %d fps presented", s.presentedFps, s.targetFps)
                return trip(GuardDecision.Cause.SLOW, why)
            }
        }
        return GuardDecision.Keep
    }

    private fun trip(cause: GuardDecision.Cause, detail: String): GuardDecision.TurnOff =
        GuardDecision.TurnOff(cause, detail).also { tripped = it }

    companion object {
        const val THERMAL_STATUS_SEVERE = 3
        /** getThermalHeadroom() at or above this leaves less than 0.2 before SEVERE. */
        const val HEADROOM_TRIP = 0.8f
        const val HOT_WINDOWS = 3
        const val WARMUP_WINDOWS = 5
        const val SLOW_WINDOWS = 8
        const val SLOW_FPS_SHARE = 0.85f

        fun statusName(status: Int) = when (status) {
            0 -> "none"; 1 -> "light"; 2 -> "moderate"; 3 -> "severe"; 4 -> "critical"; 5 -> "emergency"; 6 -> "shutdown"
            else -> "unknown"
        }
    }
}

/**
 * Keeps the acceptance numbers for a frame generation session: how long it ran and how many
 * one-second windows presented at least [thresholdFps] (the roadmap's 115 fps at 60→120).
 */
class SoakRecorder(private val thresholdFps: Float) {
    var windows = 0
        private set
    var windowsAtTarget = 0
        private set
    var minFps = Float.NaN
        private set
    var elapsedMs = 0L
        private set

    fun onWindow(presentedFps: Float, windowMs: Long) {
        if (presentedFps <= 0f) return
        windows++
        elapsedMs += windowMs
        if (presentedFps >= thresholdFps) windowsAtTarget++
        minFps = if (minFps.isNaN()) presentedFps else minOf(minFps, presentedFps)
    }

    val shareAtTarget: Float get() = if (windows == 0) 0f else windowsAtTarget.toFloat() / windows

    /** "10:02 · 99% ≥115 fps · min 109" */
    fun summary(): String {
        val s = elapsedMs / 1000
        return String.format(Locale.US, "%d:%02d · %.0f%% ≥%.0f fps · min %.0f", s / 60, s % 60, shareAtTarget * 100, thresholdFps, if (minFps.isNaN()) 0f else minFps)
    }

    companion object {
        /** 115 of 120: the roadmap target, scaled to other presentation rates. */
        fun forTarget(presentationFps: Int) = SoakRecorder(presentationFps * (115f / 120f))
    }
}
