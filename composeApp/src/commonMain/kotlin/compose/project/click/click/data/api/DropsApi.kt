package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.collaboration.ClickDropDevelopResult
import compose.project.click.click.collaboration.ClickDropKind
import compose.project.click.click.collaboration.ClickDropRef
import compose.project.click.click.collaboration.parseClickDropDevelopResults
import compose.project.click.click.collaboration.parseClickDropViews
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException

/** Develop endpoints the develop flow needs (fakeable in tests). */
interface ClickDropService {
    /** Develops ready drops (batched by 50); pending ones come back pending. Throws on failure. */
    suspend fun develop(refs: List<ClickDropRef>): List<ClickDropDevelopResult>

    /** This viewer's developed-at time per drop ID (absent: not developed). Throws on failure. */
    suspend fun developedAt(
        kind: ClickDropKind,
        ids: List<String>,
    ): Map<String, Instant>
}

@Serializable
internal data class DropRefBody(
    val kind: String,
    val id: String,
)

@Serializable
internal data class DropDevelopBody(
    val drops: List<DropRefBody>,
)

@Serializable
internal data class DropReportBody(
    val kind: String,
    val id: String,
    val reason: String,
)

/**
 * click-web `app/api/drops/{develop,views,report}`. The server decides who may develop what and
 * never signs an original before its reveal time. Not flag-gated: a viewer must be able to develop
 * a gated drop someone in the cohort sent them.
 */
class DropsApi(
    private val api: ApiClient = ApiClient(),
) : ClickDropService {
    override suspend fun develop(refs: List<ClickDropRef>): List<ClickDropDevelopResult> =
        refs.chunked(DEVELOP_BATCH).flatMap { batch ->
            val response =
                api.clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/drops/develop") {
                    contentType(ContentType.Application.Json)
                    setBody(DropDevelopBody(batch.map { DropRefBody(it.kind.wire, it.id) }))
                }
            if (response.status.value !in 200..299) api.clickWebFailure(response).getOrThrow()
            parseClickDropDevelopResults(response.body<JsonObject>())
        }

    override suspend fun developedAt(
        kind: ClickDropKind,
        ids: List<String>,
    ): Map<String, Instant> =
        ids.distinct().sorted().chunked(VIEWS_BATCH).fold(emptyMap()) { acc, batch ->
            val response =
                api.clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/drops/views") {
                    parameter("kind", kind.wire)
                    parameter("ids", batch.joinToString(","))
                }
            if (response.status.value !in 200..299) api.clickWebFailure(response).getOrThrow()
            acc + parseClickDropViews(response.body<JsonObject>())
        }

    /** A quiet report on any drop the reporter can see. Never shown to anyone. */
    suspend fun report(
        kind: ClickDropKind,
        id: String,
        reason: String,
    ): Result<Unit> =
        try {
            val response =
                api.clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/drops/report") {
                    contentType(ContentType.Application.Json)
                    setBody(DropReportBody(kind.wire, id, reason.trim().take(500)))
                }
            if (response.status.value in 200..299) Result.success(Unit) else api.clickWebFailure(response)
        } catch (e: ClientRequestException) {
            api.clickWebFailure(e.response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    companion object {
        const val DEVELOP_BATCH = 50
        const val VIEWS_BATCH = 100
    }
}
