package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * POST `/api/connections/proximity` — tri-factor proximity bind (JWT bearer).
 * Returns [ProximityHandshakePostResult.InstantMatch] on HTTP 200,
 * [ProximityHandshakePostResult.PendingMatch] on HTTP 202 (peer not online yet),
 * or HTTP 503 with `pending_handshake_id` (connection create failed; GET recovery).
 */
internal suspend fun ApiClient.postProximityHandshakeImpl(
    body: ProximityHandshakePostBody,
    bearerJwt: String? = null,
): Result<ProximityHandshakePostResult> {
    return try {
        val response: HttpResponse =
            clickWebClient.post(
                "${ApiClient.clickWebAuthOrigin}/api/connections/proximity",
            ) {
                contentType(ContentType.Application.Json)
                bearerJwt?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(body)
            }
        when (response.status.value) {
            200 -> {
                val raw = response.bodyAsText()
                if (raw.contains("ignored_empty_payload")) {
                    val ignored = json.decodeFromString(ProximityBindIgnoredResponseDto.serializer(), raw)
                    return Result.success(ProximityHandshakePostResult.IgnoredEmptyPayload(ignored))
                }
                val dto = response.body<ProximityBindOkResponseDto>()
                if (!dto.error.isNullOrBlank()) {
                    Result.failure(Exception(dto.error))
                } else {
                    Result.success(ProximityHandshakePostResult.InstantMatch(dto))
                }
            }
            202 -> {
                val dto = response.body<ProximityBindPendingResponseDto>()
                Result.success(ProximityHandshakePostResult.PendingMatch(dto))
            }
            503 -> {
                val raw = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
                val unavailable =
                    runCatching {
                        json.decodeFromString(ProximityBindUnavailableResponseDto.serializer(), raw)
                    }.getOrNull()
                val pendingId = unavailable?.pendingHandshakeId?.trim().orEmpty()
                if (pendingId.isNotEmpty()) {
                    Result.success(
                        ProximityHandshakePostResult.PendingMatch(
                            ProximityBindPendingResponseDto(
                                success = false,
                                status = unavailable?.error ?: "connection_unavailable",
                                pendingHandshakeId = pendingId,
                                expiresAt = unavailable?.expiresAt.orEmpty(),
                            ),
                        ),
                    )
                } else {
                    clickWebFailure(response)
                }
            }
            else -> clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        val status = e.response.status.value
        if (status == 503) {
            val raw = runCatching { e.response.bodyAsText() }.getOrNull().orEmpty()
            val unavailable =
                runCatching {
                    json.decodeFromString(ProximityBindUnavailableResponseDto.serializer(), raw)
                }.getOrNull()
            val pendingId = unavailable?.pendingHandshakeId?.trim().orEmpty()
            if (pendingId.isNotEmpty()) {
                return Result.success(
                    ProximityHandshakePostResult.PendingMatch(
                        ProximityBindPendingResponseDto(
                            success = false,
                            status = unavailable?.error ?: "connection_unavailable",
                            pendingHandshakeId = pendingId,
                            expiresAt = unavailable?.expiresAt.orEmpty(),
                        ),
                    ),
                )
            }
        }
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * POST `/api/connections/proximity/confirm` — host confirms selected members after
 * `awaiting_selection` on a multi-peer first-time bind.
 */
internal suspend fun ApiClient.postProximityConfirmSelectionImpl(
    bearerJwt: String,
    pendingHandshakeId: String,
    selectedMemberIds: List<String>,
    contextTags: List<String>? = null,
): Result<ProximityBindOkResponseDto> {
    val pendingId = pendingHandshakeId.trim()
    if (pendingId.isEmpty()) {
        return Result.failure(IllegalArgumentException("pendingHandshakeId required"))
    }
    val members =
        selectedMemberIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            // Server adds host; max member set is PROXIMITY_HOST_SELECTION_MAX_MEMBERS (12).
            .take(11)
    if (members.isEmpty()) {
        return Result.failure(IllegalArgumentException("selectedMemberIds required"))
    }
    val tags =
        contextTags
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
    return try {
        val response: HttpResponse =
            clickWebClient.post(
                "${ApiClient.clickWebAuthOrigin}/api/connections/proximity/confirm",
            ) {
                contentType(ContentType.Application.Json)
                bearerJwt.trim().takeIf { it.isNotEmpty() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(
                    ProximityConfirmSelectionPostBody(
                        pendingHandshakeId = pendingId,
                        selectedMemberIds = members,
                        contextTags = tags,
                    ),
                )
            }
        when (response.status.value) {
            in 200..299 -> {
                val dto = response.body<ProximityBindOkResponseDto>()
                if (!dto.error.isNullOrBlank()) {
                    Result.failure(Exception(dto.error))
                } else {
                    Result.success(dto)
                }
            }
            else -> clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** GET `/api/connections/proximity` — recover a previously accepted pending handshake. */
internal suspend fun ApiClient.getPendingProximityHandshakeImpl(
    pendingHandshakeId: String,
    bearerJwt: String? = null,
): Result<ProximityHandshakePostResult> {
    val pendingId = pendingHandshakeId.trim()
    if (pendingId.isEmpty()) {
        return Result.failure(IllegalArgumentException("pendingHandshakeId required"))
    }
    return try {
        val response: HttpResponse =
            clickWebClient.get(
                "${ApiClient.clickWebAuthOrigin}/api/connections/proximity",
            ) {
                bearerJwt?.trim()?.takeIf { it.isNotEmpty() }?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                parameter("pending_handshake_id", pendingId)
            }
        when (response.status.value) {
            200 -> {
                val dto = response.body<ProximityBindOkResponseDto>()
                if (!dto.error.isNullOrBlank()) {
                    Result.failure(Exception(dto.error))
                } else {
                    Result.success(ProximityHandshakePostResult.InstantMatch(dto))
                }
            }
            202 -> {
                val dto = response.body<ProximityBindPendingResponseDto>()
                Result.success(ProximityHandshakePostResult.PendingMatch(dto))
            }
            else -> clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** Cold-start prewarm: invalid `my_token` exercises JWT/auth without inserting a row. */
internal suspend fun ApiClient.prewarmProximityHandshakeImpl() {
    runCatching {
        clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/proximity") {
            contentType(ContentType.Application.Json)
            setBody(
                ProximityHandshakePostBody(
                    myToken = "",
                    tokens = emptyList(),
                    heardTokens = emptyList(),
                ),
            )
        }
    }
}
