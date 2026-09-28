package io.github.f_e_n_y_x.nebula.diagnostics.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.diagnostics.CapabilityCollector
import io.github.f_e_n_y_x.nebula.diagnostics.CapabilityReport
import io.github.f_e_n_y_x.nebula.diagnostics.ConnectionTimeline
import io.github.f_e_n_y_x.nebula.diagnostics.DiagnosticsExport
import io.github.f_e_n_y_x.nebula.diagnostics.ReportBuilder
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(context: Context, private val container: AppContainer) : ViewModel() {
    private val app = context.applicationContext

    private val _report = MutableStateFlow<CapabilityReport?>(null)
    val report: StateFlow<CapabilityReport?> = _report.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    val sessions = container.sessionHistory.sessions
    val attempts = ConnectionTimeline.shared.all

    sealed interface ExportState {
        data object Idle : ExportState
        data object Working : ExportState
        data class Ready(val file: File, val message: String) : ExportState
        data class Failed(val message: String) : ExportState
    }

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private var job: Job? = null

    init { refresh(probeHosts = false) }

    /** [probeHosts] asks each paired PC for its capabilities again (network). */
    fun refresh(probeHosts: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _loading.value = true
            runCatching { CapabilityCollector(app, container).collect(probeHosts) }
                .onSuccess { _report.value = ReportBuilder.build(it) }
            _loading.value = false
        }
    }

    fun clearSessions() = container.sessionHistory.clear()

    /** Builds the zip, then shares it ([save] = false) or copies it to Downloads. */
    fun exportZip(minutes: Int, save: Boolean) {
        if (_export.value == ExportState.Working) return
        viewModelScope.launch {
            _export.value = ExportState.Working
            _export.value = runCatching {
                val f = DiagnosticsExport.buildZip(app, container, container.sessionHistory, minutes)
                if (save) {
                    val where = DiagnosticsExport.saveToDownloads(app, f) ?: error("Saving needs Android 10 or newer; use Share instead.")
                    ExportState.Ready(f, "Saved to $where")
                } else {
                    DiagnosticsExport.share(app, f)
                    ExportState.Ready(f, "${f.name} · ${f.length() / 1024} KB")
                }
            }.getOrElse { ExportState.Failed(it.message ?: "Export failed.") }
        }
    }
}
