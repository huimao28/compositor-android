package com.huimao.compositor.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTest {

    /** Layers need a valid transform to serialize (upstream `LayerTransform.isValid`). */
    private fun validLayer(
        name: String,
        id: String = newUuid(),
        kind: LayerKind = LayerKind.RASTER,
        visible: Boolean = true,
        opacity: Double = 1.0,
        blendMode: BlendMode = BlendMode.NORMAL,
        parentId: String? = null,
        imageFile: String? = null,
    ) = Layer(
        id = id,
        name = name,
        kind = kind,
        visible = visible,
        opacity = opacity,
        blendMode = blendMode,
        transform = Transform(x = 0.0, y = 0.0, width = 10.0, height = 10.0),
        parentId = parentId,
        imageFile = imageFile,
    )

    private val validTransformJson =
        """"transform":{"origin":{"x":0,"y":0},"size":{"width":10,"height":10},"rotation":0,"flipX":false,"flipY":false,"sampling":"High quality"}"""

    private fun layerJson(id: String, name: String, extra: String = "") =
        """{"id":"$id","name":"$name",$validTransformJson${if (extra.isEmpty()) "" else ",$extra"}}"""

    private fun docJson(version: Int = 3, layers: String, extra: String = "") =
        """{"format":"com.compositor.project","version":$version,"colorSpace":"sRGB","documentID":"x","width":10,"height":10${if (extra.isEmpty()) "" else ",$extra"},"layers":[$layers]}"""

    @Test
    fun `blend modes cover upstream v3 set`() {
        assertEquals(9, BlendMode.entries.size)
        assertEquals(BlendMode.MULTIPLY, BlendMode.fromSerialName("Multiply"))
        assertEquals(BlendMode.NORMAL, BlendMode.fromSerialName("Nope"))
    }

    @Test
    fun `manifest uses upstream field names`() {
        val layer = validLayer("x")
        val json = Document(
            width = 100,
            height = 100,
            activeLayerId = layer.id,
            layers = listOf(layer),
        ).toManifestJson()
        assertTrue(json.contains("\"format\""))
        assertTrue(json.contains("\"documentID\""))
        assertTrue(json.contains("\"activeLayerID\""))
        assertTrue(json.contains("\"colorSpace\""))
        assertTrue(json.contains("\"isVisible\""))
        assertTrue(json.contains("\"flipX\""))
        assertTrue(json.contains("\"flipY\""))
        assertTrue(json.contains("\"sampling\""))
        assertFalse(json.contains("documentUUID"))
        assertFalse(json.contains("flipHorizontal"))
    }

    @Test
    fun `manifest round trip preserves layers bottom-to-top`() {
        val doc = Document(
            width = 1920,
            height = 1080,
            layers = listOf(
                validLayer("背景"),
                validLayer("主体", opacity = 0.8, blendMode = BlendMode.MULTIPLY),
                validLayer("分组", kind = LayerKind.GROUP),
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
    fun `manifest round trip preserves transform`() {
        val doc = Document(
            width = 100,
            height = 100,
            layers = listOf(
                validLayer("t").copy(
                    transform = Transform(
                        x = 10.0, y = 20.0, width = 30.0, height = 40.0,
                        rotation = 45.0, flipX = true,
                        sampling = LayerSampling.NEAREST,
                    ),
                ),
            ),
        )
        val restored = documentFromManifestJson(doc.toManifestJson())
        val t = restored.layers[0].transform
        assertEquals(10.0, t.x, 0.0)
        assertEquals(20.0, t.y, 0.0)
        assertEquals(30.0, t.width, 0.0)
        assertEquals(40.0, t.height, 0.0)
        assertEquals(45.0, t.rotation, 0.0)
        assertTrue(t.flipX)
        assertFalse(t.flipY)
        assertEquals(LayerSampling.NEAREST, t.sampling)
    }

    @Test
    fun `manifest accepts v11 and ignores unknown fields`() {
        val json = """
            {"format":"com.compositor.project","version":11,"colorSpace":"sRGB",
             "documentID":"doc-1","width":100,"height":100,"guides":[],
             "layers":[{"id":"a","name":"x","isVisible":true,$validTransformJson,
                        "maskFile":"a.mask.png","adjustment":{"kind":"Levels"}}]}
        """.trimIndent()
        val doc = documentFromManifestJson(json)
        assertEquals(1, doc.layers.size)
        assertEquals("x", doc.layers[0].name)
    }

    @Test
    fun `manifest rejects wrong format`() {
        assertRejects(
            """{"format":"nope","version":3,"colorSpace":"sRGB","documentID":"x","width":10,"height":10}""",
            "not a Compositor project",
        )
    }

    @Test
    fun `manifest rejects unsupported version`() {
        assertRejects(
            """{"format":"com.compositor.project","version":12,"colorSpace":"sRGB","documentID":"x","width":10,"height":10}""",
            "unsupported manifest version",
        )
    }

    @Test
    fun `manifest rejects bad color space`() {
        assertRejects(
            """{"format":"com.compositor.project","version":3,"colorSpace":"Display P3","documentID":"x","width":10,"height":10}""",
            "unsupported color space",
        )
    }

    @Test
    fun `manifest rejects bad resolution`() {
        assertRejects(
            """{"format":"com.compositor.project","version":3,"colorSpace":"sRGB","resolution":99999,"documentID":"x","width":10,"height":10}""",
            "invalid resolution",
        )
    }

    @Test
    fun `manifest rejects oversize payload`() {
        assertRejects("x".repeat(5 * 1024 * 1024), "manifest too large")
    }

    @Test
    fun `manifest rejects too many layers`() {
        val layers = (1..10_001).joinToString(",") { layerJson("l$it", "n$it") }
        assertRejects(docJson(layers = layers), "too many layers")
    }

    @Test
    fun `manifest rejects duplicate layer ids`() {
        assertRejects(
            docJson(layers = "${layerJson("a", "x")},${layerJson("a", "y")}"),
            "duplicate layer id",
        )
    }

    @Test
    fun `manifest rejects blank layer name`() {
        assertRejects(docJson(layers = layerJson("a", "  ")), "blank name")
    }

    @Test
    fun `manifest rejects image file not matching layer id`() {
        val doc = Document(
            width = 100,
            height = 100,
            layers = listOf(validLayer("x", imageFile = "../evil.png")),
        )
        try {
            doc.toManifestJson()
            throw AssertionError("should have thrown")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("unsafe image path"))
        }
    }

    @Test
    fun `manifest rejects opacity out of range`() {
        assertRejects(docJson(layers = layerJson("a", "x", """"opacity":1.5""")), "opacity out of range")
    }

    @Test
    fun `manifest rejects v1 groups`() {
        assertRejects(
            docJson(version = 1, layers = layerJson("a", "g", """"isGroup":true""")),
            "groups require manifest v2+",
        )
    }

    @Test
    fun `manifest rejects v2 non-default opacity`() {
        assertRejects(
            docJson(version = 2, layers = layerJson("a", "x", """"opacity":0.5""")),
            "appearance values require manifest v3+",
        )
    }

    @Test
    fun `manifest rejects missing parent`() {
        assertRejects(
            docJson(layers = layerJson("a", "x", """"parentID":"ghost"""")),
            "missing or not a group",
        )
    }

    @Test
    fun `manifest rejects non-group parent`() {
        assertRejects(
            docJson(
                layers = "${layerJson("a", "x")},${layerJson("b", "y", """"parentID":"a"""")}",
            ),
            "missing or not a group",
        )
    }

    @Test
    fun `manifest rejects hierarchy cycle`() {
        assertRejects(
            docJson(
                layers = "${layerJson("a", "x", """"isGroup":true,"parentID":"b"""")}," +
                    layerJson("b", "y", """"isGroup":true,"parentID":"a""""),
            ),
            "cycle",
        )
    }

    @Test
    fun `manifest rejects group carrying image`() {
        assertRejects(
            docJson(layers = layerJson("a", "g", """"isGroup":true,"imageFile":"a.png"""")),
            "cannot carry an image",
        )
    }

    @Test
    fun `manifest rejects group with blend mode`() {
        assertRejects(
            docJson(layers = layerJson("a", "g", """"isGroup":true,"blendMode":"Multiply"""")),
            "must use Normal blend mode",
        )
    }

    @Test
    fun `manifest rejects dangling active layer`() {
        assertRejects(
            docJson(layers = layerJson("a", "x"), extra = """"activeLayerID":"ghost""""),
            "active layer",
        )
    }

    @Test
    fun `manifest rejects invalid transform`() {
        val badTransform = """"transform":{"origin":{"x":0,"y":0},"size":{"width":0,"height":10}}"""
        assertRejects(
            """{"format":"com.compositor.project","version":3,"colorSpace":"sRGB","documentID":"x","width":10,"height":10,
                "layers":[{"id":"a","name":"x",$badTransform}]}""",
            "invalid transform",
        )
    }

    @Test
    fun `visibility is inherited from groups`() {
        val group = validLayer("g", id = "g", kind = LayerKind.GROUP, visible = false)
        val child = validLayer("c", id = "c", parentId = "g", visible = true)
        val doc = Document(width = 100, height = 100, layers = listOf(group, child))

        assertFalse(child.isEffectivelyVisible(doc))
        assertFalse(group.isEffectivelyVisible(doc))
        assertTrue(doc.visibleLayers().isEmpty())

        val shown = doc.copy(layers = listOf(group.copy(visible = true), child))
        assertTrue(shown.layers[1].isEffectivelyVisible(shown))
        assertEquals(1, shown.visibleLayers().size)
    }

    @Test
    fun `group opacity multiplies into descendants`() {
        val group = validLayer("g", id = "g", kind = LayerKind.GROUP, opacity = 0.5)
        val child = validLayer("c", id = "c", parentId = "g", opacity = 0.5)
        val doc = Document(width = 100, height = 100, layers = listOf(group, child))

        assertEquals(0.25, child.effectiveOpacity(doc), 1e-9)
        assertEquals(0.5, group.effectiveOpacity(doc), 1e-9)
    }

    @Test
    fun `hierarchy entries walk groups depth-first`() {
        val group = validLayer("g", id = "g", kind = LayerKind.GROUP)
        val child = validLayer("c", id = "c", parentId = "g")
        val top = validLayer("t", id = "t")
        val doc = Document(width = 100, height = 100, layers = listOf(group, child, top))

        val entries = doc.hierarchyEntries()
        assertEquals(listOf("g", "c", "t"), entries.map { it.layer.id })
        assertEquals(listOf(0, 1, 0), entries.map { it.depth })
    }

    private fun assertRejects(json: String, fragment: String) {
        try {
            documentFromManifestJson(json)
            throw AssertionError("should have thrown (expected: $fragment)")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "message [${e.message}] should contain [$fragment]",
                e.message!!.contains(fragment),
            )
        }
    }
}
