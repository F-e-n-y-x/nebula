package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.input.KeyboardPage
import io.github.f_e_n_y_x.nebula.input.ModState
import io.github.f_e_n_y_x.nebula.input.PcKey
import io.github.f_e_n_y_x.nebula.input.PcKeyboardModel
import io.github.f_e_n_y_x.nebula.input.PcLayouts
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.settings.KeyboardSettings
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.roundToInt

/**
 * The on-screen PC keyboard (V+'s custom keyboard, Nebula look): Keys / Nav / Numpad / Mini pages,
 * sticky Ctrl/Shift/Alt/Win (tap = next key, tap again = lock), hold-to-repeat keys for games,
 * opacity and size −/+ in its own bar, draggable. Keys are D-pad focus targets on TV.
 */
@Composable
fun PcKeyboardOverlay(remote: () -> RemoteInput?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { KeyboardSettings(ctx) }
    val tick by settings.changes().collectAsState(initial = null)
    val cfg = remember(tick) { settings.read() }
    val model = remember { PcKeyboardModel(remote) }
    DisposableEffect(Unit) { onDispose { model.releaseAll() } }
    val mods by model.mods.collectAsState()
    val page = KeyboardPage.entries.firstOrNull { it.name == cfg.page } ?: if (cfg.mini) KeyboardPage.MINI else KeyboardPage.FULL
    val s = Nebula.scale
    val form = Nebula.form
    val density = LocalDensity.current
    var dragX by remember { mutableFloatStateOf(cfg.offsetX) }
    var dragY by remember { mutableFloatStateOf(cfg.offsetY) }

    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
        val screenH = with(density) { maxHeight.toPx() }
        // Automatic height: ~42% of the screen for the full keyboard, less on TV (seen from the couch, used with a remote).
        val autoH = screenH * (if (page == KeyboardPage.MINI) 0.14f else if (form.isTv) 0.40f else 0.44f)
        val heightPx = if (cfg.heightPx > 0) cfg.heightPx.toFloat() else autoH
        val keysH = with(density) { heightPx.toDp() }
        // Keys / Mini span the screen (capped on big screens); Nav and Numpad are a compact block on the right.
        val small = page == KeyboardPage.NAV || page == KeyboardPage.NUM
        val maxW = when {
            small -> minOf(maxWidth, s.dp(520))
            form.isCompact || form.isLandscape && !form.isTv && form.widthDp < 1000 -> maxWidth
            else -> minOf(maxWidth, s.dp(1100))
        }
        val shape = RoundedCornerShape(topStart = s.dp(18), topEnd = s.dp(18), bottomStart = s.dp(10), bottomEnd = s.dp(10))
        Column(
            Modifier.align(if (small) Alignment.BottomEnd else Alignment.BottomCenter).offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
                .widthIn(max = maxW).fillMaxWidth()
                .alpha(cfg.opacity / 10f)
                .background(Color(0xF00E0E10), shape).border(1.dp, NebulaColors.border, shape)
                .padding(s.dp(6)),
        ) {
            ToolBar(
                page = page,
                opacity = cfg.opacity,
                onPage = { settings.setPage(it.name) },
                onOpacity = { settings.setOpacity(it) },
                onSize = { factor ->
                    val h = (heightPx * factor).coerceIn(screenH * 0.1f, screenH * 0.7f)
                    settings.setHeight(h.roundToInt())
                },
                onDrag = { dx, dy -> dragX += dx; dragY += dy },
                onDragEnd = { settings.setOffset(dragX, dragY) },
                onClose = onClose,
            )
            Spacer(Modifier.height(s.dp(4)))
            Keys(PcLayouts.rows(page), mods, model, keysH)
        }
    }
}

