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
            println("Fetching user with ID: $userId")
            val users = supabase.from("users")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<User>()
            println("Found ${users.size} user(s)")
            users.firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user by ID '$userId': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch connections for a user with pagination
     */
    suspend fun fetchUserConnections(
        userId: String, 
        page: Int = 0, 
        pageSize: Int = 20
    ): List<Connection> {
        return try {
            supabase.from("connections")
                .select {
                    filter {
                        or {
                            eq("user_ids", "cs.{$userId}")
                        }
                    }
                    order("created", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(page * pageSize.toLong(), (page + 1) * pageSize.toLong() - 1)
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
            val connections = supabase.from("connections")
                .select {
                    filter {
                        eq("id", connectionId)
                    }
                }
                .decodeList<Connection>()
            connections.firstOrNull()
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
    
    /**
     * Update connection has_begun status when chat starts (Vibe Check begins)
     */
    suspend fun updateConnectionHasBegun(
        connectionId: String,
        hasBegun: Boolean
    ): Boolean {
        return try {
            supabase.from("connections")
                .update({
                    set("has_begun", hasBegun)
                }) {
                    filter {
                        eq("id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error updating connection has_begun: ${e.message}")
            false
        }
    }
    
    /**
     * Update a specific user's keep decision for a connection.
     * @param connectionId The connection ID
     * @param userId The user making the decision
     * @param keepConnection Whether the user wants to keep the connection
     * @param currentShouldContinue The current should_continue list
     * @param userIds The user_ids list from the connection to determine index
     */
    suspend fun updateUserKeepDecision(
        connectionId: String,
        userId: String,
        keepConnection: Boolean,
        currentShouldContinue: List<Boolean>,
        userIds: List<String>
    ): Boolean {
        return try {
            val userIndex = userIds.indexOf(userId)
            if (userIndex < 0 || userIndex >= 2) {
                println("User not found in connection")
                return false
            }
            
            // Create new should_continue list with updated value
            val newShouldContinue = currentShouldContinue.toMutableList()
            // Ensure the list has at least 2 elements
            while (newShouldContinue.size < 2) {
                newShouldContinue.add(false)
            }
            newShouldContinue[userIndex] = keepConnection
            
            supabase.from("connections")
                .update({
                    set("should_continue", newShouldContinue.toList())
                }) {
                    filter {
                        eq("id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error updating user keep decision: ${e.message}")
            false
        }
    }
    
    /**
     * Delete a connection (used when Vibe Check expires without mutual keep)
     */
    suspend fun deleteConnection(connectionId: String): Boolean {
        return try {
            supabase.from("connections")
                .delete {
                    filter {
                        eq("id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error deleting connection: ${e.message}")
            false
        }
    }
    
    // ==================== Availability Methods ====================
    
    /**
     * Fetch a user's availability
     */
    suspend fun fetchUserAvailability(userId: String): compose.project.click.click.data.models.UserAvailability? {
        return try {
            val availabilities = supabase.from("user_availability")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<compose.project.click.click.data.models.UserAvailability>()
            availabilities.firstOrNull()
        } catch (e: Exception) {
            println("Error fetching user availability: ${e.message}")
            null
        }
    }
    
    /**
     * Fetch availability for multiple users
     */
    suspend fun fetchAvailabilityForUsers(userIds: List<String>): Map<String, compose.project.click.click.data.models.UserAvailability> {
        if (userIds.isEmpty()) return emptyMap()
        
        return try {
            val availabilities = supabase.from("user_availability")
                .select {
                    filter {
                        isIn("user_id", userIds)
                    }
                }
                .decodeList<compose.project.click.click.data.models.UserAvailability>()
            availabilities.associateBy { it.userId }
        } catch (e: Exception) {
            println("Error fetching availabilities: ${e.message}")
            emptyMap()
        }
    }
    
    /**
     * Update user's availability (upsert)
     * Uses manual field setting to avoid issues with empty ID
     */
    suspend fun updateUserAvailability(availability: compose.project.click.click.data.models.UserAvailability): Boolean {
        return try {
            // Check if record exists first
            val existing = fetchUserAvailability(availability.userId)
            
            if (existing != null) {
                // Update existing record
                supabase.from("user_availability")
                    .update({
                        set("is_free_this_week", availability.isFreeThisWeek)
                        set("available_days", availability.availableDays)
                        set("preferred_activities", availability.preferredActivities)
                        set("custom_status", availability.customStatus)
                        set("last_updated", availability.lastUpdated)
                    }) {
                        filter {
                            eq("user_id", availability.userId)
                        }
                    }
            } else {
                // Insert new record (let Supabase generate ID)
                supabase.from("user_availability")
                    .insert(mapOf(
                        "user_id" to availability.userId,
                        "is_free_this_week" to availability.isFreeThisWeek,
                        "available_days" to availability.availableDays,
                        "preferred_activities" to availability.preferredActivities,
                        "custom_status" to availability.customStatus,
                        "last_updated" to availability.lastUpdated
                    ))
            }
            println("Successfully updated availability for user ${availability.userId}: isFreeThisWeek=${availability.isFreeThisWeek}")
            true
        } catch (e: Exception) {
            println("Error updating availability: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Set user's "I'm free this week" status
     */
    suspend fun setFreeThisWeek(userId: String, isFree: Boolean): Boolean {
        return try {
            val existing = fetchUserAvailability(userId)
            val availability = existing?.copy(
                isFreeThisWeek = isFree,
                lastUpdated = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            ) ?: compose.project.click.click.data.models.UserAvailability(
                userId = userId,
                isFreeThisWeek = isFree,
                lastUpdated = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
            )
            val result = updateUserAvailability(availability)
            println("setFreeThisWeek for $userId: isFree=$isFree, result=$result")
            result
        } catch (e: Exception) {
            println("Error setting free this week: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Update user's name
     */
    suspend fun updateUserName(userId: String, name: String): Boolean {
        return try {
            println("Updating user name for $userId to: $name")
            supabase.from("users")
                .update({
                    set("name", name)
                }) {
                    filter {
                        eq("id", userId)
                    }
                }
            println("Successfully updated user name for $userId to: $name")
            true
        } catch (e: Exception) {
            println("Error updating user name: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Upsert a user record in the users table.
     * This ensures the user exists and is properly synchronized with Supabase Auth.
     */
    suspend fun upsertUser(user: compose.project.click.click.data.models.User): Boolean {
        return try {
            println("Upserting user: ${user.id}, name: ${user.name}")
            
            // Check if user exists
            val existing = fetchUserById(user.id)
            
            if (existing != null) {
                // Update existing user if name changed
                if (existing.name != user.name && user.name != null) {
                    supabase.from("users")
                        .update({
                            set("name", user.name)
                        }) {
                            filter {
                                eq("id", user.id)
                            }
                        }
                    println("Updated existing user name: ${user.name}")
                }
                true
            } else {
                // Insert new user
                supabase.from("users")
                    .insert(mapOf(
                        "id" to user.id,
                        "name" to user.name,
                        "email" to user.email,
                        "image" to user.image,
                        "created_at" to user.createdAt,
                        "last_polled" to user.lastPolled,
                        "connections" to user.connections,
                        "paired_with" to user.paired_with,
                        "connection_today" to user.connection_today,
                        "last_paired" to user.last_paired
                    ))
                println("Inserted new user: ${user.id}")
                true
            }
        } catch (e: Exception) {
            println("Error upserting user: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

