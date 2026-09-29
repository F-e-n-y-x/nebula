package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.ContinueItem
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.VideoCodec
import io.github.f_e_n_y_x.nebula.ui.HomeUi
import io.github.f_e_n_y_x.nebula.ui.HomeViewModel
import io.github.f_e_n_y_x.nebula.ui.LocalRailInset
import androidx.compose.foundation.layout.widthIn
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.NebulaStar
import io.github.f_e_n_y_x.nebula.ui.components.StatusDot
import io.github.f_e_n_y_x.nebula.ui.components.label
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import java.util.Calendar

/**
 * Shelf (B), from the NebB designs: a GameHub-style rail home. Wide (landscape phone, tablet, TV):
 * greeting and host, a "Continue playing" row of wide cards, then the library as a poster shelf with
 * filters. Upright: host card with the two desktops, one big continue card, then a poster grid.
 */
@Composable
fun ShelfHome(container: AppContainer, nav: Navigator, hostId: String, wide: Boolean, home: HomeActions) {
    val vm = viewModel(key = "home-$hostId") { HomeViewModel(container, hostId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (!ui.loading && ui.games.isEmpty()) return EmptyLibrary(ui.host, nav)
    val actions = ShelfActions(home.play, home.details, home.hosts, vm::toggleFavourite, home.nowPlaying)
    if (wide) ShelfWide(ui, actions) else ShelfPortrait(ui, actions)
}

internal class ShelfActions(
    /** Plays [Game]; a null mode means "whatever Play would use" (remembered choice, host default…). */
    val play: (Game, DisplayMode?) -> Unit,
    val details: (Game) -> Unit,
    val hosts: () -> Unit,
    val toggleFavourite: (Game) -> Unit,
    val nowPlaying: @Composable (Modifier, Boolean) -> Unit,
)

private enum class Filter(val label: String) { ALL("All"), FAVOURITES("Favourites"), GAMES("Games"), DESKTOPS("Desktops") }

private fun List<Game>.filtered(f: Filter, favourites: Set<String> = emptySet()) = when (f) {
    Filter.ALL -> this
    Filter.FAVOURITES -> filter { it.id in favourites }
    Filter.GAMES -> filter { it.kind == GameKind.GAME }
    Filter.DESKTOPS -> filter { it.kind != GameKind.GAME }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ShelfWide(ui: HomeUi, a: ShelfActions) {
    val s = Nebula.scale
    val t = Nebula.type
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    val first = remember { FocusRequester() }
    LaunchedEffect(ui.loading) { if (!ui.loading) runCatching { first.requestFocus() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        // Card and poster sizes follow the height: 412 dp landscape phone → mockup sizes; tablets and TV grow.
        val contH = (maxHeight * 0.29f).coerceIn(104.dp, 230.dp)
        val posterH = (maxHeight * 0.32f).coerceIn(124.dp, 300.dp)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(start = LocalRailInset.current + s.dp(20), top = s.dp(12), bottom = s.dp(12)),
            verticalArrangement = Arrangement.spacedBy(s.dp(10)),
        ) {
            Row(Modifier.padding(end = s.dp(20)), verticalAlignment = Alignment.CenterVertically) {
                Text(greeting(), style = t.heading, color = NebulaColors.text, modifier = Modifier.weight(1f))
                HostButton(ui.host, ui.settings, a.hosts)
            }
            // A game running on the PC: Resume / Quit, the first thing D-pad down reaches.
            a.nowPlaying(Modifier.padding(end = s.dp(20)).widthIn(max = s.dp(640)), contH < 140.dp)
            if (ui.continueItems.isNotEmpty()) {
                ShelfLabel("Continue playing")
                LazyRow(
                    Modifier.fillMaxWidth().focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(s.dp(12)),
                    contentPadding = PaddingValues(end = s.dp(20), top = s.dp(2), bottom = s.dp(2)),
                ) {
                    items(ui.continueItems, key = { it.game.id }) { item ->
                        val m = if (item == ui.continueItems.first()) Modifier.focusRequester(first) else Modifier
                        ContinueCard(item, Modifier.width(contH * 2.12f).height(contH).then(m), a, compact = contH < 140.dp)
                    }
                }
            }
            Row(Modifier.padding(end = s.dp(20), top = s.dp(4)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                ShelfLabel("Library", Modifier.weight(1f))
                Filter.entries.forEach { f ->
                    val n = ui.games.filtered(f, ui.favouriteIds).size
                    if (f == Filter.ALL || n > 0) FilterChip("${f.label} $n", f == filter) { filter = f }
                }
            }
            val shown = ui.games.filtered(filter, ui.favouriteIds).ifEmpty { ui.games }
            LazyRow(
                Modifier.fillMaxWidth().focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
                contentPadding = PaddingValues(end = s.dp(20), top = s.dp(2), bottom = s.dp(6)),
            ) {
                items(shown, key = { it.id }) { g ->
                    val m = if (ui.continueItems.isEmpty() && g == shown.first()) Modifier.focusRequester(first) else Modifier
                    ShelfPoster(g, g.id in ui.favouriteIds, Modifier.height(posterH).aspectRatio(2f / 3f).then(m), a)
                }
            }
        }
    }
}

@Composable
private fun ShelfPortrait(ui: HomeUi, a: ShelfActions) {
    val s = Nebula.scale
    val t = Nebula.type
    val compact = Nebula.form.isCompact
    val cols = if (compact) 3 else 5
    val hero = ui.continueItems.firstOrNull()
    // The library lists every game, the one in Continue playing included ("All N" counts them all).
    val posters = ui.games
    val side = s.dp(if (compact) 16 else 24)
    val first = remember { FocusRequester() }
    val dpad = dpadLikely()
    LaunchedEffect(ui.loading) { if (!ui.loading && dpad) runCatching { first.requestFocus() } }
    LazyVerticalGrid(
        columns = GridCells.Fixed(cols),
        modifier = Modifier.fillMaxSize().background(NebulaColors.bg),
        contentPadding = PaddingValues(start = LocalRailInset.current + side, end = side, top = s.dp(12), bottom = s.dp(24)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
        verticalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.statusBarsPadding().padding(bottom = s.dp(4)), verticalAlignment = Alignment.CenterVertically) {
                NebulaStar(Modifier.size(s.dp(22)))
                Spacer(Modifier.width(s.dp(8)))
                Text("Nebula", style = t.heading, color = NebulaColors.text)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { HostCard(ui, a, Modifier.padding(bottom = s.dp(4))) }
        item(span = { GridItemSpan(maxLineSpan) }) { a.nowPlaying(Modifier.fillMaxWidth(), compact) }
        if (hero != null) {
            item(span = { GridItemSpan(maxLineSpan) }) { ShelfLabel("Continue playing", Modifier.padding(top = s.dp(4))) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueCard(hero, Modifier.fillMaxWidth().height(s.dp(if (compact) 180 else 260)).focusRequester(first), a, big = true)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = s.dp(6)), verticalAlignment = Alignment.CenterVertically) {
                ShelfLabel("Library", Modifier.weight(1f))
                Text("All ${ui.games.size}", style = t.label, color = NebulaColors.textSecondary)
            }
        }
        items(posters, key = { it.id }) { g -> ShelfPoster(g, g.id in ui.favouriteIds, Modifier.fillMaxWidth().aspectRatio(2f / 3f), a) }
    }
}

/** Upright phones are touch-first; only take initial focus when a D-pad or controller is likely. */
@Composable
private fun dpadLikely(): Boolean = Nebula.form.isTv

@Composable
private fun ShelfLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = Nebula.type.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = NebulaColors.textMuted, modifier = modifier)
}

/** "atom · Ready  HEVC · 120 Hz": opens Hosts. */
@Composable
private fun HostButton(host: Host?, settings: StreamSettings, onClick: () -> Unit) {
    host ?: return
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    Row(
        Modifier.height(s.dp(36)).semantics { contentDescription = "${host.name}, ${host.status.label()}. Switch host" }
            .nebulaClickable(shape, onClick).background(NebulaColors.surface, shape).border(1.dp, Color(0xFF2A2A30), shape)
            .padding(horizontal = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(8)),
    ) {
        StatusDot(host.status)
        Text("${host.name} · ${host.status.label()}", style = Nebula.type.secondary, color = NebulaColors.text, maxLines = 1)
        Text(streamSummary(settings), style = Nebula.type.mono.copy(fontSize = s.sp(11)), color = NebulaColors.textMuted, maxLines = 1)
    }
}

internal fun streamSummary(s: StreamSettings): String =
    listOfNotNull(s.codec.takeIf { it != VideoCodec.AUTO }?.name, "${s.fps} Hz").joinToString(" · ")

/** The upright host card: host, status, and one-tap desktops (Virtual display / Mirror). */
@Composable
private fun HostCard(ui: HomeUi, a: ShelfActions, modifier: Modifier) {
    val host = ui.host ?: return
    val s = Nebula.scale
    val t = Nebula.type
    val shape = RoundedCornerShape(s.dp(14))
    val desktops = ui.games.filter { it.kind == GameKind.DESKTOP }
    val virtualDesk = desktops.firstOrNull { it.name.contains("virtual", true) }
    val mirrorDesk = desktops.firstOrNull { it.name.contains("mirror", true) }
    Column(
        modifier.fillMaxWidth().background(NebulaColors.surface, shape).border(1.dp, NebulaColors.border, shape).padding(s.dp(14)),
        verticalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(s.dp(40)).background(Color(0xFF18181B), RoundedCornerShape(s.dp(10))), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DesktopWindows, null, tint = NebulaColors.text, modifier = Modifier.size(s.dp(20)))
            }
            Spacer(Modifier.width(s.dp(10)))
            Column(Modifier.weight(1f)) {
                Text(host.name, style = t.bodyStrong, color = NebulaColors.text, maxLines = 1)
                val status = listOfNotNull(host.status.label(), host.gpu ?: host.version).joinToString(" · ")
                Text(status, style = t.label, color = if (host.status == io.github.f_e_n_y_x.nebula.domain.model.HostStatus.ONLINE) NebulaColors.success else NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val linkShape = RoundedCornerShape(s.dp(8))
            Text(
                "Switch", style = t.label, color = NebulaColors.textSecondary,
                modifier = Modifier.nebulaClickable(linkShape, a.hosts).padding(horizontal = s.dp(8), vertical = s.dp(10)),
            )
        }
        if (virtualDesk != null || mirrorDesk != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                virtualDesk?.let { NebulaButton("Virtual display", onClick = { a.play(it, DisplayMode.VIRTUAL) }, modifier = Modifier.weight(1f)) }
                mirrorDesk?.let { NebulaButton("Mirror desktop", onClick = { a.play(it, DisplayMode.MIRROR) }, style = ButtonStyle.Secondary, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A "Continue playing" card: hero art, name, when and how it was last played. Opens the game's page;
 * the round play button (or the controller's X) resumes straight away.
 */
@Composable
private fun ContinueCard(item: ContinueItem, modifier: Modifier, a: ShelfActions, big: Boolean = false, compact: Boolean = false) {
    val s = Nebula.scale
    val t = Nebula.type
    val g = item.game
    val shape = RoundedCornerShape(s.dp(14))
    val sub = continueSubtitle(item)
    Box(
        modifier
            .gameKeys(onDetails = { a.details(g) }, onPlay = { a.play(g, item.mode) })
            .semantics { contentDescription = "${g.name}. $sub" }
            .nebulaClickable(shape, { a.details(g) }, focusScale = 1.03f)
            .border(1.dp, NebulaColors.border, shape),
    ) {
        ArtImage(g.art.hero ?: g.art.header ?: g.art.poster, g.name, null, Modifier.fillMaxSize(), alignment = HeroFocus,
            fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.85f to NebulaColors.bg.copy(alpha = 0.92f))))
        val play = s.dp(if (big) 48 else if (compact) 36 else 44)
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = s.dp(if (big) 14 else 12), end = s.dp(10), bottom = s.dp(if (big) 12 else 10)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(g.name, style = if (big) t.heading.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else t.bodyStrong, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub.isNotBlank()) Text(sub, style = t.label, color = Color(0xFFC8C8CE), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(s.dp(8)))
            Box(
                Modifier.size(play).semantics { contentDescription = "Play ${g.name}" }
                    .nebulaClickable(CircleShape, { a.play(g, item.mode) }, role = Role.Button)
                    .background(NebulaColors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(play * 0.5f)) }
        }
    }
}

internal fun continueSubtitle(item: ContinueItem): String {
    val whenText = relativeAgo(item.playedAtMs?.div(1000) ?: item.game.lastPlayedEpochS)?.replaceFirstChar(Char::uppercase)
    val how = item.mode?.let { if (it == DisplayMode.MIRROR) "Mirror" else "Virtual display" }
    return listOfNotNull(whenText, how).joinToString(" · ")
}

@Composable
private fun ShelfPoster(g: Game, favourite: Boolean, modifier: Modifier, a: ShelfActions) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    var menu by remember { mutableStateOf(false) }
    val cardFocus = rememberMenuFocus(menu)
    Box(
        modifier
            .focusRequester(cardFocus)
            .gameKeys(onDetails = { a.details(g) }, onPlay = { a.play(g, null) }, onMenu = { menu = true })
            .nebulaClickable(shape, { a.details(g) }, focusScale = 1.06f, onLongClick = { menu = true })
            .border(1.dp, Color.White.copy(alpha = 0.1f), shape),
    ) {
        ArtImage(g.art.poster, g.name, g.name, Modifier.fillMaxSize(), fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
        if (g.art.poster == null) {
            Text(
                g.name, style = Nebula.type.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(s.dp(8)),
            )
        }
        if (favourite) FavouriteBadge(Modifier.align(Alignment.TopEnd))
        GameCardMenu(menu, favourite, { menu = false }, { a.toggleFavourite(g) }, { a.details(g) })
    }
}

@Composable
private fun FilterChip(text: String, on: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.height(s.dp(30)).semantics { selected = on }
            .nebulaClickable(shape, onClick, role = Role.Tab)
            .background(if (on) NebulaColors.accentTint else Color.Transparent, shape)
            .border(1.dp, if (on) NebulaColors.accentText else Color(0xFF2A2A30), shape)
            .padding(horizontal = s.dp(12)),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Nebula.type.label, color = if (on) NebulaColors.text else NebulaColors.textSecondary, maxLines = 1) }
}

/**
 * Controller shortcuts on a game tile: Y opens details, X (or media Play) plays, and Menu opens the
 * card menu (favourite, details) when there is one, else details.
 */
internal fun Modifier.gameKeys(onDetails: () -> Unit, onPlay: () -> Unit, onMenu: (() -> Unit)? = null): Modifier = onPreviewKeyEvent { e ->
    if (e.type != KeyEventType.KeyUp) return@onPreviewKeyEvent e.key == Key.ButtonY || e.key == Key.ButtonX || e.key == Key.Menu || e.key == Key.MediaPlay
    when (e.key) {
        Key.ButtonY -> { onDetails(); true }
        Key.Menu -> { (onMenu ?: onDetails)(); true }
        Key.ButtonX, Key.MediaPlay -> { onPlay(); true }
        else -> false
    }
}

/** "Good morning / afternoon / evening" by the device clock. */
internal fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
