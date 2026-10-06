package com.huimao.compositor.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Brush engine.
 *
 * Behavioral contract (upstream docs/brush-performance.md):
 * - a continuous round tip sweeps the pointer path; dabs are stamped at
 *   2.5% of the tip diameter (soft) / 1.5% (hard), i.e. equivalent to
 *   source-over tips at that spacing;
 * - the opacity setting caps the *entire accumulated stroke*, not each dab;
 * - hard tips keep an antialiased silhouette;
 * - a stroke never mutates its input: it returns a new [RasterImage], so
 *   undo is a matter of restoring the pre-stroke raster (pixel-exact).
 */
data class BrushTip(
    /** Tip diameter in pixels. */
    val diameter: Float,
    /** 0 = soft … 1 = hard. */
    val hardness: Float,
    /** Caps the accumulated stroke, 0..1. */
    val opacity: Float,
    /** Paint color, straight-alpha ARGB. */
    val color: Int,
) {
    init {
        require(diameter > 0f) { "diameter must be > 0" }
        require(hardness in 0f..1f) { "hardness must be 0..1" }
        require(opacity in 0f..1f) { "opacity must be 0..1" }
    }
}

/** Dab spacing as a fraction of the tip diameter (upstream software fallback). */
fun dabSpacing(tip: BrushTip): Float =
    tip.diameter * (if (tip.hardness >= 1f) 0.015f else 0.025f)

/**
 * Radial dab coverage for normalized distance [d] (0 = center, 1 = tip edge).
 * Soft: cosine falloff outside the solid [hardness] core; hard: solid disc
 * with a one-pixel antialiased edge.
 */
fun dabCoverage(d: Float, hardness: Float, diameter: Float): Float {
    if (d >= 1f) return 0f
    if (hardness >= 1f) {
        val edge = 1f / (diameter / 2f).coerceAtLeast(1f)
        return ((1f - d) / edge).coerceIn(0f, 1f)
    }
    val inner = hardness.coerceIn(0f, 1f)
    if (d <= inner) return 1f
    val t = ((d - inner) / (1f - inner)).coerceIn(0f, 1f)
    return ((cos(t * PI) + 1.0) / 2.0).toFloat()
}

/**
 * Paints one stroke onto [base] and returns the new raster.
 * [points] is the pointer path in raster pixels; at least one point.
 */
fun applyStroke(base: RasterImage, points: List<Pair<Float, Float>>, tip: BrushTip): RasterImage {
    require(points.isNotEmpty()) { "stroke needs at least one point" }
    val out = base.copy()
    if (tip.opacity <= 0f || alphaOf(tip.color) == 0) return out

    // 1. Accumulate dabs into a transparent overlay …
    val overlay = RasterImage.transparent(base.width, base.height)
    val spacing = dabSpacing(tip).coerceAtLeast(0.5f)
    stampDab(overlay, points[0].first, points[0].second, tip)
    var carry = 0f
    for (i in 1 until points.size) {
        val (x0, y0) = points[i - 1]
        val (x1, y1) = points[i]
        val dist = hypot(x1 - x0, y1 - y0)
        var d = spacing - carry
        while (d < dist) {
            val t = d / dist
            stampDab(overlay, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, tip)
            d += spacing
        }
        carry = (dist - (d - spacing)).coerceAtLeast(0f)
    }

    // 2. … then lay the overlay down with the tip opacity capping the stroke.
    val opacity = tip.opacity.toDouble()
    for (y in 0 until base.height) {
        for (x in 0 until base.width) {
            val o = overlay[x, y]
            if (alphaOf(o) == 0) continue
            out[x, y] = compositePixel(out[x, y], o, BlendMode.NORMAL, opacity)
        }
    }
    return out
}

private fun stampDab(dst: RasterImage, cx: Float, cy: Float, tip: BrushTip) {
    val radius = tip.diameter / 2f
    val x0 = (cx - radius).toInt()
    val x1 = (cx + radius).toInt()
    val y0 = (cy - radius).toInt()
    val y1 = (cy + radius).toInt()
    val paintA = alphaOf(tip.color) / 255.0
    for (y in y0..y1) {
        for (x in x0..x1) {
            if (!dst.inBounds(x, y)) continue
            val d = hypot(x + 0.5f - cx, y + 0.5f - cy) / radius
            val coverage = dabCoverage(d, tip.hardness, tip.diameter)
            if (coverage <= 0f) continue
            val dab = argb(
                (paintA * coverage * 255.0 + 0.5).toInt(),
                redOf(tip.color), greenOf(tip.color), blueOf(tip.color),
            )
            dst[x, y] = compositePixel(dst[x, y], dab, BlendMode.NORMAL)
        }
    }
}
