package io.github.f_e_n_y_x.nebula.controls

/**
 * Windows virtual-key names for the key picker, and V+ Crown's Android key codes (`k29` = A)
 * translated to virtual keys the same way the engine's KeyboardTranslator does.
 */
object VirtualKeys {
    private val named: Map<Int, String> = buildMap {
        for (c in 'A'..'Z') put(c.code, c.toString())
        for (d in 0..9) put(0x30 + d, d.toString())
        for (f in 1..12) put(0x6F + f, "F$f")
        for (n in 0..9) put(0x60 + n, "Num $n")
        put(0x08, "Backspace"); put(0x09, "Tab"); put(0x0D, "Enter"); put(0x1B, "Esc"); put(0x20, "Space")
        put(0x10, "Shift"); put(0x11, "Ctrl"); put(0x12, "Alt")
        put(0xA0, "Left Shift"); put(0xA1, "Right Shift"); put(0xA2, "Left Ctrl"); put(0xA3, "Right Ctrl")
        put(0xA4, "Left Alt"); put(0xA5, "Right Alt"); put(0x5B, "Win"); put(0x5C, "Right Win"); put(0x5D, "Menu")
        put(0x14, "Caps Lock"); put(0x90, "Num Lock"); put(0x91, "Scroll Lock"); put(0x13, "Pause"); put(0x2C, "Print Screen")
        put(0x25, "Left"); put(0x26, "Up"); put(0x27, "Right"); put(0x28, "Down")
        put(0x21, "Page Up"); put(0x22, "Page Down"); put(0x23, "End"); put(0x24, "Home"); put(0x2D, "Insert"); put(0x2E, "Delete")
        put(0xBA, ";"); put(0xBB, "="); put(0xBC, ","); put(0xBD, "-"); put(0xBE, "."); put(0xBF, "/"); put(0xC0, "`")
        put(0xDB, "["); put(0xDC, "\\"); put(0xDD, "]"); put(0xDE, "'")
        put(0x6A, "Num *"); put(0x6B, "Num +"); put(0x6D, "Num -"); put(0x6E, "Num ."); put(0x6F, "Num /")
        put(0x0C, "Clear")
        put(0xAD, "Mute"); put(0xAE, "Volume down"); put(0xAF, "Volume up")
        put(0xB0, "Next track"); put(0xB1, "Previous track"); put(0xB2, "Stop"); put(0xB3, "Play / pause")
    }

    fun name(vk: Int): String = named[vk] ?: "Key 0x${vk.toString(16).uppercase()}"

    /** Keys offered by the picker, grouped for display. */
    val pickerGroups: List<Pair<String, List<Int>>> = listOf(
        "Letters" to ('A'..'Z').map { it.code },
        "Numbers" to (0..9).map { 0x30 + it },
        "Common" to listOf(0x20, 0x0D, 0x1B, 0x09, 0x08, 0xA0, 0xA2, 0xA4, 0x5B, 0x14),
        "Arrows & navigation" to listOf(0x26, 0x28, 0x25, 0x27, 0x24, 0x23, 0x21, 0x22, 0x2D, 0x2E),
        "Function" to (1..12).map { 0x6F + it },
        "Symbols" to listOf(0xC0, 0xBD, 0xBB, 0xDB, 0xDD, 0xDC, 0xBA, 0xDE, 0xBC, 0xBE, 0xBF),
        "Media" to listOf(0xB3, 0xB1, 0xB0, 0xB2, 0xAE, 0xAF, 0xAD),
    )

    /** Android KeyEvent code → Windows VK, or null when there's no PC equivalent. */
    fun fromAndroidKeyCode(code: Int): Int? = when (code) {
        in 7..16 -> 0x30 + (code - 7) // KEYCODE_0..9
        in 29..54 -> 0x41 + (code - 29) // KEYCODE_A..Z
        in 144..153 -> 0x60 + (code - 144) // KEYCODE_NUMPAD_0..9
        in 131..142 -> 0x70 + (code - 131) // KEYCODE_F1..F12
        57 -> 0xA4; 58 -> 0xA5 // ALT_LEFT/RIGHT
        73 -> 0xDC // BACKSLASH
        115 -> 0x14 // CAPS_LOCK
        28 -> 0x0C // CLEAR
        55 -> 0xBC // COMMA
        113 -> 0xA2; 114 -> 0xA3 // CTRL_LEFT/RIGHT
        67 -> 0x08 // DEL (backspace)
        66 -> 0x0D // ENTER
        81, 70 -> 0xBB // PLUS, EQUALS
        111 -> 0x1B // ESCAPE
        112 -> 0x2E // FORWARD_DEL
        124 -> 0x2D // INSERT
        71 -> 0xDB // LEFT_BRACKET
        117 -> 0x5B; 118 -> 0x5C // META_LEFT/RIGHT
        82 -> 0x5D // MENU
        69 -> 0xBD // MINUS
        123 -> 0x23; 122 -> 0x24 // MOVE_END, MOVE_HOME
        143 -> 0x90 // NUM_LOCK
        93 -> 0x22; 92 -> 0x21 // PAGE_DOWN, PAGE_UP
        56 -> 0xBE // PERIOD
        72 -> 0xDD // RIGHT_BRACKET
        116 -> 0x91 // SCROLL_LOCK
        74 -> 0xBA // SEMICOLON
        59 -> 0xA0; 60 -> 0xA1 // SHIFT_LEFT/RIGHT
        76 -> 0xBF // SLASH
        62 -> 0x20 // SPACE
        120 -> 0x2C // SYSRQ
        61 -> 0x09 // TAB
        21 -> 0x25; 22 -> 0x27; 19 -> 0x26; 20 -> 0x28 // DPAD left/right/up/down
        68 -> 0xC0 // GRAVE
        75 -> 0xDE // APOSTROPHE
        121 -> 0x13 // BREAK
        154 -> 0x6F; 155 -> 0x6A; 156 -> 0x6D; 157 -> 0x6B; 158 -> 0x6E // numpad operators
        else -> null
    }
}
