package com.limelight.binding.audio

data class AudioHapticsSettings(
    val enabled: Boolean,
    val strength: Int,
    val mode: String,
    val scene: Int
)

object AudioHapticsRuntimePolicy {
    fun canApplyImmediately(
        systemAudioCoupledActive: Boolean,
        applied: AudioHapticsSettings,
        desired: AudioHapticsSettings
    ): Boolean {
        return !systemAudioCoupledActive || applied == desired
    }
}
