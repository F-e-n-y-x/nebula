package io.github.f_e_n_y_x.nebula.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import io.github.f_e_n_y_x.nebula.ui.theme.Geist
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * TV Material 3 with Nova's tokens, so the TV screens and the phone/tablet screens share one palette
 * (NebulaColors) instead of two themes drifting apart.
 */
@Composable
fun NebulaTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NebulaColors.accent,
            onPrimary = Color.White,
            secondary = NebulaColors.accentText,
            onSecondary = NebulaColors.bg,
            background = NebulaColors.bg,
            onBackground = NebulaColors.text,
            surface = NebulaColors.surface,
            onSurface = NebulaColors.text,
            surfaceVariant = NebulaColors.raised,
            onSurfaceVariant = NebulaColors.textSecondary,
            border = NebulaColors.controlBorder,
            borderVariant = NebulaColors.border,
            error = NebulaColors.danger,
        ),
        content = content,
    )
}

/** 10-foot text styles (dp-exact to the NebA TV design at 960×540 dp). */
object TvType {
    val display = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 40.sp)
    val heading = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val body = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val bodyStrong = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val meta = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val label = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp)
    val eyebrow = androidx.compose.ui.text.TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.sp)
}

/** Overscan-safe insets for 10-foot screens (48 dp sides, 24 dp top and bottom). */
val TvOverscan = PaddingValues(horizontal = 48.dp, vertical = 24.dp)

/** The focus ring every TV control shares: a white 2 dp outline plus a soft violet glow. */
internal val TvFocusBorder = Border(BorderStroke(2.dp, Color.White))
internal val TvFocusGlow = Glow(NebulaColors.accentText.copy(alpha = 0.45f), 10.dp)

/**
 * A TV action button: the primary one is filled with the accent; secondary ones are glassy with a
 * visible outline. Both grow slightly and get the white ring when focused.
 */
@Composable
fun TvActionButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    primary: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.heightIn(min = if (subtitle != null) 56.dp else 44.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) NebulaColors.accent else Color(0x99111113),
            contentColor = if (primary) Color.White else NebulaColors.text,
            focusedContainerColor = if (primary) NebulaColors.accent else NebulaColors.text,
            focusedContentColor = if (primary) Color.White else NebulaColors.bg,
            pressedContainerColor = if (primary) NebulaColors.accent else NebulaColors.text,
            pressedContentColor = if (primary) Color.White else NebulaColors.bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = if (primary) Border.None else Border(BorderStroke(1.dp, NebulaColors.controlBorder), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = if (primary) TvFocusGlow else Glow.None),
    ) {
        Row(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = TvType.bodyStrong, maxLines = 1)
                if (subtitle != null) Text(subtitle, style = TvType.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** A small pill in the header (Hosts, Settings): quiet until focused. */
@Composable
fun TvPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, leading: (@Composable RowScope.() -> Unit)? = null) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xB3111113),
            contentColor = NebulaColors.text,
            focusedContainerColor = NebulaColors.text,
            focusedContentColor = NebulaColors.bg,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Color(0xFF2A2A30)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
    ) {
        Row(Modifier.padding(horizontal = 14.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            leading?.invoke(this)
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = TvType.label.copy(fontSize = 13.sp), maxLines = 1)
        }
    }
}

/** A controller hint in the footer: a key cap and what it does. */
@Composable
fun TvKeyHint(key: String, action: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).background(Color(0x33FFFFFF), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(key, style = TvType.label.copy(fontWeight = FontWeight.SemiBold), color = NebulaColors.text) }
        Spacer(Modifier.width(8.dp))
        Text(action, style = TvType.label, color = NebulaColors.textSecondary)
    }
}

/**
 * Pivot scrolling for a column of rows: the focused row settles near the top of the rows area, so the
 * next row always peeks in below it. Rows themselves keep [rowSpec] (the platform TV pivot).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotRows(parentFraction: Float = 0.08f, content: @Composable (rowSpec: BringIntoViewSpec) -> Unit) {
    val rowSpec = LocalBringIntoViewSpec.current
    val spec = remember(parentFraction) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
                val target = parentFraction * containerSize
                // Already fully visible and near the pivot: don't move.
                if (offset >= 0 && offset + size <= containerSize && kotlin.math.abs(offset - target) < 8f) return 0f
                return offset - target
            }
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec) { content(rowSpec) }
}

/** Gives a lazy row back the normal (platform) bring-into-view behaviour inside [PivotRows]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WithRowSpec(spec: BringIntoViewSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}
