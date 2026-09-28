package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.ElementShape
import io.github.f_e_n_y_x.nebula.controls.StickOutput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.min

/** Live visual state of one element; read in the draw phase so a stick drag only redraws. */
class ElementLook {
    val pressed = mutableStateOf(false)
    /** Stick knob offset as a share of the radius (−1..1), y down. */
    val knob = mutableStateOf(Offset.Zero)
    /** Floating stick centre inside the element, px; Unspecified = the middle. */
    val origin = mutableStateOf(Offset.Unspecified)
    /** Pressed D-pad / key-stick directions (0 up, 1 down, 2 left, 3 right). */
    val dirs = mutableStateOf(emptySet<Int>())
}

internal val Fill = Color(0x8C111113)
internal val Edge = Color(0x66FFFFFF)
internal val KnobColor = Color(0xCCEDEDEF)

/**
 * One element's face: glass fill, hairline edge, label. Pressed turns it accent; a latched
 * toggle keeps an accent ring. The same drawing is used live and in the editor.
 */
@Composable
fun ElementFace(e: ControlElement, look: ElementLook, latched: State<Boolean>, modifier: Modifier = Modifier) {
    val t = Nebula.type
    Box(
        modifier.fillMaxSize().drawBehind {
            val down = look.pressed.value
            when (e.kind) {
                ElementKind.STICK -> drawStick(e, look)
                ElementKind.DPAD -> drawDpad(look.dirs.value)
                ElementKind.TOUCHPAD -> drawTouchpad(down)
                ElementKind.ZONE -> drawZone(e, look)
                else -> drawButton(e.shape, down, latched.value)
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val showLabel = e.kind != ElementKind.DPAD && !(e.kind == ElementKind.STICK && e.floating)
        if (showLabel && e.label.isNotBlank()) {
            val color = e.tint?.let { Color(it.toInt()) } ?: NebulaColors.text
            Text(
                e.label,
                style = if (e.kind == ElementKind.TOUCHPAD || e.label.length > 3) t.label else t.bodyStrong,
                color = if (e.kind == ElementKind.STICK) NebulaColors.textSecondary else color,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = if (e.kind == ElementKind.TOUCHPAD) Modifier.align(Alignment.TopCenter).padding(top = 8.dp) else Modifier,
            )
        }
        if (e.kind == ElementKind.ZONE) {
            val what = when (e.zone) {
                io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_MOUSE -> "Camera → mouse"
                io.github.f_e_n_y_x.nebula.controls.ZoneType.CAMERA_STICK -> "Camera → ${if (e.stick == StickOutput.LEFT) "left" else "right"} stick"
                io.github.f_e_n_y_x.nebula.controls.ZoneType.FLOATING_STICK -> "Floating ${if (e.stick == StickOutput.LEFT) "left" else "right"} stick"
            } + if (e.keepWithController) " · with controller" else ""
            Text(
                what, style = t.label, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(top = 40.dp),
            )
        }
        if (e.kind == ElementKind.COMBO || e.kind == ElementKind.MACRO) {
            Text(
                if (e.kind == ElementKind.COMBO) "combo" else "macro",
                style = t.label.copy(fontSize = t.label.fontSize * 0.75f), color = NebulaColors.textMuted,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun DrawScope.corner(shape: ElementShape): CornerRadius = when (shape) {
    ElementShape.ROUND, ElementShape.PILL -> CornerRadius(min(size.width, size.height) / 2)
    ElementShape.SQUARE -> CornerRadius(12.dp.toPx())
}

private fun DrawScope.drawButton(shape: ElementShape, down: Boolean, latched: Boolean) {
    val stroke = 1.dp.toPx()
    val r = corner(shape)
    if (shape == ElementShape.ROUND && size.width == size.height) {
        drawCircle(if (down) NebulaColors.accent else if (latched) NebulaColors.accentTint else Fill)
        drawCircle(if (down || latched) NebulaColors.accentText else Edge, style = Stroke(if (latched) stroke * 2 else stroke))
    } else {
        drawRoundRect(if (down) NebulaColors.accent else if (latched) NebulaColors.accentTint else Fill, cornerRadius = r)
        drawRoundRect(
            if (down || latched) NebulaColors.accentText else Edge, cornerRadius = r,
            style = Stroke(if (latched) stroke * 2 else stroke),
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke),
        )
    }
}

private fun DrawScope.drawStick(e: ControlElement, look: ElementLook) {
    val radius = min(size.width, size.height) / 2
    val stroke = 1.dp.toPx()
    val o = look.origin.value.takeIf { it.isSpecified() } ?: center
    if (e.floating) {
        // The whole area catches the thumb; show its bounds faintly and the stick only while held.
        drawRoundRect(Color(0x14FFFFFF), cornerRadius = CornerRadius(16.dp.toPx()))
        drawRoundRect(Color(0x33FFFFFF), cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        if (!look.pressed.value) return
    }
    val ring = if (e.floating) min(radius, 60.dp.toPx()) else radius
    drawCircle(Color(0x66111113), ring, o)
    drawCircle(if (look.pressed.value) NebulaColors.accentText else Edge, ring - stroke / 2, o, style = Stroke(stroke))
    if (e.stick == StickOutput.KEYS) {
        // Four ticks show it sends keys, lit per pressed direction.
        val dirs = look.dirs.value
        listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEachIndexed { i, d ->
            drawCircle(if (i in dirs) NebulaColors.accentText else Color(0x55FFFFFF), 3.dp.toPx(), o + d * (ring * 0.78f))
        }
    }
    val k = look.knob.value
    drawCircle(if (look.pressed.value) NebulaColors.text else KnobColor, ring * 0.42f, o + Offset(k.x * ring * 0.58f, k.y * ring * 0.58f))
}

private fun DrawScope.drawDpad(dirs: Set<Int>) {
    val s = min(size.width, size.height)
    val arm = s / 3
    val stroke = 1.dp.toPx()
    val cx = size.width / 2
    val cy = size.height / 2
    val r = CornerRadius(arm * 0.28f)
    // Up, down, left, right arms.
    val arms = listOf(
        Offset(cx - arm / 2, cy - s / 2) to Size(arm, arm * 1.08f),
        Offset(cx - arm / 2, cy + s / 2 - arm * 1.08f) to Size(arm, arm * 1.08f),
        Offset(cx - s / 2, cy - arm / 2) to Size(arm * 1.08f, arm),
        Offset(cx + s / 2 - arm * 1.08f, cy - arm / 2) to Size(arm * 1.08f, arm),
    )
    // Only the four arms: no centre tile or backdrop (the touch area is unchanged).
    arms.forEachIndexed { i, (tl, sz) ->
        drawRoundRect(if (i in dirs) NebulaColors.accent else Fill, tl, sz, r)
        drawRoundRect(if (i in dirs) NebulaColors.accentText else Edge, tl, sz, r, style = Stroke(stroke))
        // Chevron.
        val c = tl + Offset(sz.width / 2, sz.height / 2)
        val a = arm * 0.16f
        val p = Path().apply {
            when (i) {
                0 -> { moveTo(c.x - a, c.y + a / 2); lineTo(c.x, c.y - a / 2); lineTo(c.x + a, c.y + a / 2) }
                1 -> { moveTo(c.x - a, c.y - a / 2); lineTo(c.x, c.y + a / 2); lineTo(c.x + a, c.y - a / 2) }
                2 -> { moveTo(c.x + a / 2, c.y - a); lineTo(c.x - a / 2, c.y); lineTo(c.x + a / 2, c.y + a) }
                else -> { moveTo(c.x - a / 2, c.y - a); lineTo(c.x + a / 2, c.y); lineTo(c.x - a / 2, c.y + a) }
            }
        }
        drawPath(p, if (i in dirs) Color.White else NebulaColors.textSecondary, style = Stroke(2.dp.toPx()))
    }
}

private fun DrawScope.drawZone(e: ControlElement, look: ElementLook) {
    val r = CornerRadius(18.dp.toPx())
    val down = look.pressed.value
    drawRoundRect(if (down) Color(0x1FB7A2FF) else Color(0x14FFFFFF), cornerRadius = r)
    drawRoundRect(
        if (down) NebulaColors.accentText else Color(0x59FFFFFF), cornerRadius = r,
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
    )
    val o = look.origin.value.takeIf { it.isSpecified() } ?: return
    when (e.zone) {
        io.github.f_e_n_y_x.nebula.controls.ZoneType.FLOATING_STICK -> if (e.showRing) {
            val ring = FLOAT_RING.toPx()
            drawCircle(Color(0x66111113), ring, o)
            drawCircle(NebulaColors.accentText, ring, o, style = Stroke(1.5.dp.toPx()))
            val k = look.knob.value
            drawCircle(KnobColor, ring * 0.42f, o + Offset(k.x * ring * 0.58f, k.y * ring * 0.58f))
        }
        else -> {
            // Where the thumb is, with a short tail showing the stick push.
            val k = look.knob.value
            drawCircle(Color(0x33B7A2FF), 26.dp.toPx(), o)
            drawLine(NebulaColors.accentText, o, o + Offset(k.x, k.y) * 40.dp.toPx(), 3.dp.toPx())
        }
    }
}

private fun DrawScope.drawTouchpad(down: Boolean) {
    val r = CornerRadius(16.dp.toPx())
    drawRoundRect(if (down) Color(0x401D1838) else Color(0x33111113), cornerRadius = r)
    drawRoundRect(
        if (down) NebulaColors.accentText else Edge, cornerRadius = r,
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
    )
}

private fun Offset.isSpecified() = this != Offset.Unspecified && !x.isNaN()
