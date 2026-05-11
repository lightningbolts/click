package compose.project.click.click.sensors

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds `sensor_data` for POST `/api/connections/encounter` ([click-web/lib/connections/encounterSensorPayload.ts]).
 */
fun buildEncounterSensorJson(
    context: ConnectionSensorContext?,
    hardwareVibe: HardwareVibeSnapshot?,
    latitude: Double?,
    longitude: Double?,
): JsonObject = buildJsonObject {
    context?.noiseLevelCategory?.name?.trim()?.takeIf { it.isNotEmpty() }?.let { put("noise_level", it) }
    context?.exactNoiseLevelDb?.takeIf { it.isFinite() }?.let { put("exact_noise_level_db", it) }
    context?.heightCategory?.name?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { put("elevation_category", it) }
    context?.exactBarometricElevationMeters?.takeIf { it.isFinite() }
        ?.let { put("exact_barometric_elevation_m", it) }
    hardwareVibe?.luxLevel?.takeIf { it.isFinite() }?.let { put("lux_level", it.toDouble()) }
    hardwareVibe?.motionVariance?.takeIf { it.isFinite() }?.let { put("motion_variance", it.toDouble()) }
    hardwareVibe?.compassAzimuth?.takeIf { it.isFinite() }?.let { put("compass_azimuth", it.toDouble()) }
    hardwareVibe?.batteryLevel?.takeIf { it in 0..100 }?.let { put("battery_level", it) }
    latitude?.takeIf { it.isFinite() && !(it == 0.0) }?.let { put("gps_lat", it) }
    longitude?.takeIf { it.isFinite() && !(it == 0.0) }?.let { put("gps_lon", it) }
}
