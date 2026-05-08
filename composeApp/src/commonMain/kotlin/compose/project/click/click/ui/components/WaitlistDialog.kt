package compose.project.click.click.ui.components // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.project.click.click.data.api.WaitlistApiClient // pragma: allowlist secret
import com.mohamedrejeb.calf.ui.progress.AdaptiveCircularProgressIndicator
import kotlinx.coroutines.launch

@Composable
fun WaitlistDialog(
    source: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val client = remember { WaitlistApiClient() }
    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(shape)
                    .border(1.dp, GlassSheetTokens.GlassBorder, shape)
                    .background(GlassSheetTokens.OledBlack)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "Join the Waitlist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassSheetTokens.OnOled,
                )
                if (successMessage != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                        )
                        Text(
                            text = successMessage ?: "You're on the list! We'll be in touch.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = GlassSheetTokens.OnOled,
                        )
                    }
                } else {
                    Text(
                        text = "Add your email and Click will reach out when the launch is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassSheetTokens.OnOledMuted,
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email", color = GlassSheetTokens.OnOledMuted) },
                        singleLine = true,
                        isError = errorMessage != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GlassSheetTokens.OnOled,
                            unfocusedTextColor = GlassSheetTokens.OnOled,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = GlassSheetTokens.GlassBorder,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = GlassSheetTokens.OnOledMuted,
                            unfocusedLabelColor = GlassSheetTokens.OnOledMuted,
                        ),
                    )
                    errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (successMessage != null) {
                        Button(onClick = onDismiss) {
                            Text("Close")
                        }
                    } else {
                        TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                            Text("Cancel", color = GlassSheetTokens.OnOledMuted)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                if (!email.contains('@')) {
                                    errorMessage = "Enter a valid email address"
                                    return@Button
                                }
                                scope.launch {
                                    isSubmitting = true
                                    client.joinWaitlist(email.trim(), source)
                                        .onSuccess {
                                            successMessage = it
                                            errorMessage = null
                                        }
                                        .onFailure {
                                            errorMessage = it.message ?: "Failed to join waitlist"
                                        }
                                    isSubmitting = false
                                }
                            },
                            enabled = !isSubmitting,
                            modifier = Modifier.height(40.dp),
                        ) {
                            if (isSubmitting) {
                                AdaptiveCircularProgressIndicator(
                                    modifier = Modifier.height(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("Submit")
                            }
                        }
                    }
                }
            }
        }
    }
}
