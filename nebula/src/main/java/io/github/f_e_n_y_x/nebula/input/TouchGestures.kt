package io.github.f_e_n_y_x.nebula.input

import io.github.fenyx.nebula.engine.MouseButton
import kotlin.math.abs
import kotlin.math.hypot

/** How the touchscreen drives the PC. Mirrors V+'s "Control method preset". */
enum class TouchMode(val pref: String, val label: String) {
    /** Relative movement like a laptop trackpad. */
    TRACKPAD("trackpad", "Trackpad"),
    /** The pointer jumps to your finger. */
    POINTER("classic", "Direct pointer"),
    /** Real multi-touch on the PC (Windows touch). */
    TOUCH("native", "Touch"),
    /** One side is a trackpad zone, the rest is direct touch. */
    SPLIT("enhanced", "Split"),
    ;

    companion object {
        fun fromPrefs(preset: String?, touchscreenTrackpad: Boolean): TouchMode = when (preset) {
            "classic" -> POINTER
            "enhanced" -> SPLIT
            "native" -> TOUCH
            else -> if (touchscreenTrackpad) TRACKPAD else TOUCH
        }
    }
}

data class TouchConfig(
    val mode: TouchMode = TouchMode.TRACKPAD,
    /** Trackpad speed multiplier. */
    val speed: Float = 1.4f,
    /** Faster finger movement moves the pointer further, like a laptop trackpad. */
    val acceleration: Boolean = true,
    /** Content follows the fingers (touchscreen style); off = wheel style. */
    val naturalScroll: Boolean = true,
    /** Pinch zooms the local picture (nothing is sent to the PC). */
    val pinchZoom: Boolean = true,
    /** Speed inside the split mode's pointer zone (V+ pointer_velocity_factor / 100). */
    val splitSpeed: Float = 1f,
    val splitPointerOnLeft: Boolean = false,
    /** Split boundary as a share of the view width. */
    val splitDivider: Float = 0.5f,
    val doubleTapDrag: Boolean = false,
    val doubleTapMs: Long = 250,
    val keyboardGesture: Boolean = true,
    /** Fingers for the keyboard gesture in touch modes; trackpad and pointer always use 3. */
    val keyboardFingers: Int = 3,
    /** Movement (px) below which a touch still counts as a tap. */
    val slopPx: Float = 14f,
)

/** The decoded video's rectangle inside the input view, in view pixels. */
data class VideoRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    fun normX(x: Float) = ((x - left) / width).coerceIn(0f, 1f)
    fun normY(y: Float) = ((y - top) / height).coerceIn(0f, 1f)
}

data class Finger(val id: Int, val x: Float, val y: Float)

interface GestureCallbacks {
    fun onKeyboard()
    fun onMenu()
    /** Pinch: multiply the local zoom by [factor] around ([focusX], [focusY]). */
    fun onZoom(factor: Float, focusX: Float, focusY: Float) {}
    /** Two-finger drag while zoomed moves the zoomed picture. */
    fun onPan(dx: Float, dy: Float) {}
    fun isZoomed(): Boolean = false
    /** A hold was recognised (right click on release, or drag if the finger moves). */
    fun onHold() {}
    /** The PC has no native touch; the screen falls back to the direct pointer. */
    fun onTouchUnsupported() {}
}

/**
 * Turns touchscreen contacts into PC input. Pure logic, no Android types, so it's unit tested.
 * Times are milliseconds on any monotonic clock. Call [longPress] ~[LONG_PRESS_MS] after a
 * one-finger down if that finger is still down.
 */
