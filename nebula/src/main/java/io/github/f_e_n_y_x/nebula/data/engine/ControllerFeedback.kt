package io.github.f_e_n_y_x.nebula.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.lights.Light
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.InputDevice
import androidx.annotation.RequiresApi
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.input.ControllerLookup
import io.github.f_e_n_y_x.nebula.input.Feedback
import io.github.f_e_n_y_x.nebula.settings.RumbleSettings
import io.github.fenyx.nebula.engine.AudioHapticsGamepadSink

/** What a physical pad can do, for the arrival packet and for the UI. */
data class PadHardware(val motors: Int, val gyro: Boolean, val accel: Boolean, val rgbLed: Boolean) {
    /** LI_CCAP_* bits beyond analog triggers. */
    fun capabilities(): Int {
        var c = MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt()
        if (motors > 0) c = c or MoonBridge.LI_CCAP_RUMBLE.toInt()
        if (motors >= 4) c = c or MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt()
        if (gyro) c = c or MoonBridge.LI_CCAP_GYRO.toInt()
        if (accel) c = c or MoonBridge.LI_CCAP_ACCEL.toInt()
        if (rgbLed) c = c or MoonBridge.LI_CCAP_RGB_LED.toInt()
        return c
    }

    companion object {
        val NONE = PadHardware(0, gyro = false, accel = false, rgbLed = false)

        /**
         * Reads a pad's motors, IMU and lights. Controller sensors are only trusted from Android 13
         * (V+'s InputDeviceSensorPolicy: earlier releases report phantom sensors on some pads).
         */
        @SuppressLint("NewApi")
        fun of(device: InputDevice?): PadHardware {
            device ?: return NONE
            val motors = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> device.vibratorManager.vibratorIds.size
                @Suppress("DEPRECATION") device.vibrator.hasVibrator() -> 1
                else -> 0
            }
            val sensors = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val gyro = sensors && device.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
            val accel = sensors && device.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
            val rgb = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                device.lightsManager.lights.any { it.hasRgbControl() && it.type != Light.LIGHT_TYPE_PLAYER_ID }
            return PadHardware(motors, gyro, accel, rgb)
        }
    }
}

/**
 * Host → controller feedback: rumble, trigger rumble and light bar, sent to the physical pad
 * bound to each host controller (InputDevice vibrators and lights), with this device's own
 * vibrator as the fallback per the "Host game rumble output" setting. Also the controller side of
 * audio haptics: audio-driven rumble goes to the same pads and ducks while the game rumbles.
 *
 * Call on the main thread (the engine's callbacks arrive there).
 */
