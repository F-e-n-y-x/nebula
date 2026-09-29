package io.github.f_e_n_y_x.nebula.controls

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import io.github.f_e_n_y_x.nebula.controls.ProfileJson.StoreData
import io.github.f_e_n_y_x.nebula.settings.LegacyPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * On-device controls profiles: one JSON file (`files/controls/profiles.json`, [ProfileJson]
 * store format) written atomically in the background. Read it through [library], which also
 * builds the Standard profile from the current Settings (layout style, Guide, L3/R3).
 *
 * Per-game presets can read the assignment with [profileIdForGame].
 */
class ControlsStore private constructor(context: Context) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(File(app.filesDir, "controls"), "profiles.json"))
    private val prefs = LegacyPrefs(app)
    private val _data = MutableStateFlow(load())
    val data: StateFlow<StoreData> = _data.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<StoreData>(Channel.CONFLATED)

    init {
        ProfilePrefsMigration.apply(prefs.prefs)
        scope.launch { for (d in writes) write(d) }
        adoptRetiredPresets()
    }

    /** A default or game choice naming a preset 0.4 no longer bundles becomes a profile of the user's. */
    private fun adoptRetiredPresets() {
        val (d, moved) = RetiredPresets.adopt(_data.value, System.currentTimeMillis())
        if (moved.isEmpty()) return
        val all = prefs.prefs.all
        val edit = prefs.prefs.edit()
        for ((old, new) in moved) for (k in listOf(OutsideTouch.KEY, LookOutput.KEY)) {
            (all[k + old] as? String)?.let { v -> if (all[k + new] == null) edit.putString(k + new, v) }
        }
        edit.apply()
        _data.value = d
        writes.trySend(d)
        Log.i(TAG, "Kept ${moved.size} retired preset choice(s) as profiles: ${moved.values}")
    }

    fun standardOptions(): StandardOptions {
        val all = prefs.prefs.all
        fun b(k: String, d: Boolean) = when (val v = all[k]) { is Boolean -> v; is String -> v.toBooleanStrictOrNull() ?: d; else -> d }
        return StandardOptions(
            style = PadStyle.of(all["list_osc_layout"] as? String),
            showGuide = b("checkbox_show_guide_button", true),
            l3r3Buttons = b("checkbox_only_show_L3R3", false),
            portraitHalfHeight = b("checkbox_half_height_osc_portrait", true),
        )
    }

    fun library(d: StoreData = _data.value): ProfileLibrary =
        ProfileLibrary(d, DefaultProfiles.standard(standardOptions()), { "p-" + UUID.randomUUID().toString().take(12) }, System::currentTimeMillis)

    /** Applies [change] and saves the result. */
    fun update(change: (ProfileLibrary) -> ProfileLibrary): ProfileLibrary {
        val next = change(library())
        _data.value = next.data
        writes.trySend(next.data)
        return next
    }

    fun profileIdForGame(gameKey: String): String? = _data.value.games[gameKey]

    private fun load(): StoreData = runCatching {
        if (!file.baseFile.exists()) StoreData() else ProfileJson.decodeStore(file.readFully().toString(Charsets.UTF_8))
    }.onFailure { Log.w(TAG, "Couldn't read controls profiles; starting fresh", it) }.getOrDefault(StoreData())

    private fun write(d: StoreData) {
        file.baseFile.parentFile?.mkdirs()
        val out = file.startWrite()
        try {
            out.write(ProfileJson.encodeStore(d).toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "Couldn't save controls profiles", e)
        }
    }

    companion object {
        private const val TAG = "ControlsStore"

        @Volatile private var instance: ControlsStore? = null

        fun get(context: Context): ControlsStore = instance ?: synchronized(this) {
            instance ?: ControlsStore(context).also { instance = it }
        }

        fun gameKey(hostId: String, gameId: String) = "$hostId:$gameId"
    }
}
