package io.github.f_e_n_y_x.nebula.diagnostics

import io.github.f_e_n_y_x.nebula.domain.StreamRepository
import io.github.f_e_n_y_x.nebula.domain.StreamTarget
import io.github.f_e_n_y_x.nebula.domain.model.DisplayMode
import io.github.f_e_n_y_x.nebula.domain.model.Game
import io.github.f_e_n_y_x.nebula.domain.model.StreamSettings
import io.github.f_e_n_y_x.nebula.domain.model.StreamState
import io.github.f_e_n_y_x.nebula.domain.model.VideoMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart

/**
 * Wraps the real stream repository to keep diagnostics: a [SessionSummary] per stream for
 * [history], and the connect attempt in [timeline]. Everything else passes straight through.
 */
class RecordingStreamRepository(
    private val inner: StreamRepository,
    private val history: (SessionSummary) -> Unit,
    private val timeline: ConnectionTimeline = ConnectionTimeline.shared,
    private val wallMs: () -> Long = System::currentTimeMillis,
    private val monoMs: () -> Long = { System.nanoTime() / 1_000_000 },
) : StreamRepository by inner {

    @Volatile
    private var current: SessionAccumulator? = null

    override fun start(game: Game, mode: DisplayMode, settings: StreamSettings, target: StreamTarget): Flow<StreamState> {
        var acc: SessionAccumulator? = null
        var end: String? = null
        var failed = false
        var connected = false
        return inner.start(game, mode, settings, target)
            .onStart {
                acc = SessionAccumulator(wallMs(), monoMs(), game.name, game.hostId, if (mode == DisplayMode.VIRTUAL) "Virtual display" else "Mirror")
                    .also { current = it }
                timeline.begin("Start ${game.name}")
            }
            .onEach { st ->
                when (st) {
                    is StreamState.Live -> {
                        if (!connected) { connected = true; timeline.connected() }
                        acc?.add(st.stats, monoMs())
                    }
                    is StreamState.Failed -> { failed = true; end = st.reason; timeline.failed(st.reason) }
                    is StreamState.Ended -> { end = st.reason; timeline.ended(st.reason) }
                    StreamState.Starting -> Unit
                }
            }
            .onCompletion { cause ->
                val a = acc ?: return@onCompletion
                if (current === a) current = null
                if (!connected && !failed) timeline.ended(end ?: "Left before it connected")
                // A screen closed before anything happened isn't worth a slot in the history.
                if (a.samples == 0 && !failed) return@onCompletion
                val why = end ?: if (cause != null && cause !is kotlinx.coroutines.CancellationException) cause.message else null
                runCatching { history(a.finish(monoMs(), why, failed)) }
            }
    }

    override suspend fun switchMode(mode: VideoMode): Result<Unit> {
        timeline.begin("Change to ${mode.width}×${mode.height} @ ${mode.fps}")
        val r = inner.switchMode(mode)
        if (r.isSuccess) {
            timeline.connected()
            current?.resolutionChanged()
        } else {
            timeline.failed(r.exceptionOrNull()?.message ?: "failed")
        }
        return r
    }
}
