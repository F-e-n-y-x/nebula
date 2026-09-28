package io.github.f_e_n_y_x.nebula.input

import io.github.f_e_n_y_x.nebula.data.engine.MotionFeed
import io.github.f_e_n_y_x.nebula.data.engine.MotionRouting
import io.github.f_e_n_y_x.nebula.settings.GyroMode
import io.github.f_e_n_y_x.nebula.settings.GyroToggleButton
import io.github.f_e_n_y_x.nebula.settings.MotionHold
import io.github.f_e_n_y_x.nebula.settings.MotionSettings
import io.github.f_e_n_y_x.nebula.settings.MotionSource
import io.github.f_e_n_y_x.nebula.settings.RumbleRoute
import io.github.f_e_n_y_x.nebula.settings.RumbleSettings
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test
    fun `motor values map to Android amplitudes`() {
        assertEquals(0, Feedback.amplitude(0))
        assertEquals(255, Feedback.amplitude(0xFFFF))
        assertEquals(0x80, Feedback.amplitude(0x8000))
        assertEquals(255, Feedback.amplitude(200_000))
    }

    @Test
    fun `one vibrator folds both motors like V+`() {
        assertEquals(0, Feedback.singleMotor(0, 0))
        assertEquals(204, Feedback.singleMotor(0xFFFF, 0)) // 255 * 0.8
        assertEquals(84, Feedback.singleMotor(0, 0xFFFF)) // 255 * 0.33
        assertEquals(255, Feedback.singleMotor(0xFFFF, 0xFFFF))
        assertEquals(102, Feedback.singleMotor(0xFFFF, 0, strengthPercent = 50))
        assertEquals(0, Feedback.singleMotor(0xFFFF, 0xFFFF, strengthPercent = 0))
    }

    @Test
    fun `rumble goes where the setting says`() {
        // Controller mode: the pad, never the phone while a pad is bound.
        assertEquals(Feedback.Targets(controller = true, device = false), Feedback.targets(RumbleRoute.CONTROLLER, hasPad = true, padHasMotors = true))
        assertEquals(Feedback.Targets(controller = false, device = false), Feedback.targets(RumbleRoute.CONTROLLER, hasPad = true, padHasMotors = false))
        // No physical pad (touch controls): this device.
        assertEquals(Feedback.Targets(controller = false, device = true), Feedback.targets(RumbleRoute.CONTROLLER, hasPad = false, padHasMotors = false))
        // Coordinated: the phone stands in for a pad without motors.
        assertEquals(Feedback.Targets(controller = false, device = true), Feedback.targets(RumbleRoute.COORDINATED, hasPad = true, padHasMotors = false))
        assertEquals(Feedback.Targets(controller = true, device = false), Feedback.targets(RumbleRoute.COORDINATED, hasPad = true, padHasMotors = true))
        assertEquals(Feedback.Targets(controller = false, device = true), Feedback.targets(RumbleRoute.DEVICE, hasPad = true, padHasMotors = true))
    }

    @Test
    fun `game rumble ducks audio haptics`() {
        assertEquals(1000 to 2000, Feedback.mix(1000, 2000, 1f, 1f))
        assertEquals(0xFFFF to 0, Feedback.mix(0, 0, 1f, 0f))
        assertEquals(0 to 0, Feedback.mix(0, 0, 0f, 0f))
    }

    @Test
    fun `pad motors in Android order`() {
        assertArrayEquals(intArrayOf(0x20, 0x10), Feedback.padAmplitudes(2, low = 0x1000, high = 0x2000, leftTrigger = 0, rightTrigger = 0))
        assertArrayEquals(intArrayOf(0x20, 0x10, 0x30, 0x40), Feedback.padAmplitudes(4, 0x1000, 0x2000, 0x3000, 0x4000))
        assertEquals(1, Feedback.padAmplitudes(1, 0xFFFF, 0, 0, 0).size)
        assertEquals(0, Feedback.padAmplitudes(0, 0xFFFF, 0, 0, 0).size)
    }

    @Test
    fun `light bar colour`() {
        assertEquals(0xFFFF8000.toInt(), Feedback.argb(255, 128, 0))
    }

    @Test
    fun `phone axes follow the screen rotation`() {
        assertEquals(Triple(1f, 3f, -2f), Feedback.phoneToController(1f, 2f, 3f, 0))
        assertEquals(Triple(-2f, 3f, -1f), Feedback.phoneToController(1f, 2f, 3f, 1))
        assertEquals(Triple(-1f, 3f, 2f), Feedback.phoneToController(1f, 2f, 3f, 2))
        assertEquals(Triple(2f, 3f, 1f), Feedback.phoneToController(1f, 2f, 3f, 3))
    }

    @Test
    fun `rumble settings read V+ keys`() {
        assertEquals(RumbleSettings(RumbleRoute.CONTROLLER, 100), RumbleSettings.read(emptyMap<String, Any>()))
        assertEquals(RumbleSettings(RumbleRoute.COORDINATED, 150), RumbleSettings.read(mapOf("list_game_rumble_mode" to "smart", "seekbar_vibrate_fallback_strength" to 150)))
        assertEquals(200, RumbleSettings.read(mapOf("seekbar_vibrate_fallback_strength" to 999)).deviceStrength)
    }
}

