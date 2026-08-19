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
import compose.project.click.click.data.models.UserCore // pragma: allowlist secret
import compose.project.click.click.data.models.UserInterests // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.models.mergeRichestEncounterEvents // pragma: allowlist secret
import compose.project.click.click.data.models.resolveDisplayName // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneConnectionsByPeer // pragma: allowlist secret
import compose.project.click.click.util.isOfflineNetworkFailure // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Batch overlap flags for peers via get_availability_overlaps RPC (one round-trip).
 */
internal suspend fun SupabaseRepository.fetchAvailabilityOverlapsBatchImpl(peerUserIds: List<String>): Map<String, Boolean> {
    if (peerUserIds.isEmpty()) return emptyMap()
    return try {
        @Serializable
        data class OverlapRow(
            @SerialName("peer_id") val peerId: String,
            @SerialName("has_overlap") val hasOverlap: Boolean = false,
        )
        val body =
            buildJsonObject {
                putJsonArray("p_peer_ids") {
                    peerUserIds.distinct().filter { it.isNotBlank() }.forEach { add(JsonPrimitive(it)) }
                }
            }
        supabase.postgrest
            .rpc("get_availability_overlaps", body)
            .decodeList<OverlapRow>()
            .associate { it.peerId to it.hasOverlap }
    } catch (e: Exception) {
        println("fetchAvailabilityOverlapsBatch RPC failed (redacted): ${e.redactedRestMessage()}")
        emptyMap()
    }
}

/**
 * Fetch a user's availability
 */
internal suspend fun SupabaseRepository.fetchUserAvailabilityImpl(userId: String): UserAvailability? =
    // pragma: allowlist secret
    try {
        val availabilities =
            supabase
                .from("user_availability")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<UserAvailability>() // pragma: allowlist secret
        availabilities.firstOrNull()
    } catch (e: Exception) {
        println("Error fetching user availability (redacted): ${e.redactedRestMessage()}")
        null
    }

/**
 * Fetch availability for multiple users
 */
internal suspend fun SupabaseRepository.fetchAvailabilityForUsersImpl(userIds: List<String>): Map<String, UserAvailability> { // pragma: allowlist secret
    if (userIds.isEmpty()) return emptyMap()

    return try {
        val availabilities =
            supabase
                .from("user_availability")
                .select {
                    filter {
                        isIn("user_id", userIds)
                    }
                }.decodeList<UserAvailability>() // pragma: allowlist secret
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
internal suspend fun SupabaseRepository.updateUserAvailabilityImpl(availability: UserAvailability): Boolean =
    // pragma: allowlist secret
    try {
        // Check if record exists first
        val existing = fetchUserAvailability(availability.userId)

        if (existing != null) {
            // Update existing record
            supabase
                .from("user_availability")
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
            supabase
                .from("user_availability")
                .insert(availability.toInsertDto())
        }
        println("Successfully updated availability for user ${availability.userId}: isFreeThisWeek=${availability.isFreeThisWeek}")
        true
    } catch (e: Exception) {
        println("Error updating availability (redacted): ${e.redactedRestMessage()}")
        false
    }

/**
 * Set user's "I'm free this week" status
 */
internal suspend fun SupabaseRepository.setFreeThisWeekImpl(
    userId: String,
    isFree: Boolean,
): Boolean =
    try {
        val existing = fetchUserAvailability(userId)
        val availability =
            existing?.copy(
                isFreeThisWeek = isFree,
                lastUpdated =
                    kotlinx.datetime.Clock.System
                        .now()
                        .toEpochMilliseconds(),
            ) ?: UserAvailability( // pragma: allowlist secret
                userId = userId,
                isFreeThisWeek = isFree,
                lastUpdated =
                    kotlinx.datetime.Clock.System
                        .now()
                        .toEpochMilliseconds(),
            )
        val result = updateUserAvailability(availability)
        println("setFreeThisWeek for $userId: isFree=$isFree, result=$result")
        result
    } catch (e: Exception) {
        println("Error setting free this week (redacted): ${e.redactedRestMessage()}")
        false
    }

/**
 * Inserts one row into [public.availability_intents] for the current user.
 */
internal suspend fun SupabaseRepository.insertAvailabilityIntentImpl(row: AvailabilityIntentInsert): AvailabilityIntentInsertResult =
    try {
        supabase.from("availability_intents").insert(row)
        AvailabilityIntentInsertResult(success = true)
    } catch (e: Exception) {
        val short = restErrorSummary(e)
        println("Error inserting availability_intent (redacted): $short")
        AvailabilityIntentInsertResult(success = false, errorMessage = short)
    }

/**
 * Active intent rows for [userId] where expiry is in the future (local server/client clock).
 */
internal suspend fun SupabaseRepository.fetchActiveAvailabilityIntentsForUserImpl(userId: String): List<AvailabilityIntentRow> {
    if (userId.isBlank()) return emptyList()
    return try {
        val rows =
            supabase
                .from("availability_intents")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }.decodeList<AvailabilityIntentRow>()
        val now = Clock.System.now()
        rows
            .filter { row ->
                val end = row.expiresInstantOrNull() ?: return@filter false
                end > now
            }.sortedByDescending { it.createdOrStartInstant() }
    } catch (e: Exception) {
        println("Error fetching availability_intents (redacted): ${e.redactedRestMessage()}")
        emptyList()
    }
}

/**
 * Updates one [public.availability_intents] row (must belong to [userId]; enforced by RLS).
 */
internal suspend fun SupabaseRepository.updateAvailabilityIntentImpl(
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
        supabase
            .from("availability_intents")
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
internal suspend fun SupabaseRepository.deleteAvailabilityIntentImpl(intentId: String): AvailabilityIntentInsertResult {
    if (intentId.isBlank()) {
        return AvailabilityIntentInsertResult(success = false, errorMessage = "Missing intent id.")
    }
    return try {
        supabase
            .from("availability_intents")
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
 * Mirrors non-expired [availability_intents] rows onto [public.users] for profile discovery
 * and sets [last_intent_update_at]. No-ops if the profile columns are missing.
 */
internal suspend fun SupabaseRepository.syncUserAvailabilityProfileMirrorImpl(userId: String): Boolean {
    if (userId.isBlank()) return false
    return try {
        val rows = fetchActiveAvailabilityIntentsForUser(userId)
        val bubbles =
            buildJsonArray {
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
        supabase
            .from("users")
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
