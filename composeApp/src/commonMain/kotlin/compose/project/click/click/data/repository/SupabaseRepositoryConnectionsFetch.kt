@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserCore // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneConnectionsByPeer // pragma: allowlist secret
import compose.project.click.click.util.isOfflineNetworkFailure // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mutual connection between two users (same `user_ids` pair). If multiple rows exist,
 * picks the one with the latest activity (`last_message_at` or `created`).
 * Excludes connections the viewer has hidden via [connection_hidden].
 */
internal suspend fun SupabaseRepository.fetchSharedConnectionBetweenImpl(
    viewerUserId: String,
    peerUserId: String,
    forceNetwork: Boolean = false,
): Connection? {
    if (viewerUserId.isBlank() || peerUserId.isBlank()) return null
    if (!forceNetwork) {
        findSharedConnectionInMemory(viewerUserId, peerUserId)?.let { return it }
    }
    return try {
        val hidden = getHiddenConnectionIds(viewerUserId)
        val rows =
            supabase
                .from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        contains("user_ids", listOf(viewerUserId, peerUserId))
                    }
                }.decodeList<Connection>()
                .withEncountersSortedNewestFirst()
                .filter { it.isVisibleInActiveUi() && it.id !in hidden }
        val best =
            rows.maxByOrNull { conn ->
                (conn.last_message_at ?: 0L).coerceAtLeast(conn.created)
            }
        best
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("Error fetchSharedConnectionBetween (redacted): ${e.redactedRestMessage()}")
        // Fall back to memory if forced network fails.
        if (forceNetwork) findSharedConnectionInMemory(viewerUserId, peerUserId) else null
    }
}

internal fun SupabaseRepository.findSharedConnectionInMemory(
    viewerUserId: String,
    peerUserId: String,
): Connection? {
    val currentUserId =
        compose.project.click.click.data.AppDataManager.currentUser.value // pragma: allowlist secret
            ?.id
    if (currentUserId != viewerUserId || !compose.project.click.click.data.AppDataManager.isDataLoaded.value) { // pragma: allowlist secret
        return null
    }
    val hidden = compose.project.click.click.data.AppDataManager.hiddenConnectionIds.value // pragma: allowlist secret
    val rows =
        compose.project.click.click.data.AppDataManager.connections.value // pragma: allowlist secret
            .filter { conn ->
                viewerUserId in conn.user_ids &&
                    peerUserId in conn.user_ids &&
                    conn.isVisibleInActiveUi() &&
                    conn.id !in hidden
            }
    return rows.maxByOrNull { conn ->
        (conn.last_message_at ?: 0L).coerceAtLeast(conn.created)
    }
}

/**
 * Lazy-sweep then two-step fetch (active channel + archived channel), matching web API semantics.
 * Also excludes connections where the other participant has blocked this user via [user_blocks].
 */
internal suspend fun SupabaseRepository.fetchUserConnectionsSnapshotImpl(
    userId: String,
    runStaleSweep: Boolean = true,
): UserConnectionsSnapshot {
    if (userId.isBlank()) {
        return UserConnectionsSnapshot(emptyList(), emptySet(), emptySet(), emptySet())
    }
    if (runStaleSweep) {
        sweepStaleConnectionsForUserIfDue(userId)
    }
    val archivedIds = getArchivedConnectionIds(userId)
    val hiddenIds = getHiddenConnectionIds(userId)
    val coreFetch = fetchCoreConnectionIds(userId)
    val blockedByUserIds = getBlockedByUserIds(userId)
    val excludedForActive = archivedIds + hiddenIds
    val activeRows = fetchActiveChannelConnections(userId, excludedForActive)
    val validArchiveIds = archivedIds - hiddenIds
    val archivedRows = fetchArchivedChannelConnections(userId, validArchiveIds)
    val lifecycleArchivedRows = fetchLifecycleArchivedConnections(userId, hiddenIds)
    val merged =
        dedupeOneToOneConnectionsByPeer(
            viewerUserId = userId,
            connections =
                (activeRows + archivedRows + lifecycleArchivedRows)
                    .distinctBy { it.id }
                    .filter { it.normalizedConnectionStatus() != "removed" }
                    .filter { conn ->
                        // Exclude connections where the other participant has blocked this user
                        if (blockedByUserIds.isEmpty()) {
                            true
                        } else {
                            conn.user_ids.none { it != userId && it in blockedByUserIds }
                        }
                    }.sortedByDescending { it.created },
        )
    return UserConnectionsSnapshot(
        connections = merged,
        archivedConnectionIds = archivedIds,
        hiddenConnectionIds = hiddenIds,
        coreConnectionIds = coreFetch.ids,
        coreConnectionIdsAuthoritative = coreFetch.authoritative,
    )
}

