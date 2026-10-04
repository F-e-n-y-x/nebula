package io.github.f_e_n_y_x.nebula.controls

import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData
import io.github.f_e_n_y_x.nebula.ui.screens.MenuSection
import io.github.f_e_n_y_x.nebula.ui.screens.MenuSections
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun btn(id: String, x: Float, y: Float, w: Float = 56f, h: Float = 56f) =
    ControlElement(id, ElementKind.BUTTON, x, y, w, h, label = id.uppercase(), bindings = listOf(Binding.Pad(PadFlags.A)))

private fun counter(): () -> String { var n = 0; return { "p-n${++n}" } }

private fun lib(d: StoreData = StoreData()) = ProfileLibrary(d, DefaultProfiles.standard(), counter(), { 1000L })

private val foot = ControlsProfile("p-foot", "On foot", listOf(btn("a", 0.85f, 0.7f)))
private val car = ControlsProfile("p-car", "Vehicle", listOf(btn("b", 0.15f, 0.7f)))
private val loose = ControlsProfile("p-loose", "Racing wheel", listOf(btn("x", 0.5f, 0.5f)))

private fun withSet(): Pair<ProfileLibrary, LayoutSet> =
    lib(StoreData(profiles = listOf(foot, car, loose))).createSet("GTA V", listOf(foot.id, car.id))!!

// ---------------------------------------------------------------- grouping

class ProfileGroupingTest {
    @Test
    fun `a set is one item and its layouts are not listed loose`() {
        val (l, set) = withSet()
        val groups = l.groups()
        assertEquals(ControlsProfile.STANDARD_ID, (groups.first() as ProfileGroup.Single).profile.id)
        val sets = groups.filterIsInstance<ProfileGroup.SetItem>()
        assertEquals(listOf(set.id), sets.map { it.set.id })
        assertEquals(listOf(foot.id, car.id), sets.single().layouts.map { it.id })
        assertEquals("GTA V · 2 layouts", sets.single().title)
        val singles = groups.filterIsInstance<ProfileGroup.Single>().map { it.profile.id }
        assertEquals(listOf(ControlsProfile.STANDARD_ID, loose.id), singles)
        // Every profile is still reachable: nothing was dropped by grouping.
        assertEquals(l.all.map { it.id }.toSet(), (singles + sets.flatMap { g -> g.layouts.map { it.id } }).toSet())
        // Order: built-ins, sets, then the user's loose profiles.
        assertTrue(groups[1] is ProfileGroup.SetItem)
    }

    @Test
    fun `a set lists by the name before the dot, unless two would read the same`() {
        val (l, _) = withSet()
        val (l2, _) = l.renameSet(l.sets.single().id, "GTA V · on foot, vehicle, aircraft").createSet("Driving · wheel", listOf(loose.id))!!
        assertEquals(listOf("Driving · 1 layout", "GTA V · 2 layouts"), l2.groups().filterIsInstance<ProfileGroup.SetItem>().map { it.title })
        val (l3, _) = l2.createSet("GTA V · keyboard", listOf(foot.id))!!
        assertTrue(l3.groups().filterIsInstance<ProfileGroup.SetItem>().map { it.title }.containsAll(listOf("GTA V · keyboard · 1 layout", "GTA V · on foot, vehicle, aircraft · 2 layouts")))
    }

    @Test
    fun `a layout in two sets shows in both, and once a set is gone its layouts are loose again`() {
        val (l, a) = withSet()
        val (l2, b) = l.createSet("Driving", listOf(car.id))!!
        val sets = l2.groups().filterIsInstance<ProfileGroup.SetItem>()
        assertEquals(2, sets.size)
        assertTrue(sets.all { g -> g.layouts.any { it.id == car.id } })
        val l3 = l2.deleteSet(a.id)
        val singles = l3.groups().filterIsInstance<ProfileGroup.Single>().map { it.profile.id }
        assertTrue(foot.id in singles)
        assertFalse(car.id in singles) // still in Driving
        assertEquals(b.id, l3.setOf(car.id)?.id)
        assertNull(l3.setOf(foot.id))
    }

