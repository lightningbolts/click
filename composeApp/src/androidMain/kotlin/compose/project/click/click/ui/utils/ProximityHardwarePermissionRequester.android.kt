package compose.project.click.click.ui.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
actual fun rememberPlatformProximityHardwarePermissionStatus(): () -> ProximityHardwarePermissionStatus {
    val activity = LocalActivity.current as? ComponentActivity
    return remember(activity) {
        {
            val context = activity
            when {
                context == null -> ProximityHardwarePermissionStatus.Blocked
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED -> ProximityHardwarePermissionStatus.Ready
                ActivityCompat.shouldShowRequestPermissionRationale(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) -> ProximityHardwarePermissionStatus.NeedsRequest
                else -> ProximityHardwarePermissionStatus.NeedsRequest
            }
        }
    }
}

@Composable
actual fun rememberPlatformProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity
    val requiredPermissions =
        remember {
            buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
    val requiredAudioPermission = Manifest.permission.RECORD_AUDIO
    var pendingOnResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    fun hasAllPermissions(): Boolean {
        val context = activity ?: return false
        // Check every proximity permission (including Android 12+ Bluetooth grants),
        // not just the microphone — otherwise the BLE permission dialog is never
        // shown once audio has been granted and BLE silently degrades.
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) { results ->
            val complete = pendingOnResult
            pendingOnResult = null
            val granted =
                results[requiredAudioPermission] == true ||
                    (
                        activity != null &&
                            ContextCompat.checkSelfPermission(activity, requiredAudioPermission) == PackageManager.PERMISSION_GRANTED
                    )
            complete?.invoke(granted)
        }

    return { onResult ->
        val context = activity
        val microphoneGranted =
            context != null &&
                ContextCompat.checkSelfPermission(context, requiredAudioPermission) == PackageManager.PERMISSION_GRANTED
        if (microphoneGranted) {
            // Bluetooth is an enrichment path. If the user denied it previously, do not
            // re-present the permission dialog every time Connect is tapped.
            onResult(true)
        } else if (hasAllPermissions()) {
            onResult(true)
        } else {
            pendingOnResult = onResult
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }
}
