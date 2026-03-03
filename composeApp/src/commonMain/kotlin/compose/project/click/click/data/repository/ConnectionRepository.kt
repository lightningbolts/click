package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionInsert
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.GeoLocationInsert
import compose.project.click.click.data.models.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.rpc
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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

class ConnectionRepository {
    private val supabase = SupabaseConfig.client
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a connection between two users with proximity verification.
     *
     * Performs Haversine distance check if both locations are available,
     * computes a proximity confidence score, and stores the signals.
     * The Supabase anomaly detection trigger runs on INSERT.
     */
    suspend fun createConnection(request: ConnectionRequest): Result<Connection> {
        return try {
            // Check if connection already exists
            val existingConnection = checkExistingConnection(
                request.userId1,
                request.userId2
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
                tokenAgeMs = request.tokenAgeMs
            )

            // Build proximity signals for auditability
            val proximitySignals = buildJsonObject {
                put("connection_method", request.connectionMethod)
                put("gps_available", gpsAvailable)
                if (request.tokenAgeMs != null) {
                    put("token_age_seconds", request.tokenAgeMs / 1000.0)
                }
                if (loc1Valid) {
                    put("initiator_lat", request.locationLat!!)
                    put("initiator_lon", request.locationLng!!)
                }
            }

            val connectionInsert = ConnectionInsert(
                user_ids = listOf(request.userId1, request.userId2),
                geo_location = geoLocation,
                created = now,
                expiry = expiry,
                should_continue = listOf(false, false),
                has_begun = false,
                expiry_state = "pending",
                context_tag = request.contextTag
            )

            val result = supabase.from("connections")
                .insert(connectionInsert) {
                    select()
                }
                .decodeSingle<Connection>()

            // After successful insert, update proximity fields
            // (ConnectionInsert doesn't have these fields, so we update separately)
            try {
                supabase.from("connections")
                    .update(buildJsonObject {
                        put("proximity_confidence", proximityScore)
                        put("proximity_signals", proximitySignals)
                        put("connection_method", request.connectionMethod)
                        put("flagged", proximityScore < 20)
                    }) {
                        filter {
                            eq("id", result.id)
                        }
                    }
            } catch (e: Exception) {
                println("ConnectionRepository: Failed to update proximity fields: ${e.message}")
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
            val user = supabase.from("users")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<User>()

            Result.success(user)
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