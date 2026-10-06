package com.huimao.compositor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * `.comp` manifest (de)serialization.
 *
 * Field names are verified against upstream
 * `Compositor/IO/ProjectStore.swift` (`ProjectManifest` / `ProjectLayerRecord` /
 * `LayerTransform`). A `.comp` package is a folder containing `manifest.json`
 * plus `images/<layer UUID>.png` assets; the manifest's `imageFile` field is
 * the bare filename.
 *
 * Phase 1 implements the v1–v3 subset (layers, groups, opacity, 9 blend modes).
 * Reading accepts versions 1–11 and ignores unknown fields (additive v4+
 * features like masks/adjustments/text are dropped, which is documented in
 * `docs/project-format.md`); new saves declare version [MANIFEST_VERSION].
 */
const val MANIFEST_FORMAT = "com.compositor.project"

/** Version new saves declare. */
const val MANIFEST_VERSION = 3

/** Highest version this reader accepts (matches upstream `ProjectManifest.current`). */
const val MANIFEST_VERSION_MAX = 11

const val MANIFEST_COLOR_SPACE = "sRGB"

private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
private const val MAX_NAME_UTF8 = 16_384

@Serializable
data class ManifestPoint(val x: Double = 0.0, val y: Double = 0.0)

@Serializable
data class ManifestSize(val width: Double = 0.0, val height: Double = 0.0)

@Serializable
data class ManifestTransform(
    val origin: ManifestPoint = ManifestPoint(),
    val size: ManifestSize = ManifestSize(),
    /** clockwise degrees */
    val rotation: Double = 0.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false,
    val sampling: String = LayerSampling.HIGH.serialName,
)

@Serializable
data class ManifestLayer(
    val id: String,
    val name: String,
    val isVisible: Boolean = true,
    val transform: ManifestTransform = ManifestTransform(),
    val imageFile: String? = null,
    val parentID: String? = null,
    val isGroup: Boolean = false,
    val opacity: Double = 1.0,
    val blendMode: String = BlendMode.NORMAL.serialName,
)

@Serializable
data class Manifest(
    val format: String = MANIFEST_FORMAT,
    val version: Int = MANIFEST_VERSION,
    val colorSpace: String = MANIFEST_COLOR_SPACE,
    val resolution: Double? = null,
    val documentID: String,
    val width: Int,
    val height: Int,
    val activeLayerID: String? = null,
    val layers: List<ManifestLayer> = emptyList(),
)

private val manifestJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun Layer.toManifest() = ManifestLayer(
    id = id,
    name = name,
    isVisible = visible,
    transform = ManifestTransform(
        origin = ManifestPoint(transform.x, transform.y),
        size = ManifestSize(transform.width, transform.height),
        rotation = transform.rotation,
        flipX = transform.flipX,
        flipY = transform.flipY,
        sampling = transform.sampling.serialName,
    ),
    imageFile = imageFile,
    parentID = parentId,
    isGroup = kind == LayerKind.GROUP,
    opacity = opacity,
    blendMode = blendMode.serialName,
)

private fun ManifestLayer.toLayer() = Layer(
    id = id,
    name = name,
    kind = if (isGroup) LayerKind.GROUP else LayerKind.RASTER,
    visible = isVisible,
    opacity = opacity,
    blendMode = BlendMode.fromSerialName(blendMode),
    transform = Transform(
        x = transform.origin.x,
        y = transform.origin.y,
        width = transform.size.width,
        height = transform.size.height,
        rotation = transform.rotation,
        flipX = transform.flipX,
        flipY = transform.flipY,
        sampling = LayerSampling.fromSerialName(transform.sampling),
    ),
    parentId = parentID,
    imageFile = imageFile,
)

fun Document.toManifestJson(): String {
    val manifest = Manifest(
        documentID = id,
        width = width,
        height = height,
        activeLayerID = activeLayerId,
        layers = layers.map { it.toManifest() },
    )
    validateManifest(manifest)
    return manifestJson.encodeToString(Manifest.serializer(), manifest)
}

/**
 * Reads a manifest written by us or by the Mac app (v1–v11).
 * Rejects invalid metadata following upstream `ProjectStore.validate`,
 * restricted to the v1–v3 subset this port implements.
 */
