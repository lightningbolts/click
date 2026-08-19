@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentInsert // pragma: allowlist secret
import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.LocationPreferences // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelineCacheEntry // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserAvailability // pragma: allowlist secret
import compose.project.click.click.data.models.UserInterests // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.models.mergeRichestEncounterEvents // pragma: allowlist secret
import compose.project.click.click.data.models.resolveDisplayName // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val CONNECTION_ENCOUNTERS_PER_CONNECTION = 25L
internal const val CONNECTION_ENCOUNTERS_TABLE = "connection_encounters"
internal val connectionsSelectWithEncounters = Columns.raw("*, connection_encounters(*)")

internal fun Connection.withEncountersSortedNewestFirst(): Connection =
    copy(connectionEncounters = connectionEncounters.mergeRichestEncounterEvents().sortedByDescending { it.encounteredAt })

internal fun List<Connection>.withEncountersSortedNewestFirst(): List<Connection> = map { it.withEncountersSortedNewestFirst() }

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
    val coreConnectionIds: Set<String> = emptySet(),
    /** When false, an empty [coreConnectionIds] must not wipe cold-start / optimistic local pins. */
    val coreConnectionIdsAuthoritative: Boolean = true,
)

/**
 * Repository for Supabase operations
 * Handles direct database queries for users and connections
 */
class SupabaseRepository {
    companion object {
        internal var lastStaleSweepUserId: String? = null
        internal var lastStaleSweepAtMs: Long = 0L
        internal const val STALE_SWEEP_INTERVAL_MS = 24L * 60L * 60L * 1000L
        internal const val PROFILE_TIMELINE_CACHE_MAX_ENTRIES = 64
        internal val userPublicProfileCache =
            MutableStateFlow<Map<String, UserPublicProfile>>(emptyMap())
        internal val profileTimelineCache =
            MutableStateFlow<Map<String, ProfileTimelineCacheEntry>>(emptyMap())

        /** Reset sweep schedule (e.g. on logout). */
        fun resetStaleConnectionSweepSchedule() {
            lastStaleSweepUserId = null
            lastStaleSweepAtMs = 0L
        }
    }

    /** Lazy so unit tests can construct the repository without touching Android Settings / Supabase client. */
    internal val supabase by lazy { SupabaseConfig.client }

    /**
     * BFF client for Next.js profile / tabs endpoints. Constructed lazily so unit tests
     * can still new up the repository without spinning up Ktor / Supabase auth plumbing.
     */
    internal val apiClient by lazy { ApiClient() }

    /** Next.js click-web secure writes (JWT bearer). */
    internal val clickWebApi by lazy { ApiClient() }

    /**
     * When the remote `users.last_polled` column is missing, PostgREST rejects PATCHes; skip further writes
     * for this process (apply [database/add_users_last_polled_column.sql] on the project to restore).
     */
    internal var lastPolledWritesDisabled: Boolean = false

    /**
     * When PostgREST has no `connection_archives` table, skip further queries until process restart
     * (apply [database/add_connection_archives.sql] to enable user-level archives).
     */
    internal var connectionArchivesTableMissing: Boolean = false
    internal var connectionHiddenTableMissing: Boolean = false

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

    fun snapshotCachedUserPublicProfiles(): List<UserPublicProfile> = userPublicProfileCache.value.values.toList()

    fun seedCachedUserPublicProfiles(profiles: List<UserPublicProfile>) {
        val seeded =
            profiles
                .filter { it.user.id.isNotBlank() }
                .associateBy { it.user.id.trim() }
        if (seeded.isEmpty()) return
        userPublicProfileCache.value = userPublicProfileCache.value + seeded
    }

    fun clearCachedUserPublicProfiles() {
        userPublicProfileCache.value = emptyMap()
    }

    fun getCachedProfileTimeline(
        targetType: String,
        targetId: String,
    ): ProfileTimelinePayload? {
        val key = profileTimelineCacheKey(targetType, targetId) ?: return null
        return profileTimelineCache.value[key]?.payload
    }

    fun snapshotCachedProfileTimelines(): List<ProfileTimelineCacheEntry> = profileTimelineCache.value.values.toList()

    fun seedCachedProfileTimelines(entries: List<ProfileTimelineCacheEntry>) {
        val seeded =
            entries
                .filter { it.key.isNotBlank() && it.targetId.isNotBlank() }
                .associateBy { it.key }
        if (seeded.isEmpty()) return
        profileTimelineCache.value = profileTimelineCache.value + seeded
    }

