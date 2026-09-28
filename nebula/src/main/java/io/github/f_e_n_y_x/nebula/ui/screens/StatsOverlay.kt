package io.github.f_e_n_y_x.nebula.ui.screens

import android.content.Context
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.settings.StatLayout
import io.github.f_e_n_y_x.nebula.settings.StatMetric
import io.github.f_e_n_y_x.nebula.settings.StatPosition
import io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings
import io.github.f_e_n_y_x.nebula.settings.format
import io.github.f_e_n_y_x.nebula.settings.inline
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How many one-second samples the graph layout keeps. */
const val STATS_HISTORY = 60

internal fun StatPosition.alignment(): Alignment = when (this) {
    StatPosition.TOP_LEFT -> Alignment.TopStart
    StatPosition.TOP -> Alignment.TopCenter
    StatPosition.TOP_RIGHT -> Alignment.TopEnd
    StatPosition.LEFT -> Alignment.CenterStart
    StatPosition.RIGHT -> Alignment.CenterEnd
    StatPosition.BOTTOM_LEFT -> Alignment.BottomStart
    StatPosition.BOTTOM -> Alignment.BottomCenter
    StatPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
}

/** Battery percent, polled every 30 s while [wanted]. */
@Composable
internal fun rememberBattery(wanted: Boolean): Int? {
    val ctx = LocalContext.current
    var level by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(wanted) {
        while (wanted) {
            level = ctx.getSystemService(Context.BATTERY_SERVICE)?.let { it as BatteryManager }
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            delay(30_000)
        }
    }
    return level
}

/**
 * The stream's stats overlay, placed full-screen so it can sit at any corner or edge. With
 * [draggable], a long press picks it up and it snaps to the nearest slot on release ([onMove]).
 */
@Composable
fun StatsOverlay(
    stats: StreamStats,
    history: List<StreamStats>,
    cfg: StatsOverlaySettings,
    draggable: Boolean,
    onMove: (StatPosition) -> Unit,
) {
    val battery = rememberBattery(StatMetric.BATTERY in cfg.metrics)
    val moveNow by rememberUpdatedState(onMove)
    BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding().padding(Nebula.scale.dp(10))) {
        val density = LocalDensity.current
        val bw = with(density) { maxWidth.toPx() }
        val bh = with(density) { maxHeight.toPx() }
        var drag by remember { mutableStateOf(Offset.Zero) }
        var dragging by remember { mutableStateOf(false) }
        var origin by remember { mutableStateOf(Offset.Zero) }
        var sizePx by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.align(cfg.position.alignment())
                .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
                .onGloballyPositioned { c ->
                    if (!dragging) origin = c.positionInParent()
                    sizePx = Offset(c.size.width.toFloat(), c.size.height.toFloat())
                }
                .then(
                    if (draggable) Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { dragging = true },
                            onDrag = { change, amount -> change.consume(); drag += amount },
                            onDragEnd = {
                                val cx = (origin.x + drag.x + sizePx.x / 2) / bw.coerceAtLeast(1f)
                                val cy = (origin.y + drag.y + sizePx.y / 2) / bh.coerceAtLeast(1f)
                                dragging = false
                                drag = Offset.Zero
                                moveNow(StatPosition.snap(cx, cy))
                            },
                            onDragCancel = { dragging = false; drag = Offset.Zero },
                        )
                    } else Modifier,
                )
                .semantics { contentDescription = "Stream stats" },
        ) {
            StatsPanel(stats, history, cfg, battery, highlighted = dragging)
        }
    }
}

