package compose.project.click.click.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Push types iOS routes (`ClickNotificationCoordinator.tapRoute`) that Android didn't. */
class PushRoutesParityTest {
    private fun r(
        type: String,
        vararg pairs: Pair<String, String>,
    ) = PushRoutes.route(mapOf("type" to type) + pairs)

    @Test
    fun eventSocialPushesOpenTheEvent() {
        listOf("event_rsvp", "event_rsvp_request", "friends_going", "event_trending", "event_drop_recap", "event_recap").forEach {
            assertEquals(PushRoute.Event("b1"), r(it, "beacon_id" to "b1"), it)
            assertEquals(PushRoute.OpenApp, r(it), "$it without an id never navigates")
        }
    }

    @Test
    fun newConnectionAndReconnectNearbyOpenTheChat() {
        assertEquals(PushRoute.DirectChat("", "c1"), r("new_connection", "connection_id" to "c1"))
        assertEquals(PushRoute.DirectChat("", "c1"), r("reconnect_nearby", "connection_id" to "c1"))
        assertEquals(PushRoute.Connections, r("new_connection"))
    }

    @Test
    fun priorConnectionPushes() {
        assertEquals(PushRoute.Profile("u1"), r("prior_connection_accepted", "peer_user_id" to "u1"))
        assertEquals(PushRoute.Connections, r("prior_connection_accepted"))
        assertEquals(PushRoute.Connections, r("prior_connection_request", "peer_user_id" to "u1"))
    }

    @Test
    fun reactionsOpenTheBeaconOrTheSharedDrop() {
        assertEquals(PushRoute.Event("b1"), r("reaction", "target_kind" to "soundtrack", "target_id" to "b1"))
        assertEquals(PushRoute.SharedDrop("d1"), r("reaction", "target_kind" to "shared_drop", "target_id" to "d1"))
        assertEquals(PushRoute.OpenApp, r("reaction"))
    }

    @Test
    fun screensAndroidLacksJustOpenTheApp() {
        assertEquals(PushRoute.OpenApp, r("device_approval", "request_id" to "q1"))
        assertEquals(PushRoute.OpenApp, r("something_new", "beacon_id" to "b1"))
    }

    @Test
    fun aReleasedDropOpensItsStory() {
        assertEquals(PushRoute.SharedDrop("d1"), r("shared_drop_released", "drop_id" to "d1", "drop_count" to "2"))
        assertEquals(PushRoute.OpenApp, r("shared_drop_released"))
    }

    @Test
    fun everyNonChatTypeUsesServerText() {
        // These otherwise fall through to the chat-message path and read "Sent you a message".
        listOf(
            "event_rsvp",
            "event_rsvp_request",
            "friends_going",
            "event_trending",
            "event_drop_recap",
            "event_recap",
            "new_connection",
            "prior_connection_accepted",
            "prior_connection_request",
            "device_approval",
            "shared_drop_released",
            "reaction",
            "reconnect_nearby",
        ).forEach { assertTrue(it in PushRoutes.serverTextTypes, it) }
        listOf("chat_message", "new_message", "message", "hub_message", "disposable_reveal").forEach {
            assertFalse(it in PushRoutes.serverTextTypes, it)
        }
    }
}
