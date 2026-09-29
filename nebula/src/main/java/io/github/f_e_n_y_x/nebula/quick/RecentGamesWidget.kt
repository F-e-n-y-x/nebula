package io.github.f_e_n_y_x.nebula.quick

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.f_e_n_y_x.nebula.R
import io.github.f_e_n_y_x.nebula.container
import io.github.f_e_n_y_x.nebula.domain.RecentPlay
import kotlinx.coroutines.flow.first

/** Home-screen widget: the last three games streamed from this device, one tap to resume each. */
class RecentGamesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = runCatching { context.container.quick.items.first() }.getOrDefault(emptyList()).take(3)
        val posters = items.map { Posters.load(context, it.poster, 240, 360) }
        provideContent { WidgetContent(items, posters) }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val TALL = DpSize(250.dp, 200.dp)
    }
}

class RecentGamesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentGamesWidget()
}

private val Bg = Color(0xFF0A0A0B)
private val Surface = Color(0xFF17171A)
private val Text1 = Color(0xFFEDEDEF)
private val Text2 = Color(0xFFA1A1A8)

@Composable
private fun WidgetContent(items: List<RecentPlay>, posters: List<Bitmap?>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val count = when {
        size.width < 180.dp -> 1
        size.width < 250.dp -> 2
        else -> 3
    }
    Column(
        GlanceModifier.fillMaxSize().background(Bg).cornerRadius(20.dp).padding(12.dp)
            .clickable(actionStartActivity(QuickConnect.openAppIntent(context))),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_nebula_star_violet), contentDescription = null, modifier = GlanceModifier.size(16.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text("Nebula", style = TextStyle(color = ColorProvider(Text1), fontSize = 13.sp, fontWeight = FontWeight.Medium))
            Spacer(GlanceModifier.defaultWeight())
            items.firstOrNull()?.let {
                // Resume: whatever runs on that PC now (waking it first), else the last game.
                Row(
                    GlanceModifier.background(Surface).cornerRadius(12.dp).padding(horizontal = 10.dp, vertical = 4.dp)
                        .semantics { contentDescription = "Resume on ${it.hostName}" }
                        .clickable(actionStartActivity(QuickConnect.resumeIntent(context))),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(ImageProvider(R.drawable.ic_widget_play), contentDescription = null, modifier = GlanceModifier.size(14.dp))
                    Spacer(GlanceModifier.width(4.dp))
                    Text("Resume · ${it.hostName}", style = TextStyle(color = ColorProvider(Text1), fontSize = 12.sp), maxLines = 1)
                }
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        if (items.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight().background(Surface).cornerRadius(14.dp).padding(12.dp), contentAlignment = Alignment.CenterStart) {
                Text(
                    "Games you stream show up here. Tap to open Nebula.",
                    style = TextStyle(color = ColorProvider(Text2), fontSize = 13.sp),
                )
            }
        } else {
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items.take(count).forEachIndexed { i, r ->
                    if (i > 0) Spacer(GlanceModifier.width(8.dp))
                    GameTile(r, posters.getOrNull(i), GlanceModifier.defaultWeight().fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun GameTile(r: RecentPlay, poster: Bitmap?, modifier: GlanceModifier) {
    val context = LocalContext.current
    Box(
        modifier.background(Surface).cornerRadius(14.dp)
            .semantics { contentDescription = "Resume ${r.gameName} on ${r.hostName}" }
            .clickable(actionStartActivity(QuickConnect.launchIntent(context, r))),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (poster != null) {
            Image(ImageProvider(poster), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
        }
        Image(ImageProvider(R.drawable.widget_poster_scrim), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = GlanceModifier.fillMaxSize())
        Row(GlanceModifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                r.gameName, maxLines = 2, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Image(ImageProvider(R.drawable.ic_widget_play), contentDescription = null, modifier = GlanceModifier.size(24.dp))
        }
    }
}
