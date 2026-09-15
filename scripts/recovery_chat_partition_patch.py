from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"missing replacement target in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


def replace_between(path: str, start: str, end: str, replacement: str) -> None:
    p = Path(path)
    text = p.read_text()
    i = text.find(start)
    if i < 0:
        raise SystemExit(f"missing start marker in {path}: {start!r}")
    j = text.find(end, i)
    if j < 0:
        raise SystemExit(f"missing end marker in {path}: {end!r}")
    p.write_text(text[:i] + replacement + text[j:])


# Event-chat 403 is terminal until RSVP state actually changes.
event = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/EventBeaconDetail.kt"
replace_once(
    event,
    """            eventChatEligible &&
                eventChatState != EventChatOpenState.Resolving &&
                eventChatState != EventChatOpenState.Expired &&""",
    """            eventChatEligible &&
                eventChatState != EventChatOpenState.Resolving &&
                eventChatState != EventChatOpenState.RequiresRsvp &&
                eventChatState != EventChatOpenState.Expired &&""",
)

# Keep actual route accessibility labels in app-owned UIKit state. Kotlin/Native cannot reliably
# read the Objective-C accessibility getter here, so capture labels at paint time.
layer = "composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/IosHostNavBarLayer.ios.kt"
replace_once(
    layer,
    "    internal val paintedSymbols = mutableMapOf<UIButton, String>()\n",
    "    internal val paintedSymbols = mutableMapOf<UIButton, String>()\n    internal val paintedAccessibility = mutableMapOf<UIButton, String>()\n",
)

views = "composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/IosHostNavBarLayerViews.ios.kt"
replace_once(
    views,
    """    if (paintedSymbols[button] == symbol) {
        button.setAccessibilityLabel(accessibility)
        return
    }""",
    """    if (paintedSymbols[button] == symbol) {
        button.setAccessibilityLabel(accessibility)
        paintedAccessibility[button] = accessibility
        return
    }""",
)
replace_once(
    views,
    """        button.setAccessibilityLabel(accessibility)
        CATransaction.commit()
        paintedSymbols[button] = symbol
        return""",
    """        button.setAccessibilityLabel(accessibility)
        paintedAccessibility[button] = accessibility
        CATransaction.commit()
        paintedSymbols[button] = symbol
        return""",
)
replace_once(
    views,
    """    button.setAccessibilityLabel(accessibility)
    CATransaction.commit()
    paintedSymbols[button] = symbol
}""",
    """    button.setAccessibilityLabel(accessibility)
    paintedAccessibility[button] = accessibility
    CATransaction.commit()
    paintedSymbols[button] = symbol
}""",
)

stable = "composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/StableOverlayMediaChrome.ios.kt"
replace_once(
    stable,
    "backAccessibility = stableChromeAccessibility(paintedSymbols[backButton], leading = true),",
    "backAccessibility = paintedAccessibility[backButton] ?: stableChromeAccessibility(paintedSymbols[backButton], leading = true),",
)
replace_once(
    stable,
    'searchAccessibility = "Search",',
    'searchAccessibility = paintedAccessibility[searchButton] ?: "Search",',
)
replace_once(
    stable,
    "actionAccessibility = actionSymbols.map { stableChromeAccessibility(it, leading = false) },",
    """actionAccessibility =
            actionButtons.mapIndexed { index, button ->
                paintedAccessibility[button]
                    ?: stableChromeAccessibility(actionSymbols[index], leading = false)
            },""",
)
replace_once(
    stable,
    """    backButton.setAccessibilityLabel(snapshot.backAccessibility)

    snapshot.searchSymbol?.let""",
    """    backButton.setAccessibilityLabel(snapshot.backAccessibility)
    paintedAccessibility[backButton] = snapshot.backAccessibility

    snapshot.searchSymbol?.let""",
)
replace_once(
    stable,
    """    searchButton.setAccessibilityLabel(snapshot.searchAccessibility)

    actionButtons.forEachIndexed""",
    """    searchButton.setAccessibilityLabel(snapshot.searchAccessibility)
    paintedAccessibility[searchButton] = snapshot.searchAccessibility

    actionButtons.forEachIndexed""",
)
replace_once(
    stable,
    """        button.setAccessibilityLabel(snapshot.actionAccessibility[index])
    }
}""",
    """        button.setAccessibilityLabel(snapshot.actionAccessibility[index])
        paintedAccessibility[button] = snapshot.actionAccessibility[index]
    }
}""",
)
replace_once(
    stable,
    """    if (paintedSymbols[backButton] == symbol) {
        backButton.setAccessibilityLabel(accessibility)
        return
    }""",
    """    if (paintedSymbols[backButton] == symbol) {
        backButton.setAccessibilityLabel(accessibility)
        paintedAccessibility[backButton] = accessibility
        return
    }""",
)
replace_once(
    stable,
    """    if (paintedSymbols[button] == symbol) {
        button.setAccessibilityLabel(accessibility)
        return
    }""",
    """    if (paintedSymbols[button] == symbol) {
        button.setAccessibilityLabel(accessibility)
        paintedAccessibility[button] = accessibility
        return
    }""",
)

