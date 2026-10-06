package com.huimao.compositor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * 画布：棋盘格透明背景 + :core 合成后的真实图像。
 * [image] 为 null 时只显示棋盘格。
 */
@Composable
fun CanvasArea(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCheckerboard()
            image?.let { drawFittedImage(it) }
        }
    }
}

private fun DrawScope.drawCheckerboard() {
    val cell = 56f
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = 0
        var x = 0f
        while (x < size.width) {
            if ((row + col) % 2 == 0) {
                drawRect(
                    color = Color(0xFF2B2B2B),
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
            }
            x += cell
            col++
        }
        y += cell
        row++
    }
}

/** 等比适配画布区域，居中绘制。 */
private fun DrawScope.drawFittedImage(image: ImageBitmap) {
    val scale = minOf(size.width / image.width, size.height / image.height)
    val dstW = image.width * scale
    val dstH = image.height * scale
    drawImage(
        image = image,
        dstOffset = androidx.compose.ui.unit.IntOffset(
            ((size.width - dstW) / 2).toInt(),
            ((size.height - dstH) / 2).toInt(),
        ),
        dstSize = androidx.compose.ui.unit.IntSize(dstW.toInt(), dstH.toInt()),
    )
}
