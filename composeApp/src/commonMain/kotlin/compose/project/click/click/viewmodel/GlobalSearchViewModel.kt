package compose.project.click.click.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.AvailabilityIntentRow
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.MapBeacon
import compose.project.click.click.data.models.Message
import compose.project.click.click.data.models.collapseOneToOneChatsByPeer
import compose.project.click.click.data.models.isArchivedChannelForUser
import compose.project.click.click.data.repository.ChatRepository
import compose.project.click.click.data.repository.ConversationSearchHit // pragma: allowlist secret
import compose.project.click.click.data.repository.MapBeaconRepository
import compose.project.click.click.data.repository.SupabaseChatRepository
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.data.repository.UnifiedSearchSupplement
import compose.project.click.click.data.storage.TokenStorage
import compose.project.click.click.data.storage.createTokenStorage
import compose.project.click.click.util.connectionMatchesMemoryOrTimeQuery
import compose.project.click.click.util.redactedRestMessage
import compose.project.click.click.utils.LocationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock

// ── Filter chips (visibility scopes) ─────────────────────────────────────────

enum class SearchResultCategory {
    Active,
    Archived,
    Cliques,
    Nearby,
    Beacons,
    Intents,
}

// ── Unified row model ─────────────────────────────────────────────────────────

sealed class SearchResult {
    abstract val categories: Set<SearchResultCategory>
    abstract val sortKey: Long

    data class ActiveConnection(
        val details: ChatWithDetails,
        val subtitle: String? = null,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Active)
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class ArchivedConnection(
        val details: ChatWithDetails,
        val subtitle: String? = null,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Archived)
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class Clique(
        val details: ChatWithDetails,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Cliques)
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class IntentMatch(
        val details: ChatWithDetails,
        val intentLabel: String,
        val intentTimeframe: String?,
        val isArchivedChannel: Boolean,
    ) : SearchResult() {
        override val categories =
            buildSet {
                add(SearchResultCategory.Intents)
                add(SearchResultCategory.Active)
                if (isArchivedChannel) add(SearchResultCategory.Archived)
            }
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class OwnAvailabilityIntentMatch(
        val intent: AvailabilityIntentRow,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Intents, SearchResultCategory.Active)
        override val sortKey: Long = 0L
    }

    data class BeaconMatch(
        val beacon: MapBeacon,
        val distanceMeters: Double?,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Beacons, SearchResultCategory.Nearby)
        override val sortKey: Long = distanceMeters?.toLong()?.let { -it } ?: Long.MIN_VALUE
    }

    data class InterestMatch(
        val details: ChatWithDetails,
        val matchedTags: List<String>,
        val isArchivedChannel: Boolean,
    ) : SearchResult() {
        override val categories =
            buildSet {
                add(SearchResultCategory.Active)
                if (isArchivedChannel) add(SearchResultCategory.Archived)
            }
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class MemoryContextMatch(
        val details: ChatWithDetails,
        val matchLabel: String,
        val isArchivedChannel: Boolean,
    ) : SearchResult() {
        override val categories =
            buildSet {
                add(SearchResultCategory.Nearby)
                if (isArchivedChannel) add(SearchResultCategory.Archived) else add(SearchResultCategory.Active)
            }
        override val sortKey: Long = sortKeyForDetails(details)
    }

    data class MessageHit(
        val result: MessageSearchResult,
        override val categories: Set<SearchResultCategory>,
    ) : SearchResult() {
        override val sortKey: Long = -result.message.timeCreated
    }

    data class LocationBucket(
        val result: LocationSearchResult,
    ) : SearchResult() {
        override val categories = setOf(SearchResultCategory.Nearby, SearchResultCategory.Active)
        override val sortKey: Long = -result.connectionCount.toLong()
    }

    companion object {
        private fun sortKeyForDetails(d: ChatWithDetails): Long =
            -(d.lastMessage?.timeCreated ?: d.connection.last_message_at ?: d.connection.created)
    }
}

