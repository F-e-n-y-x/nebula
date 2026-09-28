package io.github.f_e_n_y_x.nebula.controls

/**
 * Undo / redo over immutable snapshots. A drag is one step: [begin] remembers the state before
 * the gesture, [update] changes the present without recording, [commit] records it (and does
 * nothing if the gesture changed nothing).
 */
class EditHistory<T>(initial: T, private val limit: Int = 100) {
    var present: T = initial
        private set
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()
    private var gestureStart: T? = null

    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    val undoDepth get() = past.size

    private var lastMergeKey: String? = null

    /**
     * One complete change (add, delete, a field edit). Consecutive pushes with the same
     * [mergeKey] (a slider being dragged, a label being typed) fold into one undo step.
     */
    fun push(next: T, mergeKey: String? = null) {
        if (next == present) return
        commitPending()
        if (mergeKey != null && mergeKey == lastMergeKey && past.isNotEmpty()) {
            present = next
            return
        }
        record(present)
        present = next
        lastMergeKey = mergeKey
    }

    fun begin() {
        lastMergeKey = null
        if (gestureStart == null) gestureStart = present
    }

    fun update(next: T) {
        if (gestureStart == null) gestureStart = present
        present = next
    }

    fun commit() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != present) { record(start); lastMergeKey = null }
    }

    fun undo(): Boolean {
        commitPending()
        lastMergeKey = null
        val prev = past.removeLastOrNull() ?: return false
        future.addLast(present)
        present = prev
        return true
    }

    fun redo(): Boolean {
        commitPending()
        lastMergeKey = null
        val next = future.removeLastOrNull() ?: return false
        past.addLast(present)
        present = next
        return true
    }

    /** Starts over from [state] with no history (a different profile or orientation was opened). */
    fun reset(state: T) {
        past.clear(); future.clear(); gestureStart = null; lastMergeKey = null
        present = state
    }

    private fun commitPending() { if (gestureStart != null) commit() }

    private fun record(state: T) {
        past.addLast(state)
        while (past.size > limit) past.removeFirst()
        future.clear()
    }
}
