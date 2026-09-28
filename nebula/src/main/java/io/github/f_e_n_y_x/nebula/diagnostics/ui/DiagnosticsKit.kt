package io.github.f_e_n_y_x.nebula.diagnostics.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.diagnostics.Level
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/** A surface card in the settings style. */
@Composable
internal fun DiagCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(16))
    Column(
        modifier
            .fillMaxWidth()
            .background(NebulaColors.surface, shape)
            .border(1.dp, NebulaColors.border, shape)
            .padding(s.dp(16)),
        content = content,
    )
}

internal fun Level.color(): Color = when (this) {
    Level.OK -> NebulaColors.success
    Level.INFO -> NebulaColors.textMuted
    Level.WARN -> NebulaColors.warning
    Level.BAD -> NebulaColors.danger
}

@Composable
internal fun LevelDot(level: Level, modifier: Modifier = Modifier) {
    Box(modifier.size(Nebula.scale.dp(8)).background(level.color(), CircleShape))
}

/** Label on the left, a monospace value on the right; wraps instead of truncating. */
@Composable
internal fun KeyValue(label: String, value: String, level: Level? = null) {
    val s = Nebula.scale
    val indent = label.startsWith("  ")
    if (!indent && label.length > 24) {
        // Long names (decoders, hosts) get their own line instead of wrapping mid-word.
        Column(Modifier.fillMaxWidth().padding(top = s.dp(6), bottom = s.dp(3))) {
            Text(label, style = Nebula.type.secondary, color = NebulaColors.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (level != null && level != Level.INFO) {
                    LevelDot(level)
                    Spacer(Modifier.width(s.dp(8)))
                }
                Text(value, style = Nebula.type.mono, color = NebulaColors.text)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().padding(start = if (indent) s.dp(14) else 0.dp, top = s.dp(3), bottom = s.dp(3)),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label.trim(), style = if (indent) Nebula.type.label else Nebula.type.secondary,
            color = if (indent) NebulaColors.textMuted else NebulaColors.textSecondary,
            modifier = Modifier.weight(0.42f).padding(end = s.dp(10)),
        )
        Row(Modifier.weight(0.58f), verticalAlignment = Alignment.CenterVertically) {
            if (level != null && level != Level.INFO) {
                LevelDot(level)
                Spacer(Modifier.width(s.dp(8)))
            }
            Text(value, style = Nebula.type.mono, color = NebulaColors.text, textAlign = TextAlign.Start)
        }
    }
}

/** A one-line note with a coloured marker (warnings, hints). */
@Composable
internal fun Note(text: String, level: Level) {
    val s = Nebula.scale
    Row(Modifier.fillMaxWidth().padding(top = s.dp(8)), verticalAlignment = Alignment.Top) {
        LevelDot(level, Modifier.padding(top = s.dp(5)))
        Spacer(Modifier.width(s.dp(10)))
        Text(text, style = Nebula.type.secondary, color = if (level == Level.INFO) NebulaColors.textSecondary else level.color())
    }
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, body: String) {
    val s = Nebula.scale
    DiagCard {
        Column(Modifier.fillMaxWidth().padding(vertical = s.dp(24)), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(s.dp(56)).background(NebulaColors.accentTint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = NebulaColors.accentText, modifier = Modifier.size(s.dp(28)))
            }
            Spacer(Modifier.height(s.dp(14)))
            Text(title, style = Nebula.type.heading, color = NebulaColors.text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(s.dp(6)))
            Text(body, style = Nebula.type.secondary, color = NebulaColors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = s.dp(12)))
        }
    }
}

/** A labelled figure, for session stats. */
@Composable
internal fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, valueColor: Color = NebulaColors.text) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    Column(modifier.background(NebulaColors.raised, shape).padding(horizontal = s.dp(12), vertical = s.dp(10))) {
        Text(label.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.textMuted, maxLines = 1)
        Spacer(Modifier.height(s.dp(4)))
        Text(value, style = Nebula.type.bodyStrong, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub != null) Text(sub, style = Nebula.type.label, color = NebulaColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = Nebula.type.label, color = NebulaColors.textMuted)
}

internal val gap: Arrangement.Vertical @Composable get() = Arrangement.spacedBy(Nebula.scale.dp(14))

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
