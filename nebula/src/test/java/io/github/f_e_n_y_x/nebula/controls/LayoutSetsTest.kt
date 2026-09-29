package io.github.f_e_n_y_x.nebula.controls

import android.view.KeyEvent
import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData
import io.github.f_e_n_y_x.nebula.input.Finger
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.fenyx.nebula.engine.MouseButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Records keys, mouse buttons and the pad, in order. */
private class Events : RemoteInput {
    val log = mutableListOf<String>()
    var buttons = 0
    var lt = 0
    var rt = 0
    var lx = 0
    override fun key(event: KeyEvent) = true
    override fun virtualKey(vk: Int, down: Boolean, modifiers: Int) { log += "vk $vk ${if (down) "down" else "up"}" }
    override fun text(text: String) = Unit
    override fun move(dx: Int, dy: Int) = Unit
    override fun position(x: Int, y: Int, refW: Int, refH: Int) = Unit
    override fun button(button: MouseButton, down: Boolean) { log += "$button ${if (down) "down" else "up"}" }
    override fun scroll(amount: Int) = Unit
    override fun scrollHorizontal(amount: Int) = Unit
    override fun touch(type: Byte, pointerId: Int, x: Float, y: Float) = false
    override fun gamepad(controller: Int, activeMask: Int, buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int) {
        this.buttons = buttons; this.lt = lt; this.rt = rt; this.lx = lx
    }
    override fun gamepadArrived(controller: Int, activeMask: Int, type: Byte, supportedButtons: Int, capabilities: Short) = Unit
}

private val SHIFT = Binding.Key(0x10)
private val E = Binding.Key(0x45)

// ---------------------------------------------------------------- chords

class ChordTest {
    @Test
    fun `chord tokens round trip and normalise`() {
        val c = Binding.parse("key:0x10+key:0x45")
        assertEquals(Binding.Chord(listOf(SHIFT, E)), c)
        assertEquals("key:16+key:69", c.token())
        assertEquals(c, Binding.parse(c.token()))
        assertEquals("Shift + E", c.describe().replace("Left ", ""))
        assertEquals(Binding.Chord(listOf(Binding.Pad(PadFlags.LB), Binding.Pad(PadFlags.RB))), Binding.parse("pad:256+pad:512"))
        // A damaged chord is nothing, never half a chord.
        assertEquals(Binding.None, Binding.parse("key:16+bogus"))
        assertEquals(Binding.None, Binding.parse("lt+rt+key:1+key:2+key:3"))
    }

    @Test
    fun `chordOf flattens, drops None and repeats`() {
        assertEquals(Binding.None, Binding.chordOf(emptyList()))
        assertEquals(SHIFT, Binding.chordOf(listOf(SHIFT, Binding.None, SHIFT)))
        assertEquals(Binding.Chord(listOf(SHIFT, E, Binding.Mouse(MouseKey.LEFT))), Binding.chordOf(listOf(Binding.Chord(listOf(SHIFT, E)), Binding.Mouse(MouseKey.LEFT))))
        assertEquals(listOf(SHIFT, E), Binding.Chord(listOf(SHIFT, E)).parts())
        assertEquals(emptyList<Binding>(), Binding.None.parts())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChordInputTest {
    @Test
    fun `a chord presses in order and releases in reverse`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        val d = newElement(ElementKind.DPAD, "d").copy(bindings = listOf(Binding.Chord(listOf(SHIFT, E)), Binding.None, Binding.None, Binding.None))
        input.dpad(d, setOf(0))
        input.dpad(d, emptySet())
        assertEquals(listOf("vk 16 down", "vk 69 down", "vk 69 up", "vk 16 up"), out.log)
    }

    @Test
    fun `chords share counts with plain bindings`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        val shift = ControlElement("s", ElementKind.BUTTON, 0f, 0f, 50f, 50f, bindings = listOf(SHIFT))
        val stick = newElement(ElementKind.STICK, "st").copy(sprint = Binding.Chord(listOf(SHIFT, E)))
        input.elementDown(shift)
        input.holdExtra("sprint", listOf(stick.sprint))
        input.elementUp(shift) // Shift stays: the chord still holds it
        assertEquals(listOf("vk 16 down", "vk 69 down"), out.log)
        input.holdExtra("sprint", emptyList())
        assertEquals(listOf("vk 16 down", "vk 69 down", "vk 69 up", "vk 16 up"), out.log)
    }

