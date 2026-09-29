package io.github.f_e_n_y_x.nebula.controls

import kotlin.math.max
import kotlin.math.min

/**
 * The editor's "Add" palette: every kind of control, sorted into categories, plus ready-made
 * groups (ABXY, WASD, number row…) placed in one tap. Pure Kotlin, so what each item places is
 * unit-tested.
 *
 * Every item is a list of [parts]: elements whose [ControlElement.x] / [ControlElement.y] are
 * **offsets in dp from the item's centre** (not shares of the screen) and whose ids are local
 * names. [place] turns them into real elements for a given controls area. A group's parts get a
 * shared [ControlElement.group] id; nothing else about them is special.
 */
enum class PaletteCategory(val label: String) {
    GROUPS("Groups"),
    GAMEPAD("Gamepad"),
    KEYBOARD("Keyboard"),
    MOUSE("Mouse & touch"),
    ADVANCED("Advanced"),
}

data class PaletteItem(
    /** Stable id; for a group also the prefix of its group ids (`"abxy"` → `"abxy:3f9a1c"`). */
    val id: String,
    val title: String,
    val help: String,
    val category: PaletteCategory,
    /** Elements with dp offsets from the item's centre (see [Palette]). */
    val parts: List<ControlElement>,
    /** Where it lands, as a share of a landscape controls area. */
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f,
    /** Extra words the search matches besides the title and help. */
    val keywords: List<String> = emptyList(),
) {
    val isGroup: Boolean get() = parts.size > 1

    /** Case-insensitive match of every word of [query] against the title, help, keywords and part labels. */
    fun matches(query: String): Boolean {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val hay = buildString {
            append(title).append(' ').append(help).append(' ').append(category.label)
            keywords.forEach { append(' ').append(it) }
            parts.forEach { append(' ').append(it.label) }
        }.lowercase()
        return words.all { it in hay }
    }
}

/** A placed group's members, found by [ControlElement.group]. */
fun List<ControlElement>.groupMembers(group: String): List<ControlElement> = filter { it.group == group }

/** The palette item a group id was placed from (its prefix before ':'), if any. */
fun groupTemplateOf(group: String): PaletteItem? = Palette.byId[group.substringBefore(':')]?.takeIf { it.isGroup }

/** A short name for a placed group. */
fun groupTitle(group: String): String = groupTemplateOf(group)?.title ?: "Group"

object Palette {
    /** Gap between the keys of a group, dp. */
    private const val GAP = 6f

    // ------------------------------------------------------------ building blocks

    private fun pad(id: String, label: String, flag: Int, dx: Float = 0f, dy: Float = 0f, size: Float = 52f, w: Float = size, tint: Long? = null, shape: ElementShape = ElementShape.ROUND) =
        ControlElement(id, ElementKind.BUTTON, dx, dy, w, size, label = label, shape = shape, bindings = listOf(Binding.Pad(flag)), tint = tint)

    private fun trigger(id: String, side: Side, dx: Float = 0f, dy: Float = 0f) =
        ControlElement(id, ElementKind.TRIGGER, dx, dy, 68f, 44f, label = if (side == Side.LEFT) "LT" else "RT", shape = ElementShape.SQUARE, bindings = listOf(Binding.Trigger(side)))

    private fun shoulder(id: String, label: String, flag: Int, dx: Float = 0f, dy: Float = 0f) =
        pad(id, label, flag, dx, dy, size = 44f, w = 68f, shape = ElementShape.PILL)

    /** A PC key; [w] wider than [h] draws a key cap (Space, Shift…). */
    private fun key(id: String, vk: Int, dx: Float = 0f, dy: Float = 0f, w: Float = 48f, h: Float = 48f, label: String = keyLabel(vk)) =
        ControlElement(id, ElementKind.BUTTON, dx, dy, w, h, label = label, shape = ElementShape.SQUARE, bindings = listOf(Binding.Key(vk)))

