package com.astrofoto.app.capture

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalCamera2Interop::class)
@Composable
fun CaptureScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                "Se necesita permiso de cámara para continuar.",
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    val controller = remember { CaptureController(context) }
    var cameraReady by remember { mutableStateOf(false) }

    var isoIndex by remember { mutableIntStateOf(2) } // 200 ISO por defecto
    var shutterIndex by remember { mutableIntStateOf(12) } // 1" por defecto
    var focusDistance by remember { mutableFloatStateOf(0f) } // 0 = infinito

    var intervalEnabled by remember { mutableStateOf(false) }
    var intervalSeconds by remember { mutableIntStateOf(5) }
    var isIntervalRunning by remember { mutableStateOf(false) }

    val iso = IsoValues[isoIndex]
    val (shutterLabel, shutterNanos) = ShutterSpeedsNanos[shutterIndex]

    LaunchedEffect(cameraReady, iso, shutterNanos, focusDistance) {
        if (cameraReady) {
            controller.applyManualSettings(iso, shutterNanos, focusDistance)
        }
    }

    DisposableEffectStop(controller)

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        controller.startCamera(
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            onReady = { cameraReady = true },
                            onError = {
                                Toast.makeText(context, "Error al iniciar cámara: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("ISO: $iso", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = isoIndex.toFloat(),
                onValueChange = { isoIndex = it.toInt() },
                valueRange = 0f..(IsoValues.size - 1).toFloat(),
                steps = IsoValues.size - 2
            )

            Text("Exposición: $shutterLabel", style = MaterialTheme.typography.titleMedium)
            Slider(
                value = shutterIndex.toFloat(),
                onValueChange = { shutterIndex = it.toInt() },
                valueRange = 0f..(ShutterSpeedsNanos.size - 1).toFloat(),
                steps = ShutterSpeedsNanos.size - 2
            )

            Text(
                "Foco: ${if (focusDistance == 0f) "Infinito" else "%.1f D".format(focusDistance)}",
                style = MaterialTheme.typography.titleMedium
            )
            Slider(
                value = focusDistance,
                onValueChange = { focusDistance = it },
                valueRange = 0f..10f
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Intervalómetro", style = MaterialTheme.typography.titleMedium)
                Switch(checked = intervalEnabled, onCheckedChange = { intervalEnabled = it })
            }

            if (intervalEnabled) {
                Text("Cada $intervalSeconds s")
                Slider(
                    value = intervalSeconds.toFloat(),
                    onValueChange = { intervalSeconds = it.toInt() },
                    valueRange = 1f..60f,
                    steps = 58
                )
            }

            Button(
                onClick = {
                    if (intervalEnabled) {
                        isIntervalRunning = !isIntervalRunning
                        if (isIntervalRunning) {
                            scope.launch {
                                while (isIntervalRunning) {
                                    controller.capturePhoto(
                                        onSaved = {
                                            Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = {
                                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    delay(intervalSeconds * 1000L)
                                }
                            }
                        }
                    } else {
                        controller.capturePhoto(
                            onSaved = {
                                Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show()
                            },
                            onError = {
                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                val label = when {
                    intervalEnabled && isIntervalRunning -> "Detener intervalómetro"
                    intervalEnabled -> "Iniciar intervalómetro"
                    else -> "Capturar"
                }
                Text(label)
            }
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun DisposableEffectStop(controller: CaptureController) {
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.stopCamera() }
    }
}
