package io.github.f_e_n_y_x.nebula.ui.tv

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.ContinueItem
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.Host
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.ui.HomeUi
import io.github.f_e_n_y_x.nebula.ui.HomeViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.NebulaStar
import io.github.f_e_n_y_x.nebula.ui.components.StatusDot
import io.github.f_e_n_y_x.nebula.ui.components.label
import io.github.f_e_n_y_x.nebula.ui.screens.EmptyLibrary
import io.github.f_e_n_y_x.nebula.ui.screens.FavouriteBadge
import io.github.f_e_n_y_x.nebula.ui.screens.GameCardMenu
import io.github.f_e_n_y_x.nebula.ui.screens.rememberMenuFocus
import io.github.f_e_n_y_x.nebula.ui.screens.HomeActions
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import io.github.f_e_n_y_x.nebula.ui.screens.HeroFocus
import io.github.f_e_n_y_x.nebula.ui.screens.continueSubtitle
import io.github.f_e_n_y_x.nebula.ui.screens.gameKeys
import io.github.f_e_n_y_x.nebula.ui.screens.metaLine
import io.github.f_e_n_y_x.nebula.ui.screens.modeLabel
import io.github.f_e_n_y_x.nebula.ui.screens.playtime
import io.github.f_e_n_y_x.nebula.ui.screens.relativeAgo
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private sealed interface TvRow {
    val key: String
    val title: String

    data class Continue(val items: List<ContinueItem>) : TvRow {
        override val key = "continue"
        override val title = "Continue playing"
    }

    data class Games(override val key: String, override val title: String, val games: List<Game>) : TvRow
    data class Hosts(val hosts: List<Host>) : TvRow {
        override val key = "hosts"
        override val title = "Hosts"
    }
}

private fun HomeUi.rows(): List<TvRow> = buildList {
    if (continueItems.isNotEmpty()) add(TvRow.Continue(continueItems))
    if (favourites.isNotEmpty()) add(TvRow.Games("favourites", "Favourites", favourites))
    if (recentlyPlayed.isNotEmpty()) add(TvRow.Games("recent", "Recently played", recentlyPlayed))
    add(TvRow.Games("all", "All games", allGames))
    add(TvRow.Hosts(hosts))
}

private fun TvRow.firstKey(): String? = when (this) {
    is TvRow.Continue -> items.firstOrNull()?.let { "$key/${it.game.id}" }
    is TvRow.Games -> games.firstOrNull()?.let { "$key/${it.id}" }
    is TvRow.Hosts -> hosts.firstOrNull()?.let { "$key/${it.id}" } ?: "$key/+add"
}