data class MessageSearchResult(
    val message: Message,
    val chatId: String,
    val chatName: String,
    val connectionId: String,
    val snippet: String,
    val hubId: String? = null,
    val hubRealtimeChannel: String? = null,
    val hubTitle: String? = null,
    val hubCreatorId: String? = null,
    val hubCategory: String = "general",
) {
    val isHub: Boolean get() = !hubId.isNullOrBlank()
}

/** Navigation payload when a unified-search row opens a conversation. */
data class SearchChatOpenTarget(
    val connectionId: String,
    val targetMessageId: String? = null,
    val hubId: String? = null,
    val hubRealtimeChannel: String? = null,
    val hubTitle: String? = null,
    val hubCreatorId: String? = null,
    val hubCategory: String = "general",
) {
    val isHub: Boolean get() = !hubId.isNullOrBlank()
}

fun SearchResult.toChatOpenTarget(): SearchChatOpenTarget? =
    when (this) {
        is SearchResult.MessageHit ->
            SearchChatOpenTarget(
                connectionId = result.connectionId,
                targetMessageId = result.message.id,
                hubId = result.hubId,
                hubRealtimeChannel = result.hubRealtimeChannel,
                hubTitle = result.hubTitle,
                hubCreatorId = result.hubCreatorId,
                hubCategory = result.hubCategory,
            )
        is SearchResult.ActiveConnection -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.ArchivedConnection -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.Clique -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.IntentMatch -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.InterestMatch -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.MemoryContextMatch -> SearchChatOpenTarget(details.connection.id)
        is SearchResult.LocationBucket,
        is SearchResult.BeaconMatch,
        is SearchResult.OwnAvailabilityIntentMatch,
        -> null
    }

data class LocationSearchResult(
    val location: String,
    val connectionCount: Int,
    val connectionIds: List<String>,
)

data class GlobalSearchResults(
    val items: List<SearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = items.isEmpty()

    fun visible(selected: Set<SearchResultCategory>): List<SearchResult> {
        if (selected.size == SearchResultCategory.entries.size) {
            return items.sortedWith(searchResultComparator)
        }
        return items
            .filter { it.categories.intersect(selected).isNotEmpty() }
            .sortedWith(searchResultComparator)
    }
}

private const val REMOTE_MESSAGE_SEARCH_MAX_DIRECT_CHATS = 40
private const val REMOTE_MESSAGE_SEARCH_MAX_GROUP_CHATS = 24
private const val REMOTE_MESSAGE_SEARCH_MAX_HUBS = 16
private const val MESSAGE_SEARCH_CONCURRENCY = 8
private const val MIN_QUERY_LENGTH_FOR_REMOTE_MESSAGE_SCAN = 2

private val searchResultComparator =
    compareBy<SearchResult>({ typeRank(it) }, { it.sortKey })

private fun typeRank(r: SearchResult): Int =
    when (r) {
        is SearchResult.OwnAvailabilityIntentMatch -> 0
        is SearchResult.IntentMatch -> 1
        is SearchResult.BeaconMatch -> 2
        is SearchResult.InterestMatch -> 3
        is SearchResult.MemoryContextMatch -> 4
        is SearchResult.ActiveConnection -> 5
        is SearchResult.ArchivedConnection -> 6
        is SearchResult.MessageHit -> 7
        is SearchResult.LocationBucket -> 8
        is SearchResult.Clique -> 9
    }

// ── ViewModel ─────────────────────────────────────────────────────────────────

