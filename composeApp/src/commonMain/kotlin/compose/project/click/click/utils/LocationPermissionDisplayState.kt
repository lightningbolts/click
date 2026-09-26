package compose.project.click.click.utils

/**
 * Human-readable location permission state for Settings / onboarding UI.
 */
enum class LocationPermissionDisplayState {
    Granted,

    /** Not granted (tap Allow to prompt). */
    NotSet,

    /** Denied or restricted. User must open system settings. */
    Denied,
}

expect fun LocationService.readLocationPermissionDisplayState(): LocationPermissionDisplayState
