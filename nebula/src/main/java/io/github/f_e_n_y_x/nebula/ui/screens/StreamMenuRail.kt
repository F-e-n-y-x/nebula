package io.github.f_e_n_y_x.nebula.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.launch

/**
 * The stream menu's sections, in the order the menu shows them. The section rail has one icon
 * for each that is on screen for this stream (a PC without a microphone has no Microphone).
 */
enum class MenuSection(val label: String, val icon: ImageVector) {
    QUICK("Quick toggles", Icons.Outlined.Tune),
    STATS("Stream stats", Icons.Outlined.Insights),
    DISPLAY("Display and resolution", Icons.Outlined.Monitor),
    FRAMEGEN("Frame generation", Icons.Outlined.AutoAwesome),
    INPUT("Keyboard, touch and mouse", Icons.Outlined.Mouse),
    CONTROLS("On-screen controls", Icons.Outlined.SportsEsports),
    MIC("Microphone", Icons.Outlined.Mic),
    PC("Clipboard and PC", Icons.Outlined.Computer),
    FEEDBACK("Gyro, rumble and haptics", Icons.Outlined.Vibration),
    NETWORK("Bitrate and connection", Icons.Outlined.NetworkCheck),
    KEYS("Special keys", Icons.Outlined.Keyboard),
    END("Disconnect or quit", Icons.Rounded.PowerSettingsNew),
}

/**
 * Where each section starts in the menu's scrolling content, kept by [menuSection] markers.
 * Positions are within the content, so they don't change while it scrolls.
 */
@Stable
class MenuSections {
    private var content: LayoutCoordinates? = null
    private val coords = HashMap<MenuSection, LayoutCoordinates>()
    /** Section → top, px from the top of the scrolling content. Only sections on screen. */
    val tops = mutableStateMapOf<MenuSection, Float>()
    /** Sections with no place in the content (the pinned footer): always listed. */
    val pinned = mutableStateMapOf<MenuSection, Boolean>()

    internal fun setContent(c: LayoutCoordinates) { content = c; refresh() }

    internal fun set(section: MenuSection, c: LayoutCoordinates) { coords[section] = c; refresh(section) }

    internal fun remove(section: MenuSection) { coords.remove(section); tops.remove(section) }

    private fun refresh(only: MenuSection? = null) {
        val root = content?.takeIf { it.isAttached } ?: return
        for ((k, c) in coords) {
            if (only != null && k != only) continue
            if (!c.isAttached) continue
            val y = root.localPositionOf(c, Offset.Zero).y
            if (tops[k] != y) tops[k] = y
        }
    }

    /** The sections the rail lists, in menu order. */
    fun present(): List<MenuSection> = MenuSection.entries.filter { it in tops || pinned[it] == true }

    /**
     * The section in view at [scrollPx]: the last one whose top has reached [threshold] px
     * below the top of the view; at the very end, the last one that starts inside the view.
     */
    fun inView(scrollPx: Int, maxPx: Int, viewportPx: Int, threshold: Float): MenuSection? = inView(tops.toMap(), scrollPx, maxPx, viewportPx, threshold)

    companion object {
        fun inView(tops: Map<MenuSection, Float>, scrollPx: Int, maxPx: Int, viewportPx: Int, threshold: Float): MenuSection? {
            val ordered = tops.entries.sortedBy { it.value }
            if (ordered.isEmpty()) return null
            val atEnd = maxPx > 0 && scrollPx >= maxPx - 1
            if (atEnd) {
                // Sections too low to reach the top: the last one that starts in view wins.
                return ordered.lastOrNull { it.value <= scrollPx + viewportPx - threshold }?.key ?: ordered.last().key
            }
            return ordered.lastOrNull { it.value <= scrollPx + threshold }?.key ?: ordered.first().key
        }
    }
}

val LocalMenuSections = staticCompositionLocalOf<MenuSections?> { null }

/** Marks where [section] starts in the stream menu, for its rail; nothing outside the menu. */
fun Modifier.menuSection(section: MenuSection): Modifier = composed {
    val sections = LocalMenuSections.current ?: return@composed Modifier
    DisposableEffect(sections, section) { onDispose { sections.remove(section) } }
    Modifier.onGloballyPositioned { sections.set(section, it) }
}

