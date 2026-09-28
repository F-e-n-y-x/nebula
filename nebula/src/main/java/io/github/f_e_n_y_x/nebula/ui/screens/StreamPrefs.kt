package io.github.f_e_n_y_x.nebula.ui.screens

import io.github.f_e_n_y_x.nebula.data.engine.GamepadConfig
import io.github.f_e_n_y_x.nebula.input.TouchConfig
import io.github.f_e_n_y_x.nebula.input.TouchMode
import io.github.f_e_n_y_x.nebula.input.VideoRect
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs

/** The PC keyboard (on screen, every PC key) or this device's own keyboard (typing text). */
enum class KeyboardKind(val id: String, val label: String) { PC("pc", "PC keyboard"), PHONE("phone", "Device keyboard") }

enum class PerfDetail(val id: String, val label: String) { OFF("off", "Off"), SIMPLE("simple", "Simple"), FULL("full", "Full") }

/** Everything the stream screen reads from V+'s preferences, snapshotted on each change. */
data class StreamUiPrefs(
    val scaleMode: ScaleMode,
    val scaleVirtual: Boolean,
    val position: String,
    val offsetX: Int,
    val offsetY: Int,
    val touch: TouchConfig,
    val absoluteMouse: Boolean,
    /** Capture a physical mouse (relative motion, local pointer hidden) when not absolute. */
    val mouseCapture: Boolean,
    val localCursor: Boolean,
    val mouseBar: Boolean,
    /** Opacity (percent) of the mouse bar and float ball; the menu's transparency control sets all overlays. */
    val overlayOpacity: Int,
    /** What the keyboard gesture / buttons open. */
    val keyboardKind: KeyboardKind,
    val mouseNavButtons: Boolean,
    val perf: PerfDetail,
    val perfOpacity: Int,
    val osc: Boolean,
    val oscOpacity: Int,
    val oscL3R3Only: Boolean,
    val oscGuide: Boolean,
    /** Keep the on-screen controls (and their player 1) while a physical controller is attached. */
    val oscWithGamepad: Boolean,
    val floatBall: Boolean,
    val floatBallPosition: String,
    val floatBallHideMs: Int,
    val floatBallTap: String,
    val floatBallDoubleTap: String,
    val floatBallLongPress: String,
    val escMenu: Boolean,
    val escMenuKey: Int,
    val gamepad: GamepadConfig,
    val quitDisconnectsOnly: Boolean,
) {
    companion object {
        const val PERF_DETAIL_KEY = "nebula_perf_overlay_detail"
        const val TRACKPAD_SPEED_KEY = "nebula_trackpad_speed"
        const val TRACKPAD_ACCEL_KEY = "nebula_trackpad_accel"
        const val NATURAL_SCROLL_KEY = "nebula_natural_scroll"
        const val PINCH_ZOOM_KEY = "nebula_pinch_zoom"
        const val LOCAL_CURSOR_KEY = "nebula_local_cursor"
        const val MOUSE_BAR_KEY = "nebula_mouse_bar"
        const val MOUSE_CAPTURE_KEY = "nebula_mouse_capture"
        const val KEYBOARD_KIND_KEY = "nebula_keyboard_kind"
        const val OSC_WITH_GAMEPAD_KEY = "nebula_osc_with_gamepad"

        /** Per-game touch mode chosen from the stream menu; overrides the global default. */
        fun touchModeKey(gameKey: String) = "nebula_touch_mode:$gameKey"

        fun read(p: LegacyPrefs, gameKey: String? = null): StreamUiPrefs {
            val all = p.prefs.all
            fun b(k: String, d: Boolean) = when (val v = all[k]) { is Boolean -> v; is String -> v.toBooleanStrictOrNull() ?: d; else -> d }
            fun i(k: String, d: Int) = when (val v = all[k]) { is Int -> v; is String -> v.toIntOrNull() ?: d; is Long -> v.toInt(); else -> d }
            fun s(k: String, d: String) = (all[k] as? String) ?: d
            val perfOn = b("checkbox_enable_perf_overlay", false)
            val detail = PerfDetail.entries.firstOrNull { it.id == all[PERF_DETAIL_KEY] } ?: PerfDetail.SIMPLE
            return StreamUiPrefs(
                scaleMode = scaleModeOf(p),
                scaleVirtual = b(SCALE_VIRTUAL_KEY, false),
                position = s("list_screen_position", "center"),
                offsetX = i("seekbar_screen_offset_x", 0),
                offsetY = i("seekbar_screen_offset_y", 0),
                touch = TouchConfig(
                    mode = gameKey?.let { k -> TouchMode.entries.firstOrNull { it.pref == all[touchModeKey(k)] } }
                        ?: TouchMode.fromPrefs(all["list_native_mouse_mode_preset"] as? String, b("checkbox_touchscreen_trackpad", true)),
                    speed = i(TRACKPAD_SPEED_KEY, 140) / 100f,
                    acceleration = b(TRACKPAD_ACCEL_KEY, true),
                    naturalScroll = b(NATURAL_SCROLL_KEY, true),
                    pinchZoom = b(PINCH_ZOOM_KEY, true),
                    splitSpeed = i("pointer_velocity_factor", 100) / 100f,
                    splitPointerOnLeft = b("checkbox_enhanced_touch_on_which_side", false),
                    splitDivider = i("enhanced_touch_zone_divider", 50) / 100f,
                    doubleTapDrag = b("pref_enable_double_click_drag", false),
                    // V+'s threshold is the gap between taps; allow for the tap itself too.
                    doubleTapMs = i("seekbar_double_tap_time_threshold", 125).toLong() + 150,
                    keyboardGesture = b("checkbox_enable_keyboard_toggle_in_native_touch", true),
                    keyboardFingers = i("seekbar_keyboard_toggle_fingers_native_touch", 3),
                ),
                absoluteMouse = b("checkbox_absolute_mouse_mode", false),
                mouseCapture = b(MOUSE_CAPTURE_KEY, true),
                localCursor = b(LOCAL_CURSOR_KEY, false),
                mouseBar = b(MOUSE_BAR_KEY, false),
                overlayOpacity = i(io.github.f_e_n_y_x.nebula.settings.OVERLAY_OPACITY_KEY, io.github.f_e_n_y_x.nebula.settings.DEFAULT_OVERLAY_OPACITY).coerceIn(10, 100),
                keyboardKind = if (all[KEYBOARD_KIND_KEY] == KeyboardKind.PHONE.id) KeyboardKind.PHONE else KeyboardKind.PC,
                mouseNavButtons = b("checkbox_mouse_nav_buttons", false),
                perf = if (perfOn) detail else PerfDetail.OFF,
                perfOpacity = i("seekbar_perf_overlay_bg_opacity", 40),
                osc = b("checkbox_show_onscreen_controls", false),
                oscOpacity = i("seekbar_osc_opacity", 90),
                oscL3R3Only = b("checkbox_only_show_L3R3", false),
                oscGuide = b("checkbox_show_guide_button", true),
                oscWithGamepad = b(OSC_WITH_GAMEPAD_KEY, false),
                floatBall = b("checkbox_enable_float_ball", true),
                floatBallPosition = s("list_float_ball_position", "center_right"),
                floatBallHideMs = i("seekbar_float_ball_auto_hide_delay", 2000),
                floatBallTap = s("list_float_ball_single_click_action", "open_keyboard"),
                floatBallDoubleTap = s("list_float_ball_double_click_action", "open_menu"),
                floatBallLongPress = s("list_float_ball_long_click_action", "toggle_visibility"),
                escMenu = b("checkbox_enable_esc_menu", true),
                escMenuKey = s("list_esc_menu_key", "111").toIntOrNull() ?: 111,
                gamepad = GamepadConfig(
                    multiController = b("checkbox_multi_controller", true),
                    flipFaceButtons = b("checkbox_flip_face_buttons", false),
                    deadzone = i("seekbar_deadzone", 7).coerceIn(0, 50) / 100f,
                ),
                quitDisconnectsOnly = all["list_quit_behavior"] == "disconnect_only" || b("checkbox_swap_quit_and_disconnect", false),
            )
        }
    }
}

