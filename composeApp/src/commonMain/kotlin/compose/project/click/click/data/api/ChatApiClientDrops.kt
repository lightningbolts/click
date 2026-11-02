package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Click Drop develop (click-web `app/api/drops/develop/route.ts`, `lib/server/drops/develop.ts`).
 * Not flag-gated: anyone must be able to develop a gated drop a flagged sender sent them. The server
 * marks ready drops developed for this viewer and signs each gated original for 10 minutes; it
 * never signs an original before its reveal time. Mirrors iOS `ClickDropService.develop`.
 */

@Serializable
data class DropDevelopRef(
    val kind: String,
    val id: String,
)

@Serializable
internal data class DropDevelopBody(
    val drops: List<DropDevelopRef>,
)

/** One drop's answer. `status` is `developed`, `pending` or `not_found`. */
@Serializable
data class DropDevelopResultDto(
    val kind: String,
    val id: String,
    val status: String,
    @SerialName("developed_at") val developedAt: String? = null,
    @SerialName("reveal_at") val revealAt: String? = null,
    /** Short-lived signed URL of a gated original; null for legacy drops (their media is the original). */
    val url: String? = null,
)

@Serializable
internal data class DropDevelopEnvelope(
    val drops: List<DropDevelopResultDto> = emptyList(),
)

/** Server cap per request. */
internal const val DROP_DEVELOP_MAX_PER_REQUEST = 50

/** POST `/api/drops/develop {drops:[{kind,id}]}` — at most [DROP_DEVELOP_MAX_PER_REQUEST] per call. */
internal suspend fun ChatApiClient.developDropsImpl(
    refs: List<DropDevelopRef>,
    authToken: String,
): Result<List<DropDevelopResultDto>> {
    val batch = refs.distinct().take(DROP_DEVELOP_MAX_PER_REQUEST)
    if (batch.isEmpty()) return Result.success(emptyList())
    val token =
        resolveClickWebAccessToken(tokenStorage)
            ?: authToken.trim().takeIf { it.isNotEmpty() }
            ?: return Result.failure(Exception("Session expired. Sign in again."))
    return try {
        val response =
            client.post("$clickWebBaseUrl/api/drops/develop") {
                header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                contentType(ContentType.Application.Json)
                setBody(DropDevelopBody(batch))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<DropDevelopEnvelope>().drops)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
