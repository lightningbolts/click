package compose.project.click.click.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.mohamedrejeb.calf.ui.sheet.AdaptiveBottomSheet
import com.mohamedrejeb.calf.ui.sheet.rememberAdaptiveSheetState
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import compose.project.click.click.data.ContextTagTaxonomy
import compose.project.click.click.data.models.ContextTag
import compose.project.click.click.data.models.UserProfile
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Matches DB limits for profile-style short labels (align with interests max length). */
private const val CUSTOM_CONTEXT_MAX_LENGTH = 25

@Composable
private fun ConnectionContextHeaderAvatars(connectedUsers: List<UserProfile>) {
    when (connectedUsers.size) {
        0 -> { }
        1 -> {
            val u = connectedUsers.first()
            StackedProfileAvatarRow(
                profiles = listOf(u),
                overflowCount = 0,
            )
        }
        else -> {
            val overflow = (connectedUsers.size - 3).coerceAtLeast(0)
            StackedProfileAvatarRow(
                profiles = connectedUsers.take(3),
                overflowCount = overflow,
            )
        }
    }
}

@Composable
private fun StackedProfileAvatarRow(
    profiles: List<UserProfile>,
    overflowCount: Int,
) {
    val avatarSize = 52.dp
    val overlap = 20.dp
    val badgeSize = (avatarSize.value * 0.92f).dp
    val stackWidth =
        avatarSize + overlap * (profiles.size - 1).coerceAtLeast(0) +
            if (overflowCount > 0) badgeSize + 6.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(avatarSize + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.width(stackWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            profiles.forEachIndexed { index, profile ->
                val borderColor = MaterialTheme.colorScheme.surfaceContainerHigh
                Surface(
                    modifier = Modifier
                        .offset(x = overlap * index)
                        .size(avatarSize)
                        .zIndex(index.toFloat())
                        .align(Alignment.CenterStart),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(2.dp, borderColor),
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(avatarSize)
                                .clip(CircleShape),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size((avatarSize.value * 0.55f).dp),
                            )
                        }
                    }
                }
            }
            if (overflowCount > 0) {
                Surface(
                    modifier = Modifier
                        .offset(x = overlap * profiles.size + 4.dp)
                        .size(badgeSize)
                        .zIndex(profiles.size + 1f)
                        .align(Alignment.CenterStart),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = "+$overflowCount",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConnectionContextSheet(
    connectedUsers: List<UserProfile>,
    locationName: String?,
    initialNoiseOptIn: Boolean,
    noisePermissionGranted: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (ContextTag?, Boolean) -> Unit,
    onSkip: (() -> Unit)? = null,
) {
    val hourOfDay = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour
    }
    val suggestions = remember(locationName, hourOfDay) {
        ContextTagTaxonomy.suggest(locationName = locationName, hourOfDay = hourOfDay)
    }
    val allTags = remember { ContextTagTaxonomy.all }
    var selectedTagId by remember { mutableStateOf<String?>(suggestions.firstOrNull()?.id) }
    var customTagText by remember { mutableStateOf("") }
    var ambientNoiseOptIn by remember(initialNoiseOptIn) { mutableStateOf(initialNoiseOptIn) }
    val sheetState = rememberAdaptiveSheetState(skipPartiallyExpanded = true)
    val isCustomSelectionInvalid = selectedTagId == "custom" && customTagText.isBlank()
    val dismissSheet = onSkip ?: onDismiss

    fun resolveSelectedTag(): ContextTag? {
        return if (selectedTagId == "custom") {
            customTagText.trim().takeIf { it.isNotEmpty() }?.let {
                ContextTagTaxonomy.formatCustomUserContextTag(it)
            }
        } else {
            allTags.firstOrNull { it.id == selectedTagId }
        }
    }

    val titleText: String
    val subtitleText: String
    when (connectedUsers.size) {
        0 -> {
            titleText = "Add context"
            subtitleText = "Pick what best describes this physical encounter. You can leave it blank and keep going."
        }
        1 -> {
            val name = connectedUsers.first().displayName
            titleText = "How was meeting $name?"
            subtitleText = "Pick what best describes this physical encounter. You can leave it blank and keep going."
        }
        else -> {
            titleText = "Set the context for this group"
            subtitleText = "This tag applies to everyone in this meetup. You can leave it blank and keep going."
        }
    }

    AdaptiveBottomSheet(
        onDismissRequest = dismissSheet,
        adaptiveSheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (connectedUsers.isNotEmpty()) {
                ConnectionContextHeaderAvatars(connectedUsers)
                Spacer(modifier = Modifier.height(4.dp))
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!locationName.isNullOrBlank()) {
                Text(
                    text = "Location hint: $locationName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Suggested",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { selectedTagId = tag.id },
                        label = { Text("${tag.emoji} ${tag.label}") }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "All tags",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTags.forEach { tag ->
                    FilterChip(
                        selected = selectedTagId == tag.id,
                        onClick = { selectedTagId = tag.id },
                        label = { Text("${tag.emoji} ${tag.label}") }
                    )
                }
            }

            HorizontalDivider()

            Text(
                text = "Custom activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "If none of the presets fit, write what you were doing. Short, natural labels work best.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilterChip(
                selected = selectedTagId == "custom",
                onClick = { selectedTagId = "custom" },
                label = { Text("✏️ Write your own") }
            )

            OutlinedTextField(
                value = customTagText,
                onValueChange = { input ->
                    customTagText = input
                        .replace(Regex("\\s+"), " ")
                        .trimStart()
                        .take(CUSTOM_CONTEXT_MAX_LENGTH)
                    if (customTagText.isNotBlank()) {
                        selectedTagId = "custom"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom activity") },
                placeholder = { Text("Dorm lounge, coffee line, hackathon kickoff...") },
                supportingText = {
                    Text(
                        text = if (isCustomSelectionInvalid) {
                            "Add a quick label before continuing."
                        } else {
                            "${customTagText.length}/$CUSTOM_CONTEXT_MAX_LENGTH characters"
                        }
                    )
                },
                isError = isCustomSelectionInvalid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                )
            )

            HorizontalDivider()

            Text(
                text = "Ambient noise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = when {
                    ambientNoiseOptIn && noisePermissionGranted ->
                        "Ambient sound enrichment is enabled from onboarding. Click stores only a 2-second noise category for this encounter."
                    ambientNoiseOptIn ->
                        "Ambient sound enrichment is enabled, but microphone permission is unavailable right now so Click will skip it for this encounter."
                    else ->
                        "Ambient sound enrichment is currently off. You can change it later in Settings."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    onClick = dismissSheet,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = { onConfirm(resolveSelectedTag(), ambientNoiseOptIn) },
                    modifier = Modifier.weight(1f),
                    enabled = !isCustomSelectionInvalid
                ) {
                    Text("Continue")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
