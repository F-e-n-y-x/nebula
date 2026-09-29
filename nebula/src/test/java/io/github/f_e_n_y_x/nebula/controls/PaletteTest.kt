package io.github.f_e_n_y_x.nebula.controls

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The Add palette, placing ready-made groups, and group editing (move, scale, split). */
class PaletteTest {
    private val phoneLand = 780f to 340f
    private val phonePortrait = 370f to 760f

    private fun item(id: String) = Palette.byId[id] ?: error("no palette item $id")
    private fun counter(): () -> String { var n = 0; return { "e${++n}" } }
    private fun ControlElement.pads() = bindings.filterIsInstance<Binding.Pad>().map { it.flag }
    private fun ControlElement.keys() = bindings.filterIsInstance<Binding.Key>().map { it.vk }

    // ---------------------------------------------------------------- catalogue

    @Test
    fun everyElementKindCanBeAdded() {
        val kinds = Palette.items.flatMap { it.parts }.map { it.kind }.toSet()
        assertEquals(ElementKind.entries.toSet(), kinds)
    }

    @Test
    fun everyZoneTypeAndStickOutputCanBeAdded() {
        val parts = Palette.items.flatMap { it.parts }
        assertEquals(ZoneType.entries.toSet(), parts.filter { it.kind == ElementKind.ZONE }.map { it.zone }.toSet())
        assertEquals(StickOutput.entries.toSet(), parts.filter { it.kind == ElementKind.STICK }.map { it.stick }.toSet())
    }

    @Test
    fun idsAreUniqueAndEveryCategoryHasItems() {
        assertEquals(Palette.items.size, Palette.items.map { it.id }.toSet().size)
        PaletteCategory.entries.forEach { c -> assertTrue(c.label, Palette.inCategory(c).isNotEmpty()) }
        // Groups are all in Groups, singles never are; group ids are valid group-id prefixes.
        Palette.items.forEach { assertEquals(it.id, it.isGroup, it.category == PaletteCategory.GROUPS) }
        Palette.items.filter { it.isGroup }.forEach { assertTrue(ProfileJson.GROUP_ID.matches("${it.id}:e-12345678")) }
    }

    @Test
    fun everyPadButtonAndPickerKeyIsOffered() {
        val singles = Palette.items.filterNot { it.isGroup }.map { it.parts.single() }
        val flags = singles.flatMap { it.pads() }.toSet()
        assertTrue(PadFlags.names.keys.all { it in flags })
        val keys = singles.flatMap { it.keys() }.toSet()
        assertTrue(VirtualKeys.pickerGroups.flatMap { it.second }.all { it in keys })
        val mouse = singles.flatMap { it.bindings }.filterIsInstance<Binding.Mouse>().map { it.button }.toSet()
        assertEquals(MouseKey.entries.toSet(), mouse)
    }