    private fun mouse(id: String, b: Binding, label: String, dx: Float = 0f, dy: Float = 0f, w: Float = 56f, h: Float = 56f, shape: ElementShape = ElementShape.ROUND) =
        ControlElement(id, ElementKind.BUTTON, dx, dy, w, h, label = label, shape = shape, bindings = listOf(b))

    private fun stick(id: String, out: StickOutput, dx: Float = 0f, dy: Float = 0f, click: Binding) =
        ControlElement(
            id, ElementKind.STICK, dx, dy, 120f, 120f, stick = out, click = click,
            label = when (out) { StickOutput.LEFT -> "LS"; StickOutput.RIGHT -> "RS"; StickOutput.KEYS -> "WASD" },
            bindings = if (out == StickOutput.KEYS) WASD else emptyList(),
        )

    /** Short key-cap text: "Esc", "F5", "⏯"… */
    fun keyLabel(vk: Int): String = when (vk) {
        0x25 -> "←"; 0x26 -> "↑"; 0x27 -> "→"; 0x28 -> "↓"
        0xB3 -> "⏯"; 0xB0 -> "⏭"; 0xB1 -> "⏮"; 0xB2 -> "⏹"
        0xAD -> "Mute"; 0xAE -> "Vol−"; 0xAF -> "Vol+"
        0xA0 -> "LShift"; 0xA1 -> "RShift"; 0xA2 -> "LCtrl"; 0xA3 -> "RCtrl"; 0xA4 -> "LAlt"; 0xA5 -> "RAlt"
        0x08 -> "Bksp"; 0x14 -> "Caps"; 0x21 -> "PgUp"; 0x22 -> "PgDn"; 0x2D -> "Ins"; 0x2E -> "Del"
        0x2C -> "PrtSc"; 0x90 -> "NumLk"; 0x91 -> "ScrLk"
        else -> VirtualKeys.name(vk)
    }

    private val WASD = listOf(0x57, 0x53, 0x41, 0x44).map { Binding.Key(it) }
    private val ARROWS = listOf(0x26, 0x28, 0x25, 0x27).map { Binding.Key(it) }

    private val FACE_TINTS = listOf(0xFF4CC38A, 0xFFE5534B, 0xFF4F8DF7, 0xFFE8C547)

    /** A row of [n] keys of [w] dp, centred on the item. */
    private fun rowX(i: Int, n: Int, w: Float) = (i - (n - 1) / 2f) * (w + GAP)

    // ------------------------------------------------------------ groups

