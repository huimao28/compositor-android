package com.huimao.compositor.ui

import android.graphics.Bitmap
import com.huimao.compositor.core.RasterImage
import com.huimao.compositor.core.alphaOf
import com.huimao.compositor.core.blueOf
import com.huimao.compositor.core.greenOf
import com.huimao.compositor.core.redOf

/**
 * :core → Android 像素桥接。
 *
 * :core 的 [RasterImage] 用直通（non-premultiplied）alpha 做 PDF 规范混合；
 * Android 的 ARGB_8888 Bitmap 存预乘 alpha，显示前在此转换。
 */
fun RasterImage.toAndroidBitmap(): Bitmap {
    val out = IntArray(width * height)
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = alphaOf(p)
        out[i] = when (a) {
            0 -> 0
            255 -> p
            else -> {
                // Straight → premultiplied.
                val r = redOf(p) * a / 255
                val g = greenOf(p) * a / 255
                val b = blueOf(p) * a / 255
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
    return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
}
