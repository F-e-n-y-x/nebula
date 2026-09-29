package io.github.f_e_n_y_x.nebula.framegen

import io.github.f_e_n_y_x.nebula.domain.model.FramegenState
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Status as S
import io.github.f_e_n_y_x.nebula.framegen.FramegenPanelState.Tone as T
import io.github.fenyx.nebula.engine.framegen.FramegenConfig
import io.github.fenyx.nebula.engine.framegen.FramegenOffReason
import io.github.fenyx.nebula.engine.framegen.SelfTestCheck
import io.github.fenyx.nebula.engine.framegen.SelfTestVerdict
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode

/**
 * What the stream menu's Frame generation panel shows, from the stream's post-processing stats,
 * the saved settings and the device check. Pure, so it is unit-tested ([framegenPanelState]).
 */
data class FramegenPanelState(
    val status: Status,
    /** Short status for the pill ("Running 2×", "Paused", "Unsupported"…). */
    val label: String,
    val tone: Tone,
    /** One or two sentences under the switch. */
    val detail: String,
    val switchOn: Boolean,
    /** Frame generation is set up for this stream: the switch pauses and resumes instantly. */
    val armed: Boolean,
    /** Live numbers while generating, else null. */
    val stats: Stats?,
    /** The upscaler is off because frame generation is set up for this stream. */
    val upscalerLocked: Boolean,
    val upscalerNote: String,
    /** Show "Check when the stream ends" (the device check never runs during a stream). */
    val offerCheckAfterStream: Boolean,
) {
    enum class Status { RUNNING, STARTING, PAUSED, THERMAL_OFF, UNSUPPORTED, CHECK_FAILED, NEEDS_CHECK, NO_ENGINE, NOT_RUNNING, OFF }
    enum class Tone { SUCCESS, NEUTRAL, WARNING, DANGER }

    data class Stats(
        val presentedFps: Float,
        val receivedFps: Float,
        val addedLatencyMs: Float,
        val addedLatencyFrames: Float,
        val lsfgMs: Int,
    )

    /** What flipping the switch to [want] does. */
    fun toggle(want: Boolean): Toggle = when {
        armed -> when {
            !want -> Toggle.PAUSE
            status == Status.THERMAL_OFF -> Toggle.FORCE_RESUME
            else -> Toggle.RESUME
        }
        !want -> Toggle.DISABLE
        status == Status.UNSUPPORTED -> Toggle.REFUSE_UNSUPPORTED
        status == Status.NO_ENGINE -> Toggle.REFUSE_NO_ENGINE
        status == Status.NEEDS_CHECK || status == Status.CHECK_FAILED -> Toggle.REFUSE_NEEDS_CHECK
        else -> Toggle.ENABLE
    }

    enum class Toggle {
        /** Instant pause / resume of the running pipeline. */
        PAUSE, RESUME, FORCE_RESUME,
        /** Change the setting; the stream applies it live. */
        ENABLE, DISABLE,
        /** Not tried: say why instead. */
        REFUSE_UNSUPPORTED, REFUSE_NO_ENGINE, REFUSE_NEEDS_CHECK,
    }

    companion object {
        const val NEEDS_CHECK_MESSAGE = "Run the device check when you're not streaming. It can't share the GPU with a live stream."
        const val QUEUED_MESSAGE = "The device check runs when this stream ends. Turn frame generation on after that."
    }
}

/**
 * Performs [t] (from [FramegenPanelState.toggle]) the way the panel's switch and the quick toggle
 * both do: pause / resume the running pipeline instantly, or change the saved setting (which the
 * stream applies per LiveSettings: never a decoder rebuild). Returns the notice to show in place,
 * or null.
 */
fun performFramegenToggle(
    t: FramegenPanelState.Toggle,
    onPause: (paused: Boolean, force: Boolean) -> Boolean,
    setEnabled: (Boolean) -> Unit,
    checkQueued: Boolean,
): String? = when (t) {
    FramegenPanelState.Toggle.PAUSE -> if (!onPause(true, false)) "Frame generation can't be paused right now." else null
    FramegenPanelState.Toggle.RESUME -> if (!onPause(false, false)) "Frame generation can't be resumed right now." else null
    FramegenPanelState.Toggle.FORCE_RESUME -> if (!onPause(false, true)) "Frame generation can't be resumed right now." else null
    FramegenPanelState.Toggle.ENABLE -> { setEnabled(true); "Frame generation starts with the next stream." }
    FramegenPanelState.Toggle.DISABLE -> { setEnabled(false); null }
    FramegenPanelState.Toggle.REFUSE_UNSUPPORTED -> "This device can't run frame generation. The full device check is in All frame generation settings."
    FramegenPanelState.Toggle.REFUSE_NO_ENGINE -> "Import Lossless.dll in All frame generation settings first."
    FramegenPanelState.Toggle.REFUSE_NEEDS_CHECK -> if (checkQueued) FramegenPanelState.QUEUED_MESSAGE else FramegenPanelState.NEEDS_CHECK_MESSAGE
}

