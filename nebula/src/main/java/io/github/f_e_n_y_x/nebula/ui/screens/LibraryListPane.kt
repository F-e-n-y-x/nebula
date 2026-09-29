package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.ui.HomeViewModel
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.Route
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * The library as the list pane of the tablet list-detail layout: every game as a poster, the one on
 * the right highlighted. Picking another swaps the details pane; Enter on a poster plays it, like
 * the keyboard shortcut on the details page.
 */
@Composable
fun LibraryListPane(container: AppContainer, nav: Navigator, hostId: String, selectedGameId: String?, home: HomeActions) {
    val vm = viewModel(key = "home-$hostId") { HomeViewModel(container, hostId) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val t = Nebula.type
    val grid = rememberLazyGridState()
    // Keep the selected game in view when the pane first appears (e.g. after a rotation).
    LaunchedEffect(ui.loading) {
        val i = ui.games.indexOfFirst { it.id == selectedGameId }
        if (!ui.loading && i > 6) grid.scrollToItem(i + 1)
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(s.dp(104)),
        state = grid,
        modifier = Modifier.fillMaxSize().background(NebulaColors.bg),
        contentPadding = PaddingValues(start = s.dp(20), end = s.dp(20), bottom = s.dp(24)),
        horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
        verticalArrangement = Arrangement.spacedBy(s.dp(12)),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = s.dp(20), bottom = s.dp(6))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", style = t.title, color = NebulaColors.text, modifier = Modifier.weight(1f))
                    HostChip(ui.host)
                }
                Spacer(Modifier.height(s.dp(4)))
                Text("${ui.games.size} on ${ui.host?.name ?: "this PC"}", style = t.secondary, color = NebulaColors.textMuted)
            }
        }
        items(ui.games, key = { it.id }) { g ->
            ListPoster(
                g, selected = g.id == selectedGameId, favourite = g.id in ui.favouriteIds,
                onOpen = { nav.showDetails(hostId, g.id) },
                // Wakes a sleeping PC first, like Play everywhere else.
                onPlay = { home.play(g, null) },
                onToggleFavourite = { vm.toggleFavourite(g) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun ListPoster(g: Game, selected: Boolean, favourite: Boolean, onOpen: () -> Unit, onPlay: () -> Unit, onToggleFavourite: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(10))
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.semantics { this.selected = selected }) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(2f / 3f)
                .onPreviewKeyEvent { e ->
                    // Keyboard: Enter plays straight away, Space (or a tap) shows the details.
                    if (e.key == Key.Enter || e.key == Key.NumPadEnter) { if (e.type == KeyEventType.KeyUp) onPlay(); true } else false
                }
                .gameKeys(onDetails = onOpen, onPlay = onPlay, onMenu = { menu = true })
                .nebulaClickable(shape, onOpen, focusScale = 1.04f, onLongClick = { menu = true })
                .border(if (selected) 2.dp else 1.dp, if (selected) NebulaColors.accentText else Color.White.copy(alpha = 0.1f), shape),
        ) {
            ArtImage(g.art.poster, g.name, g.name, Modifier.fillMaxSize(), fallbackIcon = if (g.kind == GameKind.DESKTOP) Icons.Outlined.DesktopWindows else null)
            if (favourite) FavouriteBadge(Modifier.align(Alignment.TopEnd))
            GameCardMenu(menu, favourite, { menu = false }, onToggleFavourite, onOpen)
        }
        Spacer(Modifier.height(s.dp(6)))
        Text(
            g.name, style = Nebula.type.label, color = if (selected) NebulaColors.text else NebulaColors.textSecondary,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
