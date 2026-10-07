package compose.project.click.click.data.api // pragma: allowlist secret

import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Account and Me endpoints on click-web: legacy Ghost Mode reset, blocked people, avatar
 * removal, batched display names, and the reachability ping.
 */

@Serializable
internal data class GhostModeBody(
    val enabled: Boolean,
)

@Serializable
data class BlockedUserDto(
    @SerialName("blocked_id") val blockedId: String,
    /** ISO timestamp; null for rows written before the column existed. */
    @SerialName("blocked_at") val blockedAt: String? = null,
)

@Serializable
internal data class BlockedUsersResponseDto(
    val blocks: List<BlockedUserDto> = emptyList(),
)

@Serializable
internal data class DisplayNamesBody(
    val userIds: List<String>,
)

@Serializable
data class DisplayNamesResponseDto(
    val names: Map<String, String> = emptyMap(),
    val images: Map<String, String?> = emptyMap(),
)

/** At most this many ids per `POST /api/users/display-names` call (the route truncates at 100). */
const val DISPLAY_NAMES_BATCH_MAX = 100

private suspend fun <T> ApiClient.clickWebCall(block: suspend () -> Result<T>): Result<T> =
    try {
        block()
    } catch (e: ClientRequestException) {
        clickWebFailure(e.response)
    } catch (e: Exception) {
        Result.failure(e)
    }

/**
 * PATCH `/api/user/ghost-mode {enabled:false}`. Ghost Mode was removed; this clears a server flag an
 * older client may have left on, so nobody stays hidden from Nearby without a way to turn it off.
 */
internal suspend fun ApiClient.clearLegacyGhostModeImpl(): Result<Unit> =
    clickWebCall {
        val response =
            clickWebClient.patch("${ApiClient.clickWebAuthOrigin}/api/user/ghost-mode") {
                contentType(ContentType.Application.Json)
                setBody(GhostModeBody(enabled = false))
            }
        if (response.status.value in 200..299) Result.success(Unit) else clickWebFailure(response)
    }

/** GET `/api/safety/block` — people the viewer blocked, newest first. */
internal suspend fun ApiClient.getBlockedUsersImpl(): Result<List<BlockedUserDto>> =
    clickWebCall {
        val response = clickWebClient.get("${ApiClient.clickWebAuthOrigin}/api/safety/block")
        if (response.status.value in 200..299) {
            Result.success(response.body<BlockedUsersResponseDto>().blocks)
        } else {
            clickWebFailure(response)
        }
    }

/** DELETE `/api/safety/block?blocked_id=` */
internal suspend fun ApiClient.unblockUserImpl(userId: String): Result<Unit> {
    val id = userId.trim()
    if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing user id"))
    return clickWebCall {
        val response =
            clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/safety/block") {
                parameter("blocked_id", id)
            }
        if (response.status.value in 200..299) Result.success(Unit) else clickWebFailure(response)
    }
}

/** DELETE `/api/user/avatar` — clears `users.image` and the stored files. */
internal suspend fun ApiClient.deleteAvatarImpl(): Result<Unit> =
    clickWebCall {
        val response = clickWebClient.delete("${ApiClient.clickWebAuthOrigin}/api/user/avatar")
        if (response.status.value in 200..299) Result.success(Unit) else clickWebFailure(response)
    }

/** POST `/api/users/display-names` — names and avatars for up to [DISPLAY_NAMES_BATCH_MAX] ids. */
internal suspend fun ApiClient.getDisplayNamesImpl(userIds: List<String>): Result<DisplayNamesResponseDto> {
    val ids =
        userIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(DISPLAY_NAMES_BATCH_MAX)
    if (ids.isEmpty()) return Result.success(DisplayNamesResponseDto())
    return clickWebCall {
        val response =
            clickWebClient.post("${ApiClient.clickWebAuthOrigin}/api/users/display-names") {
                contentType(ContentType.Application.Json)
                setBody(DisplayNamesBody(ids))
            }
        if (response.status.value in 200..299) {
            Result.success(response.body<DisplayNamesResponseDto>())
        } else {
            clickWebFailure(response)
        }
    }
}

/**
 * True when click-web answered at all (any HTTP status, including 401). Used to tell "you're offline"
 * apart from "the refresh failed". Unauthenticated on purpose so an expired session still counts.
 */
internal suspend fun ApiClient.isClickWebReachableImpl(): Boolean =
    runCatching { clickWebPlainClient.get("${ApiClient.clickWebAuthOrigin}/api/ping") }.isSuccess
