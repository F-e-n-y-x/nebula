package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.CameraMouse
import io.github.f_e_n_y_x.nebula.controls.CameraStick
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.Side
import io.github.f_e_n_y_x.nebula.controls.StickOutput
import io.github.f_e_n_y_x.nebula.controls.ZoneType
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** The area controls are laid out in, identical live and in the editor so layouts match. */
fun Modifier.controlsArea(): Modifier = this.fillMaxSize().displayCutoutPadding().padding(12.dp)

/** Places [e] (centre as a share of the area, size in dp) inside an area of [areaW] × [areaH] px. */
internal fun Modifier.elementBounds(e: ControlElement, areaW: Int, areaH: Int): Modifier {
    return this
        .offset {
            val sz = e.sizePx(areaW, areaH, this)
            IntOffset((e.x * areaW - sz.width / 2f).roundToInt(), (e.y * areaH - sz.height / 2f).roundToInt())
        }
        .layout { m, _ ->
            val sz = e.sizePx(areaW, areaH, this)
            val p = m.measure(Constraints.fixed(sz.width.coerceAtLeast(1), sz.height.coerceAtLeast(1)))
            layout(p.width, p.height) { p.place(0, 0) }
        }
}

/** Pixel size: zones are a share of the area, everything else dp. */
internal fun ControlElement.sizePx(areaW: Int, areaH: Int, density: Density): IntSize =
    if (areaSized) IntSize((width * areaW).roundToInt(), (height * areaH).roundToInt())
    else with(density) { IntSize(width.dp.roundToPx(), height.dp.roundToPx()) }

/**
 * The live on-screen controls for one layout. Each element is its own small touch target, so
 * touches between elements fall through to the stream's touch layer underneath, and a finger on
 * each element works at once (stick + buttons). Movement only redraws, it doesn't recompose.
 */
@Composable
fun ControlsOverlay(elements: List<ControlElement>, input: ControlsInput, opacity: Float, modifier: Modifier = Modifier) {
    val latched by input.latched.collectAsState()
    BoxWithConstraints(modifier.controlsArea()) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        // Zones first, so a button on top of a zone still gets its touches.
        elements.sortedBy { it.kind != ElementKind.ZONE }.forEach { e ->
            key(e.id) {
                val look = remember { ElementLook() }
                val on = remember(e.id) { derivedStateOf { e.id in latched } }
                LiveElement(e, look, on, input, w, h, opacity)
            }
        }
    }
}

@Composable
private fun LiveElement(e: ControlElement, look: ElementLook, latched: State<Boolean>, input: ControlsInput, w: Int, h: Int, opacity: Float) {
    Box(
        Modifier
            .elementBounds(e, w, h)
            .testTag("osc:${e.id}")
            .graphicsLayer { alpha = (opacity * e.opacity).coerceIn(0.05f, 1f) }
            .pointerInput(e) { handle(e, look, input) },
    ) {
        ElementFace(e, look, latched)
    }
}

private const val TAP_MS = 220L

private suspend fun PointerInputScope.handle(e: ControlElement, look: ElementLook, input: ControlsInput) {
    when (e.kind) {
        ElementKind.BUTTON, ElementKind.TRIGGER, ElementKind.COMBO, ElementKind.MACRO -> awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            look.pressed.value = true
            input.elementDown(e)
            try {
                do {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                } while (ev.changes.any { it.pressed })
            } finally {
                look.pressed.value = false
                input.elementUp(e)
            }
        }
        ElementKind.DPAD -> awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            first.consume()
            val id = first.id
            fun dirsAt(p: Offset): Set<Int> {
                val r = min(size.width, size.height) / 2f
                val nx = (p.x - size.width / 2f) / r
                val ny = (p.y - size.height / 2f) / r
                return buildSet {
                    if (ny < -DPAD_T) add(0)
                    if (ny > DPAD_T) add(1)
                    if (nx < -DPAD_T) add(2)
                    if (nx > DPAD_T) add(3)
                }
            }
            fun set(d: Set<Int>) { if (look.dirs.value != d) { look.dirs.value = d; input.dpad(e, d) } }
            set(dirsAt(first.position))
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    set(dirsAt(c.position))
                }
            } finally {
                set(emptySet())
            }
        }
        ElementKind.STICK -> awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            first.consume()
            val id = first.id
            val start = System.currentTimeMillis()
            val full = min(size.width, size.height) / 2f
            val radius = if (e.floating) min(full, 60.dp.toPx()) else full
            val origin = if (e.floating) first.position else Offset(size.width / 2f, size.height / 2f)
            look.origin.value = if (e.floating) origin else Offset.Unspecified
            look.pressed.value = true
            var travelled = 0f
            fun apply(p: Offset) {
                var d = (p - origin) / radius
                val m = hypot(d.x, d.y)
                if (m > 1f) d /= m
                look.knob.value = d
                input.stick(e, d.x, -d.y)
            }
            apply(first.position)
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    travelled += c.positionChangeIgnoreConsumed().getDistance()
                    apply(c.position)
                }
            } finally {
                look.knob.value = Offset.Zero
                look.pressed.value = false
                look.origin.value = Offset.Unspecified
                input.stick(e, 0f, 0f)
            }
            if (System.currentTimeMillis() - start < TAP_MS && travelled < radius * 0.25f) input.click(e)
        }
        ElementKind.ZONE -> handleZone(e, look, input)
        ElementKind.TOUCHPAD -> awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            first.consume()
            val id = first.id
            val start = System.currentTimeMillis()
            var travelled = 0f
            look.pressed.value = true
            try {
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    val d = c.positionChangeIgnoreConsumed()
                    travelled += d.getDistance()
                    input.touchpadMove(e, d.x, d.y)
                }
            } finally {
                look.pressed.value = false
            }
            if (System.currentTimeMillis() - start < TAP_MS && travelled < 12.dp.toPx()) input.click(e)
        }
    }
}

