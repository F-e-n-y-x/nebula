package io.github.fenyx.nebula.engine.framegen

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.limelight.LimeLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the device check is. */
sealed interface SelfTestState {
    data object Idle : SelfTestState
    data class Running(val stage: String) : SelfTestState
    data class Done(val report: SelfTestReport) : SelfTestState
}

/** Raw outputs of the test process (what [FramegenSelfTestService] sends back). */
data class SelfTestRaw(
    val env: SelfTestEnvironment,
    val caps: String?,
    val dllProbe: String?,
    val benchmark: String?,
)

/**
 * The self-test's state machine, without Android IPC so it can be unit-tested: stages advance,
 * then exactly one of [done], [crashed] or [timedOut] finishes it. Anything after the finish is
 * ignored (a late reply after a timeout, a binder death after the reply).
 */
class SelfTestMachine(
    private val fingerprint: String,
    private val hostEnv: SelfTestEnvironment,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state: SelfTestState = SelfTestState.Idle
        private set

    fun start(): SelfTestState = SelfTestState.Running(FramegenSelfTestService.STAGE_ENVIRONMENT).also { state = it }

    fun stage(name: String): SelfTestState {
        if (state is SelfTestState.Running) state = SelfTestState.Running(name)
        return state
    }

    fun done(raw: SelfTestRaw): SelfTestState = finish {
        SelfTestEvaluator.evaluate(raw.env, raw.caps, raw.dllProbe, raw.benchmark, fingerprint, now())
    }

    /** The test process died during the current stage (typically a driver crash). */
    fun crashed(): SelfTestState {
        val stage = (state as? SelfTestState.Running)?.stage ?: return state
        if (stage == FramegenSelfTestService.STAGE_ENVIRONMENT) {
            return finish {
                SelfTestReport(
                    SelfTestVerdict.UNSUPPORTED,
                    listOf(SelfTestCheck("native", "Frame generation engine library", false, "loading the engine crashed the test process")),
                    null, "", fingerprint, now(),
                )
            }
        }
        return finish {
            // Everything before the crashing stage passed, so the report blames that stage.
            SelfTestEvaluator.evaluate(
                hostEnv,
                caps = if (stage == FramegenSelfTestService.STAGE_CAPS) null else CAPS_UNKNOWN_OK,
                dllProbe = when (stage) {
                    FramegenSelfTestService.STAGE_DLL -> "crashed"
                    FramegenSelfTestService.STAGE_BENCHMARK -> "lossless-dll-ok (translated before the crash)"
                    else -> null
                },
                benchmark = if (stage == FramegenSelfTestService.STAGE_BENCHMARK) "error=crashed" else null,
                fingerprint = fingerprint,
                nowMs = now(),
                crashedAt = when (stage) {
                    FramegenSelfTestService.STAGE_CAPS -> "caps"
                    FramegenSelfTestService.STAGE_DLL -> "dll"
                    FramegenSelfTestService.STAGE_BENCHMARK -> "benchmark"
                    else -> "caps"
                },
            ).let { r -> r.copy(raw = mapOf("crashed_at" to stage)) } // the stand-in lines are not real output
        }
    }

    fun timedOut(): SelfTestState = finish {
        SelfTestReport(
            SelfTestVerdict.FAILED,
            listOf(SelfTestCheck("timeout", "Finish in time", false, "the check took longer than ${TIMEOUT_MS / 1000} s during ${(state as? SelfTestState.Running)?.stage}")),
            null, "", fingerprint, now(),
        )
    }

    private inline fun finish(report: () -> SelfTestReport): SelfTestState {
        if (state !is SelfTestState.Running) return state
        return SelfTestState.Done(report()).also { state = it }
    }

    companion object {
        const val TIMEOUT_MS = 60_000L
        /** Stand-in caps line when the crash happened after the caps stage passed. */
        private const val CAPS_UNKNOWN_OK = "vulkan=1 api=1.1.0 ahb=1 external_memory=1 dedicated=1 ycbcr=1 robustness2=1 null_descriptor=1"
    }
}

/** What [FramegenSelfTestRunner.start] did. */
enum class SelfTestStart {
    STARTED,
    ALREADY_RUNNING,
    /** A stream is running: the check never runs during one (see [SelfTestScheduler]). */
    REFUSED_STREAMING,
}

/**
 * Runs the device check in the separate test process and saves the report. One at a time per
 * process; [state] is shared so the Settings page and the stream menu see the same run.
 *
 * Never during a stream: StreamSession reports [onStreamStarted] / [onStreamEnded]. A check
 * requested during a stream is refused ([SelfTestStart.REFUSED_STREAMING]); [runAfterStream]
 * queues it for the end of the stream. A check still running when a stream starts is stopped (its
 * process killed) and run again afterwards.
 */
