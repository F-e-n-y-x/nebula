package io.github.f_e_n_y_x.nebula.input

import android.util.Log
import android.view.KeyEvent
import io.github.fenyx.nebula.engine.InputBridge
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the stream screen can send to the PC. Implemented by the engine and by demo mode. */
interface RemoteInput {
    fun key(event: KeyEvent): Boolean
    /** A Windows virtual-key code; [modifiers] uses the [Modifier] bits. */
    fun virtualKey(vk: Int, down: Boolean, modifiers: Int = 0)
    fun text(text: String)
    fun move(dx: Int, dy: Int)
    /** Absolute pointer at ([x], [y]) inside a [refW] × [refH] frame. */
    fun position(x: Int, y: Int, refW: Int, refH: Int)
    fun button(button: MouseButton, down: Boolean)
    /** High-resolution scroll; 120 = one wheel notch, positive = up / right. */
    fun scroll(amount: Int)
    fun scrollHorizontal(amount: Int)
    /** Native touch in normalized coordinates; false when the host doesn't support it. */
    fun touch(type: Byte, pointerId: Int, x: Float, y: Float): Boolean
    fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int)
    fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short)
}

/** Windows modifier bits, as the host expects them. */
object Modifier {
    const val SHIFT = 0x01
    const val CTRL = 0x02
    const val ALT = 0x04
    const val META = 0x08
}

/** Windows virtual-key codes used by the stream menu's shortcut keys. */
object Vk {
    const val BACKSPACE = 0x08
    const val TAB = 0x09
    const val ENTER = 0x0D
    const val SHIFT = 0x10
    const val CTRL = 0x11
    const val ALT = 0x12
    const val ESC = 0x1B
    const val PRINT_SCREEN = 0x2C
    const val DELETE = 0x2E
    const val D = 0x44
    const val LWIN = 0x5B
    const val F4 = 0x73
    const val F11 = 0x7A
}

/** A key chord from the stream menu, pressed in order and released in reverse. */
data class Shortcut(val label: String, val keys: List<Int>)

val menuShortcuts = listOf(
    Shortcut("Win", listOf(Vk.LWIN)),
    Shortcut("Esc", listOf(Vk.ESC)),
    Shortcut("Alt + Tab", listOf(Vk.ALT, Vk.TAB)),
    Shortcut("Alt + F4", listOf(Vk.ALT, Vk.F4)),
    Shortcut("Win + D", listOf(Vk.LWIN, Vk.D)),
    Shortcut("Ctrl + Alt + Del", listOf(Vk.CTRL, Vk.ALT, Vk.DELETE)),
    Shortcut("Ctrl + Shift + Esc", listOf(Vk.CTRL, Vk.SHIFT, Vk.ESC)),
    Shortcut("F11", listOf(Vk.F11)),
    Shortcut("Print Screen", listOf(Vk.PRINT_SCREEN)),
)

fun RemoteInput.press(shortcut: Shortcut) {
    var mods = 0
    shortcut.keys.forEach { vk ->
        virtualKey(vk, true, mods)
        mods = mods or modifierOf(vk)
    }
    shortcut.keys.asReversed().forEach { vk ->
        mods = mods and modifierOf(vk).inv()
        virtualKey(vk, false, mods)
    }
}

private fun modifierOf(vk: Int) = when (vk) {
    Vk.SHIFT -> Modifier.SHIFT
    Vk.CTRL -> Modifier.CTRL
    Vk.ALT -> Modifier.ALT
    Vk.LWIN -> Modifier.META
    else -> 0
}

fun RemoteInput.click(button: MouseButton = MouseButton.LEFT) {
    button(button, true)
    button(button, false)
}

