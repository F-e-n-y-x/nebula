package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.StreamLink

/** Android's RECORD_AUDIO state as far as the UI can tell. */
enum class MicPermission {
    GRANTED,
    /** Not granted, but the system dialog can still be shown. */
    ASKABLE,
    /** Denied with "don't ask again" (or denied twice): only system settings can grant it. */
    BLOCKED,
}

/** What the mic control shows. */
enum class MicUi {
    /** The mic setting is off: no control at all. */
    HIDDEN,
    /** The stream is still connecting. */
    CONNECTING,
    /** The host didn't ask for a microphone (older host, or mic turned off in Nova). */
    UNSUPPORTED,
    NEEDS_PERMISSION,
    BLOCKED,
    MUTED,
    LIVE,
    /** Was live; paused while Nebula is in the background, resumes on return. */
    PAUSED,
}

/** What a tap on the mic control (or the start of a stream) should do. */
enum class MicAction { NONE, ASK_PERMISSION, OPEN_SETTINGS, START, STOP }

/** Pure mic state rules for the stream screen; the engine only starts and stops capture. */
object MicPolicy {
    fun ui(link: StreamLink, permission: MicPermission): MicUi = when {
        !link.micEnabled -> MicUi.HIDDEN
        link.micSupported == null -> MicUi.CONNECTING
        !link.micSupported -> MicUi.UNSUPPORTED
        link.micLive -> MicUi.LIVE
        link.micPaused -> MicUi.PAUSED
        permission == MicPermission.BLOCKED -> MicUi.BLOCKED
        permission == MicPermission.ASKABLE -> MicUi.NEEDS_PERMISSION
        else -> MicUi.MUTED
    }

    fun onToggle(ui: MicUi): MicAction = when (ui) {
        MicUi.NEEDS_PERMISSION -> MicAction.ASK_PERMISSION
        MicUi.BLOCKED -> MicAction.OPEN_SETTINGS
        MicUi.MUTED -> MicAction.START
        MicUi.LIVE, MicUi.PAUSED -> MicAction.STOP
        MicUi.HIDDEN, MicUi.CONNECTING, MicUi.UNSUPPORTED -> MicAction.NONE
    }

    /**
     * Once the stream is connected: honour "Initial microphone state". A missing permission is only
     * asked for here when the user wants the mic on at start; otherwise it waits for a tap.
     */
    fun atStart(ui: MicUi, wantedAtStart: Boolean): MicAction = when {
        !wantedAtStart -> MicAction.NONE
        ui == MicUi.MUTED -> MicAction.START
        ui == MicUi.NEEDS_PERMISSION -> MicAction.ASK_PERMISSION
        else -> MicAction.NONE
    }

    /** After the system permission dialog. [canAskAgain] is shouldShowRequestPermissionRationale. */
    fun afterPermission(granted: Boolean, canAskAgain: Boolean): Pair<MicAction, MicPermission> = when {
        granted -> MicAction.START to MicPermission.GRANTED
        canAskAgain -> MicAction.NONE to MicPermission.ASKABLE
        else -> MicAction.NONE to MicPermission.BLOCKED
    }

    /** Short status for the menu row and indicator. */
    fun label(ui: MicUi): String = when (ui) {
        MicUi.HIDDEN -> ""
        MicUi.CONNECTING -> "Connecting…"
        MicUi.UNSUPPORTED -> "This PC isn't taking microphone audio"
        MicUi.NEEDS_PERMISSION -> "Tap to allow microphone access"
        MicUi.BLOCKED -> "Microphone access is off for Nebula in Android settings"
        MicUi.MUTED -> "Muted"
        MicUi.LIVE -> "Your voice is going to the PC"
        MicUi.PAUSED -> "Paused while Nebula is in the background"
    }
}
