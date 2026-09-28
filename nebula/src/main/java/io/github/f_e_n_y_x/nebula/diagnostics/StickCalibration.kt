package io.github.f_e_n_y_x.nebula.diagnostics

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * How one analog stick's raw position becomes what the host sees. Values are in stick units
 * (-1..1 per axis, 1 = the edge Android reports).
 *
 * - [centerX]/[centerY]: where this stick rests; a worn stick drifts off zero, and the offset is
 *   subtracted before anything else.
 * - [deadzone]: radius around the centre that reads as released.
 * - [outer]: radius that already counts as fully pushed. Many pads never reach 1.0 on the
 *   diagonals or at all; a smaller edge lets them.
 * - [antiDeadzone]: the smallest push the host sees once the stick leaves the deadzone. Games that
 *   add their own deadzone on top of ours feel dead near the centre; this skips past theirs.
 *
 * The response is radial, so the direction of a push never changes, only its length.
 */
data class StickShape(
    val deadzone: Float = DEFAULT_DEADZONE,
    val outer: Float = 1f,
    val antiDeadzone: Float = 0f,
    val centerX: Float = 0f,
    val centerY: Float = 0f,
) {
    /** The same shape, pulled back into ranges that always give a usable stick. */
    fun sanitized(): StickShape {
        val dz = deadzone.finiteOr(DEFAULT_DEADZONE).coerceIn(0f, MAX_DEADZONE)
        return StickShape(
            deadzone = dz,
            // Keep a usable travel between the deadzone and the edge.
            outer = outer.finiteOr(1f).coerceIn(dz + MIN_TRAVEL, 1f),
            antiDeadzone = antiDeadzone.finiteOr(0f).coerceIn(0f, MAX_ANTI_DEADZONE),
            centerX = centerX.finiteOr(0f).coerceIn(-MAX_CENTER, MAX_CENTER),
            centerY = centerY.finiteOr(0f).coerceIn(-MAX_CENTER, MAX_CENTER),
        )
    }

    /**
     * Maps a raw position to the output position, both in -1..1 per axis. Inside the deadzone the
     * result is exactly (0, 0).
     */
    fun apply(rawX: Float, rawY: Float): Pair<Float, Float> {
        val s = this
        val x = rawX - s.centerX
        val y = rawY - s.centerY
        val r = hypot(x, y)
        if (!r.isFinite() || r <= s.deadzone) return 0f to 0f
        val travel = (s.outer - s.deadzone).coerceAtLeast(MIN_TRAVEL)
        val t = ((r - s.deadzone) / travel).coerceIn(0f, 1f)
        val out = s.antiDeadzone + (1f - s.antiDeadzone) * t
        val k = out / r
        return (x * k).coerceIn(-1f, 1f) to (y * k).coerceIn(-1f, 1f)
    }

    companion object {
        /** Nebula's default deadzone (V+'s 7 %). */
        const val DEFAULT_DEADZONE = 0.07f
        const val MAX_DEADZONE = 0.5f
        const val MAX_ANTI_DEADZONE = 0.5f
        const val MAX_CENTER = 0.3f
        const val MIN_TRAVEL = 0.05f
    }
}

/** A saved calibration for one physical controller, keyed by its stable [descriptor]. */
data class StickCalibration(
    /** InputDevice.getDescriptor: stable across reconnects and reboots for the same pad. */
    val descriptor: String,
    /** The pad's name when it was saved, for lists. */
    val name: String,
    val left: StickShape = StickShape(),
    val right: StickShape = StickShape(),
) {
    fun sanitized() = copy(left = left.sanitized(), right = right.sanitized())

    /**
     * One line per calibration: descriptor, name and ten numbers, tab separated. Names can hold
     * anything, so tabs and newlines in them are replaced.
     */
    fun encode(): String = listOf(
        descriptor.clean(), name.clean(),
        left.deadzone, left.outer, left.antiDeadzone, left.centerX, left.centerY,
        right.deadzone, right.outer, right.antiDeadzone, right.centerX, right.centerY,
    ).joinToString("\t")

    companion object {
        /** Parses [encode]'s output; null for anything malformed. */
        fun decode(line: String): StickCalibration? {
            val p = line.split('\t')
            if (p.size != 12 || p[0].isBlank()) return null
            val n = p.drop(2).map { it.toFloatOrNull() ?: return null }
            return StickCalibration(
                descriptor = p[0], name = p[1],
                left = StickShape(n[0], n[1], n[2], n[3], n[4]),
                right = StickShape(n[5], n[6], n[7], n[8], n[9]),
            ).sanitized()
        }

        fun encodeAll(all: Collection<StickCalibration>): String = all.joinToString("\n") { it.encode() }

        fun decodeAll(text: String?): List<StickCalibration> =
            text.orEmpty().lineSequence().filter { it.isNotBlank() }.mapNotNull { decode(it) }.toList()

        private fun String.clean() = replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
    }
}

/**
 * Measures where a stick rests: feed it raw samples while the stick is untouched, then [result]
 * gives the average position and a deadzone that covers the jitter seen, with a margin.
 */
class CenterSampler {
    private var n = 0
    private var sumX = 0.0
    private var sumY = 0.0
    private var sumSq = 0.0
    private val xs = ArrayList<Float>()
    private val ys = ArrayList<Float>()

    val count: Int get() = n

    fun add(x: Float, y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        n++
        sumX += x
        sumY += y
        sumSq += (x * x + y * y).toDouble()
        xs += x
        ys += y
    }

    data class Result(
        val centerX: Float,
        val centerY: Float,
        /** Largest distance of any sample from the measured centre. */
        val spread: Float,
        /** A deadzone that hides the spread and the drift: spread + margin, never below the default. */
        val suggestedDeadzone: Float,
        /** True when the stick clearly moved while sampling (the result isn't trustworthy). */
        val moved: Boolean,
    )

    /** Null until at least [MIN_SAMPLES] samples arrived. */
    fun result(): Result? {
        if (n < MIN_SAMPLES) return null
        val cx = (sumX / n).toFloat()
        val cy = (sumY / n).toFloat()
        var spread = 0f
        for (i in xs.indices) spread = max(spread, hypot(xs[i] - cx, ys[i] - cy))
        val moved = spread > MOVED_SPREAD
        // Offsets smaller than the jitter are noise: don't store them.
        val keepCenter = hypot(cx, cy) > NOISE
        val drift = if (keepCenter) 0f else hypot(cx, cy)
        val dz = (spread + drift + MARGIN).coerceIn(StickShape.DEFAULT_DEADZONE, StickShape.MAX_DEADZONE)
        return Result(
            centerX = if (keepCenter) cx.coerceIn(-StickShape.MAX_CENTER, StickShape.MAX_CENTER) else 0f,
            centerY = if (keepCenter) cy.coerceIn(-StickShape.MAX_CENTER, StickShape.MAX_CENTER) else 0f,
            spread = spread,
            suggestedDeadzone = round2(dz),
            moved = moved,
        )
    }

    /** RMS distance from zero of every sample; handy for showing noise. */
    fun rms(): Float = if (n == 0) 0f else sqrt(sumSq / n).toFloat()

    companion object {
        /** About 1 s of events from a pad reporting at 60 Hz or more. */
        const val MIN_SAMPLES = 20
        const val MOVED_SPREAD = 0.25f
        const val NOISE = 0.02f
        const val MARGIN = 0.03f
    }
}

private fun Float.finiteOr(d: Float) = if (isFinite()) this else d

private fun round2(v: Float): Float = (kotlin.math.round(v * 100f) / 100f).let { if (abs(it) < 1e-6f) 0f else it }
