package io.github.f_e_n_y_x.nebula.input

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.ui.screens.ScaleMode
import io.github.f_e_n_y_x.nebula.ui.screens.videoRect
import io.github.fenyx.nebula.engine.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeInput(private val touchSupported: Boolean = true) : RemoteInput {
    val events = mutableListOf<String>()
    var dx = 0
    var dy = 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { events += "vk $vk ${if (down) "down" else "up"} $modifiers" }
    override fun text(text: String) { events += "text $text" }
    override fun move(dx: Int, dy: Int) { this.dx += dx; this.dy += dy }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) { events += "pos $x $y $refW $refH" }
    override fun button(button: MouseButton, down: Boolean) { events += "$button ${if (down) "down" else "up"}" }
    override fun scroll(amount: Int) { events += "scroll" }
    override fun scrollHorizontal(amount: Int) { events += "hscroll" }
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float): Boolean { events += "touch $type $pointerId"; return touchSupported }
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) = Unit
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

class TouchGesturesTest {
    private val out = FakeInput()
    private var config = TouchConfig(speed = 1f, acceleration = false)
    private var keyboards = 0
    private var menus = 0
    private var zoom = 1f
    private var panX = 0f
    private val g = TouchGestures(
        out = { out },
        config = { config },
        video = { VideoRect(0f, 0f, 1000f, 500f) },
        viewWidth = { 1000f },
        callbacks = object : GestureCallbacks {
            override fun onKeyboard() { keyboards++ }
            override fun onMenu() { menus++ }
            override fun onZoom(factor: Float, focusX: Float, focusY: Float) { zoom *= factor }
            override fun onPan(dx: Float, dy: Float) { panX += dx }
            override fun isZoomed() = zoom > 1.01f
        },
    )

    @Test
    fun `tap clicks left`() {
        g.down(Finger(0, 100f, 100f), 0)
        g.up(Finger(0, 100f, 100f), 80)
        assertEquals(listOf("LEFT down", "LEFT up"), out.events)
    }

    @Test
    fun `small drags keep fractional movement instead of truncating it`() {
        g.down(Finger(0, 0f, 0f), 0)
        g.move(listOf(Finger(0, 20f, 0f)), 10) // past the slop
        var x = 20f
        repeat(100) { x += 0.4f; g.move(listOf(Finger(0, x, 0f)), 20L + it) }
        g.up(Finger(0, x, 0f), 500)
        // 20 px to leave the tap slop, then 100 moves of 0.4 px; truncating each would have lost the 40.
        assertEquals(60, out.dx)
        assertTrue(out.events.none { it.startsWith("LEFT") })
    }

    @Test
    fun `speed scales trackpad movement`() {
        config = config.copy(speed = 2f)
        g.down(Finger(0, 0f, 0f), 0)
        g.move(listOf(Finger(0, 30f, 0f)), 10)
        g.move(listOf(Finger(0, 60f, 10f)), 20)
        // Movement made before crossing the tap slop isn't lost.
        assertEquals(120, out.dx)
        assertEquals(20, out.dy)
    }

    @Test
    fun `two finger tap right clicks and three finger tap opens the keyboard`() {
        g.down(Finger(0, 100f, 100f), 0)
        g.pointerDown(Finger(1, 200f, 100f), listOf(Finger(0, 100f, 100f), Finger(1, 200f, 100f)), 10)
        g.pointerUp(Finger(1, 200f, 100f), listOf(Finger(0, 100f, 100f)), 60)
        g.up(Finger(0, 100f, 100f), 70)
        assertEquals(listOf("RIGHT down", "RIGHT up"), out.events)

        val three = listOf(Finger(0, 100f, 100f), Finger(1, 200f, 100f), Finger(2, 300f, 100f))
        g.down(three[0], 1000)
        g.pointerDown(three[1], three.take(2), 1010)
        g.pointerDown(three[2], three, 1020)
        g.pointerUp(three[2], three.take(2), 1050)
        g.pointerUp(three[1], three.take(1), 1055)
        g.up(three[0], 1060)
        assertEquals(1, keyboards)
    }

    @Test
    fun `four finger tap opens the menu`() {
        val four = (0..3).map { Finger(it, 100f * it, 100f) }
        g.down(four[0], 0)
        for (i in 1..3) g.pointerDown(four[i], four.take(i + 1), i.toLong())
        for (i in 3 downTo 1) g.pointerUp(four[i], four.take(i), 50L + i)
        g.up(four[0], 60)
        assertEquals(1, menus)
    }

    @Test
    fun `two finger drag scrolls`() {
        val a = listOf(Finger(0, 100f, 300f), Finger(1, 200f, 300f))
        g.down(a[0], 0)
        g.pointerDown(a[1], a, 5)
        g.move(listOf(Finger(0, 100f, 250f), Finger(1, 200f, 250f)), 20)
        g.move(listOf(Finger(0, 100f, 200f), Finger(1, 200f, 200f)), 40)
        assertTrue(out.events.contains("scroll"))
        assertTrue(out.events.none { it.contains("RIGHT") })
    }

    @Test
    fun `long press right clicks once`() {
        g.down(Finger(0, 100f, 100f), 0)
        g.longPress(TouchGestures.LONG_PRESS_MS)
        g.up(Finger(0, 100f, 100f), 900)
        assertEquals(listOf("RIGHT down", "RIGHT up"), out.events)
    }

    @Test
    fun `hold then drag is a left click-drag`() {
        g.down(Finger(0, 100f, 100f), 0)
        g.longPress(TouchGestures.LONG_PRESS_MS)
        g.move(listOf(Finger(0, 150f, 100f)), 700)
        g.move(listOf(Finger(0, 200f, 100f)), 720)
        g.up(Finger(0, 200f, 100f), 800)
        assertEquals(listOf("LEFT down", "LEFT up"), out.events)
        assertEquals(100, out.dx)
    }

