package io.github.f_e_n_y_x.nebula.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.ui.components.NebulaStar
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.screens.DetailsScreen
import io.github.f_e_n_y_x.nebula.ui.screens.HostsScreen
import io.github.f_e_n_y_x.nebula.ui.screens.LibraryScreen
import io.github.f_e_n_y_x.nebula.ui.screens.OnboardingScreen
import io.github.f_e_n_y_x.nebula.ui.screens.PairScreen
import io.github.f_e_n_y_x.nebula.ui.screens.SettingsScreen
import io.github.f_e_n_y_x.nebula.ui.screens.StreamScreen
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.flow.first

sealed interface Route {
    data object Onboarding : Route
    data object Hosts : Route
    data class Pair(val hostId: String) : Route
    data class Library(val hostId: String) : Route
    data class Details(val hostId: String, val gameId: String) : Route
    data class Stream(val hostId: String, val gameId: String, val mode: DisplayMode) : Route
    /** [section] opens one settings section directly (its enum name, e.g. "Library"). */
    data class Settings(val section: String? = null) : Route
}

/** Navigation actions shared by every screen. */
class Navigator(private val stack: SnapshotStateList<Route>) {
    val current: Route get() = stack.last()
    fun push(r: Route) { stack.add(r) }

    /**
     * Pops one screen. At the root of Hosts or Settings it returns to the home section (the
     * library, or Hosts before any PC is paired), like a real app's tabs. Returns false only at
     * home, where Back should leave the app.
     */
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.lastIndex); return true }
        if (isAtHome) return false
        top(home)
        return true
    }

    /** Switch top-level section (rail / bottom bar): reset to just that section. */
    fun top(r: Route) { stack.clear(); stack.add(r) }
    var lastLibrary: Route.Library? = null
    val home: Route get() = lastLibrary ?: Route.Hosts
    val isAtHome: Boolean get() = stack.size == 1 && (stack[0] == home || stack[0] == Route.Onboarding)
}

/** Width of the nav rail drawn over the screen; the library draws its hero underneath it. */
val LocalRailInset = staticCompositionLocalOf { 0.dp }

private enum class Section(val label: String, val icon: ImageVector) {
    Library("Library", Icons.Outlined.SportsEsports),
    Hosts("Hosts", Icons.Outlined.Dns),
    Settings("Settings", Icons.Outlined.Tune),
}

@Composable
fun NebulaApp(container: AppContainer, startOverride: String? = null) {
    var start by remember { mutableStateOf<Route?>(null) }
    var homeLibrary by remember { mutableStateOf<Route.Library?>(null) }
    LaunchedEffect(Unit) {
        val hosts = container.hosts.observeHosts().first()
        val last = container.prefs.lastHostId.first()
        val paired = hosts.filter { it.paired }
        val pick = paired.firstOrNull { it.id == last } ?: paired.firstOrNull()
        homeLibrary = pick?.let { Route.Library(it.id) }
        start = debugStart(startOverride) ?: homeLibrary ?: Route.Onboarding
    }
    val initial = start
    if (initial == null) {
        Box(Modifier.fillMaxSize().background(NebulaColors.bg))
        return
    }
    val stack = remember { mutableStateListOf(initial) }
    val nav = remember { Navigator(stack).apply { lastLibrary = homeLibrary } }
    (stack.lastOrNull { it is Route.Library } as? Route.Library)?.let { nav.lastLibrary = it }

    val route = stack.last()
    val section = when (route) {
        is Route.Library -> Section.Library
        Route.Hosts -> Section.Hosts
        is Route.Settings -> Section.Settings
        else -> null
    }
    val form = Nebula.form
    val showChrome = section != null

    // Back at a section root goes home; NavDisplay handles the pops above that. Registered first,
    // so screen-level handlers (stream overlay, settings drill-down) take priority.
    BackHandler(enabled = stack.size == 1 && !nav.isAtHome) { nav.back() }

    val content: @Composable () -> Unit = {
        NavDisplay(
            backStack = stack,
            onBack = { nav.back() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
            entryProvider = entryProvider {
                entry<Route.Onboarding> { OnboardingScreen(container, nav) }
                entry<Route.Hosts> { HostsScreen(container, nav) }
                entry<Route.Pair> { PairScreen(container, nav, it.hostId) }
                entry<Route.Library> { LibraryScreen(container, nav, it.hostId) }
                entry<Route.Details> { DetailsScreen(container, nav, it.hostId, it.gameId) }
                entry<Route.Stream> { StreamScreen(container, nav, it.hostId, it.gameId, it.mode) }
                entry<Route.Settings> { SettingsScreen(container, nav, it.section) }
            },
        )
    }

    val go: (Section) -> Unit = { s ->
        when (s) {
            Section.Library -> nav.top(nav.lastLibrary ?: Route.Hosts)
            Section.Hosts -> nav.top(Route.Hosts)
            Section.Settings -> nav.top(Route.Settings())
        }
    }

    // One layout tree for every route so NavDisplay keeps its state when the chrome changes.
    val rail = showChrome && form.useRail
    val overlayRail = rail && section == Section.Library // the library hero runs full-bleed under the rail
    val railWidth = Nebula.scale.dp(RAIL_WIDTH)
    Box(Modifier.fillMaxSize().background(NebulaColors.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(start = if (rail && !overlayRail) railWidth else 0.dp)) {
                CompositionLocalProvider(LocalRailInset provides if (overlayRail) railWidth else 0.dp) { content() }
            }
            if (showChrome && !rail) NebulaBottomBar(section!!, go)
        }
        if (rail) NebulaRail(section!!, go, transparent = overlayRail)
    }
}

