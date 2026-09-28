package io.github.f_e_n_y_x.nebula.framegen

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.domain.model.FramegenState
import io.github.f_e_n_y_x.nebula.domain.model.PostProcessStats
import io.github.fenyx.nebula.engine.framegen.FramegenDll
import io.github.fenyx.nebula.engine.framegen.FramegenEvent
import io.github.fenyx.nebula.engine.framegen.FramegenOffReason
import io.github.fenyx.nebula.engine.framegen.FramegenSelfTestRunner
import io.github.fenyx.nebula.engine.framegen.FramegenStatus
import io.github.fenyx.nebula.engine.framegen.GuardDecision
import io.github.fenyx.nebula.engine.upscale.UpscalerStatus

/** App-side frame generation: stage the bundled DLL, tell the user about auto-off. */
object FramegenAppSetup {
    private const val TAG = "NebulaFramegen"
    const val CHANNEL = "framegen"
    private const val AUTO_OFF_ID = 4201

    /** True in the ":framegen_selftest" process, which must not build the app's object graph. */
    fun isSelfTestProcess(): Boolean =
        if (Build.VERSION.SDK_INT >= 28) Application.getProcessName().endsWith(":framegen_selftest") else false

    fun onAppStart(ctx: Context) {
        Thread({
            runCatching {
                val prefs = FramegenDll.prefs(ctx)
                if (FramegenDll.ensureStaged(ctx, prefs)) {
                    // A different DLL invalidates the old device check.
                    FramegenSelfTestRunner.clear(ctx)
                }
            }.onFailure { Log.w(TAG, "Staging the bundled Lossless.dll failed: ${it.message}") }
        }, "FramegenDllStage").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Stream events from the engine (main thread). */
    fun onEvent(ctx: Context, event: FramegenEvent) {
        when (event) {
            is FramegenEvent.AutoOff -> {
                val title = if (event.cause == GuardDecision.Cause.THERMAL) "Frame generation off: device too hot" else "Frame generation off: device too slow"
                val text = "${event.detail.replaceFirstChar { it.uppercase() }}. The stream continues at its own frame rate; turn it back on from the stream menu."
                Toast.makeText(ctx, title, Toast.LENGTH_LONG).show()
                notify(ctx, title, text)
            }
            is FramegenEvent.Unavailable -> if (event.reason != FramegenOffReason.DISABLED) {
                Toast.makeText(ctx, event.reason.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun notify(ctx: Context, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Frame generation", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When frame generation turns itself off during a stream"
                },
            )
        }
        nm.notify(
            AUTO_OFF_ID,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_nebula_monochrome)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }
}

/** Engine frame generation + upscaler status → the domain's [PostProcessStats]. */
fun postProcessStats(fg: FramegenStatus, up: UpscalerStatus): PostProcessStats = PostProcessStats(
    framegen = when (fg.state) {
        FramegenStatus.State.OFF -> FramegenState.OFF
        FramegenStatus.State.STARTING -> FramegenState.STARTING
        FramegenStatus.State.ACTIVE -> FramegenState.ACTIVE
        FramegenStatus.State.PAUSED -> FramegenState.PAUSED
        FramegenStatus.State.AUTO_OFF -> FramegenState.AUTO_OFF
    },
    presentedFps = if (fg.generating) fg.presentedFps else 0f,
    inputFps = fg.inputFps,
    targetFps = fg.targetFps,
    multiplier = fg.multiplier,
    lsfgMs = fg.lsfgMs,
    addedLatencyMs = fg.addedLatencyMs,
    addedLatencyFrames = fg.addedLatencyFrames,
    model = fg.model,
    note = fg.autoOffDetail ?: fg.reason?.takeIf { fg.state != FramegenStatus.State.ACTIVE && it != FramegenOffReason.DISABLED }?.message,
    soak = fg.soak,
    upscaler = up.active.id,
    upscalerLabel = up.active.label,
    upscaleMs = up.renderMs,
    upscaleOut = if (up.outputWidth > 0) "${up.inputWidth}×${up.inputHeight} → ${up.outputWidth}×${up.outputHeight}" else "",
)
