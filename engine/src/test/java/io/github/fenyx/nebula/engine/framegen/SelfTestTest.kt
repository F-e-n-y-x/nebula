package io.github.fenyx.nebula.engine.framegen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTestTest {
    private val phone = SelfTestEnvironment(35, listOf("arm64-v8a"), nativeLoaded = true)
    private val adreno830 = "vulkan=1 api=1.3.284 gpu=Adreno_(TM)_830 vendor=0x5143 driver=1 ahb=1 external_memory=1 dedicated=1 foreign_queue=1 ycbcr=1 robustness2=1 null_descriptor=1 fp16=1"
    private val dllOk = "lossless-dll-ok rcdata=100 generate_dxbc=4096B spirv=8192B"
    private fun bench(p95: Double) = "ok model=3.1 flow=1.00 size=640x352 frames=120 init_ms=180 first_ms=30.00 median_ms=%.2f p95_ms=%.2f max_ms=%.2f".format(p95 - 1, p95, p95 + 2)

    private fun eval(env: SelfTestEnvironment = phone, caps: String? = adreno830, dll: String? = dllOk, b: String? = bench(6.0), crashedAt: String? = null) =
        SelfTestEvaluator.evaluate(env, caps, dll, b, "fp", 1000L, crashedAt)

    @Test fun `a capable device passes with its frame time`() {
        val r = eval()
        assertEquals(SelfTestVerdict.PASSED, r.verdict)
        assertTrue(r.checks.all { it.passed })
        assertEquals(6.0, r.benchmark!!.p95Ms, 0.001)
        assertEquals("Adreno (TM) 830", r.gpu)
        assertTrue(r.summary, r.summary.startsWith("Supported"))
    }

    @Test fun `frame time decides passed, slow or failed`() {
        assertEquals(SelfTestVerdict.SLOW, eval(b = bench(14.0)).verdict)
        assertEquals(SelfTestVerdict.FAILED, eval(b = bench(25.0)).verdict)
    }

    @Test fun `old Android, 32-bit or x86 are unsupported before Vulkan is touched`() {
        assertEquals(SelfTestVerdict.UNSUPPORTED, eval(env = phone.copy(sdk = 28)).verdict)
        val x86 = eval(env = phone.copy(abis = listOf("x86_64")), caps = null)
        assertEquals(SelfTestVerdict.UNSUPPORTED, x86.verdict)
        assertTrue("no Vulkan check without the engine", x86.checks.none { it.id == "vulkan" })
        assertTrue(x86.summary, x86.summary.startsWith("Not supported on this device"))
    }

    @Test fun `missing Vulkan features are unsupported, naming the feature`() {
        val noRobust = eval(caps = adreno830.replace("robustness2=1 null_descriptor=1", "robustness2=0 null_descriptor=0"))
        assertEquals(SelfTestVerdict.UNSUPPORTED, noRobust.verdict)
        assertEquals("robustness2", noRobust.firstFailure!!.id)
        assertEquals(SelfTestVerdict.UNSUPPORTED, eval(caps = adreno830.replace("ahb=1", "ahb=0")).verdict)
        assertEquals(SelfTestVerdict.UNSUPPORTED, eval(caps = "vulkan=1 api=1.0.61 gpu=Old").verdict)
        assertEquals(SelfTestVerdict.UNSUPPORTED, eval(caps = "vulkan=0 reason=no-loader").verdict)
    }

    @Test fun `DLL and benchmark problems are failures, not unsupported`() {
        assertEquals(SelfTestVerdict.FAILED, eval(dll = null).verdict)
        assertEquals(SelfTestVerdict.FAILED, eval(dll = "lossless-dll-probe-failed: generate-shader-missing").verdict)
        val b = eval(b = "error=vkCreateDevice failed")
        assertEquals(SelfTestVerdict.FAILED, b.verdict)
        assertEquals("vkCreateDevice failed", b.firstFailure!!.detail)
    }

    @Test fun `report round-trips through JSON and goes stale on another fingerprint`() {
        val r = eval()
        val back = SelfTestReport.fromJson(r.toJson())
        assertEquals(r, back)
        assertEquals(SelfTestVerdict.PASSED, SelfTestReport.verdictFor(r.toJson(), "fp"))
        assertEquals(SelfTestVerdict.NOT_RUN, SelfTestReport.verdictFor(r.toJson(), "other-dll"))
        assertEquals(SelfTestVerdict.NOT_RUN, SelfTestReport.verdictFor("not json", "fp"))
        assertEquals(SelfTestVerdict.NOT_RUN, SelfTestReport.verdictFor(null, "fp"))
    }

    @Test fun `benchmark line parsing`() {
        val b = BenchmarkResult.parse(bench(7.5))
        assertNotNull(b)
        assertEquals(640, b!!.width)
        assertEquals(352, b.height)
        assertEquals(6.5, b.medianMs, 0.001)
        assertEquals(null, BenchmarkResult.parse("error=x"))
        assertEquals(null, BenchmarkResult.parse("ok model=3.1"))
    }

    // ---- State machine ----

    private fun machine() = SelfTestMachine("fp", phone) { 42L }

    @Test fun `machine runs stages then finishes once`() {
        val m = machine()
        assertEquals(SelfTestState.Running("environment"), m.start())
        assertEquals(SelfTestState.Running("benchmark"), m.stage("benchmark"))
        val done = m.done(SelfTestRaw(phone, adreno830, dllOk, bench(6.0))) as SelfTestState.Done
        assertEquals(SelfTestVerdict.PASSED, done.report.verdict)
        assertEquals(42L, done.report.finishedAtMs)
        // Late events after the finish change nothing.
        assertEquals(done, m.crashed())
        assertEquals(done, m.timedOut())
        assertEquals(done, m.stage("caps"))
    }

    @Test fun `a crash blames the running stage`() {
        fun crashIn(stage: String) = machine().apply { start(); stage(stage) }.crashed() as SelfTestState.Done
        val caps = crashIn("caps").report
        assertEquals(SelfTestVerdict.UNSUPPORTED, caps.verdict)
        assertTrue(caps.firstFailure!!.detail.contains("crashed"))
        val dll = crashIn("dll").report
        assertEquals(SelfTestVerdict.FAILED, dll.verdict)
        assertEquals("dll", dll.firstFailure!!.id)
        val bench = crashIn("benchmark").report
        assertEquals(SelfTestVerdict.FAILED, bench.verdict)
        assertEquals("benchmark", bench.firstFailure!!.id)
        assertEquals(SelfTestVerdict.UNSUPPORTED, crashIn("environment").report.verdict)
    }

    @Test fun `timeout fails and a reply after it is ignored`() {
        val m = machine()
        m.start()
        m.stage("benchmark")
        val t = m.timedOut() as SelfTestState.Done
        assertEquals(SelfTestVerdict.FAILED, t.report.verdict)
        assertTrue(t.report.firstFailure!!.detail.contains("benchmark"))
        assertEquals(t, m.done(SelfTestRaw(phone, adreno830, dllOk, bench(6.0))))
    }

    @Test fun `nothing happens before start`() {
        val m = machine()
        assertEquals(SelfTestState.Idle, m.crashed())
        assertEquals(SelfTestState.Idle, m.stage("caps"))
        assertFalse(m.state is SelfTestState.Done)
    }
}
