package com.astrofoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.astrofoto.app.capture.CaptureScreen
import com.astrofoto.app.ui.theme.AstrofotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstrofotoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CaptureScreen()
                }
            }
        }
    }
}
