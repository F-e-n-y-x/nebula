package io.github.f_e_n_y_x.nebula.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A key on the on-screen PC keyboard: Windows virtual-key [vk], [label], optional [shifted] label, width in key units. */
data class PcKey(val vk: Int, val label: String, val shifted: String? = null, val width: Float = 1f) {
    val isModifier get() = vk in MODIFIER_VKS
}

/** Sticky modifier state, as in V+: tap = next key only, tap again = locked, tap a third time = off. */
enum class ModState { OFF, ONCE, LOCKED }

/** V+'s keyboard pages. */
enum class KeyboardPage(val label: String) { FULL("Keys"), NAV("Nav"), NUM("Numpad"), MINI("Mini") }

/**
 * The PC keyboard's behaviour, without UI: modifiers are real key presses on the PC (so Shift+click
 * works too), a one-shot modifier is released after the next key, a locked one stays down.
 */
class PcKeyboardModel(private val out: () -> RemoteInput?) {
    private val _mods = MutableStateFlow(MODIFIER_VKS.associateWith { ModState.OFF })
    val mods: StateFlow<Map<Int, ModState>> = _mods.asStateFlow()

    val shiftActive: Boolean get() = _mods.value[VK_LSHIFT] != ModState.OFF || _mods.value[VK_RSHIFT] != ModState.OFF

    /** Modifier bits for keys sent now. */
    private fun modifierBits(): Int = _mods.value.entries.filter { it.value != ModState.OFF }.fold(0) { m, (vk, _) -> m or bitFor(vk) }

    fun tapModifier(vk: Int) {
        val o = out()
        val next = when (_mods.value[vk] ?: ModState.OFF) {
            ModState.OFF -> ModState.ONCE
            ModState.ONCE -> ModState.LOCKED
            ModState.LOCKED -> ModState.OFF
        }
        if (next == ModState.ONCE) {
            o?.virtualKey(vk, true, modifierBits() or bitFor(vk))
        }
        if (next == ModState.OFF) {
            set(vk, next)
            o?.virtualKey(vk, false, modifierBits())
            return
        }
        set(vk, next)
    }

    /** A normal key went down (pressed on screen); held until [keyUp]. */
    fun keyDown(vk: Int) {
        out()?.virtualKey(vk, true, modifierBits())
    }

    /** A normal key was released: one-shot modifiers are released with it. */
    fun keyUp(vk: Int) {
        val o = out()
        o?.virtualKey(vk, false, modifierBits())
        _mods.value.filterValues { it == ModState.ONCE }.keys.forEach { m ->
            set(m, ModState.OFF)
            o?.virtualKey(m, false, modifierBits())
        }
    }

    fun tap(vk: Int) {
        keyDown(vk)
        keyUp(vk)
    }

    /** Releases everything held, e.g. when the keyboard closes. */
    fun releaseAll() {
        val o = out()
        _mods.value.filterValues { it != ModState.OFF }.keys.forEach { m ->
            set(m, ModState.OFF)
            o?.virtualKey(m, false, modifierBits())
        }
    }

    private fun set(vk: Int, s: ModState) {
        _mods.value = _mods.value + (vk to s)
    }

    private fun bitFor(vk: Int) = when (vk) {
        VK_LSHIFT, VK_RSHIFT -> Modifier.SHIFT
        VK_LCTRL, VK_RCTRL -> Modifier.CTRL
        VK_LALT, VK_RALT -> Modifier.ALT
        VK_LWIN, VK_RWIN -> Modifier.META
        else -> 0
    }
}

const val VK_LSHIFT = 0xA0
const val VK_RSHIFT = 0xA1
const val VK_LCTRL = 0xA2
const val VK_RCTRL = 0xA3
const val VK_LALT = 0xA4
const val VK_RALT = 0xA5
const val VK_LWIN = 0x5B
const val VK_RWIN = 0x5C
val MODIFIER_VKS = setOf(VK_LSHIFT, VK_RSHIFT, VK_LCTRL, VK_RCTRL, VK_LALT, VK_RALT, VK_LWIN, VK_RWIN)

private fun letters(s: String) = s.map { PcKey(it.code, it.toString()) }
private fun f(n: Int) = PcKey(0x6F + n, "F$n")

