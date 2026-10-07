package compose.project.click.click.ui.screens

import compose.project.click.click.utils.LocationPermissionDisplayState

internal fun locationSnapHint(state: LocationPermissionDisplayState): String? =
    when (state) {
        LocationPermissionDisplayState.Granted -> null
        LocationPermissionDisplayState.NotSet ->
            "Location isn’t enabled yet — tap Allow in Me → Permissions, or allow it when you connect."
        LocationPermissionDisplayState.Denied ->
            "Location access is off — open System Settings to capture connection snaps."
    }
