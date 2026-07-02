# Sensors Module

## Module Purpose

The `sensors` package captures **ambient environmental context** at the moment of a connection encounter. Readings enrich Memory Capsules and `connection_encounters` rows with objective telemetry: noise level, barometric elevation, motion variance, and ambient light (lux). Monitors use Kotlin Multiplatform **expect/actual** for platform hardware access and are wired into the Tri-Factor handshake via `captureConnectionSensorContext()` in `ConnectionViewModel` and `App.kt`.

---

## Architecture

### Monitor components

| Monitor | Interface | Platform actual |
|---------|-----------|-----------------|
| `AmbientNoiseMonitor` | `sampleNoiseReading(durationMs)` → `AmbientNoiseSample` | `androidMain` / `iosMain` microphone sampling |
| `BarometricHeightMonitor` | `sampleHeightReading(durationMs, lat, lon)` → `BarometricHeightSample` | Barometer + optional Open-Meteo MSL calibration |
| `HardwareVibeMonitor` | `takeSnapshot()` → `HardwareVibeSnapshot` | Accelerometer, light sensor, compass, battery |

**Provider pattern:** `AmbientNoiseMonitorProvider` and `BarometricHeightMonitorProvider` expose monitors via `CompositionLocal` in Compose. `App.kt` wraps the tree in `ConnectionSensorMonitorsProvider`.

**Calibration layer:** `CalibratedBarometricHeightMonitor` wraps the platform monitor with `OpenMeteoWeatherService` for sea-level pressure, producing `isCalibrated` elevation vs ISA fallback (1013.25 hPa).

### Memory Capsule sensor fields

Encounter rows and `MemoryCapsule` persist these exact telemetry keys:

| Field | Source | Description |
|-------|--------|-------------|
| `exact_noise_level_db` | `AmbientNoiseMonitor` | Calibrated approximate dB (350ms sample at connect time) |
| `noise_level` / `noiseLevelCategory` | Derived | `VERY_QUIET` … `VERY_LOUD` tier from dB thresholds |
| `exact_barometric_elevation_m` | `BarometricHeightMonitor` | MSL-calibrated elevation in meters |
| `elevation_category` / `heightCategory` | Derived | `BELOW_GROUND`, `GROUND_LEVEL`, `ELEVATED`, `HIGH_RISE` |
| `motion_variance` | `HardwareVibeMonitor` | Accelerometer variance over ~500ms window |
| `lux_level` | `HardwareVibeMonitor` | True lux (Android) or screen-brightness proxy 0–100 (iOS) |

Additional vibe fields on `ConnectionEncounter`: `compass_azimuth`, `battery_level`.

`ConnectionEncounter.toMemoryCapsule()` maps DB rows into UI-facing `MemoryCapsule` objects.

### `captureConnectionSensorContext`

```kotlin
suspend fun captureConnectionSensorContext(
    ambientNoiseMonitor: AmbientNoiseMonitor,
    barometricHeightMonitor: BarometricHeightMonitor,
    ambientNoiseOptIn: Boolean,
    barometricContextOptIn: Boolean,
    latitude: Double? = null,
    longitude: Double? = null,
): ConnectionSensorContext
```

- Short sample windows: `CONNECTION_CONTEXT_SENSOR_NOISE_SAMPLE_MS = 350`, `BAROMETRIC = 350`
- Noise and barometric samples run **in parallel** via `coroutineScope { async }`
- Respects user opt-in flags from `TokenStorage.getAmbientNoiseOptIn()` / `getBarometricContextOptIn()` (default `true`)
- Returns `ConnectionSensorContext` with categories + exact values

Called from:
- `ConnectionViewModel` during Tri-Factor proximity handshake (parallel with GPS + `HardwareVibeMonitor`)
- `App.kt` QR scan and NFC connection flows
- `NfcScreen.kt` tap-to-connect

### `captureConnectionSensorContext` in ConnectionViewModel

During `startProximityHandshake()`:

1. Location deferred fetch (if `shouldCaptureLocationAtTap()`)
2. **Sensor deferred** — `captureConnectionSensorContext()` with coordinates from location
3. **Vibe deferred** — `HardwareVibeMonitor().takeSnapshot()` on `Dispatchers.Default`
4. Ultrasonic listen/broadcast runs concurrently
5. Results enqueue to `PendingEncounterQueue` or bind immediately with full enrichment payload

### expect/actual hardware access

| File | Platform |
|------|----------|
| `sensors/AmbientNoiseMonitor.kt` | common interface + `NoOpAmbientNoiseMonitor` |
| `androidMain/.../AmbientNoiseMonitor.android.kt` | `AudioRecord` RMS → dB |
| `iosMain/.../AmbientNoiseMonitor.ios.kt` | `AVAudioRecorder` metering |
| `sensors/BarometricHeightMonitor.kt` | common interface + `NoOpBarometricHeightMonitor` |
| `androidMain/.../BarometricHeightMonitor.android.kt` | `SensorManager.TYPE_PRESSURE` |
| `iosMain/.../BarometricHeightMonitor.ios.kt` | `CMAltimeter` + background caching |
| `sensors/HardwareVibeMonitor.kt` | `expect class` |
| `androidMain/.../HardwareVibeMonitor.android.kt` | SensorManager + light sensor |
| `iosMain/.../HardwareVibeMonitor.ios.kt` | CoreMotion + brightness proxy |

