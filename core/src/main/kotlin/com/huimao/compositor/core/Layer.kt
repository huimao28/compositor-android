package com.huimao.compositor.core

/**
 * Layer transform. Rotation is clockwise degrees, matching upstream semantics.
 * The transform is stored separately from the pixels (non-destructive).
 *
 * Field names follow upstream `LayerTransform` (flipX/flipY/sampling).
 */
data class Transform(
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val rotation: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val sampling: LayerSampling = LayerSampling.HIGH,
) {
    /** Upstream `LayerTransform.isValid`. Checked when loading a manifest. */
    val isValid: Boolean
        get() = listOf(x, y, width, height, rotation).all { it.isFinite() } &&
            width in 1.0..300_000.0 && height in 1.0..300_000.0 &&
            kotlin.math.abs(x) <= 1_000_000 && kotlin.math.abs(y) <= 1_000_000
}

/** Upstream `LayerSampling`. Serial names match the Mac app's manifest values. */
enum class LayerSampling(val serialName: String) {
    NEAREST("Nearest"),
    SMOOTH("Smooth"),
    HIGH("High quality");

    companion object {
        fun fromSerialName(name: String): LayerSampling =
            entries.firstOrNull { it.serialName == name } ?: HIGH
    }
}

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
    /**
     * Bare filename inside the package's `images/` directory, e.g. `"<id>.png"`.
     * Upstream requires it to equal `"$id.png"` exactly. Null for blank layers and groups.
     */
    val imageFile: String? = null,
) {
    init {
        require(opacity in 0.0..1.0) { "opacity must be in 0..1, was $opacity" }
        require(kind != LayerKind.GROUP || imageFile == null) { "groups cannot carry images" }
    }
}
