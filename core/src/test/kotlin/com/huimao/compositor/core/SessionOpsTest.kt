package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionOpsTest {

    @Test
    fun `new session has one blank layer`() {
        val s = newPixelSession(8, 6)
        assertEquals(8, s.document.width)
        assertEquals(6, s.document.height)
        assertEquals(1, s.document.layers.size)
        val layer = s.document.layers[0]
        assertEquals(s.document.activeLayerId, layer.id)
        assertEquals(RasterImage.transparent(8, 6), s.rasters[layer.id])
    }

    @Test
    fun `add layer goes on top and becomes active`() {
        var s = newPixelSession(4, 4)
        val bottom = s.document.layers[0].id
        s = s.addRasterLayer("top", RasterImage.filled(4, 4, argb(255, 1, 2, 3)))
        assertEquals(2, s.document.layers.size)
        assertEquals(bottom, s.document.layers[0].id)
        val top = s.document.layers[1]
        assertEquals("top", top.name)
        assertEquals(s.document.activeLayerId, top.id)
        assertEquals(argb(255, 1, 2, 3), s.rasters[top.id]!![0, 0])
    }

    @Test
    fun `delete layer removes subtree and rasters`() {
        var s = newPixelSession(4, 4)
        s = s.addGroup("g")
        val group = s.document.layers.last()
        s = s.addRasterLayer("child", RasterImage.transparent(4, 4))
        val child = s.document.layers.last().copy(parentId = group.id)
        s = s.copy(document = s.document.copy(layers = s.document.layers.dropLast(1) + child))
        val childId = child.id

        s = s.deleteLayer(group.id)
        assertTrue(s.document.layers.none { it.id == group.id || it.id == childId })
        assertFalse(s.rasters.containsKey(childId))
        // The original bottom layer survives and becomes active.
        assertEquals(1, s.document.layers.size)
        assertEquals(s.document.layers[0].id, s.document.activeLayerId)
    }

    @Test
    fun `rename rejects blank names`() {
        val s = newPixelSession(4, 4)
        val id = s.document.layers[0].id
        assertEquals("bg", s.renameLayer(id, "bg").document.layers[0].name)
        try {
            s.renameLayer(id, "   ")
            throw AssertionError("expected failure")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `move layer reorders`() {
        var s = newPixelSession(4, 4, "a")
        s = s.addRasterLayer("b", RasterImage.transparent(4, 4))
        s = s.addRasterLayer("c", RasterImage.transparent(4, 4))
        val ids = s.document.layers.map { it.id }
        s = s.moveLayer(ids[0], 2)
        assertEquals(listOf(ids[1], ids[2], ids[0]), s.document.layers.map { it.id })
        s = s.moveLayer(ids[0], 0)
        assertEquals(listOf(ids[0], ids[1], ids[2]), s.document.layers.map { it.id })
    }

    @Test
    fun `visibility opacity and blend mode update`() {
        var s = newPixelSession(4, 4)
        val id = s.document.layers[0].id
        s = s.setLayerVisible(id, false)
        assertFalse(s.document.layers[0].visible)
        s = s.setLayerOpacity(id, 0.5)
        assertEquals(0.5, s.document.layers[0].opacity, 1e-12)
        s = s.setLayerBlendMode(id, BlendMode.MULTIPLY)
        assertEquals(BlendMode.MULTIPLY, s.document.layers[0].blendMode)
    }

    @Test
    fun `paint stroke changes raster and undo restores`() {
        var s = newPixelSession(20, 20)
        val id = s.document.layers[0].id
        val before = s.rasters[id]!!
        val history = PixelHistory(s)
        val tip = BrushTip(diameter = 8f, hardness = 1f, opacity = 1f, color = argb(255, 255, 0, 0))
        s = s.paintStroke(id, listOf(StrokePoint(10f, 10f)), tip)
        assertNotEquals(before, s.rasters[id])
        history.commit(s)
        assertTrue(history.undo())
        assertEquals(before, history.current.rasters[id])
    }

    @Test
    fun `paint stroke on missing layer is identity`() {
        val s = newPixelSession(4, 4)
        val tip = BrushTip(diameter = 8f, hardness = 1f, opacity = 1f, color = argb(255, 255, 0, 0))
        assertEquals(s, s.paintStroke("nope", listOf(StrokePoint(1f, 1f)), tip))
    }
}