    @Test
    fun `mixed chord of pad button and key, and a tap click chord`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        val b = ControlElement("b", ElementKind.BUTTON, 0f, 0f, 50f, 50f, bindings = listOf(Binding.Chord(listOf(Binding.Pad(PadFlags.LB), Binding.Pad(PadFlags.RB), Binding.Key(0x46)))))
        input.elementDown(b)
        assertEquals(PadFlags.LB or PadFlags.RB, out.buttons)
        assertEquals(listOf("vk 70 down"), out.log)
        input.elementUp(b)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 1); runCurrent()
        assertEquals(0, out.buttons)
        assertEquals(listOf("vk 70 down", "vk 70 up"), out.log)

        val pad = newElement(ElementKind.TOUCHPAD, "tp").copy(click = Binding.Chord(listOf(Binding.Key(0x11), Binding.Mouse(MouseKey.LEFT))))
        out.log.clear()
        input.click(pad)
        assertEquals(listOf("vk 17 down", "LEFT down", "LEFT up", "vk 17 up"), out.log)
    }

    @Test
    fun `macro steps can be chords`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        val m = newElement(ElementKind.MACRO, "m").copy(steps = listOf(MacroStep(Binding.Chord(listOf(SHIFT, E)), holdMs = 20, gapMs = 0)))
        input.elementDown(m)
        runCurrent()
        advanceTimeBy(25); runCurrent()
        assertEquals(listOf("vk 16 down", "vk 69 down", "vk 69 up", "vk 16 up"), out.log)
    }
}

// ---------------------------------------------------------------- the switch element

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchRouterTest {
    private val area = RRect(0f, 0f, 1000f, 500f)
    private val sw = newElement(ElementKind.SWITCH, "sw", 0.5f, 0.1f)
    private val a = ControlElement("a", ElementKind.BUTTON, 0.88f, 0.75f, 52f, 52f, bindings = listOf(Binding.Pad(PadFlags.A)))
    private fun centre(e: ControlElement) = RouterLayout.place(e, area, 1f).let { it.cx to it.cy }

    @Test
    fun `fires on lift inside, not on touch, cancel or a slide off`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        val fired = mutableListOf<String>()
        val router = TouchRouter(input, onSwitch = { fired += it.id }).apply { layout = RouterLayout.of(listOf(sw, a), area, 1f, OutsideTouch.OFF, LookOutput.MOUSE) }
        val (x, y) = centre(sw)
        router.down(0, x, y, 0)
        assertTrue(fired.isEmpty())
        router.up(0, 50)
        assertEquals(listOf("sw"), fired)

        router.down(1, x, y, 100)
        router.move(listOf(Finger(1, x, 400f)), 120) // slid off
        router.up(1, 150)
        assertEquals(1, fired.size)

        router.down(2, x, y, 200)
        router.cancel(210)
        assertEquals(1, fired.size)
        assertEquals(0, out.buttons)
    }

    @Test
    fun `the lifting finger is gone before the switch runs, and cancelling then releases the rest`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        lateinit var router: TouchRouter
        var fingersAtSwitch = -1
        router = TouchRouter(input, onSwitch = {
            fingersAtSwitch = router.fingerCount
            router.cancel() // what the stream does, then it shows the next layout
        }).apply { layout = RouterLayout.of(listOf(sw, a), area, 1f, OutsideTouch.OFF, LookOutput.MOUSE) }
        val (ax, ay) = centre(a)
        val (sx, sy) = centre(sw)
        router.down(0, ax, ay, 0)
        assertEquals(PadFlags.A, out.buttons)
        router.down(1, sx, sy, 10)
        router.up(1, 100)
        assertEquals(1, fingersAtSwitch) // only the A finger was still owned
        assertEquals(0, router.fingerCount)
        advanceTimeBy(ControlsInput.MIN_HOLD_MS + 1); runCurrent()
        assertEquals(0, out.buttons) // A was let go: nothing stuck
    }

    @Test
    fun `a switch sends nothing to the PC`() = runTest {
        val out = Events()
        val input = ControlsInput({ out }, backgroundScope)
        input.elementDown(sw); input.elementUp(sw)
        assertEquals(0, out.buttons)
        assertTrue(out.log.isEmpty())
        assertEquals(LayoutTarget.XINPUT, LayoutTarget.detect(listOf(sw)))
    }
}

