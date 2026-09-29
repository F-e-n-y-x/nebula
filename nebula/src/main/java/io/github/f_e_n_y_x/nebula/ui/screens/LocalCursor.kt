package io.github.f_e_n_y_x.nebula.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorImage
import io.github.f_e_n_y_x.nebula.domain.model.RemoteCursorMode
import io.github.f_e_n_y_x.nebula.input.VideoRect
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/** Where a cursor image lands on screen, in pixels. */
data class CursorPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Places a host cursor over the video. The host sends shapes at stream resolution
 * ([streamWidth] × [streamHeight]), so the image is scaled exactly like the video: by
 * `rect.width / streamWidth` across and `rect.height / streamHeight` down. That covers Fit and Fill
 * (one factor), Stretch (two) and pinch zoom (already in [rect]). The hotspot sits on the pointer at
 * ([x], [y]), normalized to the video (0..1).
 */
fun cursorPlacement(rect: VideoRect, streamWidth: Float, streamHeight: Float, x: Float, y: Float, image: RemoteCursorImage): CursorPlacement {
    val sx = if (streamWidth > 0f) rect.width / streamWidth else 1f
    val sy = if (streamHeight > 0f) rect.height / streamHeight else 1f
    val px = rect.left + x * rect.width
    val py = rect.top + y * rect.height
    return CursorPlacement(px - image.hotspotX * sx, py - image.hotspotY * sy, image.width * sx, image.height * sy)
}

/**
 * Draws the PC's cursor [image] at the tracked pointer [position] (normalized to the video) inside
 * the video [rect]. Used while the PC leaves the cursor out of the video (local cursor).
 */
@Composable
fun LocalCursorOverlay(image: RemoteCursorImage, position: StateFlow<Pair<Float, Float>>, rect: VideoRect, streamWidth: Float, streamHeight: Float, modifier: Modifier = Modifier) {
    val bitmap: ImageBitmap = remember(image.id, image.width, image.height) {
        Bitmap.createBitmap(image.argb, image.width, image.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val pos by position.collectAsState()
    Canvas(modifier) {
        val p = cursorPlacement(rect, streamWidth, streamHeight, pos.first, pos.second, image)
        val w = p.width.roundToInt().coerceAtLeast(1)
        val h = p.height.roundToInt().coerceAtLeast(1)
        // Pixel-exact at 1:1 and on integer zoom; smooth when the video is scaled by a fraction.
        val exact = w == image.width && h == image.height || (w % image.width == 0 && h % image.height == 0)
        drawImage(
            bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(p.left.roundToInt(), p.top.roundToInt()),
            dstSize = IntSize(w, h),
            filterQuality = if (exact) FilterQuality.None else FilterQuality.Low,
        )
    }
}

/** The Local cursor toggle's explanation, for the setting's state and what the PC did with it. */
fun localCursorNote(enabled: Boolean, mode: RemoteCursorMode): String = when {
    !enabled -> "Draw the PC's cursor on this device so it moves with no stream delay. Needs Nova; other PCs keep the cursor in the video."
    mode == RemoteCursorMode.LOCAL -> "This device draws the PC's cursor, so it moves with no stream delay."
    mode == RemoteCursorMode.WAITING -> "Asking the PC for its cursor…"
    mode == RemoteCursorMode.UNSUPPORTED -> "This PC can't send its cursor, so it stays in the video. A pointer dot shows where it's going."
    mode == RemoteCursorMode.FAILED -> "The PC didn't send its cursor, so it stays in the video. A pointer dot shows where it's going."
    else -> "Draws the PC's cursor on this device once the stream is running."
}
