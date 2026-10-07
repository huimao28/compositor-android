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
 * A pointer sample in layer pixels. [pressure] is 0..1 (stylus); touch
 * without pressure data reports 1.
 */
data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)

/**
 * Paints one stroke onto [base] and returns the new raster.
 * [points] is the pointer path in raster pixels; at least one point.
 */
@JvmName("applyStrokePairs")
fun applyStroke(base: RasterImage, points: List<Pair<Float, Float>>, tip: BrushTip): RasterImage =
    applyStroke(base, points.map { StrokePoint(it.first, it.second) }, tip)

/**
 * A single dab of the tip: center ([x], [y]) and [diameter] in layer pixels,
 * [alpha] is the paint alpha (tip color alpha, before the stroke opacity cap).
 */
data class Dab(val x: Float, val y: Float, val diameter: Float, val alpha: Float)

/** Dab spec at [p] (pressure scales the diameter). */
fun dabAt(p: StrokePoint, tip: BrushTip): Dab {
    val diameter = tip.diameter * (0.35f + 0.65f * p.pressure.coerceIn(0f, 1f))
    return Dab(p.x, p.y, diameter, alphaOf(tip.color) / 255f)
}

/**
 * Walks the dabs of segment [p0]→[p1], calling [emit] for each.
 * Returns the carry distance into the next segment (dab spacing phase).
 */
fun walkSegment(
    p0: StrokePoint,
    p1: StrokePoint,
    carry: Float,
    spacing: Float,
    emit: (StrokePoint) -> Unit,
): Float {
    val dist = hypot(p1.x - p0.x, p1.y - p0.y)
    var d = spacing - carry
    while (d < dist) {
        val t = d / dist
        emit(
            StrokePoint(
                p0.x + (p1.x - p0.x) * t,
                p0.y + (p1.y - p0.y) * t,
                p0.pressure + (p1.pressure - p0.pressure) * t,
            ),
        )
        d += spacing
    }
    return (dist - (d - spacing)).coerceAtLeast(0f)
}

/** Walks every dab center of the path (first point, then segments). */
fun walkDabs(points: List<StrokePoint>, tip: BrushTip, emit: (StrokePoint) -> Unit) {
    if (points.isEmpty()) return
    emit(points[0])
    val spacing = dabSpacing(tip).coerceAtLeast(0.5f)
    var carry = 0f
    for (i in 1 until points.size) {
        carry = walkSegment(points[i - 1], points[i], carry, spacing, emit)
    }
}

/** Dab specs for the whole path (layer pixels). */
fun strokeDabs(points: List<StrokePoint>, tip: BrushTip): List<Dab> {
    val dabs = mutableListOf<Dab>()
    walkDabs(points, tip) { dabs.add(dabAt(it, tip)) }
    return dabs
}

/**
 * Pressure-aware variant: the dab diameter scales with [StrokePoint.pressure]
 * (stylus), so light touches paint thinner dabs.
 */
fun applyStroke(base: RasterImage, points: List<StrokePoint>, tip: BrushTip): RasterImage {
    require(points.isNotEmpty()) { "stroke needs at least one point" }
    val out = base.copy()
    if (tip.opacity <= 0f || alphaOf(tip.color) == 0) return out

    // 1. Accumulate dabs into a transparent overlay …
    val overlay = RasterImage.transparent(base.width, base.height)
    walkDabs(points, tip) { stampDab(overlay, it, tip) }

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

private fun stampDab(dst: RasterImage, p: StrokePoint, tip: BrushTip) {
    val diameter = tip.diameter * (0.35f + 0.65f * p.pressure.coerceIn(0f, 1f))
    val radius = diameter / 2f
    val x0 = (p.x - radius).toInt()
    val x1 = (p.x + radius).toInt()
    val y0 = (p.y - radius).toInt()
    val y1 = (p.y + radius).toInt()
    val paintA = alphaOf(tip.color) / 255.0
    for (y in y0..y1) {
        for (x in x0..x1) {
            if (!dst.inBounds(x, y)) continue
            val d = hypot(x + 0.5f - p.x, y + 0.5f - p.y) / radius
            val coverage = dabCoverage(d, tip.hardness, diameter)
            if (coverage <= 0f) continue
            val dab = argb(
                (paintA * coverage * 255.0 + 0.5).toInt(),
                redOf(tip.color), greenOf(tip.color), blueOf(tip.color),
            )
            dst[x, y] = compositePixel(dst[x, y], dab, BlendMode.NORMAL)
        }
    }
}
