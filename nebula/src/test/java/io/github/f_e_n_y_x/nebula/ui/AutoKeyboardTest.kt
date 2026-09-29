package io.github.f_e_n_y_x.nebula.ui

import io.github.f_e_n_y_x.nebula.ui.screens.AutoKeyboard
import io.github.f_e_n_y_x.nebula.ui.screens.AutoKeyboard.Action
import io.github.f_e_n_y_x.nebula.ui.screens.QuickTileState
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggle
import io.github.f_e_n_y_x.nebula.ui.screens.QuickToggles
import io.github.f_e_n_y_x.nebula.ui.screens.TextFieldKeyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoKeyboardTest {
    @Test
    fun `opens the chosen keyboard`() {
        assertEquals(Action.OPEN_DEVICE, AutoKeyboard.onFocus(TextFieldKeyboard.DEVICE, overlaysOn = true, oscShown = false, keyboardOpen = false))
        assertEquals(Action.OPEN_PC, AutoKeyboard.onFocus(TextFieldKeyboard.PC, overlaysOn = true, oscShown = false, keyboardOpen = false))
    }

    @Test
    fun `off, menu open or a keyboard already up change nothing`() {
        assertEquals(Action.NONE, AutoKeyboard.onFocus(TextFieldKeyboard.OFF, true, false, false))
        assertEquals(Action.NONE, AutoKeyboard.onFocus(TextFieldKeyboard.DEVICE, overlaysOn = false, oscShown = false, keyboardOpen = false))
        assertEquals(Action.NONE, AutoKeyboard.onFocus(TextFieldKeyboard.PC, overlaysOn = true, oscShown = false, keyboardOpen = true))
        assertEquals(Action.NONE, AutoKeyboard.onFocus(TextFieldKeyboard.OFF, true, oscShown = true, keyboardOpen = false))
    }

    @Test
    fun `never pops up over on-screen controls, offers a chip instead`() {
        assertEquals(Action.OFFER, AutoKeyboard.onFocus(TextFieldKeyboard.DEVICE, true, oscShown = true, keyboardOpen = false))
        assertEquals(Action.OFFER, AutoKeyboard.onFocus(TextFieldKeyboard.PC, true, oscShown = true, keyboardOpen = false))
    }

    @Test
    fun `setting reads with device keyboard as default and cycles to off`() {
        assertEquals(TextFieldKeyboard.DEVICE, TextFieldKeyboard.of(null))
        assertEquals(TextFieldKeyboard.DEVICE, TextFieldKeyboard.of("junk"))
        assertEquals(TextFieldKeyboard.OFF, TextFieldKeyboard.of("off"))
        assertEquals(TextFieldKeyboard.PC, TextFieldKeyboard.of("pc"))
        var k = TextFieldKeyboard.DEVICE
        val seen = mutableListOf<TextFieldKeyboard>()
        repeat(3) { k = TextFieldKeyboard.next(k); seen += k }
        assertEquals(listOf(TextFieldKeyboard.PC, TextFieldKeyboard.OFF, TextFieldKeyboard.DEVICE), seen)
    }

    @Test
    fun `lift keeps the focused line above the keyboard, never more than the keyboard`() {
        // Picture 0..1000 tall, keyboard covers 600..1000, field at 80 % = 800.
        val lift = AutoKeyboard.liftFor(0.8f, 0f, 1000f, visibleBottom = 600f, containerHeight = 1000f, margin = 20f)
        assertEquals(-220f, lift, 0.01f)
        assertEquals(0f, AutoKeyboard.liftFor(0.2f, 0f, 1000f, 600f, 1000f, 20f), 0.01f)
        assertEquals(0f, AutoKeyboard.liftFor(null, 0f, 1000f, 600f, 1000f, 20f), 0.01f)
        assertEquals(0f, AutoKeyboard.liftFor(0.9f, 0f, 1000f, 1000f, 1000f, 20f), 0.01f)
        assertEquals(-400f, AutoKeyboard.liftFor(1f, 200f, 1000f, 600f, 1000f, 20f), 0.01f)
    }

    @Test
    fun `quick toggle shows the choice and greys out without host support`() {
        assertTrue(QuickToggle.AUTO_KEYBOARD !in QuickToggles.DEFAULT)
        val on = QuickTileState.autoKeyboard(TextFieldKeyboard.PC, hostReports = true)
        assertTrue(on.on && on.enabled)
        assertEquals("PC keys", on.value)
        assertFalse(QuickTileState.autoKeyboard(TextFieldKeyboard.OFF, true).on)
        assertFalse(QuickTileState.autoKeyboard(TextFieldKeyboard.DEVICE, hostReports = false).enabled)
    }
}
