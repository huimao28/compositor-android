package com.huimao.compositor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** 画布占位：棋盘格背景。Phase 2 在此接入 :core 的合成渲染。 */
@Composable
fun CanvasArea(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
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
        Text(
            text = "画布 · Phase 2 接入渲染",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