/** The overlay's body without placement; the Settings preview uses it too. */
@Composable
fun StatsPanel(stats: StreamStats, history: List<StreamStats>, cfg: StatsOverlaySettings, battery: Int?, highlighted: Boolean = false) {
    val s = Nebula.scale
    val k = cfg.size.scale
    val shape = RoundedCornerShape(s.dp((10 * k).roundToInt()))
    val bg = NebulaColors.bg.copy(alpha = cfg.opacity.coerceIn(10, 100) / 100f)
    val mono = Nebula.type.mono.let { it.copy(fontSize = it.fontSize * k, lineHeight = it.lineHeight * k) }
    val label = Nebula.type.label.let { it.copy(fontSize = it.fontSize * k, lineHeight = it.lineHeight * k) }
    val metrics = cfg.metrics.ifEmpty { listOf(StatMetric.FPS) }
    Column(
        Modifier.background(bg, shape)
            .then(if (highlighted) Modifier.border(1.5.dp, NebulaColors.accentText, shape) else Modifier)
            .padding(horizontal = s.dp((10 * k).roundToInt()), vertical = s.dp((6 * k).roundToInt())),
    ) {
        when (cfg.layout) {
            StatLayout.LINE -> Text(metrics.joinToString("  ·  ") { it.inline(stats, battery) }, style = mono, color = NebulaColors.text, maxLines = 2)
            StatLayout.CARD -> CardRows(metrics, stats, battery, mono, label)
            StatLayout.GRAPH -> {
                Graph(history.ifEmpty { listOf(stats) }, k)
                Spacer(Modifier.height(s.dp(4)))
                val rest = metrics.filter { it != StatMetric.FPS && it != StatMetric.TOTAL_LATENCY }
                if (rest.isNotEmpty()) Text(rest.joinToString("  ·  ") { it.inline(stats, battery) }, style = label, color = NebulaColors.textSecondary, maxLines = 2, modifier = Modifier.widthIn(max = s.dp((260 * k).roundToInt())))
            }
        }
    }
}

@Composable
private fun CardRows(metrics: List<StatMetric>, stats: StreamStats, battery: Int?, mono: TextStyle, label: TextStyle) {
    val s = Nebula.scale
    Column(verticalArrangement = Arrangement.spacedBy(s.dp(1))) {
        metrics.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.short, style = label, color = NebulaColors.textMuted, modifier = Modifier.width(s.dp(62)))
                Text(m.format(stats, battery), style = mono, color = if (m == StatMetric.FPS) NebulaColors.text else NebulaColors.textSecondary)
            }
        }
    }
}

/** Frame rate (accent) and total latency (blue) over the last minute, with their current values. */
@Composable
private fun Graph(history: List<StreamStats>, k: Float) {
    val s = Nebula.scale
    val w = s.dp((220 * k).roundToInt())
    val h = s.dp((44 * k).roundToInt())
    val mono = Nebula.type.label.let { it.copy(fontSize = it.fontSize * k) }
    val last = history.last()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(s.dp(8))) {
        Canvas(Modifier.size(w, h)) {
            fun line(values: List<Float>, color: Color) {
                if (values.size < 2) return
                // Each series gets its own range, padded so a steady line sits mid-height
                // instead of hugging the top.
                val hi = values.max()
                val lo = values.min()
                val pad = maxOf((hi - lo) * 0.5f, hi * 0.08f, 0.5f)
                val top = hi + pad
                val bottom = (lo - pad).coerceAtLeast(0f)
                val step = size.width / (STATS_HISTORY - 1)
                val start = size.width - step * (values.size - 1)
                val p = Path()
                values.forEachIndexed { i, v ->
                    val x = start + i * step
                    val y = size.height - ((v - bottom) / (top - bottom).coerceAtLeast(0.001f)).coerceIn(0f, 1f) * size.height
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, color, style = Stroke(width = 1.6.dp.toPx() * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawLine(NebulaColors.border, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            line(history.map { it.fps.toFloat() }, NebulaColors.accentText)
            line(history.map { it.latencyMs }, NebulaColors.info)
        }
        Column {
            Text("${last.fps} fps", style = mono, color = NebulaColors.accentText)
            Text("%.1f ms".format(last.latencyMs), style = mono, color = NebulaColors.info)
        }
    }
}
