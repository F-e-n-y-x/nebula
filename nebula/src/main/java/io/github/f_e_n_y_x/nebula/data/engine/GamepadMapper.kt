package io.github.f_e_n_y_x.nebula.data.engine

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.fenyx.nebula.engine.InputBridge
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns Android gamepad events into one host controller (player 1). A basic mapping for the base
 * app; V+'s full ControllerHandler (multiple pads, remapping, DS5 extras) moves over in a later part.
 * Start + Select together calls [onMenu] instead of reaching the game.
 */
class GamepadMapper(private val input: () -> InputBridge?, private val onMenu: () -> Unit) {
    private var buttons = 0
    private var lt = 0
    private var rt = 0
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0
    private var hat = 0
    private var announced = false

    fun handles(event: InputEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)

    fun onKey(event: KeyEvent): Boolean {
        if (!handles(event)) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> lt = if (down) 255 else 0
            KeyEvent.KEYCODE_BUTTON_R2 -> rt = if (down) 255 else 0
            else -> {
                val flag = flagFor(event.keyCode) ?: return false
                buttons = if (down) buttons or flag else buttons and flag.inv()
            }
        }
        if (buttons and MENU_COMBO == MENU_COMBO) {
            buttons = 0
            send()
            onMenu()
            return true
        }
        send()
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        lx = stick(event.getAxisValue(MotionEvent.AXIS_X))
        ly = -stick(event.getAxisValue(MotionEvent.AXIS_Y))
        rx = stick(event.getAxisValue(MotionEvent.AXIS_Z))
        ry = -stick(event.getAxisValue(MotionEvent.AXIS_RZ))
        lt = trigger(max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)))
        rt = trigger(max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)))
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        hat = (if (hx < -0.5f) ControllerPacket.LEFT_FLAG else 0) or (if (hx > 0.5f) ControllerPacket.RIGHT_FLAG else 0) or
            (if (hy < -0.5f) ControllerPacket.UP_FLAG else 0) or (if (hy > 0.5f) ControllerPacket.DOWN_FLAG else 0)
        send()
        return true
    }

    private fun send() {
        val bridge = input() ?: return
        if (!announced) {
            bridge.gamepadArrived(0, 1, MoonBridge.LI_CTYPE_UNKNOWN, SUPPORTED, (MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort())
            announced = true
        }
        bridge.gamepad(0, 1, buttons or hat, lt, rt, lx, ly, rx, ry)
    }

    private fun stick(v: Float) = if (abs(v) < DEADZONE) 0 else (v * 32766).toInt().coerceIn(-32767, 32767)
    private fun trigger(v: Float) = (v.coerceIn(0f, 1f) * 255).toInt()

    private fun flagFor(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ControllerPacket.A_FLAG
        KeyEvent.KEYCODE_BUTTON_B -> ControllerPacket.B_FLAG
        KeyEvent.KEYCODE_BUTTON_X -> ControllerPacket.X_FLAG
        KeyEvent.KEYCODE_BUTTON_Y -> ControllerPacket.Y_FLAG
        KeyEvent.KEYCODE_DPAD_UP -> ControllerPacket.UP_FLAG
        KeyEvent.KEYCODE_DPAD_DOWN -> ControllerPacket.DOWN_FLAG
        KeyEvent.KEYCODE_DPAD_LEFT -> ControllerPacket.LEFT_FLAG
        KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerPacket.RIGHT_FLAG
        KeyEvent.KEYCODE_BUTTON_L1 -> ControllerPacket.LB_FLAG
        KeyEvent.KEYCODE_BUTTON_R1 -> ControllerPacket.RB_FLAG
        KeyEvent.KEYCODE_BUTTON_START -> ControllerPacket.PLAY_FLAG
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> ControllerPacket.BACK_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerPacket.LS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerPacket.RS_CLK_FLAG
        KeyEvent.KEYCODE_BUTTON_MODE -> ControllerPacket.SPECIAL_BUTTON_FLAG
        else -> null
    }

    private companion object {
        const val DEADZONE = 0.07f
        val MENU_COMBO = ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG
        val SUPPORTED = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or
            ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or
            ControllerPacket.LS_CLK_FLAG or ControllerPacket.RS_CLK_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG
    }
}
