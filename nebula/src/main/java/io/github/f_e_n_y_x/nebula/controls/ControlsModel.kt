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

    /** Stable text form used in Nebula's JSON: `pad:4096`, `key:65`, `mouse:left`, `lt`, `wheel:up`, `none`. */
    fun token(): String = when (this) {
        None -> "none"
        is Pad -> "pad:$flag"
        is Trigger -> if (side == Side.LEFT) "lt" else "rt"
        is Key -> "key:$vk"
        is Mouse -> "mouse:${button.id}"
        is Wheel -> if (up) "wheel:up" else "wheel:down"
    }

    companion object {
        fun parse(token: String?): Binding {
            val t = token?.trim()?.lowercase() ?: return None
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
}

/** What a [ElementKind.ZONE] does with a finger. */
enum class ZoneType(val id: String, val label: String, val help: String) {
    CAMERA_STICK("camera_stick", "Camera → stick", "Swipe to look: the camera turns with your finger and stops when it stops. Limited to the game's full-stick turn speed."),
    CAMERA_MOUSE("camera_mouse", "Camera → mouse", "Swipe to look with the mouse: 1:1 like a PC mouse. Recommended for games that take mouse look together with a pad, such as GTA V on PC."),
    FLOATING_STICK("floating_stick", "Floating joystick", "A stick appears where your thumb lands; drag to push it."),
}

/** Hold: pressed while the finger is down. Toggle: first tap latches, second tap releases. */
enum class PressMode(val id: String, val label: String) { HOLD("hold", "Hold"), TOGGLE("toggle", "Toggle") }

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
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
    )

    companion object {
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
    /** Where it came from: null (made here), "crown" (V+ import) or "builtin". */
    val origin: String? = null,
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
}

/** True when some zone keeps working with a physical controller attached. */
fun ControlsProfile.hasControllerZones(): Boolean = (landscape + portrait.orEmpty()).any { it.kind == ElementKind.ZONE && it.keepWithController }
