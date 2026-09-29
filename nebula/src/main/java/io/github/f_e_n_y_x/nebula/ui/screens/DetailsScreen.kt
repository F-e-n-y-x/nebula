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
import androidx.compose.foundation.layout.fillMaxHeight
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.statusBarScrim
import coil3.SingletonImageLoader
import androidx.compose.runtime.key
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.HostGating
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.ui.DetailsUi
import io.github.f_e_n_y_x.nebula.ui.LocalBackButtonVisibility
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(container: AppContainer, nav: Navigator, hostId: String, gameId: String) {
    val vm = viewModel { DetailsViewModel(container, hostId, gameId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val game = ui.game ?: run { Box(Modifier.fillMaxSize().background(NebulaColors.bg)); return }
    val form = Nebula.form
    val s = Nebula.scale
    val hostVm = viewModel(key = "host-actions-details-$hostId-$gameId") { io.github.f_e_n_y_x.nebula.ui.HostActionsViewModel(container, hostId, gameId) }
    val wake by hostVm.wake.collectAsStateWithLifecycle()
    val host by hostVm.host.collectAsStateWithLifecycle()
    var showCommands by remember { mutableStateOf(false) }
    var showGameSettings by remember { mutableStateOf(false) }
    val openGameSettings = { showGameSettings = true }
    val favourite by vm.favourite.collectAsStateWithLifecycle()
    val star: @Composable () -> Unit = { FavouriteButton(favourite, { vm.toggleFavourite() }) }
    HostActionToasts(hostVm)
    // A sleeping PC is woken first; the game launches once it answers.
    val play: (DisplayMode) -> Unit = { m -> vm.remember(m); hostVm.playWhenAwake { nav.push(Route.Stream(hostId, game.id, m)) } }
    val commands by hostVm.commands.collectAsStateWithLifecycle()
    LaunchedEffect(host?.status) { hostVm.loadCommands(force = true) }
    val openCommands: (() -> Unit)? = if (HostGating.showCommandsEntry(host, commands)) ({ showCommands = true }) else null
    val wide = form.isLandscape
    val ctx = LocalContext.current
    val refresh = { vm.refresh(onArtCleared = { SingletonImageLoader.get(ctx).memoryCache?.clear() }) }
    val showAbout = ui.details != null && ui.options.showDetails
    val asleep = host?.takeIf { HostGating.needsWake(it) }?.name
    // This game running on the PC: Resume / Quit above the play choices.
    val games = remember(vm) { vm.ui.map { listOfNotNull(it.game) } }
    val nowPlaying: @Composable () -> Unit = {
        NowPlayingSection(
            container, hostId, games, onlyGameId = gameId, modifier = Modifier.padding(bottom = s.dp(16)),
            onResume = { _, m -> hostVm.playWhenAwake { nav.push(Route.Stream(hostId, gameId, m)) } },
        )
    }

    Box(Modifier.fillMaxSize()) {
    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = refresh, modifier = Modifier.fillMaxSize().background(NebulaColors.bg)) {
        if (wide) {
            key(ui.artVersion) {
                ArtImage(game.art.hero ?: game.art.header, game.name, null, Modifier.fillMaxSize(), alignment = Alignment.TopEnd,
                    fallbackIcon = if (game.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
            }
            // Dark only behind the text: the left column and the bottom edge. The art stays clear top-right.
            Box(Modifier.fillMaxSize().leftScrim(0.97f, reach = 0.66f))
            Box(Modifier.fillMaxSize().bottomScrim())
            // Short landscape phones (~360–420 dp tall): tighter spacing, and the play choices are
            // pinned below a scrolling header so they're never pushed off the bottom edge.
            val short = form.heightDp < 480
            Row(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = s.dp(40), vertical = s.dp(if (short) 8 else 20)),
                horizontalArrangement = Arrangement.spacedBy(s.dp(40)),
            ) {
                if (short) Column(Modifier.weight(1.2f).fillMaxHeight()) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                            if (LocalBackButtonVisibility.current) NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() })
                            star()
                            MoreMenu(refresh, openCommands, openGameSettings)
                        }
                        Spacer(Modifier.height(s.dp(8)))
                        Header(ui)
                        Spacer(Modifier.height(s.dp(12)))
                        Stats(ui)
                    }
                    Spacer(Modifier.height(s.dp(12)))
                    PlayChoices(ui, play, showNote = false, asleepHost = asleep, onCommands = openCommands, onGameSettings = openGameSettings, nowPlaying = nowPlaying)
                } else Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                        if (LocalBackButtonVisibility.current) NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() })
                        star()
                        MoreMenu(refresh, openCommands, openGameSettings)
                    }
                    Spacer(Modifier.height(s.dp(20)))
                    Header(ui)
                    Spacer(Modifier.height(s.dp(22)))
                    PlayChoices(ui, play, asleepHost = asleep, onCommands = openCommands, onGameSettings = openGameSettings, nowPlaying = nowPlaying)
                    Spacer(Modifier.height(s.dp(24)))
                    Stats(ui)
                }
                if (showAbout && form.widthDp >= 760) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = s.dp(if (short) 8 else 68), bottom = s.dp(8))) {
                        // A glass panel keeps About readable over the visible art.
                        Column(
                            Modifier.background(NebulaColors.bg.copy(alpha = 0.86f), RoundedCornerShape(s.dp(16)))
                                .border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(16)))
                                .padding(s.dp(20)),
                        ) { About(ui, wide = true) }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().aspectRatio(if (form.isCompact) 1.05f else 1.5f)) {
                    key(ui.artVersion) {
                        ArtImage(game.art.hero ?: game.art.header ?: game.art.poster, game.name, null, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                    }
                    Box(Modifier.fillMaxSize().bottomScrim())
                    Box(Modifier.fillMaxWidth().statusBarScrim())
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(s.dp(16)),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (LocalBackButtonVisibility.current) NebulaIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", { nav.back() })
                        Row(horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
                            star()
                            MoreMenu(refresh, openCommands, openGameSettings)
                        }
                    }
                    Column(Modifier.align(Alignment.BottomStart).padding(horizontal = s.dp(20))) { Header(ui) }
                }
                Column(Modifier.padding(horizontal = s.dp(20)).navigationBarsPadding()) {
                    Spacer(Modifier.height(s.dp(18)))
                    PlayChoices(ui, play, asleepHost = asleep, onCommands = openCommands, onGameSettings = openGameSettings, nowPlaying = nowPlaying)
                    Spacer(Modifier.height(s.dp(22)))
                    Stats(ui)
                    if (showAbout) {
                        Spacer(Modifier.height(s.dp(26)))
                        About(ui)
                    }
                    Spacer(Modifier.height(s.dp(28)))
                }
            }
        }
    }
    WakeOverlay(host?.name ?: "your PC", game.name, wake, onCancel = hostVm::cancelWake, onRetry = hostVm::retryWake)
    if (showCommands) HostCommandsDialog(host?.name ?: "your PC", game.name, hostVm, onDismiss = { showCommands = false })
    if (showGameSettings) GameSettingsDialog(container, hostId, gameId, game.name, onDismiss = { showGameSettings = false; vm.reloadHostProfile() })
    // Debug QA: `--es start gamesettings:<game>` opens the details page with Game settings open.
    LaunchedEffect(Unit) { if (io.github.f_e_n_y_x.nebula.ui.DebugStart.takeGameSettings(gameId)) showGameSettings = true }
    }
}

