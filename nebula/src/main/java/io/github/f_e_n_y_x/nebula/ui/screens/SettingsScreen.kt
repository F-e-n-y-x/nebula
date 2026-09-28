package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.onFocusChanged
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.searchSettings
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import kotlin.math.roundToInt
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.SettingsViewModel
import io.github.f_e_n_y_x.nebula.ui.components.NebulaIconButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

private enum class SettingsSection(val title: String, val summary: String, val icon: ImageVector, val group: String?) {
    Stream("Stream", "Resolution, frame rate, bitrate, codec, frame pacing", Icons.Outlined.Videocam, "stream"),
    Display("Display", "Fit, fill or stretch, HDR, image position, rotation", Icons.Outlined.Tv, "display"),
    Audio("Audio & microphone", "Surround, host audio, microphone", Icons.Outlined.GraphicEq, "audio"),
    Input("Touch, mouse & keyboard", "Trackpad or direct touch, mouse modes, shortcuts", Icons.Outlined.Mouse, "input"),
    Gamepads("Gamepads", "Controllers, rumble, gyro, remapping", Icons.Outlined.Gamepad, "gamepads"),
    Osc("On-screen controls", "Virtual gamepad buttons and layout", Icons.Outlined.TouchApp, "osc"),
    Overlay("Stats & stream menu", "Performance overlay, in-stream menu", Icons.Outlined.QueryStats, "interface"),
    Network("Host & network", "Packet size, host audio, Wake-on-LAN, VPN", Icons.Outlined.NetworkCheck, "host"),
    FrameGen("Frame generation", "Lossless Scaling engine, upscaling", Icons.Outlined.AutoAwesome, "framegen"),
    Library("Library & artwork", "Details, playtime, image quality, art cache", Icons.Outlined.PhotoLibrary, null),
    Advanced("Advanced", "Backup, restore and everything else", Icons.Outlined.Tune, "advanced"),
    About("About", "Version, licenses, credits", Icons.Outlined.Info, null),
}

@Composable
fun SettingsScreen(container: AppContainer, nav: Navigator, initialSection: String? = null) {
    val vm = viewModel { SettingsViewModel(container) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val form = Nebula.form
    val s = Nebula.scale
    val twoPane = form.widthDp >= 720
    val initial = SettingsSection.entries.firstOrNull { it.name.equals(initialSection, ignoreCase = true) }
    var open by remember { mutableStateOf(initial ?: if (twoPane) SettingsSection.Stream else null) }
    // Single pane: Back closes the open section before leaving Settings.
    BackHandler(enabled = !twoPane && open != null) { open = null }
    var query by remember { mutableStateOf("") }
    BackHandler(enabled = query.isNotEmpty()) { query = "" }

    Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        if (twoPane) {
            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.width(s.dp(320)).fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(24))) {
                    Text("Settings", style = Nebula.type.title, color = NebulaColors.text)
                    Spacer(Modifier.height(s.dp(16)))
                    SearchField(query) { query = it }
                    Spacer(Modifier.height(s.dp(12)))
                    SettingsSection.entries.forEach { sec -> SectionRow(sec, sec == open && query.isEmpty()) { query = ""; open = sec } }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(NebulaColors.border))
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(s.dp(32))) {
                    if (query.isNotBlank()) SearchResults(query) else SectionBody(open ?: SettingsSection.Stream, settings, vm)
                }
            }
        } else {
            val sec = open
            Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(s.dp(20))) {
                if (sec == null) {
                    Text("Settings", style = Nebula.type.title, color = NebulaColors.text)
                    Spacer(Modifier.height(s.dp(16)))
                    SearchField(query) { query = it }
                    Spacer(Modifier.height(s.dp(12)))
                    if (query.isNotBlank()) SearchResults(query) else SettingsSection.entries.forEach { SectionRow(it, false) { open = it } }
                } else {
                    NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to settings", { open = null })
                    Spacer(Modifier.height(s.dp(12)))
                    SectionBody(sec, settings, vm)
                }
            }
        }
    }
}

