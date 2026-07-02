# Location & Progressive GPS (`utils`)

> Colocated documentation for `LocationService`, `ProgressiveLocationSession`, and hub geofence helpers.  
> Sourced from DeepWiki `lightningbolts/click` — Location Services (indexed July 1, 2026).

---

## Module Purpose

This package owns **high-accuracy location acquisition** used across Click's proximity mesh, QR proximity registration, Community Hub geofence checks, and Memory Capsule enrichment. Location is never the sole proof of co-presence—it is the **third factor** in the Tri-Factor Handshake alongside BLE and 18.5 kHz ultrasonic audio.

| Consumer | Usage |
|----------|-------|
| `NfcScreen` / `ConnectionViewModel` | `getHighAccuracyLocation(4000L)` GPS warm-up during tap-to-connect |
| `QrCodeView` | Optional initiator GPS registration for QR proximity scoring (+15 confidence when ≤15 m) |
| `ProgressiveLocationSession` | Refines accuracy over the handshake window instead of a single snapshot |
| `HubGatekeeperLocation` | Client-side helpers for hub join flows that require live coordinates |

---

## Architecture

### `LocationService` (expect/actual)

| Source set | Implementation |
|------------|----------------|
| `commonMain` | `expect` interface: permission state, `getHighAccuracyLocation(timeoutMs)`, last-known fallback |
| `androidMain` | Fused Location Provider / `LocationManager` with runtime permission checks |
| `iosMain` | Core Location with `CLLocationManager`, background modes when entitled |

**Initialization:** `initLocationService(context)` is called from `MainActivity` on Android; iOS bridges through Kotlin `actual` with permission requesters in `ui/utils/`.

### `ProgressiveLocationSession`

Samples location repeatedly during an active handshake and returns the **best accuracy fix** within the budget window. This avoids binding on a stale coarse cell-tower fix when the user is still indoors.

### Permission display state

`LocationPermissionDisplayState` maps OS permission enums to user-facing copy for settings sheets and onboarding nudges—keeping permission UX in `commonMain` while platform enums stay in `actual` layers.

---

## KMP `expect`/`actual` Rules

- **Keep in `commonMain`:** timeout budgets (e.g. 4000 ms warm-up), progressive session orchestration, permission *display* mapping.
- **Platform `actual` only:** `CLLocationManager`, Android fused provider, background location capability checks.
- **Never block UI thread:** all location reads are suspend/async; handshake screens show loading state while GPS warms.

---

## Proximity & Server Scoring

Server-side `bind-proximity-connection` awards **+15 proximity confidence** when Haversine distance between peers is **≤ 15 m**. Client must send `gps_lat` / `gps_lon` (or legacy `latitude` / `longitude`) on bind POST. Simulator builds may pass `simulator_mock` for QA.

Encounter debouncing: crossings within **50 m** and the same **12-hour block** append an **"Extended Hangout"** tag instead of duplicating encounter rows.

---

## Related Files

| File | Role |
|------|------|
| `utils/LocationService.kt` | `expect` contract |
| `utils/ProgressiveLocationSession.kt` | Multi-sample refinement |
| `utils/LocationPermissionDisplayState.kt` | Permission UX mapping |
| `utils/HubGatekeeperLocation.kt` | Hub coordinate helpers |
| `utils/ImageUtils.kt` | Image downscale helpers (adjacent media utilities) |
| `androidMain/.../LocationService.android.kt` | Android `actual` |
| `iosMain/.../LocationService.ios.kt` | iOS `actual` |
| `proximity/README.md` | Tri-Factor BLE + ultrasonic context |
| `click-web/lib/location/README.md` | Server Haversine / hub geofence |

---

## What Click Users Experience

Click is built around **real-world presence**—location powers features users feel but rarely think about:

- **Connect in person (Tri-Factor):** Your phone quietly confirms you're in the same room as someone else using Bluetooth, inaudible sound, and GPS—not just "nearby on the internet."
- **Scan a QR code:** Point your camera at someone's Click QR to connect instantly.
- **Group connect (Multi-Tap):** Three or more people can connect at once and land in a verified group chat.
- **Private encrypted chat:** Messages are end-to-end encrypted—only you and your connection can read them.
- **Send photos, files & voice notes:** Share media in chat; files are encrypted before upload.
- **Emoji reactions:** React to messages with emoji.
- **Typing indicators & read receipts:** See when someone is typing and when they've read your message.
- **Voice & video calls:** Call any connection with high-quality audio/video.
- **Memory Capsules:** Optionally save the "feel" of how you met—noise level, elevation, tags like "after class."
- **48-hour gentle archive:** New connections you don't act on move to archive after 48 hours (not deleted).
- **Connection map & timeline:** See where and when you met people on a map and journal timeline.
- **Rate the vibe:** After meeting, optionally rate the venue vibe.
- **Your QR identity card:** Show your personal QR for others to scan.
- **Availability intents:** Broadcast short plans ("coffee?", "live music tonight") to connections for 24 hours.
- **Match alerts:** Get notified when a connection has overlapping availability.
- **Community Hubs:** Join temporary venue chats when you're physically at a location (24-hour TTL).
- **Map beacons:** Discover pop-up events and venues on the map.
- **Global search:** Find connections, chats, and hubs across the app.
- **Core connections:** Pin your most important people.
- **Collaboration sessions & disposable rolls:** Fun timed photo reveals with friends after connecting.
- **Ghost mode:** Browse with reduced presence visibility when enabled.
- **Block & report:** Safety tools to block or report users.
- **Profile & interests:** Set your display name, avatar, and interest tags.
- **Onboarding:** Welcome flow with interest tagging after sign-up.
- **Google sign-in & email auth:** Sign up with Google or email/password.
- **Push notifications:** Alerts for messages, calls, matches, and reveals.
- **Deep links & App Clip:** Open connections and hubs from links without friction.
- **Web dashboard:** Use click-web in a browser for chat, calls, and connection management.
- **Business insights (venues):** Venue operators see anonymized crowd analytics, Vibe Radar, and Social Sticky Score.
- **Event reminders:** Calendar-linked reminders for upcoming events.
- **Achievements & stats:** Track connection milestones on your profile.
