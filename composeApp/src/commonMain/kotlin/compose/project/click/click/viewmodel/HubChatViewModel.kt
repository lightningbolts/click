package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.CHAT_MEDIA_BUCKET
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.api.ChatApiClient
import compose.project.click.click.data.models.ChatMessageType
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.MessageWithUser
import compose.project.click.click.data.models.User
import compose.project.click.click.data.models.audioCacheFileExtension
import compose.project.click.click.data.models.isEncryptedMedia
import compose.project.click.click.data.models.mediaUrlOrNull
import compose.project.click.click.data.repository.normalizeEncryptedMediaPayload
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.crypto.MessageCrypto
import compose.project.click.click.ui.chat.deleteSecureChatAudioTempFile
import compose.project.click.click.ui.chat.writeSecureChatAudioTempFile
import compose.project.click.click.util.LruMemoryCache
import compose.project.click.click.util.chatMediaDispatcher
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.util.teardownBlocking
import compose.project.click.click.utils.LocationResult
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

class HubChatViewModel(
    private val hubId: String,
    private val realtimeChannelName: String,
    private val hubTitle: String,
    private val currentUserId: String,
    private val hubLocationResolver: suspend () -> LocationResult? = { null },
    private val tokenStorage: TokenStorage = createTokenStorage(),
    private val chatApi: ChatApiClient = ChatApiClient(),
) : ViewModel(), SecureChatMediaHost {

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

    private val _secureChatMediaLoadState = MutableStateFlow<Map<String, SecureChatMediaLoadState>>(emptyMap())
    override val secureChatMediaLoadState: StateFlow<Map<String, SecureChatMediaLoadState>> =
        _secureChatMediaLoadState.asStateFlow()
    private val secureImageBytesCache =
        LruMemoryCache<String, ByteArray>(SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES)
    private val secureAudioPathCache =
        LruMemoryCache<String, String>(SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES)

    val title: String get() = hubTitle

    private val senderUiCache = mutableMapOf<String, Pair<String, String?>>()
    private var hubChannel: RealtimeChannel? = null
    private var sessionJob: Job? = null

    init {
        startSession()
    }

    fun updateDraft(text: String) {
        _draft.value = text.take(HUB_CHAT_DRAFT_MAX_LENGTH)
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

    private suspend fun senderDisplay(userId: String, isMine: Boolean): Pair<String, String?> {
        if (isMine) return "You" to null
        senderUiCache[userId]?.let { return it }
        val user = userRepository.fetchUserById(userId)
        val label = user?.name?.takeIf { it.isNotBlank() } ?: "Member"
        val avatar = user?.image?.trim()?.takeIf { it.isNotEmpty() }
        return (label to avatar).also { senderUiCache[userId] = it }
    }

    private suspend fun rowToMessageWithUser(row: HubMessageRow): MessageWithUser {
        val mine = row.userId == currentUserId
        val (label, avatar) = senderDisplay(row.userId, mine)
        val message = Message(
            id = row.id,
            user_id = row.userId,
            content = row.body,
            timeCreated = hubCreatedAtToEpoch(row.createdAt),
            timeEdited = null,
            isRead = true,
            messageType = row.messageType,
            metadata = row.metadata,
        )
        val user = if (mine) {
            User(id = row.userId, name = "You", image = null, createdAt = 0L)
        } else {
            User(id = row.userId, name = label, image = avatar, createdAt = 0L)
        }
        return MessageWithUser(message = message, user = user, isSent = mine)
    }

    private suspend fun mergeMessages(rows: List<HubMessageRow>) {
        val ui = rows
            .filter { it.hubId == hubId }
            .sortedBy { it.createdAt }
            .map { rowToMessageWithUser(it) }
        _messages.value = ui
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

    private fun startSession() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            try {
                loadInitialMessages()
                val channel = supabase.channel(realtimeChannelName) {
                    presence {
                        key = currentUserId
                    }
                }
                hubChannel = channel

                val hubMessageChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                    table = "hub_messages"
                }

                val occupantKeys = mutableSetOf<String>()
                fun recomputeOccupants() {
                    val n = occupantKeys.size.coerceAtLeast(1)
                    _occupantCount.value = n
                }

                val presenceJob = launch(start = CoroutineStart.UNDISPATCHED) {
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

                channel.subscribe(blockUntilSubscribed = true)
                channel.track(buildJsonObject { put("userId", currentUserId) })
                occupantKeys.add(currentUserId)
                recomputeOccupants()

                val refreshJob = launch {
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
                                val ui = rowToMessageWithUser(row)
                                if (_messages.value.none { it.message.id == ui.message.id }) {
                                    _messages.value = _messages.value + ui
                                }
                            }
                            else -> Unit
                        }
                    }
                } finally {
                    refreshJob.cancel()
                    presenceJob.cancel()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("HubChatViewModel: session error: ${e.redactedRestMessage()}")
            } finally {
                val ch = hubChannel
                hubChannel = null
                if (ch != null) {
                    runCatching { ch.untrack() }
                    runCatching { ch.unsubscribe() }
                }
            }
        }
    }

    private suspend fun loadInitialMessages() {
        try {
            val rows = supabase.from("hub_messages")
                .select {
                    filter {
                        eq("hub_id", hubId)
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<HubMessageRow>()
            mergeMessages(rows)
        } catch (e: Exception) {
            println("HubChatViewModel: load messages failed: ${e.redactedRestMessage()}")
        }
    }

    private suspend fun resolveGatekeeperLocationOrThrow(): LocationResult {
        val loc = hubLocationResolver()
            ?: throw IllegalStateException("Location is required to send hub messages.")
        if (!loc.latitude.isFinite() || !loc.longitude.isFinite()) {
            throw IllegalStateException("Invalid location.")
        }
        return loc
    }

    fun sendMessage() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            try {
                val loc = resolveGatekeeperLocationOrThrow()
                val jwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Please sign in again.")
                chatApi.sendHubMessage(
                    hubId = hubId,
                    body = text,
                    userLat = loc.latitude,
                    userLong = loc.longitude,
                    authToken = jwt,
                    messageType = ChatMessageType.TEXT,
                    metadata = null,
                ).getOrElse { e -> throw e }
                _draft.value = ""
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Could not send"
            } finally {
                _isSending.value = false
            }
        }
    }

    /**
     * Encrypt with hub broadcast key, upload ciphertext via gatekeeper, then insert image message.
     */
    fun sendHubImageFromPicker(imageBytes: ByteArray, mimeType: String) {
        if (imageBytes.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            try {
                val loc = resolveGatekeeperLocationOrThrow()
                val jwt = tokenStorage.getJwt()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalStateException("Please sign in again.")
                val keys = MessageCrypto.deriveKeysForHub(hubId)
                val cipher = MessageCrypto.encryptMediaBytes(imageBytes, keys)
                val leaf = randomHubMediaLeaf()
                val objectPath = "$currentUserId/hub/$hubId/$leaf.bin"
                val path = chatApi.uploadHubMedia(
                    fileBytes = cipher,
                    hubId = hubId,
                    mimeType = "application/octet-stream",
                    objectPath = objectPath,
                    authToken = jwt,
                    userLat = loc.latitude,
                    userLong = loc.longitude,
                ).getOrElse { e -> throw e }
                val publicUrl = supabase.storage.from(CHAT_MEDIA_BUCKET).publicUrl(path)
                val metadata: JsonObject = buildJsonObject {
                    put("media_url", JsonPrimitive(publicUrl))
                    put("is_encrypted_media", JsonPrimitive(true))
                    put("original_mime_type", JsonPrimitive(mimeType.ifBlank { "image/jpeg" }))
                }
                chatApi.sendHubMessage(
                    hubId = hubId,
                    body = "Photo",
                    userLat = loc.latitude,
                    userLong = loc.longitude,
                    authToken = jwt,
                    messageType = ChatMessageType.IMAGE,
                    metadata = metadata,
                ).getOrElse { e -> throw e }
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Could not send image"
            } finally {
                _isSending.value = false
            }
        }
    }

    override fun ensureSecureChatImageLoaded(scopeId: String, viewerUserId: String, message: Message) {
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
            val bytes = runCatching {
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

    override fun ensureSecureChatAudioLoaded(scopeId: String, viewerUserId: String, message: Message) {
        if (scopeId != hubId) return
        if (!message.isEncryptedMedia()) return
        if (message.messageType.lowercase() != ChatMessageType.AUDIO) return
        val url = message.mediaUrlOrNull() ?: return
        if (url.isBlank()) return
        val cachedPath = secureAudioPathCache.get(message.id)
        if (!cachedPath.isNullOrBlank()) {
            _secureChatMediaLoadState.update {
                it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = cachedPath))
            }
            return
        }
        val cur = _secureChatMediaLoadState.value[message.id]
        if (cur?.audioLocalPath != null || cur?.loading == true) return
        viewModelScope.launch(chatMediaDispatcher) {
            _secureChatMediaLoadState.update { it + (message.id to SecureChatMediaLoadState(loading = true)) }
            val bytes = runCatching {
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
            val path = writeSecureChatAudioTempFile(message.id, bytes, message.audioCacheFileExtension())
            if (path.isNullOrBlank()) {
                println("HubChatViewModel: secure audio cache write failed for message=${message.id}")
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, error = "Could not cache audio"))
                }
            } else {
                val evictedPath = secureAudioPathCache.put(message.id, path)
                if (!evictedPath.isNullOrBlank() && evictedPath != path) {
                    deleteSecureChatAudioTempFile(evictedPath)
                }
                _secureChatMediaLoadState.update {
                    it + (message.id to SecureChatMediaLoadState(loading = false, audioLocalPath = path))
                }
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
}

private const val SECURE_CHAT_IMAGE_CACHE_MAX_ENTRIES = 160
private const val SECURE_CHAT_AUDIO_CACHE_MAX_ENTRIES = 80
