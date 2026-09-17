@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click // pragma: allowlist secret

import androidx.compose.foundation.background
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
import compose.project.click.click.ui.components.BindPlatformNativeNavigationBar // pragma: allowlist secret
import compose.project.click.click.ui.components.ClickFormBottomSheet // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformOverlayAbovePresentedSheets // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatNavArgs // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatScreen // pragma: allowlist secret
import compose.project.click.click.ui.theme.LocalPlatformStyle // pragma: allowlist secret

/**
 * Event Hub presentation used by event/map/feed entry points.
 *
 * iOS deliberately uses the same real full-screen UIKit portal contract as profile media: the
 * existing Event/Nearby sheet stack stays mounted underneath while the conversation owns a new
 * full-screen ComposeUIViewController and the shared native Liquid Glass chrome. This avoids
 * trying to host Liquid Glass controls inside a UISheetPresentationController/Compose interop
 * boundary, which produced dark rectangular backings and inconsistent interaction.
 *
 * Android retains the existing bottom-sheet presentation. The conversation implementation and
 * [ViewModelStore] are shared on both platforms.
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

    // Keep the HubChat ViewModel owned by AppMainShell rather than by a detached presentation
    // host. Presentation changes must not recreate Supabase realtime collectors.
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

    val isIOS = LocalPlatformStyle.current.isIOS
    if (isIOS) {
        PlatformOverlayAbovePresentedSheets(
            liftAbovePresentedSheets = true,
        ) {
            when {
                args != null && owner != null -> {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        HubChatScreen(
                            args = args,
                            currentUserId = currentUserId,
                            onNavigateBack = onDismissRequest,
                            embeddedInSheet = false,
                            nativeLeadingClose = true,
                        )
                    }
                }

                else -> {
                    EventHubPortalStatus(
                        pendingTitle = pendingTitle,
                        loading = loading,
                        errorMessage = errorMessage,
                        onDismissRequest = onDismissRequest,
                    )
                }
            }
        }
        return
    }

    ClickFormBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxSize(),
        expandable = true,
        useUiKitScrollHost = false,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        when {
            args != null && owner != null -> {
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    HubChatScreen(
                        args = args,
                        currentUserId = currentUserId,
                        onNavigateBack = onDismissRequest,
                        embeddedInSheet = true,
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
private fun EventHubPortalStatus(
    pendingTitle: String?,
    loading: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
) {
    BindPlatformNativeNavigationBar(
        title = pendingTitle?.takeIf { it.isNotBlank() } ?: "Event chat",
        subtitle = if (loading) "Opening conversation…" else null,
        onNavigateBack = onDismissRequest,
        collapseFraction = 1f,
        leadingClose = true,
    )
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        EventHubStatusBody(
            pendingTitle = pendingTitle,
            loading = loading,
            errorMessage = errorMessage,
        )
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
