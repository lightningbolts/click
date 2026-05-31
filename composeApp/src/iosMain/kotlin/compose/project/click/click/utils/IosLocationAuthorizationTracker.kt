package compose.project.click.click.utils

import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject

/**
 * iOS location authorization for Compose UI and [LocationService].
 *
 * Reads [CLLocationManager.authorizationStatus] on each check. Status values are normalized to [Int]
 * before compare — Kotlin/Native often bridges them as [UInt] while kCL* constants are [Int].
 */
internal object IosLocationAuthorizationTracker {

    private val authManager = CLLocationManager()

    private val startOnce = lazy {
        authManager.delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                // Status is read live via [liveStatus]; delegate keeps the manager wired for prompts.
            }
        }
    }

    fun ensureStarted() {
        startOnce.value
    }

    fun refreshFromSystem() {
        ensureStarted()
        liveStatus()
    }

    private fun liveStatus(): Any? {
        ensureStarted()
        return authManager.authorizationStatus
    }

    fun hasWhenInUseOrAlways(): Boolean {
        val s = authStatusInt(liveStatus())
        return s == authStatusInt(kCLAuthorizationStatusAuthorizedWhenInUse) ||
            s == authStatusInt(kCLAuthorizationStatusAuthorizedAlways)
    }

    fun isDeniedOrRestricted(): Boolean {
        val s = authStatusInt(liveStatus())
        return s == authStatusInt(kCLAuthorizationStatusDenied) ||
            s == authStatusInt(kCLAuthorizationStatusRestricted)
    }

    fun isNotDetermined(): Boolean {
        return authStatusInt(liveStatus()) == authStatusInt(kCLAuthorizationStatusNotDetermined)
    }

    fun currentStatusForPermissionFlow(): Int = authStatusInt(liveStatus())

    private fun authStatusInt(value: Any?): Int = when (value) {
        is Int -> value
        is UInt -> value.toInt()
        is Long -> value.toInt()
        is ULong -> value.toInt()
        is Short -> value.toInt()
        else -> 0
    }
}
