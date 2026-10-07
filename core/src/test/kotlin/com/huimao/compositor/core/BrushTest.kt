package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushTest {

    private val red = BrushTip(
        diameter = 10f, hardness = 1f, opacity = 1f,
        color = argb(255, 255, 0, 0),
    )

    @Test
    fun `dab spacing follows upstream fallback`() {
        assertEquals(10f * 0.015f, dabSpacing(red), 1e-6f)
        val soft = red.copy(hardness = 0f)
        assertEquals(10f * 0.025f, dabSpacing(soft), 1e-6f)
    }

    @Test
    fun `hard tip paints solid disc`() {
        val base = RasterImage.transparent(30, 30)
        val out = applyStroke(base, listOf(15f to 15f), red)
        // Center fully painted.
        assertEquals(argb(255, 255, 0, 0), out[15, 15])
        // Far outside untouched.
        assertEquals(0, out[0, 0])
    }

    @Test
    fun `soft tip feathers the edge`() {
        val soft = red.copy(hardness = 0f)
        val base = RasterImage.transparent(30, 30)
        val out = applyStroke(base, listOf(15f to 15f), soft)
        val centerA = alphaOf(out[15, 15])
        val midA = alphaOf(out[18, 15])
        val edgeA = alphaOf(out[19, 15])
        assertTrue("center alpha $centerA should be strong", centerA > 200)
        assertTrue("mid alpha $midA should be partial", midA in 1 until centerA)
        assertTrue("edge alpha $edgeA should fade", edgeA < midA)
    }

    @Test
    fun `stroke does not mutate the base raster`() {
        val base = RasterImage.transparent(30, 30)
        val before = base.copy()
        applyStroke(base, listOf(5f to 5f, 25f to 25f), red)
        assertEquals(before, base)
    }

    @Test
    fun `opacity caps the accumulated stroke`() {
        val faint = red.copy(opacity = 0.5f)
        val base = RasterImage.transparent(40, 40)
        // Many overlapping dabs along a short path must not exceed the cap.
        val out = applyStroke(base, listOf(20f to 20f, 20.5f to 20f), faint)
        var maxA = 0
        for (p in out.pixels) maxA = maxOf(maxA, alphaOf(p))
        // Cap is 0.5 * 255 ≈ 128, allow ±2 for rounding.
        assertTrue("max alpha $maxA exceeds cap", maxA <= 130)
        assertTrue("stroke should deposit paint", maxA > 100)
    }

    @Test
    fun `zero opacity paints nothing`() {
        val base = RasterImage.transparent(20, 20)
        val out = applyStroke(base, listOf(10f to 10f), red.copy(opacity = 0f))
        assertEquals(base, out)
    }

    @Test
    fun `undo restores pixels exactly`() {
        val base = RasterImage.transparent(30, 30)
        val doc = Document(width = 30, height = 30, layers = listOf(Layer(id = "l", name = "l")))
        val session = PixelSession(doc, mapOf("l" to base))
        val history = PixelHistory(session)

        val stroked = applyStroke(base, listOf(5f to 5f, 25f to 25f), red)
        history.commit(PixelSession(doc, mapOf("l" to stroked)))
        assertTrue(history.current.rasters["l"] != base)

        assertTrue(history.undo())
        assertEquals("undo must restore every pixel", base, history.current.rasters["l"])

        assertTrue(history.redo())
        assertEquals(stroked, history.current.rasters["l"])
    }

    @Test
    fun `pressure thins the dab`() {
        val tip = BrushTip(diameter = 20f, hardness = 1f, opacity = 1f, color = argb(255, 255, 0, 0))
        val full = applyStroke(RasterImage.transparent(40, 40), listOf(StrokePoint(20f, 20f, 1f)), tip)
        val light = applyStroke(RasterImage.transparent(40, 40), listOf(StrokePoint(20f, 20f, 0.1f)), tip)
        fun paintedCount(r: RasterImage) = r.pixels.count { alphaOf(it) > 0 }
        assertTrue(paintedCount(light) < paintedCount(full))
        // Center still fully painted either way.
        assertEquals(argb(255, 255, 0, 0), full[20, 20])
        assertEquals(argb(255, 255, 0, 0), light[20, 20])
    }

    @Test
    fun `dab coverage is 1 at center and 0 outside`() {
        assertEquals(1f, dabCoverage(0f, 0f, 10f), 1e-6f)
        assertEquals(0f, dabCoverage(1f, 0f, 10f), 1e-6f)
        assertEquals(0f, dabCoverage(1.5f, 1f, 10f), 1e-6f)
    }

    @Test
    fun `walkDabs emits first point plus spaced dabs`() {
        val tip = BrushTip(diameter = 10f, hardness = 1f, opacity = 1f, color = argb(255, 255, 0, 0))
        // Hard tip: spacing = max(1.5% of 10, 0.5) = 0.5; 100px line -> first + 199 dabs.
        val dabs = strokeDabs(listOf(StrokePoint(0f, 0f), StrokePoint(100f, 0f)), tip)
        assertEquals(1 + 199, dabs.size)
        assertEquals(0f, dabs[0].x, 1e-6f)
        // Dab spacing is uniform after the first.
        val gaps = dabs.zipWithNext { a, b -> b.x - a.x }
        assertTrue(gaps.all { it > 0.49f && it < 0.51f })
    }

    @Test
    fun `walkSegment threads carry across segments`() {
        val tip = BrushTip(diameter = 10f, hardness = 1f, opacity = 1f, color = argb(255, 255, 0, 0))
        val spacing = (dabSpacing(tip).coerceAtLeast(0.5f))
        var carry = 0f
        val all = mutableListOf<StrokePoint>()
        val pts = listOf(StrokePoint(0f, 0f), StrokePoint(60f, 0f), StrokePoint(100f, 0f))
        all.add(pts[0])
        for (i in 1 until pts.size) {
            carry = walkSegment(pts[i - 1], pts[i], carry, spacing) { all.add(it) }
        }
        // Same dab positions as the one-shot walk.
        val direct = mutableListOf<StrokePoint>()
        walkDabs(pts, tip) { direct.add(it) }
        assertEquals(direct.map { it.x }, all.map { it.x })
    }
}