@Composable
private fun ToolBar(
    page: KeyboardPage,
    opacity: Int,
    onPage: (KeyboardPage) -> Unit,
    onOpacity: (Int) -> Unit,
    onSize: (Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onClose: () -> Unit,
) {
    val s = Nebula.scale
    val first = rememberKeyboardFocus()
    Row(Modifier.fillMaxWidth().height(s.dp(40)).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(4))) {
        Box(
            Modifier.size(s.dp(36)).pointerInput(Unit) {
                detectDragGestures(onDragEnd = onDragEnd) { c, d -> c.consume(); onDrag(d.x, d.y) }
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.DragIndicator, "Move the keyboard", tint = NebulaColors.textMuted) }
        KeyboardPage.entries.forEach { p ->
            BarChip(p.label, selected = p == page, modifier = if (p == page) Modifier.focusRequester(first) else Modifier) { onPage(p) }
        }
        Spacer(Modifier.width(s.dp(16)))
        // Same convention as the stream menu: the value is transparency, + makes it more see-through.
        BarChip("−", description = "Less transparent") { onOpacity(opacity + 1) }
        Text("${100 - opacity * 10}%", style = Nebula.type.label, color = NebulaColors.textSecondary, modifier = Modifier.width(s.dp(44)), textAlign = TextAlign.Center)
        BarChip("+", description = "More transparent") { onOpacity(opacity - 1) }
        BarChip("A−", description = "Smaller keyboard") { onSize(0.9f) }
        BarChip("A+", description = "Bigger keyboard") { onSize(1.1f) }
        BarChip("✕", description = "Close the keyboard", onClick = onClose)
    }
}

@Composable
private fun BarChip(label: String, selected: Boolean = false, description: String? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(8))
    Box(
        modifier.height(s.dp(34)).widthIn(min = s.dp(34))
            .semantics { if (description != null) contentDescription = description }
            .nebulaClickable(shape, onClick, role = Role.Button)
            .background(if (selected) NebulaColors.accent else NebulaColors.surface, shape)
            .padding(horizontal = s.dp(10)),
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Nebula.type.label, color = if (selected) Color.White else NebulaColors.text) }
}

@Composable
private fun Keys(rows: List<List<PcKey>>, mods: Map<Int, ModState>, model: PcKeyboardModel, height: Dp) {
    val s = Nebula.scale
    val shift = mods.any { (vk, st) -> (vk == 0xA0 || vk == 0xA1) && st != ModState.OFF }
    val gap = s.dp(4)
    Column(Modifier.fillMaxWidth().height(height), verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { k ->
                    Key(k, mods[k.vk] ?: ModState.OFF, shift, model, Modifier.weight(k.width).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Key(k: PcKey, mod: ModState, shift: Boolean, model: PcKeyboardModel, modifier: Modifier) {
    val s = Nebula.scale
    var down by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(s.dp(8))
    val bg = when {
        down -> NebulaColors.accent
        k.isModifier && mod == ModState.LOCKED -> NebulaColors.accent
        k.isModifier && mod == ModState.ONCE -> NebulaColors.accentTint
        k.isModifier -> NebulaColors.raised
        else -> NebulaColors.surface
    }
    val label = if (shift && k.shifted != null) k.shifted else if (!shift && k.label.length == 1 && k.label[0].isLetter()) k.label.lowercase() else k.label
    Box(
        modifier
            .semantics {
                role = Role.Button
                contentDescription = k.label
                if (k.isModifier) stateDescription = when (mod) { ModState.OFF -> "Off"; ModState.ONCE -> "Next key"; ModState.LOCKED -> "Locked" }
            }
            .background(bg, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) NebulaColors.focus else if (k.isModifier && mod != ModState.OFF) NebulaColors.accentText else NebulaColors.border, shape)
            // D-pad / remote: OK presses the key.
            .focusable(interactionSource = interaction)
            .onKeyEvent { e ->
                if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onKeyEvent false
                if (k.isModifier) {
                    if (e.type == KeyEventType.KeyUp) model.tapModifier(k.vk)
                } else if (e.type == KeyEventType.KeyDown) {
                    down = true; model.keyDown(k.vk)
                } else if (e.type == KeyEventType.KeyUp) {
                    down = false; model.keyUp(k.vk)
                }
                true
            }
            // Touch: normal keys are held while the finger is down (WASD in games); modifiers toggle on tap.
            .pointerInput(k) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    if (k.isModifier) {
                        down = true
                    } else {
                        down = true
                        model.keyDown(k.vk)
                    }
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    down = false
                    if (k.isModifier) model.tapModifier(k.vk) else model.keyUp(k.vk)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (label.length <= 2) Nebula.type.bodyStrong else Nebula.type.label, color = if (down || mod == ModState.LOCKED) Color.White else NebulaColors.text, maxLines = 1)
        if (k.isModifier && mod == ModState.LOCKED) {
            Box(Modifier.align(Alignment.TopEnd).padding(s.dp(4)).size(s.dp(5)).background(Color.White, RoundedCornerShape(50)))
        }
    }
}

/** Keeps the first key focused when the keyboard opens with a remote (TV). */
@Composable
internal fun rememberKeyboardFocus(): FocusRequester {
    val r = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { r.requestFocus() } }
    return r
}
