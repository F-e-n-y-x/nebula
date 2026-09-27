package io.github.f_e_n_y_x.nebula.data.engine

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import kotlin.math.abs
import kotlin.math.max

/** Gamepad options, from V+'s preferences. */
data class GamepadConfig(
    /** Each physical pad becomes its own player; off = every pad drives player 1. */
    val multiController: Boolean = true,
    /** Swap A/B and X/Y (Nintendo layout). */
    val flipFaceButtons: Boolean = false,
    /** Stick deadzone, 0..1. */
    val deadzone: Float = 0.07f,
)

/**
 * Turns Android gamepad events into host controllers: each device gets the next free player slot
 * (up to 16) unless multi-controller is off. Start + Select together calls [onMenu] instead of
 * reaching the game.
 */
class GamepadMapper(
    private val input: () -> RemoteInput?,
    private val onMenu: () -> Unit,
    private val config: () -> GamepadConfig = { GamepadConfig() },
) {
    private class Pad(val index: Int) {
        var buttons = 0
        var lt = 0
        var rt = 0
        var lx = 0
        var ly = 0
        var rx = 0
        var ry = 0
        var hat = 0
        var announced = false
    }

    private val pads = LinkedHashMap<Int, Pad>()

    /** Bitmask of every connected player slot, as the host expects. */
    private val activeMask: Int get() = pads.values.fold(0) { m, p -> m or (1 shl p.index) }.coerceAtLeast(1)

    /** How many pads are mapped right now. */
    val count: Int get() = pads.size

    fun handles(event: InputEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)

    fun onKey(event: KeyEvent): Boolean {
        if (!handles(event)) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        val pad = padFor(event.deviceId)
        when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> pad.lt = if (down) 255 else 0
            KeyEvent.KEYCODE_BUTTON_R2 -> pad.rt = if (down) 255 else 0
            else -> {
                val flag = flagFor(event.keyCode, config().flipFaceButtons) ?: return false
                pad.buttons = if (down) pad.buttons or flag else pad.buttons and flag.inv()
            }
        }
        if (pad.buttons and MENU_COMBO == MENU_COMBO) {
            pad.buttons = 0
            send(pad)
            onMenu()
            return true
        }
        send(pad)
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) return false
        val pad = padFor(event.deviceId)
        val dz = config().deadzone
        pad.lx = stick(event.getAxisValue(MotionEvent.AXIS_X), dz)
        pad.ly = -stick(event.getAxisValue(MotionEvent.AXIS_Y), dz)
        pad.rx = stick(event.getAxisValue(MotionEvent.AXIS_Z), dz)
        pad.ry = -stick(event.getAxisValue(MotionEvent.AXIS_RZ), dz)
        pad.lt = trigger(max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)))
        pad.rt = trigger(max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)))
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        pad.hat = (if (hx < -0.5f) ControllerPacket.LEFT_FLAG else 0) or (if (hx > 0.5f) ControllerPacket.RIGHT_FLAG else 0) or
            (if (hy < -0.5f) ControllerPacket.UP_FLAG else 0) or (if (hy > 0.5f) ControllerPacket.DOWN_FLAG else 0)
        send(pad)
        return true
    }

    /** A pad was unplugged: release its buttons on the host and free its slot. */
    fun onDeviceRemoved(deviceId: Int) {
        val pad = pads.remove(deviceId) ?: return
        input()?.gamepad(pad.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
    }

    /** Releases everything, e.g. when the stream menu opens. */
    fun releaseAll() {
        val o = input() ?: return
        pads.values.forEach { p ->
            p.buttons = 0; p.hat = 0; p.lt = 0; p.rt = 0; p.lx = 0; p.ly = 0; p.rx = 0; p.ry = 0
            if (p.announced) o.gamepad(p.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun padFor(deviceId: Int): Pad = pads.getOrPut(deviceId) {
        if (!config().multiController) {
            Pad(0)
        } else {
            val used = pads.values.map { it.index }.toSet()
            Pad((0 until MAX_PADS).firstOrNull { it !in used } ?: 0)
        }
    }

    private fun send(pad: Pad) {
        val bridge = input() ?: return
        if (!pad.announced) {
            bridge.gamepadArrived(pad.index, activeMask, MoonBridge.LI_CTYPE_UNKNOWN, SUPPORTED, (MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort())
            pad.announced = true
        }
        bridge.gamepad(pad.index, activeMask, pad.buttons or pad.hat, pad.lt, pad.rt, pad.lx, pad.ly, pad.rx, pad.ry)
    }

    private fun stick(v: Float, dz: Float) = if (abs(v) < dz) 0 else (v * 32766).toInt().coerceIn(-32767, 32767)
    private fun trigger(v: Float) = (v.coerceIn(0f, 1f) * 255).toInt()

    private fun flagFor(keyCode: Int, flip: Boolean): Int? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> if (flip) ControllerPacket.B_FLAG else ControllerPacket.A_FLAG
        KeyEvent.KEYCODE_BUTTON_B -> if (flip) ControllerPacket.A_FLAG else ControllerPacket.B_FLAG
        KeyEvent.KEYCODE_BUTTON_X -> if (flip) ControllerPacket.Y_FLAG else ControllerPacket.X_FLAG
        KeyEvent.KEYCODE_BUTTON_Y -> if (flip) ControllerPacket.X_FLAG else ControllerPacket.Y_FLAG
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

    companion object {
        const val MAX_PADS = 16
        val MENU_COMBO = ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG
        val SUPPORTED = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or
            ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or
            ControllerPacket.LS_CLK_FLAG or ControllerPacket.RS_CLK_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG
    }
}