/** Debug builds only: `adb shell am start ... --es start details:gta5` opens a screen directly (QA screenshots). */
private fun debugStart(spec: String?): Route? {
    if (!io.github.f_e_n_y_x.nebula.BuildConfig.DEBUG || spec.isNullOrBlank()) return null
    val host = io.github.f_e_n_y_x.nebula.data.demo.DemoHost.HOST_ID
    val (name, arg) = spec.split(':', limit = 2).let { it[0] to it.getOrNull(1) }
    return when (name) {
        "onboarding" -> Route.Onboarding
        "hosts" -> Route.Hosts
        "pair" -> Route.Pair(arg ?: "demo-deck")
        "library" -> Route.Library(host)
        "details" -> Route.Details(host, arg ?: "gta5")
        "stream" -> Route.Stream(host, arg ?: "gta5", DisplayMode.VIRTUAL)
        "mirror" -> Route.Stream(host, arg ?: "gta5", DisplayMode.MIRROR)
        "settings" -> Route.Settings(arg)
        else -> null
    }
}

private const val RAIL_WIDTH = 88

@Composable
private fun NebulaRail(selected: Section, onSelect: (Section) -> Unit, transparent: Boolean) {
    val s = Nebula.scale
    Column(
        Modifier
            .fillMaxHeight()
            .width(s.dp(RAIL_WIDTH))
            .background(if (transparent) Color.Transparent else NebulaColors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(vertical = s.dp(20)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NebulaStar(Modifier.size(s.dp(30)))
        Spacer(Modifier.height(s.dp(28)))
        Section.entries.forEach { sec ->
            RailItem(sec, sec == selected) { onSelect(sec) }
            Spacer(Modifier.height(s.dp(8)))
        }
    }
}

@Composable
private fun RailItem(section: Section, selected: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(14))
    Column(
        Modifier
            .width(s.dp(72))
            .semantics { this.selected = selected; contentDescription = section.label }
            .nebulaClickable(shape, onClick, role = Role.Tab)
            .background(if (selected) NebulaColors.accentTint else Color.Transparent, shape)
            .padding(vertical = s.dp(10)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(section.icon, null, tint = if (selected) NebulaColors.accentText else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(24)))
        Spacer(Modifier.height(s.dp(4)))
        Text(section.label, style = Nebula.type.label, color = if (selected) NebulaColors.text else NebulaColors.textMuted)
    }
}

@Composable
private fun NebulaBottomBar(selected: Section, onSelect: (Section) -> Unit) {
    val s = Nebula.scale
    Row(
        Modifier
            .fillMaxWidth()
            .background(NebulaColors.bg)
            .navigationBarsPadding()
            .padding(horizontal = s.dp(12), vertical = s.dp(8)),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
    ) {
        Section.entries.forEach { sec ->
            val on = sec == selected
            val shape = RoundedCornerShape(s.dp(16))
            Column(
                Modifier
                    .weight(1f)
                    .semantics { this.selected = on }
                    .nebulaClickable(shape, { onSelect(sec) }, role = Role.Tab)
                    .padding(vertical = s.dp(6)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .background(if (on) NebulaColors.accentTint else Color.Transparent, RoundedCornerShape(50))
                        .padding(horizontal = s.dp(18), vertical = s.dp(4)),
                ) {
                    Icon(sec.icon, null, tint = if (on) NebulaColors.accentText else NebulaColors.textSecondary, modifier = Modifier.size(s.dp(22)))
                }
                Spacer(Modifier.height(4.dp))
                Text(sec.label, style = Nebula.type.label, color = if (on) NebulaColors.text else NebulaColors.textMuted)
            }
        }
    }
}
