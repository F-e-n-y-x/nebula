package io.github.f_e_n_y_x.nebula.input

import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Turns gyro samples into aim for "Gyro to right stick" and "Gyro to mouse". Pure (no Android),
 * so it is unit-tested; [io.github.f_e_n_y_x.nebula.data.engine.GyroAssist] feeds it sensors.
 *
 * Samples are in the controller frame the host's motion API uses (SDL / DualSense: X right, Y up,
 * Z toward the player; a phone is rotated into it by [Feedback.phoneToController]), gyro in degrees
 * per second. Pitch is rotation about X (positive aims up). Yaw is taken in "player space": the
 * rotation about the real vertical, found from the accelerometer, so turning left and right works
 * the same whether the phone or pad is held flat, upright or anywhere between. Without an
 * accelerometer sample the pad's own Y axis stands in.
 */
class GyroAim {
    private var upX = 0f
    private var upY = 1f
    private var upZ = 0f
    private var haveGravity = false
    private var smoothYaw = 0f
    private var smoothPitch = 0f
    private var remainX = 0f
    private var remainY = 0f

    /** Forget smoothing and mouse remainders (the gyro was paused or re-targeted). */
    fun reset() {
        smoothYaw = 0f; smoothPitch = 0f; remainX = 0f; remainY = 0f
    }

    /** An accelerometer sample (m/s², controller frame). At rest it points up. */
    fun onAccel(x: Float, y: Float, z: Float) {
        val n = sqrt(x * x + y * y + z * z)
        if (n < 1f) return // free fall or a bogus sample
        val nx = x / n; val ny = y / n; val nz = z / n
        if (!haveGravity) {
            upX = nx; upY = ny; upZ = nz; haveGravity = true
            return
        }
        // Low-pass: gravity changes slowly, hand acceleration doesn't.
        upX += GRAVITY_ALPHA * (nx - upX); upY += GRAVITY_ALPHA * (ny - upY); upZ += GRAVITY_ALPHA * (nz - upZ)
    }

    /**
     * Aim rates in degrees per second, after deadzone, smoothing, sensitivity and inversion:
     * first is yaw (positive turns right), second is pitch (positive aims up).
     */
    fun rates(gx: Float, gy: Float, gz: Float, s: MotionSettings): Pair<Float, Float> {
        val yaw = if (haveGravity) {
            // Rotation about "up" is counter-clockwise from above, so turning right is negative.
            val world = -(upY * gy + upZ * gz)
            sign(world) * min(abs(world) * YAW_RELAX, hypot(gy, gz))
        } else {
            -gy
        }
        val pitch = gx
        val a = 1f - s.smoothing.coerceIn(0, MotionSettings.MAX_SMOOTHING) / 100f
        smoothYaw += a * (deadzoned(yaw, s) - smoothYaw)
        smoothPitch += a * (deadzoned(pitch, s) - smoothPitch)
        val base = s.sensitivity.coerceIn(MotionSettings.MIN_SENSITIVITY, MotionSettings.MAX_SENSITIVITY) / 100f
        val kx = base * s.sensitivityX.coerceIn(MotionSettings.MIN_SENSITIVITY, MotionSettings.MAX_SENSITIVITY) / 100f
        val ky = base * s.sensitivityY.coerceIn(MotionSettings.MIN_SENSITIVITY, MotionSettings.MAX_SENSITIVITY) / 100f
        val outYaw = smoothYaw * kx * if (s.invertX) -1f else 1f
        val outPitch = smoothPitch * ky * if (s.invertY) -1f else 1f
        return outYaw to outPitch
    }

    /**
     * Right-stick deflection as host values (signed 16-bit, up positive). [STICK_FULL_SCALE] deg/s
     * at 100 % is full deflection (V+'s scale); any movement is lifted past [MotionSettings.stickMinimum]
     * so it clears the game's own stick deadzone.
     */
    fun stick(gx: Float, gy: Float, gz: Float, s: MotionSettings): Pair<Int, Int> {
        val (yaw, pitch) = rates(gx, gy, gz, s)
        var x = yaw / STICK_FULL_SCALE
        var y = pitch / STICK_FULL_SCALE
        val mag = hypot(x, y)
        if (mag <= 0f) return 0 to 0
        val min = s.stickMinimum.coerceIn(0, MotionSettings.MAX_STICK_MINIMUM) / 100f
        val lifted = min + (1f - min) * mag.coerceAtMost(1f)
        x = x / mag * lifted
        y = y / mag * lifted
        return toShort(x) to toShort(y)
    }

    /** Relative mouse movement in pixels for a sample [dtSeconds] after the previous one (screen Y grows downwards). */
    fun mouse(gx: Float, gy: Float, gz: Float, dtSeconds: Float, s: MotionSettings): Pair<Int, Int> {
        val (yaw, pitch) = rates(gx, gy, gz, s)
        val dt = dtSeconds.coerceIn(0f, MAX_DT)
        remainX += yaw * dt * MOUSE_PIXELS_PER_DEGREE
        remainY += -pitch * dt * MOUSE_PIXELS_PER_DEGREE
        val dx = remainX.toInt()
        val dy = remainY.toInt()
        remainX -= dx; remainY -= dy
        return dx to dy
    }

    private fun deadzoned(v: Float, s: MotionSettings): Float {
        val dz = s.deadzone.coerceIn(0, MotionSettings.MAX_DEADZONE).toFloat()
        return if (abs(v) <= dz) 0f else v - sign(v) * dz
    }

    private fun toShort(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).toInt()

    companion object {
        /** deg/s for full stick deflection at 100 % (V+ uses 180 / multiplier). */
        const val STICK_FULL_SCALE = 180f
        /** V+'s gyro-to-mouse scale: 800 px per radian at multiplier 1. */
        const val MOUSE_PIXELS_PER_DEGREE = 800f / 57.2957795f
        /** Long gaps (sensor paused) don't turn into one big jump. */
        const val MAX_DT = 0.05f
        private const val GRAVITY_ALPHA = 0.05f
        /** JoyShockMapper's player-space yaw relax factor. */
        private const val YAW_RELAX = 1.41f
    }
}
