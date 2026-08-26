package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

/**
 * `GET /api/beacons` — proximity map beacons (PostGIS); bearer is the Supabase session JWT.
 */
internal suspend fun ApiClient.getMapBeaconsImpl(
    lat: Double,
    lon: Double,
    radiusMeters: Double,
    filters: String? = null,
): Result<String> {
    if (!lat.isFinite() || !lon.isFinite() || !radiusMeters.isFinite()) {
        return Result.failure(IllegalArgumentException("lat, lon, and radiusMeters must be finite"))
    }
    return try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons") {
                parameter("lat", lat)
                parameter("lon", lon)
                parameter("radius_meters", radiusMeters)
                val f = filters?.trim().orEmpty()
                if (f.isNotEmpty()) {
                    parameter("filters", f)
                }
            }
        if (response.status.value in 200..299) {
            Result.success(response.bodyAsText())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** `GET /api/beacons/{beaconId}` — full beacon row (metadata incl. event schedule). */
internal suspend fun ApiClient.getMapBeaconImpl(beaconId: String): Result<MapBeacon> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id")
        if (response.status.value in 200..299) {
            val payload = response.body<MapBeaconPostResponseDto>()
            val beaconObj =
                payload.beacon
                    ?: return Result.failure(Exception("Beacon payload was missing"))
            val beacon =
                parseMapBeaconRows(beaconObj).firstOrNull()
                    ?: return Result.failure(Exception("Beacon payload was malformed"))
            Result.success(beacon)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** `POST /api/beacons` — insert a map beacon (soundtrack rows enriched server-side). */
internal suspend fun ApiClient.postMapBeaconImpl(insert: MapBeaconInsert): Result<MapBeacon> {
    return try {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons") {
                contentType(ContentType.Application.Json)
                setBody(insert)
            }
        if (response.status.value in 200..299) {
            val payload = response.body<MapBeaconPostResponseDto>()
            val beaconObj =
                payload.beacon
                    ?: return Result.failure(Exception("Insert succeeded but beacon payload was missing"))
            val beacon =
                parseMapBeaconRows(beaconObj).firstOrNull()
                    ?: return Result.failure(Exception("Insert succeeded but beacon payload was malformed"))
            Result.success(beacon)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** PATCH `/api/beacons/{beaconId}` — update a creator-owned beacon. */
internal suspend fun ApiClient.patchMapBeaconImpl(
    beaconId: String,
    patch: MapBeaconPatchBody,
): Result<MapBeacon> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response =
            clickWebClient.patch("${ApiClient.clickWebAuthOrigin}/api/beacons/$id") {
                contentType(ContentType.Application.Json)
                setBody(patch)
            }
        if (response.status.value in 200..299) {
            val payload = response.body<MapBeaconPatchResponseDto>()
            val beaconObj =
                payload.beacon
                    ?: return Result.failure(Exception("Patch succeeded but beacon payload was missing"))
            val beacon =
                parseMapBeaconRows(beaconObj).firstOrNull()
                    ?: return Result.failure(Exception("Patch succeeded but beacon payload was malformed"))
            Result.success(beacon)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** DELETE `/api/beacons/{beaconId}` — remove a creator-owned beacon. */
internal suspend fun ApiClient.deleteMapBeaconImpl(beaconId: String): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response = clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/beacons/$id")
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** GET `/api/beacons/{beaconId}/rsvp` — attendee list for event beacons. */
internal suspend fun ApiClient.getBeaconRsvpImpl(beaconId: String): Result<BeaconRsvpGetResponseDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/rsvp")
        if (response.status.value in 200..299) {
            Result.success(response.body<BeaconRsvpGetResponseDto>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** GET `/api/beacons/{beaconId}/attendees/directory` — enriched people directory (RSVP or check-in required). */
internal suspend fun ApiClient.getBeaconAttendeeDirectoryImpl(beaconId: String): Result<BeaconAttendeeDirectoryResponseDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/attendees/directory")
        if (response.status.value in 200..299) {
            Result.success(response.body<BeaconAttendeeDirectoryResponseDto>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * POST `/api/beacons/{beaconId}/rsvp` — sign up for an event beacon, optionally persisting the
 * attendee's current GPS location for granular tracking.
 */
internal suspend fun ApiClient.postBeaconRsvpImpl(
    beaconId: String,
    latitude: Double? = null,
    longitude: Double? = null,
    accuracyMeters: Double? = null,
    platform: String? = null,
): Result<BeaconAttendeeDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/rsvp") {
                contentType(ContentType.Application.Json)
                setBody(
                    BeaconRsvpPostBody(
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = accuracyMeters,
                        source = "mobile",
                        platform = platform,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            val payload = response.body<BeaconRsvpPostResponseDto>()
            val attendee =
                payload.attendee
                    ?: return Result.failure(Exception("RSVP succeeded but attendee payload was missing"))
            Result.success(attendee)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/** DELETE `/api/beacons/{beaconId}/rsvp` — cancel the current user's RSVP. */
internal suspend fun ApiClient.deleteBeaconRsvpImpl(beaconId: String): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse = clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/rsvp")
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.getBeaconEngagementImpl(beaconId: String): Result<BeaconEngagementDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/engagement")
        if (response.status.value in 200..299) {
            Result.success(response.body<BeaconEngagementDto>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.putBeaconBookmarkImpl(
    beaconId: String,
    bookmarked: Boolean,
    telemetry: EngagementTelemetryBody = EngagementTelemetryBody(),
): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.put("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/bookmark") {
                contentType(ContentType.Application.Json)
                setBody(telemetry.copy(bookmarked = bookmarked))
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.postBeaconCheckInImpl(
    beaconId: String,
    telemetry: EngagementTelemetryBody,
): Result<BeaconCheckInMutationDto> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/check-in") {
                contentType(ContentType.Application.Json)
                setBody(telemetry)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<BeaconCheckInMutationDto>())
        } else {
            Result.failure(
                BeaconEngagementHttpException(
                    status = response.status.value,
                    message = readClickWebErrorMessage(response),
                ),
            )
        }
    } catch (e: ClientRequestException) {
        Result.failure(
            BeaconEngagementHttpException(
                status = e.response.status.value,
                message = readClickWebErrorMessage(e.response),
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.deleteBeaconCheckInImpl(beaconId: String): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/check-in")
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.postBeaconImpressionImpl(
    beaconId: String,
    telemetry: EngagementTelemetryBody = EngagementTelemetryBody(surface = "detail"),
): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/impressions") {
                contentType(ContentType.Application.Json)
                setBody(telemetry)
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        // Fire-and-forget: soft-fail
        Result.failure(e)
    }
}

internal suspend fun ApiClient.postBeaconShareImpl(
    beaconId: String,
    telemetry: EngagementTelemetryBody = EngagementTelemetryBody(surface = "detail"),
    shareUrl: String? = null,
): Result<Unit> {
    val id = beaconId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("beaconId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/beacons/$id/share") {
                contentType(ContentType.Application.Json)
                setBody(
                    ShareTelemetryBody(
                        latitude = telemetry.latitude,
                        longitude = telemetry.longitude,
                        accuracyMeters = telemetry.accuracyMeters,
                        clientOccurredAt = telemetry.clientOccurredAt,
                        source = telemetry.source,
                        platform = telemetry.platform,
                        appVersion = telemetry.appVersion,
                        surface = telemetry.surface,
                        shareUrl = shareUrl,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            Result.success(Unit)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.getMyEventBookmarksImpl(
    limit: Int = 50,
    cursor: String? = null,
): Result<EventBookmarksResponseDto> =
    try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/me/event-bookmarks") {
                parameter("limit", limit.coerceIn(1, 100))
                cursor?.trim()?.takeIf { it.isNotEmpty() }?.let { parameter("cursor", it) }
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<EventBookmarksResponseDto>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: ClientRequestException) {
        Result.failure(Exception(readClickWebErrorMessage(e.response)))
    } catch (e: Exception) {
        Result.failure(e)
    }
