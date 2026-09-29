package io.github.f_e_n_y_x.nebula.controls

/**
 * The on-screen controls, as data. A [ControlsProfile] holds one layout per orientation; each
 * [ControlElement] sits at a centre given as a share of the controls area (so a layout carries
 * across screen sizes) with a size in dp (so a button stays thumb-sized on every screen).
 *
 * Nothing here touches Android, so the model, JSON, Crown import, snapping and undo are all
 * plain unit-testable Kotlin.
 */

/** What an element sends to the PC. */
sealed interface Binding {
    /** Nothing (an unused slot). */
    data object None : Binding
    /** A gamepad button, as a `ControllerPacket` flag (A = 0x1000, LB = 0x100, …). */
    data class Pad(val flag: Int) : Binding
    /** An analog trigger, fully pressed while held. */
    data class Trigger(val side: Side) : Binding
    /** A PC key, as a Windows virtual-key code. */
    data class Key(val vk: Int) : Binding
    /** A mouse button. */
    data class Mouse(val button: MouseKey) : Binding
    /** One mouse-wheel notch per press (repeats while held). */
    data class Wheel(val up: Boolean) : Binding
    /**
     * Several bindings pressed together (layout format 2): Shift+E, LB+RB, Ctrl + left click.
     * Pressed in order, released in reverse. [parts] are 2..[MAX_CHORD] plain bindings, all different.
     */
    data class Chord(val parts: List<Binding>) : Binding

    /** What this presses: a chord's parts, one plain binding, or nothing for [None]. */
    fun parts(): List<Binding> = when (this) {
        None -> emptyList()
        is Chord -> parts
        else -> listOf(this)
    }

    /** Stable text form used in Nebula's JSON: `pad:4096`, `key:65`, `mouse:left`, `lt`, `wheel:up`, `none`. */
    fun token(): String = when (this) {
        None -> "none"
        is Pad -> "pad:$flag"
        is Trigger -> if (side == Side.LEFT) "lt" else "rt"
        is Key -> "key:$vk"
        is Mouse -> "mouse:${button.id}"
        is Wheel -> if (up) "wheel:up" else "wheel:down"
        is Chord -> parts.joinToString("+") { it.token() }
    }

    companion object {
        const val MAX_CHORD = 4

        /**
         * [list] pressed together as one binding: nothing, the one binding, or a [Chord] (nested
         * chords flattened, [None] and repeats dropped, at most [MAX_CHORD] parts).
         */
        fun chordOf(list: List<Binding>): Binding {
            val flat = list.flatMap { it.parts() }.distinct().take(MAX_CHORD)
            return when (flat.size) {
                0 -> None
                1 -> flat[0]
                else -> Chord(flat)
            }
        }

        fun parse(token: String?): Binding {
            val raw = token?.trim()?.lowercase() ?: return None
            if ('+' in raw) {
                val parts = raw.split('+').map { parseOne(it) }
                // A damaged chord (a part Nebula doesn't know) is nothing rather than half a chord.
                return if (parts.any { it == None } || parts.size > MAX_CHORD) None else chordOf(parts)
            }
            return parseOne(raw)
        }

        private fun parseOne(token: String): Binding {
            val t = token.trim()
            val (head, arg) = t.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
            return when (head) {
                "pad" -> arg?.let(::parseInt)?.let { Pad(it) } ?: None
                "key" -> arg?.let(::parseInt)?.takeIf { it in 1..0xFE }?.let { Key(it) } ?: None
                "lt" -> Trigger(Side.LEFT)
                "rt" -> Trigger(Side.RIGHT)
                "mouse" -> MouseKey.entries.firstOrNull { it.id == arg }?.let { Mouse(it) } ?: None
                "wheel" -> Wheel(arg != "down")
                else -> None
            }
        }

        private fun parseInt(s: String): Int? = if (s.startsWith("0x")) s.drop(2).toIntOrNull(16) else s.toIntOrNull()
    }
}

enum class Side { LEFT, RIGHT }

enum class MouseKey(val id: String, val label: String) {
    LEFT("left", "Left click"), MIDDLE("middle", "Middle click"), RIGHT("right", "Right click"),
    BACK("back", "Back (X1)"), FORWARD("forward", "Forward (X2)"),
}

