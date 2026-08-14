package compose.project.click.click.ui.utils // pragma: allowlist secret

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
expect fun rememberPlatformContactsPermissionRequester(): ((onComplete: () -> Unit) -> Unit)

@Composable
fun rememberContactsPermissionRequester(): ((onComplete: () -> Unit) -> Unit) =
    remember {
        { onComplete ->
            PermissionRequestQueue.enqueue(PermissionKind.Contacts, onComplete = onComplete)
        }
    }
