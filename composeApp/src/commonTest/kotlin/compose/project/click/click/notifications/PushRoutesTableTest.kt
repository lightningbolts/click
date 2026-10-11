package compose.project.click.click.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per push `data.type` (sheet 08 §16.3 and G1-3; fixtures from iOS F106 tests). */
class PushRoutesTableTest {
    private fun route(
        type: String,
        vararg data: Pair<String, String>,
    ) = PushRoutes.route(mapOf("type" to type) + data)

    @Test
    fun chatMessage() {
        assertEquals(
            PushRoute.DirectChat("ch1", "c1"),
            route(
                "chat_message",
                "chat_id" to "ch1",
                "connection_id" to "c1",
                "sender_user_id" to "u",
            ),
        )
        assertEquals(PushRoute.GroupChat("g1"), route("chat_message", "chat_id" to "g1", "group_id" to "grp", "sender_user_id" to "u"))
    }

    @Test
    fun newMessageAliasesChatMessage() {
        assertEquals(PushRoute.DirectChat("ch1", ""), route("new_message", "chat_id" to "ch1"))
        assertEquals(PushRoute.DirectChat("ch1", ""), route("message", "chat_id" to "ch1"))
    }

    @Test
    fun hubMessage() {
        assertEquals(PushRoute.Hub("h1"), route("hub_message", "hub_id" to "h1", "message_id" to "m1", "sender_user_id" to "u"))
        assertEquals(PushRoute.OpenApp, route("hub_message"))
    }

    @Test
    fun deviceApproval() {
        assertEquals(PushRoute.DeviceApproval("r1"), route("device_approval", "request_id" to "r1"))
        assertEquals(PushRoute.OpenApp, route("device_approval"))
    }

    @Test
    fun disposableRevealSingleAndBatched() {
        assertEquals(
            PushRoute.DirectChat("ch1", "c1"),
            route(
                "disposable_reveal",
                "encounter_id" to "e1",
                "connection_id" to "c1",
                "chat_id" to "ch1",
            ),
        )
        assertEquals(
            PushRoute.DirectChat("ch1", ""),
            route(
                "disposable_reveal",
                "chat_id" to "ch1",
                "message_id" to "m1",
                "drop_count" to "3",
            ),
        )
    }

    @Test
    fun eventDropRecap() {
        assertEquals(PushRoute.EventRecap("e1"), route("event_drop_recap", "beacon_id" to "e1"))
        assertEquals(PushRoute.OpenApp, route("event_drop_recap"))
    }

    @Test
    fun eventReminder() {
        assertEquals(PushRoute.Event("b1"), route("event_reminder", "beacon_id" to "b1", "reminder_kind" to "starting_soon"))
    }

    @Test
    fun eventRecap() {
        assertEquals(PushRoute.EventRecap("b1"), route("event_recap", "beacon_id" to "b1", "reminder_kind" to "recap"))
    }

    @Test
    fun eventTeaserStillRoutesToTheEvent() {
        assertEquals(PushRoute.Event("b1"), route("event_teaser", "beacon_id" to "b1", "teaser_id" to "t1"))
    }

    @Test
    fun availabilityMatch() {
        assertEquals(
            PushRoute.Connections,
            route(
                "availability_match",
                "intent_tag" to "coffee",
                "connection_id" to "c1",
                "peer_user_id" to "u2",
            ),
        )
    }

    @Test
    fun reconnectNudge() {
        assertEquals(PushRoute.Profile("u2"), route("reconnect_nudge", "connection_id" to "c1", "peer_user_id" to "u2"))
    }

    @Test
    fun sharedUpcomingEvent() {
        assertEquals(PushRoute.Event("b1"), route("shared_upcoming_event", "connection_id" to "c1", "beacon_id" to "b1"))
    }

    @Test
    fun reconnectLull() {
        assertEquals(PushRoute.Profile("u2"), route("reconnect_lull", "nudge_id" to "n1", "connection_id" to "c1", "peer_user_id" to "u2"))
        assertEquals(PushRoute.Connections, route("reconnect_lull", "nudge_id" to "n1"))
    }

    @Test
    fun anniversary() {
        assertEquals(PushRoute.Profile("u2"), route("anniversary", "peer_user_id" to "u2", "connection_id" to "c1"))
    }

    @Test
    fun memoryPrompt() {
        assertEquals(PushRoute.Profile("u2"), route("memory_prompt", "nudge_id" to "n1", "peer_user_id" to "u2"))
    }

