package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.collaboration.SharedDrop
import compose.project.click.click.collaboration.SharedDropArchivePage
import compose.project.click.click.collaboration.SharedDropAudience
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/* click-web `app/api/me/shared-drops` (strip, post, delete, archive). Flag `shared_drops`. */

@Serializable
internal data class SharedDropsStripResponse(
    val teaser: String? = null,
    val drops: List<SharedDrop> = emptyList(),
)

@Serializable
internal data class SharedDropResponse(
    val drop: SharedDrop? = null,
)

@Serializable
internal data class SharedDropCreateBody(
    @SerialName("client_drop_id") val clientDropId: String,
    val audience: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("original_b64") val originalB64: String,
    @SerialName("preview_b64") val previewB64: String,
    val width: Int? = null,
    val height: Int? = null,
    val caption: String? = null,
)

sealed class SharedDropPostException(
    message: String,
) : Exception(message) {
    /** 409 `cap_reached`: `daily_cap` (3) per rolling 24 h. */
    class CapReached : SharedDropPostException("You've shared all your drops for today. Try again tomorrow.")

    class InvalidPhoto : SharedDropPostException("That photo couldn't be used. Try another.")
}

class SharedDropsApi(
    private val api: ApiClient = ApiClient(),
) {
    private val base get() = "${ApiClient.clickWebAuthOrigin}/api/me/shared-drops"

    /** The bounded Home strip: yours and your connections', newest first. */
    suspend fun strip(): Result<List<SharedDrop>> =
        call {
            val response = api.clickWebClient.get(base)
            if (response.status.value in 200..299) Result.success(response.body<SharedDropsStripResponse>().drops) else api.clickWebFailure(response)
        }

    /** One page of every drop you can see ("View all"), newest first. */
    suspend fun archive(
        before: String?,
        limit: Int = ARCHIVE_PAGE,
    ): Result<SharedDropArchivePage> =
        call {
            val response =
                api.clickWebClient.get("$base/archive") {
                    parameter("limit", limit.coerceIn(1, ARCHIVE_MAX))
                    if (before != null) parameter("before", before)
                }
            if (response.status.value in 200..299) Result.success(response.body()) else api.clickWebFailure(response)
        }

    /** Shares one drop; retrying with the same [clientDropId] returns the drop already made. */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun share(
        original: ByteArray,
        preview: ByteArray,
        width: Int?,
        height: Int?,
        audience: SharedDropAudience,
        caption: String?,
        clientDropId: String,
    ): Result<SharedDrop> =
        call {
            val response =
                api.clickWebClient.post(base) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        SharedDropCreateBody(
                            clientDropId = clientDropId,
                            audience = audience.wire,
                            mimeType = "image/jpeg",
                            originalB64 = Base64.encode(original),
                            previewB64 = Base64.encode(preview),
                            width = width,
                            height = height,
                            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
                        ),
                    )
                }
            when (response.status.value) {
                in 200..299 -> response.body<SharedDropResponse>().drop?.let { Result.success(it) } ?: Result.failure(Exception("Missing drop"))
                409 -> Result.failure(SharedDropPostException.CapReached())
                413 -> Result.failure(SharedDropPostException.InvalidPhoto())
                else -> api.clickWebFailure(response)
            }
        }

    /** The poster removes their drop, any time. */
    suspend fun delete(id: String): Result<Unit> =
        call {
            val response = api.clickWebClient.delete("$base/${id.trim().encodeURLPathPart()}")
            if (response.status.value in 200..299) Result.success(Unit) else api.clickWebFailure(response)
        }

    /** A developed original's bytes from its signed URL (shared drops aren't E2EE). */
    suspend fun download(url: String): Result<ByteArray> =
        call {
            val response = api.clickWebPlainClient.get(url)
            if (response.status.value in 200..299) Result.success(response.body<ByteArray>()) else Result.failure(Exception("HTTP ${response.status.value}"))
        }

    private inline fun <T> call(block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: ClientRequestException) {
            if (e.response.status.value == 409) Result.failure(SharedDropPostException.CapReached()) else Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        const val ARCHIVE_PAGE = 30
        const val ARCHIVE_MAX = 60
    }
}
