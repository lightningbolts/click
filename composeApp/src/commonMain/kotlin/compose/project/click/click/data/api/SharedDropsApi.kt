package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropAudience
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * Shared Click Drops (flag `shared_drops`): click-web `app/api/me/shared-drops` (strip, post),
 * `app/api/me/shared-drops/[dropId]` (delete), `app/api/drops/develop` (kind `shared`) and
 * `app/api/drops/report` and `app/api/me/shared-drops/archive` ("View all"). Mirrors iOS `Click/Features/Home/SharedDropsStore.swift`.
 */

@Serializable
internal data class SharedDropsStripResponse(
    val teaser: String? = null,
    val drops: List<SharedDrop> = emptyList(),
)

/** One page of `GET /api/me/shared-drops/archive`; [nextBefore] is null at the end. */
@Serializable
data class SharedDropArchivePage(
    val drops: List<SharedDrop> = emptyList(),
    @SerialName("next_before") val nextBefore: String? = null,
)

/** The archive URL for the page before [before] (null: the newest page). */
internal fun sharedDropArchiveUrl(
    base: String,
    before: String?,
    limit: Int = SHARED_DROP_ARCHIVE_PAGE,
): String {
    val query = StringBuilder("limit=").append(limit)
    before?.trim()?.takeIf { it.isNotEmpty() }?.let { query.append("&before=").append(it.encodeURLParameter()) }
    return "$base/archive?$query"
}

internal const val SHARED_DROP_ARCHIVE_PAGE = 30

@Serializable
internal data class SharedDropResponse(
    val drop: SharedDrop? = null,
)

@Serializable
data class SharedDropCreateBody(
    @SerialName("client_drop_id") val clientDropId: String,
    val audience: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("original_b64") val originalB64: String,
    @SerialName("preview_b64") val previewB64: String,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
)

@Serializable
internal data class DropReportBody(
    val kind: String,
    val id: String,
    val reason: String,
)

/** Builds the `POST /api/me/shared-drops` body; a blank caption is left out (the server drops it too). */
@OptIn(ExperimentalEncodingApi::class)
internal fun sharedDropCreateBody(
    original: ByteArray,
    preview: ByteArray,
    width: Int?,
    height: Int?,
    audience: SharedDropAudience,
    caption: String?,
    clientDropId: String,
): SharedDropCreateBody =
    SharedDropCreateBody(
        clientDropId = clientDropId,
        audience = audience.wire,
        mimeType = "image/jpeg",
        originalB64 = Base64.encode(original),
        previewB64 = Base64.encode(preview),
        width = width?.takeIf { it > 0 },
        height = height?.takeIf { it > 0 },
        caption = caption?.trim()?.takeIf { it.isNotEmpty() },
    )

sealed class SharedDropPostException(
    message: String,
) : Exception(message) {
    /** 409 `cap_reached`: the daily cap (`daily_cap`, 3 by default) per rolling 24 h. */
    class CapReached(
        message: String? = null,
    ) : SharedDropPostException(message ?: "You've shared all your drops for today. Try again tomorrow.")

    /** 413 `invalid_media`, or a photo the app couldn't make a preview of. */
    class InvalidPhoto : SharedDropPostException("That photo couldn't be used. Try another.")
}

/** The shared-drop endpoints the Home strip uses (fakeable in tests). */
interface SharedDropsService {
    /** The bounded Home strip: yours and your connections', newest first. */
    suspend fun strip(): Result<List<SharedDrop>>

    /** One page of "View all": every drop you can see, newest first, older ones included. */
    suspend fun archive(before: String?): Result<SharedDropArchivePage>

    /** Shares one drop; retrying with the same [clientDropId] returns the drop already made. */
    suspend fun share(body: SharedDropCreateBody): Result<SharedDrop>

    /** The poster removes their drop, any time (not flag-gated on the server). */
    suspend fun delete(id: String): Result<Unit>

    /** Develops one shared drop for this viewer; the result carries the signed original when developed. */
    suspend fun develop(id: String): Result<DropDevelopResultDto?>

    /** A quiet report on a drop the reporter can see. Never shown to anyone. */
    suspend fun report(
        id: String,
        reason: String,
    ): Result<Unit>

    /** Bytes behind a signed preview or original URL (shared drops aren't E2EE). */
    suspend fun download(url: String): Result<ByteArray>
}

class SharedDropsApi(
    private val api: ApiClient = ApiClient(),
) : SharedDropsService {
    private val origin get() = ApiClient.clickWebAuthOrigin
    private val base get() = "$origin/api/me/shared-drops"

    override suspend fun strip(): Result<List<SharedDrop>> =
        call {
            val response = api.clickWebClient.get(base)
            if (response.status.value in 200..299) {
                Result.success(response.body<SharedDropsStripResponse>().drops)
            } else {
                api.clickWebFailure(response)
            }
        }

    override suspend fun archive(before: String?): Result<SharedDropArchivePage> =
        call {
            val response = api.clickWebClient.get(sharedDropArchiveUrl(base, before))
            if (response.status.value in 200..299) {
                Result.success(response.body<SharedDropArchivePage>())
            } else {
                api.clickWebFailure(response)
            }
        }

    override suspend fun share(body: SharedDropCreateBody): Result<SharedDrop> =
        call {
            val response =
                api.clickWebClient.post(base) {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            when (response.status.value) {
                in 200..299 ->
                    response.body<SharedDropResponse>().drop?.let { Result.success(it) }
                        ?: Result.failure(Exception("Missing drop"))
                409 -> Result.failure(SharedDropPostException.CapReached(api.readClickWebErrorMessage(response).takeIf { it.isNotBlank() }))
                413 -> Result.failure(SharedDropPostException.InvalidPhoto())
                else -> api.clickWebFailure(response)
            }
        }

    override suspend fun delete(id: String): Result<Unit> =
        call {
            val response = api.clickWebClient.delete("$base/${id.trim().encodeURLPathPart()}")
            if (response.status.value in 200..299) Result.success(Unit) else api.clickWebFailure(response)
        }

    override suspend fun develop(id: String): Result<DropDevelopResultDto?> =
        call {
            val response =
                api.clickWebClient.post("$origin/api/drops/develop") {
                    contentType(ContentType.Application.Json)
                    setBody(DropDevelopBody(listOf(DropDevelopRef(kind = SHARED_DROP_KIND, id = id))))
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<DropDevelopEnvelope>().drops.firstOrNull { it.id == id })
            } else {
                api.clickWebFailure(response)
            }
        }

    override suspend fun report(
        id: String,
        reason: String,
    ): Result<Unit> =
        call {
            val response =
                api.clickWebClient.post("$origin/api/drops/report") {
                    contentType(ContentType.Application.Json)
                    setBody(DropReportBody(SHARED_DROP_KIND, id, reason.trim().take(500).ifEmpty { "Reported from the story viewer" }))
                }
            if (response.status.value in 200..299) Result.success(Unit) else api.clickWebFailure(response)
        }

    override suspend fun download(url: String): Result<ByteArray> =
        call {
            val response = api.clickWebPlainClient.get(url)
            if (response.status.value in 200..299) {
                Result.success(response.body<ByteArray>())
            } else {
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        }

    private inline fun <T> call(block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        const val SHARED_DROP_KIND = "shared"
    }
}
