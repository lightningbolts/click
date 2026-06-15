package compose.project.click.click.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.project.click.click.PlatformHapticsPolicy
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentMenuAnchorHost // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentMenuRow // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMediaPickerHandles // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAttachmentDownloadOutcome // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChannelLoadingView // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatMessageTimeline // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatSpringPressScale // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatInterMessageHubBaseCompact // pragma: allowlist secret
import compose.project.click.click.ui.chat.buildChatTimelineEntriesNewestFirst // pragma: allowlist secret
import compose.project.click.click.ui.chat.applyTimestampPeekDragStep // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatTimestampPeekOnSwipeLeft // pragma: allowlist secret
import compose.project.click.click.ui.chat.launchTimestampPeekReplyStyleSettle // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatMediaPickers // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekRevealPx // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberTimestampPeekSoftKneePx // pragma: allowlist secret
import compose.project.click.click.ui.chat.isTimestampPeekRevealed // pragma: allowlist secret
import compose.project.click.click.ui.chat.restoreTimestampPeekRawFromDisplay // pragma: allowlist secret
import compose.project.click.click.ui.components.InteractiveSwipeBackRightToLeftPeek // pragma: allowlist secret
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.platform.KeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.platform.rememberKeyboardHeightProvider // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatComposerStripReserve // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatComposerFieldColors // pragma: allowlist secret
import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret
import compose.project.click.click.ui.components.chatThreadKeyboardDock // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassAlertDialog // pragma: allowlist secret
import compose.project.click.click.ui.components.GlassSheetTokens // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalGlassAlertAnimatedDismiss // pragma: allowlist secret
import compose.project.click.click.ui.components.UnifiedPopupFormDialog // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.utils.LocationResult // pragma: allowlist secret
import compose.project.click.click.viewmodel.HubChatNavigationEvent // pragma: allowlist secret
import compose.project.click.click.viewmodel.HubChatViewModel // pragma: allowlist secret
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class HubChatNavArgs(
    val hubId: String,
    val realtimeChannel: String,
    val hubTitle: String,
    val creatorId: String? = null,
    val hubCategory: String = "general",
)

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
    keyboardHeightProvider: KeyboardHeightProvider = rememberKeyboardHeightProvider(),
) {
    val viewModel: HubChatViewModel = viewModel(key = args.realtimeChannel) {
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
    val secureMediaLoadMap by viewModel.secureChatMediaLoadState.collectAsState()

    val isCreator by viewModel.isCreator.collectAsState()
    val resolvedCreatorId by viewModel.resolvedCreatorId.collectAsState()
    val hubDetails by viewModel.hubDetails.collectAsState()
    var settingsMenuExpanded by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editNameDraft by remember { mutableStateOf(hubDetails.name) }
    var editCategoryDraft by remember { mutableStateOf(hubDetails.category) }

    val mediaPickers = rememberChatMediaPickers(
        onImagePicked = { bytes, mime -> viewModel.sendHubImageFromPicker(bytes, mime) },
        onAudioPicked = { _, _, _ -> },
        onMediaAccessBlocked = { },
    )

    val hubIdForSecureMedia = remember(args.hubId) { args.hubId }
    val hubPeekScope = rememberCoroutineScope()
    val hubListState = remember(args.realtimeChannel) { LazyListState() }
    val density = LocalDensity.current
    val nativeKeyboardInsets = rememberChatNativeKeyboardInsets(keyboardHeightProvider)
    val focusManager = LocalFocusManager.current
    val focusManagerState = rememberUpdatedState(focusManager)
    val suppressKeyboardDismissWhileProgrammaticTimelineScroll = remember { mutableStateOf(false) }
    val keyboardDismissScrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val dismissKeyboardOnUserMessageScroll = remember(keyboardDismissScrollThresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (suppressKeyboardDismissWhileProgrammaticTimelineScroll.value) return Offset.Zero
                if (source == NestedScrollSource.UserInput &&
                    kotlin.math.abs(consumed.y) > keyboardDismissScrollThresholdPx
                ) {
                    focusManagerState.value.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    val initialTimelineScrollDone = remember(args.realtimeChannel) { mutableStateOf(false) }
    val peerNewestMessageId = messages.lastOrNull()?.takeIf { !it.isSent }?.message?.id

    suspend fun scrollHubTimelineToLatest() {
        repeat(50) {
            if (hubListState.layoutInfo.totalItemsCount > 0) {
                suppressKeyboardDismissWhileProgrammaticTimelineScroll.value = true
                hubListState.scrollToItem(0)
                delay(120)
                suppressKeyboardDismissWhileProgrammaticTimelineScroll.value = false
                return
            }
            delay(16L)
        }
    }

    LaunchedEffect(args.realtimeChannel, messages.isNotEmpty()) {
        if (messages.isEmpty() || initialTimelineScrollDone.value) return@LaunchedEffect
        initialTimelineScrollDone.value = true
        scrollHubTimelineToLatest()
    }

    LaunchedEffect(peerNewestMessageId) {
        if (peerNewestMessageId == null) return@LaunchedEffect
        scrollHubTimelineToLatest()
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
    val channelReady by viewModel.channelReady.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        ChatAmbientMeshBackground(
            connection = null,
            isHubNeutral = true,
            modifier = Modifier.fillMaxSize(),
        )

        if (!channelReady && messages.isEmpty()) {
            ChatChannelLoadingView(
                topInset = topInset,
                onBackPressed = onNavigateBack,
            )
        }

        AnimatedVisibility(
            visible = channelReady || messages.isNotEmpty(),
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)),
            exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                // ── Edge-to-edge translucent glass header (parity with ChatView 1-on-1 / group) ──
                // No solid plate: the header is transparent over the ambient mesh, matching the
                // seamless liquid-glass chrome used by standard chats.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topInset)
                        .height(56.dp)
                        .padding(horizontal = 20.dp)
                        .testTag(ChatGlassHeaderPlateTestTag),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = hubDetails.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (inLobby) {
                                    "$occupantCount ${if (occupantCount == 1) "person" else "people"} here"
                                } else {
                                    "$occupantCount people in this hub"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { settingsMenuExpanded = true }) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Hub settings",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                )
                            }
                            DropdownMenu(
                                expanded = settingsMenuExpanded,
                                onDismissRequest = { settingsMenuExpanded = false },
                            ) {
                                val items = visibleHubSettingsMenuItems(
                                    currentUserId = currentUserId,
                                    creatorId = resolvedCreatorId,
                                )
                                if (HubSettingsMenuItem.Leave in items) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Leave Hub",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            settingsMenuExpanded = false
                                            showLeaveConfirm = true
                                        },
                                        modifier = Modifier.testTag("hub_settings_leave"),
                                    )
                                }
                                if (HubSettingsMenuItem.Edit in items) {
                                    DropdownMenuItem(
                                        text = { Text("Edit Hub") },
                                        onClick = {
                                            settingsMenuExpanded = false
                                            editNameDraft = hubDetails.name
                                            editCategoryDraft = hubDetails.category
                                            showEditDialog = true
                                        },
                                        modifier = Modifier.testTag("hub_settings_edit"),
                                    )
                                }
                                if (HubSettingsMenuItem.Delete in items) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete Hub",
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            settingsMenuExpanded = false
                                            showDeleteConfirm = true
                                        },
                                        modifier = Modifier.testTag("hub_settings_delete"),
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    color = PrimaryBlue.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(14.dp),
                    tonalElevation = 0.dp,
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 0.dp,
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
                        val integration = InteractiveSwipeBackRightToLeftPeek(
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

                val newestSentMessage = remember(messages) {
                    messages.asSequence().filter { it.isSent }.maxByOrNull { it.message.timeCreated }
                }
                val timelineEntries = remember(messages) {
                    buildChatTimelineEntriesNewestFirst(messages)
                }
                val reverseListNewestEdgePad = 6.dp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .chatThreadKeyboardDock(
                            nativeKeyboardLiftPx = nativeKeyboardInsets.threadDockNativeKeyboardLiftPx,
                        ),
                ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                ChatMessageTimeline(
                    timelineEntries = timelineEntries,
                    listState = hubListState,
                    newestSentMessage = newestSentMessage,
                    listBottomPadding = PaddingValues(
                        start = 6.dp,
                        end = 6.dp,
                        top = 24.dp + reverseListNewestEdgePad,
                        bottom = 8.dp + ChatComposerStripReserve + nativeKeyboardInsets.timelineBottomPadding,
                    ),
                    dismissKeyboardOnUserMessageScroll = dismissKeyboardOnUserMessageScroll,
                    displayTimestampPeekVisualPx = displayTimestampPeekVisualPx,
                    peekRevealPx = peekRevealPx,
                    meshConnection = null,
                    useHubNeutralMesh = true,
                    isGroupChat = true,
                    currentUserId = currentUserId,
                    reactionsMap = emptyMap(),
                    secureMediaLoadMap = secureMediaLoadMap,
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
                    modifier = Modifier
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            translationY = -nativeKeyboardInsets.composerLiftPx.coerceAtLeast(0f)
                        },
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
                    OutlinedTextField(
                        value = editNameDraft,
                        onValueChange = { editNameDraft = it.take(80) },
                        singleLine = true,
                        label = { Text("Hub name", color = GlassSheetTokens.OnOledMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassSheetTokens.OnOled,
                            unfocusedTextColor = GlassSheetTokens.OnOled,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = GlassSheetTokens.GlassBorder,
                            cursorColor = PrimaryBlue,
                            focusedLabelColor = GlassSheetTokens.OnOledMuted,
                            unfocusedLabelColor = GlassSheetTokens.OnOledMuted,
                        ),
                    )
                    OutlinedTextField(
                        value = editCategoryDraft,
                        onValueChange = { editCategoryDraft = it.take(40) },
                        singleLine = true,
                        label = { Text("Category", color = GlassSheetTokens.OnOledMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassSheetTokens.OnOled,
                            unfocusedTextColor = GlassSheetTokens.OnOled,
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = GlassSheetTokens.GlassBorder,
                            cursorColor = PrimaryBlue,
                            focusedLabelColor = GlassSheetTokens.OnOledMuted,
                            unfocusedLabelColor = GlassSheetTokens.OnOledMuted,
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
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
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
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
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

    val composerStyle = LocalPlatformStyle.current
    val auxButtonSize = if (composerStyle.isIOS) 44.dp else 52.dp
    val composerRowVPad = if (composerStyle.isIOS) 6.dp else 8.dp
    val composerRowHPad = 8.dp
    val attachIconSize = if (composerStyle.isIOS) 24.dp else 26.dp
    val sendIconSize = if (composerStyle.isIOS) 22.dp else 20.dp
    val fieldCorner = if (composerStyle.isIOS) 20.dp else 12.dp
    val composerGap = if (composerStyle.isIOS) 6.dp else 8.dp
    val fieldSideInset = auxButtonSize + composerGap

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val attachInteraction = remember { MutableInteractionSource() }
    val sendInteraction = remember { MutableInteractionSource() }
    val composerStripInteraction = remember { MutableInteractionSource() }
    val composerFieldInteraction = remember { MutableInteractionSource() }
    val composerFocusRequester = remember { FocusRequester() }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }

    val canSend = !inLobby && !isOutOfBounds && draft.trim().isNotEmpty()
    val enabled = !inLobby && !isOutOfBounds && !isSending
    val attachTint = PrimaryBlue.copy(alpha = 0.92f)
    val sendGradient = Brush.linearGradient(
        colors = if (canSend) {
            listOf(PrimaryBlue, LightBlue)
        } else {
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant,
            )
        },
    )
    val composerInputTextStyle = MaterialTheme.typography.bodyMedium
    val composerTextStyleCentered = composerInputTextStyle.merge(
        TextStyle(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
    )

    val fieldColors = rememberChatComposerFieldColors()
    val fieldShape = RoundedCornerShape(fieldCorner)
    val approxLineBodyDp = 24.dp
    val innerVerticalPad = ((auxButtonSize - approxLineBodyDp) / 2).coerceIn(6.dp, 12.dp)
    val innerHorizontalPad = 12.dp
    val fieldDecorPadding = PaddingValues(
        start = innerHorizontalPad,
        end = innerHorizontalPad,
        top = innerVerticalPad,
        bottom = innerVerticalPad,
    )

    Box(modifier = Modifier.fillMaxWidth().graphicsLayer { clip = true }) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(
                    indication = null,
                    interactionSource = composerStripInteraction,
                ) {},
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = composerRowHPad, vertical = composerRowVPad),
        ) {
            sendError?.let { err ->
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = auxButtonSize),
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = viewModel::updateDraft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = fieldSideInset, end = fieldSideInset)
                        .heightIn(min = auxButtonSize)
                        .align(Alignment.BottomCenter)
                        .focusRequester(composerFocusRequester),
                    enabled = enabled,
                    textStyle = composerTextStyleCentered.merge(
                        TextStyle(color = MaterialTheme.colorScheme.onSurface),
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.None,
                    ),
                    singleLine = false,
                    minLines = 1,
                    maxLines = 10,
                    interactionSource = composerFieldInteraction,
                    cursorBrush = SolidColor(PrimaryBlue),
                    decorationBox = { innerTextField ->
                        OutlinedTextFieldDefaults.DecorationBox(
                            value = draft,
                            innerTextField = innerTextField,
                            enabled = enabled,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = composerFieldInteraction,
                            placeholder = {
                                Text(
                                    if (inLobby) "Chat unlocks when 3+ join"
                                    else if (isOutOfBounds) "You are no longer at this location"
                                    else "Message the hub…",
                                    style = composerTextStyleCentered,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            colors = fieldColors,
                            contentPadding = fieldDecorPadding,
                            container = {
                                OutlinedTextFieldDefaults.Container(
                                    enabled = enabled,
                                    isError = false,
                                    interactionSource = composerFieldInteraction,
                                    modifier = Modifier,
                                    colors = fieldColors,
                                    shape = fieldShape,
                                )
                            },
                        )
                    },
                )

                // ── Attach button (left, same as ConnectionChatMessageComposer) ─
                ChatAttachmentMenuAnchorHost(
                    expanded = attachmentMenuExpanded,
                    onExpandedChange = { attachmentMenuExpanded = it },
                    anchorSize = auxButtonSize,
                    anchorInteraction = attachInteraction,
                    anchorEnabled = enabled,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(4f)
                        .focusProperties { canFocus = false },
                    anchor = {
                        val bgAlpha = if (isSending || inLobby) 0.12f else 0.24f
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            PrimaryBlue.copy(alpha = bgAlpha),
                                            PrimaryBlue.copy(alpha = bgAlpha),
                                        ),
                                    ),
                                )
                                .chatSpringPressScale(attachInteraction),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Attach",
                                tint = if (enabled) attachTint else attachTint.copy(alpha = 0.35f),
                                modifier = Modifier.size(attachIconSize),
                            )
                        }
                    },
                    menuContent = {
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

                // ── Send button (right, gradient pill, same as ConnectionChatMessageComposer) ─
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(auxButtonSize)
                        .zIndex(4f)
                        .focusProperties { canFocus = false }
                        .chatSpringPressScale(sendInteraction)
                        .clip(if (composerStyle.isIOS) CircleShape else RoundedCornerShape(fieldCorner))
                        .background(sendGradient)
                        .clickable(
                            interactionSource = sendInteraction,
                            indication = null,
                            enabled = canSend,
                            onClick = {
                                PlatformHapticsPolicy.lightImpact()
                                viewModel.sendMessage()
                                composerFocusRequester.requestFocus()
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) {
                            Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        },
                        modifier = Modifier.size(sendIconSize),
                    )
                }
            }
        }
    }
}
