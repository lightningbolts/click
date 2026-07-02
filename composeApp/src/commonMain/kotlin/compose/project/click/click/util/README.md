# `util/` — Cross-cutting helpers, vaults & platform bridges

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Shared utilities that every layer depends on: secure storage bridges, offline detection, media vaults, search haystacks, and safe error surfacing.

---

## Purpose

`util/` is the **toolbox** for code too small for its own module but too repeated to inline:

| Category | Files |
|----------|-------|
| **Session & prefs bridge** | `TokenStorage` lives in `data/storage/`; util provides teardown, lifecycle collection |
| **Network safety** | `NetworkFailureUtil.kt`, `RedactedThrowable.kt` |
| **Media vaults** | `ProfileMediaVault.kt`, `ChatMediaVault.kt`, `ChatOutgoingImageCompression.kt`, `ChatMediaDispatcher.kt` |
| **Search** | `ConnectionSearchHaystack.kt` |
| **Availability math** | `AvailabilityIntentOverlap.kt`, `AvailabilityOverlapCache.kt`, `AvailabilityOverlapPrefetch.kt` |
| **Compose lifecycle** | `CollectAsStateLifecycleAware.kt` |
| **ViewModel cleanup** | `ViewModelTeardown.kt` |
| **Cache** | `LruMemoryCache.kt` |
| **Misc** | `MusicLinks.kt` |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  viewmodel/*  ·  ui/*  ·  data/AppDataManager  ·  telemetry │
└────────────────────────────┬────────────────────────────────┘
                             │
     ┌───────────────────────┼───────────────────────┐
     ▼                       ▼                       ▼
NetworkFailureUtil    ProfileMediaVault         TokenStorage
RedactedThrowable     ChatMediaVault            (data/storage/)
                      ChatMediaDispatcher
ConnectionSearchHaystack
AvailabilityIntentOverlap
CollectAsStateLifecycleAware
ViewModelTeardown
```

### `TokenStorage` bridge (`data/storage/TokenStorage.kt`)

Not in `util/` but **central to util consumers**:

```kotlin
interface TokenStorage {
    suspend fun saveTokens(jwt, refreshToken, expiresAt, tokenType)
    suspend fun getJwt(): String?
    // … prefs: dark mode, notifications, sensor opt-ins, onboarding, caches
    suspend fun saveCachedAppSnapshot(snapshot: String?)
    suspend fun savePendingProximityHandshakeQueue(queue: String?)
    suspend fun saveBeaconRsvpSnapshot(snapshot: String?)
    suspend fun clearSessionData()
}

expect fun createTokenStorage(): TokenStorage
```

| Platform | Implementation |
|----------|----------------|
| Android | `EncryptedSharedPreferences` in `TokenStorage.android.kt` |
| iOS | Keychain in `TokenStorage.ios.kt` |

`SupabaseConfig.startSessionSync(tokenStorage)` keeps SDK session aligned with vault.

### `NetworkFailureUtil.kt`

```kotlin
fun Throwable.isOfflineNetworkFailure(): Boolean
```

Classifies timeouts, `IOException`, unresolved hosts, connection refused — used by `AppDataManager`, `ChatViewModel`, and refresh paths to **preserve local SSOT** instead of clearing UI on transient errors.

### `RedactedThrowable.kt`

```kotlin
fun Throwable.redactedRestMessage(): String
```

Strips Supabase/Ktor error tails containing `url:`, `headers:`, `Bearer`, `apikey=` before logs or snackbars. **Mandatory** for any user-visible or `println` error path.

### `ProfileMediaVault.kt`

On-disk cache for **decrypted profile avatars/covers**:

- `profileMediaVaultId(cacheKey)` — stable FNV-1a filename stem
- `expect read/write/profileMediaVaultLocalPath` — platform file IO

### `ChatMediaVault.kt`

On-disk cache for **decrypted chat attachments** (see `media/README.md`):

- Extension inference from mime/url
- `writeChatMediaVaultFile` → `file://` URI for Compose
- `readChatMediaVaultBytesForMessage` helpers

### `ChatOutgoingImageCompression.kt`

Downscales/compresses outbound images before encrypt to save bandwidth and storage quota.

### `ChatMediaDispatcher.kt`

```kotlin
val chatMediaDispatcher = Dispatchers.Default.limitedParallelism(4)
```

Shared by encrypt/upload/decrypt and telemetry POST.

### `ConnectionSearchHaystack.kt`

Builds searchable text index from connections (names, tags, memory snippets, timestamps) for `GlobalSearchViewModel` local-first matching.

### `AvailabilityIntentOverlap.kt` + `AvailabilityOverlapCache.kt`

Intersect user availability intents with `calendar/` busy blocks; cache per peer pair to avoid repeated calendar reads.

### `AvailabilityOverlapPrefetch.kt`

Batch-fetches peer availability bubbles and writes overlap results into `AvailabilityOverlapCache`:

- `ViewerAvailabilityBubblesCache` — session cache for the signed-in user's bubbles (one fetch per session).
- `prefetchAvailabilityOverlapsForPeers()` — parallel fetch with **concurrency 8**, capped at **`AVAILABILITY_OVERLAP_MAX_PEERS` (48)** to prevent Supabase read storms for power users.
- Called from `ConnectionsListView` (inbox bolt icons) and `HomeViewModel` (overlap cards).

`ConnectionItem` reads cache only — no per-row network I/O.

See also `PERFORMANCE.md` § Scale failure modes.

### `CollectAsStateLifecycleAware.kt`

```kotlin
@Composable
expect fun <T> StateFlow<T>.collectAsStateLifecycleAware(): State<T>
```

Android pauses collection below `STARTED` lifecycle; reduces background churn from off-screen tabs.

### `ViewModelTeardown.kt`

```kotlin
internal fun teardownBlocking(timeoutMs = 500L, block: suspend () -> Unit)
```

Bounded cleanup from `ViewModel.onCleared()` when `viewModelScope` is already cancelled — used by `ChatViewModel` for realtime unsubscribe.

### `LruMemoryCache.kt`

In-memory LRU for decrypted chat image bitmaps — avoids re-decode on scroll-back.

### `MusicLinks.kt`

Parses/normalizes music service links in profiles or messages (Spotify, Apple Music).

---

## Constraints

1. **util/ stays dependency-light** — no ViewModels, no screens; OK to depend on `data/models`.
2. **expect/actual only for true platform IO** — vault paths, lifecycle collection.
3. **Never log secrets** — always `redactedRestMessage()`.
4. **Vault files are sensitive** — profile/chat vaults hold decrypted bytes; protect with app sandbox (no backup flags on Android where configured).
5. **Offline classification is conservative** — when in doubt, preserve local cache.
6. **teardownBlocking must stay short** — ≤500ms; no network in teardown block.

---

## Related files

| Path | Role |
|------|------|
| `util/NetworkFailureUtil.kt` | Offline Throwable detection |
| `util/RedactedThrowable.kt` | Safe error strings |
| `util/ProfileMediaVault.kt` | Profile image disk cache |
| `util/ChatMediaVault.kt` | Chat attachment disk cache |
| `util/ChatOutgoingImageCompression.kt` | Image compression |
| `util/ChatMediaDispatcher.kt` | Media IO dispatcher |
| `util/ConnectionSearchHaystack.kt` | Local search index |
| `util/AvailabilityIntentOverlap.kt` | Calendar ∩ intent math |
| `util/CollectAsStateLifecycleAware.kt` | Lifecycle-aware collect |
| `util/ViewModelTeardown.kt` | onCleared helper |
| `data/storage/TokenStorage.kt` | Session + prefs interface |
| `androidMain/.../TokenStorage.android.kt` | Android actual |
| `iosMain/.../TokenStorage.ios.kt` | iOS actual |
| `data/AppDataManager.kt` | Cached snapshot via TokenStorage |
| `auth/LocalSessionCache.kt` | Fast auth boot |

---

## What Click Users Experience

Utilities shape reliability and privacy users feel everywhere.

### Connect in person (Tri-Factor)
`TokenStorage.savePendingProximityHandshakeQueue` — offline handshakes sync when `isOfflineNetworkFailure` clears.

### Scan QR
Errors show redacted messages, not JWT leaks.

### Group connect (Multi-Tap)
Same offline queue semantics.

### Private encrypted chat
`ChatMediaVault` + `LruMemoryCache` — fast scroll-back through media threads; `teardownBlocking` prevents realtime leaks after leaving chat.

### Send photos / files / voice notes
`ChatOutgoingImageCompression` → encrypt → `ChatMediaVault` on receive — seamless attach/play experience.

### Emoji reactions / Typing & read receipts
Realtime state collected via `collectAsStateLifecycleAware` on connection tab.

### Voice & video calls
Token read from `TokenStorage` for LiveKit JWT requests.

### Memory Capsules
Sensor opt-in prefs in `TokenStorage` (`saveAmbientNoiseOptIn`, `saveBarometricContextOptIn`).

### 48-hour gentle archive
`AppDataManager` snapshot in `saveCachedAppSnapshot` — cold start shows last-known connections including archive state.

### Connection map & timeline
`ConnectionSearchHaystack` enables searching memory text in global search.

### Rate the vibe
Unrelated util path.

### QR identity card
Session JWT from vault for authenticated QR regeneration.

### Availability intents
`AvailabilityIntentOverlap` + `AvailabilityOverlapCache` — mutual free time cards feel instant on repeat opens.

### Match alerts
Push prefs via `saveMessageNotificationsEnabled`.

### Community Hubs / Map beacons
`saveBeaconRsvpSnapshot` / `saveActiveHubs` survive process death.

### Global search
**`ConnectionSearchHaystack`** — local instant results before network supplement.

### Core connections
Cached in app snapshot.

### Collaboration sessions & disposable rolls
Session data ephemeral in memory; prefs unaffected.

### Ghost mode
Session toggle in `AppDataManager` (not persisted in TokenStorage — resets on restart for privacy).

### Block & report
Redacted API errors in snackbars.

### Profile & interests
**`ProfileMediaVault`** — avatars load instantly on repeat profile views.

### Onboarding
`saveOnboardingState`, `saveHasCompletedOnboarding`, `saveLocationExplainerSeen`.

### Google / email auth
`saveTokens` / `clearSessionData` on login/logout.

### Push notifications
`saveMessageNotificationsEnabled`, `saveCallNotificationsEnabled`.

### Deep links & App Clip
Cached session enables fast profile fetch on clip open.

### Web dashboard
JWT from storage for authenticated web handoff.

### Business insights
Unrelated.

### Event reminders
RSVP snapshot persistence via `TokenStorage`.

### Achievements & stats
`LruMemoryCache` + snapshot cache contribute to snappy stats screens.
