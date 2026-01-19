package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

/**
 * Custom UIView subclass that properly handles the camera preview layer layout
 */
@OptIn(ExperimentalForeignApi::class)
private class CameraPreviewView : UIView(frame = CGRectMake(0.0, 0.0, 300.0, 400.0)) {
    var previewLayer: AVCaptureVideoPreviewLayer? = null
        set(value) {
            field?.removeFromSuperlayer()
            field = value
            value?.let { layer.addSublayer(it) }
            updatePreviewLayerFrame()
        }
    
    override fun layoutSubviews() {
        super.layoutSubviews()
        updatePreviewLayerFrame()
    }
    
    private fun updatePreviewLayerFrame() {
        CATransaction.begin()
        CATransaction.setValue(true, kCATransactionDisableActions)
        previewLayer?.frame = bounds
        CATransaction.commit()
    }
}

private sealed class CameraPermissionState {
    object Checking : CameraPermissionState()
    object Granted : CameraPermissionState()
    object Denied : CameraPermissionState()
    object NotDetermined : CameraPermissionState()
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun QRScanner(
    modifier: Modifier,
    onResult: (String) -> Unit
) {
    var permissionState by remember { mutableStateOf<CameraPermissionState>(CameraPermissionState.Checking) }
    var hasScanned by remember { mutableStateOf(false) }
    
    // Check and request camera permission
    LaunchedEffect(Unit) {
        val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        permissionState = when (status) {
            AVAuthorizationStatusAuthorized -> CameraPermissionState.Granted
            AVAuthorizationStatusDenied, AVAuthorizationStatusRestricted -> CameraPermissionState.Denied
            AVAuthorizationStatusNotDetermined -> {
                // Request permission
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    platform.darwin.dispatch_async(dispatch_get_main_queue()) {
                        permissionState = if (granted) {
                            CameraPermissionState.Granted
                        } else {
                            CameraPermissionState.Denied
                        }
                    }
                }
                CameraPermissionState.NotDetermined
            }
            else -> CameraPermissionState.Denied
        }
    }
    
    when (permissionState) {
        is CameraPermissionState.Checking, is CameraPermissionState.NotDetermined -> {
            // Show loading while checking permissions
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Requesting camera access...",
                        color = Color.White
                    )
                }
            }
        }
        
        is CameraPermissionState.Denied -> {
            // Show permission denied message
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Camera Access Required",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Please enable camera access in Settings to scan QR codes.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        // Open app settings
                        val settingsUrl = platform.Foundation.NSURL.URLWithString(UIApplicationOpenSettingsURLString)
                        if (settingsUrl != null) {
                            UIApplication.sharedApplication.openURL(settingsUrl)
                        }
                    }) {
                        Text("Open Settings")
                    }
                }
            }
        }
        
        is CameraPermissionState.Granted -> {
            CameraPreviewContent(
                modifier = modifier,
                onResult = { value ->
                    if (!hasScanned) {
                        hasScanned = true
                        onResult(value)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
private fun CameraPreviewContent(
    modifier: Modifier,
    onResult: (String) -> Unit
) {
    val device = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) }
    
    // Create capture session and related objects
    val captureSession = remember { AVCaptureSession() }
    val previewLayer = remember { 
        AVCaptureVideoPreviewLayer(session = captureSession).apply {
            videoGravity = AVLayerVideoGravityResizeAspectFill
        }
    }
    
    // Remember the delegate to prevent garbage collection
    val metadataDelegate = remember {
        object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
            override fun captureOutput(
                output: AVCaptureOutput,
                didOutputMetadataObjects: List<*>,
                fromConnection: AVCaptureConnection
            ) {
                didOutputMetadataObjects.firstOrNull()?.let { metadataObject ->
                    val readableObject = metadataObject as? AVMetadataMachineReadableCodeObject
                    readableObject?.stringValue?.let { value ->
                        onResult(value)
                    }
                }
            }
        }
    }
    
    // Setup capture session
    var setupComplete by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(device) {
        if (device == null) {
            setupError = "Camera not available"
            return@LaunchedEffect
        }
        
        try {
            captureSession.beginConfiguration()
            captureSession.sessionPreset = AVCaptureSessionPresetHigh
            
            // Add input
            val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
            if (input != null && captureSession.canAddInput(input)) {
                captureSession.addInput(input)
            } else {
                setupError = "Could not add camera input"
                captureSession.commitConfiguration()
                return@LaunchedEffect
            }
            
            // Add output
            val metadataOutput = AVCaptureMetadataOutput()
            if (captureSession.canAddOutput(metadataOutput)) {
                captureSession.addOutput(metadataOutput)
                metadataOutput.setMetadataObjectsDelegate(metadataDelegate, dispatch_get_main_queue())
                
                // Must set metadata types AFTER adding output
                if (metadataOutput.availableMetadataObjectTypes.contains(AVMetadataObjectTypeQRCode)) {
                    metadataOutput.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
                }
            } else {
                setupError = "Could not add metadata output"
                captureSession.commitConfiguration()
                return@LaunchedEffect
            }
            
            captureSession.commitConfiguration()
            setupComplete = true
        } catch (e: Exception) {
            setupError = "Camera setup failed: ${e.message}"
        }
    }
    
    // Start/stop capture session
    DisposableEffect(setupComplete) {
        if (setupComplete) {
            platform.darwin.dispatch_async(
                platform.darwin.dispatch_get_global_queue(
                    platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 
                    0u
                )
            ) {
                captureSession.startRunning()
            }
        }
        onDispose {
            platform.darwin.dispatch_async(
                platform.darwin.dispatch_get_global_queue(
                    platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 
                    0u
                )
            ) {
                if (captureSession.isRunning()) {
                    captureSession.stopRunning()
                }
            }
        }
    }

    if (setupError != null) {
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Camera Setup Failed",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    setupError ?: "Unknown error",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    } else {
        // Use the custom CameraPreviewView that handles layout properly
        UIKitView(
            factory = {
                CameraPreviewView().apply {
                    backgroundColor = UIColor.blackColor
                    clipsToBounds = true
                    this.previewLayer = previewLayer
                }
            },
            modifier = modifier.fillMaxSize(),
            update = { view ->
                // Force layout update
                (view as? CameraPreviewView)?.setNeedsLayout()
            },
            properties = UIKitInteropProperties(
                isInteractive = true,
                isNativeAccessibilityEnabled = false
            )
        )
    }
}
