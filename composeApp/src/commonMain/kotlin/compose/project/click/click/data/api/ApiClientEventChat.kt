package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventChatResolveDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("hub_id") val hubId: String,
    val title: String,
    @SerialName("creator_id") val creatorId: String? = null,
)

/**
 * Resolve an event to its canonical server-authorized hub. The event id is the only client input;
 * cached `hub_id` values are intentionally not sent because they are presentation hints, not
 * authorization state.
 */
internal suspend fun ApiClient.resolveEventChat(beaconId: String): Result<EventChatResolveDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/event-chat")
        if (response.status.value in 200..299) {
            val resolved = response.body<EventChatResolveDto>()
            val hubId = resolved.hubId.trim()
            if (hubId.isEmpty()) {
                Result.failure(IllegalStateException("Event chat resolver returned an empty hub id"))
            } else {
                Result.success(
                    resolved.copy(
                        eventId = resolved.eventId.trim().ifBlank { id },
                        hubId = hubId,
                        title = resolved.title.trim().ifBlank { "Event" },
                        creatorId = resolved.creatorId?.trim()?.takeIf { it.isNotEmpty() },
                    ),
                )
            }
        } else {
            Result.failure(
                ClickWebRequestException(
                    statusCode = response.status.value,
                    message = readClickWebErrorMessage(response),
                ),
            )
        }
    } catch (e: ClientRequestException) {
        Result.failure(
            ClickWebRequestException(
                statusCode = e.response.status.value,
                message = readClickWebErrorMessage(e.response),
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
