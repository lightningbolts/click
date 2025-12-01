package compose.project.click.click.data.repository


import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.ConnectionRequest
import compose.project.click.click.models.Connection
import compose.project.click.click.models.ConnectionRequest
import compose.project.click.click.models.User
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.*
import io.github.jan.supabase.postgrest.decode.*

class ConnectionRepository {
    private val supabase = SupabaseClient.client

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

            // Create new connection
            val connection = Connection(
                userId1 = request.userId1,
                userId2 = request.userId2,
                locationLat = request.locationLat,
                locationLng = request.locationLng,
                createdAt = System.currentTimeMillis()
            )

            val result = supabase.from("connections")
                .insert(connection)
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
            val connections = supabase.from("connections")
                .select {
                    or {
                        and {
                            eq("userId1", userId1)
                            eq("userId2", userId2)
                        }
                        and {
                            eq("userId1", userId2)
                            eq("userId2", userId1)
                        }
                    }
                }
                .decodeList<Connection>()

            connections.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get all connections for a user
     */
    suspend fun getUserConnections(userId: String): Result<List<Connection>> {
        return try {
            val connections = supabase.from("connections")
                .select {
                    or {
                        eq("userId1", userId)
                        eq("userId2", userId)
                    }
                    eq("status", "active")
                }
                .decodeList<Connection>()

            Result.success(connections)
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
                    eq("id", userId)
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
                    eq("id", connectionId)
                }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}