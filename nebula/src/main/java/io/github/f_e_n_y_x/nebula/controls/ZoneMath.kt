package io.github.f_e_n_y_x.nebula.controls

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Camera zone → stick: finger *speed* becomes stick deflection (like a mouse driving a stick),
 * so a slow drag turns slowly and a flick turns fast. Deflection eases back to centre when the
 * finger stops, and is zero on release.
 *
 * [sensitivity] scales the speed that gives full deflection ([FULL_SPEED_DP_S] at 1×);
 * [acceleration] is the response-curve exponent (1 = linear). Output y is up-positive, like a
 * stick; [invertY] flips it.
 */
class CameraStick(
    private val sensitivity: Float,
    private val acceleration: Float,
    private val invertY: Boolean,
) {
    var x = 0f
        private set
    var y = 0f
        private set

    /** A finger movement of ([dxDp], [dyDp]) over [dtMs]; returns the new deflection. */
    fun move(dxDp: Float, dyDp: Float, dtMs: Long): Pair<Float, Float> {
        val dt = dtMs.coerceIn(4L, 60L).toFloat()
        val vx = dxDp / dt * 1000f
        val vy = dyDp / dt * 1000f
        val speed = hypot(vx, vy)
        if (speed < 1f) return idle(dtMs)
        val n = speed * sensitivity / FULL_SPEED_DP_S
        val mag = min(1f, n.pow(acceleration))
        val tx = vx / speed * mag
        var ty = -vy / speed * mag
        if (invertY) ty = -ty
        x += (tx - x) * SMOOTHING
        y += (ty - y) * SMOOTHING
        return x to y
    }

    /** No movement for [msSinceMove]: ease back towards centre. */
    fun idle(msSinceMove: Long): Pair<Float, Float> {
        if (msSinceMove >= HOLD_MS) {
            x *= DECAY; y *= DECAY
            if (abs(x) < 0.02f) x = 0f
            if (abs(y) < 0.02f) y = 0f
        }
        return x to y
    }

    fun release() { x = 0f; y = 0f }

    companion object {
        const val FULL_SPEED_DP_S = 900f
        const val SMOOTHING = 0.55f
        /** A pause shorter than this (between touch samples) keeps the deflection. */
        const val HOLD_MS = 34L
        const val DECAY = 0.45f
        /** How often the zone checks for a stopped finger. */
        const val TICK_MS = 16L
    }
}

/**
 * Camera zone → mouse: relative movement with [sensitivity] and a speed-based [acceleration]
 * (1 = none). Sub-pixel remainders carry over so slow drags still move.
 */
class CameraMouse(private val sensitivity: Float, private val acceleration: Float, private val invertY: Boolean) {
    private var remX = 0f
    private var remY = 0f

    fun move(dxPx: Float, dyPx: Float, dtMs: Long, density: Float): Pair<Int, Int> {
        val dt = dtMs.coerceIn(4L, 60L).toFloat()
        val speedDp = hypot(dxPx, dyPx) / density / dt * 1000f
        // Gain grows with speed when acceleration > 1, up to 2× the extra at 2000 dp/s.
        val gain = sensitivity * (1f + (acceleration - 1f) * min(speedDp / 1000f, 2f))
        val fx = dxPx * gain + remX
        val fy = (if (invertY) -dyPx else dyPx) * gain + remY
        val ix = fx.roundToInt()
        val iy = fy.roundToInt()
        remX = fx - ix
        remY = fy - iy
        return ix to iy
    }
}
