package io.github.f_e_n_y_x.nebula

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The dev8 rotation bug: a stream opened with the phone upright stayed portrait. On the demo host
 * (debug build), a landscape stream asks for sensor landscape, the Activity turns without being
 * recreated (the stream would reconnect otherwise), and the stream menu keeps it.
 */
@RunWith(AndroidJUnit4::class)
class StreamOrientationDeviceTest {
    private fun waitFor(what: String, check: () -> Boolean) {
        val until = System.currentTimeMillis() + 10_000
        while (!check()) {
            if (System.currentTimeMillis() > until) throw AssertionError("timed out waiting for $what")
            Thread.sleep(100)
        }
    }

    @Test fun landscapeStreamRotatesInPlace() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).putExtra("start", "mirror:gta5")
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var first: MainActivity? = null
            scenario.onActivity { first = it }
            waitFor("sensor landscape") {
                var o = 0
                scenario.onActivity { o = it.requestedOrientation }
                o == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            }
            waitFor("landscape configuration") {
                var o = 0
                scenario.onActivity { o = it.resources.configuration.orientation }
                o == Configuration.ORIENTATION_LANDSCAPE
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { assertSame("rotation must not recreate the stream Activity", first, it) }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } // Back opens the stream menu
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { a -> assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE, a.requestedOrientation) }
        }
    }
}
