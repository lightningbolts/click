# Encounter Module

## Module Purpose

The `encounter` package manages the **offline-first queue and enrichment pipeline** for Tri-Factor proximity handshakes. When two or more devices co-present evidence (ultrasonic tokens, BLE advertisements, sensor context) but network bind is unavailable, encounters are persisted locally and synced when connectivity returns. The package also powers **Encounter Tether** — a post-connect compass UI for finding nearby peers during multi-tap sessions.

---

## Architecture

### PendingEncounterQueue

`PendingEncounterQueue` mirrors the architectural seam of `data/chat/PendingMessageQueue`:

```
Proximity handshake completes locally
        │
        ▼
PendingEncounterQueue.enqueue(myToken, heardTokens, detectedDevices, location, sensors, vibe)
        │
        ├─ Dedup: same tokens within 60s window → return existing draft
        ├─ Cap: MAX_QUEUE_SIZE = 32 (drop oldest)
        └─ Persist: TokenStorage.savePendingProximityHandshakeQueue(JSON)
        │
        ▼
AppDataManager.startPendingConnectionSync() — retry loop when online
        │
        ▼
ConnectionRepository.bindProximityConnection() → server encounter row
```

**Queue item type:** `PendingHandshake` (`LocalStateModels.kt`) containing:

- `myToken`, `heardTokens`, `detectedDevices`
- `location` (`ProximityHandshakeLocationSnapshot`)
- `hardwareVibe` (`HardwareVibeSnapshot`)
- `noiseLevel`, `exactNoiseLevelDb`, `heightCategory`, `exactBarometricElevationM`
- `contextTags`

Thread-safe via `Mutex`; exposes `StateFlow<List<PendingHandshake>>` for UI badges.

### ConnectionEncounter model

`data/models/ConnectionEncounter.kt` is the **server-side encounter row** shape:

| Column | Type | Source |
|--------|------|--------|
| `connection_id` | UUID | Bind result |
| `encountered_at` | ISO timestamp | Server clock |
| `location_name` / `display_location` | text | Reverse geocode |
| `semantic_location` | JSON | Places API |
| `gps_lat` / `gps_lon` | double | Device GPS |
| `weather_snapshot` | JSON | Open-Meteo at connect time |
| `exact_noise_level_db` | double | AmbientNoiseMonitor |
| `exact_barometric_elevation_m` | double | BarometricHeightMonitor |
| `lux_level` / `motion_variance` | double | HardwareVibeMonitor |
| `context_tags` | text[] | User-selected tags |

`mergeRichestEncounterEvents()` collapses duplicate rows per `connectionId|encounteredAt` keeping the richest sensor payload. `toMemoryCapsule()` projects into UI capsules.

### Encounter debouncing

Duplicate suppression happens at multiple layers:

1. **Queue dedup** — `PendingEncounterQueue.enqueue()` rejects identical `myToken + heardTokens + detectedDevices` within **60 seconds**.
2. **Foreground recovery debounce** — `AppDataManager.FOREGROUND_RECOVERY_DEBOUNCE_MS = 900` prevents sync storms on app resume.
3. **Proximity broadcast stagger** — `ConnectionViewModel` delays ultrasonic broadcast `Random(0, 400ms)` so nearby devices don't talk over each other.
4. **Map beacon fetch debounce** — `MapViewModel.scheduleBeaconFetchForBounds(debounceMs = 400)` for viewport queries.

### Enrichment pipeline handoff

```
ConnectionViewModel.startProximityHandshake()
    │
    ├─ Parallel: GPS, captureConnectionSensorContext(), HardwareVibeMonitor.takeSnapshot()
    ├─ Parallel: ultrasonic listen + BLE scan + broadcast
    │
    ▼
ConnectionState.TaggingContext (optional user tags via ContextTagTaxonomy)
    │
    ▼
bindProximityConnection Edge Function
    ├─ is_new_connection = true  → new Connection + ConnectionEncounter rows
    └─ is_new_connection = false → CollaborationSessionManager.activate()
    │
    ▼
Server enrichment (async):
    ├─ Reverse geocode → location_name
    ├─ Open-Meteo → weather_snapshot
    └─ mergeRichestEncounterEvents on fetch
    │
    ▼
Client: ConnectionEncounter.toMemoryCapsule() → timeline / map UI
```

If bind fails offline, the full payload stays in `PendingEncounterQueue` until `AppDataManager` sync loop succeeds.

### Encounter Tether (companion components)

| File | Role |
|------|------|
| `EncounterTetherManager` | Tracks active multi-tap session peers + RSSI hints |
| `EncounterTetherModels.kt` | Tether state DTOs |
| `TetherCompass.kt` | Compass bearing UI math |
| `EncounterTetherWidgetBridge.kt` | iOS widget / Live Activity bridge |
| `RecentEncounter.kt` | Short-term local encounter memory |

`GlobalTetherOverlay` in `App.kt` surfaces the compass when tether is active.

---

## Constraints

- **Evidence required** — `enqueue()` throws if both `heardTokens` and `detectedDevices` are empty.
- **Queue cap** — 32 items max; oldest dropped under pressure.
- **Process-death safe** — queue survives via `TokenStorage`, not in-memory only.
- **60s dedup window** — rapid re-taps within one minute collapse to one queue entry.
- **Enrichment is best-effort** — missing GPS or sensor opt-out still allows bind with partial payload.
- **Tether is session-scoped** — cleared when multi-tap session ends or app backgrounds.

---

## Related Files

| File | Role |
|------|------|
| `encounter/PendingEncounterQueue.kt` | Offline handshake queue |
| `encounter/EncounterTetherManager.kt` | Multi-tap compass session |
| `encounter/EncounterTetherModels.kt` | Tether DTOs |
| `encounter/TetherCompass.kt` | Bearing calculations |
| `encounter/RecentEncounter.kt` | Local recent encounter cache |
| `data/models/ConnectionEncounter.kt` | Server encounter entity |
| `data/models/LocalStateModels.kt` | `PendingHandshake` |
| `data/models/MemoryCapsule.kt` | UI capsule projection |
| `viewmodel/ConnectionViewModel.kt` | Handshake orchestration |
| `data/AppDataManager.kt` | Pending sync loop |
| `data/repository/ConnectionRepository.kt` | `bindProximityConnection` |
| `sensors/ConnectionSensorMonitors.kt` | Sensor capture |
| `supabase/functions/bind-proximity-connection/index.ts` | Server bind + enrichment |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module captures and enriches.

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
