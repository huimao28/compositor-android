package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTest {

    @Test
    fun `blend modes cover upstream v3 set`() {
        assertEquals(9, BlendMode.entries.size)
        assertEquals(BlendMode.MULTIPLY, BlendMode.fromSerialName("Multiply"))
        assertEquals(BlendMode.NORMAL, BlendMode.fromSerialName("Nope"))
    }

    @Test
    fun `manifest round trip preserves layers bottom-to-top`() {
        val doc = Document(
            width = 1920,
            height = 1080,
            layers = listOf(
                Layer(name = "背景"),
                Layer(name = "主体", opacity = 0.8, blendMode = BlendMode.MULTIPLY),
                Layer(name = "分组", kind = LayerKind.GROUP),
            ),
        )
        val restored = documentFromManifestJson(doc.toManifestJson())

        assertEquals(3, restored.layers.size)
        assertEquals("背景", restored.layers[0].name)
        assertEquals("主体", restored.layers[1].name)
        assertEquals(0.8, restored.layers[1].opacity, 0.0)
        assertEquals(BlendMode.MULTIPLY, restored.layers[1].blendMode)
        assertEquals(LayerKind.GROUP, restored.layers[2].kind)
    }

    @Test
    fun `manifest rejects unsafe image paths`() {
        val json = Document(
            width = 100,
            height = 100,
            layers = listOf(Layer(name = "x", imageFile = "../evil.png")),
        ).toManifestJson()
        try {
            documentFromManifestJson(json)
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unsafe"))
        }
    }

    @Test
    fun `manifest rejects wrong project id`() {
        try {
            documentFromManifestJson("""{"id":"nope","version":3,"documentUUID":"x","width":10,"height":10}""")
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("not a Compositor project"))
        }
    }
}
