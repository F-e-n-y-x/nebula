package io.github.f_e_n_y_x.nebula.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySettingsCatalogTest {
    @Test
    fun `every V+ preference is listed once`() {
        val keys = legacySettings.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue(legacySettings.size >= 150)
    }

    @Test
    fun `every entry lands in a Nebula settings group`() {
        val groups = SettingsGroup.entries.map { it.id }.toSet() + "about"
        assertTrue(legacySettings.filterNot { it.group in groups }.joinToString { it.key }, legacySettings.all { it.group in groups })
    }

    @Test
    fun `dependencies point at existing settings`() {
        val keys = legacySettings.map { it.key }.toSet()
        val broken = legacySettings.mapNotNull { it.dependsOn }.filterNot { it in keys }
        assertTrue("broken: $broken", broken.isEmpty())
    }

    @Test
    fun `search finds settings by title and help text`() {
        assertTrue(searchSettings("bitrate").isNotEmpty())
        assertTrue(searchSettings("trackpad").any { it.key == "checkbox_touchscreen_trackpad" })
        assertTrue(searchSettings("  ").isEmpty())
    }

    @Test
    fun `sliders have sane ranges`() {
        legacySettings.forEach { s ->
            val k = s.kind as? SettingKind.Slider ?: return@forEach
            assertTrue(s.key, k.max > k.min && k.divisor > 0 && k.step > 0)
        }
    }
}
