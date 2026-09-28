package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.hypot
import kotlin.math.roundToInt

/** State of the on-screen pad (player 1), sent whole on every change. */
private class OscState(private val remote: () -> RemoteInput?) {
    var buttons = 0
    var lt = 0
    var rt = 0
    var lx = 0
    var ly = 0
    var rx = 0
    var ry = 0
    private var announced = false

    fun send() {
        val o = remote() ?: return
        if (!announced) {
            o.gamepadArrived(0, 1, MoonBridge.LI_CTYPE_XBOX, GamepadMapper.SUPPORTED, MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toShort())
            announced = true
        }
        o.gamepad(0, 1, buttons, lt, rt, lx, ly, rx, ry)
    }

    fun set(flag: Int, down: Boolean) {
        buttons = if (down) buttons or flag else buttons and flag.inv()
        send()
    }

    /** Lets go of everything; a pad the host never saw isn't announced just to be released. */
    fun release() {
        buttons = 0; lt = 0; rt = 0; lx = 0; ly = 0; rx = 0; ry = 0
        if (announced) send()
    }
}

/**
 * V+'s on-screen gamepad in Nebula's style: sticks, D-pad, face buttons, shoulders, triggers,
 * Select/Start (and Guide). Honours opacity, "only L3/R3" and "show Guide" from Settings.
 */
@Composable
fun OnScreenControls(remote: () -> RemoteInput?, opacity: Int, l3r3Only: Boolean, showGuide: Boolean) {
    val state = remember { OscState(remote) }
    DisposableEffect(Unit) { onDispose { state.release() } }
    val s = Nebula.scale
    val portrait = !Nebula.form.isLandscape
    val a = (opacity.coerceIn(10, 100) / 100f)
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(s.dp(18)).alpha(a)) {
        // Shoulders and triggers.
        Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            OscTrigger("LT") { v -> state.lt = v; state.send() }
            OscButton("LB", ControllerPacket.LB_FLAG, state, wide = true)
        }
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            OscButton("RB", ControllerPacket.RB_FLAG, state, wide = true)
            OscTrigger("RT") { v -> state.rt = v; state.send() }
        }
        // Left: stick with the D-pad above it.
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
            DPad(state)
            OscStick(if (portrait) 104.dp else 124.dp, onClick = ControllerPacket.LS_CLK_FLAG.takeIf { !l3r3Only }, state) { x, y -> state.lx = x; state.ly = y; state.send() }
        }
        // Right: face buttons with the right stick beside them.
        Row(Modifier.align(Alignment.BottomEnd), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(s.dp(14))) {
            OscStick(if (portrait) 96.dp else 112.dp, onClick = ControllerPacket.RS_CLK_FLAG.takeIf { !l3r3Only }, state) { x, y -> state.rx = x; state.ry = y; state.send() }
            FaceButtons(state)
        }
        Row(Modifier.align(Alignment.BottomCenter), horizontalArrangement = Arrangement.spacedBy(s.dp(10))) {
            OscButton("Select", ControllerPacket.BACK_FLAG, state, wide = true)
            if (showGuide) OscButton("⌂", ControllerPacket.SPECIAL_BUTTON_FLAG, state)
            OscButton("Start", ControllerPacket.PLAY_FLAG, state, wide = true)
            if (l3r3Only) {
                OscButton("L3", ControllerPacket.LS_CLK_FLAG, state)
                OscButton("R3", ControllerPacket.RS_CLK_FLAG, state)
            }
        }
    }
}

@Composable
private fun FaceButtons(state: OscState) {
    val s = Nebula.scale
    val gap = s.dp(46)
    Box(Modifier.size(s.dp(150))) {
        OscButton("Y", ControllerPacket.Y_FLAG, state, Modifier.align(Alignment.TopCenter), color = Color(0xFFE8C547))
        OscButton("X", ControllerPacket.X_FLAG, state, Modifier.align(Alignment.CenterStart), color = Color(0xFF4F8DF7))
        OscButton("B", ControllerPacket.B_FLAG, state, Modifier.align(Alignment.CenterEnd), color = Color(0xFFE5534B))
        OscButton("A", ControllerPacket.A_FLAG, state, Modifier.align(Alignment.BottomCenter), color = Color(0xFF4CC38A))
        Box(Modifier.size(gap).align(Alignment.Center))
    }
}

