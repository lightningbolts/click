@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.platform.KeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.platform.rememberKeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentMenuRow // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChannelLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeHorizontalPadding // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStrip // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStripReserve // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatHeaderIconButton // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatInterMessageHubBaseCompact // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatLiquidGlassPlate // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageTimeline // pragma: allowlist secret
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatDismissKeyboardAfterScrollConnection // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineFollowUsesAnimation // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimelineShouldFollowInbound // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft // pragma: allowlist secret
import compose.project.click.click.ui.chat.isTimestampPeekRevealed // pragma: allowlist secret
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx // pragma: allowlist secret
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay // pragma: allowlist secret
import compose.project.click.click.ui.chat.scrollChatTimelineToLatest // pragma: allowlist secret
import compose.project.click.click.ui.components.BentoGlassOptionRow // pragma: allowlist secret
import compose.project.click.click.ui.components.BindPlatformNativeNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickActionBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickOutlinedTextField // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalGlassAlertAnimatedDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.chatThreadKeyboardDock // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetPageBackground // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.HubChatNavigationEvent // pragma: allowlist secret
import compose.project.click.click.viewmodel.HubChatViewModel // pragma: allowlist secret
import compose.project.click.click.viewmodel.HubRealtimeState // pragma: allowlist secret
import kotlinx.coroutines.Job

