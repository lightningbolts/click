package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.data.models.User
import io.github.jan.supabase.postgrest.from
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class ConnectionRepository {
    private val supabase = SupabaseConfig.client

    /**
     * Create a connection between two users
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

            // Use a map for insertion to let DB generate ID
            val connectionData = mapOf(
                "user_ids" to listOf(request.userId1, request.userId2),
                "geo_location" to mapOf(
                    "lat" to (request.locationLat ?: 0.0),
                    "lon" to (request.locationLng ?: 0.0)
                ),
                "created" to now,
                "expiry" to expiry,
                "should_continue" to listOf(false, false),
                "has_begun" to false
            )

            val result = supabase.from("connections")
                .insert(connectionData) {
                    select()
                }
                .decodeSingle<Connection>()

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
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
                        // Check if user_ids array contains both users
                        // Using 'cs' operator for array containment
                        // The filter function expects column, operator, value
                        // For array containment, we use "cs" (contains)
                        // The value should be a string representation of the array: "{id1,id2}"
                        filter("user_ids", io.github.jan.supabase.postgrest.query.filter.FilterOperator.CS, "{${userId1},${userId2}}")
                    }
                }
                .decodeSingleOrNull<Connection>()
        } catch (e: Exception) {
            // Log error if needed
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