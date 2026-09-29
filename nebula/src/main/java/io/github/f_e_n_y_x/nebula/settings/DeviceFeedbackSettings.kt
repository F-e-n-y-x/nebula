package io.github.f_e_n_y_x.nebula.settings

/**
 * What the gyro does. The two mapping modes turn gyro into input every game already reads; passthrough
 * hands raw motion to the PC, which only games with native gyro support (and a motion-capable virtual
 * controller on the host, Nova's DualSense profile) use.
 */
enum class GyroMode(val id: String, val label: String) {
    RIGHT_STICK("right_stick", "Gyro to right stick"),
    MOUSE("mouse", "Gyro to mouse"),
    PASSTHROUGH("passthrough", "Motion passthrough"),
    OFF("off", "Off");

    /** Nebula maps the gyro itself (works in every game, with any virtual controller). */
    val mapsLocally: Boolean get() = this == RIGHT_STICK || this == MOUSE

    companion object {
        fun fromId(id: String?): GyroMode? = entries.firstOrNull { it.id == id }
    }
}

/** Where gyro (and accelerometer) samples come from. */
enum class MotionSource(val id: String, val label: String) {
    CONTROLLER("controller", "Controller"),
    PHONE("phone", "This device");

    companion object {
        fun fromId(id: String?): MotionSource? = entries.firstOrNull { it.id == id }
    }
}

/** When the gyro acts; "while a trigger is held" is the usual aim-with-gyro mode. */
enum class MotionHold(val id: String, val label: String) {
    ALWAYS("always", "Always"),
    LEFT_TRIGGER("l2", "While L2 / LT is held"),
    RIGHT_TRIGGER("r2", "While R2 / RT is held"),
    EITHER_TRIGGER("either", "While either trigger is held"),
    TOGGLE("toggle", "Toggle with a button"),
    /** Gyro only while scoped or shooting: either trigger, or an on-screen fire / aim button (also KB+M layouts). */
    AIMING("aiming", "While aiming or firing");

    /**
     * True when gyro should act for these trigger positions (0–255); [toggledOn] is the toggle
     * button's state, [aiming] whether an on-screen fire or aim button is held.
     */
    fun allows(leftTrigger: Int, rightTrigger: Int, toggledOn: Boolean = false, aiming: Boolean = false): Boolean = when (this) {
        ALWAYS -> true
        AIMING -> aiming || leftTrigger >= TRIGGER_THRESHOLD || rightTrigger >= TRIGGER_THRESHOLD
        LEFT_TRIGGER -> leftTrigger >= TRIGGER_THRESHOLD
        RIGHT_TRIGGER -> rightTrigger >= TRIGGER_THRESHOLD
        EITHER_TRIGGER -> leftTrigger >= TRIGGER_THRESHOLD || rightTrigger >= TRIGGER_THRESHOLD
        TOGGLE -> toggledOn
    }

    companion object {
        /** About a third of the travel, so a light touch doesn't aim. */
        const val TRIGGER_THRESHOLD = 80

        fun fromId(id: String?): MotionHold? = entries.firstOrNull { it.id == id }
    }
}

