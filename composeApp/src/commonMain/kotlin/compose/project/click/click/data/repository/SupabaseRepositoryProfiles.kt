@file:Suppress("ktlint:standard:max-line-length")

package compose.project.click.click.data.repository // pragma: allowlist secret

import compose.project.click.click.data.models.AvailabilityIntentRow // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelineCacheEntry // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserPublicProfile // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal fun SupabaseRepository.cacheProfileTimeline(payload: ProfileTimelinePayload) {
    val key = profileTimelineCacheKey(payload.targetType, payload.targetId) ?: return
    val entry =
        ProfileTimelineCacheEntry(
            key = key,
            targetType = payload.targetType,
            targetId = payload.targetId,
            cachedAtMs = Clock.System.now().toEpochMilliseconds(),
            payload = payload,
        )
    val merged = SupabaseRepository.profileTimelineCache.value + (key to entry)
    SupabaseRepository.profileTimelineCache.value =
        if (merged.size <= SupabaseRepository.PROFILE_TIMELINE_CACHE_MAX_ENTRIES) {
            merged
        } else {
            merged.entries
                .sortedByDescending { it.value.cachedAtMs }
                .take(SupabaseRepository.PROFILE_TIMELINE_CACHE_MAX_ENTRIES)
                .associate { it.key to it.value }
        }
}

internal suspend fun SupabaseRepository.refreshProfileTimelineImpl(
    targetType: String,
    targetId: String,
): ProfileTimelinePayload? {
    val fresh = apiClient.getProfileTimeline(targetType, targetId).getOrNull()
    if (fresh != null) cacheProfileTimeline(fresh)
    return fresh
}

internal suspend fun SupabaseRepository.createProfileTimelineJournalEntryImpl(
    targetType: String,
    targetId: String,
    body: String,
    visibility: String,
): ProfileTimelinePayload? {
    val fresh =
        apiClient
            .postProfileTimelineJournalEntry(
                targetType = targetType,
                targetId = targetId,
                body = body,
                visibility = visibility,
            ).getOrNull()
    if (fresh != null) cacheProfileTimeline(fresh)
    return fresh
}

internal suspend fun SupabaseRepository.updateProfileTimelineJournalEntryImpl(
    id: String,
    body: String,
    visibility: String,
): ProfileTimelinePayload? {
    val fresh =
        apiClient
            .putProfileTimelineJournalEntry(
                id = id,
                body = body,
                visibility = visibility,
            ).getOrNull()
    if (fresh != null) cacheProfileTimeline(fresh)
    return fresh
}

internal suspend fun SupabaseRepository.deleteProfileTimelineJournalEntryImpl(id: String): ProfileTimelinePayload? {
    val fresh = apiClient.deleteProfileTimelineJournalEntry(id).getOrNull()
    if (fresh != null) cacheProfileTimeline(fresh)
    return fresh
}

internal fun SupabaseRepository.cacheUserPublicProfile(
    targetUserId: String,
    profile: UserPublicProfile,
) {
    val key = targetUserId.trim()
    if (key.isEmpty()) return
    SupabaseRepository.userPublicProfileCache.value = SupabaseRepository.userPublicProfileCache.value + (key to profile)
}

internal fun SupabaseRepository.profileTimelineCacheKey(
    targetType: String,
    targetId: String,
): String? {
    val type = targetType.trim().lowercase()
    val id = targetId.trim()
    if (type.isBlank() || id.isBlank()) return null
    return "$type:$id"
}