fun documentFromManifestJson(text: String): Document {
    require(text.toByteArray().size <= MAX_MANIFEST_BYTES) { "manifest too large" }
    val header = try {
        manifestJson.decodeFromString(Manifest.serializer(), text)
    } catch (e: Exception) {
        throw IllegalArgumentException("not a Compositor project: ${e.message}")
    }
    validateManifest(header)
    return Document(
        id = header.documentID,
        width = header.width,
        height = header.height,
        activeLayerId = header.activeLayerID,
        layers = header.layers.map { it.toLayer() },
    )
}

private fun validateManifest(manifest: Manifest) {
    require(manifest.format == MANIFEST_FORMAT) {
        "not a Compositor project (format=${manifest.format})"
    }
    require(manifest.version in 1..MANIFEST_VERSION_MAX) {
        "unsupported manifest version ${manifest.version}"
    }
    require(manifest.colorSpace == MANIFEST_COLOR_SPACE) {
        "unsupported color space ${manifest.colorSpace}"
    }
    manifest.resolution?.let {
        require(it.isFinite() && it in 1.0..9600.0) { "invalid resolution $it" }
    }
    require(manifest.width in 1..Document.MAX_CANVAS_SIDE && manifest.height in 1..Document.MAX_CANVAS_SIDE) {
        "canvas dimensions out of range: ${manifest.width}x${manifest.height}"
    }
    require(manifest.layers.size <= Document.MAX_LAYERS) { "too many layers: ${manifest.layers.size}" }

    val ids = mutableSetOf<String>()
    for (layer in manifest.layers) {
        require(ids.add(layer.id)) { "duplicate layer id ${layer.id}" }
        require(layer.name.trim().isNotEmpty()) { "layer has blank name" }
        require(layer.name.toByteArray().size <= MAX_NAME_UTF8) { "layer name too long" }
        require(layer.transform.toTransform().isValid) { "invalid transform on layer ${layer.id}" }
        require(layer.imageFile == null || layer.imageFile == "${layer.id}.png") {
            "unsafe image path: ${layer.imageFile}"
        }
        require(layer.opacity.isFinite() && layer.opacity in 0.0..1.0) {
            "opacity out of range on layer ${layer.id}"
        }
        // Version gates (upstream ProjectStore.validate, v3-relevant subset).
        if (manifest.version < 3) {
            require(layer.opacity == 1.0 && layer.blendMode == BlendMode.NORMAL.serialName) {
                "appearance values require manifest v3+ (layer ${layer.id})"
            }
        }
        if (layer.isGroup) {
            require(layer.imageFile == null) { "group ${layer.id} cannot carry an image" }
            require(layer.blendMode == BlendMode.NORMAL.serialName) {
                "group ${layer.id} must use Normal blend mode"
            }
            if (manifest.version < 8) {
                require(layer.opacity == 1.0) { "group opacity requires manifest v8+ (layer ${layer.id})" }
            }
        }
        if (manifest.version == 1) {
            require(layer.parentID == null && !layer.isGroup) {
                "groups require manifest v2+ (layer ${layer.id})"
            }
        }
    }
    validateHierarchy(manifest.layers)
    manifest.activeLayerID?.let {
        require(it in ids) { "active layer $it does not exist" }
    }
}

private fun ManifestTransform.toTransform() = Transform(
    x = origin.x,
    y = origin.y,
    width = size.width,
    height = size.height,
    rotation = rotation,
    flipX = flipX,
    flipY = flipY,
    sampling = LayerSampling.fromSerialName(sampling),
)

/**
 * Upstream `LayerHierarchy.validate`: every parent must exist and be a group,
 * no cycles, at most 64 ancestor levels.
 */
private fun validateHierarchy(layers: List<ManifestLayer>) {
    val byId = layers.associateBy { it.id }
    for (layer in layers) {
        val seen = mutableSetOf(layer.id)
        var parent = layer.parentID
        while (parent != null) {
            require(seen.size <= 64 && seen.add(parent)) { "layer hierarchy cycle or too deep at ${layer.id}" }
            val node = byId[parent]
            require(node != null && node.isGroup) { "parent $parent of ${layer.id} is missing or not a group" }
            parent = node.parentID
        }
        if (layer.isGroup) {
            require(seen.size <= 64) { "group nesting too deep at ${layer.id}" }
        }
    }
}
