package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.VideoMode

/** Which way up a stream (or the device) is. Square counts as landscape. */
enum class Orientation {
    PORTRAIT,
    LANDSCAPE;

    companion object {
        fun of(width: Int, height: Int): Orientation = if (height > width) PORTRAIT else LANDSCAPE
    }
}

val VideoMode.orientation: Orientation get() = Orientation.of(width, height)

/** Settings → Stream → "Portrait streaming". */
enum class PortraitStreaming {
    /** Today's behaviour: the stream keeps the orientation it started in. */
    OFF,

    /** Turning the device turns the stream: a live mode change to the rotated size. */
    FOLLOW_ROTATION,
}

/**
 * Portrait streaming.
 *
 * A rotation is a mode change: the stream is reconnected at the same size turned 90° (W×H ↔ H×W,
 * same frame rate and Virtual / Mirror mode) with the live resolution switch. Nova makes a
 * portrait Virtual display for a portrait mode, or rotates the desktop on Mirror. Nebula also
 * sends the orientation it streams in (`nova_orientation`).
 */
object Portrait {
    /** [mode] turned 90°: width and height swapped, same frame rate. */
    fun rotated(mode: VideoMode): VideoMode = VideoMode(mode.height, mode.width, mode.fps)

    /** [mode] in [orientation]: rotated when it points the other way (a square mode is kept). */
    fun inOrientation(mode: VideoMode, orientation: Orientation): VideoMode =
        if (mode.orientation == orientation || mode.width == mode.height) mode else rotated(mode)

    /**
     * The mode a stream starts at. Following rotation with the device held upright, a landscape
     * [mode] is asked for in portrait; otherwise [mode] is kept.
     */
    fun startMode(mode: VideoMode, setting: PortraitStreaming, device: Orientation): VideoMode =
        if (setting == PortraitStreaming.FOLLOW_ROTATION) inOrientation(mode, device) else mode

    /**
     * The mode the stream's orientation (the Activity's requested orientation) follows. Off, it is
     * the start mode unless the stream was rotated from the menu; following rotation it is the
     * current mode (the screen itself is free to turn, FULL_USER).
     */
    fun orientationMode(setting: PortraitStreaming, start: VideoMode?, current: VideoMode?, rotatedFromMenu: Boolean): VideoMode? =
        if (setting == PortraitStreaming.FOLLOW_ROTATION || rotatedFromMenu) current ?: start else start

    /** The menu's label for the Rotate action on a stream at [mode]. */
    fun rotateLabel(mode: VideoMode): String =
        if (mode.orientation == Orientation.PORTRAIT) "Rotate to landscape" else "Rotate to portrait"

    /** The pill while a rotation runs. */
    fun rotatingLabel(to: VideoMode): String =
        "Rotating to ${if (to.orientation == Orientation.PORTRAIT) "portrait" else "landscape"} (${to.label})…"
}

/**
 * Decides when turning the device should turn the stream ("Follow rotation").
 *
 * Feed it the screen's orientation whenever it changes ([observe]; null while it is unknown, flat
 * or face up, which never counts as a turn). Once one orientation has held for [debounceMs] it asks
 * for the current mode rotated to match ([decide]), at most once per turn: not while a switch runs,
 * not when the stream already points that way, not for a square mode, and not again after that
 * request (even a failed one) until the device is turned again.
 */
class RotationFollower(private val debounceMs: Long = DEFAULT_DEBOUNCE_MS) {
    private var candidate: Orientation? = null
    private var since = 0L
    private var handled: Orientation? = null

    /** The screen now faces [orientation] (null: flat, face up or unknown) at [now] ms. */
    fun observe(orientation: Orientation?, now: Long) {
        if (orientation == null) return  // flat / face up: keep what was held before
        if (orientation != candidate) {
            candidate = orientation
            since = now
            handled = null
        }
    }

    /** When [decide] should be asked (the end of the debounce), or null if nothing is pending. */
    fun dueAt(): Long? = candidate?.takeIf { it != handled }?.let { since + debounceMs }

    /**
     * The mode to switch to now, or null. [current] is the mode streaming (null while a switch
     * runs or before the stream is live), [busy] whether a switch is running.
     */
    fun decide(now: Long, current: VideoMode?, busy: Boolean): VideoMode? {
        val want = candidate ?: return null
        if (busy || current == null || want == handled) return null
        if (now - since < debounceMs) return null
        handled = want
        if (current.width == current.height || current.orientation == want) return null
        return Portrait.rotated(current)
    }

    /** Forget the pending turn (e.g. the setting was turned off). */
    fun reset() {
        candidate = null
        handled = null
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 600L
    }
}
