@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Message
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import compose.project.click.click.chat.attachments.AttachmentCrypto // pragma: allowlist secret
import compose.project.click.click.chat.attachments.ChatAttachmentValidator // pragma: allowlist secret
import compose.project.click.click.data.api.ConnectionTabMessage // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.repository.ConnectionRepository // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

internal fun formatFileSize(bytes: Long): String =
    when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_024L * 1_024 -> "${bytes / 1_024} KB"
        else -> "${(bytes * 10 / (1_024L * 1_024)) / 10.0} MB"
    }

internal fun formatProfileSheetDate(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${shortProfileMonth(local.monthNumber)} ${local.dayOfMonth}, ${local.year}"
}

internal fun formatProfileTimelineIso(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso.take(10)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${shortProfileMonth(local.monthNumber)} ${local.dayOfMonth}, ${local.year}"
}

internal fun profileMediaCacheKey(
    media: ProfileSheetMedia,
    chatId: String?,
    viewerUserId: String?,
): String {
    val source =
        media.storagePath?.trim()?.takeIf { it.isNotEmpty() }
            ?: media.mediaUrl?.trim()?.takeIf { it.isNotEmpty() }
            ?: media.id
    return listOf(
        chatId?.trim().orEmpty(),
        viewerUserId?.trim().orEmpty(),
        media.id,
        media.mediaType.name,
        source,
    ).joinToString("|")
}

internal suspend fun resolveProfileMediaUrl(
    media: ProfileSheetMedia,
    connectionRepository: ConnectionRepository,
): String? {
    media.mediaUrl
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it }
    val path = media.storagePath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    profileSignedUrlCache.get(path)?.let { return it }
    val signed = connectionRepository.getSignedChatAttachmentUrl(path)?.trim()?.takeIf { it.isNotEmpty() }
    if (signed != null) {
        profileSignedUrlCache.put(path, signed)
    }
    return signed
}

internal fun shortProfileMonth(monthNumber: Int): String =
    when (monthNumber) {
        1 -> "Jan"
        2 -> "Feb"
        3 -> "Mar"
        4 -> "Apr"
        5 -> "May"
        6 -> "Jun"
        7 -> "Jul"
        8 -> "Aug"
        9 -> "Sep"
        10 -> "Oct"
        11 -> "Nov"
        else -> "Dec"
    }