class GlobalSearchViewModel(
    tokenStorage: TokenStorage = createTokenStorage(),
    private val chatRepository: ChatRepository = SupabaseChatRepository(tokenStorage = tokenStorage),
    private val supabaseRepository: SupabaseRepository = SupabaseRepository(),
    private val mapBeaconRepository: MapBeaconRepository = MapBeaconRepository(),
    private val locationService: LocationService = LocationService(),
    /**
     * Junction + lifecycle ids for archive/hidden semantics (defaults to [AppDataManager]).
     * Overridden in unit tests to avoid the app singleton.
     */
    private val junctionArchivedConnectionIds: () -> Set<String> = { AppDataManager.archivedConnectionIds.value },
    private val junctionHiddenConnectionIds: () -> Set<String> = { AppDataManager.hiddenConnectionIds.value },
    /** Keystroke debounce; set to `0` in unit tests to avoid virtual-time coupling with [viewModelScope]. */
    private val searchDebounceMs: Long = 300L,
    private val fetchOwnAvailabilityIntents: suspend (String) -> List<AvailabilityIntentRow> = { userId ->
        supabaseRepository.fetchActiveAvailabilityIntentsForUser(userId)
    },
    private val fetchBeaconsForSearch: suspend (Double, Double) -> List<MapBeacon> = { lat, lon ->
        val bbox = searchBboxFromCenter(lat, lon, SEARCH_BEACON_RADIUS_METERS)
        mapBeaconRepository
            .fetchLocalBeacons(
                minLat = bbox.minLat,
                maxLat = bbox.maxLat,
                minLon = bbox.minLon,
                maxLon = bbox.maxLon,
            ).getOrElse { emptyList() }
    },
    private val resolveSearchLocation: suspend () -> Pair<Double, Double>? = {
        locationService.getCurrentLocation()?.let { it.latitude to it.longitude }
    },
    private val activeHubs: () -> List<ActiveHubEntry> = { AppDataManager.activeHubs.value },
    private val searchHubMessages: suspend (hubId: String, query: String) -> List<Message> = { hubId, query ->
        searchHubMessagesByQuery(hubId, query)
    },
    private val searchConversationHits: suspend (String) -> List<ConversationSearchHit> = { query ->
        // pragma: allowlist secret
        chatRepository.searchConversationHits(query)
    },
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _results = MutableStateFlow(GlobalSearchResults())
    val results: StateFlow<GlobalSearchResults> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _visibleCategories = MutableStateFlow(SearchResultCategory.entries.toSet())
    val visibleCategories: StateFlow<Set<SearchResultCategory>> = _visibleCategories.asStateFlow()

    private var searchJob: Job? = null

    fun toggleCategory(category: SearchResultCategory) {
        val cur = _visibleCategories.value
        if (cur.size == 1 && category in cur) return
        _visibleCategories.value = if (category in cur) cur - category else cur + category
    }

    fun selectAllFilters() {
        _visibleCategories.value = SearchResultCategory.entries.toSet()
    }

    fun search(
        query: String,
        viewerUserId: String,
    ) {
        _searchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _results.value = GlobalSearchResults()
            _isSearching.value = false
            return
        }

        _isSearching.value = true
        searchJob =
            viewModelScope.launch {
                val thisJob = coroutineContext[Job]
                if (searchDebounceMs > 0L) {
                    delay(searchDebounceMs)
                }
                try {
                    chatRepository.ensureFreshAuthToken()
                    val lowerQuery = query.lowercase().trim()
                    val userId = viewerUserId.trim()
                    if (userId.isEmpty()) {
                        _results.value = GlobalSearchResults()
                        return@launch
                    }

                    val (activeRowsRaw, archivedRowsRaw, cliqueRows, ownIntents, searchBeacons, searchLocation) =
                        coroutineScope {
                            val activeD = async { chatRepository.fetchDirectUserChatsWithDetails(userId) }
                            val archivedD = async { chatRepository.fetchArchivedUserChatsWithDetails(userId) }
                            val groupsD = async { chatRepository.fetchGroupUserChatsWithDetails(userId) }
                            val ownIntentsD = async { fetchOwnAvailabilityIntents(userId) }
                            val locationD = async { resolveSearchLocation() }
                            val location = locationD.await()
                            val beaconsD =
                                async {
                                    val loc = location ?: return@async emptyList()
                                    fetchBeaconsForSearch(loc.first, loc.second)
                                }
                            SearchContext(
                                activeRows = activeD.await(),
                                archivedRows = archivedD.await(),
                                cliqueRows = groupsD.await(),
                                ownIntents = ownIntentsD.await(),
                                searchBeacons = beaconsD.await(),
                                searchLocation = location,
                            )
                        }

                    val activityTs: (ChatWithDetails) -> Long = { row ->
                        row.lastMessage?.timeCreated
                            ?: row.connection.last_message_at
                            ?: row.connection.created
                    }
                    val activeRows = collapseOneToOneChatsByPeer(activeRowsRaw, userId, activityTs)
                    val archivedRows = collapseOneToOneChatsByPeer(archivedRowsRaw, userId, activityTs)

                    val archivedIds = junctionArchivedConnectionIds()
                    val hiddenIds = junctionHiddenConnectionIds()

                    val supplement =
                        chatRepository.unifiedSearchSupplement(
                            viewerUserId = userId,
                            peerUserIds = peerUserIdsForSupplement(activeRows, archivedRows, cliqueRows, userId),
                        )

                    val byPeerActive = indexDirectByPeer(activeRows, userId)
                    val byPeerArchived = indexDirectByPeer(archivedRows, userId)

                    val intentConnectionKeys = HashSet<String>()
                    val interestConnectionKeys = HashSet<String>()
                    val out = ArrayList<SearchResult>(64)

                    emitIntentMatches(lowerQuery, supplement, byPeerActive, byPeerArchived, intentConnectionKeys, out)
                    emitOwnAvailabilityIntentMatches(lowerQuery, ownIntents, out)
                    emitBeaconMatches(
                        lowerQuery,
                        searchBeacons,
                        searchLocation?.first,
                        searchLocation?.second,
                        out,
                    )
                    emitInterestMatches(
                        lowerQuery,
                        supplement,
                        byPeerActive,
                        byPeerArchived,
                        intentConnectionKeys,
                        interestConnectionKeys,
                        out,
                    )

                    emitMemoryMatches(lowerQuery, activeRows, archivedRows, archivedIds, hiddenIds, out)

                    emitNameMatches(
                        lowerQuery,
                        activeRows,
                        archivedRows,
                        intentConnectionKeys,
                        interestConnectionKeys,
                        out,
                    )

                    emitCliqueNameMatches(lowerQuery, cliqueRows, out)

                    emitLocationBuckets(lowerQuery, activeRows, archivedRows, out)

                    if (out.isNotEmpty()) {
                        _results.value = GlobalSearchResults(items = out.toList())
                    }

                    val messageHits =
                        if (lowerQuery.length >= MIN_QUERY_LENGTH_FOR_REMOTE_MESSAGE_SCAN) {
                            searchAllMessages(
                                lowerQuery,
                                userId,
                                activeRows,
                                archivedRows,
                                cliqueRows,
                                archivedIds,
                                activeHubs(),
                            )
                        } else {
                            emptyList()
                        }
                    out.addAll(messageHits)
                    _results.value = GlobalSearchResults(items = out)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    println("GlobalSearch error: ${e.redactedRestMessage()}")
                } finally {
                    if (searchJob === thisJob) {
                        _isSearching.value = false
                    }
                }
            }
    }

    fun clear() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _results.value = GlobalSearchResults()
        _isSearching.value = false
        _visibleCategories.value = SearchResultCategory.entries.toSet()
    }

    private suspend fun searchAllMessages(
        lowerQuery: String,
        userId: String,
        activeRows: List<ChatWithDetails>,
        archivedRows: List<ChatWithDetails>,
        cliqueRows: List<ChatWithDetails>,
        archivedIds: Set<String>,
        hubs: List<ActiveHubEntry>,
    ): List<SearchResult.MessageHit> =
        coroutineScope {
            val apiHitsD = async { searchConversationHits(lowerQuery) }
            val limiter = Semaphore(MESSAGE_SEARCH_CONCURRENCY)
            val directAll =
                (activeRows + archivedRows)
                    .filter { it.groupClique == null }
                    .sortedByDescending { row ->
                        row.lastMessage?.timeCreated ?: row.connection.last_message_at ?: row.connection.created
                    }

            fun localMessageHits(
                row: ChatWithDetails,
                chatName: String,
                chatId: String,
                categories: Set<SearchResultCategory>,
                hub: ActiveHubEntry? = null,
            ): List<SearchResult.MessageHit> {
                val local =
                    (listOfNotNull(row.lastMessage) + row.chat.messages)
                        .distinctBy { it.id }
                        .filter { it.content.contains(lowerQuery, ignoreCase = true) }
                return local.map { msg ->
                    SearchResult.MessageHit(
                        result =
                            MessageSearchResult(
                                message = msg,
                                chatId = chatId,
                                chatName = chatName,
                                connectionId = hub?.hubId ?: row.connection.id,
                                snippet = highlightedMessageSnippet(msg.content, lowerQuery),
                                hubId = hub?.hubId,
                                hubRealtimeChannel = hub?.realtimeChannel,
                                hubTitle = hub?.name,
                                hubCreatorId = hub?.creatorId,
                                hubCategory = hub?.category ?: "general",
                            ),
                        categories = categories,
                    )
                }
            }
            val localDirect =
                directAll.flatMap { row ->
                    if (directPeerId(row, userId) == null) {
                        emptyList()
                    } else {
                        val chatName = row.otherUser.name ?: row.connection.semanticLocation ?: "Chat"
                        localMessageHits(
                            row = row,
                            chatName = chatName,
                            chatId = row.chat.id ?: row.connection.id,
                            categories = messageCategories(row.connection.id, archivedIds),
                        )
                    }
                }
            val rankedGroups =
                cliqueRows
                    .sortedByDescending { row ->
                        row.lastMessage?.timeCreated ?: row.connection.last_message_at ?: row.connection.created
                    }.take(REMOTE_MESSAGE_SEARCH_MAX_GROUP_CHATS)
            val localGroups =
                rankedGroups.flatMap { row ->
                    val chatId = row.chat.id ?: return@flatMap emptyList()
                    localMessageHits(
                        row = row,
                        chatName = row.groupClique?.name ?: "Clique",
                        chatId = chatId,
                        categories = setOf(SearchResultCategory.Cliques),
                    )
                }
            val apiHits = apiHitsD.await()
            val fromApi =
                apiHits
                    .filterNot { isUndisplayableEncryptedSearchSnippet(it.snippet) }
                    .map { it.toMessageHit(archivedIds, hubs) }
            val skipRemoteScan = apiHits.isNotEmpty()
            val remoteDirectIds: Set<String> =
                if (skipRemoteScan) {
                    emptySet()
                } else {
                    directAll.take(REMOTE_MESSAGE_SEARCH_MAX_DIRECT_CHATS).map { it.connection.id }.toSet()
                }
            val directJobs =
                if (skipRemoteScan) {
                    emptyList()
                } else {
                    directAll.map { row ->
                        async {
                            limiter.withPermit {
                                if (directPeerId(row, userId) == null) return@withPermit emptyList()
                                val chatName = row.otherUser.name ?: row.connection.semanticLocation ?: "Chat"
                                val (resolvedChatId, remoteMatches) =
                                    if (row.connection.id in remoteDirectIds) {
                                        chatRepository.searchMessagesByConnectionId(
                                            connectionId = row.connection.id,
                                            query = lowerQuery,
                                        )
                                    } else {
                                        null to emptyList()
                                    }
                                val resultChatId = resolvedChatId ?: row.chat.id ?: row.connection.id
                                val cats = messageCategories(row.connection.id, archivedIds)
                                remoteMatches.map { msg ->
                                    SearchResult.MessageHit(
                                        result =
                                            MessageSearchResult(
                                                message = msg,
                                                chatId = resultChatId,
                                                chatName = chatName,
                                                connectionId = row.connection.id,
                                                snippet = highlightedMessageSnippet(msg.content, lowerQuery),
                                            ),
                                        categories = cats,
                                    )
                                }
                            }
                        }
                    }
                }
            val groupJobs =
                if (skipRemoteScan) {
                    emptyList()
                } else {
                    rankedGroups.mapNotNull { row ->
                        val chatId = row.chat.id ?: return@mapNotNull null
                        async {
                            limiter.withPermit {
                                val remote =
                                    chatRepository
                                        .fetchMessagesForChat(chatId, userId, limit = 80)
                                        ?.filter { msg ->
                                            msg.content.contains(lowerQuery, ignoreCase = true)
                                        }.orEmpty()
                                val title = row.groupClique?.name ?: "Clique"
                                remote.map { msg ->
                                    SearchResult.MessageHit(
                                        result =
                                            MessageSearchResult(
                                                message = msg,
                                                chatId = chatId,
                                                chatName = title,
                                                connectionId = row.connection.id,
                                                snippet = highlightedMessageSnippet(msg.content, lowerQuery),
                                            ),
                                        categories = setOf(SearchResultCategory.Cliques),
                                    )
                                }
                            }
                        }
                    }
                }
            val hubJobs =
                if (skipRemoteScan) {
                    emptyList()
                } else {
                    hubs
                        .sortedByDescending { it.joinedAtMs }
                        .take(REMOTE_MESSAGE_SEARCH_MAX_HUBS)
                        .map { hub ->
                            async {
                                limiter.withPermit {
                                    val matches = searchHubMessages(hub.hubId, lowerQuery)
                                    matches.map { msg ->
                                        SearchResult.MessageHit(
                                            result =
                                                MessageSearchResult(
                                                    message = msg,
                                                    chatId = hub.hubId,
                                                    chatName = hub.name,
                                                    connectionId = hub.hubId,
                                                    snippet = highlightedMessageSnippet(msg.content, lowerQuery),
                                                    hubId = hub.hubId,
                                                    hubRealtimeChannel = hub.realtimeChannel,
                                                    hubTitle = hub.name,
                                                    hubCreatorId = hub.creatorId,
                                                    hubCategory = hub.category,
                                                ),
                                            categories = setOf(SearchResultCategory.Nearby),
                                        )
                                    }
                                }
                            }
                        }
                }
            val remoteHits = (directJobs + groupJobs + hubJobs).flatMap { it.await() }
            (fromApi + localDirect + localGroups + remoteHits)
                .filterNot { isUndisplayableEncryptedSearchSnippet(it.result.snippet) }
                .distinctBy { it.result.message.id }
        }
}

