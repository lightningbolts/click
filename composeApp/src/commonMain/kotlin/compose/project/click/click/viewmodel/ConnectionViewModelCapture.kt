package compose.project.click.click.viewmodel

import androidx.lifecycle.viewModelScope
import compose.project.click.click.connect.ConnectionCaptureSession // pragma: allowlist secret
import compose.project.click.click.connect.ConnectionCaptureSnapshot // pragma: allowlist secret
import compose.project.click.click.connect.ConnectionLocationQuality // pragma: allowlist secret
import compose.project.click.click.connect.ConnectionSensorObservation // pragma: allowlist secret
import compose.project.click.click.connect.connectionQualityKeys // pragma: allowlist secret
import compose.project.click.click.connect.currentConnectionDeviceContext // pragma: allowlist secret
import compose.project.click.click.connect.hasPreciseLocationPermission // pragma: allowlist secret
import compose.project.click.click.data.AppDataManager // pragma: allowlist secret
import compose.project.click.click.data.storage.createTokenStorage // pragma: allowlist secret
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

/**
 * What a connect body carries from this phone's capture (G4-1..3). Mirrors the capture half of
 * iOS `ClickConnectionRedeemer.context` (`Click/Features/AddClick/AddClickView.swift`) and
 * `TapConnectModel`'s use of `ConnectionCaptureSession`.
 */
internal data class CapturedConnectContext(
    val snapshot: ConnectionCaptureSnapshot,
    /** Location snap on: the fix and every location-derived value may be sent. */
    val includeLocation: Boolean,
    /** Top-level `gps_*` / `barometric_*` keys plus `sensor_observation`. */
    val observationKeys: JsonObject,
) {
    /** The ≤ 50 m fix to send as the body's own coordinate, when location is included. */
    val fix get() = snapshot.location?.takeIf { includeLocation && ConnectionLocationQuality.isUseful(it) }
}

/**
 * Warms location (only with Location snap on and permission already granted; never prompts) and
 * the pressure sensor (only with barometric context opted in) while a Tap or QR screen is visible.
 */
fun ConnectionViewModel.warmUpConnectionCapture(method: String) {
    viewModelScope.launch {
        val session =
            captureSession?.takeIf { it.method == method } ?: ConnectionCaptureSession(method).also {
                captureSession?.stop()
                captureSession = it
            }
        val baroOptIn = runCatching { createTokenStorage().getBarometricContextOptIn() }.getOrNull() ?: false
        session.start(
            location = AppDataManager.shouldCaptureLocationAtTap(),
            barometer = baroOptIn,
            scope = viewModelScope,
        )
    }
}

/** Stops the warm capture (screen left). */
fun ConnectionViewModel.stopConnectionCapture() {
    captureSession?.stop()
    captureSession = null
}

/**
 * This phone's readings at [momentMs], building the quality keys and the bounded
 * `sensor_observation` in common code. Starts a cold capture when none was warmed.
 */
internal suspend fun ConnectionViewModel.captureConnectContext(
    method: String,
    momentMs: Long,
    includeLocation: Boolean,
    cold: Boolean = false,
): CapturedConnectContext {
    val session =
        captureSession ?: ConnectionCaptureSession(method).also { captureSession = it }
    if (!session.isRunning) {
        val baroOptIn = runCatching { createTokenStorage().getBarometricContextOptIn() }.getOrNull() ?: false
        session.start(location = includeLocation, barometer = baroOptIn, scope = viewModelScope)
    }
    val wait = if (cold) ConnectionLocationQuality.Wait.Cold else ConnectionLocationQuality.Wait.InFlow
    val snapshot = session.snapshot(momentMs, wait)
    val nowMs = Clock.System.now().toEpochMilliseconds()
    val tzSeconds =
        Clock.System
            .now()
            .offsetIn(TimeZone.currentSystemDefault())
            .totalSeconds
    val observation =
        ConnectionSensorObservation.bounded(
            ConnectionSensorObservation.build(
                method = session.method,
                snapshot = snapshot,
                includeLocation = includeLocation,
                device = currentConnectionDeviceContext(hasPreciseLocationPermission()),
                sentAtMs = nowMs,
                timeZoneOffsetSeconds = tzSeconds,
            ),
        )
    lastCaptureQuality = ConnectionSensorObservation.captureQuality(session.method, snapshot, includeLocation)
    val keys =
        buildJsonObject {
            connectionQualityKeys(snapshot, includeLocation).forEach { (k, v) -> put(k, v) }
            observation?.let { put("sensor_observation", it) }
        }
    return CapturedConnectContext(snapshot = snapshot, includeLocation = includeLocation, observationKeys = keys)
}
