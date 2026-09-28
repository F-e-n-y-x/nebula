package io.github.f_e_n_y_x.nebula.data.engine

import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.input.ControllerLookup
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
 * The only thing that gives out host controller slots. Turns Android gamepad events, the on-screen
 * controls and gyro-to-right-stick into host controllers: each physical pad gets the next free
 * player slot (up to 16) unless multi-controller is off. Start + Select together calls [onMenu]
 * instead of reaching the game.
 *
 * Exactly one host controller per physical pad:
 * - only real controllers get a slot (never a virtual device), and only once an event reaches the host;
 * - a buttons-only companion node (a pad's separate key node) joins its pad, even when it speaks
 *   first, instead of becoming a phantom second controller;
 * - the on-screen controls and this device's gyro never add a player while a controller is attached:
 *   they drive player 1 together with it. With no controller attached they become player 1 (the
 *   "phone pad"), and the first real pad to speak takes that slot over.
 *
 * Axes are read through each device's [GamepadAxes] layout. Main thread only.
 */
class GamepadMapper(
    private val input: () -> RemoteInput?,
    private val onMenu: () -> Unit,
    private val config: () -> GamepadConfig = { GamepadConfig() },
    private val traitsOf: (deviceId: Int) -> ControllerTraits? = ::deviceTraits,
    private val axesOf: (deviceId: Int) -> GamepadAxes = ::deviceAxes,
    /**
     * LI_CCAP bits announced for a pad (motors, IMU, light bar) so the host builds a virtual
     * controller that can rumble and report motion. Arguments: Android device id ([PHONE_PAD] for
     * the phone pad), player index.
     */
    private val capabilities: (deviceId: Int, index: Int) -> Int = { _, _ -> DEFAULT_CAPS },
    /** A pad was bound to or released from a player slot. */
    private val onPadsChanged: () -> Unit = {},
    /** Device ids of the attached real controllers with sticks, lowest first. */
    private val attached: () -> List<Int> = ::attachedControllers,
    /** Sees every mapped gamepad key first; true keeps it from the host (the gyro toggle button). */
    private val keyHook: (keyCode: Int, down: Boolean) -> Boolean = { _, _ -> false },
    /** Diagnostics: one line per slot handed out or announced (logcat tag NebulaPads). */
    private val log: (String) -> Unit = {},
) : ControllerLookup {
    /** [deviceId] is the device that owns the slot (companion nodes share it; [PHONE_PAD] for the phone pad). */
    private class Pad(val index: Int, var deviceId: Int) {
        var buttons = 0
        var lt = 0
        var rt = 0
        var lx = 0
        var ly = 0
        var rx = 0
        var ry = 0
        var hat = 0
        // The on-screen controls' share of this pad.
        var oscButtons = 0
        var oscLt = 0
        var oscRt = 0
        var oscLx = 0
        var oscLy = 0
        var oscRx = 0
        var oscRy = 0
        // Gyro to right stick, added to the physical right stick.
        var gyroX = 0
        var gyroY = 0
        var announced = false
        /** Capabilities the host was told about. */
        var caps = 0
    }

    /** Pads by Android device id; a companion device shares its pad's entry. */
    private val pads = LinkedHashMap<Int, Pad>()
    private val traits = HashMap<Int, ControllerTraits?>()
    private val layouts = HashMap<Int, GamepadAxes>()

    /** Bitmask of every connected player slot, as the host expects. */
    private val activeMask: Int
        get() = pads.values.fold(0) { m, p -> m or (1 shl p.index) }

    /** How many host controllers are mapped right now. */
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
        if (keyHook(keyCode, down)) return true
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

    // ---- On-screen controls, gyro and the phone pad ----

    /** The on-screen controls' whole state; they drive player 1 (see the class notes). */
    fun onScreenState(buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        val pad = playerOne()
        pad.oscButtons = buttons; pad.oscLt = lt; pad.oscRt = rt
        pad.oscLx = lx; pad.oscLy = ly; pad.oscRx = rx; pad.oscRy = ry
        send(pad)
    }

    /** The on-screen controls went away: let go of what they held, without announcing a pad for it. */
    fun onScreenReleased() {
        pads.values.distinct().forEach { p ->
            val held = p.oscButtons != 0 || p.oscLt != 0 || p.oscRt != 0 || p.oscLx != 0 || p.oscLy != 0 || p.oscRx != 0 || p.oscRy != 0
            p.oscButtons = 0; p.oscLt = 0; p.oscRt = 0; p.oscLx = 0; p.oscLy = 0; p.oscRx = 0; p.oscRy = 0
            if (held && p.announced) send(p)
        }
    }

    /**
     * Gyro to right stick: deflection added to the right stick of [deviceId]'s pad, or of player 1
     * when [deviceId] is null (this device's gyro). Zero never announces a new pad.
     */
    fun gyroStick(deviceId: Int?, x: Int, y: Int) {
        val existing = if (deviceId == null) existingPlayerOne() else pads[deviceId]
        if (existing == null && x == 0 && y == 0) return
        val pad = existing ?: if (deviceId == null) playerOne() else padFor(deviceId)
        if (pad.gyroX == x && pad.gyroY == y && pad.announced) return
        pad.gyroX = x; pad.gyroY = y
        send(pad)
    }

    /**
     * Announces player 1 now (motion passthrough from this device with no controller attached: the
     * host must see a motion-capable pad before a game asks for motion). No-op once player 1 exists
     * or while a controller is attached (its own arrival carries the phone's motion bits).
     */
    fun announcePlayerOne() {
        if (attached().isNotEmpty() || existingPlayerOne() != null) return
        send(playerOne())
    }

    /** The phone pad exists (player 1 without a controller). */
    val hasPhonePad: Boolean get() = pads.containsKey(PHONE_PAD)

    /** Device id of the first attached controller, for the gyro source; null when none. */
    fun firstController(): Int? = attached().firstOrNull()

    /** A pad was unplugged: release its buttons on the host and free its slot. */
    fun onDeviceRemoved(deviceId: Int) {
        traits.remove(deviceId)
        layouts.remove(deviceId)
        val pad = pads.remove(deviceId) ?: return
        // A companion node going away doesn't unplug the pad it belongs to.
        if (pad in pads.values) {
            if (pad.deviceId == deviceId) pad.deviceId = pads.entries.first { it.value === pad }.key
            return
        }
        // The bit is cleared, so the host frees its virtual controller.
        if (pad.announced) input()?.gamepad(pad.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
        onPadsChanged()
    }

    /** A device's capabilities changed (e.g. a pad reconnected with another layout). */
    fun onDeviceChanged(deviceId: Int) {
        traits.remove(deviceId)
        layouts.remove(deviceId)
    }

    // ---- ControllerLookup (rumble, lights and motion find the physical pad here) ----

    override fun deviceIdFor(index: Int): Int? =
        pads.values.firstOrNull { it.index == index }?.deviceId?.takeIf { it != PHONE_PAD }

    override fun triggers(index: Int): Pair<Int, Int> =
        pads.values.firstOrNull { it.index == index }?.let { maxOf(it.lt, it.oscLt) to maxOf(it.rt, it.oscRt) } ?: (0 to 0)

    override fun indices(): Set<Int> = pads.values.map { it.index }.toSet()

    /** Releases everything, e.g. when the stream menu opens. */
    fun releaseAll() {
        val o = input() ?: return
        pads.values.distinct().forEach { p ->
            p.buttons = 0; p.hat = 0; p.lt = 0; p.rt = 0; p.lx = 0; p.ly = 0; p.rx = 0; p.ry = 0
            p.oscButtons = 0; p.oscLt = 0; p.oscRt = 0; p.oscLx = 0; p.oscLy = 0; p.oscRx = 0; p.oscRy = 0
            p.gyroX = 0; p.gyroY = 0
            if (p.announced) o.gamepad(p.index, activeMask, 0, 0, 0, 0, 0, 0, 0)
        }
    }

    private fun traitsFor(deviceId: Int): ControllerTraits? = traits.getOrPut(deviceId) { traitsOf(deviceId) }

    /** Player 1's pad if it exists already: the phone pad, or the lowest slot of an attached controller. */
    private fun existingPlayerOne(): Pad? {
        pads[PHONE_PAD]?.let { return it }
        val att = attached()
        // Without a stick controller, whatever already holds a slot (a buttons-only node) is player 1.
        if (att.isEmpty()) return pads.values.minByOrNull { it.index }
        return pads.entries.filter { it.key in att }.minByOrNull { it.value.index }?.value
    }

    /** Player 1, created if needed: an attached controller's pad, else the phone pad. */
    private fun playerOne(): Pad {
        existingPlayerOne()?.let { return it }
        val first = attached().firstOrNull()
        if (first != null) return padFor(first)
        return Pad(freeIndex(), PHONE_PAD).also { pads[PHONE_PAD] = it; onPadsChanged() }
    }

    private fun freeIndex(): Int {
        if (!config().multiController) return 0
        val used = pads.values.map { it.index }.toSet()
        return (0 until MAX_PADS).firstOrNull { it !in used } ?: 0
    }

    private fun padFor(deviceId: Int): Pad {
        pads[deviceId]?.let { return it }
        val me = traitsFor(deviceId)
        // A buttons-only node joins a real pad (the same model first) rather than adding a player,
        // even before that pad has sent anything.
        if (me?.isCompanion == true) {
            val sameModel = { id: Int -> traitsFor(id)?.let { t -> t.hasSticks && t.vendorId == me.vendorId && t.productId == me.productId } == true }
            val owner = pads.keys.firstOrNull(sameModel)
                ?: attached().firstOrNull { it != deviceId && sameModel(it) }
                ?: pads.keys.firstOrNull { traitsFor(it)?.hasSticks == true }
                ?: attached().firstOrNull { it != deviceId }
            if (owner != null) return padFor(owner).also { pads[deviceId] = it }
            // No stick controller: never a second player next to the phone pad (on-screen controls
            // or gyro); join player 1 instead.
            existingPlayerOne()?.let { p -> pads[deviceId] = p; return p }
        }
        // The first real pad takes over the phone pad's slot (same player, now the controller).
        val phone = pads.remove(PHONE_PAD)
        val pad = if (phone != null) {
            phone.deviceId = deviceId
            phone
        } else {
            Pad(if (config().multiController) freeIndex() else 0, deviceId)
        }
        pads[deviceId] = pad
        if (phone != null && phone.announced && capabilities(deviceId, pad.index) != phone.caps) {
            // Re-announce so the host builds the virtual controller this pad needs (rumble, motion).
            input()?.gamepad(pad.index, activeMask and (1 shl pad.index).inv(), 0, 0, 0, 0, 0, 0, 0)
            pad.announced = false
        }
        if (!pad.announced) onPadsChanged()
        return pad
    }

    private fun send(pad: Pad) {
        val bridge = input() ?: return
        if (!pad.announced) {
            pad.caps = capabilities(pad.deviceId, pad.index)
            log("announce player ${pad.index + 1} for ${if (pad.deviceId == PHONE_PAD) "the phone pad (on-screen controls / gyro)" else "device ${pad.deviceId} ${traitsFor(pad.deviceId)}"}, caps 0x${pad.caps.toString(16)}, attached ${attached()}")
            bridge.gamepadArrived(pad.index, activeMask, MoonBridge.LI_CTYPE_UNKNOWN, SUPPORTED, pad.caps.toShort())
            pad.announced = true
        }
        bridge.gamepad(
            pad.index, activeMask,
            pad.buttons or pad.hat or pad.oscButtons,
            maxOf(pad.lt, pad.oscLt), maxOf(pad.rt, pad.oscRt),
            stick(pad.lx + pad.oscLx), stick(pad.ly + pad.oscLy),
            stick(pad.rx + pad.oscRx + pad.gyroX), stick(pad.ry + pad.oscRy + pad.gyroY),
        )
    }

    private fun stick(v: Int) = v.coerceIn(-32768, 32767)

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

        /** Slot owner id of the phone pad (player 1 with no controller attached); never a real device id. */
        const val PHONE_PAD = Int.MIN_VALUE + 1

        /** Real controllers with sticks that are attached now, lowest device id first. */
        fun attachedControllers(): List<Int> = InputDevice.getDeviceIds().filter { id ->
            deviceTraits(id)?.let { it.isController && it.hasSticks } == true
        }.sorted()

        /** A real controller is attached (for hiding the on-screen controls). */
        fun physicalControllerPresent(): Boolean = attachedControllers().isNotEmpty()
        val DEFAULT_CAPS = MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()
        val MENU_COMBO = ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG
        val SUPPORTED = ControllerPacket.A_FLAG or ControllerPacket.B_FLAG or ControllerPacket.X_FLAG or ControllerPacket.Y_FLAG or
            ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG or
            ControllerPacket.LB_FLAG or ControllerPacket.RB_FLAG or ControllerPacket.PLAY_FLAG or ControllerPacket.BACK_FLAG or
            ControllerPacket.LS_CLK_FLAG or ControllerPacket.RS_CLK_FLAG or ControllerPacket.SPECIAL_BUTTON_FLAG
    }
}
