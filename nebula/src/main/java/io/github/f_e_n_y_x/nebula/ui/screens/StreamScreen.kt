package io.github.f_e_n_y_x.nebula.ui.screens

import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.f_e_n_y_x.nebula.MainActivity
import io.github.f_e_n_y_x.nebula.StreamInputSink
import io.github.f_e_n_y_x.nebula.data.engine.EngineStreamRepository
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper
import io.github.f_e_n_y_x.nebula.data.engine.SurfaceStreamTarget
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import io.github.fenyx.nebula.engine.MouseButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.StreamViewModel
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay

/**
 * Hosts the engine's video surface full-screen with a minimal overlay (stats, Disconnect, Quit).
 * While the overlay is hidden, keys, gamepads and touch go to the PC; Back (or Start + Select)
 * brings the overlay back. The full in-stream menu (bitrate, display switch, keyboard, frame
 * generation) moves here in a later part.
 */
@Composable
fun StreamScreen(container: AppContainer, nav: Navigator, hostId: String, gameId: String, mode: DisplayMode) {
    val vm = viewModel { StreamViewModel(container, hostId, gameId, mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val t = Nebula.type
    val activity = LocalActivity.current as MainActivity
    val engine = container.stream as? EngineStreamRepository
    var overlay by remember { mutableStateOf(true) }
    var poke by remember { mutableIntStateOf(0) }
    val live = state is StreamState.Live
    LaunchedEffect(poke, live) { if (live) { delay(4_000); overlay = false } }
    val showOverlay = { overlay = true; poke++ }
    val end = { quit: Boolean -> vm.end(quit); nav.back() }
    BackHandler { if (!overlay) showOverlay() else end(false) }

    // Full screen while streaming.
    DisposableEffect(Unit) {
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Raw input goes to the PC only while the controls are hidden, so D-pad focus works on the overlay.
    if (engine != null) {
        val sink = remember(engine) { EngineInputSink(engine) { showOverlay() } }
        DisposableEffect(overlay, live) {
            activity.streamInput = if (!overlay && live) sink else null
            onDispose { activity.streamInput = null }
        }
    }
    if (container.isDemo) LaunchedEffect(Unit) { vm.attach(StreamTarget.None) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (engine == null) {
            // Demo build: show a real screenshot where the decoded video would be.
            val shot = game?.art?.hero ?: game?.art?.header
            ArtImage(shot, game?.name ?: "stream", null, Modifier.fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (overlay) overlay = false else showOverlay() })
        } else {
            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = Unit
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                                vm.attach(SurfaceStreamTarget(activity, holder))
                            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit // the session disconnects itself
                        })
                    }
                },
                modifier = Modifier.fillMaxSize().trackpad(engine) { if (overlay) overlay = false },
            )
        }

        val backFocus = remember { FocusRequester() }
        when (val st = state) {
            StreamState.Starting -> Column(
                Modifier.fillMaxSize().background(Color(0xCC0A0A0B)),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = NebulaColors.accentText)
                Spacer(Modifier.height(s.dp(16)))
                Text("Starting ${game?.name ?: "stream"} on ${modeShort(mode).lowercase()}…", style = t.bodyStrong, color = NebulaColors.text)
            }
            is StreamState.Failed, is StreamState.Ended -> Column(
                Modifier.align(Alignment.Center).background(Color(0xE60A0A0B), RoundedCornerShape(s.dp(16))).padding(s.dp(28)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val reason = (st as? StreamState.Failed)?.reason ?: (st as StreamState.Ended).reason ?: "The stream ended."
                Text("Stream stopped", style = t.heading, color = NebulaColors.text)
                Spacer(Modifier.height(s.dp(8)))
                Text(reason, style = t.body, color = NebulaColors.textSecondary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(s.dp(18)))
                NebulaButton("Back", onClick = { nav.back() }, style = ButtonStyle.Secondary, modifier = Modifier.focusRequester(backFocus))
                LaunchedEffect(Unit) { runCatching { backFocus.requestFocus() } }
            }
            is StreamState.Live -> AnimatedVisibility(overlay, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().systemBarsPadding().padding(s.dp(20))) {
                    val x = st.stats
                    val portrait = Nebula.form.isCompact && !Nebula.form.isLandscape
                    Row(
                        Modifier.align(Alignment.TopStart).background(Color(0xCC0A0A0B), RoundedCornerShape(50)).padding(horizontal = s.dp(14), vertical = s.dp(8)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(s.dp(10)),
                    ) {
                        Box(Modifier.size(s.dp(8)).background(NebulaColors.success, RoundedCornerShape(50)))
                        Text("Live", style = t.label, color = NebulaColors.success)
                        val full = "${x.resolution} · ${x.fps} fps · ${"%.0f".format(x.bitrateMbps)} Mbps · ${"%.1f".format(x.latencyMs)} ms · ${x.codec}"
                        val short = "${x.fps} fps · ${"%.0f".format(x.bitrateMbps)} Mbps · ${"%.1f".format(x.latencyMs)} ms"
                        Text(if (portrait) short else full, style = t.mono, color = NebulaColors.text, maxLines = 1)
                    }
                    val endFocus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { endFocus.requestFocus() } }
                    Row(
                        Modifier.align(if (portrait) Alignment.BottomCenter else Alignment.TopEnd).padding(bottom = if (portrait) s.dp(40) else 0.dp)
                            .background(Color(0xD90A0A0B), RoundedCornerShape(s.dp(16))).padding(s.dp(6)),
                        horizontalArrangement = Arrangement.spacedBy(s.dp(6)),
                    ) {
                        NebulaButton(
                            "Quit game", onClick = { end(true) }, style = ButtonStyle.Secondary,
                            modifier = Modifier.onFocusChanged { if (it.isFocused) poke++ },
                        )
                        NebulaButton(
                            "Disconnect", onClick = { end(false) }, style = ButtonStyle.Danger, icon = Icons.Rounded.Close,
                            modifier = Modifier.focusRequester(endFocus).onFocusChanged { if (it.isFocused) poke++ },
                        )
                    }
                    Text(
                        if (container.isDemo) "Demo stream · Tap or press Back for controls" else "Press Back or Start + Select for controls",
                        style = t.label, color = NebulaColors.textSecondary,
                        modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xB30A0A0B), RoundedCornerShape(50)).padding(horizontal = s.dp(12), vertical = s.dp(6)),
                    )
                }
            }
        }
    }
}

