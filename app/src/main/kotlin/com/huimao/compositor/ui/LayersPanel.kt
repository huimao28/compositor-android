package com.huimao.compositor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huimao.compositor.core.BlendMode
import com.huimao.compositor.core.Layer
import com.huimao.compositor.core.LayerKind

/** 图层面板：真实 Document 接线（增删/改名/排序/可见性/不透明度/混合模式）。 */
@Composable
fun LayersPanel(vm: EditorViewModel, modifier: Modifier = Modifier) {
    val doc = vm.session.document
    // Display top-first, like upstream.
    val display = remember(doc.layers) { doc.layers.reversed() }
    val depthOf = remember(doc.layers) {
        val byId = doc.layers.associateBy { it.id }
        fun depth(l: Layer): Int {
            var d = 0
            var p = l.parentId?.let { byId[it] }
            while (p != null) {
                d++
                p = p.parentId?.let { byId[it] }
            }
            return d
        }
        doc.layers.associate { it.id to depth(it) }
    }

    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "图层",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = vm::addLayer) {
                    Icon(Icons.Default.Add, contentDescription = "新建图层")
                }
                IconButton(onClick = vm::addGroup) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "新建分组")
                }
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(display, key = { it.id }) { layer ->
                    LayerRow(
                        layer = layer,
                        depth = depthOf[layer.id] ?: 0,
                        selected = doc.activeLayerId == layer.id,
                        onSelect = { vm.selectLayer(layer.id) },
                        onToggleVisible = { vm.setLayerVisible(layer.id, !layer.visible) },
                        expanded = {
                            LayerControls(vm = vm, layer = layer)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(
    layer: Layer,
    depth: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onToggleVisible: () -> Unit,
    expanded: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width((depth * 16).dp))
            if (layer.kind == LayerKind.GROUP) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = "分组",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onToggleVisible) {
                Icon(
                    if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (layer.visible) "隐藏" else "显示",
                    tint = if (layer.visible) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = layer.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (selected) {
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 16).dp, top = 2.dp, bottom = 4.dp),
            ) {
                expanded()
            }
        }
    }
}

@Composable
private fun LayerControls(vm: EditorViewModel, layer: Layer) {
    var blendOpen by remember { mutableStateOf(false) }
    var nameDraft by remember(layer.id, layer.name) { mutableStateOf(layer.name) }

    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { vm.renameLayer(layer.id, nameDraft) },
                enabled = nameDraft.isNotBlank() && nameDraft != layer.name,
            ) {
                Text("改名")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("不透明度", style = MaterialTheme.typography.labelMedium)
            val shown = vm.opacityPreview[layer.id] ?: layer.opacity.toFloat()
            Slider(
                value = shown,
                onValueChange = { vm.previewOpacity(layer.id, it) },
                onValueChangeFinished = { vm.commitOpacity(layer.id) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
            )
            Text("${(shown * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("混合模式", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { blendOpen = true }) {
                Text(layer.blendMode.serialName)
            }
            DropdownMenu(expanded = blendOpen, onDismissRequest = { blendOpen = false }) {
                BlendMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(mode.serialName) },
                        onClick = {
                            vm.setLayerBlendMode(layer.id, mode)
                            blendOpen = false
                        },
                    )
                }
            }
        }
        Row {
            IconButton(onClick = { vm.moveLayer(layer.id, towardTop = true) }) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "上移")
            }
            IconButton(onClick = { vm.moveLayer(layer.id, towardTop = false) }) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "下移")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.deleteLayer(layer.id) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除图层",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
