package io.github.f_e_n_y_x.nebula.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * Settings → Notifications: the "game running on your PC" reminder, and a way to grant the
 * Android 13+ permission it needs (the system prompt, or the app's notification settings once
 * Android stops asking).
 */
@Composable
internal fun NotificationsSection() {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val center = ctx.container.nowPlaying
    val enabled by center.enabled.collectAsStateWithLifecycle()
    // Re-read the permission whenever the screen returns (the user may have changed it in Settings).
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    val permitted = remember(tick) { center.notifications.permitted() }
    val activity = ctx as? android.app.Activity
    val openSettings = {
        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
        }
        runCatching { ctx.startActivity(i) }
        Unit
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        // Denied for good: Android won't show its prompt again, so the button opens Settings next time.
        if (!granted && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
            center.rationaleShown = true
        }
    }
    Column(Modifier.widthIn(max = s.dp(720)), verticalArrangement = Arrangement.spacedBy(s.dp(12))) {
        ToggleRow(
            "Games left running on your PC",
            "When you leave Nebula with a game still running on a paired PC, a notification says for how long, with Resume, Stop and Don't notify.",
            enabled,
        ) { on ->
            center.setEnabled(on)
            if (on && !permitted && Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (enabled && !permitted) {
            Text(
                "Notifications are off for Nebula, so this reminder can't show.",
                style = Nebula.type.label, color = NebulaColors.warning,
            )
            val canPrompt = Build.VERSION.SDK_INT >= 33 && activity != null &&
                (!center.rationaleShown || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS))
            NebulaButton(
                if (canPrompt) "Allow notifications" else "Open notification settings",
                onClick = { if (canPrompt) request.launch(Manifest.permission.POST_NOTIFICATIONS) else openSettings() },
                style = ButtonStyle.Secondary, icon = Icons.Outlined.NotificationsActive,
            )
        } else if (enabled) {
            NebulaButton("Notification style and sound", onClick = openSettings, style = ButtonStyle.Ghost, icon = Icons.Outlined.NotificationsActive)
        }
        Text(
            "Checked every 15 minutes while you're away, on any network. Nothing is shown while Nebula is open or streaming.",
            style = Nebula.type.label, color = NebulaColors.textMuted,
        )
    }
}