# Hub/event chats adopt the same split IME architecture as user/group chats.
hub = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HubChatScreen.kt"
replace_once(
    hub,
    "import compose.project.click.click.ui.chat.chatDismissKeyboardAfterScrollConnection // pragma: allowlist secret\n",
    "import compose.project.click.click.ui.chat.chatComposerKeyboardMotion // pragma: allowlist secret\nimport compose.project.click.click.ui.chat.chatDismissKeyboardAfterScrollConnection // pragma: allowlist secret\n",
)
replace_once(
    hub,
    "import compose.project.click.click.ui.chat.chatTimelineShouldFollowInbound // pragma: allowlist secret\n",
    "import compose.project.click.click.ui.chat.chatTimelineKeyboardViewport // pragma: allowlist secret\nimport compose.project.click.click.ui.chat.chatTimelineShouldFollowInbound // pragma: allowlist secret\nimport compose.project.click.click.ui.chat.chatTimelineShouldFollowKeyboard // pragma: allowlist secret\n",
)
replace_once(
    hub,
    "import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret\n",
    "import compose.project.click.click.ui.chat.rememberChatNativeKeyboardInsets // pragma: allowlist secret\nimport compose.project.click.click.ui.chat.rememberChatTimelineKeyboardFollow // pragma: allowlist secret\n",
)
replace_once(
    hub,
    "import compose.project.click.click.ui.components.chatThreadKeyboardDock // pragma: allowlist secret\n",
    "",
)
replace_once(
    hub,
    """    val initialTimelineScrollDone = remember(args.realtimeChannel) { mutableStateOf(false) }
    var focusedSearchMessageId by remember(args.realtimeChannel) { mutableStateOf<String?>(null) }""",
    """    val initialTimelineScrollDone = remember(args.realtimeChannel) { mutableStateOf(false) }
    val hubTimelineFollowsKeyboardState =
        rememberChatTimelineKeyboardFollow(
            nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
            shouldFollowOnKeyboardOpen = {
                chatTimelineShouldFollowKeyboard(
                    firstVisibleItemIndex = hubListState.firstVisibleItemIndex,
                    initialTimelineScrollDone = initialTimelineScrollDone.value,
                    userScrollInProgress = hubListState.isScrollInProgress,
                )
            },
        )
    var focusedSearchMessageId by remember(args.realtimeChannel) { mutableStateOf<String?>(null) }""",
)
replace_once(
    hub,
    """                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .chatThreadKeyboardDock(
                                        nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
                                        clearNativeTabBar = true,
                                    ),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {""",
    """                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .chatTimelineKeyboardViewport(
                                            nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
                                            followKeyboard = { hubTimelineFollowsKeyboardState.value },
                                        ),
                            ) {""",
)
replace_once(
    hub,
    """                            Column(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                HubChatInputBar(""",
    """                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .chatComposerKeyboardMotion(
                                            nativeKeyboardLiftPxState = nativeKeyboardInsets.liftPxState,
                                            clearNativeTabBar = true,
                                        ),
                            ) {
                                HubChatInputBar(""",
)

