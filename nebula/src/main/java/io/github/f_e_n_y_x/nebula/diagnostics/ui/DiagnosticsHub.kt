package io.github.f_e_n_y_x.nebula.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.MainActivity
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.diagnostics.CapabilityReport
import io.github.f_e_n_y_x.nebula.diagnostics.ConnectionTimeline
import io.github.f_e_n_y_x.nebula.diagnostics.ControllerTester
import io.github.f_e_n_y_x.nebula.diagnostics.DiagnosticsExport
import io.github.f_e_n_y_x.nebula.diagnostics.Level
import io.github.f_e_n_y_x.nebula.diagnostics.ReportSection
import io.github.f_e_n_y_x.nebula.diagnostics.SessionSummary
import io.github.f_e_n_y_x.nebula.diagnostics.formatDuration
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.screens.Segmented
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DiagTab(val label: String) {
    Report("Report"),
    Controllers("Controller test"),
    Calibration("Stick calibration"),
    Sessions("Sessions"),
    Logs("Export logs"),
}

/**
 * Settings → Diagnostics: the capability report, a live controller test, per-controller stick
 * calibration, the last sessions' stats and a diagnostics zip. Lives inside the Settings pane
 * (which scrolls), so nothing here is lazy.
 */
@Composable
fun DiagnosticsHub() {
    val ctx = LocalContext.current
    val container = ctx.container
    val vm = viewModel { DiagnosticsViewModel(ctx, container) }
    var tabName by rememberSaveable { mutableStateOf(DiagTab.Report.name) }
    val tab = DiagTab.valueOf(tabName)
    var calibrate by rememberSaveable { mutableStateOf<String?>(null) } // descriptor to open in Calibration

    // Gamepads feed the tester only while a controller tab is open and capture is on.
    var capture by remember { mutableStateOf(true) }
    val tester = remember { ControllerTester(ctx) { capture = false } }
    val wantsPads = tab == DiagTab.Controllers || tab == DiagTab.Calibration
    LaunchedEffect(tab) { if (wantsPads) capture = true }
    DisposableEffect(wantsPads) {
        if (wantsPads) tester.start()
        onDispose { if (wantsPads) tester.stop() }
    }
    val activity = ctx.findActivity() as? MainActivity
    DisposableEffect(wantsPads && capture, activity) {
        val on = wantsPads && capture && activity != null && activity.streamInput == null
        if (on) activity!!.streamInput = tester.sink
        onDispose { if (on && activity!!.streamInput === tester.sink) activity.streamInput = null }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Nebula.scale.dp(20))) {
        Segmented(DiagTab.entries.map { it.label to it }, tab) { tabName = it.name }
        when (tab) {
            DiagTab.Report -> ReportTab(vm)
            DiagTab.Controllers -> ControllerTestTab(tester, capture, onCapture = { capture = it }, onCalibrate = { d -> calibrate = d; tabName = DiagTab.Calibration.name })
            DiagTab.Calibration -> CalibrationTab(tester, capture, onCapture = { capture = it }, preselect = calibrate)
            DiagTab.Sessions -> SessionsTab(vm)
            DiagTab.Logs -> LogsTab(vm)
        }
    }
}

// ---------------------------------------------------------------- Report

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportTab(vm: DiagnosticsViewModel) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val report by vm.report.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var copied by remember { mutableStateOf(false) }
    Column(verticalArrangement = gap) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            NebulaButton("Refresh", onClick = { copied = false; vm.refresh(probeHosts = true) }, style = ButtonStyle.Secondary, icon = Icons.Outlined.Refresh)
            NebulaButton(if (copied) "Copied" else "Copy as text", onClick = {
                report?.let { DiagnosticsExport.copyText(ctx, "Nebula report", it.toText()); copied = true }
            }, style = ButtonStyle.Secondary, icon = if (copied) Icons.Outlined.CheckCircle else Icons.Outlined.ContentCopy)
            NebulaButton("Share", onClick = { report?.let { DiagnosticsExport.shareText(ctx, "Nebula diagnostics report", it.toText()) } }, style = ButtonStyle.Secondary, icon = Icons.Outlined.Share)
        }
        val r = report
        if (r == null || loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(s.dp(10)))
                Text(if (r == null) "Reading decoders, displays and hosts…" else "Asking your PCs again…", style = Nebula.type.secondary, color = NebulaColors.textSecondary)
            }
        }
        if (r != null) ReportBody(r)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportBody(r: CapabilityReport) {
    val s = Nebula.scale
    Text("Generated " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(r.generatedAtMs)), style = Nebula.type.label, color = NebulaColors.textMuted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        r.highlights.forEach { h ->
            Pill(h.text, color = if (h.level == Level.INFO) NebulaColors.textSecondary else h.level.color(), background = NebulaColors.raised, leading = { LevelDot(h.level) })
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            // Two balanced columns: each card goes to the shorter one.
            val left = ArrayList<ReportSection>()
            val right = ArrayList<ReportSection>()
            var lh = 0
            var rh = 0
            r.sections.forEach { sec ->
                val h = sec.rows.size + sec.notes.size * 2 + 3
                if (lh <= rh) { left += sec; lh += h } else { right += sec; rh += h }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(14))) {
                Column(Modifier.weight(1f), verticalArrangement = gap) { left.forEach { SectionCard(it) } }
                Column(Modifier.weight(1f), verticalArrangement = gap) { right.forEach { SectionCard(it) } }
            }
        } else {
            Column(verticalArrangement = gap) { r.sections.forEach { SectionCard(it) } }
        }
    }
}

