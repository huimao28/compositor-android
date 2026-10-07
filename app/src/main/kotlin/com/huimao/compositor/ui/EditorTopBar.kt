package com.huimao.compositor.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
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

/** 顶栏：文件操作 + 撤销重做 + 面板开关，全部接线到 ViewModel/launchers。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    vm: EditorViewModel,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onToggleLayers: () -> Unit,
) {
    TopAppBar(
        title = { Text(vm.docTitle) },
        navigationIcon = {
            IconButton(onClick = onOpen) {
                Icon(Icons.Default.FolderOpen, contentDescription = "打开 .comp")
            }
        },
        actions = {
            IconButton(onClick = onNew) {
                Icon(Icons.Default.Add, contentDescription = "新建")
            }
            IconButton(onClick = onSave) {
                Icon(Icons.Default.Save, contentDescription = "保存")
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "导入图片")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Download, contentDescription = "导出")
            }
            IconButton(onClick = vm::undo, enabled = vm.canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "撤销")
            }
            IconButton(onClick = vm::redo, enabled = vm.canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "重做")
            }
            IconButton(onClick = onToggleLayers) {
                Icon(Icons.Default.Layers, contentDescription = "图层面板")
            }
        },
    )
}
