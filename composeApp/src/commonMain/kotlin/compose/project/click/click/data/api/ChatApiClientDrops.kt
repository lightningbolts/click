package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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

/** `GET /api/drops/views` takes 1-100 ids per call (click-web `dropViewsQuerySchema`). */
internal const val DROP_VIEWS_MAX_PER_REQUEST = 100

/** `{developed: {id: developed_at}}`: only this viewer's rows, so foreign ids are simply absent. */
@Serializable
internal data class DropViewsEnvelope(
    val developed: Map<String, String> = emptyMap(),
)

/** Distinct, non-blank ids, sorted and cut into server-sized batches (iOS `ClickDropService.developedAt`). */
internal fun dropViewsBatches(ids: Collection<String>): List<List<String>> =
    ids
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()
        .chunked(DROP_VIEWS_MAX_PER_REQUEST)

/**
 * GET `/api/drops/views?kind=…&ids=a,b` — this viewer's developed-at per drop id, across every
 * batch. Mirrors iOS `ClickDropService.developedAt`.
 */
internal suspend fun ChatApiClient.dropViewsImpl(
    kind: String,
    ids: Collection<String>,
    authToken: String,
): Result<Map<String, String>> {
    val batches = dropViewsBatches(ids)
    if (batches.isEmpty()) return Result.success(emptyMap())
    val token =
        resolveClickWebAccessToken(tokenStorage)
            ?: authToken.trim().takeIf { it.isNotEmpty() }
            ?: return Result.failure(Exception("Session expired. Sign in again."))
    return try {
        val out = mutableMapOf<String, String>()
        for (batch in batches) {
            val response =
                client.get("$clickWebBaseUrl/api/drops/views") {
                    header(HttpHeaders.Authorization, clickWebBearerHeader(token))
                    parameter("kind", kind)
                    parameter("ids", batch.joinToString(","))
                }
            if (response.status.value !in 200..299) {
                return Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
            }
            out += response.body<DropViewsEnvelope>().developed
        }
        Result.success(out)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