// ---------------------------------------------------------------- set model and library

class LayoutSetModelTest {
    private val set = LayoutSet("set-1", "GTA", listOf("foot", "car", "plane"), cycle = listOf("foot", "car"))

    @Test
    fun `next and previous follow the cycle and wrap`() {
        assertEquals("car", set.target("foot", SwitchTarget.Next))
        assertEquals("foot", set.target("car", SwitchTarget.Next))
        assertEquals("car", set.target("foot", SwitchTarget.Previous))
        // From a layout outside the cycle: next goes to the first, previous to the last.
        assertEquals("foot", set.target("plane", SwitchTarget.Next))
        assertEquals("car", set.target("plane", SwitchTarget.Previous))
        assertEquals("plane", set.target("foot", SwitchTarget.Layout("plane")))
        assertNull(set.target("plane", SwitchTarget.Layout("plane")))
        assertNull(set.target("foot", SwitchTarget.Layout("boat")))
        assertNull(set.target("foot", SwitchTarget.Picker))
        assertEquals(listOf("foot", "car", "plane"), set.copy(cycle = null).cycleOrder())
        assertNull(LayoutSet("s", "one", listOf("x")).target("x", SwitchTarget.Next))
    }

    @Test
    fun `without drops a member from cycle and start`() {
        val s = set.copy(start = "car").without("car")!!
        assertEquals(listOf("foot", "plane"), s.members)
        assertEquals(listOf("foot"), s.cycle)
        assertEquals("foot", s.startId())
        assertNull(LayoutSet("s", "one", listOf("x")).without("x"))
    }
}

class LayoutSetLibraryTest {
    private var n = 0
    private fun lib(d: StoreData = StoreData()) = ProfileLibrary(d, DefaultProfiles.standard(), { "p-${++n}" }, { 1000L })
    private fun profile(id: String, name: String) = ControlsProfile(id, name, listOf(newElement(ElementKind.BUTTON, "a"), newElement(ElementKind.SWITCH, "sw")))

    private fun twoProfiles(): ProfileLibrary = lib(StoreData(profiles = listOf(profile("p-a", "On foot"), profile("p-b", "Vehicle"))))

    @Test
    fun `a game given a set starts on its start layout`() {
        val (l, set) = twoProfiles().createSet("GTA", listOf("p-a", "p-b"))!!
        val g = l.assign("host:gta", set.id).updateSet(set.copy(start = "p-b"))
        assertEquals(set.id, g.resolveSet("host:gta")?.id)
        assertEquals("p-b", g.resolve("host:gta").id)
        assertEquals(set.id, g.assignedTo("host:gta"))
        assertNull(g.resolveSet("host:other"))
        val active = ActiveLayout.of(g, "host:gta", null)
        assertEquals("p-b", active.profile.id)
        assertEquals("p-a", ActiveLayout.of(g, "host:gta", "p-a").profile.id)
        // A remembered layout that left the set falls back to the start.
        assertEquals("p-b", ActiveLayout.of(g, "host:gta", "p-zzz").profile.id)
        assertEquals(listOf("On foot", "Vehicle"), active.layouts(g).map { it.name })
        // A set can be the default too.
        assertEquals(set.id, g.setDefault(set.id).resolveSet(null)?.id)
    }

    @Test
    fun `deleting a profile leaves its sets, an empty set goes, and so do its games`() {
        val (l, set) = twoProfiles().createSet("GTA", listOf("p-a", "p-b"))!!
        val g = l.assign("host:gta", set.id)
        val one = g.delete("p-b")
        assertEquals(listOf("p-a"), one.findSet(set.id)!!.members)
        val none = one.delete("p-a")
        assertNull(none.findSet(set.id))
        assertTrue(none.data.sets.isEmpty())
        assertNull(none.assignedTo("host:gta"))
        assertEquals(ControlsProfile.STANDARD_ID, none.resolve("host:gta").id)
    }

    @Test
    fun `deleteSet keeps the layouts and frees the games`() {
        val (l, set) = twoProfiles().createSet("GTA", listOf("p-a", "p-b"))!!
        val g = l.assign("host:gta", set.id).deleteSet(set.id)
        assertEquals(2, g.data.profiles.size)
        assertNull(g.assignedTo("host:gta"))
    }

