package com.astrofoto.app.capture

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalCamera2Interop::class, ExperimentalMaterial3Api::class)
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

    var manualFocusEnabled by remember { mutableStateOf(false) }
    var focusDistance by remember { mutableFloatStateOf(0f) } // 0 = infinito

    var intervalEnabled by remember { mutableStateOf(false) }
    var intervalSeconds by remember { mutableIntStateOf(5) }
    var isIntervalRunning by remember { mutableStateOf(false) }

    val iso = IsoValues[isoIndex]
    val (shutterLabel, shutterNanos) = ShutterSpeedsNanos[shutterIndex]
    val effectiveFocus = if (manualFocusEnabled) focusDistance else 0f

    LaunchedEffect(cameraReady, iso, shutterNanos, effectiveFocus) {
        if (cameraReady) {
            controller.applyManualSettings(iso, shutterNanos, effectiveFocus)
        }
    }

    DisposableEffectStop(controller)

    fun takeShot() {
        controller.capturePhoto(
            onSaved = { Toast.makeText(context, "Foto guardada", Toast.LENGTH_SHORT).show() },
            onError = { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captura manual", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    ShutterButton(
                        isIntervalMode = intervalEnabled,
                        isRunning = isIntervalRunning,
                        onClick = {
                            if (intervalEnabled) {
                                isIntervalRunning = !isIntervalRunning
                                if (isIntervalRunning) {
                                    scope.launch {
                                        while (isIntervalRunning) {
                                            takeShot()
                                            delay(intervalSeconds * 1000L)
                                        }
                                    }
                                }
                            } else {
                                takeShot()
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.medium)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingsCard(title = "Exposición") {
                    LabeledSlider(
                        label = "ISO",
                        valueText = "$iso",
                        value = isoIndex.toFloat(),
                        onValueChange = { isoIndex = it.toInt() },
                        valueRange = 0f..(IsoValues.size - 1).toFloat(),
                        steps = IsoValues.size - 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LabeledSlider(
                        label = "Velocidad",
                        valueText = shutterLabel,
                        value = shutterIndex.toFloat(),
                        onValueChange = { shutterIndex = it.toInt() },
                        valueRange = 0f..(ShutterSpeedsNanos.size - 1).toFloat(),
                        steps = ShutterSpeedsNanos.size - 2
                    )
                }

                SettingsCard(title = "Enfoque") {
                    ToggleRow(
                        label = if (manualFocusEnabled) "Manual" else "Automático (infinito)",
                        checked = manualFocusEnabled,
                        onCheckedChange = { manualFocusEnabled = it }
                    )
                    AnimatedVisibility(visible = manualFocusEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            LabeledSlider(
                                label = "Distancia",
                                valueText = "%.1f D".format(focusDistance),
                                value = focusDistance,
                                onValueChange = { focusDistance = it },
                                valueRange = 0f..10f
                            )
                        }
                    }
                }

                SettingsCard(title = "Intervalómetro", icon = Icons.Default.Timer) {
                    ToggleRow(
                        label = if (intervalEnabled) "Activado" else "Desactivado",
                        checked = intervalEnabled,
                        onCheckedChange = {
                            intervalEnabled = it
                            if (!it) isIntervalRunning = false
                        }
                    )
                    AnimatedVisibility(visible = intervalEnabled) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            LabeledSlider(
                                label = "Cada",
                                valueText = "$intervalSeconds s",
                                value = intervalSeconds.toFloat(),
                                onValueChange = { intervalSeconds = it.toInt() },
                                valueRange = 1f..60f,
                                steps = 58
                            )
                        }
                    }
                }

                // Espacio extra para que la última tarjeta no quede pegada a la barra inferior
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: ColumnScopeContent
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.let {
                    Icon(it, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

private typealias ColumnScopeContent = @Composable () -> Unit

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(valueText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps
    )
}

@Composable
private fun ShutterButton(isIntervalMode: Boolean, isRunning: Boolean, onClick: () -> Unit) {
    val color = if (isIntervalMode && isRunning) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .then(Modifier),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Capturar",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            when {
                isIntervalMode && isRunning -> "Detener"
                isIntervalMode -> "Iniciar"
                else -> "Capturar"
            },
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun DisposableEffectStop(controller: CaptureController) {
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.stopCamera() }
    }
}
