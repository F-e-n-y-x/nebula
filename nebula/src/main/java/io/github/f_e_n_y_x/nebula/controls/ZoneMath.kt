package io.github.f_e_n_y_x.nebula.controls

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Camera → stick, swipe-to-look driven by a fixed tick rather than by touch events, so it feels
 * the same on 60, 120 and 240 Hz panels. Finger movement ([add], dp) is accumulated; every
 * [tick] turns the swipe speed since the last tick into a right-stick push:
 *
 * - under [MIN_SPEED_DP_S] is jitter and counts as still;
 * - full push at [FULL_SPEED_DP_S] × [sensitivity], shaped by [curve] (1 = linear), then lifted
 *   past [antiDeadzone] so slow swipes clear the game's own stick deadzone (GTA V ≈ 20 %);
 * - the push rises with τ [TAU_RISE_MS]; a finger that stops keeps it for [HOLD_MS] (one missed
 *   60 Hz sample) and then it decays with τ [TAU_DECAY_MS] to exactly zero;
 * - [release] (finger up or cancel) is zero at once.
 *
 * Output y is up-positive, like a stick; [invertY] flips it.
 */
class CameraStick(
    private val sensitivity: Float = 1f,
    private val curve: Float = 1f,
    private val antiDeadzone: Float = ControlElement.DEFAULT_ANTI_DEADZONE,
    private val invertY: Boolean = false,
) {
    var x = 0f
        private set
    var y = 0f
        private set
    private var accX = 0f
    private var accY = 0f
    private var lastTick = Long.MIN_VALUE
    private var lastMove = Long.MIN_VALUE

    /** Finger movement in dp at time [tMs]. */
    fun add(dxDp: Float, dyDp: Float, tMs: Long) {
        if (dxDp == 0f && dyDp == 0f) return
        accX += dxDp
        accY += dyDp
        lastMove = tMs
    }

    /** One frame at [tMs]; returns the push to send. */
    fun tick(tMs: Long): Pair<Float, Float> {
        if (lastTick == Long.MIN_VALUE) lastTick = tMs - TICK_MS
        val dtMs = (tMs - lastTick).coerceIn(1L, 100L).toFloat()
        lastTick = tMs
        val vx = accX / dtMs * 1000f
        val vy = accY / dtMs * 1000f
        accX = 0f; accY = 0f
        val v = hypot(vx, vy)
        if (v > MIN_SPEED_DP_S) {
            val r = min(1f, (v * sensitivity.coerceAtLeast(0.05f) / FULL_SPEED_DP_S).pow(curve.coerceIn(0.3f, 3f)))
            val anti = antiDeadzone.coerceIn(0f, 0.45f)
            val mag = anti + (1f - anti) * r
            val tx = vx / v * mag
            var ty = -vy / v * mag
            if (invertY) ty = -ty
            val a = 1f - exp(-dtMs / TAU_RISE_MS)
            x += (tx - x) * a
            y += (ty - y) * a
        } else if (lastMove != Long.MIN_VALUE && tMs - lastMove <= HOLD_MS) {
            // Keep the push across one missed touch sample.
        } else {
            val k = exp(-dtMs / TAU_DECAY_MS)
            x *= k; y *= k
            if (abs(x) < REST) x = 0f
            if (abs(y) < REST) y = 0f
        }
        return x to y
    }

    fun release() { x = 0f; y = 0f; accX = 0f; accY = 0f; lastMove = Long.MIN_VALUE }

    companion object {
        const val TICK_MS = 16L
        const val FULL_SPEED_DP_S = 700f
        const val MIN_SPEED_DP_S = 20f
        const val HOLD_MS = 24L
        const val TAU_RISE_MS = 12f
        const val TAU_DECAY_MS = 35f
        /** Decaying pushes this small are zeroed: under any game's stick deadzone, and it ends the ease-out in ~100 ms. */
        private const val REST = 0.1f
    }
}

/**
 * Camera → mouse: relative mouse movement in PC mickeys per dp of finger movement, so it feels the
 * same on every phone. [sensitivity] is mickeys per dp (default 2); [acceleration] (0–2) adds up to
 * that much extra gain between 150 and 1650 dp/s (capped at 3×). Sub-pixel remainders carry over
 * so slow drags still move; no smoothing.
 */
class CameraMouse(
    private val sensitivity: Float = DEFAULT_SENSITIVITY,
    private val acceleration: Float = DEFAULT_ACCELERATION,
    private val invertY: Boolean = false,
) {
    private var remX = 0f
    private var remY = 0f

    fun move(dxPx: Float, dyPx: Float, dtMs: Long, density: Float): Pair<Int, Int> {
        val d = density.coerceAtLeast(0.1f)
        val dxDp = dxPx / d
        val dyDp = dyPx / d
        val dt = dtMs.coerceIn(4L, 60L).toFloat()
        val speedDp = hypot(dxDp, dyDp) / dt * 1000f
        val gain = sensitivity * (1f + acceleration.coerceIn(0f, 2f) * ((speedDp - 150f) / 1500f).coerceIn(0f, 1f))
        val fx = dxDp * gain + remX
        val fy = (if (invertY) -dyDp else dyDp) * gain + remY
        val ix = fx.roundToInt()
        val iy = fy.roundToInt()
        remX = fx - ix
        remY = fy - iy
        return ix to iy
    }

    companion object {
        const val DEFAULT_SENSITIVITY = 2f
        const val DEFAULT_ACCELERATION = 0.5f

        /** A zone's settings: its sensitivity multiplies the default; its curve above 1 adds acceleration. */
        fun forZone(e: ControlElement) =
            CameraMouse(DEFAULT_SENSITIVITY * e.sensitivity, ((e.acceleration - 1f) + DEFAULT_ACCELERATION).coerceIn(0f, 2f), e.invertY)
    }
}
