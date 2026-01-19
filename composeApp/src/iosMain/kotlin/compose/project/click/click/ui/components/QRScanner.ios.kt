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
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

// Helper class to hold references that need to be retained
private class QRScannerState(
    val captureSession: AVCaptureSession,
    val previewLayer: AVCaptureVideoPreviewLayer,
    val delegate: NSObject
)

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
            // Show camera
            val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
            
            // Remember the callback to use in the delegate
            val onResultCallback = remember { 
                { value: String -> 
                    if (!hasScanned) {
                        hasScanned = true
                        onResult(value) 
                    }
                } 
            }
            
            // Remember scanner state to prevent garbage collection
            val scannerState = remember(device) {
                if (device == null) return@remember null
                
                val captureSession = AVCaptureSession()
                captureSession.sessionPreset = AVCaptureSessionPresetHigh
                
                // Input
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                if (input != null && captureSession.canAddInput(input)) {
                    captureSession.addInput(input)
                } else {
                    return@remember null
                }
                
                // Output
                val metadataOutput = AVCaptureMetadataOutput()
                var delegateRef: NSObject? = null
                
                if (captureSession.canAddOutput(metadataOutput)) {
                    captureSession.addOutput(metadataOutput)
                    
                    delegateRef = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                        override fun captureOutput(
                            output: AVCaptureOutput,
                            didOutputMetadataObjects: List<*>,
                            fromConnection: AVCaptureConnection
                        ) {
                            didOutputMetadataObjects.firstOrNull()?.let { metadataObject ->
                                val readableObject = metadataObject as? AVMetadataMachineReadableCodeObject
                                readableObject?.stringValue?.let { value ->
                                    onResultCallback(value)
                                }
                            }
                        }
                    }
                    
                    metadataOutput.setMetadataObjectsDelegate(delegateRef, dispatch_get_main_queue())
                    
                    // Set supported types after adding output
                    if (metadataOutput.availableMetadataObjectTypes.contains(AVMetadataObjectTypeQRCode)) {
                        metadataOutput.setMetadataObjectTypes(listOf(AVMetadataObjectTypeQRCode))
                    }
                } else {
                    return@remember null
                }
                
                // Preview Layer
                val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                
                QRScannerState(captureSession, previewLayer, delegateRef ?: object : NSObject() {})
            }
            
            // Start/stop capture session
            DisposableEffect(scannerState) {
                if (scannerState != null) {
                    platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                        if (!scannerState.captureSession.isRunning()) {
                            scannerState.captureSession.startRunning()
                        }
                    }
                }
                onDispose {
                    if (scannerState != null) {
                        platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                            if (scannerState.captureSession.isRunning()) {
                                scannerState.captureSession.stopRunning()
                            }
                        }
                    }
                }
            }

            if (scannerState != null) {
                UIKitView(
                    factory = {
                        val cameraContainer = UIView()
                        cameraContainer.backgroundColor = UIColor.blackColor
                        cameraContainer.clipsToBounds = true
                        
                        // Add preview layer
                        cameraContainer.layer.addSublayer(scannerState.previewLayer)
                        
                        cameraContainer
                    },
                    modifier = modifier.fillMaxSize(),
                    update = { view: UIView ->
                        // Update layer frame when view updates
                        CATransaction.begin()
                        CATransaction.setValue(true, kCATransactionDisableActions)
                        scannerState.previewLayer.frame = view.bounds
                        CATransaction.commit()
                    },
                    onResize = { view: UIView, rect: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect> ->
                        CATransaction.begin()
                        CATransaction.setValue(true, kCATransactionDisableActions)
                        scannerState.previewLayer.frame = rect
                        CATransaction.commit()
                    }
                )
            } else {
                // Camera setup failed
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
                            "Unable to initialize camera. Please try again.",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
