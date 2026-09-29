package io.github.f_e_n_y_x.nebula.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.Elapsed
import io.github.f_e_n_y_x.nebula.domain.NowPlaying
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.ui.NowPlayingViewModel
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaConfirmDialog
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The "Now playing" card for [hostId] when a game runs there: Resume (the same display it runs on)
 * and Quit (confirmed). [onlyGameId] shows it only for that game (the details page). [onResume]
 * gets the mode to reconnect with.
 */
@Composable
fun NowPlayingSection(
    container: AppContainer,
    hostId: String,
    games: Flow<List<Game>>,
    onResume: (NowPlaying, DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onlyGameId: String? = null,
) {
    val vm = viewModel(key = "now-playing-$hostId") { NowPlayingViewModel(container, hostId, games) }
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val quitting by vm.quitting.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(vm) { vm.messages.collect { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } }

    val shown = np?.takeIf { onlyGameId == null || it.gameId == onlyGameId } ?: return
    NotificationRationale(container, shown)
    NowPlayingCard(
        np = shown,
        quitting = quitting,
        compact = compact,
        modifier = modifier,
        onResume = {
            scope.launch {
                val mode = shown.display ?: runCatching {
                    games.first().firstOrNull { it.id == shown.gameId }?.let { container.resolvePlayMode(it).first() }
                }.getOrNull() ?: container.prefs.streamSettings.first().defaultMode
                onResume(shown, mode)
            }
        },
        onQuit = { confirm = true },
    )
    if (confirm) {
        NebulaConfirmDialog(
            title = "Quit ${shown.gameName}?",
            text = "The game closes on ${shown.hostName}. Unsaved progress may be lost.",
            confirm = "Quit game",
            onConfirm = { confirm = false; vm.quit() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun NowPlayingCard(np: NowPlaying, quitting: Boolean, compact: Boolean, onResume: () -> Unit, onQuit: () -> Unit, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val t = Nebula.type
    val shape = RoundedCornerShape(s.dp(16))
    // Live elapsed time: the card ticks without asking the PC again.
    val now by produceState(System.currentTimeMillis() / 1000, np.sessionKey) {
        while (true) {
            value = System.currentTimeMillis() / 1000
            delay(15_000)
        }
    }
    val line = Elapsed.cardLine(np, now)
    BoxWithConstraints(modifier) {
        val stacked = maxWidth < s.dp(430)
        if (compact && !stacked) {
            // Short landscape screens: one slim strip, no art, so the hero and poster row still fit.
            Row(
                Modifier.fillMaxWidth().background(Color(0xE6101013), shape)
                    .border(1.dp, NebulaColors.success.copy(alpha = 0.35f), shape)
                    .padding(start = s.dp(14), end = s.dp(4), top = s.dp(4), bottom = s.dp(4))
                    .focusGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiveDot()
                Spacer(Modifier.width(s.dp(10)))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Now playing: ${np.gameName}. $line" }) {
                    Text(np.gameName, style = t.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line, style = t.label, color = NebulaColors.success, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(s.dp(8)))
                Buttons(quitting, onResume, onQuit, fill = false)
            }
            return@BoxWithConstraints
        }
        val artW = s.dp(48)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xE6101013), shape)
                .border(1.dp, NebulaColors.success.copy(alpha = 0.35f), shape)
                .padding(horizontal = s.dp(12), vertical = s.dp(10))
                .focusGroup(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtImage(
                    url = np.art.poster ?: np.art.header, seed = np.gameName, contentDescription = null,
                    modifier = Modifier.width(artW).height(artW * 1.5f)
                        .clip(RoundedCornerShape(s.dp(8))).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(8))),
                )
                Spacer(Modifier.width(s.dp(12)))
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Now playing: ${np.gameName}. $line" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot()
                        Spacer(Modifier.width(s.dp(6)))
                        Text("NOW PLAYING", style = t.eyebrow, color = NebulaColors.success)
                    }
                    Text(np.gameName, style = t.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(line, style = t.label, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!stacked) {
                    Spacer(Modifier.width(s.dp(10)))
                    Buttons(quitting, onResume, onQuit, fill = false)
                }
            }
            if (stacked) {
                Spacer(Modifier.height(s.dp(10)))
                Buttons(quitting, onResume, onQuit, fill = true)
            }
        }
    }
}

@Composable
private fun Buttons(quitting: Boolean, onResume: () -> Unit, onQuit: () -> Unit, fill: Boolean) {
    val s = Nebula.scale
    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), modifier = if (fill) Modifier.fillMaxWidth() else Modifier) {
        NebulaButton("Resume", onClick = onResume, icon = Icons.Rounded.PlayArrow, modifier = if (fill) Modifier.weight(1f) else Modifier)
        NebulaButton(
            if (quitting) "Quitting…" else "Quit", onClick = { if (!quitting) onQuit() }, style = ButtonStyle.Secondary,
            icon = Icons.Rounded.PowerSettingsNew, modifier = if (fill) Modifier.weight(1f) else Modifier,
        )
    }
}

@Composable
private fun LiveDot() {
    val s = Nebula.scale
    val alpha = if (Nebula.reducedMotion) 1f else {
        val pulse = rememberInfiniteTransition(label = "live")
        pulse.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "liveAlpha").value
    }
    Box(Modifier.size(s.dp(7)).alpha(alpha).background(NebulaColors.success, CircleShape))
}

/**
 * Android 13+: before asking for the notification permission, say why (once). Only when the
 * setting is on, a game is running (the moment it matters) and the permission is missing.
 */
@Composable
private fun NotificationRationale(container: AppContainer, np: NowPlaying) {
    if (Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val center = container.nowPlaying
    val enabled by center.enabled.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(ctx, "No reminders then. You can turn them on in Settings → Notifications.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(np.sessionKey, enabled) {
        show = enabled && !center.rationaleShown &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    if (!show) return
    io.github.f_e_n_y_x.nebula.ui.screens.NebulaChoiceDialog(
        title = "Remind you about running games?",
        text = "When you leave Nebula with ${np.gameName} still running on ${np.hostName}, Nebula can notify you, " +
            "so you can stop it and save power. Android will ask for permission to show notifications.",
        confirm = "Allow",
        dismiss = "Not now",
        onConfirm = {
            show = false
            center.rationaleShown = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onDismiss = {
            show = false
            center.rationaleShown = true
        },
    )
}
