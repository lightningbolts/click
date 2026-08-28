package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.NotificationPreferencesPatchBody // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class NotificationPreferences(
    val messagePushEnabled: Boolean = true,
    val callPushEnabled: Boolean = true,
    val eventReminderPushEnabled: Boolean = true,
    val availabilityMatchPushEnabled: Boolean = true,
    val hubMessagePushEnabled: Boolean = true,
    val eventTeaserPushEnabled: Boolean = true,
    val reconnectNudgePushEnabled: Boolean = true,
)

class NotificationPreferencesRepository {
    private val supabase by lazy { SupabaseConfig.client }
    private val clickWebApi by lazy { ApiClient() }

    suspend fun fetchPreferences(userId: String): NotificationPreferences =
        try {
            val rows =
                supabase
                    .from("notification_preferences")
                    .select(
                        columns =
                            Columns.list(
                                "message_push_enabled",
                                "call_push_enabled",
                                "event_reminder_push_enabled",
                                "availability_match_push_enabled",
                                "hub_message_push_enabled",
                                "event_teaser_push_enabled",
                                "reconnect_nudge_push_enabled",
                            ),
                    ) {
                        filter {
                            eq("user_id", userId)
                        }
                    }.decodeList<NotificationPreferencesRow>()

            rows.firstOrNull()?.toNotificationPreferences() ?: NotificationPreferences()
        } catch (error: Exception) {
            println("NotificationPreferencesRepository: Failed to fetch preferences: ${error.message}")
            NotificationPreferences()
        }

    suspend fun savePreferences(
        userId: String,
        preferences: NotificationPreferences,
    ): Result<Unit> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("Missing user id"))
        }
        return clickWebApi
            .patchNotificationPreferences(
                NotificationPreferencesPatchBody(
                    messagePushEnabled = preferences.messagePushEnabled,
                    callPushEnabled = preferences.callPushEnabled,
                    eventReminderPushEnabled = preferences.eventReminderPushEnabled,
                    availabilityMatchPushEnabled = preferences.availabilityMatchPushEnabled,
                    hubMessagePushEnabled = preferences.hubMessagePushEnabled,
                    eventTeaserPushEnabled = preferences.eventTeaserPushEnabled,
                    reconnectNudgePushEnabled = preferences.reconnectNudgePushEnabled,
                ),
            ).map { }
            .onFailure { error ->
                println("NotificationPreferencesRepository: Failed to save preferences: ${error.message}")
            }
    }

    @Serializable
    private data class NotificationPreferencesRow(
        @SerialName("message_push_enabled")
        val messagePushEnabled: Boolean = true,
        @SerialName("call_push_enabled")
        val callPushEnabled: Boolean = true,
        @SerialName("event_reminder_push_enabled")
        val eventReminderPushEnabled: Boolean = true,
        @SerialName("availability_match_push_enabled")
        val availabilityMatchPushEnabled: Boolean = true,
        @SerialName("hub_message_push_enabled")
        val hubMessagePushEnabled: Boolean = true,
        @SerialName("event_teaser_push_enabled")
        val eventTeaserPushEnabled: Boolean = true,
        @SerialName("reconnect_nudge_push_enabled")
        val reconnectNudgePushEnabled: Boolean = true,
    ) {
        fun toNotificationPreferences(): NotificationPreferences =
            NotificationPreferences(
                messagePushEnabled = messagePushEnabled,
                callPushEnabled = callPushEnabled,
                eventReminderPushEnabled = eventReminderPushEnabled,
                availabilityMatchPushEnabled = availabilityMatchPushEnabled,
                hubMessagePushEnabled = hubMessagePushEnabled,
                eventTeaserPushEnabled = eventTeaserPushEnabled,
                reconnectNudgePushEnabled = reconnectNudgePushEnabled,
            )
    }
}
