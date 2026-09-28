package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.pm.ActivityInfo
import kotlin.math.max
import kotlin.math.min

/**
 * The Activity orientation while streaming, as V+'s OrientationManager.setPreferredOrientation
 * picks it:
 * - "Follow device rotation" on, or Portrait streaming set to "Follow rotation": any orientation
 *   the user allows (FULL_USER). With Follow rotation, turning the device then switches the stream
 *   to the rotated size (see RotationFollower).
 * - Otherwise a landscape stream (and Native, which is this screen in landscape) turns with the
 *   sensor between the two landscapes (USER_LANDSCAPE); a portrait stream between the portraits.
 * - A square stream on a squarish screen (foldable inner display, tablet) may turn freely,
 *   unless the on-screen controls are on (they are laid out for landscape).
 *
 * The size passed in is Portrait.orientationMode: the resolution the stream was started with, so
 * a live resolution switch never flips the phone, or the current one after Rotate in the menu. Rotating only re-lays out the video and overlays; the
 * stream Activity handles the configuration change itself (manifest configChanges) and the
 * connection stays up.
 */
fun streamOrientation(
    followRotation: Boolean, streamWidth: Int, streamHeight: Int, squarishScreen: Boolean, onscreenControls: Boolean = false,
    portraitFollow: Boolean = false,
): Int = when {
    followRotation || portraitFollow -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    streamHeight > streamWidth -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    // Square: V+ leaves it free on a squarish screen unless the on-screen controls want landscape.
    squarishScreen && streamWidth == streamHeight && !onscreenControls -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
    else -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
}

/** V+'s PreferenceConfiguration.isSquarishScreen: long side under 1.3× the short side. */
fun isSquarishScreen(width: Int, height: Int): Boolean {
    val lo = min(width, height)
    return lo > 0 && max(width, height).toFloat() / lo < 1.3f
}
