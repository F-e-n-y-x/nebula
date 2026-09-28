package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.ElementKind
import io.github.f_e_n_y_x.nebula.controls.PadMixer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import io.github.f_e_n_y_x.nebula.controls.LayoutOrientation
import io.github.f_e_n_y_x.nebula.controls.ui.ControlsOverlay
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula

/**
 * The on-screen gamepad over the stream, drawn from [profile] (the game's own profile, the
 * default one, or the built-in Standard gamepad that follows Settings' layout style, Guide and
 * L3/R3 options). [opacity] is the overlay opacity in percent; each element's own opacity
 * multiplies it. With a controller attached ([zonesOnly]) only touch zones kept for the
 * controller remain, and their sticks join the controller's player.
 */
@Composable
fun OnScreenControls(
    remote: () -> RemoteInput?,
    profile: ControlsProfile,
    opacity: Int,
    /** A physical controller is in charge: show only zones marked "Keep with controller". */
    zonesOnly: Boolean = false,
    /** Where zone sticks go in [zonesOnly] mode: mixed into the physical pad. */
    mixer: PadMixer? = null,
    motionCaps: () -> Int = { 0 },
) {
    val scope = rememberCoroutineScope()
    val mixerNow by rememberUpdatedState(mixer)
    val input = remember(remote) { ControlsInput(remote, scope, motionCaps, mixer = { mixerNow }) }
    DisposableEffect(input) { onDispose { input.releaseAll() } }
    val form = Nebula.form
    val orientation = if (form.isLandscape || form.widthDp > form.heightDp) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
    val all = profile.layout(orientation)
    val layout = if (zonesOnly) all.filter { it.kind == ElementKind.ZONE && it.keepWithController } else all
    // Switching profile or orientation mid-press must not leave a key held on the PC.
    LaunchedEffect(profile.id, orientation, zonesOnly) { input.releaseAll() }
    ControlsOverlay(layout, input, opacity.coerceIn(10, 100) / 100f)
}