@Composable
private fun SectionRow(sec: SettingsSection, selected: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = s.dp(3))
            .semantics { this.selected = selected }
            .nebulaClickable(shape, onClick, role = Role.Tab)
            .background(if (selected) NebulaColors.raised else Color.Transparent, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sec.icon, null, tint = if (selected) NebulaColors.accentText else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22)))
        Spacer(Modifier.width(s.dp(14)))
        Column(Modifier.weight(1f)) {
            Text(sec.title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(sec.summary, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
    }
}

@Composable
private fun SectionBody(sec: SettingsSection, settings: StreamSettings, vm: SettingsViewModel) {
    val update: ((StreamSettings) -> StreamSettings) -> Unit = { vm.update(it) }
    val s = Nebula.scale
    Text(sec.title, style = Nebula.type.title, color = NebulaColors.text)
    Spacer(Modifier.height(s.dp(6)))
    Text(sec.summary, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
    Spacer(Modifier.height(s.dp(24)))
    when (sec) {
        SettingsSection.Stream -> {
            StreamSection(settings, update)
            Spacer(Modifier.height(s.dp(26)))
            LegacySettingsList("stream")
        }
        SettingsSection.Display -> {
            ScalingSection()
            Spacer(Modifier.height(s.dp(12)))
            LegacySettingsList("display")
        }
        SettingsSection.About -> {
            AboutSection()
        }
        SettingsSection.Library -> LibrarySection(vm)
        SettingsSection.Input -> {
            VirtualMouseSection()
            Spacer(Modifier.height(s.dp(12)))
            PcKeyboardSection()
            Spacer(Modifier.height(s.dp(12)))
            LegacySettingsList("input")
        }
        SettingsSection.Overlay -> {
            StatsOverlaySection()
            Spacer(Modifier.height(s.dp(12)))
            LegacySettingsList("interface")
        }
        SettingsSection.Gamepads -> {
            GamepadFeedbackSection()
            Spacer(Modifier.height(s.dp(12)))
            LegacySettingsList("gamepads")
        }
        SettingsSection.Advanced -> {
            BackgroundSection()
            Spacer(Modifier.height(s.dp(12)))
            LegacySettingsList("advanced")
        }
        SettingsSection.FrameGen -> io.github.f_e_n_y_x.nebula.framegen.FramegenSettingsSection()
        else -> LegacySettingsList(sec.group!!)
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val s = Nebula.scale
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        Modifier.fillMaxWidth().widthIn(max = s.dp(560)).background(NebulaColors.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(12), vertical = s.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(20)))
        Spacer(Modifier.width(s.dp(10)))
        BasicTextField(
            value = query, onValueChange = onChange, singleLine = true,
            textStyle = Nebula.type.body.copy(color = NebulaColors.text), cursorBrush = SolidColor(NebulaColors.accentText),
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner -> if (query.isEmpty()) Text("Search all settings", style = Nebula.type.body, color = NebulaColors.textMuted); inner() },
        )
    }
}

@Composable
private fun SearchResults(query: String) {
    val s = Nebula.scale
    val hits = remember(query) { searchSettings(query).filter { it.key !in nativeKeys && it.group != "about" } }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        Text(if (hits.isEmpty()) "No settings match \"$query\"" else "${hits.size} settings", style = Nebula.type.secondary, color = NebulaColors.textSecondary)
        hits.groupBy { it.group }.forEach { (group, specs) ->
            val sec = SettingsSection.entries.firstOrNull { it.group == group }
            LegacySettingsList(group, specs, header = sec?.title ?: group)
        }
    }
}

