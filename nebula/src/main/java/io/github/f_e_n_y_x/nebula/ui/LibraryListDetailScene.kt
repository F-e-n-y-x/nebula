package io.github.f_e_n_y_x.nebula.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import io.github.f_e_n_y_x.nebula.ui.theme.FormFactor
import io.github.f_e_n_y_x.nebula.ui.theme.LocalFormFactor
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import io.github.f_e_n_y_x.nebula.ui.theme.WidthClass

/** How the library entry is being shown: alone (the full home) or as the list pane beside a game's details. */
data class LibraryPane(val isListPane: Boolean = false, val selectedGameId: String? = null)

val LocalLibraryPane = compositionLocalOf { LibraryPane() }

/** False for a detail entry shown next to its list: the list is right there, so no back arrow. */
val LocalBackButtonVisibility = compositionLocalOf { true }

object LibraryPanes {
    object ListKey : NavMetadataKey<Boolean>
    /** The selected game's id, so the list pane can highlight it. */
    object DetailKey : NavMetadataKey<String>

    fun list() = metadata { put(ListKey, true) }
    fun detail(gameId: String) = metadata { put(DetailKey, gameId) }
}

/**
 * Tablet list-detail: the library as a poster list on the left, the game's details on the right.
 * Used only on expanded, not-short windows (tablets, unfolded foldables, desktop windows) and never
 * on TV, where details are a full 10-foot screen.
 */
class LibraryListDetailSceneStrategy(private val enabled: Boolean) : SceneStrategy<Route> {
    override fun SceneStrategyScope<Route>.calculateScene(entries: List<NavEntry<Route>>): Scene<Route>? {
        if (!enabled || entries.size < 2) return null
        val detail = entries.last().takeIf { it.metadata.contains(LibraryPanes.DetailKey) } ?: return null
        val list = entries[entries.lastIndex - 1].takeIf { it.metadata.contains(LibraryPanes.ListKey) } ?: return null
        return LibraryListDetailScene(
            // Keyed by the list: switching games animates inside the scene instead of replacing it.
            key = list.contentKey,
            previousEntries = entries.dropLast(1),
            listEntry = list,
            detailEntry = detail,
        )
    }

    companion object {
        /** Expanded width and at least medium height: a landscape phone (short) stays single-pane. */
        fun shouldSplit(form: FormFactor) = !form.isTv && form.width == WidthClass.EXPANDED && form.heightDp >= 480
    }
}

private class LibraryListDetailScene(
    override val key: Any,
    override val previousEntries: List<NavEntry<Route>>,
    val listEntry: NavEntry<Route>,
    val detailEntry: NavEntry<Route>,
) : Scene<Route> {
    override val entries: List<NavEntry<Route>> = listOf(listEntry, detailEntry)

    override val content: @Composable () -> Unit = {
        val form = LocalFormFactor.current
        BoxWithConstraints(Modifier.fillMaxSize().background(NebulaColors.bg)) {
            val listWidth = (maxWidth * 0.36f).coerceIn(320.dp, 440.dp)
            val detailWidth = maxWidth - listWidth - 1.dp
            Row(Modifier.fillMaxSize()) {
                CompositionLocalProvider(
                    LocalLibraryPane provides LibraryPane(isListPane = true, selectedGameId = detailEntry.metadata[LibraryPanes.DetailKey]),
                    LocalFormFactor provides form.pane(listWidth),
                    LocalRailInset provides 0.dp,
                ) {
                    Box(Modifier.width(listWidth).fillMaxHeight()) { listEntry.Content() }
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(NebulaColors.border))
                CompositionLocalProvider(
                    LocalBackButtonVisibility provides false,
                    LocalFormFactor provides form.pane(detailWidth),
                ) {
                    AnimatedContent(
                        targetState = detailEntry,
                        contentKey = { it.contentKey },
                        transitionSpec = { (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 }) togetherWith fadeOut(tween(120)) },
                        modifier = Modifier.width(detailWidth).fillMaxHeight(),
                        label = "detailPane",
                    ) { it.Content() }
                }
            }
        }
    }
}

/** The form factor as seen by one pane: layouts inside respond to the pane's width, not the window's. */
private fun FormFactor.pane(width: Dp): FormFactor {
    val w = width.value.toInt()
    return copy(
        widthDp = w,
        width = when {
            w >= 840 -> WidthClass.EXPANDED
            w >= 600 -> WidthClass.MEDIUM
            else -> WidthClass.COMPACT
        },
    )
}
