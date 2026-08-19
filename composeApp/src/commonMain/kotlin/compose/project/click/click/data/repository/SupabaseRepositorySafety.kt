@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * User IDs that have blocked [userId] (rows in `user_blocks` where `blocked_id = userId`).
 * Uses RPC `blockers_for_blocked_user` (SECURITY DEFINER): direct PostgREST SELECT on `user_blocks`
 * is denied to the blocked party by RLS (`blocker_select` only allows `auth.uid() = blocker_id`).
 */
internal suspend fun SupabaseRepository.getBlockedByUserIdsImpl(userId: String): Set<String> {
    if (userId.isBlank()) return emptySet()
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    if (sessionUid == null || sessionUid != userId.trim()) {
        println("getBlockedByUserIds: session user mismatch")
        return emptySet()
    }
    return try {
        @Serializable
        data class BlockRow(
            @SerialName("blocker_id") val blockerId: String,
        )
        val rows =
            supabase.postgrest
                .rpc(
                    "blockers_for_blocked_user",
                    buildJsonObject { },
                ).decodeList<BlockRow>()
        rows.map { it.blockerId }.toSet()
    } catch (e: Exception) {
        println("getBlockedByUserIds (non-fatal, redacted): ${e.redactedRestMessage()}")
        emptySet()
    }
}

/**
 * Block a user. Inserts into user_blocks table.
 */
internal suspend fun SupabaseRepository.blockUserImpl(
    blockerId: String,
    blockedId: String,
): Boolean =
    try {
        supabase
            .from("user_blocks")
            .insert(
                buildJsonObject {
                    put("blocker_id", blockerId)
                    put("blocked_id", blockedId)
                },
            )
        true
    } catch (e: Exception) {
        println("Error blocking user (redacted): ${e.redactedRestMessage()}")
        false
    }

/**
 * Report a connection for safety review.
 */
