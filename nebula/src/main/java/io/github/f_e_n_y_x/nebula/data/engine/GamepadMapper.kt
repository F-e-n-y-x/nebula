package io.github.f_e_n_y_x.nebula.data.engine

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.input.RemoteInput

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
 *
 * Only real controllers get a slot (never a virtual device), a slot is taken only when an event
 * actually reaches the host, and a buttons-only companion device joins an existing pad instead of
 * becoming a phantom second controller. Axes are read through each device's [GamepadAxes] layout.
 * While [oscSlot] is true the on-screen controls own player 1 (slot 0) and pads start at slot 1.
 */
class GamepadMapper(
    private val input: () -> RemoteInput?,
    private val onMenu: () -> Unit,
    private val config: () -> GamepadConfig = { GamepadConfig() },
    private val oscSlot: () -> Boolean = { false },
    private val traitsOf: (deviceId: Int) -> ControllerTraits? = ::deviceTraits,
    private val axesOf: (deviceId: Int) -> GamepadAxes = ::deviceAxes,
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

    /** Pads by Android device id; a companion device shares its pad's entry. */
    private val pads = LinkedHashMap<Int, Pad>()
    private val traits = HashMap<Int, ControllerTraits?>()
    private val layouts = HashMap<Int, GamepadAxes>()

    /** Bitmask of every connected player slot, as the host expects. */
    private val activeMask: Int
        get() = pads.values.fold(if (oscSlot()) 1 else 0) { m, p -> m or (1 shl p.index) }.coerceAtLeast(1)

    /** How many pads are mapped right now. */
    val count: Int get() = pads.values.distinct().size

    fun handles(event: InputEvent): Boolean =
        (event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)) &&
            traitsFor(event.deviceId)?.isController == true

    fun onKey(event: KeyEvent): Boolean = onKey(event.deviceId, event.keyCode, event.action, handles(event))

    /** [onKey] without the event, for tests; [fromController] is what [handles] said. */
    internal fun onKey(deviceId: Int, keyCode: Int, action: Int, fromController: Boolean): Boolean {
        if (!fromController) return false
        val down = when (action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2 ||
                flagFor(keyCode, false) != null
        }
        // Resolve the key first: an unmapped key must not reserve a player slot.
        val flag = if (keyCode == KeyEvent.KEYCODE_BUTTON_L2 || keyCode == KeyEvent.KEYCODE_BUTTON_R2) 0
        else flagFor(keyCode, config().flipFaceButtons) ?: return false
        val pad = padFor(deviceId)
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> pad.lt = if (down) 255 else 0
            KeyEvent.KEYCODE_BUTTON_R2 -> pad.rt = if (down) 255 else 0
            else -> pad.buttons = if (down) pad.buttons or flag else pad.buttons and flag.inv()
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
        return onMotion(event.deviceId, traitsFor(event.deviceId)?.let { it.isController && it.hasSticks } == true, event::getAxisValue)
    }

    /** [onMotion] without the event, for tests: [axis] reads one axis of the move. */
    internal fun onMotion(deviceId: Int, fromStickDevice: Boolean, axis: (Int) -> Float): Boolean {
        if (!fromStickDevice) return false
        val layout = layouts.getOrPut(deviceId) { axesOf(deviceId) }
        val pad = padFor(deviceId)
        val v = layout.read(axis, config().deadzone)
        pad.lx = v.lx; pad.ly = v.ly; pad.rx = v.rx; pad.ry = v.ry
        // Pads with digital L2/R2 keys have no trigger axes; leave what the keys set.
        if (layout.leftTrigger >= 0) pad.lt = v.lt
        if (layout.rightTrigger >= 0) pad.rt = v.rt
        val hx = axis(MotionEvent.AXIS_HAT_X)
        val hy = axis(MotionEvent.AXIS_HAT_Y)
        pad.hat = (if (hx < -0.5f) ControllerPacket.LEFT_FLAG else 0) or (if (hx > 0.5f) ControllerPacket.RIGHT_FLAG else 0) or
            (if (hy < -0.5f) ControllerPacket.UP_FLAG else 0) or (if (hy > 0.5f) ControllerPacket.DOWN_FLAG else 0)
        send(pad)
        return true
    }

    /** A pad was unplugged: release its buttons on the host and free its slot. */
    fun onDeviceRemoved(deviceId: Int) {
        traits.remove(deviceId)
        layouts.remove(deviceId)
        val pad = pads.remove(deviceId) ?: return
        // A companion node going away doesn't unplug the pad it belongs to.
        if (pad in pads.values) return
        if (pad.announced) input()?.gamepad(pad.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
    }

    /** A device's capabilities changed (e.g. a pad reconnected with another layout). */
    fun onDeviceChanged(deviceId: Int) {
        traits.remove(deviceId)
        layouts.remove(deviceId)
    }

    /** Releases everything, e.g. when the stream menu opens. */
    fun releaseAll() {
        val o = input() ?: return
        pads.values.forEach { p ->
            p.buttons = 0; p.hat = 0; p.lt = 0; p.rt = 0; p.lx = 0; p.ly = 0; p.rx = 0; p.ry = 0
            if (p.announced) o.gamepad(p.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun traitsFor(deviceId: Int): ControllerTraits? = traits.getOrPut(deviceId) { traitsOf(deviceId) }

    private fun padFor(deviceId: Int): Pad = pads.getOrPut(deviceId) {
        val first = if (oscSlot()) 1 else 0
        val me = traitsFor(deviceId)
        // A buttons-only node joins a real pad (the same model first) rather than adding a player.
        if (me?.isCompanion == true) {
            val owners = pads.entries.filter { traitsFor(it.key)?.hasSticks == true }
            val owner = owners.firstOrNull { traitsFor(it.key)?.let { t -> t.vendorId == me.vendorId && t.productId == me.productId } == true }
                ?: owners.firstOrNull()
            if (owner != null) return@getOrPut owner.value
        }
        if (!config().multiController) {
            Pad(first)
        } else {
            val used = pads.values.map { it.index }.toSet()
            Pad((first until MAX_PADS).firstOrNull { it !in used } ?: first)
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

        /** Reads [ControllerTraits] from the framework's InputDevice; null when it's gone. */
        fun deviceTraits(deviceId: Int): ControllerTraits? {
            val d = InputDevice.getDevice(deviceId) ?: return null
            val joystick = d.supportsSource(InputDevice.SOURCE_JOYSTICK) &&
                d.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_JOYSTICK) != null &&
                d.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_JOYSTICK) != null
            return ControllerTraits(
                id = d.id,
                isVirtual = d.isVirtual,
                hasSticks = joystick,
                hasGamepadButtons = d.supportsSource(InputDevice.SOURCE_GAMEPAD),
                vendorId = d.vendorId,
                productId = d.productId,
            )
        }

        /** The axis layout of a device, from its joystick motion ranges. */
        fun deviceAxes(deviceId: Int): GamepadAxes {
            val d = InputDevice.getDevice(deviceId) ?: return GamepadAxes.forAxes(emptySet())
            val axes = d.motionRanges.filter { it.isFromSource(InputDevice.SOURCE_JOYSTICK) }.map { it.axis }.toSet()
            // The old Sony driver reports the triggers on RX/RY and has a C button.
            val oldDs4 = d.vendorId == 0x054c && d.hasKeys(KeyEvent.KEYCODE_BUTTON_C)[0]
            return GamepadAxes.forAxes(axes, nonStandardDualShock4 = oldDs4)
        }

        /** A real controller is attached (for hiding the on-screen controls). */
        fun physicalControllerPresent(): Boolean = InputDevice.getDeviceIds().any { id ->
            deviceTraits(id)?.let { it.isController && it.hasSticks } == true
        }
        val MENU_COMBO = ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG
        val SUPPORTED = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or
            ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or
            ControllerPacket.LS_CLK_FLAG or ControllerPacket.RS_CLK_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG
    }
}