    @Test
    fun `updateSet cleans cycle and start`() {
        val (l, set) = twoProfiles().createSet("GTA", listOf("p-a", "p-b", "p-missing"))!!
        assertEquals(listOf("p-a", "p-b"), set.members)
        val u = l.updateSet(set.copy(cycle = listOf("p-b", "p-x"), start = "p-nope")).findSet(set.id)!!
        assertEquals(listOf("p-b"), u.cycle)
        assertNull(u.start)
        // A full cycle in member order is stored as "all".
        assertNull(l.updateSet(set.copy(cycle = listOf("p-a", "p-b"))).findSet(set.id)!!.cycle)
        assertNull(twoProfiles().createSet("x", listOf("nope")))
    }

    @Test
    fun `the store keeps sets`() {
        val (l, set) = twoProfiles().createSet("GTA", listOf("p-a", "p-b"))!!
        val d = l.assign("host:gta", set.id).updateSet(set.copy(cycle = listOf("p-b"), start = "p-b")).data
        val back = ProfileJson.decodeStore(ProfileJson.encodeStore(d))
        assertEquals(d.sets, back.sets)
        assertEquals(d.games, back.games)
        val sw = back.profiles.first().landscape.first { it.kind == ElementKind.SWITCH }
        assertEquals(SwitchTarget.Next, sw.switchTo)
        // An old store without sets still reads.
        val old = JSONObject(ProfileJson.encodeStore(StoreData(profiles = d.profiles))).apply { remove("sets") }
        assertTrue(ProfileJson.decodeStore(old.toString()).sets.isEmpty())
    }

    @Test
    fun `switch targets survive the store`() {
        val p = profile("p-a", "x").let { it.copy(landscape = it.landscape.map { e -> if (e.kind == ElementKind.SWITCH) e.copy(switchTo = SwitchTarget.Layout("p-b")) else e }) }
        val back = ProfileJson.profileFromJson(ProfileJson.profileToJson(p))
        assertEquals(SwitchTarget.Layout("p-b"), back.landscape.first { it.kind == ElementKind.SWITCH }.switchTo)
    }
}

// ---------------------------------------------------------------- the file, version 2

class LayoutFileV2Test {
    private var n = 0
    private fun lib(d: StoreData = StoreData()) = ProfileLibrary(d, DefaultProfiles.standard(), { "p-${++n}" }, { 1000L })

    private fun rejects(text: String, contains: String) {
        try {
            LayoutFile.parse(text)
            fail("accepted: ${text.take(300)}")
        } catch (e: ControlsFormatException) {
            assertTrue("message \"${e.message}\" should mention \"$contains\"", e.message.orEmpty().contains(contains))
        }
    }

    private val foot = ControlsProfile(
        "p-foot", "On foot",
        listOf(
            newElement(ElementKind.STICK, "ls", 0.2f, 0.7f),
            ControlElement("e", ElementKind.BUTTON, 0.8f, 0.7f, 56f, 56f, label = "Shift+E", bindings = listOf(SHIFT, E)),
            newElement(ElementKind.SWITCH, "sw", 0.5f, 0.06f),
            newElement(ElementKind.SWITCH, "pick", 0.6f, 0.06f).copy(switchTo = SwitchTarget.Picker),
        ),
        outside = OutsideTouch.LOOK, look = LookOutput.MOUSE,
    )
    private val car = ControlsProfile(
        "p-car", "Vehicle",
        listOf(
            newElement(ElementKind.DPAD, "d", 0.2f, 0.7f).copy(bindings = listOf(Binding.Key(0x57), Binding.Chord(listOf(Binding.Key(0x53), SHIFT)), Binding.Key(0x41), Binding.Key(0x44))),
            newElement(ElementKind.SWITCH, "air", 0.5f, 0.06f).copy(switchTo = SwitchTarget.Layout("p-plane")),
            newElement(ElementKind.SWITCH, "gone", 0.6f, 0.06f).copy(switchTo = SwitchTarget.Layout("p-not-in-set")),
        ),
        portrait = listOf(newElement(ElementKind.SWITCH, "sw", 0.5f, 0.06f)),
    )
    private val plane = ControlsProfile("p-plane", "Aircraft", listOf(newElement(ElementKind.BUTTON, "a"), newElement(ElementKind.SWITCH, "sw", 0.5f, 0.06f).copy(switchTo = SwitchTarget.Previous)))
    private val set = LayoutSet("set-x", "GTA V layouts", listOf("p-foot", "p-car", "p-plane"), cycle = listOf("p-foot", "p-car"), start = "p-car",
        meta = LayoutMeta("GTA V · on foot, vehicle, aircraft", game = GameRef("Grand Theft Auto V", 271590), target = LayoutTarget.MIXED))

