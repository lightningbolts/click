@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import compose.project.click.click.data.ActiveHubEntry // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.api.ChatApiClient // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.data.models.ProfileAvailabilityIntentBubble // pragma: allowlist secret
import compose.project.click.click.data.models.User // pragma: allowlist secret
import compose.project.click.click.data.models.collapseOneToOneChatsByPeer // pragma: allowlist secret
import compose.project.click.click.data.models.isActiveForUser // pragma: allowlist secret
import compose.project.click.click.data.models.isArchivedChannelForUser // pragma: allowlist secret
import compose.project.click.click.data.models.previewLabel // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionActionSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionItem // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMemberPickerSheet // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionMenuAction // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialog // pragma: allowlist secret
import compose.project.click.click.ui.chat.ConnectionSheetDialogs // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerContext // pragma: allowlist secret
import compose.project.click.click.ui.chat.RememberMeStrip // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionListActivityTs // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionRowPressGestures // pragma: allowlist secret
import compose.project.click.click.ui.chat.connectionRowPressHighlight // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveBackground // pragma: allowlist secret
import compose.project.click.click.ui.components.AppEmptyState // pragma: allowlist secret
import compose.project.click.click.ui.components.AppScreenDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickCircularGlassIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionsFloatingHeader // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassToastHost // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalGlassAlertAnimatedDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.floatingHeaderStatusBarPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.headerCollapseFraction // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberBottomChromePadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberFabAboveNavPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberFloatingHeaderTopPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberGlassToastState // pragma: allowlist secret
import compose.project.click.click.ui.components.rememberStatusBarTopPadding // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetPageBackground // pragma: allowlist secret
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
    /** Bumped when returning from chat so header inset remeasures at expanded height. */
    listRevealEpoch: Int = 0,
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
    val statusBarTop = rememberStatusBarTopPadding()
    val bottomChrome = rememberBottomChromePadding()
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
    LaunchedEffect(listRevealEpoch) {
        if (listRevealEpoch <= 0) return@LaunchedEffect
        // Snap to top so floating-header measure can lock expanded height again.
        connectionsLazyListState.scrollToItem(0)
    }
    val thresholdPx =
        with(LocalDensity.current) {
            AppScreenDefaults.HeaderCollapseScrollThreshold.roundToPx()
        }
    val collapseFraction by remember(connectionsLazyListState, thresholdPx) {
        derivedStateOf { connectionsLazyListState.headerCollapseFraction(thresholdPx) }
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
    val (headerContentPadding, headerMeasureModifier) =
        rememberFloatingHeaderTopPadding(
            collapseFraction = collapseFraction,
            statusBarTop = statusBarTop,
            minimumExpandedBodyHeight = 168.dp,
        )
    val listTopPadding = headerContentPadding

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (effectiveChats.isEmpty() && chatListState is ChatListState.Loading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveCircularProgressIndicator()
                        }
                    } else if (effectiveChats.isEmpty() && chatListState is ChatListState.Error) {
                        val errorMsg = (chatListState as ChatListState.Error).message
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = tabContentOffsetX.value
                                        alpha = tabContentAlpha.value
                                    },
                        ) {
                            if (filteredChats.isEmpty() &&
                                !(selectedTabIndex == 1 && activeHubs.isNotEmpty())
                            ) {
                                val emptyScroll = rememberScrollState()
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(emptyScroll)
                                            .padding(top = listTopPadding),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 360.dp)
                                                .padding(horizontal = 20.dp),
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
                                LazyColumn(
                                    state = connectionsLazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding =
                                        PaddingValues(
                                            start = 20.dp,
                                            end = 20.dp,
                                            top = listTopPadding,
                                            bottom = bottomChrome,
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
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
                                    // ── Active community hubs (shown in Groups tab) ─────
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
                }

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(1f)
                            .fillMaxWidth()
                            .floatingHeaderStatusBarPadding()
                            .padding(start = 20.dp, end = 20.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .then(headerMeasureModifier),
                    ) {
                        ConnectionsFloatingHeader(
                            collapseFraction = collapseFraction,
                            title = "Clicks",
                            subtitle = headerSubtitle.takeIf { it.isNotBlank() },
                            selectedTabIndex = selectedTabIndex,
                            onTabSelected = { selectedTabIndex = it },
                            activeCount = activeCount,
                            groupCount = groupCount,
                            archivedCount = archivedCount,
                            showTabs = effectiveChats.isNotEmpty(),
                            onOpenSearch = onOpenSearch,
                            isScrollInProgress = connectionsLazyListState.isScrollInProgress,
                        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubActionSheet(
    hub: ActiveHubEntry,
    currentUserId: String?,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var details by remember(hub.hubId) {
        mutableStateOf<ChatApiClient.HubDetailsDto?>(null)
    }
    var detailsLoadAttempted by remember(hub.hubId) { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var hubSheetDismissedForDialog by remember { mutableStateOf(false) }
    var confirmTitle by remember { mutableStateOf("") }
    var confirmBody by remember { mutableStateOf("") }
    var confirmButton by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var editNameDraft by remember(hub.hubId) { mutableStateOf(hub.name) }
    var editCategoryDraft by remember(hub.hubId) { mutableStateOf(hub.category) }

    val resolvedCreatorId = details?.creatorId ?: hub.creatorId
    val isCreator = !currentUserId.isNullOrBlank() && currentUserId == resolvedCreatorId

    LaunchedEffect(hub.hubId) {
        viewModel.fetchActiveHubDetails(hub.hubId) { result ->
            scope.launch {
                detailsLoadAttempted = true
                result.onSuccess { loaded ->
                    details = loaded
                    editNameDraft = loaded.name
                    editCategoryDraft = loaded.category
                }
            }
        }
    }

    fun openConfirm(
        title: String,
        body: String,
        button: String,
        action: () -> Unit,
    ) {
        hubSheetDismissedForDialog = true
        confirmTitle = title
        confirmBody = body
        confirmButton = button
        confirmAction = action
        showConfirm = true
    }

    if (!hubSheetDismissedForDialog && !showEditDialog) {
        ClickActionBottomSheet(
            onDismissRequest = onDismiss,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(sheetPageBackground())
                        .padding(bottom = 32.dp),
            ) {
                val title = details?.name ?: hub.name
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassSheetTokens.OnOled(),
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .align(Alignment.CenterHorizontally),
                )
                HorizontalDivider(color = GlassSheetTokens.GlassBorder())

                BentoGlassOptionRow(
                    showBorder = false,
                    title = "Leave Hub",
                    subtitle = "Remove this hub from your list",
                    onClick = {
                        openConfirm(
                            title = "Leave hub?",
                            body = "You will leave this community hub and lose quick access from your Groups list.",
                            button = "Leave",
                        ) {
                            viewModel.leaveActiveHub(hub.hubId) { ok ->
                                if (ok) scope.launch { onDismiss() }
                            }
                        }
                    },
                    destructive = true,
                    leading = {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            tint = Color(0xFFFF6B6B),
                        )
                    },
                )

                if (isCreator) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Edit Hub",
                        subtitle = "Update name and category",
                        onClick = {
                            editNameDraft = details?.name ?: hub.name
                            editCategoryDraft = details?.category ?: hub.category
                            hubSheetDismissedForDialog = true
                            showEditDialog = true
                        },
                        leading = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted(),
                            )
                        },
                    )

                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Delete Hub",
                        subtitle = "Kick all users and delete history",
                        onClick = {
                            openConfirm(
                                title = "Delete hub?",
                                body = "Are you sure? This will kick all users and delete the history.",
                                button = "Delete",
                            ) {
                                viewModel.deleteActiveHub(hub.hubId) { ok ->
                                    if (ok) scope.launch { onDismiss() }
                                }
                            }
                        },
                        destructive = true,
                        leading = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                            )
                        },
                    )
                } else if (!detailsLoadAttempted) {
                    Text(
                        text = "Loading hub options…",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassSheetTokens.OnOledMuted(),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }

                Spacer(
                    modifier =
                        Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth(),
                )
            }
        }
    }

    if (showEditDialog && isCreator) {
        UnifiedPopupFormDialog(
            visible = showEditDialog,
            onDismissRequest = {
                showEditDialog = false
                onDismiss()
            },
            title = "Edit Hub",
            confirmLabel = "Save",
            onConfirm = {
                viewModel.updateActiveHub(
                    hubId = hub.hubId,
                    name = editNameDraft,
                    category = editCategoryDraft,
                ) { ok ->
                    if (ok) {
                        scope.launch {
                            details =
                                details?.copy(
                                    name = editNameDraft.trim(),
                                    category = editCategoryDraft.trim(),
                                )
                            showEditDialog = false
                        }
                    }
                }
            },
            body = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ClickOutlinedTextField(
                        value = editNameDraft,
                        onValueChange = { editNameDraft = it.take(80) },
                        singleLine = true,
                        label = { Text("Hub name", color = GlassSheetTokens.OnOledMuted()) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassSheetTokens.OnOled(),
                                unfocusedTextColor = GlassSheetTokens.OnOled(),
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                                cursorColor = PrimaryBlue,
                                focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                                unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                            ),
                    )
                    ClickOutlinedTextField(
                        value = editCategoryDraft,
                        onValueChange = { editCategoryDraft = it.take(40) },
                        singleLine = true,
                        label = { Text("Category", color = GlassSheetTokens.OnOledMuted()) },
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GlassSheetTokens.OnOled(),
                                unfocusedTextColor = GlassSheetTokens.OnOled(),
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = GlassSheetTokens.GlassBorder(),
                                cursorColor = PrimaryBlue,
                                focusedLabelColor = GlassSheetTokens.OnOledMuted(),
                                unfocusedLabelColor = GlassSheetTokens.OnOledMuted(),
                            ),
                    )
                }
            },
        )
    }

    if (showConfirm) {
        GlassAlertDialog(
            onDismissRequest = {
                showConfirm = false
                onDismiss()
            },
            title = { Text(confirmTitle) },
            text = { Text(confirmBody) },
            confirmButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(
                    onClick = {
                        confirmAction?.invoke()
                        dismissAnimated()
                    },
                ) {
                    Text(confirmButton, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(onClick = dismissAnimated) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted())
                }
            },
        )
    }
}

@Composable
private fun ActiveHubFeedRow(
    hub: ActiveHubEntry,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    onLongPress: () -> Unit,
) {
    val rowInteraction = remember { MutableInteractionSource() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner))
                .border(
                    width = 2.dp,
                    color = GlassSheetTokens.GlassBorder(),
                    shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner),
                ).background(GlassSheetTokens.GlassSurface())
                .connectionRowPressHighlight(rowInteraction)
                .connectionRowPressGestures(
                    interactionSource = rowInteraction,
                    onClick = onClick,
                    onLongPress = onLongPress,
                ).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = hub.name,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${hub.occupantCount} ${if (hub.occupantCount == 1) "person" else "people"} • Community Hub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = onOpenMenu,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Hub options",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
