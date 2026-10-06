package com.huimao.compositor.core

fun newUuid(): String = java.util.UUID.randomUUID().toString()

/**
 * A Compositor document. Layers are stored bottom-to-top,
 * matching the upstream manifest order.
 */
data class Document(
    val id: String = newUuid(),
    val width: Int,
    val height: Int,
    val activeLayerId: String? = null,
    val layers: List<Layer> = emptyList(),
) {
    init {
        require(width in 1..MAX_CANVAS_SIDE && height in 1..MAX_CANVAS_SIDE) {
            "canvas dimensions out of range: ${width}x$height"
        }
        require(layers.size <= MAX_LAYERS) { "too many layers: ${layers.size}" }
    }

    fun activeLayer(): Layer? = layers.firstOrNull { it.id == activeLayerId }

    companion object {
        const val MAX_CANVAS_SIDE = 30000
        const val MAX_LAYERS = 10000
    }
}
