@file:Suppress(
    "ktlint:standard:no-consecutive-comments",
    "ktlint:standard:backing-property-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.Connection // pragma: allowlist secret
import compose.project.click.click.data.models.ConnectionEncounter // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.collapseOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isResolvedDisplayName // pragma: allowlist secret
import compose.project.click.click.data.realtime.RealtimeCoordinator
import compose.project.click.click.util.dedupeOneToOneChatsByPeer
import compose.project.click.click.util.isPersistedApiChatId
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecordOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal fun ChatViewModel.scheduleDebouncedChatListRefresh() {
    debouncedChatListRefreshJob?.cancel()
    debouncedChatListRefreshJob =
        viewModelScope.launch {
            delay(CONNECTIONS_LIST_DEBOUNCE_MS)
            loadChats(isForced = true)
        }
}

/**
 * Junction-table realtime (archive/hide/core + new connection INSERT). Message previews
 * are patched via [startGlobalMessageListRealtime]; avoid reloading the full inbox on
 * [connections] UPDATE (last_message_at trigger) or realtime bumps get overwritten.
 */
internal fun ChatViewModel.startGlobalConnectionsRealtime(userId: String) {
    connectionsRealtimeJob?.cancel()
    debouncedChatListRefreshJob?.cancel()
    val previous = connectionsRealtimeChannel
    connectionsRealtimeChannel = null
    if (previous != null) {
        viewModelScope.launch {
            runCatching { previous.unsubscribe() }
        }
    }
    connectionsRealtimeJob =
        viewModelScope.launch {
            try {
                RealtimeCoordinator.ensureStarted(userId)
                RealtimeCoordinator.connectionJunctionChanged.collect {
                    scheduleDebouncedChatListRefresh()
                    reapplyChatListVisibilityFromAppData()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("ChatViewModel: global connections realtime unavailable: ${e.redactedRestMessage()}")
            }
        }
}

internal fun ChatViewModel.handleConnectionArchivesRealtime(
    action: PostgresAction,
    userId: String,
) {
    when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecordOrNull<ConnectionJunctionRealtimeRow>() ?: return
            if (row.userId != userId || row.connectionId.isNullOrBlank()) return
            AppDataManager.markConnectionArchivedLocally(row.connectionId)
            scheduleDebouncedChatListRefresh()
            reapplyChatListVisibilityFromAppData()
        }
        is PostgresAction.Delete -> {
            val cid = action.oldRecord.stringField("connection_id") ?: return
            AppDataManager.markConnectionUnarchivedLocally(cid)
            scheduleDebouncedChatListRefresh()
            reapplyChatListVisibilityFromAppData()
        }
        else -> Unit
    }
}

internal fun ChatViewModel.handleConnectionHiddenRealtime(
    action: PostgresAction,
    userId: String,
) {
    when (action) {
        is PostgresAction.Insert -> {
            val row = action.decodeRecordOrNull<ConnectionJunctionRealtimeRow>() ?: return
            if (row.userId != userId || row.connectionId.isNullOrBlank()) return
            AppDataManager.hideConnectionLocally(row.connectionId)
            scheduleDebouncedChatListRefresh()
            reapplyChatListVisibilityFromAppData()
        }
        is PostgresAction.Delete -> {
            val cid = action.oldRecord.stringField("connection_id") ?: return
            AppDataManager.unhideConnectionLocally(cid)
            scheduleDebouncedChatListRefresh()
        }
        else -> Unit
    }
}

internal fun ChatViewModel.reapplyChatListVisibilityFromAppData() {
    val cur = _chatListState.value
    if (cur !is ChatListState.Success) return
    val userId = _currentUserId.value
    val filtered = applyChatListVisibility(cur.chats)
    val collapsed =
        collapseOneToOneChatsByPeer(
            chats = filtered,
            viewerUserId = userId,
            activityTs = { chatListActivityTimestamp(it) },
        )
    pruneStaleReadClearedHints(collapsed)
    _chatListState.value = ChatListState.Success(applyUnreadClearHintsToInboxRows(collapsed))
}