    @Test
    fun `pinch zooms locally and sends nothing`() {
        val a = listOf(Finger(0, 400f, 250f), Finger(1, 600f, 250f))
        g.down(a[0], 0)
        g.pointerDown(a[1], a, 5)
        g.move(listOf(Finger(0, 350f, 250f), Finger(1, 650f, 250f)), 20)
        g.move(listOf(Finger(0, 300f, 250f), Finger(1, 700f, 250f)), 40)
        g.pointerUp(a[1], listOf(Finger(0, 300f, 250f)), 60)
        g.up(Finger(0, 300f, 250f), 70)
        assertEquals(2f, zoom, 0.05f)
        assertEquals(emptyList<String>(), out.events)
    }

    @Test
    fun `fast flicks move further with acceleration`() {
        config = config.copy(acceleration = true)
        g.down(Finger(0, 0f, 0f), 0)
        g.move(listOf(Finger(0, 20f, 0f)), 100) // slow: 0.2 px/ms
        val slow = out.dx
        g.move(listOf(Finger(0, 60f, 0f)), 110) // fast: 4 px/ms
        assertEquals(20, slow)
        assertTrue("fast move ${out.dx - slow}", out.dx - slow > 80)
    }

    @Test
    fun `wheel style scrolling reverses direction`() {
        val amounts = mutableListOf<Int>()
        val o = object : RemoteInput by out { override fun scroll(amount: Int) { amounts += amount } }
        for (natural in listOf(true, false)) {
            config = config.copy(naturalScroll = natural)
            val gg = TouchGestures({ o }, { config }, { VideoRect(0f, 0f, 1000f, 500f) }, { 1000f }, object : GestureCallbacks {
                override fun onKeyboard() = Unit
                override fun onMenu() = Unit
            })
            val a = listOf(Finger(0, 100f, 300f), Finger(1, 200f, 300f))
            gg.down(a[0], 0); gg.pointerDown(a[1], a, 5)
            gg.move(listOf(Finger(0, 100f, 250f), Finger(1, 200f, 250f)), 20)
        }
        assertTrue(amounts.first() < 0 && amounts.last() > 0)
    }

    @Test
    fun `double tap and hold drags with the left button`() {
        config = config.copy(doubleTapDrag = true, doubleTapMs = 300)
        g.down(Finger(0, 100f, 100f), 0)
        g.up(Finger(0, 100f, 100f), 60)
        g.down(Finger(0, 100f, 100f), 150)
        g.move(listOf(Finger(0, 160f, 100f)), 200)
        g.up(Finger(0, 160f, 100f), 400)
        assertEquals(listOf("LEFT down", "LEFT up", "LEFT down", "LEFT up"), out.events)
        assertEquals(60, out.dx)
    }

    @Test
    fun `direct pointer mode positions within the video`() {
        config = config.copy(mode = TouchMode.POINTER)
        g.down(Finger(0, 500f, 250f), 0)
        g.up(Finger(0, 500f, 250f), 50)
        assertEquals(listOf("pos 500 250 1000 500", "LEFT down", "LEFT up"), out.events)
    }

    @Test
    fun `touch mode sends native touches`() {
        config = config.copy(mode = TouchMode.TOUCH)
        g.down(Finger(7, 500f, 250f), 0)
        g.up(Finger(7, 500f, 250f), 50)
        assertEquals(listOf("touch 1 7", "touch 2 7"), out.events)
    }

    @Test
    fun `fit letterboxes and anchors, fill crops, stretch fills`() {
        val fit = videoRect(ScaleMode.FIT, 2400f, 1080f, 1920f, 1080f, "center", 0, 0)
        assertEquals(1920f, fit.width, 0.5f)
        assertEquals(240f, fit.left, 0.5f)
        val left = videoRect(ScaleMode.FIT, 2400f, 1080f, 1920f, 1080f, "center_left", 0, 0)
        assertEquals(0f, left.left, 0.5f)
        val fill = videoRect(ScaleMode.FILL, 2400f, 1080f, 1920f, 1080f, "center", 0, 0)
        assertEquals(2400f, fill.width, 0.5f)
        assertEquals(1350f, fill.height, 0.5f)
        assertEquals(-135f, fill.top, 0.5f)
        val stretch = videoRect(ScaleMode.STRETCH, 2400f, 1080f, 1920f, 1080f, "center", 0, 0)
        assertEquals(2400f, stretch.width, 0.5f)
        assertEquals(1080f, stretch.height, 0.5f)
    }

    @Test
    fun `video rect never throws for any screen and video size`() {
        val sizes = listOf(1080f, 1600f, 1920f, 2340f, 2560f, 1599.9999f, 3840f, 720f)
        val positions = listOf("center", "top_left", "bottom_right", "center_left")
        for (cw in sizes) for (ch in sizes) for (vw in sizes) for (vh in sizes) for (p in positions) for (m in ScaleMode.entries) {
            val r = videoRect(m, cw, ch, vw, vh, p, 30, 70)
            assertTrue(r.width > 0f && r.height > 0f)
        }
    }

    @Test
    fun `shortcuts press in order and release in reverse with modifiers`() {
        out.press(Shortcut("Ctrl + Alt + Del", listOf(Vk.CTRL, Vk.ALT, Vk.DELETE)))
        assertEquals(
            listOf(
                "vk ${Vk.CTRL} down 0", "vk ${Vk.ALT} down 2", "vk ${Vk.DELETE} down 6",
                "vk ${Vk.DELETE} up 6", "vk ${Vk.ALT} up 2", "vk ${Vk.CTRL} up 0",
            ),
            out.events,
        )
    }
}
