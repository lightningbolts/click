package compose.project.click.click.data.beacons

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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/*
 * "Is it still there?" on hazard alerts (flag `alert_confirmations`; click-web
 * `app/api/beacons/[beaconId]/confirm/route.ts`; iOS `AlertConfirmationSection.swift`). People nearby
 * keep the map accurate: "Still here" keeps it up, "Cleared" takes it down once enough agree; the
 * creator can take it down from anywhere. No counts: only when someone last saw it.
 */

enum class AlertVote(
    val wire: String,
) {
    STILL_HERE("still_here"),
    CLEARED("cleared"),
    ;

    companion object {
        fun fromWire(raw: String?): AlertVote? = entries.firstOrNull { it.wire == raw }
    }
}

enum class AlertPhase { ACTIVE, CLEARED, EXPIRED }

data class AlertConfirmationState(
    val phase: AlertPhase,
    val lastStillHereAtMs: Long?,
    val myVote: AlertVote?,
    val isCreator: Boolean,
) {
    /** A vote is allowed once per window; the creator can still take it down after "Still here". */
    fun canVote(vote: AlertVote): Boolean = phase == AlertPhase.ACTIVE && (myVote == null || (isCreator && vote == AlertVote.CLEARED))

    fun caption(nowEpochMs: Long): String =
        when {
            myVote == AlertVote.STILL_HERE -> "Thanks — you kept this up to date."
            myVote == AlertVote.CLEARED -> "Thanks — it comes down once others agree."
            lastStillHereAtMs != null -> "Last seen ${relativePastLabel(lastStillHereAtMs, nowEpochMs)}."
            isCreator -> "People nearby can confirm it's still there."
            else -> "Only people nearby can confirm."
        }

    companion object {
        fun parse(root: JsonObject): AlertConfirmationState {
            fun str(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull

            fun instant(key: String) = str(key)?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
            val vote = (root["my_vote"] as? JsonObject)?.get("status")?.let { (it as? JsonPrimitive)?.contentOrNull }
            return AlertConfirmationState(
                phase =
                    when (str("state")) {
                        "active" -> AlertPhase.ACTIVE
                        "cleared" -> AlertPhase.CLEARED
                        else -> AlertPhase.EXPIRED
                    },
                lastStillHereAtMs = instant("last_still_here_at"),
                myVote = AlertVote.fromWire(vote),
                isCreator = (root["is_creator"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }
    }
}

/** The server's expected refusals, with iOS's copy. */
enum class AlertVoteRejection(
    val message: String,
) {
    TOO_FAR("You need to be near this alert to confirm it."),
    ALREADY_VOTED("You've already confirmed this alert recently."),
    ENDED("This alert has already ended."),
    NEEDS_LOCATION("Your location is needed to confirm an alert."),
    ;

    companion object {
        fun fromStatus(status: Int): AlertVoteRejection? =
            when (status) {
                403 -> TOO_FAR
                409 -> ALREADY_VOTED
                404, 410 -> ENDED
                400 -> NEEDS_LOCATION
                else -> null
            }
    }
}

class AlertVoteRejectedException(
    val rejection: AlertVoteRejection,
) : Exception(rejection.message)

/** Quiet report reasons (iOS `BeaconDetailView`). Never shown to anyone. */
val BEACON_REPORT_REASONS: List<String> = listOf("Not accurate anymore", "Inappropriate", "Spam")

private val lenientJson = Json { ignoreUnknownKeys = true }

/** "just now", "5 minutes ago", "3 hours ago", "yesterday", "4 days ago". */
internal fun relativePastLabel(
    epochMs: Long,
    nowEpochMs: Long,
): String {
    val minutes = ((nowEpochMs - epochMs).coerceAtLeast(0L)) / 60_000L

    fun plural(
        n: Long,
        unit: String,
    ) = if (n == 1L) "1 $unit ago" else "$n ${unit}s ago"
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> plural(minutes, "minute")
        minutes < 24 * 60 -> plural(minutes / 60, "hour")
        minutes < 48 * 60 -> "yesterday"
        else -> plural(minutes / (24 * 60), "day")
    }
}

/** GET `/api/beacons/{id}/confirm`. A 404 means the flag is off for this viewer: hide the section. */
internal suspend fun ApiClient.getAlertConfirmation(beaconId: String): Result<AlertConfirmationState> =
    try {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/confirm")
        if (response.status.value in 200..299) {
            val root = lenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
            if (root == null) Result.failure(Exception("Unexpected response")) else Result.success(AlertConfirmationState.parse(root))
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * POST `/api/beacons/{id}/confirm {status, lat?, lng?}`. Returns the alert's new expiry (now, when
 * this vote cleared it); expected refusals throw [AlertVoteRejectedException].
 */
internal suspend fun ApiClient.confirmAlert(
    beaconId: String,
    vote: AlertVote,
    latitude: Double?,
    longitude: Double?,
): Result<Long?> =
    try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/confirm") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("status", vote.wire)
                        if (latitude != null && longitude != null) {
                            put("lat", latitude)
                            put("lng", longitude)
                        }
                    }.toString(),
                )
            }
        val status = response.status.value
        if (status in 200..299) {
            val root = lenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject
            val expires = (root?.get("expires_at") as? JsonPrimitive)?.contentOrNull
            Result.success(expires?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() })
        } else {
            val rejection = AlertVoteRejection.fromStatus(status)
            if (rejection != null) {
                Result.failure(AlertVoteRejectedException(rejection))
            } else {
                Result.failure(ClickWebRequestException(status, readClickWebErrorMessage(response)))
            }
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

/** POST `/api/beacons/{id}/report {reason}` — a quiet report for moderation. */
internal suspend fun ApiClient.reportBeacon(
    beaconId: String,
    reason: String,
): Result<Unit> =
    try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$beaconId/report") {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("reason", reason.trim().take(500)) }.toString())
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(ClickWebRequestException(response.status.value, readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