private const val DPAD_T = 0.35f

/** Radius of a floating joystick spawned in a zone. */
internal val FLOAT_RING = 64.dp

private suspend fun PointerInputScope.handleZone(e: ControlElement, look: ElementLook, input: ControlsInput) = awaitEachGesture {
    val first = awaitFirstDown(requireUnconsumed = false)
    first.consume()
    val id = first.id
    val side = if (e.stick == StickOutput.LEFT) Side.LEFT else Side.RIGHT
    look.pressed.value = true
    look.origin.value = first.position
    try {
        when (e.zone) {
            ZoneType.FLOATING_STICK -> {
                val radius = FLOAT_RING.toPx()
                val origin = first.position
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    var d = (c.position - origin) / radius
                    val m = hypot(d.x, d.y)
                    if (m > 1f) d /= m
                    look.knob.value = d
                    val (x, y) = ControlsInput.applyDeadzone(d.x, -d.y, e.deadzone)
                    input.setStick(side, x, y)
                }
            }
            ZoneType.CAMERA_STICK -> {
                val cam = CameraStick(e.sensitivity, e.acceleration, e.invertY)
                var lastMove = first.uptimeMillis
                var now = first.uptimeMillis
                while (true) {
                    val ev = withTimeoutOrNull(CameraStick.TICK_MS) { awaitPointerEvent() }
                    if (ev == null) {
                        now += CameraStick.TICK_MS
                        val (x, y) = cam.idle(now - lastMove)
                        look.knob.value = androidx.compose.ui.geometry.Offset(x, -y)
                        input.setStick(side, x, y)
                        continue
                    }
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    now = c.uptimeMillis
                    val d = c.positionChangeIgnoreConsumed()
                    if (d.x == 0f && d.y == 0f) continue
                    val (x, y) = cam.move(d.x / density, d.y / density, c.uptimeMillis - c.previousUptimeMillis)
                    lastMove = c.uptimeMillis
                    look.origin.value = c.position
                    look.knob.value = androidx.compose.ui.geometry.Offset(x, -y)
                    input.setStick(side, x, y)
                }
            }
            ZoneType.CAMERA_MOUSE -> {
                val mouse = CameraMouse(e.sensitivity, e.acceleration, e.invertY)
                while (true) {
                    val ev = awaitPointerEvent()
                    ev.changes.forEach { it.consume() }
                    val c = ev.changes.firstOrNull { it.id == id } ?: break
                    if (!c.pressed) break
                    val d = c.positionChangeIgnoreConsumed()
                    look.origin.value = c.position
                    val (mx, my) = mouse.move(d.x, d.y, c.uptimeMillis - c.previousUptimeMillis, density)
                    input.mouseMove(mx, my)
                }
            }
        }
    } finally {
        look.pressed.value = false
        look.knob.value = androidx.compose.ui.geometry.Offset.Zero
        look.origin.value = androidx.compose.ui.geometry.Offset.Unspecified
        if (e.zone != ZoneType.CAMERA_MOUSE) input.setStick(side, 0f, 0f)
    }
}
