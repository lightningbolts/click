package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalityEditor(
    selectedTags: List<String>,
    onSelectedTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = canonicalizePersonalityTags(selectedTags)
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PERSONALITY_TRAITS.forEach { trait ->
            val isSelected = trait in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onSelectedTagsChange(
                        if (isSelected) {
                            selected.filter { it != trait }
                        } else if (selected.size < PERSONALITY_REQUIRED_TAG_COUNT) {
                            selected + trait
                        } else {
                            selected
                        },
                    )
                },
                enabled = isSelected || selected.size < PERSONALITY_REQUIRED_TAG_COUNT,
                label = { Text(trait) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlue.copy(alpha = 0.18f),
                    selectedLabelColor = PrimaryBlue,
                ),
            )
        }
    }
}
