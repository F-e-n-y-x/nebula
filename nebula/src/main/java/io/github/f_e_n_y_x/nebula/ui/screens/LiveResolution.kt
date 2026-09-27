package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.domain.ResolutionOption
import io.github.f_e_n_y_x.nebula.domain.ResolutionOptions
import io.github.f_e_n_y_x.nebula.domain.SwitchState
import io.github.f_e_n_y_x.nebula.domain.model.Resolution
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.SwitchNote
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the stream menu needs to offer a live resolution change. */
class LiveResolutionUi(
    /** The mode streaming now; null while a switch runs. */
    val current: VideoMode?,
    val state: SwitchState?,
    /** The mode saved for this game, if any. */
    val saved: VideoMode?,
    val onApply: (mode: VideoMode, rememberForGame: Boolean) -> Unit,
)

/** The stream menu's "Resolution" row: what's streaming and a way to change it. */
@Composable
internal fun ResolutionRow(ui: LiveResolutionUi, onOpen: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    val busy = ui.state?.busy == true
    Row(
        Modifier.fillMaxWidth()
            .nebulaClickable(shape, { if (!busy && ui.current != null) onOpen() })
            .background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(16), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(12)),
    ) {
        Icon(Icons.Outlined.AspectRatio, contentDescription = null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(22)))
        Column(Modifier.weight(1f)) {
            Text("Resolution", style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(
                when {
                    busy -> "Switching…"
                    ui.current == null -> "Available once the stream is live"
                    ui.saved == ui.current -> "${ui.current.label} · saved for this game"
                    else -> "${ui.current.label} · change live, the game keeps running"
                },
                style = Nebula.type.label, color = NebulaColors.textMuted,
            )
        }
        Text("Change", style = Nebula.type.label, color = if (busy) NebulaColors.textMuted else NebulaColors.accentText)
    }
}

