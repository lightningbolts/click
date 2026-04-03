package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.SupabaseConfig
import compose.project.click.click.data.repository.SupabaseRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

data class HubChatMessageUi(
    val id: String,
    val body: String,
    val senderLabel: String,
    val isMine: Boolean,
    val createdAtIso: String,
)

@Serializable
private data class HubMessageRow(
    val id: String,
    @SerialName("hub_id") val hubId: String,
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String,
)

private const val HUB_CHAT_DRAFT_MAX_LENGTH = 1000

class HubChatViewModel(
    private val hubId: String,
    private val realtimeChannelName: String,
    private val hubTitle: String,
    private val currentUserId: String,
) : ViewModel() {

    private val supabase by lazy { SupabaseConfig.client }
    private val userRepository by lazy { SupabaseRepository() }

    private val _messages = MutableStateFlow<List<HubChatMessageUi>>(emptyList())
    val messages: StateFlow<List<HubChatMessageUi>> = _messages.asStateFlow()

    private val _occupantCount = MutableStateFlow(1)
    val occupantCount: StateFlow<Int> = _occupantCount.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    val title: String get() = hubTitle

    private val senderLabelCache = mutableMapOf<String, String>()
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

    private suspend fun labelForSender(userId: String): String {
        if (userId == currentUserId) return "You"
        senderLabelCache[userId]?.let { return it }
        val user = userRepository.fetchUserById(userId)
        val label = user?.name?.takeIf { it.isNotBlank() } ?: "Member"
        senderLabelCache[userId] = label
        return label
    }

    private suspend fun rowToUi(row: HubMessageRow): HubChatMessageUi {
        val mine = row.userId == currentUserId
        val label = if (mine) "You" else labelForSender(row.userId)
        return HubChatMessageUi(
            id = row.id,
            body = row.body,
            senderLabel = label,
            isMine = mine,
            createdAtIso = row.createdAt,
        )
    }

    private suspend fun mergeMessages(rows: List<HubMessageRow>) {
        val ui = rows
            .filter { it.hubId == hubId }
            .sortedBy { it.createdAt }
            .map { rowToUi(it) }
        _messages.value = ui
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
                    channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                        table = "hub_messages"
                    }.collect { action ->
                        when (action) {
                            is PostgresAction.Insert -> {
                                val row = action.decodeRecordOrNull<HubMessageRow>() ?: return@collect
                                if (row.hubId != hubId) return@collect
                                val ui = rowToUi(row)
                                if (_messages.value.none { it.id == ui.id }) {
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
                println("HubChatViewModel: session error: ${e.message}")
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
            println("HubChatViewModel: load messages failed: ${e.message}")
        }
    }

    fun sendMessage() {
        val text = _draft.value.trim()
        if (text.isEmpty() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _sendError.value = null
            try {
                supabase.from("hub_messages").insert(
                    buildJsonObject {
                        put("hub_id", hubId)
                        put("user_id", currentUserId)
                        put("body", text)
                    },
                )
                _draft.value = ""
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Could not send"
            } finally {
                _isSending.value = false
            }
        }
    }

    override fun onCleared() {
        sessionJob?.cancel()
        sessionJob = null
        val ch = hubChannel
        hubChannel = null
        if (ch != null) {
            runBlocking {
                runCatching { ch.untrack() }
                runCatching { ch.unsubscribe() }
            }
        }
        super.onCleared()
    }
}
