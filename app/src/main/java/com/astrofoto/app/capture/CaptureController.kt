package com.astrofoto.app.capture

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.DngCreator
import android.hardware.camera2.TotalCaptureResult
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import android.media.ImageReader

/**
 * Cámara en modo manual vía Camera2 directo (no CameraX): CameraX no expone
 * RAW_SENSOR, y para uso científico necesitamos DNG, no JPEG.
 */
class CaptureController(private val context: Context) {

    companion object {
        private const val TAG = "CaptureController"
    }

    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var characteristics: CameraCharacteristics? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var rawImageReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var repeatingBuilder: CaptureRequest.Builder? = null

    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val cameraOpenCloseLock = Semaphore(1)

    @Volatile private var pendingImage: Image? = null
    @Volatile private var pendingResult: TotalCaptureResult? = null

    var isRawSupported: Boolean = false
        private set

    private fun startBackgroundThread() {
        val thread = HandlerThread("CaptureBackground").also { it.start() }
        backgroundThread = thread
        backgroundHandler = Handler(thread.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error deteniendo hilo de cámara", e)
        }
        backgroundThread = null
        backgroundHandler = null
    }

    @SuppressLint("MissingPermission")
    fun startCamera(textureView: TextureView, onReady: () -> Unit, onError: (Exception) -> Unit) {
        startBackgroundThread()

        fun openWithSurface(surface: Surface) {
            try {
                val id = cameraManager.cameraIdList.firstOrNull { camId ->
                    cameraManager.getCameraCharacteristics(camId)
                        .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: cameraManager.cameraIdList.firstOrNull()
                    ?: throw IllegalStateException("No hay cámaras disponibles")

                val chars = cameraManager.getCameraCharacteristics(id)
                characteristics = chars

                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                isRawSupported = capabilities?.contains(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW
                ) == true

                if (isRawSupported) {
                    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    val rawSize = map?.getOutputSizes(ImageFormat.RAW_SENSOR)
                        ?.maxByOrNull { it.width.toLong() * it.height }
                    if (rawSize != null) {
                        rawImageReader = ImageReader.newInstance(
                            rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2
                        )
                    } else {
                        isRawSupported = false
                    }
                }

                if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                    throw IllegalStateException("Timeout esperando la cámara")
                }

                cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        cameraOpenCloseLock.release()
                        cameraDevice = device
                        createSession(device, surface, onReady, onError)
                    }

                    override fun onDisconnected(device: CameraDevice) {
                        cameraOpenCloseLock.release()
                        device.close()
                        cameraDevice = null
                    }

                    override fun onError(device: CameraDevice, error: Int) {
                        cameraOpenCloseLock.release()
                        device.close()
                        cameraDevice = null
                        onError(IllegalStateException("Error de cámara: código $error"))
                    }
                }, backgroundHandler)
            } catch (e: Exception) {
                onError(e)
            }
        }

        if (textureView.isAvailable) {
            textureView.surfaceTexture?.let {
                previewSurface = Surface(it)
                openWithSurface(previewSurface!!)
            }
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                    previewSurface = Surface(surface)
                    openWithSurface(previewSurface!!)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun createSession(
        device: CameraDevice,
        preview: Surface,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        try {
            val surfaces = mutableListOf(preview)
            rawImageReader?.surface?.let { surfaces.add(it) }

            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview)
            }
            repeatingBuilder = builder

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        try {
                            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
                            onReady()
                        } catch (e: Exception) {
                            onError(e)
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        onError(IllegalStateException("No se pudo configurar la sesión de cámara"))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    /** ISO, tiempo de exposición (ns) y foco manual (dioptrías, 0 = infinito). */
    fun applyManualSettings(iso: Int, exposureNanos: Long, focusDistanceDiopters: Float) {
        val session = captureSession ?: return
        val builder = repeatingBuilder ?: return
        try {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureNanos)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistanceDiopters)
            session.setRepeatingRequest(builder.build(), null, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudieron aplicar ajustes manuales", e)
        }
    }

    fun capturePhoto(onSaved: (Uri) -> Unit, onError: (Exception) -> Unit) {
        val device = cameraDevice
        val session = captureSession
        val reader = rawImageReader
        val builder = repeatingBuilder

        if (device == null || session == null || reader == null || builder == null || !isRawSupported) {
            onError(IllegalStateException("Captura RAW no disponible en esta cámara"))
            return
        }

        try {
            val captureBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader.surface)
                set(CaptureRequest.CONTROL_MODE, builder.get(CaptureRequest.CONTROL_MODE))
                set(CaptureRequest.CONTROL_AE_MODE, builder.get(CaptureRequest.CONTROL_AE_MODE))
                set(CaptureRequest.SENSOR_SENSITIVITY, builder.get(CaptureRequest.SENSOR_SENSITIVITY))
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, builder.get(CaptureRequest.SENSOR_EXPOSURE_TIME))
                set(CaptureRequest.CONTROL_AF_MODE, builder.get(CaptureRequest.CONTROL_AF_MODE))
                set(CaptureRequest.LENS_FOCUS_DISTANCE, builder.get(CaptureRequest.LENS_FOCUS_DISTANCE))
            }

            reader.setOnImageAvailableListener({ r ->
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                pendingImage = image
                maybeWriteDng(onSaved, onError)
            }, backgroundHandler)

            session.capture(
                captureBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        pendingResult = result
                        maybeWriteDng(onSaved, onError)
                    }

                    override fun onCaptureFailed(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        failure: CaptureFailure
                    ) {
                        onError(IllegalStateException("Falló la captura (código ${failure.reason})"))
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun maybeWriteDng(onSaved: (Uri) -> Unit, onError: (Exception) -> Unit) {
        val image = pendingImage ?: return
        val result = pendingResult ?: return
        val chars = characteristics ?: return

        try {
            val dngCreator = DngCreator(chars, result)
            val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "ASTRO_$name.dng")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/x-adobe-dng")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Astrofoto/RAW")
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("No se pudo crear el archivo de salida")

            resolver.openOutputStream(uri)?.use { out -> dngCreator.writeImage(out, image) }
                ?: throw IllegalStateException("No se pudo abrir el archivo de salida")

            dngCreator.close()
            image.close()
            pendingImage = null
            pendingResult = null
            onSaved(uri)
        } catch (e: Exception) {
            pendingImage = null
            pendingResult = null
            onError(e)
        }
    }

    fun stopCamera() {
        try {
            captureSession?.close()
            cameraDevice?.close()
            rawImageReader?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error al cerrar cámara", e)
        } finally {
            captureSession = null
            cameraDevice = null
            rawImageReader = null
            stopBackgroundThread()
        }
    }
}

/** Velocidades de obturación comunes p/ foto nocturna, en nanosegundos. */
val ShutterSpeedsNanos: List<Pair<String, Long>> = listOf(
    "1/4000" to 250_000L,
    "1/2000" to 500_000L,
    "1/1000" to 1_000_000L,
    "1/500" to 2_000_000L,
    "1/250" to 4_000_000L,
    "1/125" to 8_000_000L,
    "1/60" to 16_666_667L,
    "1/30" to 33_333_333L,
    "1/15" to 66_666_667L,
    "1/8" to 125_000_000L,
    "1/4" to 250_000_000L,
    "1/2" to 500_000_000L,
    "1\"" to 1_000_000_000L,
    "2\"" to 2_000_000_000L,
    "4\"" to 4_000_000_000L,
    "8\"" to 8_000_000_000L,
    "15\"" to 15_000_000_000L,
    "30\"" to 30_000_000_000L
)

/** ISO típicos p/ sensores de smartphone. */
val IsoValues: List<Int> = listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
