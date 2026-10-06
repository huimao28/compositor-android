package com.huimao.compositor.ui

import com.huimao.compositor.core.BlendMode
import com.huimao.compositor.core.BrushTip
import com.huimao.compositor.core.Document
import com.huimao.compositor.core.Layer
import com.huimao.compositor.core.RasterImage
import com.huimao.compositor.core.Transform
import com.huimao.compositor.core.applyStroke
import com.huimao.compositor.core.argb
import com.huimao.compositor.core.compositeDocument
import kotlin.math.hypot

/**
 * Phase 2 演示文档：用 :core 的像素引擎真实合成。
 *
 * - 背景层：垂直渐变
 * - 笔触层：软笔刷画的一笔（画笔引擎）
 * - 正片叠底层：绿色圆，MULTIPLY × 0.8
 * - 旋转方块层：黄色方块旋转 30°，SCREEN
 *
 * Phase 3 起由真实文档状态替代。
 */
fun buildDemoComposite(): RasterImage {
    val w = 480
    val h = 320

    val bg = RasterImage(w, h, IntArray(w * h) { i ->
        val y = i / w
        val t = y / h.toFloat()
        argb(255, (30 + 40 * t).toInt(), (60 + 60 * t).toInt(), (120 + 80 * t).toInt())
    })

    val strokeBase = RasterImage.transparent(w, h)
    val points = (0..60).map { i ->
        val t = i / 60f
        (40f + t * 400f) to (160f + 90f * kotlin.math.sin(t * Math.PI * 2).toFloat())
    }
    val stroke = applyStroke(
        strokeBase, points,
        BrushTip(diameter = 46f, hardness = 0.25f, opacity = 0.9f, color = argb(255, 230, 70, 60)),
    )

    val circle = RasterImage.transparent(w, h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val d = hypot(x - 340f, y - 110f)
            if (d < 90f) {
                val edge = ((90f - d) / 12f).coerceIn(0f, 1f)
                circle[x, y] = argb((220 * edge).toInt(), 80, 220, 120)
            }
        }
    }

    val square = RasterImage.filled(120, 120, argb(255, 240, 200, 60))

    fun layer(id: String, blend: BlendMode, opacity: Double, tx: Transform) =
        Layer(id = id, name = id, blendMode = blend, opacity = opacity, transform = tx)

    val full = Transform(x = 0.0, y = 0.0, width = w.toDouble(), height = h.toDouble())
    val layers = listOf(
        layer("bg", BlendMode.NORMAL, 1.0, full),
        layer("stroke", BlendMode.NORMAL, 1.0, full),
        layer("circle", BlendMode.MULTIPLY, 0.8, full),
        layer("square", BlendMode.SCREEN, 0.85, Transform(x = 60.0, y = 170.0, width = 120.0, height = 120.0, rotation = 30.0)),
    )
    val doc = Document(width = w, height = h, layers = layers)
    return compositeDocument(
        doc,
        mapOf("bg" to bg, "stroke" to stroke, "circle" to circle, "square" to square),
    )
}
