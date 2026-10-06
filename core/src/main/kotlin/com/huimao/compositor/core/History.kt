package com.huimao.compositor.core

/**
 * Undo/redo over immutable [Document] snapshots.
 *
 * Upstream keeps undo history in-session only and starts with clean history on
 * open; this port does the same ([clear]). Pixel-level undo arrives with the
 * raster engine in Phase 2 — for now a snapshot covers the whole document
 * model (layers, visibility, opacity, blend modes, hierarchy).
 */
class DocumentHistory(initial: Document, private val maxDepth: Int = 100) {
    private val past = ArrayDeque<Document>()
    private val future = ArrayDeque<Document>()

    var current: Document = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoDepth: Int get() = past.size
    val redoDepth: Int get() = future.size

    /**
     * Records [next] as the new current state and clears the redo stack.
     * No-op when [next] equals [current] (avoids junk undo steps).
     */
    fun commit(next: Document) {
        if (next == current) return
        past.addLast(current)
        if (past.size > maxDepth) past.removeFirst()
        future.clear()
        current = next
    }

    /** Restores the previous state. Returns false when there is nothing to undo. */
    fun undo(): Boolean {
        if (!canUndo) return false
        future.addLast(current)
        current = past.removeLast()
        return true
    }

    /** Re-applies an undone state. Returns false when there is nothing to redo. */
    fun redo(): Boolean {
        if (!canRedo) return false
        past.addLast(current)
        current = future.removeLast()
        return true
    }

    /** Drops all history, e.g. when opening a document. Keeps [current]. */
    fun clear() {
        past.clear()
        future.clear()
    }
}
