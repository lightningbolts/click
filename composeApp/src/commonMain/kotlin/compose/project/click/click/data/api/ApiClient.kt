package compose.project.click.click.data.api

import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.models.AuthResponse
import compose.project.click.click.data.models.ErrorResponse
import compose.project.click.click.data.models.LoginRequest
import compose.project.click.click.data.models.SignUpRequest
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.UserCore
import compose.project.click.click.util.redactedRestMessage
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * JSON body for `GET /api/ping` on [ApiConfig.CLICK_WEB_BASE_URL] (verified Supabase JWT).
 */
@Serializable
data class SecurePingResponse(
    val status: String,
    val message: String,
    @SerialName("user_id") val userId: String,
)

@Serializable
private data class UserProfilePatchResponseDto(
    val user: UserCore,
)

/**
 * Response for `GET /api/users/{userId}/profile` — BFF-owned peer profile hydration
 * (replaces the direct Supabase `users` + `user_interests` joins that used to live
 * inside `SupabaseRepository.fetchUserPublicProfile`).
 */
@Serializable
data class UserProfileGetResponse(
    val user: UserCore,
    val tags: List<String> = emptyList(),
    @SerialName("viewerInterestTags") val viewerInterestTags: List<String> = emptyList(),
    @SerialName("sharedInterestTags") val sharedInterestTags: List<String> = emptyList(),
    /** Full legacy shape (availability, availability intents, sharedConnection) preserved as JSON. */
    val availability: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("availabilityIntents") val availabilityIntents: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("sharedConnection") val sharedConnection: kotlinx.serialization.json.JsonElement? = null,
)

/**
 * Response for `GET /api/connections/{connectionId}/tabs` — returns the Media / Files
 * collections used by the profile sheet's Media and Files subtabs. Links are derived
 * client-side from locally-decrypted text messages (content is E2EE on the wire).
 */
@Serializable
data class ConnectionTabsGetResponse(
    @SerialName("chatId") val chatId: String,
    val media: List<ConnectionTabMessage> = emptyList(),
    val files: List<ConnectionTabMessage> = emptyList(),
)

