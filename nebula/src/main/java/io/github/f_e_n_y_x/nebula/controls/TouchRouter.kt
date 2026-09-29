package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.input.Finger
import kotlin.math.hypot
import kotlin.math.min

/** An axis-aligned rectangle in window pixels. */
data class RRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val cx: Float get() = (left + right) / 2f
    val cy: Float get() = (top + bottom) / 2f
    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom
}

/**
 * Where each element sits on screen: the same maths the overlay draws with ([place]), so drawing
 * and hit-testing never disagree. [area] is the controls area in window px.
 */
data class RouterLayout(
    val elements: List<Pair<ControlElement, RRect>>,
    val area: RRect,
    val density: Float,
    val background: OutsideTouch = OutsideTouch.LOOK,
    val look: LookOutput = LookOutput.STICK,
) {
    companion object {
        val EMPTY = RouterLayout(emptyList(), RRect(0f, 0f, 0f, 0f), 1f)

        /** Pixel rectangle of [e] in an [area] (zones are a share of it; everything else dp). */
        fun place(e: ControlElement, area: RRect, density: Float): RRect {
            val w = if (e.areaSized) e.width * area.width else Math.round(e.width * density).toFloat()
            val h = if (e.areaSized) e.height * area.height else Math.round(e.height * density).toFloat()
            val l = area.left + Math.round(e.x * area.width - w / 2f)
            val t = area.top + Math.round(e.y * area.height - h / 2f)
            return RRect(l, t, l + w, t + h)
        }

        fun of(elements: List<ControlElement>, area: RRect, density: Float, background: OutsideTouch, look: LookOutput) =
            RouterLayout(elements.map { it to place(it, area, density) }, area, density, background, look)
    }
}

/** What the overlay draws for one element (knob as a share of the radius, y down; origin in element px). */
data class Visual(
    val pressed: Boolean = false,
    val knobX: Float = 0f,
    val knobY: Float = 0f,
    val originX: Float = Float.NaN,
    val originY: Float = Float.NaN,
    val dirs: Set<Int> = emptySet(),
    /** Move stick: pushed past the ring, auto-sprint held. */
    val sprint: Boolean = false,
    /** Move stick: the thumb is on the run-lock mark; letting go locks. */
    val lockArmed: Boolean = false,
    /** Move stick: run lock on (running forward with no finger). */
    val locked: Boolean = false,
)

/** The stream's trackpad/touch layer, fed only the fingers the router gives it (see [TouchRouter]). */
interface BackgroundTouches {
    fun down(f: Finger, t: Long)
    fun pointerDown(f: Finger, all: List<Finger>, t: Long)
    fun move(all: List<Finger>, t: Long)
    fun pointerUp(f: Finger, remaining: List<Finger>, t: Long)
    fun up(f: Finger, t: Long)
    fun cancel()
}

/**
 * Owns every finger on the stream while the on-screen controls are shown, by Android pointer id,
 * for the finger's whole life. On down it picks, topmost first: a button, trigger, D-pad, stick
 * or touchpad; then a zone; then the profile's background mode ([OutsideTouch]). A finger keeps
 * its owner when it slides off.
 *
 * Only background fingers in Trackpad / Touch mode reach [background], so the stick and buttons
 * never count as trackpad fingers (the old full-screen layer saw every pointer of the raw
 * MotionEvent: scrolls, zooms, clicks and the keyboard gesture from the fingers on the pad).
 *
 * Anti-stuck: [cancel] releases everything; a [move] that no longer lists an owned finger releases
 * it. Camera sticks and look-through fingers are driven by [tick] (call every ~16 ms while
 * [ticking]). Pure Kotlin; the stream's input view feeds it and the overlay only draws.
 */
