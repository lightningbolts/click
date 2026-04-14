package compose.project.click.click.data.repository

import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.api.PushTokenRegisterBody

/**
 * Persists FCM / APNs tokens via click-web `POST /api/user/push-tokens` (JWT + server upsert).
 *
 * **Archive-warning pushes:** the scheduled Edge Function `expire-connections` loads tokens
 * from `push_tokens` and calls `send-push-notification` with `data.type = archive_warning`
 * (12h before auto-archive). No extra client hook is required after registration.
 */
class PushTokenRepository(
    private val apiClient: ApiClient = ApiClient(),
) {
    suspend fun savePushToken(
        userId: String,
        token: String,
        platform: String,
        tokenType: String = "standard",
    ): Boolean {
        if (userId.isBlank()) return false
        val normalizedPlatform = when (platform.lowercase()) {
            "android" -> "android"
            "ios" -> "ios"
            else -> return false
        }
        val normalizedType = when (tokenType.lowercase()) {
            "voip" -> "voip"
            else -> "standard"
        }
        return apiClient
            .postPushToken(
                PushTokenRegisterBody(
                    token = token,
                    platform = normalizedPlatform,
                    tokenType = normalizedType,
                ),
            )
            .fold(
                onSuccess = { it.ok },
                onFailure = {
                    println("Error saving push token: ${it.message}")
                    false
                },
            )
    }
}