class TouchGestures(
    private val out: () -> RemoteInput?,
    private val config: () -> TouchConfig,
    private val video: () -> VideoRect,
    private val viewWidth: () -> Float,
    private val callbacks: GestureCallbacks,
) {
    private var gestureMode = TouchMode.TRACKPAD
    private var touchUnsupported = false
    private var maxFingers = 0
    private var downAt = 0L
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var held = false
    private var dragging = false
    private var lastMoveAt = 0L
    private var startSpan = 0f
    private var lastSpan = 0f
    private var twoFinger = TwoFinger.UNDECIDED
    private var lastTapUpAt = Long.MIN_VALUE / 2
    private var accX = 0f
    private var accY = 0f
    private var scrollAccX = 0f
    private var scrollAccY = 0f
    private var lastCentroid: Pair<Float, Float>? = null
    private val touching = mutableSetOf<Int>()

    /** True while one finger has been down without moving; the view uses it to schedule [longPress]. */
    val awaitingLongPress get() = maxFingers == 1 && !moved && !held && !dragging

    fun down(f: Finger, t: Long) {
        val c = config()
        gestureMode = resolveMode(c, f.x)
        maxFingers = 1
        downAt = t
        startX = f.x; startY = f.y
        lastX = f.x; lastY = f.y
        moved = false
        held = false
        lastMoveAt = t
        twoFinger = TwoFinger.UNDECIDED
        accX = 0f; accY = 0f
        scrollAccX = 0f; scrollAccY = 0f
        lastCentroid = null
        val o = out() ?: return
        when (gestureMode) {
            TouchMode.TOUCH -> touch(o, TOUCH_DOWN, f)
            TouchMode.POINTER -> pointerTo(o, f)
            else -> Unit
        }
        if (gestureMode != TouchMode.TOUCH && c.doubleTapDrag && t - lastTapUpAt <= c.doubleTapMs) {
            dragging = true
            o.button(MouseButton.LEFT, true)
        }
    }

    fun pointerDown(f: Finger, all: List<Finger>, t: Long) {
        maxFingers = maxOf(maxFingers, all.size)
        lastCentroid = centroid(all)
        if (all.size == 2) {
            startSpan = span(all)
            lastSpan = startSpan
            twoFinger = TwoFinger.UNDECIDED
        }
        val o = out() ?: return
        if (gestureMode == TouchMode.TOUCH) touch(o, TOUCH_DOWN, f)
    }

    fun move(all: List<Finger>, t: Long) {
        if (all.isEmpty()) return
        val c = config()
        val o = out()
        val first = all.first()
        if (!moved && hypot(first.x - startX, first.y - startY) > c.slopPx) moved = true
        when {
            gestureMode == TouchMode.TOUCH -> if (o != null) all.forEach { touch(o, TOUCH_MOVE, it) }
            all.size >= 2 -> twoFingers(o, all, c)
            all.size == 1 && maxFingers == 1 -> {
                if (held && moved && !dragging) {
                    // Hold, then drag: a click-drag (drag lock) with the left button.
                    dragging = true
                    o?.button(MouseButton.LEFT, true)
                }
                if (gestureMode == TouchMode.POINTER) {
                    if (moved && !dragging) {
                        // Moving a direct pointer drags, like pressing on a real screen.
                        dragging = true
                        o?.button(MouseButton.LEFT, true)
                    }
                    if (o != null) pointerTo(o, first)
                } else if (moved || dragging) {
                    val rawX = first.x - lastX
                    val rawY = first.y - lastY
                    val base = if (gestureMode == TouchMode.SPLIT) c.splitSpeed else c.speed
                    val speed = if (c.acceleration) base * accelGain(hypot(rawX, rawY) / (t - lastMoveAt).coerceAtLeast(1)) else base
                    val dx = rawX * speed + accX
                    val dy = rawY * speed + accY
                    val ix = dx.toInt()
                    val iy = dy.toInt()
                    accX = dx - ix
                    accY = dy - iy
                    if (ix != 0 || iy != 0) o?.move(ix, iy)
                }
            }
        }
        lastX = first.x
        lastY = first.y
        lastMoveAt = t
    }

    fun pointerUp(f: Finger, remaining: List<Finger>, t: Long) {
        val o = out()
        if (gestureMode == TouchMode.TOUCH && o != null) touch(o, TOUCH_UP, f)
        lastCentroid = centroid(remaining)
        remaining.firstOrNull()?.let { lastX = it.x; lastY = it.y }
        if (remaining.size < 2) twoFinger = TwoFinger.DONE
    }

    fun up(f: Finger, t: Long) {
        val c = config()
        val o = out()
        val tap = !moved && !held && t - downAt < TAP_MS
        if (gestureMode == TouchMode.TOUCH) {
            if (o != null) touch(o, TOUCH_UP, f)
            if (tap && c.keyboardGesture && maxFingers >= c.keyboardFingers.coerceAtLeast(3)) callbacks.onKeyboard()
            touching.clear()
            return
        }
        if (dragging) {
            dragging = false
            o?.button(MouseButton.LEFT, false)
            lastTapUpAt = Long.MIN_VALUE / 2
            return
        }
        if (held && !moved && maxFingers == 1) {
            o?.click(MouseButton.RIGHT)
            return
        }
        if (!tap) return
        when {
            maxFingers >= 4 -> callbacks.onMenu()
            maxFingers == 3 -> if (c.keyboardGesture) callbacks.onKeyboard()
            maxFingers == 2 -> o?.click(MouseButton.RIGHT)
            else -> {
                o?.click(MouseButton.LEFT)
                lastTapUpAt = t
            }
        }
    }

    /**
     * One finger held still for [LONG_PRESS_MS]. Lifting it right clicks; moving it drags with the
     * left button held.
     */
    fun longPress(t: Long) {
        if (!awaitingLongPress || gestureMode == TouchMode.TOUCH || t - downAt < LONG_PRESS_MS) return
        held = true
        callbacks.onHold()
    }

    fun cancel() {
        val o = out()
        if (dragging) o?.button(MouseButton.LEFT, false)
        dragging = false
        if (gestureMode == TouchMode.TOUCH && o != null) {
            touching.toList().forEach { o.touch(TOUCH_CANCEL, it, 0f, 0f) }
        }
        touching.clear()
        maxFingers = 0
    }

    private fun resolveMode(c: TouchConfig, x: Float): TouchMode {
        val mode = if (c.mode == TouchMode.TOUCH && touchUnsupported) TouchMode.POINTER else c.mode
        if (mode != TouchMode.SPLIT) return mode
        val inLeft = x < viewWidth() * c.splitDivider
        return if (inLeft == c.splitPointerOnLeft) TouchMode.SPLIT else if (touchUnsupported) TouchMode.POINTER else TouchMode.TOUCH
    }

    /** Two fingers: scroll, or pinch to zoom the local picture, or pan it while zoomed. */
    private fun twoFingers(o: RemoteInput?, all: List<Finger>, c: TouchConfig) {
        val (cx, cy) = centroid(all) ?: return
        val prev = lastCentroid ?: (cx to cy)
        lastCentroid = cx to cy
        val dx = cx - prev.first
        val dy = cy - prev.second
        val sp = span(all)
        if (twoFinger == TwoFinger.UNDECIDED) {
            val spanChange = abs(sp - startSpan)
            twoFinger = when {
                c.pinchZoom && spanChange > c.slopPx * 2 && spanChange > startSpan * PINCH_SHARE -> TwoFinger.PINCH
                hypot(cx - startX, cy - startY) > c.slopPx || hypot(dx, dy) > c.slopPx ->
                    if (callbacks.isZoomed()) TwoFinger.PAN else TwoFinger.SCROLL
                else -> TwoFinger.UNDECIDED
            }
        }
        if (twoFinger != TwoFinger.UNDECIDED) moved = true
        when (twoFinger) {
            TwoFinger.PINCH -> {
                if (lastSpan > 0f && sp > 0f) callbacks.onZoom(sp / lastSpan, cx, cy)
                callbacks.onPan(dx, dy)
            }
            TwoFinger.PAN -> callbacks.onPan(dx, dy)
            TwoFinger.SCROLL -> {
                val dir = if (c.naturalScroll) 1f else -1f
                // Natural: fingers moving up scroll the page down, like a touchscreen.
                scrollAccY += dy * SCROLL_UNITS_PER_PX * dir
                scrollAccX += -dx * SCROLL_UNITS_PER_PX * dir
                val sy = scrollAccY.toInt()
                val sx = scrollAccX.toInt()
                if (sy != 0 && abs(dy) >= abs(dx)) { o?.scroll(sy); scrollAccY -= sy }
                if (sx != 0 && abs(dx) > abs(dy)) { o?.scrollHorizontal(sx); scrollAccX -= sx }
            }
            else -> Unit
        }
        lastSpan = sp
    }

    private fun span(all: List<Finger>) = if (all.size < 2) 0f else hypot(all[0].x - all[1].x, all[0].y - all[1].y)

    /** Pointer gain for a finger speed in px/ms: 1× when slow, up to ~2.5× when flicking. */
    private fun accelGain(v: Float) = 1f + ACCEL * (v - ACCEL_FROM).coerceIn(0f, ACCEL_RANGE)

    private fun pointerTo(o: RemoteInput, f: Finger) {
        val v = video()
        o.position((v.normX(f.x) * v.width).toInt(), (v.normY(f.y) * v.height).toInt(), v.width.toInt(), v.height.toInt())
    }

    private fun touch(o: RemoteInput, type: Byte, f: Finger) {
        val v = video()
        val ok = o.touch(type, f.id, v.normX(f.x), v.normY(f.y))
        if (!ok && type == TOUCH_DOWN && !touchUnsupported) {
            touchUnsupported = true
            callbacks.onTouchUnsupported()
        }
        if (type == TOUCH_DOWN) touching += f.id else if (type == TOUCH_UP) touching -= f.id
    }

    private fun centroid(all: List<Finger>): Pair<Float, Float>? =
        if (all.isEmpty()) null else all.sumOf { it.x.toDouble() }.toFloat() / all.size to all.sumOf { it.y.toDouble() }.toFloat() / all.size

    private enum class TwoFinger { UNDECIDED, SCROLL, PINCH, PAN, DONE }

    companion object {
        const val TAP_MS = 300L
        const val PINCH_SHARE = 0.12f
        const val ACCEL = 0.6f
        const val ACCEL_FROM = 0.25f
        const val ACCEL_RANGE = 2.5f
        const val LONG_PRESS_MS = 550L
        const val SCROLL_UNITS_PER_PX = 3f
        // MoonBridge.LI_TOUCH_EVENT_*
        const val TOUCH_DOWN: Byte = 0x01
        const val TOUCH_UP: Byte = 0x02
        const val TOUCH_MOVE: Byte = 0x03
        const val TOUCH_CANCEL: Byte = 0x04
    }
}
