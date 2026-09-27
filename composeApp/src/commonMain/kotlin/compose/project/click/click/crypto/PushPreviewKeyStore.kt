@file:OptIn(ExperimentalEncodingApi::class)

package compose.project.click.click.crypto

import compose.project.click.click.data.storage.TokenStorage
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Recent E2EE v2 epoch keys, persisted in the app's encrypted storage so the FCM service can
 * decrypt `e2e2:` push previews on-device (the Android analogue of iOS `SharedEpochKeyStore`,
 * which shares keys with the Notification Service Extension). Bounded, and wiped at sign-out.
 * Push previews therefore never need server plaintext.
 */
object PushPreviewKeyStore {
    const val MAX_EPOCHS_PER_CHAT: Int = 3
    const val MAX_CHATS: Int = 200

    private val serializer = MapSerializer(String.serializer(), MapSerializer(String.serializer(), String.serializer()))

    /** Pure merge: keep the newest [MAX_EPOCHS_PER_CHAT] epochs per chat, most recent chats last. */
    internal fun merge(
        existing: Map<String, Map<String, String>>,
        chatId: String,
        epochKeys: Map<Int, String>,
    ): Map<String, Map<String, String>> {
        val combined =
            (existing[chatId].orEmpty() + epochKeys.mapKeys { it.key.toString() })
                .entries
                .sortedByDescending { it.key.toIntOrNull() ?: Int.MIN_VALUE }
                .take(MAX_EPOCHS_PER_CHAT)
                .associate { it.key to it.value }
        val reordered = LinkedHashMap(existing).apply { remove(chatId) }
        reordered[chatId] = combined
        return reordered.entries
            .toList()
            .takeLast(MAX_CHATS)
            .associate { it.key to it.value }
    }

    private suspend fun load(storage: TokenStorage): Map<String, Map<String, String>> =
        storage.getPushPreviewKeys()?.let { runCatching { Json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    /** Records [epochKeys] for [chatId] (encodes immediately; callers may zeroize afterwards). */
    suspend fun remember(
        storage: TokenStorage,
        chatId: String,
        epochKeys: Map<Int, ByteArray>,
    ) {
        if (chatId.isBlank() || epochKeys.isEmpty()) return
        val encoded = epochKeys.mapValues { Base64.encode(it.value) }
        runCatching { storage.savePushPreviewKeys(Json.encodeToString(serializer, merge(load(storage), chatId, encoded))) }
    }

    suspend fun keyFor(
        storage: TokenStorage,
        chatId: String,
        epoch: Int,
    ): ByteArray? = load(storage)[chatId]?.get(epoch.toString())?.let { runCatching { Base64.decode(it) }.getOrNull() }

    /** Decrypts an `e2e2:` message envelope, or null when it isn't one or no key is stored. */
    suspend fun decryptPreview(
        storage: TokenStorage,
        wire: String,
    ): String? {
        if (!MessageCryptoV2.isEncrypted(wire)) return null
        val envelope =
            runCatching { MessageCryptoV2.parseE2eeV2Envelope(wire) }.getOrNull() as? MessageCryptoV2.MessageEnvelope
                ?: return null
        val key = keyFor(storage, envelope.chatId, envelope.epoch) ?: return null
        return runCatching {
            MessageCryptoV2.decryptMessage(
                metadata =
                    MessageCryptoV2.MessageMetadata(
                        chatId = envelope.chatId,
                        epoch = envelope.epoch,
                        senderDeviceId = envelope.senderDeviceId,
                        clientMessageId = envelope.clientMessageId,
                    ),
                epochKey = key,
                envelope = wire,
            )
        }.getOrNull().also { key.fill(0) }
    }
}
