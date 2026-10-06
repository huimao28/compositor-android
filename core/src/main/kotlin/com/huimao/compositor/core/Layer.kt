package com.huimao.compositor.core

/**
 * Layer transform. Rotation is clockwise degrees, matching upstream semantics.
 * The transform is stored separately from the pixels (non-destructive).
 */
data class Transform(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val rotation: Double = 0.0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

enum class LayerKind { RASTER, GROUP }

data class Layer(
    val id: String = newUuid(),
    val name: String,
    val kind: LayerKind = LayerKind.RASTER,
    val visible: Boolean = true,
    val opacity: Double = 1.0,
    val blendMode: BlendMode = BlendMode.NORMAL,
    val transform: Transform = Transform(),
    /** Null for root layers; must reference an existing GROUP layer. */
    val parentId: String? = null,
    /** Relative path like "images/<uuid>.png". Null for blank layers and groups. */
    val imageFile: String? = null,
) {
    init {
        require(opacity in 0.0..1.0) { "opacity must be in 0..1, was $opacity" }
        require(kind != LayerKind.GROUP || imageFile == null) { "groups cannot carry images" }
    }
}
