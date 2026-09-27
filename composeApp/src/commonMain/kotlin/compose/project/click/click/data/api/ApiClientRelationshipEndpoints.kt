package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.json.JsonObject

/*
 * Relationship moments (click-web migration `20260926090000_relationship_moments.sql`):
 * logged hangouts + confirmations, waves, and opt-in hangout detection pings.
 * Mirrors iOS `Core/Connections/RelationshipRepository.swift`.
 */

private suspend inline fun <T> ApiClient.clickWebCall(
    crossinline request: suspend () -> HttpResponse,
    crossinline onSuccess: suspend (HttpResponse) -> T,
): Result<T> =
    try {
        val response = request()
        if (response.status.value in 200..299) {
            Result.success(onSuccess(response))
        } else {
            clickWebFailure(response)
        }
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }

/** GET `/api/hangouts` — pending hangouts the viewer is part of (max 20, unexpired). */
internal suspend fun ApiClient.getPendingHangoutsImpl(): Result<List<PendingHangoutDto>> =
    clickWebCall(
        request = { clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/hangouts") },
        onSuccess = { it.body<PendingHangoutsResponseDto>().hangouts },
    )

/** POST `/api/hangouts` — log a hangout; the viewer is pre-confirmed and the peer is asked. */
internal suspend fun ApiClient.logHangoutImpl(body: LogHangoutBody): Result<PendingHangoutDto> {
    if (body.connectionId.isBlank()) return Result.failure(IllegalArgumentException("Missing connection id"))
    val result =
        clickWebCall(
            request = {
                clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/hangouts") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            },
            onSuccess = { it.body<HangoutEnvelopeDto>().hangout },
        )
    val error = result.exceptionOrNull()
    if (error is ClickWebRequestException && error.statusCode == 409) {
        return Result.failure(HangoutPendingExistsException(error.message ?: "A hangout is already waiting to be confirmed"))
    }
    return result
}

/** POST `/api/hangouts/{id}/confirm` — the last confirmation records the encounter. */
internal suspend fun ApiClient.confirmHangoutImpl(hangoutId: String): Result<HangoutConfirmResponseDto> {
    val id = hangoutId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing hangout id"))
    return clickWebCall(
        request = {
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/hangouts/${id.encodeURLPathPart()}/confirm") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        },
        onSuccess = { it.body<HangoutConfirmResponseDto>() },
    )
}

/** POST `/api/hangouts/{id}/decline` — "Not us". */
internal suspend fun ApiClient.declineHangoutImpl(hangoutId: String): Result<Unit> {
    val id = hangoutId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing hangout id"))
    return clickWebCall(
        request = {
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/hangouts/${id.encodeURLPathPart()}/decline") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        },
        onSuccess = { },
    )
}

/** POST `/api/connections/{id}/wave` — one wave per pair per day; waving back resolves the peer's wave. */
internal suspend fun ApiClient.waveImpl(connectionId: String): Result<WaveResponseDto> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
    return clickWebCall(
        request = {
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/connections/${id.encodeURLPathPart()}/wave") {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        },
        onSuccess = { it.body<WaveResponseDto>() },
    )
}

/** POST `/api/me/presence` — opt-in hangout detection ping (foreground only). */
internal suspend fun ApiClient.reportPresenceImpl(
    lat: Double,
    lon: Double,
): Result<PresencePingResponseDto> =
    clickWebCall(
        request = {
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/me/presence") {
                contentType(ContentType.Application.Json)
                setBody(PresencePingBody(lat = lat, lon = lon))
            }
        },
        onSuccess = { it.body<PresencePingResponseDto>() },
    )

/** DELETE `/api/me/presence` — called when hangout detection is turned off. */
internal suspend fun ApiClient.clearPresenceImpl(): Result<Unit> =
    clickWebCall(
        request = { clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/me/presence") },
        onSuccess = { },
    )
