@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.models.ChatWithDetails
import compose.project.click.click.data.models.User
import compose.project.click.click.ui.components.ClickCircularIconButton
import compose.project.click.click.ui.components.HubSheetHeaderAction
import compose.project.click.click.ui.components.LocalUseNativeHubSheetHeaderControls
import compose.project.click.click.ui.components.PlatformHubSheetHeaderButton
import compose.project.click.click.ui.components.platformPressScale
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/** Shared horizontal inset for chat header row and composer strip (outer edges align). */
internal val ChatChromeHorizontalPadding: Dp = 16.dp

/**
 * Second row of the chat identity block (under the username). Typing wins over
 * presence so the top bar never tries to render both on one horizontal line.
 */
internal fun chatPeerStatusSubtitle(
    isTyping: Boolean,
    isOnline: Boolean,
    lastSeenAtMs: Long? = null,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): String =
    when {
        isTyping -> "Typing…"
        isOnline -> "Online"
        lastSeenAtMs != null && lastSeenAtMs > 0L -> "Last seen ${formatLastSeenElapsed(lastSeenAtMs, nowMs)}"
        else -> "Offline"
    }

internal fun formatLastSeenElapsed(
    lastSeenAtMs: Long,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): String {
    val elapsedMs = (nowMs - lastSeenAtMs).coerceAtLeast(0L)
    return when {
        elapsedMs < 60_000L -> "just now"
        elapsedMs < 3_600_000L -> "${elapsedMs / 60_000L}m ago"
        elapsedMs < 86_400_000L -> "${elapsedMs / 3_600_000L}h ago"
        elapsedMs < 604_800_000L -> "${elapsedMs / 86_400_000L}d ago"
        else -> "${elapsedMs / 604_800_000L}w ago"
    }
}

internal fun chatGroupPresenceSubtitle(
    memberLastSeenAtMs: List<Long?>,
    onlineMemberCount: Int,
    nowMs: Long = Clock.System.now().toEpochMilliseconds(),
): String? =
    when {
        onlineMemberCount > 0 -> "$onlineMemberCount online"
        else ->
            memberLastSeenAtMs.filterNotNull().filter { it > 0L }.maxOrNull()?.let {
                "Last active ${formatLastSeenElapsed(it, nowMs)}"
            }
    }

/** One presence subtitle for Compose, native navigation, and profile media headers. */
@Composable
internal fun rememberChatPresenceSubtitle(
    chatDetails: ChatWithDetails?,
    isGroupChat: Boolean,
    isTyping: Boolean = false,
    isPeerOnline: Boolean = false,
): String? {
    if (chatDetails == null) return null
    val onlineUsers by AppDataManager.onlineUsers.collectAsState()
    val lastSeen by AppDataManager.lastSeenAtMs.collectAsState()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val connectedUsers by AppDataManager.connectedUsers.collectAsState()
    val nowMs by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) {
            delay(60_000L)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }

    fun lastSeenAt(user: User): Long? =
        listOfNotNull(user.lastPolled, connectedUsers[user.id]?.lastPolled, lastSeen[user.id])
            .filter { it > 0L }
            .maxOrNull()

    return if (isGroupChat) {
        val peers = chatDetails.groupMemberUsers.filter { it.id != currentUser?.id }.distinctBy { it.id }
        chatGroupPresenceSubtitle(peers.map(::lastSeenAt), peers.count { it.id in onlineUsers }, nowMs)
    } else {
        chatPeerStatusSubtitle(
            isTyping = isTyping,
            isOnline = isPeerOnline || chatDetails.otherUser.id in onlineUsers,
            lastSeenAtMs = lastSeenAt(chatDetails.otherUser),
            nowMs = nowMs,
        )
    }
}

/**
 * Circular header action for chat / hub threads.
 *
 * Prefer [showBorder]=true only for the primary back control. Trailing actions (edit / call / ⋮)
 * stay borderless so a row of 40dp rings does not crowd the title.
 */
@Composable
internal fun ChatHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    showBorder: Boolean = false,
) {
    val nativeSheetAction =
        if (LocalUseNativeHubSheetHeaderControls.current && LocalPlatformStyle.current.isIOS && enabled) {
            when (contentDescription) {
                "Back" -> HubSheetHeaderAction.Back
                "Hub settings" -> HubSheetHeaderAction.More
                else -> null
            }
        } else {
            null
        }
    if (nativeSheetAction != null) {
        PlatformHubSheetHeaderButton(
            action = nativeSheetAction,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier.size(if (size < 44.dp) 44.dp else size),
        )
        return
    }

    ClickCircularIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        size = size,
        iconSize = iconSize,
        showBorder = showBorder,
        glassStrength = if (LocalPlatformStyle.current.isIOS) 0.64f else 0.4f,
    )
}

/** Opaque content plate; native iOS navigation chrome owns actual Liquid Glass. */
@Composable
internal fun ChatLiquidGlassPlate(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 18.dp,
    testTag: String,
) {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBlur = blurRadius
    val nativeSheetHeader =
        LocalUseNativeHubSheetHeaderControls.current && LocalPlatformStyle.current.isIOS
    val background = if (nativeSheetHeader) tint.copy(alpha = 0.86f) else tint
    Box(
        modifier =
            modifier
                .graphicsLayer { clip = true }
                .background(background)
                .testTag(testTag),
    )
}

/** Solid underlay for composer chrome — no decorative gradient fade. */
@Composable
internal fun ChatComposerChromeFadeUnderlay(
    modifier: Modifier = Modifier,
    testTag: String = ChatGlassComposerPlateTestTag,
) {
    val bg = MaterialTheme.colorScheme.surface
    Box(
        modifier =
            modifier
                .testTag(testTag)
                .background(bg),
    )
}

@Composable
internal fun Modifier.chatSpringPressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    return this
        .platformPressScale(interactionSource, MotionTokens.PressScale.IconPressedScale)
        .graphicsLayer {
            alpha = if (pressed) 0.92f else 1f
        }
}

/**
 * Messenger-style composer colors. Focus is communicated by cursor/action state rather than a
 * bright brand outline around the whole field; that keeps the composer visually quiet while typing.
 */
@Composable
internal fun rememberChatComposerFieldColors(): TextFieldColors {
    val fieldFill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (LocalPlatformStyle.current.isIOS) 0.78f else 1f)
    val outline = MaterialTheme.colorScheme.outline
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = outline.copy(alpha = if (LocalPlatformStyle.current.isIOS) 0.28f else 0.5f),
        unfocusedBorderColor = outline.copy(alpha = if (LocalPlatformStyle.current.isIOS) 0.16f else 0.34f),
        focusedContainerColor = fieldFill,
        unfocusedContainerColor = fieldFill,
    )
}

/** Press scale + null indication; pair with subtle chrome tweaks at call sites for tactile feedback. */
@Composable
fun Modifier.bouncingClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return bouncingClickable(interactionSource, enabled, onClick)
}

@Composable
fun Modifier.bouncingClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    this.chatSpringPressScale(interactionSource).clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