/**
 * Listens for INSERT on [messages] (RLS-scoped). Updates the Clicks list snippet immediately
 * via [bumpConnectionInChatList], independent of debounced [loadChats] or per-chat subscriptions.
 */
internal fun ChatViewModel.startGlobalMessageListRealtime() {
    globalMessageListJob?.cancel()
    globalMessageListJob =
        viewModelScope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    val userId = _currentUserId.value
                    if (userId == null) {
                        delay(200)
                        continue
                    }
                    RealtimeCoordinator.ensureStarted(userId)
                    val seededRows = (_chatListState.value as? ChatListState.Success)?.chats.orEmpty()
                    if (seededRows.isNotEmpty()) {
                        chatRepository.seedInboxChatRouting(seededRows)
                    }
                    chatRepository.seedInboxChatRouting(AppDataManager.inboxFeedChats.value)
                    RealtimeCoordinator.messageInserts.collect { event ->
                        chatRepository.mergeCachedTimelineMessage(event.connectionId, event.message)
                        syncPrefetchFromHotTimeline(event.connectionId)
                        bumpConnectionInChatList(event.connectionId, event.message, event.chatId)
                        if (event.connectionId == currentConnectionId) {
                            val viewerId = _currentUserId.value ?: return@collect
                            viewModelScope.launch {
                                val vaulted =
                                    vaultMessagesForUi(event.chatId, viewerId, listOf(event.message))
                                        .firstOrNull() ?: event.message
                                val user =
                                    resolveMessageUser(vaulted.user_id, event.chatId)
                                        ?: User(id = vaulted.user_id, name = null, createdAt = 0L)
                                applyInsertedMessage(vaulted, user, viewerId)
                            }
                        }
                    }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    println(
                        "ChatViewModel: global message list realtime unavailable " +
                            "(attempt $attempt): ${e.redactedRestMessage()}",
                    )
                    delay(minOf(30_000L, 500L * attempt))
                }
            }
        }
}

