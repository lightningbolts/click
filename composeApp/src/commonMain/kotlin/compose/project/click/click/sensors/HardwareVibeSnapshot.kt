package compose.project.click.click.sensors

import kotlinx.serialization.Serializable

/**
 * 500ms hardware snapshot captured immediately before tri-factor BLE/audio broadcast.
 * [luxLevel] is true lux on Android; on iOS it is a [0,100] proxy derived from screen brightness.
 */
@Serializable
data class HardwareVibeSnapshot(
    val luxLevel: Float? = null,
    val motionVariance: Float? = null,
    val compassAzimuth: Float? = null,
    val batteryLevel: Int? = null,
)
