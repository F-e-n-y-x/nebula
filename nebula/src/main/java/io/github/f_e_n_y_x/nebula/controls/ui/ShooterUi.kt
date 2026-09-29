package io.github.f_e_n_y_x.nebula.controls.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.f_e_n_y_x.nebula.controls.ControlElement
import io.github.f_e_n_y_x.nebula.controls.ControlsProfile
import io.github.f_e_n_y_x.nebula.controls.DefaultProfiles
import io.github.f_e_n_y_x.nebula.controls.ElementRole
import io.github.f_e_n_y_x.nebula.controls.StandardOptions
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.components.nebulaClickable
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors

/**
 * A layout style the editor can start from; [preset] null is a style that isn't ready yet. Genre
 * templates come first, then layouts made for one game, named like the layout library
 * ("<game or genre> · <what's different>").
 */
internal data class ControlStyle(val key: String, val title: String, val help: String, val preset: (() -> ControlsProfile)?)

internal fun controlStyles(standard: StandardOptions) = listOf(
    ControlStyle("standard", "Standard", "The gamepad: sticks, face buttons, bumpers and triggers.") { DefaultProfiles.standard(standard) },
    ControlStyle(
        "touch-shooter-pad", "Touch shooter · controller",
        "For any shooter played with a controller: fire on both sides (drag the right one to aim), sprint past the ring, run lock, aim with a tap or a hold, lean.",
    ) { DefaultProfiles.touchShooterPad() },
    ControlStyle(
        "touch-shooter-kbm", "Touch shooter · keyboard & mouse",
        "The same for games played with keyboard and mouse: WASD stick, mouse look, left and right mouse buttons, Space, C, Z, R, F, Q / E lean, Alt free look.",
    ) { DefaultProfiles.touchShooterKbm() },
    ControlStyle("gta-touch-controls", "GTA V · touch controls", "The touch-shooter layout on GTA V's own controller map, with mouse look.") { DefaultProfiles.gtaTouchControls() },
    ControlStyle("racing", "Racing (later)", "Steering, throttle and brake. Coming later.", null),
)

/**
 * "Style": replaces the layout being edited with a ready-made one, fitted to this device (dp
 * sizes scaled, everything kept on screen). Undo brings the old layout back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StylePickerDialog(standard: StandardOptions, onPick: (ControlsProfile) -> Unit, onTutorial: () -> Unit, onDismiss: () -> Unit) {
    val s = Nebula.scale
    val styles = remember(standard) { controlStyles(standard) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.systemBarsPadding().padding(s.dp(12)).widthIn(max = s.dp(900)).background(NebulaColors.raised, RoundedCornerShape(s.dp(18)))
                .verticalScroll(rememberScrollState()).padding(s.dp(18)).testTag("style-picker"),
            verticalArrangement = Arrangement.spacedBy(s.dp(12)),
        ) {
            PanelHeader("Style", "Start from a layout", onDismiss)
            Text("Replaces this layout (undo brings it back), sized for this screen. Save to keep it.", style = Nebula.type.label, color = NebulaColors.textMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalArrangement = Arrangement.spacedBy(s.dp(10))) {
                styles.forEach { st ->
                    val p = remember(st.key) { st.preset?.invoke() }
                    val shape = RoundedCornerShape(s.dp(14))
                    Column(
                        Modifier.width(s.dp(260))
                            .then(if (p != null) Modifier.nebulaClickable(shape, { onPick(p) }, role = Role.Button) else Modifier)
                            .background(NebulaColors.surface, shape).border(1.dp, NebulaColors.border, shape).padding(s.dp(10)).testTag("style:${st.key}"),
                        verticalArrangement = Arrangement.spacedBy(s.dp(6)),
                    ) {
                        if (p != null) LayoutThumbnail(p.landscape.map { it.thumb() }, labels = true)
                        else Box(Modifier.width(s.dp(240)).aspectRatio(2.17f).background(Color(0x14FFFFFF), RoundedCornerShape(s.dp(10))))
                        Text(st.title, style = Nebula.type.bodyStrong, color = if (p != null) NebulaColors.text else NebulaColors.textMuted)
                        Text(st.help, style = Nebula.type.label, color = NebulaColors.textMuted)
                    }
                }
            }
            NebulaButton("How shooter controls work", onClick = onTutorial, style = ButtonStyle.Secondary)
        }
    }
}

// ---------------------------------------------------------------- tutorial

object ShooterTutorial {
    const val SEEN_KEY = "nebula_shooter_tutorial_seen"
    fun seen(p: LegacyPrefs) = p.prefs.getBoolean(SEEN_KEY, false)
    fun markSeen(p: LegacyPrefs) { p.prefs.edit().putBoolean(SEEN_KEY, true).apply() }
}

/**
 * The first time a shooter layout is used: a few numbered tips over a dim stream, the left
 * thumb's on the left and the right thumb's on the right, in the order you'd use them. Taps
 * don't reach the game while it shows.
 */
