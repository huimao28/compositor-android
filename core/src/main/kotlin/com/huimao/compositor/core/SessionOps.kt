package com.huimao.compositor.core

/**
 * Pure, immutable edits on a [PixelSession].
 *
 * Every function returns a new session; the caller decides what becomes an
 * undo step (the `:app` ViewModel commits these to [PixelHistory]).
 * All new layers get a valid [Transform] (required by manifest validation).
 */

/** A fresh document: one transparent raster layer sized to the canvas. */
fun newPixelSession(width: Int, height: Int, name: String = "图层 1"): PixelSession {
    require(width in 1..Document.MAX_CANVAS_SIDE && height in 1..Document.MAX_CANVAS_SIDE)
    val layer = Layer(
        name = name,
        transform = Transform(x = 0.0, y = 0.0, width = width.toDouble(), height = height.toDouble()),
    )
    return PixelSession(
        document = Document(width = width, height = height, layers = listOf(layer), activeLayerId = layer.id),
        rasters = mapOf(layer.id to RasterImage.transparent(width, height)),
    )
}

private fun PixelSession.updateLayer(id: String, change: (Layer) -> Layer): PixelSession {
    val layers = document.layers.map { if (it.id == id) change(it) else it }
    return copy(document = document.copy(layers = layers))
}

/** Adds a raster layer on top, sized to [raster], active afterwards. */
fun PixelSession.addRasterLayer(name: String, raster: RasterImage): PixelSession {
    val layer = Layer(
        name = name,
        transform = Transform(
            x = 0.0, y = 0.0,
            width = raster.width.toDouble(), height = raster.height.toDouble(),
        ),
    )
    return copy(
        document = document.copy(
            layers = document.layers + layer,
            activeLayerId = layer.id,
        ),
        rasters = rasters + (layer.id to raster),
    )
}

/** Adds an empty group on top. */
fun PixelSession.addGroup(name: String): PixelSession {
    val group = Layer(name = name, kind = LayerKind.GROUP)
    return copy(
        document = document.copy(
            layers = document.layers + group,
            activeLayerId = group.id,
        ),
    )
}

/** Deletes a layer (and, for groups, its children) plus their rasters. */
fun PixelSession.deleteLayer(id: String): PixelSession {
    val doomed = mutableSetOf(id)
    var grew: Boolean
    do {
        grew = false
        for (l in document.layers) {
            if (l.parentId in doomed && doomed.add(l.id)) grew = true
        }
    } while (grew)
    val layers = document.layers.filter { it.id !in doomed }
    val active = document.activeLayerId?.takeIf { it !in doomed } ?: layers.lastOrNull()?.id
    return copy(
        document = document.copy(layers = layers, activeLayerId = active),
        rasters = rasters.filterKeys { it !in doomed },
    )
}

fun PixelSession.renameLayer(id: String, name: String): PixelSession {
    require(name.isNotBlank()) { "layer name must not be blank" }
    return updateLayer(id) { it.copy(name = name.trim()) }
}

/**
 * Moves a layer to [toIndex] in bottom-to-top order (clamped).
 * Groups keep their children; a group move carries the whole subtree.
 */
fun PixelSession.moveLayer(id: String, toIndex: Int): PixelSession {
    val layers = document.layers.toMutableList()
    val from = layers.indexOfFirst { it.id == id }
    require(from >= 0) { "unknown layer $id" }
    // Collect the subtree rooted at id (the layer plus its descendants).
    val subtree = mutableListOf<Layer>()
    val queue = ArrayDeque(listOf(id))
    val inSubtree = mutableSetOf(id)
    while (queue.isNotEmpty()) {
        val cur = queue.removeFirst()
        for (l in layers) {
            if (l.parentId == cur && inSubtree.add(l.id)) queue.addLast(l.id)
        }
    }
    // Preserve document order inside the subtree.
    for (l in layers) if (l.id in inSubtree) subtree.add(l)
    layers.removeAll { it.id in inSubtree }
    val at = toIndex.coerceIn(0, layers.size)
    layers.addAll(at, subtree)
    return copy(document = document.copy(layers = layers))
}

fun PixelSession.setLayerVisible(id: String, visible: Boolean): PixelSession =
    updateLayer(id) { it.copy(visible = visible) }

fun PixelSession.setLayerOpacity(id: String, opacity: Double): PixelSession {
    require(opacity in 0.0..1.0) { "opacity must be 0..1" }
    return updateLayer(id) { it.copy(opacity = opacity) }
}

fun PixelSession.setLayerBlendMode(id: String, mode: BlendMode): PixelSession =
    updateLayer(id) { it.copy(blendMode = mode) }

fun PixelSession.setActiveLayer(id: String?): PixelSession {
    if (id != null) require(document.layers.any { it.id == id }) { "unknown layer $id" }
    return copy(document = document.copy(activeLayerId = id))
}

/**
 * Paints [points] (raster pixels of the layer) with [tip] onto [layerId].
 * Returns the session with the new raster; the old raster is untouched,
 * so committing this to [PixelHistory] gives pixel-exact undo.
 */
fun PixelSession.paintStroke(
    layerId: String,
    points: List<StrokePoint>,
    tip: BrushTip,
): PixelSession {
    val base = rasters[layerId] ?: return this
    val stroked = applyStroke(base, points, tip)
    if (stroked == base) return this
    return copy(rasters = rasters + (layerId to stroked))
}