class MotionTest {
    private val controller = MotionSettings(source = MotionSource.CONTROLLER)

    @Test
    fun `motion source routing`() {
        assertEquals(MotionFeed.CONTROLLER, MotionRouting.resolve(controller, 0, padHasSensor = true, phoneHasSensor = true))
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(controller, 0, padHasSensor = false, phoneHasSensor = true))
        assertEquals(MotionFeed.PHONE, MotionRouting.resolve(controller.copy(phoneFallback = true), 0, false, true))
        // The phone only ever stands in for player 1.
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(controller.copy(phoneFallback = true), 1, false, true))
        assertEquals(MotionFeed.PHONE, MotionRouting.resolve(MotionSettings(source = MotionSource.PHONE), 0, true, true))
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(MotionSettings(source = MotionSource.PHONE), 0, true, false))
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(MotionSettings(mode = GyroMode.OFF), 0, true, true))
        // The mapping modes keep the gyro to themselves.
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(MotionSettings(mode = GyroMode.RIGHT_STICK), 0, true, true))
        assertEquals(MotionFeed.NONE, MotionRouting.resolve(MotionSettings(mode = GyroMode.MOUSE, source = MotionSource.PHONE), 0, true, true))
    }

    @Test
    fun `phone passthrough announces its own pad only without a controller`() {
        val phone = MotionSettings(source = MotionSource.PHONE)
        assertTrue(MotionRouting.announcePhonePad(phone, controllerAttached = false, phoneHasGyro = true))
        assertFalse(MotionRouting.announcePhonePad(phone, controllerAttached = true, phoneHasGyro = true))
        assertFalse(MotionRouting.announcePhonePad(phone, controllerAttached = false, phoneHasGyro = false))
        assertFalse(MotionRouting.announcePhonePad(controller, controllerAttached = false, phoneHasGyro = true))
        assertFalse(MotionRouting.announcePhonePad(phone.copy(mode = GyroMode.RIGHT_STICK), controllerAttached = false, phoneHasGyro = true))
    }

    @Test
    fun `gyro is scaled to degrees and gated by the hold trigger`() {
        val (x, _, _) = MotionRouting.gyroOut(1f, 0f, 0f, controller, 0, 0)
        assertEquals(57.29578f, x, 0.001f)
        val double = MotionRouting.gyroOut(1f, 0f, 0f, controller.copy(sensitivity = 200), 0, 0)
        assertEquals(114.59156f, double.first, 0.001f)
        val held = controller.copy(hold = MotionHold.RIGHT_TRIGGER)
        assertEquals(Triple(0f, 0f, 0f), MotionRouting.gyroOut(1f, 1f, 1f, held, 255, 0))
        assertTrue(MotionRouting.gyroOut(1f, 1f, 1f, held, 0, 255).first > 0f)
        val toggle = controller.copy(hold = MotionHold.TOGGLE)
        assertEquals(Triple(0f, 0f, 0f), MotionRouting.gyroOut(1f, 1f, 1f, toggle, 255, 255, toggledOn = false))
        assertTrue(MotionRouting.gyroOut(1f, 1f, 1f, toggle, 0, 0, toggledOn = true).first > 0f)
    }

    @Test
    fun `hold thresholds`() {
        assertTrue(MotionHold.ALWAYS.allows(0, 0))
        assertFalse(MotionHold.LEFT_TRIGGER.allows(MotionHold.TRIGGER_THRESHOLD - 1, 255))
        assertTrue(MotionHold.LEFT_TRIGGER.allows(MotionHold.TRIGGER_THRESHOLD, 0))
        assertTrue(MotionHold.EITHER_TRIGGER.allows(0, 200))
        assertFalse(MotionHold.EITHER_TRIGGER.allows(10, 10))
        assertFalse(MotionHold.TOGGLE.allows(255, 255, toggledOn = false))
        assertTrue(MotionHold.TOGGLE.allows(0, 0, toggledOn = true))
    }

    @Test
    fun `motion settings keep V+ keys in step`() {
        // Never written by Nebula: derived from V+'s toggles.
        val fresh = MotionSettings.read(emptyMap<String, Any>())
        assertEquals(MotionSource.CONTROLLER, fresh.source)
        assertEquals(GyroMode.PASSTHROUGH, fresh.mode)
        assertEquals(GyroMode.OFF, MotionSettings.read(mapOf("checkbox_gamepad_motion_sensors" to false)).mode)
        assertTrue(MotionSettings.read(mapOf("checkbox_gamepad_motion_fallback" to true)).phoneFallback)
        assertEquals(GyroMode.RIGHT_STICK, MotionSettings.read(mapOf("gyro_to_right_stick" to true)).mode)
        assertEquals(GyroMode.MOUSE, MotionSettings.read(mapOf("gyro_to_mouse" to true, "gyro_to_right_stick" to true)).mode)
        assertEquals(250, MotionSettings.read(mapOf("gyro_sensitivity_multiplier" to 2.5f)).sensitivity)
        assertEquals(MotionHold.RIGHT_TRIGGER, MotionSettings.read(mapOf("gyro_activation_key_code" to android.view.KeyEvent.KEYCODE_BUTTON_R2)).hold)

        val stick = MotionSettings(
            mode = GyroMode.RIGHT_STICK, source = MotionSource.PHONE, sensitivity = 150, sensitivityX = 120, sensitivityY = 80,
            invertY = true, deadzone = 3, smoothing = 40, stickMinimum = 20, hold = MotionHold.TOGGLE, toggleButton = GyroToggleButton.L3,
        )
        val stored = MotionSettings.entries(stick)
        assertEquals(true, stored["gyro_to_right_stick"])
        assertEquals(false, stored["gyro_to_mouse"])
        assertEquals(1.5f, stored["gyro_sensitivity_multiplier"])
        assertEquals(true, stored["gyro_invert_y_axis"])
        assertEquals(false, stored["checkbox_gamepad_motion_fallback"])
        assertEquals(stick, MotionSettings.read(stored))

        val phone = MotionSettings(source = MotionSource.PHONE, sensitivity = 150, hold = MotionHold.EITHER_TRIGGER)
        val p = MotionSettings.entries(phone)
        assertEquals(false, p["checkbox_gamepad_motion_sensors"])
        assertEquals(true, p["checkbox_gamepad_motion_fallback"])
        assertEquals(phone, MotionSettings.read(p))
        val fallback = MotionSettings(mode = GyroMode.MOUSE, phoneFallback = true)
        assertEquals(fallback, MotionSettings.read(MotionSettings.entries(fallback)))
        val off = MotionSettings.entries(MotionSettings(mode = GyroMode.OFF))
        assertEquals(false, off["checkbox_gamepad_motion_sensors"])
        assertEquals(false, off["checkbox_gamepad_motion_fallback"])
        assertEquals(GyroMode.OFF, MotionSettings.read(off).mode)
        assertEquals(MotionSettings.MAX_SENSITIVITY, MotionSettings.read(mapOf("nebula_motion_sensitivity" to 9000)).sensitivity)
    }

    @Test
    fun `phone passthrough with always is moved to gyro to right stick until a mode is chosen`() {
        // 0.3.0-dev9 setup: "This device" + "Always" as passthrough, which an Xbox pad on the PC ignores.
        val dev9 = mapOf("nebula_motion_source" to "phone", "nebula_motion_hold" to "always", "checkbox_gamepad_motion_fallback" to true)
        val read = MotionSettings.read(dev9)
        assertEquals(GyroMode.RIGHT_STICK, read.mode)
        assertEquals(MotionSource.PHONE, read.source)
        assertTrue(read.migratedToStick)
        // Once any gyro setting is saved the mode is the user's choice.
        val chosen = MotionSettings.entries(read.copy(mode = GyroMode.PASSTHROUGH))
        assertEquals(GyroMode.PASSTHROUGH, MotionSettings.read(chosen).mode)
        assertFalse(MotionSettings.read(chosen).migratedToStick)
        // Aim-while-held passthrough and controller gyro stay as they were.
        assertEquals(GyroMode.PASSTHROUGH, MotionSettings.read(dev9 + ("nebula_motion_hold" to "r2")).mode)
        assertEquals(GyroMode.PASSTHROUGH, MotionSettings.read(mapOf("nebula_motion_source" to "controller")).mode)
        // dev9's "off" source is the Off mode.
        assertEquals(GyroMode.OFF, MotionSettings.read(mapOf("nebula_motion_source" to "off")).mode)
    }
}
