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
) {
    /** Built-ins first, then the user's profiles by name. */
    val all: List<ControlsProfile> get() = listOf(builtIn) + presets + data.profiles.sortedBy { it.name.lowercase() }

    fun find(id: String?): ControlsProfile? = when (val cur = id?.let(ControlsProfile::currentId)) {
        null -> null
        builtIn.id -> builtIn
        else -> presets.firstOrNull { it.id == cur } ?: data.profiles.firstOrNull { it.id == cur }
    }

    /** The profile for [gameKey]: its own assignment, else the default, else Standard. */
    fun resolve(gameKey: String?): ControlsProfile =
        gameKey?.let { data.games[it] }?.let(::find) ?: find(data.activeProfileId) ?: builtIn

    /** The profile id assigned to [gameKey] alone, or null when it follows the default. */
    fun assignedTo(gameKey: String): String? = data.games[gameKey]?.takeIf { find(it) != null }

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

    /** Deletes a user profile; games and the default that used it go back to Standard. */
    fun delete(id: String): ProfileLibrary {
        if (find(id)?.isBuiltIn != false) return this
        return with(
            data.copy(
                profiles = data.profiles.filterNot { it.id == id },
                activeProfileId = if (data.activeProfileId == id) builtIn.id else data.activeProfileId,
                games = data.games.filterValues { it != id },
            ),
        )
    }

    /** Adds an imported profile under a fresh id and a unique name. */
    fun add(p: ControlsProfile): Pair<ProfileLibrary, ControlsProfile> {
        val t = now()
        val added = p.copy(id = newId(), name = uniqueName(p.name.trim().ifBlank { "Imported controls" }.take(MAX_NAME)), createdAtMs = t, updatedAtMs = t)
        return with(data.copy(profiles = data.profiles + added)) to added
    }

    fun setDefault(id: String): ProfileLibrary = if (find(id) == null) this else with(data.copy(activeProfileId = id))

    /** [id] null clears the game's own choice so it follows the default. */
    fun assign(gameKey: String, id: String?): ProfileLibrary {
        val games = if (id == null || find(id) == null) data.games - gameKey else data.games + (gameKey to id)
        return with(data.copy(games = games))
    }

    private fun with(d: StoreData) = ProfileLibrary(d, builtIn, newId, now, presets)

    companion object {
        const val MAX_NAME = 40
    }
}
