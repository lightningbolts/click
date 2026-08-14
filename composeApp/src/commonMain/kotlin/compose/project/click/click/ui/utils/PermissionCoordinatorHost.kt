@file:Suppress("ktlint:standard:function-naming")

package compose.project.click.click.ui.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import compose.project.click.click.ui.components.AnimatedClickDialog

@Composable
fun PermissionCoordinatorHost() {
    val pending by PermissionRequestQueue.current.collectAsState()
    val requestLocation = rememberPlatformLocationPermissionRequester()
    val requestMicrophone = rememberPlatformMicrophonePermissionRequester()
    val requestCamera = rememberPlatformCameraPermissionRequester()
    val requestProximity = rememberPlatformProximityHardwarePermissionRequester()
    val requestCalendar = rememberPlatformCalendarPermissionRequester()
    val requestContacts = rememberPlatformContactsPermissionRequester()
    var launchingOs by remember { mutableStateOf(false) }

    LaunchedEffect(pending) {
        if (pending == null) launchingOs = false
    }

    val kind = pending?.kind
    val (title, body) = kind?.let { permissionPrimeCopy(it) } ?: ("" to "")

    AnimatedClickDialog(
        visible = pending != null && !launchingOs,
        onDismissRequest = {
            if (!launchingOs) PermissionRequestQueue.dismissCurrent()
        },
        title = title,
        confirmLabel = "Continue",
        onConfirm = {
            val current = PermissionRequestQueue.current.value ?: return@AnimatedClickDialog
            launchingOs = true
            when (current.kind) {
                PermissionKind.Location ->
                    requestLocation { PermissionRequestQueue.completeCurrent() }
                PermissionKind.Microphone ->
                    requestMicrophone { PermissionRequestQueue.completeCurrent() }
                PermissionKind.Camera ->
                    requestCamera { PermissionRequestQueue.completeCurrent() }
                PermissionKind.ProximityHardware ->
                    requestProximity { granted -> PermissionRequestQueue.completeCurrent(granted) }
                PermissionKind.Calendar ->
                    requestCalendar { PermissionRequestQueue.completeCurrent() }
                PermissionKind.Contacts ->
                    requestContacts { PermissionRequestQueue.completeCurrent() }
            }
        },
        dismissLabel = "Not now",
        modifier = Modifier.testTag("permission-prime"),
        confirmModifier = Modifier.testTag("permission-prime-continue"),
    ) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
