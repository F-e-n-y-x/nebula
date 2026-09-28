package io.github.fenyx.nebula.engine

import android.content.Context
import android.os.Handler
import com.limelight.LimeLog
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.moonlight.haptics.HapticFrame
import com.moonlight.haptics.android.AndroidHapticRenderer
import com.moonlight.haptics.android.NativeHapticsSession

/** Where audio-driven vibration goes (V+'s list_audio_vibration_mode values). */
enum class AudioHapticsRoute(val wire: String) {
    /** The controller when one can rumble, otherwise this device. */
    AUTO("auto"),
    DEVICE("device"),
    GAMEPAD("gamepad"),
    BOTH("both");

    companion object {
        fun fromWire(value: String?): AudioHapticsRoute = entries.firstOrNull { it.wire == value } ?: AUTO
    }
}

/** How the SDK shapes the effect (V+'s list_audio_vibration_scene values). */
enum class AudioHapticsScene(val wire: Int) {
    GAME(HapticFrame.SCENE_GAME),
    MUSIC(HapticFrame.SCENE_MUSIC),
    AUTO(HapticFrame.SCENE_AUTO);

    companion object {
        fun fromWire(value: Int): AudioHapticsScene = entries.firstOrNull { it.wire == value } ?: GAME
    }
}

/**
 * Audio-to-vibration ("audio haptics") through the Moonlight Audio Haptics SDK: the stream's audio
 * is analysed natively and turned into vibration on this device, the controller, or both.
 * [strength] is a percentage, 0–200 (above 100 is boost headroom).
 */
data class AudioHapticsConfig(
    val enabled: Boolean = false,
    val strength: Int = DEFAULT_STRENGTH,
    val route: AudioHapticsRoute = AudioHapticsRoute.AUTO,
    val scene: AudioHapticsScene = AudioHapticsScene.GAME,
) {
    companion object {
        const val MAX_STRENGTH = 200
        const val DEFAULT_STRENGTH = 80

        internal fun from(p: PreferenceConfiguration) = AudioHapticsConfig(
            enabled = p.enableAudioVibration,
            strength = p.audioVibrationStrength.coerceIn(0, MAX_STRENGTH),
            route = AudioHapticsRoute.fromWire(p.audioVibrationMode),
            scene = AudioHapticsScene.fromWire(p.audioVibrationScene),
        )
    }
}

/**
 * The app's controllers, as seen by audio haptics. Audio rumble should sit under the game's own
 * rumble: while the host is rumbling a pad, drop (duck) the audio part for that pad.
 */
interface AudioHapticsGamepadSink {
    fun hasRumbleCapableController(): Boolean
    /** Audio-driven motor levels, 0..1. */
    fun audioRumble(low: Float, high: Float)
    fun stopAudioRumble()
}

/** Pure routing rules, shared with V+ (AudioVibrationService) so both apps behave the same. */
internal object AudioHapticsPolicy {
    fun routesToDevice(route: AudioHapticsRoute, hasRumblePad: Boolean): Boolean = when (route) {
        AudioHapticsRoute.GAMEPAD -> false
        AudioHapticsRoute.DEVICE, AudioHapticsRoute.BOTH -> true
        AudioHapticsRoute.AUTO -> !hasRumblePad
    }

    fun routesToGamepad(route: AudioHapticsRoute, hasRumblePad: Boolean): Boolean = when (route) {
        AudioHapticsRoute.DEVICE -> false
        else -> hasRumblePad
    }

    /**
     * Android's system HapticGenerator (audio-coupled haptics) is fixed for an AudioTrack's
     * lifetime, so it's only used for music on a fixed device route; Auto keeps the SDK renderer so
     * it can follow controllers coming and going.
     */
    fun wantsSystemCoupled(config: AudioHapticsConfig): Boolean =
        config.enabled && config.scene == AudioHapticsScene.MUSIC &&
            (config.route == AudioHapticsRoute.DEVICE || config.route == AudioHapticsRoute.BOTH)

    /** Splits a frame into controller motor levels, as V+ does. */
    fun gamepadLevels(continuous: Float, transient: Float, lowBandRatio: Float, sharpness: Float, hasTransient: Boolean): Pair<Float, Float> {
        val lowBand = lowBandRatio.coerceIn(0f, 1f)
        val sharp = sharpness.coerceIn(0f, 1f)
        var low = continuous * (0.55f + 0.45f * lowBand)
        var high = continuous * (1f - lowBand) * 0.25f
        if (hasTransient) {
            low = maxOf(low, transient * (0.25f + 0.75f * lowBand) * (1f - 0.35f * sharp))
            high = maxOf(high, transient * (0.35f + 0.65f * sharp))
        }
        return low.coerceIn(0f, 1f) to high.coerceIn(0f, 1f)
    }

    fun scaled(amplitude: Float, strength: Int): Float = (amplitude.coerceIn(0f, 1f) * strength / 100f).coerceIn(0f, 1f)

    const val MIN_AMPLITUDE = 0.05f
}

/**
 * One stream's audio-haptics runtime: the SDK's native analyser (fed by the native audio path
 * through [MoonBridge.setAudioHapticsSessionHandle]) and its Android renderer for this device's
 * vibrator. Created for every stream so the stream menu can turn it on live; output is gated by
 * [MoonBridge.setAudioHapticsOutputEnabled].
 */
internal class AudioHapticsDriver(context: Context, initial: AudioHapticsConfig) {
    @Volatile
    var config: AudioHapticsConfig = initial
        private set

    @Volatile
    var gamepad: AudioHapticsGamepadSink? = null
        set(value) {
            if (field === value) return
            field?.stopAudioRumble()
            field = value
        }

