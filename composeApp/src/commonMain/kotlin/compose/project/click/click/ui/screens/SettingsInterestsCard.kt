@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.repository.SupabaseRepository // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickChip // pragma: allowlist secret
import compose.project.click.click.ui.components.InterestEditor // pragma: allowlist secret
import compose.project.click.click.ui.components.filterToPredefinedInterestTags // pragma: allowlist secret
import kotlinx.coroutines.launch

/**
 * Settings interests editor — matches click-web SettingsView "My Interests":
 * no minimum/maximum tag count on save; predefined categories/subcategories only.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsInterestsCard(
    userId: String?,
    supabaseRepository: SupabaseRepository,
    onFeedback: suspend (String) -> Unit,
) {
    if (userId.isNullOrBlank()) return

    val scope = rememberCoroutineScope()
    val cachedTags by AppDataManager.userInterestTags.collectAsState()
    val filteredCached = remember(cachedTags) { filterToPredefinedInterestTags(cachedTags) }
    var interestTags by remember(userId, filteredCached) { mutableStateOf(filteredCached) }
    var savedTags by remember(userId, filteredCached) { mutableStateOf(filteredCached) }
    var tagsDirty by remember { mutableStateOf(false) }
    var tagsSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(filteredCached) {
        if (!tagsDirty) {
            interestTags = filteredCached
            savedTags = filteredCached
        }
    }

    // Background refresh only when cache is empty (cold start before AppDataManager finishes).
    LaunchedEffect(userId, cachedTags) {
        if (cachedTags.isNotEmpty()) return@LaunchedEffect
        supabaseRepository.fetchUserInterests(userId).fold(
            onSuccess = { row ->
                val loaded = filterToPredefinedInterestTags(row?.tags.orEmpty())
                AppDataManager.applyInterestTags(loaded)
            },
            onFailure = {
                loadError = it.message
                    ?.lines()
                    ?.firstOrNull()
                    ?.take(180)
                    ?: "Could not load interests"
            },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "My Interests",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Select categories and subcategories. Changes power Common Ground with your connections.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (filteredCached.isEmpty() && interestTags.isEmpty() && loadError == null) {
            CircularProgressIndicator(
                modifier = Modifier.padding(vertical = 16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            if (interestTags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    interestTags.forEach { tag ->
                        ClickChip(
                            label = tag,
                            selected = true,
                            onClick = {},
                            compact = true,
                        )
                    }
                }
            }

            InterestEditor(
                selectedTags = interestTags,
                onSelectedTagsChange = { next ->
                    interestTags = next
                    tagsDirty = next != savedTags
                },
                minTags = null,
                maxTags = null,
                showSelectionCount = true,
            )

            loadError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            ClickButton(
                onClick = {
                    scope.launch {
                        tagsSaving = true
                        val toSave = interestTags
                        supabaseRepository.updateUserInterests(userId, toSave).fold(
                            onSuccess = {
                                savedTags = toSave
                                tagsDirty = false
                                loadError = null
                                AppDataManager.applyInterestTags(toSave)
                                onFeedback("Saved ${toSave.size} interests")
                            },
                            onFailure = { e ->
                                val msg =
                                    e.message
                                        ?.lines()
                                        ?.firstOrNull()
                                        ?.take(180)
                                        ?: "Could not save interests"
                                loadError = msg
                                onFeedback(msg)
                            },
                        )
                        tagsSaving = false
                    }
                },
                enabled = tagsDirty && !tagsSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (tagsSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 4.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        if (tagsDirty) "Save Interests" else "Saved",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
