package compose.project.click.click.data.api // pragma: allowlist secret

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Review #5: web merges `incoming[key] ?? prev[key]`, so a `true` that is left out of the PATCH
 * never re-enables a toggle. The client Json does not encode defaults, so every key must be sent.
 */
class NotificationPreferencesPatchBodyTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    @Test
    fun trueValuesAreEncoded() {
        val body =
            NotificationPreferencesPatchBody(
                messagePushEnabled = true,
                eventReminderPushEnabled = true,
                availabilityMatchPushEnabled = true,
                hubMessagePushEnabled = true,
                reconnectNudgePushEnabled = true,
                dropReleasePushEnabled = true,
            )
        val obj = json.parseToJsonElement(json.encodeToString(NotificationPreferencesPatchBody.serializer(), body)).jsonObject
        val keys =
            listOf(
                "message_push_enabled",
                "event_reminder_push_enabled",
                "availability_match_push_enabled",
                "hub_message_push_enabled",
                "reconnect_nudge_push_enabled",
                "drop_release_push_enabled",
            )
        assertEquals(keys.toSet(), obj.keys)
        keys.forEach { assertEquals(true, obj[it]!!.jsonPrimitive.boolean, it) }
    }
}