    private val groups: List<PaletteItem> = listOf(
        PaletteItem(
            "abxy", "ABXY buttons", "The four face buttons in a diamond, in Xbox colours.", PaletteCategory.GROUPS,
            listOf(
                pad("a", "A", PadFlags.A, 0f, 56f, tint = FACE_TINTS[0]),
                pad("b", "B", PadFlags.B, 58f, 0f, tint = FACE_TINTS[1]),
                pad("x", "X", PadFlags.X, -58f, 0f, tint = FACE_TINTS[2]),
                pad("y", "Y", PadFlags.Y, 0f, -56f, tint = FACE_TINTS[3]),
            ),
            anchorX = 0.86f, anchorY = 0.64f, keywords = listOf("face", "diamond", "cluster", "gamepad"),
        ),
        PaletteItem(
            "dpad-cluster", "D-pad buttons", "Up, down, left and right as four separate buttons you can move apart.", PaletteCategory.GROUPS,
            listOf(
                pad("up", "▲", PadFlags.UP, 0f, -54f, size = 48f, shape = ElementShape.SQUARE),
                pad("down", "▼", PadFlags.DOWN, 0f, 54f, size = 48f, shape = ElementShape.SQUARE),
                pad("left", "◀", PadFlags.LEFT, -54f, 0f, size = 48f, shape = ElementShape.SQUARE),
                pad("right", "▶", PadFlags.RIGHT, 54f, 0f, size = 48f, shape = ElementShape.SQUARE),
            ),
            anchorX = 0.14f, anchorY = 0.5f, keywords = listOf("dpad", "directions", "cross", "cluster"),
        ),
        PaletteItem(
            "shoulders", "Shoulders & triggers", "LT, LB, RB and RT in one strip. Split it to put each pair on its own side.", PaletteCategory.GROUPS,
            listOf(
                trigger("lt", Side.LEFT, -111f, 0f),
                shoulder("lb", "LB", PadFlags.LB, -37f, 0f),
                shoulder("rb", "RB", PadFlags.RB, 37f, 0f),
                trigger("rt", Side.RIGHT, 111f, 0f),
            ),
            anchorX = 0.5f, anchorY = 0.12f, keywords = listOf("bumpers", "lb", "rb", "lt", "rt", "l1", "r1", "l2", "r2"),
        ),
        PaletteItem(
            "left-shoulder", "LB + LT", "The left bumper and trigger, stacked.", PaletteCategory.GROUPS,
            listOf(trigger("lt", Side.LEFT, 0f, -25f), shoulder("lb", "LB", PadFlags.LB, 0f, 25f)),
            anchorX = 0.1f, anchorY = 0.16f, keywords = listOf("bumper", "trigger", "l1", "l2"),
        ),
        PaletteItem(
            "right-shoulder", "RB + RT", "The right bumper and trigger, stacked.", PaletteCategory.GROUPS,
            listOf(trigger("rt", Side.RIGHT, 0f, -25f), shoulder("rb", "RB", PadFlags.RB, 0f, 25f)),
            anchorX = 0.9f, anchorY = 0.16f, keywords = listOf("bumper", "trigger", "r1", "r2"),
        ),
        PaletteItem(
            "left-stick", "Left stick + L3", "A left stick with its own L3 button beside it.", PaletteCategory.GROUPS,
            listOf(stick("ls", StickOutput.LEFT, 0f, 0f, Binding.Pad(PadFlags.LS_CLK)), pad("l3", "L3", PadFlags.LS_CLK, 84f, -54f, size = 44f)),
            anchorX = 0.2f, anchorY = 0.74f, keywords = listOf("analog", "thumbstick", "ls", "click"),
        ),
        PaletteItem(
            "right-stick", "Right stick + R3", "A right stick with its own R3 button beside it.", PaletteCategory.GROUPS,
            listOf(stick("rs", StickOutput.RIGHT, 0f, 0f, Binding.Pad(PadFlags.RS_CLK)), pad("r3", "R3", PadFlags.RS_CLK, -84f, -54f, size = 44f)),
            anchorX = 0.7f, anchorY = 0.76f, keywords = listOf("analog", "thumbstick", "rs", "click", "camera"),
        ),
        PaletteItem(
            "menu-buttons", "Select · Guide · Start", "The three middle buttons of a gamepad.", PaletteCategory.GROUPS,
            listOf(
                pad("select", "Select", PadFlags.BACK, -78f, 0f, size = 40f, w = 68f, shape = ElementShape.PILL),
                pad("guide", "⌂", PadFlags.GUIDE, 0f, 0f, size = 44f),
                pad("start", "Start", PadFlags.START, 78f, 0f, size = 40f, w = 68f, shape = ElementShape.PILL),
            ),
            anchorX = 0.5f, anchorY = 0.9f, keywords = listOf("back", "view", "menu", "home", "options"),
        ),
        PaletteItem(
            "wasd", "WASD keys", "W, A, S and D laid out as on a keyboard.", PaletteCategory.GROUPS,
            listOf(
                key("w", 0x57, 0f, -27f),
                key("a", 0x41, -54f, 27f),
                key("s", 0x53, 0f, 27f),
                key("d", 0x44, 54f, 27f),
            ),
            anchorX = 0.15f, anchorY = 0.7f, keywords = listOf("movement", "keyboard", "pc"),
        ),
        PaletteItem(
            "arrows", "Arrow keys", "Up, down, left and right arrow keys in an inverted T.", PaletteCategory.GROUPS,
            listOf(
                key("up", 0x26, 0f, -27f),
                key("left", 0x25, -54f, 27f),
                key("down", 0x28, 0f, 27f),
                key("right", 0x27, 54f, 27f),
            ),
            anchorX = 0.85f, anchorY = 0.72f, keywords = listOf("cursor", "directions", "keyboard"),
        ),
        PaletteItem(
            "numbers", "Number row 1–0", "The ten number keys in a row, for hotbars and weapon slots.", PaletteCategory.GROUPS,
            (0 until 10).map { i -> val d = (i + 1) % 10; key("n$d", 0x30 + d, rowX(i, 10, 44f), 0f, w = 44f, h = 44f) },
            anchorX = 0.5f, anchorY = 0.12f, keywords = listOf("digits", "hotbar", "slots", "1234567890"),
        ),
        PaletteItem(
            "fkeys", "F1–F12", "The twelve function keys in two rows of six.", PaletteCategory.GROUPS,
            (0 until 12).map { i -> key("f${i + 1}", 0x70 + i, rowX(i % 6, 6, 48f), if (i < 6) -27f else 27f) },
            anchorX = 0.5f, anchorY = 0.2f, keywords = listOf("function", "keys", "quicksave"),
        ),
        PaletteItem(
            "system-keys", "Esc, Tab, Enter & modifiers", "Esc, Tab and Enter, Shift, Ctrl and Alt, and a wide Space bar.", PaletteCategory.GROUPS,
            listOf(
                key("esc", 0x1B, -64f, -54f, w = 58f),
                key("tab", 0x09, 0f, -54f, w = 58f),
                key("enter", 0x0D, 64f, -54f, w = 58f),
                key("shift", 0x10, -64f, 0f, w = 58f),
                key("ctrl", 0x11, 0f, 0f, w = 58f),
                key("alt", 0x12, 64f, 0f, w = 58f),
                key("space", 0x20, 0f, 54f, w = 186f),
            ),
            anchorX = 0.5f, anchorY = 0.72f, keywords = listOf("escape", "return", "space", "shift", "control", "ctrl", "alt", "modifier", "keyboard"),
        ),
        PaletteItem(
            "media-keys", "Media keys", "Previous, play / pause and next, plus volume down, up and mute.", PaletteCategory.GROUPS,
            listOf(0xB1, 0xB3, 0xB0, 0xAE, 0xAF, 0xAD).mapIndexed { i, vk -> key("m$i", vk, rowX(i % 3, 3, 52f), if (i < 3) -27f else 27f, w = 52f) },
            anchorX = 0.5f, anchorY = 0.2f, keywords = listOf("music", "volume", "play", "pause", "track", "mute"),
        ),
        PaletteItem(
            "mouse-buttons", "Mouse buttons + wheel", "Left, middle and right click with wheel up and down between them.", PaletteCategory.GROUPS,
            listOf(
                mouse("left", Binding.Mouse(MouseKey.LEFT), "L", -62f, 0f, w = 56f, h = 72f, shape = ElementShape.SQUARE),
                mouse("wheel-up", Binding.Wheel(true), "▲", 0f, -38f, w = 48f, h = 34f, shape = ElementShape.PILL),
                mouse("middle", Binding.Mouse(MouseKey.MIDDLE), "M", 0f, 0f, w = 48f, h = 34f, shape = ElementShape.PILL),
                mouse("wheel-down", Binding.Wheel(false), "▼", 0f, 38f, w = 48f, h = 34f, shape = ElementShape.PILL),
                mouse("right", Binding.Mouse(MouseKey.RIGHT), "R", 62f, 0f, w = 56f, h = 72f, shape = ElementShape.SQUARE),
            ),
            anchorX = 0.85f, anchorY = 0.4f, keywords = listOf("click", "scroll", "wheel", "pc"),
        ),
    )

