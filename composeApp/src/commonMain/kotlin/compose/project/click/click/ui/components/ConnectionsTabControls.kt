package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.LightBlue
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Active / Groups / Archived filter — exterior radius matches chat list bubbles ([GlassSheetTokens.BentoExteriorCorner]).
 */
@Composable
fun ConnectionsSegmentBar(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeCount: Int,
    groupCount: Int,
    archivedCount: Int,
    modifier: Modifier = Modifier,
) {
    val segStyle = LocalPlatformStyle.current
    val segBorderWidth = if (segStyle.isIOS) 0.5.dp else 1.dp
    val exterior = GlassSheetTokens.BentoExteriorCorner
    val interior = GlassSheetTokens.BentoInteriorCorner

    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = segBorderWidth,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                shape = RoundedCornerShape(exterior),
            )
            .clip(RoundedCornerShape(exterior))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(
                    alpha = if (segStyle.isIOS) 0.25f else 0.35f,
                ),
            )
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            Triple(0, "Active", activeCount),
            Triple(1, "Groups", groupCount),
            Triple(2, "Archived", archivedCount),
        ).forEach { (index, label, count) ->
            val selected = selectedTabIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(interior))
                    .then(
                        if (selected) {
                            Modifier
                                .background(PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.14f else 0.18f))
                                .border(
                                    segBorderWidth,
                                    PrimaryBlue.copy(alpha = if (segStyle.isIOS) 0.25f else 0.35f),
                                    RoundedCornerShape(interior),
                                )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$label ($count)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) LightBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Compact liquid-glass filter when the connections header is collapsed. */
@Composable
fun ConnectionsTabFilterDropdown(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeCount: Int,
    groupCount: Int,
    archivedCount: Int,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val labels = listOf(
        "Active ($activeCount)",
        "Groups ($groupCount)",
        "Archived ($archivedCount)",
    )
    val currentLabel = labels.getOrElse(selectedTabIndex) { labels[0] }

    Box(modifier = modifier.fillMaxWidth()) {
        LiquidGlassPill(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            cornerRadiusDp = GlassSheetTokens.BentoExteriorCorner.value.toInt(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = "Change filter",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            labels.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onTabSelected(index)
                        expanded = false
                    },
                    leadingIcon = if (selectedTabIndex == index) {
                        {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = PrimaryBlue)
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@Composable
fun ConnectionsHeaderTabControls(
    collapseFraction: Float,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    activeCount: Int,
    groupCount: Int,
    archivedCount: Int,
    showTabs: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!showTabs) return

    val showDropdown = collapseFraction > 0.42f
    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = !showDropdown,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(140)) + shrinkVertically(),
        ) {
            ConnectionsSegmentBar(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                activeCount = activeCount,
                groupCount = groupCount,
                archivedCount = archivedCount,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = showDropdown,
            enter = fadeIn(tween(180)) + expandVertically(),
            exit = fadeOut(tween(140)) + shrinkVertically(),
        ) {
            ConnectionsTabFilterDropdown(
                selectedTabIndex = selectedTabIndex,
                onTabSelected = onTabSelected,
                activeCount = activeCount,
                groupCount = groupCount,
                archivedCount = archivedCount,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
