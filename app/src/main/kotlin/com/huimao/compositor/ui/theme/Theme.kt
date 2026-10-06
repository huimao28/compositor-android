package com.huimao.compositor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/** 图像编辑器默认深色，避免干扰画布色彩判断。 */
private val DarkColors = darkColorScheme()

@Composable
fun CompositorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
