package io.github.f_e_n_y_x.nebula.controls

import org.junit.Test
import java.io.File

/**
 * Writes the built-in library presets (the touch-shooter genre templates and the GTA V touch
 * controls) as layout files where the library keeps them ([LibraryPaths]), only when
 * NEBULA_WRITE_SAMPLES names the library folder (e.g. designs/nebula-layouts). Run the
 * library's `tools/validate.py --write-index` afterwards to refresh index.json.
 */
class SampleLibraryExport {
    @Test
    fun writeSamples() {
        val root = System.getenv("NEBULA_WRITE_SAMPLES")?.let(::File) ?: return
        for (p in listOf(DefaultProfiles.touchShooterPad(), DefaultProfiles.touchShooterKbm(), DefaultProfiles.gtaTouchControls())) {
            val meta = p.meta ?: error("preset ${p.id} has no metadata")
            val file = File(root, LibraryPaths.pathFor(meta))
            file.parentFile.mkdirs()
            file.writeText(LayoutFile.encode(p) + "\n")
        }
    }
}
