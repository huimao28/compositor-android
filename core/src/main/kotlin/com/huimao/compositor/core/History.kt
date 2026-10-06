package com.huimao.compositor.core

/**
 * Undo/redo over immutable snapshots.
 *
 * Upstream keeps undo history in-session only and starts with clean history on
 * open; this port does the same ([clear]).
 *
 * @param T the snapshot type. Use [Document] for model-only history, or
 *   [PixelSession] for pixel-exact stroke undo (Phase 2): a stroke returns a
 *   new raster instead of mutating, so restoring the pre-stroke snapshot
 *   restores every pixel exactly.
 */
open class History<T>(initial: T, private val maxDepth: Int = 100) {
    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()
    val undoDepth: Int get() = past.size
    val redoDepth: Int get() = future.size

    /**
     * Records [next] as the new current state and clears the redo stack.
     * No-op when [next] equals [current] (avoids junk undo steps).
     */
    fun commit(next: T) {
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

/** Model-only history; kept as an alias so existing call sites keep working. */
typealias DocumentHistory = History<Document>

/**
 * The editable pixel state of a document: the model plus one raster per
 * image layer, keyed by layer id. Rasters are treated as immutable values —
 * brush strokes return new instances — so a snapshot is pixel-exact.
 */
data class PixelSession(
    val document: Document,
    val rasters: Map<String, RasterImage>,
)

/** Pixel-exact undo/redo for brush strokes and other raster edits. */
typealias PixelHistory = History<PixelSession>
