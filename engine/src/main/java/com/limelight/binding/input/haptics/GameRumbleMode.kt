package com.limelight.binding.input.haptics

/** How host rumble is routed between the controller and the device's own vibrator. */
enum class GameRumbleMode(val preferenceValue: String) {
    COORDINATED("smart"),
    DEVICE("device"),
    CONTROLLER("controller");

    companion object {
        fun fromPreferenceValue(value: String?): GameRumbleMode =
            entries.firstOrNull { it.preferenceValue == value } ?: CONTROLLER

        fun fromLegacyFallback(enabled: Boolean): GameRumbleMode =
            if (enabled) COORDINATED else CONTROLLER
    }
}
