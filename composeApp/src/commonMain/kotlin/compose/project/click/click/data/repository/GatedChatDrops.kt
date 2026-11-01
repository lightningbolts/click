package compose.project.click.click.data.repository

import compose.project.click.click.crypto.MessageCryptoV2
import compose.project.click.click.data.api.DropDevelopRef
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.isDisposableRollLocked
import compose.project.click.click.data.models.isGatedDrop
import compose.project.click.click.util.readChatMediaVaultBytes
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Gated Click Drops, receive side (click-web `lib/server/drops/chatDrops.ts`, iOS
 * `ChatRepository.loadDropOriginal`). The message's media is a pixelated preview; the E2EE original
 * sits in a private bucket and is signed only by `POST /api/drops/develop` after `reveal_at`.
 * It decrypts with `metadata.drop_original` (v2 chats) or the chat's legacy media keys (v1 chats).
 */

/** Vault key for a developed original, next to the preview stored under the message id (same as iOS). */
fun gatedDropOriginalVaultKey(messageId: String): String = "$messageId-original"

/**
 * Message ids whose developed original is on screen. Until a gated drop is listed here, the UI
 * keeps it pixelated and blocks save and share, so the preview is never kept as the photo.
 */
object GatedDropOriginals {
    private val _loaded = MutableStateFlow<Set<String>>(emptySet())
    val loaded: StateFlow<Set<String>> = _loaded.asStateFlow()

    fun isLoaded(messageId: String): Boolean = messageId in _loaded.value

    fun markLoaded(messageId: String) {
        if (messageId.isBlank()) return
        _loaded.update { it + messageId }
    }

    fun clear() {
        _loaded.value = emptySet()
    }
}

/** True while a gated drop's original isn't on screen yet: keep it pixelated and not exportable. */
fun Message.isGatedDropAwaitingOriginal(loaded: Set<String> = GatedDropOriginals.loaded.value): Boolean = isGatedDrop() && id !in loaded

/** `metadata.drop_original` = `{epoch, sender_device_id, client_message_id, media_ciphertext_sha256}`. */
internal fun Message.dropOriginalV2MetadataOrNull(chatId: String): MessageCryptoV2.MediaMetadata? {
    val fields = (metadata as? JsonObject)?.get("drop_original") as? JsonObject ?: return null
    fun string(key: String): String? =
        (fields[key] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    val digest = string("media_ciphertext_sha256") ?: return null
    val epoch = (fields["epoch"] as? JsonPrimitive)?.intOrNull ?: return null
    val device = string("sender_device_id") ?: return null
    val clientMessageId = string("client_message_id") ?: return null
    return MessageCryptoV2.MediaMetadata(
        chatId = chatId,
        epoch = epoch,
        senderDeviceId = device,
        clientMessageId = clientMessageId,
        mediaCiphertextSha256 = digest,
    )
}

/** What happened when this viewer tried to develop a gated drop. */
sealed class GatedDropOriginalResult {
    data class Developed(
        val bytes: ByteArray,
    ) : GatedDropOriginalResult()

    /** The server says it hasn't developed yet (clock skew); try again at reveal. */
    data object Pending : GatedDropOriginalResult()

    /** Not a gated drop, no access, or the download or decryption failed. */
    data object Unavailable : GatedDropOriginalResult()
}

internal suspend fun SupabaseChatRepository.loadGatedDropOriginalImpl(
    chatId: String,
    viewerUserId: String,
    message: Message,
    extension: String,
): GatedDropOriginalResult {
    if (!message.isGatedDrop() || message.isDisposableRollLocked()) return GatedDropOriginalResult.Unavailable
    val vaultKey = gatedDropOriginalVaultKey(message.id)
    readChatMediaVaultBytes(vaultKey, extension)?.takeIf { it.isNotEmpty() }?.let {
        return GatedDropOriginalResult.Developed(it)
    }
    val jwt = ensureFreshJwtForChat() ?: return GatedDropOriginalResult.Unavailable
    val refs = listOf(DropDevelopRef(kind = "chat", id = message.id))
    val result =
        apiClient.developDrops(refs, jwt).getOrElse { first ->
            val retry = refreshedJwtAfterAuthFailure()
            if (retry == null) {
                println("ChatRepository: drop develop failed: ${first.redactedRestMessage()}")
                return GatedDropOriginalResult.Unavailable
            }
            apiClient.developDrops(refs, retry).getOrElse { err ->
                println("ChatRepository: drop develop retry failed: ${err.redactedRestMessage()}")
                return GatedDropOriginalResult.Unavailable
            }
        }.firstOrNull { it.id == message.id }
            ?: return GatedDropOriginalResult.Unavailable
    when (result.status) {
        "pending" -> return GatedDropOriginalResult.Pending
        "developed" -> Unit
        else -> return GatedDropOriginalResult.Unavailable
    }
    val signedUrl = result.url?.trim()?.takeIf { it.isNotEmpty() } ?: return GatedDropOriginalResult.Unavailable
    val v2 = message.dropOriginalV2MetadataOrNull(chatId)
    val bytes =
        downloadAndDecryptChatMediaImpl(
            chatId = chatId,
            viewerUserId = viewerUserId,
            mediaUrl = signedUrl,
            v2Metadata = v2,
        )?.takeIf { it.isNotEmpty() } ?: return GatedDropOriginalResult.Unavailable
    writeChatMediaVaultFile(vaultKey, bytes, extension)
    return GatedDropOriginalResult.Developed(bytes)
}
