package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig
import io.github.jan.supabase.exceptions.RestException
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.LocationPreferences
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserCore
import compose.project.click.click.data.models.UserPublicProfile
import compose.project.click.click.data.models.AvailabilityIntentInsert // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.UserInterests
import compose.project.click.click.data.models.isResolvedDisplayName
import compose.project.click.click.data.models.resolveDisplayName
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Result of inserting into [public.availability_intents]. */
data class AvailabilityIntentInsertResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

/**
 * Repository for Supabase operations
 * Handles direct database queries for users and connections
 */
class SupabaseRepository {
    /** Lazy so unit tests can construct the repository without touching Android Settings / Supabase client. */
    private val supabase by lazy { SupabaseConfig.client }
    private val userColumnSets = listOf(
        listOf("id", "name", "full_name", "first_name", "last_name", "birthday", "email", "image", "last_polled"),
        listOf("id", "name", "full_name", "email", "image", "last_polled"),
        listOf("id", "name", "email", "image", "last_polled"),
        listOf("id", "name", "full_name", "email", "image"),
        listOf("id", "name", "email", "image")
    )

    @Serializable
    private data class DisplayNameRpcRow(
        val id: String,
        @SerialName("display_name")
        val displayName: String,
        val email: String? = null,
        val image: String? = null,
        @SerialName("last_polled")
        val lastPolled: Long? = null,
    ) {
        fun toUser(): User = User(
            id = id,
            name = resolveDisplayName(
                firstName = null,
                lastName = null,
                fullName = displayName,
                name = null,
                email = email
            ),
            email = email,
            image = image,
            createdAt = 0L,
            lastPolled = lastPolled,
            connections = emptyList(),
            paired_with = emptyList(),
            connection_today = -1,
            last_paired = null,
        )
    }

