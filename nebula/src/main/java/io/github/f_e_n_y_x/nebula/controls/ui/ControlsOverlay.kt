package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import io.github.f_e_n_y_x.nebula.controls.RRect
import io.github.f_e_n_y_x.nebula.controls.Visual
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.ElementKind
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

/** The live visual state of every element, written by the [io.github.f_e_n_y_x.nebula.controls.TouchRouter]. Main thread. */
class LookBoard {
    private val looks = HashMap<String, ElementLook>()
    fun of(id: String): ElementLook = looks.getOrPut(id) { ElementLook() }
    fun apply(id: String, v: Visual) {
        val l = of(id)
        l.pressed.value = v.pressed
        l.knob.value = Offset(v.knobX, v.knobY)
        l.origin.value = if (v.originX.isNaN()) Offset.Unspecified else Offset(v.originX, v.originY)
        l.dirs.value = v.dirs
        l.sprint.value = v.sprint
        l.lockArmed.value = v.lockArmed
        l.locked.value = v.locked
    }
    fun clear() = looks.values.forEach {
        it.pressed.value = false; it.knob.value = Offset.Zero; it.origin.value = Offset.Unspecified; it.dirs.value = emptySet()
        it.sprint.value = false; it.lockArmed.value = false; it.locked.value = false
    }
}

/**
 * The live on-screen controls for one layout, drawn only: every finger is routed by the
 * [io.github.f_e_n_y_x.nebula.controls.TouchRouter] behind the stream's input layer, which gets
 * all touches because nothing here takes pointer input. [onArea] reports the controls area in
 * window pixels (and the density) so the router hit-tests exactly what is drawn.
 */
@Composable
fun ControlsOverlay(
    elements: List<ControlElement>,
    input: ControlsInput,
    opacity: Float,
    board: LookBoard,
    onArea: (RRect, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latched by input.latched.collectAsState()
    val density = LocalDensity.current.density
    BoxWithConstraints(
        modifier.controlsArea().onGloballyPositioned { c ->
            val b = c.boundsInWindow()
            onArea(RRect(b.left, b.top, b.right, b.bottom), density)
        },
    ) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        // Zones first, so buttons draw over them (the router hit-tests in the same order).
        elements.sortedBy { it.kind != ElementKind.ZONE }.forEach { e ->
            key(e.id) {
                val look = remember(e.id) { board.of(e.id) }
                val on = remember(e.id) { derivedStateOf { e.id in latched } }
                Box(
                    Modifier
                        .elementBounds(e, w, h)
                        .testTag("osc:${e.id}")
                        .graphicsLayer { alpha = (opacity * e.opacity).coerceIn(0.05f, 1f) },
                ) {
                    ElementFace(e, look, on)
                }
            }
        }
    }
}

/** Floating joystick zone ring radius (the router uses the same, [io.github.f_e_n_y_x.nebula.controls.TouchRouter.FLOAT_RING_DP]). */
internal val FLOAT_RING = io.github.f_e_n_y_x.nebula.controls.TouchRouter.FLOAT_RING_DP.dp