    // ------------------------------------------------------------ single elements

    private fun single(id: String, title: String, help: String, cat: PaletteCategory, e: ControlElement, x: Float = 0.5f, y: Float = 0.5f, keywords: List<String> = emptyList()) =
        PaletteItem(id, title, help, cat, listOf(e.copy(id = "e", x = 0f, y = 0f)), x, y, keywords)

    private val gamepad: List<PaletteItem> = buildList {
        val face = listOf(PadFlags.A to "A", PadFlags.B to "B", PadFlags.X to "X", PadFlags.Y to "Y")
        face.forEachIndexed { i, (f, n) -> add(single("pad-$n", "$n button", "Gamepad $n.", PaletteCategory.GAMEPAD, pad("e", n, f, tint = FACE_TINTS[i]), 0.8f, 0.6f, listOf("face"))) }
        add(single("pad-lb", "LB", "Left bumper.", PaletteCategory.GAMEPAD, shoulder("e", "LB", PadFlags.LB), 0.19f, 0.12f, listOf("bumper", "l1", "shoulder")))
        add(single("pad-rb", "RB", "Right bumper.", PaletteCategory.GAMEPAD, shoulder("e", "RB", PadFlags.RB), 0.81f, 0.12f, listOf("bumper", "r1", "shoulder")))
        add(single("pad-lt", "LT", "Left trigger, fully pressed while held.", PaletteCategory.GAMEPAD, trigger("e", Side.LEFT), 0.08f, 0.12f, listOf("trigger", "l2", "aim")))
        add(single("pad-rt", "RT", "Right trigger, fully pressed while held.", PaletteCategory.GAMEPAD, trigger("e", Side.RIGHT), 0.92f, 0.12f, listOf("trigger", "r2", "fire")))
        add(single("pad-ls", "Left stick", "Analog left stick; tap it for L3. Can float.", PaletteCategory.GAMEPAD, stick("e", StickOutput.LEFT, click = Binding.Pad(PadFlags.LS_CLK)), 0.2f, 0.72f, listOf("analog", "move", "thumbstick")))
        add(single("pad-rs", "Right stick", "Analog right stick; tap it for R3.", PaletteCategory.GAMEPAD, stick("e", StickOutput.RIGHT, click = Binding.Pad(PadFlags.RS_CLK)), 0.7f, 0.76f, listOf("analog", "camera", "thumbstick")))
        add(single("pad-dpad", "D-pad", "Four directions with diagonals, as one control.", PaletteCategory.GAMEPAD, newElement(ElementKind.DPAD, "e"), 0.12f, 0.45f, listOf("dpad", "directions", "cross")))
        add(single("pad-l3", "L3", "Left stick click as a button.", PaletteCategory.GAMEPAD, pad("e", "L3", PadFlags.LS_CLK, size = 44f), keywords = listOf("click", "ls")))
        add(single("pad-r3", "R3", "Right stick click as a button.", PaletteCategory.GAMEPAD, pad("e", "R3", PadFlags.RS_CLK, size = 44f), keywords = listOf("click", "rs")))
        add(single("pad-start", "Start", "The Start / Menu button.", PaletteCategory.GAMEPAD, pad("e", "Start", PadFlags.START, size = 40f, w = 68f, shape = ElementShape.PILL), 0.58f, 0.9f, listOf("menu", "options", "pause")))
        add(single("pad-select", "Select", "The Select / View / Back button.", PaletteCategory.GAMEPAD, pad("e", "Select", PadFlags.BACK, size = 40f, w = 68f, shape = ElementShape.PILL), 0.42f, 0.9f, listOf("back", "view", "share")))
        add(single("pad-guide", "Guide", "The home / guide button.", PaletteCategory.GAMEPAD, pad("e", "⌂", PadFlags.GUIDE, size = 44f), 0.5f, 0.9f, listOf("home", "xbox", "ps")))
        add(single("pad-share", "Share", "The share / capture button.", PaletteCategory.GAMEPAD, pad("e", "Share", PadFlags.MISC, size = 40f, w = 68f, shape = ElementShape.PILL), keywords = listOf("capture", "misc")))
        add(single("pad-touchpad", "Touchpad click", "The DualSense touchpad press.", PaletteCategory.GAMEPAD, pad("e", "TP", PadFlags.TOUCHPAD, size = 40f, w = 68f, shape = ElementShape.PILL), keywords = listOf("dualsense", "ps")))
        listOf(PadFlags.PADDLE1, PadFlags.PADDLE2, PadFlags.PADDLE3, PadFlags.PADDLE4).forEachIndexed { i, f ->
            add(single("pad-p${i + 1}", "Paddle ${i + 1}", "A back paddle (Elite / Edge controllers).", PaletteCategory.GAMEPAD, pad("e", "P${i + 1}", f, size = 44f), keywords = listOf("paddle", "back", "elite")))
        }
    }

