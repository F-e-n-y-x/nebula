package io.github.f_e_n_y_x.nebula.ui.screens

/**
 * What opens when a text field on the PC takes focus after a tap or click from this device (the
 * PC tells Nebula; Nova 0.3+). Saved under [KEY]; the default is this device's own keyboard.
 */
enum class TextFieldKeyboard(val id: String, val label: String, val short: String) {
    DEVICE("phone", "Device keyboard", "Device"),
    PC("pc", "PC keyboard", "PC keys"),
    OFF("off", "Off", "Off");

    companion object {
        const val KEY = "nebula_text_field_keyboard"

        fun of(raw: Any?): TextFieldKeyboard = entries.firstOrNull { it.id == raw } ?: DEVICE

        /** The quick toggle's step: Device → PC keyboard → Off → Device. */
        fun next(k: TextFieldKeyboard): TextFieldKeyboard = when (k) {
            DEVICE -> PC
            PC -> OFF
            OFF -> DEVICE
        }
    }
}

/**
 * The rules for opening and closing a keyboard on PC text field focus, kept apart from the screen
 * so they can be tested.
 */
object AutoKeyboard {
    enum class Action {
        /** Leave everything as it is. */
        NONE,
        /** Open this device's keyboard (Android keeps it hidden while a physical keyboard is in use, unless set otherwise). */
        OPEN_DEVICE,
        /** Open the on-screen PC keyboard. */
        OPEN_PC,
        /** Don't cover the on-screen controls: offer a small "type" chip instead. */
        OFFER,
    }

    /**
     * A field took focus.
     *
     * @param setting What the user chose in Settings (or the quick toggle).
     * @param overlaysOn The stream shows and takes input (no menu, no controls editor).
     * @param oscShown On-screen controls are up (a game): never pop a keyboard over them.
     * @param keyboardOpen A keyboard is already open (the user's own); leave it alone.
     */
    fun onFocus(setting: TextFieldKeyboard, overlaysOn: Boolean, oscShown: Boolean, keyboardOpen: Boolean): Action = when {
        setting == TextFieldKeyboard.OFF || !overlaysOn || keyboardOpen -> Action.NONE
        oscShown -> Action.OFFER
        setting == TextFieldKeyboard.PC -> Action.OPEN_PC
        else -> Action.OPEN_DEVICE
    }

    /**
     * How far (pixels, zero or negative) to move the picture up so the focused line stays above
     * this device's keyboard: [focusY] is the caret's share of the video height, [top] and
     * [height] the picture on screen, [visibleBottom] the keyboard's top edge. Never more than
     * the keyboard's own height ([containerHeight] − [visibleBottom]).
     */
    fun liftFor(focusY: Float?, top: Float, height: Float, visibleBottom: Float, containerHeight: Float, margin: Float): Float {
        if (focusY == null || height <= 0f || visibleBottom >= containerHeight) return 0f
        val y = top + focusY.coerceIn(0f, 1f) * height
        val lift = visibleBottom - margin - y
        return lift.coerceIn(-(containerHeight - visibleBottom), 0f)
    }
}