private fun ConversationSearchHit.toMessageHit( // pragma: allowlist secret
    archivedIds: Set<String>,
    hubs: List<ActiveHubEntry>,
): SearchResult.MessageHit {
    val hub = hubs.firstOrNull { it.hubId == hubId || it.hubId == connectionId }
    val resolvedHubId = hubId?.takeIf { isHub } ?: hub?.hubId
    val message =
        Message(
            id = messageId,
            user_id = senderId,
            content = snippet,
            timeCreated = timestamp,
        )
    return SearchResult.MessageHit(
        result =
            MessageSearchResult(
                message = message,
                chatId = chatId.ifBlank { connectionId },
                chatName = chatName.ifBlank { if (isHub) "Hub" else "Chat" },
                connectionId = connectionId,
                snippet = snippet,
                hubId = resolvedHubId,
                hubRealtimeChannel =
                    hubRealtimeChannel?.takeIf { it.isNotBlank() } ?: hub?.realtimeChannel
                        ?: resolvedHubId?.let { "hub:$it" },
                hubTitle = hub?.name ?: chatName.takeIf { isHub },
                hubCreatorId = hub?.creatorId,
                hubCategory = hub?.category ?: "general",
            ),
        categories =
            when {
                isHub -> setOf(SearchResultCategory.Nearby)
                connectionId in archivedIds -> setOf(SearchResultCategory.Archived)
                else -> setOf(SearchResultCategory.Active)
            },
    )
}