internal suspend fun SupabaseRepository.sweepStaleConnectionsForUserIfDue(userId: String) {
    val now =
        kotlinx.datetime.Clock.System
            .now()
            .toEpochMilliseconds()
    if (SupabaseRepository.lastStaleSweepUserId == userId &&
        now - SupabaseRepository.lastStaleSweepAtMs < SupabaseRepository.STALE_SWEEP_INTERVAL_MS
    ) {
        return
    }
    sweepStaleConnectionsForUser(userId)
    SupabaseRepository.lastStaleSweepUserId = userId
    SupabaseRepository.lastStaleSweepAtMs = now
}

internal suspend fun SupabaseRepository.sweepStaleConnectionsForUser(userId: String) {
    supabase.postgrest.rpc(
        "sweep_stale_connections_for_user",
        buildJsonObject { put("target_user_id", userId) },
    )
}

/** Active tab: user is a participant, lifecycle in pending/active/kept (or null status), exclude archived ∪ hidden. */
internal suspend fun SupabaseRepository.fetchActiveChannelConnections(
    userId: String,
    excludedIds: Set<String>,
): List<Connection> =
    try {
        supabase
            .from("connections")
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
                order("encountered_at", Order.DESCENDING, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
                limit(CONNECTION_ENCOUNTERS_PER_CONNECTION, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
            }.decodeList<Connection>()
            .withEncountersSortedNewestFirst()
    } catch (e: Exception) {
        if (e.isOfflineNetworkFailure()) throw e
        println("fetchActiveChannelConnections (redacted): ${e.redactedRestMessage()}")
        emptyList()
    }

/** Archived tab: rows in `connection_archives` minus `connection_hidden`, restricted to participant. */
internal suspend fun SupabaseRepository.fetchArchivedChannelConnections(
    userId: String,
    validArchiveIds: Set<String>,
): List<Connection> {
    if (validArchiveIds.isEmpty()) return emptyList()
    return try {
        val ids = validArchiveIds.toList()
        supabase
            .from("connections")
            .select(columns = connectionsSelectWithEncounters) {
                filter {
                    contains("user_ids", listOf(userId))
                    isIn("id", ids)
                }
                order("created", Order.DESCENDING)
                order("encountered_at", Order.DESCENDING, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
                limit(CONNECTION_ENCOUNTERS_PER_CONNECTION, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
            }.decodeList<Connection>()
            .withEncountersSortedNewestFirst()
    } catch (e: Exception) {
        if (e.isOfflineNetworkFailure()) throw e
        println("fetchArchivedChannelConnections (redacted): ${e.redactedRestMessage()}")
        emptyList()
    }
}

/**
 * Rows where the server set [Connection.status] to `archived` (idle expiry, etc.).
 * Distinct from per-user [connection_archives] — those without a junction row were previously missing from snapshots.
 */
internal suspend fun SupabaseRepository.fetchLifecycleArchivedConnections(
    userId: String,
    hiddenIds: Set<String>,
): List<Connection> {
    if (userId.isBlank()) return emptyList()
    return try {
        supabase
            .from("connections")
            .select(columns = connectionsSelectWithEncounters) {
                filter {
                    contains("user_ids", listOf(userId))
                    eq("status", "archived")
                    if (hiddenIds.isNotEmpty()) {
                        filterNot("id", FilterOperator.IN, "(${hiddenIds.joinToString(",")})")
                    }
                }
                order("created", Order.DESCENDING)
                order("encountered_at", Order.DESCENDING, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
                limit(CONNECTION_ENCOUNTERS_PER_CONNECTION, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
            }.decodeList<Connection>()
            .withEncountersSortedNewestFirst()
    } catch (e: Exception) {
        if (e.isOfflineNetworkFailure()) throw e
        println("fetchLifecycleArchivedConnections (redacted): ${e.redactedRestMessage()}")
        emptyList()
    }
}

/**
 * Fetch connections for a user (paginated slice of the merged active + archived two-step result).
 */
internal suspend fun SupabaseRepository.fetchUserConnectionsImpl(
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
internal suspend fun SupabaseRepository.fetchConnectionByIdImpl(connectionId: String): Connection? =
    try {
        val connections =
            supabase
                .from("connections")
                .select(columns = connectionsSelectWithEncounters) {
                    filter {
                        eq("id", connectionId)
                    }
                    order("encountered_at", Order.DESCENDING, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
                    limit(CONNECTION_ENCOUNTERS_PER_CONNECTION, referencedTable = CONNECTION_ENCOUNTERS_TABLE)
                }.decodeList<Connection>()
                .withEncountersSortedNewestFirst()
        connections.firstOrNull()
    } catch (e: Exception) {
        println("Error fetching connection (redacted): ${e.redactedRestMessage()}")
        null
    }

/**
 * Fetch multiple users by their IDs.
 * Runs a table query and the display-name RPC in parallel so that
 * even if the public.users table lacks name/full_name the RPC can
 * still resolve names from auth metadata.  This eliminates the
 * sequential dependency that previously left names as "Connection"
 * when the RPC happened to fail after an already-null table result.
 */
internal suspend fun SupabaseRepository.fetchUsersByIdsImpl(userIds: List<String>): List<User> {
    if (userIds.isEmpty()) return emptyList()

    return try {
        val (tableUsers, rpcUsers) =
            coroutineScope {
                val tableDeferred =
                    async {
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
            val merged =
                when {
                    rpcUser != null && isResolvedDisplayName(rpcUser.name) -> {
                        // RPC gave a real name — merge with any extra table data
                        rpcUser.copy(
                            image = rpcUser.image ?: tableUser?.image,
                            lastPolled = rpcUser.lastPolled ?: tableUser?.lastPolled,
                            email = rpcUser.email ?: tableUser?.email,
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

internal suspend fun SupabaseRepository.fetchDisplayNamesViaRpc(userIds: List<String>): List<User> =
    try {
        supabase.postgrest
            .rpc(
                "get_user_display_names",
                buildJsonObject {
                    put("user_ids", kotlinx.serialization.json.JsonArray(userIds.map { kotlinx.serialization.json.JsonPrimitive(it) }))
                },
            ).decodeList<SupabaseRepository.DisplayNameRpcRow>()
            .map { it.toUser() }
    } catch (e: Exception) {
        println("Error resolving display names via RPC (redacted): ${e.redactedRestMessage()}")
        emptyList()
    }

internal suspend fun SupabaseRepository.fetchUserCoresByIds(userIds: List<String>): List<UserCore> {
    var lastError: Throwable? = null

    for (columns in userColumnSets) {
        val attempt =
            runCatching {
                supabase
                    .from("users")
                    .select(
                        columns =
                            io.github.jan.supabase.postgrest.query.Columns
                                .list(*columns.toTypedArray()),
                    ) {
                        filter { isIn("id", userIds) }
                    }.decodeList<UserCore>()
            }

        if (attempt.isSuccess) {
            return attempt.getOrThrow()
        }

        lastError = attempt.exceptionOrNull()
    }

    println("Error fetching users with all schema variants (redacted): ${lastError?.redactedRestMessage()}")
    return emptyList()
}