    @Test
    fun `plain layouts are still written as version 1, new features as version 2`() {
        assertEquals(1, JSONObject(LayoutFile.encode(DefaultProfiles.gtaTouchControls())).getInt("version"))
        val chordButtonOnly = ControlsProfile("p", "x", listOf(ControlElement("e", ElementKind.BUTTON, 0.5f, 0.5f, 56f, 56f, bindings = listOf(SHIFT, E))))
        // A button's list is "pressed together" already in version 1.
        assertEquals(1, JSONObject(LayoutFile.encode(chordButtonOnly)).getInt("version"))
        assertEquals(2, JSONObject(LayoutFile.encode(car)).getInt("version"))
        assertEquals(2, JSONObject(LayoutFile.encode(plane)).getInt("version"))
    }

    @Test
    fun `a single version 2 layout round trips and switch targets naming a layout become the picker`() {
        val json = LayoutFile.encode(car)
        val back = LayoutFile.parse(json, "p-new").profile
        assertEquals(car.landscape[0], back.landscape[0])
        assertEquals(SwitchTarget.Picker, back.landscape[1].switchTo)
        assertNull(LayoutFile.parse(json).set)
        // Switch elements carry only their own fields.
        val sw = JSONObject(json).getJSONArray("landscape").getJSONObject(1)
        assertEquals(setOf("id", "kind", "x", "y", "w", "h", "shape", "opacity", "switchTo"), sw.keys().asSequence().toSet())
    }

    @Test
    fun `a set round trips through file, share code and the library`() {
        val json = LayoutFile.encodeSet(set, listOf(plane, car, foot))
        val o = JSONObject(json)
        assertEquals(2, o.getInt("version"))
        assertTrue(!o.has("landscape") && !o.has("settings"))
        val layouts = o.getJSONArray("layouts")
        assertEquals(listOf("on-foot", "vehicle", "aircraft"), (0 until layouts.length()).map { layouts.getJSONObject(it).getString("id") })
        assertEquals("vehicle", o.getJSONObject("set").getString("start"))
        assertEquals(JSONArray(listOf("on-foot", "vehicle")).toString(), o.getJSONObject("set").getJSONArray("cycle").toString())
        val carJson = layouts.getJSONObject(1).getJSONArray("landscape")
        assertEquals("layout:aircraft", carJson.getJSONObject(1).getString("switchTo"))
        assertEquals("picker", carJson.getJSONObject(2).getString("switchTo"))
        assertEquals("key:83+key:16", carJson.getJSONObject(0).getJSONArray("bindings").getString(1))
        assertEquals("mouse", layouts.getJSONObject(0).getJSONObject("settings").getString("look"))

        for (text in listOf(json, LayoutFile.shareCodeOfSet(set, listOf(foot, car, plane)))) {
            val parsed = LayoutFile.parse(text, "p-preview", 5)
            val ps = parsed.set!!
            assertEquals(2, parsed.version)
            assertEquals("Vehicle", parsed.profile.name) // the start layout is previewed
            assertEquals(listOf("on-foot", "vehicle", "aircraft"), ps.set.members)
            assertEquals(listOf("on-foot", "vehicle"), ps.set.cycle)
            assertEquals("GTA V · on foot, vehicle, aircraft", ps.set.meta!!.name)

            val outcome = ProfileImport.Outcome.of(parsed)
            val (next, start) = ProfileImport.addTo(lib(), outcome, parsed.profile) { it }
            val imported = next.data.sets.single()
            assertEquals(start.id, imported.startId())
            val members = imported.members.map { next.find(it)!! }
            assertEquals(listOf("On foot", "Vehicle", "Aircraft"), members.map { it.name })
            // layout:aircraft now points at the imported Aircraft profile.
            assertEquals(SwitchTarget.Layout(members[2].id), members[1].landscape[1].switchTo)
            assertEquals(foot.landscape.take(2), members[0].landscape.take(2))
            assertEquals(LookOutput.MOUSE, members[0].look)
            assertEquals(car.portrait, members[1].portrait)
            assertEquals(listOf(members[0].id, members[1].id), imported.cycle)
            // And it can be shared again, identically.
            assertEquals(json, LayoutFile.encodeSet(imported, members, set.meta!!))
        }
    }

