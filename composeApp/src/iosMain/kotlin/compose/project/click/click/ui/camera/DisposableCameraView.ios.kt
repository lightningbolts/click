package compose.project.click.click.ui.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.ui.utils.openApplicationSystemSettings
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.refTo
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
import platform.objc.sel_registerName
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PhotoPreviewView(
    private val device: AVCaptureDevice?,
) : UIView(frame = CGRectMake(0.0, 0.0, 300.0, 400.0)) {
    var previewLayer: AVCaptureVideoPreviewLayer? = null
        set(value) {
            field?.removeFromSuperlayer()
            field = value
            value?.let { layer.addSublayer(it) }
            updateFrame()
        }

    private var lastZoomFactor = 1.0

    init {
        val pinch = UIPinchGestureRecognizer(target = this, action = sel_registerName("handlePinch:"))
        addGestureRecognizer(pinch)
        userInteractionEnabled = true
    }

    @ObjCAction
    fun handlePinch(gesture: UIPinchGestureRecognizer) {
        val dev = device ?: return
        when (gesture.state) {
            UIGestureRecognizerStateBegan -> {
                lastZoomFactor = dev.videoZoomFactor
            }
            UIGestureRecognizerStateChanged -> {
                val scale = gesture.scale
                val maxZoom = minOf(dev.activeFormat?.videoMaxZoomFactor ?: 5.0, 5.0)
                val newZoom = (lastZoomFactor * scale).coerceIn(1.0, maxZoom)
                try {
                    dev.lockForConfiguration(null)
                    dev.videoZoomFactor = newZoom
                    dev.unlockForConfiguration()
                } catch (_: Throwable) {
                }
            }
            else -> {}
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        updateFrame()
    }

    private fun updateFrame() {
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer?.frame = bounds
        CATransaction.commit()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private class PhotoCaptureDelegate(
    private val onJpeg: (ByteArray) -> Unit,
    private val onComplete: () -> Unit,
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(
        output: platform.AVFoundation.AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: platform.Foundation.NSError?,
    ) {
        if (error != null) {
            onComplete()
            return
        }
        val data: NSData = didFinishProcessingPhoto.fileDataRepresentation() ?: run {
            onComplete()
            return
        }
        val bytes = data.toByteArray()
        if (bytes.isNotEmpty()) {
            onJpeg(bytes)
        } else {
            onComplete()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val len = length.toInt()
    if (len == 0) return ByteArray(0)
    val out = ByteArray(len)
    val base = bytes ?: return ByteArray(0)
    memcpy(out.refTo(0), base, len.toULong())
    return out
}

private sealed class DisposableCameraPermissionState {
    object Checking : DisposableCameraPermissionState()
    object Granted : DisposableCameraPermissionState()
    object Denied : DisposableCameraPermissionState()
    object NotDetermined : DisposableCameraPermissionState()
}

private fun runOnMainQueue(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) {
        block()
    }
}

private fun runOnCameraQueue(block: () -> Unit) {
    dispatch_async(
        dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u),
    ) {
        block()
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun DisposableCameraView(
    onPhotoConfirmed: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    val currentOnPhotoConfirmed by rememberUpdatedState(onPhotoConfirmed)
    var permissionState by remember {
        mutableStateOf<DisposableCameraPermissionState>(DisposableCameraPermissionState.Checking)
    }
    var isCapturing by remember { mutableStateOf(false) }
    var setupComplete by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var retainedPhotoDelegate by remember { mutableStateOf<PhotoCaptureDelegate?>(null) }
    var isDisposed by remember { mutableStateOf(false) }

    val device = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) }
    val captureSession = remember { AVCaptureSession() }
    val photoOutput = remember { AVCapturePhotoOutput() }
    val sessionPreviewLayer = remember {
        AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    LaunchedEffect(Unit) {
        permissionState = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> DisposableCameraPermissionState.Granted
            AVAuthorizationStatusDenied,
            AVAuthorizationStatusRestricted -> DisposableCameraPermissionState.Denied
            AVAuthorizationStatusNotDetermined -> {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    runOnMainQueue {
                        permissionState = if (granted) {
                            DisposableCameraPermissionState.Granted
                        } else {
                            DisposableCameraPermissionState.Denied
                        }
                    }
                }
                DisposableCameraPermissionState.NotDetermined
            }
            else -> DisposableCameraPermissionState.Denied
        }
    }

    when (permissionState) {
        DisposableCameraPermissionState.Checking,
        DisposableCameraPermissionState.NotDetermined -> {
            DisposableCameraFallback(
                title = "Requesting camera access",
                message = "Disposable Roll needs the camera to capture a private drop.",
                onDismiss = onDismiss,
                modifier = modifier,
            )
            return
        }
        DisposableCameraPermissionState.Denied -> {
            DisposableCameraFallback(
                title = "Camera access required",
                message = "Enable camera access in Settings to use Disposable Roll.",
                primaryActionLabel = "Open Settings",
                onPrimaryAction = { openApplicationSystemSettings() },
                onDismiss = onDismiss,
                modifier = modifier,
            )
            return
        }
        DisposableCameraPermissionState.Granted -> Unit
    }

    DisposableEffect(device) {
        var disposed = false
        isDisposed = false

        if (device == null) {
            setupError = "Camera not available"
            setupComplete = false
            onDispose {
                disposed = true
                isDisposed = true
            }
        } else {
            runOnCameraQueue {
                runCatching {
                    var committed = false
                    captureSession.beginConfiguration()
                    try {
                        captureSession.sessionPreset = AVCaptureSessionPresetPhoto

                        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
                        if (input != null && captureSession.canAddInput(input)) {
                            captureSession.addInput(input)
                        } else {
                            throw IllegalStateException("Could not add camera input")
                        }

                        if (captureSession.canAddOutput(photoOutput)) {
                            captureSession.addOutput(photoOutput)
                        } else {
                            throw IllegalStateException("Could not add photo output")
                        }

                        captureSession.commitConfiguration()
                        committed = true
                    } finally {
                        if (!committed) {
                            runCatching { captureSession.commitConfiguration() }
                        }
                    }

                    if (!captureSession.isRunning()) {
                        captureSession.startRunning()
                    }
                }.onSuccess {
                    runOnMainQueue {
                        if (!disposed) {
                            setupComplete = true
                            setupError = null
                        }
                    }
                }.onFailure { throwable ->
                    runOnMainQueue {
                        if (!disposed) {
                            setupComplete = false
                            setupError = throwable.message ?: "Camera setup failed"
                        }
                    }
                }
            }

            onDispose {
                disposed = true
                isDisposed = true
                setupComplete = false
                retainedPhotoDelegate = null
                runOnCameraQueue {
                    if (captureSession.isRunning()) {
                        captureSession.stopRunning()
                    }
                }
            }
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
        isShutterEnabled = setupComplete && !isCapturing,
        onShutter = {
            if (!setupComplete || isCapturing) return@DisposableCameraChrome
            isCapturing = true
            val settings = AVCapturePhotoSettings.photoSettingsWithFormat(
                mapOf(AVVideoCodecKey to AVVideoCodecTypeJPEG),
            )
            val delegate = PhotoCaptureDelegate(
                onJpeg = { bytes ->
                    runOnMainQueue {
                        if (isDisposed) return@runOnMainQueue
                        isCapturing = false
                        retainedPhotoDelegate = null
                        currentOnPhotoConfirmed(bytes)
                    }
                },
                onComplete = {
                    runOnMainQueue {
                        if (isDisposed) return@runOnMainQueue
                        isCapturing = false
                        retainedPhotoDelegate = null
                    }
                },
            )
            retainedPhotoDelegate = delegate
            runCatching {
                photoOutput.capturePhotoWithSettings(settings, delegate)
            }.onFailure { throwable ->
                isCapturing = false
                retainedPhotoDelegate = null
                setupError = throwable.message ?: "Photo capture failed"
            }
        },
        onDismiss = onDismiss,
        previewContent = {
            UIKitView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PhotoPreviewView(device).apply {
                        backgroundColor = UIColor.blackColor
                        clipsToBounds = true
                        previewLayer = sessionPreviewLayer
                    }
                },
                update = { view ->
                    (view as? PhotoPreviewView)?.setNeedsLayout()
                },
                properties = UIKitInteropProperties(
                    isInteractive = true,
                    isNativeAccessibilityEnabled = false,
                ),
            )
        },
    )
}
