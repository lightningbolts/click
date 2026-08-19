package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.ErrorResponse // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.StoredEventBookmark // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserCore // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** POST `/api/connections/archive` — per-user archive junction (`connection_archives`). */
internal suspend fun ApiClient.postConnectionArchiveImpl(connectionId: String): Result<Unit> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/archive") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** POST `/api/connections/unarchive` — remove archive row and restore lifecycle (`kept`). */
internal suspend fun ApiClient.postConnectionUnarchiveImpl(connectionId: String): Result<Unit> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/unarchive") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** POST `/api/connections/core` — per-user core pin (`connection_core`). */
internal suspend fun ApiClient.postConnectionCoreImpl(connectionId: String): Result<Unit> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/core") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** GET `/api/connections/core` — core connection IDs for the signed-in user. */
internal suspend fun ApiClient.fetchConnectionCoreIdsImpl(): Result<Set<String>> =
    try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/connections/core")
        if (response.status.value in 200..299) {
            val body = response.body<ConnectionCoreListResponse>()
            Result.success(
                body.core
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet(),
            )
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** DELETE `/api/connections/core?connection_id=` — remove from core list. */
internal suspend fun ApiClient.deleteConnectionCoreImpl(connectionId: String): Result<Unit> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return try {
        val response =
            clickWebClient.delete(
                "${ApiClient.clickWebAuthOrigin}/api/connections/core?connection_id=$id",
            )
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** POST `/api/connections/hide` — per-user hide (`connection_hidden`) for the JWT user only. */
internal suspend fun ApiClient.postConnectionHideImpl(connectionId: String): Result<Unit> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/hide") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** POST `/api/connections/{id}/collaboration-session` — opens Disposable Roll window. */
internal suspend fun ApiClient.postOpenCollaborationSessionImpl(connectionId: String): Result<CollaborationSessionPostResponse> {
    val cid = connectionId.trim()
    if (cid.isEmpty()) return Result.failure(IllegalArgumentException("connectionId required"))
    return try {
        val response =
            clickWebClient.post(
                "${ApiClient.clickWebAuthOrigin}/api/connections/$cid/collaboration-session",
            ) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {})
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<CollaborationSessionPostResponse>())
        } else {
            clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** POST `/api/chats/{id}/collaboration-session` — opens Disposable Roll window for group chats. */
internal suspend fun ApiClient.postOpenCollaborationSessionForChatImpl(chatId: String): Result<CollaborationSessionPostResponse> {
    val cid = chatId.trim()
    if (cid.isEmpty()) return Result.failure(IllegalArgumentException("chatId required"))
    return try {
        val response =
            clickWebClient.post(
                "${ApiClient.clickWebAuthOrigin}/api/chats/$cid/collaboration-session",
            ) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {})
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<CollaborationSessionPostResponse>())
        } else {
            clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Fallback when the dedicated collaboration-session route is not deployed yet.
 * POST `/api/connections/encounter` with `{ connection_id, open_disposable_roll: true }`.
 */
internal suspend fun ApiClient.postOpenCollaborationSessionFallbackImpl(connectionId: String): Result<CollaborationSessionPostResponse> {
    val cid = connectionId.trim()
    if (cid.isEmpty()) return Result.failure(IllegalArgumentException("connectionId required"))
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/encounter") {
                contentType(ContentType.Application.Json)
                // encodeDefaults is off — must emit open_disposable_roll explicitly or the
                // server falls through to the legacy user_id/peer_id encounter validator.
                setBody(
                    buildJsonObject {
                        put("connection_id", cid)
                        put("open_disposable_roll", true)
                    },
                )
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<CollaborationSessionPostResponse>())
        } else {
            clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
