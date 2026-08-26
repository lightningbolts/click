@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.* // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** Upload ciphertext bytes to chat-media via gatekeeper; returns the public media URL. */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun ChatApiClient.uploadMediaImpl(
    fileBytes: ByteArray,
    chatId: String,
    mimeType: String,
    authToken: String,
): Result<String> {
    if (fileBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty media"))
    return try {
        val encoded = Base64.encode(fileBytes)
        val response =
            client.post("$clickWebBaseUrl/api/chat/media") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ChatMediaUploadJsonBody(
                        chatId = chatId,
                        mimeType = mimeType.ifBlank { "application/octet-stream" },
                        fileBase64 = encoded,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            val payload = response.body<ChatMediaUploadUrlResponse>()
            val url = payload.url?.trim().orEmpty()
            if (url.isNotEmpty()) {
                Result.success(url)
            } else {
                Result.failure(Exception("Upload response missing media url"))
            }
        } else {
            Result.failure(Exception("Failed to upload media: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Upload already-encrypted attachment bytes to the `chat-attachments` bucket via the Next.js
 * gatekeeper. Returns the canonical object path plus a short-lived signed URL for immediate
 * re-use by the uploader. The server writes under `{chatId}/{uid}/{safe_filename}` to satisfy
 * the Storage RLS policies; never set request-level `multipart/form-data` without a boundary.
 */
@OptIn(ExperimentalEncodingApi::class)
internal suspend fun ChatApiClient.uploadAttachmentImpl(
    fileBytes: ByteArray,
    chatId: String,
    mimeType: String,
    fileName: String,
    authToken: String,
): Result<ChatApiClient.UploadedAttachment> {
    if (fileBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty attachment"))
    if (fileName.isBlank()) return Result.failure(IllegalArgumentException("file_name is required"))
    return try {
        val encoded = Base64.encode(fileBytes)
        val response =
            client.post("$clickWebBaseUrl/api/chat/attachments") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ChatAttachmentUploadJsonBody(
                        chatId = chatId,
                        mimeType = mimeType.ifBlank { "application/octet-stream" },
                        fileName = fileName,
                        fileBase64 = encoded,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            val payload = response.body<ChatAttachmentUploadResponse>()
            val path = payload.path.trim()
            if (path.isNotEmpty()) {
                Result.success(ChatApiClient.UploadedAttachment(path = path, initialSignedUrl = payload.url))
            } else {
                Result.failure(Exception("Attachment upload response missing path"))
            }
        } else {
            Result.failure(Exception("Failed to upload attachment: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Mint a fresh signed URL for an existing `chat-attachments` object path. */
internal suspend fun ChatApiClient.signAttachmentUrlImpl(
    path: String,
    authToken: String,
): Result<String> {
    if (path.isBlank()) return Result.failure(IllegalArgumentException("path is required"))
    return try {
        val response =
            client.post("$clickWebBaseUrl/api/chat/attachments/sign") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(ChatAttachmentSignBody(path = path))
            }
        if (response.status.value in 200..299) {
            val payload = response.body<ChatAttachmentSignResponse>()
            val url = payload.url?.trim().orEmpty()
            if (url.isNotEmpty()) {
                Result.success(url)
            } else {
                Result.failure(Exception("Attachment sign response missing url"))
            }
        } else {
            Result.failure(Exception("Failed to sign attachment: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Download raw ciphertext bytes for a signed attachment URL. */
internal suspend fun ChatApiClient.downloadAttachmentBytesImpl(signedUrl: String): Result<ByteArray> =
    try {
        val response = client.get(signedUrl)
        if (response.status.value in 200..299) {
            Result.success(response.body<ByteArray>())
        } else {
            Result.failure(Exception("Attachment download failed: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