// Load all chats for the current user
internal fun ChatViewModel.loadChatsImpl(isForced: Boolean = true) {
    val userId = _currentUserId.value ?: return

    // Avoid reload if already success and not forced
    if (!isForced && _chatListState.value is ChatListState.Success) return

    viewModelScope.launch {
        // Clearing junction caches during an in-session refresh (e.g. iOS tap-back from a
        // thread) can yield transient empty/stale combine steps and make Archived tab counts
        // flash to zero. Keep caches while we already have a painted inbox; cold paths still clear.
        if (isForced && _chatListState.value !is ChatListState.Success) {
            chatRepository.clearChatListLocalCaches()
        }
        val cachedConnections = AppDataManager.connections.value
        val cachedUsers = AppDataManager.connectedUsers.value
        val persistedInbox = AppDataManager.inboxFeedChats.value

        // CRITICAL: Never revert a Success state to Loading. When navigating
        // back to the connections list the previously loaded data must remain
        // visible while the background refresh runs. Only show Loading when
        // no real data has ever been emitted.
        val alreadyHasRealData = _chatListState.value is ChatListState.Success
        val fromConnectionsOnly = buildCachedChats(cachedConnections, cachedUsers, userId)
        val cachedSeedChats =
            collapseOneToOneChatsByPeer(
                chats =
                    applyChatListVisibility(
                        if (persistedInbox.isNotEmpty()) {
                            enrichInboxRowsFromConnectedUsers(persistedInbox, cachedUsers)
                        } else {
                            fromConnectionsOnly
                        },
                    ),
                viewerUserId = userId,
                activityTs = { chatListActivityTimestamp(it) },
            )
        if (!alreadyHasRealData) {
            if (cachedSeedChats.isNotEmpty()) {
                _chatListState.value =
                    ChatListState.Success(
                        applyUnreadClearHintsToInboxRows(cachedSeedChats),
                    )
            } else {
                _chatListState.value = ChatListState.Loading
            }
        }

        if (!isForced) {
            awaitAppDataStartupIfNeeded()
            if (AppDataManager.isInboxFeedFresh()) {
                val rows =
                    buildChatListRowsFromAppDataCache(
                        userId = userId,
                        cachedConnections = cachedConnections,
                        cachedUsers = cachedUsers,
                        persistedInbox = persistedInbox,
                    )
                chatRepository.seedInboxChatRouting(rows)
                _chatListState.value = ChatListState.Success(rows)
                prefetchChatPayloads(userId, rows)
                return@launch
            }
        }

        // Build direct and group streams with immediate empty emissions so the
        // list can paint direct chats while group chats continue loading.
        try {
            val directChatsFlow: Flow<Pair<List<ChatWithDetails>, Boolean>> =
                flow {
                    val directChats = chatRepository.fetchDirectUserChatsWithDetails(userId)
                    val archivedChats = chatRepository.fetchArchivedUserChatsWithDetails(userId)
                    // Single emission: an intermediate "direct-only" payload made the Archived tab
                    // count flicker to 0 until the archived fetch completed (tap-back + loadChats path).
                    emit(
                        dedupeOneToOneChatsByPeer(
                            (directChats + archivedChats).distinctBy { it.connection.id },
                        ) to true,
                    )
                }.onStart {
                    emit(emptyList<ChatWithDetails>() to false)
                }

            val groupChatsFlow: Flow<Pair<List<ChatWithDetails>, Boolean>> =
                flow {
                    val result = runCatching { chatRepository.fetchGroupUserChatsWithDetails(userId) }
                    result.fold(
                        onSuccess = { emit(it to true) },
                        onFailure = { e ->
                            println(
                                "ChatViewModel: group chats fetch failed (preserving prior groups): " +
                                    e.redactedRestMessage(),
                            )
                            // groupLoaded=false → keep previously painted group rows; do not
                            // persist a direct-only inbox that wipes cliques from disk.
                            emit(emptyList<ChatWithDetails>() to false)
                        },
                    )
                }.onStart {
                    emit(emptyList<ChatWithDetails>() to false)
                }

            combine(directChatsFlow, groupChatsFlow) { directState, groupState ->
                val (directChats, directLoaded) = directState
                val (fetchedGroups, groupLoaded) = groupState
                val priorGroups =
                    (_chatListState.value as? ChatListState.Success)
                        ?.chats
                        ?.filter { it.groupClique != null }
                        .orEmpty()
                val persistedGroups = persistedInbox.filter { it.groupClique != null }
                // Empty successful fetch + prior groups usually means RLS returned nothing
                // under a bad/missing JWT (no exception). Keep prior rows and skip persist.
                val emptyFetchLooksPoisoned =
                    groupLoaded &&
                        fetchedGroups.isEmpty() &&
                        (priorGroups.isNotEmpty() || persistedGroups.isNotEmpty())
                val groupChats =
                    when {
                        groupLoaded && !emptyFetchLooksPoisoned -> fetchedGroups
                        priorGroups.isNotEmpty() -> priorGroups
                        else -> persistedGroups
                    }
                CombinedInboxState(
                    chats =
                        dedupeOneToOneChatsByPeer(
                            (directChats + groupChats)
                                .distinctBy { it.connection.id }
                                .sortedByDescending { chatListActivityTimestamp(it) },
                        ),
                    directLoaded = directLoaded,
                    groupLoaded = groupLoaded && !emptyFetchLooksPoisoned,
                )
            }.collect { combinedInbox ->
                val chats = combinedInbox.chats

                // Direct 1:1 chat data drives the primary Clicks list. Do not emit
                // an empty success state before direct rows have loaded.
                if (!combinedInbox.directLoaded) {
                    return@collect
                }
                if (alreadyHasRealData && chats.isEmpty() && (!combinedInbox.directLoaded || !combinedInbox.groupLoaded)) {
                    return@collect
                }

                if (chats.isNotEmpty()) {
                    // Prefer any already-resolved names from AppDataManager's cache over
                    // freshly-fetched users that still carry "Connection" (can happen when the
                    // RPC resolved names in AppDataManager before the ChatRepository fetch ran).
                    val enriched =
                        chats.map { chat ->
                            val cached = cachedUsers[chat.otherUser.id]
                            if (cached != null &&
                                isResolvedDisplayName(cached.name) &&
                                !isResolvedDisplayName(chat.otherUser.name)
                            ) {
                                chat.copy(otherUser = cached)
                            } else {
                                chat
                            }
                        }
                    val cachedChatsById =
                        buildCachedChats(cachedConnections, cachedUsers, userId).associateBy { it.connection.id }
                    val paintedListRowsById =
                        (_chatListState.value as? ChatListState.Success)
                            ?.chats
                            ?.associateBy { it.connection.id }
                            .orEmpty()
                    val mergedWithLocalPreview =
                        enriched.map { apiChat ->
                            val cachedRow =
                                cachedChatsById[apiChat.connection.id]
                                    ?: inboxRowFromCachedThread(
                                        connectionId = apiChat.connection.id,
                                        listRow = apiChat,
                                        connections = cachedConnections,
                                        users = cachedUsers,
                                        userId = userId,
                                    )
                            val paintedRow = paintedListRowsById[apiChat.connection.id]
                            val localSeed =
                                when {
                                    cachedRow != null && paintedRow != null ->
                                        mergeChatRowWithCache(cachedRow, paintedRow, null)
                                    paintedRow != null -> paintedRow
                                    else -> cachedRow
                                }
                            val freshUser =
                                if (apiChat.groupClique == null) {
                                    localSeed?.otherUser ?: cachedUsers[apiChat.otherUser.id]
                                } else {
                                    null
                                }
                            mergeChatRowWithCache(apiChat, localSeed, freshUser)
                        }
                    val visibilityFiltered =
                        applyChatListVisibility(
                            dedupeOneToOneChatsByPeer(mergedWithLocalPreview),
                        )
                    pruneStaleReadClearedHints(visibilityFiltered)
                    val collapsed =
                        collapseOneToOneChatsByPeer(
                            chats = visibilityFiltered,
                            viewerUserId = userId,
                            activityTs = { chatListActivityTimestamp(it) },
                        )
                    val finalRows = applyUnreadClearHintsToInboxRows(collapsed)
                    chatRepository.seedInboxChatRouting(finalRows)
                    _chatListState.value = ChatListState.Success(finalRows)
                    if (combinedInbox.directLoaded && combinedInbox.groupLoaded) {
                        // Only mark hydrated when at least one clique landed. An empty "success"
                        // under a bad JWT must not freeze a direct-only inbox as fresh.
                        if (finalRows.any { it.groupClique != null }) {
                            AppDataManager.markGroupInboxHydrated()
                        }
                        AppDataManager.persistInboxFeedChats(finalRows)
                        prefetchChatPayloads(userId, finalRows)
                    }
                } else {
                    val hasCachedRows =
                        cachedSeedChats.isNotEmpty() ||
                            (_chatListState.value as? ChatListState.Success)?.chats?.isNotEmpty() == true
                    if (!hasCachedRows) {
                        _chatListState.value = ChatListState.Success(emptyList())
                        if (combinedInbox.directLoaded && combinedInbox.groupLoaded) {
                            // Truly empty account (no directs, no groups) — ok to persist.
                            AppDataManager.markGroupInboxHydrated()
                            AppDataManager.persistInboxFeedChats(emptyList())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Keep an existing list visible; only error on a cold-start failure.
            if (_chatListState.value !is ChatListState.Success) {
                _chatListState.value =
                    ChatListState.Error(
                        e.redactedRestMessage().ifBlank { "Failed to load chats" },
                    )
            }
        }
    }
}

internal suspend fun ChatViewModel.awaitAppDataStartupIfNeeded() {
    if (AppDataManager.isDataLoaded.value) return
    if (!AppDataManager.isLoading.value) return
    withTimeoutOrNull(APP_DATA_STARTUP_WAIT_MS) {
        combine(
            AppDataManager.isDataLoaded,
            AppDataManager.isLoading,
        ) { loaded, loading -> loaded || !loading }
            .first { it }
    }
}

internal fun ChatViewModel.buildChatListRowsFromAppDataCache(
    userId: String,
    cachedConnections: List<Connection>,
    cachedUsers: Map<String, User>,
    persistedInbox: List<ChatWithDetails>,
): List<ChatWithDetails> {
    val fromConnectionsOnly = buildCachedChats(cachedConnections, cachedUsers, userId)
    val seed =
        if (persistedInbox.isNotEmpty()) {
            enrichInboxRowsFromConnectedUsers(persistedInbox, cachedUsers)
        } else {
            fromConnectionsOnly
        }
    val visible = applyChatListVisibility(dedupeOneToOneChatsByPeer(seed))
    pruneStaleReadClearedHints(visible)
    val collapsed =
        collapseOneToOneChatsByPeer(
            chats = visible,
            viewerUserId = userId,
            activityTs = { chatListActivityTimestamp(it) },
        )
    return applyUnreadClearHintsToInboxRows(collapsed)
}

internal fun ChatViewModel.enrichInboxRowsFromConnectedUsers(
    rows: List<ChatWithDetails>,
    cachedUsers: Map<String, User>,
): List<ChatWithDetails> =
    rows.map { chat ->
        if (chat.groupClique == null) {
            val cached = cachedUsers[chat.otherUser.id]
            if (cached != null &&
                isResolvedDisplayName(cached.name) &&
                !isResolvedDisplayName(chat.otherUser.name)
            ) {
                chat.copy(otherUser = cached)
            } else {
                chat
            }
        } else {
            chat.copy(
                groupMemberUsers =
                    chat.groupMemberUsers.map { m ->
                        cachedUsers[m.id]?.takeIf { isResolvedDisplayName(it.name) } ?: m
                    },
            )
        }
    }

/**
 * Builds a list row snapshot from a silently prefetched thread (WhatsApp-style disk cache).
 */
internal fun ChatViewModel.inboxRowFromCachedThread(
    connectionId: String,
    listRow: ChatWithDetails,
    connections: List<Connection>,
    users: Map<String, User>,
    userId: String,
): ChatWithDetails? {
    val thread = AppDataManager.cachedChatThreadFor(connectionId) ?: return null
    val lastMessage = thread.messages.lastOrNull() ?: return null
    val connection =
        connections.find { it.id == connectionId }
            ?: listRow.connection
    val otherUser =
        if (listRow.groupClique == null) {
            val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return null
            users[otherUserId] ?: listRow.otherUser
        } else {
            listRow.otherUser
        }
    val lastAt = listOfNotNull(connection.last_message_at, lastMessage.timeCreated).maxOrNull()
    return listRow.copy(
        chat =
            listRow.chat.copy(
                id = thread.chatId.takeIf { isPersistedApiChatId(it) } ?: listRow.chat.id,
            ),
        connection =
            connection.copy(
                last_message_at = lastAt,
                chat = connection.chat.copy(messages = listOf(lastMessage)),
            ),
        otherUser = otherUser,
        lastMessage = lastMessage,
    )
}

internal fun ChatViewModel.patchChatListRowFromCachedThread(connectionId: String) {
    val state = _chatListState.value as? ChatListState.Success ?: return
    val thread = AppDataManager.cachedChatThreadFor(connectionId) ?: return
    val lastMessage = thread.messages.lastOrNull() ?: return
    val chats = state.chats
    val index = findInboxRowIndex(chats, connectionId, thread.chatId)
    if (index < 0) return
    val row = chats[index]
    val rowTs = row.lastMessage?.timeCreated ?: 0L
    if (rowTs > lastMessage.timeCreated) return
    if (rowTs == lastMessage.timeCreated &&
        row.lastMessage?.content == lastMessage.content &&
        row.lastMessage?.id == lastMessage.id
    ) {
        return
    }
    val userId = _currentUserId.value
    val cachedPatch =
        userId?.let { uid ->
            inboxRowFromCachedThread(
                connectionId = connectionId,
                listRow = row,
                connections = AppDataManager.connections.value,
                users = AppDataManager.connectedUsers.value,
                userId = uid,
            )
        }
    val merged =
        mergeChatRowWithCache(
            listChat = row,
            cachedChat = cachedPatch,
            freshUser = AppDataManager.connectedUsers.value[row.otherUser.id],
        )
    if (merged == row) return
    val updated = chats.toMutableList().apply { this[index] = merged }
    _chatListState.value = ChatListState.Success(applyUnreadClearHintsToInboxRows(updated))
}

internal fun ChatViewModel.buildCachedChats(
    cachedConnections: List<Connection>,
    cachedUsers: Map<String, User>,
    userId: String,
): List<ChatWithDetails> {
    return cachedConnections
        .mapNotNull { connection ->
            val otherUserId = connection.user_ids.firstOrNull { it != userId } ?: return@mapNotNull null
            val otherUser = cachedUsers[otherUserId] ?: User(id = otherUserId, name = "Connection", createdAt = 0L)
            ChatWithDetails(
                chat = connection.chat,
                connection = connection,
                otherUser = otherUser,
                lastMessage = connection.chat.messages.lastOrNull(),
                unreadCount = 0,
            )
        }.sortedByDescending { chatListActivityTimestamp(it) }
}

/**
 * Full Clicks list: active-channel rows (pending/active/kept), server-archived rows, minus
 * soft-removed rows. [ChatListState.Success.chats] is this superset; the UI splits Active vs Archived tabs.
 * Connection time [Connection.expiry] is not used for visibility (archival uses server / [connection_archives]).
 */
internal fun ChatViewModel.applyChatListVisibility(chats: List<ChatWithDetails>): List<ChatWithDetails> {
    val hiddenIds = AppDataManager.hiddenConnectionIds.value
    val archivedIds = AppDataManager.archivedConnectionIds.value
    return chats.filter { chat ->
        // Clique rows are not 1:1 junctions — never drop them for archive/hidden tables.
        if (chat.groupClique != null) return@filter true
        val c = chat.connection
        when {
            c.id in hiddenIds -> false
            c.normalizedConnectionStatus() == "removed" -> false
            c.isArchivedChannelForUser(archivedIds, hiddenIds) -> true
            c.isActiveForUser(archivedIds, hiddenIds) -> true
            else -> false
        }
    }
}

internal fun ChatViewModel.pruneStaleReadClearedHints(rows: List<ChatWithDetails>) {
    if (_readClearedConnectionIds.value.isEmpty()) return
    _readClearedConnectionIds.update { ids ->
        ids
            .filterNot { id ->
                rows.any { it.connection.id == id && it.unreadCount == 0 }
            }.toSet()
    }
}

internal fun ChatViewModel.applyUnreadClearHintsToInboxRows(rows: List<ChatWithDetails>): List<ChatWithDetails> {
    val cleared = _readClearedConnectionIds.value
    val openConnectionId =
        (_chatMessagesState.value as? ChatMessagesState.Success)?.chatDetails?.connection?.id
    if (cleared.isEmpty() && openConnectionId.isNullOrBlank()) return rows
    return rows.map { c ->
        val shouldZero =
            c.connection.id in cleared || (!openConnectionId.isNullOrBlank() && c.connection.id == openConnectionId)
        if (shouldZero && c.unreadCount != 0) {
            c.copy(unreadCount = 0)
        } else {
            c
        }
    }
}

internal fun ChatViewModel.markInboxReadOptimistically(connectionId: String) {
    if (connectionId.isBlank()) return
    _readClearedConnectionIds.update { it + connectionId }
    val cur = _chatListState.value as? ChatListState.Success ?: return
    _chatListState.value =
        ChatListState.Success(
            cur.chats.map { chat ->
                if (chat.connection.id == connectionId) chat.copy(unreadCount = 0) else chat
            },
        )
}

internal fun ChatViewModel.markMessagesReadOptimistically(
    connectionId: String,
    chatId: String,
    userId: String,
) {
    markInboxReadOptimistically(connectionId)
    viewModelScope.launch {
        chatRepository.markMessagesAsRead(chatId, userId)
    }
}

internal fun ChatViewModel.bumpInboxUnreadLocally(
    connectionId: String,
    atLeast: Int = 1,
) {
    if (connectionId.isBlank() || atLeast <= 0) return
    val cur = _chatListState.value as? ChatListState.Success ?: return
    val viewerId = _currentUserId.value
    _chatListState.value =
        ChatListState.Success(
            cur.chats.map { chat ->
                if (chat.connection.id != connectionId) {
                    chat
                } else {
                    val nextUnread = maxOf(chat.unreadCount, atLeast)
                    val peerLast = chat.lastMessage?.takeIf { it.user_id != viewerId }
                    val nextLast = peerLast?.copy(isRead = false) ?: chat.lastMessage
                    chat.copy(unreadCount = nextUnread, lastMessage = nextLast)
                }
            },
        )
}

internal fun ChatViewModel.markConversationUnreadImpl(connectionId: String) {
    if (connectionId.isBlank()) return
    viewModelScope.launch {
        val row =
            (_chatListState.value as? ChatListState.Success)
                ?.chats
                ?.firstOrNull { it.connection.id == connectionId }
        val chatId =
            row?.chat?.id?.takeIf { it.isNotBlank() }
                ?: chatRepository.ensureChatForConnection(connectionId)?.id?.takeIf { it.isNotBlank() }
                ?: return@launch
        _readClearedConnectionIds.update { it - connectionId }
        bumpInboxUnreadLocally(connectionId, atLeast = 1)
        chatRepository.markChatAsUnread(chatId)
    }
}

internal fun ChatViewModel.chatListActivityTimestamp(chat: ChatWithDetails): Long =
    chat.connection.last_message_at
        ?: chat.lastMessage?.timeCreated
        ?: chat.connection.created

/**
 * Prefer the connection row that still carries [Connection.connectionEncounters] (or any
 * non-blank [location_name]) so list refresh / timestamp merges never drop timeline data.
 */

/**
 * Prefer the connection row that still carries [Connection.connectionEncounters] (or any
 * non-blank [location_name]) so list refresh / timestamp merges never drop timeline data.
 */
internal fun ChatViewModel.richerConnectionEncounters(
    a: Connection,
    b: Connection,
): List<ConnectionEncounter> =
    compose.project.click.click.data.models.richerConnectionEncounters(
        a.connectionEncounters,
        b.connectionEncounters,
    )

/**
 * Reconcile a server/AppDataManager-derived row with the in-memory chat list without
 * clobbering fresher [lastMessage] / [Connection.last_message_at] from realtime or send paths.
 */

/**
 * Reconcile a server/AppDataManager-derived row with the in-memory chat list without
 * clobbering fresher [lastMessage] / [Connection.last_message_at] from realtime or send paths.
 */
internal fun ChatViewModel.mergeChatRowWithCache(
    listChat: ChatWithDetails,
    cachedChat: ChatWithDetails?,
    freshUser: User?,
): ChatWithDetails {
    if (cachedChat == null) {
        return if (freshUser != null &&
            freshUser != listChat.otherUser &&
            (isResolvedDisplayName(freshUser.name) || !isResolvedDisplayName(listChat.otherUser.name))
        ) {
            listChat.copy(otherUser = freshUser)
        } else {
            listChat
        }
    }

    val listTs = chatListActivityTimestamp(listChat)
    val cacheTs = chatListActivityTimestamp(cachedChat)
    val bestLast =
        when {
            listChat.lastMessage == null -> cachedChat.lastMessage
            cachedChat.lastMessage == null -> listChat.lastMessage
            listChat.lastMessage.timeCreated >= cachedChat.lastMessage.timeCreated -> listChat.lastMessage
            else -> cachedChat.lastMessage
        }
    val preferredConnection = if (listTs >= cacheTs) listChat.connection else cachedChat.connection
    val mergedAt =
        listOfNotNull(
            listChat.connection.last_message_at,
            cachedChat.connection.last_message_at,
            bestLast?.timeCreated,
        ).maxOrNull()
    // Keep [bestLast] even when server last_message_at is slightly ahead of message.timeCreated
    // (DB trigger clock skew). Clearing here caused live inbox previews to vanish until open-chat.
    val mergedChat =
        if (bestLast != null) {
            preferredConnection.chat.copy(messages = listOf(bestLast))
        } else {
            preferredConnection.chat
        }
    val mergedEncounters =
        richerConnectionEncounters(
            listChat.connection,
            cachedChat.connection,
        )
    val mergedConnection =
        preferredConnection.copy(
            last_message_at = mergedAt ?: preferredConnection.last_message_at,
            chat = mergedChat,
            connectionEncounters = mergedEncounters,
        )
    val resolvedOther =
        when {
            freshUser != null &&
                freshUser != listChat.otherUser &&
                (isResolvedDisplayName(freshUser.name) || !isResolvedDisplayName(listChat.otherUser.name)) -> freshUser
            cachedChat.otherUser != listChat.otherUser &&
                (
                    isResolvedDisplayName(
                        cachedChat.otherUser.name,
                    ) ||
                        !isResolvedDisplayName(listChat.otherUser.name)
                ) -> cachedChat.otherUser
            else -> listChat.otherUser
        }
    val mergedUnread = maxOf(listChat.unreadCount, cachedChat.unreadCount)
    return listChat.copy(
        connection = mergedConnection,
        lastMessage = bestLast,
        otherUser = resolvedOther,
        unreadCount = mergedUnread,
    )
}

internal fun ChatViewModel.findInboxRowIndex(
    chats: List<ChatWithDetails>,
    listKey: String,
    chatId: String? = null,
): Int =
    chats.indexOfFirst { row ->
        row.connection.id == listKey ||
            row.chat.id == listKey ||
            (!chatId.isNullOrBlank() && row.chat.id == chatId)
    }

internal fun ChatViewModel.updateConnectionState(
    connectionId: String,
    transform: (Connection) -> Connection,
) {
    val currentListState = _chatListState.value
    if (currentListState is ChatListState.Success) {
        _chatListState.value =
            currentListState.copy(
                chats =
                    currentListState.chats.map { chat ->
                        if (chat.connection.id == connectionId) {
                            chat.copy(connection = transform(chat.connection))
                        } else {
                            chat
                        }
                    },
            )
    }

    val currentMessageState = _chatMessagesState.value
    if (currentMessageState is ChatMessagesState.Success && currentMessageState.chatDetails.connection.id == connectionId) {
        _chatMessagesState.value =
            currentMessageState.copy(
                chatDetails =
                    currentMessageState.chatDetails.copy(
                        connection = transform(currentMessageState.chatDetails.connection),
                    ),
            )
    }
}

internal fun ChatViewModel.removeConnectionFromCurrentList(connectionId: String) {
    val state = _chatListState.value
    if (state is ChatListState.Success) {
        _chatListState.value =
            state.copy(
                chats = state.chats.filter { it.connection.id != connectionId },
            )
    }
}

internal fun ChatViewModel.reapplyChatListVisibility() {
    val state = _chatListState.value
    if (state is ChatListState.Success) {
        _chatListState.value = ChatListState.Success(applyChatListVisibility(state.chats))
    }
}
