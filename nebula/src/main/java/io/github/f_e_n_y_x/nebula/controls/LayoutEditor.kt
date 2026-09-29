package io.github.f_e_n_y_x.nebula.controls

/**
 * The editor's document: one orientation's element list with undo/redo and the selection.
 * Everything the editor screen does goes through here, so the behaviour is unit-tested.
 *
 * The selection is either one element ([selectedId]) or one placed group ([selectedGroup]:
 * every element sharing a [ControlElement.group] id), never both.
 */
class LayoutEditor(initial: List<ControlElement>, private val newId: () -> String) {
    private val history = EditHistory(initial)
    val elements: List<ControlElement> get() = history.present
    var selectedId: String? = null
        private set
    val selected: ControlElement? get() = selectedId?.let { id -> elements.firstOrNull { it.id == id } }
    var selectedGroup: String? = null
        private set
    /** Members of the selected group, in drawing order (empty when none is selected). */
    val selectedMembers: List<ControlElement> get() = selectedGroup?.let { elements.groupMembers(it) }.orEmpty()
    val canUndo get() = history.canUndo
    val canRedo get() = history.canRedo

    /** Bumped on every change, for Compose to observe. */
    var version = 0
        private set

    private fun changed() { version++ }

    fun select(id: String?) {
        selectedId = id?.takeIf { i -> elements.any { it.id == i } }
        selectedGroup = null
        changed()
    }

    fun selectGroup(group: String?) {
        selectedGroup = group?.takeIf { g -> elements.any { it.group == g } }
        selectedId = null
        changed()
    }

    // ------------------------------------------------------------ palette and groups

    /**
     * Places a palette [item] in an area of [areaW] × [areaH] dp: one element (selected), or a
     * group of elements sharing a new group id (the group selected). One undo step.
     */
    fun addItem(item: PaletteItem, areaW: Float, areaH: Float, portrait: Boolean = areaH > areaW): List<ControlElement> {
        val gid = if (item.isGroup) "${item.id}:${newId()}".take(40) else null
        val placed = Palette.place(item, areaW, areaH, newId, gid, portrait, occupied = elements)
        history.push(elements + placed)
        if (gid != null) { selectedGroup = gid; selectedId = null } else { selectedId = placed.first().id; selectedGroup = null }
        changed()
        return placed
    }

    /** Where a drag or pinch started, so group moves and scales don't drift or compound. */
    private var gestureBase: List<ControlElement>? = null

    /** Moves [group] by ([dx], [dy]) shares from where the current gesture began. */
    fun moveGroup(group: String, dx: Float, dy: Float) {
        val base = gestureBase ?: elements
        history.update(base.map { if (it.group == group) it.copy(x = (it.x + dx).coerceIn(0f, 1f), y = (it.y + dy).coerceIn(0f, 1f)) else it })
        changed()
    }

    /**
     * Scales [group] by [factor] about its centre, from where the current gesture began: sizes
     * and the gaps between members together. The factor is limited so no member leaves the size
     * limits. [areaW] × [areaH] is the controls area in dp (positions are shares of it).
     */
    fun scaleGroup(group: String, factor: Float, areaW: Float, areaH: Float) {
        val base = gestureBase ?: elements
        val members = base.groupMembers(group)
        if (members.isEmpty() || areaW <= 0f || areaH <= 0f) return
        val f = groupScaleLimits(members).let { factor.coerceIn(it.start, it.endInclusive) }
        val box = Palette.bounds(members.map { it.copy(x = it.x * areaW, y = it.y * areaH, width = it.dpW(areaW), height = it.dpH(areaH)) })
        val cx = (box[0] + box[2]) / 2 / areaW
        val cy = (box[1] + box[3]) / 2 / areaH
        history.update(
            base.map {
                if (it.group != group) it else it.copy(
                    x = (cx + (it.x - cx) * f).coerceIn(0f, 1f),
                    y = (cy + (it.y - cy) * f).coerceIn(0f, 1f),
                    width = it.width * f,
                    height = it.height * f,
                ).clampedSize()
            },
        )
        changed()
    }

