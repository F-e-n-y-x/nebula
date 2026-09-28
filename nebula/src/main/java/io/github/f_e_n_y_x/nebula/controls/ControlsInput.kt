package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Turns on-screen element touches into PC input: the on-screen gamepad (player 1, sent whole on
 * every change), keys and mouse buttons. Bindings are reference-counted, so two elements bound
 * to the same key (or a combo sharing a key with a button) release it only when both let go.
 *
 * Toggle elements latch on the first press and release on the next; [latched] exposes which are
 * on so the renderer can show it. Macros play on their own coroutine in [scope].
 */
class ControlsInput(
    private val out: () -> RemoteInput?,
    private val scope: CoroutineScope,
    /** LI_CCAP gyro/accel bits when this device's sensors act as the pad's motion source. */
    private val motionCaps: () -> Int = { 0 },
    /**
     * Set while a physical controller owns player 1 and only "keep with controller" zones show:
     * zone sticks then steer that controller's sticks instead of a separate on-screen pad.
     */
    private val mixer: () -> PadMixer? = { null },
) {
    private var buttons = 0
    private var lt = 0
    private var rt = 0
    private var lx = 0
    private var ly = 0
    private var rx = 0
    private var ry = 0
    private var announced = false
    private val counts = HashMap<String, Int>()
    private val heldBy = HashMap<String, List<Binding>>()
    private val keyDirs = HashMap<String, Set<Int>>()
    private val macros = HashMap<String, Job>()
    private val wheels = HashMap<String, Job>()
    private val _latched = MutableStateFlow<Set<String>>(emptySet())
    val latched: StateFlow<Set<String>> = _latched.asStateFlow()

    // ---- elements ----

    /** A finger went down on [e] (button, trigger, combo, macro). */
    fun elementDown(e: ControlElement) {
        when (e.kind) {
            ElementKind.MACRO -> playMacro(e)
            ElementKind.BUTTON, ElementKind.TRIGGER, ElementKind.COMBO -> {
                val binds = e.bindings.filter { it != Binding.None }
                if (e.mode == PressMode.TOGGLE) {
                    if (e.id in _latched.value) {
                        _latched.value -= e.id
                        hold(e.id, emptyList())
                    } else {
                        _latched.value += e.id
                        hold(e.id, binds)
                    }
                } else {
                    hold(e.id, binds)
                }
            }
            else -> Unit
        }
    }

    fun elementUp(e: ControlElement) {
        if (e.mode == PressMode.TOGGLE && e.kind != ElementKind.MACRO) return
        if (e.kind == ElementKind.BUTTON || e.kind == ElementKind.TRIGGER || e.kind == ElementKind.COMBO) hold(e.id, emptyList())
    }

    /** D-pad: [dirs] holds the pressed directions as indices into up, down, left, right. */
    fun dpad(e: ControlElement, dirs: Set<Int>) {
        hold(e.id, dirs.sorted().mapNotNull { e.bindings.getOrNull(it) }.filter { it != Binding.None })
    }

    /** Stick at ([nx], [ny]) in −1..1, y up. */
    fun stick(e: ControlElement, nx: Float, ny: Float) {
        when (e.stick) {
            StickOutput.LEFT, StickOutput.RIGHT -> {
                val (x, y) = applyDeadzone(nx, ny, e.deadzone)
                setStick(if (e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT, x, y)
            }
            StickOutput.KEYS -> {
                val dz = e.deadzone.coerceIn(0.05f, 0.9f)
                val dirs = buildSet {
                    if (ny > dz) add(0)
                    if (ny < -dz) add(1)
                    if (nx < -dz) add(2)
                    if (nx > dz) add(3)
                }
                if (keyDirs[e.id] != dirs) {
                    keyDirs[e.id] = dirs
                    hold(e.id, dirs.sorted().mapNotNull { e.bindings.getOrNull(it) }.filter { it != Binding.None })
                }
            }
        }
    }

    /** Sets a gamepad stick directly (−1..1, y up): camera zones, which do their own shaping. */
    fun setStick(side: Side, x: Float, y: Float) {
        val ax = (x.coerceIn(-1f, 1f) * AXIS_MAX).roundToInt()
        val ay = (y.coerceIn(-1f, 1f) * AXIS_MAX).roundToInt()
        val m = mixer()
        if (m != null) { m.touchStick(side, ax, ay); return }
        if (side == Side.LEFT) { if (lx == ax && ly == ay) return; lx = ax; ly = ay } else { if (rx == ax && ry == ay) return; rx = ax; ry = ay }
        send()
    }

    /** Relative mouse movement in PC pixels (camera → mouse zones). */
    fun mouseMove(dx: Int, dy: Int) { if (dx != 0 || dy != 0) out()?.move(dx, dy) }

    /** Stick or touchpad click (L3 / R3, tap to click). */
    fun click(e: ControlElement) {
        if (e.click == Binding.None) return
        press(e.click)
        release(e.click)
    }

    /** Touchpad movement in screen pixels. */
    fun touchpadMove(e: ControlElement, dx: Float, dy: Float) {
        val s = e.sensitivity
        val mx = (dx * s).roundToInt()
        val my = (dy * s).roundToInt()
        if (mx != 0 || my != 0) out()?.move(mx, my)
    }

    /** Lets go of everything (menu opened, editor opened, controls hidden). */
    fun releaseAll() {
        macros.values.forEach { it.cancel() }; macros.clear()
        wheels.values.forEach { it.cancel() }; wheels.clear()
        heldBy.keys.toList().forEach { hold(it, emptyList()) }
        keyDirs.clear()
        _latched.value = emptySet()
        val padWasUsed = announced
        buttons = 0; lt = 0; rt = 0; lx = 0; ly = 0; rx = 0; ry = 0
        counts.clear()
        mixer()?.let { it.touchStick(Side.LEFT, 0, 0); it.touchStick(Side.RIGHT, 0, 0) }
        if (padWasUsed) send()
    }

    // ---- bindings ----

    /** Makes [owner] hold exactly [binds]: presses what's new, releases what it no longer holds. */
    private fun hold(owner: String, binds: List<Binding>) {
        val before = heldBy[owner].orEmpty()
        val gone = before.filter { it !in binds }
        val added = binds.filter { it !in before }
        if (binds.isEmpty()) heldBy.remove(owner) else heldBy[owner] = binds
        // Let go in reverse order: Ctrl+C releases C, then Ctrl.
        gone.asReversed().forEach { release(it); if (it is Binding.Wheel) wheels.remove(owner + it.token())?.cancel() }
        added.forEach { b ->
            press(b)
            if (b is Binding.Wheel) wheels[owner + b.token()] = scope.launch { while (true) { delay(WHEEL_REPEAT_MS); scrollOnce(b) } }
        }
    }

    internal fun press(b: Binding) {
        val key = b.token()
        val n = (counts[key] ?: 0) + 1
        counts[key] = n
        if (n == 1) apply(b, true)
    }

    internal fun release(b: Binding) {
        val key = b.token()
        val n = (counts[key] ?: return) - 1
        if (n <= 0) { counts.remove(key); apply(b, false) } else counts[key] = n
    }

    private fun apply(b: Binding, down: Boolean) {
        when (b) {
            Binding.None -> Unit
            is Binding.Pad -> { buttons = if (down) buttons or b.flag else buttons and b.flag.inv(); send() }
            is Binding.Trigger -> { if (b.side == Side.LEFT) lt = if (down) 255 else 0 else rt = if (down) 255 else 0; send() }
            is Binding.Key -> out()?.virtualKey(b.vk, down, modifiersFor())
            is Binding.Mouse -> out()?.button(mouseOf(b.button), down)
            is Binding.Wheel -> if (down) scrollOnce(b)
        }
    }

    private fun scrollOnce(b: Binding.Wheel) { out()?.scroll(if (b.up) 120 else -120) }

    /** Modifier bits for keys held right now, so Ctrl + click and Shift + key combos work. */
    private fun modifiersFor(): Int {
        var m = 0
        for ((token, _) in counts) {
            val vk = (Binding.parse(token) as? Binding.Key)?.vk ?: continue
            m = m or when (vk) {
                0x10, 0xA0, 0xA1 -> 0x01
                0x11, 0xA2, 0xA3 -> 0x02
                0x12, 0xA4, 0xA5 -> 0x04
                0x5B, 0x5C -> 0x08
                else -> 0
            }
        }
        return m
    }

    private fun playMacro(e: ControlElement) {
        if (macros[e.id]?.isActive == true) return
        macros[e.id] = scope.launch {
            try {
                for (step in e.steps) {
                    if (step.binding == Binding.None) { delay(step.holdMs.toLong() + step.gapMs); continue }
                    press(step.binding)
                    try { delay(step.holdMs.toLong()) } finally { release(step.binding) }
                    delay(step.gapMs.toLong())
                }
            } finally {
                macros.remove(e.id)
            }
        }
    }

    private fun send() {
        val o = out() ?: return
        if (!announced) {
            // With this device's sensors as motion source the host needs a motion-capable pad (it
            // picks a DualSense for an unknown type with gyro/accel); otherwise an Xbox pad.
            // MoonBridge.LI_CTYPE_UNKNOWN / LI_CTYPE_XBOX, LI_CCAP_ANALOG_TRIGGERS.
            val motion = motionCaps()
            o.gamepadArrived(0, 1, if (motion != 0) 0x00 else 0x01, SUPPORTED, (0x01 or motion).toShort())
            announced = true
        }
        o.gamepad(0, 1, buttons, lt, rt, lx, ly, rx, ry)
    }

    companion object {
        const val AXIS_MAX = 32766
        const val WHEEL_REPEAT_MS = 120L
        /** The standard Xbox set, as GamepadMapper announces for physical pads. */
        val SUPPORTED = listOf(
            PadFlags.A, PadFlags.B, PadFlags.X, PadFlags.Y, PadFlags.UP, PadFlags.DOWN, PadFlags.LEFT, PadFlags.RIGHT,
            PadFlags.LB, PadFlags.RB, PadFlags.START, PadFlags.BACK, PadFlags.LS_CLK, PadFlags.RS_CLK, PadFlags.GUIDE,
        ).fold(0) { m, f -> m or f }

        fun mouseOf(k: MouseKey) = when (k) {
            MouseKey.LEFT -> MouseButton.LEFT
            MouseKey.MIDDLE -> MouseButton.MIDDLE
            MouseKey.RIGHT -> MouseButton.RIGHT
            MouseKey.BACK -> MouseButton.BACK
            MouseKey.FORWARD -> MouseButton.FORWARD
        }

        /** Radial deadzone, rescaled so movement starts from zero at its edge. */
        fun applyDeadzone(x: Float, y: Float, dz: Float): Pair<Float, Float> {
            val m = kotlin.math.hypot(x, y)
            if (m <= dz || m == 0f) return 0f to 0f
            val clamped = m.coerceAtMost(1f)
            val scaled = ((clamped - dz) / (1f - dz)).coerceIn(0f, 1f)
            return x / m * scaled to y / m * scaled
        }
    }
}