@Composable
private fun SectionCard(sec: ReportSection) {
    DiagCard {
        SectionTitle(sec.title)
        sec.rows.forEach { KeyValue(it.label, it.value, it.level) }
        sec.notes.forEach { Note(it.value, it.level) }
    }
}

// ---------------------------------------------------------------- Sessions

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionsTab(vm: DiagnosticsViewModel) {
    val s = Nebula.scale
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val hosts by LocalContext.current.container.hosts.observeHosts().collectAsStateWithLifecycle(emptyList())
    var confirm by remember { mutableStateOf(false) }
    Column(verticalArrangement = gap, modifier = Modifier.widthIn(max = s.dp(900))) {
        Hint("A summary of each of the last ${io.github.f_e_n_y_x.nebula.diagnostics.SessionHistory.KEEP} streams, kept on this device, so a bad night can be compared with a good one.")
        if (sessions.isEmpty()) {
            EmptyState(Icons.Outlined.History, "No sessions yet", "Stream a game and its frame rate, latency, bitrate and loss land here when it ends.")
        } else {
            sessions.forEach { SessionCard(it, hosts.firstOrNull { h -> h.id == it.hostId }?.name) }
            NebulaButton("Clear history", onClick = { confirm = true }, style = ButtonStyle.Ghost)
        }
    }
    if (confirm) {
        NebulaConfirmDialog("Clear session history?", "The last sessions' summaries are deleted from this device.", "Clear", onConfirm = { confirm = false; vm.clearSessions() }, onDismiss = { confirm = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(x: SessionSummary, hostName: String?) {
    val s = Nebula.scale
    val date = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault()).format(Date(x.startedAtMs))
    DiagCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(x.game, style = Nebula.type.heading, color = NebulaColors.text)
                Text(listOfNotNull(date, hostName, x.mode).joinToString(" · "), style = Nebula.type.label, color = NebulaColors.textMuted)
            }
            Pill(
                if (x.failed) "Failed" else formatDuration(x.durationS),
                color = if (x.failed) NebulaColors.danger else NebulaColors.textSecondary,
                background = if (x.failed) NebulaColors.dangerTint else NebulaColors.raised,
            )
        }
        if (x.samples > 0) {
            Spacer(Modifier.height(s.dp(12)))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
                val tile = Modifier.widthIn(min = s.dp(128))
                StatTile("Frame rate", "%.0f fps".format(x.avgFps), "min %.0f".format(x.minFps), tile)
                StatTile("Latency", "%.1f ms".format(x.avgLatencyMs), "p95 %.1f ms".format(x.p95LatencyMs), tile)
                StatTile("Bitrate", "%.1f Mbps".format(x.avgBitrateMbps), null, tile)
                StatTile(
                    "Loss", "%.2f %%".format(x.avgLossPercent), "max %.1f %%".format(x.maxLossPercent), tile,
                    valueColor = if (x.maxLossPercent >= 5f) NebulaColors.warning else NebulaColors.text,
                )
                StatTile("Connect", x.connectMs?.let { "%.1f s".format(it / 1000f) } ?: "—", null, tile)
            }
            Spacer(Modifier.height(s.dp(10)))
            Text("Host %.1f · network %.1f · decode %.1f ms".format(x.avgHostMs, x.avgNetworkMs, x.avgDecodeMs), style = Nebula.type.mono, color = NebulaColors.textSecondary)
            Text(listOf(x.resolution, x.codec, x.decoder.ifEmpty { "decoder —" }).joinToString(" · ") + if (x.resolutionChanges > 0) " · ${x.resolutionChanges} live change(s)" else "", style = Nebula.type.mono, color = NebulaColors.textMuted)
        }
        x.endReason?.let { Note(it, if (x.failed) Level.BAD else Level.INFO) }
    }
}