/** Keyboards and TV remotes type on the PC; gamepads become player 1. Back stays with the app. */
private class EngineInputSink(private val engine: EngineStreamRepository, onMenu: () -> Unit) : StreamInputSink {
    private val pad = GamepadMapper({ engine.input }, onMenu)

    override fun onKey(event: KeyEvent): Boolean {
        if (pad.onKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        return engine.input?.sendKey(event) == true
    }

    override fun onMotion(event: MotionEvent): Boolean = pad.onMotion(event)
}

/** Touch acts as a laptop trackpad: drag moves the pointer, tap clicks, long-press right-clicks. */
private fun Modifier.trackpad(engine: EngineStreamRepository, onTouch: () -> Unit): Modifier = this
    .pointerInput(engine) {
        detectTapGestures(
            onTap = { onTouch(); engine.input?.run { mouseButton(MouseButton.LEFT, true); mouseButton(MouseButton.LEFT, false) } },
            onLongPress = { engine.input?.run { mouseButton(MouseButton.RIGHT, true); mouseButton(MouseButton.RIGHT, false) } },
        )
    }
    .pointerInput(engine) {
        detectDragGestures { change, drag ->
            change.consume()
            engine.input?.moveMouse((drag.x * TRACKPAD_SPEED).toInt(), (drag.y * TRACKPAD_SPEED).toInt())
        }
    }

private const val TRACKPAD_SPEED = 1.4f
