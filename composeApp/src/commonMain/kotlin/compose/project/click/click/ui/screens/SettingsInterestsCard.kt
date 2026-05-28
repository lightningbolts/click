package compose.project.click.click.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.AppDataManager
import compose.project.click.click.data.repository.SupabaseRepository
import compose.project.click.click.ui.components.AdaptiveCard
import compose.project.click.click.ui.components.InterestEditor
import compose.project.click.click.ui.components.filterToPredefinedInterestTags
import compose.project.click.click.ui.theme.LocalPlatformStyle
import compose.project.click.click.ui.theme.PrimaryBlue
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
    var interestTags by remember(userId) { mutableStateOf<List<String>?>(null) }
    var savedTags by remember(userId) { mutableStateOf<List<String>>(emptyList()) }
    var tagsDirty by remember { mutableStateOf(false) }
    var tagsLoading by remember { mutableStateOf(false) }
    var tagsSaving by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        tagsLoading = true
        loadError = null
        tagsDirty = false
        supabaseRepository.fetchUserInterests(userId).fold(
            onSuccess = { row ->
                val loaded = filterToPredefinedInterestTags(row?.tags.orEmpty())
                interestTags = loaded
                savedTags = loaded
            },
            onFailure = {
                interestTags = emptyList()
                savedTags = emptyList()
                loadError = it.message?.lines()?.firstOrNull()?.take(180)
                    ?: "Could not load interests"
            },
        )
        tagsLoading = false
    }

    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
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

            when {
                tagsLoading && interestTags == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(vertical = 16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                interestTags != null -> {
                    val tags = interestTags!!
                    if (tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = PrimaryBlue.copy(alpha = 0.12f),
                                ) {
                                    Text(
                                        text = tag,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PrimaryBlue,
                                    )
                                }
                            }
                        }
                    }

                    InterestEditor(
                        selectedTags = tags,
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

                    val platformStyle = LocalPlatformStyle.current
                    Button(
                        onClick = {
                            scope.launch {
                                tagsSaving = true
                                val toSave = interestTags.orEmpty()
                                supabaseRepository.updateUserInterests(userId, toSave).fold(
                                    onSuccess = {
                                        savedTags = toSave
                                        tagsDirty = false
                                        loadError = null
                                        AppDataManager.applyInterestTags(toSave)
                                        onFeedback("Saved ${toSave.size} interests")
                                    },
                                    onFailure = { e ->
                                        val msg = e.message?.lines()?.firstOrNull()?.take(180)
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
                        shape = RoundedCornerShape(if (platformStyle.isIOS) 12.dp else 28.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
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
    }
}
