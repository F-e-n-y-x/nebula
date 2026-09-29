package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.f_e_n_y_x.nebula.ui.NebulaApp
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaTheme
import kotlinx.coroutines.channels.Channel

/** Receives raw key and motion events while a stream has input focus (controls hidden). */
interface StreamInputSink {
    fun onKey(event: KeyEvent): Boolean
    fun onMotion(event: MotionEvent): Boolean
}

class MainActivity : ComponentActivity() {
    /** Set by the stream screen; null everywhere else so D-pad focus drives the UI. */
    var streamInput: StreamInputSink? = null

    /**
     * Play links (nebula://play/…) from shortcuts, the widget, the tile, Watch Next or other apps.
     * Only the link itself is taken from the intent; the UI validates it against the paired hosts.
     */
    private val playLinks = Channel<String>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = container
        // Not again when the activity is recreated with the same intent.
        if (savedInstanceState == null) handleResume(intent)
        val start = intent?.getStringExtra("start")
        takeDemoTextField(intent)
        io.github.f_e_n_y_x.nebula.controls.ui.LayoutInbox.offer(intent)
        // A recreated activity already handled its launch link.
        if (savedInstanceState == null) takePlayLink(intent)
        setContent {
            NebulaTheme { NebulaApp(container, start, playLinks) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: a layout link while Nebula runs arrives here; same checks as onCreate.
        setIntent(intent)
        io.github.f_e_n_y_x.nebula.controls.ui.LayoutInbox.offer(intent)
        handleResume(intent)
        takePlayLink(intent)
        takeDemoTextField(intent)
    }

    /** QA on the demo build: `--es demo_text_field open|password|multiline|close`. Ignored elsewhere. */
    private fun takeDemoTextField(intent: Intent?) {
        intent?.getStringExtra("demo_text_field")?.let { container.demoTextField(it) }
    }

    /**
     * Resume from the "game running on your PC" notification. This activity is exported (it's the
     * launcher), so the intent is only honoured with the private token our PendingIntent carries.
     */
    private fun handleResume(intent: Intent?) {
        if (intent?.action != io.github.f_e_n_y_x.nebula.nowplaying.RunningGameNotifications.ACTION_RESUME) return
        val n = io.github.f_e_n_y_x.nebula.nowplaying.RunningGameNotifications
        val target = container.nowPlaying.resumeTarget(
            intent.getStringExtra(n.EXTRA_HOST), intent.getStringExtra(n.EXTRA_GAME),
            intent.getStringExtra(n.EXTRA_MODE), intent.getStringExtra(n.EXTRA_TOKEN),
        )
        if (target == null) {
            android.util.Log.w("Nebula", "Ignored a resume intent without a valid token")
            return
        }
        container.nowPlaying.requestResume(target)
    }

    private fun takePlayLink(intent: Intent?) {
        intent ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (!data.scheme.equals("nebula", ignoreCase = true) || data.host !in QUICK_LINK_HOSTS) return
        playLinks.trySend(data.toString())
    }

    override fun onStart() {
        super.onStart()
        container.hosts.startWatching()
    }

    override fun onStop() {
        container.hosts.stopWatching()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        streamInput?.let { if (it.onKey(event)) return true }
        if (streamInput == null) {
            // Gamepads drive the UI like a TV remote: A selects, B goes back.
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(
                    KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount,
                        event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
                )
                KeyEvent.KEYCODE_BUTTON_B -> {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
                    return true
                }
            }
        }
        if (super.dispatchKeyEvent(event)) return true
        // Outside a stream, a controller button no screen used stays in Nebula: unhandled, the
        // system may act on it (Android TV sends some to the home screen). Guide/Mode still works.
        return streamInput == null && KeyEvent.isGamepadButton(event.keyCode) && event.keyCode != KeyEvent.KEYCODE_BUTTON_MODE
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        streamInput?.onMotion(event) == true || super.dispatchGenericMotionEvent(event)
}

/** Link hosts [MainActivity] passes to the play-link handler; `nebula://layout` goes to LayoutInbox. */
private val QUICK_LINK_HOSTS = setOf("play", "resume")
