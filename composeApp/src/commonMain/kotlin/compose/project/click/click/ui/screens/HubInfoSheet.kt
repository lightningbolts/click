@file:Suppress("ktlint:standard:function-naming")
@file:OptIn(ExperimentalLayoutApi::class)

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetChrome // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickSheetDefaults // pragma: allowlist secret
import compose.project.click.click.ui.components.sheetBodyScroll // pragma: allowlist secret

/** Hub categories shared with iOS `HubCategory`. */
val HUB_CATEGORIES: List<String> =
    listOf("general", "music", "study", "sports", "food", "nightlife", "gaming", "tech", "art", "fitness", "networking", "party")

internal fun hubCategoryLabel(category: String): String = category.trim().replaceFirstChar { it.uppercase() }

/** Picker options: the fixed set, plus the hub's current value when it's a legacy free-text one. */
internal fun hubCategoryOptions(current: String): List<String> {
    val normalized = current.trim().lowercase()
    return if (normalized.isEmpty() || normalized in HUB_CATEGORIES) HUB_CATEGORIES else HUB_CATEGORIES + normalized
}

/** Hub details (iOS `HubInfoView`): type, category (owner can change), who's here, members. */
@Composable
internal fun HubInfoSheet(
    name: String,
    category: String,
    isEventHub: Boolean,
    isOwner: Boolean,
    occupantCount: Int,
    memberIds: List<String>,
    viewerUserId: String,
    memberName: (String) -> String,
    onCategoryChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ClickFormBottomSheet(onDismissRequest = onDismiss) {
        ClickSheetChrome(
            title = name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .sheetBodyScroll()
                    .padding(horizontal = ClickSheetDefaults.ContentHorizontalPadding),
        ) {
            InfoRow("Type", if (isEventHub) "Event hub" else "Community hub")
            if (isOwner && !isEventHub) {
                Text("Category", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    hubCategoryOptions(category).forEach { option ->
                        FilterChip(
                            selected = option.equals(category.trim(), ignoreCase = true),
                            onClick = { if (!option.equals(category.trim(), ignoreCase = true)) onCategoryChange(option) },
                            label = { Text(hubCategoryLabel(option)) },
                        )
                    }
                }
            } else if (category.isNotBlank()) {
                InfoRow("Category", hubCategoryLabel(category))
            }
            InfoRow("Here now", if (occupantCount == 1) "1 person" else "$occupantCount people")
            Text(
                if (isEventHub) "Guests who RSVP'd can chat here." else "People nearby can join and post while they're within reach.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            HorizontalDivider()
            Text(
                "Members",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            if (memberIds.isEmpty()) {
                Text("No members to show yet", style = MaterialTheme.typography.bodyMedium)
            }
            memberIds.forEach { id ->
                Text(
                    if (id == viewerUserId) "You" else memberName(id),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(ClickSheetDefaults.ContentBottomPadding))
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
