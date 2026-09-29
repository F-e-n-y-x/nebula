package io.github.f_e_n_y_x.nebula.ui.screens

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursor
import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorImage
import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.input.TrackingInput
import io.github.fenyx.nebula.engine.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCursorTest {
    /** 16 × 24 arrow with its hotspot 2 px in and 4 px down, as sent at stream resolution. */
    private val arrow = RemoteCursorImage(1, 16, 24, 2, 4, IntArray(16 * 24))
    private val eps = 0.001f

    private fun place(mode: ScaleMode, cw: Float, ch: Float, vw: Float, vh: Float, x: Float, y: Float) =
        cursorPlacement(videoRect(mode, cw, ch, vw, vh, "center", 0, 0), vw, vh, x, y, arrow)

    @Test
    fun `fit scales the shape like the video and letterboxes`() {
        // 1920x1080 stream on a 2340x1080 phone: scale 1, video centred 210 px in.
        val p = place(ScaleMode.FIT, 2340f, 1080f, 1920f, 1080f, 0.5f, 0.5f)
        assertEquals(210f + 960f - 2f, p.left, eps)
        assertEquals(540f - 4f, p.top, eps)
        assertEquals(16f, p.width, eps)
        assertEquals(24f, p.height, eps)

        // 3840x2160 on the same phone: half size, hotspot offset halves too.
        val q = place(ScaleMode.FIT, 2340f, 1080f, 3840f, 2160f, 0f, 0f)
        assertEquals(210f - 1f, q.left, eps)
        assertEquals(-2f, q.top, eps)
        assertEquals(8f, q.width, eps)
        assertEquals(12f, q.height, eps)
    }

    @Test
    fun `fill crops and scales up`() {
        // 1920x1080 filling 2340x1080: scale 1.21875, height overflows and is centred.
        val s = 2340f / 1920f
        val p = place(ScaleMode.FILL, 2340f, 1080f, 1920f, 1080f, 1f, 1f)
        val top = (1080f - 1080f * s) / 2
        assertEquals(2340f - 2f * s, p.left, eps)
        assertEquals(top + 1080f * s - 4f * s, p.top, eps)
        assertEquals(16f * s, p.width, eps)
        assertEquals(24f * s, p.height, eps)
    }

    @Test
    fun `stretch scales each axis on its own`() {
        val p = place(ScaleMode.STRETCH, 2340f, 1080f, 1920f, 1440f, 0.25f, 0.75f)
        val sx = 2340f / 1920f
        val sy = 1080f / 1440f
        assertEquals(0.25f * 2340f - 2f * sx, p.left, eps)
        assertEquals(0.75f * 1080f - 4f * sy, p.top, eps)
        assertEquals(16f * sx, p.width, eps)
        assertEquals(24f * sy, p.height, eps)
    }

    @Test
    fun `pinch zoom scales the shape with the picture`() {
        val base = videoRect(ScaleMode.FIT, 1920f, 1080f, 1920f, 1080f, "center", 0, 0)
        val zoomedRect = zoomed(base, 2f, 0f, 0f, 1920f, 1080f)
        val p = cursorPlacement(zoomedRect, 1920f, 1080f, 0.5f, 0.5f, arrow)
        assertEquals(32f, p.width, eps)
        assertEquals(960f - 4f, p.left, eps)
    }

    @Test
    fun `only a LOCAL cursor is drawn instead of the video's`() {
        assertTrue(RemoteCursor(RemoteCursorMode.LOCAL).drawnLocally)
        RemoteCursorMode.entries.filter { it != RemoteCursorMode.LOCAL }.forEach { assertFalse(it.name, RemoteCursor(it).drawnLocally) }
    }

    @Test
    fun `the note explains the fallback`() {
        assertTrue(localCursorNote(true, RemoteCursorMode.UNSUPPORTED).contains("stays in the video"))
        assertTrue(localCursorNote(true, RemoteCursorMode.LOCAL).contains("no stream delay"))
    }

    private class Recorder : RemoteInput {
        val events = mutableListOf<String>()
        override fun key(event: KeyEvent) = true
        override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = Unit
        override fun text(text: String) = Unit
        override fun move(dx: Int, dy: Int) { events += "move $dx $dy" }
        override fun position(x: Int, y: Int, refW: Int, refH: Int) { events += "pos $x $y $refW $refH" }
        override fun button(button: MouseButton, down: Boolean) = Unit
        override fun scroll(amount: Int) = Unit
        override fun scrollHorizontal(amount: Int) = Unit
        override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
        override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) = Unit
        override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
    }

    @Test
    fun `a pinned pointer follows the drawn cursor exactly`() {
        val rec = Recorder()
        val t = TrackingInput(rec).apply { frameWidth = 1001; frameHeight = 501 }
        t.move(100, 50)
        assertEquals("move 100 50", rec.events.last())
        t.pinPointer = true // moves the PC's pointer to the estimate once
        assertEquals("pos 600 300 1001 501", rec.events.last())
        t.move(-100, 0)
        assertEquals("pos 500 300 1001 501", rec.events.last())
        t.pinPointer = false // a game hid the pointer: raw relative motion again
        t.move(3, 4)
        assertEquals("move 3 4", rec.events.last())
    }
}