    @Test
    fun `importing a set twice keeps names unique`() {
        val parsed = LayoutFile.parse(LayoutFile.encodeSet(set, listOf(foot, car, plane)))
        val o = ProfileImport.Outcome.of(parsed)
        val (l1, _) = ProfileImport.addTo(lib(), o, parsed.profile) { it }
        val (l2, _) = ProfileImport.addTo(l1, o, parsed.profile) { it }
        assertEquals(2, l2.data.sets.size)
        assertEquals(6, l2.data.profiles.map { it.name.lowercase() }.toSet().size)
        assertEquals(2, l2.data.sets.map { it.name }.toSet().size)
    }

    private fun setJson(): JSONObject = JSONObject(LayoutFile.encodeSet(set, listOf(foot, car, plane)))

    @Test
    fun `version 2 rejections`() {
        rejects(setJson().put("landscape", JSONArray()).toString(), "no top-level")
        rejects(setJson().put("set", JSONObject().put("start", "boat")).toString(), "set.start")
        rejects(setJson().put("set", JSONObject().put("cycle", JSONArray(listOf("on-foot", "on-foot")))).toString(), "each layout once")
        rejects(setJson().put("set", JSONObject().put("order", 1)).toString(), "unknown field")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).put("id", "on-foot") }.toString(), "used twice")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).put("name", "ON FOOT") }.toString(), "used twice")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).put("id", "Car") }.toString(), "lower-case")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).getJSONArray("landscape").getJSONObject(1).put("switchTo", "layout:boat") }.toString(), "no layout")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).getJSONArray("landscape").getJSONObject(1).put("switchTo", "first") }.toString(), "next, previous")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(1).getJSONArray("landscape").getJSONObject(1).put("bindings", JSONArray(listOf("pad:4096"))) }.toString(), "unknown field")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(0).getJSONArray("landscape").getJSONObject(1).put("switchTo", "next") }.toString(), "unknown field")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(0).getJSONArray("landscape").getJSONObject(1).put("bindings", JSONArray(listOf("key:16+none"))) }.toString(), "none")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(0).getJSONArray("landscape").getJSONObject(1).put("bindings", JSONArray(listOf("key:16+key:0x10"))) }.toString(), "once")
        rejects(setJson().apply { getJSONArray("layouts").getJSONObject(0).getJSONArray("landscape").getJSONObject(1).put("bindings", JSONArray(listOf("pad:3+key:1"))) }.toString(), "unknown binding")
        rejects(setJson().apply { val a = getJSONArray("layouts"); repeat(6) { i -> a.put(JSONObject(a.getJSONObject(2).toString()).put("id", "x$i").put("name", "X$i")) } }.toString(), "at most 8")
        rejects(setJson().put("layouts", JSONArray()).toString(), "no layouts")
        // Single layout: layout:<id> has nothing to name.
        val single = JSONObject(LayoutFile.encode(plane)).apply { getJSONArray("landscape").getJSONObject(1).put("switchTo", "layout:foot") }
        rejects(single.toString(), "single layout")
    }

    @Test
    fun `version 1 files can't use version 2 features`() {
        val v1 = JSONObject(LayoutFile.encode(DefaultProfiles.touchShooterPad()))
        rejects(JSONObject(v1.toString()).apply { getJSONArray("landscape").getJSONObject(1).put("bindings", JSONArray(listOf("pad:256+pad:512"))) }.toString(), "version\": 2")
        rejects(JSONObject(v1.toString()).apply { getJSONArray("landscape").put(JSONObject().put("id", "s").put("kind", "switch").put("x", 0.5).put("y", 0.5).put("w", 60).put("h", 40)) }.toString(), "unknown control")
        rejects(JSONObject(v1.toString()).put("layouts", JSONArray()).toString(), "unknown field")
        // The same file as version 2 is fine.
        assertNotNull(LayoutFile.parse(JSONObject(v1.toString()).put("version", 2).toString()))
    }

    @Test
    fun `the library's layouts read the same as version 2`() {
        for (p in listOf(DefaultProfiles.gtaTouchControls(), DefaultProfiles.touchShooterPad(), DefaultProfiles.touchShooterKbm())) {
            val v2 = JSONObject(LayoutFile.encode(p)).put("version", 2).toString()
            assertEquals(p.landscape, LayoutFile.parse(v2).profile.landscape)
        }
    }
}

