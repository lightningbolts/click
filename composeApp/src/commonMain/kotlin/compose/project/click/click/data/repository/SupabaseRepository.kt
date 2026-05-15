package compose.project.click.click.data.repository

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import io.github.jan.supabase.exceptions.RestException
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserCore // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentInsert // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.UserInterests // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.models.resolveDisplayName // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")

private fun Connection.withEncountersSortedNewestFirst(): Connection =
    copy(connectionEncounters = connectionEncounters.sortedByDescending { it.encounteredAt })

private fun List<Connection>.withEncountersSortedNewestFirst(): List<Connection> =
    map { it.withEncountersSortedNewestFirst() }

/** Result of inserting into [public.availability_intents]. */
data class AvailabilityIntentInsertResult(
    val success: Boolean,
    val errorMessage: String? = null,
)

/** Connections and junction IDs after lazy sweep + two-step fetch (aligned with `GET /api/connections`). */
data class UserConnectionsSnapshot(
    val connections: List<Connection>,
    val archivedConnectionIds: Set<String>,
    val hiddenConnectionIds: Set<String>,
)

/**
 * Repository for Supabase operations
 * Handles direct database queries for users and connections
 */
class SupabaseRepository {
    /** Lazy so unit tests can construct the repository without touching Android Settings / Supabase client. */
    private val supabase by lazy { SupabaseConfig.client }

    /**
     * BFF client for Next.js profile / tabs endpoints. Constructed lazily so unit tests
     * can still new up the repository without spinning up Ktor / Supabase auth plumbing.
     */
    private val apiClient by lazy { ApiClient() }

    /** Next.js click-web secure writes (JWT bearer). */
    private val clickWebApi by lazy { ApiClient() }

    /**
     * When the remote `users.last_polled` column is missing, PostgREST rejects PATCHes; skip further writes
     * for this process (apply [database/add_users_last_polled_column.sql] on the project to restore).
     */
    private var lastPolledWritesDisabled: Boolean = false

    /**
     * When PostgREST has no `connection_archives` table, skip further queries until process restart
     * (apply [database/add_connection_archives.sql] to enable user-level archives).
     */
    private var connectionArchivesTableMissing: Boolean = false
    private var connectionHiddenTableMissing: Boolean = false

    companion object {
        private val userPublicProfileCache =
            MutableStateFlow<Map<String, UserPublicProfile>>(emptyMap())
    }

    fun getCachedUserPublicProfile(targetUserId: String): UserPublicProfile? {
        val key = targetUserId.trim()
        if (key.isEmpty()) return null
        return userPublicProfileCache.value[key]
    }

    fun observeCachedUserPublicProfile(targetUserId: String): Flow<UserPublicProfile?> {
        val key = targetUserId.trim()
        if (key.isEmpty()) {
            return userPublicProfileCache.map { null }.distinctUntilChanged()
        }
        return userPublicProfileCache
            .map { it[key] }
            .distinctUntilChanged()
    }

    fun snapshotCachedUserPublicProfiles(): List<UserPublicProfile> =
        userPublicProfileCache.value.values.toList()

    fun seedCachedUserPublicProfiles(profiles: List<UserPublicProfile>) {
        val seeded = profiles
            .filter { it.user.id.isNotBlank() }
            .associateBy { it.user.id.trim() }
        if (seeded.isEmpty()) return
        userPublicProfileCache.value = userPublicProfileCache.value + seeded
    }

    fun clearCachedUserPublicProfiles() {
        userPublicProfileCache.value = emptyMap()
    }

    private fun cacheUserPublicProfile(targetUserId: String, profile: UserPublicProfile) {
        val key = targetUserId.trim()
        if (key.isEmpty()) return
        userPublicProfileCache.value = userPublicProfileCache.value + (key to profile)
    }

    suspend fun refreshUserPublicProfile(viewerUserId: String?, targetUserId: String): UserPublicProfile? {
        val key = targetUserId.trim()
        if (key.isEmpty()) return null
        val fresh = fetchUserPublicProfile(viewerUserId, key)
        if (fresh != null) cacheUserPublicProfile(key, fresh)
        return fresh
    }

