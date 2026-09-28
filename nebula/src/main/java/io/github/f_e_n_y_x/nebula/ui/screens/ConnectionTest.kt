package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.f_e_n_y_x.nebula.domain.model.AbrInfo
import io.github.f_e_n_y_x.nebula.domain.model.AbrSource
import io.github.f_e_n_y_x.nebula.domain.model.ConnectionReport
import io.github.f_e_n_y_x.nebula.domain.model.LinkQuality
import io.github.f_e_n_y_x.nebula.domain.model.SuggestedSettings
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** Where a "Test connection" run is. */
sealed interface ConnectionTestUi {
    data object Idle : ConnectionTestUi

    /** [progress] is 0..1 during the download, null while measuring latency. */
    data class Running(val progress: Float?) : ConnectionTestUi
    data class Done(val report: ConnectionReport) : ConnectionTestUi
    data class Failed(val message: String) : ConnectionTestUi
}

/** Text for the numbers of a report, shared by the host dialog, the stream menu and tests. */
object ConnectionText {
    fun quality(q: LinkQuality) = when (q) {
        LinkQuality.EXCELLENT -> "Excellent"
        LinkQuality.GOOD -> "Good"
        LinkQuality.FAIR -> "Fair"
        LinkQuality.POOR -> "Poor"
    }

    fun rtt(r: ConnectionReport) = "%.0f ms".format(r.rttMs)
    fun jitter(r: ConnectionReport) = "%.1f ms".format(r.jitterMs)
    fun loss(r: ConnectionReport) = r.lossPercent?.let { "%.1f%%".format(it) } ?: "—"
    fun throughput(r: ConnectionReport) = r.throughputMbps?.let { "%.0f Mbps".format(it) } ?: "—"

    fun suggestion(s: SuggestedSettings) =
        "${s.mode.width}×${s.mode.height} at ${s.mode.fps} fps, ${formatMbps(s.bitrateKbps)}" + if (s.nativeResolution) " (this screen)" else ""

    fun formatMbps(kbps: Int) = if (kbps % 1000 == 0) "${kbps / 1000} Mbps" else "%.1f Mbps".format(kbps / 1000f)

    /** One line for the stats overlay: the bitrate the PC encodes at, and who sets it. */
    fun bitrateLine(targetKbps: Int, abr: AbrInfo?): String? {
        if (targetKbps <= 0) return null
        val base = "Target ${formatMbps(targetKbps)}"
        abr ?: return base
        val who = when (abr.source) {
            AbrSource.HOST -> "PC"
            AbrSource.LOCAL -> "this device"
            AbrSource.CONNECTING -> "starting"
        }
        return "$base · ABR ${abr.mode.lowercase()} ($who)"
    }

    /** Menu text for adaptive bitrate: range and the last reason it changed. */
    fun abrDetail(abr: AbrInfo): String {
        val who = when (abr.source) {
            AbrSource.HOST -> "Your PC adjusts it"
            AbrSource.LOCAL -> "This device adjusts it (the PC has no adaptive bitrate)"
            AbrSource.CONNECTING -> "Turning it on at the PC"
        }
        val last = abr.lastReason?.takeIf { it.isNotBlank() }?.let { " · last change: $it" } ?: ""
        return "Adaptive bitrate ${abr.mode.lowercase()}: $who, between ${formatMbps(abr.minKbps)} and ${formatMbps(abr.maxKbps)}$last."
    }

    /** Result text for screen readers and the note under the numbers. */
    fun summary(r: ConnectionReport): String = buildString {
        append("${quality(r.quality)} connection. RTT ${rtt(r)}, jitter ${jitter(r)}")
        r.lossPercent?.let { append(", loss ${loss(r)}") }
        r.throughputMbps?.let { append(", ${throughput(r)}") }
        append(".")
    }

    fun note(r: ConnectionReport): String = when {
        r.duringStream -> "Measured during the stream: throughput is tested between streams, and loss is this stream's recent frame loss."
        r.lossPercent == null -> "This PC doesn't report packet loss; Nova does."
        else -> "Suggestions keep a third of the measured throughput spare for Wi-Fi dips."
    }
}

/** Numbers and suggestion of a finished test. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionReportCard(report: ConnectionReport, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Column(
        modifier.fillMaxWidth().background(NebulaColors.surface, shape).padding(s.dp(14))
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        Text(
            ConnectionText.quality(report.quality) + " connection",
            style = Nebula.type.bodyStrong,
            color = when (report.quality) {
                LinkQuality.EXCELLENT, LinkQuality.GOOD -> NebulaColors.success
                LinkQuality.FAIR -> NebulaColors.warning
                LinkQuality.POOR -> NebulaColors.danger
            },
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(22)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Metric("RTT", ConnectionText.rtt(report))
            Metric("Jitter", ConnectionText.jitter(report))
            Metric("Loss", ConnectionText.loss(report))
            if (!report.duringStream) Metric("Throughput", ConnectionText.throughput(report))
        }
        Text(
            report.suggestion?.let { "Suggested: " + ConnectionText.suggestion(it) } ?: "The link is too slow to suggest settings.",
            style = Nebula.type.body, color = NebulaColors.text,
        )
        Text(ConnectionText.note(report), style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, style = Nebula.type.bodyStrong, color = NebulaColors.text)
        Text(label, style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

/** Progress or failure line while a test runs. */
@Composable
fun ConnectionTestProgress(state: ConnectionTestUi) {
    val s = Nebula.scale
    when (state) {
        is ConnectionTestUi.Running -> Column(verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
            Text(if (state.progress == null) "Measuring latency…" else "Measuring throughput…", style = Nebula.type.label, color = NebulaColors.textSecondary)
            val p = state.progress
            if (p == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = NebulaColors.accent, trackColor = NebulaColors.raised)
            else LinearProgressIndicator(progress = { p.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = NebulaColors.accent, trackColor = NebulaColors.raised)
        }
        is ConnectionTestUi.Failed -> Text(state.message, style = Nebula.type.body, color = NebulaColors.warning, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        else -> Unit
    }
}

/**
 * The host page's "Test connection" dialog: runs on open, shows the numbers, and offers to make
 * the suggestion the stream settings.
 */
@Composable
fun ConnectionTestDialog(hostName: String, state: ConnectionTestUi, onRetry: () -> Unit, onUse: (SuggestedSettings) -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NebulaColors.raised,
        titleContentColor = NebulaColors.text,
        textContentColor = NebulaColors.textSecondary,
        title = { Text("Connection to $hostName", style = Nebula.type.heading) },
        text = {
            Column(Modifier.widthIn(max = s.dp(520)), verticalArrangement = Arrangement.spacedBy(s.dp(12))) {
                ConnectionTestProgress(state)
                if (state is ConnectionTestUi.Done) ConnectionReportCard(state.report)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                val suggestion = (state as? ConnectionTestUi.Done)?.report?.suggestion
                when {
                    suggestion != null -> NebulaButton("Use suggested settings", onClick = { onUse(suggestion) }, modifier = Modifier.focusRequester(focus))
                    state is ConnectionTestUi.Failed || state is ConnectionTestUi.Done ->
                        NebulaButton("Test again", onClick = onRetry, style = ButtonStyle.Secondary, modifier = Modifier.focusRequester(focus))
                    else -> Unit
                }
                LaunchedEffect(state::class) { runCatching { focus.requestFocus() } }
            }
        },
        dismissButton = { NebulaButton(if (state is ConnectionTestUi.Running) "Cancel" else "Close", onClick = onDismiss, style = ButtonStyle.Ghost) },
        shape = RoundedCornerShape(s.dp(18)),
    )
}
