package io.github.f_e_n_y_x.nebula.controls

/**
 * The editor's document: one orientation's element list with undo/redo and the selection.
 * Everything the editor screen does goes through here, so the behaviour is unit-tested.
 */
class LayoutEditor(initial: List<ControlElement>, private val newId: () -> String) {
    private val history = EditHistory(initial)
    val elements: List<ControlElement> get() = history.present
    var selectedId: String? = null
        private set
    val selected: ControlElement? get() = selectedId?.let { id -> elements.firstOrNull { it.id == id } }
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo

    /** Bumped on every change, for Compose to observe. */
    var version = 0
        private set

    private fun changed() { version++ }

    fun select(id: String?) {
        selectedId = id?.takeIf { i -> elements.any { it.id == i } }
        changed()
    }

    fun add(kind: ElementKind, x: Float = 0.5f, y: Float = 0.5f): ControlElement {
        val e = newElement(kind, newId(), x, y)
        history.push(elements + e)
        selectedId = e.id
        changed()
        return e
    }

    fun delete(id: String) {
        history.push(elements.filterNot { it.id == id })
        if (selectedId == id) selectedId = null
        changed()
    }

    fun duplicate(id: String): ControlElement? {
        val src = elements.firstOrNull { it.id == id } ?: return null
        val copy = src.copy(id = newId(), x = (src.x + 0.04f).coerceAtMost(1f), y = (src.y + 0.04f).coerceAtMost(1f))
        history.push(elements + copy)
        selectedId = copy.id
        changed()
        return copy
    }

    /** Draws [id] last, above everything else. */
    fun bringToFront(id: String) {
        val e = elements.firstOrNull { it.id == id } ?: return
        history.push(elements.filterNot { it.id == id } + e)
        changed()
    }

    /** A field edit; [mergeKey] folds a run of edits (a slider drag, typing) into one undo step. */
    fun edit(id: String, mergeKey: String? = null, change: (ControlElement) -> ControlElement) {
        history.push(elements.map { if (it.id == id) change(it).clampedSize() else it }, mergeKey?.let { "$id:$it" })
        changed()
    }

    // A drag or pinch: begin → any number of moves/resizes → end = one undo step.
    fun beginGesture() = history.begin()

    fun moveTo(id: String, x: Float, y: Float) {
        history.update(elements.map { if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it })
        changed()
    }

    fun resizeTo(id: String, width: Float, height: Float) {
        history.update(elements.map { if (it.id == id) it.copy(width = width, height = height).clampedSize() else it })
        changed()
    }

    fun endGesture() {
        history.commit()
        changed()
    }

    fun undo() {
        if (history.undo()) { fixSelection(); changed() }
    }

    fun redo() {
        if (history.redo()) { fixSelection(); changed() }
    }

    /** Replaces the whole layout as one step (reset to default, copy from the other orientation). */
    fun replaceAll(list: List<ControlElement>) {
        history.push(list)
        fixSelection()
        changed()
    }

    fun reset(list: List<ControlElement>) {
        history.reset(list)
        selectedId = null
        changed()
    }

    private fun fixSelection() {
        if (selectedId != null && elements.none { it.id == selectedId }) selectedId = null
    }
}