/**
 * The Compose-for-TV home (Spotlight A on TV): the focused game's hero fills the screen behind a
 * header, a hero block (title, meta, Play and Details) and rows of cards: Continue playing (this
 * device's recent streams), Recently played (the host's history), All games, and Hosts.
 *
 * D-pad / controller: A or OK plays the focused game, Y, Menu or a long OK press opens its details,
 * B goes back. Each row remembers its focused card, and the last focused card is restored when you
 * come back from a game.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(container: AppContainer, nav: Navigator, hostId: String, home: HomeActions) {
    val vm = viewModel(key = "home-$hostId") { HomeViewModel(container, hostId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (!ui.loading && ui.games.isEmpty()) return EmptyLibrary(ui.host, nav)
    // Play wakes a sleeping PC first (the wake overlay is drawn by LibraryScreen).
    val play: (Game, DisplayMode?) -> Unit = { g, m -> home.play(g, m ?: ui.focusedMode.takeIf { g.id == ui.focused?.id }) }
    val details: (Game) -> Unit = { g -> nav.push(Route.Details(hostId, g.id)) }
    val openHost: (Host) -> Unit = { h ->
        when {
            h.paired && h.id != hostId -> { vm.selectHost(h); nav.top(Route.Library(h.id)) }
            h.paired -> Unit
            else -> nav.push(Route.Pair(h.id))
        }
    }

    NebulaTvTheme {
        Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
            // The backdrop follows focus, but waits a beat so fast scrolling doesn't strobe art.
            var backdrop by remember { mutableStateOf<Game?>(null) }
            LaunchedEffect(ui.focused?.id) {
                if (backdrop != null) delay(160)
                backdrop = ui.focused
            }
            Crossfade(targetState = backdrop, animationSpec = tween(400), label = "backdrop") { g ->
                if (g != null) {
                    ArtImage(
                        g.art.hero ?: g.art.header ?: g.art.poster, g.name, null, Modifier.fillMaxSize(),
                        alignment = HeroFocus, fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null,
                    )
                }
            }
            // Left and bottom scrims: text on the left third and the rows are always ≥ 4.5:1.
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to NebulaColors.bg.copy(alpha = 0.94f), 0.4f to NebulaColors.bg.copy(alpha = 0.7f), 1f to NebulaColors.bg.copy(alpha = 0.1f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.62f to NebulaColors.bg.copy(alpha = 0.9f), 1f to NebulaColors.bg.copy(alpha = 0.98f))))

            Column(Modifier.fillMaxSize().padding(top = 24.dp, bottom = 14.dp)) {
                TvHeader(ui.host, onHosts = { nav.top(Route.Hosts) }, onSettings = { nav.top(Route.Settings()) })
                ui.focused?.let { TvHero(ui, it, onPlay = { play(it, null) }, onDetails = { details(it) }, onToggleFavourite = { vm.toggleFavourite(it) }) }
                TvRows(ui, Modifier.weight(1f), vm::focus, play, details, vm::toggleFavourite, openHost, addHost = { nav.top(Route.Hosts) }, nowPlaying = home.nowPlaying)
                Row(Modifier.padding(horizontal = 48.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    TvKeyHint("A", "Play")
                    TvKeyHint("Y", "Details")
                    TvKeyHint("☰", "Options")
                    TvKeyHint("B", "Back")
                }
            }
        }
    }
}

@Composable
private fun TvHeader(host: Host?, onHosts: () -> Unit, onSettings: () -> Unit) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(20_000) } }
    Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        NebulaStar(Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("Nebula", style = TvType.heading, color = NebulaColors.text)
        if (host != null) {
            Spacer(Modifier.width(16.dp))
            Row(
                Modifier.height(30.dp).background(Color(0xB3111113), RoundedCornerShape(50)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(host.status)
                Spacer(Modifier.width(8.dp))
                Text("${host.name} · ${host.status.label()}", style = TvType.label.copy(fontSize = 13.sp), color = NebulaColors.text)
            }
        }
        Spacer(Modifier.weight(1f))
        TvPill("Hosts", onHosts, icon = Icons.Outlined.Dns)
        Spacer(Modifier.width(10.dp))
        TvPill("Settings", onSettings, icon = Icons.Outlined.Tune)
        Spacer(Modifier.width(18.dp))
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), style = TvType.bodyStrong, color = NebulaColors.textSecondary)
    }
}

@Composable
private fun TvHero(ui: HomeUi, g: Game, onPlay: () -> Unit, onDetails: () -> Unit, onToggleFavourite: () -> Unit) {
    val context = LocalContext.current
    val cont = ui.continueItems.firstOrNull { it.game.id == g.id }
    val kicker = when {
        g.running -> "Running on ${ui.host?.name ?: "your PC"}"
        cont?.playedAtMs != null -> "Continue · ${relativeAgo(cont.playedAtMs / 1000)}"
        g.lastPlayedEpochS != null && ui.options.showPlaytime -> "Last played · ${relativeAgo(g.lastPlayedEpochS)}"
        g.kind == GameKind.DESKTOP -> "Desktop"
        else -> "In your library"
    }
    Column(Modifier.padding(horizontal = 48.dp).padding(top = 18.dp).widthIn(max = 560.dp)) {
        Text(kicker.uppercase(), style = TvType.eyebrow, color = NebulaColors.accentText)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(60.dp), contentAlignment = Alignment.CenterStart) {
            if (g.art.logo != null) {
                coil3.compose.AsyncImage(
                    model = g.art.logo, contentDescription = g.name, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
                    modifier = Modifier.heightIn(max = 60.dp).widthIn(max = 340.dp),
                )
            } else {
                Text(g.name, style = TvType.display, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        val meta = listOfNotNull(g.metaLine().ifBlank { null }, playtime(g.playtimeS)?.takeIf { ui.options.showPlaytime }).joinToString("  ·  ")
        Spacer(Modifier.height(8.dp))
        Text(meta.ifBlank { " " }, style = TvType.meta, color = NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvActionButton("Play", onPlay, subtitle = modeLabel(context, ui.focusedMode, ui.settings), icon = Icons.Rounded.PlayArrow, primary = true)
            TvActionButton("Details", onDetails, icon = Icons.Outlined.Info, modifier = Modifier.heightIn(min = 56.dp))
            val fav = g.id in ui.favouriteIds
            TvActionButton(
                "Favourite", onToggleFavourite,
                icon = if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder, modifier = Modifier.heightIn(min = 56.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvRows(
    ui: HomeUi,
    modifier: Modifier,
    onFocusGame: (Game) -> Unit,
    play: (Game, DisplayMode?) -> Unit,
    details: (Game) -> Unit,
    toggleFavourite: (Game) -> Unit,
    openHost: (Host) -> Unit,
    addHost: () -> Unit,
    nowPlaying: @Composable (Modifier, Boolean) -> Unit,
) {
    val rows = ui.rows()
    // "row/item" of the last focused card; restored when the user comes back to the home.
    var focusKey by rememberSaveable { mutableStateOf<String?>(null) }
    val restore = remember { FocusRequester() }
    val target = focusKey ?: rows.firstNotNullOfOrNull { it.firstKey() }
    LaunchedEffect(ui.loading) {
        if (ui.loading) return@LaunchedEffect
        delay(60) // let the rows lay out first
        runCatching { restore.requestFocus() }
    }
    val listState = rememberLazyListState()
    PivotRows { rowSpec ->
        LazyColumn(
            modifier = modifier.fillMaxWidth().padding(top = 14.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // A game running on the PC: Resume / Quit above the rows (Down from Play reaches it).
            item(key = "now-playing") { nowPlaying(Modifier.padding(horizontal = 48.dp, vertical = 6.dp).widthIn(max = 640.dp), true) }
            items(rows, key = { it.key }) { row ->
                Column {
                    Text(row.title, style = TvType.label.copy(fontSize = 13.sp), color = NebulaColors.textSecondary, modifier = Modifier.padding(horizontal = 48.dp))
                    WithRowSpec(rowSpec) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        ) {
                            when (row) {
                                is TvRow.Continue -> items(row.items, key = { it.game.id }) { item ->
                                    val k = "${row.key}/${item.game.id}"
                                    ContinueTvCard(
                                        item,
                                        Modifier.trackFocus(k, target, restore) { focusKey = k; onFocusGame(item.game) }
                                            .gameKeys(onDetails = { details(item.game) }, onPlay = { play(item.game, item.mode) }),
                                        onClick = { play(item.game, item.mode) }, onLongClick = { details(item.game) },
                                    )
                                }
                                is TvRow.Games -> items(row.games, key = { it.id }) { g ->
                                    val k = "${row.key}/${g.id}"
                                    var menu by remember { mutableStateOf(false) }
                                    val fav = g.id in ui.favouriteIds
                                    val cardFocus = rememberMenuFocus(menu)
                                    Box {
                                        PosterTvCard(
                                            g, fav,
                                            Modifier.focusRequester(cardFocus).trackFocus(k, target, restore) { focusKey = k; onFocusGame(g) }
                                                .gameKeys(onDetails = { details(g) }, onPlay = { play(g, null) }, onMenu = { menu = true }),
                                            onClick = { play(g, null) }, onLongClick = { menu = true },
                                        )
                                        GameCardMenu(menu, fav, { menu = false }, { toggleFavourite(g) }, { details(g) })
                                    }
                                }
                                is TvRow.Hosts -> {
                                    items(row.hosts, key = { it.id }) { h ->
                                        val k = "${row.key}/${h.id}"
                                        HostTvCard(h, current = h.id == ui.host?.id, Modifier.trackFocus(k, target, restore) { focusKey = k }, onClick = { openHost(h) })
                                    }
                                    item(key = "+add") {
                                        val k = "${row.key}/+add"
                                        AddHostTvCard(Modifier.trackFocus(k, target, restore) { focusKey = k }, onClick = addHost)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Attaches the restore requester to the card that should get focus back, and reports focus. */