    private val keyboard: List<PaletteItem> = buildList {
        add(single("key-dpad-wasd", "WASD pad", "A D-pad that sends W, A, S and D.", PaletteCategory.KEYBOARD, newElement(ElementKind.DPAD, "e").copy(bindings = WASD), 0.12f, 0.6f, listOf("movement", "dpad")))
        add(single("key-dpad-arrows", "Arrow pad", "A D-pad that sends the arrow keys.", PaletteCategory.KEYBOARD, newElement(ElementKind.DPAD, "e").copy(bindings = ARROWS), 0.88f, 0.6f, listOf("cursor", "dpad")))
        add(single("key-stick", "Key stick", "A stick that presses W, A, S and D as you push it.", PaletteCategory.KEYBOARD, stick("e", StickOutput.KEYS, click = Binding.None), 0.2f, 0.72f, listOf("wasd", "joystick", "movement")))
        val seen = HashSet<Int>()
        VirtualKeys.pickerGroups.forEach { (group, vks) ->
            vks.forEach { vk ->
                if (seen.add(vk)) {
                    val name = VirtualKeys.name(vk)
                    val wide = vk == 0x20 || vk == 0xA0 || vk == 0xA2 || vk == 0x0D || vk == 0x08
                    add(single("key-$vk", "$name key", "$group key.", PaletteCategory.KEYBOARD, key("e", vk, w = if (wide) 84f else 48f), keywords = listOf("key", group)))
                }
            }
        }
    }

