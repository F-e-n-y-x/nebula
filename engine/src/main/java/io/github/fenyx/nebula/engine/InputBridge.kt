package io.github.fenyx.nebula.engine

import android.view.KeyEvent
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge

/**
 * Input for a running [StreamSession]. Keyboard events go through V+'s [KeyboardTranslator]
 * (Android keycodes → Windows virtual keys, layout-aware); everything else is sent as-is.
 * Calls are cheap and thread-safe; they're dropped once the session ends.
 */
class InputBridge internal constructor(private val connection: NvConnection) {
    private val keyboard = KeyboardTranslator()

    /** Sends an Android key event; returns false when the key has no host mapping. */
    fun sendKey(event: KeyEvent): Boolean {
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return false
        }
        val vk = keyboard.translate(event.keyCode, event.deviceId)
        if (vk.toInt() == 0) return false
        val flags = if (keyboard.hasNormalizedMapping(event.keyCode, event.deviceId)) 0 else MoonBridge.SS_KBE_FLAG_NON_NORMALIZED.toInt()
        connection.sendKeyboardInput(
            vk,
            if (down) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP,
            modifiers(event),
            flags.toByte(),
        )
        return true
    }

    /** Sends a raw Windows virtual-key code, e.g. for an on-screen shortcut bar. */
    fun sendVirtualKey(vk: Short, down: Boolean, modifiers: Byte = 0) {
        connection.sendKeyboardInput(vk, if (down) KeyboardPacket.KEY_DOWN else KeyboardPacket.KEY_UP, modifiers, 0)
    }

    /** Types text on the host (IME / clipboard-style input). */
    fun sendText(text: String) = connection.sendUtf8Text(text)

    fun moveMouse(dx: Int, dy: Int) = connection.sendMouseMove(dx.toShort(), dy.toShort())

    /** Absolute pointer position within a [referenceWidth] × [referenceHeight] frame (usually the view size). */
    fun setMousePosition(x: Int, y: Int, referenceWidth: Int, referenceHeight: Int) =
        connection.sendMousePosition(x.toShort(), y.toShort(), referenceWidth.toShort(), referenceHeight.toShort())

    fun mouseButton(button: MouseButton, down: Boolean) {
        if (down) connection.sendMouseButtonDown(button.wire) else connection.sendMouseButtonUp(button.wire)
    }

    /** High-resolution vertical scroll; 120 units is one wheel notch. */
    fun scroll(amount: Int) = connection.sendMouseHighResScroll(amount.toShort())

    fun scrollHorizontal(amount: Int) = connection.sendMouseHighResHScroll(amount.toShort())

    /**
     * Sends a touch contact in normalized [0, 1] coordinates. [eventType] is one of MoonBridge's
     * LI_TOUCH_EVENT_* values. Returns MoonBridge.LI_ERR_UNSUPPORTED when the host lacks native touch.
     */
    fun touch(eventType: Byte, pointerId: Int, x: Float, y: Float, pressure: Float = 1f): Int =
        connection.sendTouchEvent(eventType, pointerId, x, y, pressure, 0f, 0f, MoonBridge.LI_ROT_UNKNOWN)

    /**
     * Full gamepad state for [controller] (0–15). Buttons use ControllerPacket flags; sticks are
     * signed 16-bit, triggers 0–255.
     */
    fun gamepad(
        controller: Int,
        activeMask: Int,
        buttons: Int,
        leftTrigger: Int,
        rightTrigger: Int,
        leftX: Int,
        leftY: Int,
        rightX: Int,
        rightY: Int,
    ) = connection.sendControllerInput(
        controller.toShort(), activeMask.toShort(), buttons,
        leftTrigger.toByte(), rightTrigger.toByte(),
        leftX.toShort(), leftY.toShort(), rightX.toShort(), rightY.toShort(),
    )

    /** Announces a controller so the host creates a matching virtual pad (type: MoonBridge.LI_CTYPE_*). */
    fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) =
        connection.sendControllerArrivalEvent(controller.toByte(), activeMask.toShort(), type, supportedButtons, capabilities)

    /**
     * One motion sample for [controller]: gyro in degrees per second, accelerometer in m/s²,
     * in the controller's frame (the SDL/DualShock convention the host expects).
     */
    fun motion(controller: Int, type: MotionType, x: Float, y: Float, z: Float) {
        connection.sendControllerMotionEvent(controller.toByte(), type.wire, x, y, z)
    }

    private fun modifiers(event: KeyEvent): Byte {
        var m = 0
        if (event.isShiftPressed) m = m or KeyboardPacket.MODIFIER_SHIFT.toInt()
        if (event.isCtrlPressed) m = m or KeyboardPacket.MODIFIER_CTRL.toInt()
        if (event.isAltPressed) m = m or KeyboardPacket.MODIFIER_ALT.toInt()
        if (event.isMetaPressed) m = m or KeyboardPacket.MODIFIER_META.toInt()
        return m.toByte()
    }
}

enum class MouseButton(internal val wire: Byte) {
    LEFT(MouseButtonPacket.BUTTON_LEFT),
    MIDDLE(MouseButtonPacket.BUTTON_MIDDLE),
    RIGHT(MouseButtonPacket.BUTTON_RIGHT),
    BACK(MouseButtonPacket.BUTTON_X1),
    FORWARD(MouseButtonPacket.BUTTON_X2),
}
