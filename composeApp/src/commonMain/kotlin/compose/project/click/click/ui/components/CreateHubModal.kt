package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import [REDACTED].data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.HubCreatePostBody // pragma: allowlist secret
import compose.project.click.click.ui.chat.ChatAmbientMeshBackground // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatNavArgs // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.utils.LocationService // pragma: allowlist secret
import kotlinx.coroutines.launch

@Composable
fun CreateHubModal(
    onDismiss: () -> Unit,
    getJwt: suspend () -> String?,
    locationService: LocationService,
    onCreated: (HubChatNavArgs) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val api = remember { ApiClient() }
    var hubName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pulse by animateFloatAsState(
        targetValue = if (busy) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "create_hub_pulse",
    )

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(pulse),
                usePrimaryBorder = true,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp),
                ) {
                    ChatAmbientMeshBackground(
                        connection = null,
                        isHubNeutral = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "Create a hub",
                            style = MaterialTheme.typography.titleLarge,
                            color = GlassSheetTokens.OnOled,
                        )
                        Text(
                            text = "Name it, pick a vibe category, and we will drop you straight into the live room.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassSheetTokens.OnOledMuted,
                        )
                        OutlinedTextField(
                            value = hubName,
                            onValueChange = { hubName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Hub name") },
                        )
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Category") },
                        )
                        error?.let {
                            Text(text = it, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val primaryTap = remember { MutableInteractionSource() }
                        val cancelTap = remember { MutableInteractionSource() }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = cancelTap,
                                        indication = null,
                                        enabled = !busy,
                                    ) { onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable(
                                        interactionSource = primaryTap,
                                        indication = null,
                                        enabled = !busy && hubName.isNotBlank(),
                                    ) {
                                        scope.launch {
                                            busy = true
                                            error = null
                                            val jwt = getJwt()
                                            if (jwt.isNullOrBlank()) {
                                                error = "Please sign in again."
                                                busy = false
                                                return@launch
                                            }
                                            val loc = if (locationService.hasLocationPermission()) {
                                                runCatching { locationService.getHighAccuracyLocation(8000L) }.getOrNull()
                                            } else {
                                                null
                                            }
                                            val body = HubCreatePostBody(
                                                name = hubName.trim(),
                                                category = category.trim().ifBlank { "general" },
                                                latitude = loc?.latitude,
                                                longitude = loc?.longitude,
                                            )
                                            val r = api.postHubCreate(jwt, body)
                                            if (r.isSuccess) {
                                                val dto = r.getOrNull()!!
                                                onCreated(
                                                    HubChatNavArgs(
                                                        hubId = dto.hubId,
                                                        realtimeChannel = dto.channel,
                                                        hubTitle = dto.name,
                                                    ),
                                                )
                                                onDismiss()
                                            } else {
                                                error = r.exceptionOrNull()?.message ?: "Could not create hub"
                                            }
                                            busy = false
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.height(28.dp),
                                        color = PrimaryBlue,
                                        strokeWidth = 3.dp,
                                    )
                                } else {
                                    Text("Create", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
