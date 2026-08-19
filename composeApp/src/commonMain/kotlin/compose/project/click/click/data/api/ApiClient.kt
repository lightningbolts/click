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
 * HTTP client for the Next.js companion (`click-web`). Auth uses Supabase JWT via Ktor Auth bearer.
 * Legacy Flask routes were removed — do not reintroduce `localhost:5000` / LAN API bases.
 */
class ApiClient {
    companion object {
        internal val clickWebAuthOrigin: String
            get() = ApiConfig.CLICK_WEB_BASE_URL.trimEnd('/')
    }

    internal val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    internal val tokenStorage by lazy { createTokenStorage() }

    /**
     * Lazy so constructing [ApiClient] (e.g. via [MapBeaconRepository] in Robolectric unit tests)
     * does not touch AndroidKeyStore until an HTTP call actually runs.
     */
    internal val clickWebClientLazy =
        lazy {
            HttpClient {
                install(ContentNegotiation) {
                    json(json)
                }
                installClickWebBearerAuth(tokenStorage)
                installClickWeb403RetryInterceptor(tokenStorage)
            }
        }
    internal val clickWebClient: HttpClient get() = clickWebClientLazy.value

    internal val clickWebPlainClientLazy =
        lazy {
            HttpClient {
                install(ContentNegotiation) {
                    json(json)
                }
            }
        }
    internal val clickWebPlainClient: HttpClient get() = clickWebPlainClientLazy.value

