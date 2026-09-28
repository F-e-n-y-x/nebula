package io.github.f_e_n_y_x.nebula.data.engine

import android.view.MotionEvent
import io.github.f_e_n_y_x.nebula.diagnostics.StickCalibration
import io.github.f_e_n_y_x.nebula.diagnostics.StickShape
import kotlin.math.hypot

/**
 * Which Android axes carry each stick and trigger on one controller. Android reports the same
 * physical control on different axes depending on the device's key layout: most pads put the
 * right stick on Z/RZ and the triggers on LTRIGGER/RTRIGGER (or BRAKE/GAS), while xpad-style
 * pads without a vendor layout report the right stick on RX/RY and the triggers on Z/RZ, idling
 * at -1. The choice follows moonlight-android's ControllerHandler, from the axes the device
 * declares. -1 means the device has no such axis.
 */
data class GamepadAxes(
    val leftX: Int = MotionEvent.AXIS_X,
    val leftY: Int = MotionEvent.AXIS_Y,
    val rightX: Int = -1,
    val rightY: Int = -1,
    val leftTrigger: Int = -1,
    val rightTrigger: Int = -1,
    /** Triggers sit on axes centred at zero, so released is -1 and fully pressed is +1. */
    val triggersIdleNegative: Boolean = false,
) {
    /** Host-ready values: sticks as signed 16-bit (up is positive), triggers 0-255. */
    data class Values(val lx: Int, val ly: Int, val rx: Int, val ry: Int, val lt: Int, val rt: Int)

    /**
     * Reads one event's axes through this layout. [axis] returns an axis value (MotionEvent's
     * getAxisValue). The deadzone is radial per stick, as in moonlight. A saved [calibration]
     * (Settings → Diagnostics) replaces the deadzone with that pad's own per-stick shape.
     */
    fun read(axis: (Int) -> Float, deadzone: Float, calibration: StickCalibration? = null): Values {
        val (lx, ly) = stick(axis, leftX, leftY, deadzone, calibration?.left)
        val (rx, ry) = stick(axis, rightX, rightY, deadzone, calibration?.right)
        return Values(lx, ly, rx, ry, trigger(axis, leftTrigger), trigger(axis, rightTrigger))
    }

    private fun stick(axis: (Int) -> Float, xAxis: Int, yAxis: Int, deadzone: Float, shape: StickShape?): Pair<Int, Int> {
        if (xAxis < 0 || yAxis < 0) return 0 to 0
        if (shape != null) {
            val (x, y) = shape.apply(axis(xAxis), axis(yAxis))
            return toShort(x) to toShort(-y)
        }
        val x = axis(xAxis)
        val y = axis(yAxis)
        if (hypot(x, y) <= deadzone) return 0 to 0
        // Android's Y grows downwards; the host's grows upwards.
        return toShort(x) to toShort(-y)
    }

    private fun trigger(axis: (Int) -> Float, a: Int): Int {
        if (a < 0) return 0
        val v = axis(a).let { if (triggersIdleNegative) (it + 1f) / 2f else it }
        return (v.coerceIn(0f, 1f) * 255f).toInt()
    }

    private fun toShort(v: Float) = (v.coerceIn(-1f, 1f) * 32767f).toInt()

    companion object {
        /**
         * Picks the layout for a device that declares [axes] (the joystick axes of its motion
         * ranges). [nonStandardDualShock4] is the old Sony driver that puts the triggers on RX/RY.
         */
        fun forAxes(axes: Set<Int>, nonStandardDualShock4: Boolean = false): GamepadAxes {
            fun has(vararg a: Int) = a.all { it in axes }
            var rightX = -1
            var rightY = -1
            var leftTrigger = -1
            var rightTrigger = -1
            var idleNegative = false
            when {
                has(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER) -> {
                    leftTrigger = MotionEvent.AXIS_LTRIGGER; rightTrigger = MotionEvent.AXIS_RTRIGGER
                }
                has(MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS) -> {
                    leftTrigger = MotionEvent.AXIS_BRAKE; rightTrigger = MotionEvent.AXIS_GAS
                }
                has(MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_THROTTLE) -> {
                    leftTrigger = MotionEvent.AXIS_BRAKE; rightTrigger = MotionEvent.AXIS_THROTTLE
                }
                has(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY) -> {
                    if (nonStandardDualShock4) {
                        leftTrigger = MotionEvent.AXIS_RX; rightTrigger = MotionEvent.AXIS_RY
                    } else {
                        // xpad-style: right stick on RX/RY; Z/RZ, when present, are the triggers.
                        rightX = MotionEvent.AXIS_RX; rightY = MotionEvent.AXIS_RY
                        if (has(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ)) {
                            leftTrigger = MotionEvent.AXIS_Z; rightTrigger = MotionEvent.AXIS_RZ
                        }
                    }
                    idleNegative = true
                }
            }
            if (rightX == -1 && rightY == -1) {
                when {
                    has(MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ) -> { rightX = MotionEvent.AXIS_Z; rightY = MotionEvent.AXIS_RZ }
                    has(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY) -> { rightX = MotionEvent.AXIS_RX; rightY = MotionEvent.AXIS_RY }
                }
            }
            val hasLeft = has(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)
            return GamepadAxes(
                leftX = if (hasLeft) MotionEvent.AXIS_X else -1,
                leftY = if (hasLeft) MotionEvent.AXIS_Y else -1,
                rightX = rightX,
                rightY = rightY,
                leftTrigger = leftTrigger,
                rightTrigger = rightTrigger,
                triggersIdleNegative = idleNegative,
            )
        }
    }
}

/**
 * What the mapper needs to know about an input device to decide whether it is a controller.
 * Kept free of android.view.InputDevice so the rules can be unit tested.
 */
data class ControllerTraits(
    val id: Int,
    /** InputDevice.isVirtual: the framework's virtual keyboard, injected events and the like. */
    val isVirtual: Boolean,
    /** Declares SOURCE_JOYSTICK with both AXIS_X and AXIS_Y motion ranges. */
    val hasSticks: Boolean,
    /** Declares SOURCE_GAMEPAD (face buttons, shoulders, ...). */
    val hasGamepadButtons: Boolean,
    val vendorId: Int = 0,
    val productId: Int = 0,
) {
    /** A real game controller, as moonlight counts them: never a virtual device. */
    val isController: Boolean get() = !isVirtual && id >= 0 && (hasSticks || hasGamepadButtons)

    /**
     * A buttons-only companion of a controller (a pad's separate key or touchpad node, a remote
     * with gamepad keys). It never gets a host controller of its own while a real pad exists.
     */
    val isCompanion: Boolean get() = isController && !hasSticks
}
