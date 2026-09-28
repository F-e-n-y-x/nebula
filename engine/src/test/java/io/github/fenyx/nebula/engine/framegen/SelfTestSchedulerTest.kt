package io.github.fenyx.nebula.engine.framegen

import io.github.fenyx.nebula.engine.framegen.SelfTestScheduler.Outcome
import io.github.fenyx.nebula.engine.framegen.SelfTestScheduler.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression (dev9.1, S25 Ultra): running the device check during a stream ended the stream.
 * The check must never start while one runs, and the stream is never touched by a request.
 */
class SelfTestSchedulerTest {
    /** A stand-in for the live stream: what the check could do to it. */
    private class FakeStream {
        var live = true
        var restarts = 0
    }

    /** Drives the scheduler the way FramegenSelfTestRunner does, counting checks launched. */
    private class Harness(val stream: FakeStream = FakeStream()) {
        val scheduler = SelfTestScheduler()
        var launched = mutableListOf<Request>()
        var aborted = 0
        var running: Request? = null

        fun request(r: Request): Outcome = scheduler.request(running != null).also { if (it == Outcome.START) { launched += r; running = r } }
        fun streamStarts() { if (scheduler.streamStarted(running)) { aborted++; running = null } }
        fun streamEnds() { scheduler.streamEnded()?.let { request(it) } }
    }

    private val r = Request(2340, 1080)

    @Test fun `check requested while streaming leaves the stream untouched`() {
        val h = Harness()
        h.streamStarts()
        assertEquals(Outcome.REFUSED_STREAMING, h.request(r))
        assertTrue(h.launched.isEmpty())
        assertEquals(0, h.aborted)
        assertTrue(h.stream.live)
        assertEquals(0, h.stream.restarts)
        assertNull(h.scheduler.pending)
    }

    @Test fun `run after stream waits for the end, then starts once`() {
        val h = Harness()
        h.streamStarts()
        assertTrue(h.scheduler.runAfterStream(r))
        assertTrue(h.launched.isEmpty())
        h.streamEnds()
        assertEquals(listOf(r), h.launched)
        assertFalse(h.scheduler.streaming)
        assertNull(h.scheduler.pending)
    }

    @Test fun `run after stream without a stream says start now`() {
        assertFalse(SelfTestScheduler().runAfterStream(r))
    }

    @Test fun `a check still running when a stream starts is stopped and re-run afterwards`() {
        val h = Harness()
        assertEquals(Outcome.START, h.request(r))
        h.streamStarts()
        assertEquals(1, h.aborted)
        assertEquals(r, h.scheduler.pending)
        h.streamEnds()
        assertEquals(listOf(r, r), h.launched)
    }

    @Test fun `cancelled queue runs nothing`() {
        val h = Harness()
        h.streamStarts()
        h.scheduler.runAfterStream(r)
        h.scheduler.cancelPending()
        h.streamEnds()
        assertTrue(h.launched.isEmpty())
    }

    @Test fun `overlapping streams wait for the last one`() {
        val h = Harness()
        h.streamStarts()
        h.streamStarts()
        h.scheduler.runAfterStream(r)
        h.streamEnds()
        assertTrue(h.launched.isEmpty())
        assertEquals(Outcome.REFUSED_STREAMING, h.request(r))
        h.streamEnds()
        assertEquals(listOf(r), h.launched)
    }

    @Test fun `busy while a check runs`() {
        val h = Harness()
        h.request(r)
        assertEquals(Outcome.ALREADY_RUNNING, h.request(r))
    }
}