/** The kinds of element the editor can place. */
enum class ElementKind(val id: String, val label: String) {
    BUTTON("button", "Button"),
    DPAD("dpad", "D-pad"),
    STICK("stick", "Stick"),
    TRIGGER("trigger", "Trigger"),
    TOUCHPAD("touchpad", "Touchpad"),
    /** Several bindings pressed together while held (Ctrl+C, LB+RB…). */
    COMBO("combo", "Combo"),
    /** A timed sequence played once per press. */
    MACRO("macro", "Macro"),
    /** A screen area (e.g. the right half) for camera look or a floating stick; sized as a share of the screen. */
    ZONE("zone", "Touch zone"),
    /** Goes to another layout of the game's layout set; sends nothing to the PC (layout format 2). */
    SWITCH("switch", "Layout switch"),
}

/**
 * Where a [ElementKind.SWITCH] goes. In a stored profile [Layout.id] is the target profile's id;
 * in a layout file it is the file's layout id ([LayoutFile] maps between them).
 */
sealed interface SwitchTarget {
    data object Next : SwitchTarget
    data object Previous : SwitchTarget
    /** A small list of the set's layouts. */
    data object Picker : SwitchTarget
    data class Layout(val id: String) : SwitchTarget

    fun token(): String = when (this) {
        Next -> "next"
        Previous -> "previous"
        Picker -> "picker"
        is Layout -> "layout:$id"
    }

    companion object {
        fun parse(token: String?): SwitchTarget? = when {
            token == null || token == "next" -> Next
            token == "previous" -> Previous
            token == "picker" -> Picker
            token.startsWith("layout:") && token.length > 7 -> Layout(token.substring(7))
            else -> null
        }
    }
}

/** What a [ElementKind.ZONE] does with a finger. */
enum class ZoneType(val id: String, val label: String, val help: String) {
    CAMERA_STICK("camera_stick", "Camera → stick", "Swipe to look: the camera turns with your finger and stops when it stops. Limited to the game's full-stick turn speed."),
    CAMERA_MOUSE("camera_mouse", "Camera → mouse", "Swipe to look with the mouse: 1:1 like a PC mouse. Recommended for games that take mouse look together with a pad, such as GTA V on PC."),
    FLOATING_STICK("floating_stick", "Floating joystick", "A stick appears where your thumb lands; drag to push it."),
}

/**
 * Hold: pressed while the finger is down. Toggle: first tap latches, second tap releases.
 * Mixed (the "tap or hold" aim common in mobile shooters): a quick tap latches like Toggle, a long
 * press is held only while the finger stays down.
 */
enum class PressMode(val id: String, val label: String) { HOLD("hold", "Hold"), TOGGLE("toggle", "Toggle"), MIXED("mixed", "Tap or hold") }

/**
 * What an element is for in a shooter layout. Nothing is sent differently because of it, except
 * [FIRE] and [ADS], which count as "aiming" for the gyro's "While aiming or firing" option; it
 * also drives the first-run tutorial and lets tools (and the library) describe a layout.
 */
enum class ElementRole(val id: String, val label: String) {
    NONE("none", "None"),
    FIRE("fire", "Fire"),
    ADS("ads", "Aim down sights"),
    MOVE("move", "Move"),
    SPRINT("sprint", "Sprint"),
    JUMP("jump", "Jump"),
    CROUCH("crouch", "Crouch"),
    PRONE("prone", "Prone"),
    RELOAD("reload", "Reload"),
    SWITCH("switch", "Switch weapon"),
    INTERACT("interact", "Interact"),
    PEEK_LEFT("peek_left", "Peek left"),
    PEEK_RIGHT("peek_right", "Peek right"),
    FREE_LOOK("free_look", "Free look"),
    MENU("menu", "Menu / map"),
    ;

    val aims: Boolean get() = this == FIRE || this == ADS

    companion object {
        fun of(id: String?): ElementRole? = entries.firstOrNull { it.id == id }
    }
}

enum class ElementShape(val id: String, val label: String) { ROUND("round", "Round"), PILL("pill", "Pill"), SQUARE("square", "Square") }

/** Where a stick's movement goes. KEYS turns it into four direction keys (WASD-style). */
enum class StickOutput(val id: String, val label: String) { LEFT("left", "Left stick"), RIGHT("right", "Right stick"), KEYS("keys", "Direction keys") }

