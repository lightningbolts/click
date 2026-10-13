package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.events.ClickPass // pragma: allowlist secret
import compose.project.click.click.events.ClickPassFetch // pragma: allowlist secret
import compose.project.click.click.events.PassScan // pragma: allowlist secret
import compose.project.click.click.events.PassScanResponse // pragma: allowlist secret
import compose.project.click.click.events.toPassScan // pragma: allowlist secret
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.coroutines.cancellation.CancellationException

@Serializable
internal data class PassScanBody(
    val credential: String,
)

/** The `{error, code}` envelope of a click-web error (`lib/api/errors.ts`). */
internal data class ClickWebErrorBody(
    val status: Int,
    val message: String,
    val code: String?,
)

internal suspend fun ApiClient.readClickWebErrorBody(response: HttpResponse): ClickWebErrorBody {
    val raw = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
    val obj = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
    val error = (obj?.get("error") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val code = (obj?.get("code") as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    return ClickWebErrorBody(
        status = response.status.value,
        message = error?.take(200) ?: "Request failed (${response.status.value})",
        code = code,
    )
}

/**
 * GET `/api/beacons/{id}/pass` (B|C, `no-store`) → `{credential_url, code, checked_in_at, wallet_available}`;
 * 403 `not_going`; 503 `pass_unavailable`.
 */
suspend fun ApiClient.getEventPass(beaconId: String): ClickPassFetch {
    val id = beaconId.trim()
    if (id.isEmpty()) return ClickPassFetch.Failed("beaconId required")
    return try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/pass")
        passFetchFrom(response)
    } catch (e: ClientRequestException) {
        passFetchFrom(e.response)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ClickPassFetch.Failed(e.message ?: "Couldn't load your pass")
    }
}

private suspend fun ApiClient.passFetchFrom(response: HttpResponse): ClickPassFetch {
    if (response.status.value in 200..299) {
        val pass =
            runCatching { json.decodeFromString(ClickPass.serializer(), response.bodyAsText()) }
                .getOrNull()
                ?.takeIf { it.credentialUrl.isNotBlank() && it.code.isNotBlank() }
                ?: return ClickPassFetch.Failed("Couldn't read your pass")
        return ClickPassFetch.Loaded(pass)
    }
    val error = readClickWebErrorBody(response)
    return if (error.status == 403) {
        ClickPassFetch.NotGoing
    } else {
        ClickPassFetch.Failed(error.message)
    }
}

/**
 * POST `/api/beacons/{id}/pass/scan {credential}` (managers only). Always 200 with
 * `{result, attendee, checked_in_at, check_in_count?}`; 403 for non-managers, 503, 500 = failed closed.
 */
suspend fun ApiClient.scanEventPass(
    beaconId: String,
    credential: String,
): Result<PassScan> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    // The server caps the credential at 512 characters.
    val value = credential.trim().take(512)
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/pass/scan") {
                contentType(ContentType.Application.Json)
                setBody(PassScanBody(credential = value))
            }
        if (response.status.value in 200..299) {
            val scan =
                runCatching { json.decodeFromString(PassScanResponse.serializer(), response.bodyAsText()) }
                    .getOrNull()
                    ?.toPassScan()
                    ?: return Result.failure(Exception("Couldn't read the scan result"))
            Result.success(scan)
        } else {
            val error = readClickWebErrorBody(response)
            Result.failure(ClickWebRequestException(error.status, error.message))
        }
    } catch (e: ClientRequestException) {
        val error = readClickWebErrorBody(e.response)
        Result.failure(ClickWebRequestException(error.status, error.message))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
