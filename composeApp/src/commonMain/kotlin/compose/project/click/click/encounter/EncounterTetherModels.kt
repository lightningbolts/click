package compose.project.click.click.encounter

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TetherPingPayload(
    @SerialName("sender_id") val senderId: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long,
)

data class TetherPingReceived(
    val senderId: String,
    val senderName: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
)
