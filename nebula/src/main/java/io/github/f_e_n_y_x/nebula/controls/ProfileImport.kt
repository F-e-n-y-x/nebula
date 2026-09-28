package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONException
import org.json.JSONObject

/** One entry point for "Import": Nebula profiles and both V+ Crown formats. */
object ProfileImport {
    enum class Source(val label: String) { NEBULA("Nebula profile"), CROWN("V+ Crown profile") }

    data class Outcome(val profile: ControlsProfile, val source: Source, val skipped: List<CrownImport.Skipped> = emptyList())

    fun parse(text: String, fallback: CrownImport.Basis, newId: String, now: Long): Outcome {
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
