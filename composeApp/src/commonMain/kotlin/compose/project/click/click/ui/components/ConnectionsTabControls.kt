@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
)

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.ui.theme.LightBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderWidth // pragma: allowlist secret

/**
 * Clicks tab header: large native title lives in [AppScreenScaffold]; this is kept as a
 * compatibility wrapper that always shows the segment bar (never a compact fake pill).
 */
@Composable
fun ConnectionsFloatingHeader(
    collapseFraction: Float,
    title: String,
    subtitle: String?,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeCount: Int,
    groupCount: Int,
    archivedCount: Int,
    showTabs: Boolean,
    onOpenSearch: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") isScrollInProgress: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        CollapsibleGlassTopBar(
            title = title,
            subtitle = subtitle,
            collapseFraction = collapseFraction,
            actions =
                if (onOpenSearch != null) {
                    { HeaderSearchIconButton(onClick = onOpenSearch) }
                } else {
                    null
                },
        )
        if (showTabs) {
            ConnectionsSegmentBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                activeCount = activeCount,
                groupCount = groupCount,
                archivedCount = archivedCount,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Discovery map feed: Distance / Recent — same collapse behavior as [ConnectionsFloatingHeader].
 */
@Composable
fun DiscoveryFloatingHeader(
    collapseFraction: Float,
    title: String,
    subtitle: String?,
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    isRefreshing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val compact = collapseFraction > 0.42f
    val sortLabels = listOf("Distance", "Recent")
    val showGlass = collapseFraction > 0.01f
    Box(modifier = modifier.fillMaxWidth()) {
        if (showGlass) {
            HeaderGlassBackdrop(
                modifier = Modifier.matchParentSize(),
                collapseFraction = collapseFraction,
            )
        }
        AnimatedContent(
            targetState = compact,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            },
            label = "discovery_header_mode",
        ) { isCompact ->
            if (isCompact) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (onRefresh != null) {
                        HeaderRefreshIconButton(
                            onClick = onRefresh,
                            enabled = !isRefreshing,
                        )
                    }
                    if (onOpenSearch != null) {
                        HeaderSearchIconButton(onClick = onOpenSearch)
                    }
                    DiscoverySortFilterMenuChip(
                        selectedSortIndex = selectedSortIndex,
                        onSortSelected = onSortSelected,
                        labels = sortLabels,
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    CollapsibleGlassTopBar(
                        title = title,
                        subtitle = subtitle,
                        collapseFraction = collapseFraction,
                        actions =
                            if (onRefresh != null || onOpenSearch != null) {
                                {
                                    if (onRefresh != null) {
                                        HeaderRefreshIconButton(
                                            onClick = onRefresh,
                                            enabled = !isRefreshing,
                                        )
                                    }
                                    if (onOpenSearch != null) {
                                        HeaderSearchIconButton(onClick = onOpenSearch)
                                    }
                                }
                            } else {
                                null
                            },
                    )
                    DiscoverySortSegmentBar(
                        selectedTabIndex = selectedSortIndex,
                        onTabSelected = onSortSelected,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySortFilterMenuChip(
    selectedSortIndex: Int,
    onSortSelected: (Int) -> Unit,
    labels: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = labels.getOrElse(selectedSortIndex) { labels[0] }
    val chipCorner = GlassSheetTokens.BentoExteriorCorner - 6.dp

    Box {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(chipCorner))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(chipCorner))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = LightBlue,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Change sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ClickDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.zIndex(20f),
            items =
                labels.mapIndexed { index, label ->
                    ClickMenuItem(
                        label = label,
                        onClick = { onSortSelected(index) },
                        icon = if (selectedSortIndex == index) Icons.Filled.Check else null,
                    )
                },
        )
    }
}

/**
 * Active / Groups / Archived filter — exterior radius matches chat list bubbles ([GlassSheetTokens.BentoExteriorCorner]).
 */
@Composable
fun DiscoverySortSegmentBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val exterior = GlassSheetTokens.BentoExteriorCorner
    val trackPadding = 6.dp
    val segmentCorner = exterior - trackPadding

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(exterior))
                .clip(RoundedCornerShape(exterior))
                .background(MaterialTheme.colorScheme.surface)
                .padding(trackPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf("Distance", "Recent").forEachIndexed { index, label ->
            val selected = selectedTabIndex == index
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(segmentCorner))
                        .then(
                            if (selected) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(2.dp, PrimaryBlue, RoundedCornerShape(segmentCorner))
                            } else {
                                Modifier
                            },
                        ).clickable { onTabSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
fun ConnectionsSegmentBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeCount: Int,
    groupCount: Int,
    archivedCount: Int,
    modifier: Modifier = Modifier,
) {
    val exterior = GlassSheetTokens.BentoExteriorCorner
    val trackPadding = 6.dp
    val segmentCorner = exterior - trackPadding

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .border(clickBorderWidth(), clickBorderColor(), RoundedCornerShape(exterior))
                .clip(RoundedCornerShape(exterior))
                .background(MaterialTheme.colorScheme.surface)
                .padding(trackPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple(0, "Active", activeCount),
            Triple(1, "Groups", groupCount),
            Triple(2, "Archived", archivedCount),
        ).forEach { (index, label, count) ->
            val selected = selectedTabIndex == index
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(segmentCorner))
                        .then(
                            if (selected) {
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(2.dp, PrimaryBlue, RoundedCornerShape(segmentCorner))
                            } else {
                                Modifier
                            },
                        ).clickable { onTabSelected(index) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$label ($count)",
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
