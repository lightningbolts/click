package compose.project.click.click.ui.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private const val PERMISSION_ASKS_PREFS = "click_permission_asks"

/** Runtime permissions behind each capability; empty when this Android version needs none. */
private fun runtimePermissions(permission: AppPermission): List<String> =
    when (permission) {
        AppPermission.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        AppPermission.Bluetooth ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
            } else {
                emptyList()
            }
        AppPermission.Microphone -> listOf(Manifest.permission.RECORD_AUDIO)
        AppPermission.Camera -> listOf(Manifest.permission.CAMERA)
        // Click uses the system photo picker, which needs no media permission.
        AppPermission.Photos -> emptyList()
        AppPermission.Contacts -> listOf(Manifest.permission.READ_CONTACTS)
        AppPermission.Calendar -> listOf(Manifest.permission.READ_CALENDAR)
        AppPermission.Notifications ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
    }

private fun isGranted(
    context: Context,
    permission: AppPermission,
    runtime: List<String>,
): Boolean {
    fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when (permission) {
        // Approximate location is enough for every Click feature.
        AppPermission.Location -> runtime.any(::has)
        AppPermission.Notifications ->
            runtime.all(::has) && NotificationManagerCompat.from(context).areNotificationsEnabled()
        else -> runtime.all(::has)
    }
}

internal fun appPermissionState(
    context: Context,
    activity: Activity?,
    permission: AppPermission,
): AppPermissionState {
    val runtime = runtimePermissions(permission)
    if (runtime.isEmpty()) {
        // Pre-13 notifications have no runtime prompt, but the user can still switch them off.
        if (permission == AppPermission.Notifications && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return AppPermissionState.Blocked
        }
        return if (permission == AppPermission.Photos) AppPermissionState.NotNeeded else AppPermissionState.Granted
    }
    if (isGranted(context, permission, runtime)) return AppPermissionState.Granted
    // A runtime grant with notifications switched off in Settings can only be fixed there.
    if (permission == AppPermission.Notifications &&
        runtime.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    ) {
        return AppPermissionState.Blocked
    }
    val asked = context.getSharedPreferences(PERMISSION_ASKS_PREFS, Context.MODE_PRIVATE).getBoolean(permission.name, false)
    val rationale = activity != null && runtime.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    // Never asked, or denied once (Android still shows the prompt): Allow. Otherwise only Settings can help.
    return if (!asked || rationale) AppPermissionState.CanRequest else AppPermissionState.Blocked
}

@Composable
actual fun rememberAppPermissionController(): AppPermissionController {
    val context = LocalContext.current.applicationContext
    val activity = LocalActivity.current
    var pendingResult by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val done = pendingResult
            pendingResult = null
            done?.invoke()
        }
    return remember(context, activity, launcher) {
        object : AppPermissionController {
            override fun state(permission: AppPermission): AppPermissionState = appPermissionState(context, activity, permission)

            override fun request(
                permission: AppPermission,
                onResult: () -> Unit,
            ) {
                val runtime = runtimePermissions(permission)
                if (runtime.isEmpty() || state(permission) != AppPermissionState.CanRequest) {
                    onResult()
                    return
                }
                context
                    .getSharedPreferences(PERMISSION_ASKS_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(permission.name, true)
                    .apply()
                pendingResult = onResult
                runCatching { launcher.launch(runtime.toTypedArray()) }.onFailure {
                    pendingResult = null
                    onResult()
                }
            }

            override fun openSettings(permission: AppPermission) {
                val intent =
                    if (permission == AppPermission.Notifications) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }.onFailure { openApplicationSystemSettings() }
            }
        }
    }
}
