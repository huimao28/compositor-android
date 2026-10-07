package com.huimao.compositor.core

import kotlin.math.cos
import kotlin.math.sin

/**
 * Document compositing: bottom-to-top over [Document.visibleLayers],
 * honoring per-layer blend mode, effective opacity and transform placement.
 *
 * Placement maps layer pixels into document space through the layer's
 * [Transform] (flips, scale to [Transform.width]/[Transform.height], clockwise
 * rotation about the center, then [Transform.x]/[Transform.y] translation).
 * Sampling is backward-mapped bilinear on premultiplied channels.
 *
 * @param rasters layer id → raster. Layers without a raster are skipped.
 */
fun compositeDocument(
    document: Document,
    rasters: Map<String, RasterImage>,
): RasterImage {
    val out = RasterImage.transparent(document.width, document.height)
    for (layer in document.visibleLayers()) {
        val raster = rasters[layer.id] ?: continue
        compositeLayerOver(out, document, layer, raster)
    }
    return out
}

/**
 * Draws one layer over [dst] (mutated in place). Public so the `:app` shell
 * can composite a live stroke preview over a cached base image without
 * re-compositing the whole document per pointer move.
 */
fun compositeLayerOver(
    dst: RasterImage,
    document: Document,
    layer: Layer,
    raster: RasterImage,
) {
    drawLayer(dst, layer, raster, document)
}

private fun drawLayer(
    dst: RasterImage,
    layer: Layer,
    src: RasterImage,
    document: Document,
) {
    val t = layer.transform
    val opacity = layer.effectiveOpacity(document)
    if (opacity <= 0.0) return
    val mode = layer.blendMode

    // Layer pixel (lx, ly) -> document (dx, dy).
    val scaleX = if (src.width > 0) t.width / src.width else 0.0
    val scaleY = if (src.height > 0) t.height / src.height else 0.0
    if (scaleX <= 0.0 || scaleY <= 0.0) return
    val radians = Math.toRadians(t.rotation)
    val cosR = cos(radians)
    val sinR = sin(radians)
    val cx = t.width / 2.0
    val cy = t.height / 2.0

    // Document bounds that the layer can touch (for the scan loop).
    // Conservative: the rotated box's axis-aligned bounds.
    val corners = arrayOf(
        doubleArrayOf(0.0, 0.0), doubleArrayOf(t.width, 0.0),
        doubleArrayOf(0.0, t.height), doubleArrayOf(t.width, t.height),
    )
    var minDx = Double.POSITIVE_INFINITY
    var maxDx = Double.NEGATIVE_INFINITY
    var minDy = Double.POSITIVE_INFINITY
    var maxDy = Double.NEGATIVE_INFINITY
    for ((px, py) in corners) {
        // forward map of the unrotated box corner, then rotate+translate
        val rx = cx + (px - cx) * cosR - (py - cy) * sinR + t.x
        val ry = cy + (px - cx) * sinR + (py - cy) * cosR + t.y
        minDx = minOf(minDx, rx); maxDx = maxOf(maxDx, rx)
        minDy = minOf(minDy, ry); maxDy = maxOf(maxDy, ry)
    }
    val x0 = maxOf(0, minDx.toInt())
    val x1 = minOf(dst.width - 1, (maxDx + 1).toInt())
    val y0 = maxOf(0, minDy.toInt())
    val y1 = minOf(dst.height - 1, (maxDy + 1).toInt())

    for (dy in y0..y1) {
        for (dx in x0..x1) {
            // Document -> layer (inverse of the forward map), pixel centers.
            val px = dx + 0.5 - t.x
            val py = dy + 0.5 - t.y
            val ux = cx + (px - cx) * cosR + (py - cy) * sinR
            val uy = cy - (px - cx) * sinR + (py - cy) * cosR
            var lx = ux / scaleX
            var ly = uy / scaleY
            if (t.flipX) lx = src.width - lx
            if (t.flipY) ly = src.height - ly
            // Bilinear taps pixel corners; shift so (0,0) is the first pixel's center.
            val argb = bilinear(src, (lx - 0.5).toFloat(), (ly - 0.5).toFloat())
            if (alphaOf(argb) == 0) continue
            dst[dx, dy] = compositePixel(dst[dx, dy], argb, mode, opacity)
        }
    }
}

/** Bilinear sample of [src] at continuous coords (pixel centers at integers). */
private fun bilinear(src: RasterImage, lx: Float, ly: Float): Int {
    val x0 = kotlin.math.floor(lx).toInt()
    val y0 = kotlin.math.floor(ly).toInt()
    val fx = (lx - x0).coerceIn(0f, 1f)
    val fy = (ly - y0).coerceIn(0f, 1f)

    // Premultiplied accumulation, then back to straight.
    var a = 0f
    var r = 0f
    var g = 0f
    var b = 0f
    val weights = floatArrayOf((1 - fx) * (1 - fy), fx * (1 - fy), (1 - fx) * fy, fx * fy)
    val xs = intArrayOf(x0, x0 + 1, x0, x0 + 1)
    val ys = intArrayOf(y0, y0, y0 + 1, y0 + 1)
    for (i in 0..3) {
        val p = if (src.inBounds(xs[i], ys[i])) src[xs[i], ys[i]] else 0
        val pa = alphaOf(p) / 255f
        val w = weights[i]
        a += pa * w
        r += redOf(p) / 255f * pa * w
        g += greenOf(p) / 255f * pa * w
        b += blueOf(p) / 255f * pa * w
    }
    if (a <= 0f) return 0
    return argb(
        (a * 255f + 0.5f).toInt(),
        (r / a * 255f + 0.5f).toInt(),
        (g / a * 255f + 0.5f).toInt(),
        (b / a * 255f + 0.5f).toInt(),
    )
}

/**
 * Inverse of the placement transform: document pixels → layer raster pixels.
 * Used to route pointer input to the layer being painted. Null when the
 * transform is degenerate (zero scale).
 */
fun Layer.toLayerPixels(docX: Double, docY: Double, rasterWidth: Int, rasterHeight: Int): Pair<Double, Double>? {
    val t = transform
    if (rasterWidth <= 0 || rasterHeight <= 0) return null
    if (t.width <= 0.0 || t.height <= 0.0) return null
    val radians = Math.toRadians(t.rotation)
    val cosR = cos(radians)
    val sinR = sin(radians)
    val cx = t.width / 2.0
    val cy = t.height / 2.0
    // Untranslate, unrotate (counterclockwise), unscale, unflip.
    val px = docX - t.x
    val py = docY - t.y
    val ux = cx + (px - cx) * cosR + (py - cy) * sinR
    val uy = cy - (px - cx) * sinR + (py - cy) * cosR
    var lx = ux * rasterWidth / t.width
    var ly = uy * rasterHeight / t.height
    if (t.flipX) lx = rasterWidth - lx
    if (t.flipY) ly = rasterHeight - ly
    return lx to ly
}

/**
 * Like [compositeDocument], but [layerId]'s raster is replaced by [replacement]
 * (e.g. a live stroke preview). Exact: the replacement goes through the same
 * blend/opacity/transform path as a committed raster.
 */
fun compositeDocumentReplacing(
    document: Document,
    rasters: Map<String, RasterImage>,
    layerId: String,
    replacement: RasterImage,
): RasterImage {
    val out = RasterImage.transparent(document.width, document.height)
    for (layer in document.visibleLayers()) {
        val raster = if (layer.id == layerId) replacement else rasters[layer.id] ?: continue
        compositeLayerOver(out, document, layer, raster)
    }
    return out
}