/** Forwards to the live session's [InputBridge]; drops input while nothing is connected. */
class BridgeInput(private val bridge: () -> InputBridge?) : RemoteInput {
    override fun key(event: KeyEvent) = bridge()?.sendKey(event) == true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { bridge()?.sendVirtualKey(vk.toShort(), down, modifiers.toByte()) }
    override fun text(text: String) { bridge()?.sendText(text) }
    override fun move(dx: Int, dy: Int) { bridge()?.moveMouse(dx, dy) }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) { bridge()?.setMousePosition(x, y, refW, refH) }
    override fun button(button: MouseButton, down: Boolean) { bridge()?.mouseButton(button, down) }
    override fun scroll(amount: Int) { bridge()?.scroll(amount) }
    override fun scrollHorizontal(amount: Int) { bridge()?.scrollHorizontal(amount) }
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float): Boolean =
        bridge()?.touch(type, pointerId, x, y)?.let { it >= 0 } == true
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        bridge()?.gamepad(controller, activeMask, buttons, lt, rt, lx, ly, rx, ry)
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) {
        bridge()?.gamepadArrived(controller, activeMask, type, supportedButtons, capabilities)
    }
}

/**
 * Demo mode and QA: logs every event (tag "NebulaInput") so the gestures can be checked with adb
 * on an emulator without a PC.
 */
class LoggingInput : RemoteInput {
    private val _last = MutableStateFlow("")
    val last: StateFlow<String> = _last.asStateFlow()

    private fun log(s: String) {
        Log.i(TAG, s)
        _last.value = s
    }

    override fun key(event: KeyEvent): Boolean {
        log("key ${KeyEvent.keyCodeToString(event.keyCode)} ${if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"}"); return true
    }
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = log("vk 0x${vk.toString(16)} ${if (down) "down" else "up"} mods=$modifiers")
    override fun text(text: String) = log("text \"$text\"")
    /** Mouse and touch events that reached the PC, for device tests. */
    val pointerEvents = java.util.concurrent.atomic.AtomicInteger()

    override fun move(dx: Int, dy: Int) { pointerEvents.incrementAndGet(); log("move $dx $dy") }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) { pointerEvents.incrementAndGet(); log("position $x $y of ${refW}x$refH") }
    override fun button(button: MouseButton, down: Boolean) { pointerEvents.incrementAndGet(); log("button $button ${if (down) "down" else "up"}") }
    override fun scroll(amount: Int) = log("scroll $amount")
    override fun scrollHorizontal(amount: Int) = log("hscroll $amount")
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float): Boolean {
        pointerEvents.incrementAndGet()
        log("touch type=$type id=$pointerId ${"%.3f".format(x)} ${"%.3f".format(y)}"); return true
    }
    /** Last gamepad state sent, for device tests: controller, buttons, lt, rt, lx, ly, rx, ry. */
    @Volatile var lastPad: IntArray? = null
        private set

    /** Every controller number announced, in order, for device tests. */
    val arrivals: MutableList<Int> = java.util.Collections.synchronizedList(mutableListOf())

    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        lastPad = intArrayOf(controller, buttons, lt, rt, lx, ly, rx, ry)
        log("pad $controller mask=$activeMask buttons=0x${buttons.toString(16)} lt=$lt rt=$rt l=$lx,$ly r=$rx,$ry")
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) {
        arrivals += controller
        log("pad $controller arrived mask=$activeMask")
    }

    companion object {
        const val TAG = "NebulaInput"
    }
}

/**
 * Passes everything to [delegate] and keeps an estimate of where the PC's pointer is (normalized
 * to the video), for the optional local cursor. Relative moves are integrated in stream pixels,
 * so it drifts if the PC applies its own pointer acceleration.
 */
class TrackingInput(private val delegate: RemoteInput) : RemoteInput by delegate {
    private val _cursor = MutableStateFlow(0.5f to 0.5f)
    val cursor: StateFlow<Pair<Float, Float>> = _cursor.asStateFlow()
    @Volatile var frameWidth = 1920
    @Volatile var frameHeight = 1080

    override fun move(dx: Int, dy: Int) {
        val (x, y) = _cursor.value
        _cursor.value = (x + dx / frameWidth.toFloat()).coerceIn(0f, 1f) to (y + dy / frameHeight.toFloat()).coerceIn(0f, 1f)
        delegate.move(dx, dy)
    }

    override fun position(x: Int, y: Int, refW: Int, refH: Int) {
        _cursor.value = (x / refW.toFloat()).coerceIn(0f, 1f) to (y / refH.toFloat()).coerceIn(0f, 1f)
        delegate.position(x, y, refW, refH)
    }
}