/**
 * Picks a new size and frame rate for the running stream: this device's size, Settings' presets,
 * custom sizes (and a new one), and a frame rate. A panel over the stream: two columns on
 * landscape phones, tablets and TV (D-pad starts on the current size), one column in portrait.
 * Back closes it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ResolutionPicker(ui: LiveResolutionUi, onDismiss: () -> Unit, onApplied: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val s = Nebula.scale
    val form = Nebula.form
    val ctx = LocalContext.current
    val current = ui.current ?: return
    val device = remember { deviceResolution(ctx).let { (w, h) -> Resolution(w, h) } }
    val displayHz = remember { displayRefreshHz(ctx) }
    val showLow = remember { LegacyPrefs(ctx).prefs.getBoolean("checkbox_show_low_resolution_presets", false) }
    var customVersion by remember { mutableIntStateOf(0) }
    val sizes = remember(customVersion) {
        ResolutionOptions.sizes(device, customResolutions(ctx).map { (w, h) -> Resolution(w, h) }, showLow, current.resolution)
    }
    val rates = remember { ResolutionOptions.frameRates(current.fps, displayHz) }

    var size by remember { mutableStateOf(current.resolution) }
    var fps by remember { mutableIntStateOf(current.fps) }
    var saveForGame by remember { mutableStateOf(ui.saved != null) }
    var adding by remember { mutableStateOf(false) }
    val target = VideoMode(size.width, size.height, fps)
    val changesMode = target != current
    val changesSaved = saveForGame != (ui.saved != null) || (saveForGame && ui.saved != target)

    val two = form.isTv || form.isLandscape || !form.isCompact
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
        val shape = if (two) RoundedCornerShape(s.dp(20)) else RoundedCornerShape(topStart = s.dp(20), topEnd = s.dp(20))
        BoxWithConstraints(
            Modifier.fillMaxSize().then(if (two) Modifier.systemBarsPadding().padding(s.dp(16)) else Modifier),
            contentAlignment = if (two) Alignment.Center else Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .then(if (two) Modifier.widthIn(max = s.dp(if (form.isTv) 820 else 760)).fillMaxWidth() else Modifier.fillMaxWidth())
                    .heightIn(max = maxHeight * (if (two) 1f else 0.86f))
                    .background(NebulaColors.panel, shape)
                    .border(1.dp, NebulaColors.border, shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .then(if (two) Modifier else Modifier.navigationBarsPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(s.dp(if (form.isTv) 28 else 22)),
                verticalArrangement = Arrangement.spacedBy(s.dp(16)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(s.dp(4))) {
                    Text("Resolution", style = Nebula.type.heading, color = NebulaColors.text)
                    Text(
                        "Now ${current.label}. Switching reconnects in place in a couple of seconds; the game keeps running on your PC.",
                        style = Nebula.type.label, color = NebulaColors.textMuted,
                    )
                }
                val sizesBlock: @Composable () -> Unit = {
                    SizeGrid(sizes, size, onPick = { size = it; adding = false }, adding = adding, onAdd = { adding = !adding })
                    if (adding) {
                        CustomSizeEntry(onAdd = { r ->
                            addCustomResolution(ctx, r)
                            customVersion++
                            size = r
                            adding = false
                        })
                    }
                }
                val sideBlock: @Composable () -> Unit = {
                    MenuLabel("Frame rate")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                        rates.forEach { r ->
                            Choice("$r", if (r == displayHz) "fps · screen" else "fps", r == fps, Modifier.widthIn(min = s.dp(72))) { fps = r }
                        }
                    }
                    ToggleRow("Use for this game from now on", "Next time this game starts at this size and frame rate.", saveForGame) { saveForGame = it }
                }
                val actionsBlock: @Composable () -> Unit = {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s.dp(8), Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                        NebulaButton("Cancel", onClick = onDismiss, style = ButtonStyle.Ghost)
                        val label = when {
                            changesMode -> "Switch to ${target.label}"
                            changesSaved -> if (saveForGame) "Save for this game" else "Forget for this game"
                            else -> "No change"
                        }
                        NebulaButton(
                            label,
                            onClick = {
                                if (changesMode || changesSaved) {
                                    ui.onApply(target, saveForGame)
                                    onApplied()
                                }
                            },
                            style = if (changesMode || changesSaved) ButtonStyle.Primary else ButtonStyle.Secondary,
                        )
                    }
                }
                if (two) {
                    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(20))) {
                        Column(Modifier.weight(1.15f), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                            MenuLabel("Size")
                            sizesBlock()
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(s.dp(12))) { sideBlock() }
                    }
                    actionsBlock()
                } else {
                    MenuLabel("Size")
                    sizesBlock()
                    sideBlock()
                    actionsBlock()
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SizeGrid(sizes: List<ResolutionOption>, selected: Resolution, onPick: (Resolution) -> Unit, adding: Boolean, onAdd: () -> Unit) {
    val s = Nebula.scale
    val first = remember { FocusRequester() }
    // D-pad and keyboards start on the size that's streaming now.
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        sizes.forEach { o ->
            val on = o.resolution == selected
            Choice(
                o.label, o.size, on,
                Modifier.widthIn(min = s.dp(132)).then(if (on) Modifier.focusRequester(first) else Modifier),
            ) { onPick(o.resolution) }
        }
        val shape = RoundedCornerShape(s.dp(12))
        Row(
            Modifier.widthIn(min = s.dp(132)).heightIn(min = s.dp(56))
                .nebulaClickable(shape, onAdd)
                .background(if (adding) NebulaColors.accentTint else Color.Transparent, shape)
                .border(1.dp, NebulaColors.controlBorder, shape)
                .padding(horizontal = s.dp(14), vertical = s.dp(8)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.dp(8)),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(18)))
            Text("Custom size", style = Nebula.type.label, color = NebulaColors.text)
        }
    }
}

/** A selectable tile: a short title over a detail line. */
@Composable
private fun Choice(title: String, detail: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(
        modifier
            .heightIn(min = s.dp(56))
            .semantics { selected = on }
            .nebulaClickable(shape, onClick, role = Role.RadioButton)
            .background(if (on) NebulaColors.accent else NebulaColors.surface, shape)
            .border(1.dp, if (on) NebulaColors.accent else NebulaColors.controlBorder, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(8)),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = Nebula.type.bodyStrong, color = if (on) Color.White else NebulaColors.text, maxLines = 1)
        Text(detail, style = Nebula.type.label, color = if (on) Color.White.copy(alpha = 0.8f) else NebulaColors.textMuted, maxLines = 1)
    }
}

