package compose.project.click.click.ui.utils

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
actual fun rememberMicrophonePermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    var pendingOnComplete by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingOnComplete?.invoke()
        pendingOnComplete = null
    }

    return { onComplete ->
        pendingOnComplete = onComplete
        launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