    private val mouseTouch: List<PaletteItem> = listOf(
        single("mouse-left", "Left click", "The left mouse button.", PaletteCategory.MOUSE, mouse("e", Binding.Mouse(MouseKey.LEFT), "L"), keywords = listOf("mouse", "button", "lmb")),
        single("mouse-right", "Right click", "The right mouse button.", PaletteCategory.MOUSE, mouse("e", Binding.Mouse(MouseKey.RIGHT), "R"), keywords = listOf("mouse", "button", "rmb")),
        single("mouse-middle", "Middle click", "The wheel button.", PaletteCategory.MOUSE, mouse("e", Binding.Mouse(MouseKey.MIDDLE), "M"), keywords = listOf("mouse", "button")),
        single("mouse-back", "Back (X1)", "The mouse's back side button.", PaletteCategory.MOUSE, mouse("e", Binding.Mouse(MouseKey.BACK), "X1"), keywords = listOf("mouse", "side", "x1")),
        single("mouse-forward", "Forward (X2)", "The mouse's forward side button.", PaletteCategory.MOUSE, mouse("e", Binding.Mouse(MouseKey.FORWARD), "X2"), keywords = listOf("mouse", "side", "x2")),
        single("wheel-up", "Wheel up", "One scroll notch per press; repeats while held.", PaletteCategory.MOUSE, mouse("e", Binding.Wheel(true), "▲", w = 56f, h = 40f, shape = ElementShape.PILL), keywords = listOf("scroll", "mouse")),
        single("wheel-down", "Wheel down", "One scroll notch per press; repeats while held.", PaletteCategory.MOUSE, mouse("e", Binding.Wheel(false), "▼", w = 56f, h = 40f, shape = ElementShape.PILL), keywords = listOf("scroll", "mouse")),
        single("touchpad", "Touchpad", "A region that moves the PC's mouse; tap to click.", PaletteCategory.MOUSE, newElement(ElementKind.TOUCHPAD, "e"), keywords = listOf("trackpad", "pointer", "cursor")),
        single(
            "zone-camera-stick", "Camera → stick", "Swipe on this area to look with the right stick.", PaletteCategory.MOUSE,
            newElement(ElementKind.ZONE, "e"), 0.75f, 0.5f, listOf("zone", "look", "aim", "swipe"),
        ),
        single(
            "zone-camera-mouse", "Camera → mouse", "Swipe on this area to look with the mouse, 1:1 like a PC mouse.", PaletteCategory.MOUSE,
            newElement(ElementKind.ZONE, "e").copy(zone = ZoneType.CAMERA_MOUSE, label = "Mouse look"), 0.75f, 0.5f, listOf("zone", "look", "aim", "swipe"),
        ),
        single(
            "zone-floating-stick", "Floating joystick", "A stick appears where your thumb lands in this area.", PaletteCategory.MOUSE,
            newElement(ElementKind.ZONE, "e").copy(zone = ZoneType.FLOATING_STICK, stick = StickOutput.LEFT, label = "Move", keepWithController = false), 0.25f, 0.5f,
            listOf("zone", "move", "dynamic"),
        ),
    )

