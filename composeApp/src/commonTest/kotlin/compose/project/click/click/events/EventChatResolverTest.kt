package compose.project.click.click.events

import compose.project.click.click.data.api.ClickWebRequestException
import compose.project.click.click.data.api.EventChatResolveDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventChatResolverTest {
    @Test
    fun successfulResponseMapsToCanonicalReadyTarget() {
        val ready =
            EventChatResolveDto(
                eventId = "event-1",
                hubId = "hub-1",
                title = "Machine Learning",
                creatorId = "host-1",
            ).toEventChatReady()

        assertEquals("event-1", ready.eventId)
        assertEquals("hub-1", ready.hubId)
        assertEquals("Machine Learning", ready.title)
        assertEquals("host-1", ready.creatorId)
    }

    @Test
    fun rsvpDenialIsTerminalRequiresRsvpEvenWhenOnlyHumanMessageIsSurfaced() {
        val state =
            classifyEventChatResolveFailure(
                ClickWebRequestException(403, "RSVP to this event to join its chat."),
            )

        assertEquals(EventChatOpenState.RequiresRsvp, state)
    }

    @Test
    fun expiredHubIsTerminalExpired() {
        val state =
            classifyEventChatResolveFailure(
                ClickWebRequestException(410, "This hub is no longer active."),
            )

        assertEquals(EventChatOpenState.Expired, state)
    }

    @Test
    fun missingEventIsTerminalNotFound() {
        val state =
            classifyEventChatResolveFailure(
                ClickWebRequestException(404, "Event not found"),
            )

        assertEquals(EventChatOpenState.NotFound, state)
    }

    @Test
    fun missingHubRelationIsExplicitRetryNotPreparingLoopEvenWithoutMachineCodeInMessage() {
        val state =
            classifyEventChatResolveFailure(
                ClickWebRequestException(409, "This event chat is not ready yet."),
            )

        val retry = assertIs<EventChatOpenState.RetryableError>(state)
        assertEquals("Event chat isn't ready yet. Try again.", retry.message)
    }

    @Test
    fun genericFailureIsBoundedRetry() {
        val retry = assertIs<EventChatOpenState.RetryableError>(
            classifyEventChatResolveFailure(IllegalStateException("network")),
        )

        assertEquals("Couldn't open event chat. Try again.", retry.message)
    }
}
