package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.StreamLink
import org.junit.Assert.assertEquals
import org.junit.Test

class MicPolicyTest {
    private val ready = StreamLink(micEnabled = true, micSupported = true)

    @Test fun `ui follows setting, host support and permission in that order`() {
        assertEquals(MicUi.HIDDEN, MicPolicy.ui(StreamLink(micEnabled = false, micSupported = true), MicPermission.GRANTED))
        assertEquals(MicUi.CONNECTING, MicPolicy.ui(StreamLink(micEnabled = true), MicPermission.GRANTED))
        assertEquals(MicUi.UNSUPPORTED, MicPolicy.ui(ready.copy(micSupported = false), MicPermission.GRANTED))
        assertEquals(MicUi.NEEDS_PERMISSION, MicPolicy.ui(ready, MicPermission.ASKABLE))
        assertEquals(MicUi.BLOCKED, MicPolicy.ui(ready, MicPermission.BLOCKED))
        assertEquals(MicUi.MUTED, MicPolicy.ui(ready, MicPermission.GRANTED))
        assertEquals(MicUi.LIVE, MicPolicy.ui(ready.copy(micLive = true), MicPermission.GRANTED))
        assertEquals(MicUi.PAUSED, MicPolicy.ui(ready.copy(micPaused = true), MicPermission.GRANTED))
    }

    @Test fun `toggle maps each state to one action`() {
        val expected = mapOf(
            MicUi.HIDDEN to MicAction.NONE, MicUi.CONNECTING to MicAction.NONE, MicUi.UNSUPPORTED to MicAction.NONE,
            MicUi.NEEDS_PERMISSION to MicAction.ASK_PERMISSION, MicUi.BLOCKED to MicAction.OPEN_SETTINGS,
            MicUi.MUTED to MicAction.START, MicUi.LIVE to MicAction.STOP, MicUi.PAUSED to MicAction.STOP,
        )
        MicUi.entries.forEach { assertEquals("for $it", expected[it], MicPolicy.onToggle(it)) }
    }

    @Test fun `initial state starts the mic or asks, never both, and never unasked`() {
        assertEquals(MicAction.START, MicPolicy.atStart(MicUi.MUTED, wantedAtStart = true))
        assertEquals(MicAction.ASK_PERMISSION, MicPolicy.atStart(MicUi.NEEDS_PERMISSION, wantedAtStart = true))
        assertEquals(MicAction.NONE, MicPolicy.atStart(MicUi.NEEDS_PERMISSION, wantedAtStart = false))
        assertEquals(MicAction.NONE, MicPolicy.atStart(MicUi.BLOCKED, wantedAtStart = true))
        assertEquals(MicAction.NONE, MicPolicy.atStart(MicUi.UNSUPPORTED, wantedAtStart = true))
        assertEquals(MicAction.NONE, MicPolicy.atStart(MicUi.MUTED, wantedAtStart = false))
    }

    @Test fun `permission result`() {
        assertEquals(MicAction.START to MicPermission.GRANTED, MicPolicy.afterPermission(granted = true, canAskAgain = false))
        assertEquals(MicAction.NONE to MicPermission.ASKABLE, MicPolicy.afterPermission(granted = false, canAskAgain = true))
        assertEquals(MicAction.NONE to MicPermission.BLOCKED, MicPolicy.afterPermission(granted = false, canAskAgain = false))
    }
}
