package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private class PadLog : RemoteInput {
    val sent = mutableListOf<List<Int>>()
    var moved = 0 to 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) = Unit
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) { moved = moved.first + dx to moved.second + dy }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) = Unit
    override fun scroll(amount: Int) = Unit
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = false
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        sent += listOf(controller, buttons, lx, ly, rx, ry)
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

/** Touch zones: camera curves and mixing into a physical controller. */
class ZonesTest {
    @Test
    fun cameraStickFollowsFingerSpeedAndRecentres() {
        val cam = CameraStick(sensitivity = 1f, acceleration = 1f, invertY = false)
        // Slow drag right: small push. 90 dp/s is 18 % of full speed.
        var p = cam.move(1.44f, 0f, 16)
        repeat(10) { p = cam.move(1.44f, 0f, 16) }
        assertTrue(p.first in 0.16f..0.20f)
        // A flick saturates; dragging up gives positive (stick-up) y.
        repeat(10) { p = cam.move(0f, -40f, 16) }
        assertTrue(p.second > 0.98f && abs(p.first) < 0.02f)
        // Stopping eases back to centre.
        repeat(10) { p = cam.idle(200) }
        assertEquals(0f to 0f, p)
        // A short gap between touch samples keeps the push.
        repeat(5) { cam.move(20f, 0f, 16) }
        val held = cam.idle(16).first
        assertTrue(held > 0.5f)
        cam.release()
        assertEquals(0f, cam.x)
    }

    @Test
    fun cameraStickInvertAndAcceleration() {
        val inv = CameraStick(1f, 1f, invertY = true)
        var p = 0f to 0f
        repeat(10) { p = inv.move(0f, -40f, 16) }
        assertTrue(p.second < -0.98f)
        // With acceleration 2, 45 % of full speed gives about a fifth instead of 45 %.
        val lin = CameraStick(1f, 1f, false)
        val acc = CameraStick(1f, 2f, false)
        var a = 0f; var l = 0f
        repeat(12) { l = lin.move(3.6f, 0f, 16).first; a = acc.move(3.6f, 0f, 16).first }
        assertTrue(abs(l - 0.45f) < 0.02f)
        assertTrue(abs(a - 0.2025f) < 0.01f)
        // Higher sensitivity reaches full push sooner.
        val fast = CameraStick(4f, 1f, false)
        var f = 0f
        repeat(12) { f = fast.move(3.6f, 0f, 16).first }
        assertTrue(f > 0.98f)
    }

    @Test
    fun cameraMouseCarriesSubPixels() {
        val m = CameraMouse(sensitivity = 0.5f, acceleration = 1f, invertY = true)
        var x = 0; var y = 0
        repeat(4) { val (dx, dy) = m.move(1f, 1f, 16, 2f); x += dx; y += dy }
        assertEquals(2, x) // 4 × 0.5 px, none lost to rounding
        assertEquals(-2, y)
        val accel = CameraMouse(1f, 2f, false)
        val slow = accel.move(1f, 0f, 16, 1f).first
        val (fastX, _) = accel.move(100f, 0f, 16, 1f)
        assertEquals(1, slow)
        assertTrue(fastX > 200) // flicks go further per pixel
    }

    @Test
    fun mixerAddsTouchSticksToThePhysicalPad() {
        val log = PadLog()
        val mix = PadMixer { log }
        mix.touchStick(Side.RIGHT, 1000, 1000)
        assertTrue("nothing sent without a controller", log.sent.isEmpty())
        mix.gamepad(0, 1, 0x1000, 0, 0, 5000, 0, 100, 0) // controller: A held, moving left stick
        assertEquals(listOf(0, 0x1000, 5000, 0, 1100, 1000), log.sent.last())
        mix.touchStick(Side.RIGHT, 32000, 0) // touch aims; the controller's buttons and move stay
        assertEquals(listOf(0, 0x1000, 5000, 0, 32100, 0), log.sent.last())
        mix.gamepad(0, 1, 0, 0, 0, 0, 0, 32000, 0)
        assertEquals(32767, log.sent.last()[4]) // clamped
        mix.gamepad(1, 3, 0, 0, 0, 0, 0, 7, 0) // a second player is left alone
        assertEquals(listOf(1, 0, 0, 0, 7, 0), log.sent.last())
    }

    @Test
    fun zoneSticksGoToTheControllerWhenMixed() = runTest {
        val log = PadLog()
        val mix = PadMixer { log }
        var mixed = true
        val input = ControlsInput({ log }, backgroundScope, mixer = { mix.takeIf { mixed } })
        mix.gamepad(0, 1, 0, 0, 0, 0, 0, 0, 0)
        input.setStick(Side.RIGHT, 0.5f, -0.5f)
        assertEquals(listOf(0, 0, 0, 0, 16383, -16383), log.sent.last())
        input.releaseAll()
        assertEquals(listOf(0, 0, 0, 0, 0, 0), log.sent.last())
        // Without a controller the zone drives the on-screen pad (player 1) itself.
        mixed = false
        input.setStick(Side.LEFT, 1f, 0f)
        assertEquals(listOf(0, 0, ControlsInput.AXIS_MAX, 0, 0, 0), log.sent.last())
    }

    @Test
    fun swipeLookFollowsTheFingerFrameByFrameAndStopsWithIt() {
        // 60 Hz: a 1 cm (63 dp) swipe over 8 frames, then the finger stays down without moving.
        val cam = CameraStick(sensitivity = 1f, acceleration = 1f, invertY = false)
        val rx = mutableListOf<Int>()
        repeat(8) { rx += (cam.move(63f / 8, 0f, 16).first * 32766).toInt() }
        var sinceMove = 0L
        repeat(6) { sinceMove += CameraStick.TICK_MS; rx += (cam.idle(sinceMove).first * 32766).toInt() }
        println("swipe-look RX per frame: $rx")
        assertTrue("turns while the finger moves: $rx", rx.take(8).all { it > 20000 })
        assertTrue("held about a frame, then decays: $rx", rx[8] > 20000 && rx[9] < rx[8])
        assertEquals("stopped within four frames: $rx", 0, rx[12])
        // Press and hold without moving: nothing.
        val still = CameraStick(1f, 1f, false)
        var t = 0L
        repeat(10) { t += CameraStick.TICK_MS; assertEquals(0f, still.idle(t).first) }
        assertEquals(0f to 0f, still.move(0f, 0f, 16))
    }
}
