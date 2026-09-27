package io.github.f_e_n_y_x.nebula.input

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.fenyx.nebula.engine.MouseButton
import kotlin.math.roundToInt

/** Is this event from a real mouse (or a laptop touchpad driving a pointer) rather than a finger? */
fun MotionEvent.isMouse(): Boolean =
    isFromSource(InputDevice.SOURCE_MOUSE) || isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE) ||
        (pointerCount > 0 && getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE)

/**
 * A physical mouse. Captured pointers send raw relative motion (best for games); otherwise the
 * pointer is sent as an absolute position (remote-desktop mode) or as deltas between positions.
 */
class MouseInput(
    private val out: () -> RemoteInput?,
    private val absolute: () -> Boolean,
    private val navButtons: () -> Boolean,
    private val video: () -> VideoRect,
) {
    private var buttons = 0
    private var pressedBare = false
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var accX = 0f
    private var accY = 0f

    fun onEvent(e: MotionEvent, captured: Boolean): Boolean {
        val o = out()
        if (o == null) return true
        when (e.actionMasked) {
            MotionEvent.ACTION_SCROLL -> {
                val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val h = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
                if (v != 0f) o.scroll((v * WHEEL).roundToInt())
                if (h != 0f) o.scrollHorizontal((h * WHEEL).roundToInt())
            }
            MotionEvent.ACTION_HOVER_EXIT -> { lastX = Float.NaN; lastY = Float.NaN }
            else -> {
                motion(o, e, captured)
                // Some pointers (tablet touchpads, adb) report a press with no button state: treat it as left.
                val state = when {
                    e.buttonState != 0 -> e.buttonState
                    e.actionMasked == MotionEvent.ACTION_DOWN || (e.actionMasked == MotionEvent.ACTION_MOVE && pressedBare) -> MotionEvent.BUTTON_PRIMARY
                    else -> 0
                }
                pressedBare = e.buttonState == 0 && state != 0
                syncButtons(o, state)
            }
        }
        return true
    }

    /** Releases any held buttons, e.g. when the menu opens mid-drag. */
    fun reset() {
        val o = out()
        if (o != null) syncButtons(o, 0)
        lastX = Float.NaN
        lastY = Float.NaN
    }

    private fun motion(o: RemoteInput, e: MotionEvent, captured: Boolean) {
        if (captured) {
            // Captured events carry relative motion in x/y, with batched history.
            var dx = accX
            var dy = accY
            for (i in 0 until e.historySize) { dx += e.getHistoricalX(i); dy += e.getHistoricalY(i) }
            dx += e.x; dy += e.y
            send(o, dx, dy)
            return
        }
        if (absolute()) {
            val v = video()
            o.position((v.normX(e.x) * v.width).toInt(), (v.normY(e.y) * v.height).toInt(), v.width.toInt(), v.height.toInt())
            return
        }
        val rx = e.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val ry = e.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)
        if (rx != 0f || ry != 0f) {
            send(o, rx + accX, ry + accY)
        } else if (!lastX.isNaN()) {
            send(o, e.x - lastX + accX, e.y - lastY + accY)
        }
        lastX = e.x
        lastY = e.y
    }

    private fun send(o: RemoteInput, dx: Float, dy: Float) {
        val ix = dx.toInt()
        val iy = dy.toInt()
        accX = dx - ix
        accY = dy - iy
        if (ix != 0 || iy != 0) o.move(ix, iy)
    }

    private fun syncButtons(o: RemoteInput, state: Int) {
        val nav = navButtons()
        for ((mask, button) in BUTTONS) {
            if (!nav && (button == MouseButton.BACK || button == MouseButton.FORWARD)) continue
            val was = buttons and mask != 0
            val now = state and mask != 0
            if (was != now) o.button(button, now)
        }
        buttons = state
    }

    private companion object {
        const val WHEEL = 120f
        val BUTTONS = listOf(
            MotionEvent.BUTTON_PRIMARY to MouseButton.LEFT,
            MotionEvent.BUTTON_SECONDARY to MouseButton.RIGHT,
            MotionEvent.BUTTON_TERTIARY to MouseButton.MIDDLE,
            MotionEvent.BUTTON_BACK to MouseButton.BACK,
            MotionEvent.BUTTON_FORWARD to MouseButton.FORWARD,
        )
    }
}