/** On the menu's scrolling content (after verticalScroll): the frame section positions are measured in. */
fun Modifier.menuContent(sections: MenuSections): Modifier = onGloballyPositioned { sections.setContent(it) }

/**
 * A thin icon rail on the menu's right edge: one icon per section; tap (or OK on a remote) to
 * scroll that section to the top. The section in view is highlighted while scrolling. Each
 * icon has a content description, and a tooltip on long press, hover or D-pad focus.
 * [onEnd] handles the pinned Disconnect / Quit row (it doesn't scroll).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamMenuRail(sections: MenuSections, scroll: ScrollState, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val threshold = with(density) { 48.dp.toPx() }
    val margin = with(density) { 8.dp.toPx() }
    // A tapped section stays lit until the user scrolls by hand.
    var tapped by remember { mutableStateOf<MenuSection?>(null) }
    var animating by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving -> if (moving && !animating) tapped = null }
    }
    val spied by remember(sections, scroll) {
        derivedStateOf { sections.inView(scroll.value, scroll.maxValue, scroll.viewportSize, threshold) }
    }
    val active = tapped ?: spied
    val present = sections.present()
    val railScroll = rememberScrollState()
    Column(
        modifier.width(s.dp(52)).fillMaxHeight()
            .background(NebulaColors.surface.copy(alpha = 0.6f))
            .verticalScroll(railScroll)
            .padding(vertical = s.dp(10))
            .semantics { contentDescription = "Menu sections" }
            .testTag("menu-rail"),
        verticalArrangement = Arrangement.spacedBy(s.dp(4)),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        present.forEach { sec ->
            RailItem(sec, selected = sec == active) {
                tapped = sec
                if (sec == MenuSection.END) {
                    scope.launch { animating = true; try { scroll.animateScrollTo(scroll.maxValue) } finally { animating = false } }
                    onEnd()
                } else {
                    val top = sections.tops[sec] ?: return@RailItem
                    scope.launch {
                        animating = true
                        try { scroll.animateScrollTo((top - margin).toInt().coerceAtLeast(0)) } finally { animating = false }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailItem(sec: MenuSection, selected: Boolean, onClick: () -> Unit) {
    val s = Nebula.scale
    val tip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val bring = remember { BringIntoViewRequester() }
    // The highlighted icon is kept in view when the rail itself has to scroll (short screens).
    LaunchedEffect(selected) { if (selected) runCatching { bring.bringIntoView() } }
    val shape = RoundedCornerShape(s.dp(12))
    val bg by animateColorAsState(if (selected) NebulaColors.accentTint else Color.Transparent, tween(if (Nebula.reducedMotion) 0 else 180), label = "railBg")
    val tint by animateColorAsState(if (selected) NebulaColors.accentText else NebulaColors.textSecondary, tween(if (Nebula.reducedMotion) 0 else 180), label = "railTint")
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Start),
        tooltip = { PlainTooltip { Text(sec.label) } },
        state = tip,
        focusable = false,
    ) {
        Box(
            Modifier.size(s.dp(44)).bringIntoViewRequester(bring)
                .onFocusChanged { f -> if (f.isFocused) scope.launch { runCatching { tip.show() } } else if (tip.isVisible) tip.dismiss() }
                .nebulaClickable(shape, onClick, role = Role.Tab)
                .background(bg, shape)
                .semantics { contentDescription = sec.label; this.selected = selected; role = Role.Tab }
                .testTag("rail:${sec.name.lowercase()}"),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(sec.icon, null, tint = tint, modifier = Modifier.size(s.dp(22)))
            if (selected) {
                // A short bar on the inner edge: a second cue besides colour.
                Box(Modifier.align(androidx.compose.ui.Alignment.CenterStart).size(width = s.dp(3), height = s.dp(18)).background(NebulaColors.accentText, RoundedCornerShape(s.dp(2))))
            }
        }
    }
}
