@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.* // pragma: allowlist secret
import compose.project.click.click.data.repository.AuthRepository // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.qr.CLICK_WEB_BASE_URL // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Insert a hub message via Next.js gatekeeper (JWT + geofence). Realtime still delivers rows to clients.
 */
internal suspend fun ChatApiClient.sendHubMessageImpl(
    hubId: String,
    body: String,
    userLat: Double,
    userLong: Double,
    authToken: String,
    messageType: String? = null,
    metadata: JsonElement? = null,
): Result<ChatApiClient.HubMessageApiDto> =
    try {
        val response =
            client.post("$clickWebBaseUrl/api/hub/messages") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(
                    ClickWebHubSendMessageBody(
                        hubId = hubId,
                        body = body,
                        userLat = userLat,
                        userLong = userLong,
                        messageType = messageType,
                        metadata = metadata,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ClickWebHubMessageEnvelope>().message)
        } else {
            val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            val message =
                when {
                    errorBody.contains("OUT_OF_BOUNDS") -> "OUT_OF_BOUNDS"
                    errorBody.contains("HUB_EXPIRED") ||
                        errorBody.contains("Hub expired", ignoreCase = true) ||
                        response.status.value == 410 -> "HUB_EXPIRED"
                    else -> "Failed to send hub message: ${response.status}"
                }
            Result.failure(Exception(message))
        }
    } catch (e: Exception) {
        println("Error sending hub message: ${e.redactedRestMessage()}")
        Result.failure(e)
    }

/**
 * Upload hub ciphertext to chat-media; [objectPath] must be `{userId}/hub/{hubId}/...`.
 * Same rule as [uploadMedia]: never set request-level `multipart/form-data` without boundary.
 */
internal suspend fun ChatApiClient.uploadHubMediaImpl(
    fileBytes: ByteArray,
    hubId: String,
    mimeType: String,
    objectPath: String,
    authToken: String,
    userLat: Double,
    userLong: Double,
): Result<String> {
    if (fileBytes.isEmpty()) return Result.failure(IllegalArgumentException("Empty media"))
    return try {
        val response =
            client.post("$clickWebBaseUrl/api/hub/media") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("hub_id", hubId)
                            append("object_path", objectPath)
                            append("mime_type", mimeType.ifBlank { "application/octet-stream" })
                            append("user_lat", userLat.toString())
                            append("user_long", userLong.toString())
                            append("file", fileBytes, encryptedUploadFileHeaders())
                        },
                    ),
                )
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ChatMediaUploadPathResponse>().path)
        } else {
            val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            val message =
                when {
                    errorBody.contains("OUT_OF_BOUNDS") -> "OUT_OF_BOUNDS"
                    errorBody.contains("HUB_EXPIRED") ||
                        errorBody.contains("Hub expired", ignoreCase = true) ||
                        response.status.value == 410 -> "HUB_EXPIRED"
                    else -> "Failed to upload hub media: ${response.status}"
                }
            Result.failure(Exception(message))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ChatApiClient.addCliqueMemberImpl(
    groupId: String,
    newMemberUserId: String,
    authToken: String,
): Result<Unit> =
    try {
        val body =
            buildJsonObject {
                put("group_id", groupId)
                put("new_member_user_id", newMemberUserId)
            }
        val response =
            client.post("$clickWebBaseUrl/api/cliques/members") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            Result.failure(Exception(errorBody.ifBlank { "Failed to add member: ${response.status}" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ChatApiClient.removeCliqueMemberImpl(
    groupId: String,
    memberUserId: String,
    authToken: String,
): Result<Unit> =
    try {
        val body =
            buildJsonObject {
                put("group_id", groupId)
                put("member_user_id", memberUserId)
            }
        val response =
            client.delete("$clickWebBaseUrl/api/cliques/members") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            val errorBody = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            Result.failure(Exception(errorBody.ifBlank { "Failed to remove member: ${response.status}" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ChatApiClient.updateHubImpl(
    hubId: String,
    name: String?,
    category: String?,
    authToken: String,
): Result<Unit> =
    try {
        val body =
            buildJsonObject {
                if (name != null) put("name", name)
                if (category != null) put("category", category)
            }
        val response =
            client.patch("$clickWebBaseUrl/api/hub/$hubId") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to update hub: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ChatApiClient.getHubDetailsImpl(
    hubId: String,
    authToken: String,
): Result<ChatApiClient.HubDetailsDto> =
    try {
        val response =
            client.get("$clickWebBaseUrl/api/hub/$hubId") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<HubDetailsEnvelope>().hub)
        } else {
            val message = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            Result.failure(Exception(message.ifBlank { "Failed to fetch hub: ${response.status}" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ChatApiClient.deleteHubImpl(
    hubId: String,
    authToken: String,
): Result<Unit> =
    try {
        val response =
            client.delete("$clickWebBaseUrl/api/hub/$hubId") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to delete hub: ${response.status}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

internal suspend fun ChatApiClient.leaveHubImpl(
    hubId: String,
    authToken: String,
): Result<Unit> =
    try {
        val response =
            client.post("$clickWebBaseUrl/api/hub/leave") {
                headers.append(HttpHeaders.Authorization, bearerAuthHeader(authToken))
                contentType(ContentType.Application.Json)
                setBody(HubLeaveRequestBody(hubId = hubId))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            val message = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            Result.failure(Exception(message.ifBlank { "Failed to leave hub: ${response.status}" }))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * GET /api/hub/messages — participant-gated hub timeline + occupant ids.
 */
internal suspend fun ChatApiClient.fetchHubThreadImpl(
    hubId: String,
    authToken: String,
    aroundMessageId: String? = null,
    limit: Int = 120,
): Result<ChatApiClient.HubThreadResponse> =
    try {
        val id = hubId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("hubId is required"))

        suspend fun getOnce(bearer: String): Result<ChatApiClient.HubThreadResponse> {
            val response =
                client.get("$clickWebBaseUrl/api/hub/messages") {
                    headers.append(HttpHeaders.Authorization, bearerAuthHeader(bearer))
                    parameter("hubId", id)
                    parameter("limit", limit.coerceIn(1, 120).toString())
                    aroundMessageId?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("aroundMessageId", it) }
                    accept(ContentType.Application.Json)
                }
            val parsed = runCatching { response.body<ChatApiClient.HubThreadResponse>() }.getOrNull()
            return when {
                response.status.value in 200..299 && parsed != null -> Result.success(parsed)
                response.status.value == 401 -> Result.failure(Exception("HTTP 401 for hub thread"))
                response.status.value == 403 ->
                    Result.failure(Exception(parsed?.error?.takeIf { it.isNotBlank() } ?: "NOT_A_PARTICIPANT"))
                else -> Result.failure(Exception("HTTP ${response.status} for hub thread"))
            }
        }
        val initial =
            resolveClickWebAccessToken(tokenStorage)
                ?: authToken.trim().takeIf { it.isNotEmpty() }
        if (initial.isNullOrBlank()) {
            return Result.failure(Exception("HTTP 401 for hub thread"))
        }
        val first = getOnce(initial)
        val msg = first.exceptionOrNull()?.message.orEmpty()
        if (msg.contains("401")) {
            AuthRepository(tokenStorage).refreshSession(forceRefresh = true)
            val retry = resolveClickWebAccessToken(tokenStorage, forceRefresh = true)
            if (!retry.isNullOrBlank()) return getOnce(retry)
        }
        first
    } catch (e: Exception) {
        println("Error fetching hub thread: ${e.redactedRestMessage()}")
        Result.failure(e)
    }