/** One step of a macro: hold [binding] for [holdMs], then wait [gapMs] before the next step. */
data class MacroStep(val binding: Binding, val holdMs: Int = 60, val gapMs: Int = 40)

/**
 * One on-screen element.
 *
 * [x], [y] are the centre as a share (0–1) of the controls area; [width], [height] are in dp.
 * [opacity] multiplies the global overlay opacity. [bindings] depend on [kind]:
 * BUTTON/TRIGGER one, COMBO up to five, DPAD and a KEYS stick four (up, down, left, right).
 */
data class ControlElement(
    val id: String,
    val kind: ElementKind,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val label: String = "",
    val opacity: Float = 1f,
    val mode: PressMode = PressMode.HOLD,
    val shape: ElementShape = ElementShape.ROUND,
    val bindings: List<Binding> = emptyList(),
    val stick: StickOutput = StickOutput.LEFT,
    /** Stick press (L3/R3) or touchpad tap; [Binding.None] turns it off. */
    val click: Binding = Binding.None,
    /** Stick only: the stick's centre follows where the finger lands inside the area. */
    val floating: Boolean = false,
    /** Stick only: share of the radius that sends nothing (direction keys) or zero. */
    val deadzone: Float = 0.15f,
    /** Touchpad only: pointer speed multiplier. */
    val sensitivity: Float = 1f,
    val steps: List<MacroStep> = emptyList(),
    /** Label tint (ARGB), e.g. the face-button colours; null uses the theme text colour. */
    val tint: Long? = null,
    /** Zone only: what it does ([stick] picks which stick for the stick types). */
    val zone: ZoneType = ZoneType.CAMERA_STICK,
    /** Zone only: response curve exponent; 1 is linear, above 1 slow moves get finer and fast ones stronger. */
    val acceleration: Float = 1f,
    val invertY: Boolean = false,
    /** Floating joystick zone: draw the ring where the thumb is. */
    val showRing: Boolean = true,
    /** Zone only: keeps working while a physical controller hides the other on-screen controls. */
    val keepWithController: Boolean = false,
    /** Button / trigger: after [LOOK_THROUGH_DP] of drag the same finger also looks (fire and aim). */
    val lookThrough: Boolean = false,
    /** Camera → stick: smallest push once the finger moves (0–0.45), to clear the game's stick deadzone. */
    val antiDeadzone: Float = DEFAULT_ANTI_DEADZONE,
    /**
     * Move stick (a stick element or a floating-stick zone): held while the thumb is pushed
     * forward past the ring ([sprintAt] × the radius), like auto-sprint in mobile shooters.
     * [Binding.None] turns it off. L3 on a pad, Shift on a keyboard.
     */
    val sprint: Binding = Binding.None,
    /** Where auto-sprint starts, as a multiple of the stick radius (1 = the ring itself). */
    val sprintAt: Float = DEFAULT_SPRINT_AT,
    /**
     * Move stick: drag up past the lock mark ([RUN_LOCK_AT] × the radius) and let go to keep
     * running forward (and sprinting, with [sprint]); touch the stick again to stop.
     */
    val runLock: Boolean = false,
    /** What the element is for (fire, aim, jump…); see [ElementRole]. */
    val role: ElementRole = ElementRole.NONE,
    /**
     * Editor-only: elements placed together from a ready-made group (ABXY, WASD…) share this id,
     * so the editor moves, resizes, duplicates and deletes them as one until they're split.
     * `"<template>:<random>"`, e.g. `"abxy:3f9a1c"`. Play ignores it: every member is an
     * ordinary element. Null for a loose element. Layout format 2.
     */
    val group: String? = null,
    /** Layout switch only: where it goes. */
    val switchTo: SwitchTarget = SwitchTarget.Next,
) {

    /** Zones are sized as a share of the controls area; everything else in dp. */
    val areaSized: Boolean get() = kind == ElementKind.ZONE

    val binding: Binding get() = bindings.firstOrNull() ?: Binding.None

    /** Size limits so an element can't vanish or swallow the screen. */
    fun clampedSize(): ControlElement = copy(
        width = if (areaSized) width.coerceIn(MIN_ZONE, 1f) else width.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        height = if (areaSized) height.coerceIn(MIN_ZONE, 1f) else height.coerceIn(MIN_SIZE_DP, MAX_SIZE_DP),
        acceleration = acceleration.coerceIn(0.5f, 2.5f),
        opacity = opacity.coerceIn(MIN_OPACITY, 1f),
        sprintAt = sprintAt.coerceIn(1f, 2f),
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
    )

    companion object {
        /** GTA V's stick deadzone is about 20 %. */
        const val DEFAULT_ANTI_DEADZONE = 0.22f
        /**
         * Fire-and-look: travel before the finger also looks. Tiny, so aiming starts at once (as
         * mobile shooters do); the travel up to it is not lost, it is applied when looking starts.
         */
        const val LOOK_THROUGH_DP = 3f
        const val DEFAULT_SPRINT_AT = 1.25f
        /** Run lock mark, as a multiple of the stick radius above its centre. */
        const val RUN_LOCK_AT = 2.1f
        /** Auto-sprint only counts a push within this angle of straight up (degrees). */
        const val SPRINT_CONE_DEG = 50f
        const val MIN_SIZE_DP = 28f
        const val MAX_SIZE_DP = 360f
        const val MIN_OPACITY = 0.1f
        /** Smallest zone, as a share of the screen side. */
        const val MIN_ZONE = 0.08f
    }
}