internal suspend fun SupabaseRepository.reportConnectionImpl(
    connectionId: String,
    reporterId: String,
    reason: String,
): Boolean {
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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

/**
 * Archive a connection for the given user.
 * Inserts into the connection_archives table (see database/add_connection_archives.sql).
 * Silently no-ops if the table has not been provisioned yet.
 */
internal suspend fun SupabaseRepository.archiveConnectionImpl(
    userId: String,
    connectionId: String,
): Boolean {
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
internal suspend fun SupabaseRepository.unarchiveConnectionImpl(
    userId: String,
    connectionId: String,
): Boolean {
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
internal suspend fun SupabaseRepository.getArchivedConnectionIdsImpl(userId: String): Set<String> {
    if (connectionArchivesTableMissing) return emptySet()
    return try {
        @kotlinx.serialization.Serializable
        data class ArchiveRow(
            @kotlinx.serialization.SerialName("connection_id") val connectionId: String,
        )
        val rows =
            supabase
                .from("connection_archives")
                .select(
                    columns =
                        io.github.jan.supabase.postgrest.query.Columns
                            .list("connection_id"),
                ) {
                    filter { eq("user_id", userId) }
                }.decodeList<ArchiveRow>()
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

internal suspend fun SupabaseRepository.getCoreConnectionIdsImpl(userId: String): Set<String> = fetchCoreConnectionIds(userId).ids

internal suspend fun SupabaseRepository.fetchCoreConnectionIds(userId: String): SupabaseRepository.CoreConnectionIdsFetch {
    if (userId.isBlank()) return SupabaseRepository.CoreConnectionIdsFetch(emptySet(), authoritative = true)
    val api = clickWebApi.fetchConnectionCoreIds()
    if (api.isSuccess) {
        val apiIds = api.getOrThrow()
        if (apiIds.isNotEmpty()) {
            return SupabaseRepository.CoreConnectionIdsFetch(apiIds, authoritative = true)
        }
        val directIds = getCoreConnectionIdsFromSupabase(userId)
        return SupabaseRepository.CoreConnectionIdsFetch(
            ids = directIds,
            authoritative = directIds.isEmpty(),
        )
    }
    return SupabaseRepository.CoreConnectionIdsFetch(
        ids = getCoreConnectionIdsFromSupabase(userId),
        authoritative = false,
    )
}

internal suspend fun SupabaseRepository.getCoreConnectionIdsFromSupabase(userId: String): Set<String> =
    try {
        @Serializable
        data class CoreRow(
            @SerialName("connection_id") val connectionId: String,
        )
        val rows =
            supabase
                .from("connection_core")
                .select(
                    columns =
                        io.github.jan.supabase.postgrest.query.Columns
                            .list("connection_id"),
                ) {
                    filter { eq("user_id", userId) }
                }.decodeList<CoreRow>()
        rows.map { it.connectionId }.toSet()
    } catch (e: Exception) {
        if (!isConnectionCoreUnavailableError(e)) {
            println("getCoreConnectionIdsFromSupabase (non-fatal, redacted): ${e.redactedRestMessage()}")
        }
        emptySet()
    }

internal suspend fun SupabaseRepository.addConnectionToCoreImpl(
    userId: String,
    connectionId: String,
): Boolean {
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    if (sessionUid == null || sessionUid != userId.trim()) return false
    return try {
        val result = clickWebApi.postConnectionCore(connectionId.trim())
        result.isSuccess
    } catch (e: Exception) {
        println("addConnectionToCore (non-fatal, redacted): ${e.redactedRestMessage()}")
        false
    }
}

internal suspend fun SupabaseRepository.removeConnectionFromCoreImpl(
    userId: String,
    connectionId: String,
): Boolean {
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    if (sessionUid == null || sessionUid != userId.trim()) return false
    return try {
        val result = clickWebApi.deleteConnectionCore(connectionId.trim())
        result.isSuccess
    } catch (e: Exception) {
        println("removeConnectionFromCore (non-fatal, redacted): ${e.redactedRestMessage()}")
        false
    }
}

internal fun SupabaseRepository.isConnectionCoreUnavailableError(e: Throwable): Boolean {
    val msg = e.message.orEmpty()
    return msg.contains("connection_core", ignoreCase = true) &&
        (msg.contains("schema cache", ignoreCase = true) || msg.contains("does not exist", ignoreCase = true))
}

/**
 * Connection IDs the user has explicitly hidden ([connection_hidden]).
 */
internal suspend fun SupabaseRepository.getHiddenConnectionIdsImpl(userId: String): Set<String> {
    if (userId.isBlank() || connectionHiddenTableMissing) return emptySet()
    return try {
        @Serializable
        data class HiddenRow(
            @SerialName("connection_id") val connectionId: String,
        )
        val rows =
            supabase
                .from("connection_hidden")
                .select(
                    columns =
                        io.github.jan.supabase.postgrest.query.Columns
                            .list("connection_id"),
                ) {
                    filter { eq("user_id", userId) }
                }.decodeList<HiddenRow>()
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
 * Hide a connection for [userId] via [connection_hidden] (user "Remove Connection").
 * Does not mutate [connections.status] or delete the connection row.
 */
internal suspend fun SupabaseRepository.hideConnectionForUserImpl(
    userId: String,
    connectionId: String,
): Boolean {
    if (userId.isBlank() || connectionId.isBlank()) return false
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
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
internal suspend fun SupabaseRepository.hideConnectionForUsersImpl(
    userIds: List<String>,
    connectionId: String,
): Boolean {
    if (connectionId.isBlank() || userIds.isEmpty()) return false
    val sessionUid =
        supabase.auth
            .currentUserOrNull()
            ?.id
            ?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return false
    val distinct = userIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (distinct.isEmpty() || sessionUid !in distinct) return false
    return hideConnectionForUser(sessionUid, connectionId)
}

/**
 * Clears [connection_archives] and [connection_hidden] for [connectionId] for both users in [userIds].
 * Used when restoring a connection after QR/NFC reconnect.
 */
internal suspend fun SupabaseRepository.clearConnectionJunctionForPairImpl(
    connectionId: String,
    userIds: List<String>,
): Boolean {
    if (connectionId.isBlank() || userIds.size < 2) return false
    val pair =
        userIds
            .take(2)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    if (pair.size < 2) return false
    return try {
        if (!connectionHiddenTableMissing) {
            try {
                supabase
                    .from("connection_hidden")
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
                supabase
                    .from("connection_archives")
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
