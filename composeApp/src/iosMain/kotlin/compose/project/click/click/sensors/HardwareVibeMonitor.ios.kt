package compose.project.click.click.sensors

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreMotion.CMAttitudeReferenceFrameXMagneticNorthZVertical
import platform.CoreMotion.CMDeviceMotion
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sqrt

private const val G_TO_MS2 = 9.80665f

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class HardwareVibeMonitor actual constructor() {

    actual suspend fun takeSnapshot(): HardwareVibeSnapshot = withTimeoutOrNull(3_500L) {
        suspendCancellableCoroutine { cont ->
            val motion = CMMotionManager()
            val queue = NSOperationQueue.mainQueue
            val accelSamples = mutableListOf<Triple<Float, Float, Float>>()
            var lastYawDeg: Float? = null
            var finished = false

            fun cleanup() {
                if (motion.accelerometerActive) motion.stopAccelerometerUpdates()
                if (motion.deviceMotionActive) motion.stopDeviceMotionUpdates()
            }

            fun finish(snapshot: HardwareVibeSnapshot) {
                if (finished) return
                finished = true
                cleanup()
                if (cont.isActive) cont.resume(snapshot)
            }

            val brightness = UIScreen.mainScreen.brightness
            val luxProxy =
                if (brightness.isFinite() && brightness >= 0.0) (brightness * 100.0).toFloat() else null

            val wasMonitoring = UIDevice.currentDevice.batteryMonitoringEnabled
            UIDevice.currentDevice.batteryMonitoringEnabled = true
            val rawLevel = UIDevice.currentDevice.batteryLevel
            val batteryPct = when {
                rawLevel < 0f -> null
                else -> (rawLevel * 100f).toInt().coerceIn(0, 100)
            }
            UIDevice.currentDevice.batteryMonitoringEnabled = wasMonitoring

            if (motion.accelerometerAvailable) {
                motion.accelerometerUpdateInterval = 0.02
                motion.startAccelerometerUpdatesToQueue(
                    queue,
                    withHandler = { data, _ ->
                        val a = data?.acceleration ?: return@startAccelerometerUpdatesToQueue
                        val triple = a.useContents {
                            Triple(
                                (x * G_TO_MS2).toFloat(),
                                (y * G_TO_MS2).toFloat(),
                                (z * G_TO_MS2).toFloat(),
                            )
                        }
                        accelSamples += triple
                    },
                )
            }

            if (motion.deviceMotionAvailable) {
                motion.deviceMotionUpdateInterval = 0.05
                motion.startDeviceMotionUpdatesUsingReferenceFrame(
                    CMAttitudeReferenceFrameXMagneticNorthZVertical,
                    toQueue = queue,
                    withHandler = { data: CMDeviceMotion?, _ ->
                        val yaw = data?.attitude?.yaw ?: return@startDeviceMotionUpdatesUsingReferenceFrame
                        if (!yaw.isFinite()) return@startDeviceMotionUpdatesUsingReferenceFrame
                        var deg = (yaw * 180.0 / PI).toFloat()
                        deg %= 360f
                        if (deg < 0f) deg += 360f
                        lastYawDeg = deg
                    },
                )
            }

            cont.invokeOnCancellation { cleanup() }

            dispatch_after(
                dispatch_time(DISPATCH_TIME_NOW, 500L * NSEC_PER_MSEC.toLong()),
                dispatch_get_main_queue(),
            ) {
                val magnitudes = accelSamples.map { (x, y, z) -> sqrt(x * x + y * y + z * z) }
                val motionVar = if (magnitudes.size >= 3) {
                    val mean = magnitudes.average().toFloat()
                    magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
                } else {
                    null
                }
                finish(
                    HardwareVibeSnapshot(
                        luxLevel = luxProxy,
                        motionVariance = motionVar?.takeIf { it.isFinite() },
                        compassAzimuth = lastYawDeg?.takeIf { it.isFinite() },
                        batteryLevel = batteryPct,
                    ),
                )
            }
        }
    } ?: HardwareVibeSnapshot()
}
