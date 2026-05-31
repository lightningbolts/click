package compose.project.click.click.utils

actual fun LocationService.readLocationPermissionDisplayState(): LocationPermissionDisplayState {
    IosLocationAuthorizationTracker.refreshFromSystem()
    return when {
        IosLocationAuthorizationTracker.hasWhenInUseOrAlways() ->
            LocationPermissionDisplayState.Granted
        IosLocationAuthorizationTracker.isDeniedOrRestricted() ->
            LocationPermissionDisplayState.Denied
        else -> LocationPermissionDisplayState.NotSet
    }
}
