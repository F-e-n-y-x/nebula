package io.github.fenyx.nebula.engine.framegen

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
            )
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

/**
 * Runs the device check in the separate test process and saves the report. One at a time per
 * process; [state] is shared so the Settings page and the stream menu see the same run.
 */
object FramegenSelfTestRunner {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow<SelfTestState>(SelfTestState.Idle)
    val state: StateFlow<SelfTestState> = _state.asStateFlow()

    /** Engine revision that the report's fingerprint includes; bump when the native pipeline changes. */
    const val NATIVE_REVISION = "nebula-fg-1"

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
     * Starts a check unless one is running. The benchmark runs at LSFG's internal size for a
     * [streamWidth]x[streamHeight] stream with the current settings. Call on the main thread.
     */
    fun start(ctx: Context, streamWidth: Int, streamHeight: Int) {
        if (_state.value is SelfTestState.Running) return
        val app = ctx.applicationContext
        val prefs = FramegenDll.prefs(app)
        val config = FramegenConfig.from(prefs.all)
        val fp = fingerprint(app)
        val machine = SelfTestMachine(fp, SelfTestEnvironment(Build.VERSION.SDK_INT, Build.SUPPORTED_ABIS.toList(), nativeLoaded = true))
        _state.value = machine.start()

        val w = config.internalWidth(streamWidth)
        val h = (w.toLong() * streamHeight.coerceAtLeast(1) / streamWidth.coerceAtLeast(1)).toInt().coerceAtLeast(64)
        var connection: ServiceConnection? = null
        fun finish(s: SelfTestState) {
            _state.value = s
            if (s is SelfTestState.Done) {
                prefs.edit().putString(FramegenKeys.SELF_TEST_REPORT, s.report.toJson()).apply()
                LimeLog.info("Framegen self-test: ${s.report.verdict} ${s.report.summary}")
                main.removeCallbacksAndMessages(TIMEOUT_TOKEN)
                connection?.let { c -> runCatching { app.unbindService(c) } }
                connection = null
            }
        }
        val replies = Messenger(object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    FramegenSelfTestService.MSG_STAGE -> _state.value = machine.stage(msg.data.getString(FramegenSelfTestService.KEY_STAGE).orEmpty())
                    FramegenSelfTestService.MSG_DONE -> finish(machine.done(msg.data.toRaw()))
                }
            }
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
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
        connection = conn
        val bound = runCatching { app.bindService(Intent(app, FramegenSelfTestService::class.java), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) {
            finish(machine.crashed())
            return
        }
        main.postAtTime({ finish(machine.timedOut()) }, TIMEOUT_TOKEN, android.os.SystemClock.uptimeMillis() + SelfTestMachine.TIMEOUT_MS)
    }

    /** Forgets the saved report (after the DLL changed, or to re-run on request). */
    fun clear(ctx: Context) {
        FramegenDll.prefs(ctx).edit().remove(FramegenKeys.SELF_TEST_REPORT).apply()
        if (_state.value is SelfTestState.Done) _state.value = SelfTestState.Idle
    }

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