@Composable
private fun DPad(state: OscState) {
    val s = Nebula.scale
    Box(Modifier.size(s.dp(132))) {
        OscButton("▲", ControllerPacket.UP_FLAG, state, Modifier.align(Alignment.TopCenter))
        OscButton("◀", ControllerPacket.LEFT_FLAG, state, Modifier.align(Alignment.CenterStart))
        OscButton("▶", ControllerPacket.RIGHT_FLAG, state, Modifier.align(Alignment.CenterEnd))
        OscButton("▼", ControllerPacket.DOWN_FLAG, state, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun OscButton(label: String, flag: Int, state: OscState, modifier: Modifier = Modifier, wide: Boolean = false, color: Color = NebulaColors.text) {
    val s = Nebula.scale
    var down by remember { mutableStateOf(false) }
    val shape = if (wide) RoundedCornerShape(50) else CircleShape
    Box(
        modifier
            .size(if (wide) s.dp(64) else s.dp(46), s.dp(46))
            .background(if (down) NebulaColors.accent else Color(0x8C111113), shape)
            .border(1.dp, if (down) NebulaColors.accentText else Color(0x66FFFFFF), shape)
            .pointerInput(flag) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    down = true
                    state.set(flag, true)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    down = false
                    state.set(flag, false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Nebula.type.label, color = if (down) Color.White else color)
    }
}

@Composable
private fun OscTrigger(label: String, onValue: (Int) -> Unit) {
    val s = Nebula.scale
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(s.dp(12))
    Box(
        Modifier.size(s.dp(64), s.dp(40))
            .background(if (down) NebulaColors.accent else Color(0x8C111113), shape)
            .border(1.dp, Color(0x66FFFFFF), shape)
            .pointerInput(label) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    down = true
                    onValue(255)
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { it.consume() }
                    } while (e.changes.any { it.pressed })
                    down = false
                    onValue(0)
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = Nebula.type.label, color = NebulaColors.text) }
}

/** An analog stick: drag inside the ring; a quick tap clicks it (L3/R3) when [onClick] is set. */
@Composable
private fun OscStick(size: Dp, onClick: Int?, state: OscState, onMove: (Int, Int) -> Unit) {
    val s = Nebula.scale
    var knob by remember { mutableStateOf(Offset.Zero) }
    val sizePx = with(androidx.compose.ui.platform.LocalDensity.current) { s.dp(size.value.toInt()).toPx() }
    val radius = sizePx / 2
    Box(
        Modifier.size(s.dp(size.value.toInt())).background(Color(0x66111113), CircleShape).border(1.dp, Color(0x66FFFFFF), CircleShape)
            .pointerInput(onClick) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val start = System.currentTimeMillis()
                    var travelled = 0f
                    var pos = first.position - Offset(radius, radius)
                    fun apply() {
                        val d = hypot(pos.x, pos.y)
                        val clamped = if (d > radius) pos * (radius / d) else pos
                        knob = clamped
                        onMove((clamped.x / radius * 32766).roundToInt(), (-clamped.y / radius * 32766).roundToInt())
                    }
                    apply()
                    do {
                        val e = awaitPointerEvent()
                        e.changes.forEach { c ->
                            travelled += c.positionChange().getDistance()
                            pos = c.position - Offset(radius, radius)
                            c.consume()
                        }
                        apply()
                    } while (e.changes.any { it.pressed })
                    knob = Offset.Zero
                    onMove(0, 0)
                    if (onClick != null && System.currentTimeMillis() - start < 200 && travelled < radius * 0.25f) {
                        state.set(onClick, true)
                        state.set(onClick, false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .size(s.dp((size.value * 0.42f).toInt())).background(Color(0xCCEDEDEF), CircleShape),
        )
    }
}