`rememberAmbientNoiseMonitor()` / `rememberBarometricHeightMonitor()` are `@Composable` expect functions that select platform implementations.

### Noise tier thresholds

`noiseLevelCategoryFromApproximateDb()`:

- `< 35 dB` → VERY_QUIET
- `< 55` → QUIET
- `< 75` → MODERATE
- `< 90` → LOUD
- `≥ 90` → VERY_LOUD

### Height tier thresholds

`deriveHeightCategory(altitudeMeters)`:

- `< -3 m` → BELOW_GROUND
- `< 8 m` → GROUND_LEVEL
- `< 35 m` → ELEVATED
- `≥ 35 m` → HIGH_RISE

---

## Constraints

- **Opt-in required** — users can disable ambient noise / barometric enrichment in Settings; monitors return null when opted out.
- **Microphone permission** — noise sampling requires runtime permission; `hasPermission` gates sampling.
- **Short sample windows at connect** — 350ms samples keep "Sparking a new connection" UI responsive; settings flows may use longer defaults (2s noise).
- **iOS lux proxy** — `lux_level` on iOS is not true ambient lux; documented as brightness proxy.
- **Barometric calibration** — without Open-Meteo MSL, `barometricCalibrated = false` and ISA fallback is used.
- **No PII in sensor payloads** — only numeric telemetry tiers and aggregates are stored.

---

## Related Files

| File | Role |
|------|------|
| `sensors/ConnectionSensorMonitors.kt` | `captureConnectionSensorContext`, providers |
| `sensors/AmbientNoiseMonitor.kt` | Noise interface + tier mapping |
| `sensors/BarometricHeightMonitor.kt` | Barometric interface |
| `sensors/CalibratedBarometricHeightMonitor.kt` | Open-Meteo calibration wrapper |
| `sensors/HardwareVibeMonitor.kt` | expect vibe snapshot |
| `sensors/HardwareVibeSnapshot.kt` | lux, motion, compass, battery DTO |
| `sensors/EncounterSensorJson.kt` | Wire JSON helpers |
| `data/models/MemoryCapsule.kt` | Capsule model with sensor fields |
| `data/models/ConnectionEncounter.kt` | DB encounter row |
| `viewmodel/ConnectionViewModel.kt` | Handshake sensor orchestration |
| `encounter/PendingEncounterQueue.kt` | Offline queue with sensor fields |
| `ui/components/ConnectionContextSheet.kt` | User-facing enrichment copy |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module enriches.

### Connection & Discovery
- **Connect in person (Tri-Factor)** — Bump phones using ultrasonic audio tokens + BLE + sensor context to verify co-presence.
- **Scan QR** — Scan a peer's QR code to initiate a connection handshake.
- **Group connect (Multi-Tap)** — Several people tap together to form a verified group ("click").
- **QR identity card** — Share your personal QR / link so others can connect without bumping.
- **Community Hubs** — Join ephemeral venue chats tied to a place.
- **Map beacons** — Discover people and events pinned on the social map.
- **Match alerts** — Get notified when availability and interests align with someone nearby.
- **Availability intents** — Signal when you're free this week; others can lock overlapping gaps.
- **Core connections** — Pin your most important people; they stay visible even in ghost mode on the map.

### Messaging & Calls
- **Private encrypted chat** — End-to-end encrypted direct and group threads.
- **Send photos/files/voice notes** — Rich media in chat with encrypted upload.
- **Emoji reactions** — React to individual messages.
- **Typing & read receipts** — Live presence indicators in conversations.
- **Voice & video calls** — In-app WebRTC calls with push-wake on incoming rings.

### Memory & Context
- **Memory Capsules** — Rich encounter records: place, weather, noise, elevation, motion, lux.
- **48-hour gentle archive** — Connections softly archive after 48 hours unless both parties continue.
- **Connection map & timeline** — See where and when you met someone on a map and timeline.
- **Rate the vibe** — Post-connection sentiment feedback.

### Collaboration
- **Collaboration sessions & disposable rolls** — Re-bump existing friends to open a time-locked Disposable Roll camera window and squad map drops.

### Privacy & Safety
- **Ghost mode** — Pause location sharing and background sync for a private session.
- **Block & report** — Block users and report abusive behavior.

### Profile & Account
- **Profile & interests** — Avatar, name, birthday, and interest tags power discovery and matching.
- **Onboarding** — Welcome → interests → avatar flow for new accounts.
- **Google/email auth** — Sign in with Google OAuth or email/password via Supabase Auth.
- **Push notifications** — Message, call, archive, and reveal alerts.
- **Deep links & App Clip** — Open connections via `https://click-us.vercel.app/c/{id}` or `click://` URLs.
- **Web dashboard** — Manage connections and insights from the browser.
- **Business insights** — Opt-in analytics for venue/business partners.
- **Event reminders** — Day-of and one-hour-before beacon reminders.
- **Achievements & stats** — Connection streaks, stats, and milestones.

### Discovery & Search
- **Global search** — Search people, chats, hubs, and map content from one entry point.