    @Test
    fun groupRevival() {
        assertEquals(PushRoute.GroupChat("g1"), route("group_revival", "chat_id" to "g1"))
    }

    @Test
    fun wave() {
        assertEquals(PushRoute.DirectChat("", "c1"), route("wave", "peer_user_id" to "u2", "connection_id" to "c1"))
    }

    @Test
    fun hangoutConfirm() {
        assertEquals(
            PushRoute.Profile("u2"),
            route(
                "hangout_confirm",
                "confirmation_id" to "h1",
                "peer_user_id" to "u2",
                "connection_id" to "c1",
            ),
        )
    }

    @Test
    fun archiveWarning() {
        assertEquals(PushRoute.Profile("u2"), route("archive_warning", "connection_id" to "c1", "peer_user_id" to "u2"))
    }

    @Test
    fun sharedDropReleased() {
        assertEquals(
            PushRoute.SharedDrop("d1", posterId = "u2", dropCount = 3),
            route(
                "shared_drop_released",
                "drop_id" to "d1",
                "poster_id" to "u2",
                "drop_count" to "3",
            ),
        )
        assertEquals(PushRoute.SharedDrop("d1", posterId = "u2"), route("shared_drop_released", "drop_id" to "d1", "poster_id" to "u2"))
        assertEquals(PushRoute.OpenApp, route("shared_drop_released"))
    }

    @Test
    fun priorConnectionRequest() {
        assertEquals(PushRoute.Activity("c1"), route("prior_connection_request", "connection_id" to "c1", "sender_user_id" to "u2"))
    }

    @Test
    fun priorConnectionAccepted() {
        assertEquals(PushRoute.Profile("u2"), route("prior_connection_accepted", "peer_user_id" to "u2", "connection_id" to "c1"))
    }

    @Test
    fun reconnectNearby() {
        assertEquals(
            PushRoute.DirectChat("", "c1"),
            route(
                "reconnect_nearby",
                "connection_id" to "c1",
                "peer_user_id" to "u1",
                "sender_name" to "Maya",
            ),
        )
        assertEquals(PushRoute.Connections, route("reconnect_nearby"))
    }

    @Test
    fun incomingCallIsIgnored() {
        assertEquals(PushRoute.OpenApp, route("incoming_call", "chat_id" to "ch1"))
    }

    // region Activity-only types (no push; Activity rows reuse the router)

    @Test
    fun reaction() {
        assertEquals(PushRoute.SharedDrop("d1"), route("reaction", "target_kind" to "shared_drop", "target_id" to "d1"))
        assertEquals(PushRoute.Event("b1"), route("reaction", "target_kind" to "soundtrack", "target_id" to "b1"))
        assertEquals(PushRoute.OpenApp, route("reaction"))
    }

    @Test
    fun eventRsvp() {
        assertEquals(PushRoute.Event("b1"), route("event_rsvp", "beacon_id" to "b1"))
    }

    @Test
    fun eventRsvpRequest() {
        assertEquals(PushRoute.Event("b1"), route("event_rsvp_request", "beacon_id" to "b1"))
    }

    @Test
    fun newConnection() {
        assertEquals(PushRoute.DirectChat("", "c1"), route("new_connection", "peer_user_id" to "u2", "connection_id" to "c1"))
    }

    @Test
    fun friendsGoing() {
        assertEquals(PushRoute.Event("b1"), route("friends_going", "beacon_id" to "b1"))
    }

    @Test
    fun eventTrending() {
        assertEquals(PushRoute.Event("b1"), route("event_trending", "beacon_id" to "b1", "trend" to "hot"))
    }

    // endregion

    @Test
    fun unknownTypesOpenTheAppAndMissingIdsNeverNavigate() {
        assertEquals(PushRoute.OpenApp, route("something_new", "chat_id" to "ch1"))
        assertEquals(PushRoute.OpenApp, route("event_reminder"))
        assertEquals(PushRoute.OpenApp, route("event_recap"))
    }

    @Test
    fun newServerTypesShowServerText() {
        listOf(
            "event_recap",
            "event_drop_recap",
            "shared_drop_released",
            "device_approval",
            "prior_connection_request",
            "prior_connection_accepted",
            "reconnect_nearby",
        ).forEach { assertTrue(it in PushRoutes.serverTextTypes, it) }
    }
}