    fun clearCachedProfileTimelines() {
        profileTimelineCache.value = emptyMap()
    }

    fun invalidateUserPublicProfiles(peerUserIds: Collection<String>) {
        val keys = peerUserIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (keys.isEmpty()) return
        userPublicProfileCache.value = userPublicProfileCache.value.filterKeys { it !in keys }
    }

    fun invalidateProfileTimelinesForPeers(peerUserIds: Collection<String>) {
        val peers = peerUserIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (peers.isEmpty()) return
        profileTimelineCache.value =
            profileTimelineCache.value.filterValues { entry ->
                !(entry.targetType.equals("user", ignoreCase = true) && entry.targetId in peers)
            }
    }

    suspend fun refreshProfileTimeline(
        targetType: String,
        targetId: String,
    ): ProfileTimelinePayload? = refreshProfileTimelineImpl(targetType = targetType, targetId = targetId)

    suspend fun createProfileTimelineJournalEntry(
        targetType: String,
        targetId: String,
        body: String,
        visibility: String,
    ): ProfileTimelinePayload? =
        createProfileTimelineJournalEntryImpl(targetType = targetType, targetId = targetId, body = body, visibility = visibility)

    suspend fun updateProfileTimelineJournalEntry(
        id: String,
        body: String,
        visibility: String,
    ): ProfileTimelinePayload? = updateProfileTimelineJournalEntryImpl(id = id, body = body, visibility = visibility)

    suspend fun deleteProfileTimelineJournalEntry(id: String): ProfileTimelinePayload? = deleteProfileTimelineJournalEntryImpl(id = id)

    suspend fun refreshUserPublicProfile(
        viewerUserId: String?,
        targetUserId: String,
    ): UserPublicProfile? = refreshUserPublicProfileImpl(viewerUserId = viewerUserId, targetUserId = targetUserId)

    internal fun isConnectionArchivesUnavailableError(e: Throwable): Boolean {
        val msg = e.redactedRestMessage()
        return msg.contains("connection_archives", ignoreCase = true) &&
            (
                msg.contains("schema cache", ignoreCase = true) ||
                    msg.contains("Could not find the table", ignoreCase = true) ||
                    msg.contains("does not exist", ignoreCase = true)
            )
    }

    internal fun isConnectionHiddenUnavailableError(e: Throwable): Boolean {
        val msg = e.redactedRestMessage()
        return msg.contains("connection_hidden", ignoreCase = true) &&
            (
                msg.contains("schema cache", ignoreCase = true) ||
                    msg.contains("Could not find the table", ignoreCase = true) ||
                    msg.contains("does not exist", ignoreCase = true)
            )
    }

    internal val userColumnSets =
        listOf(
            listOf("id", "name", "full_name", "first_name", "last_name", "birthday", "email", "image", "last_polled", "personality_tags"),
            listOf("id", "name", "full_name", "first_name", "last_name", "birthday", "email", "image", "last_polled"),
            listOf("id", "name", "first_name", "last_name", "birthday", "email", "image", "last_polled"),
            listOf("id", "name", "first_name", "last_name", "birthday", "email", "image"),
            listOf("id", "name", "full_name", "email", "image", "last_polled"),
            listOf("id", "name", "email", "image", "last_polled"),
            listOf("id", "name", "full_name", "email", "image"),
            listOf("id", "name", "email", "image"),
        )

