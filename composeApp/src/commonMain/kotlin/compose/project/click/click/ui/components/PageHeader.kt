package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
enum class HeaderDisplayMode {
    Large,
    Inline
}

@Composable
private fun PresenceSubtitleRow(online: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnimatedVisibility(
            visible = online,
            enter = fadeIn(tween(220)) + expandVertically(),
            exit = fadeOut(tween(180)) + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22C55E))
            )
        }
        AnimatedContent(
            targetState = online,
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
            },
            label = "page_header_presence"
        ) { isOn ->
            Text(
                text = if (isOn) "Online" else "Offline",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOn) Color(0xFF16A34A)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

/**
 * Floating liquid-glass header island. [collapseFraction] is 0 (large) → 1 (compact) driven by
 * [AppScreenScaffold] scroll position.
 */
@Composable
fun LiquidGlassPageHeader(
    title: String,
    subtitle: String? = null,
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    collapseFraction: Float = 0f,
) {
    val collapsed = collapseFraction.coerceIn(0f, 1f)
    val animatedCollapse by animateFloatAsState(
        targetValue = collapsed,
        animationSpec = tween(180),
        label = "header_collapse",
    )
    val titleSize = (34f - 14f * animatedCollapse).sp
    val verticalPad = (12f - 4f * animatedCollapse).dp

    val displayMode = when {
        navigationIcon != null -> HeaderDisplayMode.Inline
        animatedCollapse > 0.55f -> HeaderDisplayMode.Inline
        else -> HeaderDisplayMode.Large
    }

    LiquidGlassPill(
        modifier = Modifier.fillMaxWidth(),
        cornerRadiusDp = (28 - (10 * animatedCollapse).toInt()).coerceAtLeast(18),
        backgroundStrength = animatedCollapse,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = 1f - 0.02f * animatedCollapse
                    scaleY = 1f - 0.02f * animatedCollapse
                }
                .padding(vertical = verticalPad),
        ) {
            when (displayMode) {
                HeaderDisplayMode.Inline -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (navigationIcon != null) {
                            navigationIcon()
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = titleSize,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (subtitle != null && animatedCollapse < 0.85f) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (presenceOnline != null && animatedCollapse < 0.85f) {
                                Spacer(modifier = Modifier.height(2.dp))
                                PresenceSubtitleRow(online = presenceOnline)
                            }
                        }
                        if (actions != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            actions()
                        }
                    }
                }
                HeaderDisplayMode.Large -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                fontSize = titleSize,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (subtitle != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (presenceOnline != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                PresenceSubtitleRow(online = presenceOnline)
                            }
                        }
                        if (actions != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                actions()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Magnifying-glass action for tab headers — opens [compose.project.click.click.ui.screens.UnifiedSearchSheet]. */
@Composable
fun HeaderSearchIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    /** When non-null, shows a small presence line (e.g. Online / Offline) under [subtitle]. */
    presenceOnline: Boolean? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable (RowScope.() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") displayMode: HeaderDisplayMode =
        if (navigationIcon != null) HeaderDisplayMode.Inline else HeaderDisplayMode.Large,
    collapseFraction: Float = 0f,
) {
    LiquidGlassPageHeader(
        title = title,
        subtitle = subtitle,
        presenceOnline = presenceOnline,
        navigationIcon = navigationIcon,
        actions = actions,
        collapseFraction = collapseFraction,
    )
}
