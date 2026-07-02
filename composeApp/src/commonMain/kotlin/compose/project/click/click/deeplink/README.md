# Deep Link Module

## Module Purpose

The `deeplink` package routes **incoming URLs** into Click's connection handshake, hub join, and OAuth completion flows. `ConnectionDeepLinkRouter` is the central parser/queue for connection user IDs; platform entry points (`MainActivity`, iOS `AppDelegate`, Universal Links) forward URLs into it, and `App.kt` observes pending state for cold and warm starts.

---

## Architecture

### ConnectionDeepLinkRouter

Singleton `object` with `StateFlow<String?> pendingConnectionUserId`:

```kotlin
fun parseConnectionUserId(url: String): String?
fun handleIncomingUrl(url: String): Boolean
fun setPendingConnectionUserId(userId: String)
fun consume(): String?  // read-once semantics
```

**Flow:**
```
OS opens URL
    │
    ▼
Platform handler (MainActivity / AppDelegate / App Clip)
    │
    ▼
ConnectionDeepLinkRouter.handleIncomingUrl(url)
    ├─ parseQrPayload(url)           ← QRModels.kt
    └─ url.toUserIdFromClickUrl()    ← legacy + universal link patterns
    │
    ▼
_pendingConnectionUserId = userId
    │
    ▼
App.kt LaunchedEffect observes → starts connection handshake on Add Click flow
```

`consume()` clears pending ID after `App.kt` handles it, preventing duplicate handshakes.

### Universal Links — `click-us.vercel.app/c/`

Apple Associated Domains + Android App Links point to the Vercel web host:

| Pattern | Example |
|---------|---------|
| `https://click-us.vercel.app/c/{uuid}` | Primary universal link |
| `https://click-us.vercel.app/connect/{uuid}` | Legacy path |
| `https://click-us.vercel.app/hub/{hub_id}` | Community hub (routed via `ChatDeepLinkManager`) |

`QRModels.kt` (`toUserIdFromClickUrl()`) extracts UUID from HTTPS paths and normalizes trailing slashes / query strings.

**App Clip** — lightweight iOS clip loads the same `/c/{uuid}` path; on full app install, handoff URL is forwarded to `ConnectionDeepLinkRouter`.

### `click://hub` handler

Custom URL scheme for ephemeral community hubs:

```
click://hub/{hub_id}
```

Parsed in `QRModels.kt` via `DEEP_LINK_HUB_PATTERN`. `Click.kt` exposes `openHubFromDeepLink(hubId)` for iOS entry. `ChatDeepLinkManager.setPendingCommunityHub(hubId)` queues navigation; `App.kt` calls `launchCommunityHubJoin(hubId)`.

HTTPS equivalent: `https://click-us.vercel.app/hub/{hub_id}`.

### `click://login` handler

OAuth and magic-link return path for Supabase Auth:

```
click://login?...   (PKCE code / tokens)
```

Configured in `SupabaseConfig.client` Auth plugin:
- `scheme = "click"`
- `host = "login"`

**Android:** `MainActivity` overrides intent handling and forwards to supabase-kt `onNewIntent`.
**iOS:** `MainViewController.handleOAuthRedirect(url)` called from Swift `AppDelegate` / `onOpenURL`.

After OAuth completes, `AuthViewModel.observeOAuthCompletion()` detects `SessionStatus.Authenticated` and calls `AppDataManager.resetAndReload()`.

### URL parsing priority (`parseQrPayload`)

`QRModels.kt` resolution order:

1. Raw UUID string (36-char hyphenated)
2. Hub deep link (`click://hub/{id}` or `https://…/hub/{id}`)
3. Connection universal link (`/c/{uuid}`, `/connect/{uuid}`)
4. `click://` connection variants
5. Base64-encoded QR payload

### Integration map

| Entry point | URL types | Router |
|-------------|-----------|--------|
| `MainActivity.onCreate/onNewIntent` | `click://login`, `click://`, https app links | Auth plugin + `ConnectionDeepLinkRouter` |
| iOS `AppDelegate` | Universal Links, custom scheme | `ConnectionDeepLinkRouter` + OAuth |
| `QRScannerScreen` | Scanned QR content | `parseQrPayload` inline |
| Push notification extras | `connection_id`, `hub_id` | `ChatDeepLinkManager` |
| Share sheet / Safari | `https://click-us.vercel.app/c/{id}` | Universal Link → router |

---

## Constraints

- **Consume-once semantics** — callers must use `consume()` to avoid re-processing the same deep link on recomposition.
- **Auth required for handshake** — connection URLs queue until `AuthState.Success`; pre-auth URLs are held in `pendingConnectionUserId`.
- **UUID validation** — malformed IDs are rejected silently (`handleIncomingUrl` returns `false`).
- **Hub vs connection** — hub URLs must not be parsed as user IDs; `parseQrPayload` ordering prevents misrouting.
- **OAuth URL exclusivity** — `click://login` handled by Supabase Auth plugin, not `ConnectionDeepLinkRouter`.

---

## Related Files

| File | Role |
|------|------|
| `deeplink/ConnectionDeepLinkRouter.kt` | Connection URL queue |
| `QRModels.kt` | `parseQrPayload`, `toUserIdFromClickUrl`, hub patterns |
| `Click.kt` | `openHubFromDeepLink` iOS entry |
| `notifications/ChatDeepLinkManager.kt` | Hub + chat push routing |
| `App.kt` | Observes pending IDs, launches flows |
| `androidMain/.../MainActivity.kt` | Android intent handling |
| `iosMain/.../MainViewController.kt` | iOS OAuth redirect |
| `data/SupabaseConfig.kt` | `click://login` Auth scheme |
| `ui/screens/QRScannerScreen.kt` | In-app QR scan |
| `ui/screens/MyQRCodeScreen.kt` | Outbound QR / link share |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is reachable via deep links this module routes.

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
