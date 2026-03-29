# Click — The seamless offline-to-online connection app

Click is a Kotlin Multiplatform (KMP) social product for discovering people, staying present, and chatting—bridging in-person moments (NFC, QR) with a shared cloud backend and real-time experiences.

This workspace is a **multi-project** layout:

| Path | Role |
|------|------|
| [`click/`](./click/) | **Primary mobile app** — Compose Multiplatform UI, shared business logic, Android + iOS targets |
| [`click-web/`](./click-web/) | **Companion web stack** — Next.js app with APIs used by the mobile clients (e.g. QR flows, LiveKit token issuance) |
| [`click/supabase/`](./click/supabase/) | **Supabase Edge Functions** (e.g. push delivery) |
| [`click/database/`](./click/database/) | **SQL migrations and schema helpers** for PostgreSQL |

---

## Feature overview

- **NFC & QR connections** — Exchange secure connection flows in person; QR integrates with the web API (`CLICK_WEB_BASE_URL` / `click-web`). Connection metadata records methods such as `"qr"` or `"nfc"`.
- **Real-time chat** — Supabase Realtime channels, repositories in `commonMain`, and push-triggered delivery for background awareness.
- **VoIP video & audio (LiveKit)** — Room tokens and WebSocket URL from the web API (`/api/livekit/token`); **Android** uses `livekit-android`; **iOS** uses the LiveKit Swift SDK and a native bridge (`ClickLiveKitBridge`) plus CallKit for the system call UI.
- **Presence tracking** — Realtime subscriptions and view-layer state for online/typing and connection-oriented presence (see chat and home/map view models).
- **Interactive maps** — Map-focused screens and realtime map channels (e.g. connection discovery) backed by Supabase.
- **Ambient environment signals** — Optional microphone-based ambient noise sampling and barometric/height-style monitors for richer context during connections (user-controlled opt-in in settings and connection flows).

---

## Tech stack

- **Kotlin Multiplatform** — Shared module under `click/composeApp/src/commonMain`.
- **Jetpack Compose** — Compose Multiplatform UI (`composeApp`).
- **Supabase** — PostgreSQL, Auth, Realtime, and **Edge Functions** (e.g. `send-push-notification` for FCM + APNs/VoIP).
- **LiveKit** — Real-time WebRTC rooms (`io.livekit:livekit-android` on Android; Swift package on iOS).
- **PushKit (iOS)** — VoIP push registration, token caching, and CallKit integration in the Xcode target.
- **FCM (Android)** — Firebase Cloud Messaging for standard notifications; service account JSON is configured for the Edge Function (not for iOS VoIP).

---

## Architecture

### Shared `commonMain` vs native code

- **`commonMain`** holds the majority of product logic: repositories, view models, Compose UI, Supabase client usage (`SupabaseConfig`), call coordination (`CallSessionManager`, `CallCoordinator`, `CallApiClient`), chat, connections, maps, and shared notification helpers.
- **`androidMain` / `iosMain`** supply **expect/actual** boundaries and platform injections where native APIs are required: `TokenStorage`, `PlatformCrypto`, `LocationService`, push token pending store, `PlatformIncomingCallUi`, LiveKit room implementation on Android, permission requesters, etc.
- **iOS Swift** (`click/iosApp/`) hosts PushKit, CallKit, UserNotifications, and the LiveKit bridge—Kotlin is invoked where the shared layer must receive tokens or call events.

Prefer adding new business rules and data flow in **`commonMain`** first; only split to a platform source set when the platform SDK forces it.

---

## Local setup

### Prerequisites

- **JDK** compatible with the Gradle wrapper in `click/`
- **Android Studio** or IntelliJ with KMP plugins, **Xcode** (for iOS)
- **Node.js** (for `click-web`)
- **Supabase CLI** (optional but recommended for functions — see [`click/EXTERNAL_SETUP.md`](./click/EXTERNAL_SETUP.md))

### 1. Supabase (`SupabaseConfig.kt`)

1. Open [`click/composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt`](./click/composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt).
2. Set **`SUPABASE_URL`** and **`SUPABASE_ANON_KEY`** to your project’s values (do not commit real production secrets to public repos).
3. Run SQL from [`click/database/`](./click/database/) in the order described in [`click/EXTERNAL_SETUP.md`](./click/EXTERNAL_SETUP.md) (extensions, `push_tokens`, triggers, etc.).

Auth uses a custom URL scheme (`click://login`) configured in the Supabase Auth settings to match the client.

### 2. LiveKit & web base URL

- **Mobile → token API**: [`CallApiClient`](./click/composeApp/src/commonMain/kotlin/compose/project/click/click/calls/CallApiClient.kt) posts to `{CLICK_WEB_BASE_URL}/api/livekit/token` with the user’s JWT.
- **Configure** [`CLICK_WEB_BASE_URL`](./click/composeApp/src/commonMain/kotlin/QRModels.kt) to point at your deployed or local `click-web` instance (default in repo may point at a hosted environment).
- **Server env**: set LiveKit API credentials and WebSocket URL on the Next.js side (see `click-web` and `click/EXTERNAL_SETUP.md` sections on LiveKit Cloud / self-hosted).

### 3. Run **click-web** (for QR + LiveKit token routes)

```bash
cd click-web
npm install
cp .env.example .env.local   # if present; otherwise create .env.local per project docs
npm run dev
```

Default dev server: [http://localhost:3000](http://localhost:3000).

### 4. Run **Android**

From `click/`:

```bash
./gradlew :composeApp:assembleDebug
```

Or use Android Studio run configurations. Place **`google-services.json`** in `click/composeApp/` when using FCM (see `EXTERNAL_SETUP.md`).

### 5. Run **iOS**

1. Open **`click/iosApp/iosApp.xcodeproj`** in Xcode.
2. Ensure Swift Package dependencies resolve (including **LiveKit**).
3. Configure signing, Push Notifications, and Background Modes (VoIP) per `EXTERNAL_SETUP.md`.
4. Build and run the `iosApp` target.

---

## Further reading

- **[`click/EXTERNAL_SETUP.md`](./click/EXTERNAL_SETUP.md)** — End-to-end checklist: database, Edge Function secrets (FCM JSON, APNs key, VoIP topic), Apple capabilities, and LiveKit.
- **[`click/README.md`](./click/README.md)** — KMP module layout (`commonMain`, `iosMain`, `androidMain`).
- **AI / contributor guardrails** — See **[`AI.md`](./AI.md)** in this directory for architecture and platform rules intended for humans and coding agents.

---

## License / contributing

Add your license and contribution guidelines here when finalized.
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/lightningbolts/click)