@Composable
fun ShooterTutorialOverlay(layout: List<ControlElement>, onDone: () -> Unit) {
    val s = Nebula.scale
    fun has(role: ElementRole, pred: (ControlElement) -> Boolean = { true }) = layout.any { it.role == role && pred(it) }
    val move = layout.firstOrNull { it.role == ElementRole.MOVE }
    val left = listOfNotNull(
        move?.let { m ->
            buildString {
                append("Move with your left thumb.")
                if (m.sprint != io.github.f_e_n_y_x.nebula.controls.Binding.None) append(" Push past the ring to sprint.")
                if (m.runLock) append(" Drag up to the lock and let go to keep running; touch the stick to stop.")
            }
        },
        "Left fire: shoot while your right thumb aims.".takeIf { has(ElementRole.FIRE) { !it.lookThrough } },
    )
    val right = listOfNotNull(
        "Swipe anywhere free on the right to look.",
        "Right fire shoots at once: drag it to aim while you shoot.".takeIf { has(ElementRole.FIRE) { it.lookThrough } },
        "Aim: tap to stay aimed, or hold.".takeIf { has(ElementRole.ADS) },
        "Eye: hold and drag to look around.".takeIf { has(ElementRole.FREE_LOOK) },
        "Claw grip: index fingers reach lean (peek), map and the top buttons.".takeIf { has(ElementRole.PEEK_LEFT) },
    )
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).nebulaClickable(RoundedCornerShape(0.dp), {}).testTag("shooter-tutorial")) {
        Column(Modifier.align(Alignment.Center).systemBarsPadding().padding(s.dp(16)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s.dp(14))) {
            Text("Shooter controls", style = Nebula.type.heading, color = NebulaColors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(s.dp(24))) {
                TipColumn("Left thumb", left, 1, Modifier.weight(1f))
                TipColumn("Right thumb", right, left.size + 1, Modifier.weight(1f))
            }
            NebulaButton("Got it", onClick = onDone, modifier = Modifier.testTag("tutorial-done"))
        }
    }
}

@Composable
private fun TipColumn(title: String, tips: List<String>, first: Int, modifier: Modifier) {
    val s = Nebula.scale
    Column(modifier.widthIn(max = s.dp(380)), verticalArrangement = Arrangement.spacedBy(s.dp(8))) {
        Text(title.uppercase(), style = Nebula.type.eyebrow, color = NebulaColors.accentText)
        tips.forEachIndexed { i, text ->
            Row(
                Modifier.background(NebulaColors.raised, RoundedCornerShape(s.dp(12))).border(1.dp, NebulaColors.border, RoundedCornerShape(s.dp(12))).padding(s.dp(10)),
                horizontalArrangement = Arrangement.spacedBy(s.dp(10)), verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.background(NebulaColors.accent, CircleShape).padding(horizontal = s.dp(8), vertical = s.dp(2))) {
                    Text("${first + i}", style = Nebula.type.label, color = Color.White)
                }
                Text(text, style = Nebula.type.label, color = NebulaColors.text)
            }
        }
    }
}
