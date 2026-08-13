package compose.project.click.click.ui.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
actual fun rememberPlatformCameraPermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    var pendingOnComplete by remember { mutableStateOf<(() -> Unit)?>(null) }
    val activity = LocalActivity.current as? ComponentActivity
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val complete = pendingOnComplete
            pendingOnComplete = null
            if (!granted &&
                activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA,
                )
            ) {
                openApplicationSystemSettings()
            }
            complete?.invoke()
        }
    return { onComplete ->
        val granted =
            activity != null &&
                ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            onComplete()
        } else {
            pendingOnComplete = onComplete
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
}
