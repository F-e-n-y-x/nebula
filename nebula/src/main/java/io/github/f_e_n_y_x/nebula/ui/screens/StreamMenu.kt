package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.f_e_n_y_x.nebula.controls.OutsideTouch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.input.Shortcut
import io.github.f_e_n_y_x.nebula.input.TouchMode
import io.github.f_e_n_y_x.nebula.input.menuShortcuts
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.StatLayout
import io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** What the stream menu can do; the screen wires each to the session. */
class StreamMenuActions(
    val onResume: () -> Unit,
    val onResetZoom: () -> Unit,
    val onBitrate: (Int) -> Unit,
    val onKeyboard: () -> Unit,
    val onPcKeyboard: () -> Unit,
    /** Sets every overlay's opacity (percent). */
    val onOverlayOpacity: (Int) -> Unit,
    val onClipboard: () -> Unit,
    val onShortcut: (Shortcut) -> Unit,
    val onDisconnect: () -> Unit,
    val onQuit: () -> Unit,
    /** Frame generation quick toggle: (paused, force) → accepted. */
    val onFramegenPause: (Boolean, Boolean) -> Boolean = { _, _ -> false },
    /** Opens the on-screen controls editor over the stream. */
    val onEditControls: () -> Unit = {},
    /** Portrait streaming "Follow rotation" on or off, for the running stream and after. */
    val onPortraitFollow: (Boolean) -> Unit = {},
    /** The layout set's next layout (the Layout quick toggle). */
    val onNextLayout: () -> Unit = {},
    /** Re-measures RTT and jitter to the PC during the stream. */
    val onTestConnection: () -> Unit = {},
    /** Adaptive bitrate on or off for this stream (the Adaptive quick toggle). */
    val onAbr: (Boolean) -> Unit = {},
)

/**
 * The in-stream quick menu, Spotlight "Panel" style: a side panel in landscape and on TV, a
 * bottom sheet in portrait. Every control is a D-pad focus target; Back or B closes it.
 */
