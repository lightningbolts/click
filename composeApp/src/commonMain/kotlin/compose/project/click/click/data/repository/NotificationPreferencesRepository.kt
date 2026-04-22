package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.api.NotificationPreferencesPatchBody
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class NotificationPreferences(
    val messagePushEnabled: Boolean = true,
    val callPushEnabled: Boolean = true,
)

class NotificationPreferencesRepository {
    private val supabase by lazy { SupabaseConfig.client }
    private val clickWebApi by lazy { ApiClient() }

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

    suspend fun savePreferences(userId: String, preferences: NotificationPreferences): Result<Unit> {
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("Missing user id"))
        }
        return clickWebApi
            .patchNotificationPreferences(
                NotificationPreferencesPatchBody(
                    messagePushEnabled = preferences.messagePushEnabled,
                    callPushEnabled = preferences.callPushEnabled,
                ),
            )
            .map { }
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
    ) {
        fun toNotificationPreferences(): NotificationPreferences {
            return NotificationPreferences(
                messagePushEnabled = messagePushEnabled,
                callPushEnabled = callPushEnabled,
            )
        }
    }
}