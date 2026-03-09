package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.OpenMeteoWeatherService
import compose.project.click.click.data.WeatherService
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsert
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.GeoLocationInsert
import compose.project.click.click.data.models.GeoLocation
import compose.project.click.click.data.models.MemoryCapsule
import compose.project.click.click.data.models.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.*

/**
 * Proximity verification result from server-side validation.
 */
sealed class ProximityResult {
    data class Success(val connection: Connection) : ProximityResult()
    data class ProximityRejected(val distance: Int) : ProximityResult()
    data class Error(val message: String) : ProximityResult()
}

class ConnectionRepository(
    private val weatherService: WeatherService = OpenMeteoWeatherService()
) {
    private val supabase = SupabaseConfig.client
    private val supabaseRepository = SupabaseRepository()
    private val json = Json { ignoreUnknownKeys = true }

    private fun normalizeContextTag(
        contextTagObject: ContextTag?,
        contextTag: String?
    ): ContextTag? {
        return contextTagObject ?: contextTag
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { ContextTag(id = "custom", label = it, emoji = "✏️") }
    }

    private fun resolveContextTagId(contextTag: ContextTag?): String? {
        return when {
            contextTag == null -> null
            contextTag.id == "custom" -> contextTag.label
            else -> contextTag.id
        }
    }

    @Serializable
    private data class RedeemQrTokenResponse(
        val success: Boolean,
        val error: String? = null,
        @SerialName("user_id") val userId: String? = null,
        @SerialName("token_age_ms") val tokenAgeMs: Long? = null,
    )

    /**
     * Create a connection between two users with proximity verification.
     *
     * Performs Haversine distance check if both locations are available,
     * computes a proximity confidence score, and stores the signals.
     * The Supabase anomaly detection trigger runs on INSERT.
     */
    suspend fun createConnection(request: ConnectionRequest): Result<Connection> {
        return try {
            val redeemedToken = if (!request.qrToken.isNullOrBlank()) {
                redeemQrToken(request.qrToken)
                    .getOrElse { return Result.failure(it) }
            } else {
                null
            }

            val scannedUserId = redeemedToken?.userId ?: request.userId2
            if (scannedUserId.isBlank()) {
                return Result.failure(Exception("Invalid QR code"))
            }

            if (scannedUserId == request.userId1) {
                return Result.failure(Exception("You cannot connect with yourself!"))
            }

            val resolvedTokenAgeMs = redeemedToken?.tokenAgeMs ?: request.tokenAgeMs

            // Check if connection already exists
            val existingConnection = checkExistingConnection(
                request.userId1,
                scannedUserId
            )

            if (existingConnection != null) {
                return Result.failure(Exception("Connection already exists"))
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val expiry = now + (30L * 24 * 60 * 60 * 1000) // 30 days

            // ── Proximity validation ──

            val loc1Valid = request.locationLat != null && request.locationLng != null &&
                request.locationLat.isFinite() && request.locationLng.isFinite() &&
                !(request.locationLat == 0.0 && request.locationLng == 0.0)

            // For now, we only have the initiator's location on mobile.
            // The server can reject if it also has the scanner's location.
            val gpsAvailable = loc1Valid

            // Compute geo_location — never default to (0, 0)
            val geoLocation = if (loc1Valid) {
                GeoLocationInsert(
                    lat = request.locationLat!!,
                    lon = request.locationLng!!
                )
            } else {
                // No GPS — use 0,0 as sentinel (frontend filters these)
                GeoLocationInsert(lat = 0.0, lon = 0.0)
            }

            // Compute proximity confidence score
            val proximityScore = computeProximityScore(
                connectionMethod = request.connectionMethod,
                gpsAvailable = gpsAvailable,
                tokenAgeMs = resolvedTokenAgeMs
            )

            // Build proximity signals for auditability
            val proximitySignals = buildJsonObject {
                put("connection_method", request.connectionMethod)
                put("gps_available", gpsAvailable)
                if (resolvedTokenAgeMs != null) {
                    put("token_age_seconds", resolvedTokenAgeMs / 1000.0)
                }
                if (loc1Valid) {
                    put("initiator_lat", request.locationLat!!)
                    put("initiator_lon", request.locationLng!!)
                }
            }

            val normalizedContextTag = normalizeContextTag(
                contextTagObject = request.contextTagObject,
                contextTag = request.contextTag
            )
            val contextTagId = resolveContextTagId(normalizedContextTag)
            val initiatorId = request.initiatorId ?: when (request.connectionMethod) {
                "qr" -> scannedUserId
                "nfc" -> if (request.userId1 == scannedUserId) request.userId2 else scannedUserId
                else -> null
            }
            val responderId = request.responderId ?: when (request.connectionMethod) {
                "qr" -> request.userId1
                "nfc" -> if (initiatorId == request.userId1) scannedUserId else request.userId1
                else -> null
            }

            val connectionInsert = ConnectionInsert(
                user_ids = listOf(request.userId1, scannedUserId),
                geo_location = geoLocation,
                created = now,
                expiry = expiry,
                should_continue = listOf(false, false),
                has_begun = false,
                expiry_state = "pending",
                context_tag_id = contextTagId,
                initiator_id = initiatorId,
                responder_id = responderId
            )

            val result = supabase.from("connections")
                .insert(connectionInsert) {
                    select()
                }
                .decodeSingle<Connection>()

            var semanticLocationName: String? = null
            var fullLocationMap: Map<String, String>? = null

            if (loc1Valid) {
                try {
                    val semanticResult = resolveSemanticLocation(
                        lat = request.locationLat!!,
                        lon = request.locationLng!!
                    )
                    if (semanticResult != null) {
                        semanticLocationName = semanticResult.first
                        fullLocationMap = semanticResult.second
                    }
                } catch (e: Exception) {
                    println("ConnectionRepository: Failed to resolve semantic location: ${e.message}")
                }
            }

            val weatherSnapshot = if (loc1Valid) {
                weatherService.fetchWeather(request.locationLat!!, request.locationLng!!)
            } else {
                null
            }

            val memoryCapsule = MemoryCapsule(
                connectionId = result.id,
                locationName = semanticLocationName,
                geoLocation = if (loc1Valid) {
                    GeoLocation(lat = request.locationLat!!, lon = request.locationLng!!)
                } else {
                    null
                },
                connectedAtMs = now,
                weatherSnapshot = weatherSnapshot,
                contextTag = normalizedContextTag,
                noiseLevelCategory = request.noiseLevelCategory
            )

            try {
                supabase.from("connections")
                    .update(buildJsonObject {
                        put("proximity_confidence", proximityScore)
                        put("proximity_signals", proximitySignals)
                        put("connection_method", request.connectionMethod)
                        put("flagged", proximityScore < 20)
                        contextTagId?.let { put("context_tag_id", it) }
                        put("memory_capsule", json.encodeToJsonElement(memoryCapsule))
                        request.noiseLevelCategory?.name?.let { put("noise_level", it) }
                        weatherSnapshot?.condition?.let { put("weather_condition", it) }
                        semanticLocationName?.let { put("semantic_location", it) }
                        fullLocationMap?.let { addressMap ->
                            put(
                                "full_location",
                                JsonObject(addressMap.mapValues { (_, value) -> JsonPrimitive(value) })
                            )
                        }
                    }) {
                        filter {
                            eq("id", result.id)
                        }
                    }
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to update connection metadata: ${e.message}")
                // Non-fatal — connection was created
            }

            // Create chat row for this connection
            try {
                supabase.from("chats")
                    .insert(buildJsonObject {
                        put("connection_id", result.id)
                        put("created_at", now)
                        put("updated_at", now)
                    })
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to create chat: ${e.message}")
                // Non-fatal — connection was created
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compute a local proximity confidence score based on available signals.
     *
     * | Signal                  | Points |
     * |─────────────────────────|────────|
     * | NFC connection          | +50    |
     * | GPS available           | +15    |
     * | GPS not available       |  0     |
     * | QR token age < 30s      | +10    |
     * | QR token age 30–60s     | +5     |
     * | QR token age > 60s      |  0     |
     *
     * Note: Full scoring (GPS distance, shared BSSID) requires both users'
     * data and is done server-side when using the web API endpoint.
     */
    private fun computeProximityScore(
        connectionMethod: String,
        gpsAvailable: Boolean,
        tokenAgeMs: Long?
    ): Int {
        var score = 0

        // Connection method baseline
        if (connectionMethod == "nfc") {
            score += 50
        }

        // GPS availability (single-side — we don't have both locs on mobile)
        if (gpsAvailable) {
            score += 15
        }

        // QR token age scoring
        if (tokenAgeMs != null) {
            val tokenAgeSec = tokenAgeMs / 1000
            if (tokenAgeSec < 30) score += 10
            else if (tokenAgeSec <= 60) score += 5
        }

        return score.coerceIn(0, 100)
    }

    /**
     * Reverse geocode GPS coordinates to a semantic location name
     * using the OpenStreetMap Nominatim API.
     *
     * @return Pair of (display_name, full_address_map) or null on failure.
     */
    private suspend fun resolveSemanticLocation(lat: Double, lon: Double): Pair<String, Map<String, String>>? {
        return try {
            val url = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lon&format=json"
            val client = io.ktor.client.HttpClient()
            val response = client.get(url) {
                headers {
                    append("User-Agent", "ClickApp/1.0")
                }
            }
            val body = response.bodyAsText()
            client.close()

            val jsonObj = kotlinx.serialization.json.Json.parseToJsonElement(body)
                .jsonObject

            val displayName = jsonObj["display_name"]?.jsonPrimitive?.contentOrNull
            val addressObj = jsonObj["address"]?.jsonObject

            // Build a flat map of address components
            val addressMap = addressObj?.mapValues { (_, v) ->
                v.jsonPrimitive.contentOrNull ?: ""
            } ?: emptyMap()

            // Use a short display name: prefer building/amenity, then road, then full display_name
            val shortName = addressMap["building"]
                ?: addressMap["amenity"]
                ?: addressMap["leisure"]
                ?: addressMap["tourism"]
                ?: listOfNotNull(
                    addressMap["road"],
                    addressMap["neighbourhood"] ?: addressMap["suburb"]
                ).joinToString(", ").ifEmpty { null }
                ?: displayName

            if (shortName != null) {
                Pair(shortName, addressMap)
            } else {
                null
            }
        } catch (e: Exception) {
            println("ConnectionRepository: Nominatim reverse geocode failed: ${e.message}")
            null
        }
    }


    /**
     * Check if a connection already exists between two users
     */
    private suspend fun checkExistingConnection(
        userId1: String,
        userId2: String
    ): Connection? {
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        filter("user_ids", io.github.jan.supabase.postgrest.query.filter.FilterOperator.CS, "{${userId1},${userId2}}")
                    }
                }
                .decodeSingleOrNull<Connection>()
        } catch (e: Exception) {
            println("Error checking connection: ${e.message}")
            null
        }
    }

    /**
     * Get all connections for a user
     */
    suspend fun getUserConnections(userId: String): Result<List<Connection>> {
        return try {
            val result = supabase.from("connections")
                .select {
                    filter {
                        filter("user_ids", io.github.jan.supabase.postgrest.query.filter.FilterOperator.CS, "{${userId}}")
                    }
                }
                .decodeList<Connection>()
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val user = supabaseRepository.fetchUsersByIds(listOf(userId)).firstOrNull()
                ?: return Result.failure(Exception("User not found"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun redeemQrToken(token: String): Result<RedeemQrTokenResponse> {
        return try {
            val response = supabase.postgrest.rpc(
                "redeem_qr_token",
                buildJsonObject {
                    put("p_token", token)
                }
            ).decodeSingle<RedeemQrTokenResponse>()

            if (!response.success) {
                val message = when (response.error) {
                    "expired" -> "This QR code has expired. Ask them to generate a new one."
                    "already_used" -> "This QR code was already used. Ask them to generate a new one."
                    "not_found" -> "This QR code is no longer valid. Ask them to generate a new one."
                    else -> "Failed to redeem QR code"
                }
                Result.failure(Exception(message))
            } else if (response.userId.isNullOrBlank()) {
                Result.failure(Exception("Invalid QR code"))
            } else {
                Result.success(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a connection
     */
    suspend fun deleteConnection(connectionId: String): Result<Unit> {
        return try {
            supabase.from("connections")
                .delete {
                    filter {
                        eq("id", connectionId)
                    }
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}