@Composable
fun StreamMenu(
    gameName: String,
    mode: DisplayMode,
    stats: StreamStats?,
    ui: StreamUiPrefs,
    prefs: LegacyPrefs,
    bitrateKbps: Int,
    bitrateNote: String?,
    gamepads: Int,
    gameKey: String,
    zoomed: Boolean,
    actions: StreamMenuActions,
    resolution: LiveResolutionUi? = null,
    /** Mic, clipboard, host commands and power ([StreamHostMenuSection]). */
    hostSection: (@Composable () -> Unit)? = null,
    /** False when a Nova host says it lacks a feature ("motion", "rumble", …). */
    supports: (String) -> Boolean = { true },
    /** Host features this stream used that Nebula doesn't do yet. */
    unsupported: List<String> = emptyList(),
    hapticsNote: String? = null,
    phoneHasGyro: Boolean = true,
    /** The on-screen controls' profile while they're on, for the "touch outside controls" toggle. */
    controlsProfileId: String? = null,
    /** The profile itself, so a layout file's own "outside touches" choice shows. */
    controlsProfile: io.github.f_e_n_y_x.nebula.controls.ControlsProfile? = null,
    /** Mic state for the quick toggle (the same one [hostSection] shows). */
    hostState: StreamHostState? = null,
    /** Portrait streaming "Follow rotation", or null where the stream can't follow (TV). */
    portraitFollow: Boolean? = null,
    /** The layout on screen when the game's controls are a layout set, else null. */
    setLayout: String? = null,
    connectionTest: ConnectionTestUi = ConnectionTestUi.Idle,
    cursorMode: io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode = io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode.OFF,
) {
    val s = Nebula.scale
    val form = Nebula.form
    val side = form.isLandscape || form.isTv || !form.isCompact
    var confirmQuit by remember { mutableStateOf(false) }
    // The resolution picker replaces the panel; Back returns to it, applying closes the menu.
    var picking by remember { mutableStateOf(false) }
    // "All frame generation settings": the Settings page over the stream; Back returns here.
    var framegenSettings by remember { mutableStateOf(false) }
    if (picking && resolution != null) {
        ResolutionPicker(resolution, onDismiss = { picking = false }, onApplied = { picking = false; actions.onResume() })
        return
    }
    if (framegenSettings) {
        io.github.f_e_n_y_x.nebula.framegen.FramegenSettingsSheet(onBack = { framegenSettings = false })
        return
    }
    val tick by prefs.changes().collectAsState(initial = null)
    val (framegen, framegenQueued) = io.github.f_e_n_y_x.nebula.framegen.rememberFramegenPanelState(stats?.post, stats?.receivedFps, prefs)
    val quickShown = remember(tick) { QuickToggles.read(prefs.prefs.all).isNotEmpty() }
    // Opening the menu focuses the first quick toggle (the top of the panel, so nothing scrolls
    // away); with none shown, Resume.
    val firstQuick = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (!quickShown) return@LaunchedEffect
        // After the enter animation's first frame, when the tiles are attached and the input
        // layer has let go of focus.
        kotlinx.coroutines.delay(120)
        runCatching { firstQuick.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        // Scrim: tapping outside the panel resumes. A tap handler, not clickable: a focusable
        // scrim would take the D-pad's first focus (invisibly) and OK would close the menu.
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .pointerInput(Unit) { detectTapGestures { actions.onResume() } },
        )
        val panelShape = if (side) RoundedCornerShape(topStart = s.dp(20), bottomStart = s.dp(20)) else RoundedCornerShape(topStart = s.dp(20), topEnd = s.dp(20))
        Column(
            (if (side) Modifier.align(Alignment.CenterEnd).width(s.dp(420)).fillMaxHeight() else Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.78f))
                .background(NebulaColors.panel, panelShape)
                .border(1.dp, NebulaColors.border, panelShape)
                // Swallows taps on the panel's gaps (they'd reach the scrim); not a focus target.
                .pointerInput(Unit) { detectTapGestures { } }
                .then(if (side) Modifier.statusBarsPadding() else Modifier)
                .navigationBarsPadding(),
        ) {
        // The menu scrolls; Disconnect and Quit stay pinned at the bottom, always reachable.
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = s.dp(22), end = s.dp(22), top = s.dp(22), bottom = s.dp(12)),
            verticalArrangement = Arrangement.spacedBy(s.dp(18)),
        ) {
            // The title, then the quick toggles, so they're in view without scrolling even on a
            // phone held sideways; the stream's details follow.
            QuickToggleStrip(
                title = { m -> Text(gameName, style = Nebula.type.heading, color = NebulaColors.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = m) },
                ui = ui, prefs = prefs, tick = tick,
                controlsProfileId = controlsProfileId, controlsProfile = controlsProfile,
                framegen = framegen, framegenQueued = framegenQueued, post = stats?.post,
                onFramegenPause = actions.onFramegenPause,
                mic = hostState,
                portraitFollow = portraitFollow, onPortraitFollow = actions.onPortraitFollow,
                onKeyboard = if (ui.keyboardKind == KeyboardKind.PHONE) actions.onKeyboard else actions.onPcKeyboard,
                firstFocus = firstQuick,
                setLayout = setLayout, onNextLayout = actions.onNextLayout,
                abrOn = stats?.abr != null, onAbr = actions.onAbr,
            )
            Header(mode, stats)
            StatsBlock(stats, full = true)
            resolution?.let {
                ResolutionRow(it, onOpen = { picking = true })
                RotateRow(it, onRotated = actions.onResume)
            }
            io.github.f_e_n_y_x.nebula.framegen.FramegenMenuSection(stats?.post, stats?.receivedFps, prefs, actions.onFramegenPause, onOpenSettings = { framegenSettings = true })
            Controls(ui, prefs, actions, gameKey, zoomed, hostSection != null, controlsProfileId, controlsProfile, tick, focusResume = !quickShown, cursorMode = cursorMode)
            hostSection?.invoke()
            FeedbackSection(ui, prefs, supports, hapticsNote, phoneHasGyro)
            if (unsupported.isNotEmpty()) {
                Text("This game asked for: ${unsupported.joinToString()}.", style = Nebula.type.label, color = NebulaColors.textMuted)
            }
            Bitrate(bitrateKbps, bitrateNote, stats, connectionTest, actions)
            Keys(actions.onShortcut)
            if (gamepads > 0) Text("$gamepads controller${if (gamepads > 1) "s" else ""} connected · Start + Select opens this menu", style = Nebula.type.label, color = NebulaColors.textMuted)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(NebulaColors.border))
        Box(Modifier.fillMaxWidth().padding(horizontal = s.dp(22), vertical = s.dp(12))) {
            Footer(actions, onQuit = { confirmQuit = true }, quitLabel = if (ui.quitDisconnectsOnly) "Quit (disconnect)" else "Quit game")
        }
        }
    }
    if (confirmQuit) {
        NebulaConfirmDialog(
            title = "Quit $gameName?",
            text = if (ui.quitDisconnectsOnly) "Settings make Quit disconnect only; the game keeps running on your PC." else "The game closes on your PC. Unsaved progress may be lost.",
            confirm = "Quit",
            onConfirm = { confirmQuit = false; actions.onQuit() },
            onDismiss = { confirmQuit = false },
        )
    }
}

