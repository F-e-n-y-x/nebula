package io.github.f_e_n_y_x.nebula.input

import io.github.f_e_n_y_x.nebula.settings.RumbleRoute

/** Which physical device drives each host controller slot; implemented by the gamepad mapper. */
interface ControllerLookup {
    /** Android input device id bound to host controller [index], or null (touch / on-screen pad). */
    fun deviceIdFor(index: Int): Int?
    /** Current trigger positions (0–255) of host controller [index]. */
    fun triggers(index: Int): Pair<Int, Int>
    /** Every bound host controller index. */
    fun indices(): Set<Int>
}

/**
 * Pure rules behind rumble and motion passthrough, kept free of Android so they're unit-tested.
 * Host motor values are 0–65535; Android vibration amplitudes are 1–255 (0 = off).
 */
object Feedback {
    /** The top byte of a host motor value, as Android amplitudes want it. */
    fun amplitude(motor: Int): Int = (motor.coerceIn(0, 0xFFFF) ushr 8) and 0xFF

    /**
     * One vibrator standing in for two motors: V+'s fold (80% of the heavy motor plus 33% of the
     * light one), then [strengthPercent] (0–200).
     */
    fun singleMotor(low: Int, high: Int, strengthPercent: Int = 100): Int {
        val mixed = amplitude(low) * LOW_WEIGHT + amplitude(high) * HIGH_WEIGHT
        return (mixed * strengthPercent.coerceIn(0, 200) / 100f).toInt().coerceIn(0, 255)
    }

    /** Where one host rumble goes. */
    data class Targets(val controller: Boolean, val device: Boolean)

    /**
     * [padHasMotors]: the physical pad bound to this host controller can vibrate. A host controller
     * without a physical pad (touch, on-screen controls) always falls back to this device unless
     * the route says controller only and a pad exists.
     */
    fun targets(route: RumbleRoute, hasPad: Boolean, padHasMotors: Boolean): Targets = when (route) {
        RumbleRoute.DEVICE -> Targets(controller = false, device = true)
        RumbleRoute.CONTROLLER -> Targets(controller = padHasMotors, device = !hasPad)
        RumbleRoute.COORDINATED -> Targets(controller = padHasMotors, device = !padHasMotors)
    }

    /**
     * Game rumble wins over audio haptics on the same motors: while the host rumbles, the audio
     * part ducks out. Audio levels are 0..1.
     */
    fun mix(gameLow: Int, gameHigh: Int, audioLow: Float, audioHigh: Float): Pair<Int, Int> =
        if (gameLow != 0 || gameHigh != 0) gameLow to gameHigh
        else (audioLow.coerceIn(0f, 1f) * 0xFFFF).toInt() to (audioHigh.coerceIn(0f, 1f) * 0xFFFF).toInt()

    /** Amplitudes for a pad's motors in Android's vibrator order (light motor first, as V+ found). */
    fun padAmplitudes(vibratorCount: Int, low: Int, high: Int, leftTrigger: Int, rightTrigger: Int): IntArray = when {
        vibratorCount >= 4 -> intArrayOf(amplitude(high), amplitude(low), amplitude(leftTrigger), amplitude(rightTrigger))
        vibratorCount >= 2 -> intArrayOf(amplitude(high), amplitude(low))
        vibratorCount == 1 -> intArrayOf(singleMotor(low, high))
        else -> IntArray(0)
    }

    /** ARGB for Android's LightState from the host's 0–255 components. */
    fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    /** rad/s → deg/s. */
    const val RAD_TO_DEG = 57.2957795f

    /**
     * A phone's sensor sample in the host controller's frame, for the current screen rotation
     * (Surface.ROTATION_* as 0–3). Same axis swap as V+ uses for its device-sensor fallback, so a
     * phone held in landscape behaves like a pad held in front of you.
     */
    fun phoneToController(x: Float, y: Float, z: Float, rotation: Int): Triple<Float, Float, Float> {
        val v = floatArrayOf(x, y, z)
        return when (rotation and 3) {
            0 -> Triple(v[0], v[2], -v[1])
            1 -> Triple(-v[1], v[2], -v[0])
            2 -> Triple(-v[0], v[2], v[1])
            else -> Triple(v[1], v[2], v[0])
        }
    }

    private const val LOW_WEIGHT = 0.80f
    private const val HIGH_WEIGHT = 0.33f
}