/** Nebula's virtual mouse (touch as a trackpad) tuning, on top of V+'s input options below. */
@Composable
private fun VirtualMouseSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    fun b(k: String, d: Boolean) = prefs.prefs.all[k] as? Boolean ?: d
    var speed by remember { mutableStateOf((prefs.prefs.all[StreamUiPrefs.TRACKPAD_SPEED_KEY] as? Int) ?: 140) }
    var accel by remember { mutableStateOf(b(StreamUiPrefs.TRACKPAD_ACCEL_KEY, true)) }
    var natural by remember { mutableStateOf(b(StreamUiPrefs.NATURAL_SCROLL_KEY, true)) }
    var pinch by remember { mutableStateOf(b(StreamUiPrefs.PINCH_ZOOM_KEY, true)) }
    var bar by remember { mutableStateOf(b(StreamUiPrefs.MOUSE_BAR_KEY, false)) }
    var cursor by remember { mutableStateOf(b(StreamUiPrefs.LOCAL_CURSOR_KEY, false)) }
    var capture by remember { mutableStateOf(b(StreamUiPrefs.MOUSE_CAPTURE_KEY, true)) }
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        SectionTitle("Virtual mouse")
        Text(
            "Trackpad: drag to move, tap to click, two-finger tap for right click, hold then drag to click-drag, two fingers to scroll, " +
                "pinch to zoom, three fingers for the keyboard, four for the stream menu. Pick the touch mode per game from the stream menu.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
        Setting("Trackpad sensitivity", "How far the pointer moves for a finger movement.") {
            SliderField(
                label = "Trackpad sensitivity", value = speed.toFloat(), range = 25f..400f, step = 5f, unit = "%",
                onValueChange = { v -> speed = v.roundToInt(); prefs.put(StreamUiPrefs.TRACKPAD_SPEED_KEY, speed) },
            )
        }
        ToggleRow("Pointer acceleration", "Fast swipes travel further, slow ones stay precise.", accel) { accel = it; prefs.put(StreamUiPrefs.TRACKPAD_ACCEL_KEY, it) }
        ToggleRow("Natural scrolling", "Content follows your fingers, like a phone. Off scrolls like a mouse wheel.", natural) { natural = it; prefs.put(StreamUiPrefs.NATURAL_SCROLL_KEY, it) }
        ToggleRow("Pinch to zoom", "Zoom and pan the picture on this device; nothing is sent to the PC.", pinch) { pinch = it; prefs.put(StreamUiPrefs.PINCH_ZOOM_KEY, it) }
        ToggleRow("Mouse buttons bar", "Left, middle, right, scroll strip, drag lock and keyboard on screen.", bar) { bar = it; prefs.put(StreamUiPrefs.MOUSE_BAR_KEY, it) }
        ToggleRow("Pointer dot", "Draws a dot on this device where the pointer should be, for instant feedback.", cursor) { cursor = it; prefs.put(StreamUiPrefs.LOCAL_CURSOR_KEY, it) }
        ComingRow("Host cursor sync", "Coming in 0.4", "Showing the PC's real cursor shape here and hiding it in the video needs Nova's cursor channel.")
        ToggleRow("Capture a connected mouse", "Games get raw relative movement and the local pointer hides. Off keeps a free pointer.", capture) { capture = it; prefs.put(StreamUiPrefs.MOUSE_CAPTURE_KEY, it) }
    }
}

/** A whole section that isn't built yet. */
@Composable
internal fun ComingBanner(title: String, text: String) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Column(
        Modifier.widthIn(max = s.dp(720)).fillMaxWidth().background(NebulaColors.accentTint, shape).border(1.dp, NebulaColors.accent.copy(alpha = 0.5f), shape).padding(s.dp(16)),
        verticalArrangement = Arrangement.spacedBy(s.dp(4)),
    ) {
        Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(text, style = Nebula.type.label, color = NebulaColors.textSecondary)
    }
}

/** One feature that isn't built yet, in Nebula's own sections. */
@Composable
internal fun ComingRow(title: String, badge: String, why: String) {
    val s = Nebula.scale
    Row(
        Modifier.fillMaxWidth().background(NebulaColors.surface, RoundedCornerShape(s.dp(12))).padding(horizontal = s.dp(16), vertical = s.dp(12))
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).alpha(0.62f)) {
            Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(why, style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        Spacer(Modifier.width(s.dp(12)))
        Pill(badge, color = NebulaColors.accentText, background = NebulaColors.accentTint)
    }
}

/** Stats overlay: live preview, layout, position, size, transparency and metrics. */
@Composable
private fun StatsOverlaySection() {
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    val tick by prefs.changes().collectAsState(initial = null)
    val cfg = remember(tick) { io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings.read(prefs) }
    Column(Modifier.widthIn(max = Nebula.scale.dp(720))) {
        SectionTitle("Stats overlay")
        StatsOverlayControls(prefs, cfg, showToggle = true, showPreview = true)
    }
}

