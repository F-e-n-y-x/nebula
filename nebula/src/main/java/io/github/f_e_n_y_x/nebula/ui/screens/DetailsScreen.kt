package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.ui.DetailsUi
import io.github.f_e_n_y_x.nebula.ui.DetailsViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.NebulaIconButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.bottomScrim
import io.github.f_e_n_y_x.nebula.ui.components.leftScrim
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

@Composable
fun DetailsScreen(container: AppContainer, nav: Navigator, hostId: String, gameId: String) {
    val vm = viewModel { DetailsViewModel(container, hostId, gameId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val game = ui.game ?: run { Box(Modifier.fillMaxSize().background(NebulaColors.bg)); return }
    val form = Nebula.form
    val s = Nebula.scale
    val play: (DisplayMode) -> Unit = { m -> vm.remember(m); nav.push(Route.Stream(hostId, game.id, m)) }
    val wide = form.isLandscape

    Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        if (wide) {
            ArtImage(game.art.hero ?: game.art.header, game.name, null, Modifier.fillMaxSize(), alignment = Alignment.TopEnd,
                fallbackIcon = if (game.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
            Box(Modifier.fillMaxSize().background(NebulaColors.bg.copy(alpha = if (ui.details != null && form.widthDp >= 760) 0.55f else 0.2f)))
            Box(Modifier.fillMaxSize().leftScrim(0.97f))
            Box(Modifier.fillMaxSize().bottomScrim())
            Row(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = s.dp(40), vertical = s.dp(20)),
                horizontalArrangement = Arrangement.spacedBy(s.dp(40)),
            ) {
                Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
                    NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() })
                    Spacer(Modifier.height(s.dp(20)))
                    Header(ui)
                    Spacer(Modifier.height(s.dp(22)))
                    PlayChoices(ui, play)
                    Spacer(Modifier.height(s.dp(24)))
                    Stats(ui)
                }
                if (ui.details != null && form.widthDp >= 760) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = s.dp(68))) { About(ui) }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().aspectRatio(if (form.isCompact) 1.05f else 1.5f)) {
                    ArtImage(game.art.hero ?: game.art.header ?: game.art.poster, game.name, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                    Box(Modifier.fillMaxSize().bottomScrim())
                    NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() }, Modifier.statusBarsPadding().padding(s.dp(16)))
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = s.dp(20))) { Header(ui) }
                }
                Column(Modifier.padding(horizontal = s.dp(20)).navigationBarsPadding()) {
                    Spacer(Modifier.height(s.dp(18)))
                    PlayChoices(ui, play)
                    Spacer(Modifier.height(s.dp(22)))
                    Stats(ui)
                    if (ui.details != null) {
                        Spacer(Modifier.height(s.dp(26)))
                        About(ui)
                    }
                    Spacer(Modifier.height(s.dp(28)))
                }
            }
        }
    }
}

@Composable
private fun Header(ui: DetailsUi) {
    val game = ui.game ?: return
    val s = Nebula.scale
    val maxH = s.dp(if (Nebula.form.isCompact) 64 else 92)
    if (game.art.logo != null) {
        coil3.compose.AsyncImage(
            model = game.art.logo, contentDescription = game.name, alignment = Alignment.CenterStart,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.heightIn(max = maxH).widthIn(max = maxH * 6f),
        )
    } else {
        Text(game.name, style = Nebula.type.display, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    val meta = game.metaLine()
    if (meta.isNotBlank()) {
        Spacer(Modifier.height(s.dp(10)))
        Text(meta, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
    }
}

@Composable
private fun PlayChoices(ui: DetailsUi, onPlay: (DisplayMode) -> Unit) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val primary = remember { FocusRequester() }
    LaunchedEffect(ui.game?.id) { runCatching { primary.requestFocus() } }
    val modes = if (ui.mode == DisplayMode.VIRTUAL) listOf(DisplayMode.VIRTUAL, DisplayMode.MIRROR) else listOf(DisplayMode.MIRROR, DisplayMode.VIRTUAL)
    val compact = Nebula.form.isCompact && !Nebula.form.isLandscape
    SectionTitle("Play on")
    val content: @Composable (DisplayMode, Modifier) -> Unit = { m, mod ->
        PlayCard(
            highlighted = m == ui.mode,
            title = if (m == DisplayMode.VIRTUAL) "Virtual display" else "Desktop (Mirror)",
            subtitle = if (m == DisplayMode.VIRTUAL) modeLabel(ctx, m, ui.settings).removePrefix("Virtual display · ") else "The PC's own screen",
            icon = if (m == DisplayMode.VIRTUAL) Icons.Rounded.PhoneAndroid else Icons.Outlined.DesktopWindows,
            onClick = { onPlay(m) },
            modifier = if (m == ui.mode) mod.focusRequester(primary) else mod,
        )
    }
    // Stacked on phones held upright and on TV, where up/down is the natural D-pad move.
    if (compact || Nebula.form.isTv) {
        Column(verticalArrangement = Arrangement.spacedBy(s.dp(10))) { modes.forEach { content(it, Modifier.fillMaxWidth()) } }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(s.dp(12))) { modes.forEach { content(it, Modifier.weight(1f)) } }
    }
    Spacer(Modifier.height(s.dp(10)))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, tint = NebulaColors.success, modifier = Modifier.size(s.dp(16)))
        Spacer(Modifier.width(s.dp(6)))
        Text(
            if (ui.remembered) "Nebula remembers your choice for this game" else "Your choice is remembered for this game",
            style = Nebula.type.label, color = NebulaColors.textSecondary,
        )
    }
}

