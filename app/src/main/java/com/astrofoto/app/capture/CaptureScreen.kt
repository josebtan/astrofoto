package com.astrofoto.app.capture

import android.Manifest
import android.content.pm.PackageManager
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SettingsTab(val label: String) {
    EXPOSURE("Exposición"),
    FOCUS("Enfoque"),
    INTERVAL("Intervalómetro"),
    CALIBRATION("Calibración")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(onOpenGallery: () -> Unit = {}) {
    val context = LocalContext.current
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
            Text("Se necesita permiso de cámara para continuar.", modifier = Modifier.padding(24.dp))
        }
        return
    }

    val controller = remember { CaptureController(context) }
    var cameraReady by remember { mutableStateOf(false) }
    var rawSupported by remember { mutableStateOf(true) }

    var isoIndex by remember { mutableIntStateOf(2) } // 200 ISO por defecto
    var shutterIndex by remember { mutableIntStateOf(12) } // 1" por defecto

    var manualFocusEnabled by remember { mutableStateOf(false) }
    var focusDistance by remember { mutableFloatStateOf(0f) } // 0 = infinito

    var intervalEnabled by remember { mutableStateOf(false) }
    var intervalSeconds by remember { mutableIntStateOf(5) }
    var isIntervalRunning by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }

    val iso = IsoValues[isoIndex]
    val (shutterLabel, shutterNanos) = ShutterSpeedsNanos[shutterIndex]
    val effectiveFocus = if (manualFocusEnabled) focusDistance else 0f

    LaunchedEffect(cameraReady, iso, shutterNanos, effectiveFocus) {
        if (cameraReady) controller.applyManualSettings(iso, shutterNanos, effectiveFocus)
    }

    DisposableEffectStop(controller)

    fun takeShot(frameType: FrameType = FrameType.LIGHT, label: String = "RAW guardado") {
        controller.capturePhoto(
            frameType = frameType,
            onSaved = { Toast.makeText(context, label, Toast.LENGTH_SHORT).show() },
            onError = { Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_SHORT).show() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Captura manual", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenGallery) {
                        Icon(Icons.Default.Collections, contentDescription = "Galería")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).also { textureView ->
                            controller.startCamera(
                                textureView = textureView,
                                onReady = {
                                    cameraReady = true
                                    rawSupported = controller.isRawSupported
                                },
                                onError = {
                                    Toast.makeText(context, "Error al iniciar cámara: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
                )
            }

            if (cameraReady && !rawSupported) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Text(
                        "Esta cámara no soporta captura RAW (DNG). No vas a poder capturar en este dispositivo.",
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(tab.label) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (SettingsTab.entries[selectedTab]) {
                    SettingsTab.EXPOSURE -> ExposureTab(
                        iso = iso,
                        isoIndex = isoIndex,
                        onIsoIndexChange = { isoIndex = it },
                        shutterLabel = shutterLabel,
                        shutterIndex = shutterIndex,
                        onShutterIndexChange = { shutterIndex = it }
                    )

                    SettingsTab.FOCUS -> FocusTab(
                        manualEnabled = manualFocusEnabled,
                        onManualEnabledChange = { manualFocusEnabled = it },
                        focusDistance = focusDistance,
                        onFocusDistanceChange = { focusDistance = it }
                    )

                    SettingsTab.INTERVAL -> IntervalTab(
                        enabled = intervalEnabled,
                        onEnabledChange = {
                            intervalEnabled = it
                            if (!it) isIntervalRunning = false
                        },
                        seconds = intervalSeconds,
                        onSecondsChange = { intervalSeconds = it }
                    )

                    SettingsTab.CALIBRATION -> CalibrationTab(
                        onCaptureDark = { takeShot(FrameType.DARK, "Dark frame guardado") },
                        onCaptureFlat = { takeShot(FrameType.FLAT, "Flat frame guardado") },
                        onCaptureBias = { takeShot(FrameType.BIAS, "Bias frame guardado") }
                    )
                }
            }
        }
    }
}

@Composable
private fun BigValue(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun ExposureTab(
    iso: Int,
    isoIndex: Int,
    onIsoIndexChange: (Int) -> Unit,
    shutterLabel: String,
    shutterIndex: Int,
    onShutterIndexChange: (Int) -> Unit
) {
    BigValue("ISO", "$iso")
    Slider(
        value = isoIndex.toFloat(),
        onValueChange = { onIsoIndexChange(it.toInt()) },
        valueRange = 0f..(IsoValues.size - 1).toFloat(),
        steps = IsoValues.size - 2
    )

    Spacer(modifier = Modifier.height(8.dp))

    BigValue("Velocidad de obturación", shutterLabel)
    Slider(
        value = shutterIndex.toFloat(),
        onValueChange = { onShutterIndexChange(it.toInt()) },
        valueRange = 0f..(ShutterSpeedsNanos.size - 1).toFloat(),
        steps = ShutterSpeedsNanos.size - 2
    )
}

@Composable
private fun FocusTab(
    manualEnabled: Boolean,
    onManualEnabledChange: (Boolean) -> Unit,
    focusDistance: Float,
    onFocusDistanceChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BigValue("Enfoque", if (manualEnabled) "Manual" else "Automático (infinito)")
        Switch(checked = manualEnabled, onCheckedChange = onManualEnabledChange)
    }

    if (manualEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Distancia: ${"%.1f".format(focusDistance)} D",
            style = MaterialTheme.typography.titleMedium
        )
        Slider(value = focusDistance, onValueChange = onFocusDistanceChange, valueRange = 0f..10f)
    } else {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "En infinito, ideal para estrellas y objetos del cielo profundo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun IntervalTab(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    seconds: Int,
    onSecondsChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BigValue("Intervalómetro", if (enabled) "Activado" else "Desactivado")
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }

    if (enabled) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Cada $seconds s", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = seconds.toFloat(),
            onValueChange = { onSecondsChange(it.toInt()) },
            valueRange = 1f..60f,
            steps = 58
        )
        Text(
            "El botón de captura pasa a Iniciar/Detener secuencia.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CalibrationTab(
    onCaptureDark: () -> Unit,
    onCaptureFlat: () -> Unit,
    onCaptureBias: () -> Unit
) {
    Text(
        "Frames de calibración para reducir ruido y defectos en el procesamiento posterior.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    CalibrationCaptureRow(
        title = "Dark",
        description = "Tapá el lente. Usa el mismo ISO y exposición que tus lights.",
        onClick = onCaptureDark
    )
    CalibrationCaptureRow(
        title = "Flat",
        description = "Apuntá a una superficie uniforme (cielo al amanecer o panel de luz).",
        onClick = onCaptureFlat
    )
    CalibrationCaptureRow(
        title = "Bias",
        description = "Tapá el lente con la exposición más corta posible.",
        onClick = onCaptureBias
    )
}

@Composable
private fun CalibrationCaptureRow(title: String, description: String, onClick: () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(onClick = onClick, colors = ButtonDefaults.outlinedButtonColors()) {
            Text("Capturar $title")
        }
    }
}

@Composable
private fun ShutterButton(isIntervalMode: Boolean, isRunning: Boolean, onClick: () -> Unit) {
    val color = if (isIntervalMode && isRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(72.dp),
                colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = color)
            ) {
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

@Composable
private fun DisposableEffectStop(controller: CaptureController) {
    androidx.compose.runtime.DisposableEffect(controller) {
        onDispose { controller.stopCamera() }
    }
}
