# `network/` — Connectivity observation & HTTP client boundary

> **Anti-doomscrolling · Stop scrolling, start living.**  
> The `network/` package defines **how the app knows it is online** and documents the **Ktor HTTP layer** that talks to Click's Next.js companion (`click-web`) and Supabase.

---

## Purpose

This folder contains the **platform connectivity monitor**. The heavier HTTP clients live in `data/api/` by historical layout — they are documented here because they form the app's **network boundary** together with `network/`.

| Package / path | Responsibility |
|----------------|----------------|
| `network/NetworkConnectivityMonitor.kt` | `expect`/`actual` online/offline `StateFlow` |
| `data/api/ApiClient.kt` | Primary Ktor client — auth, profiles, connections, beacons, hubs, telemetry |
| `data/api/ChatApiClient.kt` | Chat media upload/download, click-web message tunnel |
| `data/api/ApiConfig.kt` | Environment URLs including `CLICK_WEB_BASE_URL` |
| `qr/QRModels.kt` | Canonical `CLICK_WEB_BASE_URL` constant |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  ViewModels / AppDataManager / TelemetryBatcher             │
└────────────┬───────────────────────────────┬────────────────┘
             │                               │
             ▼                               ▼
┌────────────────────────┐    ┌──────────────────────────────┐
│ NetworkConnectivity    │    │  data/api/                    │
│ Monitor (expect/actual)│    │  ApiClient (Ktor + Bearer)    │
│  isOnline: StateFlow   │    │  ChatApiClient (Ktor)         │
└────────────────────────┘    └──────────────┬───────────────┘
             │                               │
             ▼                               ▼
    Android: ConnectivityManager      ApiConfig.CLICK_WEB_BASE_URL (click-web)
    iOS: NWPathMonitor                Supabase REST (via supabase-kt)
