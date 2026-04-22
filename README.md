# Click (KMP) — Mobile app

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Click is built as a **people-first social utility**: fewer infinite feeds, more real-world presence. We optimize for intentional connection—not passive consumption—so moments in the room matter more than minutes on the timeline.

**Click — The seamless offline-to-online connection app.**  
This repository is the **Kotlin Multiplatform** mobile client: **Compose Multiplatform** UI with **Android** and **iOS** targets. Backend pieces (Postgres, Edge Functions, companion HTTP APIs) live in sibling services; the app integrates via **Supabase** and a configurable **web base URL** for QR and LiveKit token calls.

---

## Core concept

Click is a **digital handshake**: it turns brief, in-person encounters into lasting connections using a **multi-modal proximity mesh**—not a single hardware sandbox. Phones agree they shared the same physical context through corroborating signals, then open a lightweight path to chat, calls, and memory—without treating the OS as the source of truth for “who was really there.”

### The Tri-Factor Handshake

New connections use a **simultaneous** blend of:

- **Bluetooth Low Energy (BLE) advertising** — local presence and session framing in the room.  
- **18.5 kHz ultrasonic audio chirps** — inaudible to most listeners, detectable on the ultrasonic-capable microphone path for tight co-location.  
- **Progressive high-accuracy GPS** — refined over the handshake window so devices can assert they occupy the **same** space, not merely the same building.

Together, these factors **mathematically verify** that two (or more) phones were in the **exact same room**, reducing reliance on legacy OS-imposed proximity restrictions.

### Verified Group Cliques (Multi-Tap)

When **three or more people** connect **at the same time** (**Multi-Tap**), the app coordinates a **verified group clique**: the backend performs **O(1)** validation so the cohort forms a **fully connected subgraph** (everyone mutually verified with everyone else). On success, participants drop into an **end-to-end encrypted (E2EE) group chat**—a real clique, not a loose list of pairwise guesses.

### Memory Capsules

**Memory Capsules** pair **objective** signals with **subjective** story:

- **Hardware-backed context** — **`BarometricHeightMonitor`** captures **elevation** trends; **`AmbientNoiseMonitor`** reads **exact decibel levels** from the same stream used for ultrasonic detection, grounding “how loud / how high” the moment felt in data.  
- **Polymorphic subjective tagging** — users layer meaning (“met at open mic,” “after class”) in a flexible schema; tagged payloads participate in **data fan-out** so capsules stay rich without collapsing into one rigid field.

### Lifecycle: 48-Hour Auto-Archive Sweep

There is **no “hard lock”** or forced expiry that deletes relationships by timer alone. Instead, Click runs a **48-Hour Auto-Archive Sweep**: connections that are **not acted on** within **48 hours** move to **archive**, keeping the **active** surface calm and intentional. Archived items remain recoverable according to product policy—this is about **UI hygiene and focus**, not punishing users with arbitrary countdowns.

---

## Source sets and entry points

| Location | Purpose |
|----------|---------|
| [`composeApp/src/commonMain/kotlin/`](./composeApp/src/commonMain/kotlin/) | Shared UI (Compose), ViewModels, repositories, Supabase client, call coordination, chat, connections, maps, most business logic |
| [`composeApp/src/androidMain/kotlin/`](./composeApp/src/androidMain/kotlin/) | Android `actual` implementations: FCM service, LiveKit Android SDK, `TokenStorage`, crypto, location, incoming-call UI bridge |
| [`composeApp/src/iosMain/kotlin/`](./composeApp/src/iosMain/kotlin/) | iOS `actual` implementations: `TokenStorage`, push helpers, permission requesters, stubs/bridges to Swift where needed |
| [`iosApp/iosApp/`](./iosApp/iosApp/) | Xcode app: **Swift** for PushKit, CallKit, UserNotifications, **ClickLiveKitBridge** (LiveKit Swift SDK), app lifecycle and Kotlin entry |
| [`composeApp/build.gradle.kts`](./composeApp/build.gradle.kts) | Multiplatform targets, dependencies (e.g. Supabase KMP, Ktor, `livekit-android`) |

**Guideline:** add new features in **`commonMain`** first; use **`expect`/`actual`** (or small platform facades) only when you must touch **BLE / audio / GPS pipelines, CallKit, PushKit, Keychain, EncryptedSharedPreferences, platform location, or platform LiveKit**.

---

## Features (app scope)

- **Tri-Factor proximity mesh + QR** — In-person discovery and connection via **BLE + ultrasonic + progressive GPS**; **QR** remains a fallback path that uses HTTP against the configured web base URL (see `QRModels.kt` / `QrCodeView.kt`). Connection metadata can record `connectionMethod` (e.g. `"qr"`, `"tri_factor"`).  
- **Multi-Tap verified cliques** — Simultaneous 3+ person handshakes validated server-side; E2EE group chat on success.  
- **Real-time chat** — Supabase **Realtime** channels, `SupabaseChatRepository` / `ChatViewModel`, typing and presence-oriented state, push hooks for background delivery (Edge Function + FCM/APNs).  
- **Voice & video calls (LiveKit)** — `CallSessionManager`, `CallCoordinator`, `CallApiClient` fetch a JWT and `wsUrl` from `{CLICK_WEB_BASE_URL}/api/livekit/token`. **Android:** `livekit-android` in `CallManager.android.kt`. **iOS:** native room in **Swift** (`ClickLiveKitBridge.swift`) with Compose driving state from shared Kotlin.  
- **Presence** — Realtime subscriptions in home/chat/map-related ViewModels for online status and activity signals.  
- **Maps** — Map screens and Realtime channels (e.g. connection discovery) backed by Supabase-backed repositories.  
- **Memory Capsules (opt-in)** — `rememberAmbientNoiseMonitor`, `rememberBarometricHeightMonitor`, and subjective tagging flows when the user opts in (settings + connection sheets).

