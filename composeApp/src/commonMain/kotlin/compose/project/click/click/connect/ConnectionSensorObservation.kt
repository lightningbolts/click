package compose.project.click.click.connect

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs

/** Device context for sensor-quality analysis only (iOS `ConnectionSensorObservation.Device`). */
data class ConnectionDeviceContext(
    val batteryPercent: Int?,
    val lowPowerMode: Boolean,
    val thermalState: String,
    val deviceModel: String,
    val osVersion: String,
    val charging: Boolean?,
    val hasBarometer: Boolean,
    val hasDeviceMotion: Boolean,
    val hasMagnetometer: Boolean,
    val preciseLocationAuthorized: Boolean,
)

/** The platform's device context, or null when unavailable. */
expect fun currentConnectionDeviceContext(preciseLocationAuthorized: Boolean): ConnectionDeviceContext?

/** True when precise (fine) location was granted; never prompts. */
expect fun hasPreciseLocationPermission(): Boolean

/**
 * One phone's versioned record of a connection (`sensor_observation` v3, 07 §6 G4-3; port of iOS
 * `ConnectionSensorObservation`). Snake_case JSON; every `*_ms` timeline value is relative to
 * `connection_moment`. Blocks Android cannot measure (heading, motion, activity, pedometer,
 * Bluetooth/ultrasonic traces, UWB, absolute altimeter accuracy) are omitted, never faked.
 */
object ConnectionSensorObservation {
    const val SCHEMA_VERSION: Int = 3

    /** Stays under the server's 64 KB cap. */
    const val MAXIMUM_PAYLOAD_BYTES: Int = 56 * 1024
    const val MAXIMUM_TRAIL: Int = 8
    const val MAXIMUM_BAROMETER_SAMPLES: Int = 12

    /**
     * @param includeLocation false when Location snap is off: no location-derived value is included.
     * @param sentAtMs this phone's clock when sent (`clock.sent_at`).
     */
    fun build(
        method: String,
        snapshot: ConnectionCaptureSnapshot,
        includeLocation: Boolean,
        device: ConnectionDeviceContext?,
        sentAtMs: Long,
        timeZoneOffsetSeconds: Int,
    ): JsonObject {
        val moment = snapshot.momentMs
        return buildJsonObject {
            put("schema_version", SCHEMA_VERSION)
            put("method", method)
            snapshot.startedAtMs?.let { put("captured_at", isoMillis(it)) }
            put("connection_moment", isoMillis(moment))
            snapshot.startedAtMs?.let { put("capture_duration_ms", snapshot.finishedAtMs - it) }
            val fix = snapshot.location
            if (includeLocation && fix != null) {
                put("location", locationBlock(fix, snapshot))
            }
            snapshot.barometer?.let { put("barometer", barometerBlock(it, snapshot)) }
            device?.let { put("device", deviceBlock(it)) }
            put(
                "clock",
                buildJsonObject {
                    put("sent_at", isoMillis(sentAtMs))
                    put("time_zone_offset_s", timeZoneOffsetSeconds)
                },
            )
        }
    }

