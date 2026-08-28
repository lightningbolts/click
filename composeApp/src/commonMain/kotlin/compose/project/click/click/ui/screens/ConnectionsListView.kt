@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.InboxNudgeDto // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.collapseOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.deeplink.EventDeepLinkRouter // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionItem // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMemberPickerSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMenuAction // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialogs // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerContext // pragma: allowlist secret
import compose.project.click.click.ui.chat.RememberMeStrip // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionListActivityTs // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AppEmptyState // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenScaffold // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickCircularGlassIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickListRowShimmer // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionsSegmentBar // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.InboxNudgeBanner // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberFabAboveNavPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberGlassToastState // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.util.ViewerAvailabilityBubblesCache // pragma: allowlist secret
import compose.project.click.click.util.dedupeOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.util.prefetchAvailabilityOverlapsForPeers // pragma: allowlist secret
import compose.project.click.click.util.redactedRestMessage // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatListState // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.VerifiedCliqueProximityIntent // pragma: allowlist secret
import kotlinx.coroutines.Dispatchers // pragma: allowlist secret
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // pragma: allowlist secret
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsListView(
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onOpenSearch: (() -> Unit)? = null,
    onChatSelected: (String) -> Unit,
    onHubSelected: ((ActiveHubEntry) -> Unit)? = null,
    onNavigateToLocationSettings: (() -> Unit)? = null,
    onUserProfileClick: (String) -> Unit = {},
    onGroupMembersPicker: (GroupMembersPickerContext) -> Unit = {},
    verifiedCliqueProximityAutofill: VerifiedCliqueProximityIntent? = null,
    onVerifiedCliqueProximityAutofillConsumed: () -> Unit = {},
    isListObscured: Boolean = false,
) {
    val chatListState by viewModel.chatListState.collectAsState()
    val decryptedPreviews by viewModel.decryptedPreviews.collectAsState()
    val archivedConnectionIds by viewModel.archivedConnectionIds.collectAsState()
    val hiddenConnectionIds by viewModel.hiddenConnectionIds.collectAsState()
    val coreConnectionIds by AppDataManager.coreConnectionIds.collectAsState()
    val cachedChatThreads by AppDataManager.cachedChatThreads.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val activeHubs by AppDataManager.activeHubs.collectAsState()
    val fabAboveNav = rememberFabAboveNavPadding()
    val nudgeResult by viewModel.nudgeResult.collectAsState()
    val toastState = rememberGlassToastState()
    var selectedTabIndex by remember { mutableStateOf(0) } // 0 = Active, 1 = Groups, 2 = Archived
    val tabContentOffsetX = remember { Animatable(0f) }
    val tabContentAlpha = remember { Animatable(1f) }
    var previousTabIndexForAnim by remember { mutableStateOf(selectedTabIndex) }
    var hasInitializedTabAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTabIndex) {
        if (!hasInitializedTabAnimation) {
            hasInitializedTabAnimation = true
            previousTabIndexForAnim = selectedTabIndex
            return@LaunchedEffect
        }

        val direction = if (selectedTabIndex >= previousTabIndexForAnim) 1f else -1f
        previousTabIndexForAnim = selectedTabIndex
        tabContentOffsetX.snapTo(direction * 36f)
        tabContentAlpha.snapTo(0.88f)
        coroutineScope {
            launch {
                tabContentOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                )
            }
            launch {
                tabContentAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                )
            }
        }
    }

    // Connection menu state: holds the chatWithDetails for which the menu is open
    var pendingMenuChat by remember { mutableStateOf<ChatWithDetails?>(null) }
    var pendingHubMenu by remember { mutableStateOf<ActiveHubEntry?>(null) }

    var cliqueSheetVisible by remember { mutableStateOf(false) }
    var selectedCliqueFriendIds by remember { mutableStateOf(setOf<String>()) }
    val listScope = rememberCoroutineScope()
    val inboxNudgeApi = remember { ApiClient() }
    var inboxNudges by remember { mutableStateOf<List<InboxNudgeDto>>(emptyList()) }
    var proximityCliqueHintUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var cliqueProximityAutofillLoading by remember { mutableStateOf(false) }

    LaunchedEffect(verifiedCliqueProximityAutofill) {
        val intent =
            verifiedCliqueProximityAutofill ?: run {
                cliqueProximityAutofillLoading = false
                return@LaunchedEffect
            }
        cliqueProximityAutofillLoading = true
        proximityCliqueHintUsers =
            intent.matchedUsers.filter { it.id in intent.preselectFriendIds.toSet() }
        selectedCliqueFriendIds = intent.preselectFriendIds.toSet()
        selectedTabIndex = 0
        cliqueSheetVisible = true
        viewModel.loadChats(isForced = true)
        withTimeoutOrNull(4_500L) {
            snapshotFlow {
                val chats =
                    when (val s = chatListState) {
                        is ChatListState.Success -> s.chats
                        else -> emptyList()
                    }
                val pickable =
                    chats.filter {
                        it.groupClique == null &&
                            it.connection.normalizedConnectionStatus() != "removed"
                    }
                intent.preselectFriendIds.all { pid -> pickable.any { it.otherUser.id == pid } }
            }.first { it }
        }
        cliqueProximityAutofillLoading = false
        onVerifiedCliqueProximityAutofillConsumed()
    }

    fun dismissVerifiedCliqueSheet(onAfterHide: () -> Unit = {}) {
        cliqueSheetVisible = false
        proximityCliqueHintUsers = emptyList()
        onAfterHide()
    }

    val connectionsLazyListState =
        rememberSaveable(saver = LazyListState.Saver) {
            LazyListState(0, 0)
        }

    // Render only the unified inbox payload emitted by ChatViewModel.
    val effectiveChats: List<ChatWithDetails> =
        when (val state = chatListState) {
            is ChatListState.Success -> state.chats
            else -> emptyList()
        }

    var viewerAvailabilityBubbles by remember(currentUserId) {
        mutableStateOf<List<ProfileAvailabilityIntentBubble>?>(null)
    }
    var overlapPrefetchGeneration by remember(currentUserId) { mutableStateOf(0) }
    LaunchedEffect(currentUserId) {
        val userId =
            currentUserId?.takeIf { it.isNotBlank() } ?: run {
                viewerAvailabilityBubbles = null
                ViewerAvailabilityBubblesCache.clear()
                return@LaunchedEffect
            }
        val cached = ViewerAvailabilityBubblesCache.get(userId)
        if (cached != null) {
            viewerAvailabilityBubbles = cached
            return@LaunchedEffect
        }
        val overlapRepo = SupabaseRepository()
        try {
            viewerAvailabilityBubbles =
                withContext(Dispatchers.Default) {
                    overlapRepo.fetchPeerProfileAvailabilityBubbles(userId, userId)
                }.also { ViewerAvailabilityBubblesCache.put(userId, it) }
        } catch (e: Exception) {
            viewerAvailabilityBubbles = emptyList()
            ViewerAvailabilityBubblesCache.put(userId, emptyList())
            println("ConnectionsListView: viewer availability bubbles: ${e.redactedRestMessage()}")
        }
    }

    val activeChats =
        remember(effectiveChats, archivedConnectionIds, hiddenConnectionIds, currentUserId) {
            collapseOneToOneChatsByPeer(
                chats =
                    effectiveChats.filter {
                        it.groupClique == null &&
                            it.connection.isActiveForUser(archivedConnectionIds, hiddenConnectionIds)
                    },
                viewerUserId = currentUserId,
                activityTs = { connectionListActivityTs(it) },
            )
        }
    val activeOneToOneChats =
        remember(activeChats) {
            activeChats.sortedByDescending { connectionListActivityTs(it) }
        }
    val rememberMeChats =
        remember(activeChats, coreConnectionIds, currentUserId) {
            collapseOneToOneChatsByPeer(
                chats =
                    activeChats.filter {
                        it.groupClique == null && it.connection.id in coreConnectionIds
                    },
                viewerUserId = currentUserId,
                activityTs = { connectionListActivityTs(it) },
            ).sortedByDescending { connectionListActivityTs(it) }
        }
    val showRememberMeStrip =
        selectedTabIndex == 0 && searchQuery.isBlank() && rememberMeChats.isNotEmpty()

    LaunchedEffect(currentUserId, selectedTabIndex) {
        if (currentUserId.isNullOrBlank() || selectedTabIndex != 0) return@LaunchedEffect
        inboxNudges =
            inboxNudgeApi
                .getInboxNudges()
                .getOrNull()
                ?.nudges
                .orEmpty()
                .take(2)
    }

    val rememberMeConnectionIds =
        remember(rememberMeChats) {
            rememberMeChats.map { it.connection.id }.toSet()
        }

    LaunchedEffect(currentUserId, viewerAvailabilityBubbles, activeOneToOneChats) {
        val userId = currentUserId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val mine = viewerAvailabilityBubbles ?: return@LaunchedEffect
        val peerIds = activeOneToOneChats.map { it.otherUser.id }
        if (peerIds.isEmpty()) return@LaunchedEffect
        val fetched =
            prefetchAvailabilityOverlapsForPeers(
                viewerUserId = userId,
                peerUserIds = peerIds,
                viewerBubbles = mine,
            )
        if (fetched > 0) {
            overlapPrefetchGeneration++
        }
    }

    val groupChats =
        remember(effectiveChats) {
            // Group cliques use a synthetic connection id (= group id). Do not gate them on
            // connection_archives / connection_hidden — those junctions are for 1:1 edges only.
            effectiveChats.filter { it.groupClique != null }
        }
    val archivedChats =
        remember(effectiveChats, archivedConnectionIds, hiddenConnectionIds, currentUserId) {
            collapseOneToOneChatsByPeer(
                chats =
                    effectiveChats.filter {
                        it.groupClique == null &&
                            it.connection.isArchivedChannelForUser(archivedConnectionIds, hiddenConnectionIds)
                    },
                viewerUserId = currentUserId,
                activityTs = { connectionListActivityTs(it) },
            )
        }
    val sortedTabChats =
        remember(
            activeChats,
            groupChats,
            archivedChats,
            selectedTabIndex,
            coreConnectionIds,
            showRememberMeStrip,
            rememberMeConnectionIds,
        ) {
            val tabChats =
                when (selectedTabIndex) {
                    0 ->
                        if (showRememberMeStrip) {
                            // Core people live in the Remember Me strip — don't list them again below.
                            activeChats.filter { it.connection.id !in rememberMeConnectionIds }
                        } else {
                            activeChats
                        }
                    1 -> groupChats
                    else -> archivedChats
                }
            tabChats.sortedWith(
                compareByDescending<ChatWithDetails> { it.connection.id in coreConnectionIds }
                    .thenByDescending { connectionListActivityTs(it) },
            )
        }
    val filteredChats =
        remember(sortedTabChats, searchQuery) {
            if (searchQuery.isBlank()) {
                sortedTabChats
            } else {
                sortedTabChats.filter { chat ->
                    val groupHit =
                        chat.groupClique?.name?.contains(searchQuery, ignoreCase = true) == true
                    val userHit =
                        chat.otherUser.name?.contains(searchQuery, ignoreCase = true) == true
                    groupHit || userHit
                }
            }
        }
    val connectionsDisplayLimit by viewModel.connectionsDisplayLimit.collectAsState()
    val displayedChats =
        remember(filteredChats, connectionsDisplayLimit, searchQuery) {
            val page = if (searchQuery.isNotBlank()) filteredChats else filteredChats.take(connectionsDisplayLimit)
            dedupeOneToOneChatsByPeer(page.distinctBy { it.connection.id })
        }
    LaunchedEffect(connectionsLazyListState, filteredChats.size, connectionsDisplayLimit, searchQuery) {
        if (searchQuery.isNotBlank()) return@LaunchedEffect
        snapshotFlow {
            connectionsLazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index ?: 0
        }.collect { lastVisible ->
            if (lastVisible >= displayedChats.size - 4 && filteredChats.size > connectionsDisplayLimit) {
                viewModel.loadMoreConnectionsPage()
            }
        }
    }
    LaunchedEffect(selectedTabIndex) {
        viewModel.resetConnectionsDisplayLimit()
        // Groups tab: always force a network inbox reload when clique rows are missing so we
        // recover from a direct-only poisoned disk cache (hubs use a separate store).
        if (selectedTabIndex == 1 && groupChats.isEmpty()) {
            viewModel.loadChats(isForced = true)
        }
    }

    /** Verified-click picker: every non-group 1:1 edge still in the inbox, including pending and archived-tab rows. */
    val verifiedCliquePickableOneToOneChats =
        remember(effectiveChats) {
            effectiveChats
                .filter {
                    it.groupClique == null &&
                        it.connection.normalizedConnectionStatus() != "removed"
                }.sortedByDescending { connectionListActivityTs(it) }
        }

    val chatListRefreshEpoch by AppDataManager.chatListRefreshEpoch.collectAsState()
    LaunchedEffect(chatListRefreshEpoch) {
        if (chatListRefreshEpoch > 0) {
            viewModel.loadChats(isForced = true)
        }
    }

    var cliqueAddableMask by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var cliqueCreateGraphOk by remember { mutableStateOf(false) }

    /** False while edge RPCs run — blocks taps so users cannot pick ineligible friends during load. */
    var cliqueSheetEligibilityReady by remember { mutableStateOf(false) }

    LaunchedEffect(
        cliqueSheetVisible,
        selectedCliqueFriendIds,
        currentUserId,
        verifiedCliquePickableOneToOneChats,
    ) {
        val uid = currentUserId
        if (!cliqueSheetVisible || uid.isNullOrBlank()) {
            cliqueAddableMask = emptyMap()
            cliqueCreateGraphOk = false
            cliqueSheetEligibilityReady = false
            return@LaunchedEffect
        }
        cliqueSheetEligibilityReady = false
        val others = verifiedCliquePickableOneToOneChats.map { it.otherUser.id }
        val uidNonNull = uid
        coroutineScope {
            val mask =
                viewModel.computeVerifiedCliqueAddableMask(
                    baseMemberUserIds = listOf(uidNonNull) + selectedCliqueFriendIds.toList(),
                    candidateUserIds = others,
                    selectedCandidateIds = selectedCliqueFriendIds,
                )
            val fullOk =
                if (selectedCliqueFriendIds.isEmpty()) {
                    false
                } else {
                    viewModel.memberSetSatisfiesVerifiedCliqueGraph(
                        (listOf(uidNonNull) + selectedCliqueFriendIds).distinct().sorted(),
                    )
                }
            cliqueAddableMask = mask
            cliqueCreateGraphOk = fullOk
            cliqueSheetEligibilityReady = true
        }
    }

    val memberSetDuplicatesExistingClick =
        remember(
            effectiveChats,
            selectedCliqueFriendIds,
            currentUserId,
        ) {
            val uid = currentUserId ?: return@remember false
            if (selectedCliqueFriendIds.isEmpty()) return@remember false
            val target = (selectedCliqueFriendIds + uid).toSet()
            effectiveChats.any { chat ->
                chat.groupClique != null &&
                    chat.groupClique.memberUserIds.toSet() == target
            }
        }

    DisposableEffect(Unit) {
        onDispose { toastState.dismiss() }
    }
    LaunchedEffect(isListObscured) {
        if (isListObscured) {
            toastState.dismiss()
        }
    }

    // Show nudge feedback (clear before show so returning to this tab does not replay).
    LaunchedEffect(nudgeResult) {
        val result = nudgeResult ?: return@LaunchedEffect
        viewModel.clearNudgeResult()
        toastState.show(listScope, result)
    }

    val activeCount = activeChats.size
    val groupCount = groupChats.size + activeHubs.size
    val archivedCount = archivedChats.size
    val headerSubtitle =
        remember(
            effectiveChats,
            selectedTabIndex,
            searchQuery,
            chatListState,
            activeCount,
            groupCount,
            archivedCount,
            activeHubs.size,
            filteredChats.size,
        ) {
            if (effectiveChats.isEmpty() && activeHubs.isEmpty()) {
                if (chatListState is ChatListState.Loading) "Loading…" else ""
            } else {
                val tabCount =
                    when (selectedTabIndex) {
                        0 -> activeChats.size
                        1 -> groupChats.size + activeHubs.size
                        else -> archivedChats.size
                    }
                val filteredCount =
                    if (searchQuery.isNotBlank()) {
                        filteredChats.size +
                            if (selectedTabIndex == 1) {
                                activeHubs.count { it.name.contains(searchQuery, ignoreCase = true) }
                            } else {
                                0
                            }
                    } else {
                        tabCount
                    }
                val tabLabel =
                    when (selectedTabIndex) {
                        0 -> "active"
                        1 -> "group"
                        else -> "archived"
                    }
                if (searchQuery.isNotBlank()) {
                    "$filteredCount result${if (filteredCount == 1) "" else "s"} for \"$searchQuery\""
                } else {
                    "$filteredCount $tabLabel ${if (filteredCount == 1) "connection" else "connections"}"
                }
            }
        }
    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppScreenScaffold(
                    title = "Clicks",
                    subtitle = headerSubtitle.takeIf { it.isNotBlank() },
                    onOpenSearch = onOpenSearch,
                    lazyListState = connectionsLazyListState,
                    belowHeaderSpacing = 8.dp,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier.graphicsLayer {
                            translationX = tabContentOffsetX.value
                            alpha = tabContentAlpha.value
                        },
                ) {
                    if (effectiveChats.isNotEmpty() || activeHubs.isNotEmpty()) {
                        item(key = "clicks_segment_bar", contentType = "segment_bar") {
                            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                                ConnectionsSegmentBar(
                                    selectedTabIndex = selectedTabIndex,
                                    onTabSelected = { selectedTabIndex = it },
                                    activeCount = activeCount,
                                    groupCount = groupCount,
                                    archivedCount = archivedCount,
                                )
                            }
                        }
                    }
                    if (effectiveChats.isEmpty() && chatListState is ChatListState.Loading) {
                        items(
                            items = listOf(0, 1, 2, 3, 4, 5),
                            key = { "clicks_shimmer_$it" },
                            contentType = { "shimmer" },
                        ) {
                            ClickListRowShimmer()
                        }
                    } else if (effectiveChats.isEmpty() && chatListState is ChatListState.Error) {
                        val errorMsg = (chatListState as ChatListState.Error).message
                        item(key = "clicks_error") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Error loading chats",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    errorMsg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.loadChats() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    } else if (
                        filteredChats.isEmpty() &&
                        !(selectedTabIndex == 1 && activeHubs.isNotEmpty())
                    ) {
                        item(key = "clicks_empty") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 360.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppEmptyState(
                                    icon =
                                        if (searchQuery.isNotBlank()) {
                                            Icons.Filled.SearchOff
                                        } else {
                                            Icons.Filled.ChatBubbleOutline
                                        },
                                    title =
                                        if (searchQuery.isNotBlank()) {
                                            "No matches found"
                                        } else if (selectedTabIndex == 1) {
                                            "No group chats"
                                        } else if (selectedTabIndex == 2) {
                                            "No archived connections"
                                        } else {
                                            "No connections yet"
                                        },
                                    body =
                                        if (searchQuery.isNotBlank()) {
                                            "Try a different search term"
                                        } else if (selectedTabIndex == 1) {
                                            "Group clicks will appear here"
                                        } else if (selectedTabIndex == 2) {
                                            "Archived chats will appear here"
                                        } else {
                                            "Start clicking with people nearby!"
                                        },
                                )
                            }
                        }
                    } else {
                        if (showRememberMeStrip) {
                            item(key = "remember_me_strip", contentType = "remember_me") {
                                RememberMeStrip(
                                    chats = rememberMeChats,
                                    onChatSelected = onChatSelected,
                                    onlineUserIds = onlineUsers,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        if (selectedTabIndex == 0 && searchQuery.isBlank() && inboxNudges.isNotEmpty()) {
                            items(
                                inboxNudges,
                                key = { "inbox_nudge_${it.id}" },
                                contentType = { "inbox_nudge" },
                            ) { nudge ->
                                InboxNudgeBanner(
                                    nudge = nudge,
                                    onOpen = {
                                        listScope.launch { inboxNudgeApi.markInboxNudgeActed(nudge.id) }
                                        inboxNudges = inboxNudges.filterNot { it.id == nudge.id }
                                        val eventId = nudge.beaconId?.trim().orEmpty()
                                        if (nudge.nudgeType == "shared_upcoming_event" && eventId.isNotEmpty()) {
                                            EventDeepLinkRouter.setPendingBeaconId(eventId)
                                        } else {
                                            val connectionId = nudge.connectionId?.trim().orEmpty()
                                            val chatId =
                                                displayedChats
                                                    .firstOrNull { it.connection.id == connectionId }
                                                    ?.chat
                                                    ?.id
                                                    ?: connectionId
                                            if (chatId.isNotBlank()) onChatSelected(chatId)
                                        }
                                    },
                                    onDismiss = {
                                        listScope.launch { inboxNudgeApi.dismissInboxNudge(nudge.id) }
                                        inboxNudges = inboxNudges.filterNot { it.id == nudge.id }
                                    },
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        if (selectedTabIndex == 1 && activeHubs.isNotEmpty()) {
                            items(
                                activeHubs.filter { hub ->
                                    searchQuery.isBlank() || hub.name.contains(searchQuery, ignoreCase = true)
                                },
                                key = { "hub_${it.hubId}" },
                                contentType = { "hub" },
                            ) { hub ->
                                ActiveHubFeedRow(
                                    hub = hub,
                                    onClick = { onHubSelected?.invoke(hub) },
                                    onOpenMenu = { pendingHubMenu = hub },
                                    onLongPress = { pendingHubMenu = hub },
                                )
                            }
                        }
                        items(
                            displayedChats,
                            key = { it.connection.id },
                            contentType = { "connection" },
                        ) { chatDetails ->
                            val connectionId = chatDetails.connection.id
                            val cachedThread = cachedChatThreads[connectionId]
                            val cachedPreviewLabel = cachedThread?.messages?.lastOrNull()?.previewLabel()
                            ConnectionItem(
                                chatDetails = chatDetails,
                                viewerUserId = currentUserId,
                                overlapPrefetchGeneration = overlapPrefetchGeneration,
                                isCore = connectionId in coreConnectionIds,
                                showOnlineIndicator =
                                    chatDetails.groupClique == null &&
                                        chatDetails.otherUser.id in onlineUsers,
                                decryptedPreview = decryptedPreviews[connectionId] ?: cachedPreviewLabel,
                                hasCachedThreadPreview = !cachedThread?.messages.isNullOrEmpty(),
                                onAvatarClick = {
                                    if (chatDetails.groupClique == null) {
                                        onUserProfileClick(chatDetails.otherUser.id)
                                    }
                                },
                                onGroupMembersPicker = onGroupMembersPicker,
                                onClick = {
                                    onChatSelected(
                                        chatDetails.chat.id ?: chatDetails.connection.id,
                                    )
                                },
                                onLongPress = { pendingMenuChat = chatDetails },
                            )
                        }
                    }
                }
            }
        }

        val showCreateClickFab =
            (selectedTabIndex == 0 || selectedTabIndex == 1) && currentUserId != null

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .zIndex(50f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, bottom = fabAboveNav),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(end = if (showCreateClickFab) 12.dp else 0.dp),
            ) {
                GlassToastHost(
                    state = toastState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            if (showCreateClickFab) {
                ClickCircularGlassIconButton(
                    icon = Icons.Filled.Groups,
                    contentDescription = "Create verified click",
                    onClick = {
                        selectedCliqueFriendIds = emptySet()
                        cliqueSheetVisible = true
                    },
                    size = 56.dp,
                )
            }
        }

        val cliquePickerCandidates =
            remember(verifiedCliquePickableOneToOneChats) {
                // Duplicate 1:1 rows for the same peer must not enter the picker LazyColumn.
                verifiedCliquePickableOneToOneChats.map { it.otherUser }.distinctBy { it.id }
            }
        val canCreateVerifiedClique =
            cliqueSheetEligibilityReady &&
                selectedCliqueFriendIds.isNotEmpty() &&
                cliqueCreateGraphOk &&
                !memberSetDuplicatesExistingClick

        if (cliqueSheetVisible && currentUserId != null) {
            ConnectionMemberPickerSheet(
                onDismissRequest = { dismissVerifiedCliqueSheet() },
                title = "Create verified click",
                subtitle = "Pick friends who are all connected to each other. Eligibility is verified on the server.",
                candidates = cliquePickerCandidates,
                selectedIds = selectedCliqueFriendIds,
                onSelectedIdsChange = { selectedCliqueFriendIds = it },
                eligibilityMask = cliqueAddableMask,
                eligibilityReady = cliqueSheetEligibilityReady,
                eligibilityCheckingLabel = "Checking who can join…",
                errorMessage =
                    if (memberSetDuplicatesExistingClick) {
                        "You already have a verified click with this group."
                    } else {
                        null
                    },
                onSelectionBlocked = {
                    viewModel.notifyVerifiedCliqueSelectionBlocked()
                },
                primaryButtonLabel = "Create",
                primaryEnabled = canCreateVerifiedClique,
                onPrimaryClick = {
                    viewModel.createVerifiedClique(selectedCliqueFriendIds.toList()) { result ->
                        result.onSuccess {
                            dismissVerifiedCliqueSheet {
                                toastState.show(listScope, "Click created")
                            }
                        }
                        result.onFailure { e ->
                            val raw = e.message?.takeIf { it.isNotBlank() }.orEmpty()
                            val msg =
                                when {
                                    raw.contains("verified click already exists", ignoreCase = true) ->
                                        "You already have a verified click with this group."
                                    else -> raw.ifBlank { "Couldn’t create click" }
                                }
                            toastState.show(listScope, msg)
                        }
                    }
                },
                headerContent = {
                    if (cliqueProximityAutofillLoading) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AdaptiveCircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "Loading your tap group in Clicks…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = GlassSheetTokens.OnOledMuted(),
                            )
                        }
                    }
                    val pickableIdsForHint =
                        remember(verifiedCliquePickableOneToOneChats) {
                            verifiedCliquePickableOneToOneChats.map { it.otherUser.id }.toSet()
                        }
                    val supplementalHintUsers =
                        remember(proximityCliqueHintUsers, pickableIdsForHint) {
                            proximityCliqueHintUsers.filter { it.id !in pickableIdsForHint }
                        }
                    if (supplementalHintUsers.isNotEmpty()) {
                        Text(
                            text = "People from your tap (profiles may still sync)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            supplementalHintUsers.forEach { u ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = GlassSheetTokens.GlassSurface(),
                                ) {
                                    Text(
                                        u.name?.trim()?.ifBlank { null } ?: "Friend",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = GlassSheetTokens.OnOled(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }

        var pendingConnectionDialog by remember { mutableStateOf<ConnectionSheetDialog?>(null) }
        var dialogConnectionId by remember { mutableStateOf<String?>(null) }
        var dialogGroupId by remember { mutableStateOf<String?>(null) }

        // Connection action sheet
        if (pendingMenuChat != null) {
            val selected = pendingMenuChat!!
            val isUserArchived = selected.connection.id in archivedConnectionIds
            val isServerArchived = selected.connection.isServerLifecycleArchived()
            ConnectionActionSheet(
                chatDetails = selected,
                currentUserId = currentUserId,
                isArchived = isUserArchived,
                isServerLifecycleArchived = isServerArchived,
                isCore = selected.connection.id in coreConnectionIds,
                onDismiss = { pendingMenuChat = null },
                onMenuAction = { action ->
                    val connId = selected.connection.id
                    when (action) {
                        ConnectionMenuAction.Nudge -> {
                            val chatId = selected.chat.id
                            if (chatId != null) {
                                viewModel.sendNudgeToChat(chatId, selected.otherUser.name ?: "them")
                            }
                        }
                        ConnectionMenuAction.Archive -> {
                            viewModel.archiveConnectionById(connId) { }
                        }
                        ConnectionMenuAction.Unarchive -> {
                            viewModel.unarchiveConnection(connId)
                        }
                        ConnectionMenuAction.AddToCore -> {
                            viewModel.addConnectionToCore(connId)
                        }
                        ConnectionMenuAction.RemoveFromCore -> {
                            viewModel.removeConnectionFromCore(connId)
                        }
                        ConnectionMenuAction.MarkUnread -> {
                            viewModel.markConversationUnread(connId)
                        }
                        ConnectionMenuAction.RequestRemove -> {
                            dialogConnectionId = connId
                            pendingConnectionDialog = ConnectionSheetDialog.Remove
                        }
                        ConnectionMenuAction.RequestReport -> {
                            dialogConnectionId = connId
                            pendingConnectionDialog = ConnectionSheetDialog.Report()
                        }
                        ConnectionMenuAction.RequestBlock -> {
                            dialogConnectionId = connId
                            pendingConnectionDialog = ConnectionSheetDialog.Block
                        }
                        ConnectionMenuAction.RequestLeaveGroup -> {
                            dialogGroupId = selected.groupClique?.groupId
                            pendingConnectionDialog = ConnectionSheetDialog.LeaveGroup
                        }
                        ConnectionMenuAction.RequestDeleteGroup -> {
                            dialogGroupId = selected.groupClique?.groupId
                            pendingConnectionDialog = ConnectionSheetDialog.DeleteGroup
                        }
                    }
                },
            )
        }

        val dialogConnId = dialogConnectionId
        ConnectionSheetDialogs(
            dialog = pendingConnectionDialog,
            onDismiss = {
                pendingConnectionDialog = null
                dialogConnectionId = null
                dialogGroupId = null
            },
            onConfirmRemove = {
                if (dialogConnId != null) {
                    viewModel.deleteConnectionPermanentlyById(dialogConnId) { }
                }
            },
            onConfirmBlock = {
                if (dialogConnId != null) {
                    viewModel.blockUserForConnection(dialogConnId) { }
                }
            },
            onConfirmReport = { reason ->
                if (dialogConnId != null) {
                    viewModel.reportConnectionForConnection(dialogConnId, reason) { }
                }
            },
            onConfirmLeaveGroup = {
                dialogGroupId?.let { viewModel.leaveVerifiedClique(it) { } }
            },
            onConfirmDeleteGroup = {
                dialogGroupId?.let { viewModel.deleteVerifiedClique(it) { } }
            },
        )

        if (pendingHubMenu != null) {
            HubActionSheet(
                hub = pendingHubMenu!!,
                currentUserId = currentUserId,
                viewModel = viewModel,
                onDismiss = { pendingHubMenu = null },
            )
        }
    } // End outer Box
}
