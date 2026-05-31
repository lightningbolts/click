package compose.project.click.click.utils

/**
 * Human-readable location permission state for Settings / onboarding UI.
 */
enum class LocationPermissionDisplayState {
    Granted,
    /** iOS: not determined yet. Android: not granted (tap Allow to prompt). */
    NotSet,
    /** iOS: denied or restricted. User must open system settings. */
    Denied,
}

expect fun LocationService.readLocationPermissionDisplayState(): LocationPermissionDisplayState
