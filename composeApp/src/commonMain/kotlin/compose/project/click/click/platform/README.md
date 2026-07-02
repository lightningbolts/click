# Platform — KMP Boundaries & Native Bridges

> Architectural reference for platform abstractions in `compose.project.click.click` (root package) and `compose.project.click.click.platform`.  
> Sourced from the Click Platforms KMP codebase and DeepWiki index (July 1, 2026).

---

## Module Purpose

The platform layer defines **where shared Kotlin ends and native code begins** in Click's Compose Multiplatform app. It provides:

- **`expect`/`actual` facades** for OS services that have no portable API (haptics, keyboard height, crypto RNG, LiveKit, BLE, push, secure storage)
- **App bootstrap sequences** on Android (`MainActivity`) and iOS (`MainViewController` + Swift `iosApp`)
- **Deep link and Universal Link routing** into shared navigation (`App.kt`, `ConnectionDeepLinkRouter`, `ChatDeepLinkManager`)
- **Source-set conventions** so new features default to `commonMain` and only spill to platform code when necessary

This README covers cross-cutting platform contracts. Feature-specific actuals are documented in sibling module READMEs ([`proximity/`](./proximity/README.md), [`crypto/`](./crypto/README.md), [`calls/`](./calls/README.md)).

---

## Architecture & Key Classes

### KMP source-set responsibilities

| Source set | Owns | Does **not** own |
|------------|------|------------------|
| **`commonMain`** | Compose UI, ViewModels, repositories, Supabase client, business logic, `expect` declarations | Direct BLE, CallKit, Keychain, FCM SDK calls |
| **`androidMain`** | `actual` for Android APIs, `MainActivity`, FCM service, LiveKit Android, EncryptedSharedPreferences token storage | Duplicate business rules already in commonMain |
| **`iosMain`** | `actual` for iOS APIs, `MainViewController`, bridges to Swift | LiveKit room implementation (lives in Swift) |
| **`iosApp/iosApp/` (Swift)** | PushKit, CallKit, `ClickLiveKitBridge`, Notification Service extension, Xcode lifecycle | Shared ViewModel logic |

**Rule of thumb:** Add features in **`commonMain` first**. Introduce `expect`/`actual` only when you must touch:

- BLE / ultrasonic / GPS sensor pipelines
- CallKit, PushKit, full-screen incoming call UI
- Keychain, EncryptedSharedPreferences, Keystore
- Platform LiveKit SDKs
- OS permission dialogs and Settings deep links

### `Platform.kt`

```kotlin
interface Platform {
    val name: String
}
expect fun getPlatform(): Platform
```

Lightweight platform identification for diagnostics and conditional logging. Media/deep-link helpers like `openBeaconOriginalMediaUrl` also live as `expect fun` at the package root.

### `PlatformHapticsPolicy`

App-level haptics **outside** Compose `LocalHapticFeedback`:

| API | Use |
|-----|-----|
| `lightImpact()` | Subtle taps, threshold crossings |
| `heavyImpact()` | Strong confirmations |
| `successNotification()` | Proximity handshake success, connection made |

| File | Platform |
|------|----------|
| `PlatformHapticsPolicy.kt` | `expect object` |
| `PlatformHapticsPolicy.android.kt` | `Vibrator` / `View.performHapticFeedback` |
| `PlatformHapticsPolicy.ios.kt` | `UIImpactFeedbackGenerator` / `UINotificationFeedbackGenerator` |

`BindPlatformHapticsToViewHierarchy()` — Android binds a `View` host from composition; no-op on iOS (installed in `MainViewController` via `IosUIKitHapticFeedback`).

`shouldUseNoOpComposeHaptics()` — parity flag; iOS uses UIKit generators instead of Compose's Core Haptics bridge.

### `platform/` subpackage

| File | Role |
|------|------|
| `KeyboardHeightProvider.kt` | expect/actual IME inset reporting for composer layouts |
| `InteractiveBackKeyboardFollow.kt` | Keyboard-aware back gesture coordination on Android |

### Android: `MainActivity` init sequence

`androidMain/.../MainActivity.kt` — ordered bootstrap in `onCreate`:

```text
1. enableEdgeToEdge(), portrait lock, screen-wake for calls
2. passSupabaseAuthDeepLink(intent)     → click://login OAuth
3. initTokenStorage(applicationContext)
4. initLocationService(applicationContext)
5. initCalendarProvider(applicationContext)
6. initEncounterTetherWidgetBridge(applicationContext)
7. initAppSystemSettings(applicationContext)
8. initCallManager(applicationContext, this)
9. initPushNotificationService(applicationContext, this)
10. handleIncomingCallIntent(intent)
11. handleChatDeepLinkIntent(intent)
12. handleCommunityHubViewIntent(intent)   → click://hub
13. handleConnectionUniversalLinkIntent(intent)
14. setContent { App() }
```

