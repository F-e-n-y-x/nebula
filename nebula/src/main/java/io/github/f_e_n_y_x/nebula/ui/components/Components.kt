package io.github.f_e_n_y_x.nebula.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.f_e_n_y_x.nebula.domain.model.HostStatus
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlin.math.absoluteValue

/**
 * Click + visible focus for every interactive surface: a 2 dp light ring when focused (D-pad, gamepad,
 * keyboard) and a small press dip. [focusScale] lets tiles grow when focused (TV/console feel).
 */
@Composable
fun Modifier.nebulaClickable(
    shape: Shape,
    onClick: () -> Unit,
    focusScale: Float = 1f,
    role: Role = Role.Button,
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
): Modifier {
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val reduced = Nebula.reducedMotion
    val target = when {
        pressed -> 0.97f
        focused -> focusScale
        else -> 1f
    }
    val s by animateFloatAsState(target, if (reduced) tween(0) else tween(160), label = "focusScale")
    return this
        .scale(s)
        .then(if (focused) Modifier.border(BorderStroke(2.dp, NebulaColors.focus), shape) else Modifier)
        .clip(shape)
        .clickable(interactionSource = interaction, indication = null, role = role, onClick = onClick)
}

enum class ButtonStyle { Primary, Secondary, Ghost, Danger }

@Composable
fun NebulaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    sublabel: String? = null,
) {
    val s = Nebula.scale
    val shape = RoundedCornerShape(s.dp(12))
    val (bg, fg, border) = when (style) {
        ButtonStyle.Primary -> Triple(NebulaColors.accent, Color.White, null)
        ButtonStyle.Secondary -> Triple(Color(0x99111113), NebulaColors.text, NebulaColors.controlBorder)
        ButtonStyle.Ghost -> Triple(Color.Transparent, NebulaColors.text, null)
        ButtonStyle.Danger -> Triple(Color(0xCC2A1413), NebulaColors.danger, NebulaColors.danger)
    }
    Row(
        modifier = modifier
            .heightIn(min = s.dp(if (sublabel != null) 56 else 48))
            .nebulaClickable(shape, onClick)
            .background(bg, shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .padding(horizontal = s.dp(18), vertical = s.dp(10)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(s.dp(20)))
        Column {
            Text(text, style = Nebula.type.bodyStrong, color = fg, maxLines = 1)
            if (sublabel != null) {
                Text(sublabel, style = Nebula.type.label, color = fg.copy(alpha = 0.78f), maxLines = 1)
            }
        }
    }
}

@Composable
fun NebulaIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = Nebula.scale
    Box(
        modifier = modifier
            .size(s.dp(48))
            .nebulaClickable(CircleShape, onClick)
            .background(Color(0x66111113), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = NebulaColors.text, modifier = Modifier.size(s.dp(22)))
    }
}

/** Deterministic, tasteful art for games without artwork: two-tone glow seeded by the title, never letters. */
@Composable
fun GeneratedArt(seed: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val h = seed.hashCode().absoluteValue
    val hue1 = (h % 360).toFloat()
    val hue2 = ((h / 360) % 360).toFloat()
    val c1 = Color.hsv(hue1, 0.55f, 0.55f)
    val c2 = Color.hsv(hue2, 0.45f, 0.30f)
    Box(modifier.clipToBounds().background(NebulaColors.surface), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(listOf(c2, NebulaColors.surface), start = Offset.Zero, end = Offset(size.width, size.height)))
            drawCircle(
                Brush.radialGradient(listOf(c1.copy(alpha = 0.85f), Color.Transparent), center = Offset(size.width * 0.3f, size.height * 0.25f), radius = size.maxDimension * 0.7f),
                radius = size.maxDimension * 0.7f, center = Offset(size.width * 0.3f, size.height * 0.25f),
            )
        }
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(Nebula.scale.dp(40)))
    }
}

/** Artwork with a generated fallback underneath while loading or when missing. */
@Composable
fun ArtImage(
    url: String?,
    seed: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackIcon: ImageVector? = null,
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier) {
        GeneratedArt(seed, Modifier.fillMaxSize(), fallbackIcon)
        if (url != null) {
            AsyncImage(
                model = url, contentDescription = contentDescription, contentScale = contentScale,
                alignment = alignment, modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun StatusDot(status: HostStatus, size: Dp = 8.dp) {
    val color = when (status) {
        HostStatus.ONLINE -> NebulaColors.success
        HostStatus.STREAMING -> NebulaColors.accentText
        HostStatus.OFFLINE -> NebulaColors.textMuted
        HostStatus.UNKNOWN -> NebulaColors.warning
    }
    Box(Modifier.size(size).background(color, CircleShape))
}

fun HostStatus.label(): String = when (this) {
    HostStatus.ONLINE -> "Ready"
    HostStatus.STREAMING -> "Streaming"
    HostStatus.OFFLINE -> "Offline"
    HostStatus.UNKNOWN -> "Checking…"
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = NebulaColors.textSecondary, background: Color = Color(0x99111113), leading: (@Composable () -> Unit)? = null) {
    val s = Nebula.scale
    Row(
        modifier
            .background(background, RoundedCornerShape(50))
            .border(1.dp, NebulaColors.border, RoundedCornerShape(50))
            .padding(horizontal = s.dp(12), vertical = s.dp(6)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(s.dp(8)))
        }
        Text(text, style = Nebula.type.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.textMuted, modifier = modifier.padding(bottom = Nebula.scale.dp(10)))
}

/** Dark scrim so text on artwork always meets contrast. */
fun Modifier.leftScrim(strength: Float = 0.94f) = this.background(
    Brush.horizontalGradient(0f to NebulaColors.bg.copy(alpha = strength), 0.55f to NebulaColors.bg.copy(alpha = strength * 0.55f), 1f to Color.Transparent),
)

fun Modifier.bottomScrim(strength: Float = 1f) = this.background(
    Brush.verticalGradient(0f to Color.Transparent, 0.55f to NebulaColors.bg.copy(alpha = 0.35f * strength), 1f to NebulaColors.bg.copy(alpha = strength)),
)

/** The Nebula mark: a four-point star with a dark core (matches the launcher icon). */
@Composable
fun NebulaStar(modifier: Modifier = Modifier, color: Color = Color(0xFF8B72FF)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.567f, h * 0.433f, w * 0.567f, h * 0.433f, w, h * 0.5f)
            cubicTo(w * 0.567f, h * 0.567f, w * 0.567f, h * 0.567f, w * 0.5f, h)
            cubicTo(w * 0.433f, h * 0.567f, w * 0.433f, h * 0.567f, 0f, h * 0.5f)
            cubicTo(w * 0.433f, h * 0.433f, w * 0.433f, h * 0.433f, w * 0.5f, 0f)
            close()
        }
        drawPath(p, color)
        drawCircle(NebulaColors.bg, radius = w * 0.065f, center = Offset(w * 0.5f, h * 0.5f))
    }
}
