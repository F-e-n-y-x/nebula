package io.github.fenyx.nebula.engine.framegen

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import com.limelight.framegen.FramegenInterceptor

/**
 * Runs the frame generation device check in its own process (":framegen_selftest", see the
 * engine manifest), so a Vulkan driver or DLL translation crash only kills this process and the
 * app reports "crashed during …" instead of dying. It also keeps LSFG's global state out of the
 * app process. The process exits after answering.
 *
 * Protocol (Messenger): the client sends [MSG_RUN] with [KEY_DLL_PATH], [KEY_WIDTH], [KEY_HEIGHT],
 * [KEY_FLOW_SCALE] and [KEY_PERFORMANCE]. The service replies [MSG_STAGE] with [KEY_STAGE] before
 * each step and [MSG_DONE] with the raw outputs ([KEY_CAPS], [KEY_DLL_PROBE], [KEY_BENCHMARK]).
 */
class FramegenSelfTestService : Service() {
    private lateinit var worker: HandlerThread

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("FramegenSelfTest").apply { start() }
    }

    private val incoming by lazy {
        Messenger(object : Handler(worker.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what != MSG_RUN) return
                val reply = msg.replyTo ?: return
                run(msg.data, reply)
            }
        })
    }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    private fun run(args: Bundle, reply: Messenger) {
        fun send(what: Int, data: Bundle) = runCatching { reply.send(Message.obtain(null, what).apply { this.data = data }) }
        fun stage(name: String) = send(MSG_STAGE, Bundle().apply { putString(KEY_STAGE, name) })

        val out = Bundle()
        stage(STAGE_ENVIRONMENT)
        val native = FramegenInterceptor.isAvailable()
        out.putInt(KEY_SDK, Build.VERSION.SDK_INT)
        out.putStringArray(KEY_ABIS, Build.SUPPORTED_ABIS)
        out.putBoolean(KEY_NATIVE, native)
        if (native) {
            stage(STAGE_CAPS)
            out.putString(KEY_CAPS, FramegenInterceptor.probeDeviceCaps())
            val dll = args.getString(KEY_DLL_PATH)
            if (dll != null) {
                stage(STAGE_DLL)
                val probe = FramegenInterceptor().probeLosslessDll(dll)
                out.putString(KEY_DLL_PROBE, probe)
                if (probe.startsWith("lossless-dll-ok")) {
                    stage(STAGE_BENCHMARK)
                    FramegenInterceptor.configureLosslessDllPath(dll)
                    out.putString(
                        KEY_BENCHMARK,
                        FramegenInterceptor.runBenchmark(
                            args.getInt(KEY_WIDTH, 640),
                            args.getInt(KEY_HEIGHT, 360),
                            BENCHMARK_FRAMES,
                            args.getFloat(KEY_FLOW_SCALE, 1f),
                            args.getBoolean(KEY_PERFORMANCE, false),
                        ),
                    )
                }
            }
        }
        send(MSG_DONE, out)
        // Free the Vulkan device and LSFG state for good.
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
    }

    override fun onDestroy() {
        worker.quitSafely()
        super.onDestroy()
    }

    companion object {
        const val MSG_RUN = 1
        const val MSG_STAGE = 2
        const val MSG_DONE = 3
        const val KEY_DLL_PATH = "dll"
        const val KEY_WIDTH = "w"
        const val KEY_HEIGHT = "h"
        const val KEY_FLOW_SCALE = "flow"
        const val KEY_PERFORMANCE = "perf"
        const val KEY_STAGE = "stage"
        const val KEY_SDK = "sdk"
        const val KEY_ABIS = "abis"
        const val KEY_NATIVE = "native"
        const val KEY_CAPS = "caps"
        const val KEY_DLL_PROBE = "dllProbe"
        const val KEY_BENCHMARK = "benchmark"
        const val STAGE_ENVIRONMENT = "environment"
        const val STAGE_CAPS = "caps"
        const val STAGE_DLL = "dll"
        const val STAGE_BENCHMARK = "benchmark"
        const val BENCHMARK_FRAMES = 120
    }
}
