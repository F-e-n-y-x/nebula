package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData

/**
 * Nebula 0.4 bundles only the Standard controller; the touch-shooter templates and the GTA V
 * layouts moved to the layout library (Browse layouts). Someone who picked one of those
 * read-only presets as their default or for a game keeps it: on the first start of 0.4 the
 * preset becomes an ordinary profile of theirs (with the same controls, touch settings and
 * name), and the choice moves onto it. Profiles already saved from a preset were copies and are
 * untouched.
 */
object RetiredPresets {
    /** The presets 0.3 offered, by id (the definitions stay so old choices can be kept). */
    fun all(): List<ControlsProfile> = DefaultProfiles.retired()

    /** The profile id a retired preset becomes: `p-<preset name part>`. */
    fun newIdFor(presetId: String): String = "p-" + presetId.removePrefix(ControlsProfile.BUILTIN_PREFIX)

    /** An editable copy of the retired preset [presetId], or null if it isn't one. */
    fun copyOf(presetId: String, now: Long = 0L): ControlsProfile? {
        val id = ControlsProfile.currentId(presetId)
        val p = all().firstOrNull { it.id == id } ?: return null
        return p.copy(
            id = newIdFor(id), origin = "library", createdAtMs = now, updatedAtMs = now,
            // Its defaults went by the preset id; the copy states them.
            outside = p.outside ?: OutsideTouch.default(id), look = p.look ?: LookOutput.default(id),
        )
    }

    /**
     * [d] with every retired preset it still names (the default, per-game choices, set members)
     * turned into a profile, plus old id → new id for what moved (per-profile settings follow).
     */
    fun adopt(d: StoreData, now: Long = 0L): Pair<StoreData, Map<String, String>> {
        val retired = all().map { it.id }.toSet()
        val named = (listOf(d.activeProfileId) + d.games.values + d.sets.flatMap { it.members }).map(ControlsProfile::currentId).filter { it in retired }.toSet()
        if (named.isEmpty()) return d to emptyMap()
        val moved = named.associateWith(::newIdFor)
        val have = d.profiles.map { it.id }.toSet()
        val added = named.filter { moved.getValue(it) !in have }.mapNotNull { copyOf(it, now) }
        fun map(id: String) = moved[ControlsProfile.currentId(id)] ?: id
        return d.copy(
            profiles = d.profiles + added,
            activeProfileId = map(d.activeProfileId),
            games = d.games.mapValues { map(it.value) },
            sets = d.sets.map { s -> s.copy(members = s.members.map(::map), cycle = s.cycle?.map(::map), start = s.start?.let(::map)) },
        ) to moved
    }
}
