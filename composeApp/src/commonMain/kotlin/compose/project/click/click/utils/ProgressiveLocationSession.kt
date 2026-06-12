package compose.project.click.click.utils

import kotlin.time.TimeSource

/**
 * Progressive acceptance for high-accuracy GPS: ≤1m for the first 3s, then ≤5m / ≤10m through 6.5s.
 * At timeout, [bestAtTimeout] returns the fix with the smallest horizontal accuracy among readings
 * that are at or below [FINAL_ACCURACY_THRESHOLD_METERS], or null if none qualify.
 */
class ProgressiveLocationSession private constructor(
    private val elapsedMillis: () -> Long,
) {
    private val buffer = mutableListOf<LocationResult>()

    /**
     * Records a reading and returns it immediately if it satisfies the current time bucket; otherwise null.
     */
    fun onReading(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Double,
        altitudeMeters: Double?,
    ): LocationResult? {
        if (latitude == 0.0 && longitude == 0.0) return null
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (!accuracyMeters.isFinite() || accuracyMeters <= 0.0) return null

        val elapsedMs = elapsedMillis()
        val result = LocationResult(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            accuracyMeters = accuracyMeters,
        )
        buffer.add(result)

        // Mutually exclusive windows: 0–3s ≤1m, 3–4.8s ≤5m, 4.8–6.5s ≤10m.
        return when {
            elapsedMs < BUCKET1_END_MS ->
                if (accuracyMeters <= BUCKET1_ACCURACY_METERS) result else null
            elapsedMs < BUCKET2_END_MS ->
                if (accuracyMeters <= BUCKET2_ACCURACY_METERS) result else null
            elapsedMs < BUCKET3_END_MS ->
                if (accuracyMeters <= BUCKET3_ACCURACY_METERS) result else null
            else -> null
        }
    }

    fun bestAtTimeout(): LocationResult? {
        val candidates = buffer.filter { (it.accuracyMeters ?: Double.MAX_VALUE) <= FINAL_ACCURACY_THRESHOLD_METERS }
        if (candidates.isEmpty()) return null
        return candidates.minBy { it.accuracyMeters ?: Double.MAX_VALUE }
    }

    companion object {
        const val FINAL_ACCURACY_THRESHOLD_METERS = 15.0

        private const val BUCKET1_ACCURACY_METERS = 1.0
        private const val BUCKET2_ACCURACY_METERS = 5.0
        private const val BUCKET3_ACCURACY_METERS = 10.0

        private const val BUCKET1_END_MS = 3000L
        private const val BUCKET2_END_MS = 4800L
        private const val BUCKET3_END_MS = 6500L

        fun start(): ProgressiveLocationSession {
            val mark = TimeSource.Monotonic.markNow()
            return ProgressiveLocationSession { mark.elapsedNow().inWholeMilliseconds }
        }

        internal fun forTest(elapsedMillis: () -> Long): ProgressiveLocationSession =
            ProgressiveLocationSession(elapsedMillis)
    }
}