    private val advanced: List<PaletteItem> = listOf(
        single("button", "Button", "A blank button: pick what it sends (pad, key or mouse). Hold or toggle.", PaletteCategory.ADVANCED, newElement(ElementKind.BUTTON, "e"), keywords = listOf("custom")),
        single("combo", "Combo", "Several buttons or keys pressed together while held, like Ctrl + C.", PaletteCategory.ADVANCED, newElement(ElementKind.COMBO, "e"), keywords = listOf("chord", "shortcut", "together")),
        single("macro", "Macro", "A timed sequence of presses, played once per tap.", PaletteCategory.ADVANCED, newElement(ElementKind.MACRO, "e"), keywords = listOf("sequence", "script")),
        single("trigger", "Trigger", "An analog trigger (LT or RT), fully pressed while held.", PaletteCategory.ADVANCED, newElement(ElementKind.TRIGGER, "e"), 0.92f, 0.12f),
        single("stick", "Stick", "An analog stick; switch it to left, right or direction keys.", PaletteCategory.ADVANCED, newElement(ElementKind.STICK, "e")),
        single(
            "layout-switch", "Layout switch", "Changes to another layout of the game's layout set (next, previous or a picker). Lets go of everything first.",
            PaletteCategory.ADVANCED, newElement(ElementKind.SWITCH, "e"), 0.5f, 0.06f, listOf("set", "layouts", "next", "previous", "picker", "mode", "page", "vehicle"),
        ),
    )

    /** Everything, in display order. */
    val items: List<PaletteItem> = groups + gamepad + keyboard + mouseTouch + advanced

    val byId: Map<String, PaletteItem> = items.associateBy { it.id }

    fun inCategory(c: PaletteCategory?): List<PaletteItem> = if (c == null) items else items.filter { it.category == c }

    fun search(query: String, category: PaletteCategory? = null): List<PaletteItem> = inCategory(category).filter { it.matches(query) }

    // ------------------------------------------------------------ placing

    /** Space kept between a newly placed item and the controls around it, dp. */
    private const val CLEARANCE = 4f

    /**
     * The box centre (shares) nearest ([x], [y]) where a box of half-size [hw] × [hh] (shares)
     * overlaps none of [occupied]: ([x], [y]) itself when that is free, null when nowhere is.
     * Candidates are 16 dp apart.
     */
    fun freeSpot(
        x: Float, y: Float, hw: Float, hh: Float,
        xs: ClosedFloatingPointRange<Float>, ys: ClosedFloatingPointRange<Float>,
        occupied: List<ControlElement>, areaW: Float, areaH: Float,
    ): Pair<Float, Float>? {
        val boxes = occupied.filterNot { it.areaSized }.map { o ->
            floatArrayOf(o.x * areaW - o.width / 2, o.y * areaH - o.height / 2, o.x * areaW + o.width / 2, o.y * areaH + o.height / 2)
        }
        fun free(px: Float, py: Float): Boolean {
            val l = px * areaW - hw * areaW - CLEARANCE; val r = px * areaW + hw * areaW + CLEARANCE
            val t = py * areaH - hh * areaH - CLEARANCE; val b = py * areaH + hh * areaH + CLEARANCE
            return boxes.none { o -> l < o[2] && r > o[0] && t < o[3] && b > o[1] }
        }
        if (free(x, y)) return x to y
        val stepX = 16f / areaW
        val stepY = 16f / areaH
        var best: Pair<Float, Float>? = null
        var bestD = Float.MAX_VALUE
        var px = xs.start
        while (px <= xs.endInclusive + 1e-6f) {
            var py = ys.start
            while (py <= ys.endInclusive + 1e-6f) {
                val dx = (px - x) * areaW
                val dy = (py - y) * areaH
                val d = dx * dx + dy * dy
                if (d < bestD && free(px, py)) { bestD = d; best = px to py }
                py += stepY
            }
            px += stepX
        }
        return best
    }

