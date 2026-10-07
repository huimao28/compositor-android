package com.huimao.compositor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

/**
 * 画布：棋盘格透明背景 + 合成图；承载画笔/缩放/平移手势。
 * 视图变换（scale/offset）由 ViewModel 持有，手势回调里做 screen→document 映射。
 */
@Composable
fun CanvasArea(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val image by vm.display.collectAsStateWithLifecycle()
    var lastFitNonce by remember { mutableStateOf(-1) }

    Box(
        modifier = modifier.onSizeChanged { size ->
            if (size.width > 0 && lastFitNonce != vm.fitNonce) {
                lastFitNonce = vm.fitNonce
                vm.fitToView(size.width.toFloat(), size.height.toFloat())
            }
        },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .editorGestures(
                    brushActive = vm.tool == EditorTool.BRUSH,
                    onStrokeStart = { screen, pressure, stylus ->
                        vm.beginStroke(screen, pressure, stylus)
                    },
                    onStrokeMove = { screen, pressure, _ ->
                        vm.addStrokePoint(screen, pressure)
                    },
                    onStrokeEnd = { commit ->
                        vm.endStroke(commit)
                    },
                    onZoomPan = { centroid, pan, zoom ->
                        vm.applyZoomPan(centroid, pan, zoom)
                    },
                ),
        ) {
            drawCheckerboard()
            val bmp = image
            if (bmp != null) {
                val doc = vm.session.document
                drawImage(
                    image = bmp,
                    dstOffset = IntOffset(
                        vm.viewOffset.x.roundToInt(),
                        vm.viewOffset.y.roundToInt(),
                    ),
                    dstSize = IntSize(
                        (doc.width * vm.viewScale).roundToInt().coerceAtLeast(1),
                        (doc.height * vm.viewScale).roundToInt().coerceAtLeast(1),
                    ),
                )
            }
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