    @Serializable
    internal data class DisplayNameRpcRow(
        val id: String,
        @SerialName("display_name")
        val displayName: String,
        val email: String? = null,
        val image: String? = null,
        @SerialName("last_polled")
        val lastPolled: Long? = null,
    ) {
        fun toUser(): User =
            User(
                id = id,
                name =
                    resolveDisplayName(
                        firstName = null,
                        lastName = null,
                        fullName = displayName,
                        name = null,
                        email = email,
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
    suspend fun fetchUserById(userId: String): User? =
        try {
            val result = fetchUserCoresByIds(listOf(userId))
            result.firstOrNull()?.toUser()
        } catch (e: Exception) {
            println("Error fetching user by ID (redacted): ${e.redactedRestMessage()}")
            null
        }

    suspend fun fetchUserPublicProfile(
        viewerUserId: String?,
        targetUserId: String,
    ): UserPublicProfile? = fetchUserPublicProfileImpl(viewerUserId = viewerUserId, targetUserId = targetUserId)

    suspend fun fetchAvailabilityIntentBubblesFromUsersColumn(userId: String): List<ProfileAvailabilityIntentBubble> =
        fetchAvailabilityIntentBubblesFromUsersColumnImpl(userId = userId)

    suspend fun fetchAvailabilityIntentBubblesFromIntentsTable(targetUserId: String): List<ProfileAvailabilityIntentBubble> =
        fetchAvailabilityIntentBubblesFromIntentsTableImpl(targetUserId = targetUserId)

    suspend fun fetchAvailabilityOverlapsBatch(peerUserIds: List<String>): Map<String, Boolean> =
        fetchAvailabilityOverlapsBatchImpl(peerUserIds = peerUserIds)

    suspend fun fetchPeerProfileAvailabilityBubbles(
        viewerUserId: String,
        peerUserId: String,
    ): List<ProfileAvailabilityIntentBubble> = fetchPeerProfileAvailabilityBubblesImpl(viewerUserId = viewerUserId, peerUserId = peerUserId)

    suspend fun fetchSharedConnectionBetween(
        viewerUserId: String,
        peerUserId: String,
        forceNetwork: Boolean = false,
    ): Connection? = fetchSharedConnectionBetweenImpl(viewerUserId = viewerUserId, peerUserId = peerUserId, forceNetwork = forceNetwork)

    suspend fun fetchUserConnectionsSnapshot(
        userId: String,
        runStaleSweep: Boolean = true,
    ): UserConnectionsSnapshot = fetchUserConnectionsSnapshotImpl(userId = userId, runStaleSweep = runStaleSweep)

    suspend fun fetchUserConnections(
        userId: String,
        page: Int = 0,
        pageSize: Int = 20,
    ): List<Connection> = fetchUserConnectionsImpl(userId = userId, page = page, pageSize = pageSize)

    suspend fun fetchConnectionById(connectionId: String): Connection? = fetchConnectionByIdImpl(connectionId = connectionId)

    suspend fun fetchUsersByIds(userIds: List<String>): List<User> = fetchUsersByIdsImpl(userIds = userIds)

    /**
     * Update user's last polled timestamp.
     * Callers must ensure a fresh JWT ([EnsureFreshAccessToken]) before invoking.
     */
    suspend fun updateUserLastPolled(
        userId: String,
        timestamp: Long,
    ): Boolean {
        if (lastPolledWritesDisabled) return true
        // Soft refresh if SDK session is near expiry — avoids "JWT expired" spam every 30s.
        runCatching {
            compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
                .get()
        }
        return try {
            supabase
                .from("users")
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
        shouldContinue: List<Boolean>,
    ): Boolean =
        try {
            supabase
                .from("connections")
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

    /**
     * Update connection has_begun status when chat starts (Vibe Check begins)
     */
    suspend fun updateConnectionHasBegun(
        connectionId: String,
        hasBegun: Boolean,
    ): Boolean =
        try {
            supabase
                .from("connections")
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

    /**
     * Update connection expiry_state lifecycle.
     * Valid states: 'pending', 'active', 'kept', 'expired'
     */
    suspend fun updateConnectionExpiryState(
        connectionId: String,
        state: String,
    ): Boolean {
        return try {
            if (state == "pending" || state == "active" || state == "kept") {
                val withStatus =
                    runCatching {
                        supabase
                            .from("connections")
                            .update({
                                set("expiry_state", state)
                                set("status", state)
                            }) {
                                filter { eq("id", connectionId) }
                            }
                    }
                if (withStatus.isSuccess) return true
                println(
                    "updateConnectionExpiryState (status column may be missing): ${withStatus.exceptionOrNull()?.redactedRestMessage()}",
                )
            }
            supabase
                .from("connections")
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
        userIds: List<String>,
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

            supabase
                .from("connections")
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

    suspend fun hideConnectionForUser(
        userId: String,
        connectionId: String,
    ): Boolean = hideConnectionForUserImpl(userId = userId, connectionId = connectionId)

    suspend fun hideConnectionForUsers(
        userIds: List<String>,
        connectionId: String,
    ): Boolean = hideConnectionForUsersImpl(userIds = userIds, connectionId = connectionId)

    suspend fun clearConnectionJunctionForPair(
        connectionId: String,
        userIds: List<String>,
    ): Boolean = clearConnectionJunctionForPairImpl(connectionId = connectionId, userIds = userIds)

    // ==================== Availability Methods ====================

    suspend fun fetchUserAvailability(userId: String): UserAvailability? = fetchUserAvailabilityImpl(userId = userId)

    suspend fun fetchAvailabilityForUsers(userIds: List<String>): Map<String, UserAvailability> =
        fetchAvailabilityForUsersImpl(userIds = userIds)

    suspend fun updateUserAvailability(availability: UserAvailability): Boolean = updateUserAvailabilityImpl(availability = availability)

    suspend fun setFreeThisWeek(
        userId: String,
        isFree: Boolean,
    ): Boolean = setFreeThisWeekImpl(userId = userId, isFree = isFree)

    suspend fun insertAvailabilityIntent(row: AvailabilityIntentInsert): AvailabilityIntentInsertResult =
        insertAvailabilityIntentImpl(row = row)

    suspend fun fetchActiveAvailabilityIntentsForUser(userId: String): List<AvailabilityIntentRow> =
        fetchActiveAvailabilityIntentsForUserImpl(userId = userId)

    suspend fun updateAvailabilityIntent(
        id: String,
        userId: String,
        intentTag: String,
        timeframe: String,
        startsAt: String,
        endsAt: String,
        expiresAt: String,
    ): AvailabilityIntentInsertResult =
        updateAvailabilityIntentImpl(
            id = id,
            userId = userId,
            intentTag = intentTag,
            timeframe = timeframe,
            startsAt = startsAt,
            endsAt = endsAt,
            expiresAt = expiresAt,
        )

    suspend fun deleteAvailabilityIntent(intentId: String): AvailabilityIntentInsertResult =
        deleteAvailabilityIntentImpl(intentId = intentId)

    /**
     * Update user's name
     */
    suspend fun updateUserName(
        userId: String,
        name: String,
    ): Result<Unit> {
        val trimmed = name.trim()
        val spaceIdx = trimmed.indexOf(' ')
        val first = if (spaceIdx < 0) trimmed else trimmed.take(spaceIdx).trim()
        val last = if (spaceIdx < 0) "" else trimmed.substring(spaceIdx + 1).trim()
        return updateUserProfileNames(userId, first, last)
    }

    /**
     * Updates [public.users] display fields from explicit first/last name (via click-web PATCH).
     */
    suspend fun updateUserProfileNames(
        userId: String,
        firstName: String,
        lastName: String,
    ): Result<Unit> {
        val f = firstName.trim()
        val l = lastName.trim()
        if (f.isEmpty()) {
            return Result.failure(IllegalArgumentException("First name is required"))
        }
        return clickWebApi
            .patchUserProfile(userId = userId, firstName = f, lastName = l)
            .map { }
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
        val b =
            birthdayIso
                .trim()
                .substringBefore('T')
                .substringBefore(' ')
        if (f.isEmpty()) {
            return Result.failure(IllegalArgumentException("First name is required"))
        }
        if (b.isEmpty()) {
            return Result.failure(IllegalArgumentException("Birthday is required"))
        }
        return clickWebApi
            .patchUserProfile(
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
    suspend fun upsertUser(user: User): Boolean =
        try {
            // Check if user exists
            val existing = fetchUserById(user.id)
            val resolvedName =
                user.name?.trim()?.takeIf { it.isNotEmpty() }
                    ?: user.email
                        ?.substringBefore('@')
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
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
                        supabase
                            .from("users")
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
                        supabase
                            .from("users")
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
                    supabase
                        .from("users")
                        .insert(
                            buildJsonObject {
                                put("id", user.id)
                                put("name", resolvedName)
                                put("full_name", resolvedName)
                                resolvedFirst?.let { put("first_name", it) }
                                resolvedLast?.let { put("last_name", it) }
                                resolvedBirthday?.let { put("birthday", it) }
                                put("email", user.email ?: "")
                                put(
                                    "created_at",
                                    if (user.createdAt >
                                        0L
                                    ) {
                                        user.createdAt
                                    } else {
                                        kotlinx.datetime.Clock.System
                                            .now()
                                            .toEpochMilliseconds()
                                    },
                                )
                                user.image?.let { put("image", it) }
                            },
                        )
                }.getOrElse {
                    supabase
                        .from("users")
                        .insert(user.toInsertDto().copy(name = resolvedName, email = user.email ?: ""))
                }
                true
            }
        } catch (e: Exception) {
            println("Error upserting user (redacted): ${e.redactedRestMessage()}")
            false
        }

    @Serializable
    internal data class UserInterestsDto(
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
    suspend fun fetchUserInterests(userId: String): Result<UserInterests?> =
        try {
            val rows =
                supabase
                    .from("user_interests")
                    .select {
                        filter { eq("user_id", userId) }
                        limit(1)
                    }.decodeList<UserInterestsDto>()
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

    /**
     * Insert or update interest tags for the user (canonical store for onboarding + Common Ground).
     * Persisted through click-web so the mobile client does not write `user_interests` directly.
     */
    suspend fun updateUserInterests(
        userId: String,
        tags: List<String>,
    ): Result<Unit> =
        clickWebApi
            .patchUserProfile(userId = userId, tags = tags)
            .map { }
            .onFailure { e ->
                println("Error updating user_interests (redacted): ${e.redactedRestMessage()}")
            }

    internal suspend fun fetchUserInterestsMap(userIds: List<String>): Map<String, List<String>> {
        if (userIds.isEmpty()) return emptyMap()
        return try {
            supabase
                .from("user_interests")
                .select {
                    filter {
                        isIn("user_id", userIds)
                    }
                }.decodeList<UserInterestsDto>()
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
    suspend fun fetchLocationPreferences(userId: String): LocationPreferences =
        try {
            val result =
                supabase
                    .from("users")
                    .select(
                        columns =
                            io.github.jan.supabase.postgrest.query.Columns.list(
                                "location_connection_snap_enabled",
                                "location_show_on_map_enabled",
                                "location_include_in_insights_enabled",
                            ),
                    ) {
                        filter { eq("id", userId) }
                    }.decodeList<LocationPreferences>()
            result.firstOrNull() ?: LocationPreferences()
        } catch (e: Exception) {
            println("Error fetching location preferences (redacted): ${e.redactedRestMessage()}")
            LocationPreferences()
        }

    /**
     * Update location privacy preferences for a user.
     */
    suspend fun updateLocationPreferences(
        userId: String,
        prefs: LocationPreferences,
    ): Boolean =
        try {
            supabase
                .from("users")
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

    suspend fun getBlockedByUserIds(userId: String): Set<String> = getBlockedByUserIdsImpl(userId = userId)

    // ==================== Safety Methods ====================

    suspend fun blockUser(
        blockerId: String,
        blockedId: String,
    ): Boolean = blockUserImpl(blockerId = blockerId, blockedId = blockedId)

    suspend fun reportConnection(
        connectionId: String,
        reporterId: String,
        reason: String,
    ): Boolean = reportConnectionImpl(connectionId = connectionId, reporterId = reporterId, reason = reason)

    // ==================== Archive Methods ====================

    suspend fun archiveConnection(
        userId: String,
        connectionId: String,
    ): Boolean = archiveConnectionImpl(userId = userId, connectionId = connectionId)

    suspend fun unarchiveConnection(
        userId: String,
        connectionId: String,
    ): Boolean = unarchiveConnectionImpl(userId = userId, connectionId = connectionId)

    suspend fun getArchivedConnectionIds(userId: String): Set<String> = getArchivedConnectionIdsImpl(userId = userId)

    /**
     * Connection IDs the user has marked as core ([connection_core]).
     */
    internal data class CoreConnectionIdsFetch(
        val ids: Set<String>,
        val authoritative: Boolean,
    )

    suspend fun getCoreConnectionIds(userId: String): Set<String> = getCoreConnectionIdsImpl(userId = userId)

    suspend fun addConnectionToCore(
        userId: String,
        connectionId: String,
    ): Boolean = addConnectionToCoreImpl(userId = userId, connectionId = connectionId)

    suspend fun removeConnectionFromCore(
        userId: String,
        connectionId: String,
    ): Boolean = removeConnectionFromCoreImpl(userId = userId, connectionId = connectionId)

    suspend fun getHiddenConnectionIds(userId: String): Set<String> = getHiddenConnectionIdsImpl(userId = userId)

    suspend fun syncUserAvailabilityProfileMirror(userId: String): Boolean = syncUserAvailabilityProfileMirrorImpl(userId = userId)

    // ==================== Message Edit / Delete (direct Supabase) ====================

    suspend fun editMessage(
        messageId: String,
        newContent: String,
        chatId: String? = null,
    ): Boolean = editMessageImpl(messageId = messageId, newContent = newContent, chatId = chatId)

    @kotlinx.serialization.Serializable
    internal data class ChatConnectionIdOnly(
        @kotlinx.serialization.SerialName("connection_id")
        val connectionId: String,
    )

    @kotlinx.serialization.Serializable
    internal data class ConnectionUserIdsOnlyRow(
        val id: String,
        @kotlinx.serialization.SerialName("user_ids")
        val userIds: List<String>,
    )

    suspend fun deleteMessage(messageId: String): Boolean = deleteMessageImpl(messageId = messageId)

    /** Short PostgREST / Supabase error (never includes URL, headers, or tokens). */
    internal fun restErrorSummary(e: Throwable): String {
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
