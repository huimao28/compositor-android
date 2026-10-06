package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RasterTest {

    private fun approx(expected: Double, actual: Double, eps: Double = 1e-9) {
        assertTrue("expected=$expected actual=$actual", abs(expected - actual) <= eps)
    }

    @Test
    fun `blend formulas match PDF 32000`() {
        val cb = 0.25
        val cs = 0.6
        approx(cs, BlendMode.NORMAL.blend(cb, cs))
        approx(cb * cs, BlendMode.MULTIPLY.blend(cb, cs))
        approx(1 - (1 - cb) * (1 - cs), BlendMode.SCREEN.blend(cb, cs))
        // cb <= 0.5 branch of overlay
        approx(2 * cb * cs, BlendMode.OVERLAY.blend(cb, cs))
        // cb > 0.5 branch
        approx(1 - 2 * (1 - 0.8) * (1 - cs), BlendMode.OVERLAY.blend(0.8, cs))
        approx(minOf(cb, cs), BlendMode.DARKEN.blend(cb, cs))
        approx(maxOf(cb, cs), BlendMode.LIGHTEN.blend(cb, cs))
        approx(cb / (1 - cs), BlendMode.COLOR_DODGE.blend(cb, cs))
        approx(1 - minOf(1.0, (1 - cb) / cs), BlendMode.COLOR_BURN.blend(cb, cs))
        approx(abs(cb - cs), BlendMode.DIFFERENCE.blend(cb, cs))
    }

    @Test
    fun `dodge and burn edge cases`() {
        // PDF: dodge of black stays black; burn of white stays white.
        assertEquals(0.0, BlendMode.COLOR_DODGE.blend(0.0, 0.9), 1e-12)
        assertEquals(1.0, BlendMode.COLOR_DODGE.blend(0.3, 1.0), 1e-12)
        assertEquals(1.0, BlendMode.COLOR_BURN.blend(1.0, 0.2), 1e-12)
        assertEquals(0.0, BlendMode.COLOR_BURN.blend(0.4, 0.0), 1e-12)
    }

    @Test
    fun `source over opaque replaces`() {
        val dst = argb(255, 10, 20, 30)
        val src = argb(255, 200, 100, 50)
        assertEquals(src, compositePixel(dst, src, BlendMode.NORMAL))
    }

    @Test
    fun `transparent source is identity`() {
        val dst = argb(255, 10, 20, 30)
        assertEquals(dst, compositePixel(dst, 0, BlendMode.MULTIPLY))
    }

    @Test
    fun `half alpha blends halfway`() {
        // 50% white over opaque black, normal mode.
        val out = compositePixel(argb(255, 0, 0, 0), argb(128, 255, 255, 255), BlendMode.NORMAL)
        assertEquals(255, alphaOf(out))
        // 128/255 ≈ 0.502 → rounds to 128.
        assertEquals(128, redOf(out))
        assertEquals(128, greenOf(out))
        assertEquals(128, blueOf(out))
    }

    @Test
    fun `multiply darkens correctly`() {
        // Opaque red over opaque green → black.
        val out = compositePixel(
            argb(255, 0, 255, 0),
            argb(255, 255, 0, 0),
            BlendMode.MULTIPLY,
        )
        assertEquals(argb(255, 0, 0, 0), out)
    }

    @Test
    fun `screen lightens correctly`() {
        // Opaque red over opaque green → yellow.
        val out = compositePixel(
            argb(255, 0, 255, 0),
            argb(255, 255, 0, 0),
            BlendMode.SCREEN,
        )
        assertEquals(argb(255, 255, 255, 0), out)
    }

    @Test
    fun `opacity scale folds into alpha`() {
        val out = compositePixel(
            argb(255, 0, 0, 0),
            argb(255, 255, 255, 255),
            BlendMode.NORMAL,
            0.5,
        )
        assertEquals(128, redOf(out))
    }
}