class LibraryIndexSetTest {
    @Test
    fun `index entries of sets list their layouts, and older entries have none`() {
        val entry = JSONObject().put("id", "gta-v/sets").put("name", "GTA V · sets").put("path", "layouts/gta-v/sets.json").put("size", 100)
            .put("version", 2).put("layouts", JSONArray(listOf("On foot", "Vehicle", "Aircraft"))).put("preview", JSONArray())
        val plain = JSONObject().put("id", "gta-v/one").put("name", "One").put("path", "layouts/gta-v/one.json").put("size", 100)
        val idx = LayoutIndex.parse(JSONObject().put("format", LayoutIndex.FORMAT).put("version", 1).put("layouts", JSONArray().put(entry).put(plain)).toString())
        assertEquals(listOf("On foot", "Vehicle", "Aircraft"), idx.entries[0].layouts)
        assertTrue(idx.entries[1].layouts.isEmpty())
    }
}

class ElementGroupFileTest {
    @Test
    fun `editor groups are written as version 2 and read back`() {
        val a = ControlElement("a", ElementKind.BUTTON, 0.8f, 0.7f, 56f, 56f, bindings = listOf(Binding.Pad(PadFlags.A)), group = "abxy:e-3f9a1c2d")
        val p = ControlsProfile("p", "Grouped", listOf(a, a.copy(id = "b", group = null)))
        val json = LayoutFile.encode(p)
        assertEquals(2, JSONObject(json).getInt("version"))
        val back = LayoutFile.parse(json).profile.landscape
        assertEquals("abxy:e-3f9a1c2d", back[0].group)
        assertNull(back[1].group)
        assertEquals("abxy:e-3f9a1c2d", ProfileJson.elementFromJson(ProfileJson.elementToJson(a))!!.group)
        try {
            LayoutFile.parse(JSONObject(json).apply { getJSONArray("landscape").getJSONObject(0).put("group", "bad group!") }.toString())
            fail("accepted a bad group id")
        } catch (e: ControlsFormatException) {
            assertTrue(e.message.orEmpty().contains("group"))
        }
    }
}

class RetiredPresetsTest {
    private fun lib(d: StoreData) = ProfileLibrary(d, DefaultProfiles.standard(), { "p-new" }, { 1000L })

    @Test
    fun `choices naming a retired preset become the user's own profiles`() {
        val old = StoreData(
            profiles = listOf(ControlsProfile("p-7", "Mine", listOf(newElement(ElementKind.BUTTON, "a")))),
            activeProfileId = "builtin:shooter-pubg-kbm", // renamed in 0.3.0-dev16, retired in 0.4
            games = mapOf("h:gta" to ControlsProfile.GTA_TOUCH_CONTROLS_ID, "h:x" to "p-7"),
        )
        val decoded = ProfileJson.decodeStore(ProfileJson.encodeStore(old))
        // Without adoption they would silently fall back to Standard.
        assertEquals(ControlsProfile.STANDARD_ID, lib(decoded).resolve("h:gta").id)
        val (d, moved) = RetiredPresets.adopt(decoded, 5)
        assertEquals(setOf(ControlsProfile.GTA_TOUCH_CONTROLS_ID, ControlsProfile.TOUCH_SHOOTER_KBM_ID), moved.keys)
        val l = lib(d)
        val gta = l.resolve("h:gta")
        assertEquals("GTA V · touch controls", gta.name)
        assertEquals("p-gta-touch-controls", gta.id)
        assertEquals(false, gta.isBuiltIn)
        assertEquals(DefaultProfiles.gtaTouchControls().landscape, gta.landscape)
        assertEquals(OutsideTouch.LOOK, gta.outside)
        assertEquals(LookOutput.MOUSE, gta.look)
        assertEquals("Touch shooter · keyboard & mouse", l.resolve("h:other").name)
        assertEquals("p-7", l.resolve("h:x").id)
        // Running it again changes nothing.
        assertEquals(d, RetiredPresets.adopt(d, 9).first)
        // A store that never used a preset is left alone.
        val plain = StoreData(profiles = old.profiles, games = mapOf("h:x" to "p-7"))
        assertEquals(plain to emptyMap<String, String>(), RetiredPresets.adopt(plain))
    }
}
