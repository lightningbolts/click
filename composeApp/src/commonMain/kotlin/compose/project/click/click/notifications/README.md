# Notifications Module

## Module Purpose

The `notifications` package bridges **server-initiated push delivery** (Supabase Edge Function `send-push-notification`) with **client-side routing** into chats, calls, hubs, and collaboration reveals. It handles FCM on Android, APNs + PushKit VoIP on iOS, token registration lifecycle, and deep-link intent queues consumed by `App.kt`.

---

## Architecture

### Platform push stacks

| Platform | Transport | Token type | Wake behavior |
|----------|-----------|------------|---------------|
| **Android** | Firebase Cloud Messaging (FCM) | `standard` | Notification tray + data payload |
| **iOS** | APNs standard + **PushKit VoIP** | `standard` / `voip` | VoIP wakes CallKit; standard adds banner fallback |

**Android (`PushNotificationService.android.kt`):**
- `FirebaseMessaging.getInstance().token` → `PushTokenRepository.savePushToken(platform = "android")`
- `POST_NOTIFICATIONS` runtime permission on API 33+
- EncryptedSharedPreferences for pending tokens pre-login
- Per-`tokenType` pending slots (FCM standard + optional VoIP channel)

**iOS (`PushNotificationService.ios.kt`):**
- Posts `ClickRegisterForRemoteNotifications` → Swift `AppDelegate` calls `registerForRemoteNotifications()`
- Posts `ClickRequestNotificationPermission` for UNUserNotificationCenter prompt
- PushKit VoIP token uploaded separately with `token_type = "voip"`
- `consumePendingPushTokens()` uploads tokens that arrived before login

### `send-push-notification` Edge Function contract

Endpoint: `POST {SUPABASE_URL}/functions/v1/send-push-notification`

**Client-initiated chat push** (`ChatPushNotifier`):

```json
{
  "data": {
    "type": "chat_message",
    "chat_id": "...",
    "message_id": "...",
    "sender_user_id": "...",
    "message_preview": "optional plaintext preview"
  }
}
```

Headers: `Authorization: Bearer {user_jwt}`, `Content-Type: application/json`

**Push categories** (`PushCategory` in Edge Function):

| `data.type` | Purpose |
|-------------|---------|
| `chat_message` | New message notification |
| `incoming_call` | Voice/video ring |
| `archive_warning` | 48-hour gentle archive nudge |
| `disposable_reveal` | Click Drop TTL reveal |

Server resolves `recipient_user_id` from chat membership for `chat_message`; validates connection membership for `incoming_call`.

### `incoming_call` payload parity with web

`incoming_call` pushes require validated fields matching the web call flow:

```json
{
  "recipient_user_id": "{callee_id}",
  "title": "Incoming call",
  "body": "{caller_name}",
  "data": {
    "type": "incoming_call",
    "connection_id": "...",
    "caller_id": "...",
    "callee_id": "...",
    "call_id": "...",
    "is_video": false
  }
}
```

Edge Function rules:
- `recipient_user_id` must match `callee_id`
- `connection_id`, `caller_id`, `callee_id` are required
- iOS: sends to **all** iOS tokens (VoIP + standard) so CallKit wakes even if VoIP is delayed
- Android: FCM high-priority data message → `CallSessionManager` incoming handler

Web (`click-web`) and mobile share the same `data` shape so `CallSessionManager.bindUser()` routes consistently.

### Client-side push routing

```
OS notification tap / data message
        │
        ├─ chat_message ──► ChatDeepLinkManager.setPendingChat(connectionId)
        │                      └─ App.kt LaunchedEffect → navigateTo(Connections) + pendingChatId
        │
        ├─ incoming_call ──► CallSessionManager.onIncomingPush(data)
        │                      └─ ActiveCallOverlay / CallPreviewOverlay
        │
        ├─ disposable_reveal ──► CollaborationSessionManager + open chat
        │
        └─ hub / connection URL ──► ConnectionDeepLinkRouter / ChatDeepLinkManager
```

**Supporting types:**

| Class | Role |
|-------|------|
| `ChatDeepLinkManager` | Pending `connectionId` and `hubId` StateFlows |
| `ChatNotificationDismisser` | Clears tray notifications when chat is opened |
| `ChatPushNotifier` | Outbound chat push trigger after send |
| `NotificationRuntimeState` | In-memory message/call toggle mirror for foreground suppression |
| `PendingPushTokenStore` | Cross-platform pending token queue before auth |
| `PushNotificationService` | expect `registerToken` / `requestPermission` |

`AppDataManager` registers push tokens after notification prefs load — off the critical startup path so chats paint first.

### Foreground behavior

When the app is foregrounded:
- `NotificationRuntimeState` suppresses duplicate message banners if the active chat matches
- Incoming calls still surface `CallPreviewOverlay` via `CallSessionManager`
- `ChatNotificationDismisser` clears notifications on thread open

---

## Constraints

- **Permission-gated** — no token upload until user grants notification permission (platform-specific).
- **Pre-login token queue** — tokens received before auth are persisted and flushed on `registerToken(userId)`.
- **JWT required for client-initiated pushes** — `ChatPushNotifier` fails without `TokenStorage.getJwt()`.
- **Retry policy** — `ChatPushNotifier` retries 3× with backoff on 5xx only.
- **VoIP entitlement** — iOS PushKit requires Apple VoIP capability; standard APNs is fallback.
- **Service-role paths** — cron/disposable reveal pushes use service key, not user JWT.

---

## Related Files

| File | Role |
|------|------|
| `notifications/PushNotificationService.kt` | expect interface |
| `notifications/PushNotificationService.android.kt` | FCM registration |
| `notifications/PushNotificationService.ios.kt` | APNs registration bridge |
| `notifications/ChatPushNotifier.kt` | Outbound chat push client |
| `notifications/ChatDeepLinkManager.kt` | Pending chat/hub routing |
| `notifications/ChatNotificationDismisser.kt` | Tray cleanup |
| `notifications/NotificationRuntimeState.kt` | Foreground prefs mirror |
| `notifications/PendingPushTokenStore.kt` | Pre-auth token queue |
| `data/repository/PushTokenRepository.kt` | Token upsert to Supabase |
| `data/AppDataManager.kt` | Startup token registration |
| `calls/CallSessionManager.kt` | Incoming call routing |
| `supabase/functions/send-push-notification/index.ts` | Server push dispatcher |
| `androidMain/.../MainActivity.kt` | FCM intent handling |
| iOS `AppDelegate.swift` | PushKit + UNUserNotificationCenter |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module delivers alerts for.

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