internal suspend fun SupabaseRepository.refreshUserPublicProfileImpl(
    viewerUserId: String?,
    targetUserId: String,
): UserPublicProfile? {
    val key = targetUserId.trim()
    if (key.isEmpty()) return null
    val fresh = fetchUserPublicProfile(viewerUserId, key) ?: return null
    // Always re-fetch shared connection from network so BLE encounter rows aren't
    // short-circuited by a stale in-memory Connection without connectionEncounters.
    val viewer = viewerUserId?.trim()?.takeIf { it.isNotEmpty() && it != key }
    val updated =
        if (viewer != null) {
            val shared = fetchSharedConnectionBetween(viewer, key, forceNetwork = true)
            fresh.copy(sharedConnection = shared)
        } else {
            fresh
        }
    cacheUserPublicProfile(key, updated)
    return updated
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
internal suspend fun SupabaseRepository.fetchUserPublicProfileImpl(
    viewerUserId: String?,
    targetUserId: String,
): UserPublicProfile? {
    val trimmedTarget = targetUserId.trim()
    if (trimmedTarget.isEmpty()) return null

    // Primary BFF path — matches the app's other Next.js round-trips (archive,
    // tags, safety). Falls through to the direct Supabase path on any failure so
    // an offline client still renders whatever RLS happens to allow.
    val bffProfile = runCatching { apiClient.getUserProfile(trimmedTarget).getOrNull() }.getOrNull()
    if (bffProfile != null) {
        val user = bffProfile.user.toUser()
        var tags = bffProfile.tags
        var viewerTagsFromBff = bffProfile.viewerInterestTags
        val availability = fetchUserAvailability(trimmedTarget)
        // Prefer BFF availabilityIntents (admin-backed). Fall back to Supabase only when
        // the BFF omitted them so offline / older BFF deploys still work.
        val now = Clock.System.now()
        val fromBff =
            bffProfile.availabilityIntents.filter { bubble ->
                val tag = bubble.intentTag?.trim().orEmpty()
                if (tag.isEmpty()) return@filter false
                val exp =
                    bubble.expiresAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
                        ?: return@filter true
                exp > now
            }
        val profileIntents =
            if (fromBff.isNotEmpty()) {
                fromBff
            } else {
                val fromUsersMirror = fetchAvailabilityIntentBubblesFromUsersColumn(trimmedTarget)
                val fromIntentsTable =
                    if (!viewerUserId.isNullOrBlank() && viewerUserId != trimmedTarget) {
                        val mutual = fetchSharedConnectionBetween(viewerUserId, trimmedTarget)
                        if (mutual != null) fetchAvailabilityIntentBubblesFromIntentsTable(trimmedTarget) else emptyList()
                    } else if (!viewerUserId.isNullOrBlank() && viewerUserId == trimmedTarget) {
                        fetchAvailabilityIntentBubblesFromIntentsTable(trimmedTarget)
                    } else {
                        emptyList()
                    }
                if (fromIntentsTable.isNotEmpty()) fromIntentsTable else fromUsersMirror
            }
        val shared =
            viewerUserId?.takeIf { it.isNotBlank() && it != trimmedTarget }?.let { v ->
                fetchSharedConnectionBetween(v, trimmedTarget)
            }
        // BFF can return empty tags when mutual-connection/admin path fails; backfill locally
        // when we know the users are connected (or self). Own tags always readable via RLS.
        if (tags.isEmpty() && (shared != null || viewerUserId == trimmedTarget)) {
            tags = fetchUserInterests(trimmedTarget).getOrNull()?.tags.orEmpty()
        }
        if (viewerTagsFromBff.isEmpty() &&
            !viewerUserId.isNullOrBlank() &&
            viewerUserId != trimmedTarget &&
            shared != null
        ) {
            viewerTagsFromBff = fetchUserInterests(viewerUserId).getOrNull()?.tags.orEmpty()
        }
        val personalityTags = bffProfile.personalityTags.ifEmpty { bffProfile.user.personalityTags }
        val profile =
            UserPublicProfile(
                user = user.copy(personalityTags = personalityTags.ifEmpty { user.personalityTags }),
                interestTags = tags,
                personalityTags = personalityTags.ifEmpty { user.personalityTags },
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
    val shared =
        viewerUserId?.takeIf { it.isNotBlank() && it != trimmedTarget }?.let { v ->
            fetchSharedConnectionBetween(v, trimmedTarget)
        }
    val viewerTags =
        viewerUserId
            ?.takeIf { it.isNotBlank() && it != trimmedTarget }
            ?.let { v ->
                fetchUserInterests(v).getOrNull()?.tags.orEmpty()
            }.orEmpty()
    val profile =
        UserPublicProfile(
            user = user,
            interestTags = tags,
            personalityTags = user.personalityTags,
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
internal suspend fun SupabaseRepository.fetchAvailabilityIntentBubblesFromUsersColumnImpl(
    userId: String,
): List<ProfileAvailabilityIntentBubble> {
    if (userId.isBlank()) return emptyList()
    return try {
        @Serializable
        data class Row(
            @SerialName("availability_intents")
            val availabilityIntents: List<ProfileAvailabilityIntentBubble>? = null,
        )
        val row =
            supabase
                .from("users")
                .select(
                    columns =
                        io.github.jan.supabase.postgrest.query.Columns
                            .list("availability_intents"),
                ) {
                    filter { eq("id", userId) }
                }.decodeList<Row>()
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
internal suspend fun SupabaseRepository.fetchAvailabilityIntentBubblesFromIntentsTableImpl(
    targetUserId: String,
): List<ProfileAvailabilityIntentBubble> {
    if (targetUserId.isBlank()) return emptyList()
    return try {
        val nowIso = Clock.System.now().toString()
        val rows =
            supabase
                .from("availability_intents")
                .select {
                    filter {
                        eq("user_id", targetUserId)
                        gte("expires_at", nowIso)
                    }
                    order("expires_at", Order.ASCENDING)
                }.decodeList<AvailabilityIntentRow>()
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
internal suspend fun SupabaseRepository.fetchPeerProfileAvailabilityBubblesImpl(
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
