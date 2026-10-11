package compose.project.click.click.connect

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * One fused-location fix reduced to explicit scalar measurements (port of iOS
 * `LocationObservation`). Each phone reports only its own observation. Fields Android cannot
 * measure (floor, accessory provenance) stay null and are omitted on the wire.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Radius of uncertainty (m), not a correctness score. */
    val horizontalAccuracyMeters: Double,
    /** Null when the platform reported no valid vertical accuracy. */
    val verticalAccuracyMeters: Double? = null,
    /** Altitude above mean sea level (Android 34+ `getMslAltitudeMeters`), only with a vertical accuracy. */
    val altitudeMslMeters: Double? = null,
    /** WGS84 ellipsoidal altitude (`Location.getAltitude`), only with a vertical accuracy. */
    val ellipsoidalAltitudeMeters: Double? = null,
    val observedAtMs: Long,
    val floorLevel: Int? = null,
    /** False when only approximate location was granted. */
    val isFullAccuracy: Boolean = true,
    /** `Location.isMock` / `isFromMockProvider`. */
    val isSimulated: Boolean? = null,
    val isExternalAccessory: Boolean? = null,
    val speedMetersPerSecond: Double? = null,
    val speedAccuracyMetersPerSecond: Double? = null,
    val courseDegrees: Double? = null,
    val courseAccuracyDegrees: Double? = null,
) {
    val hasValidVertical: Boolean get() = verticalAccuracyMeters != null

    /** The same fix (its accuracy, time and altitude) at another position. */
    fun moved(
        latitude: Double,
        longitude: Double,
    ): LocationFix = copy(latitude = latitude, longitude = longitude)

    /**
     * Request-body keys (= `connection_encounters` column names) for this fix's quality
     * (07 §6 G4-2). The coordinate itself travels under each endpoint's existing keys. Absent
     * values are omitted, never sent as negative numbers.
     */
    fun qualityColumns(): JsonObject =
        buildJsonObject {
            put("gps_horizontal_accuracy_m", rounded(horizontalAccuracyMeters, 2))
            put("gps_observed_at", isoMillis(observedAtMs))
            put("gps_full_accuracy", isFullAccuracy)
            verticalAccuracyMeters?.let { vertical ->
                put("gps_vertical_accuracy_m", rounded(vertical, 2))
                altitudeMslMeters?.let { put("gps_altitude_m", rounded(it, 2)) }
                ellipsoidalAltitudeMeters?.let { put("gps_ellipsoidal_altitude_m", rounded(it, 2)) }
            }
            floorLevel?.let { put("gps_floor", it) }
            speedMetersPerSecond?.let { speed ->
                put("gps_speed_mps", rounded(speed, 2))
                speedAccuracyMetersPerSecond?.let { put("gps_speed_accuracy_mps", rounded(it, 2)) }
            }
            courseDegrees?.let { course ->
                put("gps_course_deg", rounded(course, 1))
                courseAccuracyDegrees?.let { put("gps_course_accuracy_deg", rounded(it, 1)) }
            }
            isSimulated?.let { put("gps_simulated", it) }
            isExternalAccessory?.let { put("gps_external_accessory", it) }
        }

    companion object {
        /** A fix stamped further in the future than this is a clock fault. */
        const val FUTURE_SKEW_TOLERANCE_MS: Long = 2_000L

        /**
         * Validates raw platform values: finite in-range coordinate (not null island), a
         * non-negative horizontal accuracy and a timestamp no older than [maximumAgeMs].
         * Altitude is kept only with a positive vertical accuracy; speed/course only when valid.
         */
        fun validated(
            latitude: Double,
            longitude: Double,
            horizontalAccuracyMeters: Double?,
            observedAtMs: Long,
            nowMs: Long,
            maximumAgeMs: Long,
            verticalAccuracyMeters: Double? = null,
            altitudeMslMeters: Double? = null,
            ellipsoidalAltitudeMeters: Double? = null,
            isFullAccuracy: Boolean = true,
            isSimulated: Boolean? = null,
            speedMetersPerSecond: Double? = null,
            speedAccuracyMetersPerSecond: Double? = null,
            courseDegrees: Double? = null,
            courseAccuracyDegrees: Double? = null,
        ): LocationFix? {
            if (!latitude.isFinite() || !longitude.isFinite()) return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            if (latitude == 0.0 && longitude == 0.0) return null
            val horizontal = horizontalAccuracyMeters?.takeIf { it.isFinite() && it >= 0 } ?: return null
            val age = nowMs - observedAtMs
            if (age > maximumAgeMs || age < -FUTURE_SKEW_TOLERANCE_MS) return null
            val vertical = verticalAccuracyMeters?.takeIf { it.isFinite() && it > 0 }
            val speed = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0 }
            val course = courseDegrees?.takeIf { it.isFinite() && it >= 0 }
            return LocationFix(
                latitude = latitude,
                longitude = longitude,
                horizontalAccuracyMeters = horizontal,
                verticalAccuracyMeters = vertical,
                altitudeMslMeters = if (vertical != null) altitudeMslMeters?.takeIf { it.isFinite() } else null,
                ellipsoidalAltitudeMeters = if (vertical != null) ellipsoidalAltitudeMeters?.takeIf { it.isFinite() } else null,
                observedAtMs = observedAtMs,
                isFullAccuracy = isFullAccuracy,
                isSimulated = isSimulated,
                speedMetersPerSecond = speed,
                speedAccuracyMetersPerSecond =
                    if (speed != null) speedAccuracyMetersPerSecond?.takeIf { it.isFinite() && it >= 0 } else null,
                courseDegrees = course,
                courseAccuracyDegrees =
                    if (course != null) courseAccuracyDegrees?.takeIf { it.isFinite() && it >= 0 } else null,
            )
        }
    }
}