// ---------------------------------------------------------------- Logs

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogsTab(vm: DiagnosticsViewModel) {
    val s = Nebula.scale
    val export by vm.export.collectAsStateWithLifecycle()
    val attempts by vm.attempts.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf(15) }
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(22)), modifier = Modifier.widthIn(max = s.dp(760))) {
        io.github.f_e_n_y_x.nebula.ui.screens.Setting("Log window", "How far back Nebula's log goes in the zip. Android keeps only a few MB, so older lines may already be gone.") {
            Segmented(listOf("5 min" to 5, "15 min" to 15, "30 min" to 30, "1 hour" to 60), minutes) { minutes = it }
        }
        DiagCard {
            SectionTitle("In the zip")
            DiagnosticsExport.CONTENTS.forEach { (name, what) ->
                Column(Modifier.fillMaxWidth().padding(vertical = s.dp(4))) {
                    Text(name, style = Nebula.type.mono, color = NebulaColors.text)
                    Text(what, style = Nebula.type.label, color = NebulaColors.textMuted)
                }
            }
            Note("Pairing keys, certificates and host secrets are never included. Nothing leaves this device until you share it.", Level.OK)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            NebulaButton("Share zip", onClick = { vm.exportZip(minutes, save = false) }, icon = Icons.Outlined.Share)
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                NebulaButton("Save to Downloads", onClick = { vm.exportZip(minutes, save = true) }, style = ButtonStyle.Secondary, icon = Icons.Outlined.SaveAlt)
            }
        }
        when (val e = export) {
            DiagnosticsViewModel.ExportState.Idle -> Unit
            DiagnosticsViewModel.ExportState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
                Spacer(Modifier.width(s.dp(10)))
                Text("Collecting…", style = Nebula.type.secondary, color = NebulaColors.textSecondary)
            }
            is DiagnosticsViewModel.ExportState.Ready -> Note(e.message, Level.OK)
            is DiagnosticsViewModel.ExportState.Failed -> Note(e.message, Level.BAD)
        }
        ConnectionTimings(attempts)
    }
}

@Composable
private fun ConnectionTimings(attempts: List<ConnectionTimeline.Attempt>) {
    val s = Nebula.scale
    DiagCard {
        SectionTitle("Connection timings")
        if (attempts.isEmpty()) {
            Text("No connection attempts since Nebula started. Start a stream and each stage's time shows here.", style = Nebula.type.secondary, color = NebulaColors.textSecondary)
            return@DiagCard
        }
        attempts.asReversed().take(3).forEachIndexed { i, a ->
            if (i > 0) Spacer(Modifier.height(s.dp(14)))
            val level = when (a.outcome) {
                ConnectionTimeline.Outcome.CONNECTED -> Level.OK
                ConnectionTimeline.Outcome.FAILED -> Level.BAD
                ConnectionTimeline.Outcome.RUNNING -> Level.INFO
                ConnectionTimeline.Outcome.ENDED -> Level.WARN
            }
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(a.startedAtWallMs))
            KeyValue("$time  ${a.label}", a.outcome.name.lowercase().replaceFirstChar { it.uppercase() } + (a.totalMs?.let { " in $it ms" } ?: ""), level)
            val parts = ConnectionTimeline.durations(a)
            val max = parts.maxOfOrNull { it.second ?: 0L }?.coerceAtLeast(1L) ?: 1L
            parts.forEach { (name, ms) -> StageBar(name, ms, max) }
            a.failure?.let { Note(it, level) }
        }
    }
}

@Composable
private fun StageBar(name: String, ms: Long?, max: Long) {
    val s = Nebula.scale
    Row(Modifier.fillMaxWidth().padding(start = s.dp(14), top = s.dp(4)), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Nebula.type.label, color = NebulaColors.textSecondary, modifier = Modifier.weight(0.42f), maxLines = 1)
        Box(Modifier.weight(0.4f).padding(end = s.dp(10)).height(s.dp(6)).background(NebulaColors.raised, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxWidth(((ms ?: 0L).toFloat() / max).coerceIn(0.02f, 1f)).height(s.dp(6)).background(NebulaColors.accent, RoundedCornerShape(50)))
        }
        Text(ms?.let { "$it ms" } ?: "…", style = Nebula.type.mono, color = NebulaColors.text, modifier = Modifier.weight(0.18f))
    }
}
