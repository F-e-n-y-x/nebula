package io.github.f_e_n_y_x.nebula.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionTimelineTest {
    private var now = 1_000L
    private val t = ConnectionTimeline(clockMs = { now }, wallMs = { 0L }, keep = 3)

    @Test
    fun `stage durations run until the next stage, the last until connected`() {
        t.begin("Start GTA V")
        now += 10; t.stage("RTSP handshake")
        now += 120; t.stage("control stream")
        now += 30; t.stage("video stream")
        now += 40; t.connected()
        val a = t.all.value.single()
        assertEquals(ConnectionTimeline.Outcome.CONNECTED, a.outcome)
        assertEquals(200L, a.totalMs)
        assertEquals(listOf("RTSP handshake" to 120L, "control stream" to 30L, "video stream" to 40L), ConnectionTimeline.durations(a))
    }

    @Test
    fun `a failure closes the attempt with the reason`() {
        t.begin("Start")
        now += 5; t.stage("RTSP handshake")
        now += 3000; t.failed("RTSP handshake failed (error -1)")
        val a = t.all.value.single()
        assertEquals(ConnectionTimeline.Outcome.FAILED, a.outcome)
        assertEquals("RTSP handshake failed (error -1)", a.failure)
        assertTrue(t.describe().contains("failed after 3005 ms"))
    }

    @Test
    fun `events after the attempt closed are ignored and old attempts roll off`() {
        repeat(5) { i -> t.begin("A$i"); now += 1; t.connected() }
        t.stage("late")
        assertEquals(listOf("A2", "A3", "A4"), t.all.value.map { it.label })
        assertTrue(t.all.value.last().stages.isEmpty())
    }

    @Test
    fun `a new attempt supersedes one still running`() {
        t.begin("first"); t.begin("second")
        assertEquals(ConnectionTimeline.Outcome.ENDED, t.all.value.first().outcome)
        assertEquals(ConnectionTimeline.Outcome.RUNNING, t.all.value.last().outcome)
    }
}
