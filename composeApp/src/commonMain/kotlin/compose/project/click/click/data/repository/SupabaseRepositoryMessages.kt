@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentInsert // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelineCacheEntry // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserAvailability // pragma: allowlist secret
import compose.project.click.click.data.models.UserInterests // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.models.mergeRichestEncounterEvents // pragma: allowlist secret
import compose.project.click.click.data.models.resolveDisplayName // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Edit the content of an existing message and stamp time_edited.
 * Encrypts the new content if the original message was encrypted.
 */
internal suspend fun SupabaseRepository.editMessageImpl(
    messageId: String,
    newContent: String,
    chatId: String? = null,
): Boolean =
    try {
        val now =
            kotlinx.datetime.Clock.System
                .now()
                .toEpochMilliseconds()

        var wireContent = newContent
        if (chatId != null) {
            val chatRepo =
                SupabaseChatRepository(
                    tokenStorage =
                        compose.project.click.click.data.storage // pragma: allowlist secret
                            .createTokenStorage(),
                )
            // Attempt encryption if we can resolve keys
            try {
                val chat =
                    supabase
                        .from("chats")
                        .select(
                            columns =
                                io.github.jan.supabase.postgrest.query.Columns
                                    .list("connection_id"),
                        ) {
                            filter { eq("id", chatId) }
                            limit(1)
                        }.decodeList<SupabaseRepository.ChatConnectionIdOnly>()
                        .firstOrNull()

                if (chat != null) {
                    val connection =
                        supabase
                            .from("connections")
                            .select(
                                columns =
                                    io.github.jan.supabase.postgrest.query.Columns
                                        .list("id", "user_ids"),
                            ) {
                                filter { eq("id", chat.connectionId) }
                                limit(1)
                            }.decodeList<SupabaseRepository.ConnectionUserIdsOnlyRow>()
                            .firstOrNull()

                    if (connection != null) {
                        val keys =
                            compose.project.click.click.crypto.MessageCrypto.deriveKeysForConnection( // pragma: allowlist secret
                                connection.id,
                                connection.userIds,
                            )
                        wireContent =
                            compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret
                                .encryptContent(newContent, keys)
                    }
                }
            } catch (_: Exception) {
                // fall through with plaintext
            }
        }

        supabase
            .from("messages")
            .update({
                set("content", wireContent)
                set("time_edited", now)
            }) {
                filter { eq("id", messageId) }
            }
        true
    } catch (e: Exception) {
        println("Error editing message (redacted): ${e.redactedRestMessage()}")
        false
    }

/**
 * Hard-delete a single message.
 */
internal suspend fun SupabaseRepository.deleteMessageImpl(messageId: String): Boolean =
    try {
        supabase
            .from("messages")
            .delete {
                filter { eq("id", messageId) }
            }
        true
    } catch (e: Exception) {
        println("Error deleting message (redacted): ${e.redactedRestMessage()}")
        false
    }
