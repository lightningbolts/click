package compose.project.click.click.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

@Composable
actual fun DisposableCameraView(
    onPhotoConfirmed: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhotoConfirmed by rememberUpdatedState(onPhotoConfirmed)
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasCameraPermission) {
        DisposableCameraFallback(
            title = "Camera permission needed",
            message = "Disposable Roll uses the camera only for this shared drop.",
            primaryActionLabel = "Enable camera",
            onPrimaryAction = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    val isDisposed = remember { AtomicBoolean(false) }
    val captureExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    fun unbindCamera() {
        runCatching { cameraProvider?.unbindAll() }
        imageCapture = null
        camera = null
        zoomRatio = 1f
    }

    fun bindCameraIfReady() {
        val provider = cameraProvider ?: return
        val view = previewView ?: return
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        runCatching {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            imageCapture = capture
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            setupError = null
        }.onFailure { throwable ->
            imageCapture = null
            camera = null
            setupError = throwable.message ?: "Camera setup failed"
        }
    }

    DisposableEffect(Unit) {
        isDisposed.set(false)
        onDispose {
            isDisposed.set(true)
            unbindCamera()
            captureExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, cameraProvider, previewView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> bindCameraIfReady()
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> unbindCamera()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        bindCameraIfReady()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unbindCamera()
        }
    }

    if (setupError != null) {
        DisposableCameraFallback(
            title = "Camera unavailable",
            message = setupError ?: "The camera could not be prepared. Close and try again.",
            onDismiss = onDismiss,
            modifier = modifier,
        )
        return
    }

    DisposableCameraChrome(
        modifier = modifier,
        isShutterEnabled = imageCapture != null && !isCapturing,
        onShutter = {
            val capture = imageCapture ?: return@DisposableCameraChrome
            isCapturing = true
            capture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bytes = try {
                            val buffer = image.planes.firstOrNull()?.buffer
                            if (buffer == null) {
                                ByteArray(0)
                            } else {
                                ByteArray(buffer.remaining()).also { buffer.get(it) }
                            }
                        } finally {
                            image.close()
                        }

                        mainExecutor.execute {
                            if (isDisposed.get()) return@execute
                            isCapturing = false
                            if (bytes.isNotEmpty()) {
                                currentOnPhotoConfirmed(bytes)
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        mainExecutor.execute {
                            if (isDisposed.get()) return@execute
                            isCapturing = false
                            setupError = exception.message ?: "Photo capture failed"
                        }
                    }
                },
            )
        },
        onDismiss = onDismiss,
        previewContent = {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val cam = camera ?: return@detectTransformGestures
                            val info = cam.cameraInfo.zoomState.value
                            val minZ = info?.minZoomRatio ?: 1f
                            val maxZ = info?.maxZoomRatio ?: 5f
                            zoomRatio = (zoomRatio * zoom).coerceIn(minZ, maxZ)
                            cam.cameraControl.setZoomRatio(zoomRatio)
                        }
                    },
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            if (isDisposed.get()) return@addListener
                            runCatching {
                                cameraProvider = providerFuture.get()
                                bindCameraIfReady()
                            }.onFailure { throwable ->
                                setupError = throwable.message ?: "Camera setup failed"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                update = {
                    if (previewView !== it) {
                        previewView = it
                    }
                },
            )
        },
    )
}
