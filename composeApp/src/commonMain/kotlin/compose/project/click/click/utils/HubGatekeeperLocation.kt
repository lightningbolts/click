package compose.project.click.click.utils

/**
 * Fast location resolution for hub geofence gatekeeper calls (join + send).
 *
 * Hub chat already verified proximity at join; sends only need coordinates fresh enough
 * for the server-side haversine check. Prefer cached / last-known fixes before waiting
 * on a long high-accuracy GPS session.
 */
fun hasUsableHubLocation(location: LocationResult?): Boolean {
    return location != null &&
        location.latitude.isFinite() &&
        location.longitude.isFinite() &&
        !(location.latitude == 0.0 && location.longitude == 0.0)
}

suspend fun resolveHubGatekeeperLocation(
    locationService: LocationService,
    lastKnownLatLon: Pair<Double, Double>? = null,
    seed: LocationResult? = null,
    highAccuracyTimeoutMs: Long = HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS,
): LocationResult? {
    if (hasUsableHubLocation(seed)) return seed

    lastKnownLatLon?.let { (lat, lon) ->
        val cached = LocationResult(latitude = lat, longitude = lon)
        if (hasUsableHubLocation(cached)) return cached
    }

    val current = locationService.getCurrentLocation()
    if (hasUsableHubLocation(current)) return current

    return runCatching {
        locationService.getHighAccuracyLocation(highAccuracyTimeoutMs)
    }.getOrNull()?.takeIf(::hasUsableHubLocation)
}

/** Short GPS wait — gatekeeper already accepted a join fix moments earlier. */
const val HUB_GATEKEEPER_HIGH_ACCURACY_TIMEOUT_MS = 2_500L

/** Reuse a gatekeeper fix for rapid back-to-back hub sends without re-querying GPS. */
const val HUB_GATEKEEPER_LOCATION_CACHE_TTL_MS = 90_000L
