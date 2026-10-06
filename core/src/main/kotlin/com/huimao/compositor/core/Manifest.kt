package com.huimao.compositor.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * .comp manifest (de)serialization, upstream format v1–v3 subset.
 *
 * A .comp project is a folder containing manifest.json plus
 * images/<layer UUID>.png assets. Field names follow the upstream
 * spec (docs/project-format.md); transform subfield names should be
 * re-verified against ProjectStore.swift in Phase 1.
 */
const val MANIFEST_ID = "com.compositor.project"

/** New saves declare this version; versions 1..3 remain readable. */
const val MANIFEST_VERSION = 3

private const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024

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
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
)

@Serializable
data class ManifestLayer(
    val uuid: String,
    val name: String,
    val visible: Boolean = true,
    val transform: ManifestTransform = ManifestTransform(),
    val parentID: String? = null,
    val isGroup: Boolean = false,
    val opacity: Double = 1.0,
    val blendMode: String = "Normal",
    val imageFile: String? = null,
)

@Serializable
data class Manifest(
    val id: String = MANIFEST_ID,
    val version: Int = MANIFEST_VERSION,
    val documentUUID: String,
    val width: Int,
    val height: Int,
    val activeLayerUUID: String? = null,
    val layers: List<ManifestLayer> = emptyList(),
)

private val manifestJson = Json { prettyPrint = true; encodeDefaults = true }

private fun Layer.toManifest() = ManifestLayer(
    uuid = id,
    name = name,
    visible = visible,
    transform = ManifestTransform(
        origin = ManifestPoint(transform.x, transform.y),
        size = ManifestSize(transform.width, transform.height),
        rotation = transform.rotation,
        flipHorizontal = transform.flipHorizontal,
        flipVertical = transform.flipVertical,
    ),
    parentID = parentId,
    isGroup = kind == LayerKind.GROUP,
    opacity = opacity,
    blendMode = blendMode.serialName,
    imageFile = imageFile,
)

private fun ManifestLayer.toLayer() = Layer(
    id = uuid,
    name = name,
    kind = if (isGroup) LayerKind.GROUP else LayerKind.RASTER,
    visible = visible,
    opacity = opacity,
    blendMode = BlendMode.fromSerialName(blendMode),
    transform = Transform(
        x = transform.origin.x,
        y = transform.origin.y,
        width = transform.size.width,
        height = transform.size.height,
        rotation = transform.rotation,
        flipHorizontal = transform.flipHorizontal,
        flipVertical = transform.flipVertical,
    ),
    parentId = parentID,
    imageFile = imageFile,
)

fun Document.toManifestJson(): String {
    val manifest = Manifest(
        documentUUID = id,
        width = width,
        height = height,
        activeLayerUUID = activeLayerId,
        layers = layers.map { it.toManifest() },
    )
    return manifestJson.encodeToString(Manifest.serializer(), manifest)
}

private fun isSafeImagePath(path: String): Boolean =
    path.startsWith("images/") && !path.contains("..") && '/' !in path.removePrefix("images/")

/** Reads a manifest written by us or by the Mac app (v1–v3). Rejects invalid metadata. */
fun documentFromManifestJson(text: String): Document {
    require(text.toByteArray().size <= MAX_MANIFEST_BYTES) { "manifest too large" }
    val manifest = manifestJson.decodeFromString(Manifest.serializer(), text)
    require(manifest.id == MANIFEST_ID) { "not a Compositor project (id=${manifest.id})" }
    require(manifest.version in 1..MANIFEST_VERSION) {
        "unsupported manifest version ${manifest.version}"
    }
    manifest.layers.forEach { layer ->
        layer.imageFile?.let { path ->
            require(isSafeImagePath(path)) { "unsafe image path: $path" }
        }
        require(layer.opacity in 0.0..1.0) { "opacity out of range on layer ${layer.uuid}" }
    }
    return Document(
        id = manifest.documentUUID,
        width = manifest.width,
        height = manifest.height,
        activeLayerId = manifest.activeLayerUUID,
        layers = manifest.layers.map { it.toLayer() },
    )
}