internal fun ProfileSheetLocalMessage.toProfileSheetMedia(): ProfileSheetMedia? {
    val meta = metadata as? JsonObject ?: return null
    val url =
        METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
            meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    val path =
        METADATA_PATH_KEYS.firstNotNullOfOrNull { key ->
            meta[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        }
    if (url == null && path == null) return null
    val lowerType = messageType.lowercase()
    val mediaType = if (lowerType == "audio") ProfileSheetMediaType.Audio else ProfileSheetMediaType.Image
    val (isDisposableRoll, collaborationTtlIso) = meta.disposableRollMetadata()
    return ProfileSheetMedia(
        id = id,
        mediaUrl = url,
        storagePath = path,
        mimeType =
            meta.stringAt("original_mime_type")
                ?: meta.stringAt("mime_type")
                ?: meta.stringAt("content_type"),
        isEncrypted =
            Message(
                id = id,
                user_id = "",
                content = content.trim().ifBlank { " " },
                timeCreated = 0L,
                messageType = lowerType,
                metadata = metadata,
            ).isEncryptedMedia(),
        mediaType = mediaType,
        captionedAt =
            content
                .takeUnless { it.isLikelyWireEncrypted() || AttachmentCrypto.isAttachmentEnvelope(it) }
                ?.takeIf { it.isNotBlank() },
        sortEpochMs = sortEpochMs,
        durationSeconds =
            meta.intAt("duration_seconds")
                ?: meta.intAt("durationSeconds")
                ?: meta["duration"]?.jsonPrimitive?.intOrNull,
        isDisposableRoll = isDisposableRoll,
        collaborationTtlIso = collaborationTtlIso,
    )
}

internal fun ConnectionTabMessage.toProfileSheetMediaFromTab(): ProfileSheetMedia? {
    val lowerType = messageType.lowercase()
    if (lowerType != "image" && lowerType != "audio") return null
    val meta = metadata as? JsonObject
    val url =
        METADATA_URL_KEYS.firstNotNullOfOrNull { key ->
            meta
                ?.get(key)
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }
    val path =
        METADATA_PATH_KEYS.firstNotNullOfOrNull { key ->
            meta
                ?.get(key)
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        }
    if (url == null && path == null) return null
    val (isDisposableRoll, collaborationTtlIso) = meta?.disposableRollMetadata() ?: (false to null)
    return ProfileSheetMedia(
        id = id,
        mediaUrl = url,
        storagePath = path,
        mimeType =
            meta?.stringAt("original_mime_type")
                ?: meta?.stringAt("mime_type")
                ?: meta?.stringAt("content_type"),
        isEncrypted =
            Message(
                id = id,
                user_id = userId,
                content = content.trim().ifBlank { " " },
                timeCreated = timeCreated,
                messageType = lowerType,
                metadata = metadata,
            ).isEncryptedMedia(),
        mediaType = if (lowerType == "audio") ProfileSheetMediaType.Audio else ProfileSheetMediaType.Image,
        captionedAt =
            content
                .takeUnless { it.isLikelyWireEncrypted() || AttachmentCrypto.isAttachmentEnvelope(it) }
                ?.takeIf { it.isNotBlank() },
        sortEpochMs = timeCreated,
        durationSeconds =
            meta?.let { m ->
                m.intAt("duration_seconds")
                    ?: m.intAt("durationSeconds")
                    ?: m["duration"]?.jsonPrimitive?.intOrNull
            },
        isDisposableRoll = isDisposableRoll,
        collaborationTtlIso = collaborationTtlIso,
    )
}

internal fun ProfileSheetLocalMessage.toProfileSheetFile(): ProfileSheetFile =
    buildProfileSheetFile(
        id = id,
        content = content,
        metadata = metadata,
        timestamp = timestamp,
    )

internal fun ConnectionTabMessage.toProfileSheetFileFromTab(): ProfileSheetFile =
    buildProfileSheetFile(
        id = id,
        content = content,
        metadata = metadata,
        timestamp = formatProfileSheetDate(timeCreated),
    )

internal fun buildProfileSheetFile(
    id: String,
    content: String,
    metadata: JsonElement?,
    timestamp: String,
): ProfileSheetFile {
    val envelope = AttachmentCrypto.resolveEnvelope(content, metadata)
    val meta = metadata as? JsonObject
    val fileName =
        envelope?.name?.takeIf { it.isNotBlank() }
            ?: meta?.firstString("attachment_name", "file_name", "filename", "name")
            ?: content
                .takeUnless { it.isLikelyWireEncrypted() || AttachmentCrypto.isAttachmentEnvelope(it) }
                ?.takeIf { it.isNotBlank() }
            ?: "Attachment"
    val size =
        envelope?.size
            ?: meta?.firstLong("attachment_size", "file_size", "size_bytes", "size")
            ?: 0L
    val mime =
        envelope?.mime?.takeIf { it.isNotBlank() }
            ?: meta?.firstString("attachment_mime", "mime_type", "content_type")
            ?: "application/octet-stream"
    val attachmentPath =
        envelope?.path?.takeIf { it.isNotBlank() }
            ?: meta?.stringAt("attachment_path")
            ?: meta?.stringAt("path")
            ?: meta?.stringAt("storage_path")
            ?: meta?.stringAt("object_path")
            ?: meta?.stringAt("media_path")
    val attachmentKeyBase64 =
        envelope?.key?.takeIf { it.isNotBlank() }
            ?: meta?.stringAt("key")
            ?: meta?.stringAt("file_key")
            ?: meta?.stringAt("file_master_key")
    val attachmentSha256Base64 =
        envelope?.sha256?.takeIf { it.isNotBlank() }
            ?: meta?.stringAt("sha256")
            ?: meta?.stringAt("sha256_base64")
    val downloadUrl =
        if (!attachmentPath.isNullOrBlank()) {
            null
        } else {
            meta?.stringAt("signed_url")
                ?: meta?.stringAt("public_url")
                ?: meta?.stringAt("url")
                ?: meta?.stringAt("storage_url")
                ?: meta?.stringAt("media_url")
        }
    return ProfileSheetFile(
        id = id,
        fileName = fileName,
        sizeBytes = size,
        mimeType = mime,
        timestamp = timestamp,
        downloadUrl = downloadUrl,
        attachmentPath = attachmentPath,
        attachmentKeyBase64 = attachmentKeyBase64,
        attachmentSha256Base64 = attachmentSha256Base64,
    )
}

internal fun ProfileSheetFile.canOpenProfileFile(): Boolean {
    val path = attachmentPath?.trim().orEmpty()
    if (path.isNotBlank()) {
        return attachmentKeyBase64?.trim().orEmpty().isNotBlank() &&
            attachmentSha256Base64?.trim().orEmpty().isNotBlank()
    }
    return !downloadUrl.isNullOrBlank()
}

internal fun ensureProfileAttachmentFileName(
    fileName: String,
    mimeType: String,
): String {
    val trimmed = fileName.trim().ifBlank { "attachment" }
    if (ChatAttachmentValidator.extensionOf(trimmed) != null) return trimmed
    val ext =
        when {
            mimeType.contains("pdf", ignoreCase = true) -> "pdf"
            mimeType.contains("png", ignoreCase = true) -> "png"
            mimeType.contains("jpeg", ignoreCase = true) || mimeType.contains("jpg", ignoreCase = true) -> "jpg"
            mimeType.contains("plain", ignoreCase = true) || mimeType.contains("text/", ignoreCase = true) -> "txt"
            mimeType.contains("csv", ignoreCase = true) -> "csv"
            mimeType.contains("zip", ignoreCase = true) -> "zip"
            mimeType.contains("mp4", ignoreCase = true) -> "mp4"
            mimeType.contains("quicktime", ignoreCase = true) -> "mov"
            mimeType.contains("wordprocessingml", ignoreCase = true) -> "docx"
            else -> return trimmed
        }
    return "$trimmed.$ext"
}

internal val METADATA_URL_KEYS =
    listOf(
        "signed_url",
        "public_url",
        "url",
        "storage_url",
        "image_url",
        "audio_url",
        "media_url",
    )

internal val METADATA_PATH_KEYS =
    listOf(
        "path",
        "storage_path",
        "object_path",
        "media_path",
    )

internal fun JsonObject.disposableRollMetadata(): Pair<Boolean, String?> {
    val isRoll = this["disposable_roll"]?.jsonPrimitive?.booleanOrNull == true
    val ttl =
        this["collaboration_ttl"]
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    return isRoll to ttl
}

internal fun ProfileSheetMedia.isDisposableRollLocked(now: Instant = Clock.System.now()): Boolean {
    if (!isDisposableRoll) return false
    val ttlIso = collaborationTtlIso ?: return true
    val ttl = runCatching { Instant.parse(ttlIso) }.getOrNull() ?: return true
    return now < ttl
}

internal fun ProfileSheetMedia.disposableRollCountdownLabel(): String? {
    if (!isDisposableRollLocked()) return null
    val ttlIso = collaborationTtlIso ?: return "Locked"
    val ttl = runCatching { Instant.parse(ttlIso) }.getOrNull() ?: return "Locked"
    val remainMs = (ttl.toEpochMilliseconds() - Clock.System.now().toEpochMilliseconds()).coerceAtLeast(0L)
    val totalMin = remainMs / 60_000L
    val hours = totalMin / 60L
    val mins = totalMin % 60L
    return if (hours > 0) "Reveals in ${hours}h ${mins}m" else "Reveals in ${mins}m"
}

internal fun ProfileSheetLocalMessage.hasMetadataMediaUrl(): Boolean {
    val meta = metadata as? JsonObject ?: return false
    return METADATA_URL_KEYS.any { key ->
        meta[key]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
    }
}

internal fun ProfileSheetLocalMessage.hasMetadataAttachmentV1(): Boolean {
    val meta = metadata as? JsonObject ?: return false
    val raw = meta["attachment_v"]?.jsonPrimitive ?: return false
    val asInt = raw.intOrNull ?: raw.contentOrNull?.toIntOrNull()
    if (asInt == 1) return true
    return raw.contentOrNull?.equals("true", ignoreCase = true) == true
}

internal fun JsonObject.stringAt(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

internal fun JsonObject.firstString(vararg keys: String): String? = keys.firstNotNullOfOrNull { key -> stringAt(key) }

internal fun JsonObject.firstLong(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key ->
        val raw = this[key]?.jsonPrimitive ?: return@firstNotNullOfOrNull null
        raw.longOrNull ?: raw.contentOrNull?.trim()?.toLongOrNull()
    }

internal fun JsonObject.intAt(key: String): Int? {
    val raw = this[key]?.jsonPrimitive ?: return null
    raw.intOrNull?.let { return it }
    raw.contentOrNull
        ?.trim()
        ?.toIntOrNull()
        ?.let { return it }
    return null
}

internal fun JsonObject.booleanAt(key: String): Boolean? {
    val raw = this[key]?.jsonPrimitive ?: return null
    raw.contentOrNull?.trim()?.lowercase()?.let { text ->
        if (text == "true" || text == "1") return true
        if (text == "false" || text == "0") return false
    }
    return null
}

internal fun String.isLikelyWireEncrypted(): Boolean {
    val text = trim()
    if (text.isBlank()) return false
    return text.startsWith("e2e:", ignoreCase = true)
}

internal fun profileMediaVaultExtension(media: ProfileSheetMedia): String {
    if (media.mediaType == ProfileSheetMediaType.Audio) {
        return extensionFromMimeType(media.mimeType)
    }
    val mt =
        media.mimeType
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return when {
        "png" in mt -> "png"
        "webp" in mt -> "webp"
        "gif" in mt -> "gif"
        else -> "jpg"
    }
}

internal fun extensionFromMimeType(mimeType: String?): String {
    val mt = mimeType?.trim()?.lowercase().orEmpty()
    return when {
        "wav" in mt -> "wav"
        "webm" in mt -> "webm"
        "ogg" in mt -> "ogg"
        "mpeg" in mt || "mp3" in mt -> "mp3"
        "aac" in mt -> "aac"
        else -> "m4a"
    }
}

internal fun normalizeExternalUri(raw: String): String {
    val value = raw.trim()
    if (value.isBlank()) return value
    return when {
        "://" in value -> value
        value.startsWith("/") -> "file://$value"
        else -> value
    }
}

/**
 * Regex matching bare `http://` / `https://` URLs in locally-decrypted text. Keep
 * this simple + conservative — we don't try to resolve punctuation-adjacent URLs
 * perfectly; the Links tab is a lightweight preview, not a full URL parser.
 */
internal val URL_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

/**
 * Extract http(s) URLs from a list of already-decrypted text messages. Runs
 * client-side because message `content` is E2EE on the server and the BFF
 * intentionally does not parse links.
 */
internal fun extractLinksFromLocalMessages(messages: List<ProfileSheetLocalMessage>): List<ProfileSheetLink> {
    if (messages.isEmpty()) return emptyList()
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ProfileSheetLink>()
    messages
        .filter {
            it.messageType == "text" &&
                (it.content.contains("http://") || it.content.contains("https://"))
        }.forEach { msg ->
            URL_REGEX.findAll(msg.content).forEach { match ->
                val url = match.value.trimEnd('.', ',', ')', ']', '}', ';', ':')
                if (url.isNotBlank() && seen.add(url)) {
                    out +=
                        ProfileSheetLink(
                            id = "${msg.id}:$url",
                            url = url,
                            title = null,
                            timestamp = msg.timestamp,
                        )
                }
            }
        }
    return out
}

internal fun mergeProfileMedia(items: List<ProfileSheetMedia>): List<ProfileSheetMedia> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetMedia>()
    items.forEach { media ->
        val prev = merged[media.id]
        if (prev == null) {
            merged[media.id] = media
            return@forEach
        }
        merged[media.id] =
            prev.copy(
                mediaUrl = media.mediaUrl ?: prev.mediaUrl,
                storagePath = media.storagePath ?: prev.storagePath,
                mimeType = media.mimeType ?: prev.mimeType,
                isEncrypted = media.isEncrypted || prev.isEncrypted,
                mediaType = if (prev.mediaType == ProfileSheetMediaType.Audio) prev.mediaType else media.mediaType,
                captionedAt = media.captionedAt ?: prev.captionedAt,
                sortEpochMs = maxOf(media.sortEpochMs, prev.sortEpochMs),
                durationSeconds = media.durationSeconds ?: prev.durationSeconds,
                isDisposableRoll = media.isDisposableRoll || prev.isDisposableRoll,
                collaborationTtlIso = media.collaborationTtlIso ?: prev.collaborationTtlIso,
            )
    }
    return merged.values
        .sortedByDescending { profileMediaSortEpoch(it) }
}

internal fun profileMediaSortEpoch(media: ProfileSheetMedia): Long {
    if (media.sortEpochMs > 0L) return media.sortEpochMs
    val raw = media.captionedAt?.trim().orEmpty()
    if (raw.isEmpty()) return 0L
    return runCatching {
        kotlinx.datetime.Instant
            .parse(raw)
            .toEpochMilliseconds()
    }.getOrNull()
        ?: raw.filter(Char::isDigit).takeLast(13).toLongOrNull()
        ?: 0L
}

internal fun mergeProfileFiles(items: List<ProfileSheetFile>): List<ProfileSheetFile> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetFile>()
    items.forEach { file ->
        val prev = merged[file.id]
        if (prev == null) {
            merged[file.id] = file
            return@forEach
        }
        merged[file.id] =
            prev.copy(
                fileName = if (file.fileName != "Attachment") file.fileName else prev.fileName,
                sizeBytes = if (file.sizeBytes > 0) file.sizeBytes else prev.sizeBytes,
                mimeType = if (file.mimeType != "application/octet-stream") file.mimeType else prev.mimeType,
                timestamp = if (file.timestamp.isNotBlank()) file.timestamp else prev.timestamp,
                downloadUrl = file.downloadUrl?.takeIf { it.isNotBlank() } ?: prev.downloadUrl,
                attachmentPath = file.attachmentPath?.takeIf { it.isNotBlank() } ?: prev.attachmentPath,
                attachmentKeyBase64 = file.attachmentKeyBase64?.takeIf { it.isNotBlank() } ?: prev.attachmentKeyBase64,
                attachmentSha256Base64 =
                    file.attachmentSha256Base64?.takeIf { it.isNotBlank() }
                        ?: prev.attachmentSha256Base64,
            )
    }
    return merged.values.toList()
}

internal fun mergeProfileLinks(items: List<ProfileSheetLink>): List<ProfileSheetLink> {
    if (items.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, ProfileSheetLink>()
    items.forEach { link ->
        val key = link.url.trim().lowercase()
        if (key.isNotBlank()) merged[key] = link
    }
    return merged.values.toList()
}
