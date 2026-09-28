package io.github.f_e_n_y_x.nebula.diagnostics

import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import io.github.f_e_n_y_x.nebula.data.engine.ControllerTraits
import io.github.f_e_n_y_x.nebula.data.engine.GamepadAxes
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper

/** One joystick axis a controller declares. */
data class AxisRange(val axis: Int, val name: String, val min: Float, val max: Float, val flat: Float, val fuzz: Float)

/** A connected controller as the stream would see it: identity, declared axes and the layout GamepadAxes picked. */
data class ControllerDevice(
    val id: Int,
    val name: String,
    val descriptor: String,
    val vendorId: Int,
    val productId: Int,
    val traits: ControllerTraits,
    val layout: GamepadAxes,
    val axes: List<AxisRange>,
    val sources: String,
    val hasVibrator: Boolean,
    val battery: String?,
    val external: Boolean?,
) {
    val ids: String get() = "%04X:%04X".format(vendorId, productId)

    /** A buttons-only node of a pad (it joins that pad instead of being a player). */
    val isCompanion: Boolean get() = traits.isCompanion
}

object ControllerInfo {
    /** Every controller Nebula would map, in device id order. */
    fun list(): List<ControllerDevice> = InputDevice.getDeviceIds().sorted().mapNotNull { inspect(it) }

    fun inspect(id: Int): ControllerDevice? {
        val d = InputDevice.getDevice(id) ?: return null
        val traits = GamepadMapper.deviceTraits(id) ?: return null
        if (!traits.isController) return null
        val axes = d.motionRanges.filter { it.isFromSource(InputDevice.SOURCE_JOYSTICK) }.distinctBy { it.axis }.sortedBy { it.axis }.map {
            AxisRange(it.axis, axisName(it.axis), it.min, it.max, it.flat, it.fuzz)
        }
        return ControllerDevice(
            id = id,
            name = d.name,
            descriptor = d.descriptor,
            vendorId = d.vendorId,
            productId = d.productId,
            traits = traits,
            layout = GamepadMapper.deviceAxes(id),
            axes = axes,
            sources = sources(d.sources),
            hasVibrator = hasVibrator(d),
            battery = battery(d),
            external = if (Build.VERSION.SDK_INT >= 29) d.isExternal else null,
        )
    }

    fun axisName(axis: Int): String = MotionEvent.axisToString(axis).removePrefix("AXIS_")

    /** "Left X/Y · Right Z/RZ · Triggers LTRIGGER/RTRIGGER". */
    fun describeLayout(l: GamepadAxes): String {
        fun pair(a: Int, b: Int) = if (a < 0 || b < 0) "none" else "${axisName(a)}/${axisName(b)}"
        val triggers = if (l.leftTrigger < 0 && l.rightTrigger < 0) "buttons (L2/R2 keys)" else pair(l.leftTrigger, l.rightTrigger) + if (l.triggersIdleNegative) " (rest at −1)" else ""
        return "Left ${pair(l.leftX, l.leftY)} · Right ${pair(l.rightX, l.rightY)} · Triggers $triggers"
    }

    fun describeAll(): String = runCatching {
        list().joinToString("\n\n") { c ->
            buildString {
                append(c.name).append("  [").append(c.ids).append("]  id ").append(c.id).append('\n')
                append("  descriptor ").append(c.descriptor).append('\n')
                append("  sources ").append(c.sources).append(if (c.isCompanion) " (companion node)" else "").append('\n')
                append("  layout ").append(describeLayout(c.layout)).append('\n')
                append("  axes ").append(c.axes.joinToString(", ") { "${it.name}[${fmt(it.min)}..${fmt(it.max)} flat ${fmt(it.flat)}]" }).append('\n')
                append("  vibrator ").append(if (c.hasVibrator) "yes" else "no")
                c.battery?.let { append(" · battery ").append(it) }
            }
        }.ifEmpty { "No controllers connected." }
    }.getOrElse { "Couldn't list controllers: $it" }

    @Suppress("DEPRECATION")
    fun hasVibrator(d: InputDevice): Boolean = if (Build.VERSION.SDK_INT >= 31) d.vibratorManager.vibratorIds.isNotEmpty() else d.vibrator.hasVibrator()

    private fun battery(d: InputDevice): String? {
        if (Build.VERSION.SDK_INT < 31) return null
        val b = d.batteryState
        if (!b.isPresent) return null
        val pct = b.capacity.takeIf { it >= 0f }?.let { "${(it * 100).toInt()} %" }
        val status = when (b.status) {
            android.hardware.BatteryState.STATUS_CHARGING -> "charging"
            android.hardware.BatteryState.STATUS_FULL -> "full"
            android.hardware.BatteryState.STATUS_DISCHARGING -> null
            else -> null
        }
        return listOfNotNull(pct, status).joinToString(" · ").ifEmpty { null }
    }

    private fun sources(s: Int): String = listOfNotNull(
        "gamepad".takeIf { s and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD },
        "joystick".takeIf { s and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK },
        "dpad".takeIf { s and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD },
        "keyboard".takeIf { s and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD },
        "touchpad".takeIf { s and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD },
        "mouse".takeIf { s and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE },
        "sensor".takeIf { Build.VERSION.SDK_INT >= 31 && s and InputDevice.SOURCE_SENSOR == InputDevice.SOURCE_SENSOR },
    ).joinToString(", ")

    fun keyName(code: Int): String = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    private fun fmt(v: Float) = "%.2f".format(v)
}
