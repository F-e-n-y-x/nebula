package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * test/resources/crown holds a V+ Crown export made the way V+ makes one (SuperConfigDatabaseHelper
 * rows serialised with Gson, md5 = MD5(version + settings + elements), wrapped by
 * CrownProfileShareManager.createBundle with the payload's SHA-256 and layoutBasis): an FPS
 * layout on a 2400×1080, 2.75× phone with every element type V+ has.
 */
class CrownImportTest {
    private fun res(name: String) = javaClass.classLoader!!.getResource("crown/$name")!!.readText()
    private val phone = CrownImport.Basis(1080, 2400, 3f) // a different, portrait device: must not be used for the bundle

    private fun bundle() = CrownImport.import(res("apex-fps.crown.json"), phone, "p-new", 42)

    @Test
    fun bundleImportsSupportedElementsAndReportsTheRest() {
        val r = bundle()
        assertTrue(r.basisFromFile)
        assertEquals(CrownImport.Basis(2400, 1080, 2.75f), r.basis)
        val p = r.profile
        assertEquals("Apex FPS", p.name)
        assertEquals("p-new", p.id)
        assertEquals("crown", p.origin)
        assertEquals(42L, p.createdAtMs)
        assertEquals(null, p.portrait)
        assertEquals(13, p.landscape.size)
        assertEquals(5, r.skipped.size)
        val reasons = r.skipped.joinToString { it.what + ": " + it.reason }
        assertTrue(reasons, reasons.contains("Group button"))
        assertTrue(reasons, reasons.contains("Wheel pad"))
        assertTrue(reasons, reasons.contains("Performance readout"))
        assertTrue(reasons, reasons.contains("PKS"))
        assertTrue(reasons, reasons.contains("Android key 1"))
    }

    @Test
    fun geometryIsConvertedFromThePhoneItWasMadeOn() {
        val jump = bundle().profile.landscape.first { it.label == "Jump" }
        near(2150f / 2400f, jump.x)
        near(820f / 1080f, jump.y)
        near(160f / 2.75f, jump.width)
        assertEquals(ElementShape.ROUND, jump.shape)
        near(0xF0 / 255f, jump.opacity)
        // Oversized elements are capped.
        val camera = bundle().profile.landscape.first { it.stick == StickOutput.RIGHT }
        assertEquals(ControlElement.MAX_SIZE_DP, camera.width)
    }

    @Test
    fun elementTypesMapToNebulaKinds() {
        val els = bundle().profile.landscape.associateBy { it.id.removePrefix("crown-").toLong() - 1718000000000 }
        // Layer 40 (camera area) is drawn first, under everything else.
        assertEquals("crown-1718000000002", bundle().profile.landscape.first().id)

        els.getValue(1).let { // digital stick → WASD stick, middle = Shift
            assertEquals(ElementKind.STICK, it.kind)
            assertEquals(StickOutput.KEYS, it.stick)
            assertEquals(listOf(0x57, 0x53, 0x41, 0x44).map { vk -> Binding.Key(vk) }, it.bindings)
            assertEquals(Binding.Key(0xA0), it.click)
            assertFalse(it.floating)
            near(30f / 220f, it.deadzone)
        }
        els.getValue(2).let { // invisible analog → floating right stick, faint
            assertEquals(StickOutput.RIGHT, it.stick)
            assertTrue(it.floating)
            assertEquals(Binding.None, it.click)
            assertEquals(ControlElement.MIN_OPACITY, it.opacity)
        }
        assertEquals(Binding.Key(0x20), els.getValue(3).binding) // k62 space
        assertEquals(Binding.Key(0xA2), els.getValue(4).binding) // k113 left ctrl
        assertEquals(Binding.Key(0x52), els.getValue(5).binding) // k46 R
        assertEquals(Binding.Mouse(MouseKey.LEFT), els.getValue(6).binding)
        els.getValue(7).let { // switch button → toggle right click
            assertEquals(PressMode.TOGGLE, it.mode)
            assertEquals(Binding.Mouse(MouseKey.RIGHT), it.binding)
        }
        els.getValue(8).let { // combine button: value + up value, "null" slots dropped
            assertEquals(ElementKind.COMBO, it.kind)
            assertEquals(listOf(Binding.Key(0xA2), Binding.Key(0xA0)), it.bindings)
            assertEquals(ElementShape.PILL, it.shape)
        }
        els.getValue(9).let { // digital pad with gamepad codes
            assertEquals(ElementKind.DPAD, it.kind)
            assertEquals(PadFlags.DPAD.map { f -> Binding.Pad(f) }, it.bindings)
        }
        els.getValue(10).let { // analog stick, move mode = floating, g64 = L3
            assertEquals(StickOutput.LEFT, it.stick)
            assertTrue(it.floating)
            assertEquals(Binding.Pad(PadFlags.LS_CLK), it.click)
        }
        els.getValue(11).let { // movable button in trackpad mode → touchpad
            assertEquals(ElementKind.TOUCHPAD, it.kind)
            assertEquals(Binding.Mouse(MouseKey.LEFT), it.click)
            near(1.5f, it.sensitivity)
            assertEquals(ElementShape.SQUARE, it.shape)
        }
        assertEquals(Binding.Trigger(Side.LEFT), els.getValue(12).binding)
        assertEquals(Binding.Wheel(true), els.getValue(13).binding)
    }

    @Test
    fun legacyMdatUsesTheFallbackScreenAndConfigName() {
        val r = CrownImport.import(res("apex-fps.mdat"), CrownImport.Basis(2400, 1080, 2.75f), "p2")
        assertFalse(r.basisFromFile)
        assertEquals("Apex FPS", r.profile.name) // from settings.config_name
        assertEquals(bundle().profile.landscape, r.profile.landscape)
        // Made on a portrait screen: it becomes the portrait layout (and the landscape start).
        val tall = CrownImport.import(res("apex-fps.mdat"), CrownImport.Basis(1080, 2400, 2.75f), "p3")
        assertEquals(tall.profile.landscape, tall.profile.portrait)
    }

    @Test
    fun tamperedFilesAreRejectedLikeVPlusDoes() {
        val bundle = JSONObject(res("apex-fps.crown.json"))
        bundle.getJSONObject("profile").put("payloadSha256", "00")
        expectError { CrownImport.import(bundle.toString(), phone, "x") }

        val payload = JSONObject(res("apex-fps.mdat"))
        payload.put("settings", payload.getString("settings").replace("Apex FPS", "Hacked"))
        expectError { CrownImport.import(payload.toString(), phone, "x") }

        expectError { CrownImport.import("""{"kind":"crown-profile-bundle","schemaVersion":2}""", phone, "x") }
        expectError { CrownImport.import("[]", phone, "x") }
    }

    @Test
    fun importEntryPointRecognisesBothCrownWrappers() {
        val a = ProfileImport.parse(res("apex-fps.crown.json"), phone, "a", 0)
        val b = ProfileImport.parse(res("apex-fps.mdat"), CrownImport.Basis(2400, 1080, 2.75f), "b", 0)
        assertEquals(ProfileImport.Source.CROWN, a.source)
        assertEquals(ProfileImport.Source.CROWN, b.source)
        assertEquals(5, a.skipped.size)
        // And the result exports as a Nebula profile that reads back the same.
        assertEquals(a.profile, ProfileJson.importProfile(ProfileJson.exportProfile(a.profile)))
    }

    @Test
    fun valueCodes() {
        assertEquals(Binding.Key(0x1B), CrownImport.mapValue("k111"))
        assertEquals(Binding.Key(0x70), CrownImport.mapValue("k131"))
        assertEquals(Binding.Pad(PadFlags.Y), CrownImport.mapValue("g32768"))
        assertEquals(Binding.Mouse(MouseKey.MIDDLE), CrownImport.mapValue("m2"))
        assertEquals(Binding.Wheel(false), CrownImport.mapValue("SD"))
        assertEquals(Binding.Trigger(Side.RIGHT), CrownImport.mapValue("rt"))
        assertEquals(Binding.None, CrownImport.mapValue("null"))
        val notes = mutableListOf<String>()
        assertEquals(Binding.None, CrownImport.mapValue("OGM", notes))
        assertTrue(notes.single().contains("OGM"))
    }

    private fun near(expected: Float, actual: Float) = assertTrue("expected $expected, got $actual", abs(expected - actual) < 0.002f)

    private fun expectError(block: () -> Unit) {
        try { block(); fail("expected ControlsFormatException") } catch (_: ControlsFormatException) { }
    }
}
