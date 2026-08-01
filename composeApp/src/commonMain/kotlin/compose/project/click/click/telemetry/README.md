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
5. **Connection-flow telemetry** — proximity handshake funnel events (separate from map friction).

Server persistence targets:

- **`system_friction_logs`** via `/api/telemetry/friction` (`map_friction_anomaly` only).
- **`connection_flow_events`** via `/api/telemetry/connection-flow` (proximity handshake allowlist).
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

When user pans the map **>4 minutes** without `recordActionTaken()`, and panned within last 45s, `showGrassNudge = true`. UI dismisses via `dismissGrassNudge()` which **sticks for the rest of the map session** (survives `refreshUiClock` / pan updates) until `endMapSession` / `resetSession` — aligns with anti-doomscroll product goal.

### `system_friction_logs`

Client does not write Supabase directly. The click-web BFF validates JWT, rate-limits, and inserts into **`system_friction_logs`** with:

- `event` type (`map_friction_anomaly`)
- `duration_sec`, `pan_count`
- `hexbin_id` (spatial bucket)
- `user_id` (server-side from token — not in client payload body)

This keeps PII handling server-controlled.

---

## Constraints

1. **No raw coordinates in HTTP body** — only `hexbin_id` from `AnonymizedHexbin` (friction) or omit location entirely (connection-flow).
2. **No flush without auth** — missing JWT silently skips POST.
3. **Non-blocking** — all flush work on `Dispatchers.Default`; never block UI thread.
4. **Session reset after flush** — prevents duplicate friction posts for same session.
5. **Pan-only sessions** — zero pans → no anomaly (browsing list/map static view not penalized).
6. **Privacy review before new events** — extend payloads only with anonymized fields; keep connection-flow allowlist separate from `map_friction_anomaly`.
7. **Do not mix streams** — connection-flow events must never write to `system_friction_logs`.

---

## Related files

| Path | Role |
|------|------|
| `telemetry/TelemetryBatcher.kt` | Map friction session tracking + HTTP flush |
| `telemetry/ConnectionFlowTelemetry.kt` | Proximity handshake funnel events |
| `telemetry/AnonymizedHexbin.kt` | Spatial bucketing |
| `viewmodel/MapViewModel.kt` | Pan/hexbin hooks |
| `viewmodel/ConnectionViewModel.kt` | Handshake funnel hooks |
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

### Connection-flow telemetry (`ConnectionFlowTelemetry`)

Proximity handshake funnel — **do not mix** into `map_friction_anomaly` / `system_friction_logs`.

| Piece | Role |
|-------|------|
| `ConnectionFlowTelemetry.kt` | Fire-and-forget JWT Bearer POST; ~10% sample on success-path events |
| `POST /api/telemetry/connection-flow` | Allowlisted ingest → `connection_flow_events` (60/min/user in-memory rate limit) |
| `lib/server/telemetry/connectionFlowEvents.ts` | Shared allowlist + server emit (`emitProximityAtEventOutcome`) |
| `ConnectionViewModel` | Hooks: started, matched / awaiting_selection / pending / offline_queued / failed |
| `bindProximityHandshake` / `confirmProximitySelection` | Server emits `proximity_at_event_attached` / `_skipped` on live-event resolve |

**Always emit (client):** `started`, `awaiting_selection`, `failed`, `host_selection_abandoned`, `reconnect_rate_limited`, recovery timeouts/incomplete, clique blocked, `proximity_at_event_skipped`.

**Sampled (~10% client):** `matched`, `pending`, `offline_queued`, `host_selection_confirmed`, reconnect saved, recovery success, clique created, `proximity_at_event_attached`.

**Server emits (no client sampling):** at-event attached/skipped are also written directly from proximity encounter paths with skip reasons `missing_gps`, `insufficient_participants`, or `no_live_event_match` (includes RSVP-without-active-check-in and out-of-fence cases — attachment requires RSVP **and** active check-in for all participants).

**Payload fields only:** `event`, `peer_count?`, `is_group?`, `is_reconnect?`, `selected_count?`, `candidate_count?`, `reason?` — no user ids, no raw GPS.

**Sampling note:** Client success-path sampling (~10% via `SUCCESS_SAMPLE_RATE`) reduces volume; failures, `awaiting_selection`, and selection abandons always leave the device. Rate limit on the BFF is per-process only (not shared across serverless instances).

### Event reminders
Unrelated.

### Achievements & stats
Grass nudge is anti-achievement — encourages leaving the map to live offline.

### Anti-doomscroll product tie-in

The **4-minute grass nudge** explicitly supports Click's mission: if you're panning endlessly without connecting, the app gently suggests putting the phone down and engaging in the room — telemetry backs whether map UX changes actually reduce friction.
