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
actual fun rememberProximityHardwarePermissionRequester(): ((onResult: (Boolean) -> Unit) -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity
    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }
    }
    var pendingOnResult by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    fun hasAllPermissions(): Boolean {
        val context = activity ?: return false
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val complete = pendingOnResult
        pendingOnResult = null
        val granted = requiredPermissions.all { permission ->
            results[permission] == true ||
                (activity != null &&
                    ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED)
        }
        if (!granted && activity != null) {
            val permanentlyDenied = requiredPermissions.any { permission ->
                ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            }
            if (permanentlyDenied) {
                openApplicationSystemSettings()
            }
        }
        complete?.invoke(granted)
    }

    return { onResult ->
        if (hasAllPermissions()) {
            onResult(true)
        } else {
            pendingOnResult = onResult
            launcher.launch(requiredPermissions.toTypedArray())
        }
    }
}
