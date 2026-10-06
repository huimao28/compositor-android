package com.huimao.compositor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class Tool(val name: String, val icon: ImageVector)

/** Phase 0 占位工具集；Phase 3 起按原版工具逐个实现。 */
private val TOOLS = listOf(
    Tool("移动", Icons.Default.OpenWith),
    Tool("选框", Icons.Default.Crop),
    Tool("画笔", Icons.Default.Brush),
    Tool("修复", Icons.Default.AutoFixHigh),
    Tool("文字", Icons.Default.TextFields),
    Tool("取色", Icons.Default.Colorize),
)

@Composable
fun ToolStrip() {
    var selected by remember { mutableStateOf(2) }
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TOOLS.forEachIndexed { index, tool ->
                IconButton(onClick = { selected = index }) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.name,
                        tint = if (index == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}
