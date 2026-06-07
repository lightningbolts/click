package compose.project.click.click.ui.camera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.refTo
import kotlinx.cinterop.usePinned
import platform.AVFoundation.*
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.NSObject
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
) : NSObject(), AVCapturePhotoCaptureDelegateProtocol {
    override fun captureOutput(
        output: platform.AVFoundation.AVCapturePhotoOutput,
        didFinishProcessingPhoto: AVCapturePhoto,
        error: platform.Foundation.NSError?,
    ) {
        if (error != null) return
        val data: NSData = didFinishProcessingPhoto.fileDataRepresentation() ?: return
        val bytes = data.toByteArray()
        if (bytes.isNotEmpty()) onJpeg(bytes)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun DisposableCameraView(
    onPhotoConfirmed: (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var capturedImage by remember { mutableStateOf<ByteArray?>(null) }
    var vaultAnimating by remember { mutableStateOf(false) }

    val device = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) }
    val captureSession = remember { AVCaptureSession() }
    val photoOutput = remember { AVCapturePhotoOutput() }
    val sessionPreviewLayer = remember {
        AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }

    DisposableEffect(device) {
        val input = device?.let { AVCaptureDeviceInput.deviceInputWithDevice(it, error = null) }
        captureSession.beginConfiguration()
        captureSession.sessionPreset = AVCaptureSessionPresetPhoto
        if (input != null && captureSession.canAddInput(input)) {
            captureSession.addInput(input)
        }
        if (captureSession.canAddOutput(photoOutput)) {
            captureSession.addOutput(photoOutput)
        }
        captureSession.commitConfiguration()
        captureSession.startRunning()
        onDispose {
            captureSession.stopRunning()
            capturedImage = null
        }
    }

    DisposableCameraChrome(
        capturedImage = capturedImage,
        onShutter = {
            val settings = AVCapturePhotoSettings.photoSettingsWithFormat(
                mapOf(AVVideoCodecKey to AVVideoCodecTypeJPEG),
            )
            val delegate = PhotoCaptureDelegate { bytes -> capturedImage = bytes }
            photoOutput.capturePhotoWithSettings(settings, delegate)
        },
        onRetake = {
            capturedImage = null
            vaultAnimating = false
        },
        onConfirmSend = {
            val bytes = capturedImage
            capturedImage = null
            vaultAnimating = false
            if (bytes != null) onPhotoConfirmed(bytes)
        },
        onDismiss = onDismiss,
        vaultAnimating = vaultAnimating,
        onVaultAnimationStarted = { vaultAnimating = true },
        previewContent = {
            UIKitView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PhotoPreviewView(device).apply {
                        backgroundColor = UIColor.blackColor
                        clipsToBounds = true
                        this.previewLayer = sessionPreviewLayer
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
        frozenPreviewContent = { bytes ->
            UIKitView(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                factory = {
                    UIImageView().apply {
                        contentMode = UIViewContentMode.UIViewContentModeScaleAspectFill
                        clipsToBounds = true
                    }
                },
                update = { iv ->
                    iv.image = UIImage.imageWithData(bytes.toNSData())
                },
                properties = UIKitInteropProperties(
                    isInteractive = false,
                    isNativeAccessibilityEnabled = false,
                ),
            )
        },
    )
}
