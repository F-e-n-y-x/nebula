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
        setContent {
            NebulaTheme { NebulaApp(container, start) }
        }
    }

    override fun onStart() {
        super.onStart()
        container.hosts.startWatching()
    }

    override fun onStop() {
        container.hosts.stopWatching()
        super.onStop()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        streamInput?.onKey(event) == true || super.dispatchKeyEvent(event)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        streamInput?.onMotion(event) == true || super.dispatchGenericMotionEvent(event)
}