internal fun rounded(
    value: Double,
    places: Int,
): Double {
    val scale = 10.0.pow(places)
    return round(value * scale) / scale
}

/**
 * The one location-quality policy shared by Tap to Connect and QR (port of iOS
 * `ConnectionLocationQuality`, 07 §6 G4-1). Times are epoch milliseconds.
 */
object ConnectionLocationQuality {
    const val EXCELLENT_ACCURACY_M: Double = 5.0
    const val GOOD_ACCURACY_M: Double = 10.0
    const val USABLE_ACCURACY_M: Double = 25.0

    /** Coarser fixes are never attached to an encounter (also clears `/api/qr`'s 100 m check). */
    const val MAXIMUM_USEFUL_ACCURACY_M: Double = 50.0

    const val MINIMUM_CAPTURE_MS: Long = 1_500L
    const val COLD_CAPTURE_MS: Long = 3_000L
    const val HARD_CAPTURE_LIMIT_MS: Long = 8_000L
    const val IN_FLOW_GRACE_MS: Long = 1_000L
    const val MAXIMUM_SEED_AGE_MS: Long = 10_000L
    const val SELECTION_LOOKBACK_MS: Long = 8_000L

    /** Walking pace: a fix `t` seconds from the moment may be this far off per second. */
    const val ASSUMED_DRIFT_MPS: Double = 1.5
    const val TIE_TOLERANCE_M: Double = 0.5
    const val MAXIMUM_WARM_MS: Long = 120_000L
    const val BUFFER_CAPACITY: Int = 64

    // Refinement after the moment (G4-7).
    const val REFINEMENT_WINDOW_MS: Long = 20_000L
    const val REFINEMENT_GAIN: Double = 0.8
    const val STATIONARY_ACCELERATION_RMS_G: Double = 0.12
    const val MOVING_SECONDS: Int = 3
    const val STATIONARY_SPEED_MPS: Double = 0.8

