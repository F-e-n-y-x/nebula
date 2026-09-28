package io.github.f_e_n_y_x.nebula.diagnostics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.diagnostics.CenterSampler
import io.github.f_e_n_y_x.nebula.diagnostics.ControllerTester
import io.github.f_e_n_y_x.nebula.diagnostics.Level
import io.github.f_e_n_y_x.nebula.diagnostics.PadLive
import io.github.f_e_n_y_x.nebula.diagnostics.StickCalibration
import io.github.f_e_n_y_x.nebula.diagnostics.StickCalibrations
import io.github.f_e_n_y_x.nebula.diagnostics.StickShape
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.SliderField
import io.github.f_e_n_y_x.nebula.ui.screens.Segmented
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.roundToInt

/**
 * Per-controller stick shaping: deadzone, outer edge and anti-deadzone per stick, plus a measured
 * resting centre. Saved by the pad's descriptor and used by every stream from then on.
 */
@Composable
internal fun CalibrationTab(tester: ControllerTester, capture: Boolean, onCapture: (Boolean) -> Unit, preselect: String?) {
    val sticks = tester.pads.filter { it.device.traits.hasSticks }
    Column(verticalArrangement = gap) {
        CaptureBanner(capture, onCapture)
        if (sticks.isEmpty()) {
            EmptyState(Icons.Outlined.Tune, "No controller with sticks", "Connect a controller to set its deadzone and stick range. Each controller keeps its own.")
            return@Column
        }
        var chosen by remember { mutableStateOf(preselect) }
        val pad = sticks.firstOrNull { it.device.descriptor == chosen } ?: sticks.first()
        if (sticks.size > 1) {
            Segmented(sticks.map { it.device.name to it.device.descriptor }, pad.device.descriptor) { chosen = it }
        }
        // Keyed on the pad so switching controllers loads that pad's saved values.
        androidx.compose.runtime.key(pad.device.descriptor) { PadCalibration(pad) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PadCalibration(pad: PadLive) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val d = pad.device
    val saved by StickCalibrations.all.collectAsState()
    val stored = saved[d.descriptor]
    val base = remember { globalDeadzone(ctx) }
    var draft by remember(stored) { mutableStateOf(stored ?: StickCalibration(d.descriptor, d.name, StickShape(deadzone = base), StickShape(deadzone = base))) }
    var linked by remember { mutableStateOf(stored == null || stored.left.copy(centerX = 0f, centerY = 0f) == stored.right.copy(centerX = 0f, centerY = 0f)) }
    var measuring by remember { mutableStateOf(false) }
    var measureNote by remember { mutableStateOf<Pair<String, Level>?>(null) }
    val dirty = draft.sanitized() != stored
    val hasRight = d.layout.rightX >= 0 && d.layout.rightY >= 0

    if (measuring) {
        LaunchedEffect(Unit) {
            val left = CenterSampler()
            val right = CenterSampler()
            val start = withFrameMillis { it }
            var now = start
            while (now - start < MEASURE_MS) {
                left.add(pad.axis(d.layout.leftX), pad.axis(d.layout.leftY))
                if (hasRight) right.add(pad.axis(d.layout.rightX), pad.axis(d.layout.rightY))
                now = withFrameMillis { it }
            }
            val l = left.result()
            val r = if (hasRight) right.result() else null
            measuring = false
            if (l == null) {
                measureNote = "Not enough readings; try again." to Level.WARN
                return@LaunchedEffect
            }
            fun StickShape.with(res: CenterSampler.Result?) = if (res == null) this else copy(centerX = res.centerX, centerY = res.centerY, deadzone = maxOf(deadzone, res.suggestedDeadzone))
            draft = draft.copy(left = draft.left.with(l), right = draft.right.with(r)).sanitized()
            val moved = l.moved || r?.moved == true
            measureNote = if (moved) {
                "A stick moved while measuring. Let go of both sticks and measure again." to Level.WARN
            } else {
                val offs = "left %+.3f, %+.3f".format(l.centerX, l.centerY) + (r?.let { " · right %+.3f, %+.3f".format(it.centerX, it.centerY) } ?: "")
                "Centre measured ($offs). Deadzones cover the jitter seen. Save to keep it." to Level.OK
            }
        }
    }

    DiagCard {
        Text(d.name, style = Nebula.type.heading, color = NebulaColors.text)
        Text(
            if (stored != null) "Saved for this controller. Streams use these values instead of the global deadzone (${(base * 100).roundToInt()} %)."
            else "Not calibrated: streams use the global deadzone (${(base * 100).roundToInt()} %) from Settings → Gamepads.",
            style = Nebula.type.label, color = if (stored != null) NebulaColors.success else NebulaColors.textSecondary,
        )
        Spacer(Modifier.height(s.dp(14)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            NebulaButton(
                if (measuring) "Measuring… hands off" else "Measure resting centre",
                onClick = { if (!measuring) { measureNote = null; measuring = true } },
                style = ButtonStyle.Secondary, icon = Icons.Outlined.CenterFocusStrong,
            )
        }
        Spacer(Modifier.height(s.dp(6)))
        measureNote?.let { (t, l) -> Note(t, l) } ?: Hint("Leave both sticks untouched, then measure: about one second of readings sets each stick's centre and a deadzone just past its jitter.")
    }

    ToggleRow("Same shape for both sticks", "Deadzone, edge and anti-deadzone apply to both; each stick keeps its own measured centre.", linked) { on ->
        linked = on
        if (on) draft = draft.copy(right = draft.left.copy(centerX = draft.right.centerX, centerY = draft.right.centerY))
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val two = maxWidth >= 720.dp && hasRight
        val leftCard: @Composable (Modifier) -> Unit = { m ->
            StickEditor(if (linked && hasRight) "Both sticks (left shown)" else "Left stick", pad.axis(d.layout.leftX), pad.axis(d.layout.leftY), draft.left, m) { v ->
                draft = draft.copy(left = v, right = if (linked) v.copy(centerX = draft.right.centerX, centerY = draft.right.centerY) else draft.right)
            }
        }
        val rightCard: @Composable (Modifier) -> Unit = { m ->
            StickEditor("Right stick", pad.axis(d.layout.rightX), pad.axis(d.layout.rightY), draft.right, m, editable = !linked) { v -> draft = draft.copy(right = v) }
        }
        if (two) {
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(14))) {
                leftCard(Modifier.weight(1f))
                rightCard(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = gap) {
                leftCard(Modifier)
                if (hasRight) rightCard(Modifier)
            }
        }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        NebulaButton(if (dirty) "Save for this controller" else "Saved", onClick = { StickCalibrations.save(draft.copy(name = d.name)) }, icon = Icons.Outlined.Save)
        NebulaButton(
            "Reset to default",
            onClick = {
                StickCalibrations.remove(d.descriptor)
                draft = StickCalibration(d.descriptor, d.name, StickShape(deadzone = base), StickShape(deadzone = base))
                measureNote = null
            },
            style = ButtonStyle.Secondary, icon = Icons.Outlined.RestartAlt,
        )
    }
}

@Composable
private fun StickEditor(title: String, x: Float, y: Float, shape: StickShape, modifier: Modifier, editable: Boolean = true, onChange: (StickShape) -> Unit) {
    val s = Nebula.scale
    val (ox, oy) = shape.apply(x, y)
    DiagCard(modifier) {
        SectionTitle(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val desc = "$title: raw %.2f, %.2f; sent %.2f, %.2f".format(x, y, ox, oy)
            Canvas(Modifier.size(s.dp(150)).semantics { contentDescription = desc }) {
                val r = size.minDimension / 2 - 2.dp.toPx()
                stick(Offset(size.width / 2, size.height / 2), r, x, y, shape, clicked = false, present = true)
            }
            Spacer(Modifier.width(s.dp(16)))
            Column(verticalArrangement = Arrangement.spacedBy(s.dp(4))) {
                Readout("Raw", x, y)
                Readout("Sent", ox, oy)
                Readout("Centre", shape.centerX, shape.centerY)
                Hint("Faint dot: the stick. White dot: what the PC gets. Amber ring: deadzone. Dashed ring: full push.")
            }
        }
        Spacer(Modifier.height(s.dp(14)))
        if (!editable) {
            Hint("Shaped like the left stick. Turn off \"Same shape for both sticks\" to tune it separately.")
            return@DiagCard
        }
        Column(Modifier.widthIn(max = s.dp(520)), verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
            io.github.f_e_n_y_x.nebula.ui.screens.Setting("Deadzone", "Movement inside this reads as centred. Raise it if the stick drifts.") {
                SliderField("$title deadzone", pct(shape.deadzone), { onChange(shape.copy(deadzone = it / 100f).sanitized()) }, 0f..50f, 1f, "%")
            }
            io.github.f_e_n_y_x.nebula.ui.screens.Setting("Outer edge", "How far counts as a full push. Lower it if the stick never reaches 100 %.") {
                SliderField("$title outer edge", pct(shape.outer), { onChange(shape.copy(outer = it / 100f).sanitized()) }, 50f..100f, 1f, "%")
            }
            io.github.f_e_n_y_x.nebula.ui.screens.Setting("Anti-deadzone", "The smallest push the game sees. Raise it when a game adds its own deadzone and small moves do nothing.") {
                SliderField("$title anti-deadzone", pct(shape.antiDeadzone), { onChange(shape.copy(antiDeadzone = it / 100f).sanitized()) }, 0f..50f, 1f, "%")
            }
        }
    }
}

@Composable
private fun Readout(label: String, x: Float, y: Float) {
    Row {
        Text(label, style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.width(Nebula.scale.dp(52)))
        Text("%+.3f  %+.3f".format(x, y), style = Nebula.type.mono, color = NebulaColors.text)
    }
}

private fun pct(v: Float) = (v * 100f).roundToInt().toFloat()

private const val MEASURE_MS = 1_200L
