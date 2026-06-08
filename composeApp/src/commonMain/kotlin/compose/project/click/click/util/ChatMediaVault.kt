package compose.project.click.click.util

import compose.project.click.click.chat.attachments.AttachmentCrypto
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.audioCacheFileExtension
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.data.models.originalMimeTypeOrNull

/** Native directory for decrypted chat media vault files. */
expect fun chatMediaVaultDirectory(): String

/** Writes decrypted chat media to the native cache and returns a file:// URI for UI playback. */
expect fun writeChatMediaVaultFile(messageId: String, bytes: ByteArray, extension: String): String?

/** Reads decrypted bytes from the on-device vault when present. */
expect fun readChatMediaVaultBytes(messageId: String, preferredExtension: String? = null): ByteArray?

/** Local filesystem path for a vaulted file when it already exists. */
expect fun chatMediaVaultLocalPath(messageId: String, preferredExtension: String? = null): String?

/** Reads bytes from a file:// URI produced by the vault or message metadata. */
expect fun readFileUriBytes(fileUri: String): ByteArray?

private val VAULT_IMAGE_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp", "heic", "gif")
private val VAULT_AUDIO_EXTENSIONS = listOf("m4a", "mp3", "wav", "webm", "ogg", "aac", "caf")
private val VAULT_FILE_EXTENSIONS = listOf(
    "pdf", "zip", "bin", "txt", "json", "csv", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
    "mp4", "mov", "mkv", "epub", "pages", "numbers", "key",
)

internal fun vaultFileExtensionCandidates(preferredExtension: String?): List<String> {
    val preferred = preferredExtension
        ?.trim()
        ?.trimStart('.')
        ?.lowercase()
        ?.takeIf { it.isNotEmpty() }
    return buildList {
        if (preferred != null) add(preferred)
        VAULT_IMAGE_EXTENSIONS.forEach { ext -> if (ext !in this) add(ext) }
        VAULT_AUDIO_EXTENSIONS.forEach { ext -> if (ext !in this) add(ext) }
        VAULT_FILE_EXTENSIONS.forEach { ext -> if (ext !in this) add(ext) }
    }
}

fun imageVaultFileExtension(mimeType: String?, mediaUrl: String): String {
    val mime = mimeType?.lowercase().orEmpty()
    return when {
        "png" in mime -> "png"
        "webp" in mime -> "webp"
        "gif" in mime -> "gif"
        "heic" in mime || "heif" in mime -> "heic"
        "jpeg" in mime || "jpg" in mime -> "jpg"
        else -> mediaUrl.substringBefore('?')
            .substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .lowercase()
            .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
            ?: "jpg"
    }
}

fun AttachmentCrypto.Envelope.vaultCacheExtension(): String {
    val fromName = name.substringBefore('?')
        .substringAfterLast('/', name)
        .substringAfterLast('.', "")
        .lowercase()
        .takeIf { it.length in 2..5 && it.all { ch -> ch.isLetterOrDigit() } }
    if (fromName != null) return fromName
    val mime = mime.lowercase()
    return when {
        "pdf" in mime -> "pdf"
        "png" in mime -> "png"
        "jpeg" in mime || "jpg" in mime -> "jpg"
        "webp" in mime -> "webp"
        "gif" in mime -> "gif"
        "mpeg" in mime || "mp3" in mime -> "mp3"
        "wav" in mime -> "wav"
        "webm" in mime -> "webm"
        "ogg" in mime -> "ogg"
        "zip" in mime -> "zip"
        "plain" in mime && "text" in mime -> "txt"
        "json" in mime -> "json"
        "csv" in mime -> "csv"
        else -> "bin"
    }
}

fun chatMediaVaultExtensionForMessage(message: Message): String? {
    return when (message.messageType.lowercase()) {
        ChatMessageType.IMAGE -> imageVaultFileExtension(
            message.originalMimeTypeOrNull(),
            message.mediaUrlOrNull().orEmpty(),
        )
        ChatMessageType.AUDIO -> message.audioCacheFileExtension()
        ChatMessageType.FILE -> AttachmentCrypto.resolveEnvelope(message.content, message.metadata)
            ?.vaultCacheExtension()
        else -> null
    }
}

fun fileUriToLocalPath(fileUri: String): String = fileUri.trim().removePrefix("file://")

fun isChatMediaVaultLocalPath(path: String): Boolean {
    val normalized = path.trim().lowercase()
    return normalized.contains("click_media_vault")
}

fun readChatMediaVaultBytesForMessage(
    messageId: String,
    mediaUrl: String? = null,
    preferredExtension: String? = null,
): ByteArray? {
    if (messageId.isNotBlank()) {
        readChatMediaVaultBytes(messageId, preferredExtension)
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    val uri = mediaUrl?.trim().orEmpty()
    if (uri.startsWith("file://", ignoreCase = true)) {
        readFileUriBytes(uri)?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

fun readChatMediaVaultLocalPathForMessage(
    messageId: String,
    preferredExtension: String? = null,
    mediaUrl: String? = null,
): String? {
    val uri = mediaUrl?.trim().orEmpty()
    if (uri.startsWith("file://", ignoreCase = true)) {
        val path = fileUriToLocalPath(uri)
        if (path.isNotBlank()) return path
    }
    return chatMediaVaultLocalPath(messageId, preferredExtension)
}