    /**
     * Temporary helper: calls Next.js `GET /api/ping` with a Supabase JWT (see Ktor [Auth] bearer config).
     */
    suspend fun testSecurePing(): Result<SecurePingResponse> =
        try {
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

    internal suspend fun readClickWebErrorMessage(response: HttpResponse): String {
        val status = response.status.value
        val fromJson =
            runCatching { response.body<ErrorResponse>() }
                .getOrNull()
                ?.error
                ?.trim()
                .orEmpty()
        if (fromJson.isNotEmpty()) return fromJson.take(200)
        val raw = runCatching { response.bodyAsText() }.getOrNull()?.trim().orEmpty()
        if (raw.contains("<!DOCTYPE", ignoreCase = true) || raw.contains("<html", ignoreCase = true)) {
            return when (status) {
                404 -> "Server update required for Disposable Roll (missing API route)."
                401, 403 -> "Session expired. Sign in again."
                in 500..599 -> "Server error ($status). Try again later."
                else -> "Request failed ($status)."
            }
        }
        return raw.take(200).ifEmpty { "Request failed ($status)" }
    }

    internal suspend fun clickWebFailure(response: HttpResponse): Result<Nothing> {
        val status = response.status.value
        val message = readClickWebErrorMessage(response)
        return Result.failure(ClickWebRequestException(status, message))
    }

    internal suspend fun currentAccessToken(): String? = EnsureFreshAccessToken.get(tokenStorage)

    /**
     * POST `/api/user/avatar` on click-web (JSON base64 `file_b64` + JWT bearer).
     * Returns the new public image URL.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadAvatar(
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        return try {
            val payload =
                AvatarUploadBodyDto(
                    fileBase64 = Base64.encode(imageBytes),
                    mimeType = normalizedMime,
                )
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/user/avatar") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
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

    /** Unencrypted public photo for a community beacon (max 2 MB after client compress). */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadBeaconImage(
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<String> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        return try {
            val payload =
                AvatarUploadBodyDto(
                    fileBase64 = Base64.encode(imageBytes),
                    mimeType = normalizedMime,
                )
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/beacons/image") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            if (response.status.value in 200..299) {
                val dto = response.body<AvatarUploadResponseDto>()
                val url = dto.image.trim()
                if (url.isEmpty()) {
                    Result.failure(Exception("Beacon image upload returned an empty URL"))
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

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadGroupAvatar(
        groupId: String,
        imageBytes: ByteArray,
        mimeType: String,
    ): Result<String> {
        val gid = groupId.trim()
        if (gid.isEmpty()) return Result.failure(IllegalArgumentException("groupId required"))
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Empty image"))
        }
        val normalizedMime = mimeType.trim().ifEmpty { "image/jpeg" }
        return try {
            val payload =
                AvatarUploadBodyDto(
                    fileBase64 = Base64.encode(imageBytes),
                    mimeType = normalizedMime,
                )
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/groups/$gid/avatar") {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                }
            if (response.status.value in 200..299) {
                val dto = response.body<AvatarUploadResponseDto>()
                val url = dto.image.trim()
                if (url.isEmpty()) {
                    Result.failure(Exception("Group avatar upload returned an empty URL"))
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

    suspend fun getUserProfile(userId: String): Result<UserProfileGetResponse> = getUserProfileImpl(userId = userId)

    suspend fun getProfileTimeline(
        targetType: String,
        targetId: String,
    ): Result<ProfileTimelinePayload> = getProfileTimelineImpl(targetType = targetType, targetId = targetId)

    suspend fun postProfileTimelineJournalEntry(
        targetType: String,
        targetId: String,
        body: String,
        visibility: String,
    ): Result<ProfileTimelinePayload> = postProfileTimelineJournalEntryImpl(targetType = targetType, targetId = targetId, body = body, visibility = visibility)

    suspend fun putProfileTimelineJournalEntry(
        id: String,
        body: String,
        visibility: String,
    ): Result<ProfileTimelinePayload> = putProfileTimelineJournalEntryImpl(id = id, body = body, visibility = visibility)

    suspend fun deleteProfileTimelineJournalEntry(id: String): Result<ProfileTimelinePayload> = deleteProfileTimelineJournalEntryImpl(id = id)

    suspend fun getActivityRecap(window: String = "week"): Result<ActivityRecapDto> = getActivityRecapImpl(window = window)

    suspend fun getConnectionTabs(connectionId: String): Result<ConnectionTabsGetResponse> = getConnectionTabsImpl(connectionId = connectionId)

    suspend fun patchUserProfile(
        userId: String,
        firstName: String? = null,
        lastName: String? = null,
        image: String? = null,
        tags: List<String>? = null,
        birthday: String? = null,
        personalityTags: List<String>? = null,
    ): Result<User> = patchUserProfileImpl(userId = userId, firstName = firstName, lastName = lastName, image = image, tags = tags, birthday = birthday, personalityTags = personalityTags)

    /**
     * PATCH `/api/user/preferences` on click-web.
     */
    suspend fun patchNotificationPreferences(body: NotificationPreferencesPatchBody): Result<NotificationPreferencesPatchResponse> =
        try {
            val response =
                clickWebClient.patch("$clickWebAuthOrigin/api/user/preferences") {
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

    suspend fun postConnectionArchive(connectionId: String): Result<Unit> = postConnectionArchiveImpl(connectionId = connectionId)

    suspend fun postConnectionUnarchive(connectionId: String): Result<Unit> = postConnectionUnarchiveImpl(connectionId = connectionId)

    suspend fun postConnectionCore(connectionId: String): Result<Unit> = postConnectionCoreImpl(connectionId = connectionId)

    suspend fun fetchConnectionCoreIds(): Result<Set<String>> = fetchConnectionCoreIdsImpl()

    suspend fun deleteConnectionCore(connectionId: String): Result<Unit> = deleteConnectionCoreImpl(connectionId = connectionId)

    suspend fun postConnectionHide(connectionId: String): Result<Unit> = postConnectionHideImpl(connectionId = connectionId)

    /**
     * POST `/api/livekit/token` on click-web (JWT via Ktor Auth bearer).
     * [roomName] must be `click-{connectionId}-…` as issued for the in-flight call invite.
     */
    suspend fun postLiveKitToken(body: LiveKitTokenPostBody): Result<LiveKitTokenResponse> {
        repeat(3) { attempt ->
            try {
                val response =
                    clickWebClient.post("$clickWebAuthOrigin/api/livekit/token") {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                when {
                    response.status.value in 200..299 -> {
                        return Result.success(response.body<LiveKitTokenResponse>())
                    }
                    response.status.value in 500..599 -> {
                        val detail = readClickWebErrorMessage(response)
                        if (attempt == 2) {
                            return Result.failure(
                                Exception(
                                    detail.ifBlank {
                                        "Failed to create call token (${response.status.value})"
                                    },
                                ),
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
    suspend fun postPushToken(body: PushTokenRegisterBody): Result<PushTokenRegisterResponse> =
        try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/user/push-tokens") {
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

    /** POST `/api/safety/report` — insert `connection_reports` for the JWT user. */
    suspend fun postSafetyReport(
        connectionId: String,
        reason: String,
    ): Result<Unit> {
        val id = connectionId.trim()
        val trimmedReason = reason.trim()
        if (id.isEmpty() || trimmedReason.isEmpty()) {
            return Result.failure(IllegalArgumentException("connection_id and reason are required"))
        }
        return try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/safety/report") {
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

    /** POST `/api/contacts/discover` — SHA-256 hashes only. */
    suspend fun discoverContacts(hashedContacts: List<String>): Result<List<DiscoverProfileCard>> {
        if (hashedContacts.isEmpty()) return Result.success(emptyList())
        return try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/contacts/discover") {
                    contentType(ContentType.Application.Json)
                    setBody(ContactsDiscoverBody(hashedContacts = hashedContacts))
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ContactsDiscoverResponse>().matches)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/connections/prior/request`. */
    suspend fun requestPriorConnection(
        targetUserId: String,
        knownSince: String = "unspecified",
        contextTag: String? = null,
    ): Result<PriorConnectionMutationResponse> {
        val id = targetUserId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("target_user_id is required"))
        return try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/connections/prior/request") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        PriorConnectionRequestBody(
                            targetUserId = id,
                            knownSince = knownSince,
                            contextTag = contextTag,
                        ),
                    )
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<PriorConnectionMutationResponse>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/connections/prior/respond` — accept or decline. */
    suspend fun respondPriorConnection(
        connectionId: String,
        action: String,
    ): Result<PriorConnectionMutationResponse> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("connection_id is required"))
        return try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/connections/prior/respond") {
                    contentType(ContentType.Application.Json)
                    setBody(PriorConnectionRespondBody(connectionId = id, action = action))
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<PriorConnectionMutationResponse>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMapBeacons(
        lat: Double,
        lon: Double,
        radiusMeters: Double,
        filters: String? = null,
    ): Result<String> = getMapBeaconsImpl(lat = lat, lon = lon, radiusMeters = radiusMeters, filters = filters)

    suspend fun getMapBeacon(beaconId: String): Result<MapBeacon> = getMapBeaconImpl(beaconId = beaconId)

    suspend fun postMapBeacon(insert: MapBeaconInsert): Result<MapBeacon> = postMapBeaconImpl(insert = insert)

    suspend fun patchMapBeacon(
        beaconId: String,
        patch: MapBeaconPatchBody,
    ): Result<MapBeacon> = patchMapBeaconImpl(beaconId = beaconId, patch = patch)

    suspend fun deleteMapBeacon(beaconId: String): Result<Unit> = deleteMapBeaconImpl(beaconId = beaconId)

    /** POST `/api/chat/attachments/sign` — mint short-lived URL for a chat attachment path. */
    suspend fun getSignedChatAttachmentUrl(path: String): Result<String> {
        val p = path.trim()
        if (p.isEmpty()) return Result.failure(IllegalArgumentException("path required"))
        return try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/chat/attachments/sign") {
                    contentType(ContentType.Application.Json)
                    setBody(SignAttachmentPostBody(path = p))
                }
            if (response.status.value in 200..299) {
                val payload = response.body<SignAttachmentResponse>()
                val url = payload.url.trim()
                if (url.isEmpty()) {
                    Result.failure(Exception("Attachment sign response missing URL"))
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

    /** GET `/api/users/{userId}/public-profile` — unauthenticated. */
    suspend fun getPublicProfileUnauthenticated(userId: String): Result<PublicProfileUnauthenticatedResponse> {
        val id = userId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("userId required"))
        return try {
            val response: HttpResponse =
                clickWebPlainClient.get(
                    "$clickWebAuthOrigin/api/users/$id/public-profile",
                )
            if (response.status.value in 200..299) {
                Result.success(response.body<PublicProfileUnauthenticatedResponse>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** POST `/api/hub/create` — JWT bearer. */
    suspend fun postHubCreate(body: HubCreatePostBody): Result<HubCreateResponseDto> =
        try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/hub/create") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<HubCreateResponseDto>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun postProximityHandshake(
        body: ProximityHandshakePostBody,
        bearerJwt: String? = null,
    ): Result<ProximityHandshakePostResult> = postProximityHandshakeImpl(body = body, bearerJwt = bearerJwt)

    suspend fun postProximityConfirmSelection(
        bearerJwt: String,
        pendingHandshakeId: String,
        selectedMemberIds: List<String>,
        contextTags: List<String>? = null,
    ): Result<ProximityBindOkResponseDto> = postProximityConfirmSelectionImpl(bearerJwt = bearerJwt, pendingHandshakeId = pendingHandshakeId, selectedMemberIds = selectedMemberIds, contextTags = contextTags)

    suspend fun getPendingProximityHandshake(
        pendingHandshakeId: String,
        bearerJwt: String? = null,
    ): Result<ProximityHandshakePostResult> = getPendingProximityHandshakeImpl(pendingHandshakeId = pendingHandshakeId, bearerJwt = bearerJwt)

    suspend fun prewarmProximityHandshake() = prewarmProximityHandshakeImpl()

    /** POST `/api/connections/encounter` — JWT bearer; inserts encounter row only. */
    suspend fun postConnectionEncounter(body: ConnectionEncounterPostBody): Result<Unit> =
        try {
            val response =
                clickWebClient.post("$clickWebAuthOrigin/api/connections/encounter") {
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            when {
                response.status.value in 200..299 -> Result.success(Unit)
                response.status.value == 429 -> Result.failure(Exception("Encounter rate limit — try again later."))
                else -> Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }

    suspend fun postOpenCollaborationSession(connectionId: String): Result<CollaborationSessionPostResponse> = postOpenCollaborationSessionImpl(connectionId = connectionId)

    suspend fun postOpenCollaborationSessionForChat(chatId: String): Result<CollaborationSessionPostResponse> = postOpenCollaborationSessionForChatImpl(chatId = chatId)

    suspend fun postOpenCollaborationSessionFallback(connectionId: String): Result<CollaborationSessionPostResponse> = postOpenCollaborationSessionFallbackImpl(connectionId = connectionId)

    /** GET `/api/insights/widget-vibe` — JWT bearer. */
    suspend fun getWidgetVibePayload(): Result<WidgetVibePayloadDto> =
        try {
            val response: HttpResponse = clickWebClient.get("$clickWebAuthOrigin/api/insights/widget-vibe")
            if (response.status.value in 200..299) {
                Result.success(response.body<WidgetVibePayloadDto>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }

    /** GET `/api/hub/nearby` — JWT bearer; active hubs near lat/lon. */
    suspend fun getNearbyCommunityHubs(
        lat: Double,
        lon: Double,
        radiusMeters: Double = 15_000.0,
    ): Result<List<CommunityHubNearbyDto>> {
        if (!lat.isFinite() || !lon.isFinite()) {
            return Result.failure(IllegalArgumentException("lat/lon required"))
        }
        return try {
            val response: HttpResponse =
                clickWebClient.get("$clickWebAuthOrigin/api/hub/nearby") {
                    parameter("lat", lat)
                    parameter("lon", lon)
                    parameter("radius_meters", radiusMeters)
                }
            if (response.status.value in 200..299) {
                val env = response.body<CommunityHubNearbyEnvelope>()
                Result.success(env.hubs)
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBeaconRsvp(beaconId: String): Result<BeaconRsvpGetResponseDto> = getBeaconRsvpImpl(beaconId = beaconId)

    suspend fun getBeaconAttendeeDirectory(beaconId: String): Result<BeaconAttendeeDirectoryResponseDto> = getBeaconAttendeeDirectoryImpl(beaconId = beaconId)

    /**
     * GET `/api/connections/{connectionId}/event-recommendation` — one shared upcoming event for a new peer.
     */
    suspend fun getConnectionEventRecommendation(
        connectionId: String,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Result<ConnectionEventRecommendationResponseDto> {
        val id = connectionId.trim()
        if (id.isEmpty()) return Result.failure(IllegalArgumentException("connectionId required"))
        return try {
            val response: HttpResponse =
                clickWebClient.get("$clickWebAuthOrigin/api/connections/$id/event-recommendation") {
                    if (latitude != null) parameter("lat", latitude)
                    if (longitude != null) parameter("lng", longitude)
                }
            if (response.status.value in 200..299) {
                Result.success(response.body<ConnectionEventRecommendationResponseDto>())
            } else {
                Result.failure(Exception(readClickWebErrorMessage(response)))
            }
        } catch (e: ClientRequestException) {
            Result.failure(Exception(readClickWebErrorMessage(e.response)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postBeaconRsvp(
        beaconId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracyMeters: Double? = null,
        platform: String? = null,
    ): Result<BeaconAttendeeDto> = postBeaconRsvpImpl(beaconId = beaconId, latitude = latitude, longitude = longitude, accuracyMeters = accuracyMeters, platform = platform)

    suspend fun deleteBeaconRsvp(beaconId: String): Result<Unit> = deleteBeaconRsvpImpl(beaconId = beaconId)

    suspend fun getBeaconEngagement(beaconId: String): Result<BeaconEngagementDto> = getBeaconEngagementImpl(beaconId = beaconId)

    suspend fun putBeaconBookmark(
        beaconId: String,
        bookmarked: Boolean,
        telemetry: EngagementTelemetryBody = EngagementTelemetryBody(),
    ): Result<Unit> = putBeaconBookmarkImpl(beaconId = beaconId, bookmarked = bookmarked, telemetry = telemetry)

    suspend fun postBeaconCheckIn(
        beaconId: String,
        telemetry: EngagementTelemetryBody,
    ): Result<BeaconCheckInMutationDto> = postBeaconCheckInImpl(beaconId = beaconId, telemetry = telemetry)

    suspend fun deleteBeaconCheckIn(beaconId: String): Result<Unit> = deleteBeaconCheckInImpl(beaconId = beaconId)

    suspend fun postBeaconImpression(
        beaconId: String,
        telemetry: EngagementTelemetryBody = EngagementTelemetryBody(surface = "detail"),
    ): Result<Unit> = postBeaconImpressionImpl(beaconId = beaconId, telemetry = telemetry)

    suspend fun postBeaconShare(
        beaconId: String,
        telemetry: EngagementTelemetryBody = EngagementTelemetryBody(surface = "detail"),
        shareUrl: String? = null,
    ): Result<Unit> = postBeaconShareImpl(beaconId = beaconId, telemetry = telemetry, shareUrl = shareUrl)

    suspend fun getMyEventBookmarks(
        limit: Int = 50,
        cursor: String? = null,
    ): Result<EventBookmarksResponseDto> = getMyEventBookmarksImpl(limit = limit, cursor = cursor)

    fun close() {
        if (clickWebClientLazy.isInitialized()) clickWebClientLazy.value.close()
        if (clickWebPlainClientLazy.isInitialized()) clickWebPlainClientLazy.value.close()
    }
}

@Serializable
data class MapBeaconPatchBody(
    val metadata: JsonObject? = null,
    @SerialName("show_creator_name") val showCreatorName: Boolean? = null,
    @SerialName("ttl_ms") val ttlMs: Long? = null,
)

@Serializable
data class MapBeaconPatchResponseDto(
    val beacon: JsonObject? = null,
)

@Serializable
data class MapBeaconPostResponseDto(
    val beacon: JsonObject? = null,
    val deduplicated: Boolean = false,
)

@Serializable
data class BeaconAttendeeDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class MutualViaDto(
    @SerialName("user_id") val userId: String,
    val name: String,
)

@Serializable
data class DirectoryAttendeeDto(
    @SerialName("user_id") val userId: String,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("signed_up_at") val signedUpAt: String? = null,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("shared_interests") val sharedInterests: List<String> = emptyList(),
    @SerialName("shared_interest_count") val sharedInterestCount: Int = 0,
    val relationship: String = "stranger",
    @SerialName("mutual_via") val mutualVia: List<MutualViaDto> = emptyList(),
    @SerialName("mutual_connection_count") val mutualConnectionCount: Int = 0,
)

@Serializable
data class BeaconAttendeeDirectoryResponseDto(
    @SerialName("beacon_id") val beaconId: String? = null,
    val attendees: List<DirectoryAttendeeDto> = emptyList(),
    @SerialName("current_user_signed_up") val currentUserSignedUp: Boolean = false,
    @SerialName("current_user_checked_in") val currentUserCheckedIn: Boolean = false,
    @SerialName("mutuals_section_unlocked") val mutualsSectionUnlocked: Boolean = false,
)

@Serializable
data class ConnectionEventRecommendationDto(
    @SerialName("beacon_id") val beaconId: String,
    val title: String,
    @SerialName("event_start_at") val eventStartAt: String? = null,
    @SerialName("event_end_at") val eventEndAt: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("peer_name") val peerName: String,
    @SerialName("peer_user_id") val peerUserId: String,
    val score: Double = 0.0,
    @SerialName("shared_category_tags") val sharedCategoryTags: List<String> = emptyList(),
)

@Serializable
data class ConnectionEventRecommendationResponseDto(
    val recommendation: ConnectionEventRecommendationDto? = null,
)

@Serializable
data class BeaconRsvpGetResponseDto(
    @SerialName("beacon_id") val beaconId: String? = null,
    val attendees: List<BeaconAttendeeDto> = emptyList(),
    @SerialName("current_user_signed_up") val currentUserSignedUp: Boolean = false,
)

@Serializable
data class BeaconRsvpPostResponseDto(
    val ok: Boolean = false,
    val attendee: BeaconAttendeeDto? = null,
)

@Serializable
data class BeaconRsvpPostBody(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerialName("client_occurred_at") val clientOccurredAt: String? = null,
    val source: String? = null,
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
)

@Serializable
data class EngagementTelemetryBody(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerialName("client_occurred_at") val clientOccurredAt: String? = null,
    val source: String? = "mobile",
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val surface: String? = null,
    val bookmarked: Boolean? = null,
)

@Serializable
data class ShareTelemetryBody(
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerialName("client_occurred_at") val clientOccurredAt: String? = null,
    val source: String? = "mobile",
    val platform: String? = null,
    @SerialName("app_version") val appVersion: String? = null,
    val surface: String? = null,
    @SerialName("share_url") val shareUrl: String? = null,
)

@Serializable
data class BeaconEngagementDto(
    @SerialName("beacon_id") val beaconId: String? = null,
    val bookmarked: Boolean = false,
    @SerialName("checked_in") val checkedIn: Boolean = false,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("check_in_count") val checkInCount: Int = 0,
)

@Serializable
data class EventBookmarkItemDto(
    @SerialName("beacon_id") val beaconId: String,
    @SerialName("bookmarked_at") val bookmarkedAt: String? = null,
    val title: String? = null,
    @SerialName("event_start_at") val eventStartAt: String? = null,
    @SerialName("event_end_at") val eventEndAt: String? = null,
    @SerialName("location_name") val locationName: String? = null,
    @SerialName("formatted_address") val formattedAddress: String? = null,
    @SerialName("event_categories") val eventCategories: List<String> = emptyList(),
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("creator_id") val creatorId: String? = null,
    @SerialName("creator_name") val creatorName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("show_creator_name") val showCreatorName: Boolean = false,
)

fun EventBookmarkItemDto.toStoredEventBookmark(): StoredEventBookmark =
    StoredEventBookmark(
        beaconId = beaconId,
        bookmarkedAt = bookmarkedAt,
        title = title,
        eventStartAt = eventStartAt,
        eventEndAt = eventEndAt,
        locationName = locationName,
        formattedAddress = formattedAddress,
        eventCategories = eventCategories,
        latitude = latitude,
        longitude = longitude,
        expiresAt = expiresAt,
        creatorId = creatorId,
        creatorName = creatorName,
        createdAt = createdAt,
        showCreatorName = showCreatorName,
    )

fun StoredEventBookmark.toEventBookmarkItemDto(): EventBookmarkItemDto =
    EventBookmarkItemDto(
        beaconId = beaconId,
        bookmarkedAt = bookmarkedAt,
        title = title,
        eventStartAt = eventStartAt,
        eventEndAt = eventEndAt,
        locationName = locationName,
        formattedAddress = formattedAddress,
        eventCategories = eventCategories,
        latitude = latitude,
        longitude = longitude,
        expiresAt = expiresAt,
        creatorId = creatorId,
        creatorName = creatorName,
        createdAt = createdAt,
        showCreatorName = showCreatorName,
    )

@Serializable
data class EventBookmarksResponseDto(
    val bookmarks: List<EventBookmarkItemDto> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class BeaconCheckInMutationDto(
    val ok: Boolean = false,
    @SerialName("checked_in") val checkedIn: Boolean = false,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
    @SerialName("check_in_count") val checkInCount: Int = 0,
)

class BeaconEngagementHttpException(
    val status: Int,
    override val message: String,
) : Exception(message)
