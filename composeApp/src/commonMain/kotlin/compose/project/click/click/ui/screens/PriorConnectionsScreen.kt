@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.screens // pragma: allowlist secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.project.click.click.data.api.ApiClient // pragma: allowlist secret
import compose.project.click.click.data.api.DiscoverProfileCard // pragma: allowlist secret
import compose.project.click.click.data.contacts.ContactBook // pragma: allowlist secret
import compose.project.click.click.data.contacts.KnownSinceBucket // pragma: allowlist secret
import compose.project.click.click.ui.components.AdaptiveCard // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButton // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickButtonVariant // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickDropdownMenu // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickMenuItem // pragma: allowlist secret
import compose.project.click.click.ui.components.ConnectionListUserAvatarFace // pragma: allowlist secret
import compose.project.click.click.ui.theme.PrimaryBlue // pragma: allowlist secret
import compose.project.click.click.ui.utils.rememberContactsPermissionRequester // pragma: allowlist secret
import kotlinx.coroutines.launch

/**
 * Skippable onboarding step: hash on-device contacts and send prior-connection requests.
 */
@Composable
fun PriorConnectionsScreen(
    onSkip: () -> Unit,
    apiClient: ApiClient = remember { ApiClient() },
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scope = rememberCoroutineScope()
    val requestContacts = rememberContactsPermissionRequester()

    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var matches by remember { mutableStateOf<List<DiscoverProfileCard>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val buckets = remember { mutableStateMapOf<String, KnownSinceBucket>() }
    val sending = remember { mutableStateMapOf<String, Boolean>() }
    val sent = remember { mutableStateMapOf<String, Boolean>() }
    val menuFor = remember { mutableStateOf<String?>(null) }

    fun discover() {
        requestContacts {
            scope.launch {
                loading = true
                errorMessage = null
                try {
                    val hashes = ContactBook.hashedContacts()
                    searched = true
                    if (hashes.isEmpty()) {
                        matches = emptyList()
                        return@launch
                    }
                    val result = apiClient.discoverContacts(hashes)
                    result.fold(
                        onSuccess = { matches = it },
                        onFailure = { err ->
                            errorMessage = err.message?.take(180) ?: "Could not find friends right now."
                        },
                    )
                } finally {
                    loading = false
                }
            }
        }
    }

    fun sendRequest(card: DiscoverProfileCard) {
        if (sent[card.id] == true || sending[card.id] == true) return
        scope.launch {
            sending[card.id] = true
            errorMessage = null
            val bucket = buckets[card.id] ?: KnownSinceBucket.Unspecified
            val result =
                apiClient.requestPriorConnection(
                    targetUserId = card.id,
                    knownSince = bucket.apiValue,
                    contextTag = null,
                )
            sending[card.id] = false
            result.fold(
                onSuccess = { sent[card.id] = true },
                onFailure = { err ->
                    errorMessage = err.message?.take(180) ?: "Could not send request."
                },
            )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .testTag("onboarding-prior-connections"),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = topInset + 28.dp,
                        bottom = 32.dp,
                        start = 24.dp,
                        end = 24.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Connect with people you already know",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Find friends from your contacts. Numbers and emails are hashed on this device and never uploaded.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            if (!searched) {
                ClickButton(
                    onClick = ::discover,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !loading,
                    variant = ClickButtonVariant.Primary,
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Find Friends from Contacts")
                    }
                }
                Spacer(Modifier.height(12.dp))
                ClickButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    enabled = !loading,
                    variant = ClickButtonVariant.Secondary,
                ) {
                    Text("Skip for now")
                }
            } else {
                if (loading) {
                    CircularProgressIndicator(color = PrimaryBlue)
                } else if (matches.isEmpty()) {
                    Text(
                        text = "No friends on Click yet. You can skip and come back later.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        items(matches, key = { it.id }) { card ->
                            AdaptiveCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ConnectionListUserAvatarFace(
                                        displayName = card.name,
                                        email = null,
                                        avatarUrl = card.avatarUrl,
                                        userId = card.id,
                                        modifier = Modifier.size(44.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = card.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        val bucket = buckets[card.id] ?: KnownSinceBucket.Unspecified
                                        Box {
                                            ClickButton(
                                                onClick = { menuFor.value = card.id },
                                                variant = ClickButtonVariant.Secondary,
                                                enabled = sent[card.id] != true,
                                            ) {
                                                Text("Known since: ${bucket.label}")
                                            }
                                            ClickDropdownMenu(
                                                expanded = menuFor.value == card.id,
                                                onDismissRequest = { menuFor.value = null },
                                                items =
                                                    KnownSinceBucket.entries.map { option ->
                                                        ClickMenuItem(
                                                            label = option.label,
                                                            onClick = { buckets[card.id] = option },
                                                        )
                                                    },
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    ClickButton(
                                        onClick = { sendRequest(card) },
                                        enabled = sent[card.id] != true && sending[card.id] != true,
                                        variant = ClickButtonVariant.Primary,
                                    ) {
                                        Text(if (sent[card.id] == true) "Sent" else "Request")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ClickButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    variant = ClickButtonVariant.Secondary,
                ) {
                    Text("Skip for now")
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
