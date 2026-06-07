package compose.project.click.click.ui.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import compose.project.click.click.ui.utils.openApplicationSystemSettings
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
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
    private var device: AVCaptureDevice?,
) : UIView(frame = CGRectMake(0.0, 0.0, 300.0, 400.0)) {
    var previewLayer: AVCaptureVideoPreviewLayer? = null
        set(value) {
            field?.removeFromSuperlayer()
            field = value
            value?.let { layer.addSublayer(it) }
            updateFrame()
        }

    private var lastZoomFactor = 1.0

    fun updateDevice(newDevice: AVCaptureDevice?) {
        device = newDevice
    }

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
        output: AVCapturePhotoOutput,
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

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
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

@OptIn(ExperimentalForeignApi::class)
private fun captureDevice(position: Long): AVCaptureDevice? =
    AVCaptureDevice.defaultDeviceWithDeviceType(
        AVCaptureDeviceTypeBuiltInWideAngleCamera,
        mediaType = AVMediaTypeVideo,
        position = position,
    )

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun DisposableCameraView(
    onPhotoConfirmed: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
    extraBottomPadding: Dp,
) {
    val currentOnPhotoConfirmed by rememberUpdatedState(onPhotoConfirmed)
    val coroutineScope = rememberCoroutineScope()
    var permissionState by remember {
        mutableStateOf<DisposableCameraPermissionState>(DisposableCameraPermissionState.Checking)
    }
    var isCapturing by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<ByteArray?>(null) }
    var setupComplete by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var retainedPhotoDelegate by remember { mutableStateOf<PhotoCaptureDelegate?>(null) }
    var isDisposed by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var isSending by remember { mutableStateOf(false) }
    var previewHost by remember { mutableStateOf<PhotoPreviewView?>(null) }

    val frontDevice = remember { captureDevice(AVCaptureDevicePositionFront) }
    val backDevice = remember { captureDevice(AVCaptureDevicePositionBack) }
    var activeDevice by remember { mutableStateOf(frontDevice) }

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

    fun configureSession(device: AVCaptureDevice?) {
        if (device == null) {
            runOnMainQueue {
                setupError = "Camera not available"
                setupComplete = false
            }
            return
        }
        runOnCameraQueue {
            runCatching {
                if (captureSession.isRunning()) {
                    captureSession.stopRunning()
                }
                captureSession.beginConfiguration()
                var committed = false
                try {
                    captureSession.inputs.forEach { input ->
                        captureSession.removeInput(input as AVCaptureInput)
                    }
                    captureSession.sessionPreset = AVCaptureSessionPresetPhoto

                    val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
                        ?: throw IllegalStateException("Could not add camera input")
                    if (!captureSession.canAddInput(input)) {
                        throw IllegalStateException("Could not add camera input")
                    }
                    captureSession.addInput(input)

                    if (!captureSession.outputs.contains(photoOutput)) {
                        if (captureSession.canAddOutput(photoOutput)) {
                            captureSession.addOutput(photoOutput)
                        } else {
                            throw IllegalStateException("Could not add photo output")
                        }
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
                    if (!isDisposed) {
                        setupComplete = true
                        setupError = null
                        previewHost?.updateDevice(device)
                    }
                }
            }.onFailure { throwable ->
                runOnMainQueue {
                    if (!isDisposed) {
                        setupComplete = false
                        setupError = throwable.message ?: "Camera setup failed"
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        isDisposed = false
        activeDevice = if (useFrontCamera) frontDevice else backDevice
        configureSession(activeDevice)
        onDispose {
            isDisposed = true
            setupComplete = false
            capturedImage = null
            retainedPhotoDelegate = null
            runOnCameraQueue {
                if (captureSession.isRunning()) {
                    captureSession.stopRunning()
                }
            }
        }
    }

    LaunchedEffect(useFrontCamera) {
        if (isDisposed) return@LaunchedEffect
        val nextDevice = if (useFrontCamera) frontDevice else backDevice
        if (nextDevice == activeDevice) return@LaunchedEffect
        activeDevice = nextDevice
        setupComplete = false
        configureSession(nextDevice)
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
        capturedImage = capturedImage,
        isShutterEnabled = capturedImage == null && setupComplete && !isCapturing && !isSending,
        selectedFilterIndex = selectedFilterIndex,
        onFilterIndexChange = { selectedFilterIndex = it },
        onFlipCamera = {
            if (capturedImage == null && !isCapturing) {
                useFrontCamera = !useFrontCamera
            }
        },
        extraBottomPadding = extraBottomPadding,
        mirrorCapturedPreview = useFrontCamera,
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
                        capturedImage = bytes
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
        onSend = {
            val bytes = capturedImage ?: return@DisposableCameraChrome
            if (isSending) return@DisposableCameraChrome
            isSending = true
            coroutineScope.launch {
                val filtered = applyDisposableRollFilterToJpeg(bytes, selectedFilterIndex)
                capturedImage = null
                isSending = false
                currentOnPhotoConfirmed(filtered)
            }
        },
        onDismiss = onDismiss,
        previewContent = {
            UIKitView(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useFrontCamera) {
                            Modifier.graphicsLayer { scaleX = -1f }
                        } else {
                            Modifier
                        },
                    ),
                factory = {
                    PhotoPreviewView(activeDevice).apply {
                        backgroundColor = UIColor.blackColor
                        clipsToBounds = true
                        previewLayer = sessionPreviewLayer
                        previewHost = this
                    }
                },
                update = { view ->
                    val host = view as? PhotoPreviewView
                    host?.updateDevice(activeDevice)
                    host?.setNeedsLayout()
                },
                properties = UIKitInteropProperties(
                    isInteractive = true,
                    isNativeAccessibilityEnabled = false,
                ),
            )
        },
    )
}
