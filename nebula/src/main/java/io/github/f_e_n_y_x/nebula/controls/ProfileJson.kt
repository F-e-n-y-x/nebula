package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class ControlsFormatException(message: String) : Exception(message)

/**
 * Nebula's JSON for controls profiles: one profile for sharing (`nebula.controls.profile`) and
 * the whole on-device store (`nebula.controls.store`, profiles + per-game assignments).
 *
 * Readers are lenient: unknown fields are ignored, unknown element kinds are dropped, missing
 * values take defaults. Writers always write every field.
 */
object ProfileJson {
    const val PROFILE_KIND = "nebula.controls.profile"
    const val STORE_KIND = "nebula.controls.store"
    const val SCHEMA_VERSION = 1

    // ---- share format ----

    fun exportProfile(p: ControlsProfile): String = JSONObject()
        .put("kind", PROFILE_KIND)
        .put("schemaVersion", SCHEMA_VERSION)
        .put("profile", profileToJson(p))
        .toString(2)

    fun importProfile(text: String): ControlsProfile {
        val root = parseObject(text)
        if (root.optString("kind") != PROFILE_KIND) throw ControlsFormatException("Not a Nebula controls profile")
        checkVersion(root)
        return profileFromJson(root.optJSONObject("profile") ?: throw ControlsFormatException("The file has no profile in it"))
    }

    fun isNebulaProfile(root: JSONObject) = root.optString("kind") == PROFILE_KIND

    // ---- store format ----

    data class StoreData(
        val profiles: List<ControlsProfile> = emptyList(),
        val activeProfileId: String = ControlsProfile.STANDARD_ID,
        /** "hostId:gameId" → profile id. */
        val games: Map<String, String> = emptyMap(),
    )

    fun encodeStore(d: StoreData): String = JSONObject()
        .put("kind", STORE_KIND)
        .put("schemaVersion", SCHEMA_VERSION)
        .put("activeProfileId", d.activeProfileId)
        .put("profiles", JSONArray().apply { d.profiles.forEach { put(profileToJson(it)) } })
        .put("games", JSONObject().apply { d.games.toSortedMap().forEach { (k, v) -> put(k, v) } })
        .toString()

