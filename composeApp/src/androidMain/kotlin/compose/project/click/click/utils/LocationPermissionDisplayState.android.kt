package compose.project.click.click.utils

actual fun LocationService.readLocationPermissionDisplayState(): LocationPermissionDisplayState {
    return if (hasLocationPermission()) {
        LocationPermissionDisplayState.Granted
    } else {
        // Without an Activity we cannot distinguish "never asked" vs "denied"; prefer Not set
        // so the in-app Allow action stays the primary path.
        LocationPermissionDisplayState.NotSet
    }
}
