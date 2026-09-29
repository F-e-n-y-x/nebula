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
 * Where the on-screen pad's whole state goes when something else owns the host controller slots
 * (the gamepad mapper). Each call returns false when that owner isn't available right now.
 */
interface OnScreenPad {
    fun state(buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int): Boolean
    /** Lets go of what the on-screen pad held, without announcing a pad for it. */
    fun released(): Boolean
}

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
    /**
     * The host-slot owner (GamepadMapper) for the on-screen pad's state; null sends player 1
     * directly to [out] with its own arrival.
     */
    private val pad: () -> OnScreenPad? = { null },
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
                when (e.mode) {
                    PressMode.TOGGLE, PressMode.MIXED -> if (e.id in _latched.value) {
                        // A latched toggle (or a tapped Mixed button) lets go on the next press.
                        _latched.value -= e.id
                        hold(e.id, emptyList())
                        if (e.mode == PressMode.MIXED) unlatchedBy += e.id
                    } else {
                        if (e.mode == PressMode.TOGGLE) _latched.value += e.id
                        hold(e.id, binds)
                    }
                    PressMode.HOLD -> hold(e.id, binds)
                }
            }
            else -> Unit
        }
        trackAim(e)
    }

    /**
     * A finger let go of [e] after [heldMs]. Hold releases; Toggle stays latched; Mixed latches
     * after a quick tap (under [MIXED_TAP_MS]) and releases after a long press.
     */
    fun elementUp(e: ControlElement, heldMs: Long = Long.MAX_VALUE) {
        if (e.kind != ElementKind.BUTTON && e.kind != ElementKind.TRIGGER && e.kind != ElementKind.COMBO) return
        when (e.mode) {
            PressMode.TOGGLE -> Unit
            PressMode.MIXED -> when {
                unlatchedBy.remove(e.id) -> Unit // this press only let go of the latch
                heldMs < MIXED_TAP_MS -> _latched.value += e.id
                else -> hold(e.id, emptyList())
            }
            PressMode.HOLD -> hold(e.id, emptyList())
        }
        trackAim(e)
    }

    /** Presses of Mixed buttons that only released their latch (they must not latch again on lift). */
    private val unlatchedBy = HashSet<String>()

    /** Fire / ADS elements holding right now (the gyro's "while aiming or firing"). */
    private val aimHolders = HashSet<String>()

    /** True while an on-screen fire or aim element is held or latched. */
    val aiming: Boolean get() = aimHolders.isNotEmpty()

    private fun trackAim(e: ControlElement) {
        if (!e.role.aims) return
        if (heldBy.containsKey(e.id) || e.id in _latched.value) aimHolders += e.id else aimHolders -= e.id
        OnScreenAim.active = aimHolders.isNotEmpty()
    }

    /**
     * Holds exactly [binds] for an extra [owner] that isn't an element's own press: a move
     * stick's auto-sprint and run lock. Reference-counted with everything else.
     */
    fun holdExtra(owner: String, binds: List<Binding>) = hold(owner, binds.filter { it != Binding.None })

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
        // Mix into a pad the PC already has; with none yet (no physical pad has spoken, or the
        // phone wrongly counts one as attached) the zone drives the on-screen pad itself, so it is
        // never dropped.
        val m = mixer()
        if (m != null && m.hasController) {
            // A drag that started on the on-screen pad lets go of it there.
            if (side == Side.LEFT && (lx != 0 || ly != 0)) { lx = 0; ly = 0; send() }
            if (side == Side.RIGHT && (rx != 0 || ry != 0)) { rx = 0; ry = 0; send() }
            m.touchStick(side, ax, ay)
            return
        }
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
        unlatchedBy.clear()
        aimHolders.clear()
        OnScreenAim.active = false
        val padWasUsed = announced
        minHold.values.forEach { it.cancel() }; minHold.clear()
        deferredRelease.values.forEach { it.cancel() }; deferredRelease.clear()
        resend?.cancel(); resend = null
        buttons = 0; lt = 0; rt = 0; lx = 0; ly = 0; rx = 0; ry = 0
        counts.clear()
        mixer()?.let { it.touchStick(Side.LEFT, 0, 0); it.touchStick(Side.RIGHT, 0, 0) }
        if (padWasUsed && pad()?.released() != true) send()
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

    /** Running while a digital press is younger than [MIN_HOLD_MS], per binding token. */
    private val minHold = HashMap<String, Job>()
    private val deferredRelease = HashMap<String, Job>()
    private var resend: Job? = null

    /**
     * Pad buttons and triggers are held at least [MIN_HOLD_MS]: moonlight-common-c merges queued
     * controller packets unless the button flags change (a trigger change never ends a batch), and
     * games read input once a frame, so a quick tap could vanish while sticks are streaming.
     */
    private fun padPress(token: String, down: Boolean, set: (Boolean) -> Unit) {
        if (down) {
            deferredRelease.remove(token)?.cancel()
            set(true); send()
            minHold[token]?.cancel()
            minHold[token] = scope.launch { delay(MIN_HOLD_MS) }
            return
        }
        val hold = minHold.remove(token)
        if (hold != null && hold.isActive) {
            deferredRelease[token] = scope.launch {
                hold.join()
                deferredRelease.remove(token)
                set(false); send()
            }
        } else {
            set(false); send()
        }
    }

    private fun apply(b: Binding, down: Boolean) {
        when (b) {
            Binding.None -> Unit
            is Binding.Pad -> padPress(b.token(), down) { on -> buttons = if (on) buttons or b.flag else buttons and b.flag.inv() }
            is Binding.Trigger -> padPress(b.token(), down) { on -> if (b.side == Side.LEFT) lt = if (on) 255 else 0 else rt = if (on) 255 else 0 }
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
        sendNow()
        // One full-state resend shortly after the last change: cheap insurance against a dropped packet.
        resend?.cancel()
        resend = scope.launch { delay(RESEND_MS); sendNow() }
    }

    private fun sendNow() {
        if (pad()?.state(buttons, lt, rt, lx, ly, rx, ry) == true) { announced = true; return }
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
        const val MIN_HOLD_MS = 50L
        /** Mixed mode: a press shorter than this is a tap and latches. */
        const val MIXED_TAP_MS = 250L
        const val RESEND_MS = 100L
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

/**
 * Whether an on-screen fire or aim-down-sights element is held right now, for the gyro's
 * "While aiming or firing" option (the gyro reads it on the main thread with every sample).
 */
object OnScreenAim {
    @Volatile var active: Boolean = false
}
