package io.github.fenyx.nebula.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host's cursor updates as moonlight-common-c hands them over (flags, id, size, hotspot, BGRA). */
class HostCursorTrackerTest {
    private val shapeVisible = HostCursorTracker.FLAG_SHAPE or HostCursorTracker.FLAG_VISIBLE

    private fun bgra(w: Int, h: Int, b: Int = 0x10, g: Int = 0x20, r: Int = 0x30, a: Int = 0xFF) =
        ByteArray(w * h * 4) { i -> listOf(b, g, r, a)[i % 4].toByte() }

    @Test
    fun `converts BGRA to ARGB`() {
        val argb = HostCursorTracker.bgraToArgb(byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80.toByte()))
        assertArrayEquals(intArrayOf(0x40302010, 0x80706050.toInt()), argb)
    }

    @Test
    fun `a shape update shows it`() {
        val t = HostCursorTracker()
        val s = t.onUpdate(shapeVisible, 7, 2, 1, 1, 0, bgra(2, 1))!!
        assertEquals(LocalCursorStatus.ACTIVE, s.status)
        assertTrue(s.visible)
        val shape = s.shape!!
        assertEquals(7, shape.id)
        assertEquals(2, shape.width)
        assertEquals(1, shape.hotspotX)
        assertEquals(0xFF302010.toInt(), shape.argb[0])
    }

    @Test
    fun `state-only updates hide, show and switch to cached shapes`() {
        val t = HostCursorTracker()
        t.onUpdate(shapeVisible, 1, 4, 4, 0, 0, bgra(4, 4))
        t.onUpdate(shapeVisible, 2, 8, 8, 3, 3, bgra(8, 8))

        val hidden = t.onUpdate(0, 2, 0, 0, 0, 0, null)!!
        assertFalse(hidden.visible)
        assertEquals(2, hidden.shape!!.id)

        val back = t.onUpdate(HostCursorTracker.FLAG_VISIBLE, 1, 0, 0, 0, 0, null)!!
        assertTrue(back.visible)
        assertEquals(4, back.shape!!.width)

        // An id this client never got: nothing to draw until the host sends pixels.
        assertNull(t.onUpdate(HostCursorTracker.FLAG_VISIBLE, 99, 0, 0, 0, 0, null)!!.shape)
    }

    @Test
    fun `rejects malformed shapes like the V+ client`() {
        val t = HostCursorTracker()
        assertNull(t.onUpdate(shapeVisible, 1, 0, 1, 0, 0, ByteArray(0)))
        assertNull(t.onUpdate(shapeVisible, 1, 257, 1, 0, 0, bgra(257, 1)))
        assertNull(t.onUpdate(shapeVisible, 1, 4, 4, 4, 0, bgra(4, 4)))
        assertNull(t.onUpdate(shapeVisible, 1, 4, 4, 0, 0, bgra(4, 3)))
        assertNull(t.onUpdate(shapeVisible, 1, 4, 4, 0, 0, null))
        assertNull(t.state().shape)
    }

    @Test
    fun `evicts the oldest shapes beyond the budget but keeps the current one`() {
        val budget = 3 * 16 * 16 * 4
        val t = HostCursorTracker(budget)
        for (id in 1..4) t.onUpdate(shapeVisible, id, 16, 16, 0, 0, bgra(16, 16))
        assertNull(t.onUpdate(HostCursorTracker.FLAG_VISIBLE, 1, 0, 0, 0, 0, null)!!.shape)
        assertNotNull(t.onUpdate(HostCursorTracker.FLAG_VISIBLE, 4, 0, 0, 0, 0, null)!!.shape)
        // A single shape larger than the budget is still kept while shown.
        val big = HostCursorTracker(16)
        assertNotNull(big.onUpdate(shapeVisible, 5, 16, 16, 0, 0, bgra(16, 16))!!.shape)
    }

    @Test
    fun `reset forgets everything`() {
        val t = HostCursorTracker()
        t.onUpdate(shapeVisible, 1, 4, 4, 0, 0, bgra(4, 4))
        t.reset()
        val s = t.state()
        assertFalse(s.visible)
        assertNull(s.shape)
        assertNull(t.onUpdate(HostCursorTracker.FLAG_VISIBLE, 1, 0, 0, 0, 0, null)!!.shape)
    }
}