```

### `NetworkConnectivityMonitor`

```kotlin
expect class NetworkConnectivityMonitor() {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}
```

- Started from `AppDataManager` and `ChatViewModel` on boot.
- On **online transition**, triggers pending queue flushes (messages, proximity handshakes, refresh).
- Uses `util/isOfflineNetworkFailure()` semantics consistently with HTTP error handling.

**Android actual** — `ConnectivityManager` network callback.  
**iOS actual** — `NWPathMonitor` on background queue.

### `ApiConfig`

| Constant | Purpose |
|----------|---------|
| `CLICK_WEB_BASE_URL` | Delegates to `qr.CLICK_WEB_BASE_URL` (Next.js companion — sole HTTP API base) |

Default companion URL (from `QRModels.kt`):

```kotlin
const val CLICK_WEB_BASE_URL = "https://joinclick.co"
```

### `ApiClient` (Ktor)

Singleton-style HTTP client with:

- **Bearer auth** — Supabase JWT from `TokenStorage` / session refresh.
- **JSON** — `ContentNegotiation` + kotlinx.serialization.
- **Retry** — selective delay/retry on transient failures.

Representative endpoints consumed by the app:

| Endpoint area | Examples |
|---------------|----------|
| Auth / session | `GET /api/ping` secure ping (Supabase auth is client-side) |
| Profiles | `GET /api/users/{id}/profile`, patch profile |
| Connections | Proximity bind, timeline, block/report |
| Map | Beacon CRUD, RSVP, community hub nearby |
| Business | Waitlist, venue hub setup |
| Telemetry | Friction anomaly POST (also used by `TelemetryBatcher`) |

### `ChatApiClient` (Ktor)

Dedicated client for **chat-heavy** operations:

| Capability | Notes |
|------------|-------|
| Encrypted media multipart upload | `filename="encrypted_media.bin"` — Ktor-safe disposition headers |
| Public URL download | `downloadUrlBytes` for Supabase Storage public chat-media URLs |
| Click-web message sync | BFF tunnel for message operations where direct Supabase is insufficient |
| Bearer auth | Normalized `Bearer ` prefix handling |

All routes use `CLICK_WEB_BASE_URL` (the legacy Flask `BASE_URL` was removed).

### `CLICK_WEB_BASE_URL` usage map

| Feature | Route / usage |
|---------|---------------|
| QR identity card | `/c/{userId}` Universal Links |
| Secure API tunnel | `/api/ping`, profile BFF routes |
| Friction telemetry | `/api/telemetry/friction` |
| Unified search (messages) | `GET /api/chat/search` |
| Hub chat hydrate | `GET /api/hub/messages?hubId=` |
| App Clip CTA | App Store URL alongside web profile load |
| Web dashboard | Opens base URL in browser from Settings |

---

## Constraints

1. **Never log raw JWTs or apikey headers** — use `util/redactedRestMessage()`.
2. **Offline-first** — HTTP failures classified by `isOfflineNetworkFailure()` must not wipe `AppDataManager` caches.
3. **Ghost mode** — `AppDataManager` blocks background refresh; monitors may still report online but sync is gated upstream.
4. **No Flask / LAN API bases** — never reintroduce `localhost:5000` or hardcoded developer LAN IPs; companion traffic uses `CLICK_WEB_BASE_URL` only.
5. **Multipart compatibility** — `ChatApiClient` disposition headers must stay Ktor/Node-compatible (documented in source).
6. **CLICK_WEB_BASE_URL single source** — change only in `QRModels.kt`; `ApiConfig` re-exports.

---

## Related files

| Path | Role |
|------|------|
| `network/NetworkConnectivityMonitor.kt` | expect declaration |
| `androidMain/.../NetworkConnectivityMonitor.android.kt` | Android actual |
| `iosMain/.../NetworkConnectivityMonitor.ios.kt` | iOS actual |
| `data/api/ApiClient.kt` | Main REST client (~1600 lines) |
| `data/api/ChatApiClient.kt` | Chat media + message tunnel |
| `data/api/ApiConfig.kt` | URL configuration |
| `data/api/WaitlistApiClient.kt` | Waitlist-specific client |
| `qr/QRModels.kt` | `CLICK_WEB_BASE_URL`, QR payload models |
| `data/SupabaseConfig.kt` | Supabase Realtime (parallel to HTTP) |
| `util/NetworkFailureUtil.kt` | Throwable → offline classification |
| `telemetry/TelemetryBatcher.kt` | Friction POST client |

---

## What Click Users Experience

Network layer enables every online feature; users see graceful degradation when offline.

### Connect in person (Tri-Factor)
Proximity bind `POST` to click-web `/api/connections/proximity`; offline queue in `TokenStorage` syncs on `NetworkConnectivityMonitor` online.

### Scan QR
Token redemption HTTP to BFF; QR fallback increments `TelemetryBatcher.recordQrFallback()`.

### Group connect (Multi-Tap)
Edge function validation over network; 202 pending match when peer offline.

### Private encrypted chat
Realtime over Supabase WebSocket; message/media HTTP via `ChatApiClient` when needed.

### Send photos / files / voice notes
Encrypted multipart upload through `ChatApiClient`; download from Storage URLs.

### Emoji reactions / Typing & read receipts
Supabase Realtime channels (not Ktor) — still gated on connectivity for initial hydrate.

### Memory Capsules
Sensor metadata uploaded with connection bind payload.

### 48-hour gentle archive
Server-side sweep + client refresh over HTTP.

### Connection map & timeline
`ApiClient` profile timeline fetch.

### Rate the vibe
Connection status patch over REST/Supabase.

### QR identity card
Encodes `CLICK_WEB_BASE_URL/c/{userId}`.

### Availability intents
Intent rows synced via Supabase/REST.

### Match alerts
Push + realtime; bind endpoint returns match notifications.

### Community Hubs / Map beacons
`ApiClient` hub/beacon endpoints; map refresh on reconnect.

### Global search
Local first; `SupabaseRepository.unifiedSearch` supplement when online.

### Core connections
`connection_core` pin API via repository.

### Collaboration sessions & disposable rolls
Bind-proximity response activates session — network required for bump detection.

### Ghost mode
Connectivity monitor runs; `AppDataManager` suppresses sync anyway.

### Block & report
`user_blocks` insert via API.

### Profile & interests
`GET/PATCH /api/users/{id}/profile` BFF routes.

### Onboarding / Google-email auth
Supabase Auth HTTP (SDK-managed).

### Push notifications
FCM/APNs token registration to backend.

### Deep links & App Clip
`AppClipHandshakeScreen` loads profile via `ApiClient` from invocation URL on `CLICK_WEB_BASE_URL`.

### Web dashboard
Browser opens `CLICK_WEB_BASE_URL` with user session on web.

### Business insights
B2B waitlist + venue APIs.

### Event reminders
Beacon/event data fetched over network; reminders computed locally in `events/`.

### Achievements & stats
Stats from profile/connection count APIs on refresh.