    /** How long a flow may wait at its moment, and what ends the wait early. */
    data class Wait(
        val settleAccuracyM: Double,
        val graceMs: Long,
    ) {
        companion object {
            /** Tap / QR: location has been warming during the interaction. */
            val InFlow = Wait(GOOD_ACCURACY_M, IN_FLOW_GRACE_MS)

            /** No warm-up (e.g. a Click link). */
            val Cold = Wait(GOOD_ACCURACY_M, COLD_CAPTURE_MS)
        }
    }

    /** Coarse accuracy band for diagnostics and aggregate telemetry. */
    fun tier(accuracyM: Double): String =
        when {
            accuracyM <= EXCELLENT_ACCURACY_M -> "excellent"
            accuracyM <= GOOD_ACCURACY_M -> "good"
            accuracyM <= USABLE_ACCURACY_M -> "usable"
            accuracyM <= MAXIMUM_USEFUL_ACCURACY_M -> "coarse"
            else -> "unusable"
        }

    fun isUseful(fix: LocationFix): Boolean = fix.horizontalAccuracyMeters <= MAXIMUM_USEFUL_ACCURACY_M

    /** Reported uncertainty grown by how far the fix is in time from the moment. */
    fun effectiveRadius(
        fix: LocationFix,
        momentMs: Long,
    ): Double = fix.horizontalAccuracyMeters + ASSUMED_DRIFT_MPS * abs(fix.observedAtMs - momentMs) / 1000.0

    /** Full accuracy first, then the smallest time-adjusted radius, valid vertical, closest in time. */
    fun isBetter(
        a: LocationFix,
        b: LocationFix,
        momentMs: Long,
    ): Boolean {
        if (a.isFullAccuracy != b.isFullAccuracy) return a.isFullAccuracy
        val ra = effectiveRadius(a, momentMs)
        val rb = effectiveRadius(b, momentMs)
        if (abs(ra - rb) > TIE_TOLERANCE_M) return ra < rb
        if (a.hasValidVertical != b.hasValidVertical) return a.hasValidVertical
        return abs(a.observedAtMs - momentMs) < abs(b.observedAtMs - momentMs)
    }

    /** The most useful fix for an event at [momentMs]; fixes worse than 50 m never qualify. */
    fun best(
        fixes: List<LocationFix>,
        momentMs: Long,
        untilMs: Long,
    ): LocationFix? {
        val earliest = momentMs - SELECTION_LOOKBACK_MS
        var best: LocationFix? = null
        for (fix in fixes) {
            if (!isUseful(fix) || fix.observedAtMs < earliest || fix.observedAtMs > untilMs) continue
            val current = best
            if (current == null || isBetter(fix, current, momentMs)) best = fix
        }
        return best
    }

    data class Fused(
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
        val fixCount: Int,
        val spanMs: Long,
    )

    private const val METERS_PER_DEGREE = 111_320.0

    /**
     * Several consistent fixes near the moment combined by inverse-variance weighting over
     * time-adjusted radii; outliers farther than their combined radii from the best fix are
     * left out. Never claims a radius below half the best fix's. Null with fewer than two.
     */
    fun fused(
        fixes: List<LocationFix>,
        momentMs: Long,
        untilMs: Long,
    ): Fused? {
        val anchor = best(fixes, momentMs, untilMs) ?: return null
        val earliest = momentMs - SELECTION_LOOKBACK_MS
        val anchorRadius = effectiveRadius(anchor, momentMs)
        val cosLat = cos(anchor.latitude * PI / 180)

        fun offset(fix: LocationFix): Pair<Double, Double> =
            (fix.longitude - anchor.longitude) * METERS_PER_DEGREE * cosLat to
                (fix.latitude - anchor.latitude) * METERS_PER_DEGREE

        val consistent =
            fixes.filter { fix ->
                if (!isUseful(fix) || fix.isFullAccuracy != anchor.isFullAccuracy) return@filter false
                if (fix.observedAtMs < earliest || fix.observedAtMs > untilMs) return@filter false
                val (x, y) = offset(fix)
                sqrt(x * x + y * y) <= effectiveRadius(fix, momentMs) + anchorRadius
            }
        if (consistent.size < 2) return null
        var sumW = 0.0
        var sumX = 0.0
        var sumY = 0.0
        for (fix in consistent) {
            val radius = max(1.0, effectiveRadius(fix, momentMs))
            val w = 1 / (radius * radius)
            val (x, y) = offset(fix)
            sumW += w
            sumX += w * x
            sumY += w * y
        }
        val times = consistent.map { it.observedAtMs }
        return Fused(
            latitude = anchor.latitude + (sumY / sumW) / METERS_PER_DEGREE,
            longitude = anchor.longitude + (sumX / sumW) / (METERS_PER_DEGREE * cosLat),
            radiusMeters = max(sqrt(1 / sumW), anchorRadius / 2),
            fixCount = consistent.size,
            spanMs = (times.maxOrNull() ?: momentMs) - (times.minOrNull() ?: momentMs),
        )
    }