@Composable
private fun Header(mode: DisplayMode, stats: StreamStats?) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
            Pill(if (stats != null) "● Live" else "Connecting…", color = if (stats != null) NebulaColors.success else NebulaColors.textSecondary, background = if (stats != null) NebulaColors.successTint else NebulaColors.raised)
            Pill(
                if (mode == DisplayMode.VIRTUAL) "Virtual display" else "Desktop (Mirror)",
                color = NebulaColors.accentText, background = NebulaColors.accentTint,
            )
            stats?.let { Pill("${it.resolution} · ${it.codec}", color = NebulaColors.textSecondary, background = NebulaColors.raised) }
            io.github.f_e_n_y_x.nebula.framegen.FramegenPill(stats?.post)
        }
        Text(
            if (mode == DisplayMode.VIRTUAL) "Your PC made a display that matches this screen; it goes away when you leave."
            else "You're seeing your PC's own desktop at its current resolution.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
    }
}

@Composable
private fun StatsBlock(stats: StreamStats?, full: Boolean) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Column(
        Modifier.fillMaxWidth().background(NebulaColors.surface, shape).padding(s.dp(14)),
        verticalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stat("FPS", stats?.fps?.toString() ?: "—")
            Stat("Bitrate", stats?.let { "%.0f Mbps".format(it.bitrateMbps) } ?: "—")
            Stat("Latency", stats?.let { "%.1f ms".format(it.latencyMs) } ?: "—")
            Stat("Loss", stats?.let { "%.1f%%".format(it.lossPercent) } ?: "—")
        }
        if (stats != null) {
            Text(
                "Host %.1f · Network %.1f · Decode %.1f · Render %.1f ms".format(stats.hostMs, stats.networkMs, stats.decodeMs, stats.renderMs),
                style = Nebula.type.mono, color = NebulaColors.textSecondary,
            )
            if (full) {
                Text("Received %.0f fps · %s".format(stats.receivedFps, stats.decoder.ifEmpty { "decoder —" }), style = Nebula.type.mono, color = NebulaColors.textMuted)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(label, style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
    ui: StreamUiPrefs, prefs: LegacyPrefs, actions: StreamMenuActions, gameKey: String, zoomed: Boolean, hostSection: Boolean,
    controlsProfileId: String? = null, controlsProfile: io.github.f_e_n_y_x.nebula.controls.ControlsProfile? = null,
    tick: Any? = null, focusResume: Boolean = true,
    cursorMode: io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode = io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode.OFF,
) {
    val s = Nebula.scale
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusResume) runCatching { first.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton("Resume", onClick = actions.onResume, icon = Icons.Rounded.PlayArrow, modifier = Modifier.focusRequester(first))
            NebulaButton("PC keyboard", onClick = actions.onPcKeyboard, style = ButtonStyle.Secondary, icon = Icons.Outlined.Keyboard)
            NebulaButton("Device keyboard", onClick = actions.onKeyboard, style = ButtonStyle.Secondary)
            if (!hostSection) NebulaButton("Type clipboard", onClick = actions.onClipboard, style = ButtonStyle.Secondary, icon = Icons.Outlined.ContentPaste)
            if (zoomed) NebulaButton("Reset zoom", onClick = actions.onResetZoom, style = ButtonStyle.Secondary)
        }
        OverlayTransparency(ui.overlayOpacity, actions.onOverlayOpacity)
        if (controlsProfileId != null) {
            // Re-read on every change: the quick toggle above writes the same key.
            val outside = remember(controlsProfileId, controlsProfile, tick) { controlsProfile?.let { OutsideTouch.read(prefs, it) } ?: OutsideTouch.read(prefs, controlsProfileId) }
            MenuSetting("Touch outside controls · remembered for these controls") {
                Segmented(listOf("Mouse" to OutsideTouch.TRACKPAD, "Look" to OutsideTouch.LOOK, "Off" to OutsideTouch.OFF), outside) { v ->
                    OutsideTouch.write(prefs, controlsProfileId, v)
                }
            }
        }
        MenuSetting("Touch · remembered for this game") {
            Segmented(TouchMode.entries.map { it.label to it }, ui.touch.mode) { m -> prefs.put(StreamUiPrefs.touchModeKey(gameKey), m.pref) }
        }
        MenuSetting("Mouse") {
            val current = when {
                ui.absoluteMouse -> "absolute"
                ui.mouseCapture -> "captured"
                else -> "free"
            }
            Segmented(listOf("Captured (games)" to "captured", "Free pointer" to "free", "Absolute (desktop)" to "absolute"), current) { m ->
                prefs.put("checkbox_absolute_mouse_mode", m == "absolute")
                prefs.put(StreamUiPrefs.MOUSE_CAPTURE_KEY, m == "captured")
            }
        }
        ToggleRow("Mouse buttons bar", "Left, middle, right, scroll strip, drag lock and keyboard on screen.", ui.mouseBar) { prefs.put(StreamUiPrefs.MOUSE_BAR_KEY, it) }
        ToggleRow(
            "Local cursor",
            localCursorNote(ui.localCursor, cursorMode),
            ui.localCursor,
        ) { prefs.put(StreamUiPrefs.LOCAL_CURSOR_KEY, it) }
        MenuSetting("Picture") {
            Segmented(listOf("Fit" to ScaleMode.FIT, "Fill" to ScaleMode.FILL, "Stretch" to ScaleMode.STRETCH), ui.scaleMode) { m ->
                prefs.put(SCALE_MODE_KEY, m.id)
                prefs.put("checkbox_stretch_video", m == ScaleMode.STRETCH)
            }
        }
        StatsQuick(ui, prefs)
        io.github.f_e_n_y_x.nebula.controls.ui.ControlsMenuSection(
            gameKey = gameKey, shown = ui.osc,
            onShown = { prefs.put("checkbox_show_onscreen_controls", it) },
            onEdit = actions.onEditControls,
        )
    }
}

