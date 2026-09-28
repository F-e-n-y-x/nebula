package io.github.f_e_n_y_x.nebula.controls

/** Face-button naming, from V+'s "Virtual controller layout" (list_osc_layout). */
enum class PadStyle(val id: String) {
    XBOX("xbox"), DUALSENSE("ds"), SWITCH("ns"), CLASSIC("classic");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: XBOX
    }
}

/** What the built-in "Standard" profile reads from Settings, so those settings keep working. */
data class StandardOptions(
    val style: PadStyle = PadStyle.XBOX,
    val showGuide: Boolean = true,
    /** Separate L3 / R3 buttons instead of clicking the sticks. */
    val l3r3Buttons: Boolean = false,
    /** Portrait keeps the pad in the lower half, under the picture (V+ "half-height portrait"); off uses the landscape layout full screen. */
    val portraitHalfHeight: Boolean = true,
)

/**
 * The built-in gamepad, the same arrangement Nebula shipped before the editor: shoulders and
 * triggers on top, D-pad over the left stick, face buttons beside the right stick, Select / Guide
 * / Start at the bottom. Portrait keeps everything in the lower half, under the picture.
 */
object DefaultProfiles {
    fun standard(o: StandardOptions = StandardOptions()): ControlsProfile = ControlsProfile(
        id = ControlsProfile.STANDARD_ID,
        name = "Standard gamepad",
        landscape = landscape(o),
        portrait = if (o.portraitHalfHeight) portrait(o) else null,
        origin = "builtin",
    )

    private data class Face(val bottom: String, val right: String, val left: String, val top: String)

    private fun face(style: PadStyle) = when (style) {
        PadStyle.DUALSENSE -> Face("✕", "○", "□", "△")
        // Nintendo names by position: B bottom, A right, Y left, X top (the PC sees Xbox positions).
        PadStyle.SWITCH -> Face("B", "A", "Y", "X")
        else -> Face("A", "B", "X", "Y")
    }

    private fun tints(style: PadStyle): List<Long?> = when (style) {
        PadStyle.XBOX -> listOf(0xFF4CC38A, 0xFFE5534B, 0xFF4F8DF7, 0xFFE8C547)
        PadStyle.DUALSENSE -> listOf(0xFF7AA7FF, 0xFFF2766E, 0xFFE29BD9, 0xFF4CC38A)
        else -> listOf(null, null, null, null)
    }