/** Gyro passthrough and host rumble. */
@Composable
private fun GamepadFeedbackSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    val tick by prefs.changes().collectAsState(initial = null)
    val motion = remember(tick) { io.github.f_e_n_y_x.nebula.settings.MotionSettings.read(prefs) }
    val rumble = remember(tick) { io.github.f_e_n_y_x.nebula.settings.RumbleSettings.read(prefs.prefs.all) }
    val gyro = remember { ctx.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null }
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        SectionTitle("Gyro")
        MotionControls(prefs, motion, hostNote = null, phoneHasGyro = gyro)
        SectionTitle("Rumble")
        RumbleControls(prefs, rumble, hostNote = null)
        Text("Controller light bars follow the game on Android 12 and later. Adaptive triggers and DualSense haptics are coming in 0.4.", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

/** The on-screen PC keyboard and the transparency shared by every stream overlay. */
@Composable
private fun PcKeyboardSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    val kb = remember { io.github.f_e_n_y_x.nebula.settings.KeyboardSettings(ctx) }
    var kind by remember { mutableStateOf(if (prefs.prefs.all[StreamUiPrefs.KEYBOARD_KIND_KEY] == KeyboardKind.PHONE.id) KeyboardKind.PHONE else KeyboardKind.PC) }
    var opacity by remember { mutableStateOf(io.github.f_e_n_y_x.nebula.settings.overlayOpacity(ctx)) }
    var page by remember { mutableStateOf(kb.read().page) }
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        SectionTitle("PC keyboard")
        Setting("The keyboard gesture opens", "Three-finger tap, the float ball and the mouse bar's keyboard button. Both stay in the stream menu.") {
            Segmented(KeyboardKind.entries.map { it.label to it }, kind) { k -> kind = k; prefs.put(StreamUiPrefs.KEYBOARD_KIND_KEY, k.id) }
        }
        Setting("Layout", "Keys is a full compact PC keyboard; Nav, Numpad and Mini are smaller pages. Switch any time from the keyboard's bar.") {
            Segmented(io.github.f_e_n_y_x.nebula.input.KeyboardPage.entries.map { it.label to it.name }, page) { p -> page = p; kb.setPage(p) }
        }
        Setting("Overlay transparency", "How see-through the PC keyboard, on-screen controls, mouse bar and float ball are. Also in the stream menu.") {
            SliderField(
                label = "Overlay transparency", value = (100 - opacity).toFloat(), range = 0f..90f, step = 5f, unit = "%",
                onValueChange = { v -> opacity = 100 - v.roundToInt(); io.github.f_e_n_y_x.nebula.settings.setOverlayOpacity(ctx, opacity) },
            )
        }
        NebulaButton("Reset keyboard size and position", onClick = { kb.resetPlacement() }, style = ButtonStyle.Secondary)
        Text("Tap Ctrl, Shift, Alt or Win for the next key only; tap again to lock it, a third time to release.", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

/** How long a stream survives in the background; the behaviour itself is under Host & network. */
@Composable
private fun BackgroundSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    var grace by remember { mutableStateOf((prefs.prefs.all[BACKGROUND_GRACE_KEY] as? Int) ?: 60) }
    Column(Modifier.widthIn(max = s.dp(720))) {
        Setting("Keep a backgrounded stream for", "Switching apps, the notification shade or locking the screen keeps the stream connected this long, then disconnects. 0 disconnects at once.") {
            SliderField(
                label = "Background grace period", value = grace.toFloat(), range = 0f..600f, step = 5f, unit = "s",
                onValueChange = { v -> grace = v.roundToInt(); prefs.put(BACKGROUND_GRACE_KEY, grace) },
            )
        }
    }
}

private const val BACKGROUND_GRACE_KEY = "nebula_background_grace_s"

/** Nebula's own scaling choice; also keeps V+'s stretch checkbox in sync for the engine. */
@Composable
private fun ScalingSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val prefs = remember { LegacyPrefs(ctx) }
    var mode by remember { mutableStateOf(scaleModeOf(prefs)) }
    var virtualToo by remember { mutableStateOf(prefs.prefs.getBoolean(SCALE_VIRTUAL_KEY, false)) }
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        Setting("Video scaling", "Fit shows the whole picture, Fill zooms and crops the edges, Stretch fills the screen and distorts.") {
            Segmented(listOf("Fit" to ScaleMode.FIT, "Fill (zoom)" to ScaleMode.FILL, "Stretch" to ScaleMode.STRETCH), mode) { m ->
                mode = m
                prefs.put(SCALE_MODE_KEY, m.id)
                prefs.put("checkbox_stretch_video", m == ScaleMode.STRETCH)
            }
        }
        ToggleRow(
            "Also scale Virtual display streams",
            "Virtual display already matches this screen, so it's shown pixel for pixel unless you turn this on.",
            virtualToo,
        ) { virtualToo = it; prefs.put(SCALE_VIRTUAL_KEY, it) }
    }
}

