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

}