private fun messageCategories(
    connectionId: String,
    archivedIds: Set<String>,
): Set<SearchResultCategory> =
    if (connectionId in archivedIds) {
        setOf(SearchResultCategory.Archived)
    } else {
        setOf(SearchResultCategory.Active)
    }

private fun peerUserIdsForSupplement(
    active: List<ChatWithDetails>,
    archived: List<ChatWithDetails>,
    cliques: List<ChatWithDetails>,
    viewerId: String,
): List<String> {
    val ids = LinkedHashSet<String>()
    for (row in active) {
        directPeerId(row, viewerId)?.let { ids.add(it) }
    }
    for (row in archived) {
        directPeerId(row, viewerId)?.let { ids.add(it) }
    }
    for (row in cliques) {
        for (uid in row.connection.user_ids) {
            if (uid != viewerId) ids.add(uid)
        }
    }
    return ids.toList()
}

private fun directPeerId(
    row: ChatWithDetails,
    viewerId: String,
): String? {
    if (row.groupClique != null) return null
    return row.connection.user_ids.firstOrNull { it != viewerId }
}

private fun indexDirectByPeer(
    rows: List<ChatWithDetails>,
    viewerId: String,
): Map<String, ChatWithDetails> {
    val map = HashMap<String, ChatWithDetails>()
    for (row in rows) {
        val pid = directPeerId(row, viewerId) ?: continue
        map[pid] = row
    }
    return map
}

