package io.github.f_e_n_y_x.nebula.settings

/** Where motion (gyro + accelerometer) for the host's virtual controller comes from. */
enum class MotionSource(val id: String, val label: String) {
    CONTROLLER("controller", "Controller"),
    PHONE("phone", "This device"),
    OFF("off", "Off");

    companion object {
        fun fromId(id: String?): MotionSource? = entries.firstOrNull { it.id == id }
    }
}

/** When gyro samples reach the host; "while a trigger is held" is the usual aim-with-gyro mode. */
enum class MotionHold(val id: String, val label: String) {
    ALWAYS("always", "Always"),
    LEFT_TRIGGER("l2", "While L2 / LT is held"),
    RIGHT_TRIGGER("r2", "While R2 / RT is held"),
    EITHER_TRIGGER("either", "While either trigger is held");

    /** True when gyro should flow for these trigger positions (0–255). */
    fun allows(leftTrigger: Int, rightTrigger: Int): Boolean = when (this) {
        ALWAYS -> true
        LEFT_TRIGGER -> leftTrigger >= TRIGGER_THRESHOLD
        RIGHT_TRIGGER -> rightTrigger >= TRIGGER_THRESHOLD
        EITHER_TRIGGER -> leftTrigger >= TRIGGER_THRESHOLD || rightTrigger >= TRIGGER_THRESHOLD
    }

    companion object {
        /** About a third of the travel, so a light touch doesn't aim. */
        const val TRIGGER_THRESHOLD = 80

        fun fromId(id: String?): MotionHold = entries.firstOrNull { it.id == id } ?: ALWAYS
    }
}

/**
 * Motion passthrough settings. The source is Nebula's own key; V+'s two motion toggles
 * (checkbox_gamepad_motion_sensors, checkbox_gamepad_motion_fallback) are kept in step so the
 * value means the same in both apps, and are read when Nebula's key was never written.
 */
data class MotionSettings(
    val source: MotionSource = MotionSource.CONTROLLER,
    /** Controller source only: use this device's sensors when the controller has none. */
    val phoneFallback: Boolean = false,
    /** Gyro scale, percent (25–300). */
    val sensitivity: Int = 100,
    val hold: MotionHold = MotionHold.ALWAYS,
) {
    /** Gyro multiplier the forwarder applies (accelerometer is never scaled). */
    val gyroScale: Float get() = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY) / 100f

    companion object {
        const val SOURCE_KEY = "nebula_motion_source"
        const val SENSITIVITY_KEY = "nebula_motion_sensitivity"
        const val HOLD_KEY = "nebula_motion_hold"
        const val V_SENSORS_KEY = "checkbox_gamepad_motion_sensors"
        const val V_FALLBACK_KEY = "checkbox_gamepad_motion_fallback"
        const val MIN_SENSITIVITY = 25
        const val MAX_SENSITIVITY = 300

        fun read(all: Map<String, *>): MotionSettings {
            val vSensors = all[V_SENSORS_KEY] as? Boolean ?: true
            val vFallback = all[V_FALLBACK_KEY] as? Boolean ?: false
            val source = MotionSource.fromId(all[SOURCE_KEY] as? String) ?: when {
                vSensors -> MotionSource.CONTROLLER
                vFallback -> MotionSource.PHONE
                else -> MotionSource.OFF
            }
            return MotionSettings(
                source = source,
                phoneFallback = source == MotionSource.CONTROLLER && vFallback,
                sensitivity = ((all[SENSITIVITY_KEY] as? Int) ?: 100).coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
                hold = MotionHold.fromId(all[HOLD_KEY] as? String),
            )
        }

        fun read(p: LegacyPrefs) = read(p.prefs.all)

        /** The key/value pairs that store [s], V+ keys included. */
        fun entries(s: MotionSettings): Map<String, Any> = mapOf(
            SOURCE_KEY to s.source.id,
            SENSITIVITY_KEY to s.sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
            HOLD_KEY to s.hold.id,
            V_SENSORS_KEY to (s.source == MotionSource.CONTROLLER),
            V_FALLBACK_KEY to (s.source == MotionSource.PHONE || (s.source == MotionSource.CONTROLLER && s.phoneFallback)),
        )

        fun write(p: LegacyPrefs, s: MotionSettings) {
            val e = p.prefs.edit()
            entries(s).forEach { (k, v) -> if (v is Boolean) e.putBoolean(k, v) else if (v is Int) e.putInt(k, v) else e.putString(k, v.toString()) }
            e.apply()
        }
    }
}

/** V+'s "Host game rumble output" (list_game_rumble_mode). */
enum class RumbleRoute(val id: String, val label: String) {
    /** The controller vibrates; this device takes over when the controller has no motors. */
    COORDINATED("smart", "Coordinated"),
    DEVICE("device", "This device"),
    CONTROLLER("controller", "Controller");

    companion object {
        fun fromId(id: String?): RumbleRoute = entries.firstOrNull { it.id == id } ?: CONTROLLER
    }
}

/** Host rumble settings, from V+'s keys. */
data class RumbleSettings(
    val route: RumbleRoute = RumbleRoute.CONTROLLER,
    /** This device's vibrator strength, percent (0–200). */
    val deviceStrength: Int = 100,
) {
    companion object {
        const val ROUTE_KEY = "list_game_rumble_mode"
        const val STRENGTH_KEY = "seekbar_vibrate_fallback_strength"

        fun read(all: Map<String, *>) = RumbleSettings(
            route = RumbleRoute.fromId(all[ROUTE_KEY] as? String),
            deviceStrength = when (val v = all[STRENGTH_KEY]) { is Int -> v; is String -> v.toIntOrNull() ?: 100; else -> 100 }.coerceIn(0, 200),
        )
    }
}

/** Audio-to-vibration settings, in V+'s keys (checkbox_audio_vibration and friends). */
data class HapticsSettings(
    val enabled: Boolean = false,
    /** Percent, 0–200. */
    val strength: Int = 80,
    /** auto / device / gamepad / both. */
    val route: String = "auto",
    /** 0 game, 1 music, 2 automatic. */
    val scene: Int = 0,
) {
    companion object {
        const val ENABLED_KEY = "checkbox_audio_vibration"
        const val STRENGTH_KEY = "seekbar_audio_vibration_strength"
        const val ROUTE_KEY = "list_audio_vibration_mode"
        const val SCENE_KEY = "list_audio_vibration_scene"
        val ROUTES = listOf("auto" to "Automatic", "device" to "This device", "gamepad" to "Controller", "both" to "Both")
        val SCENES = listOf(0 to "Game", 1 to "Music", 2 to "Automatic")

        fun read(all: Map<String, *>) = HapticsSettings(
            enabled = all[ENABLED_KEY] as? Boolean ?: false,
            strength = when (val v = all[STRENGTH_KEY]) { is Int -> v; is String -> v.toIntOrNull() ?: 80; else -> 80 }.coerceIn(0, 200),
            route = (all[ROUTE_KEY] as? String)?.takeIf { r -> ROUTES.any { it.first == r } } ?: "auto",
            scene = (all[SCENE_KEY]?.toString()?.toIntOrNull() ?: 0).coerceIn(0, 2),
        )

        fun write(p: LegacyPrefs, s: HapticsSettings) {
            p.prefs.edit().putBoolean(ENABLED_KEY, s.enabled).putInt(STRENGTH_KEY, s.strength.coerceIn(0, 200))
                .putString(ROUTE_KEY, s.route).putString(SCENE_KEY, s.scene.toString()).apply()
        }
    }
}
