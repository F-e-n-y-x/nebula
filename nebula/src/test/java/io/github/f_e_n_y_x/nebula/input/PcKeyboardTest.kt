package io.github.f_e_n_y_x.nebula.input

import android.view.KeyEvent
import io.github.fenyx.nebula.engine.MouseButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class KeyLog : RemoteInput {
    val keys = mutableListOf<String>()
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { keys += "${vk.toString(16)}${if (down) "↓" else "↑"}$modifiers" }
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) = Unit
    override fun scroll(amount: Int) = Unit
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = true
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) = Unit
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

class PcKeyboardTest {
    private val log = KeyLog()
    private val kb = PcKeyboardModel { log }

    @Test
    fun `plain key goes down then up without modifiers`() {
        kb.tap(0x41)
        assertEquals(listOf("41↓0", "41↑0"), log.keys)
    }

    @Test
    fun `one-shot ctrl applies to the next key only`() {
        kb.tapModifier(VK_LCTRL)
        kb.tap(0x43) // C
        kb.tap(0x56) // V, no longer with Ctrl
        assertEquals(listOf("a2↓2", "43↓2", "43↑2", "a2↑0", "56↓0", "56↑0"), log.keys)
        assertEquals(ModState.OFF, kb.mods.value[VK_LCTRL])
    }

    @Test
    fun `second tap locks, third releases`() {
        kb.tapModifier(VK_LSHIFT)
        kb.tapModifier(VK_LSHIFT)
        assertEquals(ModState.LOCKED, kb.mods.value[VK_LSHIFT])
        kb.tap(0x41)
        kb.tap(0x42)
        assertTrue(kb.shiftActive)
        kb.tapModifier(VK_LSHIFT)
        assertEquals(listOf("a0↓1", "41↓1", "41↑1", "42↓1", "42↑1", "a0↑0"), log.keys)
    }

    @Test
    fun `ctrl alt del chord with two one-shot modifiers`() {
        kb.tapModifier(VK_LCTRL)
        kb.tapModifier(VK_LALT)
        kb.tap(0x2E)
        assertEquals("a2↓2", log.keys[0])
        assertEquals("a4↓6", log.keys[1])
        assertEquals("2e↓6", log.keys[2])
        assertTrue(log.keys.containsAll(listOf("a2↑4", "a4↑0")) || log.keys.containsAll(listOf("a4↑2", "a2↑0")))
        assertTrue(kb.mods.value.values.all { it == ModState.OFF })
    }

    @Test
    fun `closing releases held modifiers`() {
        kb.tapModifier(VK_LWIN)
        kb.tapModifier(VK_LWIN)
        kb.releaseAll()
        assertEquals("5b↑0", log.keys.last())
    }

    @Test
    fun `every page has keys with real virtual-key codes`() {
        KeyboardPage.entries.forEach { p ->
            val keys = PcLayouts.rows(p).flatten()
            assertTrue(p.name, keys.isNotEmpty())
            assertTrue(p.name, keys.all { it.vk in 0x01..0xFE && it.width > 0f })
        }
        val full = PcLayouts.full.flatten().map { it.vk }.toSet()
        // Letters, digits, F1–F12, Esc, Tab, Enter, Space, arrows and all four modifiers.
        assertTrue(((0x41..0x5A) + (0x30..0x39) + (0x70..0x7B) + listOf(0x1B, 0x09, 0x0D, 0x20, 0x25, 0x26, 0x27, 0x28, VK_LCTRL, VK_LSHIFT, VK_LALT, VK_LWIN)).all { it in full })
    }
}
