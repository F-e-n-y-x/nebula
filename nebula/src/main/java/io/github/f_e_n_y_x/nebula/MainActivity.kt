package io.github.f_e_n_y_x.nebula

import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.f_e_n_y_x.nebula.ui.NebulaApp
import io.github.f_e_n_y_x.nebula.ui.theme.NebulaTheme

/** Receives raw key and motion events while a stream has input focus (controls hidden). */
interface StreamInputSink {
    fun onKey(event: KeyEvent): Boolean
    fun onMotion(event: MotionEvent): Boolean
}

class MainActivity : ComponentActivity() {
    /** Set by the stream screen; null everywhere else so D-pad focus drives the UI. */
    var streamInput: StreamInputSink? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val container = container
        val start = intent?.getStringExtra("start")
        io.github.f_e_n_y_x.nebula.controls.ui.LayoutInbox.offer(intent)
        setContent {
            NebulaTheme { NebulaApp(container, start) }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // singleTask: a layout link while Nebula runs arrives here; same checks as onCreate.
        setIntent(intent)
        io.github.f_e_n_y_x.nebula.controls.ui.LayoutInbox.offer(intent)
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
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        streamInput?.onMotion(event) == true || super.dispatchGenericMotionEvent(event)
}
