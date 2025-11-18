package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class SupabaseRepository {
    private val supabase = SupabaseConfig.client

    suspend fun fetchUserByName(name: String): User? {
        return try {
            val result = supabase.from("users")
                .select {
                    filter {
                        eq("name", name)
                    }
                }
                .decodeSingle<User>()
            result
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    suspend fun fetchUserById(id: String): User? {
        return try {
            val result = supabase.from("users")
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingle<User>()
            result
        } catch (e: Exception) {
            println("Error fetching user: ${e.message}")
            null
        }
    }

    suspend fun createUser(user: User): User? {
        return try {
            val result = supabase.from("users")
                .insert(user) {
                    select()
                }
                .decodeSingle<User>()
            result
        } catch (e: Exception) {
            println("Error creating user: ${e.message}")
            null
        }
    }

    suspend fun fetchConnectionById(id: String): Connection? {
        return try {
            val result = supabase.from("connections")
                .select {
                    filter {
                        eq("id", id)
                    }
                }
                .decodeSingle<Connection>()
            result
        } catch (e: Exception) {
            println("Error fetching connection: ${e.message}")
            null
        }
    }

    suspend fun createConnection(connection: Connection): Connection? {
        return try {
            val result = supabase.from("connections")
                .insert(connection) {
                    select()
                }
                .decodeSingle<Connection>()
            result
        } catch (e: Exception) {
            println("Error creating connection: ${e.message}")
            null
        }
    }

    suspend fun fetchUserConnections(userId: String): List<Connection> {
        return try {
            // For now, fetch all connections and filter in-memory
            // TODO: Update with proper PostgreSQL array query when supabase-kt supports it
            val allConnections = supabase.from("connections")
                .select()
                .decodeList<Connection>()

            allConnections.filter { connection ->
                connection.user_ids.contains(userId)
            }
        } catch (e: Exception) {
            println("Error fetching user connections: ${e.message}")
            emptyList()
        }
    }

    suspend fun updateConnection(id: String, connection: Connection): Connection? {
        return try {
            val result = supabase.from("connections")
                .update(connection) {
                    filter {
                        eq("id", id)
                    }
                    select()
                }
                .decodeSingle<Connection>()
            result
        } catch (e: Exception) {
            println("Error updating connection: ${e.message}")
            null
        }
    }
}

