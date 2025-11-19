package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import io.github.jan.supabase.postgrest.from

/**
 * Repository for Supabase operations
 * Handles direct database queries for users and connections
 */
class SupabaseRepository {
    private val supabase = SupabaseConfig.client

    /**
     * Fetch a user by their ID
     */
    suspend fun fetchUserById(userId: String): User? {
        return try {
            supabase.from("users")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<User>()
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    /**
     * Fetch all connections for a user
     */
    suspend fun fetchUserConnections(userId: String): List<Connection> {
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        or {
                            eq("user_ids", "cs.{$userId}")
                        }
                    }
                }
                .decodeList<Connection>()
        } catch (e: Exception) {
            println("Error fetching connections: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch a connection by ID
     */
    suspend fun fetchConnectionById(connectionId: String): Connection? {
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        eq("id", connectionId)
                    }
                }
                .decodeSingle<Connection>()
        } catch (e: Exception) {
            println("Error fetching connection: ${e.message}")
            null
        }
    }

    /**
     * Fetch multiple users by their IDs
     */
    suspend fun fetchUsersByIds(userIds: List<String>): List<User> {
        return try {
            if (userIds.isEmpty()) return emptyList()

            supabase.from("users")
                .select {
                    filter {
                        isIn("id", userIds)
                    }
                }
                .decodeList<User>()
        } catch (e: Exception) {
            println("Error fetching users: ${e.message}")
            emptyList()
        }
    }

    /**
     * Update user's last polled timestamp
     */
    suspend fun updateUserLastPolled(userId: String, timestamp: Long): Boolean {
        return try {
            supabase.from("users")
                .update({
                    set("last_polled", timestamp)
                }) {
                    filter {
                        eq("id", userId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error updating user: ${e.message}")
            false
        }
    }

    /**
     * Update connection should_continue status
     */
    suspend fun updateConnectionShouldContinue(
        connectionId: String,
        shouldContinue: List<Boolean>
    ): Boolean {
        return try {
            supabase.from("connections")
                .update({
                    set("should_continue", shouldContinue)
                }) {
                    filter {
                        eq("id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error updating connection: ${e.message}")
            false
        }
    }
}

