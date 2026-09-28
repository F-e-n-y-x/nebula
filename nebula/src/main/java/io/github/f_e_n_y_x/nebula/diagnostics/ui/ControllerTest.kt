package io.github.f_e_n_y_x.nebula.diagnostics.ui

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.diagnostics.ControllerInfo
import io.github.f_e_n_y_x.nebula.diagnostics.ControllerTester
import io.github.f_e_n_y_x.nebula.diagnostics.Level
import io.github.f_e_n_y_x.nebula.diagnostics.PadLive
import io.github.f_e_n_y_x.nebula.diagnostics.StickCalibration
import io.github.f_e_n_y_x.nebula.diagnostics.StickCalibrations
import io.github.f_e_n_y_x.nebula.diagnostics.StickShape
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.theme.GeistMono
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.abs

/** The deadzone streams use for pads without a calibration (Settings → Gamepads). */
internal fun globalDeadzone(context: Context): Float =
    ((LegacyPrefs(context).prefs.all["seekbar_deadzone"] as? Int) ?: 7).coerceIn(0, 50) / 100f

/** The shape a stream applies to [pad]'s stick: its calibration, else a plain radial deadzone. */
internal fun effectiveShape(cal: StickCalibration?, left: Boolean, deadzone: Float): StickShape =
    cal?.let { if (left) it.left else it.right } ?: StickShape(deadzone = deadzone)

