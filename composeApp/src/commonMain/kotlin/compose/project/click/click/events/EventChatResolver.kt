package compose.project.click.click.events

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import compose.project.click.click.data.api.EventChatResolveDto // pragma: allowlist secret
import compose.project.click.click.data.api.resolveEventChat // pragma: allowlist secret
import kotlinx.coroutines.CancellationException

sealed interface EventChatOpenState {
    data object Idle : EventChatOpenState

    data object Resolving : EventChatOpenState

    data class Ready(
        val eventId: String,
        val hubId: String,
        val title: String,
        val creatorId: String?,
    ) : EventChatOpenState

    data object RequiresRsvp : EventChatOpenState

    data object Expired : EventChatOpenState

    data object NotFound : EventChatOpenState

    data class RetryableError(
        val message: String,
    ) : EventChatOpenState
}

internal fun EventChatResolveDto.toEventChatReady(): EventChatOpenState.Ready =
    EventChatOpenState.Ready(
        eventId = eventId,
        hubId = hubId,
        title = title,
        creatorId = creatorId,
    )

/**
 * Maps server authority into bounded UI states. No failure classification can produce another
 * implicit loading loop; retries happen only after an explicit user action.
 *
 * Classification primarily uses HTTP status because the shared click-web error reader intentionally
 * surfaces the human-readable `message` field when present and may omit the machine `error` code.
 * This endpoint has a narrow contract: 403 means RSVP/host access denied, 409 means the event-hub
 * relation is not ready, 410 means expired, and 404 means the event/chat target no longer exists.
 */
internal fun classifyEventChatResolveFailure(error: Throwable): EventChatOpenState {
    if (error is CancellationException) throw error
    val status = (error as? ClickWebRequestException)?.statusCode
    return when (status) {
        403 -> EventChatOpenState.RequiresRsvp
        410 -> EventChatOpenState.Expired
        404 -> EventChatOpenState.NotFound
        409 -> EventChatOpenState.RetryableError("Event chat isn't ready yet. Try again.")
        401 -> EventChatOpenState.RetryableError("Your session needs to be refreshed. Try again.")
        else -> EventChatOpenState.RetryableError("Couldn't open event chat. Try again.")
    }
}

/**
 * Event-centric resolver used by the detail CTA. It performs exactly one authoritative request per
 * invocation. Hub identity, RSVP authorization, and expiry are all decided by click-web.
 */
class EventChatResolver(
    private val apiClient: ApiClient = ApiClient(),
) {
    suspend fun resolve(eventId: String): EventChatOpenState =
        apiClient
            .resolveEventChat(eventId)
            .fold(
                onSuccess = { it.toEventChatReady() },
                onFailure = { classifyEventChatResolveFailure(it) },
            )
}

internal fun EventChatOpenState.label(): String =
    when (this) {
        EventChatOpenState.Idle -> "Open event chat"
        EventChatOpenState.Resolving -> "Opening event chat…"
        is EventChatOpenState.Ready -> "Open event chat"
        EventChatOpenState.RequiresRsvp -> "RSVP to join event chat"
        EventChatOpenState.Expired -> "Event chat ended"
        EventChatOpenState.NotFound -> "Event chat unavailable"
        is EventChatOpenState.RetryableError -> "Retry event chat"
    }
