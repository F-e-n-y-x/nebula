package io.github.f_e_n_y_x.nebula.diagnostics

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.CombinedVibration
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.f_e_n_y_x.nebula.StreamInputSink

/** Live state of one controller while the test screen is open. */
class PadLive(val device: ControllerDevice) {
    /** Latest value of every axis the device declares. */
    var axes by mutableStateOf<Map<Int, Float>>(device.axes.associate { it.axis to 0f })
    /** Largest distance from the resting value each axis has reached; a mapped axis stuck at 0 is a dead axis. */
    var peaks by mutableStateOf<Map<Int, Float>>(emptyMap())
    var keys by mutableStateOf<Set<Int>>(emptySet())
    var lastKey by mutableStateOf<Int?>(null)
    var events by mutableIntStateOf(0)
    /** Motion events per second over the last second. */
    var rateHz by mutableIntStateOf(0)
    private var windowStart = SystemClock.uptimeMillis()
    private var windowCount = 0

    fun axis(a: Int): Float = if (a < 0) 0f else axes[a] ?: 0f

    internal fun motion(e: MotionEvent) {
        val now = device.axes.associate { it.axis to e.getAxisValue(it.axis) }
        axes = now
        val p = peaks.toMutableMap()
        device.axes.forEach { r ->
            // Axes that rest at -1 (xpad triggers) measure from there.
            val rest = if (r.min < -0.5f && r.axis in triggerAxes && device.layout.triggersIdleNegative) -1f else 0f
            p[r.axis] = maxOf(p[r.axis] ?: 0f, kotlin.math.abs((now[r.axis] ?: 0f) - rest))
        }
        peaks = p
        events++
        windowCount++
        val t = SystemClock.uptimeMillis()
        if (t - windowStart >= 1000) {
            rateHz = (windowCount * 1000 / (t - windowStart)).toInt()
            windowStart = t
            windowCount = 0
        }
    }

    private val triggerAxes = setOf(device.layout.leftTrigger, device.layout.rightTrigger)

    internal fun key(code: Int, down: Boolean) {
        keys = if (down) keys + code else keys - code
        if (down) lastKey = code
        events++
    }
}

/**
 * Reads every controller while the controller test or calibration is on screen. [sink] goes into
 * MainActivity.streamInput, so gamepad events reach this instead of moving focus; everything else
 * (touch, a TV remote's D-pad and Back) still drives the UI. Start + Select together calls
 * [onReleaseCombo] so a gamepad-only user can get the UI back.
 */
class ControllerTester(context: Context, private val onReleaseCombo: () -> Unit) {
    private val input = context.getSystemService(InputManager::class.java)
    val pads = mutableStateListOf<PadLive>()

    private val listener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) { StickCalibrations.forget(deviceId); refresh() }
        override fun onInputDeviceChanged(deviceId: Int) { StickCalibrations.forget(deviceId); refresh() }
    }

    fun start() {
        input?.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        input?.unregisterInputDeviceListener(listener)
    }

    fun refresh() {
        val now = ControllerInfo.list()
        val keep = pads.associateBy { it.device.id }
        pads.clear()
        pads.addAll(now.map { d -> keep[d.id]?.takeIf { it.device == d } ?: PadLive(d) })
    }

    private fun padFor(deviceId: Int): PadLive? = pads.firstOrNull { it.device.id == deviceId }

    val sink = object : StreamInputSink {
        override fun onKey(event: KeyEvent): Boolean {
            val pad = padFor(event.deviceId) ?: return false
            if (!event.isFromSource(InputDevice.SOURCE_GAMEPAD) && !event.isFromSource(InputDevice.SOURCE_JOYSTICK) &&
                !event.isFromSource(InputDevice.SOURCE_DPAD)
            ) return false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) pad.key(event.keyCode, true)
                KeyEvent.ACTION_UP -> pad.key(event.keyCode, false)
            }
            val k = pad.keys
            val select = KeyEvent.KEYCODE_BUTTON_SELECT in k || KeyEvent.KEYCODE_BACK in k
            if (KeyEvent.KEYCODE_BUTTON_START in k && select) {
                pad.keys = emptySet()
                onReleaseCombo()
            }
            // A remote that also claims gamepad keys must keep driving the UI: show its keys, don't eat them.
            return pad.device.traits.hasSticks || event.keyCode !in NAV_KEYS
        }

        override fun onMotion(event: MotionEvent): Boolean {
            if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return false
            val pad = padFor(event.deviceId) ?: return false
            pad.motion(event)
            return true
        }
    }

    /** A short buzz on the pad's motors, if it has any. */
    @Suppress("DEPRECATION")
    fun rumble(deviceId: Int) {
        val d = InputDevice.getDevice(deviceId) ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = d.vibratorManager
                if (vm.vibratorIds.isEmpty()) return
                vm.vibrate(CombinedVibration.createParallel(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE)))
            } else if (d.vibrator.hasVibrator()) {
                d.vibrator.vibrate(VibrationEffect.createOneShot(350, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }

    private companion object {
        val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
        )
    }
}
