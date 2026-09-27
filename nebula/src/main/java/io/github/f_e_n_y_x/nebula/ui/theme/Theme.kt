package io.github.f_e_n_y_x.nebula.ui.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.f_e_n_y_x.nebula.R

/** Nova brand tokens (SPEC.md, dark). Nebula is dark-only: it is a launcher you look at next to a screen. */
@Immutable
object NebulaColors {
    val bg = Color(0xFF0A0A0B)
    val surface = Color(0xFF111113)
    val raised = Color(0xFF17171A)
    val panel = Color(0xFF0E0E10)
    val border = Color(0xFF1E1E22)
    val divider = Color(0xFF1A1A1D)
    val controlBorder = Color(0xFF63636B)
    val text = Color(0xFFEDEDEF)
    val textSecondary = Color(0xFFA1A1A8)
    val textMuted = Color(0xFF8B8B93)
    val accent = Color(0xFF6B4EFF)
    val accentText = Color(0xFFB7A2FF)
    val accentTint = Color(0xFF1D1838)
    val success = Color(0xFF4CC38A)
    val successTint = Color(0xFF10231A)
    val warning = Color(0xFFE8A93F)
    val danger = Color(0xFFF2766E)
    val dangerTint = Color(0xFF2A1413)
    val info = Color(0xFF7AA7FF)
    val focus = Color(0xFFEDEDEF)
}

val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)
val GeistMono = FontFamily(
    Font(R.font.geist_mono_regular, FontWeight.Normal),
    Font(R.font.geist_mono_medium, FontWeight.Medium),
)

/** How the UI should adapt: width class drives layout, [isTv] drives 10-foot sizing and D-pad-first behaviour. */
@Immutable
data class FormFactor(
    val width: WidthClass,
    val isLandscape: Boolean,
    val isTv: Boolean,
    val widthDp: Int,
    val heightDp: Int,
) {
    val isCompact get() = width == WidthClass.COMPACT
    /** Nav rail instead of a bottom bar: tablets, landscape phones, TV. */
    val useRail get() = isTv || width != WidthClass.COMPACT
}

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

/** Type + spacing scale. TV multiplies everything for viewing from a couch. */
@Immutable
data class NebulaScale(val factor: Float) {
    fun dp(v: Int): Dp = (v * factor).dp
    fun sp(v: Int): TextUnit = (v * factor).sp
}

val LocalFormFactor = staticCompositionLocalOf { FormFactor(WidthClass.COMPACT, false, false, 400, 800) }
val LocalScale = staticCompositionLocalOf { NebulaScale(1f) }
val LocalReducedMotion = staticCompositionLocalOf { false }

@Immutable
data class NebulaType(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val secondary: TextStyle,
    val label: TextStyle,
    val eyebrow: TextStyle,
    val mono: TextStyle,
)

fun nebulaType(s: NebulaScale) = NebulaType(
    display = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Bold, fontSize = s.sp(44), lineHeight = s.sp(48), letterSpacing = (-0.02).em),
    title = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = s.sp(28), lineHeight = s.sp(34), letterSpacing = (-0.015).em),
    heading = TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = s.sp(18), lineHeight = s.sp(24)),
    body = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = s.sp(15), lineHeight = s.sp(22)),
    bodyStrong = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = s.sp(15), lineHeight = s.sp(22)),
    secondary = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = s.sp(13), lineHeight = s.sp(18)),
    label = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = s.sp(12), lineHeight = s.sp(16)),
    eyebrow = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = s.sp(12), lineHeight = s.sp(16), letterSpacing = 0.08.em),
    mono = TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = s.sp(13), lineHeight = s.sp(18)),
)

val LocalNebulaType = staticCompositionLocalOf { nebulaType(NebulaScale(1f)) }

object Nebula {
    val type: NebulaType @Composable get() = LocalNebulaType.current
    val scale: NebulaScale @Composable get() = LocalScale.current
    val form: FormFactor @Composable get() = LocalFormFactor.current
    val reducedMotion: Boolean @Composable get() = LocalReducedMotion.current
}

fun isTelevision(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature("android.software.leanback")
}

@Composable
fun NebulaTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val tv = remember { isTelevision(context) }
    val w = config.screenWidthDp
    val form = FormFactor(
        width = when {
            w >= 840 -> WidthClass.EXPANDED
            w >= 600 -> WidthClass.MEDIUM
            else -> WidthClass.COMPACT
        },
        isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
        isTv = tv,
        widthDp = w,
        heightDp = config.screenHeightDp,
    )
    val scale = NebulaScale(if (tv) 1.15f else 1f)
    val reduced = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE) == 0f }.getOrDefault(false)
    }
    val scheme = darkColorScheme(
        primary = NebulaColors.accent,
        onPrimary = Color.White,
        secondary = NebulaColors.accentText,
        background = NebulaColors.bg,
        onBackground = NebulaColors.text,
        surface = NebulaColors.surface,
        onSurface = NebulaColors.text,
        surfaceVariant = NebulaColors.raised,
        onSurfaceVariant = NebulaColors.textSecondary,
        outline = NebulaColors.controlBorder,
        outlineVariant = NebulaColors.border,
        error = NebulaColors.danger,
    )
    CompositionLocalProvider(
        LocalFormFactor provides form,
        LocalScale provides scale,
        LocalNebulaType provides nebulaType(scale),
        LocalReducedMotion provides reduced,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