private fun Modifier.trackFocus(key: String, target: String?, restore: FocusRequester, onFocused: () -> Unit): Modifier =
    then(if (key == target) Modifier.focusRequester(restore) else Modifier).onFocusChanged { if (it.isFocused) onFocused() }

private val cardShape = RoundedCornerShape(12.dp)

@Composable
private fun tvCardBorder() = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = cardShape))

@Composable
private fun ContinueTvCard(item: ContinueItem, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val g = item.game
    Column(Modifier.width(ContinueW)) {
        Card(
            onClick = onClick, onLongClick = onLongClick,
            modifier = modifier.width(ContinueW).height(ContinueH),
            shape = CardDefaults.shape(cardShape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            border = tvCardBorder(),
            glow = CardDefaults.glow(focusedGlow = TvFocusGlow),
        ) {
            Box(Modifier.fillMaxSize()) {
                ArtImage(g.art.header ?: g.art.hero ?: g.art.poster, g.name, g.name, Modifier.fillMaxSize(), alignment = HeroFocus)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to NebulaColors.bg.copy(alpha = 0.92f))))
                Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = TvType.label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(continueSubtitle(item), style = TvType.label.copy(fontSize = 11.sp), color = Color(0xFFC8C8CE), maxLines = 1)
                    }
                    Box(Modifier.size(26.dp).background(NebulaColors.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterTvCard(g: Game, favourite: Boolean, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        onClick = onClick, onLongClick = onLongClick,
        modifier = modifier.width(PosterW).height(PosterH),
        shape = CardDefaults.shape(cardShape),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = tvCardBorder(),
        glow = CardDefaults.glow(focusedGlow = TvFocusGlow),
    ) {
        Box(Modifier.fillMaxSize()) {
            ArtImage(g.art.poster, g.name, g.name, Modifier.fillMaxSize(), fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
            if (g.art.poster == null) {
                Text(g.name, style = TvType.label, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
            }
            if (g.running) {
                Text(
                    "Running", style = TvType.label, color = NebulaColors.success,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).background(Color(0xE610231A), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (favourite) FavouriteBadge(Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun HostTvCard(h: Host, current: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.width(ContinueW).height(HostH),
        shape = CardDefaults.shape(cardShape),
        colors = CardDefaults.colors(containerColor = Color(0xE6111113), focusedContainerColor = NebulaColors.raised),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, if (current) NebulaColors.accentText else NebulaColors.border), shape = cardShape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = cardShape),
        ),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DesktopWindows, null, tint = NebulaColors.text, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(h.name, style = TvType.bodyStrong, color = NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (current) Text("Current", style = TvType.label, color = NebulaColors.accentText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(h.status)
                Spacer(Modifier.width(8.dp))
                val status = if (!h.paired) "Not paired · Select to pair" else listOfNotNull(h.status.label(), h.gpu ?: h.version).joinToString(" · ")
                Text(status, style = TvType.label, color = if (h.status == HostStatus.ONLINE && h.paired) NebulaColors.success else NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AddHostTvCard(modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.width(PosterW * 1.6f).height(HostH),
        shape = CardDefaults.shape(cardShape),
        colors = CardDefaults.colors(containerColor = Color(0x99111113), focusedContainerColor = NebulaColors.raised),
        scale = CardDefaults.scale(focusedScale = 1.08f),
        border = CardDefaults.border(
            border = Border(BorderStroke(1.dp, NebulaColors.controlBorder), shape = cardShape),
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = cardShape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Add, null, tint = NebulaColors.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add a PC", style = TvType.bodyStrong, color = NebulaColors.text)
        }
    }
}

private val ContinueW: Dp = 208.dp
private val ContinueH: Dp = 117.dp
private val PosterW: Dp = 88.dp
private val PosterH: Dp = 132.dp
private val HostH: Dp = 88.dp