    @Test
    fun requiredGroupsHoldTheRightControls() {
        assertEquals(listOf(PadFlags.A, PadFlags.B, PadFlags.X, PadFlags.Y), item("abxy").parts.flatMap { it.pads() })
        assertEquals(PadFlags.DPAD, item("dpad-cluster").parts.flatMap { it.pads() })
        item("shoulders").parts.let { p ->
            assertEquals(listOf(PadFlags.LB, PadFlags.RB), p.flatMap { it.pads() })
            assertEquals(listOf(Side.LEFT, Side.RIGHT), p.flatMap { it.bindings }.filterIsInstance<Binding.Trigger>().map { it.side })
        }
        item("left-stick").parts.let { p ->
            assertEquals(StickOutput.LEFT, p.single { it.kind == ElementKind.STICK }.stick)
            assertEquals(listOf(PadFlags.LS_CLK), p.single { it.kind == ElementKind.BUTTON }.pads())
        }
        assertEquals(listOf(0x57, 0x41, 0x53, 0x44), item("wasd").parts.flatMap { it.keys() })
        assertEquals(setOf(0x25, 0x26, 0x27, 0x28), item("arrows").parts.flatMap { it.keys() }.toSet())
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), item("numbers").parts.map { it.label })
        assertEquals((0x70..0x7B).toList(), item("fkeys").parts.flatMap { it.keys() })
        assertEquals(setOf(0x1B, 0x09, 0x0D, 0x20, 0x10, 0x11, 0x12), item("system-keys").parts.flatMap { it.keys() }.toSet())
        item("mouse-buttons").parts.flatMap { it.bindings }.let { b ->
            assertEquals(setOf(MouseKey.LEFT, MouseKey.MIDDLE, MouseKey.RIGHT), b.filterIsInstance<Binding.Mouse>().map { it.button }.toSet())
            assertEquals(setOf(true, false), b.filterIsInstance<Binding.Wheel>().map { it.up }.toSet())
        }
    }

    @Test
    fun groupPartsDontOverlap() {
        Palette.items.filter { it.isGroup }.forEach { g ->
            val p = g.parts
            for (i in p.indices) for (j in i + 1 until p.size) {
                val a = p[i]; val b = p[j]
                val overlapX = kotlin.math.abs(a.x - b.x) < (a.width + b.width) / 2
                val overlapY = kotlin.math.abs(a.y - b.y) < (a.height + b.height) / 2
                assertFalse("${g.id}: ${a.id} overlaps ${b.id}", overlapX && overlapY)
            }
        }
    }

    // ---------------------------------------------------------------- search

    @Test
    fun searchMatchesTitlesKeywordsAndLabels() {
        assertTrue(Palette.search("abxy").any { it.id == "abxy" })
        assertTrue(Palette.search("F5").any { it.id == "key-${0x74}" })
        assertTrue(Palette.search("wheel").any { it.id == "mouse-buttons" })
        assertTrue(Palette.search("scroll").any { it.id == "wheel-up" })
        assertTrue(Palette.search("zone look").all { it.parts.any { p -> p.kind == ElementKind.ZONE } })
        assertTrue(Palette.search("  ").size == Palette.items.size)
        assertTrue(Palette.search("no such control xyz").isEmpty())
        // A category narrows the search.
        assertTrue(Palette.search("A", PaletteCategory.GAMEPAD).all { it.category == PaletteCategory.GAMEPAD })
        assertTrue(Palette.search("esc", PaletteCategory.GROUPS).any { it.id == "system-keys" })
    }

    // ---------------------------------------------------------------- placing

    @Test
    fun placedGroupSharesOneGroupIdAndKeepsItsShape() {
        val (w, h) = phoneLand
        val placed = Palette.place(item("abxy"), w, h, counter(), "abxy:g1", portrait = false)
        assertEquals(4, placed.size)
        assertTrue(placed.all { it.group == "abxy:g1" })
        assertEquals(4, placed.map { it.id }.toSet().size)
        val a = placed[0]; val y = placed[3]
        // A below Y by 112 dp (as a share of the height), same column.
        assertEquals(a.x, y.x, 1e-4f)
        assertEquals(112f / h, a.y - y.y, 1e-4f)
        assertEquals(52f, a.width)
    }

    @Test
    fun placedGroupsStayOnScreenAndShrinkToFit() {
        for ((w, h) in listOf(phoneLand, phonePortrait, 1200f to 800f)) {
            Palette.items.filter { it.isGroup }.forEach { g ->
                val placed = Palette.place(g, w, h, counter(), "${g.id}:x", portrait = h > w)
                placed.forEach { e ->
                    assertTrue("${g.id} ${e.id} left edge in ${w}x$h", e.x * w - e.width / 2 >= -0.5f)
                    assertTrue("${g.id} ${e.id} right edge in ${w}x$h", e.x * w + e.width / 2 <= w + 0.5f)
                    assertTrue("${g.id} ${e.id} top edge in ${w}x$h", e.y * h - e.height / 2 >= -0.5f)
                    assertTrue("${g.id} ${e.id} bottom edge in ${w}x$h", e.y * h + e.height / 2 <= h + 0.5f)
                    assertTrue(e.width >= ControlElement.MIN_SIZE_DP)
                }
            }
        }
        // The number row is 482 dp wide: it shrinks on a portrait phone, not on a landscape one.
        val narrow = Palette.place(item("numbers"), phonePortrait.first, phonePortrait.second, counter(), "numbers:x", portrait = true)
        assertTrue(narrow[0].width < 44f)
        val wide = Palette.place(item("numbers"), phoneLand.first, phoneLand.second, counter(), "numbers:x", portrait = false)
        assertEquals(44f, wide[0].width)
    }

    @Test
    fun aGroupDoesNotLandOnExistingControls() {
        val (w, h) = phoneLand
        val first = Palette.place(item("abxy"), w, h, counter(), "abxy:1", portrait = false)
        val second = Palette.place(item("abxy"), w, h, counter(), "abxy:2", portrait = false, occupied = first)
        fun box(e: ControlElement) = floatArrayOf(e.x * w - e.width / 2, e.y * h - e.height / 2, e.x * w + e.width / 2, e.y * h + e.height / 2)
        for (a in first) for (b in second) {
            val p = box(a); val q = box(b)
            assertFalse("${a.id} vs ${b.id}", p[0] < q[2] && p[2] > q[0] && p[1] < q[3] && p[3] > q[1])
        }
        // Same shape, just moved, and still on screen.
        assertEquals(first[0].x - first[3].x, second[0].x - second[3].x, 1e-4f)
        assertEquals(first[0].y - first[3].y, second[0].y - second[3].y, 1e-4f)
        // Zones (the camera half) don't count as taken.
        val zone = newElement(ElementKind.ZONE, "z").copy(x = 0.5f, width = 1f, height = 1f)
        val third = Palette.place(item("abxy"), w, h, counter(), "abxy:3", portrait = false, occupied = listOf(zone))
        assertEquals(first.map { it.x }, third.map { it.x })
    }

    @Test
    fun aFullScreenKeepsTheAnchor() {
        // Nowhere free: the item still lands at its anchor rather than failing.
        val wall = (0 until 20).flatMap { i -> (0 until 10).map { j -> ControlElement("w$i-$j", ElementKind.BUTTON, (i + 0.5f) / 20, (j + 0.5f) / 10, 40f, 34f) } }
        val placed = Palette.place(item("pad-A"), 800f, 340f, counter(), occupied = wall)
        assertEquals(item("pad-A").anchorX, placed.single().x, 1e-4f)
    }

    @Test
    fun portraitPlacesInTheLowerPart() {
        val placed = Palette.place(item("abxy"), phonePortrait.first, phonePortrait.second, counter(), "abxy:x", portrait = true)
        assertTrue(placed.all { it.y > 0.5f })
    }

    @Test
    fun singlesAndZonesHaveNoGroup() {
        val one = Palette.place(item("pad-A"), 800f, 400f, counter(), "ignored")
        assertNull(one.single().group)
        val zone = Palette.place(item("zone-camera-mouse"), 800f, 400f, counter()).single()
        assertEquals(ZoneType.CAMERA_MOUSE, zone.zone)
        assertEquals(0.75f, zone.x)
        assertEquals(0.5f, zone.width) // a share of the screen, not shrunk
    }

    // ---------------------------------------------------------------- editing groups

    private fun editorWith(id: String): Pair<LayoutEditor, String> {
        val ed = LayoutEditor(emptyList(), counter())
        ed.addItem(item(id), phoneLand.first, phoneLand.second, portrait = false)
        return ed to ed.selectedGroup!!
    }

    @Test
    fun addingAGroupSelectsItAndIsOneUndoStep() {
        val (ed, g) = editorWith("wasd")
        assertTrue(g.startsWith("wasd:"))
        assertNull(ed.selectedId)
        assertEquals(4, ed.selectedMembers.size)
        assertEquals("WASD keys", groupTitle(g))
        ed.undo()
        assertTrue(ed.elements.isEmpty())
        assertNull(ed.selectedGroup)
        // A single item selects the element instead.
        ed.addItem(item("pad-lb"), 800f, 400f)
        assertNotNull(ed.selectedId)
        assertNull(ed.selectedGroup)
    }

    @Test
    fun selectionIsElementOrGroupNeverBoth() {
        val (ed, g) = editorWith("abxy")
        val first = ed.elements.first().id
        ed.select(first)
        assertNull(ed.selectedGroup)
        ed.selectGroup(g)
        assertNull(ed.selectedId)
        ed.selectGroup("nope:1")
        assertNull(ed.selectedGroup)
    }

    @Test
    fun movingAGroupMovesEveryMemberAsOneStep() {
        val (ed, g) = editorWith("abxy")
        val before = ed.elements
        ed.beginGesture()
        ed.moveGroup(g, -0.1f, 0.05f)
        ed.moveGroup(g, -0.2f, 0.02f) // relative to the gesture start, not cumulative
        ed.endGesture()
        ed.elements.zip(before).forEach { (now, was) ->
            assertEquals(was.x - 0.2f, now.x, 1e-5f)
            assertEquals(was.y + 0.02f, now.y, 1e-5f)
        }
        ed.undo()
        assertEquals(before, ed.elements)
    }

    @Test
    fun scalingAGroupScalesSizesAndGaps() {
        val (ed, g) = editorWith("wasd")
        val before = ed.elements
        val w0 = before[0].x - before[1].x // W is right of A by 54 dp
        ed.beginGesture()
        ed.scaleGroup(g, 1.5f, phoneLand.first, phoneLand.second)
        ed.scaleGroup(g, 1.25f, phoneLand.first, phoneLand.second) // from the start, not compounded
        ed.endGesture()
        val now = ed.elements
        assertEquals(60f, now[0].width, 1e-3f)
        assertEquals(w0 * 1.25f, now[0].x - now[1].x, 1e-4f)
        // The group's centre stays put.
        assertEquals(before.map { it.x }.average(), now.map { it.x }.average(), 1e-4)
        ed.undo()
        assertEquals(before, ed.elements)
    }

    @Test
    fun scalingStopsAtTheSizeLimits() {
        val (ed, g) = editorWith("abxy")
        ed.beginGesture(); ed.scaleGroup(g, 0.01f, 780f, 340f); ed.endGesture()
        assertTrue(ed.elements.all { it.width >= ControlElement.MIN_SIZE_DP - 1e-3f })
        // All members shrank by the same factor, so ABXY keeps its shape.
        assertEquals(ControlElement.MIN_SIZE_DP, ed.elements[0].width, 1e-3f)
        ed.beginGesture(); ed.scaleGroup(g, 100f, 780f, 340f); ed.endGesture()
        assertTrue(ed.elements.all { it.width <= ControlElement.MAX_SIZE_DP })
    }

    @Test
    fun scaleLimitsComeFromTheSmallestAndLargestMember() {
        val m = listOf(
            ControlElement("a", ElementKind.BUTTON, 0f, 0f, 56f, 28f * 2),
            ControlElement("b", ElementKind.STICK, 0f, 0f, 180f, 180f),
        )
        val r = groupScaleLimits(m)
        assertEquals(0.5f, r.start, 1e-4f)
        assertEquals(2f, r.endInclusive, 1e-4f)
    }

    @Test
    fun splittingMakesLooseElements() {
        val (ed, g) = editorWith("numbers")
        ed.splitGroup(g)
        assertTrue(ed.elements.all { it.group == null })
        assertNull(ed.selectedGroup)
        assertEquals(10, ed.elements.size)
        ed.undo()
        assertTrue(ed.elements.all { it.group == g })
    }

    @Test
    fun leavingOrDeletingDownToOneMemberDissolvesTheGroup() {
        val (ed, g) = editorWith("left-shoulder") // two members
        ed.removeFromGroup(ed.elements[0].id)
        assertTrue(ed.elements.all { it.group == null })
        ed.undo()
        ed.delete(ed.elements[0].id)
        assertNull(ed.elements.single().group)
        ed.undo()
        assertTrue(ed.elements.all { it.group == g })
        // With more members, only the one leaves.
        val (ed2, g2) = editorWith("abxy")
        ed2.removeFromGroup(ed2.elements[0].id)
        assertNull(ed2.elements[0].group)
        assertEquals(3, ed2.elements.count { it.group == g2 })
    }

    @Test
    fun duplicateAndDeleteWholeGroups() {
        val (ed, g) = editorWith("arrows")
        val copies = ed.duplicateGroup(g)
        assertEquals(4, copies.size)
        val g2 = copies[0].group!!
        assertNotEquals(g, g2)
        assertTrue(g2.startsWith("arrows:"))
        assertEquals(g2, ed.selectedGroup)
        assertEquals(8, ed.elements.map { it.id }.toSet().size)
        ed.deleteGroup(g)
        assertEquals(4, ed.elements.size)
        assertTrue(ed.elements.all { it.group == g2 })
        ed.deleteGroup(g2)
        assertTrue(ed.elements.isEmpty())
        assertNull(ed.selectedGroup)
        ed.undo(); ed.undo()
        assertEquals(8, ed.elements.size)
    }

    @Test
    fun duplicatingOneMemberGivesALooseCopy() {
        val (ed, _) = editorWith("abxy")
        val copy = ed.duplicate(ed.elements[0].id)!!
        assertNull(copy.group)
    }

    @Test
    fun groupEditsAreOneStep() {
        val (ed, g) = editorWith("abxy")
        ed.editGroup(g, "opacity") { it.copy(opacity = 0.6f) }
        ed.editGroup(g, "opacity") { it.copy(opacity = 0.5f) }
        assertTrue(ed.elements.all { it.opacity == 0.5f })
        ed.undo()
        assertTrue(ed.elements.all { it.opacity == 1f })
    }

    // ---------------------------------------------------------------- storage

    @Test
    fun groupIdSurvivesStoreAndLayoutFile() {
        val (ed, g) = editorWith("mouse-buttons")
        ed.addItem(item("pad-A"), 780f, 340f)
        val p = ControlsProfile("p1", "Mine", ed.elements)
        val stored = ProfileJson.profileFromJson(ProfileJson.profileToJson(p))
        assertEquals(p.landscape, stored.landscape)
        val file = LayoutFile.encode(p)
        val back = LayoutFile.parse(file, "p-x", 1).profile
        assertEquals(p.landscape.map { it.group }, back.landscape.map { it.group })
        assertEquals(5, back.landscape.count { it.group == g })
        // Loose elements carry no group key at all.
        assertFalse(ProfileJson.elementToJson(p.landscape.last()).has("group"))
    }

    @Test
    fun layoutFileRejectsABadGroupId() {
        val p = ControlsProfile("p1", "Mine", listOf(newElement(ElementKind.BUTTON, "a").copy(group = "ok:1")))
        val o = JSONObject(LayoutFile.encode(p))
        o.getJSONArray("landscape").getJSONObject(0).put("group", "has spaces!")
        try {
            LayoutFile.parse(o.toString())
            fail("accepted a bad group id")
        } catch (e: ControlsFormatException) {
            assertTrue(e.message.orEmpty().contains("group"))
        }
        // The store just drops one it can't use.
        val el = ProfileJson.elementToJson(p.landscape[0]).put("group", "has spaces!")
        assertNull(ProfileJson.elementFromJson(el)!!.group)
    }
}
