package io.github.fenyx.nebula.engine

import com.limelight.nvstream.RemoteTextContext
import com.limelight.nvstream.RemoteTextContextPolicy

/**
 * A text field on the PC that has keyboard focus after this device tapped or clicked it (control
 * message 24, host feature `LI_FF_REMOTE_TEXT_CONTEXT`). The host never sends field contents.
 *
 * [activation] is the host's id for this focus; a new one means a new field (or the same field
 * clicked again after it closed). [focusY] is where the caret or the tap is, as a share of the
 * video height (0 = top), when the host said; the app can keep that line above its keyboard.
 */
data class RemoteTextField(
    val activation: Long,
    val password: Boolean,
    val multiline: Boolean,
    val focusY: Float?,
)

/**
 * Applies the client trust rules ([RemoteTextContextPolicy]) to the host's packets: only an
 * activation the host matched to this device's own input opens a field, only the matching
 * deactivation closes it, and stale or replayed revisions are dropped. Not thread-safe; the
 * session calls it from the control thread only.
 */
class RemoteTextFieldTracker {
    private var lastRevision = -1L
    private var active: RemoteTextField? = null

    /** The field that has focus now, or null. */
    val current: RemoteTextField? get() = active

    /**
     * One packet from the host. Returns true when [current] changed.
     */
    fun onContext(context: RemoteTextContext): Boolean {
        val revision = context.revision.toLong() and 0xffff_ffffL
        if (lastRevision >= 0 && !RemoteTextContextPolicy.isNewerRevision(revision, lastRevision)) return false
        val open = active
        if (open != null && RemoteTextContextPolicy.isTrustedDeactivation(context, open.activation)) {
            lastRevision = revision
            active = null
            return true
        }
        if (!RemoteTextContextPolicy.isTrustedActivation(context)) return false
        lastRevision = revision
        val focus = RemoteTextContextPolicy.focusY(context)?.let { y ->
            if (context.captureHeight > 0) (y.toFloat() / context.captureHeight).coerceIn(0f, 1f) else null
        }
        val next = RemoteTextField(
            activation = context.activationId,
            password = context.hasFlag(RemoteTextContext.FLAG_PASSWORD),
            multiline = context.hasFlag(RemoteTextContext.FLAG_MULTILINE),
            focusY = focus,
        )
        if (next == active) return false
        active = next
        return true
    }

    /** The connection ended or restarted: nothing has focus, and revisions start over. */
    fun reset() {
        lastRevision = -1L
        active = null
    }
}