/** Buttons that can toggle the gyro on and off (the press is kept from the PC). */
enum class GyroToggleButton(val keyCode: Int, val label: String) {
    R3(android.view.KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"),
    L3(android.view.KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"),
    SELECT(android.view.KeyEvent.KEYCODE_BUTTON_SELECT, "Select"),
    GUIDE(android.view.KeyEvent.KEYCODE_BUTTON_MODE, "Guide");

    companion object {
        fun fromKeyCode(code: Int?): GyroToggleButton = entries.firstOrNull { it.keyCode == code } ?: R3
    }
}

/**
 * Gyro settings: the mode, the sensor source, and the aim tuning used by the right-stick and mouse
 * modes. Stored in V+'s keys where V+ has one (gyro_to_right_stick, gyro_to_mouse,
 * gyro_sensitivity_multiplier, gyro_invert_x_axis, gyro_invert_y_axis, gyro_activation_key_code,
 * checkbox_gamepad_motion_sensors, checkbox_gamepad_motion_fallback) so a choice means the same in
 * both apps, plus Nebula's own keys for the rest.
 */
data class MotionSettings(
    val mode: GyroMode = GyroMode.PASSTHROUGH,
    val source: MotionSource = MotionSource.CONTROLLER,
    /** Controller source only: use this device's sensors when the controller has none. */
    val phoneFallback: Boolean = false,
    /** Overall gyro scale, percent (25–300). */
    val sensitivity: Int = 100,
    /** Horizontal (yaw) scale on top of [sensitivity], percent (25–300). */
    val sensitivityX: Int = 100,
    /** Vertical (pitch) scale on top of [sensitivity], percent (25–300). */
    val sensitivityY: Int = 100,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
    /** Rotation slower than this, in degrees per second, is ignored (hand tremor). 0–20. */
    val deadzone: Int = 1,
    /** Smoothing, percent (0–90): higher is steadier but lags more. */
    val smoothing: Int = 20,
    /** Right-stick mode: smallest deflection sent once the gyro moves, percent (0–40), to get past a game's stick deadzone. */
    val stickMinimum: Int = 12,
    val hold: MotionHold = MotionHold.ALWAYS,
    val toggleButton: GyroToggleButton = GyroToggleButton.R3,
    /** Read-only: the mode was never chosen and was switched from phone passthrough to right stick. */
    val migratedToStick: Boolean = false,
) {
    /** Gyro multiplier the passthrough forwarder applies (accelerometer is never scaled). */
    val gyroScale: Float get() = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY) / 100f

    /** Host passthrough is on (the forwarder answers the PC's motion requests). */
    val passthrough: Boolean get() = mode == GyroMode.PASSTHROUGH

    /** This device's sensors are the source (chosen, or as the controller fallback). */
    val usesPhone: Boolean get() = source == MotionSource.PHONE || phoneFallback

    companion object {
        const val MODE_KEY = "nebula_gyro_mode"
        const val SOURCE_KEY = "nebula_motion_source"
        const val PHONE_FALLBACK_KEY = "nebula_gyro_phone_fallback"
        const val SENSITIVITY_KEY = "nebula_motion_sensitivity"
        const val SENSITIVITY_X_KEY = "nebula_gyro_sensitivity_x"
        const val SENSITIVITY_Y_KEY = "nebula_gyro_sensitivity_y"
        const val DEADZONE_KEY = "nebula_gyro_deadzone"
        const val SMOOTHING_KEY = "nebula_gyro_smoothing"
        const val STICK_MIN_KEY = "nebula_gyro_stick_minimum"
        const val HOLD_KEY = "nebula_motion_hold"
        const val TOGGLE_BUTTON_KEY = "nebula_gyro_toggle_button"
        const val V_SENSORS_KEY = "checkbox_gamepad_motion_sensors"
        const val V_FALLBACK_KEY = "checkbox_gamepad_motion_fallback"
        const val V_TO_STICK_KEY = "gyro_to_right_stick"
        const val V_TO_MOUSE_KEY = "gyro_to_mouse"
        const val V_MULTIPLIER_KEY = "gyro_sensitivity_multiplier"
        const val V_INVERT_X_KEY = "gyro_invert_x_axis"
        const val V_INVERT_Y_KEY = "gyro_invert_y_axis"
        const val V_ACTIVATION_KEY = "gyro_activation_key_code"
        /** V+'s ControllerGyroManager.GYRO_ACTIVATION_ALWAYS. */
        const val V_ACTIVATION_ALWAYS = -1000
        const val MIN_SENSITIVITY = 25
        const val MAX_SENSITIVITY = 300
        const val MAX_DEADZONE = 20
        const val MAX_SMOOTHING = 90
        const val MAX_STICK_MINIMUM = 40

        private fun int(all: Map<String, *>, key: String): Int? = when (val v = all[key]) {
            is Int -> v; is Long -> v.toInt(); is String -> v.toIntOrNull(); else -> null
        }

        private fun vHold(code: Int?): MotionHold? = when (code) {
            V_ACTIVATION_ALWAYS -> MotionHold.ALWAYS
            android.view.KeyEvent.KEYCODE_BUTTON_L2 -> MotionHold.LEFT_TRIGGER
            android.view.KeyEvent.KEYCODE_BUTTON_R2 -> MotionHold.RIGHT_TRIGGER
            else -> null
        }

        fun read(all: Map<String, *>): MotionSettings {
            val vSensors = all[V_SENSORS_KEY] as? Boolean ?: true
            val vFallback = all[V_FALLBACK_KEY] as? Boolean ?: false
            val storedSource = all[SOURCE_KEY] as? String
            // Before 0.3.0-dev10 the source key also held "off"; V+ only has its two toggles.
            val legacyOff = storedSource == "off" || (storedSource == null && !vSensors && !vFallback)
            val source = MotionSource.fromId(storedSource) ?: if (!vSensors && vFallback) MotionSource.PHONE else MotionSource.CONTROLLER
            val hold = MotionHold.fromId(all[HOLD_KEY] as? String) ?: vHold(int(all, V_ACTIVATION_KEY)) ?: MotionHold.ALWAYS
            val chosen = GyroMode.fromId(all[MODE_KEY] as? String)
            // Phone gyro with "always" was only ever passthrough, which does nothing on an Xbox-style
            // virtual pad (the usual case). The mapping mode is what that setup was after.
            val migrate = chosen == null && all[V_TO_MOUSE_KEY] != true && all[V_TO_STICK_KEY] != true &&
                !legacyOff && source == MotionSource.PHONE && hold == MotionHold.ALWAYS
            val mode = chosen ?: when {
                all[V_TO_MOUSE_KEY] == true -> GyroMode.MOUSE
                all[V_TO_STICK_KEY] == true -> GyroMode.RIGHT_STICK
                legacyOff -> GyroMode.OFF
                migrate -> GyroMode.RIGHT_STICK
                else -> GyroMode.PASSTHROUGH
            }
            val vMultiplier = (all[V_MULTIPLIER_KEY] as? Float)?.takeIf { it > 0f }?.let { (it * 100).toInt() }
            fun pct(key: String) = (int(all, key) ?: 100).coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
            return MotionSettings(
                mode = mode,
                source = source,
                phoneFallback = source == MotionSource.CONTROLLER && (all[PHONE_FALLBACK_KEY] as? Boolean ?: (vFallback && !legacyOff)),
                sensitivity = (int(all, SENSITIVITY_KEY) ?: vMultiplier ?: 100).coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
                sensitivityX = pct(SENSITIVITY_X_KEY),
                sensitivityY = pct(SENSITIVITY_Y_KEY),
                invertX = all[V_INVERT_X_KEY] as? Boolean ?: false,
                invertY = all[V_INVERT_Y_KEY] as? Boolean ?: false,
                deadzone = (int(all, DEADZONE_KEY) ?: 1).coerceIn(0, MAX_DEADZONE),
                smoothing = (int(all, SMOOTHING_KEY) ?: 20).coerceIn(0, MAX_SMOOTHING),
                stickMinimum = (int(all, STICK_MIN_KEY) ?: 12).coerceIn(0, MAX_STICK_MINIMUM),
                hold = hold,
                toggleButton = GyroToggleButton.fromKeyCode(int(all, TOGGLE_BUTTON_KEY)),
                migratedToStick = migrate,
            )
        }

        fun read(p: LegacyPrefs) = read(p.prefs.all)

        /** The key/value pairs that store [s], V+ keys included. */
        fun entries(s: MotionSettings): Map<String, Any> = mapOf(
            MODE_KEY to s.mode.id,
            SOURCE_KEY to s.source.id,
            PHONE_FALLBACK_KEY to (s.source == MotionSource.CONTROLLER && s.phoneFallback),
            SENSITIVITY_KEY to s.sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
            SENSITIVITY_X_KEY to s.sensitivityX.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
            SENSITIVITY_Y_KEY to s.sensitivityY.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY),
            DEADZONE_KEY to s.deadzone.coerceIn(0, MAX_DEADZONE),
            SMOOTHING_KEY to s.smoothing.coerceIn(0, MAX_SMOOTHING),
            STICK_MIN_KEY to s.stickMinimum.coerceIn(0, MAX_STICK_MINIMUM),
            HOLD_KEY to s.hold.id,
            TOGGLE_BUTTON_KEY to s.toggleButton.keyCode,
            V_SENSORS_KEY to (s.passthrough && s.source == MotionSource.CONTROLLER),
            V_FALLBACK_KEY to (s.passthrough && s.usesPhone),
            V_TO_STICK_KEY to (s.mode == GyroMode.RIGHT_STICK),
            V_TO_MOUSE_KEY to (s.mode == GyroMode.MOUSE),
            V_MULTIPLIER_KEY to (s.sensitivity / 100f).coerceIn(0.5f, 10f),
            V_INVERT_X_KEY to s.invertX,
            V_INVERT_Y_KEY to s.invertY,
            V_ACTIVATION_KEY to when (s.hold) {
                MotionHold.LEFT_TRIGGER, MotionHold.EITHER_TRIGGER, MotionHold.AIMING -> android.view.KeyEvent.KEYCODE_BUTTON_L2
                MotionHold.RIGHT_TRIGGER -> android.view.KeyEvent.KEYCODE_BUTTON_R2
                MotionHold.ALWAYS, MotionHold.TOGGLE -> V_ACTIVATION_ALWAYS
            },
        )

        fun write(p: LegacyPrefs, s: MotionSettings) {
            val e = p.prefs.edit()
            entries(s).forEach { (k, v) ->
                when (v) {
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Float -> e.putFloat(k, v)
                    else -> e.putString(k, v.toString())
                }
            }
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
