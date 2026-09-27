package compose.project.click.click.ui.utils

import androidx.compose.runtime.Composable

/** Every OS capability Click uses (iOS `PermissionsSettingsView`, spec §65.11). */
enum class AppPermission(
    val title: String,
    val use: String,
) {
    Location("Location", "Map, Tap to Connect, event check-in"),
    Bluetooth("Bluetooth", "Tap to Connect"),
    Microphone("Microphone", "Tap to Connect sound check, voice notes"),
    Camera("Camera", "QR scanning, photos"),
    Photos("Photos", "Sharing and saving photos"),
    Contacts("Contacts", "Finding people you know (hashed on this phone)"),
    Calendar("Calendar", "Free/busy for availability"),
    Notifications("Notifications", "Messages and alerts"),
}

enum class AppPermissionState {
    Granted,

    /** Not granted and the OS will still show its prompt. */
    CanRequest,

    /** Denied in a way only system Settings can undo. */
    Blocked,

    /** This Android version grants it without a prompt (e.g. the photo picker needs no access). */
    NotNeeded,
}

/** What a permission row offers on its trailing side. */
sealed interface PermissionRowAction {
    data class Label(
        val text: String,
    ) : PermissionRowAction

    data object Allow : PermissionRowAction

    data object OpenSettings : PermissionRowAction
}

fun permissionRowAction(state: AppPermissionState): PermissionRowAction =
    when (state) {
        AppPermissionState.Granted -> PermissionRowAction.Label("Allowed")
        AppPermissionState.NotNeeded -> PermissionRowAction.Label("Not needed")
        AppPermissionState.CanRequest -> PermissionRowAction.Allow
        AppPermissionState.Blocked -> PermissionRowAction.OpenSettings
    }

interface AppPermissionController {
    fun state(permission: AppPermission): AppPermissionState

    /** Shows the OS prompt; [onResult] runs after it closes (granted or not). Never auto-opens Settings. */
    fun request(
        permission: AppPermission,
        onResult: () -> Unit = {},
    )

    /** App settings, or the app's notification settings for [AppPermission.Notifications]. */
    fun openSettings(permission: AppPermission)
}

@Composable
expect fun rememberAppPermissionController(): AppPermissionController