    private fun isConnectionArchivesUnavailableError(e: Throwable): Boolean {
        val msg = e.redactedRestMessage()
        return msg.contains("connection_archives", ignoreCase = true) &&
            (
                msg.contains("schema cache", ignoreCase = true) ||
                    msg.contains("Could not find the table", ignoreCase = true) ||
                    msg.contains("does not exist", ignoreCase = true)
                )
    }

    private fun isConnectionHiddenUnavailableError(e: Throwable): Boolean {
        val msg = e.redactedRestMessage()
        return msg.contains("connection_hidden", ignoreCase = true) &&
            (
                msg.contains("schema cache", ignoreCase = true) ||
                    msg.contains("Could not find the table", ignoreCase = true) ||
                    msg.contains("does not exist", ignoreCase = true)
                )
    }
    private val userColumnSets = listOf(
        listOf("id", "name", "full_name", "first_name", "last_name", "birthday", "email", "image", "last_polled"),
        listOf("id", "name", "first_name", "last_name", "birthday", "email", "image", "last_polled"),
        listOf("id", "name", "first_name", "last_name", "birthday", "email", "image"),
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
            val result = fetchUserCoresByIds(listOf(userId))
            result.firstOrNull()?.toUser()
        } catch (e: Exception) {
            println("Error fetching user by ID (redacted): ${e.redactedRestMessage()}")
            null
        }
    }

    /**
     * Loads [User], [user_interests] tags, and [user_availability] for a profile sheet.
     * When [viewerUserId] is non-null, attaches the most relevant mutual [Connection] row.
     *
     * BFF migration (C15): primary path is now `GET /api/users/{id}/profile` on click-web
     * which performs the `users` + `user_interests` + availability joins server-side. The
     * direct Supabase PostgREST queries below remain as a fallback for offline / network
     * failure scenarios (and for the availability-intent bubbles the BFF doesn't yet
     * expose in a SDK-friendly shape), but the canonical read path is the Next.js route.
     */
    suspend fun fetchUserPublicProfile(viewerUserId: String?, targetUserId: String): UserPublicProfile? {
        val trimmedTarget = targetUserId.trim()
        if (trimmedTarget.isEmpty()) return null

        // Primary BFF path — matches the app's other Next.js round-trips (archive,
        // tags, safety). Falls through to the direct Supabase path on any failure so
        // an offline client still renders whatever RLS happens to allow.
        val bffProfile = runCatching { apiClient.getUserProfile(trimmedTarget).getOrNull() }.getOrNull()
        if (bffProfile != null) {
            val user = bffProfile.user.toUser()
            val tags = bffProfile.tags
            val viewerTagsFromBff = bffProfile.viewerInterestTags
            // Availability + mutual connection still come through the Supabase client
            // because the BFF returns them as opaque JSON — keeping the existing typed
            // KMP models avoids a parallel deserialization path for Phase 3.
            val availability = fetchUserAvailability(trimmedTarget)
            val fromUsersMirror = fetchAvailabilityIntentBubblesFromUsersColumn(trimmedTarget)
            val fromIntentsTable =
                if (!viewerUserId.isNullOrBlank() && viewerUserId != trimmedTarget) {
                    val mutual = fetchSharedConnectionBetween(viewerUserId, trimmedTarget)
                    if (mutual != null) fetchAvailabilityIntentBubblesFromIntentsTable(trimmedTarget) else emptyList()
                } else {
                    emptyList()
                }
            val profileIntents = if (fromIntentsTable.isNotEmpty()) fromIntentsTable else fromUsersMirror
            val shared = viewerUserId?.takeIf { it.isNotBlank() && it != trimmedTarget }?.let { v ->
                fetchSharedConnectionBetween(v, trimmedTarget)
            }
            val profile = UserPublicProfile(
                user = user,
                interestTags = tags,
                availability = availability,
                profileAvailabilityIntents = profileIntents,
                viewerInterestTags = viewerTagsFromBff,
                sharedConnection = shared,
            )
            cacheUserPublicProfile(trimmedTarget, profile)
            return profile
        }

        // Fallback: legacy direct-Supabase path.
        val user = fetchUserById(trimmedTarget) ?: return null
        val tags = fetchUserInterests(trimmedTarget).getOrNull()?.tags.orEmpty()
        val availability = fetchUserAvailability(trimmedTarget)
        val fromUsersMirror = fetchAvailabilityIntentBubblesFromUsersColumn(trimmedTarget)
        val fromIntentsTable =
            if (!viewerUserId.isNullOrBlank() && viewerUserId != trimmedTarget) {
                val mutual = fetchSharedConnectionBetween(viewerUserId, trimmedTarget)
                if (mutual != null) fetchAvailabilityIntentBubblesFromIntentsTable(trimmedTarget) else emptyList()
            } else {
                emptyList()
            }
        val profileIntents = if (fromIntentsTable.isNotEmpty()) fromIntentsTable else fromUsersMirror
        val shared = viewerUserId?.takeIf { it.isNotBlank() && it != trimmedTarget }?.let { v ->
            fetchSharedConnectionBetween(v, trimmedTarget)
        }
        val viewerTags = viewerUserId?.takeIf { it.isNotBlank() && it != trimmedTarget }?.let { v ->
            fetchUserInterests(v).getOrNull()?.tags.orEmpty()
        }.orEmpty()
        val profile = UserPublicProfile(
            user = user,
            interestTags = tags,
            availability = availability,
            profileAvailabilityIntents = profileIntents,
            viewerInterestTags = viewerTags,
            sharedConnection = shared,
        )
        cacheUserPublicProfile(trimmedTarget, profile)
        return profile
    }

    /**
     * Reads [public.users.availability_intents] JSON mirror when the column exists (migration optional).
     */
    suspend fun fetchAvailabilityIntentBubblesFromUsersColumn(userId: String): List<ProfileAvailabilityIntentBubble> {
        if (userId.isBlank()) return emptyList()
        return try {
            @Serializable
            data class Row(
                @SerialName("availability_intents")
                val availabilityIntents: List<ProfileAvailabilityIntentBubble>? = null,
            )
            val row = supabase.from("users")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("availability_intents")) {
                    filter { eq("id", userId) }
                }
                .decodeList<Row>()
                .firstOrNull()
            val now = Clock.System.now()
            row?.availabilityIntents.orEmpty().filter { bubble ->
                val exp = bubble.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@filter true
                exp > now
            }
        } catch (e: Exception) {
            println("fetchAvailabilityIntentBubblesFromUsersColumn (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Live rows from [public.availability_intents] (RLS: own row + mutual-connection read policy).
     */
    suspend fun fetchAvailabilityIntentBubblesFromIntentsTable(targetUserId: String): List<ProfileAvailabilityIntentBubble> {
        if (targetUserId.isBlank()) return emptyList()
        return try {
            val nowIso = Clock.System.now().toString()
            val rows = supabase.from("availability_intents")
                .select {
                    filter {
                        eq("user_id", targetUserId)
                        gte("expires_at", nowIso)
                    }
                    order("expires_at", Order.ASCENDING)
                }
                .decodeList<AvailabilityIntentRow>()
            rows.mapNotNull { row ->
                val tag = row.intentTag?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ProfileAvailabilityIntentBubble(
                    intentTag = tag,
                    timeframe = row.timeframe?.trim().orEmpty(),
                    expiresAt = row.expiresAt ?: row.endsAt,
                )
            }
        } catch (e: Exception) {
            println("fetchAvailabilityIntentBubblesFromIntentsTable (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Intent bubbles for [peerUserId] when [viewerUserId] may read them (self or mutual connection).
     */
    suspend fun fetchPeerProfileAvailabilityBubbles(
        viewerUserId: String,
        peerUserId: String,
    ): List<ProfileAvailabilityIntentBubble> {
        if (peerUserId.isBlank()) return emptyList()
        if (viewerUserId == peerUserId) {
            val t = fetchAvailabilityIntentBubblesFromIntentsTable(peerUserId)
            return if (t.isNotEmpty()) t else fetchAvailabilityIntentBubblesFromUsersColumn(peerUserId)
        }
        if (fetchSharedConnectionBetween(viewerUserId, peerUserId) == null) return emptyList()
        val t = fetchAvailabilityIntentBubblesFromIntentsTable(peerUserId)
        return if (t.isNotEmpty()) t else fetchAvailabilityIntentBubblesFromUsersColumn(peerUserId)
    }

    /**
     * Mutual connection between two users (same `user_ids` pair). If multiple rows exist,
     * picks the one with the latest activity (`last_message_at` or `created`).
     * Excludes connections the viewer has hidden via [connection_hidden].
     */
    suspend fun fetchSharedConnectionBetween(viewerUserId: String, peerUserId: String): Connection? {
        if (viewerUserId.isBlank() || peerUserId.isBlank()) return null
        return try {
            val hidden = getHiddenConnectionIds(viewerUserId)
            val rows = supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(viewerUserId, peerUserId))
                    }
                }
                .decodeList<Connection>()
                .withEncountersSortedNewestFirst()
                .filter { it.isVisibleInActiveUi() && it.id !in hidden }
            val best = rows.maxByOrNull { conn ->
                (conn.last_message_at ?: 0L).coerceAtLeast(conn.created)
            }
            best
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Error fetchSharedConnectionBetween (redacted): ${e.redactedRestMessage()}")
            null
        }
    }

    /**
     * Lazy-sweep then two-step fetch (active channel + archived channel), matching web API semantics.
     * Also excludes connections where the other participant has blocked this user via [user_blocks].
     */
    suspend fun fetchUserConnectionsSnapshot(userId: String): UserConnectionsSnapshot {
        if (userId.isBlank()) {
            return UserConnectionsSnapshot(emptyList(), emptySet(), emptySet())
        }
        sweepStaleConnectionsForUser(userId)
        val archivedIds = getArchivedConnectionIds(userId)
        val hiddenIds = getHiddenConnectionIds(userId)
        val blockedByUserIds = getBlockedByUserIds(userId)
        val excludedForActive = archivedIds + hiddenIds
        val activeRows = fetchActiveChannelConnections(userId, excludedForActive)
        val validArchiveIds = archivedIds - hiddenIds
        val archivedRows = fetchArchivedChannelConnections(userId, validArchiveIds)
        val lifecycleArchivedRows = fetchLifecycleArchivedConnections(userId, hiddenIds)
        val merged = (activeRows + archivedRows + lifecycleArchivedRows)
            .distinctBy { it.id }
            .filter { it.normalizedConnectionStatus() != "removed" }
            .filter { conn ->
                // Exclude connections where the other participant has blocked this user
                if (blockedByUserIds.isEmpty()) true
                else conn.user_ids.none { it != userId && it in blockedByUserIds }
            }
            .sortedByDescending { it.created }
        return UserConnectionsSnapshot(merged, archivedIds, hiddenIds)
    }

    private suspend fun sweepStaleConnectionsForUser(userId: String) {
        supabase.postgrest.rpc(
            "sweep_stale_connections_for_user",
            buildJsonObject { put("target_user_id", userId) },
        )
    }

    /** Active tab: user is a participant, lifecycle in pending/active/kept (or null status), exclude archived ∪ hidden. */
    private suspend fun fetchActiveChannelConnections(
        userId: String,
        excludedIds: Set<String>,
    ): List<Connection> {
        return try {
            supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(userId))
                        or {
                            filter("status", FilterOperator.IS, "null")
                            eq("status", "pending")
                            eq("status", "active")
                            eq("status", "kept")
                        }
                        if (excludedIds.isNotEmpty()) {
                            filterNot("id", FilterOperator.IN, "(${excludedIds.joinToString(",")})")
                        }
                    }
                    order("created", Order.DESCENDING)
                }
                .decodeList<Connection>()
                .withEncountersSortedNewestFirst()
        } catch (e: Exception) {
            println("fetchActiveChannelConnections (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /** Archived tab: rows in `connection_archives` minus `connection_hidden`, restricted to participant. */
    private suspend fun fetchArchivedChannelConnections(
        userId: String,
        validArchiveIds: Set<String>,
    ): List<Connection> {
        if (validArchiveIds.isEmpty()) return emptyList()
        return try {
            val ids = validArchiveIds.toList()
            supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(userId))
                        isIn("id", ids)
                    }
                    order("created", Order.DESCENDING)
                }
                .decodeList<Connection>()
                .withEncountersSortedNewestFirst()
        } catch (e: Exception) {
            println("fetchArchivedChannelConnections (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Rows where the server set [Connection.status] to `archived` (idle expiry, etc.).
     * Distinct from per-user [connection_archives] — those without a junction row were previously missing from snapshots.
     */
    private suspend fun fetchLifecycleArchivedConnections(
        userId: String,
        hiddenIds: Set<String>,
    ): List<Connection> {
        if (userId.isBlank()) return emptyList()
        return try {
            supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(userId))
                        eq("status", "archived")
                        if (hiddenIds.isNotEmpty()) {
                            filterNot("id", FilterOperator.IN, "(${hiddenIds.joinToString(",")})")
                        }
                    }
                    order("created", Order.DESCENDING)
                }
                .decodeList<Connection>()
                .withEncountersSortedNewestFirst()
        } catch (e: Exception) {
            println("fetchLifecycleArchivedConnections (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    /**
     * Fetch connections for a user (paginated slice of the merged active + archived two-step result).
     */
    suspend fun fetchUserConnections(
        userId: String,
        page: Int = 0,
        pageSize: Int = 20,
    ): List<Connection> {
        val all = fetchUserConnectionsSnapshot(userId).connections
        val start = page * pageSize
        return all.drop(start).take(pageSize)
    }

    /**
     * Fetch a connection by ID
     */
    suspend fun fetchConnectionById(connectionId: String): Connection? {
        return try {
            val connections = supabase.from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        eq("id", connectionId)
                    }
                }
                .decodeList<Connection>()
                .withEncountersSortedNewestFirst()
            connections.firstOrNull()
        } catch (e: Exception) {
            println("Error fetching connection (redacted): ${e.redactedRestMessage()}")
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
            println("Error fetching users (redacted): ${e.redactedRestMessage()}")
            emptyList()
        }
    }

    private suspend fun fetchDisplayNamesViaRpc(userIds: List<String>): List<User> {
        return try {
            supabase.postgrest.rpc("get_user_display_names", buildJsonObject {
                put("user_ids", kotlinx.serialization.json.JsonArray(userIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            }).decodeList<DisplayNameRpcRow>().map { it.toUser() }
        } catch (e: Exception) {
            println("Error resolving display names via RPC (redacted): ${e.redactedRestMessage()}")
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

        println("Error fetching users with all schema variants (redacted): ${lastError?.redactedRestMessage()}")
        return emptyList()
    }

    /**
     * Update user's last polled timestamp
     */
    suspend fun updateUserLastPolled(userId: String, timestamp: Long): Boolean {
        if (lastPolledWritesDisabled) return true
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
            val msg = e.redactedRestMessage()
            if (msg.contains("last_polled", ignoreCase = true)) {
                lastPolledWritesDisabled = true
            }
            println("Error updating user last_polled (redacted): $msg")
            true
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
            println("Error updating connection (redacted): ${e.redactedRestMessage()}")
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
            println("Error updating connection has_begun (redacted): ${e.redactedRestMessage()}")
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
                println("updateConnectionExpiryState (status column may be missing): ${withStatus.exceptionOrNull()?.redactedRestMessage()}")
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
            println("Error updating connection expiry_state (redacted): ${e.redactedRestMessage()}")
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
            println("Error updating user keep decision (redacted): ${e.redactedRestMessage()}")
            false
        }
    }
    
    /**
     * Hide a connection for [userId] via [connection_hidden] (user "Remove Connection").
     * Does not mutate [connections.status] or delete the connection row.
     */
    suspend fun hideConnectionForUser(userId: String, connectionId: String): Boolean {
        if (userId.isBlank() || connectionId.isBlank()) return false
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionUid == null || sessionUid != userId.trim()) {
            println("hideConnectionForUser: session user mismatch")
            return false
        }
        return try {
            val result = clickWebApi.postConnectionHide(connectionId.trim())
            if (result.isSuccess) return true
            println("hideConnectionForUser (redacted): ${result.exceptionOrNull()?.redactedRestMessage()}")
            false
        } catch (e: Exception) {
            println("hideConnectionForUser (redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /**
     * Hides the connection for the signed-in user only (`POST /api/connections/hide`).
     * [userIds] must include the current session user (used to validate before calling the API).
     */
    suspend fun hideConnectionForUsers(userIds: List<String>, connectionId: String): Boolean {
        if (connectionId.isBlank() || userIds.isEmpty()) return false
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val distinct = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty() || sessionUid !in distinct) return false
        return hideConnectionForUser(sessionUid, connectionId)
    }

    /**
     * Clears [connection_archives] and [connection_hidden] for [connectionId] for both users in [userIds].
     * Used when restoring a connection after QR/NFC reconnect.
     */
    suspend fun clearConnectionJunctionForPair(connectionId: String, userIds: List<String>): Boolean {
        if (connectionId.isBlank() || userIds.size < 2) return false
        val pair = userIds.take(2).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (pair.size < 2) return false
        return try {
            if (!connectionHiddenTableMissing) {
                try {
                    supabase.from("connection_hidden")
                        .delete {
                            filter {
                                eq("connection_id", connectionId)
                                isIn("user_id", pair)
                            }
                        }
                } catch (e: Exception) {
                    if (isConnectionHiddenUnavailableError(e)) {
                        connectionHiddenTableMissing = true
                    } else {
                        throw e
                    }
                }
            }
            if (!connectionArchivesTableMissing) {
                try {
                    supabase.from("connection_archives")
                        .delete {
                            filter {
                                eq("connection_id", connectionId)
                                isIn("user_id", pair)
                            }
                        }
                } catch (e: Exception) {
                    if (isConnectionArchivesUnavailableError(e)) {
                        connectionArchivesTableMissing = true
                    } else {
                        throw e
                    }
                }
            }
            true
        } catch (e: Exception) {
            println("clearConnectionJunctionForPair (redacted): ${e.redactedRestMessage()}")
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
            println("Error fetching user availability (redacted): ${e.redactedRestMessage()}")
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
            println("Error fetching availabilities (redacted): ${e.redactedRestMessage()}")
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
            println("Error updating availability (redacted): ${e.redactedRestMessage()}")
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
            println("Error setting free this week (redacted): ${e.redactedRestMessage()}")
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
            println("Error inserting availability_intent (redacted): $short")
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
            println("Error fetching availability_intents (redacted): ${e.redactedRestMessage()}")
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
            println("Error updating availability_intent (redacted): $short")
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
            println("Error deleting availability_intent (redacted): $short")
            AvailabilityIntentInsertResult(success = false, errorMessage = short)
        }
    }
    
    /**
     * Update user's name
     */
    suspend fun updateUserName(userId: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        val spaceIdx = trimmed.indexOf(' ')
        val first = if (spaceIdx < 0) trimmed else trimmed.take(spaceIdx).trim()
        val last = if (spaceIdx < 0) "" else trimmed.substring(spaceIdx + 1).trim()
        return updateUserProfileNames(userId, first, last)
    }

    /**
     * Updates [public.users] display fields from explicit first/last name (via click-web PATCH).
     */
    suspend fun updateUserProfileNames(userId: String, firstName: String, lastName: String): Result<Unit> {
        val f = firstName.trim()
        val l = lastName.trim()
        if (f.isEmpty()) {
            return Result.failure(IllegalArgumentException("First name is required"))
        }
        return clickWebApi.patchUserProfile(userId = userId, firstName = f, lastName = l).map { }
            .onFailure { e ->
                println("Error updating user profile names (redacted): ${e.redactedRestMessage()}")
            }
    }

    /**
     * OAuth / incomplete rows: persist first name, last name, and birthday via click-web PATCH
     * (writes [public.users] with RLS-safe server-side validation).
     */
    suspend fun updateUserProfileBasics(
        userId: String,
        firstName: String,
        lastName: String,
        birthdayIso: String,
    ): Result<Unit> {
        val f = firstName.trim()
        val l = lastName.trim()
        val b = birthdayIso
            .trim()
            .substringBefore('T')
            .substringBefore(' ')
        if (f.isEmpty()) {
            return Result.failure(IllegalArgumentException("First name is required"))
        }
        if (b.isEmpty()) {
            return Result.failure(IllegalArgumentException("Birthday is required"))
        }
        return clickWebApi.patchUserProfile(
            userId = userId,
            firstName = f,
            lastName = l,
            birthday = b,
        ).map { }
            .onFailure { e ->
                println("Error updating user profile basics (redacted): ${e.redactedRestMessage()}")
            }
    }
    
    /**
     * Upsert a user record in the users table.
     * This ensures the user exists and is properly synchronized with Supabase Auth.
     */
    suspend fun upsertUser(user: compose.project.click.click.data.models.User): Boolean {
        return try {
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
                            }
                        )
                }.getOrElse {
                    supabase.from("users")
                        .insert(user.toInsertDto().copy(name = resolvedName, email = user.email ?: ""))
                }
                true
            }
        } catch (e: Exception) {
            println("Error upserting user (redacted): ${e.redactedRestMessage()}")
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
            println("Error fetching user_interests (redacted): ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    /**
     * Insert or update interest tags for the user (canonical store for onboarding + Common Ground).
     * Persisted through click-web so the mobile client does not write `user_interests` directly.
     */
    suspend fun updateUserInterests(userId: String, tags: List<String>): Result<Unit> {
        return clickWebApi.patchUserProfile(userId = userId, tags = tags).map { }
            .onFailure { e ->
                println("Error updating user_interests (redacted): ${e.redactedRestMessage()}")
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
            println("Error batch-fetching user_interests (redacted): ${e.redactedRestMessage()}")
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
            println("Error fetching location preferences (redacted): ${e.redactedRestMessage()}")
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
            println("Error updating location preferences (redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /**
     * User IDs that have blocked [userId] (rows in `user_blocks` where `blocked_id = userId`).
     * Uses RPC `blockers_for_blocked_user` (SECURITY DEFINER): direct PostgREST SELECT on `user_blocks`
     * is denied to the blocked party by RLS (`blocker_select` only allows `auth.uid() = blocker_id`).
     */
    suspend fun getBlockedByUserIds(userId: String): Set<String> {
        if (userId.isBlank()) return emptySet()
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionUid == null || sessionUid != userId.trim()) {
            println("getBlockedByUserIds: session user mismatch")
            return emptySet()
        }
        return try {
            @Serializable
            data class BlockRow(
                @SerialName("blocker_id") val blockerId: String,
            )
            val rows = supabase.postgrest.rpc(
                "blockers_for_blocked_user",
                buildJsonObject { },
            ).decodeList<BlockRow>()
            rows.map { it.blockerId }.toSet()
        } catch (e: Exception) {
            println("getBlockedByUserIds (non-fatal, redacted): ${e.redactedRestMessage()}")
            emptySet()
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
            println("Error blocking user (redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /**
     * Report a connection for safety review.
     */
    suspend fun reportConnection(connectionId: String, reporterId: String, reason: String): Boolean {
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionUid == null || sessionUid != reporterId.trim()) {
            println("reportConnection: session user mismatch")
            return false
        }
        return try {
            clickWebApi.postSafetyReport(connectionId.trim(), reason).isSuccess
        } catch (e: Exception) {
            println("Error reporting connection (redacted): ${e.redactedRestMessage()}")
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
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionUid == null || sessionUid != userId.trim()) {
            println("archiveConnection: session user mismatch")
            return false
        }
        return try {
            val result = clickWebApi.postConnectionArchive(connectionId.trim())
            if (result.isSuccess) return true
            println("archiveConnection (non-fatal, redacted): ${result.exceptionOrNull()?.redactedRestMessage()}")
            false
        } catch (e: Exception) {
            println("archiveConnection (non-fatal, redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /**
     * Unarchive a connection, removing it from the user's archive list.
     */
    suspend fun unarchiveConnection(userId: String, connectionId: String): Boolean {
        val sessionUid = supabase.auth.currentUserOrNull()?.id?.trim()?.takeIf { it.isNotEmpty() }
        if (sessionUid == null || sessionUid != userId.trim()) {
            println("unarchiveConnection: session user mismatch")
            return false
        }
        return try {
            val result = clickWebApi.postConnectionUnarchive(connectionId.trim())
            if (result.isSuccess) return true
            println("unarchiveConnection (non-fatal, redacted): ${result.exceptionOrNull()?.redactedRestMessage()}")
            false
        } catch (e: Exception) {
            println("unarchiveConnection (non-fatal, redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /**
     * Fetch all archived connection IDs for a user.
     */
    suspend fun getArchivedConnectionIds(userId: String): Set<String> {
        if (connectionArchivesTableMissing) return emptySet()
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
            if (isConnectionArchivesUnavailableError(e)) {
                connectionArchivesTableMissing = true
            } else {
                println("getArchivedConnectionIds (non-fatal, redacted): ${e.redactedRestMessage()}")
            }
            emptySet()
        }
    }

    /**
     * Connection IDs the user has explicitly hidden ([connection_hidden]).
     */
    suspend fun getHiddenConnectionIds(userId: String): Set<String> {
        if (userId.isBlank() || connectionHiddenTableMissing) return emptySet()
        return try {
            @Serializable
            data class HiddenRow(
                @SerialName("connection_id") val connectionId: String,
            )
            val rows = supabase.from("connection_hidden")
                .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("connection_id")) {
                    filter { eq("user_id", userId) }
                }
                .decodeList<HiddenRow>()
            rows.map { it.connectionId }.toSet()
        } catch (e: Exception) {
            if (isConnectionHiddenUnavailableError(e)) {
                connectionHiddenTableMissing = true
            } else {
                println("getHiddenConnectionIds (non-fatal, redacted): ${e.redactedRestMessage()}")
            }
            emptySet()
        }
    }

    /**
     * Mirrors non-expired [availability_intents] rows onto [public.users] for profile discovery
     * and sets [last_intent_update_at]. No-ops if the profile columns are missing.
     */
    suspend fun syncUserAvailabilityProfileMirror(userId: String): Boolean {
        if (userId.isBlank()) return false
        return try {
            val rows = fetchActiveAvailabilityIntentsForUser(userId)
            val bubbles = buildJsonArray {
                rows.forEach { row ->
                    val exp = row.expiresAt ?: row.endsAt
                    if (!row.intentTag.isNullOrBlank() && !exp.isNullOrBlank()) {
                        add(
                            buildJsonObject {
                                put("intent_tag", row.intentTag!!)
                                put("timeframe", row.timeframe ?: "")
                                put("expires_at", exp)
                            },
                        )
                    }
                }
            }
            val nowIso = Clock.System.now().toString()
            supabase.from("users")
                .update({
                    set("availability_intents", bubbles)
                    set("last_intent_update_at", nowIso)
                }) {
                    filter { eq("id", userId) }
                }
            true
        } catch (e: Exception) {
            println("syncUserAvailabilityProfileMirror (redacted): ${e.redactedRestMessage()}")
            false
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
            println("Error editing message (redacted): ${e.redactedRestMessage()}")
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
            println("Error deleting message (redacted): ${e.redactedRestMessage()}")
            false
        }
    }

    /** Short PostgREST / Supabase error (never includes URL, headers, or tokens). */
    private fun restErrorSummary(e: Throwable): String {
        var t: Throwable? = e
        while (t != null) {
            if (t is RestException) {
                val err = t.error.trim()
                if (err.isNotEmpty()) return err.take(400)
            }
            t = t.cause
        }
        return e.redactedRestMessage()
    }
}

