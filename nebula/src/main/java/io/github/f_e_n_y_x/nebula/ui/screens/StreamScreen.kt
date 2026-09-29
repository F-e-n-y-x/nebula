package io.github.f_e_n_y_x.nebula.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.f_e_n_y_x.nebula.AppContainer
import io.github.f_e_n_y_x.nebula.MainActivity
import io.github.f_e_n_y_x.nebula.domain.Orientation
import io.github.f_e_n_y_x.nebula.domain.PortraitStreaming
import io.github.f_e_n_y_x.nebula.StreamInputSink
import io.github.f_e_n_y_x.nebula.controls.ControlsStore
import io.github.f_e_n_y_x.nebula.controls.PadMixer
import io.github.f_e_n_y_x.nebula.controls.hasControllerZones
import io.github.f_e_n_y_x.nebula.controls.ui.ControlsEditor
import io.github.f_e_n_y_x.nebula.data.engine.GamepadMapper
import io.github.f_e_n_y_x.nebula.data.engine.GyroAssist
import io.github.f_e_n_y_x.nebula.controls.isShooter
import io.github.f_e_n_y_x.nebula.data.engine.GyroToggle
import io.github.f_e_n_y_x.nebula.data.engine.MotionRouting
import io.github.f_e_n_y_x.nebula.data.engine.SurfaceStreamTarget
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import io.github.f_e_n_y_x.nebula.domain.SwitchState
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.StreamStats
import io.github.f_e_n_y_x.nebula.input.GestureCallbacks
import io.github.f_e_n_y_x.nebula.input.MouseInput
import io.github.f_e_n_y_x.nebula.input.RemoteInput
import io.github.f_e_n_y_x.nebula.input.StreamInputView
import io.github.f_e_n_y_x.nebula.input.TrackingInput
import androidx.compose.runtime.mutableFloatStateOf
import io.github.f_e_n_y_x.nebula.input.TouchGestures
import io.github.f_e_n_y_x.nebula.input.TouchMode
import io.github.f_e_n_y_x.nebula.input.VideoRect
import io.github.f_e_n_y_x.nebula.input.isMouse
import io.github.f_e_n_y_x.nebula.input.press
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import io.github.f_e_n_y_x.nebula.settings.setOverlayOpacity
import io.github.f_e_n_y_x.nebula.ui.Navigator
import io.github.f_e_n_y_x.nebula.ui.StreamViewModel
import io.github.f_e_n_y_x.nebula.ui.components.ArtImage
import io.github.f_e_n_y_x.nebula.ui.components.ButtonStyle
import io.github.f_e_n_y_x.nebula.ui.components.NebulaButton
import io.github.f_e_n_y_x.nebula.ui.theme.Nebula
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

/** Mutable state the Android input objects read; updated from composition. */
private class InputState {
    var rect = VideoRect(0f, 0f, 1f, 1f)
    var viewWidth = 1f
    var ui: StreamUiPrefs? = null
    var active = false
    /** Set while the on-screen controls are shown: what touches outside them do (null = no controls). */
    var outside: io.github.f_e_n_y_x.nebula.controls.OutsideTouch? = null
    var zoom: (Float, Float, Float) -> Unit = { _, _, _ -> }
    var pan: (Float, Float) -> Unit = { _, _ -> }
}

/**
 * The stream: video scaled per Settings (Fit / Fill / Stretch; Virtual display shows native
 * pixels), a full-screen input layer (touch gestures, mouse with pointer capture, soft keyboard),
 * optional on-screen controls, stats overlay and float ball, and the Panel quick menu.
 *
 * Back, B, Start + Select, a four-finger tap or double Esc open the menu; leaving the stream only
 * happens from the menu. The session survives the app going to the background for the grace
 * period set in Settings.
 */
