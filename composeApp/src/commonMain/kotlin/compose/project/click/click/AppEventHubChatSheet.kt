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
import compose.project.click.click.ui.components.LocalUseNativeHubSheetHeaderControls // pragma: allowlist secret
import compose.project.click.click.ui.components.PlatformPresentedSheetStackScope // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatNavArgs // pragma: allowlist secret
import compose.project.click.click.ui.screens.HubChatScreen // pragma: allowlist secret

/**
 * Sheet-owned Event Hub presentation used by event/map/feed entry points.
 *
 * The conversation itself remains the same [HubChatScreen]/HubChatViewModel stack as Connections,
 * but this shell deliberately stays inside the native page-sheet hierarchy. It never asks the root
 * app UINavigationBar, UITabBar, or interactive-back host to represent a sheet-origin route.
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

    // This state is owned by AppMainShell, but the presentation originates inside the native
    // Nearby/Event sheet stack. Re-parent the sheet host to UIKit's currently presented controller
    // so Event Hub stacks above that dialog instead of replacing/dismissing it from the root host.
    PlatformPresentedSheetStackScope {
        ClickFormBottomSheet(
            onDismissRequest = onDismissRequest,
            modifier = Modifier.fillMaxSize(),
            expandable = true,
            useUiKitScrollHost = false,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            when {
                args != null -> {
                    val owner =
                        remember(args.realtimeChannel, currentUserId) {
                            object : ViewModelStoreOwner {
                                override val viewModelStore = ViewModelStore()
                            }
                        }
                    DisposableEffect(owner) {
                        onDispose { owner.viewModelStore.clear() }
                    }
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owner,
                        LocalUseNativeHubSheetHeaderControls provides true,
                    ) {
                        HubChatScreen(
                            args = args,
                            currentUserId = currentUserId,
                            onNavigateBack = onDismissRequest,
                            embeddedInSheet = true,
                        )
                    }
                }

                loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = pendingTitle?.takeIf { it.isNotBlank() } ?: "Event chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Opening conversation…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
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
        }
    }
}
