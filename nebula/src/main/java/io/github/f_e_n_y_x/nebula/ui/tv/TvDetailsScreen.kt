package io.github.f_e_n_y_x.nebula.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.ui.DetailsUi
import io.github.f_e_n_y_x.nebula.ui.DetailsViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.screens.metaLine
import io.github.f_e_n_y_x.nebula.ui.screens.modeLabel
import io.github.f_e_n_y_x.nebula.ui.screens.playtime
import io.github.f_e_n_y_x.nebula.ui.screens.relativeAgo
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * A game's page on TV: full-bleed hero, title, stats, and the two ways to play side by side —
 * "Play · Virtual display" (this TV's resolution, the host's own virtual screen) and "Play on
 * desktop · Mirror". The remembered choice comes first and takes focus. Down reaches the
 * screenshots; Back returns to the home with its focus restored.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun TvDetailsScreen(container: AppContainer, nav: Navigator, hostId: String, gameId: String) {
    val vm = viewModel { DetailsViewModel(container, hostId, gameId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val game = ui.game ?: run { Box(Modifier.fillMaxSize().background(NebulaColors.bg)); return }
    val play: (DisplayMode) -> Unit = { m -> vm.remember(m); nav.push(Route.Stream(hostId, game.id, m)) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val shots = ui.details?.screenshots.orEmpty().takeIf { ui.options.showDetails }.orEmpty()

    NebulaTvTheme {
        Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
            ArtImage(
                game.art.hero ?: game.art.header, game.name, null, Modifier.fillMaxSize(), alignment = Alignment.TopEnd,
                fallbackIcon = if (game.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null,
            )
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to NebulaColors.bg.copy(alpha = 0.97f), 0.45f to NebulaColors.bg.copy(alpha = 0.82f), 0.8f to NebulaColors.bg.copy(alpha = 0.2f), 1f to Color.Transparent)))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to NebulaColors.bg)))

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp)) {
                Header(ui)
                Spacer(Modifier.height(22.dp))
                PlayButtons(ui, play)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, tint = NebulaColors.success, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (ui.remembered) "Nebula remembers your choice for this game" else "Your choice is remembered for this game",
                        style = TvType.label, color = NebulaColors.textSecondary,
                    )
                }
                val d = ui.details
                if (d != null && ui.options.showDetails) {
                    Spacer(Modifier.height(24.dp))
                    d.description?.let {
                        Text(it, style = TvType.body, color = NebulaColors.textSecondary, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 600.dp))
                        Spacer(Modifier.height(14.dp))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 600.dp)) {
                        d.genres.forEach { Chip(it) }
                        d.metacritic?.let { Chip("Metacritic $it", NebulaColors.success) }
                    }
                    if (shots.isNotEmpty()) {
                        Spacer(Modifier.height(22.dp))
                        Text("Screenshots", style = TvType.label, color = NebulaColors.textSecondary)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
                        ) {
                            itemsIndexed(shots) { i, url ->
                                Card(
                                    onClick = { viewing = i },
                                    modifier = Modifier.width(224.dp).aspectRatio(16f / 9f),
                                    shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                                    scale = CardDefaults.scale(focusedScale = 1.08f),
                                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(10.dp))),
                                ) { ArtImage(url, url, "Screenshot ${i + 1}", Modifier.fillMaxSize()) }
                            }
                        }
                    }
                    val credit = listOfNotNull(d.developer, d.publisher?.takeIf { it != d.developer }, d.releaseDate).joinToString(" · ")
                    if (credit.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(credit, style = TvType.label, color = NebulaColors.textMuted)
                    }
                }
            }
        }
        viewing?.let { start -> ScreenshotViewer(shots, start) { viewing = null } }
    }
}

