package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONException
import org.json.JSONObject

/** One entry point for "Import": Nebula profiles and both V+ Crown formats. */
object ProfileImport {
    enum class Source(val label: String) { LAYOUT("Nebula layout"), NEBULA("Nebula profile"), CROWN("V+ Crown profile") }

    data class Outcome(
        val profile: ControlsProfile,
        val source: Source,
        val skipped: List<CrownImport.Skipped> = emptyList(),
        /** A layout file's metadata (author, game, target…). */
        val meta: LayoutMeta? = null,
    )

    fun parse(text: String, fallback: CrownImport.Basis, newId: String, now: Long): Outcome {
        if (text.length > LayoutFile.MAX_BYTES * 16) throw ControlsFormatException("That file is too large to be a controls profile")
        // Shared layouts (files and share codes) are validated strictly.
        if (LayoutFile.looksLikeLayout(text)) {
            val parsed = LayoutFile.parse(text, newId, now)
            return Outcome(parsed.profile, Source.LAYOUT, meta = parsed.meta)
        }
        val root = try {
            JSONObject(text.trim())
        } catch (e: JSONException) {
            throw ControlsFormatException("That isn't a controls profile (not JSON)")
        }
        return when {
            ProfileJson.isNebulaProfile(root) -> Outcome(ProfileJson.importProfile(text).copy(id = newId, origin = null), Source.NEBULA)
            CrownImport.looksLikeCrown(root) -> CrownImport.import(text, fallback, newId, now).let { Outcome(it.profile, Source.CROWN, it.skipped) }
            else -> throw ControlsFormatException("That isn't a Nebula or V+ Crown controls profile")
        }.also { if (it.profile.landscape.isEmpty() && it.profile.portrait.isNullOrEmpty()) throw ControlsFormatException(emptyMessage(it)) }
    }

    private fun emptyMessage(o: Outcome) =
        if (o.skipped.isEmpty()) "The profile has no controls in it" else "None of its ${o.skipped.size} controls work in Nebula yet"

    fun fileName(p: ControlsProfile): String {
        val stem = p.name.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-', '.', '_').ifBlank { "controls" }
        return "$stem.nebula-controls.json"
    }
}
