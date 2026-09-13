@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalityEditor(
    selectedTags: List<String>,
    onSelectedTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = canonicalizePersonalityTags(selectedTags)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PERSONALITY_TRAIT_GROUPS.forEach { group ->
            ClickSectionHeader(text = group.title)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                group.traits.forEach { trait ->
                    val isSelected = trait in selected
                    ClickChip(
                        label = trait,
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
                        compact = true,
                    )
                }
            }
        }
    }
}
