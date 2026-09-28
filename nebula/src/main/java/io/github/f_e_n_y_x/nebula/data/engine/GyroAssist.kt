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
import io.github.f_e_n_y_x.nebula.input.Feedback
import io.github.f_e_n_y_x.nebula.input.GyroAim
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.settings.MotionHold
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.MotionSource

/** Where the mapping modes read the gyro from, decided by [GyroAssistRouting.pick]. */
sealed interface GyroFeed {
    data object None : GyroFeed
    data object Phone : GyroFeed
    /** The IMU of the controller with this Android device id (Android 13+). */
    data class Controller(val deviceId: Int) : GyroFeed
}

/** Pure source choice for the mapping modes, unit-tested. */
object GyroAssistRouting {
    /** [controllerWithGyro]: the first attached controller whose IMU Android exposes, or null. */
    fun pick(s: MotionSettings, controllerWithGyro: Int?, phoneHasGyro: Boolean): GyroFeed = when {
        !s.mode.mapsLocally -> GyroFeed.None
        s.source == MotionSource.CONTROLLER && controllerWithGyro != null -> GyroFeed.Controller(controllerWithGyro)
        (s.source == MotionSource.PHONE || s.phoneFallback) && phoneHasGyro -> GyroFeed.Phone
        else -> GyroFeed.None
    }

    /** True when [keyCode] is the toggle button and the gyro uses it; flips [GyroToggle] on press. Consumed either way. */
    fun onKey(s: MotionSettings, keyCode: Int, down: Boolean): Boolean {
        if (s.mode == GyroMode.OFF || s.hold != MotionHold.TOGGLE || keyCode != s.toggleButton.keyCode) return false
        if (down) GyroToggle.on = !GyroToggle.on
        return true
    }
}

/**
 * "Gyro to right stick" and "Gyro to mouse": reads the controller's IMU (Android 13+) or this
 * device's gyro (rotation-corrected into the controller frame) and turns it into right-stick
 * deflection on the [pad] mapper or relative mouse movement through [mouse]. Works in every game
 * and with any virtual controller on the PC, because the host only sees stick or mouse input.
 * Sensors run only while [update] says the stream is active. Main thread.
 */
class GyroAssist(
    context: Context,
    private val pad: () -> GamepadMapper?,
    private val settings: () -> MotionSettings,
    private val rotation: () -> Int,
    private val mouse: (dx: Int, dy: Int) -> Unit,
) {
    private val phoneSensors = context.getSystemService(SensorManager::class.java)
    private val aim = GyroAim()
    private var feed: GyroFeed = GyroFeed.None
    private var manager: SensorManager? = null
    private var current = settings()
    private var lastGyroNanos = 0L
    private var stickDevice: Int? = null
    private var stickSent = false

    val phoneHasGyro: Boolean get() = phoneSensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    /** Where the gyro is read from right now, for the stream menu. */
    val activeFeed: GyroFeed get() = feed

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val phone = feed == GyroFeed.Phone
            val (x, y, z) = if (phone) Feedback.phoneToController(e.values[0], e.values[1], e.values[2], rotation())
            else Triple(e.values[0], e.values[1], e.values[2])
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> aim.onAccel(x, y, z)
                Sensor.TYPE_GYROSCOPE -> onGyro(x * Feedback.RAD_TO_DEG, y * Feedback.RAD_TO_DEG, z * Feedback.RAD_TO_DEG, e.timestamp)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Toggle button: true when the key was the gyro toggle (kept from the PC). */
    fun onKey(keyCode: Int, down: Boolean): Boolean = GyroAssistRouting.onKey(current, keyCode, down)

    /** Starts, re-targets or stops the sensors; call when settings, pads or [active] change. */
    fun update(active: Boolean) {
        current = settings()
        val want = if (active) GyroAssistRouting.pick(current, controllerWithGyro(), phoneHasGyro) else GyroFeed.None
        if (want == feed) return
        stop()
        val m = when (want) {
            GyroFeed.None -> null
            GyroFeed.Phone -> phoneSensors
            is GyroFeed.Controller -> controllerSensors(want.deviceId)
        } ?: return
        val gyro = m.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return
        if (!m.registerListener(listener, gyro, SAMPLE_US)) {
            Log.w(TAG, "Couldn't start the gyro for $want")
            return
        }
        m.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { m.registerListener(listener, it, SAMPLE_US) }
        manager = m
        feed = want
        stickDevice = (want as? GyroFeed.Controller)?.deviceId
    }

    fun release() = stop()

    private fun stop() {
        manager?.unregisterListener(listener)
        manager = null
        centerStick()
        feed = GyroFeed.None
        aim.reset()
        lastGyroNanos = 0L
    }

    private fun onGyro(gx: Float, gy: Float, gz: Float, nanos: Long) {
        val s = current
        val index = pad()?.let { p -> (stickDevice?.let { id -> p.indices().firstOrNull { p.deviceIdFor(it) == id } } ?: 0) }
        val (lt, rt) = index?.let { pad()?.triggers(it) } ?: (0 to 0)
        val dt = if (lastGyroNanos == 0L) 0f else (nanos - lastGyroNanos) / 1e9f
        lastGyroNanos = nanos
        if (!s.hold.allows(lt, rt, GyroToggle.on)) {
            aim.reset()
            centerStick()
            return
        }
        when (s.mode) {
            GyroMode.RIGHT_STICK -> {
                val (x, y) = aim.stick(gx, gy, gz, s)
                pad()?.gyroStick(stickDevice, x, y)
                stickSent = x != 0 || y != 0
            }
            GyroMode.MOUSE -> {
                centerStick()
                val (dx, dy) = aim.mouse(gx, gy, gz, dt, s)
                if (dx != 0 || dy != 0) mouse(dx, dy)
            }
            else -> Unit
        }
    }

    private fun centerStick() {
        if (!stickSent) return
        pad()?.gyroStick(stickDevice, 0, 0)
        stickSent = false
    }

    @SuppressLint("NewApi")
    private fun controllerSensors(deviceId: Int): SensorManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) InputDevice.getDevice(deviceId)?.sensorManager else null

    /** The first attached controller whose own gyro Android exposes (Android 13+). */
    private fun controllerWithGyro(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return GamepadMapper.attachedControllers().firstOrNull { id ->
            controllerSensors(id)?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        }
    }

    private companion object {
        const val TAG = "NebulaGyroAssist"
        /** 200 Hz. */
        const val SAMPLE_US = 5_000
    }
}
