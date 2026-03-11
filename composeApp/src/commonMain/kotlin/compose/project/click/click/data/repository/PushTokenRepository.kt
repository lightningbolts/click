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
        tokenType: String = "standard",
        updatedAt: Long = Clock.System.now().toEpochMilliseconds()
    ): Boolean {
        val existing = runCatching {
            supabase.from("push_tokens")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("token", token)
                    }
                }
                .decodeList<ExistingPushTokenRow>()
        }.getOrElse {
            println("Error loading push token state: ${it.message}")
            return false
        }

        return try {
            if (existing.isNotEmpty()) {
                supabase.from("push_tokens")
                    .update({
                        set("user_id", userId)
                        set("platform", platform)
                        set("token_type", tokenType)
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
                            tokenType = tokenType,
                            updatedAt = updatedAt
                        )
                    )
            }

            true
        } catch (e: Exception) {
            if (!e.message.orEmpty().contains("token_type", ignoreCase = true)) {
                println("Error saving push token: ${e.message}")
                return false
            }

            runCatching {
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
                            LegacyPushTokenInsert(
                                userId = userId,
                                token = token,
                                platform = platform,
                                updatedAt = updatedAt
                            )
                        )
                }
            }.onFailure {
                println("Error saving push token: ${it.message}")
                return false
            }

            true
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
        @SerialName("token_type")
        val tokenType: String,
        @SerialName("updated_at")
        val updatedAt: Long
    )

    @Serializable
    private data class LegacyPushTokenInsert(
        @SerialName("user_id")
        val userId: String,
        val token: String,
        val platform: String,
        @SerialName("updated_at")
        val updatedAt: Long
    )
}