@Composable
private fun PlayCard(highlighted: Boolean, title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Row(
        modifier
            .heightIn(min = s.dp(76))
            .nebulaClickable(shape, onClick, focusScale = 1.02f)
            .background(if (highlighted) NebulaColors.accent else Color(0xB3111113), shape)
            .then(if (highlighted) Modifier else Modifier.border(1.dp, NebulaColors.controlBorder, shape))
            .padding(horizontal = s.dp(18), vertical = s.dp(14)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlighted) Color.White else NebulaColors.text, modifier = Modifier.size(s.dp(26)))
        Spacer(Modifier.width(s.dp(14)))
        Column {
            Text(title, style = Nebula.type.bodyStrong, color = if (highlighted) Color.White else NebulaColors.text)
            Text(subtitle, style = Nebula.type.label, color = if (highlighted) Color.White.copy(alpha = 0.82f) else NebulaColors.textSecondary, maxLines = 2)
        }
    }
}

@Composable
private fun Stats(ui: DetailsUi) {
    val game = ui.game ?: return
    val s = Nebula.scale
    val items = listOfNotNull(
        playtime(game.playtimeS)?.let { "Played" to it.removeSuffix(" played") },
        relativeAgo(game.lastPlayedEpochS)?.let { "Last played" to it.replaceFirstChar(Char::uppercase) },
        ui.details?.lastSession?.let { "Last stream" to "${it.resolution} · ${it.fps} Hz" },
    )
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(28))) {
        items.forEach { (k, v) ->
            Column {
                Text(k, style = Nebula.type.label, color = NebulaColors.textMuted)
                Spacer(Modifier.height(s.dp(4)))
                Text(v, style = Nebula.type.bodyStrong, color = NebulaColors.text)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun About(ui: DetailsUi) {
    val d = ui.details ?: return
    val s = Nebula.scale
    if (d.description != null) {
        SectionTitle("About")
        Text(d.description, style = Nebula.type.body, color = NebulaColors.textSecondary)
        Spacer(Modifier.height(s.dp(14)))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(8)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        d.genres.forEach { Pill(it) }
        d.metacritic?.let { Pill("Metacritic $it", color = NebulaColors.success) }
    }
    val credit = listOfNotNull(d.developer, d.publisher?.takeIf { it != d.developer }, d.releaseDate).joinToString(" · ")
    if (credit.isNotBlank()) {
        Spacer(Modifier.height(s.dp(12)))
        Text(credit, style = Nebula.type.label, color = NebulaColors.textMuted)
    }
    if (d.screenshots.isNotEmpty()) {
        Spacer(Modifier.height(s.dp(22)))
        SectionTitle("Screenshots")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), contentPadding = PaddingValues(vertical = s.dp(4))) {
            items(d.screenshots) { url ->
                val shape = RoundedCornerShape(s.dp(10))
                ArtImage(url, url, "Screenshot", Modifier.width(s.dp(220)).aspectRatio(16f / 9f).nebulaClickable(shape, {}, focusScale = 1.04f).border(1.dp, NebulaColors.border, shape))
            }
        }
        Spacer(Modifier.height(s.dp(8)))
        Text("Details and screenshots from Steam, fetched by Nova", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
}