    /**
     * Fetch a user by their ID
     * Only fetches core columns that definitely exist
     */
    suspend fun fetchUserById(userId: String): User? {
        return try {
            println("Fetching user with ID: $userId")
            val result = fetchUserCoresByIds(listOf(userId))
            println("Found ${result.size} user(s)")
            result.firstOrNull()?.toUser()
        } catch (e: Exception) {
            println("Error fetching user by ID '$userId': ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Loads [User], [user_interests] tags, and [user_availability] for a profile sheet.
     * When [viewerUserId] is non-null, attaches the most relevant mutual [Connection] row.
     * RLS must allow the current user to read the target (e.g. mutual connection).
     */
    suspend fun fetchUserPublicProfile(viewerUserId: String?, targetUserId: String): UserPublicProfile? {
        val user = fetchUserById(targetUserId) ?: return null
        val tags = fetchUserInterests(targetUserId).getOrNull()?.tags.orEmpty()
        val availability = fetchUserAvailability(targetUserId)
        val shared = viewerUserId?.takeIf { it.isNotBlank() && it != targetUserId }?.let { v ->
            fetchSharedConnectionBetween(v, targetUserId)
        }
        return UserPublicProfile(
            user = user,
            interestTags = tags,
            availability = availability,
            sharedConnection = shared,
        )
    }

    /**
     * Mutual connection between two users (same `user_ids` pair). If multiple rows exist,
     * picks the one with the latest activity (`last_message_at` or `created`).
     */
    suspend fun fetchSharedConnectionBetween(viewerUserId: String, peerUserId: String): Connection? {
        if (viewerUserId.isBlank() || peerUserId.isBlank()) return null
        return try {
            val rows = supabase.from("connections")
                .select {
                    filter {
                        contains("user_ids", listOf(viewerUserId, peerUserId))
                    }
                }
                .decodeList<Connection>()
                .filter { it.isVisibleInActiveUi() }
            rows.maxByOrNull { conn ->
                (conn.last_message_at ?: 0L).coerceAtLeast(conn.created)
            }
        } catch (e: Exception) {
            println("Error fetchSharedConnectionBetween: ${e.message}")
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
            // Try server-side status filter first; fall back to client-side for legacy schemas
            val withServerFilter = runCatching {
                supabase.from("connections")
                    .select {
                        filter {
                            contains("user_ids", listOf(userId))
                            filterNot(
                                "status",
                                io.github.jan.supabase.postgrest.query.filter.FilterOperator.IN,
                                "('archived','removed')",
                            )
                        }
                        order("created", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                        range(page * pageSize.toLong(), (page + 1) * pageSize.toLong() - 1)
                    }
                    .decodeList<Connection>()
            }
            if (withServerFilter.isSuccess) return withServerFilter.getOrThrow()

            supabase.from("connections")
                .select {
                    filter {
                        contains("user_ids", listOf(userId))
                    }
                    order("created", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(page * pageSize.toLong(), (page + 1) * pageSize.toLong() - 1)
                }
                .decodeList<Connection>()
                .filter { it.isVisibleInActiveUi() }
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
     * Fetch multiple users by their IDs.
     * Runs a table query and the display-name RPC in parallel so that
     * even if the public.users table lacks name/full_name the RPC can
     * still resolve names from auth metadata.  This eliminates the
     * sequential dependency that previously left names as "Connection"
     * when the RPC happened to fail after an already-null table result.
     */
    suspend fun fetchUsersByIds(userIds: List<String>): List<User> {
        if (userIds.isEmpty()) return emptyList()

        return try {
            val (tableUsers, rpcUsers) = coroutineScope {
                val tableDeferred = async {
                    fetchUserCoresByIds(userIds)
                }
                val rpcDeferred = async { fetchDisplayNamesViaRpc(userIds) }
                tableDeferred.await() to rpcDeferred.await()
            }

            val tableById = tableUsers.associate { it.id to it.toUser() }
            val rpcById = rpcUsers.associateBy { it.id }

            // Merge: prefer RPC-resolved name (checks auth metadata), fall back to table
            val interestsByUserId = fetchUserInterestsMap(userIds)
            userIds.mapNotNull { userId ->
                val rpcUser = rpcById[userId]
                val tableUser = tableById[userId]
                val merged = when {
                    rpcUser != null && isResolvedDisplayName(rpcUser.name) -> {
                        // RPC gave a real name — merge with any extra table data
                        rpcUser.copy(
                            image = rpcUser.image ?: tableUser?.image,
                            lastPolled = rpcUser.lastPolled ?: tableUser?.lastPolled,
                            email = rpcUser.email ?: tableUser?.email
                        )
                    }
                    tableUser != null && isResolvedDisplayName(tableUser.name) -> tableUser
                    rpcUser != null -> rpcUser
                    tableUser != null -> tableUser
                    else -> null
                }
                merged?.copy(tags = interestsByUserId[userId] ?: merged.tags)
            }
        } catch (e: Exception) {
            println("Error fetching users: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchDisplayNamesViaRpc(userIds: List<String>): List<User> {
        return try {
            supabase.postgrest.rpc("get_user_display_names", buildJsonObject {
                put("user_ids", kotlinx.serialization.json.JsonArray(userIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }).decodeList<DisplayNameRpcRow>().map { it.toUser() }
        } catch (e: Exception) {
            println("Error resolving display names via RPC: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchUserCoresByIds(userIds: List<String>): List<UserCore> {
        var lastError: Throwable? = null

        for (columns in userColumnSets) {
            val attempt = runCatching {
                supabase.from("users")
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list(*columns.toTypedArray())) {
                        filter { isIn("id", userIds) }
                    }
                    .decodeList<UserCore>()
            }

            if (attempt.isSuccess) {
                return attempt.getOrThrow()
            }

            lastError = attempt.exceptionOrNull()
        }

        println("Error fetching users with all schema variants: ${lastError?.message}")
        return emptyList()
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
     * Update connection expiry_state lifecycle.
     * Valid states: 'pending', 'active', 'kept', 'expired'
     */
    suspend fun updateConnectionExpiryState(
        connectionId: String,
        state: String
    ): Boolean {
        return try {
            if (state == "pending" || state == "active" || state == "kept") {
                val withStatus = runCatching {
                    supabase.from("connections")
                        .update({
                            set("expiry_state", state)
                            set("status", state)
                        }) {
                            filter { eq("id", connectionId) }
                        }
                }
                if (withStatus.isSuccess) return true
                println("updateConnectionExpiryState (status column may be missing): ${withStatus.exceptionOrNull()?.message}")
            }
            supabase.from("connections")
                .update({
                    set("expiry_state", state)
                }) {
                    filter {
                        eq("id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("Error updating connection expiry_state: ${e.message}")
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
     * Soft-remove a connection (user-initiated "Remove Connection").
     * Row stays in the database for possible future reconnection tracking.
     */
    suspend fun deleteConnection(connectionId: String): Boolean {
        return try {
            val withStatus = runCatching {
                supabase.from("connections")
                    .update({
                        set("status", "removed")
                        set("expiry_state", "expired")
                    }) {
                        filter { eq("id", connectionId) }
                    }
            }
            if (withStatus.isSuccess) return true
            println("deleteConnection soft-remove (retry without status): ${withStatus.exceptionOrNull()?.message}")
            supabase.from("connections")
                .update({
                    set("expiry_state", "expired")
                }) {
                    filter { eq("id", connectionId) }
                }
            true
        } catch (e: Exception) {
            println("Error soft-removing connection: ${e.message}")
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
                // Insert new record using serializable DTO (let Supabase generate ID)
                supabase.from("user_availability")
                    .insert(availability.toInsertDto())
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
     * Inserts one row into [public.availability_intents] for the current user.
     */
    suspend fun insertAvailabilityIntent(row: AvailabilityIntentInsert): AvailabilityIntentInsertResult {
        return try {
            supabase.from("availability_intents").insert(row)
            AvailabilityIntentInsertResult(success = true)
        } catch (e: Exception) {
            val short = restErrorSummary(e)
            println("Error inserting availability_intent: $short")
            e.printStackTrace()
            AvailabilityIntentInsertResult(success = false, errorMessage = short)
        }
    }

    /**
     * Active intent rows for [userId] where expiry is in the future (local server/client clock).
     */
    suspend fun fetchActiveAvailabilityIntentsForUser(userId: String): List<AvailabilityIntentRow> {
        if (userId.isBlank()) return emptyList()
        return try {
            val rows = supabase.from("availability_intents")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<AvailabilityIntentRow>()
            val now = Clock.System.now()
            rows
                .filter { row ->
                    val end = row.expiresInstantOrNull() ?: return@filter false
                    end > now
                }
                .sortedByDescending { it.createdOrStartInstant() }
        } catch (e: Exception) {
            println("Error fetching availability_intents: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Updates one [public.availability_intents] row (must belong to [userId]; enforced by RLS).
     */
    suspend fun updateAvailabilityIntent(
        id: String,
        userId: String,
        intentTag: String,
        timeframe: String,
        startsAt: String,
        endsAt: String,
        expiresAt: String,
    ): AvailabilityIntentInsertResult {
        if (id.isBlank() || userId.isBlank()) {
            return AvailabilityIntentInsertResult(success = false, errorMessage = "Missing intent id.")
        }
        return try {
            supabase.from("availability_intents")
                .update({
                    set("intent_tag", intentTag)
                    set("timeframe", timeframe)
                    set("starts_at", startsAt)
                    set("ends_at", endsAt)
                    set("expires_at", expiresAt)
                }) {
                    filter {
                        eq("id", id)
                        eq("user_id", userId)
                    }
                }
            AvailabilityIntentInsertResult(success = true)
        } catch (e: Exception) {
            val short = restErrorSummary(e)
            println("Error updating availability_intent: $short")
            e.printStackTrace()
            AvailabilityIntentInsertResult(success = false, errorMessage = short)
        }
    }

    /**
     * Deletes one row by primary key (RLS restricts to the signed-in user’s rows).
     */
    suspend fun deleteAvailabilityIntent(intentId: String): AvailabilityIntentInsertResult {
        if (intentId.isBlank()) {
            return AvailabilityIntentInsertResult(success = false, errorMessage = "Missing intent id.")
        }
        return try {
            supabase.from("availability_intents")
                .delete {
                    filter {
                        eq("id", intentId)
                    }
                }
            AvailabilityIntentInsertResult(success = true)
        } catch (e: Exception) {
            val short = restErrorSummary(e)
            println("Error deleting availability_intent: $short")
            e.printStackTrace()
            AvailabilityIntentInsertResult(success = false, errorMessage = short)
        }
    }
    
    /**
     * Update user's name
     */
    suspend fun updateUserName(userId: String, name: String): Boolean {
        val trimmed = name.trim()
        val spaceIdx = trimmed.indexOf(' ')
        val first = if (spaceIdx < 0) trimmed else trimmed.take(spaceIdx).trim()
        val last = if (spaceIdx < 0) "" else trimmed.substring(spaceIdx + 1).trim()
        return updateUserProfileNames(userId, first, last)
    }

    /**
     * Updates [public.users] display fields from explicit first/last name.
     */
    suspend fun updateUserProfileNames(userId: String, firstName: String, lastName: String): Boolean {
        val f = firstName.trim()
        val l = lastName.trim()
        if (f.isEmpty()) return false
        val display = listOf(f, l).filter { it.isNotEmpty() }.joinToString(" ")
        return try {
            println("Updating user profile names for $userId to: $display")
            runCatching {
                supabase.from("users")
                    .update({
                        set("name", display)
                        set("full_name", display)
                        set("first_name", f)
                        set("last_name", l)
                    }) {
                        filter {
                            eq("id", userId)
                        }
                    }
            }.getOrElse {
                supabase.from("users")
                    .update({
                        set("name", display)
                    }) {
                        filter {
                            eq("id", userId)
                        }
                    }
            }
            println("Successfully updated user profile names for $userId")
            true
        } catch (e: Exception) {
            println("Error updating user profile names: ${e.message}")
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
            val resolvedName = user.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: user.email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
                ?: "User"
            val resolvedFirst = user.firstName?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedLast = user.lastName?.trim()?.takeIf { it.isNotEmpty() }
            val resolvedBirthday = user.birthday?.trim()?.takeIf { it.isNotEmpty() }

            if (existing != null) {
                val profileChanged =
                    existing.name != resolvedName ||
                        (user.email != null && existing.email != user.email) ||
                        existing.image != user.image ||
                        (user.firstName != null && user.firstName != existing.firstName) ||
                        (user.lastName != null && user.lastName != existing.lastName) ||
                        (user.birthday != null && user.birthday != existing.birthday)
                if (profileChanged) {
                    runCatching {
                        supabase.from("users")
                            .update({
                                set("name", resolvedName)
                                set("full_name", resolvedName)
                                resolvedFirst?.let { set("first_name", it) }
                                resolvedLast?.let { set("last_name", it) }
                                resolvedBirthday?.let { set("birthday", it) }
                                user.email?.let { set("email", it) }
                                user.image?.let { set("image", it) }
                            }) {
                                filter {
                                    eq("id", user.id)
                                }
                            }
                    }.getOrElse {
                        supabase.from("users")
                            .update({
                                set("name", resolvedName)
                                user.email?.let { set("email", it) }
                                user.image?.let { set("image", it) }
                            }) {
                                filter {
                                    eq("id", user.id)
                                }
                            }
                    }
                    println("Updated existing user profile: $resolvedName")
                }
                true
            } else {
                // Insert a valid user row so other clients can resolve this user's name directly from Supabase.
                runCatching {
                    supabase.from("users")
                        .insert(
                            buildJsonObject {
                                put("id", user.id)
                                put("name", resolvedName)
                                put("full_name", resolvedName)
                                resolvedFirst?.let { put("first_name", it) }
                                resolvedLast?.let { put("last_name", it) }
                                resolvedBirthday?.let { put("birthday", it) }
                                put("email", user.email ?: "")
                                put("created_at", if (user.createdAt > 0L) user.createdAt else kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
                                user.image?.let { put("image", it) }
                                user.lastPolled?.let { put("last_polled", it) }
                            }
                        )
                }.getOrElse {
                    supabase.from("users")
                        .insert(user.toInsertDto().copy(name = resolvedName, email = user.email ?: ""))
                }
                println("Inserted new user: ${user.id}")
                true
            }
        } catch (e: Exception) {
            println("Error upserting user: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    @Serializable
    private data class UserInterestsDto(
        @SerialName("user_id")
        val userId: String,
        val tags: List<String> = emptyList(),
        @SerialName("updated_at")
        val updatedAt: Long = 0L,
    )

    @Serializable
    private data class UserInterestsInsertDto(
        @SerialName("user_id")
        val userId: String,
        val tags: List<String>,
        @SerialName("updated_at")
        val updatedAt: Long,
    )

    /**
     * Load the current user's row from [public.user_interests].
     *
     * @return [Result.success] with `null` when no row exists; [Result.failure] on transport/schema errors.
     */
    suspend fun fetchUserInterests(userId: String): Result<UserInterests?> {
        return try {
            val rows = supabase.from("user_interests")
                .select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }
                .decodeList<UserInterestsDto>()
            val row = rows.firstOrNull()
            Result.success(
                row?.let {
                    UserInterests(userId = it.userId, tags = it.tags, updatedAt = it.updatedAt)
                },
            )
        } catch (e: Exception) {
            println("Error fetching user_interests: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Insert or update interest tags for the user (canonical store for onboarding + Common Ground).
     */
    suspend fun updateUserInterests(userId: String, tags: List<String>): Boolean {
        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        return try {
            when (val existing = fetchUserInterests(userId).getOrNull()) {
                null -> {
                    supabase.from("user_interests")
                        .insert(
                            UserInterestsInsertDto(
                                userId = userId,
                                tags = tags,
                                updatedAt = now,
                            ),
                        )
                }
                else -> {
                    supabase.from("user_interests")
                        .update({
                            set("tags", tags)
                            set("updated_at", now)
                        }) {
                            filter { eq("user_id", userId) }
                        }
                }
            }
            true
        } catch (e: Exception) {
            println("Error updating user_interests: ${e.message}")
            false
        }
    }

    private suspend fun fetchUserInterestsMap(userIds: List<String>): Map<String, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            supabase.from("user_interests")
                .select {
                    filter {
                        isIn("user_id", userIds)
                    }
                }
                .decodeList<UserInterestsDto>()
                .associate { it.userId to it.tags }
        } catch (e: Exception) {
            println("Error batch-fetching user_interests: ${e.message}")
            emptyMap()
        }
    }

    // ==================== Location preferences ====================

    /**
     * Fetch location privacy preferences for a user.
     * Returns default (all true) if columns are missing or on error.
     */
    suspend fun fetchLocationPreferences(userId: String): LocationPreferences {
        return try {
            val result = supabase.from("users")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list(
                    "location_connection_snap_enabled",
                    "location_show_on_map_enabled",
                    "location_include_in_insights_enabled"
                )) {
                    filter { eq("id", userId) }
                }
                .decodeList<LocationPreferences>()
            result.firstOrNull() ?: LocationPreferences()
        } catch (e: Exception) {
            println("Error fetching location preferences: ${e.message}")
            LocationPreferences()
        }
    }

    /**
     * Update location privacy preferences for a user.
     */
    suspend fun updateLocationPreferences(userId: String, prefs: LocationPreferences): Boolean {
        return try {
            supabase.from("users")
                .update({
                    set("location_connection_snap_enabled", prefs.connectionSnapEnabled)
                    set("location_show_on_map_enabled", prefs.showOnMapEnabled)
                    set("location_include_in_insights_enabled", prefs.includeInInsightsEnabled)
                }) {
                    filter { eq("id", userId) }
                }
            true
        } catch (e: Exception) {
            println("Error updating location preferences: ${e.message}")
            false
        }
    }

    // ==================== Safety Methods ====================

    /**
     * Block a user. Inserts into user_blocks table.
     */
    suspend fun blockUser(blockerId: String, blockedId: String): Boolean {
        return try {
            supabase.from("user_blocks")
                .insert(buildJsonObject {
                    put("blocker_id", blockerId)
                    put("blocked_id", blockedId)
                })
            true
        } catch (e: Exception) {
            println("Error blocking user: ${e.message}")
            false
        }
    }

    /**
     * Report a connection for safety review.
     */
    suspend fun reportConnection(connectionId: String, reporterId: String, reason: String): Boolean {
        return try {
            supabase.from("connection_reports")
                .insert(buildJsonObject {
                    put("connection_id", connectionId)
                    put("reporter_id", reporterId)
                    put("reason", reason)
                })
            true
        } catch (e: Exception) {
            println("Error reporting connection: ${e.message}")
            false
        }
    }

    // ==================== Archive Methods ====================

    /**
     * Archive a connection for the given user.
     * Inserts into the connection_archives table (see database/add_connection_archives.sql).
     * Silently no-ops if the table has not been provisioned yet.
     */
    suspend fun archiveConnection(userId: String, connectionId: String): Boolean {
        return try {
            supabase.from("connection_archives")
                .insert(buildJsonObject {
                    put("user_id", userId)
                    put("connection_id", connectionId)
                })
            true
        } catch (e: Exception) {
            println("archiveConnection (non-fatal): ${e.message}")
            false // table may not exist yet; in-memory fallback handles UI state
        }
    }

    /**
     * Unarchive a connection, removing it from the user's archive list.
     */
    suspend fun unarchiveConnection(userId: String, connectionId: String): Boolean {
        return try {
            supabase.from("connection_archives")
                .delete {
                    filter {
                        eq("user_id", userId)
                        eq("connection_id", connectionId)
                    }
                }
            true
        } catch (e: Exception) {
            println("unarchiveConnection (non-fatal): ${e.message}")
            false
        }
    }

    /**
     * Fetch all archived connection IDs for a user.
     */
    suspend fun getArchivedConnectionIds(userId: String): Set<String> {
        return try {
            @kotlinx.serialization.Serializable
            data class ArchiveRow(
                @kotlinx.serialization.SerialName("connection_id") val connectionId: String
            )
            val rows = supabase.from("connection_archives")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("connection_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<ArchiveRow>()
            rows.map { it.connectionId }.toSet()
        } catch (e: Exception) {
            println("getArchivedConnectionIds (non-fatal): ${e.message}")
            emptySet()
        }
    }

    // ==================== Message Edit / Delete (direct Supabase) ====================

    /**
     * Edit the content of an existing message and stamp time_edited.
     * Encrypts the new content if the original message was encrypted.
     */
    suspend fun editMessage(messageId: String, newContent: String, chatId: String? = null): Boolean {
        return try {
            val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()

            var wireContent = newContent
            if (chatId != null) {
                val chatRepo = SupabaseChatRepository(tokenStorage = compose.project.click.click.data.storage.createTokenStorage())
                // Attempt encryption if we can resolve keys
                try {
                    val chat = supabase.from("chats")
                        .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("connection_id")) {
                            filter { eq("id", chatId) }
                            limit(1)
                        }
                        .decodeList<ChatConnectionIdOnly>()
                        .firstOrNull()

                    if (chat != null) {
                        val connection = supabase.from("connections")
                            .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("id", "user_ids")) {
                                filter { eq("id", chat.connectionId) }
                                limit(1)
                            }
                            .decodeList<ConnectionUserIdsOnlyRow>()
                            .firstOrNull()

                        if (connection != null) {
                            val keys = compose.project.click.click.crypto.MessageCrypto.deriveKeysForConnection(
                                connection.id, connection.userIds
                            )
                            wireContent = compose.project.click.click.crypto.MessageCrypto.encryptContent(newContent, keys)
                        }
                    }
                } catch (_: Exception) { /* fall through with plaintext */ }
            }

            supabase.from("messages")
                .update({
                    set("content", wireContent)
                    set("time_edited", now)
                }) {
                    filter { eq("id", messageId) }
                }
            true
        } catch (e: Exception) {
            println("Error editing message: ${e.message}")
            false
        }
    }

    @kotlinx.serialization.Serializable
    private data class ChatConnectionIdOnly(
        @kotlinx.serialization.SerialName("connection_id")
        val connectionId: String
    )

    @kotlinx.serialization.Serializable
    private data class ConnectionUserIdsOnlyRow(
        val id: String,
        @kotlinx.serialization.SerialName("user_ids")
        val userIds: List<String>
    )

    /**
     * Hard-delete a single message.
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        return try {
            supabase.from("messages")
                .delete {
                    filter { eq("id", messageId) }
                }
            true
        } catch (e: Exception) {
            println("Error deleting message: ${e.message}")
            false
        }
    }

    /** Short PostgREST / Supabase error (not the long RestException message with URL/headers). */
    private fun restErrorSummary(e: Throwable): String {
        var t: Throwable? = e
        while (t != null) {
            if (t is RestException) return t.error
            t = t.cause
        }
        return e.message
            ?.substringBefore("\nURL:")
            ?.trim()
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?: e::class.simpleName
            ?: "Unknown error"
    }
}

