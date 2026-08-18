package compose.project.click.click.viewmodel // pragma: allowlist secret

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.crypto.MessageCrypto // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.CHAT_MEDIA_BUCKET // pragma: allowlist secret
import compose.project.click.click.data.SupabaseConfig // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.auth.EnsureFreshAccessToken // pragma: allowlist secret
import compose.project.click.click.data.realtime.subscribeWithTimeout // pragma: allowlist secret
import compose.project.click.click.data.models.ChatMessageType // pragma: allowlist secret
import compose.project.click.click.data.models.Message // pragma: allowlist secret
import compose.project.click.click.data.models.MessageDeliveryState // pragma: allowlist secret
import compose.project.click.click.data.models.MessageWithUser // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.audioCacheFileExtension // pragma: allowlist secret
import compose.project.click.click.data.models.hasLocalMediaUri // pragma: allowlist secret
import compose.project.click.click.data.models.isEncryptedMedia // pragma: allowlist secret
import compose.project.click.click.data.models.mediaUrlOrNull // pragma: allowlist secret
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
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
import kotlinx.coroutines.coroutineScope
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
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

private const val HUB_CHAT_DRAFT_MAX_LENGTH = 1000

/** Server + client send spacing for community hub messages (seconds). */
const val HUB_MESSAGE_COOLDOWN_SECONDS = 5

@Serializable
private data class HubDetailsRow(
    val name: String? = null,
    val category: String? = null,
    @SerialName("creator_id") val creatorId: String,
)

data class HubDetailsState(
    val name: String,
    val category: String,
    val isCreator: Boolean,
)

sealed interface HubChatNavigationEvent {
    data object PopBackToConnections : HubChatNavigationEvent
}

sealed interface HubRealtimeState {
    data object Loading : HubRealtimeState

    data object Ready : HubRealtimeState

    data class Error(
        val message: String,
    ) : HubRealtimeState
}

interface HubLifecycleGateway {
    suspend fun updateHub(
        hubId: String,
        name: String,
        category: String,
        authToken: String,
    ): Result<Unit>

    suspend fun deleteHub(
        hubId: String,
        authToken: String,
    ): Result<Unit>

    suspend fun leaveHub(
        hubId: String,
        authToken: String,
    ): Result<Unit>
}

private class ChatApiHubLifecycleGateway(
    private val chatApi: ChatApiClient,
) : HubLifecycleGateway {
    override suspend fun updateHub(
        hubId: String,
        name: String,
        category: String,
        authToken: String,
    ): Result<Unit> =
        chatApi.updateHub(
            hubId = hubId,
            name = name,
            category = category,
            authToken = authToken,
        )

    override suspend fun deleteHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = chatApi.deleteHub(hubId = hubId, authToken = authToken)

    override suspend fun leaveHub(
        hubId: String,
        authToken: String,
    ): Result<Unit> = chatApi.leaveHub(hubId = hubId, authToken = authToken)
}

interface ActiveHubCache {
    fun removeActiveHub(hubId: String)
}

private object AppDataManagerActiveHubCache : ActiveHubCache {
    override fun removeActiveHub(hubId: String) {
        AppDataManager.removeActiveHub(hubId)
    }
}

@Serializable
private data class HubMessageRow(
    val id: String,
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("message_type") val messageType: String = ChatMessageType.TEXT,
    val metadata: JsonElement? = null,
)

