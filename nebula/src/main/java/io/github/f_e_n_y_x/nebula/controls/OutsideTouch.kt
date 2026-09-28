package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs

/**
 * What a finger does on the stream picture outside the on-screen controls while they're shown,
 * per profile. The default is nothing: with a touch gamepad on screen, stray touches must not
 * click, drag or move the PC's mouse (they fight the camera and fire in shooters).
 */
enum class OutsideTouch(val id: String, val label: String) {
    NOTHING("nothing", "Nothing"),
    /** Settings' touch mode (trackpad, direct pointer, touch or split). */
    TRACKPAD("trackpad", "Touch mode from Settings"),
    /** Real multi-touch on the PC. */
    DIRECT("direct", "Direct touch");

    companion object {
        private const val KEY = "nebula_osc_outside_touch:"
        fun of(id: String?): OutsideTouch = entries.firstOrNull { it.id == id } ?: NOTHING
        fun read(p: LegacyPrefs, profileId: String): OutsideTouch = of(p.prefs.getString(KEY + profileId, null))
        fun write(p: LegacyPrefs, profileId: String, v: OutsideTouch) { p.prefs.edit().putString(KEY + profileId, v.id).apply() }
    }
}
