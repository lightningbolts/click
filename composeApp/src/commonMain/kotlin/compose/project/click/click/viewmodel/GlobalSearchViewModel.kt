package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.Connection
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── Result Models ─────────────────────────────────────────────────────────────

/** A message hit, enriched with its parent chat's display info. */
data class MessageSearchResult(
    val message: Message,
    val chatId: String,
    val chatName: String,
    val connectionId: String
)

/** A location bucket, grouping connections that met at the same semantic location. */
data class LocationSearchResult(
    val location: String,
    val connectionCount: Int,
    val connectionIds: List<String>
)

/** Aggregated results across the three search categories. */
data class GlobalSearchResults(
    val people: List<ChatWithDetails> = emptyList(),
    val messages: List<MessageSearchResult> = emptyList(),
    val locations: List<LocationSearchResult> = emptyList()
) {
    val isEmpty: Boolean get() = people.isEmpty() && messages.isEmpty() && locations.isEmpty()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GlobalSearchViewModel(
    tokenStorage: TokenStorage = createTokenStorage(),
    private val chatRepository: ChatRepository = ChatRepository(tokenStorage = tokenStorage)
) : ViewModel() {

    private val appDataManager = AppDataManager

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _results = MutableStateFlow(GlobalSearchResults())
    val results: StateFlow<GlobalSearchResults> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    /**
     * Executes a global search across people, messages, and locations.
     * - People: in-memory filter on connected user names
     * - Messages: parallel per-chat search via [ChatRepository.searchMessages]
     * - Locations: in-memory grouping of connections' semantic_location
     *
     * Cancels any in-flight search before starting a new one.
     */
    fun search(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _results.value = GlobalSearchResults()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            // Debounce: wait 300ms so we don't fire on every keystroke
            delay(300L)
            _isSearching.value = true
            try {
                val lowerQuery = query.lowercase().trim()
                val connections = appDataManager.connections.value
                val connectedUsers = appDataManager.connectedUsers.value
                val currentUserId = appDataManager.currentUser.value?.id

                // ── People search ──────────────────────────────────────────────
                val matchingPeople = connections.mapNotNull { conn ->
                    val otherUserId = conn.user_ids.firstOrNull { it != currentUserId } ?: return@mapNotNull null
                    val otherUser = connectedUsers[otherUserId] ?: return@mapNotNull null
                    val name = otherUser.name ?: ""
                    if (name.lowercase().contains(lowerQuery)) {
                        ChatWithDetails(
                            chat = conn.chat,
                            connection = conn,
                            otherUser = otherUser,
                            lastMessage = null,
                            unreadCount = 0
                        )
                    } else null
                }

                // ── Location search ───────────────────────────────────────────
                val locationResults = connections
                    .filter {
                        val loc = it.semantic_location ?: return@filter false
                        loc.lowercase().contains(lowerQuery)
                    }
                    .groupBy { it.semantic_location ?: "Unknown" }
                    .map { (location, conns) ->
                        LocationSearchResult(
                            location = location,
                            connectionCount = conns.size,
                            connectionIds = conns.map { it.id }
                        )
                    }
                    .sortedByDescending { it.connectionCount }

                // ── Message search (parallel per-chat) ────────────────────────
                val messageResults: List<MessageSearchResult> = coroutineScope {
                    connections.map { conn ->
                        async {
                            val chatId = conn.chat.id ?: return@async emptyList<MessageSearchResult>()
                            val otherUserId = conn.user_ids.firstOrNull { it != currentUserId }
                            val chatName = otherUserId?.let { id ->
                                connectedUsers[id]?.name
                            } ?: conn.semantic_location ?: "Chat"

                            val remoteMatches = chatRepository.searchMessages(chatId, lowerQuery)
                            val localMatches = conn.chat.messages.filter { msg ->
                                msg.content.lowercase().contains(lowerQuery)
                            }
                            val mergedMatches = (remoteMatches + localMatches).distinctBy { it.id }

                            mergedMatches.map { msg ->
                                MessageSearchResult(
                                    message = msg,
                                    chatId = chatId,
                                    chatName = chatName,
                                    connectionId = conn.id
                                )
                            }
                        }
                    }.flatMap { it.await() }
                        .sortedByDescending { it.message.timeCreated }
                }

                _results.value = GlobalSearchResults(
                    people = matchingPeople,
                    messages = messageResults,
                    locations = locationResults
                )
            } catch (e: Exception) {
                println("GlobalSearch error: ${e.message}")
            } finally {
                _isSearching.value = false
            }
        }
    }

    /** Clears the current query and results. */
    fun clear() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _results.value = GlobalSearchResults()
        _isSearching.value = false
    }
}
