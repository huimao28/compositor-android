package com.huimao.compositor.core

/**
 * A raster image: [width]×[height] pixels, row-major [pixels].
 *
 * Pixels are ARGB_8888 with **straight (non-premultiplied)** alpha, which is
 * what the PDF blend formulas are written in. The `:app` module converts to
 * Android's premultiplied `Bitmap` at the display boundary.
 */
class RasterImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "invalid raster size ${width}x$height" }
        require(pixels.size == width * height) { "pixel buffer size mismatch" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    operator fun set(x: Int, y: Int, argb: Int) {
        pixels[y * width + x] = argb
    }

    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height

    fun copy(): RasterImage = RasterImage(width, height, pixels.copyOf())

    override fun equals(other: Any?): Boolean =
        other is RasterImage && width == other.width && height == other.height &&
            pixels.contentEquals(other.pixels)

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()

    companion object {
        fun transparent(width: Int, height: Int) =
            RasterImage(width, height, IntArray(width * height))

        fun filled(width: Int, height: Int, argb: Int) =
            RasterImage(width, height, IntArray(width * height) { argb })
    }
}

/** Pack straight-alpha ARGB. Components in 0..255. */
fun argb(a: Int, r: Int, g: Int, b: Int): Int =
    (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or
        (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

fun alphaOf(argb: Int): Int = (argb ushr 24) and 0xff
fun redOf(argb: Int): Int = (argb ushr 16) and 0xff
fun greenOf(argb: Int): Int = (argb ushr 8) and 0xff
fun blueOf(argb: Int): Int = argb and 0xff

/**
 * Source-over composite of one straight-alpha pixel over another.
 * `srcAlphaScale` folds layer opacity into the source alpha.
 */
fun compositePixel(dst: Int, src: Int, mode: BlendMode, srcAlphaScale: Double = 1.0): Int {
    val sa = alphaOf(src) / 255.0 * srcAlphaScale
    if (sa <= 0.0) return dst
    val da = alphaOf(dst) / 255.0
    val outA = sa + da * (1.0 - sa)
    if (outA <= 0.0) return 0

    fun blendChannel(dc: Int, sc: Int): Double {
        val cb = dc / 255.0
        val cs = sc / 255.0
        // PDF basic compositing: αs·(1−αb)·Cs + αs·αb·B(Cb,Cs) + (1−αs)·αb·Cb, over αo.
        val blended = mode.blend(cb, cs)
        val co = sa * (1.0 - da) * cs + sa * da * blended + (1.0 - sa) * da * cb
        return co / outA
    }

    return argb(
        (outA * 255.0 + 0.5).toInt(),
        (blendChannel(redOf(dst), redOf(src)) * 255.0 + 0.5).toInt(),
        (blendChannel(greenOf(dst), greenOf(src)) * 255.0 + 0.5).toInt(),
        (blendChannel(blueOf(dst), blueOf(src)) * 255.0 + 0.5).toInt(),
    )
}