/** Key rows for each page, a compact PC layout (US labels; the PC applies its own layout). */
object PcLayouts {
    val full: List<List<PcKey>> = listOf(
        listOf(PcKey(0x1B, "Esc")) + (1..12).map(::f) + listOf(PcKey(0x2E, "Del")),
        listOf(
            PcKey(0xC0, "`", "~"), PcKey(0x31, "1", "!"), PcKey(0x32, "2", "@"), PcKey(0x33, "3", "#"), PcKey(0x34, "4", "$"),
            PcKey(0x35, "5", "%"), PcKey(0x36, "6", "^"), PcKey(0x37, "7", "&"), PcKey(0x38, "8", "*"), PcKey(0x39, "9", "("),
            PcKey(0x30, "0", ")"), PcKey(0xBD, "-", "_"), PcKey(0xBB, "=", "+"), PcKey(0x08, "⌫", width = 1.6f),
        ),
        listOf(PcKey(0x09, "Tab", width = 1.4f)) + letters("QWERTYUIOP") +
            listOf(PcKey(0xDB, "[", "{"), PcKey(0xDD, "]", "}"), PcKey(0xDC, "\\", "|", width = 1.2f)),
        listOf(PcKey(0x14, "Caps", width = 1.7f)) + letters("ASDFGHJKL") +
            listOf(PcKey(0xBA, ";", ":"), PcKey(0xDE, "'", "\""), PcKey(0x0D, "Enter", width = 1.9f)),
        listOf(PcKey(VK_LSHIFT, "Shift", width = 2.2f)) + letters("ZXCVBNM") +
            listOf(PcKey(0xBC, ",", "<"), PcKey(0xBE, ".", ">"), PcKey(0xBF, "/", "?"), PcKey(0x26, "↑"), PcKey(VK_RSHIFT, "Shift", width = 1.4f)),
        listOf(
            PcKey(VK_LCTRL, "Ctrl", width = 1.3f), PcKey(VK_LWIN, "Win", width = 1.2f), PcKey(VK_LALT, "Alt", width = 1.2f),
            PcKey(0x20, "Space", width = 5.4f), PcKey(VK_RALT, "Alt", width = 1.2f), PcKey(VK_RCTRL, "Ctrl", width = 1.2f),
            PcKey(0x25, "←"), PcKey(0x28, "↓"), PcKey(0x27, "→"),
        ),
    )

    val nav: List<List<PcKey>> = listOf(
        listOf(PcKey(0x2C, "PrtSc"), PcKey(0x91, "ScrLk"), PcKey(0x13, "Pause"), PcKey(0x5D, "Menu")),
        listOf(PcKey(0x2D, "Ins"), PcKey(0x24, "Home"), PcKey(0x21, "PgUp"), PcKey(0x1B, "Esc")),
        listOf(PcKey(0x2E, "Del"), PcKey(0x23, "End"), PcKey(0x22, "PgDn"), PcKey(0x09, "Tab")),
        listOf(PcKey(VK_LCTRL, "Ctrl"), PcKey(0x26, "↑"), PcKey(VK_LALT, "Alt"), PcKey(0x0D, "Enter")),
        listOf(PcKey(0x25, "←"), PcKey(0x28, "↓"), PcKey(0x27, "→"), PcKey(VK_LSHIFT, "Shift")),
    )

    val num: List<List<PcKey>> = listOf(
        listOf(PcKey(0x90, "Num"), PcKey(0x6F, "/"), PcKey(0x6A, "*"), PcKey(0x6D, "−")),
        listOf(PcKey(0x67, "7"), PcKey(0x68, "8"), PcKey(0x69, "9"), PcKey(0x6B, "+")),
        listOf(PcKey(0x64, "4"), PcKey(0x65, "5"), PcKey(0x66, "6"), PcKey(0x08, "⌫")),
        listOf(PcKey(0x61, "1"), PcKey(0x62, "2"), PcKey(0x63, "3"), PcKey(0x0D, "Enter")),
        listOf(PcKey(0x60, "0", width = 2f), PcKey(0x6E, "."), PcKey(0x09, "Tab")),
    )

    /** One strip: the keys games and shortcuts need most. */
    val mini: List<List<PcKey>> = listOf(
        listOf(
            PcKey(0x1B, "Esc"), PcKey(0x09, "Tab"), PcKey(VK_LCTRL, "Ctrl"), PcKey(VK_LSHIFT, "Shift"), PcKey(VK_LALT, "Alt"),
            PcKey(VK_LWIN, "Win"), PcKey(0x25, "←"), PcKey(0x26, "↑"), PcKey(0x28, "↓"), PcKey(0x27, "→"),
            PcKey(0x20, "Space", width = 1.6f), PcKey(0x08, "⌫"), PcKey(0x0D, "Enter", width = 1.4f),
        ),
    )

    fun rows(page: KeyboardPage) = when (page) {
        KeyboardPage.FULL -> full
        KeyboardPage.NAV -> nav
        KeyboardPage.NUM -> num
        KeyboardPage.MINI -> mini
    }
}
