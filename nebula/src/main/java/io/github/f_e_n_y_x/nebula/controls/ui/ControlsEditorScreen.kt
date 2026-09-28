package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map

/**
 * The editor as its own screen (from Settings or a game), over a preview: the game's artwork
 * when opened for a game, otherwise a dim stage. Full screen like a stream, so the layout is
 * exactly what playing shows.
 */
@Composable
fun ControlsEditorScreen(container: AppContainer, nav: Navigator, hostId: String?, gameId: String?) {
    val ctx = LocalContext.current
    val store = remember { ControlsStore.get(ctx) }
    val game by remember(hostId, gameId) {
        if (hostId == null || gameId == null) emptyFlow<io.github.f_e_n_y_x.nebula.domain.model.Game?>() else container.library.observeGames(hostId).map { list -> list.firstOrNull { it.id == gameId } }
    }.collectAsState(initial = null)
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        val window = activity?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    val gameKey = if (hostId != null && gameId != null) ControlsStore.gameKey(hostId, gameId) else null
    ControlsEditor(
        store = store,
        gameKey = gameKey,
        gameName = game?.name,
        onClose = { nav.back() },
    ) {
        PreviewStage(game?.art?.hero ?: game?.art?.header, game?.name)
    }
}

@Composable
internal fun PreviewStage(art: String?, name: String?) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF1D1838), NebulaColors.bg), radius = 1400f),
        ),
    ) {
        if (art != null) ArtImage(art, name ?: "Preview", null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
