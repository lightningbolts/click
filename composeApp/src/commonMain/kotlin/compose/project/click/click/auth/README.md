# Auth Module

## Module Purpose

The `auth` package implements **offline-first session bootstrap** for Click. It resolves whether a returning user is logged in **before any network I/O**, synchronizes dual token storage between the Supabase GoTrue SDK and platform secure storage, and supplies Google OAuth configuration for native sign-in. The package sits upstream of `AuthViewModel`, `AppDataManager`, and the onboarding gate in `App.kt`.

Authentication orchestration (sign-in, sign-up, OAuth, token refresh) lives in `data/repository/AuthRepository.kt` and `viewmodel/AuthViewModel.kt`; this package holds the **fast-path primitives** those layers depend on.

---

## Architecture

### Auth state machine (`AuthViewModel` + `AuthState`)

`AuthState` is a sealed hierarchy consumed by `App.kt` for routing:

| State | Meaning |
|-------|---------|
| `Idle` | Initial / reset |
| `Loading` | Cold boot in progress |
| `Success(userId, email, name?)` | Authenticated; main app may render |
| `Error(message)` | Recoverable auth failure |

`AuthViewModel` boot sequence:

1. **`AuthBootFastPath.resolveLoggedInState(tokenStorage)`** — reads local JWT + refresh token, parses identity from JWT claims, admits the user immediately when a refresh token exists (expired access tokens are acceptable offline).
2. **`AppDataManager.primeOfflineBootCache()`** — hydrates `CachedAppSnapshot` from disk so Home/Clicks paint without waiting for Supabase.
3. **`SupabaseConfig.startSessionSync(tokenStorage)`** — starts SDK session observers; every refreshed token is mirrored into `TokenStorage`.
4. **Background refresh** — `refreshSessionAndProfileInBackground()` runs on `Dispatchers.IO` after UI paint; network failures fall back to `restoreOfflineSessionIfPossible()`.

`AuthViewModel` never clears local tokens on `SessionStatus.NotAuthenticated` SDK transitions — explicit sign-out only.

### Offline-first fast path

```
Cold launch
    │
    ▼
AuthBootFastPath.resolveLoggedInState()
    ├─ SupabaseSettingsSessionReader.syncTokensToStorageIfMissing()
    ├─ LocalSessionCache.read(tokenStorage)  ← JWT payload parse
    └─ fallback: SupabaseSettingsSessionReader.readIdentity()
    │
    ├─ Success → AuthState.Success + primeOfflineBootCache()
    └─ null    → network restore via AuthRepository.restoreSession()
```

`LocalSessionCache` decodes the JWT payload (base64url) for `sub`, `email`, `name`/`full_name`, and `exp`. `isUsableForOfflineBoot()` requires a refresh token; expired access tokens are permitted. `isAccessTokenFresh()` is the strict gate for API calls.

`SupabaseSettingsSessionReader` reads GoTrue session JSON from `SettingsSessionManager` **without initializing the Supabase client**, avoiding blocking network refresh on cold boot.

### Dual token storage (`TokenStorage` expect/actual)

Session tokens are persisted in two places:

1. **Supabase SDK** — `SettingsSessionManager` via `createSupabaseAuthSettings()` (platform secure `Settings`).
2. **`TokenStorage`** — app-owned copy used by `ApiClient`, `ChatPushNotifier`, and offline boot.

| Platform | Implementation | Storage backend |
|----------|----------------|-----------------|
| Android | `AndroidTokenStorage` | `EncryptedSharedPreferences` (`auth_prefs`) |
| iOS | `IosTokenStorage` | Keychain **and** `NSUserDefaults` suite `click_auth_prefs` (write both, read Keychain first) |

`SupabaseConfig.startSessionSync()` keeps both copies aligned on every `SessionStatus.Authenticated` / refresh event, eliminating stale-Keychain "random logouts."

`TokenStorage` also persists onboarding state, `CachedAppSnapshot`, pending encounter/connection queues, notification prefs, sensor opt-ins, and ghost-mode-adjacent prefs.

### SupabaseConfig session sync

`SupabaseConfig.client` installs Auth with:

