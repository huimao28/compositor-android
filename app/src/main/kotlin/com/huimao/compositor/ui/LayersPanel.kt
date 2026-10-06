package com.huimao.compositor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huimao.compositor.core.Layer

/** 图层面板。Phase 0 用内存示例数据；Phase 3 接入 :core 的 Document。 */
@Composable
fun LayersPanel(modifier: Modifier = Modifier) {
    var layers by remember {
        mutableStateOf(
            listOf(
                Layer(name = "背景"),
                Layer(name = "图层 1"),
                Layer(name = "图层 2", visible = false),
            ),
        )
    }
    Surface(modifier = modifier, tonalElevation = 2.dp) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "图层",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    layers = layers + Layer(name = "图层 ${layers.size + 1}")
                }) {
                    Icon(Icons.Default.Add, contentDescription = "添加图层")
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            // 上游 manifest 是 bottom-to-top 顺序，面板反序展示
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(layers.reversed(), key = { it.id }) { layer ->
                    LayerRow(
                        layer = layer,
                        onToggleVisible = {
                            layers = layers.map {
                                if (it.id == layer.id) it.copy(visible = !it.visible) else it
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerRow(layer: Layer, onToggleVisible: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleVisible, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (layer.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = "可见性",
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(layer.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = layer.blendMode.serialName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