@Composable
private fun Header(ui: DetailsUi) {
    val game = ui.game ?: return
    val s = Nebula.scale
    val maxH = s.dp(if (Nebula.form.isCompact || Nebula.form.heightDp < 480) 56 else 92)
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
private fun PlayChoices(
    ui: DetailsUi, onPlay: (DisplayMode) -> Unit, showNote: Boolean = true, asleepHost: String? = null, onCommands: (() -> Unit)? = null,
    onGameSettings: (() -> Unit)? = null, nowPlaying: @Composable () -> Unit = {},
) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val primary = remember { FocusRequester() }
    LaunchedEffect(ui.game?.id) { runCatching { primary.requestFocus() } }
    val modes = if (ui.mode == DisplayMode.VIRTUAL) listOf(DisplayMode.VIRTUAL, DisplayMode.MIRROR) else listOf(DisplayMode.MIRROR, DisplayMode.VIRTUAL)
    val compact = Nebula.form.isCompact && !Nebula.form.isLandscape
    nowPlaying()
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
    if (asleepHost != null || onCommands != null || onGameSettings != null) {
        Spacer(Modifier.height(s.dp(10)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
            if (asleepHost != null) Pill("$asleepHost is asleep · Play wakes it", color = NebulaColors.warning)
            if (onGameSettings != null) NebulaButton("Game settings", onClick = onGameSettings, style = ButtonStyle.Ghost, icon = Icons.Outlined.Tune)
            if (onCommands != null) NebulaButton("Host commands", onClick = onCommands, style = ButtonStyle.Ghost, icon = Icons.Outlined.Terminal)
        }
    }
    GameSettingsChips(ui)
    if (!showNote) return
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
            .padding(horizontal = s.dp(14), vertical = s.dp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlighted) Color.White else NebulaColors.text, modifier = Modifier.size(s.dp(24)))
        Spacer(Modifier.width(s.dp(12)))
        Column {
            Text(title, style = Nebula.type.bodyStrong, color = if (highlighted) Color.White else NebulaColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Nebula.type.label, color = if (highlighted) Color.White.copy(alpha = 0.82f) else NebulaColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Stats(ui: DetailsUi) {
    val game = ui.game ?: return
    val s = Nebula.scale
    val items = listOfNotNull(
        playtime(game.playtimeS)?.takeIf { ui.options.showPlaytime }?.let { "Played" to it.removeSuffix(" played") },
        relativeAgo(game.lastPlayedEpochS)?.takeIf { ui.options.showPlaytime }?.let { "Last played" to it.replaceFirstChar(Char::uppercase) },
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
private fun About(ui: DetailsUi, wide: Boolean = false) {
    val d = ui.details ?: return
    val s = Nebula.scale
    var viewing by remember { mutableStateOf<String?>(null) }
    if (d.description != null) {
        SectionTitle("About")
        Text(
            d.description, style = Nebula.type.body, color = NebulaColors.textSecondary,
            maxLines = if (wide) (if (Nebula.form.heightDp < 480) 3 else 5) else Int.MAX_VALUE, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(s.dp(16)))
    }
    // Screenshots come right after the description so they're visible without scrolling.
    var loadShots by remember { mutableStateOf(false) }
    if (d.screenshots.isNotEmpty() && ui.saveData && !loadShots) {
        SectionTitle("Screenshots")
        NebulaButton("Load ${d.screenshots.size} screenshots", onClick = { loadShots = true }, style = ButtonStyle.Secondary, icon = Icons.Outlined.Image)
        Text("Data saver is on while you're on mobile data.", style = Nebula.type.label, color = NebulaColors.textMuted, modifier = Modifier.padding(top = s.dp(6)))
        Spacer(Modifier.height(s.dp(16)))
    } else if (d.screenshots.isNotEmpty()) {
        SectionTitle("Screenshots")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), contentPadding = PaddingValues(vertical = s.dp(4))) {
            items(d.screenshots) { url ->
                val shape = RoundedCornerShape(s.dp(10))
                ArtImage(url, url, "Screenshot", Modifier.width(s.dp(if (wide) 200 else 240)).aspectRatio(16f / 9f)
                    .nebulaClickable(shape, { viewing = url }, focusScale = 1.04f).border(1.dp, NebulaColors.border, shape))
            }
        }
        Spacer(Modifier.height(s.dp(16)))
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
        Spacer(Modifier.height(s.dp(8)))
        Text("Details and screenshots from Steam, fetched by Nova", style = Nebula.type.label, color = NebulaColors.textMuted)
    }
    viewing?.let { url ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(Color.Black).nebulaClickable(RoundedCornerShape(0.dp), { viewing = null }), contentAlignment = Alignment.Center) {
                ArtImage(url, url, "Screenshot", Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            }
        }
    }
}

/**
 * What Game settings changes for this game, at a glance: this device's preset ("1440p · 120 fps ·
 * FG") and the PC's settings ("PC: 60 fps cap · FSR 2"). Nothing when the game uses Settings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GameSettingsChips(ui: DetailsUi) {
    val s = Nebula.scale
    val ctx = LocalContext.current
    val game = ui.game ?: return
    val controls = remember(game.hostId, game.id) { io.github.f_e_n_y_x.nebula.controls.ControlsStore.get(ctx) }
    val data by controls.data.collectAsStateWithLifecycle()
    val controlsName = remember(data, game.id) {
        val lib = controls.library(data)
        val key = io.github.f_e_n_y_x.nebula.controls.ControlsStore.gameKey(game.hostId, game.id)
        lib.assignedTo(key)?.let { id -> lib.findSet(id)?.name ?: lib.find(id)?.name }
    }
    val mine = io.github.f_e_n_y_x.nebula.domain.GamePresets.chips(ui.preset, controlsName)
    val pc = ui.hostProfile?.chips().orEmpty()
    if (mine.isEmpty() && pc.isEmpty()) return
    Spacer(Modifier.height(s.dp(10)))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(6)), verticalArrangement = Arrangement.spacedBy(s.dp(6))) {
        mine.forEach { Pill(it, color = NebulaColors.text, leading = { Icon(Icons.Outlined.Tune, null, tint = NebulaColors.accent, modifier = Modifier.size(s.dp(14))) }) }
        pc.forEach { Pill("PC · $it", color = NebulaColors.textSecondary) }
    }
}

/** "More" actions for D-pad and TV users, who can't pull to refresh. */
@Composable
private fun MoreMenu(onRefresh: () -> Unit, onCommands: (() -> Unit)? = null, onGameSettings: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        NebulaIconButton(Icons.Rounded.MoreVert, "More actions", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = NebulaColors.raised) {
            DropdownMenuItem(
                text = { Text("Refresh details and art", style = Nebula.type.body, color = NebulaColors.text) },
                leadingIcon = { Icon(Icons.Outlined.Refresh, null, tint = NebulaColors.textSecondary) },
                onClick = { open = false; onRefresh() },
            )
            if (onGameSettings != null) {
                DropdownMenuItem(
                    text = { Text("Game settings…", style = Nebula.type.body, color = NebulaColors.text) },
                    leadingIcon = { Icon(Icons.Outlined.Tune, null, tint = NebulaColors.textSecondary) },
                    onClick = { open = false; onGameSettings() },
                )
            }
            if (onCommands != null) {
                DropdownMenuItem(
                    text = { Text("Host commands…", style = Nebula.type.body, color = NebulaColors.text) },
                    leadingIcon = { Icon(Icons.Outlined.Terminal, null, tint = NebulaColors.textSecondary) },
                    onClick = { open = false; onCommands() },
                )
            }
        }
    }
}
