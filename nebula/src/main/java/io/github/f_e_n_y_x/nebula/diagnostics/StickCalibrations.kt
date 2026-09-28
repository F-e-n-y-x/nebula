package io.github.f_e_n_y_x.nebula.diagnostics

import android.content.Context
import android.content.SharedPreferences
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Saved stick calibrations, one per physical controller (by InputDevice descriptor), shared by the
 * diagnostics screen and GamepadMapper. [install] loads them at app start; until then, and for
 * pads without one, [forDevice] returns null and the mapper uses the global deadzone.
 */
object StickCalibrations {
    private const val FILE = "nebula_diagnostics"
    private const val KEY = "stick_calibrations"

    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow<Map<String, StickCalibration>>(emptyMap())

    /** Every saved calibration by descriptor. */
    val all: StateFlow<Map<String, StickCalibration>> = state.asStateFlow()

    /** Descriptors by Android device id; ids are not reused while a device stays connected. */
    private val descriptors = HashMap<Int, String?>()

    /** Resolves a device id to its descriptor; replaced in tests. */
    @Volatile
    internal var descriptorOf: (deviceId: Int) -> String? = { id -> InputDevice.getDevice(id)?.descriptor }

    fun install(context: Context) {
        val p = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = p
        state.value = StickCalibration.decodeAll(p.getString(KEY, null)).associateBy { it.descriptor }
    }

    /** The calibration for a connected device, or null. Cheap: called for every stick event. */
    fun forDevice(deviceId: Int): StickCalibration? {
        val map = state.value
        if (map.isEmpty()) return null
        val d = synchronized(descriptors) { descriptors.getOrPut(deviceId) { runCatching { descriptorOf(deviceId) }.getOrNull() } }
        return d?.let { map[it] }
    }

    fun get(descriptor: String): StickCalibration? = state.value[descriptor]

    fun save(calibration: StickCalibration) {
        val c = calibration.sanitized()
        write(state.value + (c.descriptor to c))
    }

    fun remove(descriptor: String) = write(state.value - descriptor)

    /** A device went away or changed: forget its cached descriptor. */
    fun forget(deviceId: Int) { synchronized(descriptors) { descriptors.remove(deviceId) } }

    /** Test hook: replace the stored set without any Android storage. */
    internal fun setForTest(all: Collection<StickCalibration>) {
        prefs = null
        synchronized(descriptors) { descriptors.clear() }
        state.value = all.associateBy { it.descriptor }
    }

    private fun write(map: Map<String, StickCalibration>) {
        state.value = map
        prefs?.edit()?.putString(KEY, StickCalibration.encodeAll(map.values))?.apply()
    }

    /** Plain-text dump for the diagnostics export. */
    fun describe(): String = state.value.values.joinToString("\n") { c ->
        fun s(n: String, v: StickShape) = "  $n: deadzone ${pct(v.deadzone)}, edge ${pct(v.outer)}, anti-deadzone ${pct(v.antiDeadzone)}, centre ${"%+.3f".format(v.centerX)}, ${"%+.3f".format(v.centerY)}"
        "${c.name} [${c.descriptor}]\n${s("Left stick", c.left)}\n${s("Right stick", c.right)}"
    }.ifEmpty { "No stick calibrations saved." }

    private fun pct(v: Float) = "${(v * 100).toInt()} %"
}