@Composable
fun StreamScreen(container: AppContainer, nav: Navigator, hostId: String, gameId: String, mode: DisplayMode) {
    val vm = viewModel { StreamViewModel(container, hostId, gameId, mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val game by vm.game.collectAsStateWithLifecycle()
    val bitrate by vm.bitrateKbps.collectAsStateWithLifecycle()
    val bitrateNote by vm.bitrateNote.collectAsStateWithLifecycle()
    val switchState by vm.switchState.collectAsStateWithLifecycle()
    val switchNote by vm.switchNote.collectAsStateWithLifecycle()
    val gameVideoMode by vm.gameVideoMode.collectAsStateWithLifecycle()
    val s = Nebula.scale
    val t = Nebula.type
    val ctx = LocalContext.current
    val activity = LocalActivity.current as MainActivity
    val prefs = remember { LegacyPrefs(ctx) }
    val tick by prefs.changes().collectAsState(initial = null)
    val gameKey = "$hostId:$gameId"
    val ui = remember(tick) { StreamUiPrefs.read(prefs, gameKey) }
    val baseInput = container.stream.remoteInput
    // Everything goes through the tracker so the optional local cursor knows where the pointer is.
    val remoteInput = remember(baseInput) { baseInput?.let { TrackingInput(it) } }
    val remote = remember(remoteInput) { { remoteInput } }
    val view = androidx.compose.ui.platform.LocalView.current

    var menu by remember { mutableStateOf(false) }
    val live = state is StreamState.Live
    val stats = (state as? StreamState.Live)?.stats
    // A minute of stats for the graph layout.
    val history = remember { androidx.compose.runtime.mutableStateListOf<StreamStats>() }
    LaunchedEffect(stats) {
        stats?.let { history.add(it); if (history.size > STATS_HISTORY) history.removeAt(0) }
    }
    val ended = state is StreamState.Failed || state is StreamState.Ended
    var hint by remember { mutableStateOf(true) }
    LaunchedEffect(live) { if (live) { delay(6_000); hint = false } }

    // "Show latency message after streaming": the session's average, like V+.
    val latencyToast: () -> Unit = {
        if (ui.latencyToast && history.isNotEmpty()) {
            val avg = history.map { it.latencyMs }.average()
            val fps = history.map { it.fps }.average()
            Toast.makeText(ctx, "Average latency %.1f ms at %.0f fps".format(avg, fps), Toast.LENGTH_LONG).show()
        }
    }
    val end = { quit: Boolean -> latencyToast(); vm.end(quit); nav.back() }
    // Back never leaves a running stream: it toggles the menu. Leaving is Disconnect / Quit.
    BackHandler { if (ended) nav.back() else menu = !menu }

    // Full screen while streaming.
    DisposableEffect(Unit) {
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // V+'s stream orientation: sensor landscape for a landscape stream (portrait for a portrait
    // one), any orientation with "Follow device rotation". Rotating re-lays out; it never reconnects.
    val orientationMode by vm.orientationMode.collectAsStateWithLifecycle()
    val streamSettings by vm.settings.collectAsStateWithLifecycle()
    val portraitFollow = streamSettings.portraitStreaming == PortraitStreaming.FOLLOW_ROTATION
    val squarish = remember { deviceResolution(ctx).let { (w, h) -> isSquarishScreen(w, h) } }
    val orientation = streamOrientation(
        ui.followRotation, orientationMode?.width ?: 1, orientationMode?.height ?: 0, squarish, ui.osc, portraitFollow,
    )
    // Portrait streaming "Follow rotation": the screen turned (FULL_USER; Android itself ignores a
    // phone laid flat and honours the rotation lock), so the stream follows after a short debounce.
    val screenConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val screenOrientation = when (screenConfig.orientation) {
        android.content.res.Configuration.ORIENTATION_PORTRAIT -> Orientation.PORTRAIT
        android.content.res.Configuration.ORIENTATION_LANDSCAPE -> Orientation.LANDSCAPE
        else -> null
    }
    LaunchedEffect(screenOrientation, portraitFollow) { vm.onScreenOrientation(screenOrientation) }
    DisposableEffect(Unit) {
        val before = activity.requestedOrientation
        onDispose { activity.requestedOrientation = before }
    }
    LaunchedEffect(orientation) { activity.requestedOrientation = orientation }
    // "Maximum display brightness for HDR" while the host sends HDR.
    val hdrNow = stats?.hdr == true
    DisposableEffect(hdrNow, ui.hdrMaxBrightness) {
        val w = activity.window
        val before = w.attributes.screenBrightness
        if (hdrNow && ui.hdrMaxBrightness) w.attributes = w.attributes.apply { screenBrightness = 1f }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = before } }
    }

    // The ongoing "Streaming…" notification needs permission on Android 13+; ask once.
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(live) {
        if (live && Build.VERSION.SDK_INT >= 33 && !container.isDemo &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !prefs.prefs.getBoolean(ASKED_NOTIFY_KEY, false)
        ) {
            prefs.put(ASKED_NOTIFY_KEY, true)
            notify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Mic, clipboard sync, host commands and Sleep PC (phase 1 host-linked features).
    val hostLink = rememberStreamHostState(container, hostId, gameId, live)

    // On-screen controls: the game's profile, else the default, else the Standard pad (which follows Settings).
    val controlsStore = remember { ControlsStore.get(ctx) }
    val controlsData by controlsStore.data.collectAsState()
    // With a layout set, the layout the stream is on (switch elements change it; it lasts the session).
    var activeLayoutId by remember(gameKey) { mutableStateOf<String?>(null) }
    val controlsLib = remember(controlsData, tick) { controlsStore.library(controlsData) }
    val controlsActive = remember(controlsLib, activeLayoutId) { io.github.f_e_n_y_x.nebula.controls.ActiveLayout.of(controlsLib, gameKey, activeLayoutId) }
    val controlsProfile = controlsActive.profile
    var layoutPicker by remember { mutableStateOf(false) }
    val switchContext = remember(controlsActive, controlsLib) {
        io.github.f_e_n_y_x.nebula.controls.ui.SwitchContext(
            current = controlsProfile.name,
            names = controlsActive.layouts(controlsLib).associate { it.id to it.name },
            inSet = controlsActive.set != null,
        )
    }
    // Edit mode over the live picture: nothing reaches the PC until it closes.
    var editingControls by remember { mutableStateOf(false) }
    LaunchedEffect(ended) { if (ended) editingControls = false }
    val overlaysOn = live && !menu && !editingControls

    val input = remember { InputState() }
    input.ui = ui
    input.active = overlaysOn
    val uiNow by rememberUpdatedState(ui)
    val stream = container.stream
    var inputView by remember { mutableStateOf<StreamInputView?>(null) }
    val routing = remember { RouterSlot() }
    var devices by remember { mutableIntStateOf(0) }
    // With a physical controller attached the on-screen controls stay away unless the user keeps
    // them; kept, they drive player 1 together with the controller (never a second player).
    val padPresent = remember(devices) { GamepadMapper.physicalControllerPresent() }
    val oscShown = overlaysOn && ui.osc && (!padPresent || ui.oscWithGamepad)
    // Touch zones marked "Keep with controller" stay while the pad hides the rest; their stick
    // output is mixed into the physical pad's player (controller moves, touch aims).
    val zonesShown = overlaysOn && ui.osc && !oscShown && controlsProfile.hasControllerZones()
    val padMixer = remember(remoteInput) { PadMixer { remoteInput } }
    // Gyro to right stick / mouse (created below); the mapper asks it about the toggle button.
    var gyroAssist by remember { mutableStateOf<GyroAssist?>(null) }
    val gyroAssistNow by rememberUpdatedState(gyroAssist)
    // Everything the mapper sends (physical pads, the on-screen pad, gyro to right stick) passes
    // through the mixer, so touch zones steer the same player the controller and gyro drive.
    val pad = remember(remote) {
        GamepadMapper(
            { remoteInput?.let { padMixer } }, onMenu = { menu = true }, config = { uiNow.gamepad },
            capabilities = { id, index -> stream.padCapabilities(id, index) ?: GamepadMapper.DEFAULT_CAPS },
            onPadsChanged = { stream.refreshFeedback() },
            keyHook = { key, down -> gyroAssistNow?.onKey(key, down) == true },
            log = { android.util.Log.i("NebulaPads", it) },
        )
    }
    // Rumble, light bar and motion reach the physical pad through the mapper.
    DisposableEffect(pad) {
        stream.bindControllers(pad)
        onDispose { stream.bindControllers(null) }
    }
    LaunchedEffect(ui.motion, live) { if (live) stream.refreshFeedback() }
    var hapticsNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(ui.haptics, live) {
        if (live) hapticsNote = if (stream.applyAudioHaptics(ui.haptics)) null else "Music on this device uses Android's audio-coupled haptics; that change applies from the next stream."
    }
    val host by remember(hostId) { container.hosts.observeHosts().map { list -> list.firstOrNull { it.id == hostId } } }.collectAsState(initial = null)
    val unsupported by stream.unsupportedFeatures.collectAsState(initial = emptyList())
    // Desktop scaling (Foundation `/display-scale`): only when the host advertises it, which Nova
    // does for Virtual display sessions. The demo host shows it for QA.
    val scaleSupported = mode == DisplayMode.VIRTUAL && (host?.advertises("display_scale") == true || container.isDemo)
    val displayScale by vm.displayScale.collectAsStateWithLifecycle()
    val scaleKey = "$DISPLAY_SCALE_KEY:$gameKey"
    val savedScale = remember(tick) { prefs.prefs.getInt(scaleKey, 0).takeIf { it > 0 } }
    LaunchedEffect(live, scaleSupported) { if (live && scaleSupported) savedScale?.let(vm::applySavedDisplayScale) }
    val phoneHasGyro = remember {
        ctx.getSystemService(android.hardware.SensorManager::class.java)?.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE) != null
    }
    DisposableEffect(pad) {
        GyroToggle.on = false
        @Suppress("DEPRECATION")
        val g = GyroAssist(ctx, { pad }, { uiNow.motion }, rotation = { activity.windowManager.defaultDisplay.rotation }) { dx, dy ->
            remote()?.move(dx, dy)
        }
        gyroAssist = g
        onDispose { g.release(); gyroAssist = null }
    }
    // The mapping modes run while the stream has input; passthrough from this device with no
    // controller announces player 1 so the PC builds a motion-capable pad before a game asks.
    LaunchedEffect(gyroAssist, ui.motion, live, overlaysOn, devices) {
        gyroAssist?.update(overlaysOn)  // paused with the menu and while editing controls
        if (live && MotionRouting.announcePhonePad(ui.motion, padPresent, phoneHasGyro)) pad.announcePlayerOne()
    }

    // Keys, gamepads and D-pad go to the PC while the menu is closed.
    // On-screen PC keyboard (V+'s custom keyboard); the three-finger tap, float ball and mouse bar open it.
    var pcKeyboard by remember { mutableStateOf(false) }
    val pcKeyboardNow by rememberUpdatedState(pcKeyboard)
    val openKeyboard: () -> Unit = {
        if (uiNow.keyboardKind == KeyboardKind.PHONE) inputView?.toggleKeyboard() else pcKeyboard = !pcKeyboard
    }
    val sink = remember(remote) { StreamKeySink(remote, pad, { uiNow }, keyboardOpen = { pcKeyboardNow }, onMenu = { menu = true }) }
    DisposableEffect(menu, live, editingControls) {
        activity.streamInput = if (overlaysOn) sink else null
        if (menu || editingControls) { pad.releaseAll(); inputView?.hideKeyboard() }
        onDispose { activity.streamInput = null }
    }
    // Gamepads coming and going; a mouse appearing turns pointer capture on.
    DisposableEffect(Unit) {
        val im = ctx.getSystemService(InputManager::class.java)
        val l = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(id: Int) { devices++ }
            override fun onInputDeviceRemoved(id: Int) { pad.onDeviceRemoved(id); devices++ }
            override fun onInputDeviceChanged(id: Int) { pad.onDeviceChanged(id); devices++ }
        }
        im?.registerInputDeviceListener(l, null)
        onDispose { im?.unregisterInputDeviceListener(l) }
    }
    val hasMouse = remember(devices) { physicalMousePresent() }
    val capture = overlaysOn && !ui.absoluteMouse && ui.mouseCapture && hasMouse
    // Pinch zoom of the local picture (V+'s zoom/pan); nothing is sent to the PC.
    var zoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(capture, inputView) { inputView?.wantCapture = capture }
    // The input layer gives up focus while the menu or the PC keyboard needs the D-pad.
    LaunchedEffect(overlaysOn, pcKeyboard, inputView) { inputView?.inputEnabled = overlaysOn && !pcKeyboard }

    if (container.isDemo) LaunchedEffect(Unit) { vm.attach(StreamTarget.None) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val cw = with(density) { maxWidth.toPx() }
        val ch = with(density) { maxHeight.toPx() }
        val vw = stats?.width?.takeIf { it > 0 }?.toFloat() ?: cw
        val vh = stats?.height?.takeIf { it > 0 }?.toFloat() ?: ch
        // A rotation (portrait ↔ landscape video) starts unzoomed; the rect below then follows the new shape.
        LaunchedEffect(vh > vw) { zoom = 1f; panX = 0f; panY = 0f }
        // Virtual display already matches this screen: show it pixel for pixel unless told otherwise.
        val scale = if (mode == DisplayMode.VIRTUAL && !ui.scaleVirtual) ScaleMode.FIT else ui.scaleMode
        val base = videoRect(scale, cw, ch, vw, vh, ui.position, ui.offsetX, ui.offsetY)
        val rect = zoomed(base, zoom, panX, panY, cw, ch)
        input.rect = rect
        input.viewWidth = cw
        input.zoom = { factor, fx, fy ->
            val z = (zoom * factor).coerceIn(1f, MAX_ZOOM)
            val applied = z / zoom
            // Keep the point under the fingers in place.
            panX += (fx - (cw / 2 + panX)) * (1 - applied)
            panY += (fy - (ch / 2 + panY)) * (1 - applied)
            zoom = z
            if (z <= 1.001f) { panX = 0f; panY = 0f }
            clampPan(base, zoom, cw, ch, panX, panY).let { (x, y) -> panX = x; panY = y }
        }
        input.pan = { dx, dy ->
            clampPan(base, zoom, cw, ch, panX + dx, panY + dy).let { (x, y) -> panX = x; panY = y }
        }
        remoteInput?.let { it.frameWidth = vw.toInt(); it.frameHeight = vh.toInt() }
        val place = Modifier.layout { m, c ->
            val p = m.measure(Constraints.fixed(rect.width.roundToInt().coerceAtLeast(1), rect.height.roundToInt().coerceAtLeast(1)))
            layout(c.maxWidth, c.maxHeight) { p.place(rect.left.roundToInt(), rect.top.roundToInt()) }
        }

        if (container.engine == null) {
            // Demo build: a real screenshot where the decoded video would be, scaled the same way.
            val shot = game?.art?.hero ?: game?.art?.header
            ArtImage(shot, game?.name ?: "stream", null, place, contentScale = ContentScale.FillBounds)
        } else {
            // A new SurfaceView (and so a new Surface) whenever the next connection needs one: a
            // live resolution switch must not reuse a surface the old connection drew into.
            val surfaceGeneration by stream.surfaceGeneration.collectAsState(initial = 0)
            androidx.compose.runtime.key(surfaceGeneration) {
            AndroidView(
                factory = { c ->
                    SurfaceView(c).apply {
                        keepScreenOn = true
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) = Unit
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) =
                                vm.attach(SurfaceStreamTarget(activity, holder))
                            // The engine pauses video and keeps the session for the grace period.
                            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                        })
                    }
                },
                modifier = place,
            )
            }
        }

        // Input layer over the whole screen (letterbox bars included, like a trackpad).
        if (remoteInput != null) {
            AndroidView(
                factory = { c ->
                    val callbacks = object : GestureCallbacks {
                        override fun onKeyboard() = openKeyboard()
                        override fun onMenu() { menu = true }
                        override fun onZoom(factor: Float, focusX: Float, focusY: Float) = input.zoom(factor, focusX, focusY)
                        override fun onPan(dx: Float, dy: Float) = input.pan(dx, dy)
                        override fun isZoomed() = zoom > 1.001f
                        override fun onHold() { view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS) }
                        override fun onTouchUnsupported() {
                            Toast.makeText(c, "This PC doesn't support touch; using the direct pointer.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val gestures = TouchGestures(
                        // With the on-screen controls up, the picture only reaches the PC if the profile says so.
                        out = { remoteInput.takeIf { input.active && input.outside != io.github.f_e_n_y_x.nebula.controls.OutsideTouch.OFF && input.outside != io.github.f_e_n_y_x.nebula.controls.OutsideTouch.LOOK } },
                        config = {
                            val c = input.ui?.touch ?: io.github.f_e_n_y_x.nebula.input.TouchConfig()
                            if (input.outside == io.github.f_e_n_y_x.nebula.controls.OutsideTouch.TOUCH) c.copy(mode = TouchMode.TOUCH) else c
                        },
                        video = { input.rect },
                        viewWidth = { input.viewWidth },
                        callbacks = callbacks,
                    )
                    val mouse = MouseInput(
                        out = { remoteInput.takeIf { input.active } },
                        absolute = { input.ui?.absoluteMouse == true },
                        navButtons = { input.ui?.mouseNavButtons == true },
                        video = { input.rect },
                    )
                    StreamInputView(c, gestures, mouse) { remoteInput.takeIf { input.active } }.also {
                        // While the on-screen controls are up, every finger goes through their router.
                        it.router = { routing.router?.takeIf { input.active } }
                        inputView = it
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Local cursor: optional in Settings, always on in demo mode so gestures can be checked without a PC.
        val relative = ui.touch.mode == TouchMode.TRACKPAD || ui.touch.mode == TouchMode.SPLIT || ui.touch.mode == TouchMode.POINTER
        remoteInput?.takeIf { (container.isDemo || ui.localCursor) && relative }?.let { tracker ->
            val cur by tracker.cursor.collectAsState()
            if (live) {
                Box(
                    Modifier.offset { IntOffset((rect.left + cur.first * rect.width).roundToInt() - 6, (rect.top + cur.second * rect.height).roundToInt() - 6) }
                        .size(12.dp).background(Color.White, CircleShape).border(2.dp, Color.Black, CircleShape),
                )
            }
        }

        val outsideMode = remember(controlsProfile.id, tick) { io.github.f_e_n_y_x.nebula.controls.OutsideTouch.read(prefs, controlsProfile) }
        val lookMode = remember(controlsProfile.id, tick) { io.github.f_e_n_y_x.nebula.controls.LookOutput.read(prefs, controlsProfile) }
        input.outside = if (oscShown || zonesShown) outsideMode else null
        if (oscShown || zonesShown) {
            OnScreenControls(
                remote, { pad }, controlsProfile, ui.oscOpacity, zonesOnly = !oscShown, mixer = padMixer.takeIf { !oscShown },
                routing = routing, outside = outsideMode, look = lookMode, background = { inputView?.background },
                switchContext = switchContext,
                onSwitch = { e ->
                    val set = controlsActive.set
                    if (set != null) {
                        if (e.switchTo == io.github.f_e_n_y_x.nebula.controls.SwitchTarget.Picker) layoutPicker = true
                        else set.target(controlsProfile.id, e.switchTo)?.let { activeLayoutId = it }
                    }
                },
            )
        }
        if (layoutPicker && controlsActive.set != null && live) {
            io.github.f_e_n_y_x.nebula.controls.ui.LayoutPickerDialog(
                layouts = controlsActive.layouts(controlsLib), current = controlsProfile.id, setName = controlsActive.set.name,
                onPick = { id -> layoutPicker = false; activeLayoutId = id },
                onDismiss = { layoutPicker = false },
            )
        }
        // First time a shooter layout is in play: a short tutorial (Style → "How shooter controls work" shows it again).
        var tutorialDone by remember { mutableStateOf(io.github.f_e_n_y_x.nebula.controls.ui.ShooterTutorial.seen(prefs)) }
        if (oscShown && live && !menu && !editingControls && !tutorialDone && controlsProfile.isShooter()) {
            io.github.f_e_n_y_x.nebula.controls.ui.ShooterTutorialOverlay(controlsProfile.landscape) {
                io.github.f_e_n_y_x.nebula.controls.ui.ShooterTutorial.markSeen(prefs)
                tutorialDone = true
            }
        }
        val statsAtTop = ui.stats.enabled && ui.stats.position.row == 0
        if (overlaysOn && ui.mouseBar) MouseBar(remote, opacity = ui.overlayOpacity, onKeyboard = openKeyboard, onHide = { prefs.put(StreamUiPrefs.MOUSE_BAR_KEY, false) }, atTop = oscShown, topInset = if (statsAtTop) 60 + (ui.stats.metrics.size.coerceAtMost(8) * if (ui.stats.layout == io.github.f_e_n_y_x.nebula.settings.StatLayout.CARD) 18 else 0) else 12)
        if (overlaysOn && ui.stats.enabled && stats != null) {
            StatsOverlay(stats, history, ui.stats.copy(opacity = ui.overlayOpacity), draggable = !Nebula.form.isTv) { p ->
                io.github.f_e_n_y_x.nebula.settings.StatsOverlaySettings.setPosition(prefs, p)
            }
        }
        StreamMicIndicator(hostLink, visible = overlaysOn && !pcKeyboard, prefs = prefs)
        if (overlaysOn && pcKeyboard) PcKeyboardOverlay(remote, onClose = { pcKeyboard = false })
        if (overlaysOn && ui.floatBall && !pcKeyboard) {
            FloatBall(
                ui,
                onAction = { action ->
                    when (action) {
                        "open_keyboard" -> openKeyboard()
                        "open_menu" -> menu = true
                        "toggle_visibility" -> prefs.put("checkbox_enable_float_ball", false)
                    }
                },
            )
        }

        if (editingControls && live) {
            ControlsEditor(
                store = controlsStore, gameKey = gameKey, gameName = game?.name, startProfileId = controlsProfile.id,
                onClose = { editingControls = false },
            ) { /* the live stream shows through */ }
        }

        val backFocus = remember { FocusRequester() }
        // Fallback for hosts without clipboard sync: type the clipboard's text as keystrokes.
        val typeClipboard: () -> Unit = {
            val text = ctx.getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.coerceToText(ctx)?.toString()
            if (text.isNullOrEmpty()) {
                Toast.makeText(ctx, "The clipboard is empty", Toast.LENGTH_SHORT).show()
            } else {
                remoteInput?.text(text)
                Toast.makeText(ctx, "Typed ${text.length} characters on your PC", Toast.LENGTH_SHORT).show()
            }
        }
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
            is StreamState.Live -> AnimatedVisibility(hint && !menu && !editingControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(
                    if (Nebula.form.isTv) "Press Back or Start + Select for the menu" else "Back, four-finger tap or Start + Select for the menu · three fingers for the keyboard",
                    style = t.label, color = NebulaColors.textSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.systemBarsPadding().padding(bottom = s.dp(20), start = s.dp(20), end = s.dp(20))
                        .background(Color(0xB30A0A0B), RoundedCornerShape(50)).padding(horizontal = s.dp(14), vertical = s.dp(7)),
                )
            }
        }

        // Live resolution change: the last frame stays up behind a small "Switching to…" pill.
        val rotatingTo by vm.rotatingTo.collectAsStateWithLifecycle()
        SwitchingOverlay(switchState, switchNote, onNoteShown = vm::clearSwitchNote, modifier = Modifier.align(Alignment.TopCenter), rotatingTo = rotatingTo)

        AnimatedVisibility(menu && !ended, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            StreamMenu(
                gameName = game?.name ?: "Stream",
                mode = mode,
                stats = stats,
                ui = ui,
                prefs = prefs,
                bitrateKbps = bitrate,
                bitrateNote = bitrateNote,
                gamepads = pad.count,
                gameKey = gameKey,
                zoomed = zoom > 1.001f,
                resolution = LiveResolutionUi(
                    current = (switchState as? SwitchState.Streaming)?.mode?.takeIf { live },
                    state = switchState,
                    saved = gameVideoMode,
                    onApply = vm::changeResolution,
                    onRotate = vm::rotate,
                    displayScale = if (scaleSupported) DisplayScaleUi(displayScale, savedScale, onApply = { p, keep ->
                        if (p != displayScale) vm.setDisplayScale(p)
                        prefs.put(scaleKey, if (keep) p else null)
                    }) else null,
                ),
                supports = { f -> host?.supports(f) ?: true },
                unsupported = unsupported,
                hapticsNote = hapticsNote,
                phoneHasGyro = phoneHasGyro,
                controlsProfileId = controlsProfile.id.takeIf { ui.osc },
                controlsProfile = controlsProfile.takeIf { ui.osc },
                hostState = hostLink,
                // A TV doesn't turn; everything else can follow the device rotation.
                portraitFollow = portraitFollow.takeUnless { Nebula.form.isTv },
                actions = StreamMenuActions(
                    onResume = { menu = false },
                    onResetZoom = { zoom = 1f; panX = 0f; panY = 0f },
                    onBitrate = { vm.setBitrate(it) },
                    onKeyboard = {
                        menu = false
                        inputView?.postDelayed({ inputView?.showKeyboard() }, 150)
                    },
                    onPcKeyboard = {
                        menu = false
                        pcKeyboard = true
                    },
                    onOverlayOpacity = { setOverlayOpacity(ctx, it) },
                    onEditControls = {
                        menu = false
                        prefs.put("checkbox_show_onscreen_controls", true)
                        editingControls = true
                    },
                    onClipboard = typeClipboard,
                    onShortcut = { remoteInput?.press(it) },
                    onDisconnect = { end(false) },
                    onQuit = { end(!ui.quitDisconnectsOnly) },
                    onFramegenPause = { paused, force -> container.stream.setFramegenPaused(paused, force) },
                    onPortraitFollow = { vm.setPortraitStreaming(if (it) PortraitStreaming.FOLLOW_ROTATION else PortraitStreaming.OFF) },
                ),
                hostSection = { StreamHostMenuSection(hostLink, prefs, onTypeClipboard = typeClipboard, onSlept = { end(false) }) },
            )
        }
    }
}

/** Per game: the desktop scaling (percent) chosen with "Use for this game from now on". */
private const val DISPLAY_SCALE_KEY = "nebula_display_scale"
private const val ASKED_NOTIFY_KEY = "nebula_asked_notification_permission"
private const val MAX_ZOOM = 4f

/** Keeps a zoomed picture covering the screen: pan no further than its overhang. */
private fun clampPan(base: VideoRect, zoom: Float, cw: Float, ch: Float, x: Float, y: Float): Pair<Float, Float> {
    val maxX = ((base.width * zoom - cw) / 2).coerceAtLeast(0f)
    val maxY = ((base.height * zoom - ch) / 2).coerceAtLeast(0f)
    return x.coerceIn(-maxX, maxX) to y.coerceIn(-maxY, maxY)
}

/** A real mouse (or laptop touchpad) is attached; the emulator's and touchscreen's virtual pointers don't count. */
private fun physicalMousePresent(): Boolean = InputDevice.getDeviceIds().any { id ->
    val d = InputDevice.getDevice(id) ?: return@any false
    !d.isVirtual && d.supportsSource(InputDevice.SOURCE_MOUSE) && !d.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)
}

/** Keyboards, remotes and gamepads while the menu is closed. Back stays with the app (menu). */
private class StreamKeySink(
    private val remote: () -> RemoteInput?,
    private val pad: GamepadMapper,
    private val ui: () -> StreamUiPrefs,
    private val keyboardOpen: () -> Boolean,
    private val onMenu: () -> Unit,
) : StreamInputSink {
    private var lastMenuKeyUp = 0L

    override fun onKey(event: KeyEvent): Boolean {
        // With the PC keyboard open, a TV remote's D-pad moves between its keys instead of reaching the PC.
        if (keyboardOpen() && event.keyCode in DPAD_KEYS && !event.isFromSource(InputDevice.SOURCE_GAMEPAD) &&
            event.device?.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
        ) return false
        if (pad.onKey(event)) return true
        val fromMouse = event.isFromSource(InputDevice.SOURCE_MOUSE)
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_FORWARD) {
            // Mouse side buttons reach the PC as X1/X2 when enabled; otherwise Back opens the menu.
            if (fromMouse && ui().mouseNavButtons) return true
            if (event.keyCode == KeyEvent.KEYCODE_BACK) return false
        }
        val u = ui()
        if (u.escMenu && event.keyCode == u.escMenuKey && event.action == KeyEvent.ACTION_UP) {
            val now = SystemClock.uptimeMillis()
            if (now - lastMenuKeyUp < DOUBLE_PRESS_MS) {
                lastMenuKeyUp = 0
                remote()?.key(event)
                onMenu()
                return true
            }
            lastMenuKeyUp = now
        }
        return remote()?.key(event) == true
    }

    override fun onMotion(event: MotionEvent): Boolean = !event.isMouse() && pad.onMotion(event)

    private companion object {
        const val DOUBLE_PRESS_MS = 400L
        val DPAD_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        )
    }
}