# Keep high-frequency typing/presence/reaction signals out of ChatView root.
chat = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatView.kt"
replace_once(chat, "    val isPeerTyping by viewModel.isPeerTyping.collectAsState()\n", "")
replace_once(chat, "    val isPeerOnline by viewModel.isPeerOnline.collectAsState()\n", "")
replace_once(chat, "    val onlineUsers by AppDataManager.onlineUsers.collectAsState()\n", "")
replace_once(chat, "import compose.project.click.click.ui.chat.chatPeerStatusSubtitle // pragma: allowlist secret\n", "")
replace_between(
    chat,
    "    val bindOnline =\n",
    "    val chatNativeClearance =\n",
    """    val chatNativeHasStackedSubtitle =
        !bindIsGroup && (successChat != null || hintedChatRow != null)
""",
)
replace_once(
    chat,
    """    ChatViewNativeNavBinding(
        nativeNavChrome = nativeNavChrome,""",
    """    ChatViewNativeNavBinding(
        viewModel = viewModel,
        nativeNavChrome = nativeNavChrome,""",
)
replace_once(chat, "        bindStatusSubtitle = bindStatusSubtitle,\n", "")
replace_once(chat, "        bindOnline = bindOnline,\n", "")
replace_once(chat, "                    val reactionsMap by viewModel.messageReactions.collectAsState()\n", "")
replace_once(
    chat,
    """                            ChatViewSuccessHeader(
                                nativeNavChrome = nativeNavChrome,""",
    """                            ChatViewSuccessHeader(
                                viewModel = viewModel,
                                nativeNavChrome = nativeNavChrome,""",
)
replace_once(chat, "                                isPeerTyping = isPeerTyping,\n", "")
replace_once(chat, "                                isPeerOnline = isPeerOnline,\n", "")
replace_once(chat, "                                onlineUsers = onlineUsers,\n", "")
replace_once(chat, "                                reactionsMap = reactionsMap,\n", "")
replace_once(chat, "                                isPeerTyping = isPeerTyping,\n", "")

header = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatViewHeader.kt"
replace_once(
    header,
    "import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret\n",
    "import compose.project.click.click.data.AppDataManager // pragma: allowlist secret\nimport compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret\n",
)
replace_once(
    header,
    "import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret\n",
    "import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret\nimport compose.project.click.click.viewmodel.ChatViewModel // pragma: allowlist secret\n",
)
replace_once(
    header,
    """internal fun ChatViewSuccessHeader(
    nativeNavChrome: Boolean,""",
    """internal fun ChatViewSuccessHeader(
    viewModel: ChatViewModel,
    nativeNavChrome: Boolean,""",
)
replace_once(header, "    isPeerTyping: Boolean,\n", "")
replace_once(header, "    isPeerOnline: Boolean,\n", "")
replace_once(header, "    onlineUsers: Set<String>,\n", "")
replace_once(
    header,
    """    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState""",
    """    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    val isPeerOnline by viewModel.isPeerOnline.collectAsState()
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState""",
)
replace_once(
    header,
    """internal fun ChatViewNativeNavBinding(
    nativeNavChrome: Boolean,""",
    """internal fun ChatViewNativeNavBinding(
    viewModel: ChatViewModel,
    nativeNavChrome: Boolean,""",
)
replace_once(header, "    bindStatusSubtitle: String?,\n", "")
replace_once(header, "    bindOnline: Boolean?,\n", "")
replace_once(
    header,
    """    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState
    var renameGroupDraft by renameGroupDraftState
    if (nativeNavChrome) {""",
    """    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState
    var renameGroupDraft by renameGroupDraftState
    if (nativeNavChrome) {
        val isPeerTyping by viewModel.isPeerTyping.collectAsState()
        val isPeerOnline by viewModel.isPeerOnline.collectAsState()
        val onlineUsers by AppDataManager.onlineUsers.collectAsState()
        val peerId = successChat?.chatDetails?.otherUser?.id ?: hintedChatRow?.otherUser?.id
        val bindOnline = if (bindIsGroup) null else peerId?.let { it in onlineUsers || isPeerOnline }
        val bindStatusSubtitle =
            if (bindIsGroup) {
                null
            } else if (bindOnline != null || successChat != null || hintedChatRow != null) {
                chatPeerStatusSubtitle(isTyping = isPeerTyping, isOnline = bindOnline == true)
            } else {
                null
            }""",
)

