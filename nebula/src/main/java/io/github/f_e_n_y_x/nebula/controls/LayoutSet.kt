package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONArray
import org.json.JSONObject

/**
 * Several layouts for one game, switched between while playing (GTA V: On foot / Vehicle /
 * Aircraft). Each layout is an ordinary [ControlsProfile] (so the editor, sharing and per-layout
 * touch settings all work unchanged); the set only names them, in [members] order (the picker's
 * order), with an optional [cycle] for next / previous and a [start] layout.
 *
 * A game is given a set the same way it is given a profile: [ProfileJson.StoreData.games] maps
 * the game to the set's id.
 */
data class LayoutSet(
    val id: String,
    val name: String,
    /** Profile ids, in picker order. */
    val members: List<String>,
    /** The order next / previous go through; null means every member, in order. */
    val cycle: List<String>? = null,
    /** The layout a stream starts on; null means the first member. */
    val start: String? = null,
    /** Sharing metadata kept from a set file, for re-sharing. */
    val meta: LayoutMeta? = null,
    /** Where it came from: null (made here), "library" or "builtin". */
    val origin: String? = null,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
) {
    val isBuiltIn: Boolean get() = id.startsWith(ControlsProfile.BUILTIN_PREFIX)

    /** The cycle, limited to real members; every member when none is left. */
    fun cycleOrder(): List<String> = cycle?.filter { it in members }?.distinct()?.takeIf { it.isNotEmpty() } ?: members

    fun startId(): String? = start?.takeIf { it in members } ?: members.firstOrNull()

    /**
     * The layout [to] leads to from [current], or null when it stays put (the picker, which the
     * caller shows, or a target outside the set). From a layout outside the cycle, next goes to
     * the cycle's first layout and previous to its last.
     */
    fun target(current: String?, to: SwitchTarget): String? {
        val order = cycleOrder()
        if (order.isEmpty()) return null
        val i = order.indexOf(current)
        return when (to) {
            SwitchTarget.Next -> if (i < 0) order.first() else order[(i + 1) % order.size]
            SwitchTarget.Previous -> if (i < 0) order.last() else order[(i - 1 + order.size) % order.size]
            SwitchTarget.Picker -> null
            is SwitchTarget.Layout -> to.id.takeIf { it in members && it != current }
        }?.takeIf { it != current }
    }

    /** Without [profileId] (a deleted profile); null when nothing is left. */
    fun without(profileId: String): LayoutSet? {
        if (profileId !in members) return this
        val m = members - profileId
        if (m.isEmpty()) return null
        return copy(members = m, cycle = cycle?.minus(profileId)?.takeIf { it.isNotEmpty() }, start = start?.takeIf { it != profileId })
    }

    companion object {
        const val ID_PREFIX = "set-"
        const val MAX_LAYOUTS = 8
        const val MAX_NAME = 40

        fun toJson(s: LayoutSet): JSONObject = JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("members", JSONArray(s.members))
            .apply { s.cycle?.let { put("cycle", JSONArray(it)) } }
            .apply { s.start?.let { put("start", it) } }
            .apply { s.meta?.let { put("meta", LayoutFile.metaToJson(it)) } }
            .apply { s.origin?.let { put("origin", it) } }
            .put("createdAt", s.createdAtMs)
            .put("updatedAt", s.updatedAtMs)

        /** Lenient, like the rest of the store: null for a set that can't be read. */
        fun fromJson(o: JSONObject): LayoutSet? {
            val id = o.optString("id").ifBlank { return null }
            fun list(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifBlank { null } } }
            val members = list("members").orEmpty().distinct().take(MAX_LAYOUTS)
            if (members.isEmpty()) return null
            return LayoutSet(
                id = id,
                name = o.optString("name").ifBlank { "Layout set" },
                members = members,
                cycle = list("cycle"),
                start = o.optString("start").ifBlank { null },
                meta = o.optJSONObject("meta")?.let { m -> runCatching { LayoutFile.parseMeta(m) }.getOrNull() },
                origin = o.optString("origin").ifBlank { null },
                createdAtMs = o.optLong("createdAt", 0),
                updatedAtMs = o.optLong("updatedAt", 0),
            )
        }
    }
}

/**
 * What a stream shows from a game's choice: the set (if the game has one) and the layout it is
 * on. Pure, so switching is unit-tested without a stream.
 */
data class ActiveLayout(val set: LayoutSet?, val profile: ControlsProfile) {
    /** The set's layouts that still exist, in picker order. */
    fun layouts(lib: ProfileLibrary): List<ControlsProfile> = set?.members?.mapNotNull(lib::find).orEmpty()

    companion object {
        /**
         * The layout for [gameKey]: with a set, [current] when it still belongs to it, else the
         * set's start layout; without, the game's profile.
         */
        fun of(lib: ProfileLibrary, gameKey: String?, current: String?): ActiveLayout {
            val set = lib.resolveSet(gameKey)
            if (set != null) {
                val p = current?.takeIf { it in set.members }?.let(lib::find)
                    ?: set.startId()?.let(lib::find)
                    ?: set.members.firstNotNullOfOrNull(lib::find)
                if (p != null) return ActiveLayout(set, p)
            }
            return ActiveLayout(null, lib.resolve(gameKey))
        }
    }
}
