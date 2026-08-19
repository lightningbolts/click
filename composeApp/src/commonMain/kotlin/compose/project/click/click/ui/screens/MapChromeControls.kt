@file:Suppress(
    "ktlint:standard:function-naming",
    "ktlint:standard:no-wildcard-imports",
    "ktlint:standard:max-line-length",
)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import compose.project.click.click.data.models.MapBeacon // pragma: allowlist secret
import compose.project.click.click.data.models.heroImageUrl // pragma: allowlist secret
import compose.project.click.click.getPlatform // pragma: allowlist secret
import compose.project.click.click.ui.components.CardVisualHero // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickCircularGlassIconButton // pragma: allowlist secret
import compose.project.click.click.ui.components.LiquidGlassPill // pragma: allowlist secret
import compose.project.click.click.ui.components.LocalNativeChromeActive // pragma: allowlist secret
import compose.project.click.click.ui.components.NativeMapLayerOption // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformNativeMapFloatingChrome // pragma: allowlist secret
import compose.project.click.click.ui.theme.* // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.utils.* // pragma: allowlist secret
import compose.project.click.click.viewmodel.MapLayerFilter // pragma: allowlist secret

@Composable
internal fun MapAlwaysOnChrome(
    dockBottomPadding: Dp,
    layerFilters: Set<MapLayerFilter>,
    onToggleLayerFilter: (MapLayerFilter) -> Unit,
    onDropBeacon: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    chromeVisible: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val style = LocalPlatformStyle.current
    if (style.isIOS) {
        PlatformNativeMapFloatingChrome(
            visible = chromeVisible && LocalNativeChromeActive.current,
            layerLabel = mapLayerFilterShortLabel(layerFilters),
            layerOptions =
                MapLayerFilter.entries.map { filter ->
                    NativeMapLayerOption(
                        id = filter.name,
                        label = filter.label,
                        selected =
                            when (filter) {
                                MapLayerFilter.ALL -> MapLayerFilter.ALL in layerFilters
                                else -> filter in layerFilters
                            },
                    )
                },
            onToggleLayerId = { id ->
                MapLayerFilter.entries.firstOrNull { it.name == id }?.let(onToggleLayerFilter)
            },
            onDropBeacon = onDropBeacon,
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            bottomPadding = dockBottomPadding,
        )
        return
    }
    val glassStrength = 0.4f
    val topSafe =
        WindowInsets.safeDrawing.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        )

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(40f)
                    .windowInsetsPadding(topSafe)
                    .padding(top = 8.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapLayerFilterDropdown(
                selected = layerFilters,
                onToggle = onToggleLayerFilter,
                opensDownward = true,
                modifier = Modifier.widthIn(max = 132.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .zIndex(10f)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 16.dp, end = 16.dp, bottom = dockBottomPadding),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ClickCircularGlassIconButton(
                icon = Icons.Filled.AddLocationAlt,
                contentDescription = "Drop beacon",
                onClick = onDropBeacon,
                glassStrength = glassStrength,
                size = 56.dp,
            )
            Spacer(modifier = Modifier.weight(1f))
            MapZoomGlassControls(
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                glassStrength = glassStrength,
            )
        }
    }
}

@Composable
internal fun MapZoomGlassControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    glassStrength: Float,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ClickCircularGlassIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Zoom in",
            onClick = onZoomIn,
            glassStrength = glassStrength,
            size = 48.dp,
        )
        ClickCircularGlassIconButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Zoom out",
            onClick = onZoomOut,
            glassStrength = glassStrength,
            size = 48.dp,
        )
    }
}

/** One-line label for the compact map layer control. */
internal fun mapLayerFilterShortLabel(selected: Set<MapLayerFilter>): String {
    if (MapLayerFilter.ALL in selected) return "All"
    val withoutAll = selected - MapLayerFilter.ALL
    if (withoutAll.isEmpty()) return "—"
    if (withoutAll.size == 1) {
        return when (val f = withoutAll.first()) {
            MapLayerFilter.MY_CONNECTIONS -> "Conn"
            MapLayerFilter.SOUNDTRACKS -> "Audio"
            MapLayerFilter.ALERTS_UTILITIES -> "Alerts"
            MapLayerFilter.SOCIAL_VIBES -> "Social"
            MapLayerFilter.COMMUNITY_HUBS -> "Hubs"
            else -> f.label.take(6)
        }
    }
    return "${withoutAll.size} on"
}

/**
 * Native [DropdownMenu] from a liquid-glass style pill (iOS) / solid surface (Android).
 * Menu opens **upward** (negative offset) so it stays on-screen over the bottom bar, with a
 * fully opaque container for readable text.
 */
@Composable
internal fun MapLayerFilterDropdown(
    selected: Set<MapLayerFilter>,
    onToggle: (MapLayerFilter) -> Unit,
    modifier: Modifier = Modifier,
    opensDownward: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val isIOS = remember { getPlatform().name.contains("iOS", ignoreCase = true) }
    val style = LocalPlatformStyle.current
    val menuSurface = MaterialTheme.colorScheme.surface
    val onMenuSurface = MaterialTheme.colorScheme.onSurface
    val menuOutline = clickBorderColor()
    val itemCount = MapLayerFilter.entries.size
    val menuUpOffset =
        if (opensDownward) {
            8.dp
        } else {
            -(itemCount * 48 + 24).dp
        }
    val menuWidth = 240.dp
    val triggerWidth = 132.dp
    val glassStrength = if (style.isIOS) 0.64f else 0.4f

    val triggerShape = RoundedCornerShape(20.dp)
    Box(
        modifier =
            modifier
                .widthIn(max = triggerWidth)
                .wrapContentWidth(Alignment.End),
    ) {
        LiquidGlassPill(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 48.dp)
                    .clip(triggerShape)
                    .clickable { expanded = true },
            cornerRadiusDp = 20,
            backgroundStrength = glassStrength,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = mapLayerFilterShortLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        // Same horizontal origin as the pill; full-opacity surface for legibility.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier =
                Modifier
                    .width(menuWidth)
                    .wrapContentWidth(Alignment.Start)
                    .zIndex(20f),
            offset = DpOffset(0.dp, -menuUpOffset),
            shape = RoundedCornerShape(if (isIOS) 14.dp else 12.dp),
            containerColor = menuSurface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(clickBorderWidth(), menuOutline),
        ) {
            MapLayerFilter.entries.forEach { filter ->
                val isSelected =
                    when (filter) {
                        MapLayerFilter.ALL -> MapLayerFilter.ALL in selected
                        else -> filter in selected
                    }
                DropdownMenuItem(
                    text = {
                        Text(
                            filter.label,
                            color = onMenuSurface,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        onToggle(filter)
                        expanded = false
                    },
                    leadingIcon = {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = onMenuSurface,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                )
            }
        }
    }
}

/**
 * Purely decorative header for a beacon detail sheet.
 *
 * It takes no title or subtitle on purpose. The structured content directly below is the single
 * source of the beacon's title, schedule, and location — repeating them in the banner meant every
 * detail sheet showed the same name and dates twice on one screen.
 */
@Composable
internal fun BeaconIdentityBanner(
    beacon: MapBeacon,
    showTypeChip: Boolean = true,
) {
    CardVisualHero(
        id = beacon.id,
        kind = beacon.kind,
        typeKey = beacon.sourceBeaconType,
        imageUrl = beacon.metadata.heroImageUrl(),
        chipLabel = beacon.displayTypeTitle().takeIf { showTypeChip },
        modifier =
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(12.dp)),
    )
}
