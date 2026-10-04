package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardAlt
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.OutsideTouch
import io.github.f_e_n_y_x.nebula.domain.MicUi
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
import io.github.f_e_n_y_x.nebula.framegen.FramegenMenuState
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState
import io.github.f_e_n_y_x.nebula.framegen.performFramegenToggle
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.settings.HapticsSettings
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.fenyx.nebula.engine.framegen.FramegenKeys
import io.github.fenyx.nebula.engine.framegen.UpscalerMode

private const val COLUMNS = 4

/**
 * The quick-toggle grid at the top of the stream menu. Tap flips a setting for the running
 * stream; long-press (or a held OK on a remote) or Edit opens the chooser, where toggles are
 * shown, hidden and reordered (up to [QuickToggles.MAX]). Every tile is a D-pad focus target.
 */
@Composable
fun QuickToggleStrip(
    /** The menu's title, drawn left of Edit (one row saved on short screens); gets the row's weight. */
    title: @Composable (Modifier) -> Unit,
    ui: StreamUiPrefs,
    prefs: LegacyPrefs,
    /** Changes whenever a preference does, so per-profile values re-read. */
    tick: Any?,
    controlsProfileId: String?,
    controlsProfile: ControlsProfile?,
    framegen: FramegenMenuState,
    framegenQueued: Boolean,
    post: PostProcessStats?,
    onFramegenPause: (Boolean, Boolean) -> Boolean,
    mic: StreamHostState?,
    /** Portrait streaming "Follow rotation", or null where it doesn't apply (TV). */
    portraitFollow: Boolean?,
    onPortraitFollow: (Boolean) -> Unit,
    onKeyboard: () -> Unit,
    firstFocus: FocusRequester?,
    /** The layout on screen when the game's controls are a layout set (and shown), else null. */
    setLayout: String? = null,
    /** Goes to the set's next layout, as a switch element set to "next" does. */
    onNextLayout: () -> Unit = {},
    /** Adaptive bitrate is running for this stream. */
    abrOn: Boolean = false,
    /** Turns adaptive bitrate on or off for this stream (and saves it). */
    onAbr: (Boolean) -> Unit = {},
    /** The PC reports text field focus (Nova `text_context`, or a host that doesn't say). */
    textFields: Boolean = true,
) {
    val s = Nebula.scale
    val shown = remember(tick) { QuickToggles.read(prefs.prefs.all) }
    var editing by remember { mutableStateOf(false) }
    // The focused tile goes away when the editor opens (and the editor when it closes): move
    // focus with it, or a remote's D-pad would be left on the footer.
    val editorFirst = remember { FocusRequester() }
    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (editing == wasEditing) return@LaunchedEffect
        wasEditing = editing
        kotlinx.coroutines.delay(60)
        runCatching { if (editing) editorFirst.requestFocus() else firstFocus?.requestFocus() }
    }
    var notice by remember { mutableStateOf<String?>(null) }
    // Frame generation's live state comes with the next stats window; show the tap at once.
    var fgPending by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(framegen.panel.status) { fgPending = null }
    LaunchedEffect(fgPending) { if (fgPending != null) { kotlinx.coroutines.delay(3_000); fgPending = null } }

    val outside = remember(controlsProfileId, controlsProfile, tick) {
        when {
            controlsProfileId == null -> null
            controlsProfile != null -> OutsideTouch.read(prefs, controlsProfile)
            else -> OutsideTouch.read(prefs, controlsProfileId)
        }
    }
    val upscalerRunning = post != null && post.upscaler != UpscalerMode.OFF.id
    val micUi = mic?.mic ?: MicUi.HIDDEN

    fun state(t: QuickToggle): QuickTileState = when (t) {
        QuickToggle.CONTROLS -> QuickTileState.switch(ui.osc)
        QuickToggle.OUTSIDE_TOUCH -> QuickTileState.outside(outside)
        QuickToggle.FRAMEGEN -> QuickTileState.framegen(framegen.panel).let { st -> fgPending?.let { st.copy(on = it) } ?: st }
        QuickToggle.UPSCALER -> QuickTileState.upscaler(framegen.upscalerConfig.mode, framegen.panel.upscalerLocked)
        QuickToggle.STATS -> QuickTileState(ui.stats.enabled, if (ui.stats.enabled) ui.stats.layout.label else "Off")
        QuickToggle.GYRO -> QuickTileState.gyro(ui.motion.mode)
        QuickToggle.MIC -> QuickTileState.mic(micUi)
        QuickToggle.KEYBOARD -> QuickTileState.keyboard(ui.keyboardKind)
        QuickToggle.PORTRAIT -> QuickTileState.portrait(portraitFollow)
        QuickToggle.HAPTICS -> QuickTileState.switch(ui.haptics.enabled)
        QuickToggle.MOUSE_BAR -> QuickTileState.switch(ui.mouseBar)
        QuickToggle.LAYOUT -> QuickTileState.layout(setLayout)
        QuickToggle.ABR -> QuickTileState.switch(abrOn)
        QuickToggle.AUTO_KEYBOARD -> QuickTileState.autoKeyboard(ui.textFieldKeyboard, textFields)
    }

    fun tap(t: QuickToggle, st: QuickTileState) {
        notice = null
        if (!st.enabled) { notice = st.reason; return }
        when (t) {
            QuickToggle.CONTROLS -> prefs.put("checkbox_show_onscreen_controls", !ui.osc)
            QuickToggle.OUTSIDE_TOUCH -> if (outside != null && controlsProfileId != null) {
                OutsideTouch.write(prefs, controlsProfileId, QuickToggles.nextOutside(outside))
            }
            QuickToggle.FRAMEGEN -> {
                val want = !st.on
                val action = framegen.panel.toggle(want)
                notice = performFramegenToggle(action, onFramegenPause, { prefs.put(FramegenKeys.ENABLED, it) }, framegenQueued)
                val instant = action == FramegenPanelState.Toggle.PAUSE || action == FramegenPanelState.Toggle.RESUME || action == FramegenPanelState.Toggle.FORCE_RESUME
                if (instant && notice == null) fgPending = want
            }
            QuickToggle.UPSCALER -> {
                val current = framegen.upscalerConfig.mode
                val last = UpscalerMode.entries.firstOrNull { it.id == prefs.prefs.getString(QuickToggles.UPSCALER_LAST_KEY, null) }
                val r = QuickToggles.upscalerTap(current, last, framegen.panel.upscalerLocked, upscalerRunning)
                r.mode?.let { next ->
                    if (current != UpscalerMode.OFF) prefs.put(QuickToggles.UPSCALER_LAST_KEY, current.id)
                    prefs.put(FramegenKeys.UPSCALER, next.id)
                }
                notice = r.notice
            }
            QuickToggle.STATS -> StatsOverlaySettings.setEnabled(prefs, !ui.stats.enabled)
            QuickToggle.GYRO -> {
                val current = ui.motion.mode
                val last = GyroMode.fromId(prefs.prefs.getString(QuickToggles.GYRO_LAST_KEY, null))
                if (current != GyroMode.OFF) prefs.put(QuickToggles.GYRO_LAST_KEY, current.id)
                MotionSettings.write(prefs, ui.motion.copy(mode = QuickToggles.gyroToggled(current, last)))
            }
            QuickToggle.MIC -> {
                // Asks for the permission, or opens Android's settings when it's blocked.
                mic?.toggleMic()
            }
            QuickToggle.KEYBOARD -> onKeyboard()
            QuickToggle.PORTRAIT -> portraitFollow?.let { onPortraitFollow(!it) }
            QuickToggle.HAPTICS -> HapticsSettings.write(prefs, ui.haptics.copy(enabled = !ui.haptics.enabled))
            QuickToggle.MOUSE_BAR -> prefs.put(StreamUiPrefs.MOUSE_BAR_KEY, !ui.mouseBar)
            QuickToggle.LAYOUT -> onNextLayout()
            QuickToggle.ABR -> onAbr(!abrOn)
            QuickToggle.AUTO_KEYBOARD -> prefs.put(TextFieldKeyboard.KEY, TextFieldKeyboard.next(ui.textFieldKeyboard).id)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            title(Modifier.weight(1f))
            NebulaButton(
                if (editing) "Done" else "Edit",
                onClick = { editing = !editing; notice = null },
                style = ButtonStyle.Ghost,
                icon = if (editing) null else Icons.Outlined.Edit,
                modifier = Modifier.semantics { contentDescription = if (editing) "Done choosing quick settings" else "Choose quick settings" },
            )
        }
        if (editing) {
            QuickToggleEditor(shown, editorFirst, onChange = { prefs.put(QuickToggles.KEY, QuickToggles.encode(it)) }, onReset = { prefs.put(QuickToggles.KEY, null) })
        } else if (shown.isEmpty()) {
            Text("No quick settings. Edit to choose some.", style = Nebula.type.label, color = NebulaColors.textMuted)
        } else {
            shown.chunked(COLUMNS).forEachIndexed { row, items ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                    items.forEachIndexed { i, t ->
                        val st = state(t)
                        QuickTile(
                            t, st, icon(t, st),
                            onClick = { tap(t, st) },
                            onLongClick = { editing = true; notice = null },
                            modifier = Modifier.weight(1f).then(if (row == 0 && i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier),
                        )
                    }
                    repeat(COLUMNS - items.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        notice?.let { Text(it, style = Nebula.type.label, color = NebulaColors.warning, modifier = Modifier.padding(horizontal = s.dp(4))) }
    }
}

private fun icon(t: QuickToggle, st: QuickTileState): ImageVector = when (t) {
    QuickToggle.CONTROLS -> Icons.Outlined.SportsEsports
    QuickToggle.OUTSIDE_TOUCH -> Icons.Outlined.TouchApp
    QuickToggle.FRAMEGEN -> Icons.Outlined.AutoAwesome
    QuickToggle.UPSCALER -> Icons.Outlined.AutoFixHigh
    QuickToggle.STATS -> Icons.Outlined.QueryStats
    QuickToggle.GYRO -> Icons.Outlined.Explore
    QuickToggle.MIC -> if (st.on) Icons.Outlined.Mic else Icons.Outlined.MicOff
    QuickToggle.KEYBOARD -> Icons.Outlined.Keyboard
    QuickToggle.PORTRAIT -> Icons.Outlined.ScreenRotation
    QuickToggle.HAPTICS -> Icons.Outlined.Vibration
    QuickToggle.MOUSE_BAR -> Icons.Outlined.Mouse
    QuickToggle.LAYOUT -> Icons.Outlined.SwapHoriz
    QuickToggle.ABR -> Icons.Outlined.NetworkCheck
    QuickToggle.AUTO_KEYBOARD -> if (st.on) Icons.Outlined.KeyboardAlt else Icons.Outlined.KeyboardHide
}

@Composable
private fun QuickTile(t: QuickToggle, st: QuickTileState, icon: ImageVector, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    val on = st.on && st.enabled
    val role = when (st.kind) {
        QuickTileState.Kind.SWITCH -> Role.Switch
        QuickTileState.Kind.CYCLE, QuickTileState.Kind.ACTION -> Role.Button
    }
    Column(
        modifier
            .heightIn(min = s.dp(64))
            .testTag("quick:${t.id}")
            .semantics {
                contentDescription = t.longLabel
                stateDescription = st.spoken
            }
            .nebulaClickable(shape, onClick, role = role, onLongClick = onLongClick)
            .background(if (on) NebulaColors.accentTint else NebulaColors.surface, shape)
            .border(1.dp, if (on) NebulaColors.accent else NebulaColors.border, shape)
            .alpha(if (st.enabled) 1f else 0.5f)
            .padding(horizontal = s.dp(4), vertical = s.dp(7)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(s.dp(2)),
    ) {
        Box(
            Modifier.size(s.dp(26)).background(if (on) NebulaColors.accent else NebulaColors.raised, RoundedCornerShape(50)).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (on) Color.White else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(16))) }
        Text(
            t.label, style = Nebula.type.label, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {},
        )
        Text(
            st.value, style = Nebula.type.label, color = if (on) NebulaColors.accentText else NebulaColors.textMuted, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Show / hide and reorder: shown ones first, in order, with up and down; the rest below. */
@Composable
private fun QuickToggleEditor(shown: List<QuickToggle>, firstFocus: FocusRequester, onChange: (List<QuickToggle>) -> Unit, onReset: () -> Unit) {
    val s = Nebula.scale
    val full = shown.size >= QuickToggles.MAX
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        Text(
            "Choose which to show (${shown.size} of ${QuickToggles.MAX} shown). Up and down set the order.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
        QuickToggles.editorOrder(shown).forEachIndexed { n, t -> key(t) {
            val isShown = t in shown
            val canAdd = isShown || !full
            val index = shown.indexOf(t)
            val shape = RoundedCornerShape(s.dp(12))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(6))) {
                Row(
                    Modifier.weight(1f).heightIn(min = s.dp(48))
                        .then(if (n == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .semantics { stateDescription = if (isShown) "Shown" else if (canAdd) "Hidden" else "Hidden, ${QuickToggles.MAX} already shown" }
                        .nebulaClickable(shape, { if (canAdd) onChange(QuickToggles.toggleShown(shown, t)) }, role = Role.Checkbox)
                        .background(NebulaColors.surface, shape)
                        .alpha(if (canAdd) 1f else 0.5f)
                        .padding(horizontal = s.dp(12), vertical = s.dp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
                ) {
                    Icon(
                        if (isShown) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank, null,
                        tint = if (isShown) NebulaColors.accentText else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(20)),
                    )
                    Text(t.longLabel, style = Nebula.type.body, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (isShown) {
                    MoveButton(Icons.Outlined.KeyboardArrowUp, "Move ${t.longLabel} up", enabled = index > 0) { onChange(QuickToggles.move(shown, t, -1)) }
                    MoveButton(Icons.Outlined.KeyboardArrowDown, "Move ${t.longLabel} down", enabled = index < shown.lastIndex) { onChange(QuickToggles.move(shown, t, 1)) }
                }
            }
        } }
        NebulaButton("Reset to default", onClick = onReset, style = ButtonStyle.Ghost)
    }
}

@Composable
private fun MoveButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Box(
        Modifier.size(s.dp(44))
            .semantics { contentDescription = description }
            .nebulaClickable(shape, { if (enabled) onClick() })
            .background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.controlBorder, shape)
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = NebulaColors.text, modifier = Modifier.size(s.dp(20))) }
}
