@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.utils

import android.Manifest
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.app.ActivityCompat
import compose.project.click.click.ui.components.AnimatedClickDialog

/**
 * Asks for location. A "Don't allow" answer is respected: nothing opens Settings. Only a request
 * that Android denies without a prompt (location off for good) shows a notice with an Open
 * Settings button.
 * Mirrors click-ios 6a8310d (App Review 5.1.1(iv)).
 */
@Composable
actual fun rememberPlatformLocationPermissionRequester(): ((onComplete: () -> Unit) -> Unit) {
    var pendingOnComplete by remember { mutableStateOf<(() -> Unit)?>(null) }
    var launchedAtMs by remember { mutableStateOf(0L) }
    var rationaleBefore by remember { mutableStateOf(false) }
    var showsLocationOffNotice by remember { mutableStateOf(false) }
    val activity = LocalActivity.current as? ComponentActivity

    fun rationale(): Boolean =
        activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { granted ->
            val complete = pendingOnComplete
            pendingOnComplete = null
            val elapsedMs = SystemClock.elapsedRealtime() - launchedAtMs
            if (!granted && shouldExplainDeniedLocation(rationaleBefore, rationale(), elapsedMs)) {
                showsLocationOffNotice = true
            }
            complete?.invoke()
        }

    AnimatedClickDialog(
        visible = showsLocationOffNotice,
        onDismissRequest = { showsLocationOffNotice = false },
        title = LOCATION_OFF_NOTICE_TITLE,
        confirmLabel = "Open Settings",
        onConfirm = {
            showsLocationOffNotice = false
            openApplicationSystemSettings()
        },
        dismissLabel = "Not now",
        modifier = Modifier.testTag("location-off-notice"),
    ) {
        Text(
            text = LOCATION_OFF_NOTICE_BODY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    return { onComplete ->
        rationaleBefore = rationale()
        launchedAtMs = SystemClock.elapsedRealtime()
        pendingOnComplete = onComplete
        launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