/** V+'s quick float ball: tap / double tap / long press run the actions chosen in Settings. */
@Composable
private fun FloatBall(ui: StreamUiPrefs, onAction: (String) -> Unit) {
    val base = ui.overlayOpacity / 100f
    val s = Nebula.scale
    var poke by remember { mutableIntStateOf(0) }
    var faded by remember { mutableStateOf(false) }
    LaunchedEffect(poke) { faded = false; delay(ui.floatBallHideMs.toLong().coerceAtLeast(500)); faded = true }
    val align = when (ui.floatBallPosition) {
        "top_left" -> Alignment.TopStart
        "top_center" -> Alignment.TopCenter
        "top_right" -> Alignment.TopEnd
        "center_left" -> Alignment.CenterStart
        "bottom_left" -> Alignment.BottomStart
        "bottom_center" -> Alignment.BottomCenter
        "bottom_right" -> Alignment.BottomEnd
        else -> Alignment.CenterEnd
    }
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(s.dp(10))) {
        Box(
            Modifier.align(align).size(s.dp(44)).alpha(if (faded) base * 0.4f else base)
                .background(Color(0xCC17171A), CircleShape).border(1.dp, NebulaColors.controlBorder, CircleShape)
                .pointerInput(ui.floatBallTap, ui.floatBallDoubleTap, ui.floatBallLongPress) {
                    detectTapGestures(
                        onTap = { poke++; onAction(ui.floatBallTap) },
                        onDoubleTap = { poke++; onAction(ui.floatBallDoubleTap) },
                        onLongPress = { poke++; onAction(ui.floatBallLongPress) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.MoreHoriz, "Stream menu", tint = NebulaColors.text, modifier = Modifier.size(s.dp(22)))
        }
    }
}
