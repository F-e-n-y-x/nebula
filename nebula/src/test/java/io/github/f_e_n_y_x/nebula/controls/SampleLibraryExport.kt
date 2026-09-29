package io.github.f_e_n_y_x.nebula.controls

import org.junit.Test
import java.io.File

/**
 * Writes the built-in PUBG-style presets as layout files for the layout library, only when
 * NEBULA_WRITE_SAMPLES names the library folder (e.g. designs/nebula-layouts). Run the
 * library's `tools/validate.py --write-index` afterwards to refresh index.json.
 */
class SampleLibraryExport {
    @Test
    fun writeSamples() {
        val root = System.getenv("NEBULA_WRITE_SAMPLES")?.let(::File) ?: return
        for (p in listOf(DefaultProfiles.gtaPubg(), DefaultProfiles.shooterPad(), DefaultProfiles.shooterKbm())) {
            val meta = p.meta ?: error("preset ${p.id} has no metadata")
            val game = meta.game?.let { if (it.steamAppId == 271590) "gta-v" else LayoutFile.slug(it.name) } ?: "generic"
            val file = File(root, "layouts/$game/${LayoutFile.slug(meta.name.substringAfter(": "))}.json")
            file.parentFile.mkdirs()
            file.writeText(LayoutFile.encode(p) + "\n")
        }
    }
}