    fun decodeStore(text: String): StoreData {
        val root = parseObject(text)
        if (root.optString("kind") != STORE_KIND) throw ControlsFormatException("Not a Nebula controls store")
        checkVersion(root)
        val profiles = root.optJSONArray("profiles")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::profileFromJsonOrNull) } }.orEmpty()
        val games = root.optJSONObject("games")?.let { g -> g.keys().asSequence().associateWith { g.optString(it) }.filterValues { it.isNotBlank() } }.orEmpty()
        return StoreData(profiles, root.optString("activeProfileId", ControlsProfile.STANDARD_ID), games)
    }

    // ---- pieces ----

    private fun parseObject(text: String): JSONObject = try {
        JSONObject(text.trim())
    } catch (e: JSONException) {
        throw ControlsFormatException("The file isn't valid JSON")
    }

    private fun checkVersion(root: JSONObject) {
        val v = root.optInt("schemaVersion", -1)
        if (v < 1) throw ControlsFormatException("The file has no schema version")
        if (v > SCHEMA_VERSION) throw ControlsFormatException("This profile was made by a newer Nebula; update Nebula to import it")
    }

    fun profileToJson(p: ControlsProfile): JSONObject = JSONObject()
        .put("id", p.id)
        .put("name", p.name)
        .put("createdAt", p.createdAtMs)
        .put("updatedAt", p.updatedAtMs)
        .apply { p.origin?.let { put("origin", it) } }
        .put("landscape", elementsToJson(p.landscape))
        .apply { p.portrait?.let { put("portrait", elementsToJson(it)) } }

    private fun profileFromJsonOrNull(o: JSONObject): ControlsProfile? = runCatching { profileFromJson(o) }.getOrNull()

    fun profileFromJson(o: JSONObject): ControlsProfile {
        val id = o.optString("id").ifBlank { throw ControlsFormatException("A profile has no id") }
        return ControlsProfile(
            id = id,
            name = o.optString("name").ifBlank { "Imported controls" },
            landscape = elementsFromJson(o.optJSONArray("landscape")),
            portrait = o.optJSONArray("portrait")?.let(::elementsFromJson),
            createdAtMs = o.optLong("createdAt", 0),
            updatedAtMs = o.optLong("updatedAt", 0),
            origin = o.optString("origin").ifBlank { null },
        )
    }

    private fun elementsToJson(list: List<ControlElement>) = JSONArray().apply { list.forEach { put(elementToJson(it)) } }

    private fun elementsFromJson(a: JSONArray?): List<ControlElement> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(::elementFromJson) }
    }

    fun elementToJson(e: ControlElement): JSONObject = JSONObject()
        .put("id", e.id)
        .put("kind", e.kind.id)
        .put("x", e.x.toDouble())
        .put("y", e.y.toDouble())
        .put("w", e.width.toDouble())
        .put("h", e.height.toDouble())
        .put("label", e.label)
        .put("opacity", e.opacity.toDouble())
        .put("mode", e.mode.id)
        .put("shape", e.shape.id)
        .put("bindings", JSONArray().apply { e.bindings.forEach { put(it.token()) } })
        .put("stick", e.stick.id)
        .put("click", e.click.token())
        .put("floating", e.floating)
        .put("deadzone", e.deadzone.toDouble())
        .put("sensitivity", e.sensitivity.toDouble())
        .put(
            "steps",
            JSONArray().apply {
                e.steps.forEach { s -> put(JSONObject().put("binding", s.binding.token()).put("holdMs", s.holdMs).put("gapMs", s.gapMs)) }
            },
        )
        .apply { e.tint?.let { put("tint", "#%08X".format(it)) } }
        .put("zone", e.zone.id)
        .put("acceleration", e.acceleration.toDouble())
        .put("invertY", e.invertY)
        .put("showRing", e.showRing)
        .put("keepWithController", e.keepWithController)

    /** Null for an element this version doesn't know (a newer kind); the rest of the profile still loads. */
    fun elementFromJson(o: JSONObject): ControlElement? {
        val kind = ElementKind.entries.firstOrNull { it.id == o.optString("kind") } ?: return null
        val id = o.optString("id").ifBlank { return null }
        val bindings = o.optJSONArray("bindings")?.let { a -> (0 until a.length()).map { Binding.parse(a.optString(it)) } }.orEmpty()
        val steps = o.optJSONArray("steps")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { s ->
                    MacroStep(Binding.parse(s.optString("binding")), s.optInt("holdMs", 60).coerceIn(10, 5000), s.optInt("gapMs", 40).coerceIn(0, 5000))
                }
            }
        }.orEmpty()
        return ControlElement(
            id = id,
            kind = kind,
            x = o.optDouble("x", 0.5).toFloat(),
            y = o.optDouble("y", 0.5).toFloat(),
            width = o.optDouble("w", 56.0).toFloat(),
            height = o.optDouble("h", 56.0).toFloat(),
            label = o.optString("label", ""),
            opacity = o.optDouble("opacity", 1.0).toFloat(),
            mode = PressMode.entries.firstOrNull { it.id == o.optString("mode") } ?: PressMode.HOLD,
            shape = ElementShape.entries.firstOrNull { it.id == o.optString("shape") } ?: ElementShape.ROUND,
            bindings = bindings,
            stick = StickOutput.entries.firstOrNull { it.id == o.optString("stick") } ?: StickOutput.LEFT,
            click = Binding.parse(o.optString("click", "none")),
            floating = o.optBoolean("floating", false),
            deadzone = o.optDouble("deadzone", 0.15).toFloat().coerceIn(0f, 0.9f),
            sensitivity = o.optDouble("sensitivity", 1.0).toFloat().coerceIn(0.1f, 5f),
            steps = steps,
            tint = o.optString("tint").takeIf { it.startsWith("#") && it.length == 9 }?.drop(1)?.toLongOrNull(16),
            zone = ZoneType.entries.firstOrNull { it.id == o.optString("zone") } ?: ZoneType.CAMERA_STICK,
            acceleration = o.optDouble("acceleration", 1.0).toFloat(),
            invertY = o.optBoolean("invertY", false),
            showRing = o.optBoolean("showRing", true),
            keepWithController = o.optBoolean("keepWithController", false),
        ).clampedSize()
    }
}
