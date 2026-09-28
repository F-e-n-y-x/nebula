package io.github.f_e_n_y_x.nebula.framegen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.f_e_n_y_x.nebula.domain.model.FramegenState
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.screens.ToggleRow
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.fenyx.nebula.engine.framegen.FramegenKeys

/**
 * Stream menu: frame generation quick toggle with presented vs received fps. While frame
 * generation is set up for this stream the toggle pauses / resumes it instantly; otherwise it
 * changes the setting for the next stream.
 */
@Composable
fun FramegenMenuCard(post: PostProcessStats?, receivedFps: Float?, prefs: LegacyPrefs, onPause: (paused: Boolean, force: Boolean) -> Boolean) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val armed = post?.framegenArmed == true
    val on = when (post?.framegen) {
        FramegenState.ACTIVE, FramegenState.STARTING -> true
        FramegenState.PAUSED, FramegenState.AUTO_OFF -> false
        else -> io.github.fenyx.nebula.engine.framegen.FramegenConfig.from(prefs.prefs.all).enabled
    }
    val help = when {
        post == null -> "Doubles the frame rate on this device."
        post.framegen == FramegenState.AUTO_OFF -> (post.note ?: "Turned off automatically.") + " Turn on to try again."
        armed -> framegenLine(post, receivedFps)
        else -> (post.note ?: "Set up at stream start.") + " Changes apply from the next stream."
    }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        ToggleRow("Frame generation", help, on) { want ->
            if (armed) {
                val force = post?.framegen == FramegenState.AUTO_OFF
                if (!onPause(!want, force)) Toast.makeText(ctx, "Frame generation can't be changed right now.", Toast.LENGTH_SHORT).show()
            } else {
                prefs.put(FramegenKeys.ENABLED, want)
                Toast.makeText(ctx, if (want) "Frame generation turns on from the next stream." else "Frame generation off from the next stream.", Toast.LENGTH_SHORT).show()
            }
        }
        if (post != null && post.upscaler != "off") {
            Text(upscaleLine(post), style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.padding(start = s.dp(4)))
        }
    }
}

/** "Presented 118 fps from 60 received · +0.6 frame (9.8 ms) · LSFG 3.1 · flow 100%" */
fun framegenLine(post: PostProcessStats, receivedFps: Float?): String = when (post.framegen) {
    FramegenState.ACTIVE -> "Presented %.0f fps from %.0f received · +%.1f frame (%.1f ms) · %s".format(
        post.presentedFps, receivedFps ?: post.inputFps, post.addedLatencyFrames, post.addedLatencyMs, post.model,
    )
    FramegenState.STARTING -> "Starting ${post.multiplier}× (${post.targetFps} fps)…"
    FramegenState.PAUSED -> "Paused: showing the %.0f received fps.".format(receivedFps ?: post.inputFps)
    FramegenState.AUTO_OFF -> post.note ?: "Turned off automatically."
    FramegenState.OFF -> post.note ?: "Off"
}

fun upscaleLine(post: PostProcessStats) = "Upscaler: ${post.upscalerLabel}" +
    (if (post.upscaleOut.isNotEmpty()) " · ${post.upscaleOut}" else "") + " · %.1f ms".format(post.upscaleMs)

/** Lines for the performance overlay: presented vs received fps while frame generation is set up. */
@Composable
fun FramegenOverlayLines(post: PostProcessStats?, receivedFps: Float, full: Boolean) {
    post ?: return
    val s = Nebula.scale
    if (post.framegenArmed) {
        val color = when (post.framegen) {
            FramegenState.ACTIVE -> NebulaColors.success
            FramegenState.AUTO_OFF -> NebulaColors.warning
            else -> NebulaColors.textSecondary
        }
        val text = when (post.framegen) {
            FramegenState.ACTIVE -> "FG %d× · %.0f presented / %.0f received · +%.1f f".format(post.multiplier, post.presentedFps, receivedFps, post.addedLatencyFrames)
            FramegenState.STARTING -> "FG %d× starting".format(post.multiplier)
            FramegenState.PAUSED -> "FG paused · %.0f received".format(receivedFps)
            FramegenState.AUTO_OFF -> "FG off (hot/slow) · %.0f received".format(receivedFps)
            FramegenState.OFF -> ""
        }
        Text(text, style = Nebula.type.mono, color = color)
        if (full && post.framegen == FramegenState.ACTIVE) {
            Text("LSFG %d ms · %s%s".format(post.lsfgMs, post.model, post.soak?.let { " · $it" } ?: ""), style = Nebula.type.mono, color = NebulaColors.textMuted)
        }
    }
    if (post.upscaler != "off") {
        Text("%s · %.1f ms".format(post.upscalerLabel, post.upscaleMs), style = Nebula.type.mono, color = NebulaColors.textSecondary)
    }
}

/** A compact pill for the stream-menu header. */
@Composable
fun FramegenPill(post: PostProcessStats?) {
    if (post?.framegen != FramegenState.ACTIVE) return
    Pill("FG %d× · %.0f fps".format(post.multiplier, post.presentedFps), color = NebulaColors.success, background = NebulaColors.successTint)
}