fun framegenPanelState(
    post: PostProcessStats?,
    receivedFps: Float?,
    config: FramegenConfig,
    upscaler: UpscalerConfig,
    verdict: SelfTestVerdict,
    failure: SelfTestCheck?,
    checkQueued: Boolean,
): FramegenPanelState {
    val armed = post?.framegenArmed == true
    val received = receivedFps?.takeIf { it > 0f } ?: post?.inputFps ?: 0f
    val mult = post?.multiplier ?: config.multiplier
    val checkDetail = if (checkQueued) FramegenPanelState.QUEUED_MESSAGE else FramegenPanelState.NEEDS_CHECK_MESSAGE
    val (status, label, tone, detail) = when {
        armed && post!!.framegen == FramegenState.ACTIVE -> Quad(
            S.RUNNING, "Running $mult×", T.SUCCESS,
            "Presented %.0f fps from %.0f received · %s".format(post.presentedFps, received, post.model).trimEnd(' ', '·'),
        )
        armed && post!!.framegen == FramegenState.STARTING -> Quad(S.STARTING, "Starting", T.NEUTRAL, "Starting $mult× (${post.targetFps} fps)…")
        armed && post!!.framegen == FramegenState.PAUSED -> Quad(
            S.PAUSED, "Paused", T.NEUTRAL, "Showing the %.0f received fps. Turn on to resume instantly.".format(received),
        )
        armed && post!!.framegen == FramegenState.AUTO_OFF -> Quad(
            S.THERMAL_OFF, "Auto-off", T.WARNING,
            (post.note?.replaceFirstChar { it.uppercase() }?.trimEnd('.') ?: "Turned off because the device was too hot or too slow") +
                ". The stream carries on; turn on to try again.",
        )
        verdict == SelfTestVerdict.UNSUPPORTED -> Quad(
            S.UNSUPPORTED, "Unsupported", T.DANGER,
            "This device can't run frame generation" + (failure?.let { ": ${it.title} (${it.detail})." } ?: "."),
        )
        verdict == SelfTestVerdict.FAILED -> Quad(
            S.CHECK_FAILED, "Check failed", T.DANGER,
            "The device check failed" + (failure?.let { ": ${it.title} (${it.detail})." } ?: ".") + " " + checkDetail,
        )
        config.dllPath == null -> Quad(S.NO_ENGINE, "No engine", T.WARNING, FramegenOffReason.NO_ENGINE.message)
        verdict == SelfTestVerdict.NOT_RUN -> Quad(S.NEEDS_CHECK, "Not checked", T.WARNING, checkDetail)
        config.enabled -> Quad(S.NOT_RUNNING, "Not running", T.NEUTRAL, post?.note ?: "Frame generation starts with the next stream.")
        else -> Quad(S.OFF, "Off", T.NEUTRAL, "Doubles the frame rate on this device, e.g. 60 fps streamed, 120 fps on screen. Adds about half a frame of latency.")
    }
    val stats = if (status == S.RUNNING) {
        FramegenPanelState.Stats(post!!.presentedFps, received, post.addedLatencyMs, post.addedLatencyFrames, post.lsfgMs)
    } else null
    val upscalerNote = when {
        armed -> "Off while frame generation is set up for this stream (paused included)." +
            (if (upscaler.mode != UpscalerMode.OFF) " Your choice, ${upscaler.mode.label}, runs on streams without frame generation." else "")
        upscaler.mode == UpscalerMode.OFF -> "The decoder draws straight to the screen."
        post != null && post.upscaler != UpscalerMode.OFF.id -> "%s · %.1f ms".format(post.upscalerLabel, post.upscaleMs) + (if (post.upscaleOut.isNotEmpty()) " · ${post.upscaleOut}" else "")
        else -> "Runs when frame generation isn't. Not for HDR streams."
    }
    return FramegenPanelState(
        status = status,
        label = label,
        tone = tone,
        detail = detail,
        switchOn = when (status) {
            S.RUNNING, S.STARTING -> true
            // Never shown on when it can't run: the switch says what the stream does.
            S.PAUSED, S.THERMAL_OFF, S.UNSUPPORTED, S.CHECK_FAILED, S.NO_ENGINE, S.NEEDS_CHECK -> false
            S.NOT_RUNNING, S.OFF -> config.enabled
        },
        armed = armed,
        stats = stats,
        upscalerLocked = armed,
        upscalerNote = upscalerNote,
        offerCheckAfterStream = (status == S.NEEDS_CHECK || status == S.CHECK_FAILED) && !checkQueued,
    )
}

private data class Quad(val status: FramegenPanelState.Status, val label: String, val tone: FramegenPanelState.Tone, val detail: String)