class TouchRouter(
    private val input: ControlsInput,
    private val background: () -> BackgroundTouches? = { null },
    private val onVisual: (id: String, v: Visual) -> Unit = { _, _ -> },
) {
    var layout: RouterLayout = RouterLayout.EMPTY

    private sealed class Owner {
        abstract val id: String?
    }

    private class Press(val e: ControlElement, val downX: Float, val downY: Float, val downT: Long) : Owner() {
        override val id get() = e.id
        var look: Look? = null
    }

    /** Auto-sprint / run-lock state of the finger on a move stick. */
    private class MoveState {
        var sprinting = false
        var armed = false
    }

    private class Dpad(val e: ControlElement, val rect: RRect) : Owner() {
        override val id get() = e.id
        var dirs: Set<Int> = emptySet()
    }

    private class Stick(val e: ControlElement, val rect: RRect, val ox: Float, val oy: Float, val radius: Float, val start: Long) : Owner() {
        override val id get() = e.id
        var travelled = 0f
        val move = MoveState()
    }

    private class Pad(val e: ControlElement, val start: Long) : Owner() {
        override val id get() = e.id
        var travelled = 0f
    }

    private class FloatStick(val e: ControlElement, val rect: RRect, val ox: Float, val oy: Float, val radius: Float) : Owner() {
        override val id get() = e.id
        val move = MoveState()
    }

    /** A run-locked move stick: its element, and where to draw it (element px). */
    private class Lock(val e: ControlElement, val originX: Float, val originY: Float)
    private val locks = LinkedHashMap<String, Lock>()

    /** Element ids whose run lock is on (tests and the overlay). */
    val runLocked: Set<String> get() = locks.keys

    /** A finger that looks: a camera zone, the Look background, or a fire-and-look button. */
    private class Look(val zoneId: String?, val side: Side, val stick: CameraStick?, val mouse: CameraMouse?) : Owner() {
        override val id get() = zoneId
    }

    private object Background : Owner() { override val id: String? = null }
    private object Ignored : Owner() { override val id: String? = null }

    private val owners = LinkedHashMap<Int, Owner>()
    private val last = HashMap<Int, Pair<Float, Float>>()
    private val lastT = HashMap<Int, Long>()
    private val pressCount = HashMap<String, Int>()
    private val bgFingers = LinkedHashMap<Int, Finger>()

    /** A camera stick or look-through finger needs [tick]. */
    val ticking: Boolean get() = owners.values.any { it is Look && it.stick != null || it is Press && it.look?.stick != null } || decaying.isNotEmpty()
    private val decaying = HashMap<Side, CameraStick>()

    /** How many fingers the router owns (tests and diagnostics). */
    val fingerCount: Int get() = owners.size

    // ---- events ----

    fun down(id: Int, x: Float, y: Float, t: Long) {
        owners.remove(id)?.let { release(id, it, t, cancelled = true) }
        last[id] = x to y
        lastT[id] = t
        val owner = claim(x, y, t)
        // Touching a run-locked stick again stops the run (PUBG); the finger then steers as usual.
        when (owner) {
            is Stick -> unlock(owner.e.id)
            is FloatStick -> unlock(owner.e.id)
            else -> Unit
        }
        owners[id] = owner
        when (owner) {
            is Press -> {
                val n = (pressCount[owner.e.id] ?: 0) + 1
                pressCount[owner.e.id] = n
                if (n == 1) { input.elementDown(owner.e); visual(owner.e.id, Visual(pressed = true)) }
            }
            is Dpad -> dpadAt(owner, x, y)
            is Stick -> stickAt(owner, x, y)
            is Pad -> visual(owner.e.id, Visual(pressed = true))
            is FloatStick -> { visual(owner.e.id, Visual(pressed = true, originX = owner.ox - owner.rect.left, originY = owner.oy - owner.rect.top)) }
            is Look -> owner.zoneId?.let { visual(it, Visual(pressed = true)) }
            Background -> bgDown(id, x, y, t)
            Ignored -> Unit
        }
    }

    /** Every finger in the event, with its current position. Owned fingers missing from it are released. */
    fun move(fingers: List<Finger>, t: Long) {
        val present = fingers.map { it.id }.toSet()
        owners.keys.filter { it !in present }.forEach { id -> owners.remove(id)?.let { release(id, it, t, cancelled = true) } }
        var bgMoved = false
        for (f in fingers) {
            val o = owners[f.id] ?: continue
            val (px, py) = last[f.id] ?: (f.x to f.y)
            val dx = f.x - px
            val dy = f.y - py
            val dt = (t - (lastT[f.id] ?: t)).coerceAtLeast(1L)
            last[f.id] = f.x to f.y
            lastT[f.id] = t
            when (o) {
                is Press -> pressMove(o, f, dx, dy, dt, t)
                is Dpad -> dpadAt(o, f.x, f.y)
                is Stick -> { o.travelled += hypot(dx, dy); stickAt(o, f.x, f.y) }
                is Pad -> { o.travelled += hypot(dx, dy); input.touchpadMove(o.e, dx, dy) }
                is FloatStick -> floatAt(o, f.x, f.y)
                is Look -> lookMove(o, dx, dy, dt, t)
                Background -> { bgFingers[f.id] = f; bgMoved = true }
                Ignored -> Unit
            }
        }
        if (bgMoved) background()?.move(bgFingers.values.toList(), t)
    }

    fun up(id: Int, t: Long) {
        val o = owners.remove(id) ?: return
        release(id, o, t, cancelled = false)
    }

    /** CANCEL, FLAG_CANCELED, focus loss: every finger lets go, nothing clicks. */
    fun cancel(t: Long = 0L) {
        val all = owners.toList()
        owners.clear()
        all.forEach { (id, o) -> release(id, o, t, cancelled = true) }
        pressCount.clear()
        if (bgFingers.isNotEmpty()) { bgFingers.clear(); background()?.cancel() }
        decaying.values.forEach { it.release() }
        decaying.clear()
        locks.keys.toList().forEach { unlock(it) }
        // Nothing may wait out a minimum hold after a cancel.
        input.releaseAll()
    }

    /** One frame: camera sticks turn finger speed into a push, and ease out after release. */
    fun tick(t: Long) {
        val pushes = HashMap<Side, Pair<Float, Float>>()
        for (o in owners.values) {
            val look = when (o) { is Look -> o; is Press -> o.look; else -> null } ?: continue
            val cam = look.stick ?: continue
            pushes[look.side] = cam.tick(t)
        }
        for ((side, cam) in decaying.entries.toList()) {
            if (side in pushes) { decaying.remove(side); continue }
            val p = cam.tick(t)
            pushes[side] = p
            if (p.first == 0f && p.second == 0f) decaying.remove(side)
        }
        pushes.forEach { (side, p) -> input.setStick(side, p.first, p.second) }
    }

    // ---- ownership ----

    private fun claim(x: Float, y: Float, t: Long): Owner {
        val l = layout
        // Topmost first: the overlay draws zones under everything else, in list order.
        val controls = l.elements.filter { it.first.kind != ElementKind.ZONE }.asReversed()
        for ((e, r) in controls) {
            if (!r.contains(x, y)) continue
            return when (e.kind) {
                ElementKind.BUTTON, ElementKind.TRIGGER, ElementKind.COMBO, ElementKind.MACRO -> Press(e, x, y, t)
                ElementKind.DPAD -> Dpad(e, r)
                ElementKind.STICK -> {
                    val full = min(r.width, r.height) / 2f
                    if (e.floating) Stick(e, r, x, y, min(full, FLOAT_STICK_DP * l.density), t)
                    else Stick(e, r, r.cx, r.cy, full, t)
                }
                ElementKind.TOUCHPAD -> Pad(e, t)
                ElementKind.ZONE -> Ignored
            }
        }
        for ((e, r) in l.elements.filter { it.first.kind == ElementKind.ZONE }.asReversed()) {
            if (!r.contains(x, y)) continue
            val side = if (e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT
            return when (e.zone) {
                ZoneType.FLOATING_STICK -> FloatStick(e, r, x, y, FLOAT_RING_DP * l.density)
                else -> zoneLook(e, e.id)
            }
        }
        return when (l.background) {
            OutsideTouch.LOOK -> if (x >= l.area.left + l.area.width * LOOK_SPLIT) newLook() else Ignored
            OutsideTouch.OFF -> Ignored
            OutsideTouch.TRACKPAD, OutsideTouch.TOUCH -> Background
        }
    }

    private fun newLook(zoneId: String? = null): Look = when (layout.look) {
        LookOutput.STICK -> Look(zoneId, Side.RIGHT, CameraStick(), null)
        LookOutput.MOUSE -> Look(zoneId, Side.RIGHT, null, CameraMouse())
    }

    private fun zoneLook(e: ControlElement, zoneId: String?): Look {
        val side = if (e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT
        return if (e.zone == ZoneType.CAMERA_MOUSE) Look(zoneId, side, null, CameraMouse.forZone(e))
        else Look(zoneId, side, CameraStick(e.sensitivity, e.acceleration, e.antiDeadzone, e.invertY), null)
    }

    /**
     * The look a fire-and-look finger uses: the layout's right-hand camera zone when it has one
     * (so aiming with the fire button turns exactly as fast as the look area), else the Look
     * background's.
     */
    private fun aimLook(): Look {
        val zone = layout.elements.firstOrNull { (e, _) ->
            e.kind == ElementKind.ZONE && e.zone != ZoneType.FLOATING_STICK && e.stick != StickOutput.LEFT
        }?.first
        return if (zone != null) zoneLook(zone, null) else newLook()
    }

    private fun release(id: Int, o: Owner, t: Long, cancelled: Boolean) {
        val pos = last.remove(id)
        lastT.remove(id)
        when (o) {
            is Press -> {
                o.look?.let { endLook(it) }
                val n = (pressCount[o.e.id] ?: 1) - 1
                if (n <= 0) { pressCount.remove(o.e.id); input.elementUp(o.e, if (cancelled) Long.MAX_VALUE else t - o.downT); visual(o.e.id, Visual()) } else pressCount[o.e.id] = n
            }
            is Dpad -> { if (o.dirs.isNotEmpty()) input.dpad(o.e, emptySet()); visual(o.e.id, Visual()) }
            is Stick -> {
                if (!cancelled && o.move.armed) {
                    lock(o.e, o.ox - o.rect.left, o.oy - o.rect.top)
                } else {
                    input.stick(o.e, 0f, 0f)
                    endMove(o.e, o.move)
                    visual(o.e.id, Visual())
                    if (!cancelled && t - o.start < TAP_MS && o.travelled < o.radius * 0.25f) input.click(o.e)
                }
            }
            is Pad -> {
                visual(o.e.id, Visual())
                if (!cancelled && t - o.start < TAP_MS && o.travelled < TAP_SLOP_DP * layout.density) input.click(o.e)
            }
            is FloatStick -> {
                if (!cancelled && o.move.armed) {
                    lock(o.e, o.ox - o.rect.left, o.oy - o.rect.top)
                } else {
                    pushForward(o.e, 0f)
                    endMove(o.e, o.move)
                    visual(o.e.id, Visual())
                }
            }
            is Look -> { endLook(o); o.zoneId?.let { visual(it, Visual()) } }
            Background -> {
                bgFingers.remove(id)
                val f = Finger(id, pos?.first ?: 0f, pos?.second ?: 0f)
                val b = background()
                if (cancelled) { if (bgFingers.isEmpty()) b?.cancel() }
                else if (bgFingers.isEmpty()) b?.up(f, t) else b?.pointerUp(f, bgFingers.values.toList(), t)
            }
            Ignored -> Unit
        }
    }

    private fun endLook(l: Look) {
        if (l.stick != null) {
            l.stick.release()
            decaying.remove(l.side)
            input.setStick(l.side, 0f, 0f)
        }
    }

    // ---- per kind ----

    private fun pressMove(o: Press, f: Finger, dx: Float, dy: Float, dt: Long, t: Long) {
        if (!o.e.lookThrough) return
        val look = o.look
        if (look == null) {
            // Fire at once; aim as soon as the finger moves more than a tap's jitter. The travel so
            // far isn't lost: it turns the camera now, so the aim starts exactly where the drag did.
            val tx = f.x - o.downX
            val ty = f.y - o.downY
            if (hypot(tx, ty) >= ControlElement.LOOK_THROUGH_DP * layout.density) {
                val l = aimLook()
                o.look = l
                lookMove(l, tx, ty, dt, t)
            }
            return
        }
        lookMove(look, dx, dy, dt, t)
    }

    private fun lookMove(o: Look, dx: Float, dy: Float, dt: Long, t: Long) {
        val d = layout.density.coerceAtLeast(0.1f)
        o.stick?.add(dx / d, dy / d, t)
        o.mouse?.let { m ->
            val (mx, my) = m.move(dx, dy, dt, d)
            input.mouseMove(mx, my)
        }
    }

    private fun dpadAt(o: Dpad, x: Float, y: Float) {
        val r = min(o.rect.width, o.rect.height) / 2f
        val nx = (x - o.rect.cx) / r
        val ny = (y - o.rect.cy) / r
        val dirs = buildSet {
            if (ny < -DPAD_T) add(0)
            if (ny > DPAD_T) add(1)
            if (nx < -DPAD_T) add(2)
            if (nx > DPAD_T) add(3)
        }
        if (dirs != o.dirs) {
            o.dirs = dirs
            input.dpad(o.e, dirs)
            visual(o.e.id, Visual(pressed = dirs.isNotEmpty(), dirs = dirs))
        }
    }

    private fun stickAt(o: Stick, x: Float, y: Float) {
        var nx = (x - o.ox) / o.radius
        var ny = (y - o.oy) / o.radius
        val m = hypot(nx, ny)
        if (m > 1f) { nx /= m; ny /= m }
        moveExtras(o.e, o.move, (x - o.ox) / o.radius, (y - o.oy) / o.radius)
        // On the lock mark the character already runs straight ahead.
        if (o.move.armed) { nx = 0f; ny = -1f }
        input.stick(o.e, nx, -ny)
        visual(o.e.id, Visual(pressed = true, knobX = nx, knobY = ny, originX = if (o.e.floating) o.ox - o.rect.left else Float.NaN, originY = if (o.e.floating) o.oy - o.rect.top else Float.NaN, sprint = o.move.sprinting, lockArmed = o.move.armed))
    }

    private fun floatAt(o: FloatStick, x: Float, y: Float) {
        var nx = (x - o.ox) / o.radius
        var ny = (y - o.oy) / o.radius
        val m = hypot(nx, ny)
        if (m > 1f) { nx /= m; ny /= m }
        moveExtras(o.e, o.move, (x - o.ox) / o.radius, (y - o.oy) / o.radius)
        if (o.move.armed) { nx = 0f; ny = -1f }
        if (o.e.stick == StickOutput.KEYS) {
            // A floating WASD stick (keyboard shooters): the same direction keys as a key stick.
            input.stick(o.e, nx, -ny)
        } else {
            val (sx, sy) = ControlsInput.applyDeadzone(nx, -ny, o.e.deadzone)
            input.setStick(if (o.e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT, sx, sy)
        }
        visual(o.e.id, Visual(pressed = true, knobX = nx, knobY = ny, originX = o.ox - o.rect.left, originY = o.oy - o.rect.top, sprint = o.move.sprinting, lockArmed = o.move.armed))
    }

    /**
     * Auto-sprint and run lock from the thumb's raw offset ([rx], [ry]: multiples of the radius,
     * y down, not clamped). Sprint holds while the thumb is pushed forward past the ring; the
     * lock arms while it sits on the mark above the stick.
     */
    private fun moveExtras(e: ControlElement, s: MoveState, rx: Float, ry: Float) {
        if (e.sprint == Binding.None && !e.runLock) return
        val m = hypot(rx, ry)
        val forward = ry < 0f && Math.toDegrees(kotlin.math.atan2(kotlin.math.abs(rx), -ry).toDouble()) <= ControlElement.SPRINT_CONE_DEG
        val armed = e.runLock && -ry >= ControlElement.RUN_LOCK_AT && kotlin.math.abs(rx) <= RUN_LOCK_WIDTH
        val sprinting = e.sprint != Binding.None && (armed || (forward && m >= e.sprintAt.coerceIn(1f, 2f)))
        s.armed = armed
        if (sprinting != s.sprinting) {
            s.sprinting = sprinting
            input.holdExtra(sprintOwner(e), if (sprinting) listOf(e.sprint) else emptyList())
        }
    }

    private fun endMove(e: ControlElement, s: MoveState) {
        if (s.sprinting) input.holdExtra(sprintOwner(e), emptyList())
        s.sprinting = false
        s.armed = false
    }

    /** Run lock: keep running forward (and sprinting) with no finger on the stick. */
    private fun lock(e: ControlElement, ox: Float, oy: Float) {
        locks[e.id] = Lock(e, ox, oy)
        pushForward(e, 1f)
        if (e.sprint != Binding.None) input.holdExtra(sprintOwner(e), listOf(e.sprint))
        visual(e.id, Visual(pressed = false, knobX = 0f, knobY = -1f, originX = if (e.kind == ElementKind.ZONE || e.floating) ox else Float.NaN, originY = if (e.kind == ElementKind.ZONE || e.floating) oy else Float.NaN, sprint = e.sprint != Binding.None, locked = true))
    }

    private fun unlock(id: String) {
        val l = locks.remove(id) ?: return
        pushForward(l.e, 0f)
        input.holdExtra(sprintOwner(l.e), emptyList())
        visual(id, Visual())
    }

    private fun pushForward(e: ControlElement, amount: Float) {
        if (e.kind == ElementKind.STICK || e.stick == StickOutput.KEYS) input.stick(e, 0f, amount)
        else input.setStick(if (e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT, 0f, amount)
    }

    private fun sprintOwner(e: ControlElement) = e.id + "#sprint"

    private fun bgDown(id: Int, x: Float, y: Float, t: Long) {
        val f = Finger(id, x, y)
        val b = background()
        if (bgFingers.isEmpty()) { bgFingers[id] = f; b?.down(f, t) } else { bgFingers[id] = f; b?.pointerDown(f, bgFingers.values.toList(), t) }
    }

    private fun visual(id: String, v: Visual) = onVisual(id, v)

    companion object {
        /** The Look background starts this far across the controls area (the right 55 % looks). */
        const val LOOK_SPLIT = 0.45f
        const val TAP_MS = 220L
        const val TAP_SLOP_DP = 12f
        const val DPAD_T = 0.35f
        /** Floating joystick zone ring radius, dp (the overlay draws the same). */
        const val FLOAT_RING_DP = 64f
        /** A floating stick element's largest radius, dp. */
        const val FLOAT_STICK_DP = 60f
        /** The run-lock mark catches the thumb this far either side of the stick's axis (× radius). */
        const val RUN_LOCK_WIDTH = 1.2f
    }
}
