# `telemetry/` — Friction telemetry & anonymized map analytics

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Click measures **product friction** — especially aimless map panning — to improve discovery UX. Telemetry is **aggregated, anonymized, and opt-in by session rules** before anything leaves the device.

---

## Purpose

The `telemetry/` package implements:

1. **On-device friction session tracking** — map pan counts, duration, QR fallbacks, user actions.
2. **Anonymized spatial bucketing** — coarse hexbin IDs, never raw GPS in payloads.
3. **Grass nudge UI state** — gentle prompt when users pan without acting (anti-doomscroll nudge).
4. **Background flush** — POST aggregated anomaly to click-web when session thresholds met.

Server persistence target: **`system_friction_logs`** table (via click-web `/api/telemetry/friction` BFF route).

---

## Architecture

```
MapScreen / MapViewModel
        │
        ├── TelemetryBatcher.beginMapSession(hexbinId)
        ├── TelemetryBatcher.recordMapPan()
        ├── TelemetryBatcher.recordActionTaken()  (beacon tap, connect, etc.)
        ├── TelemetryBatcher.recordQrFallback()
        └── TelemetryBatcher.endMapSession()

App lifecycle background
        │
        └── TelemetryBatcher.onAppBackgrounded()
                │
                ▼
        flushOnBackgroundIfNeeded()
                │
                ▼
        POST CLICK_WEB_BASE_URL/api/telemetry/friction
                │
                ▼
        system_friction_logs (server)
```

### `TelemetryBatcher` (singleton)

| State | Fields |
|-------|--------|
| `FrictionSession` | `sessionStartTimeMs`, `mapPanCount`, `qrFallbackCount`, `actionTakenCount`, `lastPanAtMs`, `hexbinId` |
| `FrictionUiState` | `session` + `showGrassNudge` |

**Thresholds:**

| Constant | Value | Meaning |
|----------|-------|---------|
| `ANOMALY_MIN_DURATION_SEC` | 30s | Minimum session length to flush |
| `GRASS_NUDGE_MIN_DURATION_SEC` | 240s (4 min) | Show grass nudge after idle panning |
| `ACTIVE_PAN_WINDOW_MS` | 45s | Recent pan required for nudge |

**Flush conditions (all required):**

- Session duration ≥ 30 seconds
- `mapPanCount > 0`
- App backgrounded or map session ended

**Payload (`FrictionAnomalyPayload`):**

```json
{
  "event": "map_friction_anomaly",
  "duration_sec": 120,
  "pan_count": 47,
  "action_taken": null,
  "hexbin_id": "hx_a1b2c3d4e5f6"
}
```

Posted with Supabase JWT Bearer to `{CLICK_WEB_BASE_URL}/api/telemetry/friction`.

### `AnonymizedHexbin`

```kotlin
object AnonymizedHexbin {
    fun fromCoordinates(latitude, longitude): String  // ~500m buckets
    const val UNKNOWN_CELL = "hx_unknown"
}
```

- Buckets via `floor(lat * 200)` / `floor(lon * 200)` → FNV-1a hash → `hx_{12 hex chars}`.
- Matches server-side `anonymized_cell_id` shape for vibe-radar aggregation.
- `(0,0)` and non-finite coords → `hx_unknown`.

`MapViewModel` calls `updateHexbinFromCoordinates` on location updates.

### Grass nudge

When user pans the map **>4 minutes** without `recordActionTaken()`, and panned within last 45s, `showGrassNudge = true`. UI dismisses via `dismissGrassNudge()` — aligns with anti-doomscroll product goal.

### `system_friction_logs`

Client does not write Supabase directly. The click-web BFF validates JWT, rate-limits, and inserts into **`system_friction_logs`** with:

- `event` type (`map_friction_anomaly`)
- `duration_sec`, `pan_count`
- `hexbin_id` (spatial bucket)
- `user_id` (server-side from token — not in client payload body)

This keeps PII handling server-controlled.

---

## Constraints

1. **No raw coordinates in HTTP body** — only `hexbin_id` from `AnonymizedHexbin`.
2. **No flush without auth** — missing JWT silently skips POST.
3. **Non-blocking** — all flush work on `Dispatchers.Default`; never block UI thread.
4. **Session reset after flush** — prevents duplicate posts for same session.
5. **Pan-only sessions** — zero pans → no anomaly (browsing list/map static view not penalized).
6. **Privacy review before new events** — extend `FrictionAnomalyPayload` only with anonymized fields.

---

## Related files

| Path | Role |
|------|------|
| `telemetry/TelemetryBatcher.kt` | Session tracking + HTTP flush |
| `telemetry/AnonymizedHexbin.kt` | Spatial bucketing |
| `viewmodel/MapViewModel.kt` | Pan/hexbin hooks |
| `ui/screens/MapScreen.kt` | Grass nudge UI consumption |
| `data/api/ApiConfig.kt` | `CLICK_WEB_BASE_URL` |
| `data/SupabaseConfig.kt` | JWT for Bearer header |
| `util/chatMediaDispatcher.kt` | IO dispatcher for POST |
| `util/redactedRestMessage.kt` | Safe error logging |
| `network/NetworkConnectivityMonitor.kt` | Offline → POST fails silently |

---

## What Click Users Experience

Telemetry is invisible to most users except the optional grass nudge.

### Connect in person (Tri-Factor)
`recordActionTaken()` when user completes handshake from map context.

### Scan QR
`recordQrFallback()` when user switches from map discovery to QR scan — signals friction with map discovery.

### Group connect / Chat / Media / Reactions / Typing / Calls
Not instrumented in telemetry module today.

### Memory Capsules
Unrelated.

### 48-hour gentle archive
Unrelated.

### Connection map & timeline
Map is the instrumented surface.

### Rate the vibe
Unrelated.

### QR identity card
QR fallback metric when chosen over map panning.

### Availability intents / Match alerts
Unrelated.

### Community Hubs / Map beacons
Tapping beacon/connect on map → `recordActionTaken()` — clears grass nudge.

### Global search
Leaving map for search may end map session → background flush.

### Core connections / Collaboration / Ghost mode
Ghost mode stops location updates → hexbin may stay `hx_unknown`.

### Block & report / Profile / Onboarding / Auth
Unrelated.

### Push notifications / Deep links / Web dashboard
Unrelated.

### Business insights
Aggregated `system_friction_logs` + hexbin powers **business map friction dashboards** on web — merchants see neighborhood-level discovery pain, not individual user tracks.

### Event reminders
Unrelated.

### Achievements & stats
Grass nudge is anti-achievement — encourages leaving the map to live offline.

### Anti-doomscroll product tie-in

The **4-minute grass nudge** explicitly supports Click's mission: if you're panning endlessly without connecting, the app gently suggests putting the phone down and engaging in the room — telemetry backs whether map UX changes actually reduce friction.
