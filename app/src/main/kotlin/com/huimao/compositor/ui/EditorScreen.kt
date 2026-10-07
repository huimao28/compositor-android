package com.huimao.compositor.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 主编辑界面。抄桌面端布局：左工具条 | 中画布 | 右图层面板。
 * Pad/大屏横屏走 Expanded 三栏；小屏降级为画布 + 可收起面板。
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen() {
    val activity = LocalContext.current as Activity
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel()
    val windowSizeClass = calculateWindowSizeClass(activity)
    var layersVisible by remember { mutableStateOf(true) }
    var showNewDoc by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // vm.message → snackbar（一次性）。
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }

    fun takeTreePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // Some providers don't support persistable permissions; best effort.
        }
    }

    // 打开 .comp 目录
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        takeTreePermission(uri)
        scope.launch {
            try {
                val session = openComp(context, uri)
                val title = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                    ?: "已打开工程"
                vm.openSession(session, title, uri)
                vm.message = "已打开 $title"
            } catch (e: Exception) {
                vm.message = "打开失败：${e.message}"
            }
        }
    }
    // 保存：选目录（首次保存 / 另存）
    val saveTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        takeTreePermission(uri)
        scope.launch {
            try {
                saveComp(context, uri, vm.session)
                val title = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                    ?: "工程"
                vm.setCompDir(uri, title)
                vm.message = "已保存"
            } catch (e: Exception) {
                vm.message = "保存失败：${e.message}"
            }
        }
    }
    // 导入图片
    val importImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val (name, raster) = importImage(context, uri)
                vm.importImage(name, raster)
            } catch (e: Exception) {
                vm.message = "导入失败：${e.message}"
            }
        }
    }
    // 导出拼合图
    var exportJpeg by remember { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                exportFlattened(context, uri, vm.session, exportJpeg)
                vm.message = "已导出"
            } catch (e: Exception) {
                vm.message = "导出失败：${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                vm = vm,
                onNew = { showNewDoc = true },
                onOpen = { openTree.launch(null) },
                onSave = {
                    scope.launch {
                        if (!vm.saveToCurrentDir()) {
                            // 没有已选目录 → 让用户选一个。
                            saveTree.launch(null)
                        }
                    }
                },
                onImport = { importImagePicker.launch(arrayOf("image/*")) },
                onExport = { showExport = true },
                onToggleLayers = { layersVisible = !layersVisible },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    ToolStrip(vm)
                    CanvasArea(
                        vm = vm,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    if (layersVisible) {
                        LayersPanel(
                            vm = vm,
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
                        vm = vm,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                    if (layersVisible) {
                        LayersPanel(
                            vm = vm,
                            modifier = Modifier
                                .height(240.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showNewDoc) {
        NewDocumentDialog(
            onConfirm = { w, h ->
                vm.newDocument(w, h)
                showNewDoc = false
            },
            onDismiss = { showNewDoc = false },
        )
    }
    if (showExport) {
        AlertDialog(
            onDismissRequest = { showExport = false },
            title = { Text("导出拼合图") },
            text = { Text("选择导出格式") },
            confirmButton = {
                TextButton(onClick = {
                    showExport = false
                    exportJpeg = false
                    exportPicker.launch("export.png")
                }) { Text("PNG") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showExport = false
                    exportJpeg = true
                    exportPicker.launch("export.jpg")
                }) { Text("JPEG") }
            },
        )
    }
}

@Composable
private fun NewDocumentDialog(
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var width by remember { mutableStateOf("1920") }
    var height by remember { mutableStateOf("1080") }
    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val valid = w != null && h != null && w in 1..8000 && h in 1..8000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文档") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { width = it.filter(Char::isDigit).take(4) },
                    label = { Text("宽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = height,
                    onValueChange = { height = it.filter(Char::isDigit).take(4) },
                    label = { Text("高") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(w!!, h!!) }, enabled = valid) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
