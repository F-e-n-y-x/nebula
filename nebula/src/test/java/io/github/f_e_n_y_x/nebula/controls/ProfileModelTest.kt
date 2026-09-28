package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileModelTest {
    private var n = 0
    private var clock = 1000L
    private val standard = DefaultProfiles.standard()
    private fun lib(d: StoreData = StoreData()) = ProfileLibrary(d, standard, { "p${++n}" }, { clock++ })

    @Test
    fun bindingTokensRoundTrip() {
        val all = listOf(
            Binding.None, Binding.Pad(PadFlags.A), Binding.Pad(PadFlags.PADDLE3), Binding.Trigger(Side.LEFT), Binding.Trigger(Side.RIGHT),
            Binding.Key(0x41), Binding.Mouse(MouseKey.RIGHT), Binding.Mouse(MouseKey.FORWARD), Binding.Wheel(true), Binding.Wheel(false),
        )
        all.forEach { assertEquals(it, Binding.parse(it.token())) }
        assertEquals(Binding.Pad(0x1000), Binding.parse("pad:0x1000"))
        assertEquals(Binding.None, Binding.parse("key:999"))
        assertEquals(Binding.None, Binding.parse("teleport"))
        assertEquals(Binding.None, Binding.parse(null))
    }

    @Test
    fun clampedSizeKeepsElementsUsable() {
        val e = ControlElement("e", ElementKind.BUTTON, 1.4f, -0.2f, 2f, 9000f, opacity = 0f).clampedSize()
        assertEquals(ControlElement.MIN_SIZE_DP, e.width)
        assertEquals(ControlElement.MAX_SIZE_DP, e.height)
        assertEquals(ControlElement.MIN_OPACITY, e.opacity)
        assertEquals(1f, e.x)
        assertEquals(0f, e.y)
    }

    @Test
    fun portraitFallsBackToLandscapeUntilEdited() {
        val p = ControlsProfile("p", "P", landscape = listOf(newElement(ElementKind.BUTTON, "a")))
        assertSame(p.landscape, p.layout(LayoutOrientation.PORTRAIT))
        val edited = p.withLayout(LayoutOrientation.PORTRAIT, emptyList())
        assertTrue(edited.layout(LayoutOrientation.PORTRAIT).isEmpty())
        assertEquals(1, edited.layout(LayoutOrientation.LANDSCAPE).size)
    }

    @Test
    fun standardProfileFollowsSettings() {
        val plain = DefaultProfiles.standard()
        assertTrue(plain.isBuiltIn)
        val ids = plain.landscape.map { it.id }
        assertTrue(ids.containsAll(listOf("a", "b", "x", "y", "lb", "rb", "lt", "rt", "dpad", "ls", "rs", "select", "start", "guide")))
        assertEquals(Binding.Pad(PadFlags.LS_CLK), plain.landscape.first { it.id == "ls" }.click)
        // Every element sits inside the screen in both orientations.
        (plain.landscape + plain.portrait!!).forEach { assertTrue(it.id, it.x in 0f..1f && it.y in 0f..1f) }
        // Portrait keeps the controls under the picture.
        assertTrue(plain.portrait!!.all { it.y > 0.45f })

        val noGuide = DefaultProfiles.standard(StandardOptions(showGuide = false, l3r3Buttons = true))
        assertTrue(noGuide.landscape.none { it.id == "guide" })
        assertEquals(Binding.None, noGuide.landscape.first { it.id == "ls" }.click)
        assertEquals(Binding.Pad(PadFlags.RS_CLK), noGuide.landscape.first { it.id == "r3" }.binding)

        val ds = DefaultProfiles.standard(StandardOptions(style = PadStyle.DUALSENSE))
        assertEquals("✕", ds.landscape.first { it.id == "a" }.label)
        assertEquals(Binding.Pad(PadFlags.A), ds.landscape.first { it.id == "a" }.binding)
        assertEquals("B", DefaultProfiles.standard(StandardOptions(style = PadStyle.SWITCH)).landscape.first { it.id == "a" }.label)
    }

    @Test
    fun savingTheBuiltInMakesAnEditableCopyAndMovesTheDefault() {
        val edited = standard.withLayout(LayoutOrientation.LANDSCAPE, standard.landscape.drop(1))
        val (l, saved) = lib().save(edited)
        assertFalse(saved.isBuiltIn)
        assertEquals("My controls", saved.name)
        assertEquals(saved.id, l.data.activeProfileId)
        assertEquals(saved, l.resolve("host:game"))
        assertEquals(standard.landscape.size - 1, saved.landscape.size)
        // Saving again updates in place.
        val (l2, again) = l.save(saved.copy(landscape = emptyList()))
        assertEquals(saved.id, again.id)
        assertEquals(1, l2.data.profiles.size)
        assertTrue(again.updatedAtMs > saved.updatedAtMs)
    }

    @Test
    fun renameDuplicateDelete() {
        val (l1, a) = lib().add(ControlsProfile("x", "Racing", emptyList()))
        val l2 = l1.rename(a.id, "  Racing wheel  ")
        assertEquals("Racing wheel", l2.find(a.id)!!.name)
        assertSame(l2, l2.rename(a.id, "   "))
        assertSame(l2, l2.rename(ControlsProfile.STANDARD_ID, "Nope"))

        val (l3, copy) = l2.duplicate(a.id)!!
        assertEquals("Racing wheel copy", copy.name)
        assertNotEquals(a.id, copy.id)
        val (l4, copy2) = l3.duplicate(a.id)!!
        assertEquals("Racing wheel copy 2", copy2.name)

        val l5 = l4.setDefault(copy.id).assign("h:forza", copy.id).assign("h:gt", a.id)
        val l6 = l5.delete(copy.id)
        assertNull(l6.find(copy.id))
        assertEquals(ControlsProfile.STANDARD_ID, l6.data.activeProfileId)
        assertNull(l6.assignedTo("h:forza"))
        assertEquals(a.id, l6.assignedTo("h:gt"))
        // The built-in can't be deleted.
        assertSame(l6, l6.delete(ControlsProfile.STANDARD_ID))
        assertEquals(listOf("Standard gamepad", "GTA V: controller + touch camera (right half)", "GTA V: controller + mouse camera (right half)", "Racing wheel", "Racing wheel copy 2"), l6.all.map { it.name })
    }

    @Test
    fun perGameAssignmentWinsOverDefault() {
        val (l1, a) = lib().add(ControlsProfile("x", "FPS", emptyList()))
        val (l2, b) = l1.add(ControlsProfile("y", "Menus", emptyList()))
        val l3 = l2.setDefault(b.id).assign("pc:doom", a.id)
        assertEquals(a, l3.resolve("pc:doom"))
        assertEquals(b, l3.resolve("pc:tetris"))
        assertEquals(b, l3.resolve(null))
        assertEquals(b, l3.assign("pc:doom", null).resolve("pc:doom"))
        // A dangling id (profile deleted elsewhere) falls back instead of crashing.
        val dangling = ProfileLibrary(l3.data.copy(games = mapOf("pc:doom" to "gone"), activeProfileId = "gone"), standard, { "z" }, { 0 })
        assertEquals(standard, dangling.resolve("pc:doom"))
    }

    @Test
    fun importedNamesAreMadeUnique() {
        val (l1, a) = lib().add(ControlsProfile("x", "Standard gamepad", emptyList()))
        assertEquals("Standard gamepad 2", a.name)
        val (_, b) = l1.add(ControlsProfile("x", "   ", emptyList()))
        assertEquals("Imported controls", b.name)
    }

    @Test
    fun gtaPresetIsAReadOnlyTouchCameraKeptWithTheController() {
        val l = lib()
        val gta = l.find(ControlsProfile.GTA_ID)!!
        assertTrue(gta.isBuiltIn)
        assertTrue(gta.hasControllerZones())
        val zone = gta.landscape.single()
        assertEquals(ElementKind.ZONE, zone.kind)
        assertEquals(ZoneType.CAMERA_STICK, zone.zone)
        assertEquals(StickOutput.RIGHT, zone.stick)
        assertEquals(0.75f, zone.x); assertEquals(0.5f, zone.width); assertEquals(1f, zone.height)
        // Assignable to a game as-is; editing it saves a copy.
        assertEquals(gta, l.assign("pc:gta5", gta.id).resolve("pc:gta5"))
        assertSame(l, l.delete(gta.id))
        val (_, copy) = l.save(gta.copy(landscape = listOf(zone.copy(sensitivity = 2f))))
        assertFalse(copy.isBuiltIn)
        assertTrue(copy.name.startsWith("My GTA V"))
        assertFalse(DefaultProfiles.standard().hasControllerZones())
    }

    @Test
    fun zonesAreSizedAsAShareOfTheScreen() {
        val z = newElement(ElementKind.ZONE, "z").copy(width = 3f, height = 0.01f).clampedSize()
        assertEquals(1f, z.width)
        assertEquals(ControlElement.MIN_ZONE, z.height)
        val halfHeight = DefaultProfiles.standard(StandardOptions(portraitHalfHeight = false))
        assertNull(halfHeight.portrait)
    }
}