    @Test
    fun `migration - an older store whose set layouts were shown loose regroups with nothing lost`() {
        // As 0.4.0-dev23 wrote it: three profiles, one set over two of them, a game and the default on the set.
        val old = JSONObject()
            .put("kind", ProfileJson.STORE_KIND).put("schemaVersion", 1)
            .put("activeProfileId", "set-gta")
            .put("profiles", JSONArray().put(ProfileJson.profileToJson(foot)).put(ProfileJson.profileToJson(car)).put(ProfileJson.profileToJson(loose)))
            .put("games", JSONObject().put("host:gta", "set-gta").put("host:race", loose.id))
            .put("sets", JSONArray().put(JSONObject().put("id", "set-gta").put("name", "GTA V").put("members", JSONArray(listOf(foot.id, car.id))).put("start", car.id)))
            .toString()
        val d = ProfileJson.decodeStore(old)
        val l = lib(d)
        val groups = l.groups()
        assertEquals(listOf("set-gta"), groups.filterIsInstance<ProfileGroup.SetItem>().map { it.set.id })
        assertEquals(listOf(ControlsProfile.STANDARD_ID, loose.id), groups.filterIsInstance<ProfileGroup.Single>().map { it.profile.id })
        // The stored data is untouched: same profiles, assignments, set.
        assertEquals(3, d.profiles.size)
        assertEquals(car.id, l.resolve("host:gta").id)
        assertEquals(loose.id, l.resolve("host:race").id)
        assertEquals("set-gta", l.resolveSet(null)?.id)
        assertEquals(d, ProfileJson.decodeStore(ProfileJson.encodeStore(d)))
    }
}

// ---------------------------------------------------------------- the switch button

class SwitchPlacementTest {
    private val area = SwitchPlacement.area(LayoutOrientation.LANDSCAPE)

    @Test
    fun `an empty layout gets the switch at the top centre`() {
        val (x, y) = SwitchPlacement.place(emptyList(), area.first, area.second)
        assertEquals(0.5f, x, 0.001f)
        assertTrue(y < 0.1f)
    }

    @Test
    fun `the switch keeps clear of controls at the top centre, and look areas don't count`() {
        val (aw, ah) = area
        val blocker = btn("map", 0.5f, 0.06f, 120f, 50f)
        val look = newElement(ElementKind.ZONE, "look") // the right half, full height
        val (x, y) = SwitchPlacement.place(listOf(blocker, look), aw, ah)
        // Not over the blocker (with its gap).
        val dx = kotlin.math.abs(x * aw - 0.5f * aw)
        val dy = kotlin.math.abs(y * ah - 0.06f * ah)
        assertTrue("overlaps: x=$x y=$y", dx >= (SwitchPlacement.W + 120f) / 2 || dy >= (SwitchPlacement.H + 50f) / 2)
        // Still near the top centre.
        assertTrue(y < 0.5f)
        assertTrue(kotlin.math.abs(x - 0.5f) <= 0.31f)
    }

    @Test
    fun `withSwitch adds one switch with a free id, only when there is none`() {
        val els = listOf(btn("switch", 0.1f, 0.9f))
        val out = SwitchPlacement.withSwitch(els, LayoutOrientation.LANDSCAPE)
        assertEquals(2, out.size)
        val sw = out.last()
        assertEquals(ElementKind.SWITCH, sw.kind)
        assertEquals("switch-2", sw.id)
        assertEquals(SwitchTarget.Next, sw.switchTo)
        assertTrue(SwitchPlacement.withSwitch(out, LayoutOrientation.LANDSCAPE) === out)
    }

    @Test
    fun `ensure covers the portrait layout when it has its own`() {
        val p = car.copy(portrait = listOf(btn("b", 0.5f, 0.8f)))
        val e = SwitchPlacement.ensure(p, SwitchTarget.Picker)
        assertTrue(SwitchPlacement.hasSwitch(e.landscape))
        assertTrue(SwitchPlacement.hasSwitch(e.portrait!!))
        assertEquals(SwitchTarget.Picker, e.portrait!!.last().switchTo)
        // No portrait of its own: none is made up.
        assertNull(SwitchPlacement.ensure(car).portrait)
        // Already has one: unchanged.
        assertTrue(SwitchPlacement.ensure(e) === e)
    }

    @Test
    fun `a crowded top still yields a switch`() {
        val (aw, ah) = area
        val wall = (0 until 40).map { i -> btn("w$i", (i % 10) / 9f, (i / 10) * 0.13f + 0.02f, 90f, 60f) }
        val (x, y) = SwitchPlacement.place(wall, aw, ah)
        assertTrue(x in 0f..1f && y in 0f..1f)
    }
}

// ---------------------------------------------------------------- new set flow

