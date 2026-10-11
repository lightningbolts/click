package compose.project.click.click.telemetry

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** G4-8: mirrors click-web `CONNECTION_FLOW_ALLOWED_EVENTS` and the capture-quality allowlist. */
class ConnectionFlowAllowlistTest {
    // Copied from click-web lib/server/telemetry/connectionFlowEvents.ts (origin/main fa1d6f4).
    private val webEvents =
        setOf(
            "proximity_handshake_started",
            "proximity_handshake_matched",
            "proximity_handshake_awaiting_selection",
            "proximity_handshake_pending",
            "proximity_handshake_offline_queued",
            "proximity_handshake_failed",
            "proximity_host_selection_confirmed",
            "proximity_host_selection_abandoned",
            "proximity_reconnect_encounter_saved",
            "proximity_reconnect_rate_limited",
            "proximity_recovery_poll_success",
            "proximity_recovery_poll_timeout",
            "proximity_recovery_incomplete",
            "verified_clique_from_proximity_created",
            "verified_clique_from_proximity_blocked",
            "proximity_at_event_attached",
            "proximity_at_event_skipped",
            "qr_connect_started",
            "qr_connect_succeeded",
            "qr_connect_failed",
            "qr_connect_reconnect_rate_limited",
        )

    @Test
    fun eventsMatchClickWeb() {
        assertEquals(webEvents, ConnectionFlowTelemetry.ALLOWED_EVENTS)
    }

    @Test
    fun captureQualityDropsNonAllowlistedKeys() {
        val raw =
            mapOf(
                "location_accuracy_m" to JsonPrimitive(4.8),
                "connection_method" to JsonPrimitive("qr"),
                "lat" to JsonPrimitive(47.6),
                "token" to JsonPrimitive("1234"),
            )
        assertEquals(setOf("location_accuracy_m", "connection_method"), ConnectionFlowTelemetry.sanitizeCaptureQuality(raw)!!.keys)
        assertNull(ConnectionFlowTelemetry.sanitizeCaptureQuality(mapOf("lon" to JsonPrimitive(1))))
    }

    @Test
    fun qrFailureReasonsAreShortCodes() {
        assertEquals(
            "proximity_failed",
            ConnectionFlowTelemetry.qrFailureReason("Connection failed: Users must be in the same physical location."),
        )
        assertEquals("expired", ConnectionFlowTelemetry.qrFailureReason("This QR code has expired. Ask them to generate a new one."))
        assertEquals("validation", ConnectionFlowTelemetry.qrFailureReason(null))
    }
}