pane = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatViewTimelinePane.kt"
replace_once(pane, "    reactionsMap: Map<String, List<compose.project.click.click.data.models.MessageReaction>>,\n", "")
replace_once(pane, "    isPeerTyping: Boolean,\n", "")
replace_once(
    pane,
    "                                reactionsMap = reactionsMap,\n",
    "                                reactionsMap = emptyMap(),\n                                reactionsFlow = viewModel.messageReactions,\n",
)
replace_between(
    pane,
    "                // Typing indicator — label + bouncing dots (Realtime Broadcast)\n",
    "\n                // Edit mode indicator strip",
    "                ChatTypingIndicator(viewModel = viewModel, typingPeerLabel = typingPeerLabel)\n",
)
p = Path(pane)
text = p.read_text()
text += """

@Composable
private fun ChatTypingIndicator(
    viewModel: ChatViewModel,
    typingPeerLabel: String,
) {
    val isPeerTyping by viewModel.isPeerTyping.collectAsState()
    AnimatedVisibility(
        visible = isPeerTyping,
        enter = fadeIn(ChatChromeMotion.ShortFade) + slideInVertically(
            animationSpec = ChatChromeMotion.ShortSlide,
            initialOffsetY = { it / 4 },
        ),
        exit = fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)) + slideOutVertically(
            animationSpec = ChatChromeMotion.ShortSlide,
            targetOffsetY = { it / 4 },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 80.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bubbleShape = RoundedCornerShape(
                topStart = chatBubbleScaledDp(6f),
                topEnd = chatBubbleScaledDp(21f),
                bottomStart = chatBubbleScaledDp(21f),
                bottomEnd = chatBubbleScaledDp(21f),
            )
            Box(
                modifier = Modifier
                    .border(width = 1.dp, color = PrimaryBlue.copy(alpha = 0.15f), shape = bubbleShape)
                    .clip(bubbleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
                    .padding(horizontal = chatBubbleScaledDp(18f), vertical = chatBubbleScaledDp(12f)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(chatBubbleScaledDp(9f)),
                ) {
                    Text(
                        text = typingPeerLabel,
                        style = chatBubbleReplySnippetStyle(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = FontStyle.Italic,
                    )
                    ChatTypingDots()
                }
            }
        }
    }
}
"""
p.write_text(text)

timeline = "composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatMessageTimeline.kt"
replace_once(
    timeline,
    "import androidx.compose.runtime.MutableState\n",
    "import androidx.compose.runtime.MutableState\nimport androidx.compose.runtime.collectAsState\nimport androidx.compose.runtime.getValue\n",
)
replace_once(
    timeline,
    "import kotlinx.coroutines.delay\n",
    "import kotlinx.coroutines.delay\nimport kotlinx.coroutines.flow.StateFlow\n",
)
replace_once(
    timeline,
    "    reactionsMap: Map<String, List<MessageReaction>>,\n",
    "    reactionsMap: Map<String, List<MessageReaction>>,\n    reactionsFlow: StateFlow<Map<String, List<MessageReaction>>>? = null,\n",
)
replace_once(
    timeline,
    """    val onOpenBeaconState = rememberUpdatedState(onOpenBeacon)

    Box(""",
    """    val onOpenBeaconState = rememberUpdatedState(onOpenBeacon)
    val observedReactionsMap =
        if (reactionsFlow != null) {
            val liveReactions by reactionsFlow.collectAsState()
            liveReactions
        } else {
            reactionsMap
        }

    Box(""",
)
replace_once(
    timeline,
    "val msgReactions = reactionsMap[messageWithUser.message.id] ?: emptyList()",
    "val msgReactions = observedReactionsMap[messageWithUser.message.id] ?: emptyList()",
)
