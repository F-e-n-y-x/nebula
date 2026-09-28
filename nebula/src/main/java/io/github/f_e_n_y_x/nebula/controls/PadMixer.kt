package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton

/**
 * Sits between the physical-controller mapper and the PC so touch zones can steer a stick of the
 * controller the player is holding: GTA V style, the pad moves and shoots while a thumb on the
 * screen aims. The touch sticks are added to the first physical pad's sticks (clamped), and
 * re-sent whenever either side changes. Everything else passes straight through.
 */
class PadMixer(private val out: () -> RemoteInput?) : RemoteInput {
    private class State(val mask: Int, val buttons: Int, val lt: Int, val rt: Int, val lx: Int, val ly: Int, val rx: Int, val ry: Int)

    private val pads = sortedMapOf<Int, State>()
    private var tlx = 0
    private var tly = 0
    private var trx = 0
    private var try_ = 0

    /** A physical pad has reported at least once. */
    val hasController: Boolean get() = pads.isNotEmpty()

    /** Touch stick values (−32767..32767, y up) to mix into the physical pad. */
    fun touchStick(side: Side, x: Int, y: Int) {
        if (side == Side.LEFT) { if (tlx == x && tly == y) return; tlx = x; tly = y } else { if (trx == x && try_ == y) return; trx = x; try_ = y }
        if (pads.isEmpty()) return
        send(pads.firstKey())
    }

    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        pads[controller] = State(activeMask, buttons, lt, rt, lx, ly, rx, ry)
        send(controller)
    }

    private fun send(controller: Int) {
        val s = pads[controller] ?: return
        val mix = controller == pads.firstKey()
        out()?.gamepad(
            controller, s.mask, s.buttons, s.lt, s.rt,
            if (mix) clamp(s.lx + tlx) else s.lx, if (mix) clamp(s.ly + tly) else s.ly,
            if (mix) clamp(s.rx + trx) else s.rx, if (mix) clamp(s.ry + try_) else s.ry,
        )
    }

    private fun clamp(v: Int) = v.coerceIn(-32767, 32767)

    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) {
        out()?.gamepadArrived(controller, activeMask, type, supportedButtons, capabilities)
    }

    override fun key(event: KeyEvent): Boolean = out()?.key(event) == true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { out()?.virtualKey(vk, down, modifiers) }
    override fun text(text: String) { out()?.text(text) }
    override fun move(dx: Int, dy: Int) { out()?.move(dx, dy) }
    override fun position(x: Int, y: Int, refW: Int, refH: Int) { out()?.position(x, y, refW, refH) }
    override fun button(button: MouseButton, down: Boolean) { out()?.button(button, down) }
    override fun scroll(amount: Int) { out()?.scroll(amount) }
    override fun scrollHorizontal(amount: Int) { out()?.scrollHorizontal(amount) }
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float): Boolean = out()?.touch(type, pointerId, x, y) == true
}
