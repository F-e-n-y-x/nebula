package io.github.f_e_n_y_x.nebula.ui.screens

import io.github.f_e_n_y_x.nebula.controls.OutsideTouch
import io.github.f_e_n_y_x.nebula.domain.MicUi
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.fenyx.nebula.engine.framegen.UpscalerMode

/**
 * The stream menu's quick toggles: one tap for the settings people flip mid-game, at the top of
 * the menu so nothing needs scrolling. Each writes the same key as the detailed control further
 * down (or calls the same live path), so the two always agree.
 */
enum class QuickToggle(val id: String, val label: String) {
    CONTROLS("controls", "Controls"),
    OUTSIDE_TOUCH("outside", "Outside touch"),
    FRAMEGEN("framegen", "Frame gen"),
    UPSCALER("upscaler", "Upscaler"),
    STATS("stats", "Stats"),
    GYRO("gyro", "Gyro"),
    MIC("mic", "Mic"),
    KEYBOARD("keyboard", "Keyboard"),
    PORTRAIT("portrait", "Rotation"),
    HAPTICS("haptics", "Haptics"),
    MOUSE_BAR("mouse_bar", "Mouse bar"),
    LAYOUT("layout", "Layout");

    /** What the long name reads as in the editor and to TalkBack. */
    val longLabel: String get() = when (this) {
        CONTROLS -> "On-screen controls"
        OUTSIDE_TOUCH -> "Touch outside controls"
        FRAMEGEN -> "Frame generation"
        UPSCALER -> "Upscaler"
        STATS -> "Stats overlay"
        GYRO -> "Gyro"
        MIC -> "Microphone"
        KEYBOARD -> "Keyboard"
        PORTRAIT -> "Follow device rotation (portrait streaming)"
        HAPTICS -> "Audio haptics"
        MOUSE_BAR -> "Mouse buttons bar"
        LAYOUT -> "Next layout of the layout set"
    }

    companion object {
        fun of(id: String): QuickToggle? = entries.firstOrNull { it.id == id }
    }
}

/** Which toggles show and in what order, saved as a comma list under [KEY]. */
object QuickToggles {
    const val KEY = "nebula_quick_toggles"
    /** Two rows of four: fits the side panel and the portrait sheet without scrolling. */
    const val MAX = 8
    /** The last non-off gyro mode, so the toggle turns back on to what was used. */
    const val GYRO_LAST_KEY = "nebula_quick_gyro_last"
    /** The last non-off upscaler, likewise. */
    const val UPSCALER_LAST_KEY = "nebula_quick_upscaler_last"

    val DEFAULT: List<QuickToggle> = listOf(
        QuickToggle.CONTROLS, QuickToggle.OUTSIDE_TOUCH, QuickToggle.FRAMEGEN, QuickToggle.UPSCALER,
        QuickToggle.STATS, QuickToggle.GYRO, QuickToggle.MIC, QuickToggle.KEYBOARD,
    )

    /** Never saved: [DEFAULT]. Saved empty: none (the user removed them all). Unknown ids are dropped. */
    fun parse(raw: String?): List<QuickToggle> {
        if (raw == null) return DEFAULT
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull(QuickToggle::of).distinct().take(MAX)
    }

    fun encode(list: List<QuickToggle>): String = list.distinct().take(MAX).joinToString(",") { it.id }

    fun read(all: Map<String, *>): List<QuickToggle> = parse(all[KEY] as? String)

    /** Shows [t] (at the end) or hides it; adding past [MAX] changes nothing. */
    fun toggleShown(list: List<QuickToggle>, t: QuickToggle): List<QuickToggle> = when {
        t in list -> list - t
        list.size >= MAX -> list
        else -> list + t
    }

    /** Moves [t] by [delta] places, clamped to the ends. */
    fun move(list: List<QuickToggle>, t: QuickToggle, delta: Int): List<QuickToggle> {
        val from = list.indexOf(t)
        if (from < 0) return list
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (to == from) return list
        return list.toMutableList().apply { removeAt(from); add(to, t) }
    }

    /** The editor's order: shown ones first (in order), then the rest. */
    fun editorOrder(list: List<QuickToggle>): List<QuickToggle> = list + (QuickToggle.entries - list.toSet())

    /** Mouse → Look → Off → Mouse (Touch, set in the editor, steps to Mouse). */
    fun nextOutside(o: OutsideTouch): OutsideTouch = when (o) {
        OutsideTouch.TRACKPAD -> OutsideTouch.LOOK
        OutsideTouch.LOOK -> OutsideTouch.OFF
        OutsideTouch.OFF, OutsideTouch.TOUCH -> OutsideTouch.TRACKPAD
    }

    /** The gyro mode after a tap: off, or back to the last mode used (right stick if none). */
    fun gyroToggled(current: GyroMode, last: GyroMode?): GyroMode =
        if (current != GyroMode.OFF) GyroMode.OFF else last?.takeIf { it != GyroMode.OFF } ?: GyroMode.RIGHT_STICK