data class HubChatNavArgs(
    val hubId: String,
    val realtimeChannel: String,
    val hubTitle: String,
    val creatorId: String? = null,
    val hubCategory: String = "general",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubChatScreen(
    args: HubChatNavArgs,
    currentUserId: String,
    onNavigateBack: () -> Unit,
    resolveHubGatekeeperLocation: suspend () -> LocationResult? = { null },
    /**
     * When true, timestamp peek is driven by the parent `InteractiveSwipeBackContainer` horizontal
     * drag (register callbacks with [onRegisterSwipeBackRightToLeftPeek]).
     */
    integrateTimestampPeekWithSwipeBackContainer: Boolean = false,
    onRegisterSwipeBackRightToLeftPeek: (InteractiveSwipeBackRightToLeftPeek?) -> Unit = {},
    parentInteractiveBackSwipePx: androidx.compose.runtime.MutableFloatState? = null,
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
) {
    val viewModel: HubChatViewModel =
        viewModel(key = args.realtimeChannel) {
            HubChatViewModel(
                hubId = args.hubId,
                realtimeChannelName = args.realtimeChannel,
                hubTitle = args.hubTitle,
                currentUserId = currentUserId,
                hubCategory = args.hubCategory,
                creatorId = args.creatorId,
                hubLocationResolver = resolveHubGatekeeperLocation,
            )
        }

    val messages by viewModel.messages.collectAsState()
    val occupantCount by viewModel.occupantCount.collectAsState()
    val outOfBounds by viewModel.outOfBounds.collectAsState()

    val isCreator by viewModel.isCreator.collectAsState()
    val resolvedCreatorId by viewModel.resolvedCreatorId.collectAsState()
    val hubDetails by viewModel.hubDetails.collectAsState()
    var settingsMenuExpanded by remember { mutableStateOf(false) }
    val nativeNavChrome = LocalPlatformStyle.current.isIOS
    if (nativeNavChrome) {
        BindPlatformNativeNavigationBar(
            title = hubDetails.name.ifBlank { args.hubTitle },
            subtitle = "$occupantCount people in this hub",
            onNavigateBack = onNavigateBack,
            nativeTrailingActions =
                listOf(
                    NativeChromeAction(
                        sfSymbol = "ellipsis",
                        contentDescription = "Hub settings",
                        onClick = { settingsMenuExpanded = true },
                    ),
                ),
            collapseFraction = 1f,
        )
    }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editNameDraft by remember { mutableStateOf(hubDetails.name) }
    var editCategoryDraft by remember { mutableStateOf(hubDetails.category) }

    val mediaPickers =
        rememberChatMediaPickers(
            onImagePicked = { bytes, mime -> viewModel.sendHubImageFromPicker(bytes, mime) },
            onAudioPicked = { _, _, _ -> },
            onMediaAccessBlocked = { },
        )

    val hubIdForSecureMedia = remember(args.hubId) { args.hubId }
    val hubPeekScope = rememberCoroutineScope()
    val hubListState = remember(args.realtimeChannel) { LazyListState() }
    val density = LocalDensity.current
    val nativeKeyboardInsets =
        rememberChatNativeKeyboardInsets(
            keyboardHeightProvider = keyboardHeightProvider,
            subtractTabBarOverlay = false,
        )
    val focusManager = LocalFocusManager.current
    val focusManagerState = rememberUpdatedState(focusManager)
    val suppressKeyboardDismissWhileProgrammaticTimelineScroll = remember { mutableStateOf(false) }
    val keyboardDismissScrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val dismissKeyboardOnUserMessageScroll =
        remember(keyboardDismissScrollThresholdPx) {
            chatDismissKeyboardAfterScrollConnection(
                thresholdPx = keyboardDismissScrollThresholdPx,
                isSuppressed = { suppressKeyboardDismissWhileProgrammaticTimelineScroll.value },
                onDismiss = { focusManagerState.value.clearFocus() },
            )
        }

    val initialTimelineScrollDone = remember(args.realtimeChannel) { mutableStateOf(false) }
    val peerNewestMessageId =
        messages
            .lastOrNull()
            ?.takeIf { !it.isSent }
            ?.message
            ?.id

    LaunchedEffect(args.realtimeChannel, messages.isNotEmpty()) {
        if (messages.isEmpty() || initialTimelineScrollDone.value) return@LaunchedEffect
        initialTimelineScrollDone.value = true
        scrollChatTimelineToLatest(
            listState = hubListState,
            suppressKeyboardDismiss = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
        )
    }

    LaunchedEffect(peerNewestMessageId) {
        if (peerNewestMessageId == null) return@LaunchedEffect
        if (chatTimelineShouldFollowInbound(
                firstVisibleItemIndex = hubListState.firstVisibleItemIndex,
                initialTimelineScrollDone = initialTimelineScrollDone.value,
            )
        ) {
            scrollChatTimelineToLatest(
                listState = hubListState,
                suppressKeyboardDismiss = suppressKeyboardDismissWhileProgrammaticTimelineScroll,
                animated = chatTimelineFollowUsesAnimation(initialTimelineScrollDone.value),
            )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                HubChatNavigationEvent.PopBackToConnections -> onNavigateBack()
            }
        }
    }

    LaunchedEffect(hubDetails.name, hubDetails.category, showEditDialog) {
        if (!showEditDialog) {
            editNameDraft = hubDetails.name
            editCategoryDraft = hubDetails.category
        }
    }

    val inLobby = false // TODO: restore `occupantCount < 3` after testing
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val realtimeState by viewModel.realtimeState.collectAsState()
    val channelReady = realtimeState is HubRealtimeState.Ready
    val channelError = (realtimeState as? HubRealtimeState.Error)?.message

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        ChatAmbientMeshBackground(
            connection = null,
            isHubNeutral = true,
            modifier = Modifier.fillMaxSize(),
        )

        if (channelError != null && messages.isEmpty()) {
            HubRealtimeErrorView(
                topInset = topInset,
                message = channelError,
                onBackPressed = onNavigateBack,
                onRetry = { viewModel.retryRealtime() },
                composeHeader = !nativeNavChrome,
            )
        } else if (!channelReady && messages.isEmpty()) {
            ChatChannelLoadingView(
                topInset = topInset,
                onBackPressed = onNavigateBack,
                composeHeader = !nativeNavChrome,
            )
        }

        AnimatedVisibility(
            visible = channelReady || messages.isNotEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (nativeNavChrome) {
                        Spacer(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = topInset)
                                    .height(44.dp)
                                    .testTag(ChatGlassHeaderPlateTestTag),
                        )
                    } else {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = topInset)
                                    .height(56.dp)
                                    .testTag(ChatGlassHeaderPlateTestTag),
                        ) {
                            ChatLiquidGlassPlate(
                                modifier = Modifier.matchParentSize(),
                                testTag = ChatGlassHeaderPlateTestTag,
                            )
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = ChatChromeHorizontalPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ChatHeaderIconButton(
                                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = onNavigateBack,
                                    showBorder = true,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hubDetails.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text =
                                            if (inLobby) {
                                                "$occupantCount ${if (occupantCount == 1) "person" else "people"} here"
                                            } else {
                                                "$occupantCount people in this hub"
                                            },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                ChatHeaderIconButton(
                                    icon = Icons.Filled.MoreVert,
                                    contentDescription = "Hub settings",
                                    onClick = { settingsMenuExpanded = true },
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.testTag("hub_settings_menu"),
                                )
                            }
                        }
                    }

                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(14.dp)),
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                    ) {
                        Text(
                            text = "See someone interesting? Go tap phones to make a permanent connection.",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                    }

                    if (inLobby) {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(16.dp)),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Text(
                                text = "You're the first one here! We'll ping you when others join.",
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    // ── Timestamp peek ──────────────────────────────────────────
                    val rawTimestampPeekTravelPx = remember { mutableFloatStateOf(0f) }
                    val displayTimestampPeekVisualPx = remember { mutableFloatStateOf(0f) }
                    val timestampPeekSettleJob = remember { mutableStateOf<Job?>(null) }
                    val peekRevealPx = rememberTimestampPeekRevealPx()
                    val timestampPeekSoftKneePx = rememberTimestampPeekSoftKneePx()
                    DisposableEffect(
                        integrateTimestampPeekWithSwipeBackContainer,
                        peekRevealPx,
                        timestampPeekSoftKneePx,
                        hubPeekScope,
                    ) {
                        val integrate = integrateTimestampPeekWithSwipeBackContainer
                        if (integrate) {
                            val integration =
                                InteractiveSwipeBackRightToLeftPeek(
                                    onGestureStart = {
                                        timestampPeekSettleJob.value?.cancel()
                                        timestampPeekSettleJob.value = null
                                        restoreTimestampPeekRawFromDisplay(
                                            rawLeftPx = rawTimestampPeekTravelPx,
                                            displayVisualPx = displayTimestampPeekVisualPx,
                                            maxRevealPx = peekRevealPx,
                                            softKneePx = timestampPeekSoftKneePx,
                                        )
                                    },
                                    onLeftDragDelta = { dLeft ->
                                        applyTimestampPeekDragStep(
                                            rawLeftPx = rawTimestampPeekTravelPx,
                                            displayVisualPx = displayTimestampPeekVisualPx,
                                            maxRevealPx = peekRevealPx,
                                            softKneePx = timestampPeekSoftKneePx,
                                            dLeftPx = dLeft,
                                        )
                                    },
                                    onLeftDragEnd = {
                                        hubPeekScope.launchTimestampPeekReplyStyleSettle(
                                            rawLeftPx = rawTimestampPeekTravelPx,
                                            displayVisualPx = displayTimestampPeekVisualPx,
                                            settleJobHolder = timestampPeekSettleJob,
                                        )
                                    },
                                    isPeekRevealed = {
                                        isTimestampPeekRevealed(displayTimestampPeekVisualPx.floatValue)
                                    },
                                    onRightDragDelta = { dRight ->
                                        applyTimestampPeekDragStep(
                                            rawLeftPx = rawTimestampPeekTravelPx,
                                            displayVisualPx = displayTimestampPeekVisualPx,
                                            maxRevealPx = peekRevealPx,
                                            softKneePx = timestampPeekSoftKneePx,
                                            dLeftPx = -dRight,
                                        )
                                    },
                                    onRightDragFromRest = {
                                        timestampPeekSettleJob.value?.cancel()
                                        timestampPeekSettleJob.value = null
                                        rawTimestampPeekTravelPx.floatValue = 0f
                                        displayTimestampPeekVisualPx.floatValue = 0f
                                    },
                                )
                            onRegisterSwipeBackRightToLeftPeek(integration)
                        }
                        onDispose {
                            timestampPeekSettleJob.value?.cancel()
                            timestampPeekSettleJob.value = null
                            if (integrate) {
                                onRegisterSwipeBackRightToLeftPeek(null)
                            }
                        }
                    }

                    val timelineEntries =
                        remember(messages) {
                            buildChatTimelineEntriesNewestFirst(messages)
                        }
                    val reverseListNewestEdgePad = 6.dp

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clipToBounds(),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .chatThreadKeyboardDock(
                                        nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
                                        clearNativeTabBar = false,
                                    ),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                ChatMessageTimeline(
                                    timelineEntries = timelineEntries,
                                    listState = hubListState,
                                    newestSentMessage = null,
                                    listBottomPadding =
                                        PaddingValues(
                                            start = 6.dp,
                                            end = 6.dp,
                                            top = 24.dp + reverseListNewestEdgePad,
                                            bottom = 8.dp + ChatComposerStripReserve,
                                        ),
                                    dismissKeyboardOnUserMessageScroll = dismissKeyboardOnUserMessageScroll,
                                    displayTimestampPeekVisualPx = displayTimestampPeekVisualPx,
                                    peekRevealPx = peekRevealPx,
                                    meshConnection = null,
                                    useHubNeutralMesh = true,
                                    isGroupChat = true,
                                    currentUserId = currentUserId,
                                    reactionsMap = emptyMap(),
                                    secureMediaHost = viewModel,
                                    activeChatId = hubIdForSecureMedia,
                                    onToggleReaction = { _, _ -> },
                                    onForward = {},
                                    onLongPress = {},
                                    onSwipeReply = {},
                                    onDownloadAttachment = { _, _ ->
                                        ChatAttachmentDownloadOutcome.Failure("Download not available in hub chat.")
                                    },
                                    interMessageBaseCompact = ChatInterMessageHubBaseCompact,
                                    enableMessageContextMenu = false,
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (!integrateTimestampPeekWithSwipeBackContainer) {
                                                    Modifier.chatTimestampPeekOnSwipeLeft(
                                                        maxRevealPx = peekRevealPx,
                                                        softKneePx = timestampPeekSoftKneePx,
                                                        rawLeftPx = rawTimestampPeekTravelPx,
                                                        displayVisualPx = displayTimestampPeekVisualPx,
                                                        scope = hubPeekScope,
                                                        settleJobHolder = timestampPeekSettleJob,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                )
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                HubChatInputBar(
                                    viewModel = viewModel,
                                    inLobby = inLobby,
                                    isOutOfBounds = outOfBounds,
                                    mediaPickers = mediaPickers,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (settingsMenuExpanded) {
        val menuItems =
            visibleHubSettingsMenuItems(
                currentUserId = currentUserId,
                creatorId = resolvedCreatorId,
            )
        ClickActionBottomSheet(
            onDismissRequest = { settingsMenuExpanded = false },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(sheetPageBackground())
                        .padding(bottom = 32.dp),
            ) {
                Text(
                    text = hubDetails.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassSheetTokens.OnOled(),
                    modifier =
                        Modifier
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .align(Alignment.CenterHorizontally),
                )
                HorizontalDivider(color = GlassSheetTokens.GlassBorder())

                if (HubSettingsMenuItem.Leave in menuItems) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Leave Hub",
                        subtitle = "Remove this hub from your list",
                        onClick = {
                            settingsMenuExpanded = false
                            showLeaveConfirm = true
                        },
                        destructive = true,
                        modifier = Modifier.testTag("hub_settings_leave"),
                        leading = {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                            )
                        },
                    )
                }
                if (HubSettingsMenuItem.Edit in menuItems) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Edit Hub",
                        subtitle = "Update name and category",
                        onClick = {
                            settingsMenuExpanded = false
                            editNameDraft = hubDetails.name
                            editCategoryDraft = hubDetails.category
                            showEditDialog = true
                        },
                        modifier = Modifier.testTag("hub_settings_edit"),
                        leading = {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = GlassSheetTokens.OnOledMuted(),
                            )
                        },
                    )
                }
                if (HubSettingsMenuItem.Delete in menuItems) {
                    BentoGlassOptionRow(
                        showBorder = false,
                        title = "Delete Hub",
                        subtitle = "Kick all users and delete history",
                        onClick = {
                            settingsMenuExpanded = false
                            showDeleteConfirm = true
                        },
                        destructive = true,
                        modifier = Modifier.testTag("hub_settings_delete"),
                        leading = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = Color(0xFFFF6B6B),
                            )
                        },
                    )
                }
            }
        }
    }

    if (showEditDialog && isCreator) {
        UnifiedPopupFormDialog(
            visible = showEditDialog,
            onDismissRequest = { showEditDialog = false },
            title = "Edit Hub",
            confirmLabel = "Save",
            onConfirm = {
                if (editNameDraft.isBlank() || editCategoryDraft.isBlank()) return@UnifiedPopupFormDialog
                viewModel.editHubDetails(editNameDraft, editCategoryDraft) { success ->
                    if (success) showEditDialog = false
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

    if (showLeaveConfirm) {
        GlassAlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave hub?") },
            text = {
                Text("You will leave this community hub and lose quick access from your Groups list.")
            },
            confirmButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(
                    onClick = {
                        dismissAnimated()
                        viewModel.leaveHub()
                    },
                ) {
                    Text("Leave", color = MaterialTheme.colorScheme.error)
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

    if (showDeleteConfirm && isCreator) {
        GlassAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete hub?") },
            text = { Text("Are you sure? This will kick all users and delete the history.") },
            confirmButton = {
                val dismissAnimated = LocalGlassAlertAnimatedDismiss.current
                TextButton(
                    onClick = {
                        dismissAnimated()
                        viewModel.deleteHub()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
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

/**
 * Hub composer strip modeled exactly on [ConnectionChatMessageComposer]:
 * `+` attachment button (left) → BasicTextField (center) → gradient send button (right).
 * Same sizes, shapes, and spring press animations.
 */
@Composable
private fun HubChatInputBar(
    viewModel: HubChatViewModel,
    inLobby: Boolean,
    isOutOfBounds: Boolean = false,
    mediaPickers: ChatMediaPickerHandles,
) {
    val draft by viewModel.draft.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val sendError by viewModel.sendError.collectAsState()
    val cooldownSec by viewModel.sendCooldownRemainingSec.collectAsState()

    val composerStyle = LocalPlatformStyle.current
    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
    val composerRowHPad = ChatChromeHorizontalPadding

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composerStripInteraction = remember { MutableInteractionSource() }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val onCooldown = cooldownSec > 0
    val enabled = !inLobby && !isOutOfBounds && !isSending && !onCooldown

    Box(modifier = Modifier.fillMaxWidth().graphicsLayer { clip = true }) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(
                        indication = null,
                        interactionSource = composerStripInteraction,
                    ) {},
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = composerRowHPad, vertical = composerRowVPad),
        ) {
            sendError?.let { err ->
                Text(
                    text = "$err · Review and tap send to retry",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                )
            }
            ChatComposerStrip(
                value = draft,
                onValueChange = viewModel::updateDraft,
                placeholder =
                    if (inLobby) {
                        "Chat unlocks when 3+ join"
                    } else if (isOutOfBounds) {
                        "You are no longer at this location"
                    } else if (onCooldown) {
                        "Wait ${cooldownSec}s…"
                    } else {
                        "Message the hub…"
                    },
                enabled = enabled,
                externallySending = isSending,
                sendIcon = Icons.AutoMirrored.Filled.Send,
                sendContentDescription = if (onCooldown) "Wait ${cooldownSec}s" else "Send",
                onSend = viewModel::sendMessage,
                attachmentMenuExpanded = attachmentMenuExpanded,
                onAttachmentMenuExpandedChange = { attachmentMenuExpanded = it },
                attachmentMenuContent = {
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        ChatAttachmentMenuRow(
                            label = "Photo library",
                            icon = Icons.Outlined.Image,
                            enabled = enabled,
                            onClick = {
                                PlatformHapticsPolicy.lightImpact()
                                attachmentMenuExpanded = false
                                mediaPickers.openPhotoLibrary()
                            },
                        )
                        ChatAttachmentMenuRow(
                            label = "Take photo",
                            icon = Icons.Outlined.PhotoCamera,
                            enabled = enabled,
                            onClick = {
                                PlatformHapticsPolicy.lightImpact()
                                attachmentMenuExpanded = false
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                mediaPickers.openCamera()
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming")
private fun HubRealtimeErrorView(
    topInset: androidx.compose.ui.unit.Dp,
    message: String,
    onBackPressed: () -> Unit,
    onRetry: () -> Unit,
    composeHeader: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (composeHeader) {
            Box(modifier = Modifier.padding(start = 20.dp, top = topInset, end = 20.dp)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatHeaderIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackPressed,
                        showBorder = true,
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth().height(topInset + 44.dp))
        }
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .padding(top = if (composeHeader) topInset + 56.dp else topInset + 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Couldn't join this hub",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}