object FramegenSelfTestRunner {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<SelfTestState>(SelfTestState.Idle)
    val state: StateFlow<SelfTestState> = _state.asStateFlow()

    private val scheduler = SelfTestScheduler()
    private val _streaming = MutableStateFlow(false)

    /** True while a stream runs (the check is refused then). */
    val streaming: StateFlow<Boolean> = _streaming.asStateFlow()
    private val _queued = MutableStateFlow(false)

    /** True when a check will run as soon as the stream ends. */
    val queuedAfterStream: StateFlow<Boolean> = _queued.asStateFlow()

    /** The run in progress; callbacks from an older (stopped) run are ignored. */
    private class Run(val request: SelfTestScheduler.Request, val machine: SelfTestMachine) {
        var connection: ServiceConnection? = null
    }
    private var current: Run? = null

    /** Engine revision that the report's fingerprint includes; bump when the native pipeline changes. */
    const val NATIVE_REVISION = "nebula-fg-1"

    /** Pause between the end of a stream and a queued check, so the decoder and GPU are released. */
    private const val AFTER_STREAM_DELAY_MS = 2_000L

    fun fingerprint(ctx: Context): String {
        val prefs = FramegenDll.prefs(ctx)
        return listOf(Build.FINGERPRINT, FramegenDll.stagedSha(prefs) ?: "no-dll", NATIVE_REVISION).joinToString("|")
    }

    /** The saved verdict for this device, DLL and engine; NOT_RUN when stale or missing. */
    fun verdict(ctx: Context): SelfTestVerdict =
        SelfTestReport.verdictFor(FramegenDll.prefs(ctx).getString(FramegenKeys.SELF_TEST_REPORT, null), fingerprint(ctx))

    fun savedReport(ctx: Context): SelfTestReport? =
        SelfTestReport.fromJson(FramegenDll.prefs(ctx).getString(FramegenKeys.SELF_TEST_REPORT, null))
            ?.takeIf { it.fingerprint == fingerprint(ctx) }

    /**
     * Starts a check unless one is running or a stream is. The benchmark runs at LSFG's internal
     * size for a [streamWidth]x[streamHeight] stream with the current settings. Call on the main thread.
     */
    fun start(ctx: Context, streamWidth: Int, streamHeight: Int): SelfTestStart =
        when (scheduler.request(running = current != null)) {
            SelfTestScheduler.Outcome.ALREADY_RUNNING -> SelfTestStart.ALREADY_RUNNING
            SelfTestScheduler.Outcome.REFUSED_STREAMING -> {
                LimeLog.info("Framegen self-test refused: a stream is running")
                SelfTestStart.REFUSED_STREAMING
            }
            SelfTestScheduler.Outcome.START -> {
                launch(ctx.applicationContext, SelfTestScheduler.Request(streamWidth, streamHeight))
                SelfTestStart.STARTED
            }
        }

    /** Runs the check when the current stream ends (now when none is running). Main thread. */
    fun runAfterStream(ctx: Context, streamWidth: Int, streamHeight: Int) {
        if (scheduler.runAfterStream(SelfTestScheduler.Request(streamWidth, streamHeight))) {
            _queued.value = true
            LimeLog.info("Framegen self-test queued for the end of the stream")
        } else {
            start(ctx, streamWidth, streamHeight)
        }
    }

    fun cancelQueued() {
        scheduler.cancelPending()
        _queued.value = false
    }

    /** A stream is starting (main thread). Stops a running check; it runs again after the stream. */
    fun onStreamStarted(ctx: Context) {
        val run = current
        _streaming.value = true
        if (scheduler.streamStarted(run?.request)) {
            LimeLog.warning("Framegen self-test stopped: a stream is starting; it runs again when the stream ends")
            abort(ctx.applicationContext)
            _queued.value = scheduler.pending != null
        }
    }

    /** The stream ended (main thread). Starts a queued check after a short pause. */
    fun onStreamEnded(ctx: Context) {
        val next = scheduler.streamEnded()
        _streaming.value = scheduler.streaming
        _queued.value = scheduler.pending != null
        next ?: return
        val app = ctx.applicationContext
        main.postDelayed({ start(app, next.width, next.height) }, AFTER_STREAM_DELAY_MS)
    }

    private fun launch(app: Context, request: SelfTestScheduler.Request) {
        val prefs = FramegenDll.prefs(app)
        val config = FramegenConfig.from(prefs.all)
        val fp = fingerprint(app)
        val machine = SelfTestMachine(fp, SelfTestEnvironment(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), nativeLoaded = true))
        val run = Run(request, machine)
        current = run
        _state.value = machine.start()

