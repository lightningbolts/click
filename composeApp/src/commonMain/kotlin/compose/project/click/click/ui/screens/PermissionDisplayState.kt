package compose.project.click.click.ui.screens

import compose.project.click.click.utils.LocationPermissionDisplayState

/**
 * Badge / row state for the Settings Permissions Hub.
 */
internal enum class PermissionHubStatus {
    Granted,
    NotSet,
    Denied,
    SystemManaged,
}

internal fun PermissionHubStatus.primaryActionLabel(permissionName: String): String? = when (this) {
    PermissionHubStatus.Granted,
    PermissionHubStatus.SystemManaged,
    -> null
    PermissionHubStatus.NotSet -> "Allow $permissionName"
    PermissionHubStatus.Denied -> "Open settings"
}

internal fun LocationPermissionDisplayState.toHubStatus(): PermissionHubStatus = when (this) {
    LocationPermissionDisplayState.Granted -> PermissionHubStatus.Granted
    LocationPermissionDisplayState.NotSet -> PermissionHubStatus.NotSet
    LocationPermissionDisplayState.Denied -> PermissionHubStatus.Denied
}

internal fun locationSnapHint(state: LocationPermissionDisplayState): String? = when (state) {
    LocationPermissionDisplayState.Granted -> null
    LocationPermissionDisplayState.NotSet ->
        "Location isn’t enabled yet — tap Allow location in Permissions Hub when you connect."
    LocationPermissionDisplayState.Denied ->
        "Location access is off — open System Settings to capture connection snaps."
}
