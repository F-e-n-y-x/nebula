package io.github.f_e_n_y_x.nebula.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When each engine connection stage started and how the attempt ended, for the last few
 * connection attempts (a stream start or a live resolution change). The engine reports only
 * stage starts; stages run one after another, so a stage lasts until the next one starts, and the
 * last one until the stream is connected or fails.
 */
class ConnectionTimeline(
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val wallMs: () -> Long = System::currentTimeMillis,
    private val keep: Int = 10,
    private val log: (String) -> Unit = {},
) {
    enum class Outcome { RUNNING, CONNECTED, FAILED, ENDED }

    /** [startMs] is relative to the attempt's start. */
    data class Stage(val name: String, val startMs: Long)

    data class Attempt(
        val label: String,
        val startedAtWallMs: Long,
        val startMs: Long,
        val stages: List<Stage> = emptyList(),
        val outcome: Outcome = Outcome.RUNNING,
        /** Time from the start to connected / failed. */
        val totalMs: Long? = null,
        val failure: String? = null,
    )

    private val lock = Any()
    private val attempts = ArrayDeque<Attempt>()
    private val flow = MutableStateFlow<List<Attempt>>(emptyList())

    /** Newest last. */
    val all: StateFlow<List<Attempt>> = flow.asStateFlow()

    fun begin(label: String) = mutate {
        // An attempt still running is abandoned when the next begins (the screen went away).
        replaceLast { if (it.outcome == Outcome.RUNNING) close(it, Outcome.ENDED, "Superseded") else it }
        attempts.addLast(Attempt(label, wallMs(), clockMs()))
        while (attempts.size > keep) attempts.removeFirst()
        log("connect: begin $label")
    }

    fun stage(name: String) = mutate {
        replaceLast { a ->
            if (a.outcome != Outcome.RUNNING) return@replaceLast a
            a.copy(stages = a.stages + Stage(name, clockMs() - a.startMs))
        }
        log("connect: stage $name")
    }

    fun connected() = mutate {
        replaceLast { if (it.outcome == Outcome.RUNNING) close(it, Outcome.CONNECTED, null) else it }
        log("connect: connected")
    }

    fun failed(why: String) = mutate {
        replaceLast { if (it.outcome == Outcome.RUNNING) close(it, Outcome.FAILED, why) else it }
        log("connect: failed $why")
    }

    /** The stream ended; only matters for an attempt that never connected. */
    fun ended(why: String?) = mutate {
        replaceLast { if (it.outcome == Outcome.RUNNING) close(it, Outcome.ENDED, why) else it }
    }

    private fun close(a: Attempt, outcome: Outcome, why: String?): Attempt {
        return a.copy(outcome = outcome, totalMs = clockMs() - a.startMs, failure = why)
    }

    private inline fun mutate(block: () -> Unit) {
        synchronized(lock) {
            block()
            flow.value = attempts.toList()
        }
    }

    private inline fun replaceLast(f: (Attempt) -> Attempt) {
        val last = attempts.removeLastOrNull() ?: return
        attempts.addLast(f(last))
    }

    /** Plain text for the export, newest first. */
    fun describe(): String {
        val list = all.value
        if (list.isEmpty()) return "No connection attempts since Nebula started."
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return list.asReversed().joinToString("\n\n") { a ->
            buildString {
                append(fmt.format(Date(a.startedAtWallMs))).append("  ").append(a.label).append('\n')
                durations(a).forEach { (name, ms) ->
                    append("  ").append(name.padEnd(28)).append(ms?.let { "$it ms" } ?: "—").append('\n')
                }
                append("  => ").append(a.outcome.name.lowercase())
                a.totalMs?.let { append(" after $it ms") }
                a.failure?.let { append(" ($it)") }
            }
        }
    }

    companion object {
        /** Every stage with its duration: until the next stage started, or until the attempt closed. */
        fun durations(a: Attempt): List<Pair<String, Long?>> = a.stages.mapIndexed { i, s ->
            val end = a.stages.getOrNull(i + 1)?.startMs ?: a.totalMs
            s.name to end?.let { (it - s.startMs).coerceAtLeast(0) }
        }

        /** The process-wide timeline the engine and the session recorder write to. */
        val shared = ConnectionTimeline(log = { android.util.Log.i("NebulaDiag", it) })
    }
}