@Serializable
data class ConnectionTabMessage(
    val id: String,
    @SerialName("chat_id") val chatId: String,
    @SerialName("user_id") val userId: String,
    val content: String = "",
    @SerialName("time_created") val timeCreated: Long,
    @SerialName("message_type") val messageType: String,
    val metadata: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
private data class AvatarUploadResponseDto(
    val image: String,
    val user: UserCore? = null,
)

@Serializable
data class NotificationPreferencesPatchBody(
    @SerialName("message_push_enabled")
    val messagePushEnabled: Boolean,
    @SerialName("call_push_enabled")
    val callPushEnabled: Boolean,
)

@Serializable
data class NotificationPreferencesPatchResponse(
    val ok: Boolean,
    val message: String,
)

@Serializable
private data class ConnectionLifecyclePostBody(
    @SerialName("connection_id") val connectionId: String,
)

@Serializable
private data class SafetyReportPostBody(
    @SerialName("connection_id") val connectionId: String,
    val reason: String,
)

/** POST `/api/livekit/token` — matches [click-web/app/api/livekit/token/route.ts]. */
@Serializable
data class LiveKitTokenPostBody(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("participant_name") val participantName: String,
)

@Serializable
data class LiveKitTokenResponse(
    val token: String,
    @SerialName("ws_url") val wsUrl: String,
)

/** POST `/api/user/push-tokens` — matches [click-web/app/api/user/push-tokens/route.ts]. */
@Serializable
data class PushTokenRegisterBody(
    val token: String,
    val platform: String,
    @SerialName("token_type") val tokenType: String,
)

@Serializable
data class PushTokenRegisterResponse(
    val ok: Boolean,
)

class ApiClient(private val baseUrl: String = BASE_URL) {

    companion object {
        /**
         * Base URL for the Click Python/Flask backend.
         *
         * • Local dev (iOS simulator): http://localhost:5000
         * • Local dev (Android emulator): http://10.0.2.2:5000
         *   (Android emulator maps 10.0.2.2 → host machine's localhost)
         * • Production: replace with your deployed server URL
         *   e.g. https://api.your-domain.com
         */
        const val BASE_URL = "http://localhost:5000"

        private val clickWebAuthOrigin: String
            get() = ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')

        private val clickWebAuthHost: String
            get() = io.ktor.http.Url(clickWebAuthOrigin).host
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** HTTP client for Flask backend calls — no bearer-auth plugin. */
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** HTTP client for click-web (Next.js) calls — attaches Supabase JWT automatically. */
    private val clickWebClient = HttpClient {
        install(ContentNegotiation) {
            json(json)
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val session = SupabaseConfig.client.auth.currentSessionOrNull()
                        ?: return@loadTokens null
                    val access = session.accessToken
                    if (access.isBlank()) return@loadTokens null
                    BearerTokens(access, session.refreshToken.orEmpty())
                }
                refreshTokens {
                    try {
                        SupabaseConfig.client.auth.refreshCurrentSession()
                    } catch (_: Exception) {
                        return@refreshTokens null
                    }
                    val session = SupabaseConfig.client.auth.currentSessionOrNull()
                        ?: return@refreshTokens null
                    val access = session.accessToken
                    if (access.isBlank()) return@refreshTokens null
                    BearerTokens(access, session.refreshToken.orEmpty())
                }
                sendWithoutRequest { request ->
                    request.url.host == clickWebAuthHost
                }
            }
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Login failed")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        birthdayIso: String,
    ): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/create_account") {
                contentType(ContentType.Application.Json)
                setBody(SignUpRequest(email, password, firstName, lastName, birthdayIso))
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Sign up failed")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun authenticateWithGoogle(token: String): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/google") {
                parameter("token", token)
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<AuthResponse>())
            } else {
                Result.failure(Exception("Google authentication failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshToken(refreshToken: String): Result<String> {
        return try {
            val response = client.post("$baseUrl/refresh") {
                header("Authorization", refreshToken)
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<String>())
            } else {
                Result.failure(Exception("Token refresh failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout(refreshToken: String): Result<Unit> {
        return try {
            val response = client.post("$baseUrl/logout") {
                header("Authorization", refreshToken)
            }

            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Logout failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createConnection(
        authToken: String,
        user1Id: String,
        user2Id: String,
        latitude: Double,
        longitude: Double,
        contextTag: String? = null
    ): Result<Connection> {
        return try {
            val response = client.post("$baseUrl/connection/new/") {
                header("Authorization", authToken)
                contentType(ContentType.Application.Json)
                parameter("id1", user1Id)
                parameter("id2", user2Id)
                parameter("lat", latitude)
                parameter("long", longitude)
                contextTag?.let { parameter("context_tag_id", it) }
            }

            if (response.status.value in 200..299) {
                Result.success(response.body<Connection>())
            } else {
                val error = try {
                    response.body<ErrorResponse>()
                } catch (e: Exception) {
                    ErrorResponse("Failed to create connection")
                }
                Result.failure(Exception(error.error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserById(userId: String): Result<User> {
        return try {
            val response = client.get("$baseUrl/user/$userId")

            if (response.status.value in 200..299) {
                Result.success(response.body<User>())
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Temporary helper: calls Next.js `GET /api/ping` with a Supabase JWT (see Ktor [Auth] bearer config).
     */
    suspend fun testSecurePing(): Result<SecurePingResponse> {
        return try {
            val response: HttpResponse = clickWebClient.get("$clickWebAuthOrigin/api/ping")
            if (response.status.value in 200..299) {
                val body = response.body<SecurePingResponse>()
                println(
                    "ApiClient.testSecurePing: status=${response.status.value} " +
                        "body=${body.status} message=${body.message} user_id=${body.userId}",
                )
                Result.success(body)
            } else {
                val errText = runCatching { response.body<String>() }.getOrElse { it.message ?: "error" }
                println("ApiClient.testSecurePing: failed status=${response.status.value} body=$errText")
                Result.failure(Exception("Ping failed (${response.status.value}): $errText"))
            }
        } catch (e: Exception) {
            println("ApiClient.testSecurePing: exception ${e.redactedRestMessage()}")
            Result.failure(e)
        }
    }

    private suspend fun readClickWebErrorMessage(response: HttpResponse): String {
        val fromJson = runCatching { response.body<ErrorResponse>() }.getOrNull()?.error?.trim().orEmpty()
        if (fromJson.isNotEmpty()) return fromJson
        return runCatching { response.body<String>() }.getOrNull()?.trim().orEmpty()
            .ifEmpty { "Request failed (${response.status.value})" }
    }

    /**
     * PATCH `/api/users/{userId}/profile` on click-web (JWT via Ktor Auth bearer).
     * Provide at least one of [firstName], [lastName], [image], [tags].
     */
    /**
     * POST `/api/user/avatar` on click-web (multipart `file` + JWT bearer).
     * Returns the new public image URL.
     */
    suspend fun uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        val filename = when {
            normalizedMime.contains("png", ignoreCase = true) -> "avatar.png"
            normalizedMime.contains("webp", ignoreCase = true) -> "avatar.webp"
            normalizedMime.contains("gif", ignoreCase = true) -> "avatar.gif"
            else -> "avatar.jpg"
        }
        return try {
            val multipart = MultiPartFormDataContent(
                formData {
                    append(
                        key = "file",
                        value = imageBytes,
                        headers = Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "form-data; name=\"file\"; filename=\"$filename\"",
                            )
                            append(HttpHeaders.ContentType, normalizedMime)
                        },
                    )
                    append(key = "mime_type", value = normalizedMime)
                },
            )
            val response = clickWebClient.post("$clickWebAuthOrigin/api/user/avatar") {
                setBody(multipart)
            }
            if (response.status.value in 200..299) {
                val dto = response.body<AvatarUploadResponseDto>()
                val url = dto.image.trim()
                if (url.isEmpty()) {
                    Result.failure(Exception("Avatar upload returned an empty URL"))
                } else {
                    Result.success(url)
                }
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET `/api/users/{userId}/profile` on click-web (JWT via Ktor Auth bearer).
     *
     * BFF migration (Phase 3 — C15): replaces the direct `users` + `user_interests`
     * Supabase PostgREST joins that used to live inside
     * [SupabaseRepository.fetchUserPublicProfile]. The Next.js route owns the join so
     * the mobile client never talks to Supabase directly for profile hydration.
     */
    suspend fun getUserProfile(userId: String): Result<UserProfileGetResponse> {
        val id = userId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("userId required"))
        return try {
            val response: HttpResponse = clickWebClient.get(
                "$clickWebAuthOrigin/api/users/$id/profile",
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

    /**
     * GET `/api/connections/{connectionId}/tabs` on click-web — fetches Media + Files
     * listings for the profile sheet. Links remain client-side because message
     * [content] is E2EE on the wire; callers filter locally-decrypted state.
     */
    suspend fun getConnectionTabs(connectionId: String): Result<ConnectionTabsGetResponse> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("connectionId required"))
        return try {
            val response: HttpResponse = clickWebClient.get(
                "$clickWebAuthOrigin/api/connections/$id/tabs",
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

    suspend fun patchUserProfile(
        userId: String,
        firstName: String? = null,
        lastName: String? = null,
        image: String? = null,
        tags: List<String>? = null,
    ): Result<User> {
        if (firstName == null && lastName == null && image == null && tags == null) {
            return Result.failure(IllegalArgumentException("No profile fields to update"))
        }
        val body = buildJsonObject {
            firstName?.let { put("first_name", it) }
            lastName?.let { put("last_name", it) }
            image?.let { put("image", it) }
            tags?.let { list ->
                put("tags", JsonArray(list.map { JsonPrimitive(it) }))
            }
        }
        if (body.isEmpty()) {
            return Result.failure(IllegalArgumentException("No profile fields to update"))
        }
        return try {
            val response = clickWebClient.patch("$clickWebAuthOrigin/api/users/$userId/profile") {
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

    /**
     * PATCH `/api/user/preferences` on click-web.
     */
    suspend fun patchNotificationPreferences(body: NotificationPreferencesPatchBody): Result<NotificationPreferencesPatchResponse> {
        return try {
            val response = clickWebClient.patch("$clickWebAuthOrigin/api/user/preferences") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<NotificationPreferencesPatchResponse>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/connections/archive` — per-user archive junction (`connection_archives`). */
    suspend fun postConnectionArchive(connectionId: String): Result<Unit> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
        return try {
            val response = clickWebClient.post("$clickWebAuthOrigin/api/connections/archive") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/connections/unarchive` — remove archive row and restore lifecycle (`kept`). */
    suspend fun postConnectionUnarchive(connectionId: String): Result<Unit> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
        return try {
            val response = clickWebClient.post("$clickWebAuthOrigin/api/connections/unarchive") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/connections/hide` — per-user hide (`connection_hidden`) for the JWT user only. */
    suspend fun postConnectionHide(connectionId: String): Result<Unit> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("Missing connection id"))
        return try {
            val response = clickWebClient.post("$clickWebAuthOrigin/api/connections/hide") {
                contentType(ContentType.Application.Json)
                setBody(ConnectionLifecyclePostBody(connectionId = id))
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST `/api/livekit/token` on click-web (JWT via Ktor Auth bearer).
     * [roomName] must be `click-{connectionId}-…` as issued for the in-flight call invite.
     */
    suspend fun postLiveKitToken(body: LiveKitTokenPostBody): Result<LiveKitTokenResponse> {
        repeat(3) { attempt ->
            try {
                val response = clickWebClient.post("$clickWebAuthOrigin/api/livekit/token") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
                when {
                    response.status.value in 200..299 -> {
                        return Result.success(response.body<LiveKitTokenResponse>())
                    }
                    response.status.value in 500..599 -> {
                        if (attempt == 2) {
                            return Result.failure(
                                Exception("Failed to create call token (${response.status.value})"),
                            )
                        }
                    }
                    else -> {
                        return Result.failure(Exception(readClickWebErrorMessage(response)))
                    }
                }
            } catch (e: Exception) {
                if (attempt == 2) {
                    return Result.failure(e)
                }
            }
            delay(350L * (attempt + 1))
        }
        return Result.failure(Exception("Failed to create call token"))
    }

    /** POST `/api/user/push-tokens` — upserts the device token for the signed-in user. */
    suspend fun postPushToken(body: PushTokenRegisterBody): Result<PushTokenRegisterResponse> {
        return try {
            val response = clickWebClient.post("$clickWebAuthOrigin/api/user/push-tokens") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.value in 200..299) {
                Result.success(response.body<PushTokenRegisterResponse>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/safety/report` — insert `connection_reports` for the JWT user. */
    suspend fun postSafetyReport(connectionId: String, reason: String): Result<Unit> {
        val id = connectionId.trim()
        val trimmedReason = reason.trim()
        if (id.isEmpty() || trimmedReason.isEmpty()) {
            return Result.failure(IllegalArgumentException("connection_id and reason are required"))
        }
        return try {
            val response = clickWebClient.post("$clickWebAuthOrigin/api/safety/report") {
                contentType(ContentType.Application.Json)
                setBody(SafetyReportPostBody(connectionId = id, reason = trimmedReason))
            }
            if (response.status.value in 200..299) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
        clickWebClient.close()
    }
}