/** Gamepad capture on/off, with the way out for gamepad-only users. */
@Composable
internal fun CaptureBanner(capture: Boolean, onCapture: (Boolean) -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Row(
        Modifier.fillMaxWidth().background(if (capture) NebulaColors.accentTint else NebulaColors.raised, shape).padding(s.dp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(if (capture) "Testing controllers" else "Controllers drive the menus", style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(
                if (capture) "Buttons and sticks show here instead of moving around the app. Press Start + Select together to give the controller back to the menus."
                else "Turn testing back on to see every button and axis.",
                style = Nebula.type.label, color = NebulaColors.textSecondary,
            )
        }
        Spacer(Modifier.width(s.dp(12)))
        NebulaButton(if (capture) "Stop testing" else "Test again", onClick = { onCapture(!capture) }, style = ButtonStyle.Secondary)
    }
}

@Composable
internal fun ControllerTestTab(tester: ControllerTester, capture: Boolean, onCapture: (Boolean) -> Unit, onCalibrate: (String) -> Unit) {
    val ctx = LocalContext.current
    val deadzone = remember { globalDeadzone(ctx) }
    val cals by StickCalibrations.all.collectAsState()
    Column(verticalArrangement = gap) {
        CaptureBanner(capture, onCapture)
        if (tester.pads.isEmpty()) {
            EmptyState(Icons.Outlined.SportsEsports, "No controller connected", "Connect one over USB or Bluetooth. It shows up here as soon as Android sees it, with every raw axis.")
        }
        tester.pads.forEach { pad ->
            PadCard(pad, cals[pad.device.descriptor], deadzone, onRumble = { tester.rumble(pad.device.id) }, onCalibrate = { onCalibrate(pad.device.descriptor) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PadCard(pad: PadLive, cal: StickCalibration?, deadzone: Float, onRumble: () -> Unit, onCalibrate: () -> Unit) {
    val s = Nebula.scale
    val d = pad.device
    DiagCard {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(d.name, style = Nebula.type.heading, color = NebulaColors.text)
                Text("Layout: " + ControllerInfo.describeLayout(d.layout), style = Nebula.type.label, color = NebulaColors.textSecondary)
            }
        }
        Spacer(Modifier.height(s.dp(10)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Pill("VID:PID ${d.ids}", background = NebulaColors.raised)
            Pill("id ${d.id}", background = NebulaColors.raised)
            if (d.isCompanion) Pill("Buttons-only node", color = NebulaColors.warning, background = NebulaColors.raised)
            d.battery?.let { Pill("Battery $it", background = NebulaColors.raised) }
            Pill(if (cal != null) "Calibrated" else "Default deadzone ${(deadzone * 100).toInt()} %", color = if (cal != null) NebulaColors.success else NebulaColors.textSecondary, background = NebulaColors.raised)
            Pill("${pad.rateHz} Hz · ${pad.events} events", background = NebulaColors.raised)
        }
        Spacer(Modifier.height(s.dp(14)))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 640.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(s.dp(18))) {
                    PadVisual(pad, cal, deadzone, Modifier.weight(1.1f))
                    Column(Modifier.weight(1f)) { RawAxes(pad) }
                }
            } else {
                Column(verticalArrangement = gap) {
                    PadVisual(pad, cal, deadzone, Modifier.fillMaxWidth())
                    RawAxes(pad)
                }
            }
        }
        Spacer(Modifier.height(s.dp(12)))
        Text(
            "Last button: " + (pad.lastKey?.let { ControllerInfo.keyName(it) } ?: "—") +
                if (pad.keys.isNotEmpty()) "   Held: " + pad.keys.joinToString(" ") { ControllerInfo.keyName(it) } else "",
            style = Nebula.type.mono, color = NebulaColors.textSecondary,
        )
        Spacer(Modifier.height(s.dp(12)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            if (d.hasVibrator) NebulaButton("Rumble", onClick = onRumble, style = ButtonStyle.Secondary, icon = Icons.Outlined.Vibration)
            if (d.traits.hasSticks) NebulaButton("Calibrate sticks", onClick = onCalibrate, style = ButtonStyle.Secondary, icon = Icons.Outlined.Tune)
        }
        Hint("Descriptor ${d.descriptor.take(16)}… · sources ${d.sources}")
    }
}

/** Every joystick axis the device declares, raw, with what the stream uses it for. */
@Composable
private fun RawAxes(pad: PadLive) {
    val s = Nebula.scale
    val l = pad.device.layout
    val roles = buildMap {
        if (l.leftX >= 0) put(l.leftX, "left X")
        if (l.leftY >= 0) put(l.leftY, "left Y")
        if (l.rightX >= 0) put(l.rightX, "right X")
        if (l.rightY >= 0) put(l.rightY, "right Y")
        if (l.leftTrigger >= 0) put(l.leftTrigger, "L2")
        if (l.rightTrigger >= 0) put(l.rightTrigger, "R2")
        put(MotionEvent.AXIS_HAT_X, "D-pad X")
        put(MotionEvent.AXIS_HAT_Y, "D-pad Y")
    }
    SectionTitle("Raw axes")
    if (pad.device.axes.isEmpty()) {
        Text("This node declares no axes (buttons only).", style = Nebula.type.secondary, color = NebulaColors.textSecondary)
        return
    }
    pad.device.axes.forEach { r ->
        val v = pad.axis(r.axis)
        val role = roles[r.axis]
        val stuck = role != null && pad.events > 60 && (pad.peaks[r.axis] ?: 0f) < 0.02f && r.axis != MotionEvent.AXIS_HAT_X && r.axis != MotionEvent.AXIS_HAT_Y
        Row(Modifier.fillMaxWidth().padding(vertical = s.dp(3)), verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, style = Nebula.type.mono, color = if (role != null) NebulaColors.text else NebulaColors.textMuted, modifier = Modifier.width(s.dp(92)), maxLines = 1)
            Text(role ?: "unused", style = Nebula.type.label, color = if (stuck) NebulaColors.warning else if (role != null) NebulaColors.accentText else NebulaColors.textMuted, modifier = Modifier.width(s.dp(62)), maxLines = 1)
            AxisBar(v, r.min, r.max, Modifier.weight(1f).height(s.dp(8)))
            Text("%+.3f".format(v), style = Nebula.type.mono, color = NebulaColors.text, modifier = Modifier.padding(start = s.dp(10)).width(s.dp(62)))
        }
        if (stuck) Note("${r.name} (${role}) hasn't moved. Push that stick or trigger all the way; if it stays at zero, Android isn't reporting this axis.", Level.WARN)
    }
}

@Composable
private fun AxisBar(v: Float, min: Float, max: Float, modifier: Modifier) {
    val centred = min < -0.5f
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(NebulaColors.raised, cornerRadius = r)
        if (centred) {
            val mid = size.width / 2
            val w = (v.coerceIn(-1f, 1f) * mid)
            drawRect(NebulaColors.accent, topLeft = Offset(if (w < 0) mid + w else mid, 0f), size = Size(abs(w), size.height))
            drawLine(NebulaColors.textMuted, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 1.dp.toPx())
        } else {
            val f = ((v - min) / (max - min).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
            drawRoundRect(NebulaColors.accent, size = Size(size.width * f, size.height), cornerRadius = r)
        }
    }
}

/**
 * A schematic pad: both sticks (outer ring, deadzone ring, raw position and what the host gets),
 * the triggers as fill bars, bumpers, D-pad, face buttons and the middle buttons.
 */
@Composable
private fun PadVisual(pad: PadLive, cal: StickCalibration?, deadzone: Float, modifier: Modifier) {
    val l = pad.device.layout
    val keys = pad.keys
    val tm = rememberTextMeasurer()
    val lx = pad.axis(l.leftX); val ly = pad.axis(l.leftY)
    val rx = pad.axis(l.rightX); val ry = pad.axis(l.rightY)
    val lt = triggerValue(pad, l.leftTrigger, l.triggersIdleNegative, KeyEvent.KEYCODE_BUTTON_L2)
    val rt = triggerValue(pad, l.rightTrigger, l.triggersIdleNegative, KeyEvent.KEYCODE_BUTTON_R2)
    val hx = pad.axis(MotionEvent.AXIS_HAT_X); val hy = pad.axis(MotionEvent.AXIS_HAT_Y)
    val left = effectiveShape(cal, true, deadzone)
    val right = effectiveShape(cal, false, deadzone)
    val desc = "Left stick %.2f, %.2f. Right stick %.2f, %.2f. Triggers %.0f%%, %.0f%%.".format(lx, ly, rx, ry, lt * 100, rt * 100)
    Canvas(modifier.widthIn(max = 520.dp).aspectRatio(1.7f).semantics { contentDescription = desc }) {
        val w = size.width
        val h = size.height
        val u = h / 10f
        // Body.
        drawRoundRect(NebulaColors.panel, topLeft = Offset(0f, u * 1.6f), size = Size(w, h - u * 1.6f), cornerRadius = CornerRadius(u * 2.4f))
        drawRoundRect(NebulaColors.border, topLeft = Offset(0f, u * 1.6f), size = Size(w, h - u * 1.6f), cornerRadius = CornerRadius(u * 2.4f), style = Stroke(1.dp.toPx()))
        // Triggers (fill = pressure) and bumpers.
        trigger(Offset(w * 0.08f, 0f), Size(w * 0.2f, u * 0.9f), lt, "L2", tm)
        trigger(Offset(w * 0.72f, 0f), Size(w * 0.2f, u * 0.9f), rt, "R2", tm)
        pill(Offset(w * 0.08f, u * 1.05f), Size(w * 0.2f, u * 0.75f), KeyEvent.KEYCODE_BUTTON_L1 in keys, "L1", tm)
        pill(Offset(w * 0.72f, u * 1.05f), Size(w * 0.2f, u * 0.75f), KeyEvent.KEYCODE_BUTTON_R1 in keys, "R1", tm)
        // Sticks.
        stick(Offset(w * 0.2f, h * 0.45f), u * 1.9f, lx, ly, left, KeyEvent.KEYCODE_BUTTON_THUMBL in keys, l.leftX >= 0)
        stick(Offset(w * 0.64f, h * 0.74f), u * 1.9f, rx, ry, right, KeyEvent.KEYCODE_BUTTON_THUMBR in keys, l.rightX >= 0)
        // D-pad (keys or hat).
        val dc = Offset(w * 0.36f, h * 0.74f)
        val ds = u * 0.62f
        dpadArm(dc, Offset(0f, -1f), ds, KeyEvent.KEYCODE_DPAD_UP in keys || hy < -0.5f)
        dpadArm(dc, Offset(0f, 1f), ds, KeyEvent.KEYCODE_DPAD_DOWN in keys || hy > 0.5f)
        dpadArm(dc, Offset(-1f, 0f), ds, KeyEvent.KEYCODE_DPAD_LEFT in keys || hx < -0.5f)
        dpadArm(dc, Offset(1f, 0f), ds, KeyEvent.KEYCODE_DPAD_RIGHT in keys || hx > 0.5f)
        // Face buttons.
        val fc = Offset(w * 0.8f, h * 0.45f)
        val fr = u * 0.62f
        val fd = u * 1.25f
        face(fc + Offset(0f, fd), fr, KeyEvent.KEYCODE_BUTTON_A in keys, "A", tm)
        face(fc + Offset(fd, 0f), fr, KeyEvent.KEYCODE_BUTTON_B in keys, "B", tm)
        face(fc + Offset(-fd, 0f), fr, KeyEvent.KEYCODE_BUTTON_X in keys, "X", tm)
        face(fc + Offset(0f, -fd), fr, KeyEvent.KEYCODE_BUTTON_Y in keys, "Y", tm)
        // Middle buttons.
        pill(Offset(w * 0.39f, h * 0.38f), Size(w * 0.08f, u * 0.6f), KeyEvent.KEYCODE_BUTTON_SELECT in keys || KeyEvent.KEYCODE_BACK in keys, "", tm)
        pill(Offset(w * 0.53f, h * 0.38f), Size(w * 0.08f, u * 0.6f), KeyEvent.KEYCODE_BUTTON_START in keys, "", tm)
        face(Offset(w * 0.5f, h * 0.56f), u * 0.45f, KeyEvent.KEYCODE_BUTTON_MODE in keys, "", tm)
    }
}

private fun triggerValue(pad: PadLive, axis: Int, idleNegative: Boolean, key: Int): Float {
    if (axis < 0) return if (key in pad.keys) 1f else 0f
    val v = pad.axis(axis)
    return (if (idleNegative) (v + 1f) / 2f else v).coerceIn(0f, 1f)
}

private val labelStyle = TextStyle(fontFamily = GeistMono, color = NebulaColors.textSecondary)

private fun DrawScope.label(tm: TextMeasurer, text: String, center: Offset, on: Boolean) {
    if (text.isEmpty()) return
    val style = labelStyle.copy(fontSize = (size.height / 18f).toSp(), color = if (on) Color.White else NebulaColors.textSecondary)
    val m = tm.measure(text, style)
    drawText(m, topLeft = center - Offset(m.size.width / 2f, m.size.height / 2f))
}

private fun DrawScope.trigger(tl: Offset, sz: Size, v: Float, name: String, tm: TextMeasurer) {
    val r = CornerRadius(sz.height / 2)
    drawRoundRect(NebulaColors.raised, tl, sz, r)
    if (v > 0.001f) drawRoundRect(NebulaColors.accent, tl, Size(sz.width * v, sz.height), r)
    label(tm, "$name ${(v * 100).toInt()}", tl + Offset(sz.width / 2, sz.height / 2), v > 0.5f)
}

private fun DrawScope.pill(tl: Offset, sz: Size, on: Boolean, name: String, tm: TextMeasurer) {
    drawRoundRect(if (on) NebulaColors.accent else NebulaColors.raised, tl, sz, CornerRadius(sz.height / 2))
    label(tm, name, tl + Offset(sz.width / 2, sz.height / 2), on)
}

private fun DrawScope.face(c: Offset, r: Float, on: Boolean, name: String, tm: TextMeasurer) {
    drawCircle(if (on) NebulaColors.accent else NebulaColors.raised, r, c)
    drawCircle(NebulaColors.controlBorder, r, c, style = Stroke(1.dp.toPx()))
    label(tm, name, c, on)
}

private fun DrawScope.dpadArm(c: Offset, dir: Offset, s: Float, on: Boolean) {
    val center = c + dir * (s * 1.05f)
    drawRoundRect(if (on) NebulaColors.accent else NebulaColors.raised, center - Offset(s / 2, s / 2), Size(s, s), CornerRadius(s * 0.2f))
}

/** Outer ring = full push, dashed ring = deadzone, faint dot = raw, bright dot = what the host gets. */
internal fun DrawScope.stick(c: Offset, r: Float, x: Float, y: Float, shape: StickShape, clicked: Boolean, present: Boolean) {
    drawCircle(NebulaColors.bg, r, c)
    drawCircle(if (clicked) NebulaColors.accentText else NebulaColors.controlBorder, r, c, style = Stroke(if (clicked) 2.dp.toPx() else 1.dp.toPx()))
    if (!present) return
    drawLine(NebulaColors.border, c - Offset(r, 0f), c + Offset(r, 0f), 1.dp.toPx())
    drawLine(NebulaColors.border, c - Offset(0f, r), c + Offset(0f, r), 1.dp.toPx())
    val centre = c + Offset(shape.centerX * r, shape.centerY * r)
    if (shape.outer < 0.999f) drawCircle(NebulaColors.textMuted, r * shape.outer, centre, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
    if (shape.deadzone > 0f) {
        drawCircle(NebulaColors.warning.copy(alpha = 0.16f), r * shape.deadzone, centre)
        drawCircle(NebulaColors.warning, r * shape.deadzone, centre, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
    }
    val raw = c + Offset(x.coerceIn(-1f, 1f) * r, y.coerceIn(-1f, 1f) * r)
    val (ox, oy) = shape.apply(x, y)
    val out = c + Offset(ox * r, oy * r)
    drawLine(NebulaColors.accent.copy(alpha = 0.6f), c, out, 2.dp.toPx())
    drawCircle(NebulaColors.accentText.copy(alpha = 0.45f), r * 0.1f, raw)
    drawCircle(Color.White, r * 0.085f, out)
}