private data class SearchContext(
    val activeRows: List<ChatWithDetails>,
    val archivedRows: List<ChatWithDetails>,
    val cliqueRows: List<ChatWithDetails>,
    val ownIntents: List<AvailabilityIntentRow>,
    val searchBeacons: List<MapBeacon>,
    val searchLocation: Pair<Double, Double>?,
)

private fun emitIntentMatches(
    lowerQuery: String,
    supplement: UnifiedSearchSupplement,
    byPeerActive: Map<String, ChatWithDetails>,
    byPeerArchived: Map<String, ChatWithDetails>,
    intentConnectionKeys: MutableSet<String>,
    out: MutableList<SearchResult>,
) {
    for ((uid, intents) in supplement.activePeerIntentsByUserId) {
        val activeRow = byPeerActive[uid]
        val archivedRow = byPeerArchived[uid]
        val row = activeRow ?: archivedRow ?: continue
        val isArchived = activeRow == null
        for (intent in intents) {
            if (!availabilityIntentMatchesQuery(intent, lowerQuery)) continue
            val label =
                intent.intentTag
                    ?.trim()
                    .orEmpty()
                    .ifEmpty { "Intent" }
            out.add(
                SearchResult.IntentMatch(
                    details = row,
                    intentLabel = label,
                    intentTimeframe = intent.timeframe?.trim()?.takeIf { it.isNotEmpty() },
                    isArchivedChannel = isArchived,
                ),
            )
            intentConnectionKeys.add(row.connection.id)
        }
    }
}

