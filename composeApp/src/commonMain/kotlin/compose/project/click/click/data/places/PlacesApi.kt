package compose.project.click.click.data.places

import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Places API surface (iOS `PlaceRepositoryProtocol`), so screens can be tested with a fake.
 * Every failure is a [PlaceApiException] carrying a [PlaceError].
 */
interface PlacesRepository {
    suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): Result<List<PlaceSummaryDto>>

    suspend fun detail(
        idOrSlug: String,
        source: String? = null,
    ): Result<PlaceDetail>

    suspend fun checkIn(
        placeId: String,
        body: JsonObject,
    ): Result<PlaceCheckInDto>

    suspend fun checkOut(placeId: String): Result<Boolean>

    suspend fun submitPulse(
        placeId: String,
        body: JsonObject,
    ): Result<PulseSubmitResult>

    suspend fun updatePulse(
        placeId: String,
        pulseId: String,
        body: JsonObject,
    ): Result<PulseSummaryDto>

    suspend fun myPlaces(): Result<List<MyPlaceVisit>>
}

/** Paths and bodies, pure so they can be unit tested (click-web `app/api/places`). */
object PlacesRequests {
    /** `/api/places/{idOrSlug}` with the segment percent-encoded. */
    fun placePath(idOrSlug: String): String = "/api/places/${idOrSlug.encodeURLPathPart()}"

    const val NEARBY_PATH = "/api/places/nearby"
    const val MY_PLACES_PATH = "/api/me/places"

    /** Filtering is client-side (§6.6), so only the point and radius go up. */
    fun nearbyParams(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ): List<Pair<String, String>> =
        listOf(
            "lat" to latitude.toString(),
            "lon" to longitude.toString(),
            "radius_meters" to radiusMeters.toString(),
        )

    /** `source` is reported once per open: map, nearby, event, link, qr or history (server allowlist). */
    fun detailParams(source: String?): List<Pair<String, String>> = source?.let { listOf("source" to it) }.orEmpty()

    /** `POST /check-in` (§5.4). GPS fix and/or a QR anchor token; the server owns the geofence. */
    fun checkInBody(
        latitude: Double?,
        longitude: Double?,
        accuracyMeters: Double?,
        anchorToken: String?,
        shareWithConnections: Boolean,
        appVersion: String? = null,
    ): JsonObject =
        buildJsonObject {
            put("share_with_connections", shareWithConnections)
            put("platform", "android")
            if (latitude != null && longitude != null) {
                put("latitude", latitude)
                put("longitude", longitude)
            }
            accuracyMeters?.let { put("accuracy_meters", it) }
            anchorToken?.takeIf { it.isNotBlank() }?.let { put("anchor_token", it) }
            appVersion?.takeIf { it.isNotBlank() }?.let { put("app_version", it.take(32)) }
        }

    /** `POST /pulse`: energy (1–4) and/or would_return (0/1), plus optional follow-ups. */
    fun pulseBody(
        energy: Int? = null,
        talkable: Int? = null,
        categoryAnswer: Int? = null,
        wouldReturn: Int? = null,
    ): JsonObject =
        buildJsonObject {
            put("question_version", 1)
            energy?.let { put("energy", it) }
            talkable?.let { put("talkable", it) }
            categoryAnswer?.let { put("category_answer", it) }
            wouldReturn?.let { put("would_return", it) }
        }

    /** `PATCH /pulse/{id}`: fill a still-empty follow-up. */
    fun pulsePatchBody(
        talkable: Int? = null,
        categoryAnswer: Int? = null,
    ): JsonObject =
        buildJsonObject {
            talkable?.let { put("talkable", it) }
            categoryAnswer?.let { put("category_answer", it) }
        }
}

/** Click Places over click-web. Location is passed in per action; nothing here reads location. */
class ClickWebPlacesRepository(
    private val api: ApiClient = ApiClient(),
) : PlacesRepository {
    private val origin get() = ApiClient.clickWebAuthOrigin

    private suspend fun <T> call(
        forbidden: PlaceError = PlaceError.NotPresent,
        request: suspend () -> HttpResponse,
        decode: (JsonElement) -> T?,
    ): Result<T> =
        try {
            val response = request()
            val text = runCatching { response.bodyAsText() }.getOrNull()
            if (response.status.value in 200..299) {
                val decoded = text?.let { runCatching { placesJson.parseToJsonElement(it) }.getOrNull() }?.let(decode)
                decoded?.let { Result.success(it) } ?: Result.failure(PlaceApiException(PlaceError.Network))
            } else {
                Result.failure(PlaceApiException(PlaceError.from(response.status.value, text, forbidden)))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.failure(PlaceApiException(PlaceError.Network))
        }

    override suspend fun nearby(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int,
    ) = call(request = {
        api.clickWebClient.get("$origin${PlacesRequests.NEARBY_PATH}") {
            PlacesRequests.nearbyParams(latitude, longitude, radiusMeters).forEach { (k, v) -> parameter(k, v) }
        }
    }, decode = ::decodeNearbyPlaces)

    override suspend fun detail(
        idOrSlug: String,
        source: String?,
    ) = call(request = {
        api.clickWebClient.get("$origin${PlacesRequests.placePath(idOrSlug)}") {
            PlacesRequests.detailParams(source).forEach { (k, v) -> parameter(k, v) }
        }
    }, decode = ::decodePlaceDetail)

    override suspend fun checkIn(
        placeId: String,
        body: JsonObject,
    ) = call(request = {
        api.clickWebClient.post("$origin${PlacesRequests.placePath(placeId)}/check-in") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }, decode = ::decodeCheckIn)

    override suspend fun checkOut(placeId: String) =
        call(request = {
            api.clickWebClient.delete("$origin${PlacesRequests.placePath(placeId)}/check-in")
        }, decode = ::decodeAskWouldReturn)

    override suspend fun submitPulse(
        placeId: String,
        body: JsonObject,
    ) = call(request = {
        api.clickWebClient.post("$origin${PlacesRequests.placePath(placeId)}/pulse") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }, decode = ::decodePulseSubmit)

    override suspend fun updatePulse(
        placeId: String,
        pulseId: String,
        body: JsonObject,
    ) = call(request = {
        api.clickWebClient.patch("$origin${PlacesRequests.placePath(placeId)}/pulse/${pulseId.encodeURLPathPart()}") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }, decode = ::decodePulseSummary)

    override suspend fun myPlaces() =
        call(request = {
            api.clickWebClient.get("$origin${PlacesRequests.MY_PLACES_PATH}")
        }, decode = ::decodeMyPlaces)
}