enum class LayoutOrientation(val id: String) { LANDSCAPE("landscape"), PORTRAIT("portrait") }

/**
 * A named set of controls. [portrait] is null until the user edits the portrait layout; until
 * then portrait shows the landscape layout.
 */
data class ControlsProfile(
    val id: String,
    val name: String,
    val landscape: List<ControlElement>,
    val portrait: List<ControlElement>? = null,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    /** Where it came from: null (made here), "crown" (V+ import), "builtin" or "library". */
    val origin: String? = null,
    /** What touches outside the controls do; null follows the user's choice / the default. */
    val outside: OutsideTouch? = null,
    /** How look fingers turn the camera; null follows the user's choice / the default. */
    val look: LookOutput? = null,
    /** Sharing metadata (author, game, target…) kept from a layout file, for re-sharing. */
    val meta: LayoutMeta? = null,
) {
    val isBuiltIn: Boolean get() = id.startsWith(BUILTIN_PREFIX)

    fun layout(o: LayoutOrientation): List<ControlElement> = if (o == LayoutOrientation.PORTRAIT) portrait ?: landscape else landscape

    fun withLayout(o: LayoutOrientation, elements: List<ControlElement>): ControlsProfile =
        if (o == LayoutOrientation.PORTRAIT) copy(portrait = elements) else copy(landscape = elements)

    companion object {
        const val BUILTIN_PREFIX = "builtin:"
        const val STANDARD_ID = "builtin:standard"
        const val GTA_ID = "builtin:gta-touch-camera"
        const val GTA_MOUSE_ID = "builtin:gta-mouse-camera"
        const val GTA_TOUCH_ID = "builtin:gta-touch-only"
        const val TOUCH_SHOOTER_PAD_ID = "builtin:touch-shooter-pad"
        const val TOUCH_SHOOTER_KBM_ID = "builtin:touch-shooter-kbm"
        const val GTA_TOUCH_CONTROLS_ID = "builtin:gta-touch-controls"

        /**
         * Built-in ids from 0.3.0-dev15 and earlier, and what they became. Saved defaults,
         * per-game assignments and per-profile settings that name an old id are moved over when
         * they're read ([ProfileJson.decodeStore], [ProfilePrefsMigration]).
         */
        val RENAMED_IDS: Map<String, String> = mapOf(
            "builtin:shooter-pubg-pad" to TOUCH_SHOOTER_PAD_ID,
            "builtin:shooter-pubg-kbm" to TOUCH_SHOOTER_KBM_ID,
            "builtin:gta-pubg" to GTA_TOUCH_CONTROLS_ID,
        )

        /** [id], or what it was renamed to. */
        fun currentId(id: String): String = RENAMED_IDS[id] ?: id
    }
}

