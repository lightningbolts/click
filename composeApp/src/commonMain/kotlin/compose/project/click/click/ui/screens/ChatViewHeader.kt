@file:Suppress(
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:function-naming",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Edit // pragma: allowlist secret
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.PlatformHapticsPolicy // pragma: allowlist secret
import compose.project.click.click.data.models.ChatWithDetails // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatChromeHorizontalPadding // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatGlassHeaderPlateTestTag // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatHeaderIconButton // pragma: allowlist secret
import compose.project.click.click.ui.chat.GroupMembersPickerContext // pragma: allowlist secret
import compose.project.click.click.ui.chat.chatPeerStatusSubtitle // pragma: allowlist secret
import compose.project.click.click.ui.chat.groupMembersPickerContextFrom // pragma: allowlist secret
import compose.project.click.click.ui.components.AvatarWithOnlineIndicator // pragma: allowlist secret
import compose.project.click.click.ui.components.BindPlatformNativeNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDropdownMenu // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.components.CoreConnectionAvatarFrame // pragma: allowlist secret
import compose.project.click.click.ui.components.GroupAvatar // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeAction // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeIdentity // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeChromeMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.groupAvatarClusterWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.ChatMessagesState // pragma: allowlist secret

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatViewSuccessHeader(
    nativeNavChrome: Boolean,
    chatNativeClearance: Dp,
    topInset: Dp,
    chatDetails: ChatWithDetails,
    isGroupChat: Boolean,
    groupTitle: String,
    memberSummaryLine: String?,
    isPeerTyping: Boolean,
    isPeerOnline: Boolean,
    onlineUsers: Set<String>,
    coreConnectionIds: Set<String>,
    chatHasIntentOverlap: Boolean,
    onBackPressed: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenGroupMembersPicker: (GroupMembersPickerContext) -> Unit,
    startVoiceCall: () -> Unit,
    startVideoCall: () -> Unit,
    showCallMenuState: MutableState<Boolean>,
    showConnectionSheetState: MutableState<Boolean>,
    showRenameGroupDialogState: MutableState<Boolean>,
    renameGroupDraftState: MutableState<String>,
) {
    var showCallMenu by showCallMenuState
    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState
    var renameGroupDraft by renameGroupDraftState
    if (nativeNavChrome) {
        Spacer(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chatNativeClearance)
                    .testTag(ChatGlassHeaderPlateTestTag),
        )
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = topInset)
                    .heightIn(min = 56.dp)
                    .padding(horizontal = ChatChromeHorizontalPadding, vertical = 6.dp)
                    .testTag(ChatGlassHeaderPlateTestTag),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatHeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBackPressed,
                    showBorder = true,
                )

                if (isGroupChat) {
                    val chatHeaderGroupAvatarSize = 34.dp
                    val groupAvatarUrl =
                        chatDetails.groupClique
                            ?.avatarUrl
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                    val groupClusterWidth =
                        if (groupAvatarUrl != null) {
                            chatHeaderGroupAvatarSize
                        } else {
                            groupAvatarClusterWidth(
                                chatDetails.groupMemberUsers.size,
                                chatHeaderGroupAvatarSize,
                            )
                        }
                    Box(
                        modifier =
                            Modifier
                                .width(groupClusterWidth)
                                .heightIn(min = 40.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = false, radius = 22.dp),
                                    onClick = {
                                        groupMembersPickerContextFrom(chatDetails)
                                            ?.let(onOpenGroupMembersPicker)
                                    },
                                ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        GroupAvatar(
                            members = chatDetails.groupMemberUsers,
                            avatarSize = chatHeaderGroupAvatarSize,
                            avatarUrl = groupAvatarUrl,
                        )
                    }
                } else {
                    val isPeerCore = chatDetails.connection.id in coreConnectionIds
                    val peerOnline =
                        chatDetails.otherUser.id in onlineUsers || isPeerOnline
                    AvatarWithOnlineIndicator(
                        isOnline = peerOnline,
                        indicatorSize = 9.dp,
                        indicatorBorder = 1.25.dp,
                    ) {
                        CoreConnectionAvatarFrame(
                            isCore = isPeerCore,
                            avatarSize = 36.dp,
                            onClick = { onOpenUserProfile(chatDetails.otherUser.id) },
                        ) {
                            ConnectionListUserAvatarFace(
                                displayName = chatDetails.otherUser.name,
                                email = chatDetails.otherUser.email,
                                avatarUrl = chatDetails.otherUser.image,
                                userId = chatDetails.otherUser.id,
                                modifier = Modifier.fillMaxSize(),
                                useCompactTypography = true,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement =
                        Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = if (isGroupChat) groupTitle else (chatDetails.otherUser.name ?: "Unknown"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isGroupChat && memberSummaryLine != null) {
                        Text(
                            text = memberSummaryLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (!isGroupChat) {
                        val subtitleOnline =
                            chatDetails.otherUser.id in onlineUsers || isPeerOnline
                        val statusText =
                            chatPeerStatusSubtitle(
                                isTyping = isPeerTyping,
                                isOnline = subtitleOnline,
                            )
                        val showOnlineDot = subtitleOnline && !isPeerTyping
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AnimatedVisibility(
                                visible = showOnlineDot,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF22C55E)),
                                )
                            }
                            AnimatedContent(
                                targetState = statusText,
                                transitionSpec = {
                                    fadeIn(
                                        animationSpec =
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                    ) togetherWith
                                        fadeOut(
                                            animationSpec =
                                                spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium,
                                                ),
                                        )
                                },
                                label = "peer_presence_subtitle",
                            ) { label ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color =
                                        if (label == "Online") {
                                            Color(0xFF16A34A)
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        },
                                )
                            }
                        }
                    }
                }

                if (!isGroupChat && chatHasIntentOverlap) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = "Shared availability",
                        tint = Color(0xFFFBBF24),
                        modifier = Modifier.size(22.dp),
                    )
                }

                if (isGroupChat) {
                    ChatHeaderIconButton(
                        icon = Icons.Outlined.Edit,
                        contentDescription = "Rename group",
                        onClick = {
                            renameGroupDraft = groupTitle
                            showRenameGroupDialog = true
                        },
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    )
                }

                Box {
                    ChatHeaderIconButton(
                        icon = Icons.Filled.Call,
                        contentDescription = "Call options",
                        onClick = { showCallMenu = true },
                        tint = PrimaryBlue.copy(alpha = 0.85f),
                    )
                    ClickDropdownMenu(
                        expanded = showCallMenu,
                        onDismissRequest = { showCallMenu = false },
                        items =
                            listOf(
                                ClickMenuItem(
                                    label = if (isGroupChat) "Group voice call" else "Voice call",
                                    onClick = {
                                        PlatformHapticsPolicy.lightImpact()
                                        startVoiceCall()
                                    },
                                    icon = Icons.Filled.Call,
                                ),
                                ClickMenuItem(
                                    label = if (isGroupChat) "Group video call" else "Video call",
                                    onClick = {
                                        PlatformHapticsPolicy.lightImpact()
                                        startVideoCall()
                                    },
                                    icon = Icons.Filled.Videocam,
                                ),
                            ),
                    )
                }
                // Overflow / connection options
                ChatHeaderIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    onClick = { showConnectionSheet = true },
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
internal fun ChatViewNativeNavBinding(
    nativeNavChrome: Boolean,
    chatId: String,
    bindTitle: String,
    bindStatusSubtitle: String?,
    bindOnline: Boolean?,
    bindIsGroup: Boolean,
    bindAvatarUrl: String?,
    successChat: ChatMessagesState.Success?,
    hintedChatRow: ChatWithDetails?,
    onOpenUserProfile: (String) -> Unit,
    onOpenGroupMembersPicker: (GroupMembersPickerContext) -> Unit,
    onBackPressed: () -> Unit,
    showConnectionSheetState: MutableState<Boolean>,
    showRenameGroupDialogState: MutableState<Boolean>,
    renameGroupDraftState: MutableState<String>,
) {
    var showConnectionSheet by showConnectionSheetState
    var showRenameGroupDialog by showRenameGroupDialogState
    var renameGroupDraft by renameGroupDraftState
    if (nativeNavChrome) {
        BindPlatformNativeNavigationBar(
            title = bindTitle,
            subtitle = bindStatusSubtitle,
            presenceOnline = bindOnline,
            identity =
                NativeChromeIdentity(
                    displayName = bindTitle,
                    email =
                        successChat
                            ?.chatDetails
                            ?.otherUser
                            ?.email
                            ?: hintedChatRow?.otherUser?.email,
                    avatarUrl = bindAvatarUrl,
                    userId =
                        successChat
                            ?.chatDetails
                            ?.groupClique
                            ?.groupId
                            ?: successChat
                                ?.chatDetails
                                ?.otherUser
                                ?.id
                            ?: hintedChatRow?.groupClique?.groupId
                            ?: hintedChatRow?.otherUser?.id
                            ?: chatId,
                    onClick = {
                        if (bindIsGroup) {
                            successChat?.chatDetails?.let { details ->
                                groupMembersPickerContextFrom(details)?.let(onOpenGroupMembersPicker)
                            }
                        } else {
                            val peerId =
                                successChat
                                    ?.chatDetails
                                    ?.otherUser
                                    ?.id
                                    ?: hintedChatRow?.otherUser?.id
                            if (peerId != null) onOpenUserProfile(peerId)
                        }
                    },
                ),
            onNavigateBack = onBackPressed,
            nativeTrailingActions =
                buildList {
                    if (bindIsGroup) {
                        add(
                            NativeChromeAction(
                                sfSymbol = "pencil",
                                contentDescription = "Rename group",
                                onClick = {
                                    renameGroupDraft =
                                        successChat
                                            ?.chatDetails
                                            ?.groupClique
                                            ?.name
                                            .orEmpty()
                                            .ifBlank { bindTitle }
                                    showRenameGroupDialog = true
                                },
                            ),
                        )
                    }
                    add(
                        NativeChromeAction(
                            sfSymbol = "phone.fill",
                            contentDescription = "Call options",
                            onClick = {},
                            menuItems =
                                listOf(
                                    NativeChromeMenuItem(
                                        title = if (bindIsGroup) "Group voice call" else "Voice call",
                                        sfSymbol = "phone.fill",
                                        onClick = { successChat?.let { startOutgoingChatCall(it, videoEnabled = false) } },
                                    ),
                                    NativeChromeMenuItem(
                                        title = if (bindIsGroup) "Group video call" else "Video call",
                                        sfSymbol = "video.fill",
                                        onClick = { successChat?.let { startOutgoingChatCall(it, videoEnabled = true) } },
                                    ),
                                ),
                        ),
                    )
                    add(
                        NativeChromeAction(
                            sfSymbol = "ellipsis.circle",
                            contentDescription = "More options",
                            onClick = { showConnectionSheet = true },
                        ),
                    )
                },
            collapseFraction = 1f,
        )
    }
}
