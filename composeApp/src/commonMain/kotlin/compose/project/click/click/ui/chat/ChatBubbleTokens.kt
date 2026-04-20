package compose.project.click.click.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Layout / type scale vs token base (typography uses theme × 1.5× × this). */
private const val REL = 0.8f

internal fun chatBubbleScaledDp(value: Float): Dp = (value * REL).dp

/** Inline audio time labels — same multiplier intent as message reply text. */
internal val chatBubbleAudioTimeTypeScale: Float
    get() = 1.5f * REL

/** Typing-indicator dot vertical bounce (full scale uses 6.dp peak). */
internal fun chatBubbleTypingDotOffsetY(progress: Float): Dp = (-6f * REL * progress).dp

/**
 * Layout and typography for in-app chat bubbles.
 * LazyColumn [Arrangement.spacedBy] values stay defined on the list, not here.
 */
internal object ChatBubbleTokens {
    /** Max width for message body as a fraction of measured chat row width (see [ChatMessageBubble]). */
    const val messageMaxWidthToParentFraction: Float = 0.75f

    /** Fallback when composables are not under measured constraints (e.g. previews). */
    val contentMaxWidth: Dp = chatBubbleScaledDp(450f)
    val cornerMain: Dp = chatBubbleScaledDp(27f)
    val cornerTailSmall: Dp = chatBubbleScaledDp(8f)
    val bubblePaddingHorizontal: Dp = chatBubbleScaledDp(12f)
    val bubblePaddingVertical: Dp = chatBubbleScaledDp(9f)
    val replyBlockCorner: Dp = chatBubbleScaledDp(15f)
    val replyBlockPaddingH: Dp = chatBubbleScaledDp(12f)
    val replyBlockPaddingV: Dp = chatBubbleScaledDp(9f)
    val replyAboveMediaSpacing: Dp = chatBubbleScaledDp(9f)
    val stackGap: Dp = chatBubbleScaledDp(9f)
    val peerAvatarSize: Dp = chatBubbleScaledDp(36f)
    val captionBelowImageSpacing: Dp = chatBubbleScaledDp(9f)
    val reactionFontSp: Float = 19.5f * REL
    val reactionChipCorner: Dp = chatBubbleScaledDp(18f)
    val reactionChipPadH: Dp = chatBubbleScaledDp(9f)
    val reactionChipPadV: Dp = chatBubbleScaledDp(3f)
    val reactionRowPadH: Dp = chatBubbleScaledDp(12f)
    val reactionRowPadV: Dp = chatBubbleScaledDp(2f)
    val bubbleRowHorizontalInset: Dp = chatBubbleScaledDp(9f)
    val peerAvatarEndPad: Dp = chatBubbleScaledDp(9f)
    val peerAvatarBottomPad: Dp = chatBubbleScaledDp(3f)
    /** Extra row gap between reaction chips (~6.dp before shrink). */
    val reactionChipGap: Dp = chatBubbleScaledDp(6f)
}

private const val BODY_TYPE_SCALE = 1.5f * REL
private const val EDITED_TYPE_SCALE = 1.35f * REL

@Composable
internal fun chatBubbleMessageTextStyle(): TextStyle {
    val base = MaterialTheme.typography.bodyMedium
    return base.copy(fontSize = scaleSp(base.fontSize, BODY_TYPE_SCALE))
}

@Composable
internal fun chatBubbleReplySnippetStyle(): TextStyle {
    val base = MaterialTheme.typography.bodySmall
    return base.copy(fontSize = scaleSp(base.fontSize, BODY_TYPE_SCALE))
}

@Composable
internal fun chatBubbleReplyLabelStyle(): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    return base.copy(fontSize = scaleSp(base.fontSize, BODY_TYPE_SCALE))
}

@Composable
internal fun chatBubbleEditedFootnoteStyle(): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    return base.copy(fontSize = scaleSp(base.fontSize, EDITED_TYPE_SCALE))
}

private fun scaleSp(unit: TextUnit, factor: Float): TextUnit =
    (unit.value * factor).sp
