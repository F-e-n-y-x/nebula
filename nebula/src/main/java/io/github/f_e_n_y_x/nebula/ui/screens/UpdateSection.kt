package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.BuildConfig
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.f_e_n_y_x.nebula.update.Releases
import io.github.f_e_n_y_x.nebula.update.UpdateInstaller
import io.github.f_e_n_y_x.nebula.update.UpdateState
import io.github.f_e_n_y_x.nebula.update.UpdateViewModel

/** Settings → About: update check, download and install from the GitHub releases. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun UpdateSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val vm = viewModel { UpdateViewModel(ctx.applicationContext, BuildConfig.VERSION_NAME) }
    val state by vm.state.collectAsStateWithLifecycle()
    val installerMessage by UpdateInstaller.events.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.checkIfStale() }

    Column(Modifier.widthIn(max = s.dp(640)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
        SectionTitle("Updates", Modifier.padding(top = s.dp(12)))
        when (val st = state) {
            UpdateState.Idle -> Text("Nebula checks the F-e-n-y-x/nebula releases when you ask.", style = Nebula.type.body, color = NebulaColors.textSecondary)
            UpdateState.Checking -> Busy("Checking for updates…")
            is UpdateState.UpToDate -> Text("Up to date (${st.current}).", style = Nebula.type.body, color = NebulaColors.textSecondary)
            is UpdateState.Available -> {
                Text("Update available: ${st.release.version}", style = Nebula.type.bodyStrong, color = NebulaColors.text)
                Releases.notesExcerpt(st.release.notes).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = Nebula.type.label, color = NebulaColors.textMuted)
                }
            }
            is UpdateState.Downloading -> {
                Text("Downloading ${st.release.version}…", style = Nebula.type.body, color = NebulaColors.textSecondary)
                val fraction = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = NebulaColors.accent)
            }
            is UpdateState.Verifying -> Busy("Verifying ${st.release.version}…")
            is UpdateState.Ready -> Text(
                "${st.release.version} is downloaded and verified (checksum and signing key match). Android asks you to confirm the install.",
                style = Nebula.type.body, color = NebulaColors.textSecondary,
            )
            is UpdateState.Error -> Text(st.message, style = Nebula.type.body, color = NebulaColors.danger)
        }
        installerMessage?.let { Text(it, style = Nebula.type.label, color = NebulaColors.danger) }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
            when (state) {
                is UpdateState.Available -> NebulaButton("Download update", onClick = { vm.download() }, icon = Icons.Outlined.Download)
                is UpdateState.Ready -> NebulaButton(
                    if (UpdateInstaller.canInstall(ctx)) "Install update" else "Allow installs, then Install",
                    onClick = { vm.install(ctx) }, icon = Icons.Outlined.InstallMobile,
                )
                else -> Unit
            }
            val busy = state is UpdateState.Checking || state is UpdateState.Downloading || state is UpdateState.Verifying
            if (!busy) NebulaButton("Check for updates", onClick = { vm.check() }, style = ButtonStyle.Secondary, icon = Icons.Outlined.Refresh)
        }

        var pre by remember { mutableStateOf(vm.settings.includePrereleases) }
        ToggleRow("Include pre-releases", "Offer nebula-v…-rc and other test builds too.", pre) { pre = it; vm.setIncludePrereleases(it) }
    }
}

@Composable
private fun Busy(text: String) {
    val s = Nebula.scale
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(s.dp(16)), color = NebulaColors.accentText, strokeWidth = 2.dp)
        Spacer(Modifier.width(s.dp(10)))
        Text(text, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
    }
}