@Composable
private fun Header(ui: DetailsUi) {
    val g = ui.game ?: return
    val eyebrow = when {
        g.running -> "RUNNING ON YOUR PC"
        g.lastPlayedEpochS != null && ui.options.showPlaytime -> "LAST PLAYED · ${relativeAgo(g.lastPlayedEpochS)!!.uppercase()}"
        g.kind == GameKind.DESKTOP -> "DESKTOP"
        else -> "IN YOUR LIBRARY"
    }
    Text(eyebrow, style = TvType.eyebrow, color = NebulaColors.accentText)
    Spacer(Modifier.height(10.dp))
    if (g.art.logo != null) {
        coil3.compose.AsyncImage(
            model = g.art.logo, contentDescription = g.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = Modifier.heightIn(max = 76.dp).widthIn(max = 420.dp),
        )
    } else {
        Text(g.name, style = TvType.display, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 560.dp))
    }
    val meta = g.metaLine()
    if (meta.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(meta, style = TvType.meta, color = NebulaColors.textSecondary)
    }
    val stats = listOfNotNull(
        playtime(g.playtimeS)?.takeIf { ui.options.showPlaytime }?.let { "Played" to it.removeSuffix(" played") },
        relativeAgo(g.lastPlayedEpochS)?.takeIf { ui.options.showPlaytime }?.let { "Last played" to it.replaceFirstChar(Char::uppercase) },
        ui.details?.lastSession?.let { "Last stream" to "${it.device} · ${it.resolution} · ${it.fps} Hz" },
    )
    if (stats.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            stats.forEach { (k, v) ->
                Column {
                    Text(k, style = TvType.label, color = NebulaColors.textMuted)
                    Spacer(Modifier.height(2.dp))
                    Text(v, style = TvType.bodyStrong, color = NebulaColors.text)
                }
            }
        }
    }
}

@Composable
private fun PlayButtons(ui: DetailsUi, onPlay: (DisplayMode) -> Unit) {
    val context = LocalContext.current
    val primary = remember { FocusRequester() }
    LaunchedEffect(ui.game?.id) { runCatching { primary.requestFocus() } }
    val modes = if (ui.mode == DisplayMode.VIRTUAL) listOf(DisplayMode.VIRTUAL, DisplayMode.MIRROR) else listOf(DisplayMode.MIRROR, DisplayMode.VIRTUAL)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        modes.forEach { m ->
            val chosen = m == ui.mode
            TvActionButton(
                title = if (m == DisplayMode.VIRTUAL) "Play · Virtual display" else "Play on desktop · Mirror",
                subtitle = if (m == DisplayMode.VIRTUAL) {
                    modeLabel(context, m, ui.settings).removePrefix("Virtual display · ") + if (chosen) " · your choice" else ""
                } else {
                    "The PC's own screen" + if (chosen) " · your choice" else ""
                },
                icon = if (m == DisplayMode.VIRTUAL) Icons.Rounded.Tv else Icons.Outlined.DesktopWindows,
                primary = chosen,
                onClick = { onPlay(m) },
                modifier = Modifier.width(290.dp).heightIn(min = 64.dp).then(if (chosen) Modifier.focusRequester(primary) else Modifier),
            )
        }
    }
}

@Composable
private fun Chip(text: String, color: Color = NebulaColors.textSecondary) {
    Text(
        text, style = TvType.label, color = color,
        modifier = Modifier.background(Color(0x99111113), RoundedCornerShape(50)).border(1.dp, NebulaColors.border, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Full-screen screenshots: left/right pages through them, Back closes. */
@Composable
private fun ScreenshotViewer(shots: List<String>, start: Int, onClose: () -> Unit) {
    var index by remember { mutableStateOf(start) }
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black).focusRequester(focus)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { index = (index - 1).coerceAtLeast(0); true }
                        Key.DirectionRight -> { index = (index + 1).coerceAtMost(shots.lastIndex); true }
                        Key.DirectionCenter, Key.Enter -> { onClose(); true }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            ArtImage(shots[index], shots[index], "Screenshot ${index + 1} of ${shots.size}", Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Fit)
            Text(
                "${index + 1} / ${shots.size}", style = TvType.label, color = NebulaColors.textSecondary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            )
        }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    }
}