    /** The upscaler after a tap: off, or back to the last one used (FSR 1 if none). */
    fun upscalerToggled(current: UpscalerMode, last: UpscalerMode?): UpscalerMode =
        if (current != UpscalerMode.OFF) UpscalerMode.OFF else last?.takeIf { it != UpscalerMode.OFF } ?: UpscalerMode.FSR1

    /**
     * What an upscaler tap does. [locked]: frame generation owns the screen this stream (the
     * panel's rule). [running]: the upscaler pass is set up for this stream, so any mode applies
     * live; otherwise turning it on waits for the next stream ([LiveSettings.upscaler]).
     */
    fun upscalerTap(current: UpscalerMode, last: UpscalerMode?, locked: Boolean, running: Boolean): UpscalerTap {
        if (locked) return UpscalerTap(null, "The upscaler is off while frame generation is set up for this stream.")
        val next = upscalerToggled(current, last)
        val note = if (next != UpscalerMode.OFF && !running) "${next.label} starts with the next stream." else null
        return UpscalerTap(next, note)
    }

    data class UpscalerTap(val mode: UpscalerMode?, val notice: String?)
}

/** How a tile looks: on/off, the short value under the label, and whether a tap does anything. */
data class QuickTileState(
    val on: Boolean,
    /** Short state under the label ("On", "Look", "Paused"…), also TalkBack's state. */
    val value: String,
    val enabled: Boolean = true,
    /** Why it's greyed, shown when tapped anyway. */
    val reason: String? = null,
    val kind: Kind = Kind.SWITCH,
) {
    /** TalkBack's state: "On" / "Off", with the value when it says more ("On, Look"). */
    val spoken: String get() = when {
        !enabled -> "Unavailable, $value"
        kind == Kind.ACTION -> value
        value == "On" || value == "Off" -> value
        else -> "${if (on) "On" else "Off"}, $value"
    }

    enum class Kind {
        /** On / off. */
        SWITCH,
        /** Steps through several values. */
        CYCLE,
        /** Does something (opens the keyboard); no state. */
        ACTION,
    }

    companion object {
        fun switch(on: Boolean) = QuickTileState(on, if (on) "On" else "Off")

        fun outside(o: OutsideTouch?): QuickTileState =
            if (o == null) QuickTileState(false, "Controls off", enabled = false, reason = "Turn the on-screen controls on first.", kind = Kind.CYCLE)
            else QuickTileState(o != OutsideTouch.OFF, o.label, kind = Kind.CYCLE)

        fun framegen(s: FramegenPanelState): QuickTileState = QuickTileState(s.switchOn, s.label)

        fun upscaler(mode: UpscalerMode, locked: Boolean): QuickTileState = when {
            locked -> QuickTileState(false, "Locked", enabled = false, reason = "The upscaler is off while frame generation is set up for this stream.")
            mode == UpscalerMode.OFF -> QuickTileState(false, "Off")
            else -> QuickTileState(true, upscalerShort(mode))
        }

        fun gyro(mode: GyroMode): QuickTileState = QuickTileState(
            mode != GyroMode.OFF,
            when (mode) {
                GyroMode.OFF -> "Off"
                GyroMode.RIGHT_STICK -> "Stick"
                GyroMode.MOUSE -> "Mouse"
                GyroMode.PASSTHROUGH -> "Passthrough"
            },
        )

        fun mic(ui: MicUi): QuickTileState = when (ui) {
            MicUi.HIDDEN -> QuickTileState(false, "Off", enabled = false, reason = "The microphone is off in Settings › Audio; it applies from the next stream.")
            MicUi.CONNECTING -> QuickTileState(false, "Connecting", enabled = false, reason = "The stream is still connecting.")
            MicUi.UNSUPPORTED -> QuickTileState(false, "No PC mic", enabled = false, reason = "This PC didn't ask for a microphone.")
            MicUi.NEEDS_PERMISSION -> QuickTileState(false, "Allow")
            MicUi.BLOCKED -> QuickTileState(false, "Blocked")
            MicUi.MUTED -> QuickTileState(false, "Muted")
            MicUi.LIVE -> QuickTileState(true, "Live")
            MicUi.PAUSED -> QuickTileState(true, "Paused")
        }

        fun keyboard(kind: KeyboardKind) = QuickTileState(false, if (kind == KeyboardKind.PC) "PC keys" else "Device", kind = Kind.ACTION)

        fun portrait(follow: Boolean?): QuickTileState =
            if (follow == null) QuickTileState(false, "Not here", enabled = false, reason = "Following the device rotation isn't available on this device.")
            else QuickTileState(follow, if (follow) "Follow" else "Fixed")

        /** [current]: the layout on screen when the game's controls are a layout set, else null. */
        fun layout(current: String?): QuickTileState =
            if (current == null) QuickTileState(false, "No set", enabled = false, reason = "This game's controls aren't a layout set. Pick one under Controls below.", kind = Kind.ACTION)
            else QuickTileState(false, current, kind = Kind.ACTION)

        private fun upscalerShort(m: UpscalerMode) = when (m) {
            UpscalerMode.OFF -> "Off"
            UpscalerMode.SHARPEN -> "Sharpen"
            UpscalerMode.SGSR1 -> "SGSR 1"
            UpscalerMode.FSR1 -> "FSR 1"
        }
    }
}