class ControllerFeedback(
    context: Context,
    private val lookup: () -> ControllerLookup?,
    private val settings: () -> RumbleSettings,
    /** Tells the engine whether game rumble is on this device's vibrator (audio haptics ducks). */
    private val onDeviceRumble: (Boolean) -> Unit = {},
) : AudioHapticsGamepadSink {
    private class Motors(var low: Int = 0, var high: Int = 0, var lt: Int = 0, var rt: Int = 0) {
        val idle get() = low == 0 && high == 0 && lt == 0 && rt == 0
    }

    private val deviceVibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }

    private val game = HashMap<Int, Motors>()
    private var deviceOwnedBy = -1
    @Volatile private var audio = 0f to 0f
    private val lightSessions = HashMap<Int, Any>()

    // ---- Host callbacks ----

    fun rumble(controller: Int, low: Int, high: Int) {
        val m = game.getOrPut(controller) { Motors() }
        m.low = low
        m.high = high
        apply(controller)
    }

    fun rumbleTriggers(controller: Int, left: Int, right: Int) {
        val m = game.getOrPut(controller) { Motors() }
        m.lt = left
        m.rt = right
        apply(controller)
    }

    @SuppressLint("NewApi")
    fun setLed(controller: Int, r: Int, g: Int, b: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val device = device(controller) ?: return
        val lights = device.lightsManager.lights.filter { it.hasRgbControl() && it.type != Light.LIGHT_TYPE_PLAYER_ID }
        if (lights.isEmpty()) return
        runCatching {
            val session = lightSessions.getOrPut(device.id) { device.lightsManager.openSession() } as LightsManager.LightsSession
            val state = LightState.Builder().setColor(Feedback.argb(r, g, b)).build()
            session.requestLights(LightsRequest.Builder().apply { lights.forEach { addLight(it, state) } }.build())
        }.onFailure { Log.w(TAG, "Light bar update failed: ${it.message}") }
    }

    /** Stops every motor and closes light sessions; call when the stream ends. */
    @SuppressLint("NewApi")
    fun release() {
        game.keys.toList().forEach { c -> game[c] = Motors(); apply(c) }
        game.clear()
        audio = 0f to 0f
        deviceVibrator?.cancel()
        setDeviceOwner(-1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            lightSessions.values.forEach { runCatching { (it as LightsManager.LightsSession).close() } }
        }
        lightSessions.clear()
    }

    // ---- Audio haptics (called on the SDK's thread) ----

    override fun hasRumbleCapableController(): Boolean {
        val l = lookup() ?: return false
        return l.indices().any { PadHardware.of(device(it)).motors > 0 }
    }

    override fun audioRumble(low: Float, high: Float) {
        audio = low to high
        mainHandler.post { lookup()?.indices()?.forEach { if (game[it]?.idle != false) apply(it) } }
    }

    override fun stopAudioRumble() {
        if (audio == (0f to 0f)) return
        audio = 0f to 0f
        mainHandler.post { lookup()?.indices()?.forEach { apply(it) } }
    }

    // ---- Output ----

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun device(controller: Int): InputDevice? = lookup()?.deviceIdFor(controller)?.let { InputDevice.getDevice(it) }

    private fun apply(controller: Int) {
        val m = game[controller] ?: Motors()
        val device = device(controller)
        val hw = PadHardware.of(device)
        val route = settings()
        val targets = Feedback.targets(route.route, device != null, hw.motors > 0)
        if (targets.controller && device != null) {
            val (low, high) = Feedback.mix(m.low, m.high, audio.first, audio.second)
            vibratePad(device, hw.motors, low, high, m.lt, m.rt)
        }
        // This device's motor: game rumble only (audio haptics drives it through the SDK itself).
        if (targets.device) {
            val amp = Feedback.singleMotor(m.low, m.high, route.deviceStrength)
            vibrateDevice(amp)
            setDeviceOwner(if (amp > 0) controller else if (deviceOwnedBy == controller) -1 else deviceOwnedBy)
        } else if (deviceOwnedBy == controller) {
            vibrateDevice(0)
            setDeviceOwner(-1)
        }
    }

    private fun setDeviceOwner(controller: Int) {
        if (deviceOwnedBy == controller) return
        val wasActive = deviceOwnedBy >= 0
        deviceOwnedBy = controller
        if (wasActive != (controller >= 0)) onDeviceRumble(controller >= 0)
    }

    @SuppressLint("NewApi", "MissingPermission")
    private fun vibratePad(device: InputDevice, motors: Int, low: Int, high: Int, lt: Int, rt: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && motors >= 2) {
                val vm = device.vibratorManager
                val ids = vm.vibratorIds
                val amps = Feedback.padAmplitudes(ids.size, low, high, lt, rt)
                if (amps.all { it == 0 }) { vm.cancel(); return }
                val combo = CombinedVibration.startParallel()
                ids.forEachIndexed { i, id -> amps.getOrNull(i)?.takeIf { it > 0 }?.let { combo.addVibrator(id, VibrationEffect.createOneShot(HOLD_MS, it)) } }
                vm.vibrate(combo.combine(), mediaAttributes())
            } else {
                @Suppress("DEPRECATION")
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) device.vibratorManager.defaultVibrator else device.vibrator
                oneShot(v, Feedback.singleMotor(low, high))
            }
        }.onFailure { Log.w(TAG, "Controller rumble failed: ${it.message}") }
    }

    private fun vibrateDevice(amplitude: Int) {
        val v = deviceVibrator ?: return
        runCatching { oneShot(v, amplitude) }.onFailure { Log.w(TAG, "Device rumble failed: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun oneShot(v: Vibrator, amplitude: Int) {
        if (amplitude <= 0) { v.cancel(); return }
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(HOLD_MS, amplitude)
        } else {
            // No amplitude control: a 20 ms PWM pattern approximates the level.
            val on = (amplitude / 255.0 * 20).toLong().coerceIn(1, 20)
            VibrationEffect.createWaveform(longArrayOf(0, on, 20 - on), 0)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, mediaAttributes())
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun mediaAttributes(): VibrationAttributes = VibrationAttributes.Builder().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setUsage(VibrationAttributes.USAGE_MEDIA)
    }.build()

    private companion object {
        const val TAG = "NebulaFeedback"
        /** The host re-sends rumble on change and stops it with 0/0; hold long, like V+. */
        const val HOLD_MS = 60_000L
    }
}
