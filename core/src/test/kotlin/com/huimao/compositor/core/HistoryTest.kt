package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun docWith(vararg names: String) = Document(
        width = 100,
        height = 100,
        layers = names.map { Layer(name = it) },
    )

    @Test
    fun `undo restores previous state`() {
        val history = DocumentHistory(docWith("a"))
        history.commit(docWith("a", "b"))

        assertTrue(history.canUndo)
        assertTrue(history.undo())
        assertEquals(listOf("a"), history.current.layers.map { it.name })
        assertFalse(history.canUndo)
    }

    @Test
    fun `redo re-applies undone state`() {
        val history = DocumentHistory(docWith("a"))
        history.commit(docWith("a", "b"))
        history.undo()

        assertTrue(history.canRedo)
        assertTrue(history.redo())
        assertEquals(listOf("a", "b"), history.current.layers.map { it.name })
        assertFalse(history.canRedo)
    }

    @Test
    fun `commit clears redo stack`() {
        val history = DocumentHistory(docWith("a"))
        history.commit(docWith("a", "b"))
        history.undo()
        history.commit(docWith("a", "c"))

        assertFalse(history.canRedo)
        assertEquals(listOf("a", "c"), history.current.layers.map { it.name })
    }

    @Test
    fun `commit of identical state is a no-op`() {
        val history = DocumentHistory(docWith("a"))
        val same = history.current.copy()
        history.commit(same)

        assertFalse(history.canUndo)
        assertEquals(0, history.undoDepth)
    }

    @Test
    fun `undo on empty history returns false`() {
        val history = DocumentHistory(docWith("a"))
        assertFalse(history.undo())
        assertFalse(history.redo())
        assertEquals(listOf("a"), history.current.layers.map { it.name })
    }

    @Test
    fun `history is capped at max depth`() {
        val history = DocumentHistory(docWith("a"), maxDepth = 3)
        repeat(5) { history.commit(docWith("a", "b$it")) }

        assertEquals(3, history.undoDepth)
        // Oldest states were dropped; 3 undos land on b1, not the initial "a".
        repeat(3) { assertTrue(history.undo()) }
        assertFalse(history.canUndo)
        assertEquals(listOf("a", "b1"), history.current.layers.map { it.name })
    }

    @Test
    fun `clear drops history but keeps current`() {
        val history = DocumentHistory(docWith("a"))
        history.commit(docWith("a", "b"))
        history.clear()

        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertEquals(listOf("a", "b"), history.current.layers.map { it.name })
    }

    @Test
    fun `multi-step undo-redo walk`() {
        val history = DocumentHistory(docWith("a"))
        history.commit(docWith("a", "b"))
        history.commit(docWith("a", "b", "c"))

        history.undo()
        history.undo()
        assertEquals(listOf("a"), history.current.layers.map { it.name })
        history.redo()
        assertEquals(listOf("a", "b"), history.current.layers.map { it.name })
    }
}
