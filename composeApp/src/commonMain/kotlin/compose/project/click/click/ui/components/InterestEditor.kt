package compose.project.click.click.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue

/**
 * Shared interest picker used in onboarding and settings (predefined categories only).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterestEditor(
    selectedTags: List<String>,
    onSelectedTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    minTags: Int? = null,
    maxTags: Int? = null,
    showSelectionCount: Boolean = true,
) {
    var expandedCategory by remember { mutableStateOf<String?>(null) }

    fun toggleTag(tag: String) {
        val next = if (tag in selectedTags) {
            selectedTags.filter { it != tag }
        } else if (maxTags == null || selectedTags.size < maxTags) {
            selectedTags + tag
        } else {
            selectedTags
        }
        onSelectedTagsChange(next)
    }

    Column(modifier = modifier) {
        if (showSelectionCount && minTags != null) {
            Text(
                text = "${selectedTags.size} selected" +
                    if (selectedTags.size < minTags) " · need ${minTags - selectedTags.size} more" else " ✓",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = if (selectedTags.size >= minTags) PrimaryBlue
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else if (showSelectionCount) {
            Text(
                text = "${selectedTags.size} selected",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        INTEREST_CATEGORIES.forEachIndexed { index, category ->
            val isCategorySelected = category.label in selectedTags
            val hasSelectedSubs = category.subcategories.any { it in selectedTags }
            val isExpanded = expandedCategory == category.label
            val borderColor by animateColorAsState(
                targetValue = when {
                    isCategorySelected -> PrimaryBlue
                    hasSelectedSubs -> PrimaryBlue.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                },
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = if (index == 0) 0.dp else 5.dp)
                    .clickable { toggleTag(category.label) },
                shape = RoundedCornerShape(16.dp),
                color = if (isCategorySelected) PrimaryBlue.copy(alpha = 0.10f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, borderColor),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = category.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isCategorySelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        fontWeight = if (isCategorySelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    IconButton(
                        onClick = { expandedCategory = if (isExpanded) null else category.label },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    if (isCategorySelected) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val chipStyle = LocalPlatformStyle.current
                    category.subcategories.forEach { sub ->
                        val isSubSelected = sub in selectedTags
                        val atMax = maxTags != null && !isSubSelected && selectedTags.size >= maxTags
                        FilterChip(
                            selected = isSubSelected,
                            onClick = { if (!atMax) toggleTag(sub) },
                            enabled = !atMax,
                            label = {
                                Text(
                                    sub,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
                                )
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(
                                if (chipStyle.isIOS) 0.5.dp else 1.dp,
                                if (isSubSelected) PrimaryBlue else MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = if (chipStyle.isIOS) 0.12f else 0.15f,
                                ),
                            ),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = PrimaryBlue.copy(alpha = if (chipStyle.isIOS) 0.12f else 0.14f),
                                selectedLabelColor = PrimaryBlue,
                                labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            ),
                        )
                    }
                }
            }
        }
    }
}