class NewSetTest {
    @Test
    fun `newSet makes new, copied and moved layouts, each with a switch`() {
        val base = lib(StoreData(profiles = listOf(foot, car, loose)))
        val (l, set) = base.newSet(
            "  My GTA  ",
            listOf(SetSource.Blank("Aircraft"), SetSource.Copy(foot.id, "On foot 2"), SetSource.Existing(car.id)),
            start = 2, inCycle = listOf(false, true, true),
        )!!
        assertEquals("My GTA", set.name)
        assertEquals(3, set.members.size)
        val layouts = set.members.map { l.find(it)!! }
        assertEquals(listOf("Aircraft", "On foot 2", "Vehicle"), layouts.map { it.name })
        assertTrue(layouts.all { SwitchPlacement.hasSwitch(it.landscape) })
        // The new one starts from the standard controller (plus its switch).
        assertEquals(DefaultProfiles.standard().landscape.size + 1, layouts[0].landscape.size)
        // Existing: moved in, not copied; it is the same profile id, now with a switch.
        assertEquals(car.id, set.members[2])
        assertEquals(car.landscape.size + 1, l.find(car.id)!!.landscape.size)
        // The source of the copy is left alone.
        assertEquals(foot.landscape, l.find(foot.id)!!.landscape)
        assertEquals(car.id, set.startId())
        assertEquals(listOf(set.members[1], set.members[2]), set.cycleOrder())
        // Grouped: the moved and new layouts are no longer loose; the copied-from profile still is.
        val singles = l.groups().filterIsInstance<ProfileGroup.Single>().map { it.profile.id }
        assertTrue(foot.id in singles)
        assertFalse(car.id in singles)
    }

    @Test
    fun `moving Standard in copies it, and an empty set is refused`() {
        val (l, set) = lib().newSet("S", listOf(SetSource.Existing(ControlsProfile.STANDARD_ID)))!!
        val p = l.find(set.members.single())!!
        assertFalse(p.isBuiltIn)
        assertTrue(SwitchPlacement.hasSwitch(p.landscape))
        assertNull(lib().newSet("S", listOf(SetSource.Existing("p-nope"))))
        assertNull(lib().newSet("S", emptyList()))
    }

    @Test
    fun `the switch can be a picker, and layouts that have a switch keep theirs`() {
        val withOwn = foot.copy(landscape = foot.landscape + newElement(ElementKind.SWITCH, "mine", 0.2f, 0.1f).copy(switchTo = SwitchTarget.Previous))
        val (l, set) = lib(StoreData(profiles = listOf(withOwn))).newSet("S", listOf(SetSource.Existing(withOwn.id), SetSource.Blank("B")), switchTo = SwitchTarget.Picker)!!
        assertEquals(withOwn.landscape, l.find(withOwn.id)!!.landscape)
        assertEquals(SwitchTarget.Picker, l.find(set.members[1])!!.landscape.last().switchTo)
    }

    @Test
    fun `at most eight layouts`() {
        val (_, set) = lib().newSet("Big", (1..10).map { SetSource.Blank("L$it") })!!
        assertEquals(LayoutSet.MAX_LAYOUTS, set.members.size)
    }

    @Test
    fun `addToSet, renameSet and deleting a set with its layouts`() {
        val (l, set) = withSet()
        val (l2, added) = l.addToSet(set.id, SetSource.Blank("Aircraft"))!!
        assertEquals(listOf(foot.id, car.id, added.id), l2.findSet(set.id)!!.members)
        assertTrue(SwitchPlacement.hasSwitch(added.landscape))
        // A set that lists its cycle gets the new layout in it too.
        val cyc = l2.updateSet(l2.findSet(set.id)!!.copy(cycle = listOf(foot.id)))
        val (l3, plane) = cyc.addToSet(set.id, SetSource.Copy(loose.id))!!
        assertEquals(listOf(foot.id, plane.id), l3.findSet(set.id)!!.cycleOrder())

        val renamed = l3.renameSet(set.id, "Los Santos")
        assertEquals("Los Santos", renamed.findSet(set.id)!!.name)
        assertEquals(renamed, renamed.renameSet(set.id, "   "))

        val assigned = renamed.assign("h:g", set.id).assign("h:car", car.id)
        val gone = assigned.deleteSet(set.id, withLayouts = true)
        assertNull(gone.findSet(set.id))
        assertNull(gone.find(foot.id))
        assertNull(gone.find(car.id))
        assertNotNull(gone.find(loose.id))
        assertTrue(gone.data.games.isEmpty())
        // Keeping the layouts: all still there.
        val kept = assigned.deleteSet(set.id)
        assertNotNull(kept.find(foot.id))
        assertEquals(car.id, kept.data.games["h:car"])
    }

    @Test
    fun `deleting a set with its layouts keeps layouts another set uses`() {
        val (l, a) = withSet()
        val (l2, _) = l.createSet("Driving", listOf(car.id))!!
        val gone = l2.deleteSet(a.id, withLayouts = true)
        assertNull(gone.find(foot.id))
        assertNotNull(gone.find(car.id))
    }
}

// ---------------------------------------------------------------- Browse layouts: a card per game