---

## Tech stack (mobile)

- **Kotlin Multiplatform** + **Compose Multiplatform**  
- **Android:** Gradle, Jetpack lifecycle/viewmodel where used, **FCM** (`google-services.json` in `composeApp/`)  
- **iOS:** Xcode project, **PushKit** (VoIP), **CallKit**, **APNs** (via backend—not Firebase for VoIP)  
- **Supabase KMP:** Auth (with `SettingsSessionManager` + app `TokenStorage` sync in `SupabaseConfig`), Postgrest, Realtime  
- **Ktor** client for companion HTTP APIs (QR, waitlist, LiveKit token)  
- **LiveKit:** `io.livekit:livekit-android` (see `build.gradle.kts`); iOS via Swift Package in Xcode  

---

## Configuration (this app)

### Supabase

Edit [`composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt`](./composeApp/src/commonMain/kotlin/compose/project/click/click/data/SupabaseConfig.kt):

- `SUPABASE_URL`, `SUPABASE_ANON_KEY`  
- Auth redirect scheme/host (`click` / `login`) must match Supabase Auth and the iOS/Android URL handlers.

`SupabaseConfig.startSessionSync(tokenStorage)` keeps the SDK session aligned with **`TokenStorage`** (Keychain / encrypted prefs) to avoid stale tokens after refresh.

### Web base URL (QR, LiveKit token, waitlist)

[`composeApp/src/commonMain/kotlin/QRModels.kt`](./composeApp/src/commonMain/kotlin/QRModels.kt) defines **`CLICK_WEB_BASE_URL`**. Point it at your deployed or local companion app that exposes `/api/qr`, `/api/livekit/token`, etc. (typically the **click-web** project next to this repo).

### Android push (FCM)

Place **`google-services.json`** in [`composeApp/`](./composeApp/) (package `compose.project.click.click`). The Supabase Edge Function uses a Firebase **service account** for server-side FCM—not a substitute for iOS VoIP pushes.

### iOS capabilities

Enable Push Notifications, Background Modes (**Voice over IP**), and associated entitlements. VoIP token is cached in **UserDefaults** (`cached_voip_token`) before syncing to Kotlin; see `iosApp/iosApp/ClickVoipPushManager.swift` and `iOSApp.swift`.

---

## Build and run

### Android

From this directory:

```shell
./gradlew :composeApp:assembleDebug
```

Use Android Studio’s **composeApp** run configuration, or install the debug APK from `composeApp/build/outputs/`.

### iOS

1. Open [`iosApp/iosApp.xcodeproj`](./iosApp/iosApp.xcodeproj) in Xcode.  
2. Resolve Swift packages (**LiveKit** and transitive deps).  
3. Select the **iosApp** scheme, set signing team, build and run.

### Database and server-side setup

SQL migrations and ordering: [`database/`](./database/). Full operator checklist (Edge Function secrets, APNs, FCM, LiveKit env): **[`EXTERNAL_SETUP.md`](./EXTERNAL_SETUP.md)**.

Optional: [`quick_start_chat.sh`](./quick_start_chat.sh) for guided prompts around Supabase config.

---

## Notable modules (navigation)

| Area | Starting points |
|------|------------------|
| Auth & session | `data/SupabaseConfig.kt`, `viewmodel/AuthViewModel.kt`, `data/repository/AuthRepository.kt` |
| Chat | `viewmodel/ChatViewModel.kt`, `data/repository/SupabaseChatRepository.kt` |
| Connections / proximity / QR | `viewmodel/ConnectionViewModel.kt`, `ui/screens/ConnectionsScreen.kt`, `ui/components/QrCodeView.kt` |
| Calls | `calls/CallSessionManager.kt`, `calls/CallCoordinator.kt`, `calls/CallApiClient.kt` |
| Maps | `viewmodel/MapViewModel.kt` |
| Push (Kotlin side) | `notifications/ChatPushNotifier.kt`, `notifications/CallPushNotifier.kt`, `data/repository/PushTokenRepository.kt` |

---

## Monorepo note

If your checkout includes **`click-web`** beside **`click/`**, run that Next.js app locally when developing QR or LiveKit token flows against `http://localhost:3000` (and set `CLICK_WEB_BASE_URL` accordingly). The KMP app does not embed that server—only calls it over HTTP.

Repository-level docs for the whole workspace may live in the parent folder’s `README.md` / `AI.md`.

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/lightningbolts/click)