        val w = config.internalWidth(request.width)
        val h = (w.toLong() * request.height.coerceAtLeast(1) / request.width.coerceAtLeast(1)).toInt().coerceAtLeast(64)
        fun finish(s: SelfTestState) {
            if (current !== run) return
            _state.value = s
            if (s is SelfTestState.Done) {
                current = null
                prefs.edit().putString(FramegenKeys.SELF_TEST_REPORT, s.report.toJson()).apply()
                log(s.report)
                main.removeCallbacksAndMessages(TIMEOUT_TOKEN)
                run.connection?.let { c -> runCatching { app.unbindService(c) } }
                run.connection = null
            }
        }
        val replies = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (current !== run) return
                when (msg.what) {
                    FramegenSelfTestService.MSG_STAGE -> _state.value = machine.stage(msg.data.getString(FramegenSelfTestService.KEY_STAGE).orEmpty())
                    FramegenSelfTestService.MSG_DONE -> finish(machine.done(msg.data.toRaw()))
                }
            }
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                if (current !== run) return
                runCatching {
                    service.linkToDeath({ main.post { finish(machine.crashed()) } }, 0)
                    Messenger(service).send(Message.obtain(null, FramegenSelfTestService.MSG_RUN).apply {
                        replyTo = replies
                        data = Bundle().apply {
                            putString(FramegenSelfTestService.KEY_DLL_PATH, config.dllPath)
                            putInt(FramegenSelfTestService.KEY_WIDTH, w)
                            putInt(FramegenSelfTestService.KEY_HEIGHT, h)
                            putFloat(FramegenSelfTestService.KEY_FLOW_SCALE, config.flowScale)
                            putBoolean(FramegenSelfTestService.KEY_PERFORMANCE, config.performanceMode)
                        }
                    })
                }.onFailure { finish(machine.crashed()) }
            }

            override fun onServiceDisconnected(name: ComponentName?) = finish(machine.crashed())
        }
        run.connection = conn
        val bound = runCatching { app.bindService(Intent(app, FramegenSelfTestService::class.java), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) {
            finish(machine.crashed())
            return
        }
        main.postAtTime({ finish(machine.timedOut()) }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + SelfTestMachine.TIMEOUT_MS)
    }

    /** Stops the running check without saving a report, and kills its process. */
    private fun abort(app: Context) {
        val run = current ?: return
        current = null
        main.removeCallbacksAndMessages(TIMEOUT_TOKEN)
        run.connection?.let { c -> runCatching { app.unbindService(c) } }
        run.connection = null
        runCatching {
            app.getSystemService(ActivityManager::class.java)?.runningAppProcesses
                ?.filter { it.processName.endsWith(SELF_TEST_PROCESS_SUFFIX) }
                ?.forEach { android.os.Process.killProcess(it.pid) }
        }.onFailure { LimeLog.warning("Framegen self-test: couldn't stop the test process: ${it.message}") }
        _state.value = SelfTestState.Idle
    }

    /** The whole report goes to the app log so a failure can be reported from the log alone. */
    private fun log(report: SelfTestReport) {
        val text = report.fullText()
        if (report.verdict == SelfTestVerdict.PASSED || report.verdict == SelfTestVerdict.SLOW) {
            LimeLog.info("Framegen self-test: ${report.verdict} ${report.summary}")
        } else {
            report.checks.filter { !it.passed }.forEach { LimeLog.warning("Framegen self-test failed: ${it.title}: ${it.detail}") }
        }
        text.lines().forEach { LimeLog.info("Framegen self-test | $it") }
    }

    /** Forgets the saved report (after the DLL changed, or to re-run on request). */
    fun clear(ctx: Context) {
        FramegenDll.prefs(ctx).edit().remove(FramegenKeys.SELF_TEST_REPORT).apply()
        if (_state.value is SelfTestState.Done) _state.value = SelfTestState.Idle
    }

    private const val SELF_TEST_PROCESS_SUFFIX = ":framegen_selftest"
    private val TIMEOUT_TOKEN = Any()

    private fun Bundle.toRaw() = SelfTestRaw(
        env = SelfTestEnvironment(
            getInt(FramegenSelfTestService.KEY_SDK),
            getStringArray(FramegenSelfTestService.KEY_ABIS)?.toList().orEmpty(),
            getBoolean(FramegenSelfTestService.KEY_NATIVE),
        ),
        caps = getString(FramegenSelfTestService.KEY_CAPS),
        dllProbe = getString(FramegenSelfTestService.KEY_DLL_PROBE),
        benchmark = getString(FramegenSelfTestService.KEY_BENCHMARK),
    )
}
