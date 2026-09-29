package io.github.f_e_n_y_x.nebula.domain

import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.GameKind
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Which display mode "Play" uses for a game: the game's preset (Game settings), else the user's
 * remembered choice, else the host's default for that app, else desktops mirror and games get a
 * virtual display, else the global default.
 */
class ResolvePlayModeUseCase(private val prefs: PreferencesRepository, private val presets: GamePresetRepository? = null) {
    operator fun invoke(game: Game): Flow<DisplayMode> {
        val preset = presets?.observe(game.hostId, game.id) ?: kotlinx.coroutines.flow.flowOf(GamePreset.NONE)
        return combine(prefs.modeFor(game.hostId, game.id), prefs.streamSettings, preset) { remembered, settings, p ->
            resolve(game, remembered, settings, p.displayMode)
        }
    }

    companion object {
        fun resolve(game: Game, remembered: DisplayMode?, settings: StreamSettings, preset: DisplayMode? = null): DisplayMode =
            preset
                ?: remembered
                ?: game.hostDefaultMode
                ?: when {
                    game.kind == GameKind.DESKTOP && game.name.contains("mirror", ignoreCase = true) -> DisplayMode.MIRROR
                    game.kind == GameKind.DESKTOP && game.name.contains("virtual", ignoreCase = true) -> DisplayMode.VIRTUAL
                    else -> settings.defaultMode
                }
    }
}

/** Library order: running first, then games by most recently played, then desktops/apps, then name. */
object SortLibrary {
    operator fun invoke(games: List<Game>): List<Game> =
        games.sortedWith(
            compareByDescending<Game> { it.running }
                .thenBy { it.kind != GameKind.GAME }
                .thenByDescending { it.lastPlayedEpochS ?: 0L }
                .thenBy { it.name.lowercase() },
        )
}