@Composable
private fun StreamSection(st: StreamSettings, update: ((StreamSettings) -> StreamSettings) -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val (dw, dh) = remember { deviceResolution(ctx) }
    var editCustom by remember { mutableStateOf(false) }
    val custom = remember(editCustom) { customResolutions(ctx).map { (w, h) -> "${w}×$h" to Resolution(w, h) } }
    Column(Modifier.widthIn(max = s.dp(680)), verticalArrangement = Arrangement.spacedBy(s.dp(26))) {
        Setting("Resolution", "Virtual display streams at exactly this size.") {
            Segmented(
                listOf("This device ${dw}×$dh" to Resolution.Native, "720p" to Resolution(1280, 720), "1080p" to Resolution(1920, 1080), "1440p" to Resolution(2560, 1440), "4K" to Resolution(3840, 2160)) +
                    custom + ("Custom…" to CUSTOM_SENTINEL),
                st.resolution,
            ) { r -> if (r == CUSTOM_SENTINEL) editCustom = true else update { it.copy(resolution = r) } }
        }
        val nativeHz = remember { io.github.fenyx.nebula.engine.DisplayRefresh.nativeHz(ctx) }
        Setting("Frame rate", "Native is this screen's highest refresh rate ($nativeHz Hz). Higher than the screen can show doesn't look smoother.") {
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(12))) {
                Segmented(io.github.fenyx.nebula.engine.DisplayRefresh.frameRateChoices(nativeHz), st.fps) { f -> update { it.copy(fps = f) } }
                SliderField(
                    label = "Exact frame rate", value = st.fps.toFloat(), range = 10f..240f, step = 1f, unit = "fps",
                    onValueChange = { v -> update { it.copy(fps = v.roundToInt()) } },
                )
            }
        }
        Setting("Bitrate", "Higher looks sharper; lower copes better with weak Wi-Fi.") {
            SliderField(
                label = "Bitrate", value = st.bitrateKbps / 1000f, range = 5f..150f, step = 5f, unit = "Mbps",
                onValueChange = { v -> update { it.copy(bitrateKbps = (v * 1000).roundToInt()) } },
            )
        }
        Setting("Video codec", "Auto picks HEVC when both sides support it.") {
            Segmented(listOf("Auto" to VideoCodec.AUTO, "HEVC" to VideoCodec.HEVC, "H.264" to VideoCodec.H264, "AV1" to VideoCodec.AV1), st.codec) { c -> update { it.copy(codec = c) } }
        }
        if (editCustom) CustomResolutionsDialog(onDismiss = { editCustom = false })
        Setting("Default screen for games", "Used until you choose per game on its page.") {
            Segmented(listOf("Virtual display" to DisplayMode.VIRTUAL, "Desktop (Mirror)" to DisplayMode.MIRROR), st.defaultMode) { m -> update { it.copy(defaultMode = m) } }
        }
    }
}

private val CUSTOM_SENTINEL = Resolution(-1, -1)

@Composable
internal fun Setting(title: String, help: String, control: @Composable () -> Unit) {
    val s = Nebula.scale
    Column {
        Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        Spacer(Modifier.height(s.dp(12)))
        control()
    }
}

