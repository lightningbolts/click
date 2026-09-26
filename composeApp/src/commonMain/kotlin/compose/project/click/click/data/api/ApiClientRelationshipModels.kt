package compose.project.click.click.data.api // pragma: allowlist secret

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A hangout awaiting confirmation, from the viewer's side (`serializeHangout` in click-web
 * `lib/hangouts/hangouts.ts`). Confirmed hangouts become `connection_encounters` rows.
 */
@Serializable
data class PendingHangoutDto(
    val id: String,
    @SerialName("connection_id") val connectionId: String,
    @SerialName("peer_user_id") val peerUserId: String? = null,
    /** `manual` (someone logged it) or `nearby` (hangout detection). */
    val source: String = "manual",
    val status: String = "pending",
    @SerialName("occurred_at") val occurredAt: String,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("confirmed_by_me") val confirmedByMe: Boolean = false,
    @SerialName("requested_by_me") val requestedByMe: Boolean = false,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("encounter_id") val encounterId: String? = null,
) {
    /** True when this user still needs to answer (Confirm / Not us). */
    val awaitingMyAnswer: Boolean get() = status == "pending" && !confirmedByMe
}

@Serializable
data class PendingHangoutsResponseDto(
    val hangouts: List<PendingHangoutDto> = emptyList(),
)

@Serializable
data class HangoutEnvelopeDto(
    val hangout: PendingHangoutDto,
)

@Serializable
data class LogHangoutBody(
    @SerialName("connection_id") val connectionId: String,
    /** ISO-8601 with offset; server allows up to 7 days back. */
    @SerialName("occurred_at") val occurredAt: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("location_name") val locationName: String? = null,
)

@Serializable
data class HangoutConfirmResponseDto(
    /** `confirmed` once both people confirmed, else `pending`. */
    val status: String,
    /** A tap within 3 h already logged this encounter, so no new row was created. */
    @SerialName("already_logged") val alreadyLogged: Boolean = false,
    val hangout: PendingHangoutDto? = null,
)

@Serializable
data class WaveResponseDto(
    val sent: Boolean = false,
    @SerialName("already_waved_today") val alreadyWavedToday: Boolean = false,
)

@Serializable
data class PresencePingBody(
    val lat: Double,
    val lon: Double,
)

@Serializable
data class PresencePingResponseDto(
    val prompted: Int = 0,
)

/** Thrown by [ApiClient.logHangout] when the pair already has an open request (HTTP 409 `pending_exists`). */
class HangoutPendingExistsException(
    message: String,
) : Exception(message)
