package com.astrofoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.astrofoto.app.capture.CaptureScreen
import com.astrofoto.app.gallery.GalleryScreen
import com.astrofoto.app.ui.theme.AstrofotoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstrofotoTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showGallery by remember { mutableStateOf(false) }
                    if (showGallery) {
                        GalleryScreen(onBack = { showGallery = false })
                    } else {
                        CaptureScreen(onOpenGallery = { showGallery = true })
                    }
                }
            }
        }
    }
}
