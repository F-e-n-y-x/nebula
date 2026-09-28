package io.github.fenyx.nebula.engine

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.PreferenceConfiguration.FormatOption

/**
 * The user's streaming settings, stored exactly where V+ keeps them (the default
 * SharedPreferences, same keys), so settings carry over between the two apps.
 */
class EnginePreferences internal constructor(context: Context) {
    private val context = context.applicationContext

    /** The raw store, for settings the engine doesn't model yet. Keys match V+'s preference screens. */
    val sharedPreferences: SharedPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)

    /** A fresh read of every V+ setting. */
    fun configuration(): PreferenceConfiguration = PreferenceConfiguration.readPreferences(context)

    /** The saved settings as a ready-to-use request. */
    fun defaultRequest(): StreamRequest = configuration().toStreamRequest()

    /** The saved audio-to-vibration settings (V+ keys checkbox_audio_vibration and friends). */
    fun audioHaptics(): AudioHapticsConfig = AudioHapticsConfig.from(configuration())

    /** Makes [request] the saved default (resolution, fps, bitrate, codec, HDR, audio and known extras). */
    fun save(request: StreamRequest) = edit { request.applyTo(it) }

    /** Reads, mutates and synchronously writes the full V+ configuration. */
    fun edit(block: (PreferenceConfiguration) -> Unit) {
        val config = configuration()
        block(config)
        config.writePreferences(context, true)
        // writePreferences doesn't persist the channel layout; keep it in step.
        sharedPreferences.edit()
            .putString(PreferenceConfiguration.AUDIO_CONFIG_PREF_STRING, config.audioConfiguration.toLayout().wire)
            .commit()
    }
}

internal val AudioLayout.wire: String
    get() = when (this) {
        AudioLayout.STEREO -> "2"
        AudioLayout.SURROUND_51 -> "51"
        AudioLayout.SURROUND_71 -> "71"
        AudioLayout.SURROUND_714 -> "714"
    }

internal fun AudioLayout.toMoonBridge(): MoonBridge.AudioConfiguration = when (this) {
    AudioLayout.STEREO -> MoonBridge.AUDIO_CONFIGURATION_STEREO
    AudioLayout.SURROUND_51 -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
    AudioLayout.SURROUND_71 -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
    AudioLayout.SURROUND_714 -> MoonBridge.AUDIO_CONFIGURATION_714_SURROUND
}

internal fun MoonBridge.AudioConfiguration.toLayout(): AudioLayout = when (channelCount) {
    12 -> AudioLayout.SURROUND_714
    8 -> AudioLayout.SURROUND_71
    6 -> AudioLayout.SURROUND_51
    else -> AudioLayout.STEREO
}

internal fun CodecPreference.toFormatOption(): FormatOption = when (this) {
    CodecPreference.AUTO -> FormatOption.AUTO
    CodecPreference.H264 -> FormatOption.FORCE_H264
    CodecPreference.HEVC -> FormatOption.FORCE_HEVC
    CodecPreference.AV1 -> FormatOption.FORCE_AV1
}

internal fun FormatOption.toCodecPreference(): CodecPreference = when (this) {
    FormatOption.AUTO -> CodecPreference.AUTO
    FormatOption.FORCE_H264 -> CodecPreference.H264
    FormatOption.FORCE_HEVC -> CodecPreference.HEVC
    FormatOption.FORCE_AV1 -> CodecPreference.AV1
}

internal fun PreferenceConfiguration.toStreamRequest(): StreamRequest = StreamRequest(
    width = width,
    height = height,
    fps = fps,
    bitrateKbps = bitrate,
    codec = videoFormat.toCodecPreference(),
    hdr = enableHdr,
    audio = audioConfiguration.toLayout(),
    extras = mapOf(
        StreamExtras.ENABLE_MIC to enableMic,
        StreamExtras.PLAY_HOST_AUDIO to playHostAudio,
        StreamExtras.ENABLE_SOPS to enableSops,
        StreamExtras.CONTROL_ONLY to controlOnly,
        StreamExtras.TOUCH_KEYBOARD to touchKeyboardAutoInvoke,
        StreamExtras.CUSTOM_SCREEN_MODE to screenCombinationMode,
    ),
)

/** Overlays [this] request on a V+ configuration, as used for one stream or when saving defaults. */
internal fun StreamRequest.applyTo(config: PreferenceConfiguration) {
    config.width = width
    config.height = height
    config.isNativeResolution = false
    config.fps = fps
    config.bitrate = bitrateKbps
    config.videoFormat = codec.toFormatOption()
    config.enableHdr = hdr
    if (hdr && config.hdrMode == MoonBridge.HDR_MODE_SDR) config.hdrMode = MoonBridge.HDR_MODE_HDR10
    config.audioConfiguration = audio.toMoonBridge()
    (extras[StreamExtras.ENABLE_MIC] as? Boolean)?.let { config.enableMic = it }
    (extras[StreamExtras.PLAY_HOST_AUDIO] as? Boolean)?.let { config.playHostAudio = it }
    (extras[StreamExtras.ENABLE_SOPS] as? Boolean)?.let { config.enableSops = it }
    (extras[StreamExtras.CONTROL_ONLY] as? Boolean)?.let { config.controlOnly = it }
    (extras[StreamExtras.TOUCH_KEYBOARD] as? Boolean)?.let { config.touchKeyboardAutoInvoke = it }
    (extras[StreamExtras.CUSTOM_SCREEN_MODE] as? Int)?.let { config.screenCombinationMode = it }
}

/** Keys for [StreamRequest.extras]. */
object StreamExtras {
    const val ENABLE_MIC = "enableMic"
    const val PLAY_HOST_AUDIO = "playHostAudio"
    const val ENABLE_SOPS = "enableSops"
    const val CONTROL_ONLY = "controlOnly"
    /** Boolean; null/absent lets the host decide. */
    const val USE_VDD = "useVdd"
    const val TOUCH_KEYBOARD = "touchKeyboard"
    const val CUSTOM_SCREEN_MODE = "customScreenMode"
    const val MAX_PACKET_SIZE = "maxPacketSize"
    /** Int bitmask of attached gamepads (bit n = controller n); defaults to controller 0. */
    const val GAMEPAD_MASK = "gamepadMask"
}