- `scheme = "click"`, `host = "login"` — OAuth return via `click://login`
- `alwaysAutoRefresh = false` — no blocking refresh on cold boot
- `autoLoadFromStorage = true`
- `importStoredSessionWithoutRefresh()` — imports `TokenStorage` session into GoTrue without network

`startSessionSync(tokenStorage)` collects `sessionStatus` and writes refreshed tokens back to `TokenStorage`.

### Google OAuth (`GoogleOAuthConfig`)

Native Google Sign-In requires **iOS and Web client IDs from the same GCP project**:

- `WEB_CLIENT_ID` — Supabase verifies ID tokens against this audience
- `IOS_CLIENT_ID` — `GIDClientID` / reversed URL scheme for iOS native flow
- `iosNativeSignInConfigured()` — validates project-number parity
- Supabase Auth → Google must list both IDs and enable "Skip nonce check"

`AuthRepository.signInWithGoogleNative()` exchanges the native ID token via Supabase `IDToken` provider.

### Onboarding gate (`WelcomeScreen`, `InterestTaggingScreen`)

After `AuthState.Success`, `App.kt` runs a multi-stage gate before the main shell:

1. **Profile basics gate** — `ProfileBasicsGateScreen` if `public.users` row is incomplete (birthday / first name).
2. **OnboardingViewModel** — `Welcome` → `Interests` → `Avatar` → `Complete`.
3. **`WelcomeScreen`** — first-run welcome; `onWelcomeAcknowledged()` advances.
4. **`InterestTaggingScreen`** — saves `user_interests` via `SupabaseRepository.updateUserInterests()`; sets `flowVersion = ONBOARDING_FLOW_VERSION_COMPLETE`.
5. **`AvatarScreen`** — optional profile photo upload via `AuthRepository.uploadProfilePicture()`.

Onboarding state is persisted in `TokenStorage.saveOnboardingState()` as JSON (`OnboardingState` in `LocalStateModels.kt`).

---

## Constraints

- **No network on cold boot for auth admission** — UI must not block on `restoreSession()` when local tokens exist.
- **Never auto-clear tokens** on SDK `NotAuthenticated` transitions; only explicit sign-out clears storage.
- **Dual-storage consistency** — any SDK refresh must propagate to `TokenStorage` via `startSessionSync`.
- **Google OAuth** — iOS native sign-in fails at runtime if `IOS_CLIENT_ID` is from a different GCP project than `WEB_CLIENT_ID`.
- **JWT parsing is client-side only** — used for offline identity display; server validation remains authoritative for API calls.
- **Onboarding flow version** — bump `ONBOARDING_FLOW_VERSION_COMPLETE` when gating rules change so legacy installs re-run the flow once.

---

## Related Files

| File | Role |
|------|------|
| `auth/AuthBootFastPath.kt` | Offline boot resolver |
| `auth/LocalSessionCache.kt` | JWT identity parse + offline usability checks |
| `auth/SupabaseSettingsSessionReader.kt` | SDK session JSON reader without client init |
| `auth/GoogleOAuthConfig.kt` | GCP client IDs + validation helpers |
| `viewmodel/AuthViewModel.kt` | Auth state machine, observers, background refresh |
| `data/repository/AuthRepository.kt` | Email/OAuth sign-in, sign-up, session restore |
| `data/SupabaseConfig.kt` | Supabase client, session sync, `importStoredSessionWithoutRefresh` |
| `data/storage/TokenStorage.kt` | expect interface |
| `data/storage/TokenStorage.android.kt` | EncryptedSharedPreferences |
| `data/storage/TokenStorage.ios.kt` | Keychain + NSUserDefaults |
| `data/models/AuthModels.kt` | Auth DTOs |
| `data/models/LocalStateModels.kt` | `OnboardingState`, `CachedAppSnapshot` |
| `ui/screens/WelcomeScreen.kt` | Onboarding welcome step |
| `ui/screens/InterestTaggingScreen.kt` | Interest selection step |
| `App.kt` | Auth + onboarding routing gates |
| `androidMain/.../MainActivity.kt` | `click://login` OAuth callback |
| `iosMain/.../MainViewController.kt` | iOS OAuth return path |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module enables or gates.

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
