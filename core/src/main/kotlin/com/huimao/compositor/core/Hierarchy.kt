package com.huimao.compositor.core

/**
 * Layer hierarchy traversal. Semantics follow upstream `LayerHierarchy`:
 * array order is bottom-to-top sibling order, renderers walk each group as a
 * contiguous subtree, and visibility is inherited without changing child flags.
 */
data class HierarchyEntry(
    val layer: Layer,
    val depth: Int,
    val effectiveVisible: Boolean,
)

/** Depth-first entries, bottom-to-top. Groups are included (use [Document.visibleLayers] to skip them). */
fun Document.hierarchyEntries(topFirst: Boolean = false): List<HierarchyEntry> {
    val children = layers.groupBy { it.parentId }
    val result = mutableListOf<HierarchyEntry>()
    fun visit(parentId: String?, depth: Int, visible: Boolean) {
        if (depth > 64) return
        val siblings = children[parentId] ?: emptyList()
        val ordered = if (topFirst) siblings.asReversed() else siblings
        for (layer in ordered) {
            val effective = visible && layer.visible
            result.add(HierarchyEntry(layer, depth, effective))
            if (layer.kind == LayerKind.GROUP) {
                visit(layer.id, depth + 1, effective)
            }
        }
    }
    visit(null, 0, true)
    return result
}

/** Layers that actually draw, bottom-to-top: no groups, no hidden subtrees. */
fun Document.visibleLayers(): List<Layer> =
    hierarchyEntries().filter { it.effectiveVisible && it.layer.kind != LayerKind.GROUP }.map { it.layer }

/**
 * Upstream `LayerOpacity.effective`: a layer's own opacity multiplied by every
 * group ancestor's opacity (folders are pass-through).
 */
fun Layer.effectiveOpacity(doc: Document): Double {
    val byId = doc.layers.associateBy { it.id }
    var opacity = this.opacity
    var id = parentId
    var depth = 0
    while (id != null && depth < 64) {
        val node = byId[id] ?: break
        opacity *= node.opacity
        id = node.parentId
        depth++
    }
    return opacity
}

/** Own flag ANDed with every ancestor's flag, per upstream `LayerHierarchy.entries`. */
fun Layer.isEffectivelyVisible(doc: Document): Boolean {
    val byId = doc.layers.associateBy { it.id }
    if (!visible) return false
    var id = parentId
    var depth = 0
    while (id != null && depth < 64) {
        val node = byId[id] ?: return false
        if (!node.visible) return false
        id = node.parentId
        depth++
    }
    return true
}