    /** Ground distance (m) between two nearby fixes (equirectangular). */
    fun meters(
        a: LocationFix,
        b: LocationFix,
    ): Double {
        val x = (b.longitude - a.longitude) * METERS_PER_DEGREE * cos(a.latitude * PI / 180)
        val y = (b.latitude - a.latitude) * METERS_PER_DEGREE
        return sqrt(x * x + y * y)
    }

    /**
     * A tighter fix for the moment from [later] fixes (taken after it, while still), or null
     * unless clearly better (≤ 0.8 × the original radius). Position: inverse-variance mean of
     * the fixes agreeing with the best one; radius: the best fix's own. Dropped if it disagrees
     * with the original by more than the sum of the two radii (the phone moved).
     */
    fun refined(
        original: LocationFix,
        later: List<LocationFix>,
        momentMs: Long,
    ): LocationFix? {
        val still =
            later.filter {
                isUseful(it) &&
                    it.isFullAccuracy == original.isFullAccuracy &&
                    it.observedAtMs > momentMs &&
                    (it.speedMetersPerSecond ?: 0.0) < STATIONARY_SPEED_MPS
            }
        val best = still.minByOrNull { it.horizontalAccuracyMeters } ?: return null
        if (best.horizontalAccuracyMeters > original.horizontalAccuracyMeters * REFINEMENT_GAIN) return null
        val agreeing = still.filter { meters(it, best) <= it.horizontalAccuracyMeters + best.horizontalAccuracyMeters }
        var sumW = 0.0
        var sumLat = 0.0
        var sumLon = 0.0
        for (fix in agreeing) {
            val radius = max(1.0, fix.horizontalAccuracyMeters)
            val w = 1 / (radius * radius)
            sumW += w
            sumLat += w * fix.latitude
            sumLon += w * fix.longitude
        }
        val result = best.moved(sumLat / sumW, sumLon / sumW)
        if (meters(result, original) > original.horizontalAccuracyMeters + best.horizontalAccuracyMeters) return null
        return result
    }

    /** True once the minimum window has passed and [best] is at least `settleAccuracy`. */
    fun isSettled(
        best: LocationFix?,
        momentMs: Long,
        startedAtMs: Long,
        nowMs: Long,
        wait: Wait,
    ): Boolean {
        if (best == null || nowMs - startedAtMs < MINIMUM_CAPTURE_MS) return false
        return effectiveRadius(best, momentMs) <= wait.settleAccuracyM
    }

    /** `grace` after the moment, bounded by the hard limit, never before the minimum window. */
    fun deadline(
        momentMs: Long,
        startedAtMs: Long,
        wait: Wait,
    ): Long {
        val bounded = minOf(momentMs + wait.graceMs, startedAtMs + HARD_CAPTURE_LIMIT_MS)
        return maxOf(startedAtMs + MINIMUM_CAPTURE_MS, bounded)
    }
}