    private fun locationBlock(
        fix: LocationFix,
        snapshot: ConnectionCaptureSnapshot,
    ): JsonObject {
        val moment = snapshot.momentMs
        return buildJsonObject {
            put("lat", fix.latitude)
            put("lon", fix.longitude)
            put("horizontal_accuracy_m", rounded(fix.horizontalAccuracyMeters, 2))
            fix.altitudeMslMeters?.let { put("altitude_msl_m", rounded(it, 2)) }
            fix.ellipsoidalAltitudeMeters?.let { put("ellipsoidal_altitude_m", rounded(it, 2)) }
            fix.verticalAccuracyMeters?.let { put("vertical_accuracy_m", rounded(it, 2)) }
            fix.speedMetersPerSecond?.let { put("speed_mps", rounded(it, 2)) }
            fix.speedAccuracyMetersPerSecond?.let { put("speed_accuracy_mps", rounded(it, 2)) }
            fix.courseDegrees?.let { put("course_deg", rounded(it, 1)) }
            fix.courseAccuracyDegrees?.let { put("course_accuracy_deg", rounded(it, 1)) }
            fix.floorLevel?.let { put("floor", it) }
            put("full_accuracy", fix.isFullAccuracy)
            put("observed_at", isoMillis(fix.observedAtMs))
            put("age_at_moment_ms", moment - fix.observedAtMs)
            fix.isSimulated?.let { put("simulated", it) }
            fix.isExternalAccessory?.let { put("external_accessory", it) }
            put("updates_seen", snapshot.locationUpdates)
            ConnectionLocationQuality.fused(snapshot.locationFixes, moment, snapshot.finishedAtMs)?.let { f ->
                put(
                    "fused",
                    buildJsonObject {
                        put("lat", f.latitude)
                        put("lon", f.longitude)
                        put("radius_m", rounded(f.radiusMeters, 2))
                        put("fix_count", f.fixCount)
                        put("span_ms", f.spanMs)
                    },
                )
            }
            val trail =
                snapshot.locationFixes
                    .sortedBy { abs(it.observedAtMs - moment) }
                    .take(MAXIMUM_TRAIL)
                    .sortedBy { it.observedAtMs }
            if (trail.isNotEmpty()) {
                put(
                    "trail",
                    buildJsonArray {
                        for (t in trail) {
                            add(
                                buildJsonObject {
                                    put("t_ms", t.observedAtMs - moment)
                                    put("lat", t.latitude)
                                    put("lon", t.longitude)
                                    put("accuracy_m", rounded(t.horizontalAccuracyMeters, 2))
                                    t.speedMetersPerSecond?.let { put("speed_mps", rounded(it, 2)) }
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun barometerBlock(
        reading: BarometerReading,
        snapshot: ConnectionCaptureSnapshot,
    ): JsonObject {
        val moment = snapshot.momentMs
        val first = snapshot.pressureSamples.firstOrNull()
        val samples =
            snapshot.pressureSamples
                .sortedBy { abs(it.observedAtMs - moment) }
                .take(MAXIMUM_BAROMETER_SAMPLES)
                .sortedBy { it.observedAtMs }
        return buildJsonObject {
            put("pressure_kpa", rounded(reading.pressureKpa, 3))
            reading.relativeAltitudeMeters?.let { put("relative_altitude_m", rounded(it, 2)) }
            put("observed_at", isoMillis(reading.observedAtMs))
            put("samples_collected", snapshot.pressureSamples.size)
            snapshot.barometerStartedAtMs?.let { put("started_t_ms", it - moment) }
            put(
                "samples",
                buildJsonArray {
                    for (s in samples) {
                        add(
                            buildJsonObject {
                                put("t_ms", s.observedAtMs - moment)
                                put("pressure_kpa", rounded(s.pressureHpa / 10.0, 3))
                                if (first != null) {
                                    val rel = pressureAltitudeMeters(s.pressureHpa) - pressureAltitudeMeters(first.pressureHpa)
                                    if (rel.isFinite()) put("relative_altitude_m", rounded(rel, 2))
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    private fun deviceBlock(device: ConnectionDeviceContext): JsonObject =
        buildJsonObject {
            device.batteryPercent?.let { put("battery_percent", it) }
            put("low_power_mode", device.lowPowerMode)
            put("thermal_state", device.thermalState)
            put("device_model", device.deviceModel)
            put("os_version", device.osVersion)
            put(
                "capabilities",
                buildJsonObject {
                    put("barometer", device.hasBarometer)
                    put("absolute_altitude", false)
                    put("device_motion", device.hasDeviceMotion)
                    put("magnetometer", device.hasMagnetometer)
                    put("uwb", false)
                    put("precise_location_authorized", device.preciseLocationAuthorized)
                },
            )
            device.charging?.let { put("charging", it) }
        }

    /**
     * The bounded wire value: if over [maximumBytes], drop (in order) motion samples (none on
     * Android yet), the barometer samples, then the location trail; null if still too large.
     */
    fun bounded(
        observation: JsonObject,
        maximumBytes: Int = MAXIMUM_PAYLOAD_BYTES,
    ): JsonObject? {
        val candidates =
            sequence {
                yield(observation)
                val noMotion = observation.withNested("motion", "samples", JsonArray(emptyList()))
                yield(noMotion)
                val noBaroSamples = noMotion.withNested("barometer", "samples", JsonArray(emptyList()))
                yield(noBaroSamples)
                yield(noBaroSamples.withoutNested("location", "trail"))
            }
        return candidates.firstOrNull { it.toString().encodeToByteArray().size <= maximumBytes }
    }

    private fun JsonObject.withNested(
        block: String,
        key: String,
        value: JsonArray,
    ): JsonObject {
        val inner = this[block] as? JsonObject ?: return this
        if (inner[key] == null) return this
        return JsonObject(this + (block to JsonObject(inner + (key to value))))
    }

    private fun JsonObject.withoutNested(
        block: String,
        key: String,
    ): JsonObject {
        val inner = this[block] as? JsonObject ?: return this
        return JsonObject(this + (block to JsonObject(inner - key)))
    }

    /** `connection_flow_events.capture_quality` aggregates (allowlisted keys; no coordinates). */
    fun captureQuality(
        method: String,
        snapshot: ConnectionCaptureSnapshot,
        includeLocation: Boolean,
    ): Map<String, JsonPrimitive> {
        val fix = snapshot.location.takeIf { includeLocation }
        val out =
            mutableMapOf(
                "location_available" to JsonPrimitive(fix != null),
                "barometer_available" to JsonPrimitive(false),
                "motion_available" to JsonPrimitive(false),
                "heading_available" to JsonPrimitive(false),
                "uwb_available" to JsonPrimitive(false),
                "activity_available" to JsonPrimitive(false),
                "pedometer_available" to JsonPrimitive(false),
                "connection_method" to JsonPrimitive(method),
                "location_accuracy_bucket" to
                    JsonPrimitive(fix?.let { ConnectionLocationQuality.tier(it.horizontalAccuracyMeters) } ?: "none"),
            )
        if (fix != null) {
            out["location_accuracy_m"] = JsonPrimitive(rounded(fix.horizontalAccuracyMeters, 2))
            out["location_full_accuracy"] = JsonPrimitive(fix.isFullAccuracy)
            out["location_update_count"] = JsonPrimitive(snapshot.locationUpdates)
            out["floor_available"] = JsonPrimitive(fix.floorLevel != null)
        }
        snapshot.startedAtMs?.let {
            val duration = (snapshot.finishedAtMs - it).coerceAtLeast(0)
            out["capture_duration_ms"] = JsonPrimitive(duration)
            if (fix != null) out["location_capture_ms"] = JsonPrimitive(duration)
        }
        out["sensor_failure"] = JsonPrimitive(if (fix == null) "location_unavailable" else "motion_unavailable")
        return out
    }
}

/**
 * Top-level quality keys for a connect body (07 §6 G4-2): the fix's `gps_*` keys (only when
 * location is included and the fix is ≤ 50 m), plus `barometric_pressure_kpa` and
 * `barometric_relative_altitude_m`. Accuracy/precision describe an absolute altimeter fix,
 * which Android does not have, so they are never sent.
 */
fun connectionQualityKeys(
    snapshot: ConnectionCaptureSnapshot?,
    includeLocation: Boolean,
): JsonObject =
    buildJsonObject {
        if (snapshot == null) return@buildJsonObject
        val fix = snapshot.location
        if (includeLocation && fix != null && ConnectionLocationQuality.isUseful(fix)) {
            fix.qualityColumns().forEach { (k, v) -> put(k, v) }
        }
        snapshot.barometer?.let { baro ->
            put("barometric_pressure_kpa", rounded(baro.pressureKpa, 3))
            baro.relativeAltitudeMeters?.let { put("barometric_relative_altitude_m", rounded(it, 2)) }
        }
    }
