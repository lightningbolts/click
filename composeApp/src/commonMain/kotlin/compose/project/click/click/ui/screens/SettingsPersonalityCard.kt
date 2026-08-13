package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.PERSONALITY_REQUIRED_TAG_COUNT // pragma: allowlist secret
import compose.project.click.click.ui.components.PersonalityEditor // pragma: allowlist secret
import compose.project.click.click.ui.components.canonicalizePersonalityTags // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.SoftBlue // pragma: allowlist secret
import compose.project.click.click.ui.theme.clickBorderColor // pragma: allowlist secret
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsPersonalityCard(
    userId: String?,
    onFeedback: suspend (String) -> Unit,
) {
    if (userId.isNullOrBlank()) return

    val scope = rememberCoroutineScope()
    val currentUser by AppDataManager.currentUser.collectAsState()
    val cached = remember(currentUser?.personalityTags) {
        canonicalizePersonalityTags(currentUser?.personalityTags.orEmpty())
    }
    var selected by remember(userId, cached) { mutableStateOf(cached) }
    var saved by remember(userId, cached) { mutableStateOf(cached) }
    var dirty by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val apiClient = remember { ApiClient() }

    LaunchedEffect(cached) {
        if (!dirty) {
            selected = cached
            saved = cached
        }
    }

    LaunchedEffect(userId) {
        val loaded = apiClient.getUserProfile(userId).getOrNull()
            ?.personalityTags
            .orEmpty()
            .let { canonicalizePersonalityTags(it) }
        if (loaded.isNotEmpty()) {
            AppDataManager.applyPersonalityTags(loaded)
            if (!dirty) {
                selected = loaded
                saved = loaded
            }
        }
    }

    AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "My personality",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Pick exactly $PERSONALITY_REQUIRED_TAG_COUNT traits. Existing accounts can skip this — it is not a login gate.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (selected.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    selected.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftBlue,
                            border = BorderStroke(1.dp, clickBorderColor()),
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

            PersonalityEditor(
                selectedTags = selected,
                onSelectedTagsChange = { next ->
                    selected = next
                    dirty = next != saved
                },
            )

            Text(
                text = "${selected.size} of $PERSONALITY_REQUIRED_TAG_COUNT selected",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val platformStyle = LocalPlatformStyle.current
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        val toSave = canonicalizePersonalityTags(selected)
                        apiClient.patchUserProfile(userId, personalityTags = toSave).fold(
                            onSuccess = { user ->
                                saved = toSave
                                dirty = false
                                AppDataManager.applyPersonalityTags(user.personalityTags.ifEmpty { toSave })
                                onFeedback("Saved $PERSONALITY_REQUIRED_TAG_COUNT personality traits")
                            },
                            onFailure = {
                                val msg = it.message?.lines()?.firstOrNull()?.take(180)
                                    ?: "Could not save personality traits"
                                onFeedback(msg)
                            },
                        )
                        saving = false
                    }
                },
                enabled = !saving && dirty && selected.size == PERSONALITY_REQUIRED_TAG_COUNT,
                shape = RoundedCornerShape(if (platformStyle.isIOS) 12.dp else 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            ) {
                Text(if (saving) "Saving…" else "Save personality")
            }
        }
    }
}
