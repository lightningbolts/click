@file:Suppress("ktlint:standard:backing-property-naming")

package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.CHAT_MEDIA_BUCKET // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.audioCacheFileExtension // pragma: allowlist secret
import compose.project.click.click.data.models.hasLocalMediaUri // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
import compose.project.click.click.data.realtime.subscribeWithTimeout // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.data.repository.normalizeEncryptedMediaPayload // pragma: allowlist secret
import compose.project.click.click.data.storage.TokenStorage // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile // pragma: allowlist secret
import compose.project.click.click.util.LruMemoryCache // pragma: allowlist secret
import compose.project.click.click.util.chatMediaDispatcher // pragma: allowlist secret
import compose.project.click.click.util.chatMediaVaultExtensionForMessage // pragma: allowlist secret
import compose.project.click.click.util.fileUriToLocalPath // pragma: allowlist secret
import compose.project.click.click.util.isChatMediaVaultLocalPath // pragma: allowlist secret
import compose.project.click.click.util.readChatMediaVaultLocalPathForMessage // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.util.teardownBlocking // pragma: allowlist secret
import compose.project.click.click.util.writeChatMediaVaultFile // pragma: allowlist secret
import compose.project.click.click.utils.HUB_GATEKEEPER_LOCATION_CACHE_TTL_MS // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class HubChatViewModel(
    internal val hubId: String,
    internal val realtimeChannelName: String,
    internal val hubTitle: String,
    internal val currentUserId: String,
    internal val hubCategory: String = "general",
    internal val creatorId: String? = null,
    internal val hubLocationResolver: suspend () -> LocationResult? = { null },
    internal val tokenStorage: TokenStorage = createTokenStorage(),
    internal val chatApi: ChatApiClient = ChatApiClient(tokenStorage = tokenStorage),
    internal val hubLifecycleGateway: HubLifecycleGateway = ChatApiHubLifecycleGateway(chatApi),
    internal val activeHubCache: ActiveHubCache = AppDataManagerActiveHubCache,
    internal val mutationDispatcher: CoroutineDispatcher = chatMediaDispatcher,
    internal val startRealtime: Boolean = true,
    internal val loadHubDetails: Boolean = true,
    internal val realtimeSessionOverride: (suspend CoroutineScope.() -> Unit)? = null,
) : ViewModel(),
    SecureChatMediaHost {
    internal val supabase by lazy { SupabaseConfig.client }
    internal val userRepository by lazy { SupabaseRepository() }

    internal val _messages = MutableStateFlow<List<MessageWithUser>>(emptyList())
    val messages: StateFlow<List<MessageWithUser>> = _messages.asStateFlow()

    internal val _occupantCount = MutableStateFlow(1)
    val occupantCount: StateFlow<Int> = _occupantCount.asStateFlow()

    internal val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    internal val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    internal val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    internal val _outOfBounds = MutableStateFlow(false)
    val outOfBounds: StateFlow<Boolean> = _outOfBounds.asStateFlow()

    internal val _secureChatMediaLoadState = MutableStateFlow<Map<String, SecureChatMediaLoadState>>(emptyMap())
    override val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>> =
        _secureChatMediaLoadState.asStateFlow()

    internal val _realtimeState =
        MutableStateFlow<HubRealtimeState>(
            if (startRealtime) HubRealtimeState.Loading else HubRealtimeState.Ready,
        )
    val realtimeState: StateFlow<HubRealtimeState> = _realtimeState.asStateFlow()
    val channelReady: StateFlow<Boolean> =
        _realtimeState
            .map { it is HubRealtimeState.Ready }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                _realtimeState.value is HubRealtimeState.Ready,
            )

    internal val _isCreator = MutableStateFlow(creatorId != null && creatorId == currentUserId)
    val isCreator: StateFlow<Boolean> = _isCreator.asStateFlow()

    internal val _resolvedCreatorId = MutableStateFlow(creatorId)
    val resolvedCreatorId: StateFlow<String?> = _resolvedCreatorId.asStateFlow()

    internal val _hubDetails =
        MutableStateFlow(
            HubDetailsState(
                name = hubTitle,
                category = hubCategory.ifBlank { "general" },
                isCreator = creatorId != null && creatorId == currentUserId,
            ),
        )
    val hubDetails: StateFlow<HubDetailsState> = _hubDetails.asStateFlow()

    internal val navigationEventChannel = Channel<HubChatNavigationEvent>(capacity = Channel.BUFFERED)
    val navigationEvents: Flow<HubChatNavigationEvent> = navigationEventChannel.receiveAsFlow()

    internal val secureImageBytesCache =
        LruMemoryCache<String, ByteArray>(SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES)
    internal val secureAudioPathCache =
        LruMemoryCache<String, String>(SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES)

    val title: String get() = _hubDetails.value.name

    internal val senderUiCache = mutableMapOf<String, Pair<String, String?>>()
    internal var cachedGatekeeperLocation: LocationResult? = null
    internal var cachedGatekeeperLocationAtMs: Long = 0L
    internal var hubChannel: RealtimeChannel? = null
    internal var sessionJob: Job? = null
    internal var participantDenied: Boolean = false

    init {
        hydrateFromDiskCache()
        if (loadHubDetails) {
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    val rows =
                        supabase
                            .from("hub_venues")
                            .select(
                                columns =
                                    io.github.jan.supabase.postgrest.query.Columns
                                        .list("name", "category", "creator_id"),
                            ) {
                                filter { eq("id", hubId) }
                                limit(1)
                            }.decodeList<HubDetailsRow>()
                    val row = rows.firstOrNull()
                    val creator = row?.creatorId?.trim()?.takeIf { it.isNotEmpty() }
                    if (creator != null) {
                        _resolvedCreatorId.value = creator
                    }
                    val ownsHub = creator == currentUserId
                    _isCreator.value = ownsHub
                    _hubDetails.update { current ->
                        current.copy(
                            name = row?.name?.takeIf { it.isNotBlank() } ?: current.name,
                            category = row?.category?.takeIf { it.isNotBlank() } ?: current.category,
                            isCreator = ownsHub,
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }
        if (startRealtime) {
            launchRealtimeSession()
        }
        viewModelScope.launch {
            runCatching { hubLocationResolver() }.getOrNull()?.let { loc ->
                if (loc.latitude.isFinite() && loc.longitude.isFinite()) {
                    cachedGatekeeperLocation = loc
                    cachedGatekeeperLocationAtMs = Clock.System.now().toEpochMilliseconds()
                }
            }
        }
    }

    fun updateDraft(text: String) {
        _draft.value = text.take(HUB_CHAT_DRAFT_MAX_LENGTH)
    }

    fun retryRealtime() {
        if (!startRealtime) return
        launchRealtimeSession()
    }

    suspend fun ensureTargetMessageLoaded(messageId: String): Boolean {
        val id = messageId.trim()
        if (id.isEmpty()) return false
        if (_messages.value.any { it.message.id == id }) return true
        loadMessagesAround(id)
        return _messages.value.any { it.message.id == id }
    }

    internal suspend fun resolveGatekeeperLocationOrThrow(): LocationResult {
        val now = Clock.System.now().toEpochMilliseconds()
        cachedGatekeeperLocation
            ?.takeIf { now - cachedGatekeeperLocationAtMs < HUB_GATEKEEPER_LOCATION_CACHE_TTL_MS }
            ?.let { return it }

        val loc =
            hubLocationResolver()
                ?: throw IllegalStateException("Location is required to send hub messages.")
        if (!loc.latitude.isFinite() || !loc.longitude.isFinite()) {
            throw IllegalStateException("Invalid location.")
        }
        cachedGatekeeperLocation = loc
        cachedGatekeeperLocationAtMs = now
        return loc
    }

    fun sendMessage() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return

        _draft.value = ""
        _sendError.value = null
        val tempId = appendOptimisticOutgoing(text)

        viewModelScope.launch {
            try {
                val loc = resolveGatekeeperLocationOrThrow()
                val jwt =
                    tokenStorage.requireFreshHubJwt()
                val dto =
                    chatApi
                        .sendHubMessage(
                            hubId = hubId,
                            body = text,
                            userLat = loc.latitude,
                            userLong = loc.longitude,
                            authToken = jwt,
                            messageType = ChatMessageType.TEXT,
                            metadata = null,
                        ).getOrElse { e -> throw e }
                applyInsertedHubMessage(
                    serverMessage =
                        rowToMessageWithUser(
                            HubMessageRow(
                                id = dto.id,
                                hubId = dto.hubId,
                                userId = dto.userId,
                                body = dto.body,
                                createdAt = dto.createdAt,
                                messageType = dto.messageType,
                                metadata = dto.metadata,
                            ),
                        ).message,
                    optimisticTempId = tempId,
                )
            } catch (e: Exception) {
                markOptimisticSendFailed(tempId)
                _draft.value = text
                if (isHubExpired(e)) {
                    _sendError.value = HUB_EXPIRED_MESSAGE
                } else if (isHubOutOfRange(e)) {
                    _outOfBounds.value = true
                    _sendError.value = HUB_OUT_OF_RANGE_MESSAGE
                } else {
                    _sendError.value = e.redactedRestMessage().ifBlank { "Could not send" }
                }
            }
        }
    }

    /**
     * Encrypt with hub broadcast key, upload ciphertext via gatekeeper, then insert image message.
     */
    fun sendHubImageFromPicker(
        imageBytes: ByteArray,
        mimeType: String,
    ) {
        if (imageBytes.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            try {
                val loc = resolveGatekeeperLocationOrThrow()
                val jwt =
                    tokenStorage.requireFreshHubJwt()
                val keys = MessageCrypto.deriveKeysForHub(hubId)
                val cipher = MessageCrypto.encryptMediaBytes(imageBytes, keys)
                val leaf = randomHubMediaLeaf()
                val objectPath = "$currentUserId/hub/$hubId/$leaf.bin"
                val path =
                    chatApi
                        .uploadHubMedia(
                            fileBytes = cipher,
                            hubId = hubId,
                            mimeType = "application/octet-stream",
                            objectPath = objectPath,
                            authToken = jwt,
                            userLat = loc.latitude,
                            userLong = loc.longitude,
                        ).getOrElse { e -> throw e }
                val publicUrl = supabase.storage.from(CHAT_MEDIA_BUCKET).publicUrl(path)
                val metadata: JsonObject =
                    buildJsonObject {
                        put("media_url", JsonPrimitive(publicUrl))
                        put("is_encrypted_media", JsonPrimitive(true))
                        put("original_mime_type", JsonPrimitive(mimeType.ifBlank { "image/jpeg" }))
                    }
                chatApi
                    .sendHubMessage(
                        hubId = hubId,
                        body = "Photo",
                        userLat = loc.latitude,
                        userLong = loc.longitude,
                        authToken = jwt,
                        messageType = ChatMessageType.IMAGE,
                        metadata = metadata,
                    ).getOrElse { e -> throw e }
            } catch (e: Exception) {
                if (isHubExpired(e)) {
                    _sendError.value = HUB_EXPIRED_MESSAGE
                } else if (isHubOutOfRange(e)) {
                    _outOfBounds.value = true
                    _sendError.value = HUB_OUT_OF_RANGE_MESSAGE
                } else {
                    _sendError.value = e.redactedRestMessage().ifBlank { "Could not send image" }
                }
            } finally {
                _isSending.value = false
            }
        }
    }

    override fun ensureSecureChatImageLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    ) {
        if (scopeId != hubId) return
        if (!message.isEncryptedMedia()) return
        if (message.messageType.lowercase() != ChatMessageType.IMAGE) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank()) return
        val cachedBytes = secureImageBytesCache.get(message.id)
        if (cachedBytes != null && cachedBytes.isNotEmpty()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = cachedBytes))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.imageBytes != null || cur?.loading == true) return
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            val bytes =
                runCatching {
                    val raw = chatApi.downloadUrlBytes(url).getOrElse { return@runCatching null }
                    val normalized = normalizeEncryptedMediaPayload(raw)
                    if (normalized !== raw) {
                        println("HubChatViewModel: decoded base64-wrapped encrypted image payload for message=${message.id}")
                    }
                    MessageCrypto.decryptMediaBytes(normalized, MessageCrypto.deriveKeysForHub(hubId))
                }.onFailure { e ->
                    println("HubChatViewModel: secure image decrypt failed for message=${message.id}: ${e.redactedRestMessage()}")
                }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                println("HubChatViewModel: secure image bytes missing for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not load image"))
                }
            } else {
                secureImageBytesCache.put(message.id, bytes)
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, imageBytes = bytes))
                }
            }
        }
    }

    override fun ensureSecureChatAudioLoaded(
        scopeId: String,
        viewerUserId: String,
        message: Message,
    ) {
        if (scopeId != hubId) return
        if (message.messageType.lowercase() != ChatMessageType.AUDIO) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank() && !message.hasLocalMediaUri()) return
        val extension = chatMediaVaultExtensionForMessage(message)
        val cachedPath = secureAudioPathCache.get(message.id)
        if (!cachedPath.isNullOrBlank()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = cachedPath))
            }
            return
        }
        readChatMediaVaultLocalPathForMessage(
            messageId = message.id,
            preferredExtension = extension,
            mediaUrl = message.mediaUrlOrNull(),
        )?.let { localPath ->
            secureAudioPathCache.put(message.id, localPath)
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = localPath))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.audioLocalPath != null || cur?.loading == true) return
        if (!message.isEncryptedMedia()) return
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            val bytes =
                runCatching {
                    val raw = chatApi.downloadUrlBytes(url).getOrElse { return@runCatching null }
                    val normalized = normalizeEncryptedMediaPayload(raw)
                    if (normalized !== raw) {
                        println("HubChatViewModel: decoded base64-wrapped encrypted audio payload for message=${message.id}")
                    }
                    MessageCrypto.decryptMediaBytes(normalized, MessageCrypto.deriveKeysForHub(hubId))
                }.onFailure { e ->
                    println("HubChatViewModel: secure audio decrypt failed for message=${message.id}: ${e.redactedRestMessage()}")
                }.getOrNull()
            if (bytes == null || bytes.isEmpty()) {
                println("HubChatViewModel: secure audio bytes missing for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not load audio"))
                }
                return@launch
            }
            val path =
                writeChatMediaVaultFile(message.id, bytes, message.audioCacheFileExtension())
                    ?.let { fileUriToLocalPath(it) }
                    ?: writeSecureChatAudioTempFile(message.id, bytes, message.audioCacheFileExtension())
            if (path.isNullOrBlank()) {
                println("HubChatViewModel: secure audio cache write failed for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not cache audio"))
                }
            } else {
                val evictedPath = secureAudioPathCache.put(message.id, path)
                if (!evictedPath.isNullOrBlank() && evictedPath != path && !isChatMediaVaultLocalPath(evictedPath)) {
                    deleteSecureChatAudioTempFile(evictedPath)
                }
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = path))
                }
            }
        }
    }

    fun editHubDetails(
        name: String,
        category: String,
        onResult: (Boolean) -> Unit = {},
    ) {
        val nextName = name.trim().take(80)
        val nextCategory = category.trim().take(40)
        if (nextName.isEmpty() || nextCategory.isEmpty()) {
            _sendError.value = "Hub name and category are required"
            onResult(false)
            return
        }
        viewModelScope.launch(mutationDispatcher) {
            try {
                val jwt =
                    tokenStorage.requireFreshHubJwt()
                hubLifecycleGateway
                    .updateHub(
                        hubId = hubId,
                        name = nextName,
                        category = nextCategory,
                        authToken = jwt,
                    ).getOrThrow()
                _hubDetails.update { it.copy(name = nextName, category = nextCategory) }
                onResult(true)
            } catch (e: Exception) {
                _sendError.value = e.redactedRestMessage().ifBlank { "Could not update hub" }
                onResult(false)
            }
        }
    }

    fun leaveHub(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(mutationDispatcher) {
            try {
                val jwt =
                    tokenStorage.requireFreshHubJwt()
                hubLifecycleGateway
                    .leaveHub(
                        hubId = hubId,
                        authToken = jwt,
                    ).getOrThrow()
                clearLocalHubState()
                navigationEventChannel.send(HubChatNavigationEvent.PopBackToConnections)
                onResult(true)
            } catch (e: Exception) {
                _sendError.value = e.redactedRestMessage().ifBlank { "Could not leave hub" }
                onResult(false)
            }
        }
    }

    fun deleteHub(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch(mutationDispatcher) {
            try {
                val jwt =
                    tokenStorage.requireFreshHubJwt()
                hubLifecycleGateway
                    .deleteHub(
                        hubId = hubId,
                        authToken = jwt,
                    ).getOrThrow()
                AppDataManager.dismissCommunityHub(hubId)
                clearLocalHubState(clearDiskCache = true)
                navigationEventChannel.send(HubChatNavigationEvent.PopBackToConnections)
                onResult(true)
            } catch (e: Exception) {
                _sendError.value = e.redactedRestMessage().ifBlank { "Could not delete hub" }
                onResult(false)
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        sessionJob = null
        clearHubSecureMediaCache(purgePersistentCache = true)
        val ch = hubChannel
        hubChannel = null
        super.onCleared()
        if (ch != null) {
            // Bounded, off-main teardown: viewModelScope is dead, main-thread
            // blocking is an ANR risk — use the shared helper (≤500 ms, Default).
            teardownBlocking {
                runCatching { ch.untrack() }
                runCatching { ch.unsubscribe() }
            }
        }
    }

    companion object {
        /** Shown when the geofence rejects a send (out of bounds). */
        const val HUB_OUT_OF_RANGE_MESSAGE = "No longer near hub. Move closer to send a message."

        /** Shown when the hub venue has an expires_at in the past (legacy / admin-set). */
        const val HUB_EXPIRED_MESSAGE = "This hub is no longer active."

        /** Maps the gatekeeper rejection markers surfaced by [ChatApiClient] into the user-facing state. */
        fun isHubOutOfRange(e: Throwable): Boolean {
            val msg = e.message ?: return false
            return msg.contains("OUT_OF_BOUNDS") || msg.contains("HUB_OUT_OF_RANGE")
        }

        fun isHubExpired(e: Throwable): Boolean {
            val msg = e.message ?: return false
            return msg.contains("HUB_EXPIRED")
        }
    }

    internal suspend fun CoroutineScope.runRealtimeSession() {
        prepareHubRealtimeAuth()
        val channel =
            supabase.channel(realtimeChannelName) {
                presence {
                    key = currentUserId
                }
            }
        hubChannel = channel

        val hubMessageChanges =
            channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "hub_messages"
            }

        val occupantKeys = mutableSetOf<String>()

        fun recomputeOccupants() {
            val n = occupantKeys.size.coerceAtLeast(1)
            _occupantCount.value = n
        }

        val presenceJob =
            launch(context = Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                try {
                    channel.presenceChangeFlow().collect { action ->
                        action.leaves.keys.forEach { occupantKeys.remove(it) }
                        action.joins.keys.forEach { occupantKeys.add(it) }
                        action.joins.values.forEach { p ->
                            userIdFromPresence(p)?.let { occupantKeys.add(it) }
                        }
                        action.leaves.values.forEach { p ->
                            userIdFromPresence(p)?.let { occupantKeys.remove(it) }
                        }
                        recomputeOccupants()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }

        withContext(Dispatchers.Default) {
            if (!channel.subscribeWithTimeout()) { // pragma: allowlist secret
                presenceJob.cancel()
                runCatching { channel.unsubscribe() }
                throw IllegalStateException("Couldn't connect to this hub")
            }
        }
        if (!participantDenied) {
            _realtimeState.value = HubRealtimeState.Ready
        }
        channel.track(buildJsonObject { put("userId", currentUserId) })
        occupantKeys.add(currentUserId)
        recomputeOccupants()

        val refreshJob =
            launch {
                while (isActive) {
                    delay(25_000L)
                    runCatching {
                        channel.track(buildJsonObject { put("userId", currentUserId) })
                    }
                }
            }

        try {
            hubMessageChanges.collect { action ->
                when (action) {
                    is PostgresAction.Insert -> {
                        val row = action.decodeRecordOrNull<HubMessageRow>() ?: return@collect
                        if (row.hubId != hubId) return@collect
                        if (row.userId != currentUserId && !senderUiCache.containsKey(row.userId)) {
                            prefetchSenderUi(listOf(row.userId))
                        }
                        applyInsertedHubMessage(rowToMessageWithUser(row).message)
                    }
                    is PostgresAction.Update -> {
                        val row = action.decodeRecordOrNull<HubMessageRow>() ?: return@collect
                        if (row.hubId != hubId) return@collect
                        val current = _messages.value
                        val idx = current.indexOfFirst { it.message.id == row.id }
                        if (idx >= 0) {
                            val existing = current[idx].message
                            val refreshed = rowToMessageWithUser(row)
                            val preservedMessage =
                                refreshed.message.copy(
                                    localSentAt = refreshed.message.localSentAt ?: existing.localSentAt,
                                    deliveryState =
                                        if (existing.deliveryState == MessageDeliveryState.PENDING) {
                                            MessageDeliveryState.SENT
                                        } else {
                                            refreshed.message.deliveryState
                                        },
                                )
                            val next =
                                current.toMutableList().also {
                                    it[idx] = refreshed.copy(message = preservedMessage)
                                }
                            _messages.value = next
                            persistHubMessagesToDisk(next)
                        }
                    }
                    is PostgresAction.Delete -> {
                        val deletedId = action.oldRecord.hubMessageRowId() ?: return@collect
                        val current = _messages.value
                        if (current.any { it.message.id == deletedId }) {
                            val next = current.filterNot { it.message.id == deletedId }
                            _messages.value = next
                            persistHubMessagesToDisk(next)
                        }
                    }
                    else -> Unit
                }
            }
        } finally {
            refreshJob.cancel()
            presenceJob.cancel()
            val ch = hubChannel
            hubChannel = null
            if (ch != null) {
                runCatching { ch.untrack() }
                runCatching { ch.unsubscribe() }
            }
        }
    }
}

private const val SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES = 160
private const val SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES = 80
