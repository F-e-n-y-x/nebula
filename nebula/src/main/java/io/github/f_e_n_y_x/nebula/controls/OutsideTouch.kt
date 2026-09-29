package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs

/**
 * What a finger that lands outside every on-screen control does while they're shown, per
 * profile. It never clicks in Look or Off: a stray left click is a shot in a shooter, and it
 * switches the game to keyboard and mouse.
 */
enum class OutsideTouch(val id: String, val label: String, val help: String) {
    /** The right part of the screen ([TouchRouter.LOOK_SPLIT] onwards) looks; the rest is ignored. */
    LOOK("look", "Look", "Swipe on the right of the screen to look around. Never clicks."),
    OFF("nothing", "Off", "Touches outside the controls do nothing."),
    /** Settings' touch mode (trackpad, direct pointer, touch or split), with those fingers only. */
    TRACKPAD("trackpad", "Mouse", "The mouse (Settings' touch mode) for fingers outside the controls only; fingers on the controls never move it."),
    /** Real multi-touch on the PC. */
    TOUCH("direct", "Touch", "Multi-touch on the PC, for fingers outside the controls only.");

    companion object {
        private const val KEY = "nebula_osc_outside_touch:"
        fun of(id: String?): OutsideTouch? = entries.firstOrNull { it.id == id }
        /** The GTA presets look; every other profile keeps the mouse (trackpad) outside the controls. */
        fun default(profileId: String): OutsideTouch =
            if (profileId in LOOK_PRESETS) LOOK else TRACKPAD
        private val LOOK_PRESETS = setOf(
            ControlsProfile.GTA_TOUCH_ID, ControlsProfile.GTA_ID, ControlsProfile.GTA_MOUSE_ID,
            ControlsProfile.SHOOTER_PAD_ID, ControlsProfile.SHOOTER_KBM_ID, ControlsProfile.GTA_PUBG_ID,
        )
        /** The user's choice for this profile, else what the profile (or layout file) says, else [default]. */
        fun resolve(stored: String?, profile: ControlsProfile): OutsideTouch = of(stored) ?: profile.outside ?: default(profile.id)
        fun read(p: LegacyPrefs, profileId: String): OutsideTouch = of(p.prefs.getString(KEY + profileId, null)) ?: default(profileId)
        fun read(p: LegacyPrefs, profile: ControlsProfile): OutsideTouch = resolve(p.prefs.getString(KEY + profile.id, null), profile)
        fun write(p: LegacyPrefs, profileId: String, v: OutsideTouch) { p.prefs.edit().putString(KEY + profileId, v.id).apply() }
    }
}

/** How a look finger (the Look background, or a fire-and-look button) turns the camera. */
enum class LookOutput(val id: String, val label: String, val help: String) {
    MOUSE("mouse", "Mouse", "1:1 mouse look. Best where the game takes mouse look alongside a pad (GTA V on PC)."),
    STICK("stick", "Right stick", "Swipe-to-stick for games that only read the pad; limited to the game's turn speed.");

    companion object {
        private const val KEY = "nebula_osc_look_output:"
        /** The GTA V touch preset looks with the mouse; everything else with the right stick (works in any game). */
        fun default(profileId: String): LookOutput =
            if (profileId == ControlsProfile.GTA_TOUCH_ID || profileId == ControlsProfile.GTA_PUBG_ID || profileId == ControlsProfile.SHOOTER_KBM_ID) MOUSE else STICK
        fun of(id: String?): LookOutput? = entries.firstOrNull { it.id == id }
        fun resolve(stored: String?, profile: ControlsProfile): LookOutput = of(stored) ?: profile.look ?: default(profile.id)
        fun read(p: LegacyPrefs, profileId: String): LookOutput = of(p.prefs.getString(KEY + profileId, null)) ?: default(profileId)
        fun read(p: LegacyPrefs, profile: ControlsProfile): LookOutput = resolve(p.prefs.getString(KEY + profile.id, null), profile)
        fun write(p: LegacyPrefs, profileId: String, v: LookOutput) { p.prefs.edit().putString(KEY + profileId, v.id).apply() }
    }
}
