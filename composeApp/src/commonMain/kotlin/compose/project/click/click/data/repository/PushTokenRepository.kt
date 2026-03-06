package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class PushTokenRepository {
    private val supabase = SupabaseConfig.client

    suspend fun savePushToken(
        userId: String,
        token: String,
        platform: String,
        updatedAt: Long = Clock.System.now().toEpochMilliseconds()
    ): Boolean {
        return try {
            val existing = supabase.from("push_tokens")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("token", token)
                    }
                }
                .decodeList<ExistingPushTokenRow>()

            if (existing.isNotEmpty()) {
                supabase.from("push_tokens")
                    .update({
                        set("user_id", userId)
                        set("platform", platform)
                        set("updated_at", updatedAt)
                    }) {
                        filter {
                            eq("token", token)
                        }
                    }
            } else {
                supabase.from("push_tokens")
                    .insert(
                        PushTokenInsert(
                            userId = userId,
                            token = token,
                            platform = platform,
                            updatedAt = updatedAt
                        )
                    )
            }

            true
        } catch (e: Exception) {
            println("Error saving push token: ${e.message}")
            false
        }
    }

    @Serializable
    private data class ExistingPushTokenRow(
        val id: String
    )

    @Serializable
    private data class PushTokenInsert(
        @SerialName("user_id")
        val userId: String,
        val token: String,
        val platform: String,
        @SerialName("updated_at")
        val updatedAt: Long
    )
}