/** Stats overlay quick switch (Off / Line / Card / Graph) plus the full picker. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsQuick(ui: StreamUiPrefs, prefs: LegacyPrefs) {
    val s = Nebula.scale
    var picker by remember { mutableStateOf(false) }
    val current: StatLayout? = if (ui.stats.enabled) ui.stats.layout else null
    MenuSetting("Stats overlay · ${ui.stats.metrics.size} metrics, ${ui.stats.position.label.lowercase()}") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Segmented(listOf<Pair<String, StatLayout?>>("Off" to null) + StatLayout.entries.map { it.label to it }, current) { l ->
                StatsOverlaySettings.setEnabled(prefs, l != null)
                if (l != null) StatsOverlaySettings.setLayout(prefs, l)
            }
            NebulaButton("Metrics & position", onClick = { picker = true }, style = ButtonStyle.Secondary)
        }
    }
    if (picker) StatsOverlayDialog(prefs, ui.stats) { picker = false }
}

/** Gyro, rumble and audio haptics for this stream; changes apply live. */
@Composable
private fun FeedbackSection(ui: StreamUiPrefs, prefs: LegacyPrefs, supports: (String) -> Boolean, hapticsNote: String?, phoneHasGyro: Boolean) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
        SectionTitle("Gyro", Modifier.padding(bottom = 0.dp))
        MotionControls(
            prefs, ui.motion,
            hostHasMotion = supports("motion"),
            phoneHasGyro = phoneHasGyro,
        )
        SectionTitle("Rumble & haptics", Modifier.padding(bottom = 0.dp))
        RumbleControls(
            prefs, io.github.f_e_n_y_x.nebula.settings.RumbleSettings.read(prefs.prefs.all),
            hostNote = if (!supports("rumble")) "Your PC doesn't report rumble support." else null,
        )
        HapticsControls(prefs, ui.haptics, hapticsNote)
    }
}

