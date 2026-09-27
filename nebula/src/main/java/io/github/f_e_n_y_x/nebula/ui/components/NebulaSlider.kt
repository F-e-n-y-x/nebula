package io.github.f_e_n_y_x.nebula.ui.components

import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.roundToInt

/**
 * A continuous slider: one full-width track filled up to the value and a round thumb, with no
 * tick marks. Drag or tap to set it; with a D-pad or keyboard, left/right move by [step].
 * [onValueChange] fires while moving and [onValueChangeFinished] once the user lets go (or after
 * each key press), which is where callers persist the value.
 */
@Composable
fun NebulaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    modifier: Modifier = Modifier,
    valueLabel: String = value.roundToInt().toString(),
    onValueChangeFinished: () -> Unit = {},
) {
    val s = Nebula.scale
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val span = range.endInclusive - range.start
    fun snap(v: Float) = (range.start + ((v - range.start) / step).roundToInt() * step).coerceIn(range)

    val thumbR = s.dp(10)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(s.dp(36))
            .semantics {
                stateDescription = valueLabel
                progressBarRangeInfo = ProgressBarRangeInfo(value, range, steps = 0)
                setProgress { target -> change(snap(target)); finished(); true }
            }
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = when (e.key) {
                    Key.DirectionLeft -> -step
                    Key.DirectionRight -> step
                    else -> return@onKeyEvent false
                }
                val next = snap(current + delta)
                if (next != current) { change(next); finished() }
                true
            }
            .focusable(interactionSource = interaction)
            .pointerInput(range, step) {
                fun at(x: Float): Float {
                    val r = thumbR.toPx()
                    val f = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                    return snap(range.start + f * span)
                }
                detectTapGestures { change(at(it.x)); finished() }
            }
            .pointerInput(range, step) {
                fun at(x: Float): Float {
                    val r = thumbR.toPx()
                    val f = ((x - r) / (size.width - 2 * r)).coerceIn(0f, 1f)
                    return snap(range.start + f * span)
                }
                detectHorizontalDragGestures(
                    onDragStart = { change(at(it.x)) },
                    onDragEnd = { finished() },
                    onDragCancel = { finished() },
                ) { c, _ -> c.consume(); change(at(c.position.x)) }
            },
    ) {
        val r = thumbR.toPx()
        val trackH = s.dp(6).toPx()
        val cy = size.height / 2
        val left = r
        val width = size.width - 2 * r
        val x = left + width * ((value - range.start) / span).coerceIn(0f, 1f)
        val corner = CornerRadius(trackH / 2)
        drawRoundRect(NebulaColors.raised, Offset(left, cy - trackH / 2), Size(width, trackH), corner)
        drawRoundRect(NebulaColors.accent, Offset(left, cy - trackH / 2), Size(x - left, trackH), corner)
        if (focused) drawCircle(NebulaColors.focus, r + s.dp(4).toPx(), Offset(x, cy), style = Stroke(s.dp(2).toPx()))
        drawCircle(NebulaColors.text, r, Offset(x, cy))
    }
}

/**
 * A slider with its value in a chip beside it. Clicking the chip (touch, or OK on a D-pad) turns
 * it into a number field: Done/Enter or moving focus away applies the typed value, clamped to
 * [range]; Esc or Back cancels. Every slider setting uses this, so they all edit the same way.
 * [onValueChange] receives committed values only (drag release, key step, or typed value).
 */
@Composable
fun SliderField(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val s = Nebula.scale
    var live by remember(value) { mutableFloatStateOf(value) }
    var editing by remember { mutableStateOf(false) }
    fun fmt(v: Float) = v.roundToInt().toString()
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NebulaSlider(
                value = live, onValueChange = { live = it }, range = range, step = step,
                valueLabel = "${fmt(live)} $unit", onValueChangeFinished = { onValueChange(live) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(s.dp(14)))
            if (editing) {
                ValueEditor(
                    initial = fmt(live), unit = unit, range = range,
                    onApply = { v -> editing = false; live = v; onValueChange(v) },
                    onCancel = { editing = false },
                )
            } else {
                val shape = RoundedCornerShape(s.dp(10))
                Row(
                    Modifier
                        .semantics { contentDescription = "$label ${fmt(live)} $unit, tap to type a value" }
                        .nebulaClickable(shape, { editing = true })
                        .background(NebulaColors.surface, shape)
                        .border(1.dp, NebulaColors.controlBorder, shape)
                        .padding(horizontal = s.dp(12), vertical = s.dp(8)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(fmt(live), style = Nebula.type.mono.copy(fontFeatureSettings = "tnum"), color = NebulaColors.text)
                    Spacer(Modifier.width(s.dp(4)))
                    Text(unit, style = Nebula.type.label, color = NebulaColors.textSecondary)
                    Spacer(Modifier.width(s.dp(6)))
                    Icon(Icons.Outlined.Edit, null, tint = NebulaColors.textMuted, modifier = Modifier.size(s.dp(14)))
                }
            }
        }
    }
}

@Composable
private fun ValueEditor(
    initial: String,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    onApply: (Float) -> Unit,
    onCancel: () -> Unit,
) {
    val s = Nebula.scale
    var text by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val parsed = text.text.toFloatOrNull()
    val outOfRange = parsed != null && parsed !in range
    fun apply() {
        if (done) return
        done = true
        if (parsed == null) onCancel() else onApply(parsed.coerceIn(range))
    }
    fun cancel() { if (!done) { done = true; onCancel() } }
    BackHandler { cancel() }
    val shape = RoundedCornerShape(s.dp(10))
    Column(horizontalAlignment = Alignment.End) {
        Row(
            Modifier
                .background(NebulaColors.surface, shape)
                .border(2.dp, if (outOfRange) NebulaColors.warning else NebulaColors.focus, shape)
                .padding(horizontal = s.dp(12), vertical = s.dp(8)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { v -> if (v.text.length <= 6 && v.text.all { it.isDigit() || it == '.' }) text = v },
                singleLine = true,
                textStyle = Nebula.type.mono.copy(fontFeatureSettings = "tnum", color = NebulaColors.text, textAlign = TextAlign.End),
                cursorBrush = SolidColor(NebulaColors.accentText),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { apply() }),
                modifier = Modifier
                    .width(s.dp(56))
                    .focusRequester(focus)
                    .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) apply() }
                    .onPreviewKeyEvent { e ->
                        when {
                            e.type != KeyEventType.KeyDown -> false
                            e.key == Key.Escape -> { cancel(); true }
                            e.key == Key.Enter || e.key == Key.NumPadEnter -> { apply(); true }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = "Type a value in $unit" },
            )
            Spacer(Modifier.width(s.dp(4)))
            Text(unit, style = Nebula.type.label, color = NebulaColors.textSecondary)
        }
        if (outOfRange) {
            Spacer(Modifier.height(s.dp(4)))
            Text(
                "Between ${range.start.roundToInt()} and ${range.endInclusive.roundToInt()} $unit",
                style = Nebula.type.label, color = NebulaColors.warning,
            )
        }
    }
}