    /** Bounds of [parts] around the item centre, dp: (left, top, right, bottom). */
    fun bounds(parts: List<ControlElement>): FloatArray {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        parts.forEach { p ->
            l = min(l, p.x - p.width / 2); r = max(r, p.x + p.width / 2)
            t = min(t, p.y - p.height / 2); b = max(b, p.y + p.height / 2)
        }
        return floatArrayOf(l, t, r, b)
    }

    /**
     * Real elements for [item] in a controls area of [areaW] × [areaH] dp, centred on
     * ([cx], [cy]) (shares; defaults to the item's anchor, moved into the lower part of a
     * portrait area). A group too wide for the area is shrunk to fit, then kept fully on screen.
     * [newId] names each element; [groupId] (a group only) is shared by every member.
     * When it would land on top of one of the [occupied] controls (zones don't count), it moves
     * to the nearest free spot, if there is one.
     */
    fun place(
        item: PaletteItem,
        areaW: Float,
        areaH: Float,
        newId: () -> String,
        groupId: String? = null,
        portrait: Boolean = areaH > areaW,
        cx: Float = item.anchorX,
        cy: Float = if (portrait) 0.55f + item.anchorY * 0.4f else item.anchorY,
        occupied: List<ControlElement> = emptyList(),
    ): List<ControlElement> {
        val parts = item.parts
        val zoneOnly = parts.size == 1 && parts[0].areaSized
        if (zoneOnly) return listOf(parts[0].copy(id = newId(), x = cx, y = cy, group = null).clampedSize())
        val (l, t, r, b) = bounds(parts).let { listOf(it[0], it[1], it[2], it[3]) }
        val bw = (r - l).coerceAtLeast(1f)
        val bh = (b - t).coerceAtLeast(1f)
        // Shrink to fit 96 % of the area, but never below the smallest element size.
        val smallest = parts.minOf { min(it.width, it.height) }
        val fit = min(1f, min(areaW * 0.96f / bw, areaH * 0.96f / bh)).coerceIn(min(1f, ControlElement.MIN_SIZE_DP / smallest), 1f)
        // Keep the whole box on screen.
        val halfW = bw * fit / 2 / areaW
        val halfH = bh * fit / 2 / areaH
        val midX = (l + r) / 2 * fit / areaW
        val midY = (t + b) / 2 * fit / areaH
        val minX = min(halfW, 0.5f); val maxX = max(1f - halfW, 0.5f)
        val minY = min(halfH, 0.5f); val maxY = max(1f - halfH, 0.5f)
        var boxX = (cx + midX).coerceIn(minX, maxX)
        var boxY = (cy + midY).coerceIn(minY, maxY)
        freeSpot(boxX, boxY, halfW, halfH, minX..maxX, minY..maxY, occupied, areaW, areaH)?.let { (fx, fy) -> boxX = fx; boxY = fy }
        val centreX = boxX - midX
        val centreY = boxY - midY
        val g = if (parts.size > 1) groupId else null
        return parts.map { p ->
            p.copy(
                id = newId(),
                x = centreX + p.x * fit / areaW,
                y = centreY + p.y * fit / areaH,
                width = p.width * fit,
                height = p.height * fit,
                group = g,
            ).clampedSize()
        }
    }
}