    /** True while the host's own rumble is using this device's vibrator: audio output ducks out. */
    @Volatile
    var deviceDucked = false
        set(value) {
            if (field == value) return
            field = value
            if (value) worker.post { stopDevice() }
        }

    @Volatile
    private var systemCoupledActive = false
    @Volatile
    private var foreground = true
    private var deviceActive = false
    private var gamepadActive = false
    private var lastTimestampUs = -1L

    private val session = NativeHapticsSession(listener = { frame -> onFrame(frame) }, initialScene = initial.scene.wire)
    private val renderer = AndroidHapticRenderer(context.applicationContext, workerLooper = session.deliveryLooper)
    private val worker = Handler(session.deliveryLooper)
    private val transientEnd = Runnable { gamepad?.let { if (gamepadActive) it.audioRumble(lastContinuous.first, lastContinuous.second) } }
    private var lastContinuous = 0f to 0f

    val nativeHandle: Long get() = session.nativeHandle

    /** What the stream's AudioTrack should ask for at setup (see [AudioHapticsPolicy.wantsSystemCoupled]). */
    val wantsSystemCoupled: Boolean get() = AudioHapticsPolicy.wantsSystemCoupled(config)

    init {
        applyScene(initial.scene)
        publish()
        LimeLog.info("AudioHaptics: deviceProfile=${renderer.deviceProfileId} enabled=${initial.enabled}")
    }

    fun update(next: AudioHapticsConfig) {
        val previous = config
        val bounded = next.copy(strength = next.strength.coerceIn(0, AudioHapticsConfig.MAX_STRENGTH))
        config = bounded
        if (bounded.scene != previous.scene) applyScene(bounded.scene)
        if (!bounded.enabled) worker.post { stopAll() }
        publish()
    }

    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) worker.post { stopAll() }
        publish()
    }

    fun setSystemCoupledActive(active: Boolean) {
        systemCoupledActive = active
        if (active) worker.post { stopDevice() }
    }

    fun updatePresentationClock(framePosition: Long, systemNanoTime: Long, sampleRate: Int) {
        if (framePosition < 0 || sampleRate <= 0) renderer.clearAudioPresentationClock()
        else renderer.updateAudioPresentationClock(framePosition, systemNanoTime, sampleRate)
    }

    fun release() {
        MoonBridge.setAudioHapticsOutputEnabled(false)
        session.stop()
        worker.post { stopAll() }
        renderer.clearAudioPresentationClock()
        renderer.close()
        session.close()
    }

    private fun publish() {
        MoonBridge.setAudioHapticsSceneMode(config.scene.wire)
        MoonBridge.setAudioHapticsOutputEnabled(config.enabled && foreground)
    }

    private fun applyScene(scene: AudioHapticsScene) {
        session.setSensitivity(if (scene == AudioHapticsScene.MUSIC) MUSIC_SENSITIVITY else DEFAULT_SENSITIVITY)
        session.setScene(scene.wire)
    }

    /** Runs on the SDK's delivery thread. */
    private fun onFrame(frame: HapticFrame) {
        val c = config
        if (!c.enabled || !foreground || frame.timestampUs < lastTimestampUs) return
        lastTimestampUs = frame.timestampUs
        val hasTransient = frame.hasFlag(HapticFrame.FLAG_TRANSIENT)
        val changed = frame.hasFlag(HapticFrame.FLAG_CONTINUOUS_CHANGED)
        if (frame.hasFlag(HapticFrame.FLAG_STOP)) {
            stopDevice()
            stopGamepad()
            if (!hasTransient) return
        }
        if (!hasTransient && !changed) return
        val continuous = AudioHapticsPolicy.scaled(frame.continuousAmplitude, c.strength)
        val transient = AudioHapticsPolicy.scaled(frame.transientAmplitude, c.strength)
        if (!hasTransient && continuous < AudioHapticsPolicy.MIN_AMPLITUDE) {
            stopDevice()
            stopGamepad()
            return
        }
        val pad = gamepad
        val hasPad = pad?.hasRumbleCapableController() == true

        if (AudioHapticsPolicy.routesToDevice(c.route, hasPad) && !systemCoupledActive && !deviceDucked) {
            deviceActive = renderer.submit(
                frame.timestampUs, frame.flags, continuous, transient, frame.transientDurationMs,
                frame.sharpness, frame.lowBandRatio, frame.stereoPan, frame.confidence, frame.activeScene, frame.producerTimeUs,
            ) || deviceActive
        } else {
            stopDevice()
        }

        if (pad != null && AudioHapticsPolicy.routesToGamepad(c.route, hasPad)) {
            lastContinuous = AudioHapticsPolicy.gamepadLevels(continuous, 0f, frame.lowBandRatio, frame.sharpness, false)
            val (low, high) = AudioHapticsPolicy.gamepadLevels(continuous, transient, frame.lowBandRatio, frame.sharpness, hasTransient)
            pad.audioRumble(low, high)
            gamepadActive = true
            worker.removeCallbacks(transientEnd)
            if (hasTransient) worker.postDelayed(transientEnd, frame.transientDurationMs.toLong().coerceIn(10, 250))
        } else {
            stopGamepad()
        }
    }

    private fun stopDevice() {
        if (!deviceActive) return
        renderer.stop()
        deviceActive = false
    }

    private fun stopGamepad() {
        worker.removeCallbacks(transientEnd)
        if (!gamepadActive) return
        gamepad?.stopAudioRumble()
        gamepadActive = false
    }

    private fun stopAll() {
        renderer.stop()
        deviceActive = false
        stopGamepad()
        lastTimestampUs = -1
    }

    private companion object {
        const val DEFAULT_SENSITIVITY = 1.0f
        const val MUSIC_SENSITIVITY = 2.5f
    }
}
