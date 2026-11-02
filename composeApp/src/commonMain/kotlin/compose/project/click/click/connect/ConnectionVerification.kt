package compose.project.click.click.connect

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What confirmed a connection on this phone, shown on the result ("Bluetooth · Sound · Location ±8 m";
 * 07 §6 G4-6). Only this phone's own signals; nothing here is sent to the server.
 *
 * iOS: `ConnectionVerification` in `Click/Core/Proximity/ProximityRepository.swift`, built in
 * `TapConnectModel.run()` and `ClickConnectionRedeemer.submit` (`AddClickView.swift`).
 */
data class ConnectionVerification(
    val signals: List<String>,
    /** This phone's own location accuracy at the connection moment, when Location snap is on. */
    val locationAccuracyMeters: Double? = null,
) {
    /** "Bluetooth · Sound · Location ±8 m" (never below ±1 m). */
    val summary: String
        get() {
            val parts = signals.toMutableList()
            locationAccuracyMeters?.takeIf { it.isFinite() && it >= 0 }?.let { accuracy ->
                val location = "Location ±${max(1, accuracy.roundToInt())} m"
                val index = parts.indexOf(LOCATION)
                if (index >= 0) parts[index] = location else parts += location
            }
            return parts.joinToString(" · ")
        }

    /** The same verification with a tighter accuracy from the location follow-up (G4-7). */
    fun refined(accuracyMeters: Double?): ConnectionVerification =
        if (accuracyMeters == null || locationAccuracyMeters == null) this else copy(locationAccuracyMeters = accuracyMeters)

    companion object {
        internal const val LOCATION = "Location"

        /** A tap: which of this phone's factors found the peer. */
        fun forTap(
            bluetooth: Boolean,
            sound: Boolean,
            hasLocation: Boolean,
            locationAccuracyMeters: Double?,
            simulator: Boolean = false,
        ): ConnectionVerification =
            if (simulator) {
                ConnectionVerification(listOf("Simulator"))
            } else {
                ConnectionVerification(
                    signals =
                        listOfNotNull(
                            "Bluetooth".takeIf { bluetooth },
                            "Sound".takeIf { sound },
                            LOCATION.takeIf { hasLocation },
                        ),
                    locationAccuracyMeters = locationAccuracyMeters.takeIf { hasLocation },
                )
            }

        /** A QR scan, with this phone's fix when one was attached. */
        fun forQr(locationAccuracyMeters: Double?): ConnectionVerification =
            ConnectionVerification(
                signals = listOfNotNull("QR code", LOCATION.takeIf { locationAccuracyMeters != null }),
                locationAccuracyMeters = locationAccuracyMeters,
            )
    }
}

/** The "Verified by" line: the summary, else the method's own name (iOS `PostConnectModel.verificationLine`). */
fun verificationLine(
    verification: ConnectionVerification?,
    isQr: Boolean,
    refinedAccuracyMeters: Double? = null,
): String {
    val summary = verification?.refined(refinedAccuracyMeters)?.summary.orEmpty()
    return summary.ifEmpty { if (isQr) "QR code" else "Tap to Connect" }
}
