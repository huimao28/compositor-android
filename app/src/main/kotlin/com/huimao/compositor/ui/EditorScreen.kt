package com.huimao.compositor.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 主编辑界面。抄桌面端布局：左工具条 | 中画布 | 右图层面板。
 * Pad/大屏横屏走 Expanded 三栏；小屏降级为画布 + 可收起面板。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val activity = LocalContext.current as Activity
    val windowSizeClass = calculateWindowSizeClass(activity)
    var layersVisible by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { EditorTopBar(onToggleLayers = { layersVisible = !layersVisible }) },
    ) { padding ->
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ToolStrip()
                    CanvasArea(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    if (layersVisible) {
                        LayersPanel(
                            modifier = Modifier
                                .width(300.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    CanvasArea(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    if (layersVisible) {
                        LayersPanel(
                            modifier = Modifier
                                .height(240.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
