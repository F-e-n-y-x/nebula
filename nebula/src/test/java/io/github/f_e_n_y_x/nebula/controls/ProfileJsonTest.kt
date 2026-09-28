package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileJsonTest {
    private val custom = ControlsProfile(
        id = "p-1",
        name = "Souls",
        landscape = listOf(
            newElement(ElementKind.STICK, "s").copy(stick = StickOutput.KEYS, bindings = listOf(0x57, 0x53, 0x41, 0x44).map { Binding.Key(it) }, floating = true, deadzone = 0.2f),
            newElement(ElementKind.BUTTON, "b").copy(label = "Roll", mode = PressMode.TOGGLE, shape = ElementShape.PILL, opacity = 0.6f, tint = 0xFF4CC38A),
            newElement(ElementKind.MACRO, "m").copy(steps = listOf(MacroStep(Binding.Key(0x20), 80, 120), MacroStep(Binding.Mouse(MouseKey.LEFT), 30, 0))),
            newElement(ElementKind.TOUCHPAD, "t").copy(sensitivity = 1.75f),
            newElement(ElementKind.COMBO, "c").copy(bindings = listOf(Binding.Key(0xA2), Binding.Key(0xA0), Binding.Key(0x1B))),
            newElement(ElementKind.DPAD, "d"),
            newElement(ElementKind.TRIGGER, "r").copy(bindings = listOf(Binding.Wheel(false))),
        ),
        portrait = listOf(newElement(ElementKind.BUTTON, "only").copy(x = 0.25f, y = 0.8f)),
        createdAtMs = 5, updatedAtMs = 9,
    )

    @Test
    fun profileRoundTripsExactly() {
        val text = ProfileJson.exportProfile(custom)
        assertEquals(ProfileJson.PROFILE_KIND, JSONObject(text).getString("kind"))
        assertEquals(custom, ProfileJson.importProfile(text))
    }

    @Test
    fun builtInProfileRoundTrips() {
        val std = DefaultProfiles.standard(StandardOptions(style = PadStyle.DUALSENSE))
        assertEquals(std, ProfileJson.importProfile(ProfileJson.exportProfile(std)))
    }

    @Test
    fun storeRoundTrips() {
        val d = StoreData(listOf(custom), activeProfileId = "p-1", games = mapOf("h:elden" to "p-1", "h:tetris" to ControlsProfile.STANDARD_ID))
        assertEquals(d, ProfileJson.decodeStore(ProfileJson.encodeStore(d)))
    }

    @Test
    fun unknownElementKindsAreDroppedNotFatal() {
        val root = JSONObject(ProfileJson.exportProfile(custom))
        val land = root.getJSONObject("profile").getJSONArray("landscape")
        land.put(JSONObject().put("id", "future").put("kind", "hologram"))
        land.getJSONObject(0).put("someNewField", 42)
        val back = ProfileJson.importProfile(root.toString())
        assertEquals(custom.landscape, back.landscape)
    }

    @Test
    fun missingFieldsTakeDefaultsAndBadValuesAreClamped() {
        val e = ProfileJson.elementFromJson(JSONObject().put("id", "x").put("kind", "button").put("w", 1).put("opacity", 7).put("mode", "sometimes"))!!
        assertEquals(ControlElement.MIN_SIZE_DP, e.width)
        assertEquals(56f, e.height)
        assertEquals(1f, e.opacity)
        assertEquals(PressMode.HOLD, e.mode)
        assertTrue(e.bindings.isEmpty())
        assertNull(ProfileJson.elementFromJson(JSONObject().put("kind", "button")))
    }

    @Test
    fun rejectsOtherFilesAndNewerVersions() {
        expectFormatError { ProfileJson.importProfile("not json") }
        expectFormatError { ProfileJson.importProfile("""{"kind":"something"}""") }
        expectFormatError { ProfileJson.importProfile("""{"kind":"nebula.controls.profile","schemaVersion":99,"profile":{}}""") }
        expectFormatError { ProfileJson.importProfile("""{"kind":"nebula.controls.profile","schemaVersion":1}""") }
    }

    @Test
    fun importAssignsFreshIdAndDetectsFormat() {
        val o = ProfileImport.parse(ProfileJson.exportProfile(custom), CrownImport.Basis(2400, 1080, 2.75f), "new-id", 1)
        assertEquals(ProfileImport.Source.NEBULA, o.source)
        assertEquals("new-id", o.profile.id)
        assertEquals(custom.landscape, o.profile.landscape)
        expectFormatError { ProfileImport.parse("""{"hello":"world"}""", CrownImport.Basis(1, 1, 1f), "x", 0) }
        expectFormatError { ProfileImport.parse(ProfileJson.exportProfile(custom.copy(landscape = emptyList(), portrait = null)), CrownImport.Basis(1, 1, 1f), "x", 0) }
        assertEquals("souls.nebula-controls.json", ProfileImport.fileName(custom))
        assertEquals("controls.nebula-controls.json", ProfileImport.fileName(custom.copy(name = "!!!")))
    }

    private fun expectFormatError(block: () -> Unit) {
        try {
            block(); fail("expected ControlsFormatException")
        } catch (e: ControlsFormatException) {
            assertTrue(e.message!!.isNotBlank())
        }
    }
}
