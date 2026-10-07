package com.huimao.compositor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.huimao.compositor.core.BrushTip
import com.huimao.compositor.core.alphaOf
import com.huimao.compositor.core.blueOf
import com.huimao.compositor.core.greenOf
import com.huimao.compositor.core.redOf

private val PRESET_COLORS = listOf(
    Color(0xFF191919),
    Color(0xFFFFFFFF),
    Color(0xFFE6463C),
    Color(0xFFF5A623),
    Color(0xFF2ECC71),
    Color(0xFF3498DB),
    Color(0xFF9B59B6),
)

/** 左工具条：画笔/抓手切换；画笔参数走设置弹窗。 */
@Composable
fun ToolStrip(vm: EditorViewModel) {
    var showSettings by remember { mutableStateOf(false) }

    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ToolButton(
                selected = vm.tool == EditorTool.BRUSH,
                onClick = { vm.tool = EditorTool.BRUSH },
                icon = { Icon(Icons.Default.Brush, contentDescription = "画笔") },
            )
            ToolButton(
                selected = vm.tool == EditorTool.HAND,
                onClick = { vm.tool = EditorTool.HAND },
                icon = { Icon(Icons.Default.PanTool, contentDescription = "抓手") },
            )
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Tune, contentDescription = "笔刷设置")
            }
            // Current color dot.
            val c = vm.brushTip.color
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(redOf(c), greenOf(c), blueOf(c), alphaOf(c)))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { showSettings = true },
            )
        }
    }

    if (showSettings) {
        BrushSettingsDialog(
            tip = vm.brushTip,
            onTipChange = { vm.brushTip = it },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun ToolButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    IconButton(onClick = onClick) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                )
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@Composable
private fun BrushSettingsDialog(
    tip: BrushTip,
    onTipChange: (BrushTip) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("笔刷设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSlider("大小", tip.diameter, 1f, 200f) {
                    onTipChange(tip.copy(diameter = it))
                }
                SettingSlider("硬度", tip.hardness, 0f, 1f) {
                    onTipChange(tip.copy(hardness = it))
                }
                SettingSlider("不透明度", tip.opacity, 0.05f, 1f) {
                    onTipChange(tip.copy(opacity = it))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PRESET_COLORS.forEach { preset ->
                        val argb = (0xFF shl 24) or
                            (preset.red * 255).toInt().shl(16) or
                            (preset.green * 255).toInt().shl(8) or
                            (preset.blue * 255).toInt()
                        val selected = tip.color == argb
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(preset)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape,
                                )
                                .clickable { onTipChange(tip.copy(color = argb)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (max <= 1f) "${(value * 100).toInt()}%" else value.toInt().toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}
