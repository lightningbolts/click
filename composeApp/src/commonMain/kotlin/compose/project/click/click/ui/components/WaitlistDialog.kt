package compose.project.click.click.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.WaitlistApiClient
import kotlinx.coroutines.launch

@Composable
fun WaitlistDialog(
    source: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val client = remember { WaitlistApiClient() }
    var email by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join the Waitlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (successMessage != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50)
                        )
                        Text(
                            text = successMessage ?: "You're on the list! We'll be in touch.",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        text = "Add your email and Click will reach out when the launch is ready.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            errorMessage = null
                        },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        isError = errorMessage != null
                    )
                    errorMessage?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (successMessage != null) {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            } else {
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
                    modifier = androidx.compose.ui.Modifier.height(40.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = androidx.compose.ui.Modifier.height(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Submit")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(if (successMessage != null) "Done" else "Cancel")
            }
        }
    )
}