/**
 * Full-screen input layer over the video: fingers go to [gestures], mice to [mouse], and it is
 * the soft keyboard's target. Requests pointer capture while [wantCapture] and focused.
 */
@SuppressLint("ViewConstructor")
class StreamInputView(
    context: Context,
    private val gestures: TouchGestures,
    private val mouse: MouseInput,
    private val remote: () -> RemoteInput?,
) : View(context) {
    private val longPress = Runnable { gestures.longPress(android.os.SystemClock.uptimeMillis()) }
    private val imm = context.getSystemService(InputMethodManager::class.java)

    /** Capture the mouse (relative motion, hidden local pointer) while true. */
    var wantCapture = false
        set(value) {
            field = value
            updateCapture()
        }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setOnCapturedPointerListener { _, e -> mouse.onEvent(e, captured = true) }
    }

    fun updateCapture() {
        if (wantCapture) {
            if (!hasFocus()) requestFocus()
            if (hasWindowFocus() && !hasPointerCapture()) requestPointerCapture()
        } else if (hasPointerCapture()) {
            releasePointerCapture()
        }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) updateCapture()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.isMouse()) {
            if (wantCapture && !hasPointerCapture()) updateCapture()
            return mouse.onEvent(e, captured = false)
        }
        val t = e.eventTime
        val i = e.actionIndex
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                gestures.down(finger(e, i), t)
                postDelayed(longPress, TouchGestures.LONG_PRESS_MS)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(longPress)
                gestures.pointerDown(finger(e, i), fingers(e), t)
            }
            MotionEvent.ACTION_MOVE -> {
                gestures.move(fingers(e), t)
                if (!gestures.awaitingLongPress) removeCallbacks(longPress)
            }
            MotionEvent.ACTION_POINTER_UP -> gestures.pointerUp(finger(e, i), fingers(e).filterNot { it.id == e.getPointerId(i) }, t)
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                gestures.up(finger(e, i), t)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                gestures.cancel()
            }
        }
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean =
        if (e.isMouse()) mouse.onEvent(e, captured = false) else super.onGenericMotionEvent(e)

    private fun finger(e: MotionEvent, i: Int) = Finger(e.getPointerId(i), e.getX(i), e.getY(i))
    private fun fingers(e: MotionEvent) = (0 until e.pointerCount).map { finger(e, it) }

    // Soft keyboard: text goes to the PC as Unicode, editing keys as key presses.

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return RemoteInputConnection(this, remote)
    }

    val keyboardVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(this)?.isVisible(WindowInsetsCompat.Type.ime()) == true

    fun showKeyboard() {
        requestFocus()
        imm?.restartInput(this)
        imm?.showSoftInput(this, 0)
    }

    fun hideKeyboard() {
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }

    fun toggleKeyboard() = if (keyboardVisible) hideKeyboard() else showKeyboard()
}

private class RemoteInputConnection(view: View, private val remote: () -> RemoteInput?) : BaseInputConnection(view, false) {
    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        val s = text?.toString().orEmpty()
        when (s) {
            "" -> Unit
            "\n" -> remote()?.tap(Vk.ENTER)
            else -> remote()?.text(s)
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

    override fun finishComposingText(): Boolean = true

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        repeat(beforeLength.coerceIn(0, 64)) { remote()?.tap(Vk.BACKSPACE) }
        repeat(afterLength.coerceIn(0, 64)) { remote()?.tap(Vk.DELETE) }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        // Enter, arrows and backspace from the IME: send them straight to the PC.
        val r = remote() ?: return true
        if (!r.key(event) && event.action == KeyEvent.ACTION_DOWN && event.unicodeChar != 0) {
            r.text(event.unicodeChar.toChar().toString())
        }
        return true
    }
}

private fun RemoteInput.tap(vk: Int) {
    virtualKey(vk, true)
    virtualKey(vk, false)
}
