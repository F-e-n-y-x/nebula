package io.github.f_e_n_y_x.nebula.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "No empty stub": every setting shown in Settings is either wired to code that reads it, or shown
 * as not live ("Coming in …" / not part of Nebula). Checks the claims in [settingWiring] against
 * the source tree, so a setting can't quietly become a no-op again.
 *
 * Unit tests run with the :nebula module as working directory.
 */
class SettingWiringTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "engine").isDirectory && File(it, "nebula").isDirectory }
    private val engineSrc = File(root, "engine/src/main/java")
    private val nebulaSrc = File(root, "nebula/src/main/java")
    private val prefConfig = File(engineSrc, "com/limelight/preferences/PreferenceConfiguration.kt").readText()
    private val engineFiles = sources(engineSrc).filterKeys { !it.endsWith("PreferenceConfiguration.kt") }
    private val facadeFiles = engineFiles.filterKeys { it.contains("/io/github/fenyx/nebula/engine/") }
    private val nebulaFiles = sources(nebulaSrc).filterKeys { !it.endsWith("LegacySettingsCatalog.kt") && !it.endsWith("SettingWiring.kt") }
    private val session = File(engineSrc, "io/github/fenyx/nebula/engine/StreamSession.kt").readText()

    private fun sources(dir: File) = dir.walkTopDown().filter { it.extension == "kt" || it.extension == "java" }.associate { it.path to it.readText() }

    @Test
    fun `every visible setting says what it does`() {
        val keys = legacySettings.map { it.key }.toSet()
        val missing = keys - settingWiring.keys
        val stale = settingWiring.keys - keys
        assertTrue("settings with no wiring entry: $missing", missing.isEmpty())
        assertTrue("wiring entries for settings that no longer exist: $stale", stale.isEmpty())
    }

    @Test
    fun `engine settings are read by V+ config and used by the engine`() {
        val broken = settingWiring.mapNotNull { (key, w) ->
            val field = (w as? Wiring.Engine)?.field ?: return@mapNotNull null
            val constNames = Regex("""const val (\w+)\s*=\s*"${Regex.escape(key)}"""").findAll(prefConfig).map { it.groupValues[1] }.toList()
            val read = prefConfig.lines().any { line ->
                !line.contains("const val") && (line.contains("\"$key\"") || constNames.any { Regex("""\b$it\b""").containsMatchIn(line) })
            }
            val declared = Regex("""var $field\b""").containsMatchIn(prefConfig)
            val used = engineFiles.values.any { Regex("""\.$field\b""").containsMatchIn(it) }
            if (read && declared && used) null else "$key → $field (read=$read declared=$declared used=$used)"
        }
        assertTrue("engine wiring without evidence:\n${broken.joinToString("\n")}", broken.isEmpty())
    }

    @Test
    fun `Nebula settings are read by Nebula code`() {
        val readers = nebulaFiles.values + facadeFiles.values
        val broken = settingWiring.filter { (_, w) -> w is Wiring.Nebula || w is Wiring.Native }
            .keys.filterNot { key -> readers.any { it.contains("\"$key\"") } }
        assertTrue("Nebula/Native wiring with no reader: $broken", broken.isEmpty())
    }

    @Test
    fun `native settings are not listed twice`() {
        val native = settingWiring.filterValues { it == Wiring.Native }.keys
        val section = nebulaFiles.entries.first { it.key.endsWith("LegacySettingsSection.kt") }.value
        val listed = Regex("""internal val nativeKeys = setOf\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL).find(section)!!.groupValues[1]
        val missing = native.filterNot { listed.contains("\"$it\"") }
        assertTrue("native settings still shown as generic rows: $missing", missing.isEmpty())
    }

    @Test
    fun `actions are performed`() {
        val section = nebulaFiles.entries.first { it.key.endsWith("LegacySettingsSection.kt") }.value
        val broken = settingWiring.filterValues { it == Wiring.Action }.keys.filterNot { section.contains("\"$it\" ->") || section.contains("\"$it\",") }
        assertTrue("actions with no handler: $broken", broken.isEmpty())
    }

    /** The StreamSession callbacks that carry host → client features. */
    private val featureCallbacks = setOf(
        "rumble", "rumbleTriggers", "setAdaptiveTriggers", "setMotionEventState", "setControllerLED",
        "ds5HapticsPcm", "onCursorUpdate", "onRemoteTextContext",
    )

    /** Names of `override fun x(...) = Unit` in StreamSession (parameters may span lines). */
    private fun stubbedCallbacks(): Set<String> =
        Regex("""override fun (\w+)\((?:[^()]|\([^()]*\))*\)\s*=\s*Unit""").findAll(session).map { it.groupValues[1] }.toSet()

    @Test
    fun `no host feature callback is an empty stub`() {
        val stubs = stubbedCallbacks() intersect featureCallbacks
        assertTrue("empty `= Unit` host callbacks in StreamSession: $stubs", stubs.isEmpty())
        // Each one either forwards to the listener or reports the feature as unsupported.
        featureCallbacks.forEach { cb ->
            val body = Regex("""override fun $cb\((?:[^()]|\([^()]*\))*\)[^\n]*(\n[^\n]*){0,3}""").find(session)?.value.orEmpty()
            assertTrue("$cb neither forwards nor reports unsupported", body.contains("listener.") || body.contains("unsupported(HostFeature."))
        }
    }

    @Test
    fun `no live setting depends on a stubbed callback`() {
        val stubs = stubbedCallbacks()
        val bad = settingWiring.mapNotNull { (key, w) ->
            val cbs = when (w) { is Wiring.Engine -> w.callbacks; is Wiring.Nebula -> w.callbacks; else -> emptySet() }
            (cbs intersect stubs).takeIf { it.isNotEmpty() }?.let { "$key → $it" }
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
    }

    @Test
    fun `the app consumes rumble, trigger rumble, lights and motion`() {
        val repo = nebulaFiles.entries.first { it.key.endsWith("EngineRepositories.kt") }.value
        listOf("onRumble", "onRumbleTriggers", "onControllerLed", "onMotionRequest").forEach {
            assertTrue("EngineStreamRepository ignores $it", repo.contains("override fun $it("))
        }
    }

    @Test
    fun `unfinished features look unfinished`() {
        val framegen = legacySettings.filter { it.group == "framegen" }.map { it.key }
        (framegen + listOf("list_osc_layout", "checkbox_dualsense_direct_bluetooth", "list_dualsense_output_mode", "checkbox_usb_driver")).forEach { key ->
            val s = rowState(key)
            assertTrue("$key should show as coming in 0.4, is $s", s is RowState.Coming && s.release == "0.4")
        }
        settingWiring.filterValues { it is Wiring.ComingSoon }.keys.forEach { assertTrue(it, rowState(it) is RowState.Coming) }
        settingWiring.filterValues { it is Wiring.NotInNebula }.keys.forEach { assertTrue(it, rowState(it) is RowState.Unavailable) }
    }

    @Test
    fun `live rows are exactly the wired ones`() {
        val live = legacySettings.filter { rowState(it.key) == RowState.Live }.map { it.key }.toSet()
        val wired = settingWiring.filterValues { it is Wiring.Engine || it is Wiring.Nebula || it == Wiring.Native || it == Wiring.Action }.keys
        assertEquals(wired, live)
    }
}