/** Extract the `id` column out of a realtime `oldRecord` JsonObject (DELETE payloads carry PKs only). */
private fun JsonObject.hubMessageRowId(): String? = (this["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun hubCreatedAtToEpoch(iso: String): Long {
    val t = iso.trim().replace(" ", "T")
    return runCatching { Instant.parse(t) }.getOrNull()?.toEpochMilliseconds()
        ?: Clock.System.now().toEpochMilliseconds()
}

private fun randomHubMediaLeaf(): String =
    buildString(20) {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        repeat(20) { append(alphabet[Random.nextInt(alphabet.length)]) }
    }

private const val HUB_INITIAL_MESSAGE_LIMIT = 120L

private suspend fun TokenStorage.requireFreshHubJwt(): String {
    val fresh =
        runCatching { EnsureFreshAccessToken.get(this) }
            .getOrNull()
            ?.trim()
            .orEmpty()
    if (fresh.isNotEmpty()) return fresh
    val stored = getJwt()?.trim().orEmpty()
    if (stored.isNotEmpty()) return stored
    throw IllegalStateException("Please sign in again.")
}

class HubChatViewModel(
    private val hubId: String,
    private val realtimeChannelName: String,
    private val hubTitle: String,
    private val currentUserId: String,
    private val hubCategory: String = "general",
    private val creatorId: String? = null,
    private val hubLocationResolver: suspend () -> LocationResult? = { null },
    private val tokenStorage: TokenStorage = createTokenStorage(),
    private val chatApi: ChatApiClient = ChatApiClient(tokenStorage = tokenStorage),
    private val hubLifecycleGateway: HubLifecycleGateway = ChatApiHubLifecycleGateway(chatApi),
    private val activeHubCache: ActiveHubCache = AppDataManagerActiveHubCache,
    private val mutationDispatcher: CoroutineDispatcher = chatMediaDispatcher,
    private val startRealtime: Boolean = true,
    private val loadHubDetails: Boolean = true,
    private val realtimeSessionOverride: (suspend CoroutineScope.() -> Unit)? = null,
) : ViewModel(),
    SecureChatMediaHost {
    private val supabase by lazy { SupabaseConfig.client }
    private val userRepository by lazy { SupabaseRepository() }

    private val _messages = MutableStateFlow<List<MessageWithUser>>(emptyList())
    val messages: StateFlow<List<MessageWithUser>> = _messages.asStateFlow()

    private val _occupantCount = MutableStateFlow(1)
    val occupantCount: StateFlow<Int> = _occupantCount.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _sendCooldownRemainingSec = MutableStateFlow(0)
    val sendCooldownRemainingSec: StateFlow<Int> = _sendCooldownRemainingSec.asStateFlow()

    private val _outOfBounds = MutableStateFlow(false)
    val outOfBounds: StateFlow<Boolean> = _outOfBounds.asStateFlow()

    private var sendCooldownTickerJob: Job? = null

    private val _secureChatMediaLoadState = MutableStateFlow<Map<String, SecureChatMediaLoadState>>(emptyMap())
    override val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>> =
        _secureChatMediaLoadState.asStateFlow()

    private val _realtimeState =
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

    private val _isCreator = MutableStateFlow(creatorId != null && creatorId == currentUserId)
    val isCreator: StateFlow<Boolean> = _isCreator.asStateFlow()

    private val _resolvedCreatorId = MutableStateFlow(creatorId)
    val resolvedCreatorId: StateFlow<String?> = _resolvedCreatorId.asStateFlow()

    private val _hubDetails =
        MutableStateFlow(
            HubDetailsState(
                name = hubTitle,
                category = hubCategory.ifBlank { "general" },
                isCreator = creatorId != null && creatorId == currentUserId,
            ),
        )
    val hubDetails: StateFlow<HubDetailsState> = _hubDetails.asStateFlow()

    private val navigationEventChannel = Channel<HubChatNavigationEvent>(capacity = Channel.BUFFERED)
    val navigationEvents: Flow<HubChatNavigationEvent> = navigationEventChannel.receiveAsFlow()

    private val secureImageBytesCache =
        LruMemoryCache<String, ByteArray>(SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES)
    private val secureAudioPathCache =
        LruMemoryCache<String, String>(SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES)

    val title: String get() = _hubDetails.value.name

    private val senderUiCache = mutableMapOf<String, Pair<String, String?>>()
    private var cachedGatekeeperLocation: LocationResult? = null
    private var cachedGatekeeperLocationAtMs: Long = 0L
    private var hubChannel: RealtimeChannel? = null
    private var sessionJob: Job? = null
    private var participantDenied: Boolean = false

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

    private fun launchRealtimeSession() {
        sessionJob?.cancel()
        participantDenied = false
        _realtimeState.value = HubRealtimeState.Loading
        sessionJob =
            viewModelScope.launch {
                try {
                    coroutineScope {
                        val override = realtimeSessionOverride
                        val realtimeJob =
                            launch {
                                if (override != null) {
                                    override()
                                    _realtimeState.value = HubRealtimeState.Ready
                                } else {
                                    runRealtimeSession()
                                }
                            }
                        if (override == null) {
                            loadInitialMessages()
                        }
                        realtimeJob.join()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _realtimeState.value =
                        HubRealtimeState.Error(
                            e.message?.takeIf { it.isNotBlank() } ?: "Couldn't connect to this hub",
                        )
                    println("HubChatViewModel: session error: ${e.redactedRestMessage()}")
                }
            }
    }

    private fun userIdFromPresence(p: Presence): String? {
        fun fromObject(obj: JsonObject): String? {
            val el = obj["userId"] ?: obj["user_id"] ?: return null
            return (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }
        fromObject(p.state)?.let { return it }
        val nested = p.state["state"]?.let { it as? JsonObject } ?: return null
        return fromObject(nested)
    }

    private suspend fun prefetchSenderUi(userIds: Collection<String>) {
        val missing =
            userIds
                .filter { it != currentUserId && !senderUiCache.containsKey(it) }
                .distinct()
        if (missing.isEmpty()) return
        userRepository.fetchUsersByIds(missing).forEach { user ->
            val label = user.name?.takeIf { it.isNotBlank() } ?: "Member"
            val avatar = user.image?.trim()?.takeIf { it.isNotEmpty() }
            senderUiCache[user.id] = label to avatar
        }
    }

    private fun rowToMessageWithUser(row: HubMessageRow): MessageWithUser {
        val mine = row.userId == currentUserId
        val (label, avatar) =
            if (mine) {
                "You" to null
            } else {
                senderUiCache[row.userId] ?: ("Member" to null)
            }
        val message =
            Message(
                id = row.id,
                user_id = row.userId,
                content = row.body,
                timeCreated = hubCreatedAtToEpoch(row.createdAt),
                timeEdited = null,
                isRead = false,
                messageType = row.messageType,
                metadata = row.metadata,
            )
        val user =
            if (mine) {
                User(id = row.userId, name = "You", image = null, createdAt = 0L)
            } else {
                User(id = row.userId, name = label, image = avatar, createdAt = 0L)
            }
        return MessageWithUser(message = message, user = user, isSent = mine)
    }

    private fun messageWithUserFromCached(
        message: Message,
        participants: List<User>,
    ): MessageWithUser {
        val mine = message.user_id == currentUserId
        val participant = participants.firstOrNull { it.id == message.user_id }
        val (label, avatar) =
            when {
                mine -> "You" to null
                participant != null -> (participant.name?.takeIf { it.isNotBlank() } ?: "Member") to participant.image
                else -> senderUiCache[message.user_id] ?: ("Member" to null)
            }
        val user =
            if (mine) {
                User(id = message.user_id, name = "You", image = null, createdAt = 0L)
            } else {
                User(id = message.user_id, name = label, image = avatar, createdAt = 0L)
            }
        return MessageWithUser(message = message, user = user, isSent = mine)
    }

    private fun hydrateFromDiskCache() {
        val cached = AppDataManager.cachedHubThreadFor(hubId) ?: return
        if (cached.messages.isEmpty()) return
        cached.participants.forEach { user ->
            if (user.id != currentUserId) {
                val label = user.name?.takeIf { it.isNotBlank() } ?: "Member"
                val avatar = user.image?.trim()?.takeIf { it.isNotEmpty() }
                senderUiCache[user.id] = label to avatar
            }
        }
        _messages.value = cached.messages.map { messageWithUserFromCached(it, cached.participants) }
    }

    private fun persistHubMessagesToDisk(messages: List<MessageWithUser>) {
        if (messages.isEmpty()) return
        AppDataManager.cacheHubThread(
            hubId = hubId,
            realtimeChannel = realtimeChannelName,
            messages = messages.map { it.message },
            participants = messages.map { it.user }.distinctBy { it.id },
        )
    }

    private fun pendingOptimisticOutgoing(serverMessages: List<MessageWithUser>): List<MessageWithUser> {
        return _messages.value.filter { mwu ->
            val message = mwu.message
            if (!message.id.startsWith("temp-") || message.user_id != currentUserId) return@filter false
            if (message.deliveryState != MessageDeliveryState.PENDING) return@filter false
            val stamp = message.localSentAt
            if (stamp != null &&
                serverMessages.any { s ->
                    s.message.user_id == message.user_id && s.message.localSentAt == stamp
                }
            ) {
                return@filter false
            }
            !serverMessages.any { s ->
                s.message.user_id == message.user_id && s.message.content == message.content
            }
        }
    }

    private fun stripOptimisticMatchingServerRow(
        messages: List<MessageWithUser>,
        serverMessage: Message,
    ): List<MessageWithUser> {
        val stamp = serverMessage.localSentAt
        return messages.filterNot { mwu ->
            mwu.message.id.startsWith("temp-") &&
                mwu.message.user_id == serverMessage.user_id &&
                stamp != null &&
                mwu.message.localSentAt == stamp
        }
    }

    private fun findPendingOptimisticTempId(
        messages: List<MessageWithUser>,
        serverMessage: Message,
    ): String? {
        if (serverMessage.user_id != currentUserId) return null
        serverMessage.localSentAt?.let { stamp ->
            messages
                .firstOrNull { mwu ->
                    mwu.message.id.startsWith("temp-") &&
                        mwu.message.user_id == currentUserId &&
                        mwu.message.localSentAt == stamp
                }?.message
                ?.id
                ?.let { return it }
        }
        return messages
            .lastOrNull { mwu ->
                mwu.message.id.startsWith("temp-") &&
                    mwu.message.user_id == currentUserId &&
                    mwu.message.messageType == serverMessage.messageType &&
                    mwu.message.deliveryState == MessageDeliveryState.PENDING &&
                    mwu.message.content == serverMessage.content
            }?.message
            ?.id
    }

    private fun resolveInsertedMessage(
        serverMessage: Message,
        messages: List<MessageWithUser>,
        tempId: String?,
    ): Message {
        if (serverMessage.localSentAt != null) {
            return serverMessage.copy(deliveryState = MessageDeliveryState.SENT)
        }
        val optimistic = tempId?.let { id -> messages.find { it.message.id == id }?.message }
        val stamp = optimistic?.localSentAt ?: return serverMessage.copy(deliveryState = MessageDeliveryState.SENT)
        return serverMessage.copy(localSentAt = stamp, deliveryState = MessageDeliveryState.SENT)
    }

    private fun applyInsertedHubMessage(
        serverMessage: Message,
        optimisticTempId: String? = null,
    ) {
        val current = _messages.value
        val tempIdToReplace =
            optimisticTempId
                ?: findPendingOptimisticTempId(current, serverMessage)
        val mergedMessage = resolveInsertedMessage(serverMessage, current, tempIdToReplace)
        val user =
            if (mergedMessage.user_id == currentUserId) {
                User(id = currentUserId, name = "You", image = null, createdAt = 0L)
            } else {
                val (label, avatar) = senderUiCache[mergedMessage.user_id] ?: ("Member" to null)
                User(id = mergedMessage.user_id, name = label, image = avatar, createdAt = 0L)
            }

        if (tempIdToReplace != null) {
            val idx = current.indexOfFirst { it.message.id == tempIdToReplace }
            if (idx >= 0) {
                val replaced = current.toMutableList()
                replaced[idx] =
                    MessageWithUser(
                        message = mergedMessage,
                        user = user,
                        isSent = mergedMessage.user_id == currentUserId,
                    )
                _messages.value = replaced
                persistHubMessagesToDisk(replaced)
                return
            }
        }

        val baseList = stripOptimisticMatchingServerRow(current, mergedMessage)
        val existingIdx = baseList.indexOfFirst { it.message.id == mergedMessage.id }
        if (existingIdx >= 0) {
            val updated = baseList.toMutableList()
            val prior = updated[existingIdx].message
            updated[existingIdx] =
                MessageWithUser(
                    message =
                        mergedMessage.copy(
                            localSentAt = mergedMessage.localSentAt ?: prior.localSentAt,
                        ),
                    user = user,
                    isSent = mergedMessage.user_id == currentUserId,
                )
            _messages.value = updated
            persistHubMessagesToDisk(updated)
            return
        }

        val next =
            baseList +
                MessageWithUser(
                    message = mergedMessage,
                    user = user,
                    isSent = mergedMessage.user_id == currentUserId,
                )
        _messages.value = next
        persistHubMessagesToDisk(next)
    }

    private fun markOptimisticSendFailed(tempId: String) {
        val next =
            _messages.value.map { mwu ->
                if (mwu.message.id == tempId) {
                    mwu.copy(message = mwu.message.copy(deliveryState = MessageDeliveryState.ERROR))
                } else {
                    mwu
                }
            }
        _messages.value = next
        persistHubMessagesToDisk(next)
    }

    private fun appendOptimisticOutgoing(text: String): String {
        val localMs = Clock.System.now().toEpochMilliseconds()
        val tempId = "temp-$localMs-${Random.nextLong()}"
        val optimistic =
            MessageWithUser(
                message =
                    Message(
                        id = tempId,
                        user_id = currentUserId,
                        content = text,
                        timeCreated = localMs,
                        timeEdited = null,
                        isRead = false,
                        messageType = ChatMessageType.TEXT,
                        metadata = null,
                        localSentAt = localMs,
                        deliveryState = MessageDeliveryState.PENDING,
                    ),
                user = User(id = currentUserId, name = "You", image = null, createdAt = 0L),
                isSent = true,
            )
        val next = _messages.value + optimistic
        _messages.value = next
        persistHubMessagesToDisk(next)
        return tempId
    }

    private suspend fun mergeMessages(rows: List<HubMessageRow>) {
        val filtered =
            rows
                .filter { it.hubId == hubId }
                .sortedBy { it.createdAt }
        prefetchSenderUi(filtered.map { it.userId })
        val merged = filtered.map { rowToMessageWithUser(it) }
        val next = merged + pendingOptimisticOutgoing(merged)
        _messages.value = next
        persistHubMessagesToDisk(next)
    }

    private fun clearHubSecureMediaCache(purgePersistentCache: Boolean = false) {
        _secureChatMediaLoadState.value = emptyMap()
        if (purgePersistentCache) {
            secureAudioPathCache.valuesSnapshot().forEach { path ->
                deleteSecureChatAudioTempFile(path)
            }
            secureAudioPathCache.clear()
            secureImageBytesCache.clear()
        }
    }

    private fun clearLocalHubState(clearDiskCache: Boolean = false) {
        sessionJob?.cancel()
        sessionJob = null
        _messages.value = emptyList()
        _draft.value = ""
        _occupantCount.value = 1
        _outOfBounds.value = false
        clearHubSecureMediaCache(purgePersistentCache = true)
        activeHubCache.removeActiveHub(hubId)
        if (clearDiskCache) {
            AppDataManager.clearHubThreadCache(hubId)
        }
    }

    private suspend fun prepareHubRealtimeAuth() {
        if (supabase.auth.currentSessionOrNull() == null) {
            runCatching { SupabaseConfig.importStoredSessionIfSdkEmpty(tokenStorage) }
        }
        tokenStorage.requireFreshHubJwt()
        runCatching { supabase.realtime.connect() }
    }

    private suspend fun CoroutineScope.runRealtimeSession() {
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

    private fun ChatApiClient.HubMessageApiDto.toHubMessageRow(): HubMessageRow =
        HubMessageRow(
            id = id,
            hubId = hubId,
            userId = userId,
            body = body,
            createdAt = createdAt,
            messageType = messageType,
            metadata = metadata,
        )

    private suspend fun loadInitialMessages() {
        withContext(Dispatchers.Default) {
            try {
                val token = tokenStorage.requireFreshHubJwt()
                val thread = chatApi.fetchHubThread(hubId, token)
                thread.fold(
                    onSuccess = { snapshot ->
                        if (snapshot.occupantCount > 0) {
                            _occupantCount.value = snapshot.occupantCount.coerceAtLeast(1)
                        }
                        prefetchSenderUi(snapshot.participantIds)
                        mergeMessages(snapshot.messages.map { it.toHubMessageRow() })
                        return@withContext
                    },
                    onFailure = { err ->
                        val msg = err.message.orEmpty()
                        if (msg.contains("NOT_A_PARTICIPANT")) {
                            participantDenied = true
                            _realtimeState.value =
                                HubRealtimeState.Error("Join this hub from the map to chat")
                            return@withContext
                        }
                        println("HubChatViewModel: hub thread API failed: ${err.redactedRestMessage()}")
                    },
                )
                val rows =
                    supabase
                        .from("hub_messages")
                        .select {
                            filter {
                                eq("hub_id", hubId)
                            }
                            order("created_at", Order.DESCENDING)
                            limit(HUB_INITIAL_MESSAGE_LIMIT)
                        }.decodeList<HubMessageRow>()
                        .asReversed()
                mergeMessages(rows)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("HubChatViewModel: load messages failed: ${e.redactedRestMessage()}")
            }
        }
    }

    suspend fun ensureTargetMessageLoaded(messageId: String): Boolean {
        val id = messageId.trim()
        if (id.isEmpty()) return false
        if (_messages.value.any { it.message.id == id }) return true
        loadMessagesAround(id)
        return _messages.value.any { it.message.id == id }
    }

    private suspend fun loadMessagesAround(messageId: String) {
        withContext(Dispatchers.Default) {
            try {
                val token = runCatching { tokenStorage.requireFreshHubJwt() }.getOrNull()
                if (!token.isNullOrBlank()) {
                    val thread = chatApi.fetchHubThread(hubId, token, aroundMessageId = messageId)
                    thread.getOrNull()?.let { snapshot ->
                        prefetchSenderUi(snapshot.participantIds)
                        mergeMessages(snapshot.messages.map { it.toHubMessageRow() })
                        if (_messages.value.any { it.message.id == messageId }) return@withContext
                    }
                }
                val target =
                    supabase
                        .from("hub_messages")
                        .select {
                            filter {
                                eq("hub_id", hubId)
                                eq("id", messageId)
                            }
                            limit(1)
                        }.decodeList<HubMessageRow>()
                        .firstOrNull() ?: return@withContext
                val older =
                    supabase
                        .from("hub_messages")
                        .select {
                            filter {
                                eq("hub_id", hubId)
                                lte("created_at", target.createdAt)
                            }
                            order("created_at", Order.DESCENDING)
                            limit(HUB_INITIAL_MESSAGE_LIMIT)
                        }.decodeList<HubMessageRow>()
                        .asReversed()
                val newer =
                    supabase
                        .from("hub_messages")
                        .select {
                            filter {
                                eq("hub_id", hubId)
                                gt("created_at", target.createdAt)
                            }
                            order("created_at", Order.ASCENDING)
                            limit(40)
                        }.decodeList<HubMessageRow>()
                mergeMessages((older + newer + listOf(target)).distinctBy { it.id })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("HubChatViewModel: load around $messageId failed: ${e.redactedRestMessage()}")
            }
        }
    }

    private suspend fun resolveGatekeeperLocationOrThrow(): LocationResult {
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
        if (_sendCooldownRemainingSec.value > 0) {
            _sendError.value = "Please wait ${_sendCooldownRemainingSec.value}s before sending again."
            return
        }

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
                armSendCooldown(HUB_MESSAGE_COOLDOWN_SECONDS)
            } catch (e: Exception) {
                markOptimisticSendFailed(tempId)
                _draft.value = text
                val cooldownSec = parseHubCooldownSeconds(e)
                if (cooldownSec != null) {
                    armSendCooldown(cooldownSec)
                    _sendError.value = "Please wait ${cooldownSec}s before sending again."
                } else if (isHubExpired(e)) {
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
        if (_sendCooldownRemainingSec.value > 0) {
            _sendError.value = "Please wait ${_sendCooldownRemainingSec.value}s before sending again."
            return
        }
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
                armSendCooldown(HUB_MESSAGE_COOLDOWN_SECONDS)
            } catch (e: Exception) {
                val cooldownSec = parseHubCooldownSeconds(e)
                if (cooldownSec != null) {
                    armSendCooldown(cooldownSec)
                    _sendError.value = "Please wait ${cooldownSec}s before sending again."
                } else if (isHubExpired(e)) {
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

    private fun sendCooldownRemainingSecCeil(endEpochMs: Long): Int =
        ((endEpochMs - Clock.System.now().toEpochMilliseconds() + 999L) / 1000L).toInt().coerceAtLeast(0)

    private fun armSendCooldown(seconds: Int) {
        val secs = seconds.coerceAtLeast(1)
        sendCooldownTickerJob?.cancel()
        val end = Clock.System.now().toEpochMilliseconds() + secs * 1000L
        _sendCooldownRemainingSec.value = sendCooldownRemainingSecCeil(end)
        sendCooldownTickerJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(1_000L)
                    val rem = sendCooldownRemainingSecCeil(end)
                    _sendCooldownRemainingSec.value = rem
                    if (rem <= 0) break
                }
                _sendCooldownRemainingSec.value = 0
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
        sendCooldownTickerJob?.cancel()
        sendCooldownTickerJob = null
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

        /** Parses `HUB_MESSAGE_COOLDOWN:N` from [ChatApiClient] 429 responses. */
        fun parseHubCooldownSeconds(e: Throwable): Int? = parseHubCooldownSecondsForTest(e)

        /** Test-visible wrapper for cooldown error parsing. */
        fun parseHubCooldownSecondsForTest(e: Throwable): Int? {
            val msg = e.message ?: return null
            if (!msg.contains("HUB_MESSAGE_COOLDOWN")) return null
            val afterColon =
                msg
                    .substringAfter("HUB_MESSAGE_COOLDOWN:", missingDelimiterValue = "")
                    .trim()
                    .takeWhile { it.isDigit() }
            return afterColon.toIntOrNull()?.coerceAtLeast(1) ?: HUB_MESSAGE_COOLDOWN_SECONDS
        }
    }
}

private const val SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES = 160
private const val SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES = 80