/** A new element of [kind], centred at ([x], [y]), with sensible defaults. */
fun newElement(kind: ElementKind, id: String, x: Float = 0.5f, y: Float = 0.5f): ControlElement = when (kind) {
    ElementKind.BUTTON -> ControlElement(id, kind, x, y, 56f, 56f, label = "A", bindings = listOf(Binding.Pad(PadFlags.A)))
    ElementKind.DPAD -> ControlElement(id, kind, x, y, 128f, 128f, bindings = PadFlags.DPAD.map { Binding.Pad(it) })
    ElementKind.STICK -> ControlElement(id, kind, x, y, 120f, 120f, label = "LS", stick = StickOutput.LEFT, click = Binding.Pad(PadFlags.LS_CLK))
    ElementKind.TRIGGER -> ControlElement(id, kind, x, y, 68f, 44f, label = "RT", shape = ElementShape.SQUARE, bindings = listOf(Binding.Trigger(Side.RIGHT)))
    ElementKind.TOUCHPAD -> ControlElement(id, kind, x, y, 200f, 130f, label = "Touchpad", shape = ElementShape.SQUARE, click = Binding.Mouse(MouseKey.LEFT))
    ElementKind.COMBO -> ControlElement(
        id, kind, x, y, 72f, 48f, label = "LB+RB", shape = ElementShape.PILL,
        bindings = listOf(Binding.Pad(PadFlags.LB), Binding.Pad(PadFlags.RB)),
    )
    ElementKind.ZONE -> ControlElement(
        id, kind, 0.75f, 0.5f, 0.5f, 1f, label = "Camera", shape = ElementShape.SQUARE,
        zone = ZoneType.CAMERA_STICK, stick = StickOutput.RIGHT, keepWithController = true, deadzone = 0f,
    )
    ElementKind.MACRO -> ControlElement(
        id, kind, x, y, 72f, 48f, label = "Macro", shape = ElementShape.PILL,
        steps = listOf(MacroStep(Binding.Pad(PadFlags.A)), MacroStep(Binding.Pad(PadFlags.B))),
    )
    ElementKind.SWITCH -> ControlElement(id, kind, x, y, 88f, 34f, shape = ElementShape.PILL, opacity = 0.9f)
}

/** Gamepad button flags; the same values as the engine's `ControllerPacket` (and V+ Crown's `g` codes). */
object PadFlags {
    const val UP = 0x0001
    const val DOWN = 0x0002
    const val LEFT = 0x0004
    const val RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val LS_CLK = 0x0040
    const val RS_CLK = 0x0080
    const val LB = 0x0100
    const val RB = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000
    const val PADDLE1 = 0x010000
    const val PADDLE2 = 0x020000
    const val PADDLE3 = 0x040000
    const val PADDLE4 = 0x080000
    const val TOUCHPAD = 0x100000
    const val MISC = 0x200000

    /** D-pad order used by [ElementKind.DPAD] bindings: up, down, left, right. */
    val DPAD = listOf(UP, DOWN, LEFT, RIGHT)

    val names: Map<Int, String> = linkedMapOf(
        A to "A", B to "B", X to "X", Y to "Y", LB to "LB", RB to "RB", START to "Start", BACK to "Select",
        GUIDE to "Guide", LS_CLK to "L3", RS_CLK to "R3", UP to "Up", DOWN to "Down", LEFT to "Left", RIGHT to "Right",
        PADDLE1 to "Paddle 1", PADDLE2 to "Paddle 2", PADDLE3 to "Paddle 3", PADDLE4 to "Paddle 4", TOUCHPAD to "Touchpad click", MISC to "Share",
    )
}

/** A short, readable name for a binding (inspector and generated labels). */
fun Binding.describe(): String = when (this) {
    Binding.None -> "None"
    is Binding.Pad -> PadFlags.names[flag] ?: "Button 0x${flag.toString(16)}"
    is Binding.Trigger -> if (side == Side.LEFT) "LT" else "RT"
    is Binding.Key -> VirtualKeys.name(vk)
    is Binding.Mouse -> button.label
    is Binding.Wheel -> if (up) "Wheel up" else "Wheel down"
    is Binding.Chord -> parts.joinToString(" + ") { it.describe() }
}

/** A shooter layout: something fires and a finger can aim with it (tutorial, style picker). */
fun ControlsProfile.isShooter(): Boolean = (landscape + portrait.orEmpty()).any { it.role == ElementRole.FIRE }

/** True when some zone keeps working with a physical controller attached. */
fun ControlsProfile.hasControllerZones(): Boolean = (landscape + portrait.orEmpty()).any { it.kind == ElementKind.ZONE && it.keepWithController }
