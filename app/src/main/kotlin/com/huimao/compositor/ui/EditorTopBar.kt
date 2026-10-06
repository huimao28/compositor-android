package com.huimao.compositor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

/** 顶栏：文件操作 + 撤销重做 + 面板开关。按钮功能在 Phase 3 逐个接线。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(onToggleLayers: () -> Unit) {
    TopAppBar(
        title = { Text("Compositor") },
        navigationIcon = {
            IconButton(onClick = { /* Phase 3: 打开 .comp */ }) {
                Icon(Icons.Default.FolderOpen, contentDescription = "打开")
            }
        },
        actions = {
            IconButton(onClick = { /* Phase 3: 撤销 */ }) {
                Icon(Icons.Default.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = { /* Phase 3: 重做 */ }) {
                Icon(Icons.Default.Redo, contentDescription = "重做")
            }
            IconButton(onClick = { /* Phase 3: 保存 */ }) {
                Icon(Icons.Default.Save, contentDescription = "保存")
            }
            IconButton(onClick = { /* Phase 3: 导出 */ }) {
                Icon(Icons.Default.Download, contentDescription = "导出")
            }
            IconButton(onClick = onToggleLayers) {
                Icon(Icons.Default.Layers, contentDescription = "图层面板")
            }
        },
    )
}
