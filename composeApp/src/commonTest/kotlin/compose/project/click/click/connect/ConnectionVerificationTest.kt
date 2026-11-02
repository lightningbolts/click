package compose.project.click.click.connect

import kotlin.test.Test
import kotlin.test.assertEquals

/** G4-6 "Verified by" (iOS `ConnectionVerification.summary`, `PostConnectModel.verificationLine`). */
class ConnectionVerificationTest {
    @Test
    fun tapSummaryReplacesLocationWithItsAccuracy() {
        val v = ConnectionVerification.forTap(bluetooth = true, sound = true, hasLocation = true, locationAccuracyMeters = 7.6)
        assertEquals("Bluetooth · Sound · Location ±8 m", v.summary)
        assertEquals("Bluetooth · Location ±1 m", ConnectionVerification(listOf("Bluetooth", "Location"), 0.2).summary)
    }

    @Test
    fun tapWithoutLocationNeverShowsAccuracy() {
        val v = ConnectionVerification.forTap(bluetooth = false, sound = true, hasLocation = false, locationAccuracyMeters = 12.0)
        assertEquals("Sound", v.summary)
        assertEquals("Simulator", ConnectionVerification.forTap(true, true, true, 3.0, simulator = true).summary)
    }

    @Test
    fun qrSummary() {
        assertEquals("QR code · Location ±12 m", ConnectionVerification.forQr(12.3).summary)
        assertEquals("QR code", ConnectionVerification.forQr(null).summary)
    }

    @Test
    fun lineFallsBackToTheMethodAndShowsTheRefinedAccuracy() {
        assertEquals("Tap to Connect", verificationLine(null, isQr = false))
        assertEquals("QR code", verificationLine(ConnectionVerification(emptyList()), isQr = true))
        val v = ConnectionVerification.forTap(true, false, true, 14.0)
        assertEquals("Bluetooth · Location ±5 m", verificationLine(v, isQr = false, refinedAccuracyMeters = 5.0))
        // A refinement never adds a location the capture didn't have.
        val noFix = ConnectionVerification.forTap(true, false, false, null)
        assertEquals("Bluetooth", verificationLine(noFix, isQr = false, refinedAccuracyMeters = 5.0))
    }
}
