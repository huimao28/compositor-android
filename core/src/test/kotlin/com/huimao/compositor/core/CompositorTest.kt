package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CompositorTest {

    private fun layerWith(
        id: String,
        raster: RasterImage,
        blend: BlendMode = BlendMode.NORMAL,
        opacity: Double = 1.0,
        visible: Boolean = true,
        transform: Transform = Transform(),
    ) = Pair(
        Layer(
            id = id, name = id, visible = visible,
            opacity = opacity, blendMode = blend, transform = transform,
        ),
        raster,
    )

    @Test
    fun `empty document composites to transparent`() {
        val doc = Document(width = 4, height = 4, layers = emptyList())
        val out = compositeDocument(doc, emptyMap())
        for (p in out.pixels) assertEquals(0, p)
    }

    @Test
    fun `single opaque layer fills canvas`() {
        val (layer, raster) = layerWith(
            "bg", RasterImage.filled(4, 4, argb(255, 10, 20, 30)),
            transform = Transform(x = 0.0, y = 0.0, width = 4.0, height = 4.0),
        )
        val doc = Document(width = 4, height = 4, layers = listOf(layer))
        val out = compositeDocument(doc, mapOf("bg" to raster))
        for (p in out.pixels) assertEquals(argb(255, 10, 20, 30), p)
    }

    @Test
    fun `layers stack bottom to top`() {
        val (bottom, rBottom) = layerWith(
            "bottom", RasterImage.filled(2, 2, argb(255, 255, 0, 0)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val (top, rTop) = layerWith(
            "top", RasterImage.filled(2, 2, argb(255, 0, 0, 255)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        // layers[0] is bottom in document order.
        val doc = Document(width = 2, height = 2, layers = listOf(bottom, top))
        val out = compositeDocument(doc, mapOf("bottom" to rBottom, "top" to rTop))
        for (p in out.pixels) assertEquals(argb(255, 0, 0, 255), p)
    }

    @Test
    fun `hidden layer is skipped`() {
        val (bottom, rBottom) = layerWith(
            "bottom", RasterImage.filled(2, 2, argb(255, 255, 0, 0)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val (top, rTop) = layerWith(
            "top", RasterImage.filled(2, 2, argb(255, 0, 0, 255)),
            visible = false,
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val doc = Document(width = 2, height = 2, layers = listOf(bottom, top))
        val out = compositeDocument(doc, mapOf("bottom" to rBottom, "top" to rTop))
        for (p in out.pixels) assertEquals(argb(255, 255, 0, 0), p)
    }

    @Test
    fun `layer opacity blends with backdrop`() {
        val (bottom, rBottom) = layerWith(
            "bottom", RasterImage.filled(2, 2, argb(255, 0, 0, 0)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val (top, rTop) = layerWith(
            "top", RasterImage.filled(2, 2, argb(255, 255, 255, 255)),
            opacity = 0.5,
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val doc = Document(width = 2, height = 2, layers = listOf(bottom, top))
        val out = compositeDocument(doc, mapOf("bottom" to rBottom, "top" to rTop))
        for (p in out.pixels) assertEquals(128, redOf(p))
    }

    @Test
    fun `blend mode applies during compositing`() {
        val (bottom, rBottom) = layerWith(
            "bottom", RasterImage.filled(2, 2, argb(255, 0, 255, 0)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val (top, rTop) = layerWith(
            "top", RasterImage.filled(2, 2, argb(255, 255, 0, 0)),
            blend = BlendMode.MULTIPLY,
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val doc = Document(width = 2, height = 2, layers = listOf(bottom, top))
        val out = compositeDocument(doc, mapOf("bottom" to rBottom, "top" to rTop))
        for (p in out.pixels) assertEquals(argb(255, 0, 0, 0), p)
    }

    @Test
    fun `translation places layer at origin`() {
        val raster = RasterImage.filled(2, 2, argb(255, 255, 0, 0))
        val (layer, _) = layerWith(
            "dot", raster,
            transform = Transform(x = 2.0, y = 1.0, width = 2.0, height = 2.0),
        )
        val doc = Document(width = 6, height = 4, layers = listOf(layer))
        val out = compositeDocument(doc, mapOf("dot" to raster))
        assertEquals(argb(255, 255, 0, 0), out[2, 1])
        assertEquals(argb(255, 255, 0, 0), out[3, 2])
        assertEquals(0, out[0, 0])
        assertEquals(0, out[5, 3])
    }

    @Test
    fun `scale down averages coverage`() {
        // 4x4 opaque white scaled to 2x2: every doc pixel fully covered.
        val raster = RasterImage.filled(4, 4, argb(255, 255, 255, 255))
        val (layer, _) = layerWith(
            "small", raster,
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val doc = Document(width = 4, height = 4, layers = listOf(layer))
        val out = compositeDocument(doc, mapOf("small" to raster))
        assertEquals(255, alphaOf(out[0, 0]))
        assertEquals(255, alphaOf(out[1, 1]))
        assertEquals(0, alphaOf(out[3, 3]))
    }

    @Test
    fun `group visibility hides children`() {
        val (child, rChild) = layerWith(
            "child", RasterImage.filled(2, 2, argb(255, 255, 0, 0)),
            transform = Transform(x = 0.0, y = 0.0, width = 2.0, height = 2.0),
        )
        val group = Layer(id = "g", name = "g", kind = LayerKind.GROUP, visible = false)
        val doc = Document(
            width = 2, height = 2,
            layers = listOf(group, child.copy(parentId = "g")),
        )
        val out = compositeDocument(doc, mapOf("child" to rChild))
        for (p in out.pixels) assertEquals(0, p)
    }

    @Test
    fun `toLayerPixels inverts the placement transform`() {
        val layer = Layer(
            id = "l", name = "l",
            transform = Transform(x = 30.0, y = 40.0, width = 200.0, height = 100.0, rotation = 30.0, flipX = true),
        )
        // Forward-map a layer pixel through the same math as drawLayer, then invert.
        val (lx, ly) = 25.0 to 60.0
        val w = 100
        val h = 80
        val t = layer.transform
        val radians = Math.toRadians(t.rotation)
        val cosR = kotlin.math.cos(radians)
        val sinR = kotlin.math.sin(radians)
        val cx = t.width / 2.0
        val cy = t.height / 2.0
        val fx = if (t.flipX) w - lx else lx
        val sx = fx * t.width / w
        val sy = ly * t.height / h
        val dx = cx + (sx - cx) * cosR - (sy - cy) * sinR + t.x
        val dy = cy + (sx - cx) * sinR + (sy - cy) * cosR + t.y
        val (ix, iy) = layer.toLayerPixels(dx, dy, w, h)!!
        org.junit.Assert.assertEquals(lx, ix, 1e-9)
        org.junit.Assert.assertEquals(ly, iy, 1e-9)
    }

    @Test
    fun `toLayerPixels rejects degenerate transforms`() {
        val bad = Layer(id = "b", name = "b", transform = Transform(x = 0.0, y = 0.0, width = 0.0, height = 10.0))
        org.junit.Assert.assertNull(bad.toLayerPixels(5.0, 5.0, 10, 10))
    }

    @Test
    fun `replacing equals full composite with swapped raster`() {
        val (bottom, rBottom) = layerWith(
            "bottom", RasterImage.filled(3, 3, argb(255, 0, 0, 255)),
            transform = Transform(x = 0.0, y = 0.0, width = 3.0, height = 3.0),
        )
        val (top, rTop) = layerWith(
            "top", RasterImage.filled(3, 3, argb(255, 255, 0, 0)),
            blend = BlendMode.MULTIPLY, opacity = 0.7,
            transform = Transform(x = 0.0, y = 0.0, width = 3.0, height = 3.0),
        )
        val doc = Document(width = 3, height = 3, layers = listOf(bottom, top))
        val rasters = mapOf("bottom" to rBottom, "top" to rTop)
        val replacement = RasterImage.filled(3, 3, argb(255, 0, 255, 0))
        val a = compositeDocumentReplacing(doc, rasters, "top", replacement)
        val b = compositeDocument(doc, rasters + ("top" to replacement))
        assertEquals(a, b)
    }
}