private fun emitOwnAvailabilityIntentMatches(
    lowerQuery: String,
    ownIntents: List<AvailabilityIntentRow>,
    out: MutableList<SearchResult>,
) {
    for (intent in ownIntents) {
        if (!availabilityIntentMatchesQuery(intent, lowerQuery)) continue
        out.add(SearchResult.OwnAvailabilityIntentMatch(intent = intent))
    }
}

private fun emitBeaconMatches(
    lowerQuery: String,
    beacons: List<MapBeacon>,
    userLat: Double?,
    userLon: Double?,
    out: MutableList<SearchResult>,
) {
    val now = Clock.System.now().toEpochMilliseconds()
    val seen = HashSet<String>()
    for (beacon in beacons) {
        if (!isBeaconStillActive(beacon, now)) continue
        if (!mapBeaconMatchesQuery(beacon, lowerQuery)) continue
        if (!seen.add(beacon.id)) continue
        val distance = beaconDistanceMeters(beacon, userLat, userLon)
        out.add(SearchResult.BeaconMatch(beacon = beacon, distanceMeters = distance))
    }
}

private fun emitInterestMatches(
    lowerQuery: String,
    supplement: UnifiedSearchSupplement,
    byPeerActive: Map<String, ChatWithDetails>,
    byPeerArchived: Map<String, ChatWithDetails>,
    intentConnectionKeys: Set<String>,
    interestConnectionKeys: MutableSet<String>,
    out: MutableList<SearchResult>,
) {
    for ((uid, tags) in supplement.peerInterestTagsByUserId) {
        val matched = tags.filter { it.lowercase().contains(lowerQuery) }
        if (matched.isEmpty()) continue
        val activeRow = byPeerActive[uid]
        val archivedRow = byPeerArchived[uid]
        val row = activeRow ?: archivedRow ?: continue
        if (row.connection.id in intentConnectionKeys) continue
        val isArchived = activeRow == null
        out.add(
            SearchResult.InterestMatch(
                details = row,
                matchedTags = matched,
                isArchivedChannel = isArchived,
            ),
        )
        interestConnectionKeys.add(row.connection.id)
    }
}

