package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import io.github.f_e_n_y_x.nebula.controls.ControlsInput
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.LayoutOrientation
import io.github.f_e_n_y_x.nebula.controls.ui.ControlsOverlay
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula

/**
 * The on-screen gamepad over the stream, drawn from [profile] (the game's own profile, the
 * default one, or the built-in Standard gamepad that follows Settings' layout style, Guide and
 * L3/R3 options). [opacity] is the overlay opacity in percent; each element's own opacity
 * multiplies it.
 */
@Composable
fun OnScreenControls(remote: () -> RemoteInput?, profile: ControlsProfile, opacity: Int) {
    val scope = rememberCoroutineScope()
    val input = remember(remote) { ControlsInput(remote, scope) }
    DisposableEffect(input) { onDispose { input.releaseAll() } }
    val form = Nebula.form
    val orientation = if (form.isLandscape || form.widthDp > form.heightDp) LayoutOrientation.LANDSCAPE else LayoutOrientation.PORTRAIT
    val layout = profile.layout(orientation)
    // Switching profile or orientation mid-press must not leave a key held on the PC.
    LaunchedEffect(profile.id, orientation) { input.releaseAll() }
    ControlsOverlay(layout, input, opacity.coerceIn(10, 100) / 100f)
}
