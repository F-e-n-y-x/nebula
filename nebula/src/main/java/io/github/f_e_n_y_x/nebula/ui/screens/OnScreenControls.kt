package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.PadMixer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.f_e_n_y_x.nebula.controls.LayoutOrientation
import io.github.f_e_n_y_x.nebula.controls.OnScreenPad
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper
import io.github.f_e_n_y_x.nebula.controls.ui.ControlsOverlay
import io.github.f_e_n_y_x.nebula.controls.ui.LookBoard
import io.github.f_e_n_y_x.nebula.controls.BackgroundTouches
import io.github.f_e_n_y_x.nebula.controls.LookOutput
import io.github.f_e_n_y_x.nebula.controls.OutsideTouch
import io.github.f_e_n_y_x.nebula.controls.RRect
import io.github.f_e_n_y_x.nebula.controls.RouterLayout
import io.github.f_e_n_y_x.nebula.controls.TouchRouter
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula

/**
 * The on-screen gamepad over the stream, drawn from [profile] (the game's own profile, the
 * default one, or the built-in Standard gamepad that follows Settings' layout style, Guide and
 * L3/R3 options). [opacity] is the overlay opacity in percent; each element's own opacity
 * multiplies it. With a controller attached ([zonesOnly]) only touch zones kept for the
 * controller remain, and their sticks join the controller's player.
 *
 * The gamepad part goes through [pad], the mapper that owns every host slot: with no controller
 * attached it is player 1 (the phone pad), otherwise it drives player 1 together with the
 * controller instead of adding a second player. Keys and the mouse go straight to [remote].
 */
@Composable
fun OnScreenControls(
    remote: () -> RemoteInput?,
    pad: () -> GamepadMapper?,
    profile: ControlsProfile,
    opacity: Int,
    /** A physical controller is in charge: show only zones marked "Keep with controller". */
    zonesOnly: Boolean = false,
    /** Where zone sticks go in [zonesOnly] mode: mixed into the physical pad. */
    mixer: PadMixer? = null,
    motionCaps: () -> Int = { 0 },
    /** Where the stream's input layer finds the finger router while these controls are shown. */
    routing: RouterSlot? = null,
    /** What fingers outside the controls do, and how they look (per profile). */
    outside: OutsideTouch = OutsideTouch.LOOK,
    look: LookOutput = LookOutput.STICK,
    /** The stream's trackpad layer, for background fingers in Trackpad / Touch mode. */
    background: () -> BackgroundTouches? = { null },
) {
    // Timers (the 50 ms minimum hold, the resend, macros) need exact delays: a Handler-backed main
    // scope, not the composition's frame-driven one, which only runs on the next frame.
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate) }
    DisposableEffect(scope) { onDispose { scope.coroutineContext[kotlinx.coroutines.Job]?.cancel() } }
    val mixerNow by rememberUpdatedState(mixer)
    val padNow by rememberUpdatedState(pad)
    val onScreenPad = remember {
        object : OnScreenPad {
            override fun state(buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int): Boolean {
                val p = padNow() ?: return false
                p.onScreenState(buttons, lt, rt, lx, ly, rx, ry)
                return true
            }
            override fun released(): Boolean {
                val p = padNow() ?: return false
                p.onScreenReleased()
                return true
            }
        }
    }
    val input = remember(remote) { ControlsInput(remote, scope, motionCaps, mixer = { mixerNow }, pad = { onScreenPad }) }
    DisposableEffect(input) { onDispose { input.releaseAll() } }
    val form = Nebula.form
    val orientation = if (form.isLandscape || form.widthDp > form.heightDp) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
    val all = profile.layout(orientation)
    val layout = if (zonesOnly) all.filter { it.kind == ElementKind.ZONE && it.keepWithController } else all
    // Switching profile or orientation mid-press must not leave a key held on the PC.
    val backgroundNow by rememberUpdatedState(background)
    val board = remember { LookBoard() }
    val router = remember(input) { TouchRouter(input, background = { backgroundNow() }, onVisual = board::apply) }
    LaunchedEffect(profile.id, orientation, zonesOnly) { router.cancel(); input.releaseAll() }
    DisposableEffect(router, routing) {
        routing?.router = router
        onDispose {
            if (routing?.router === router) routing.router = null
            router.cancel()
            board.clear()
        }
    }
    var area by remember { mutableStateOf<Pair<RRect, Float>?>(null) }
    val placed = area
    if (placed != null) {
        // Re-hit-test when the layout, area or background policy changes.
        val lay = remember(layout, placed, outside, look) { RouterLayout.of(layout, placed.first, placed.second, outside, look) }
        SideEffect { router.layout = lay }
    }
    ControlsOverlay(layout, input, opacity.coerceIn(10, 100) / 100f, board, onArea = { r, d -> if (area?.first != r || area?.second != d) area = r to d })
}

/** The router the stream's input layer should feed; set while on-screen controls are shown. Main thread. */
class RouterSlot {
    var router: TouchRouter? = null
}
