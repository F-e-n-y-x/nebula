package io.github.f_e_n_y_x.nebula.framegen

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.FramegenSelfTestRunner
import io.github.fenyx.nebula.engine.framegen.SelfTestState
import io.github.fenyx.nebula.engine.framegen.SelfTestVerdict
import io.github.fenyx.nebula.engine.framegen.UpscalerConfig
import io.github.fenyx.nebula.engine.framegen.UpscalerMode
import io.github.fenyx.nebula.engine.upscale.UpscaleFrameInfo
import io.github.fenyx.nebula.engine.upscale.UpscalerRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device smoke for frame generation and the upscaler. Needs no host. Results (the self-test
 * report and upscaled PNGs) are logged under "FramegenDeviceTest" and written to the test app's
 * external files dir for inspection.
 */
@RunWith(AndroidJUnit4::class)
class FramegenDeviceTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(ctx.getExternalFilesDir(null), "framegen-test").apply { mkdirs() }

    /** The device check always finishes with a report, and never takes this process down. */
    @Test fun selfTestFinishesWithAReport() {
        // The debug build of a private checkout bundles Lossless.dll; stage it like app start does.
        val staged = FramegenDll.ensureStaged(ctx) || FramegenDll.stagedPath(FramegenDll.prefs(ctx)) != null
        Log.i(TAG, "bundled DLL: ${FramegenDll.hasBundled(ctx)} staged: $staged")
        FramegenSelfTestRunner.clear(ctx)
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { FramegenSelfTestRunner.start(ctx, 2560, 1440) }
        val deadline = System.currentTimeMillis() + 70_000
        while (System.currentTimeMillis() < deadline) {
            if (FramegenSelfTestRunner.state.value is SelfTestState.Done) { done.countDown(); break }
            Thread.sleep(200)
        }
        val state = FramegenSelfTestRunner.state.value
        assertTrue("self-test didn't finish: $state", state is SelfTestState.Done)
        val r = (state as SelfTestState.Done).report
        Log.i(TAG, "self-test verdict=${r.verdict} summary=${r.summary} gpu=${r.gpu} dll=${FramegenDll.describe(FramegenDll.prefs(ctx))}")
        r.checks.forEach { Log.i(TAG, "  ${if (it.passed) "PASS" else "FAIL"} ${it.id}: ${it.title} — ${it.detail}") }
        File(out, "selftest.json").writeText(r.toJson())
        assertNotEquals(SelfTestVerdict.NOT_RUN, r.verdict)
        assertEquals(r.verdict, FramegenSelfTestRunner.verdict(ctx))
    }

    /** Each upscaler mode renders a synthetic 480x270 frame to 960x540 through GLES. */
    @Test fun upscalerRendersEveryMode() {
        for (mode in UpscalerMode.entries) {
            val (outW, outH) = 960 to 540
            val reader = ImageReader.newInstance(outW, outH, PixelFormat.RGBA_8888, 2)
            val rt = HandlerThread("reader").apply { start() }
            val got = CountDownLatch(1)
            var bmp: Bitmap? = null
            reader.setOnImageAvailableListener({ r ->
                r.acquireLatestImage()?.use { img ->
                    val plane = img.planes[0]
                    val b = Bitmap.createBitmap(plane.rowStride / plane.pixelStride, outH, Bitmap.Config.ARGB_8888)
                    b.copyPixelsFromBuffer(plane.buffer)
                    bmp = Bitmap.createBitmap(b, 0, 0, outW, outH)
                    got.countDown()
                }
            }, Handler(rt.looper))
            var info: UpscaleFrameInfo? = null
            val renderer = UpscalerRenderer.create(480, 270, UpscalerConfig(mode, 60)) { info = it }
            assertNotNull("GLES 3 upscaler couldn't start for $mode", renderer)
            renderer!!.setOutput(reader.surface)
            // Two frames: the first also creates the output EGL surface.
            repeat(2) {
                val c = renderer.inputSurface.lockCanvas(null)
                drawPattern(c, 480, 270)
                renderer.inputSurface.unlockCanvasAndPost(c)
                Thread.sleep(150)
            }
            assertTrue("no frame from $mode", got.await(3, TimeUnit.SECONDS))
            renderer.release()
            reader.close()
            rt.quitSafely()
            val b = bmp!!
            File(out, "upscale-${mode.id}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "upscaler $mode → ran ${info?.mode} ${info?.outW}x${info?.outH} draw ${info?.drawMs} ms")
            // The white square in the pattern's top-left must come out white and the background dark.
            val white = b.getPixel(outW * 3 / 20, outH * 3 / 20)
            val dark = b.getPixel(outW * 9 / 10, outH * 9 / 10)
            assertTrue("$mode: expected white, got ${Integer.toHexString(white)}", Color.red(white) > 200 && Color.green(white) > 200)
            assertTrue("$mode: expected dark, got ${Integer.toHexString(dark)}", Color.red(dark) < 80)
            val expected = if (mode == UpscalerMode.OFF) null else mode
            if (expected != null) assertEquals(expected, info?.mode)
        }
    }

    private fun drawPattern(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.rgb(20, 20, 30))
        val p = Paint().apply { isAntiAlias = false }
        p.color = Color.WHITE
        c.drawRect(w * 0.1f, h * 0.1f, w * 0.2f, h * 0.2f, p)
        p.color = Color.rgb(230, 60, 60)
        for (i in 0 until 12) c.drawLine(w * 0.3f + i * 8, h * 0.2f, w * 0.3f + i * 8 + 40, h * 0.8f, p)
        p.textSize = 22f
        p.isAntiAlias = true
        p.color = Color.rgb(120, 220, 140)
        c.drawText("Nebula 1080p→1440p", w * 0.35f, h * 0.55f, p)
    }

    private companion object { const val TAG = "FramegenDeviceTest" }
}