/** Where the video sits in a [cw] × [ch] container, per the scaling mode and V+'s anchor + offsets. */
fun videoRect(mode: ScaleMode, cw: Float, ch: Float, vw: Float, vh: Float, position: String, offsetX: Int, offsetY: Int): VideoRect {
    if (cw <= 0f || ch <= 0f || vw <= 0f || vh <= 0f || mode == ScaleMode.STRETCH) return VideoRect(0f, 0f, cw.coerceAtLeast(1f), ch.coerceAtLeast(1f))
    val scale = if (mode == ScaleMode.FILL) maxOf(cw / vw, ch / vh) else minOf(cw / vw, ch / vh)
    val w = vw * scale
    val h = vh * scale
    if (mode == ScaleMode.FILL) return VideoRect((cw - w) / 2, (ch - h) / 2, w, h)
    val ax = when { position.endsWith("left") -> 0f; position.endsWith("right") -> 1f; else -> 0.5f }
    val ay = when { position.startsWith("top") -> 0f; position.startsWith("bottom") -> 1f; else -> 0.5f }
    // Same-aspect video can leave -0.0002 px of "free" space from float error.
    val freeX = (cw - w).coerceAtLeast(0f)
    val freeY = (ch - h).coerceAtLeast(0f)
    // Offsets move the image away from its anchor, as a share of the free space.
    val left = (freeX * ax + freeX * offsetX / 100f * (if (ax == 1f) -1f else 1f)).coerceIn(0f, freeX)
    val top = (freeY * ay + freeY * offsetY / 100f * (if (ay == 1f) -1f else 1f)).coerceIn(0f, freeY)
    return VideoRect(left, top, w, h)
}

/** Local zoom (pinch) applied on top of [base]: [zoom] ≥ 1 around the view, shifted by [panX]/[panY]. */
fun zoomed(base: VideoRect, zoom: Float, panX: Float, panY: Float, cw: Float, ch: Float): VideoRect {
    if (zoom <= 1.001f) return base
    val w = base.width * zoom
    val h = base.height * zoom
    val cx = cw / 2 + panX
    val cy = ch / 2 + panY
    return VideoRect(cx - w / 2 + (base.left + base.width / 2 - cw / 2) * zoom, cy - h / 2 + (base.top + base.height / 2 - ch / 2) * zoom, w, h)
}
