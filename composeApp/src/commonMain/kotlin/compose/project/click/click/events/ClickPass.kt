package compose.project.click.click.events // pragma: allowlist secret

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebRequestException // pragma: allowlist secret
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/*
 * Click Pass and the host's door scanner (click-web `app/api/beacons/[beaconId]/pass/route.ts` and
 * `pass/scan/route.ts`; iOS `ClickPassView.swift`, `PassScannerView.swift`). The pass is a QR the host
 * scans at the door; a repeat scan shows when it was first used, so a shared screenshot is caught.
 */

data class ClickPass(
    /** What the QR encodes (a joinclick.co URL carrying the signed token). */
    val credentialUrl: String,
    /** Short human-readable code under the QR. */
    val code: String,
    val checkedInAtMs: Long?,
)

private val lenient = Json { ignoreUnknownKeys = true }

private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

private fun JsonObject.instant(key: String) = str(key)?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

internal fun parseClickPass(root: JsonObject): ClickPass? {
    val url = root.str("credential_url")?.takeIf { it.isNotBlank() } ?: return null
    return ClickPass(credentialUrl = url, code = root.str("code").orEmpty(), checkedInAtMs = root.instant("checked_in_at"))
}

/** Why there's no pass: not going (RSVP first) or the server can't issue passes right now. */
enum class ClickPassUnavailable { NOT_GOING, UNAVAILABLE }

class ClickPassUnavailableException(
    val reason: ClickPassUnavailable,
) : Exception(reason.name)

/** GET `/api/beacons/{id}/pass`. 403 `not_going` and 503 `pass_unavailable` become [ClickPassUnavailableException]. */
internal suspend fun ApiClient.getClickPass(beaconId: String): Result<ClickPass> =
    try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/pass")
        when (val status = response.status.value) {
            in 200..299 ->
                (lenient.parseToJsonElement(response.bodyAsText()) as? JsonObject)
                    ?.let(::parseClickPass)
                    ?.let { Result.success(it) }
                    ?: Result.failure(Exception("Unexpected response"))
            403 -> Result.failure(ClickPassUnavailableException(ClickPassUnavailable.NOT_GOING))
            503 -> Result.failure(ClickPassUnavailableException(ClickPassUnavailable.UNAVAILABLE))
            else -> Result.failure(ClickWebRequestException(status, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

enum class PassScanResult(
    val wire: String,
) {
    CHECKED_IN("checked_in"),
    ALREADY_CHECKED_IN("already_checked_in"),
    NOT_GOING("not_going"),
    WRONG_EVENT("wrong_event"),
    INVALID("invalid"),
    REFUNDED("refunded"),
    EVENT_CANCELLED("event_cancelled"),
    ;

    companion object {
        fun fromWire(raw: String?): PassScanResult = entries.firstOrNull { it.wire == raw } ?: INVALID
    }
}

data class PassScan(
    val result: PassScanResult,
    val holderName: String?,
    val holderAvatarUrl: String?,
    val checkedInAtMs: Long?,
    val checkInCount: Int?,
)

internal fun parsePassScan(root: JsonObject): PassScan {
    val attendee = root["attendee"] as? JsonObject
    return PassScan(
        result = PassScanResult.fromWire(root.str("result")),
        holderName = attendee?.str("name")?.trim()?.takeIf { it.isNotEmpty() },
        holderAvatarUrl = attendee?.str("avatar_url")?.trim()?.takeIf { it.isNotEmpty() },
        checkedInAtMs = root.instant("checked_in_at"),
        checkInCount = (root["check_in_count"] as? JsonPrimitive)?.intOrNull,
    )
}

/** Verdict copy for the door (iOS `PassScannerView`). [clock] formats a time like "7:42 PM". */
data class PassVerdict(
    val title: String,
    val detail: String?,
    val good: Boolean,
)

fun PassScan.verdict(clock: (Long) -> String): PassVerdict =
    when (result) {
        PassScanResult.CHECKED_IN -> PassVerdict("Checked in", checkInCount?.let { "$it here now" }, good = true)
        PassScanResult.ALREADY_CHECKED_IN ->
            PassVerdict("Already checked in", checkedInAtMs?.let { "At ${clock(it)}. Make sure it's them." }, good = false)
        PassScanResult.NOT_GOING -> PassVerdict("Not on the list", "Their RSVP isn't active for this event.", good = false)
        PassScanResult.WRONG_EVENT -> PassVerdict("Pass for another event", null, good = false)
        PassScanResult.REFUNDED -> PassVerdict("Refunded", "This ticket was refunded.", good = false)
        PassScanResult.EVENT_CANCELLED -> PassVerdict("Event cancelled", "Tickets for this event no longer admit anyone.", good = false)
        PassScanResult.INVALID -> PassVerdict("Not a Click Pass", null, good = false)
    }

/** POST `/api/beacons/{id}/pass/scan {credential}` — hosts and co-hosts only. Every outcome is a 200. */
internal suspend fun ApiClient.scanClickPass(
    beaconId: String,
    credential: String,
): Result<PassScan> =
    try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/pass/scan") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("credential", credential.trim()) }.toString())
            }
        if (response.status.value in 200..299) {
            (lenient.parseToJsonElement(response.bodyAsText()) as? JsonObject)?.let { Result.success(parsePassScan(it)) }
                ?: Result.failure(Exception("Unexpected response"))
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
