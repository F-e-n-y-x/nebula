package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData

/**
 * Profile bookkeeping over [StoreData], as pure functions: save, rename, duplicate, delete,
 * per-game assignment and "which profile does this game use". The built-in Standard profile is
 * never stored; it's rebuilt from Settings each time ([builtIn]).
 *
 * Per-game assignments are keyed by [gameKey] ("hostId:gameId", the same key the stream screen
 * uses for per-game touch mode), so per-game presets can read [StoreData.games] directly.
 */
class ProfileLibrary(
    val data: StoreData,
    private val builtIn: ControlsProfile,
    private val newId: () -> String,
    private val now: () -> Long,
    /** Read-only ready-made profiles: genre templates and game layouts ([DefaultProfiles.presets]). */
    private val presets: List<ControlsProfile> = DefaultProfiles.presets(),
    /** Read-only ready-made layout sets; their layouts are among [presets]. */
    private val presetSets: List<LayoutSet> = DefaultProfiles.presetSets(),
) {
    /** Built-ins first, then the user's profiles by name. */
    val all: List<ControlsProfile> get() = listOf(builtIn) + presets + data.profiles.sortedBy { it.name.lowercase() }

    fun find(id: String?): ControlsProfile? = when (val cur = id?.let(ControlsProfile::currentId)) {
        null -> null
        builtIn.id -> builtIn
        else -> presets.firstOrNull { it.id == cur } ?: data.profiles.firstOrNull { it.id == cur }
    }

    /** Ready-made sets first, then the user's by name. */
    val sets: List<LayoutSet> get() = presetSets + data.sets.sortedBy { it.name.lowercase() }

    fun findSet(id: String?): LayoutSet? = id?.let { i -> presetSets.firstOrNull { it.id == i } ?: data.sets.firstOrNull { it.id == i } }
        ?.takeIf { s -> s.members.any { find(it) != null } }

    /** The sets [profileId] is a layout of. */
    fun setsOf(profileId: String): List<LayoutSet> = sets.filter { profileId in it.members }

    private fun exists(id: String) = find(id) != null || findSet(id) != null

    /** What [gameKey] uses (a profile or set id): its own choice, else the default. */
    private fun choice(gameKey: String?): String? =
        gameKey?.let { data.games[it] }?.takeIf(::exists) ?: data.activeProfileId.takeIf(::exists)

    /** The profile for [gameKey]: its own assignment, else the default, else Standard. With a set, its start layout. */
    fun resolve(gameKey: String?): ControlsProfile {
        val id = choice(gameKey) ?: return builtIn
        findSet(id)?.let { set -> return set.startId()?.let(::find) ?: set.members.firstNotNullOfOrNull(::find) ?: builtIn }
        return find(id) ?: builtIn
    }

    /** The layout set [gameKey] plays with, or null when it uses a single profile. */
    fun resolveSet(gameKey: String?): LayoutSet? = findSet(choice(gameKey))

    /** The profile or set id assigned to [gameKey] alone, or null when it follows the default. */
    fun assignedTo(gameKey: String): String? = data.games[gameKey]?.takeIf(::exists)

    fun uniqueName(base: String): String {
        val names = all.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in names) return base
        var n = 2
        while ("$base $n".lowercase() in names) n++
        return "$base $n"
    }

    /**
     * Saves [p]. Saving the built-in profile stores an editable copy under a new id instead and
     * returns it; the default moves onto the copy if it was Standard.
     */
    fun save(p: ControlsProfile): Pair<ProfileLibrary, ControlsProfile> {
        val t = now()
        if (p.isBuiltIn) {
            val copy = p.copy(id = newId(), name = uniqueName(if (p.id == builtIn.id) "My controls" else "My ${p.name}".take(MAX_NAME)), origin = null, createdAtMs = t, updatedAtMs = t)
            val active = if (data.activeProfileId == p.id) copy.id else data.activeProfileId
            return with(data.copy(profiles = data.profiles + copy, activeProfileId = active)) to copy
        }
        val saved = p.copy(updatedAtMs = t, createdAtMs = if (p.createdAtMs == 0L) t else p.createdAtMs)
        val exists = data.profiles.any { it.id == p.id }
        val list = if (exists) data.profiles.map { if (it.id == p.id) saved else it } else data.profiles + saved
        return with(data.copy(profiles = list)) to saved
    }

    fun rename(id: String, name: String): ProfileLibrary {
        val clean = name.trim().take(MAX_NAME)
        if (clean.isEmpty() || find(id)?.isBuiltIn != false) return this
        return with(data.copy(profiles = data.profiles.map { if (it.id == id) it.copy(name = clean, updatedAtMs = now()) else it }))
    }

    fun duplicate(id: String): Pair<ProfileLibrary, ControlsProfile>? {
        val src = find(id) ?: return null
        val t = now()
        val copy = src.copy(id = newId(), name = uniqueName("${src.name} copy"), origin = null, createdAtMs = t, updatedAtMs = t)
        return with(data.copy(profiles = data.profiles + copy)) to copy
    }

    /**
     * Deletes a user profile; games and the default that used it go back to Standard. It leaves
     * every set it was in; a set left empty is deleted too.
     */
    fun delete(id: String): ProfileLibrary {
        if (find(id)?.isBuiltIn != false) return this
        val sets = data.sets.mapNotNull { it.without(id) }
        val goneSets = data.sets.map { it.id }.toSet() - sets.map { it.id }.toSet()
        val gone = goneSets + id
        return with(
            data.copy(
                profiles = data.profiles.filterNot { it.id == id },
                activeProfileId = if (data.activeProfileId in gone) builtIn.id else data.activeProfileId,
                games = data.games.filterValues { it !in gone },
                sets = sets,
            ),
        )
    }

    // ---- layout sets ----

    /** A new set of [members] (existing profile ids, at most [LayoutSet.MAX_LAYOUTS]). */
    fun createSet(name: String, members: List<String>): Pair<ProfileLibrary, LayoutSet>? {
        val m = members.distinct().filter { find(it) != null }.take(LayoutSet.MAX_LAYOUTS)
        if (m.isEmpty()) return null
        val t = now()
        val set = LayoutSet(LayoutSet.ID_PREFIX + newId().removePrefix("p-"), uniqueSetName(name.trim().ifBlank { "Layout set" }.take(LayoutSet.MAX_NAME)), m, createdAtMs = t, updatedAtMs = t)
        return with(data.copy(sets = data.sets + set)) to set
    }

    /** Saves changes to a user set (members, cycle, start, name); built-in sets are read-only. */
    fun updateSet(set: LayoutSet): ProfileLibrary {
        if (set.isBuiltIn || data.sets.none { it.id == set.id }) return this
        val m = set.members.distinct().filter { find(it) != null }.take(LayoutSet.MAX_LAYOUTS)
        if (m.isEmpty()) return deleteSet(set.id)
        val clean = set.copy(
            name = set.name.trim().ifBlank { "Layout set" }.take(LayoutSet.MAX_NAME),
            members = m,
            cycle = set.cycle?.filter { it in m }?.distinct()?.takeIf { it.isNotEmpty() && it != m },
            start = set.start?.takeIf { it in m && it != m.first() },
            updatedAtMs = now(),
        )
        return with(data.copy(sets = data.sets.map { if (it.id == set.id) clean else it }))
    }

    /** Deletes a user set (its layouts stay as profiles); games and the default that used it go back to Standard. */
    fun deleteSet(id: String): ProfileLibrary {
        if (data.sets.none { it.id == id }) return this
        return with(
            data.copy(
                sets = data.sets.filterNot { it.id == id },
                activeProfileId = if (data.activeProfileId == id) builtIn.id else data.activeProfileId,
                games = data.games.filterValues { it != id },
            ),
        )
    }

    /**
     * Adds an imported set: every layout in [layouts] (ids as in the file) becomes a profile under
     * a fresh id, switch elements that name a layout are pointed at its new profile, and a set
     * groups them. Returns the new set and its profiles, in [layouts] order.
     */
    fun addSet(set: LayoutSet, layouts: List<ControlsProfile>): Triple<ProfileLibrary, LayoutSet, List<ControlsProfile>> {
        val t = now()
        val ids = layouts.associate { it.id to newId() }
        fun retarget(l: List<ControlElement>) = l.map { e ->
            val to = e.switchTo
            if (e.kind == ElementKind.SWITCH && to is SwitchTarget.Layout) e.copy(switchTo = ids[to.id]?.let { SwitchTarget.Layout(it) } ?: SwitchTarget.Picker) else e
        }
        val taken = all.map { it.name.lowercase() }.toMutableSet()
        val added = layouts.map { p ->
            val base = p.name.trim().ifBlank { "Layout" }.take(MAX_NAME)
            var name = base
            var n = 2
            while (name.lowercase() in taken) name = "$base ${n++}"
            taken += name.lowercase()
            p.copy(id = ids.getValue(p.id), name = name, landscape = retarget(p.landscape), portrait = p.portrait?.let(::retarget), createdAtMs = t, updatedAtMs = t)
        }
        val members = set.members.mapNotNull(ids::get).ifEmpty { added.map { it.id } }
        val newSet = set.copy(
            id = LayoutSet.ID_PREFIX + newId().removePrefix("p-"),
            name = uniqueSetName(set.name.trim().ifBlank { "Layout set" }.take(LayoutSet.MAX_NAME)),
            members = members,
            cycle = set.cycle?.mapNotNull(ids::get),
            start = set.start?.let(ids::get),
            createdAtMs = t, updatedAtMs = t,
        )
        return Triple(with(data.copy(profiles = data.profiles + added, sets = data.sets + newSet)), newSet, added)
    }

    fun uniqueSetName(base: String): String {
        val names = sets.map { it.name.lowercase() }.toSet()
        if (base.lowercase() !in names) return base
        var n = 2
        while ("$base $n".lowercase() in names) n++
        return "$base $n"
    }

    /** Adds an imported profile under a fresh id and a unique name. */
    fun add(p: ControlsProfile): Pair<ProfileLibrary, ControlsProfile> {
        val t = now()
        val added = p.copy(id = newId(), name = uniqueName(p.name.trim().ifBlank { "Imported controls" }.take(MAX_NAME)), createdAtMs = t, updatedAtMs = t)
        return with(data.copy(profiles = data.profiles + added)) to added
    }

    fun setDefault(id: String): ProfileLibrary = if (!exists(id)) this else with(data.copy(activeProfileId = id))

    /** [id] (a profile or set id) null clears the game's own choice so it follows the default. */
    fun assign(gameKey: String, id: String?): ProfileLibrary {
        val games = if (id == null || !exists(id)) data.games - gameKey else data.games + (gameKey to id)
        return with(data.copy(games = games))
    }

    private fun with(d: StoreData) = ProfileLibrary(d, builtIn, newId, now, presets, presetSets)

    companion object {
        const val MAX_NAME = 40
    }
}
