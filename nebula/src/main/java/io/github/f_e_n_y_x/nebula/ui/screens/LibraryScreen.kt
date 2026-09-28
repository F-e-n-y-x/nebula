package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.f_e_n_y_x.nebula.ui.components.statusBarScrim
import io.github.f_e_n_y_x.nebula.ui.LocalRailInset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.ui.LibraryUi
import io.github.f_e_n_y_x.nebula.ui.LibraryViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.Pill
import io.github.f_e_n_y_x.nebula.ui.components.SectionTitle
import io.github.f_e_n_y_x.nebula.ui.components.StatusDot
import io.github.f_e_n_y_x.nebula.ui.components.bottomScrim
import io.github.f_e_n_y_x.nebula.ui.components.label
import io.github.f_e_n_y_x.nebula.ui.components.leftScrim
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

@Composable
fun LibraryScreen(container: AppContainer, nav: Navigator, hostId: String) {
    val vm = viewModel { LibraryViewModel(container, hostId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val form = Nebula.form
    val hostVm = viewModel(key = "host-actions-library-$hostId") { io.github.f_e_n_y_x.nebula.ui.HostActionsViewModel(container, hostId, null) }
    val wake by hostVm.wake.collectAsStateWithLifecycle()
    var waking by remember { mutableStateOf<Game?>(null) }
    // A sleeping PC is woken first; the game launches once it answers.
    val play: (Game) -> Unit = { g ->
        val mode = ui.focusedMode
        waking = g
        hostVm.playWhenAwake { waking = null; nav.push(Route.Stream(hostId, g.id, mode)) }
    }
    val details: (Game) -> Unit = { g -> nav.push(Route.Details(hostId, g.id)) }
    Box(Modifier.fillMaxSize()) {
        when {
            !ui.loading && ui.games.isEmpty() -> EmptyLibrary(ui.host, nav)
            !form.isLandscape && !form.isTv -> PortraitLibrary(ui, vm::focus, play, details)
            else -> SpotlightLibrary(ui, vm::focus, play, details)
        }
        WakeOverlay(ui.host?.name ?: "your PC", waking?.name, wake, onCancel = { waking = null; hostVm.cancelWake() }, onRetry = hostVm::retryWake)
    }
}

@Composable
private fun HostChip(host: Host?) {
    host ?: return
    Pill(
        text = "${host.name} · ${host.status.label()}",
        color = NebulaColors.text,
        background = Color(0xB30A0A0B),
        leading = { StatusDot(host.status) },
    )
}

/** Console-style home: the focused game fills the screen; a poster row at the bottom drives focus. */
@Composable
private fun SpotlightLibrary(ui: LibraryUi, onFocus: (Game) -> Unit, onPlay: (Game) -> Unit, onDetails: (Game) -> Unit) {
    val s = Nebula.scale
    val t = Nebula.type
    val ctx = LocalContext.current
    val focused = ui.focused ?: return
    val playRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playRequester.requestFocus() } }

    val reduced = Nebula.reducedMotion
    BoxWithConstraints(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        val compactHeight = maxHeight < 480.dp
        AnimatedContent(
            targetState = focused,
            transitionSpec = { if (reduced) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(450)) togetherWith fadeOut(tween(450)) },
            contentKey = { it.id },
            label = "hero",
        ) { g ->
            ArtImage(
                url = g.art.hero ?: g.art.header ?: g.art.poster, seed = g.name, contentDescription = null,
                modifier = Modifier.fillMaxSize(), alignment = HeroFocus,
            )
        }
        Box(Modifier.fillMaxSize().leftScrim())
        Box(Modifier.fillMaxSize().bottomScrim())
        Box(Modifier.fillMaxWidth().statusBarScrim())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(start = LocalRailInset.current + s.dp(40), end = s.dp(32), top = s.dp(if (compactHeight) 12 else 24), bottom = s.dp(if (compactHeight) 10 else 24)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                HostChip(ui.host)
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.widthIn(max = s.dp(560))) {
                val eyebrow = when {
                    focused.running -> "RUNNING ON ${ui.host?.name?.uppercase() ?: "HOST"}"
                    focused.lastPlayedEpochS != null && ui.options.showPlaytime -> "LAST PLAYED · ${relativeAgo(focused.lastPlayedEpochS)!!.uppercase()}"
                    focused.kind == GameKind.DESKTOP -> "DESKTOP"
                    else -> "IN YOUR LIBRARY"
                }
                Text(eyebrow, style = t.eyebrow, color = NebulaColors.accentText)
                Spacer(Modifier.height(s.dp(10)))
                GameTitle(focused, maxHeight = s.dp(if (compactHeight) 64 else 96), small = compactHeight)
                val meta = listOfNotNull(focused.metaLine().ifBlank { null }, playtime(focused.playtimeS)?.takeIf { ui.options.showPlaytime }).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Spacer(Modifier.height(s.dp(10)))
                    Text(meta, style = t.secondary, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(s.dp(if (compactHeight) 14 else 20)))
                Row(horizontalArrangement = Arrangement.spacedBy(s.dp(12)), verticalAlignment = Alignment.CenterVertically) {
                    NebulaButton(
                        text = "Play", sublabel = modeLabel(ctx, ui.focusedMode, ui.settings), icon = Icons.Rounded.PlayArrow,
                        onClick = { onPlay(focused) }, modifier = Modifier.focusRequester(playRequester),
                    )
                    NebulaButton(text = "Details", icon = Icons.Outlined.Info, style = ButtonStyle.Secondary, onClick = { onDetails(focused) })
                }
            }
            Spacer(Modifier.height(s.dp(if (compactHeight) 14 else 28)))
            val tileH = if (compactHeight) s.dp(118) else s.dp(168)
            LazyRow(
                modifier = Modifier.fillMaxWidth().height(tileH + s.dp(34)).focusGroup(),
                horizontalArrangement = Arrangement.spacedBy(s.dp(14)),
                contentPadding = PaddingValues(vertical = s.dp(8), horizontal = s.dp(4)),
                verticalAlignment = Alignment.Top,
            ) {
                items(ui.games, key = { it.id }) { g ->
                    PosterTile(
                        game = g, selected = g.id == focused.id, height = tileH,
                        onFocus = { onFocus(g) },
                        onClick = { if (g.id == focused.id) onDetails(g) else onFocus(g) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTitle(game: Game, maxHeight: androidx.compose.ui.unit.Dp, small: Boolean = false) {
    val t = Nebula.type
    if (game.art.logo != null) {
        Box(Modifier.heightIn(max = maxHeight).widthIn(max = maxHeight * 6f)) {
            coil3.compose.AsyncImage(
                model = game.art.logo, contentDescription = game.name, contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart, modifier = Modifier.heightIn(max = maxHeight),
            )
        }
    } else {
        Text(game.name, style = if (small) t.title else t.display, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PosterTile(game: Game, selected: Boolean, height: androidx.compose.ui.unit.Dp, onFocus: () -> Unit, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(Modifier.width(height * (2f / 3f))) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .nebulaClickable(shape, onClick, focusScale = 1.06f)
                .then(if (selected) Modifier.border(2.dp, NebulaColors.accentText, shape) else Modifier.border(1.dp, NebulaColors.border, shape)),
        ) {
            ArtImage(url = game.art.poster, seed = game.name, contentDescription = game.name, modifier = Modifier.fillMaxSize())
            if (game.art.poster == null && game.kind == GameKind.DESKTOP) {
                androidx.compose.material3.Icon(
                    Icons.Outlined.DesktopWindows, null, tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.align(Alignment.TopStart).padding(s.dp(10)).size(s.dp(26)),
                )
            }
            if (game.art.poster == null) {
                Text(
                    game.name, style = Nebula.type.label, color = NebulaColors.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(s.dp(10)),
                )
            }
            if (game.running) {
                Pill("Running", color = NebulaColors.success, background = Color(0xE610231A), modifier = Modifier.align(Alignment.TopStart).padding(s.dp(6)))
            }
        }
        if (selected) {
            Spacer(Modifier.height(s.dp(8)))
            Text(game.name, style = Nebula.type.label, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Phone held upright: a hero card for the focused game, then the whole library as posters. */
@Composable
private fun PortraitLibrary(ui: LibraryUi, onFocus: (Game) -> Unit, onPlay: (Game) -> Unit, onDetails: (Game) -> Unit) {
    val s = Nebula.scale
    val t = Nebula.type
    val ctx = LocalContext.current
    val focused = ui.focused ?: return
    val compact = Nebula.form.isCompact
    val cols = if (compact) 3 else 5
    val side = s.dp(if (compact) 16 else 24)
    val start = LocalRailInset.current + side
    val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.58f).dp
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier.fillMaxSize().background(NebulaColors.bg),
        contentPadding = PaddingValues(start = start, end = side, bottom = s.dp(24)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
        verticalArrangement = Arrangement.spacedBy(s.dp(12)),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            // Full-bleed: the hero ignores the grid's side padding (and the rail) and runs under the status bar.
            Box(Modifier.fillMaxWidth().bleed(start, side).height(heroHeight)) {
                Box(Modifier.fillMaxSize()) {
                    ArtImage(
                        url = focused.art.hero ?: focused.art.header ?: focused.art.poster, seed = focused.name, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), alignment = HeroFocus,
                    )
                    Box(Modifier.fillMaxSize().bottomScrim())
                    Box(Modifier.fillMaxWidth().statusBarScrim())
                }
                Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = s.dp(12), end = side)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        HostChip(ui.host)
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = start, end = side, bottom = s.dp(4))) {
                    val eyebrow = focused.lastPlayedEpochS?.takeIf { ui.options.showPlaytime }?.let { "LAST PLAYED · ${relativeAgo(it)!!.uppercase()}" } ?: "IN YOUR LIBRARY"
                    Text(eyebrow, style = t.eyebrow, color = NebulaColors.accentText)
                    Spacer(Modifier.height(s.dp(8)))
                    GameTitle(focused, maxHeight = s.dp(72), small = true)
                    val meta = focused.metaLine()
                    if (meta.isNotBlank()) {
                        Spacer(Modifier.height(s.dp(8)))
                        Text(meta, style = t.secondary, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(s.dp(14)))
                    Row(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalAlignment = Alignment.CenterVertically) {
                        NebulaButton(
                            text = "Play", sublabel = modeLabel(ctx, ui.focusedMode, ui.settings), icon = Icons.Rounded.PlayArrow,
                            onClick = { onPlay(focused) }, modifier = Modifier.weight(1f),
                        )
                        NebulaButton(text = "Details", style = ButtonStyle.Secondary, onClick = { onDetails(focused) })
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Library · ${ui.games.size} on ${ui.host?.name ?: "host"}", Modifier.padding(top = s.dp(12)))
        }
        items(ui.games, key = { it.id }) { g ->
            val shape = RoundedCornerShape(s.dp(10))
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .nebulaClickable(shape, { if (g.id == focused.id) onDetails(g) else onFocus(g) }, focusScale = 1.04f)
                        .then(if (g.id == focused.id) Modifier.border(2.dp, NebulaColors.accentText, shape) else Modifier.border(1.dp, NebulaColors.border, shape)),
                ) {
                    ArtImage(g.art.poster, g.name, g.name, Modifier.fillMaxSize(), fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
                }
                Spacer(Modifier.height(s.dp(6)))
                Text(g.name, style = t.label, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EmptyLibrary(host: Host?, nav: Navigator) {
    val s = Nebula.scale
    Column(
        Modifier.fillMaxSize().background(NebulaColors.bg).statusBarsPadding().padding(s.dp(32)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No games on ${host?.name ?: "this host"} yet", style = Nebula.type.title, color = NebulaColors.text)
        Spacer(Modifier.height(s.dp(10)))
        Text(
            "Add games in Nova's web UI (Library → Add games), or pair another host.",
            style = Nebula.type.body, color = NebulaColors.textSecondary,
        )
        Spacer(Modifier.height(s.dp(24)))
        NebulaButton("Choose a host", onClick = { nav.top(Route.Hosts) }, style = ButtonStyle.Secondary)
    }
}

/** Crop focus for hero art: a little right of centre and above the middle, where faces usually are. */
private val HeroFocus = BiasAlignment(0.35f, -0.35f)

/** Lets a grid item draw past the grid's side padding (full-bleed headers). */
private fun Modifier.bleed(start: Dp, end: Dp) = layout { measurable, constraints ->
    val s = start.roundToPx()
    val e = end.roundToPx()
    val width = constraints.maxWidth + s + e
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-s, 0) }
}
