package compose.project.click.click.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.data.api.ApiClient
import compose.project.click.click.data.api.HubCreateLocationBody
import compose.project.click.click.data.api.HubCreatePostBody
import compose.project.click.click.ui.theme.PrimaryBlue
import compose.project.click.click.utils.LocationService
import io.ktor.client.plugins.ClientRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val hubCategories = listOf(
    "general", "music", "study", "sports", "food", "nightlife",
    "gaming", "tech", "art", "fitness", "networking", "party",
)

private const val CUSTOM_CATEGORY_MAX_CHARS = 24

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateHubModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    onHubCreated: (hubId: String) -> Unit,
    locationService: LocationService,
    onError: (String) -> Unit = {},
    initialName: String = "",
    initialCategory: String = "",
) {
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }
    var nameDraft by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(hubCategories.first()) }
    var isCustomCategory by remember { mutableStateOf(false) }
    var customCategoryDraft by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var locationPrimed by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            nameDraft = initialName.ifBlank { "" }
            category = if (initialCategory.isNotBlank() && initialCategory in hubCategories) {
                initialCategory
            } else {
                hubCategories.first()
            }
            isCustomCategory = false
            customCategoryDraft = ""
            submitting = false
            locationPrimed = false
            withContext(Dispatchers.Default) {
                runCatching { locationService.getHighAccuracyLocation(8000L) }
            }
            locationPrimed = true
        }
    }

    val springOk by animateFloatAsState(
        targetValue = if (submitting) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "create_hub_cta",
    )

    if (!visible) return

    val resolvedCategory = if (isCustomCategory) customCategoryDraft.trim().lowercase() else category
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = GlassSheetTokens.OnOled,
        unfocusedTextColor = GlassSheetTokens.OnOled,
        focusedBorderColor = PrimaryBlue,
        unfocusedBorderColor = GlassSheetTokens.GlassBorder,
        cursorColor = PrimaryBlue,
        focusedLabelColor = GlassSheetTokens.OnOledMuted,
        unfocusedLabelColor = GlassSheetTokens.OnOledMuted,
        focusedPlaceholderColor = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f),
    )

    Dialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = !submitting,
            dismissOnBackPress = !submitting,
        ),
    ) {
        val shape = RoundedCornerShape(GlassSheetTokens.BentoExteriorCorner)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(shape)
                .border(1.dp, GlassSheetTokens.GlassBorder, shape)
                .background(GlassSheetTokens.OledBlack)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Groups,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = "Create community hub",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = GlassSheetTokens.OnOled,
            )
            Text(
                text = "Ephemeral 24h space — GPS anchors the venue ring.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassSheetTokens.OnOledMuted,
            )
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Hub name", color = GlassSheetTokens.OnOledMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = textFieldColors,
            )
            Text(
                text = "Category",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = GlassSheetTokens.OnOled,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hubCategories.forEach { c ->
                    val label = c.replaceFirstChar { ch -> ch.uppercase() }
                    FilterChip(
                        selected = !isCustomCategory && category == c,
                        onClick = {
                            category = c
                            isCustomCategory = false
                        },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            labelColor = GlassSheetTokens.OnOledMuted,
                            selectedLabelColor = GlassSheetTokens.OnOled,
                            selectedContainerColor = PrimaryBlue.copy(alpha = 0.25f),
                        ),
                    )
                }
                FilterChip(
                    selected = isCustomCategory,
                    onClick = { isCustomCategory = true },
                    label = { Text("Custom…") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = GlassSheetTokens.OnOledMuted,
                        selectedLabelColor = GlassSheetTokens.OnOled,
                        selectedContainerColor = PrimaryBlue.copy(alpha = 0.25f),
                    ),
                )
            }
            if (isCustomCategory) {
                OutlinedTextField(
                    value = customCategoryDraft,
                    onValueChange = { customCategoryDraft = it.take(CUSTOM_CATEGORY_MAX_CHARS) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom category", color = GlassSheetTokens.OnOledMuted) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            "${customCategoryDraft.length}/$CUSTOM_CATEGORY_MAX_CHARS",
                            color = GlassSheetTokens.OnOledMuted.copy(alpha = 0.5f),
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = textFieldColors,
                )
            }
            if (!locationPrimed) {
                Text(
                    text = "Locking GPS…",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassSheetTokens.OnOledMuted,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { if (!submitting) onDismiss() }) {
                    Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                }
                TextButton(
                    onClick = {
                        val label = nameDraft.trim()
                        if (label.isEmpty() || submitting || resolvedCategory.isEmpty()) return@TextButton
                        scope.launch {
                            submitting = true
                            try {
                                val loc = withContext(Dispatchers.Default) {
                                    locationService.getHighAccuracyLocation(12_000L)
                                }
                                if (loc == null) {
                                    onError("Could not read GPS for this hub.")
                                    submitting = false
                                    return@launch
                                }
                                val body = HubCreatePostBody(
                                    name = label,
                                    category = resolvedCategory,
                                    location = HubCreateLocationBody(
                                        latitude = loc.latitude,
                                        longitude = loc.longitude,
                                        radiusMeters = 50,
                                    ),
                                )
                                val result = withContext(Dispatchers.Default) {
                                    api.postHubCreate(body)
                                }
                                result.fold(
                                    onSuccess = { dto ->
                                        val hid = dto.hubId.trim()
                                        if (hid.isNotEmpty()) {
                                            onDismiss()
                                            onHubCreated(hid)
                                        } else {
                                            onError("Hub created but id missing.")
                                        }
                                    },
                                    onFailure = { e ->
                                        val msg = when (e) {
                                            is ClientRequestException ->
                                                "Could not create hub (${e.response.status.value})"
                                            else -> e.message ?: "Could not create hub"
                                        }
                                        onError(msg)
                                    },
                                )
                            } catch (e: Exception) {
                                onError(e.message ?: "Could not create hub")
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = nameDraft.isNotBlank() && resolvedCategory.isNotEmpty() && locationPrimed && !submitting,
                    modifier = Modifier.scale(springOk),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = PrimaryBlue,
                        )
                    } else {
                        Text("Create hub", color = GlassSheetTokens.OnOled)
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
