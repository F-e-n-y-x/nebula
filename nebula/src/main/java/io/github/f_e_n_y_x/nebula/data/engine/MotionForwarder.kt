package io.github.f_e_n_y_x.nebula.data.engine

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import android.view.InputDevice
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.input.ControllerLookup
import io.github.f_e_n_y_x.nebula.input.Feedback
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.MotionSource
import io.github.fenyx.nebula.engine.MotionType

/** Where one host motion request is served from, decided by [MotionRouting.resolve]. */
enum class MotionFeed { NONE, CONTROLLER, PHONE }

/** Pure routing for motion requests, unit-tested. */
object MotionRouting {
    /**
     * [padHasSensor]: the pad bound to [controller] has this sensor (Android 13+).
     * [phoneHasSensor]: this device has it. Phone sensors only ever stand in for controller 0.
     */
    fun resolve(settings: MotionSettings, controller: Int, padHasSensor: Boolean, phoneHasSensor: Boolean): MotionFeed = when (settings.source) {
        MotionSource.OFF -> MotionFeed.NONE
        MotionSource.PHONE -> if (controller == 0 && phoneHasSensor) MotionFeed.PHONE else MotionFeed.NONE
        MotionSource.CONTROLLER -> when {
            padHasSensor -> MotionFeed.CONTROLLER
            settings.phoneFallback && controller == 0 && phoneHasSensor -> MotionFeed.PHONE
            else -> MotionFeed.NONE
        }
    }

    /** True when this device should announce a motion-capable controller 0 by itself (no pad attached). */
    fun announcePhonePad(settings: MotionSettings, anyPad: Boolean, phoneHasGyro: Boolean): Boolean =
        !anyPad && phoneHasGyro && settings.source == MotionSource.PHONE

    /** Gyro as sent: deg/s times sensitivity, or zero while the hold trigger is released. */
    fun gyroOut(x: Float, y: Float, z: Float, settings: MotionSettings, lt: Int, rt: Int): Triple<Float, Float, Float> {
        if (!settings.hold.allows(lt, rt)) return Triple(0f, 0f, 0f)
        val k = Feedback.RAD_TO_DEG * settings.gyroScale
        return Triple(x * k, y * k, z * k)
    }
}

/**
 * Motion passthrough. The host asks for accelerometer and gyro per controller
 * ([onRequest]); only then are sensors registered, on the pad itself (InputDevice sensors,
 * Android 13+) or on this device (rotation-corrected), per [MotionSettings]. Samples go to the
 * host through [send]. Everything stops on [release]. Call from the main thread.
 */
class MotionForwarder(
    context: Context,
    private val lookup: () -> ControllerLookup?,
    private val settings: () -> MotionSettings,
    private val rotation: () -> Int,
    private val send: (controller: Int, type: MotionType, x: Float, y: Float, z: Float) -> Unit,
) {
    private data class Key(val controller: Int, val type: MotionType)

    private inner class Feed(val key: Key, val manager: SensorManager, val phone: Boolean) : SensorEventListener {
        private val last = FloatArray(3) { Float.NaN }
        private var zeroSent = false

        override fun onSensorChanged(e: SensorEvent) {
            val (x, y, z) = if (phone) Feedback.phoneToController(e.values[0], e.values[1], e.values[2], rotation())
            else Triple(e.values[0], e.values[1], e.values[2])
            val s = current
            if (key.type == MotionType.GYRO) {
                val (lt, rt) = lookup()?.triggers(key.controller) ?: (0 to 0)
                val out = MotionRouting.gyroOut(x, y, z, s, lt, rt)
                val zero = out.first == 0f && out.second == 0f && out.third == 0f
                if (zero && zeroSent) return
                zeroSent = zero
                emit(out.first, out.second, out.third)
            } else {
                emit(x, y, z)
            }
        }

        private fun emit(x: Float, y: Float, z: Float) {
            if (x == last[0] && y == last[1] && z == last[2]) return
            last[0] = x; last[1] = y; last[2] = z
            send(key.controller, key.type, x, y, z)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val phoneSensors = context.getSystemService(SensorManager::class.java)
    private val requests = HashMap<Key, Int>()
    private val feeds = HashMap<Key, Feed>()
    @Volatile private var current = settings()

    /** True when this device has a gyroscope (phone source is possible). */
    val phoneHasGyro: Boolean get() = phoneSensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    /** Where each active host request is being served from, for the stream menu. */
    fun activeFeeds(): Map<Pair<Int, MotionType>, MotionFeed> =
        requests.keys.associate { (it.controller to it.type) to (feeds[it]?.let { f -> if (f.phone) MotionFeed.PHONE else MotionFeed.CONTROLLER } ?: MotionFeed.NONE) }

    /** The host's SetMotionEventState; [rateHz] 0 stops. */
    fun onRequest(controller: Int, type: MotionType, rateHz: Int) {
        val key = Key(controller, type)
        if (rateHz <= 0) requests.remove(key) else requests[key] = rateHz
        apply(key)
    }

    /** Settings changed or a pad came or went: re-route every request. */
    fun refresh() {
        current = settings()
        (requests.keys + feeds.keys).toSet().forEach(::apply)
    }

    fun release() {
        feeds.values.forEach { it.manager.unregisterListener(it) }
        feeds.clear()
        requests.clear()
    }

    /** LI_CCAP bits this device's own sensors add to controller 0 when it serves motion. */
    fun phoneCapabilities(): Int {
        val s = current
        if (s.source != MotionSource.PHONE && !(s.source == MotionSource.CONTROLLER && s.phoneFallback)) return 0
        var c = 0
        if (phoneSensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) c = c or MoonBridge.LI_CCAP_GYRO.toInt()
        if (phoneSensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) c = c or MoonBridge.LI_CCAP_ACCEL.toInt()
        return c
    }

    private fun sensorType(t: MotionType) = if (t == MotionType.GYRO) Sensor.TYPE_GYROSCOPE else Sensor.TYPE_ACCELEROMETER

    @SuppressLint("NewApi")
    private fun padManager(controller: Int): SensorManager? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val id = lookup()?.deviceIdFor(controller) ?: return null
        return InputDevice.getDevice(id)?.sensorManager
    }

    private fun apply(key: Key) {
        feeds.remove(key)?.let { it.manager.unregisterListener(it) }
        val rate = requests[key] ?: return
        val type = sensorType(key.type)
        val pad = padManager(key.controller)
        val feed = MotionRouting.resolve(
            current, key.controller,
            padHasSensor = pad?.getDefaultSensor(type) != null,
            phoneHasSensor = phoneSensors?.getDefaultSensor(type) != null,
        )
        val manager = when (feed) {
            MotionFeed.CONTROLLER -> pad
            MotionFeed.PHONE -> phoneSensors
            MotionFeed.NONE -> null
        } ?: return
        val f = Feed(key, manager, phone = feed == MotionFeed.PHONE)
        val ok = manager.registerListener(f, manager.getDefaultSensor(type), 1_000_000 / rate.coerceIn(1, 1000))
        if (ok) feeds[key] = f else Log.w(TAG, "Couldn't start ${key.type} for controller ${key.controller}")
    }

    private companion object {
        const val TAG = "NebulaMotion"
    }
}
