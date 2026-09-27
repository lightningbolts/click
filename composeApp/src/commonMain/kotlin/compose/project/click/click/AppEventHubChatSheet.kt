@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.screens.EventHubBottomSheetChatScreen // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatNavArgs // pragma: allowlist secret

/**
 * Event Hub presentation used by event/map/feed entry points.
 *
 * This is deliberately not the Connections/search Hub route: it uses a sheet-local presentation
 * that shares HubChat data/view-model internals without sharing navigation semantics.
 */
@Composable
internal fun AppEventHubChatSheet(
    args: HubChatNavArgs?,
    pendingTitle: String?,
    loading: Boolean,
    errorMessage: String?,
    currentUserId: String,
    onDismissRequest: () -> Unit,
) {
    if (args == null && !loading && errorMessage == null) return

    // Keep the HubChat ViewModel owned above the sheet host so presentation/layout changes
    // do not recreate Supabase realtime collectors.
    val owner =
        remember(args?.realtimeChannel, currentUserId) {
            if (args == null) {
                null
            } else {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
        }
    DisposableEffect(owner) {
        onDispose { owner?.viewModelStore?.clear() }
    }

    ClickFormBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        expandable = true,
        fillBody = true,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        when {
            args != null && owner != null -> {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    EventHubBottomSheetChatScreen(
                        args = args,
                        currentUserId = currentUserId,
                        onClose = onDismissRequest,
                    )
                }
            }

            else -> {
                EventHubStatusBody(
                    pendingTitle = pendingTitle,
                    loading = loading,
                    errorMessage = errorMessage,
                )
            }
        }
    }
}

@Composable
private fun EventHubStatusBody(
    pendingTitle: String?,
    loading: Boolean,
    errorMessage: String?,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) {
                CircularProgressIndicator()
                Text(
                    text = pendingTitle?.takeIf { it.isNotBlank() } ?: "Event chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Opening conversation…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = "Couldn't open event chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