    private fun build(
        o: LayoutOrientation,
        opts: StandardOptions,
    ): List<ControlElement> {
        val land = o == LayoutOrientation.LANDSCAPE
        val f = face(opts.style)
        val t = tints(opts.style)
        val out = mutableListOf<ControlElement>()
        fun add(e: ControlElement) { out += e }
        fun btn(id: String, label: String, flag: Int, x: Float, y: Float, size: Float = 52f, tint: Long? = null, shape: ElementShape = ElementShape.ROUND, w: Float = size) =
            add(ControlElement(id, ElementKind.BUTTON, x, y, w, size, label = label, shape = shape, bindings = listOf(Binding.Pad(flag)), tint = tint))
        fun trig(id: String, label: String, side: Side, x: Float, y: Float) =
            add(ControlElement(id, ElementKind.TRIGGER, x, y, 68f, 42f, label = label, shape = ElementShape.SQUARE, bindings = listOf(Binding.Trigger(side))))
        fun stick(id: String, label: String, out: StickOutput, click: Int, x: Float, y: Float, size: Float) =
            add(
                ControlElement(
                    id, ElementKind.STICK, x, y, size, size, label = label, stick = out,
                    click = if (opts.l3r3Buttons) Binding.None else Binding.Pad(click),
                ),
            )

        if (land) {
            trig("lt", "LT", Side.LEFT, 0.06f, 0.09f)
            btn("lb", "LB", PadFlags.LB, 0.16f, 0.09f, size = 44f, w = 68f, shape = ElementShape.PILL)
            btn("rb", "RB", PadFlags.RB, 0.84f, 0.09f, size = 44f, w = 68f, shape = ElementShape.PILL)
            trig("rt", "RT", Side.RIGHT, 0.94f, 0.09f)
            add(ControlElement("dpad", ElementKind.DPAD, 0.105f, 0.45f, 124f, 124f, bindings = PadFlags.DPAD.map { Binding.Pad(it) }))
            stick("ls", "LS", StickOutput.LEFT, PadFlags.LS_CLK, 0.225f, 0.78f, 120f)
            stick("rs", "RS", StickOutput.RIGHT, PadFlags.RS_CLK, 0.70f, 0.80f, 108f)
            btn("a", f.bottom, PadFlags.A, 0.88f, 0.75f, tint = t[0])
            btn("b", f.right, PadFlags.B, 0.945f, 0.62f, tint = t[1])
            btn("x", f.left, PadFlags.X, 0.815f, 0.62f, tint = t[2])
            btn("y", f.top, PadFlags.Y, 0.88f, 0.49f, tint = t[3])
            btn("select", "Select", PadFlags.BACK, 0.42f, 0.9f, size = 40f, w = 68f, shape = ElementShape.PILL)
            if (opts.showGuide) btn("guide", "⌂", PadFlags.GUIDE, 0.5f, 0.9f, size = 44f)
            btn("start", "Start", PadFlags.START, 0.58f, 0.9f, size = 40f, w = 68f, shape = ElementShape.PILL)
            if (opts.l3r3Buttons) {
                btn("l3", "L3", PadFlags.LS_CLK, 0.34f, 0.9f, size = 44f)
                btn("r3", "R3", PadFlags.RS_CLK, 0.66f, 0.9f, size = 44f)
            }
        } else {
            trig("lt", "LT", Side.LEFT, 0.1f, 0.53f)
            btn("lb", "LB", PadFlags.LB, 0.29f, 0.53f, size = 42f, w = 68f, shape = ElementShape.PILL)
            btn("rb", "RB", PadFlags.RB, 0.71f, 0.53f, size = 42f, w = 68f, shape = ElementShape.PILL)
            trig("rt", "RT", Side.RIGHT, 0.9f, 0.53f)
            add(ControlElement("dpad", ElementKind.DPAD, 0.2f, 0.655f, 112f, 112f, bindings = PadFlags.DPAD.map { Binding.Pad(it) }))
            stick("ls", "LS", StickOutput.LEFT, PadFlags.LS_CLK, 0.27f, 0.845f, 112f)
            stick("rs", "RS", StickOutput.RIGHT, PadFlags.RS_CLK, 0.7f, 0.86f, 100f)
            btn("a", f.bottom, PadFlags.A, 0.78f, 0.7225f, size = 50f, tint = t[0])
            btn("b", f.right, PadFlags.B, 0.905f, 0.66f, size = 50f, tint = t[1])
            btn("x", f.left, PadFlags.X, 0.655f, 0.66f, size = 50f, tint = t[2])
            btn("y", f.top, PadFlags.Y, 0.78f, 0.5975f, size = 50f, tint = t[3])
            btn("select", "Select", PadFlags.BACK, 0.36f, 0.955f, size = 38f, w = 64f, shape = ElementShape.PILL)
            if (opts.showGuide) btn("guide", "⌂", PadFlags.GUIDE, 0.5f, 0.955f, size = 40f)
            btn("start", "Start", PadFlags.START, 0.64f, 0.955f, size = 38f, w = 64f, shape = ElementShape.PILL)
            if (opts.l3r3Buttons) {
                btn("l3", "L3", PadFlags.LS_CLK, 0.1f, 0.955f, size = 40f)
                btn("r3", "R3", PadFlags.RS_CLK, 0.9f, 0.955f, size = 40f)
            }
        }
        return out
    }

    /**
     * "GTA V: controller + touch camera (right half)": a controller in hand moves, drives and
     * shoots; a thumb on the right half of the screen aims (right stick). Nothing else on screen,
     * and the zone stays when the controller hides the other controls.
     */
    fun gtaTouchCamera(): ControlsProfile {
        val zone = newElement(ElementKind.ZONE, "camera").copy(
            label = "Camera", sensitivity = 1.2f, acceleration = 1.3f, opacity = 0.35f,
        )
        return ControlsProfile(
            id = ControlsProfile.GTA_ID,
            name = "GTA V: controller + touch camera (right half)",
            landscape = listOf(zone),
            portrait = listOf(zone.copy(y = 0.3f, height = 0.6f)),
            origin = "builtin",
        )
    }

    /** The same with "Camera → mouse": 1:1 mouse look, which GTA V on PC takes alongside a pad. */
    fun gtaMouseCamera(): ControlsProfile {
        val base = gtaTouchCamera()
        fun mouse(l: List<ControlElement>) = l.map { it.copy(zone = ZoneType.CAMERA_MOUSE, sensitivity = 1f, acceleration = 1.2f) }
        return base.copy(
            id = ControlsProfile.GTA_MOUSE_ID, name = "GTA V: controller + mouse camera (right half)",
            landscape = mouse(base.landscape), portrait = base.portrait?.let(::mouse),
        )
    }

    /** Ready-made profiles after Standard. */
    fun presets(): List<ControlsProfile> = listOf(gtaTouchCamera(), gtaMouseCamera())

    fun landscape(o: StandardOptions = StandardOptions()) = build(LayoutOrientation.LANDSCAPE, o)
    fun portrait(o: StandardOptions = StandardOptions()) = build(LayoutOrientation.PORTRAIT, o)
}