@Composable
private fun CustomSizeEntry(onAdd: (Resolution) -> Unit) {
    val s = Nebula.scale
    var w by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    val error = ResolutionOptions.validateCustom(w.toIntOrNull(), h.toIntOrNull())
    val widthFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { widthFocus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            SizeField(w, "Width", Modifier.weight(1f).focusRequester(widthFocus)) { w = it }
            Text("×", color = NebulaColors.textMuted)
            SizeField(h, "Height", Modifier.weight(1f)) { h = it }
            NebulaButton("Add", onClick = { if (error == null) onAdd(Resolution(w.toInt(), h.toInt())) }, style = if (error == null) ButtonStyle.Primary else ButtonStyle.Secondary)
        }
        Text(
            if (w.isEmpty() && h.isEmpty()) "Saved with your custom resolutions in Settings." else error ?: "Ready to add ${w}×$h.",
            style = Nebula.type.label, color = if (error != null && (w.isNotEmpty() || h.isNotEmpty())) NebulaColors.warning else NebulaColors.textMuted,
        )
    }
}

@Composable
private fun SizeField(value: String, hint: String, modifier: Modifier, onChange: (String) -> Unit) {
    val s = Nebula.scale
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(10))
    BasicTextField(
        value = value, onValueChange = { v -> onChange(v.filter(Char::isDigit).take(4)) }, singleLine = true,
        textStyle = Nebula.type.body.copy(color = NebulaColors.text),
        cursorBrush = SolidColor(NebulaColors.accentText),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = modifier.semantics { contentDescription = hint }.onFocusChanged { focused = it.isFocused }
            .background(NebulaColors.bg, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .padding(s.dp(12)),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, style = Nebula.type.body, color = NebulaColors.textMuted); inner() },
    )
}

@Composable
private fun MenuLabel(text: String) = Text(text, style = Nebula.type.label, color = NebulaColors.textSecondary)

/**
 * "Switching to 2560×1440@120…" while a live resolution change runs (the last frame stays on
 * screen behind it), then the result for a few seconds.
 */
@Composable
internal fun SwitchingOverlay(state: SwitchState?, note: SwitchNote?, onNoteShown: () -> Unit, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val text = when (state) {
        is SwitchState.Switching -> "Switching to ${state.to.label}…"
        is SwitchState.RollingBack -> "Couldn't switch. Going back to ${state.to.label}…"
        else -> null
    }
    LaunchedEffect(note) {
        if (note != null) {
            delay(if (note.ok) 3_000 else 6_000)
            onNoteShown()
        }
    }
    val shown = text ?: note?.text
    AnimatedVisibility(shown != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Row(
            Modifier.systemBarsPadding().padding(top = s.dp(16), start = s.dp(16), end = s.dp(16))
                .widthIn(max = s.dp(560))
                .background(Color(0xE60A0A0B), RoundedCornerShape(50))
                .border(1.dp, NebulaColors.border, RoundedCornerShape(50))
                .padding(horizontal = s.dp(16), vertical = s.dp(10)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
        ) {
            when {
                text != null -> CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                note?.ok == true -> Icon(Icons.Rounded.CheckCircle, null, tint = NebulaColors.success, modifier = Modifier.size(s.dp(18)))
                else -> Icon(Icons.Rounded.ErrorOutline, null, tint = NebulaColors.warning, modifier = Modifier.size(s.dp(18)))
            }
            Text(shown ?: "", style = Nebula.type.label, color = NebulaColors.text)
        }
    }
}

/** This screen's refresh rate, rounded (60, 90, 120…). */
private fun displayRefreshHz(ctx: Context): Int {
    val hz = if (Build.VERSION.SDK_INT >= 30) {
        runCatching { ctx.display?.refreshRate }.getOrNull()
    } else {
        @Suppress("DEPRECATION")
        (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.refreshRate
    }
    return (hz ?: 60f).roundToInt().coerceIn(30, 240)
}

/** Adds [r] to V+'s custom resolution list (the one Settings → Custom resolutions edits). */
private fun addCustomResolution(ctx: Context, r: Resolution) {
    val p = ctx.getSharedPreferences(CUSTOM_RES, Context.MODE_PRIVATE)
    val set = p.getStringSet(CUSTOM_RES, emptySet()).orEmpty() + "${r.width}x${r.height}"
    p.edit().putStringSet(CUSTOM_RES, set).apply()
}

private const val CUSTOM_RES = "custom_resolutions"