`onNewIntent` repeats deep-link and call intent handlers for `singleTask` re-entry.

`onResume` / `onPause` toggle `AndroidPushNotificationRuntime.setAppInForeground` and lifecycle hooks.

### iOS: Swift bridges

| Entry | Role |
|-------|------|
| **`MainViewController()`** | Compose host; `OnFocusBehavior.DoNothing` for IME; installs `IosUIKitHapticFeedback` |
| **`handleSupabaseAuthDeepLink(url: NSURL)`** | Called from Swift when app opens `click://login…` — finishes PKCE via `SupabaseConfig.client.handleDeeplinks` |
| **`iosApp/iosApp/iOSApp.swift`** | `@main` app, PushKit delegate, forwards URLs to Kotlin |
| **`ClickLiveKitBridge.swift`** | LiveKit room; posts `NSNotificationCenter` events consumed by Kotlin call layer |
| **`NotificationService` extension** | E2EE push preview decrypt (see [`crypto/README.md`](./crypto/README.md)) |

### Deep link schemes

Parsed primarily in `QRModels.kt`, `ConnectionDeepLinkRouter.kt`, `ChatDeepLinkManager.kt`, and `AndroidManifest.xml` intent filters.

| Scheme / URL | Host / path | Handler |
|--------------|-------------|---------|
| **`click://login`** | Supabase OAuth callback | `SupabaseConfig.client.handleDeeplinks` (Android intent filter + iOS Swift forward) |
| **`click://hub/{hub_id}`** | Community hub | `ChatDeepLinkManager.setPendingCommunityHub` |
| **`click://c/{uuid}`** | Connection deep link | `ConnectionDeepLinkRouter` |
| **`click://connect/{uuid}`** | Legacy connection link | `ConnectionDeepLinkRouter` |
| **`https://click-us.vercel.app/c/{uuid}`** | Universal Link (connection) | `ConnectionDeepLinkRouter.handleIncomingUrl` |
| **`https://click-us.vercel.app/hub/{id}`** | Universal Link (hub) | Hub intent filter → `ChatDeepLinkManager` |

**App Clip / Instant App:** Android manifest includes preview activity for unauthenticated deep-link previews.

### Factory pattern for platform services

Common pattern across the codebase:

```kotlin
// commonMain
expect fun createTokenStorage(): TokenStorage
expect fun createPushNotificationService(): PushNotificationService
expect fun rememberProximityManager(): ProximityManager
expect fun createCallManager(): CallManager
```

Each `actual` in `androidMain` / `iosMain` wires the native SDK or stub (simulator mocks where applicable).

---

## E2EE / KMP Constraints

| Constraint | Platform implication |
|------------|---------------------|
| **`PlatformCrypto` actual** | Only platform code touches `SecureRandom` / `SecRandomCopyBytes` — keeps CSPRNG audit surface small |
| **Token storage** | JWT/session tokens in EncryptedSharedPreferences (Android) / Keychain (iOS) — separate from E2EE message keys (memory-only) |
| **Push decrypt extensions** | iOS Notification Service + Android FCM run **native** crypto mirrors — must stay algorithm-identical to `MessageCrypto` |
| **No business logic in actuals** | Platform files should delegate back to `commonMain` after I/O; avoids drift between Android/iOS behavior |
| **Simulator detection** | `isSimulatorOrEmulatorRuntime()` actuals gate mock proximity, auth smoke tests |

---

## Related Files

| Path | Role |
|------|------|
| `Platform.kt` | Platform name + media URL opener expect |
| `PlatformHapticsPolicy.kt` | Haptics expect + `BindPlatformHapticsToViewHierarchy` |
| `PlatformHapticsPolicy.android.kt` / `.ios.kt` | Haptic actuals |
| `platform/KeyboardHeightProvider.kt` | IME height expect |
| `platform/InteractiveBackKeyboardFollow.kt` | Back gesture + keyboard |
| `androidMain/.../MainActivity.kt` | Android bootstrap + deep links |
| `iosMain/.../MainViewController.kt` | iOS Compose entry + OAuth deeplink |
| `iosApp/iosApp/iOSApp.swift` | Swift app lifecycle |
| `deeplink/ConnectionDeepLinkRouter.kt` | Universal link routing |
| `notifications/ChatDeepLinkManager.kt` | Hub + chat notification deep links |
| `QRModels.kt` | QR / deep-link URL parsing |
| `androidMain/AndroidManifest.xml` | Intent filters for `click://` and https |
| `App.kt` | Root composition, `rememberProximityManager`, navigation |
| `Click.kt` | iOS hub opener helper |
| `README.md` (repo root `click/`) | Source-set guideline summary |

---

## What Click Users Experience

- **Connect in person (Tri-Factor):** Tap phones together using Bluetooth, inaudible sound, and GPS to prove you're in the same room.
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
