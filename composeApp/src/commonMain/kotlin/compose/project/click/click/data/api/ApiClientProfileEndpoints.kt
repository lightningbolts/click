package compose.project.click.click.data.api // pragma: allowlist secret

import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.models.ErrorResponse // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.MapBeaconInsert // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileTimelinePayload // pragma: allowlist secret
import compose.project.click.click.data.models.StoredEventBookmark // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.UserCore // pragma: allowlist secret
import compose.project.click.click.data.models.parseMapBeaconRows // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * GET `/api/users/{userId}/profile` on click-web (JWT via Ktor Auth bearer).
 *
 * BFF migration (Phase 3 — C15): replaces the direct `users` + `user_interests`
 * Supabase PostgREST joins that used to live inside
 * [SupabaseRepository.fetchUserPublicProfile]. The Next.js route owns the join so
 * the mobile client never talks to Supabase directly for profile hydration.
 */
internal suspend fun ApiClient.getUserProfileImpl(userId: String): Result<UserProfileGetResponse> {
    val id = userId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("userId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.get(
                "${ApiClient.clickWebAuthOrigin}/api/users/$id/profile",
            )
        if (response.status.value in 200..299) {
            Result.success(response.body<UserProfileGetResponse>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.getProfileTimelineImpl(
    targetType: String,
    targetId: String,
): Result<ProfileTimelinePayload> {
    val type = targetType.trim()
    val id = targetId.trim()
    if (type.isEmpty() || id.isEmpty()) return Result.failure(IllegalArgumentException("Timeline target required"))
    return try {
        val bearer = currentAccessToken()
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/profile/timeline") {
                bearer?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                parameter("target_type", type)
                parameter("target_id", id)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ProfileTimelinePayload>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.postProfileTimelineJournalEntryImpl(
    targetType: String,
    targetId: String,
    body: String,
    visibility: String,
): Result<ProfileTimelinePayload> {
    val type = targetType.trim()
    val id = targetId.trim()
    val text = body.trim()
    val vis = visibility.trim().lowercase()
    if (type.isEmpty() || id.isEmpty()) return Result.failure(IllegalArgumentException("Timeline target required"))
    if (text.isEmpty()) return Result.failure(IllegalArgumentException("Journal entry required"))
    return try {
        val bearer = currentAccessToken()
        val response: HttpResponse =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/profile/timeline") {
                contentType(ContentType.Application.Json)
                bearer?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(
                    ProfileTimelinePostBody(
                        targetType = type,
                        targetId = id,
                        body = text,
                        visibility = vis,
                    ),
                )
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ProfileTimelinePayload>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.putProfileTimelineJournalEntryImpl(
    id: String,
    body: String,
    visibility: String,
): Result<ProfileTimelinePayload> {
    val entryId = id.trim()
    val text = body.trim()
    val vis = visibility.trim().lowercase()
    if (entryId.isEmpty()) return Result.failure(IllegalArgumentException("Journal entry required"))
    if (text.isEmpty()) return Result.failure(IllegalArgumentException("Journal entry body required"))
    return try {
        val bearer = currentAccessToken()
        val response: HttpResponse =
            clickWebClient.put("${ApiClient.clickWebAuthOrigin}/api/profile/timeline") {
                contentType(ContentType.Application.Json)
                bearer?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(ProfileTimelineMutateBody(id = entryId, body = text, visibility = vis))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ProfileTimelinePayload>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.deleteProfileTimelineJournalEntryImpl(id: String): Result<ProfileTimelinePayload> {
    val entryId = id.trim()
    if (entryId.isEmpty()) return Result.failure(IllegalArgumentException("Journal entry required"))
    return try {
        val bearer = currentAccessToken()
        val response: HttpResponse =
            clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/profile/timeline") {
                contentType(ContentType.Application.Json)
                bearer?.let { token ->
                    header("Authorization", "Bearer $token")
                }
                setBody(ProfileTimelineMutateBody(id = entryId))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ProfileTimelinePayload>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * GET `/api/me/recap?window=day|week` — Home activity rollup.
 */
internal suspend fun ApiClient.getActivityRecapImpl(window: String = "week"): Result<ActivityRecapDto> {
    val normalized = if (window == "day") "day" else "week"
    return try {
        val response: HttpResponse =
            clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/me/recap") {
                parameter("window", normalized)
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<ActivityRecapResponseDto>().recap)
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * GET `/api/connections/{connectionId}/tabs` on click-web — fetches Media + Files
 * listings for the profile sheet. Links remain client-side because message
 * [content] is E2EE on the wire; callers filter locally-decrypted state.
 */
internal suspend fun ApiClient.getConnectionTabsImpl(connectionId: String): Result<ConnectionTabsGetResponse> {
    val id = connectionId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("connectionId required"))
    return try {
        val response: HttpResponse =
            clickWebClient.get(
                "${ApiClient.clickWebAuthOrigin}/api/connections/$id/tabs",
            )
        if (response.status.value in 200..299) {
            Result.success(response.body<ConnectionTabsGetResponse>())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

internal suspend fun ApiClient.patchUserProfileImpl(
    userId: String,
    firstName: String? = null,
    lastName: String? = null,
    image: String? = null,
    tags: List<String>? = null,
    birthday: String? = null,
    personalityTags: List<String>? = null,
): Result<User> {
    if (
        firstName == null &&
        lastName == null &&
        image == null &&
        tags == null &&
        birthday == null &&
        personalityTags == null
    ) {
        return Result.failure(IllegalArgumentException("No profile fields to update"))
    }
    val body =
        buildJsonObject {
            firstName?.let { put("first_name", it) }
            lastName?.let { put("last_name", it) }
            image?.let { put("image", it) }
            tags?.let { list ->
                put("tags", JsonArray(list.map { JsonPrimitive(it) }))
            }
            birthday?.let { put("birthday", it) }
            personalityTags?.let { list ->
                put("personality_tags", JsonArray(list.map { JsonPrimitive(it) }))
            }
        }
    if (body.isEmpty()) {
        return Result.failure(IllegalArgumentException("No profile fields to update"))
    }
    return try {
        val response =
            clickWebClient.patch("${ApiClient.clickWebAuthOrigin}/api/users/$userId/profile") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        if (response.status.value in 200..299) {
            val dto = response.body<UserProfilePatchResponseDto>()
            Result.success(dto.user.toUser())
        } else {
            Result.failure(Exception(readClickWebErrorMessage(response)))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