    /** Turns [group] back into loose elements; the group is no longer selected. */
    fun splitGroup(group: String) {
        history.push(elements.map { if (it.group == group) it.copy(group = null) else it })
        if (selectedGroup == group) selectedGroup = null
        changed()
    }

    /** Takes one element out of its group (a group left with one member is dissolved). */
    fun removeFromGroup(id: String) {
        val g = elements.firstOrNull { it.id == id }?.group ?: return
        val left = elements.count { it.group == g } - 1
        history.push(elements.map { if (it.id == id || (left < 2 && it.group == g)) it.copy(group = null) else it })
        changed()
    }

    fun deleteGroup(group: String) {
        history.push(elements.filterNot { it.group == group })
        if (selectedGroup == group) selectedGroup = null
        fixSelection()
        changed()
    }

    /** A copy of [group] under a new group id, nudged down-right and selected. */
    fun duplicateGroup(group: String): List<ControlElement> {
        val members = elements.groupMembers(group)
        if (members.isEmpty()) return emptyList()
        val gid = "${group.substringBefore(':')}:${newId()}".take(40)
        val copies = members.map { it.copy(id = newId(), group = gid, x = (it.x + 0.04f).coerceAtMost(1f), y = (it.y + 0.04f).coerceAtMost(1f)) }
        history.push(elements + copies)
        selectedGroup = gid
        selectedId = null
        changed()
        return copies
    }

    fun bringGroupToFront(group: String) {
        history.push(elements.filterNot { it.group == group } + elements.groupMembers(group))
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
        val g = elements.firstOrNull { it.id == id }?.group
        val rest = elements.filterNot { it.id == id }
        // A group needs two members; a lone survivor becomes a loose element.
        history.push(if (g != null && rest.count { it.group == g } < 2) rest.map { if (it.group == g) it.copy(group = null) else it } else rest)
        if (selectedId == id) selectedId = null
        fixSelection()
        changed()
    }

    fun duplicate(id: String): ControlElement? {
        val src = elements.firstOrNull { it.id == id } ?: return null
        val copy = src.copy(id = newId(), group = null, x = (src.x + 0.04f).coerceAtMost(1f), y = (src.y + 0.04f).coerceAtMost(1f))
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

    /** The same field edit on every member of [group], as one undo step (merged like [edit]). */
    fun editGroup(group: String, mergeKey: String? = null, change: (ControlElement) -> ControlElement) {
        history.push(elements.map { if (it.group == group) change(it).clampedSize() else it }, mergeKey?.let { "group:$group:$it" })
        changed()
    }

    // A drag or pinch: begin → any number of moves/resizes → end = one undo step.
    fun beginGesture() {
        history.begin()
        if (gestureBase == null) gestureBase = elements
    }

    fun moveTo(id: String, x: Float, y: Float) {
        history.update(elements.map { if (it.id == id) it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f)) else it })
        changed()
    }

    fun resizeTo(id: String, width: Float, height: Float) {
        history.update(elements.map { if (it.id == id) it.copy(width = width, height = height).clampedSize() else it })
        changed()
    }

    fun endGesture() {
        gestureBase = null
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
        selectedGroup = null
        changed()
    }

    private fun fixSelection() {
        if (selectedId != null && elements.none { it.id == selectedId }) selectedId = null
        if (selectedGroup != null && elements.none { it.group == selectedGroup }) selectedGroup = null
    }
}

/** The range a group can be scaled by so every member stays within the element size limits. */
fun groupScaleLimits(members: List<ControlElement>): ClosedFloatingPointRange<Float> {
    var lo = 0f
    var hi = Float.MAX_VALUE
    members.forEach { m ->
        val (mn, mx) = if (m.areaSized) ControlElement.MIN_ZONE to 1f else ControlElement.MIN_SIZE_DP to ControlElement.MAX_SIZE_DP
        val small = minOf(m.width, m.height)
        val big = maxOf(m.width, m.height)
        if (small > 0f) lo = maxOf(lo, mn / small)
        if (big > 0f) hi = minOf(hi, mx / big)
    }
    return if (lo <= hi) lo..hi else 1f..1f
}

private fun ControlElement.dpW(areaW: Float) = if (areaSized) width * areaW else width
private fun ControlElement.dpH(areaH: Float) = if (areaSized) height * areaH else height
