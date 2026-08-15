package dev.xichen.crossfitlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.xichen.crossfitlog.ui.CrossFitLogApp
import dev.xichen.crossfitlog.ui.theme.CrossFitLogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CrossFitLogTheme { CrossFitLogApp(application as CrossFitLogApplication) } }
    }
}
