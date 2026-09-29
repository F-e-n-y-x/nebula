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
        internal const val KEY = "nebula_osc_outside_touch:"
        fun of(id: String?): OutsideTouch? = entries.firstOrNull { it.id == id }
        /** The touch-shooter and GTA presets look; every other profile keeps the mouse (trackpad) outside the controls. */
        fun default(profileId: String): OutsideTouch =
            if (profileId in LOOK_PRESETS) LOOK else TRACKPAD
        private val LOOK_PRESETS = setOf(
            ControlsProfile.GTA_TOUCH_ID, ControlsProfile.GTA_ID, ControlsProfile.GTA_MOUSE_ID,
            ControlsProfile.TOUCH_SHOOTER_PAD_ID, ControlsProfile.TOUCH_SHOOTER_KBM_ID, ControlsProfile.GTA_TOUCH_CONTROLS_ID,
        )
        /** The user's choice for this profile, else what the profile (or layout file) says, else [default]. */
        fun resolve(stored: String?, profile: ControlsProfile): OutsideTouch = of(stored) ?: profile.outside ?: default(profile.id)
        fun read(p: LegacyPrefs, profileId: String): OutsideTouch = of(p.prefs.getString(KEY + profileId, null)) ?: default(profileId)
        fun read(p: LegacyPrefs, profile: ControlsProfile): OutsideTouch = resolve(p.prefs.getString(KEY + profile.id, null), profile)
        fun write(p: LegacyPrefs, profileId: String, v: OutsideTouch) { p.prefs.edit().putString(KEY + profileId, v.id).apply() }
        fun clear(p: LegacyPrefs, profileId: String) { p.prefs.edit().remove(KEY + profileId).apply() }
    }
}

/** How a look finger (the Look background, or a fire-and-look button) turns the camera. */
enum class LookOutput(val id: String, val label: String, val help: String) {
    MOUSE("mouse", "Mouse", "1:1 mouse look. Best where the game takes mouse look alongside a pad (GTA V on PC)."),
    STICK("stick", "Right stick", "Swipe-to-stick for games that only read the pad; limited to the game's turn speed.");

    companion object {
        internal const val KEY = "nebula_osc_look_output:"
        /** The GTA V touch presets and the keyboard & mouse shooter look with the mouse; everything else with the right stick (works in any game). */
        fun default(profileId: String): LookOutput =
            if (profileId in MOUSE_PRESETS) MOUSE else STICK
        private val MOUSE_PRESETS = setOf(ControlsProfile.GTA_TOUCH_ID, ControlsProfile.GTA_TOUCH_CONTROLS_ID, ControlsProfile.TOUCH_SHOOTER_KBM_ID)
        fun of(id: String?): LookOutput? = entries.firstOrNull { it.id == id }
        fun resolve(stored: String?, profile: ControlsProfile): LookOutput = of(stored) ?: profile.look ?: default(profile.id)
        fun read(p: LegacyPrefs, profileId: String): LookOutput = of(p.prefs.getString(KEY + profileId, null)) ?: default(profileId)
        fun read(p: LegacyPrefs, profile: ControlsProfile): LookOutput = resolve(p.prefs.getString(KEY + profile.id, null), profile)
        fun write(p: LegacyPrefs, profileId: String, v: LookOutput) { p.prefs.edit().putString(KEY + profileId, v.id).apply() }
        fun clear(p: LegacyPrefs, profileId: String) { p.prefs.edit().remove(KEY + profileId).apply() }
    }
}

/**
 * Per-profile settings ([OutsideTouch], [LookOutput]) are saved under the profile's id; when a
 * built-in id is renamed ([ControlsProfile.RENAMED_IDS]) they move to the new id once. A value
 * already saved under the new id wins.
 */
object ProfilePrefsMigration {
    private val PREFIXES = listOf(OutsideTouch.KEY, LookOutput.KEY)

    /** Old key to new key for every saved per-profile setting that names a renamed id. */
    fun moves(keys: Set<String>): Map<String, String> = buildMap {
        for (prefix in PREFIXES) for ((old, new) in ControlsProfile.RENAMED_IDS) {
            val from = prefix + old
            if (from in keys) put(from, prefix + new)
        }
    }

    fun apply(p: android.content.SharedPreferences) {
        val all = p.all
        val moves = moves(all.keys)
        if (moves.isEmpty()) return
        val e = p.edit()
        for ((from, to) in moves) {
            val v = all[from]
            if (to !in all && v is String) e.putString(to, v)
            e.remove(from)
        }
        e.apply()
    }
}
