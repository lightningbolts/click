package compose.project.click.click.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationRoutingTest {
    // region push preview bodies

    @Test
    fun pushBodyUsesLocalPlaintextAndCollapsesWhitespace() {
        assertEquals("See you at 7", chatPushBody("See you\n at   7", "text"))
    }

    @Test
    fun pushBodyNeverShowsCiphertext() {
        assertEquals("New message", chatPushBody("e2e2:eyJ2IjoyfQ==", "text"))
        assertEquals("New message", chatPushBody("e2e:abc", null))
        assertEquals("📷 Photo", chatPushBody("e2e_grp:abc", "image"))
    }

    @Test
    fun pushBodyFallsBackToTypeLabel() {
        assertEquals("🎤 Voice message", chatPushBody(null, "audio"))
        assertEquals("📎 File", chatPushBody("  ", "file"))
        assertEquals("New message", chatPushBody(null, "something_new"))
    }

    @Test
    fun pushBodyMasksAttachmentEnvelopesAndTruncates() {
        assertEquals("📎 Attachment", chatPushBody("ccx:v1:{\"k\":\"secret\"}", "file"))
        assertEquals(PUSH_PREVIEW_MAX_CHARS, chatPushBody("x".repeat(500), "text").length)
    }

    // endregion

    // region relationship-moment pushes

    @Test
    fun momentTypesAreRecognizedAndGated() {
        assertTrue(RelationshipMomentPush.isMoment("wave"))
        assertFalse(RelationshipMomentPush.isMoment("chat_message"))
        assertFalse(RelationshipMomentPush.isMoment(null))
        assertTrue(RelationshipMomentPush.usesRelationshipMomentsPreference("anniversary"))
        assertTrue(RelationshipMomentPush.usesRelationshipMomentsPreference("group_revival"))
        assertFalse(RelationshipMomentPush.usesRelationshipMomentsPreference("wave"))
        assertFalse(RelationshipMomentPush.usesRelationshipMomentsPreference("hangout_confirm"))
    }

    @Test
    fun momentsRouteToProfileChatOrGroup() {
        val peer = mapOf("peer_user_id" to "u1", "connection_id" to "c1")
        assertEquals(RelationshipMomentPush.Route.Profile("u1"), RelationshipMomentPush.route("anniversary", peer))
        assertEquals(RelationshipMomentPush.Route.Profile("u1"), RelationshipMomentPush.route("hangout_confirm", peer))
        assertEquals(
            RelationshipMomentPush.Route.Chat(chatId = "", connectionId = "c1"),
            RelationshipMomentPush.route("wave", peer),
        )
        assertEquals(
            RelationshipMomentPush.Route.Chat(chatId = "g1", connectionId = ""),
            RelationshipMomentPush.route("group_revival", mapOf("chat_id" to "g1", "group_id" to "grp")),
        )
        // Missing peer: fall back to the chat, then Home.
        assertEquals(
            RelationshipMomentPush.Route.Chat(chatId = "", connectionId = "c1"),
            RelationshipMomentPush.route("memory_prompt", mapOf("connection_id" to "c1")),
        )
        assertEquals(RelationshipMomentPush.Route.Home, RelationshipMomentPush.route("group_revival", emptyMap()))
    }

    // endregion

    // region plan reminder timing

    @Test
    fun planRemindersFireAnHourBeforeOrSoon() {
        val now = 1_000_000_000L
        val hour = 60 * 60 * 1000L
        assertEquals(now + 2 * hour, PlanReminderTiming.fireAt(startsAtEpochMs = now + 3 * hour, nowEpochMs = now))
        // Starts in 30 min: remind in a minute.
        assertEquals(now + 60_000L, PlanReminderTiming.fireAt(startsAtEpochMs = now + hour / 2, nowEpochMs = now))
        // Starts within 5 minutes (or already started): no reminder.
        assertNull(PlanReminderTiming.fireAt(startsAtEpochMs = now + 4 * 60_000L, nowEpochMs = now))
        assertNull(PlanReminderTiming.fireAt(startsAtEpochMs = now - hour, nowEpochMs = now))
        assertEquals("plan.m1", PlanReminderTiming.reminderId("m1"))
    }

    // endregion
}
