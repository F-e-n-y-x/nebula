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

    /**
     * Deletes a user set; games and the default that used it go back to Standard. Its layouts
     * stay as loose profiles, unless [withLayouts]: then the ones no other set uses go too.
     */
    fun deleteSet(id: String, withLayouts: Boolean = false): ProfileLibrary {
        val set = data.sets.firstOrNull { it.id == id } ?: return this
        val left = data.sets.filterNot { it.id == id }
        val dropped = if (withLayouts) set.members.filter { m -> left.none { m in it.members } && data.profiles.any { it.id == m } }.toSet() else emptySet()
        val gone = dropped + id
        return with(
            data.copy(
                profiles = data.profiles.filterNot { it.id in dropped },
                sets = left,
                activeProfileId = if (data.activeProfileId in gone) builtIn.id else data.activeProfileId,
                games = data.games.filterValues { it !in gone },
            ),
        )
    }

    fun renameSet(id: String, name: String): ProfileLibrary {
        val clean = name.trim().take(LayoutSet.MAX_NAME)
        val set = data.sets.firstOrNull { it.id == id } ?: return this
        if (clean.isEmpty() || clean == set.name) return this
        val unique = if (sets.any { it.id != id && it.name.equals(clean, ignoreCase = true) }) uniqueSetName(clean) else clean
        return with(data.copy(sets = data.sets.map { if (it.id == id) it.copy(name = unique, updatedAtMs = now()) else it }))
    }

    // ---- grouping: a set is one item, its layouts aren't also loose profiles ----

    /**
     * Everything to list, a set as one item: loose built-in profiles (Standard) first, then the
     * sets with their layouts in picker order, then the user's loose profiles by name. A profile
     * in a set isn't listed loose; one in two sets shows in both. Nothing is changed or lost:
     * delete the set and its layouts are loose again.
     */
    fun groups(): List<ProfileGroup> {
        // "GTA V · on foot, vehicle, aircraft" lists as "GTA V"; the full name only when two sets would read the same.
        val short = sets.groupBy { it.name.substringBefore(" · ") }
        val setGroups = sets.mapNotNull { s ->
            val name = s.name.substringBefore(" · ").takeIf { short.getValue(it).size == 1 } ?: s.name
            s.members.mapNotNull(::find).takeIf { it.isNotEmpty() }?.let { ProfileGroup.SetItem(s, it, name) }
        }
        val inSets = setGroups.flatMap { g -> g.layouts.map { it.id } }.toSet()
        val (builtIns, mine) = all.filter { it.id !in inSets }.partition { it.isBuiltIn }
        return builtIns.map(ProfileGroup::Single) + setGroups + mine.map(ProfileGroup::Single)
    }

    /** The first set [profileId] is a layout of, if any. */
    fun setOf(profileId: String?): LayoutSet? = profileId?.let { id -> sets.firstOrNull { id in it.members } }

    // ---- making sets on the device ----

    /**
     * A new set from [sources], in order: a new layout from the standard controller, a copy of
     * a profile, or an existing profile moved in (a built-in one is copied, as it can't change).
     * [start] and [inCycle] index [sources]. Every layout without a layout switch gets one (top
     * centre, clear of its controls) going to [switchTo]. Null when nothing could be added.
     */
    fun newSet(
        name: String,
        sources: List<SetSource>,
        start: Int = 0,
        inCycle: List<Boolean>? = null,
        switchTo: SwitchTarget = SwitchTarget.Next,
    ): Pair<ProfileLibrary, LayoutSet>? {
        var lib = this
        val ids = mutableListOf<String?>()
        for (src in sources.take(LayoutSet.MAX_LAYOUTS)) {
            val (next, p) = lib.materialize(src, switchTo) ?: (lib to null)
            lib = next
            ids += p?.id
        }
        val members = ids.filterNotNull().distinct()
        if (members.isEmpty()) return null
        val cycle = inCycle?.let { flags -> ids.filterIndexed { i, id -> id != null && flags.getOrElse(i) { true } }.filterNotNull().distinct() }
        val (made, set) = lib.createSet(name, members) ?: return null
        val wanted = set.copy(start = ids.getOrNull(start), cycle = cycle?.takeIf { it.isNotEmpty() })
        val done = made.updateSet(wanted)
        return done to (done.findSet(set.id) ?: set)
    }

    /** Adds a layout to user set [setId] (with a switch, as [newSet] does); the new layout, or null. */
    fun addToSet(setId: String, source: SetSource, switchTo: SwitchTarget = SwitchTarget.Next): Pair<ProfileLibrary, ControlsProfile>? {
        val set = data.sets.firstOrNull { it.id == setId } ?: return null
        if (set.members.size >= LayoutSet.MAX_LAYOUTS) return null
        val (lib, p) = materialize(source, switchTo) ?: return null
        if (p.id in set.members) return lib to p
        // A layout added to a set that lists its cycle joins the cycle too.
        val updated = lib.updateSet(set.copy(members = set.members + p.id, cycle = set.cycle?.plus(p.id)))
        return updated to (updated.find(p.id) ?: p)
    }

    private fun materialize(src: SetSource, switchTo: SwitchTarget): Pair<ProfileLibrary, ControlsProfile>? {
        val t = now()
        val p: ControlsProfile = when (src) {
            is SetSource.Blank -> builtIn.copy(id = newId(), name = uniqueName(src.name.trim().ifBlank { "Layout" }.take(MAX_NAME)), origin = null, meta = null, createdAtMs = t, updatedAtMs = t)
            is SetSource.Copy -> {
                val from = find(src.profileId) ?: return null
                from.copy(id = newId(), name = uniqueName((src.name?.trim()?.ifBlank { null } ?: "${from.name} copy").take(MAX_NAME)), origin = null, createdAtMs = t, updatedAtMs = t)
            }
            is SetSource.Existing -> {
                val from = find(src.profileId) ?: return null
                if (from.isBuiltIn) from.copy(id = newId(), name = uniqueName("My ${from.name}".take(MAX_NAME)), origin = null, createdAtMs = t, updatedAtMs = t) else from
            }
        }
        val withSwitch = SwitchPlacement.ensure(p, switchTo)
        if (withSwitch === p && data.profiles.any { it.id == p.id }) return this to p
        val list = if (data.profiles.any { it.id == p.id }) data.profiles.map { if (it.id == p.id) withSwitch.copy(updatedAtMs = t) else it } else data.profiles + withSwitch
        return with(data.copy(profiles = list)) to withSwitch
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

/** One item in a profiles list: a loose profile, or a layout set with its layouts. */
sealed interface ProfileGroup {
    data class Single(val profile: ControlsProfile) : ProfileGroup
    data class SetItem(val set: LayoutSet, val layouts: List<ControlsProfile>, val shortName: String = set.name) : ProfileGroup {
        /** "GTA V · 5 layouts" */
        val title: String get() = "$shortName · ${layouts.size} layout${if (layouts.size == 1) "" else "s"}"
    }
}

/** Where a layout of a new set comes from. */
sealed interface SetSource {
    /** A new layout, starting from the standard controller. */
    data class Blank(val name: String) : SetSource
    /** A copy of a profile (or of another set's layout). */
    data class Copy(val profileId: String, val name: String? = null) : SetSource
    /** A profile moved into the set as it is. */
    data class Existing(val profileId: String) : SetSource
}
