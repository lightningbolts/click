package compose.project.click.click.telemetry

/**
 * On-device coarse spatial bucket — opaque neighborhood key, never raw coordinates.
 * Matches the vibe-radar `anonymized_cell_id` shape used server-side (~500 m buckets).
 */
object AnonymizedHexbin {

    private const val BUCKET_SCALE = 200.0

    fun fromCoordinates(latitude: Double, longitude: Double): String {
        if (!latitude.isFinite() || !longitude.isFinite()) return UNKNOWN_CELL
        if (latitude == 0.0 && longitude == 0.0) return UNKNOWN_CELL

        val latBucket = kotlin.math.floor(latitude * BUCKET_SCALE).toInt()
        val lonBucket = kotlin.math.floor(longitude * BUCKET_SCALE).toInt()
        val digest = fnv1a64("$latBucket:$lonBucket")
        return "hx_${digest.toString(16).padStart(16, '0').take(12)}"
    }

    const val UNKNOWN_CELL: String = "hx_unknown"

    private fun fnv1a64(input: String): Long {
        var hash = -0x340d631b7bdddcdbL
        for (byte in input.encodeToByteArray()) {
            hash = hash xor byte.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }
}
