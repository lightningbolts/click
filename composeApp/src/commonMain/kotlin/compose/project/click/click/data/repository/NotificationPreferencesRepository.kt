package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class NotificationPreferences(
    val messagePushEnabled: Boolean = true,
    val callPushEnabled: Boolean = true,
)

class NotificationPreferencesRepository {
    private val supabase = SupabaseConfig.client

    suspend fun fetchPreferences(userId: String): NotificationPreferences {
        return try {
            val rows = supabase.from("notification_preferences")
                .select(columns = Columns.list("message_push_enabled", "call_push_enabled")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<NotificationPreferencesRow>()

            rows.firstOrNull()?.toNotificationPreferences() ?: NotificationPreferences()
        } catch (error: Exception) {
            println("NotificationPreferencesRepository: Failed to fetch preferences: ${error.message}")
            NotificationPreferences()
        }
    }

    suspend fun savePreferences(userId: String, preferences: NotificationPreferences): Boolean {
        return try {
            val existing = supabase.from("notification_preferences")
                .select(columns = Columns.list("user_id")) {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<NotificationPreferenceIdentityRow>()

            val updatedAt = Clock.System.now().toEpochMilliseconds()
            if (existing.isNotEmpty()) {
                supabase.from("notification_preferences")
                    .update({
                        set("message_push_enabled", preferences.messagePushEnabled)
                        set("call_push_enabled", preferences.callPushEnabled)
                        set("updated_at", updatedAt)
                    }) {
                        filter {
                            eq("user_id", userId)
                        }
                    }
            } else {
                supabase.from("notification_preferences")
                    .insert(
                        NotificationPreferencesInsert(
                            userId = userId,
                            messagePushEnabled = preferences.messagePushEnabled,
                            callPushEnabled = preferences.callPushEnabled,
                            updatedAt = updatedAt,
                        )
                    )
            }

            true
        } catch (error: Exception) {
            println("NotificationPreferencesRepository: Failed to save preferences: ${error.message}")
            false
        }
    }

    @Serializable
    private data class NotificationPreferenceIdentityRow(
        @SerialName("user_id")
        val userId: String,
    )

    @Serializable
    private data class NotificationPreferencesRow(
        @SerialName("message_push_enabled")
        val messagePushEnabled: Boolean = true,
        @SerialName("call_push_enabled")
        val callPushEnabled: Boolean = true,
    ) {
        fun toNotificationPreferences(): NotificationPreferences {
            return NotificationPreferences(
                messagePushEnabled = messagePushEnabled,
                callPushEnabled = callPushEnabled,
            )
        }
    }

    @Serializable
    private data class NotificationPreferencesInsert(
        @SerialName("user_id")
        val userId: String,
        @SerialName("message_push_enabled")
        val messagePushEnabled: Boolean,
        @SerialName("call_push_enabled")
        val callPushEnabled: Boolean,
        @SerialName("updated_at")
        val updatedAt: Long,
    )
}