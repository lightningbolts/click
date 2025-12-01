package compose.project.click.click.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.AVFoundation.*
import platform.CoreGraphics.CGRect
import platform.Foundation.NSNotificationCenter
import platform.QuartzCore.CALayer
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCATransactionDisableActions
import platform.UIKit.*
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Composable
actual fun QRScanner(
    modifier: Modifier,
    onResult: (String) -> Unit
) {
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)

    if (device != null) {
        UIKitView(
            factory = {
                val cameraContainer = UIView()
                cameraContainer.backgroundColor = UIColor.blackColor
                
                val captureSession = AVCaptureSession()
                
                // Input
                val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null)
                if (input != null && captureSession.canAddInput(input)) {
                    captureSession.addInput(input)
                }
                
                // Output
                val metadataOutput = AVCaptureMetadataOutput()
                if (captureSession.canAddOutput(metadataOutput)) {
                    captureSession.addOutput(metadataOutput)
                    
                    val delegate = object : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
                        override fun captureOutput(
                            output: AVCaptureOutput,
                            didOutputMetadataObjects: List<*>,
                            fromConnection: AVCaptureConnection
                        ) {
                            didOutputMetadataObjects.firstOrNull()?.let { metadataObject ->
                                val readableObject = metadataObject as? AVMetadataMachineReadableCodeObject
                                readableObject?.stringValue?.let { value ->
                                    // Dispatch to main thread to avoid UI issues
                                    onResult(value)
                                }
                            }
                        }
                    }
                    
                    metadataOutput.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
                    metadataOutput.setMetadataObjectTypes(listOf(AVMetadataObjectTypeQRCode))
                }
                
                // Preview Layer
                val previewLayer = AVCaptureVideoPreviewLayer(session = captureSession)
                previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
                previewLayer.frame = cameraContainer.layer.bounds
                cameraContainer.layer.addSublayer(previewLayer)
                
                // Start Session
                platform.darwin.dispatch_async(platform.darwin.dispatch_get_global_queue(platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
                    captureSession.startRunning()
                }
                
                // Handle Layout
                cameraContainer
            },
            modifier = modifier.fillMaxSize(),
            onResize = { view: UIView, rect: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect> ->
                CATransaction.begin()
                CATransaction.setValue(true, kCATransactionDisableActions)
                view.layer.sublayers?.firstOrNull()?.let { layer ->
                    (layer as CALayer).frame = rect
                }
                CATransaction.commit()
            }
        )
    } else {
        // Fallback for Simulator
        Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Camera not available in Simulator",
                    color = Color.White
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Button(onClick = {
                    // Simulate a valid payload: "click:user:<userId>"
                    // We'll use a dummy ID for testing
                    onResult("click:user:simulated-user-id-123")
                }) {
                    Text("Simulate Scan")
                }
            }
        }
    }
}
