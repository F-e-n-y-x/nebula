package io.github.fenyx.nebula.engine

import com.limelight.nvstream.RemoteTextContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Remote text context packets as Nova sends them (UIA source, input matched), through the client trust rules. */
class RemoteTextFieldTrackerTest {
    private val openFlags = RemoteTextContext.FLAG_ACTIVE or RemoteTextContext.FLAG_EDITABLE or
        RemoteTextContext.FLAG_ELEMENT_RECT or RemoteTextContext.FLAG_INPUT_MATCHED or RemoteTextContext.FLAG_ANCHOR_POINT

    private fun ctx(
        flags: Int, revision: Int, activation: Long, cause: Int = RemoteTextContext.CAUSE_REMOTE_TOUCH,
        source: Int = RemoteTextContext.SOURCE_UIA, anchorY: Int = 270,
    ) = RemoteTextContext(
        flags, revision, activation, 7L, source, cause,
        anchorX = 500, anchorY = anchorY,
        elementLeft = 400, elementTop = 250, elementRight = 800, elementBottom = 290,
        caretLeft = 0, caretTop = 0, caretRight = 0, caretBottom = 0,
        captureWidth = 1920, captureHeight = 1080,
    )

    private fun close(revision: Int, activation: Long) = ctx(RemoteTextContext.FLAG_INPUT_MATCHED or RemoteTextContext.FLAG_ANCHOR_POINT, revision, activation)

    @Test
    fun `a matched activation opens and its deactivation closes`() {
        val t = RemoteTextFieldTracker()
        assertTrue(t.onContext(ctx(openFlags, 1, 1)))
        val f = t.current
        assertNotNull(f)
        assertEquals(1L, f!!.activation)
        assertFalse(f.password)
        assertEquals(270f / 1080f, f.focusY!!, 0.0001f)
        assertTrue(t.onContext(close(2, 1)))
        assertNull(t.current)
    }

    @Test
    fun `focus the host did not match to this device never opens`() {
        val t = RemoteTextFieldTracker()
        assertFalse(t.onContext(ctx(openFlags and RemoteTextContext.FLAG_INPUT_MATCHED.inv(), 1, 1)))
        assertFalse(t.onContext(ctx(openFlags and RemoteTextContext.FLAG_ELEMENT_RECT.inv(), 2, 2)))
        assertFalse(t.onContext(ctx(openFlags, 3, 3, cause = 0)))
        assertNull(t.current)
    }

    @Test
    fun `stale revisions and foreign deactivations are ignored`() {
        val t = RemoteTextFieldTracker()
        assertTrue(t.onContext(ctx(openFlags, 10, 4)))
        assertFalse("older revision", t.onContext(close(9, 4)))
        assertFalse("another activation", t.onContext(close(11, 3)))
        assertNotNull(t.current)
        assertFalse("replayed revision", t.onContext(ctx(openFlags, 10, 4)))
        assertTrue(t.onContext(close(12, 4)))
    }

    @Test
    fun `revisions wrap around`() {
        val t = RemoteTextFieldTracker()
        assertTrue(t.onContext(ctx(openFlags, -1, 1)))
        assertTrue(t.onContext(close(0, 1)))
    }

    @Test
    fun `password and multiline flags come through, a new field replaces the old`() {
        val t = RemoteTextFieldTracker()
        t.onContext(ctx(openFlags or RemoteTextContext.FLAG_PASSWORD, 1, 1))
        assertTrue(t.current!!.password)
        assertTrue(t.onContext(ctx(openFlags or RemoteTextContext.FLAG_MULTILINE, 2, 2)))
        assertEquals(2L, t.current!!.activation)
        assertTrue(t.current!!.multiline)
        assertFalse(t.current!!.password)
    }

    @Test
    fun `reset forgets focus and revisions`() {
        val t = RemoteTextFieldTracker()
        t.onContext(ctx(openFlags, 50, 1))
        t.reset()
        assertNull(t.current)
        assertTrue("revisions start over after a reconnect", t.onContext(ctx(openFlags, 1, 1)))
    }
}
