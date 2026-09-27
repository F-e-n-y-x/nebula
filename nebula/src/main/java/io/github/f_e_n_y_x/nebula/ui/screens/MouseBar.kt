package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.fenyx.nebula.engine.MouseButton
import kotlin.math.roundToInt

/**
 * On-screen mouse bar: hold-to-press Left / Middle / Right, a scroll strip, drag lock (left button
 * stays down until tapped again) and the keyboard. Drag the handle to move it; × hides it.
 */
@Composable
fun MouseBar(remote: () -> RemoteInput?, onKeyboard: () -> Unit, onHide: () -> Unit, atTop: Boolean = false, topInset: Int = 12) {
    val s = Nebula.scale
    var x by rememberSaveable { mutableFloatStateOf(0f) }
    var y by rememberSaveable { mutableFloatStateOf(0f) }
    var locked by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { if (locked) remote()?.button(MouseButton.LEFT, false) } }
    // Above the on-screen controls' Select/Start when they're shown, otherwise near the bottom.
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(top = s.dp(topInset), bottom = s.dp(64))) {
        val shape = RoundedCornerShape(s.dp(16))
        Row(
            Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter).offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .background(Color(0xD90E0E10), shape).border(1.dp, NebulaColors.border, shape).padding(s.dp(6)),
            horizontalArrangement = Arrangement.spacedBy(s.dp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(s.dp(28), s.dp(44)).pointerInput(Unit) {
                    detectDragGestures { c, d -> c.consume(); x += d.x; y += d.y }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.DragIndicator, "Move the mouse bar", tint = NebulaColors.textMuted) }
            HoldKey("Left") { down -> remote()?.button(MouseButton.LEFT, down) }
            HoldKey("Middle") { down -> remote()?.button(MouseButton.MIDDLE, down) }
            HoldKey("Right") { down -> remote()?.button(MouseButton.RIGHT, down) }
            ScrollStrip(remote)
            BarIcon(if (locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (locked) "Release drag lock" else "Drag lock", active = locked) {
                locked = !locked
                remote()?.button(MouseButton.LEFT, locked)
            }
            BarIcon(Icons.Outlined.Keyboard, "Keyboard", onClick = onKeyboard)
            BarIcon(Icons.Rounded.Close, "Hide the mouse bar", onClick = onHide)
        }
    }
}

@Composable
private fun HoldKey(label: String, onPress: (Boolean) -> Unit) {
    val s = Nebula.scale
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(10))
    Box(
        Modifier.size(s.dp(58), s.dp(44))
            .background(if (down) NebulaColors.accent else NebulaColors.surface, shape)
            .border(1.dp, if (down) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    down = true
                    onPress(true)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    down = false
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Nebula.type.label, color = if (down) Color.White else NebulaColors.text) }
}

/** Drag up/down (or sideways) on the strip to scroll the PC; 40 px ≈ one wheel notch. */
@Composable
private fun ScrollStrip(remote: () -> RemoteInput?) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    var acc by remember { mutableFloatStateOf(0f) }
    var accH by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.size(s.dp(40), s.dp(44)).background(NebulaColors.surface, shape).border(1.dp, NebulaColors.controlBorder, shape)
            .pointerInput(Unit) {
                detectDragGestures { c, d ->
                    c.consume()
                    acc += -d.y * 3f
                    accH += d.x * 3f
                    val v = acc.toInt()
                    val h = accH.toInt()
                    if (v != 0) { remote()?.scroll(v); acc -= v }
                    if (h != 0) { remote()?.scrollHorizontal(h); accH -= h }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text("⇕", style = Nebula.type.bodyStrong, color = NebulaColors.text) }
}

@Composable
private fun BarIcon(icon: ImageVector, description: String, active: Boolean = false, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Box(
        Modifier.size(s.dp(44)).background(if (active) NebulaColors.accent else NebulaColors.surface, shape)
            .border(1.dp, if (active) NebulaColors.accentText else NebulaColors.controlBorder, shape)
            .pointerInput(description) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = if (active) Color.White else NebulaColors.text, modifier = Modifier.size(s.dp(20))) }
}
