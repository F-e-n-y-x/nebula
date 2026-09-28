package io.github.f_e_n_y_x.nebula.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Undo/redo, the layout editor's operations, and snapping. */
class EditorTest {
    @Test
    fun historyUndoRedo() {
        val h = EditHistory(0)
        h.push(1); h.push(2); h.push(3)
        assertTrue(h.undo()); assertEquals(2, h.present)
        assertTrue(h.undo()); assertEquals(1, h.present)
        assertTrue(h.redo()); assertEquals(2, h.present)
        h.push(9) // a new edit drops the redo branch
        assertFalse(h.canRedo)
        assertTrue(h.undo()); assertEquals(2, h.present)
        assertTrue(h.undo()); assertTrue(h.undo())
        assertEquals(0, h.present)
        assertFalse(h.undo())
    }

    @Test
    fun aGestureIsOneStepAndANoOpGestureIsNone() {
        val h = EditHistory("a")
        h.begin(); h.update("b"); h.update("c"); h.update("d"); h.commit()
        assertEquals(1, h.undoDepth)
        h.undo(); assertEquals("a", h.present)
        h.redo(); assertEquals("d", h.present)
        h.begin(); h.update("x"); h.update("d"); h.commit()
        assertEquals(1, h.undoDepth)
    }

    @Test
    fun mergeKeysFoldSliderDrags() {
        val h = EditHistory(0)
        h.push(10, "opacity"); h.push(20, "opacity"); h.push(30, "opacity")
        h.push(31, "size")
        assertEquals(2, h.undoDepth)
        h.undo(); assertEquals(30, h.present)
        h.undo(); assertEquals(0, h.present)
        // After an undo the same key starts a new step.
        h.redo(); h.push(40, "opacity")
        assertEquals(2, h.undoDepth)
    }

    @Test
    fun historyIsBounded() {
        val h = EditHistory(0, limit = 5)
        repeat(20) { h.push(it + 1) }
        assertEquals(5, h.undoDepth)
        repeat(10) { h.undo() }
        assertEquals(15, h.present)
    }

    @Test
    fun layoutEditorOperationsUndo() {
        var n = 0
        val ed = LayoutEditor(emptyList()) { "e${++n}" }
        val a = ed.add(ElementKind.BUTTON, 0.3f, 0.4f)
        assertEquals(a.id, ed.selectedId)
        ed.beginGesture(); ed.moveTo(a.id, 0.5f, 0.5f); ed.moveTo(a.id, 0.6f, 0.7f); ed.endGesture()
        ed.beginGesture(); ed.resizeTo(a.id, 80f, 80f); ed.endGesture()
        ed.edit(a.id, "opacity") { it.copy(opacity = 0.5f) }
        ed.edit(a.id, "opacity") { it.copy(opacity = 0.4f) }
        val b = ed.duplicate(a.id)!!
        assertEquals(2, ed.elements.size)
        assertEquals(b.id, ed.selectedId)
        ed.delete(b.id)
        assertNull(ed.selectedId)

        val e = ed.elements.single()
        assertEquals(0.6f, e.x); assertEquals(0.7f, e.y); assertEquals(80f, e.width); assertEquals(0.4f, e.opacity)

        ed.undo() // delete
        assertEquals(2, ed.elements.size)
        ed.undo() // duplicate
        ed.undo() // opacity (both edits)
        assertEquals(1f, ed.elements.single().opacity)
        ed.undo() // resize
        assertEquals(56f, ed.elements.single().width)
        ed.undo() // move
        assertEquals(0.3f, ed.elements.single().x)
        ed.select(a.id)
        ed.undo() // add
        assertTrue(ed.elements.isEmpty())
        assertNull(ed.selectedId) // selection follows the element away
        ed.redo()
        assertEquals(1, ed.elements.size)
    }

    @Test
    fun editsAreClampedAndMovesStayOnScreen() {
        val ed = LayoutEditor(listOf(newElement(ElementKind.STICK, "s"))) { "x" }
        ed.edit("s") { it.copy(width = 5000f) }
        assertEquals(ControlElement.MAX_SIZE_DP, ed.elements[0].width)
        ed.beginGesture(); ed.moveTo("s", 3f, -1f); ed.endGesture()
        assertEquals(1f, ed.elements[0].x); assertEquals(0f, ed.elements[0].y)
    }

    @Test
    fun bringToFrontReorders() {
        val ed = LayoutEditor(listOf(newElement(ElementKind.BUTTON, "a"), newElement(ElementKind.BUTTON, "b"))) { "x" }
        ed.bringToFront("a")
        assertEquals(listOf("b", "a"), ed.elements.map { it.id })
        ed.undo()
        assertEquals(listOf("a", "b"), ed.elements.map { it.id })
    }

    // ---- snapping ----

    @Test
    fun gridSnapsTheCentre() {
        val r = Snapping.move(Box(103f, 49f, 40f, 40f), emptyList(), 1000f, 500f, grid = 16f, threshold = 6f, gridOn = true, alignOn = false)
        assertEquals(128f, r.left + 20f) // centre 123 → 128
        assertEquals(64f, r.top + 20f) // centre 69 → 64
        assertTrue(r.guidesX.isEmpty())
    }

    @Test
    fun gridOffLeavesPositionAlone() {
        val r = Snapping.move(Box(103f, 49f, 40f, 40f), emptyList(), 1000f, 500f, 16f, 6f, gridOn = false, alignOn = false)
        assertEquals(103f, r.left); assertEquals(49f, r.top)
    }

    @Test
    fun alignmentWithOtherElementsBeatsTheGrid() {
        val other = Box(300f, 200f, 60f, 60f) // centre (330, 230)
        // Moving box's centre at x = 333 (within 6 px of 330): aligns centres and draws a guide.
        val r = Snapping.move(Box(313f, 50f, 40f, 40f), listOf(other), 1000f, 500f, 16f, 6f, gridOn = true)
        assertEquals(330f, r.left + 20f)
        assertEquals(listOf(330f), r.guidesX)
        // Left edge 4 px from the other's right edge (360) snaps edge to edge.
        val e = Snapping.move(Box(364f, 50f, 40f, 40f), listOf(other), 1000f, 500f, 16f, 6f, gridOn = true)
        assertEquals(360f, e.left)
        // The screen's centre line counts too.
        val c = Snapping.move(Box(475f, 400f, 40f, 40f), emptyList(), 1000f, 500f, 16f, 6f, gridOn = false)
        assertEquals(500f, c.left + 20f)
        assertEquals(listOf(500f), c.guidesX)
    }

    @Test
    fun snappingKeepsElementsInsideTheCanvas() {
        val r = Snapping.move(Box(-50f, 480f, 40f, 40f), emptyList(), 1000f, 500f, 16f, 6f, gridOn = true, alignOn = false)
        assertEquals(0f, r.left)
        assertEquals(460f, r.top)
    }

    @Test
    fun sizeSnapping() {
        assertEquals(64f, Snapping.size(61f, 8f, true))
        assertEquals(61f, Snapping.size(61f, 8f, false))
        assertEquals(ControlElement.MIN_SIZE_DP, Snapping.size(3f, 8f, true))
        assertEquals(ControlElement.MAX_SIZE_DP, Snapping.size(999f, 8f, true))
    }
}