class LibraryCardsTest {
    private fun e(id: String, name: String, target: String, game: Boolean, layouts: List<String> = emptyList(), tags: List<String> = listOf("shooter")) = JSONObject()
        .put("id", id).put("name", name).put("author", "Nebula").put("target", target).put("device", "phone")
        .apply { if (game) put("game", JSONObject().put("name", "Grand Theft Auto V").put("steamAppId", 271590)) }
        .put("tags", JSONArray(tags))
        .put("path", if (game) "layouts/gta-v/${id.substringAfter('/')}.json" else "layouts/genre-shooter/${id.substringAfter('/')}.json")
        .put("size", 1000).put("controls", 10)
        .apply { if (layouts.isNotEmpty()) put("version", 2).put("layouts", JSONArray(layouts)) }

    /** The public library as of 2026-10-04. */
    private val index = LayoutIndex.parse(
        JSONObject().put("format", LayoutIndex.FORMAT).put("version", 1).put(
            "layouts",
            JSONArray()
                .put(e("genre-shooter/touch-shooter-controller", "Touch shooter · controller", "xinput", false))
                .put(e("genre-shooter/touch-shooter-keyboard-mouse", "Touch shooter · keyboard & mouse", "kbm", false))
                .put(e("gta-v/touch-controls", "GTA V · touch controls", "xinput", true, tags = listOf("action-adventure", "shooter")))
                .put(e("gta-v/touch-sets-controller", "GTA V · on foot, vehicle, aircraft", "xinput", true, listOf("On foot", "Vehicle", "Aircraft", "Parachute", "Phone"), listOf("action-adventure", "shooter")))
                .put(e("gta-v/touch-sets-keyboard-mouse", "GTA V · on foot, vehicle, aircraft (keyboard)", "kbm", true, listOf("On foot", "Vehicle", "Aircraft", "Parachute", "Phone"), listOf("action-adventure", "shooter"))),
        ).toString(),
    )

    @Test
    fun `one card for GTA V with the sets first and the old single layout last`() {
        val sections = index.browse()
        assertEquals(listOf("Shooter templates", "Games"), sections.map { it.title })
        val gta = sections.last().cards.single()
        assertEquals("GTA V", gta.title)
        assertEquals(listOf("gta-v/touch-sets-controller", "gta-v/touch-sets-keyboard-mouse", "gta-v/touch-controls"), gta.variants.map { it.entry.id })
        assertEquals(listOf(false, false, true), gta.variants.map { it.superseded })
        assertEquals("gta-v/touch-sets-controller", gta.default.entry.id)
        assertEquals("Controller · 5 layouts", gta.variants[0].label)
        assertEquals("Keyboard + mouse · 5 layouts", gta.variants[1].label)
        assertEquals("Controller · single layout · older", gta.variants[2].label)
    }

    @Test
    fun `genre templates of one family are one card too`() {
        val shooter = index.browse().first().cards.single()
        assertEquals("Touch shooter", shooter.title)
        assertEquals(listOf("Controller · single layout", "Keyboard + mouse · single layout"), shooter.variants.map { it.label })
        // Templates are never superseded.
        assertTrue(shooter.variants.none { it.superseded })
    }

    @Test
    fun `search narrows the variants, a card stays one card`() {
        val kb = index.browse("keyboard")
        val gta = kb.first { it.title == "Games" }.cards.single()
        assertEquals(listOf("gta-v/touch-sets-keyboard-mouse"), gta.variants.map { it.entry.id })
        // Layout names are searchable.
        assertEquals("GTA V", index.browse("parachute").single().cards.single().title)
    }

    @Test
    fun `a game with only single layouts has nothing superseded`() {
        val only = LibraryCards.variants(index.entries.filter { it.id == "gta-v/touch-controls" })
        assertFalse(only.single().superseded)
    }
}

// ---------------------------------------------------------------- stream menu rail: which section is in view

class MenuSectionsTest {
    private val tops = mapOf(MenuSection.QUICK to 0f, MenuSection.STATS to 300f, MenuSection.CONTROLS to 900f, MenuSection.KEYS to 1500f)

    @Test
    fun `the section whose top has reached the top of the view is in view`() {
        assertEquals(MenuSection.QUICK, MenuSections.inView(tops, 0, 1200, 600, 50f))
        assertEquals(MenuSection.QUICK, MenuSections.inView(tops, 200, 1200, 600, 50f))
        assertEquals(MenuSection.STATS, MenuSections.inView(tops, 260, 1200, 600, 50f))
        assertEquals(MenuSection.CONTROLS, MenuSections.inView(tops, 900, 1200, 600, 50f))
    }

    @Test
    fun `at the very end the last section that starts in view wins`() {
        assertEquals(MenuSection.KEYS, MenuSections.inView(tops, 1200, 1200, 600, 50f))
        assertNull(MenuSections.inView(emptyMap(), 0, 0, 600, 50f))
    }
}