@Composable
internal fun <T> Segmented(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    val s = Nebula.scale
    val outer = RoundedCornerShape(s.dp(12))
    Row(
        Modifier.horizontalScroll(rememberScrollState())
            .background(NebulaColors.surface, outer).border(1.dp, NebulaColors.controlBorder, outer).padding(s.dp(4)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(4)),
    ) {
        options.forEach { (label, value) ->
            val on = value == selected
            val shape = RoundedCornerShape(s.dp(9))
            Box(
                Modifier
                    .semantics { this.selected = on }
                    .nebulaClickable(shape, { onSelect(value) }, role = Role.RadioButton)
                    .background(if (on) NebulaColors.accent else Color.Transparent, shape)
                    .padding(horizontal = s.dp(14), vertical = s.dp(10)),
            ) {
                Text(label, style = Nebula.type.label, color = if (on) Color.White else NebulaColors.textSecondary, maxLines = 1)
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LibrarySection(vm: SettingsViewModel) {
    val s = Nebula.scale
    val o by vm.options.collectAsStateWithLifecycle()
    val used by vm.cacheUsed.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val host by vm.host.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    Column(Modifier.widthIn(max = s.dp(680)), verticalArrangement = Arrangement.spacedBy(s.dp(26))) {
        Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            ToggleRow("Show game details", "Description, genres and screenshots on each game's page.", o.showDetails) { v -> vm.updateOptions { it.copy(showDetails = v) } }
            ToggleRow("Show playtime", "Hours played and last played, from Nova.", o.showPlaytime) { v -> vm.updateOptions { it.copy(showPlaytime = v) } }
        }
        Setting("Image quality", "Data saver skips large hero art and waits to load screenshots on mobile data.") {
            Segmented(listOf("Original" to false, "Data saver on mobile data" to true), o.dataSaver) { v -> vm.updateOptions { it.copy(dataSaver = v) } }
        }
        Setting("Art cache size", "Posters, heroes and screenshots kept on this device. " + (used?.let { "Using ${formatBytes(it)}." } ?: "")) {
            Segmented(listOf("256 MB" to 256, "512 MB" to 512, "1 GB" to 1024), o.cacheLimitMb) { v -> vm.updateOptions { it.copy(cacheLimitMb = v) } }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            NebulaButton("Clear cache", onClick = { confirmClear = true }, style = ButtonStyle.Secondary, icon = Icons.Outlined.DeleteSweep)
            NebulaButton(
                host?.let { "Refresh from ${it.name}" } ?: "Refresh from host", onClick = { vm.refreshHost() },
                style = ButtonStyle.Secondary, icon = Icons.Outlined.Refresh,
            )
        }
        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(s.dp(10)))
                Text(it, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
            }
        }
    }
    if (confirmClear) {
        NebulaConfirmDialog(
            title = "Clear the art cache?",
            text = "Posters, heroes and screenshots download again from your PC the next time you open the library.",
            confirm = "Clear cache",
            onConfirm = { confirmClear = false; vm.clearCache() },
            onDismiss = { confirmClear = false },
        )
    }
}

/** A setting that's on or off: the whole row is one focus target, so OK/tap toggles it. */
@Composable
internal fun ToggleRow(title: String, help: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        Modifier
            .fillMaxWidth()
            .semantics { stateDescription = if (checked) "On" else "Off" }
            .nebulaClickable(shape, { onChange(!checked) }, role = Role.Switch)
            .background(NebulaColors.surface, shape)
            .padding(horizontal = s.dp(16), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        Spacer(Modifier.width(s.dp(12)))
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = NebulaColors.accent,
                uncheckedThumbColor = NebulaColors.textSecondary, uncheckedTrackColor = NebulaColors.raised, uncheckedBorderColor = NebulaColors.controlBorder,
            ),
        )
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> "%.1f GB".format(b / (1024f * 1024 * 1024))
    b >= 1024L * 1024 -> "%.1f MB".format(b / (1024f * 1024))
    else -> "${b / 1024} KB"
}

@Composable
private fun AboutSection() {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(10)), modifier = Modifier.widthIn(max = s.dp(640))) {
        Text("Nebula ${BuildConfig.VERSION_NAME}", style = Nebula.type.heading, color = NebulaColors.text)
        Text(
            "Nebula is a game streaming client for Nova, Sunshine and GameStream hosts, by Fenyx. Its streaming engine comes from Moonlight (moonlight-stream) and moonlight-vplus by qiin2333, licensed GPL-3.0.",
            style = Nebula.type.body, color = NebulaColors.textSecondary,
        )
        Text("Geist and Geist Mono fonts: SIL Open Font License.", style = Nebula.type.label, color = NebulaColors.textMuted)
        SectionTitle("Privacy", Modifier.padding(top = s.dp(12)))
        Text("No account, no analytics, no tracking. Nebula only talks to the hosts you pair.", style = Nebula.type.body, color = NebulaColors.textSecondary)
    }
}
