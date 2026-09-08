@file:Suppress(
    "ktlint:standard:function-naming",
)
@file:OptIn(ExperimentalFoundationApi::class)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.MotionTokens // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickCardSurface // pragma: allowlist secret

/**
 * Shared gutters for launch surfaces. Values wrap the existing 8pt scale and the native
 * large-title leading inset (20pt) so tab roots and [AppScreenScaffold] stay aligned.
 *
 * Use [Horizontal] instead of a screen-local `20.dp` / `16.dp` constant.
 * Purple, typefaces, and radii stay in [compose.project.click.click.ui.theme].
 */
object ClickScreenSpacing {
    /** Tab-root / sheet gutter — matches native title leading, not chat bubble inset. */
    val Horizontal = 20.dp

    /** Space between major sections (greeting → hero, list → composer). */
    val Section = 24.dp

    /** Gap between stacked rows inside a section. */
    val RowGap = 8.dp

    /** Compact inner padding (chips, icon wells, section-header trailing). */
    val Compact = 8.dp
}

/** Default two-line social row; one-line rows can be shorter via [ClickListRow] padding. */
val ClickListRowHeight = 64.dp

/**
 * Canonical social / settings / search row: avatar or icon, title, one subtitle line,
 * optional trailing status. No resting border. Optional inset divider.
 *
 * Horizontal inset comes from [AppScreenScaffold] / [ClickSheetDefaults] — do not add another
 * 16–20.dp here or rows will look like inset cards.
 *
 * Prefer this over wrapping a row in [AdaptiveCard] / [GlassCard].
 */
@Composable
fun ClickListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    dividerStartIndent: Dp = ClickPlatformListDividerIndent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val rowShape = RoundedCornerShape(LocalPlatformStyle.current.compactCardCornerRadius)
    val clickableModifier =
        when {
            onClick != null && onLongPress != null ->
                Modifier
                    .platformPressScale(interactionSource)
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = if (LocalPlatformStyle.current.useRipple) ripple(bounded = true) else null,
                        onClick = onClick,
                        onLongClick = onLongPress,
                    )
            onClick != null ->
                Modifier
                    .platformPressScale(interactionSource)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = if (LocalPlatformStyle.current.useRipple) ripple(bounded = true) else null,
                        onClick = onClick,
                    )
            else -> Modifier
        }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(rowShape)
                    .then(clickableModifier)
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(vertical = ClickScreenSpacing.Compact),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(
                    modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    leading()
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f), content = content)
            if (trailing != null) {
                Spacer(modifier = Modifier.width(ClickScreenSpacing.Compact))
                trailing()
            }
        }
        if (showDivider) {
            ClickInsetDivider(startIndent = dividerStartIndent)
        }
    }
}

@Composable
fun ClickListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
    dividerStartIndent: Dp = ClickPlatformListDividerIndent,
) {
    ClickListRow(
        modifier = modifier,
        onClick = onClick,
        onLongPress = onLongPress,
        leading = leading,
        trailing = trailing,
        showDivider = showDivider,
        dividerStartIndent = dividerStartIndent,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Section title with the standard compact gap under the label.
 * Wraps [SectionHeader] — do not re-space titles per screen.
 */
@Composable
fun ClickSectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    SectionHeader(
        text = text,
        modifier = modifier.padding(bottom = ClickScreenSpacing.Compact),
        caption = caption,
        trailing = trailing,
    )
}

/**
 * Compact in-content icon action (not header glass chrome).
 * Neutral by default; [selected] is the only purple treatment.
 */
@Composable
fun ClickActionIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    val tint =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> PrimaryBlue
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier =
            modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .platformPressScale(interactionSource, MotionTokens.PressScale.IconPressedScale)
                .clip(RoundedCornerShape(LocalPlatformStyle.current.compactCardCornerRadius))
                .clickable(
                    interactionSource = interactionSource,
                    indication = if (LocalPlatformStyle.current.useRipple) ripple(bounded = true) else null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Filter / trait chip. Unselected recedes; selected uses primary container without a
 * second purple outline. Do not use chips for ordinary navigation — prefer [ClickListRow].
 */
@Composable
fun ClickChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    val background =
        when {
            !enabled -> MaterialTheme.colorScheme.surface
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val foreground =
        when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val showBorder = enabled && !selected
    val hPad = if (compact) 10.dp else 12.dp
    val vPad = if (compact) 6.dp else 8.dp
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier =
            modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .platformPressScale(interactionSource, MotionTokens.PressScale.ButtonPressedScale)
                .clip(shape)
                .background(background)
                .then(
                    if (showBorder) {
                        Modifier.border(clickBorderWidth(), clickBorderColor(), shape)
                    } else {
                        Modifier
                    },
                ).selectable(
                    selected = selected,
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = if (LocalPlatformStyle.current.useRipple) ripple(bounded = true) else null,
                    enabled = enabled,
                    onClick = onClick,
                ).padding(horizontal = hPad, vertical = vPad),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                tint = foreground,
            )
        }
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Enclosure used only when grouping is meaningful. Default is a quiet surface with no
 * border and no shadow — not a second nested card family.
 */
@Composable
fun ClickContentCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showBorder: Boolean = false,
    containerColor: Color = clickCardSurface(),
    contentPadding: Dp = ClickScreenSpacing.Horizontal,
    content: @Composable ColumnScope.() -> Unit,
) {
    val radius = LocalPlatformStyle.current.cardCornerRadius
    val shape = RoundedCornerShape(radius)
    val interactionSource = remember { MutableInteractionSource() }
    val cardModifier =
        modifier
            .then(
                if (onClick != null) {
                    Modifier.platformPressScale(interactionSource)
                } else {
                    Modifier
                },
            ).then(
                if (showBorder) {
                    Modifier.border(clickBorderWidth(), clickBorderColor(), shape)
                } else {
                    Modifier
                },
            )
    if (onClick != null) {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = containerColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
            onClick = onClick,
            interactionSource = interactionSource,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    } else {
        Surface(
            modifier = cardModifier,
            shape = shape,
            color = containerColor,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content,
            )
        }
    }
}