@Composable
private fun MenuSetting(title: String, control: @Composable () -> Unit) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(title, style = Nebula.type.label, color = NebulaColors.textSecondary)
        control()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bitrate(kbps: Int, note: String?, stats: StreamStats?, test: ConnectionTestUi, actions: StreamMenuActions) {
    val s = Nebula.scale
    val abr = stats?.abr
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        SectionTitle("Bitrate", Modifier.padding(bottom = 0.dp))
        SliderField(
            label = "Bitrate", value = (kbps.coerceAtLeast(1000)) / 1000f, range = 1f..150f, step = 1f, unit = "Mbps",
            onValueChange = { actions.onBitrate((it * 1000).toInt()) },
        )
        Text(
            note ?: if (abr != null) "Changes apply live and become adaptive bitrate's new starting point." else "Changes apply live; tap the value to type one.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
        abr?.let { Text(ConnectionText.abrDetail(it), style = Nebula.type.label, color = NebulaColors.textSecondary) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton(
                if (test is ConnectionTestUi.Running) "Testing…" else "Test connection",
                onClick = actions.onTestConnection, style = ButtonStyle.Secondary, icon = Icons.Outlined.NetworkCheck,
            )
            val suggestion = (test as? ConnectionTestUi.Done)?.report?.suggestion
            if (suggestion != null && suggestion.bitrateKbps != kbps) {
                NebulaButton("Use ${ConnectionText.formatMbps(suggestion.bitrateKbps)}", onClick = { actions.onBitrate(suggestion.bitrateKbps) }, style = ButtonStyle.Secondary)
            }
        }
        ConnectionTestProgress(test)
        if (test is ConnectionTestUi.Done) ConnectionReportCard(test.report)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Keys(onShortcut: (Shortcut) -> Unit) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        SectionTitle("Special keys", Modifier.padding(bottom = 0.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            menuShortcuts.forEach { k ->
                val shape = RoundedCornerShape(s.dp(10))
                Box(
                    Modifier.heightIn(min = s.dp(40)).nebulaClickable(shape, { onShortcut(k) }, role = Role.Button)
                        .background(NebulaColors.surface, shape).border(1.dp, NebulaColors.controlBorder, shape)
                        .padding(horizontal = s.dp(12), vertical = s.dp(9)),
                    contentAlignment = Alignment.Center,
                ) { Text(k.label, style = Nebula.type.label, color = NebulaColors.text) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Footer(actions: StreamMenuActions, onQuit: () -> Unit, quitLabel: String) {
    val s = Nebula.scale
    // A Column: the caller's Box would otherwise stack the note on top of the buttons.
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton("Disconnect", sublabel = "Game keeps running", onClick = actions.onDisconnect, style = ButtonStyle.Secondary, icon = Icons.Rounded.Close)
            NebulaButton(
                quitLabel, sublabel = if (quitLabel == "Quit game") "Closes it on the PC" else "Game keeps running",
                onClick = onQuit, style = ButtonStyle.Danger, icon = Icons.Rounded.PowerSettingsNew,
            )
        }
        Text("Disconnect leaves the game running on your PC: resume it from Home. Back or B closes this menu.", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

/**
 * Transparency of every overlay (PC keyboard, on-screen controls, mouse bar, float ball) with
 * − / + steps and the slider (tap the value to type one). Shown as transparency, stored as opacity.
 */
@Composable
private fun OverlayTransparency(opacity: Int, onOpacity: (Int) -> Unit) {
    val s = Nebula.scale
    val transparency = 100 - opacity
    MenuSetting("Overlay transparency · keyboard, controls, mouse bar, float ball, stats") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            NebulaButton("−", onClick = { onOpacity(opacity + STEP) }, style = ButtonStyle.Secondary, modifier = Modifier.semantics { contentDescription = "Less transparent" })
            SliderField(
                label = "Overlay transparency", value = transparency.toFloat(), range = 0f..90f, step = 5f, unit = "%",
                onValueChange = { onOpacity(100 - it.toInt()) }, modifier = Modifier.weight(1f),
            )
            NebulaButton("+", onClick = { onOpacity(opacity - STEP) }, style = ButtonStyle.Secondary, modifier = Modifier.semantics { contentDescription = "More transparent" })
        }
    }
}

private const val STEP = 10
