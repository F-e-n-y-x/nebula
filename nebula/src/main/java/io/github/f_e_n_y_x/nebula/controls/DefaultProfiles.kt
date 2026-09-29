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
            trig("lt", "LT", Side.LEFT, 0.085f, 0.12f)
            btn("lb", "LB", PadFlags.LB, 0.19f, 0.12f, size = 44f, w = 68f, shape = ElementShape.PILL)
            btn("rb", "RB", PadFlags.RB, 0.81f, 0.12f, size = 44f, w = 68f, shape = ElementShape.PILL)
            trig("rt", "RT", Side.RIGHT, 0.915f, 0.12f)
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
     * "GTA V · controller + touch camera": a controller in hand moves, drives and
     * shoots; a thumb on the right half of the screen aims (right stick). Nothing else on screen,
     * and the zone stays when the controller hides the other controls.
     */
    fun gtaTouchCamera(): ControlsProfile {
        val zone = newElement(ElementKind.ZONE, "camera").copy(
            label = "Camera", sensitivity = 1.2f, acceleration = 1f, opacity = 0.35f,
        )
        return ControlsProfile(
            id = ControlsProfile.GTA_ID,
            name = "GTA V · controller + touch camera",
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
            id = ControlsProfile.GTA_MOUSE_ID, name = "GTA V · controller + mouse camera",
            landscape = mouse(base.landscape), portrait = base.portrait?.let(::mouse),
        )
    }

    /**
     * "GTA V · touch gamepad": no controller needed. A floating move stick on the left half, the
     * Standard buttons, RT and LT that fire at once and look when dragged, and the rest of the
     * right side looks (mouse look by default, [LookOutput]; outside touches never click).
     */
    fun gtaTouchGamepad(o: StandardOptions = StandardOptions()): ControlsProfile {
        fun shooter(l: List<ControlElement>, portrait: Boolean): List<ControlElement> {
            val kept = l.filterNot { it.id == "ls" || it.id == "rs" || it.id == "l3" || it.id == "r3" }
                .map { if (it.id == "rt" || it.id == "lt") it.copy(lookThrough = true) else it }
            val move = newElement(ElementKind.ZONE, "move").copy(
                label = "Move", zone = ZoneType.FLOATING_STICK, stick = StickOutput.LEFT,
                x = 0.25f, y = if (portrait) 0.8f else 0.62f, width = 0.5f, height = if (portrait) 0.4f else 0.6f,
                opacity = 0.25f, keepWithController = false, deadzone = 0.1f,
            )
            val r3 = ControlElement("r3", ElementKind.BUTTON, if (portrait) 0.9f else 0.7f, if (portrait) 0.955f else 0.9f, 40f, 40f, label = "R3", bindings = listOf(Binding.Pad(PadFlags.RS_CLK)))
            val l3 = ControlElement("l3", ElementKind.BUTTON, if (portrait) 0.1f else 0.3f, if (portrait) 0.955f else 0.9f, 40f, 40f, label = "L3", bindings = listOf(Binding.Pad(PadFlags.LS_CLK)))
            return listOf(move) + kept + listOf(l3, r3)
        }
        return ControlsProfile(
            id = ControlsProfile.GTA_TOUCH_ID,
            name = "GTA V · touch gamepad",
            landscape = shooter(landscape(o), portrait = false),
            portrait = shooter(portrait(o), portrait = true),
            origin = "builtin",
        )
    }

    /** Suggested profile for a game, by name: GTA V gets its touch-controls layout. */
    fun suggestedFor(gameName: String?): String? {
        val n = gameName?.lowercase() ?: return null
        return if ("grand theft auto" in n || Regex("""\bgta\b""").containsMatchIn(n)) ControlsProfile.GTA_TOUCH_CONTROLS_ID else null
    }

    /** Ready-made profiles after Standard: genre templates first, then layouts made for one game. */
    fun presets(): List<ControlsProfile> = listOf(touchShooterPad(), touchShooterKbm(), gtaTouchControls(), gtaTouchGamepad(), gtaTouchCamera(), gtaMouseCamera())

    // ---------------------------------------------------------------- touch shooters

    /**
     * What each action of the touch-shooter layout ([shooter]) sends. [None][Binding.None] leaves the button out,
     * except [eye], which stays as a camera-only drag button (see [shooter]).
     */
    data class ShooterBinds(
        val fire: Binding,
        val ads: Binding,
        val adsMode: PressMode = PressMode.MIXED,
        val sprint: Binding,
        val jump: Binding,
        val crouch: Binding,
        val crouchMode: PressMode = PressMode.HOLD,
        /** Prone: a key, or null with [proneHold] set to hold [crouch] long (console "hold B"). */
        val prone: Binding = Binding.None,
        val proneHold: Boolean = false,
        val reload: Binding,
        val switchWeapon: Binding,
        val switchLabel: String = "Swap",
        val interact: Binding,
        val interactLabel: String = "Use",
        val peekLeft: Binding = Binding.None,
        val peekRight: Binding = Binding.None,
        val peekMode: PressMode = PressMode.TOGGLE,
        /** Free look while held (Alt in many PC shooters); None = the eye only moves the camera. */
        val eye: Binding = Binding.None,
        val melee: Binding = Binding.None,
        val meleeLabel: String = "Melee",
        val map: Binding,
        val mapLabel: String = "Map",
        val menu: Binding,
        /** Extra small buttons along the top edge (label to binding), e.g. GTA's phone and cover. */
        val extras: List<Pair<String, Binding>> = emptyList(),
        /** The move stick sends direction keys (WASD) instead of the left stick. */
        val keysMove: Boolean = false,
    )

    /**
     * The two-thumb arrangement most mobile shooters share, laid out for thumbs and a claw grip:
     *
     * - left: a floating move stick over the lower left; push past the ring to sprint, drag up to
     *   the lock to keep running; a left fire button above it fires while the right thumb aims;
     * - right: everything that isn't a button looks (the Look background, [LookOutput]); the big
     *   fire button fires at once and aims when dragged; ADS, jump, crouch, prone and reload
     *   around it where mobile players expect them; swap, use and the eye (free look) inside
     *   thumb reach;
     * - top edge, for index fingers (claw): lean (peek) left / right, map, menu, melee.
     *
     * Works with two thumbs (left: move + left fire; right: look + drag-fire) or three/four
     * fingers. Positions are shares of the screen; sizes in dp (the style picker scales them).
     */
    fun shooter(b: ShooterBinds): List<ControlElement> {
        val out = mutableListOf<ControlElement>()
        fun btn(id: String, label: String, bind: Binding, x: Float, y: Float, size: Float, role: ElementRole, mode: PressMode = PressMode.HOLD, shape: ElementShape = ElementShape.ROUND, w: Float = size, look: Boolean = false, kind: ElementKind = ElementKind.BUTTON) {
            out += ControlElement(id, kind, x, y, w, size, label = label, mode = mode, shape = shape, bindings = listOf(bind), role = role, lookThrough = look)
        }
        out += ControlElement(
            "move", ElementKind.ZONE, 0.22f, 0.62f, 0.44f, 0.76f, label = "Move", opacity = 0.25f, shape = ElementShape.SQUARE,
            zone = ZoneType.FLOATING_STICK, stick = if (b.keysMove) StickOutput.KEYS else StickOutput.LEFT,
            bindings = if (b.keysMove) WASD else emptyList(), deadzone = 0.1f, sprint = b.sprint, runLock = true, role = ElementRole.MOVE,
        )
        val fireKind = if (b.fire is Binding.Trigger) ElementKind.TRIGGER else ElementKind.BUTTON
        btn("fire-left", "FIRE", b.fire, 0.075f, 0.33f, 64f, ElementRole.FIRE, kind = fireKind)
        btn("fire", "FIRE", b.fire, 0.80f, 0.55f, 80f, ElementRole.FIRE, look = true, kind = fireKind)
        btn("ads", "ADS", b.ads, 0.925f, 0.33f, 58f, ElementRole.ADS, mode = b.adsMode, kind = if (b.ads is Binding.Trigger) ElementKind.TRIGGER else ElementKind.BUTTON)
        // Jump sits in from the edge: the stream's mic and menu buttons live on the right edge.
        btn("jump", "Jump", b.jump, 0.895f, 0.6f, 54f, ElementRole.JUMP)
        btn("crouch", "Crouch", b.crouch, 0.905f, 0.83f, 54f, ElementRole.CROUCH, mode = b.crouchMode)
        when {
            b.prone != Binding.None -> btn("prone", "Prone", b.prone, 0.795f, 0.9f, 48f, ElementRole.PRONE)
            b.proneHold -> out += ControlElement(
                "prone", ElementKind.MACRO, 0.795f, 0.9f, 48f, 48f, label = "Prone", role = ElementRole.PRONE,
                steps = listOf(MacroStep(b.crouch, holdMs = 800, gapMs = 40)),
            )
        }
        btn("reload", "Reload", b.reload, 0.69f, 0.88f, 50f, ElementRole.RELOAD)
        btn("swap", b.switchLabel, b.switchWeapon, 0.585f, 0.88f, 48f, ElementRole.SWITCH)
        btn("use", b.interactLabel, b.interact, 0.62f, 0.66f, 48f, ElementRole.INTERACT)
        // The eye: hold and drag to look around. With a free-look key the game keeps the aim still.
        btn("eye", "Eye", b.eye, 0.69f, 0.3f, 50f, ElementRole.FREE_LOOK, look = true)
        if (b.peekLeft != Binding.None) btn("peek-left", "◀ Peek", b.peekLeft, 0.26f, 0.1f, 40f, ElementRole.PEEK_LEFT, mode = b.peekMode, shape = ElementShape.PILL, w = 72f)
        if (b.peekRight != Binding.None) btn("peek-right", "Peek ▶", b.peekRight, 0.74f, 0.1f, 40f, ElementRole.PEEK_RIGHT, mode = b.peekMode, shape = ElementShape.PILL, w = 72f)
        if (b.melee != Binding.None) btn("melee", b.meleeLabel, b.melee, 0.925f, 0.1f, 44f, ElementRole.NONE)
        btn("map", b.mapLabel, b.map, 0.43f, 0.07f, 34f, ElementRole.MENU, shape = ElementShape.PILL, w = 60f)
        btn("menu", "Menu", b.menu, 0.57f, 0.07f, 34f, ElementRole.MENU, shape = ElementShape.PILL, w = 60f)
        b.extras.forEachIndexed { i, (label, bind) ->
            btn("extra-$i", label, bind, 0.075f + i * 0.085f, 0.1f, 40f, ElementRole.NONE, shape = ElementShape.PILL, w = 58f)
        }
        return out
    }

    private val WASD = listOf(0x57, 0x53, 0x41, 0x44).map { Binding.Key(it) }

    /** The usual console shooter map: RT fire, LT aim, A jump, B crouch (hold for prone), X reload, Y swap, L3 sprint, R3 melee, LB / RB lean. */
    val PAD_BINDS = ShooterBinds(
        fire = Binding.Trigger(Side.RIGHT), ads = Binding.Trigger(Side.LEFT), sprint = Binding.Pad(PadFlags.LS_CLK),
        jump = Binding.Pad(PadFlags.A), crouch = Binding.Pad(PadFlags.B), proneHold = true,
        reload = Binding.Pad(PadFlags.X), switchWeapon = Binding.Pad(PadFlags.Y), interact = Binding.Pad(PadFlags.X),
        peekLeft = Binding.Pad(PadFlags.LB), peekRight = Binding.Pad(PadFlags.RB), peekMode = PressMode.HOLD,
        melee = Binding.Pad(PadFlags.RS_CLK), map = Binding.Pad(PadFlags.BACK), menu = Binding.Pad(PadFlags.START),
    )

    /** The usual PC shooter keys: LMB fire, RMB aim, Shift sprint, Space jump, C crouch, Z prone, R reload, F use, Q / E lean, Alt free look, Tab, M map. */
    val KBM_BINDS = ShooterBinds(
        fire = Binding.Mouse(MouseKey.LEFT), ads = Binding.Mouse(MouseKey.RIGHT), sprint = Binding.Key(0xA0),
        jump = Binding.Key(0x20), crouch = Binding.Key(0x43), prone = Binding.Key(0x5A),
        reload = Binding.Key(0x52), switchWeapon = Binding.Wheel(up = false), interact = Binding.Key(0x46), interactLabel = "F",
        peekLeft = Binding.Key(0x51), peekRight = Binding.Key(0x45), peekMode = PressMode.TOGGLE,
        eye = Binding.Key(0xA4), melee = Binding.Key(0x09), meleeLabel = "Bag",
        map = Binding.Key(0x4D), menu = Binding.Key(0x1B), keysMove = true,
    )

    /**
     * GTA V's default controller map (on foot): RT shoot, LT aim, A sprint, X jump, B reload,
     * Y enter vehicle, LB weapon wheel, RB cover, L3 stealth (crouch), R3 look behind,
     * D-pad up phone, D-pad right next weapon, Back camera, Start pause. No prone or peek.
     */
    val GTA_BINDS = ShooterBinds(
        fire = Binding.Trigger(Side.RIGHT), ads = Binding.Trigger(Side.LEFT), adsMode = PressMode.MIXED, sprint = Binding.Pad(PadFlags.A),
        jump = Binding.Pad(PadFlags.X), crouch = Binding.Pad(PadFlags.LS_CLK), crouchMode = PressMode.HOLD,
        reload = Binding.Pad(PadFlags.B), switchWeapon = Binding.Pad(PadFlags.LB), switchLabel = "Wheel",
        interact = Binding.Pad(PadFlags.Y), interactLabel = "Car",
        melee = Binding.Pad(PadFlags.RS_CLK), meleeLabel = "Behind",
        map = Binding.Pad(PadFlags.BACK), mapLabel = "Cam", menu = Binding.Pad(PadFlags.START),
        extras = listOf("Cover" to Binding.Pad(PadFlags.RB), "Phone" to Binding.Pad(PadFlags.UP), "Next" to Binding.Pad(PadFlags.RIGHT)),
    )

    /**
     * Library-style names and tags ("<game or genre> · <what's different>", see the nebula-layouts
     * README): they describe the controls, never another game, and match the library's copies.
     */
    fun touchShooterPad(): ControlsProfile = ControlsProfile(
        id = ControlsProfile.TOUCH_SHOOTER_PAD_ID, name = "Touch shooter · controller", landscape = shooter(PAD_BINDS), origin = "builtin",
        outside = OutsideTouch.LOOK, look = LookOutput.STICK,
        meta = LayoutMeta(
            "Touch shooter · controller", author = "Nebula", target = LayoutTarget.XINPUT, device = DeviceClass.PHONE, aspect = 2.17f,
            description = "Two-thumb shooter layout for any game that uses a controller. Floating move stick (push past the ring to sprint, " +
                "drag up to lock the run), swipe anywhere on the right to look, fire on both sides (the right one aims while you drag it), " +
                "aim, jump, crouch (hold for prone), reload, swap, melee and lean.",
            tags = listOf("shooter", "first-person", "third-person", "controller", "touch-only"),
        ),
    )

    fun touchShooterKbm(): ControlsProfile = ControlsProfile(
        id = ControlsProfile.TOUCH_SHOOTER_KBM_ID, name = "Touch shooter · keyboard & mouse", landscape = shooter(KBM_BINDS), origin = "builtin",
        outside = OutsideTouch.LOOK, look = LookOutput.MOUSE,
        meta = LayoutMeta(
            "Touch shooter · keyboard & mouse", author = "Nebula", target = LayoutTarget.KBM, device = DeviceClass.PHONE, aspect = 2.17f,
            description = "Two-thumb shooter layout for games played with keyboard and mouse. The stick is WASD (Shift past the ring, " +
                "drag up to lock the run), swipe to look with the mouse, left and right mouse buttons on both sides, " +
                "Space, C, Z, R, F, Q/E lean, Alt free look, Tab, M and Esc.",
            tags = listOf("shooter", "first-person", "third-person", "keyboard-mouse", "touch-only"),
        ),
    )

    /** The touch-shooter arrangement on GTA V's own controller map, with mouse look. */
    fun gtaTouchControls(): ControlsProfile = ControlsProfile(
        id = ControlsProfile.GTA_TOUCH_CONTROLS_ID, name = "GTA V · touch controls", landscape = shooter(GTA_BINDS), origin = "builtin",
        outside = OutsideTouch.LOOK, look = LookOutput.MOUSE,
        meta = LayoutMeta(
            "GTA V · touch controls", author = "Nebula", game = GameRef("Grand Theft Auto V", 271590), target = LayoutTarget.XINPUT, device = DeviceClass.PHONE, aspect = 2.17f,
            description = "Touch-only GTA V on GTA's own controller map: RT shoot (a second fire button on the left, and the right one aims while dragged), " +
                "LT aim, sprint past the ring or locked, X jump, B reload, L3 stealth, Y enter vehicle, LB weapon wheel, RB cover, R3 look behind. " +
                "Swipe the right side to look.",
            tags = listOf("action-adventure", "third-person", "shooter", "controller", "touch-only"),
        ),
    )

    fun landscape(o: StandardOptions = StandardOptions()) = build(LayoutOrientation.LANDSCAPE, o)
    fun portrait(o: StandardOptions = StandardOptions()) = build(LayoutOrientation.PORTRAIT, o)
}
