package compose.project.click.click.utils

import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

/**
 * Avoids calling [CLLocationManager.authorizationStatus] synchronously on the main thread (iOS warns
 * this can freeze UI). Status updates come from [locationManagerDidChangeAuthorization] and a
 * one-time read on a background queue when tracking starts.
 */
internal object IosLocationAuthorizationTracker {

    private const val UNSET = -1

    private val authManager = CLLocationManager()

    /** Written only on the main queue; read from main thread (Compose / UI). */
    private var cachedStatus: Int = UNSET

    private val startOnce = lazy {
        authManager.delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
            override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                val raw = manager.authorizationStatus
                dispatch_async(dispatch_get_main_queue()) {
                    cachedStatus = raw.toInt()
                }
            }
        }
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            val raw = CLLocationManager.authorizationStatus()
            dispatch_async(dispatch_get_main_queue()) {
                if (cachedStatus == UNSET) {
                    cachedStatus = raw.toInt()
                }
            }
        }
    }

    fun ensureStarted() {
        startOnce.value
    }

    /**
     * Raw [platform.CoreLocation.CLAuthorizationStatus] as [Int]; [UNSET] maps to 0 (not determined)
     * for conservative UI until the first callback or background read lands.
     */
    fun currentStatusInt(): Int {
        ensureStarted()
        val c = cachedStatus
        return if (c == UNSET) 0 else c
    }

    fun hasWhenInUseOrAlways(): Boolean {
        val s = currentStatusInt()
        return s == kCLAuthorizationStatusAuthorizedWhenInUse ||
            s == kCLAuthorizationStatusAuthorizedAlways
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Any?.toInt(): Int =
    when (this) {
        is Int -> this
        is UInt -> this.toInt()
        is Long -> this.toInt()
        else -> 0
    }
