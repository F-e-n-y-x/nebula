package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** The two projects' source repositories, shown in Settings → About. */
internal val SourceRepos = listOf(
    Triple("Nebula", "This app, for Android phones, tablets and TV", "https://github.com/F-e-n-y-x/nebula"),
    Triple("Nova", "The PC host that Nebula streams from", "https://github.com/F-e-n-y-x/nova-host"),
)

@Composable
internal fun SourceCodeLinks(modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        SectionTitle("Source code")
        SourceRepos.forEach { (name, help, url) -> LinkRow(name, help, url) { openInBrowser(ctx, url) } }
        Text("Both repositories are private for now, so the links open once they're made public.", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}

@Composable
private fun LinkRow(title: String, help: String, url: String, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Row(
        Modifier.fillMaxWidth().heightIn(min = s.dp(56))
            .nebulaClickable(shape, onClick)
            .background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.border, shape)
            .padding(horizontal = s.dp(14), vertical = s.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Code, null, tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(20)))
        Spacer(Modifier.width(s.dp(12)))
        Column(Modifier.weight(1f)) {
            Text(title, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            Text(help, style = Nebula.type.label, color = NebulaColors.textMuted)
            Text(url.removePrefix("https://"), style = Nebula.type.label, color = NebulaColors.accentText)
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Open in browser", tint = NebulaColors.textSecondary, modifier = Modifier.size(s.dp(18)))
    }
}

/** ACTION_VIEW; a TV without a browser gets a note instead of a crash. */
internal fun openInBrowser(ctx: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
    if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No browser on this device. The address is $url", Toast.LENGTH_LONG).show()
    }
}