private fun emitMemoryMatches(
    lowerQuery: String,
    activeRows: List<ChatWithDetails>,
    archivedRows: List<ChatWithDetails>,
    archivedIds: Set<String>,
    hiddenIds: Set<String>,
    out: MutableList<SearchResult>,
) {
    for (row in activeRows) {
        if (row.groupClique != null) continue
        if (!connectionMatchesMemoryOrTimeQuery(row.connection, lowerQuery)) continue
        val isArchived = row.connection.isArchivedChannelForUser(archivedIds, hiddenIds)
        out.add(
            SearchResult.MemoryContextMatch(
                details = row,
                matchLabel = "Context / time / place",
                isArchivedChannel = isArchived,
            ),
        )
    }
    for (row in archivedRows) {
        if (row.groupClique != null) continue
        if (!connectionMatchesMemoryOrTimeQuery(row.connection, lowerQuery)) continue
        out.add(
            SearchResult.MemoryContextMatch(
                details = row,
                matchLabel = "Context / time / place",
                isArchivedChannel = true,
            ),
        )
    }
}

private fun emitNameMatches(
    lowerQuery: String,
    activeRows: List<ChatWithDetails>,
    archivedRows: List<ChatWithDetails>,
    intentConnectionKeys: Set<String>,
    interestConnectionKeys: Set<String>,
    out: MutableList<SearchResult>,
) {
    for (row in activeRows) {
        if (row.groupClique != null) continue
        if (row.connection.id in intentConnectionKeys || row.connection.id in interestConnectionKeys) continue
        val name =
            row.otherUser.name
                ?.lowercase()
                .orEmpty()
        if (!name.contains(lowerQuery)) continue
        if (connectionMatchesMemoryOrTimeQuery(row.connection, lowerQuery)) continue
        out.add(SearchResult.ActiveConnection(details = row))
    }
    for (row in archivedRows) {
        if (row.groupClique != null) continue
        if (row.connection.id in intentConnectionKeys || row.connection.id in interestConnectionKeys) continue
        val name =
            row.otherUser.name
                ?.lowercase()
                .orEmpty()
        if (!name.contains(lowerQuery)) continue
        if (connectionMatchesMemoryOrTimeQuery(row.connection, lowerQuery)) continue
        out.add(SearchResult.ArchivedConnection(details = row))
    }
}

private fun emitCliqueNameMatches(
    lowerQuery: String,
    cliqueRows: List<ChatWithDetails>,
    out: MutableList<SearchResult>,
) {
    for (row in cliqueRows) {
        val title =
            row.groupClique
                ?.name
                ?.lowercase()
                .orEmpty()
        val titleHit = title.contains(lowerQuery)
        val memberHit =
            row.groupMemberUsers.any { m ->
                m.name?.lowercase()?.contains(lowerQuery) == true
            }
        if (titleHit || memberHit) {
            out.add(SearchResult.Clique(details = row))
        }
    }
}

private fun emitLocationBuckets(
    lowerQuery: String,
    activeRows: List<ChatWithDetails>,
    archivedRows: List<ChatWithDetails>,
    out: MutableList<SearchResult>,
) {
    val pairs = ArrayList<Pair<String, String>>()
    for (row in activeRows + archivedRows) {
        if (row.groupClique != null) continue
        val loc =
            row.connection.semanticLocation
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: continue
        if (!loc.lowercase().contains(lowerQuery)) continue
        pairs.add(loc to row.connection.id)
    }
    if (pairs.isEmpty()) return
    val grouped = pairs.groupBy({ it.first }, { it.second }).mapValues { (_, ids) -> ids.distinct() }
    for ((location, ids) in grouped) {
        out.add(
            SearchResult.LocationBucket(
                LocationSearchResult(
                    location = location,
                    connectionCount = ids.size,
                    connectionIds = ids,
                ),
            ),
        )
    }
}
