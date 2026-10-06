package com.huimao.compositor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.huimao.compositor.ui.EditorScreen
import com.huimao.compositor.ui.theme.CompositorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CompositorTheme {
                EditorScreen()
            }
        }
    }
}
