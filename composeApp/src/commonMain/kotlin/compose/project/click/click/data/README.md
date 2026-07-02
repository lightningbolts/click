# Data Module

## Module Purpose

The `data` package is Click's **client-side single source of truth (SSOT)**. `AppDataManager` centralizes authenticated app state, coordinates repository calls to Supabase and Edge Functions, persists cold-start snapshots, and exposes `StateFlow` surfaces consumed by ViewModels and Compose screens. Repositories encapsulate network I/O; `models/` holds domain entities and wire serializers.

---

## Architecture

### AppDataManager singleton

`AppDataManager` is an `object` with a dedicated `CoroutineScope(SupervisorJob + Dispatchers.Default)`. It is initialized once via `initializeData()` after auth succeeds.

**Responsibilities:**

| Concern | Implementation |
|---------|----------------|
| Current user + interests | `_currentUser`, `_userInterestTags` |
| Connections inbox | `_connections`, `_inboxFeedChats`, `_connectedUsers` |
| Per-user overlays | `_archivedConnectionIds`, `_hiddenConnectionIds`, `_coreConnectionIds` |
| Chat/hub thread cache | `_cachedChatThreads`, `_cachedHubThreads` |
| Map discovery prefetch | `_prefetchedMapBeacons`, `_prefetchedCommunityHubs` |
| Presence | Delegates to `SupabaseChatRepository.onlineUsers` |
| Ghost mode | `_ghostModeEnabled` — halts sync, beacon prefetch, presence heartbeat |
| Push registration | `createPushNotificationService()` after prefs load |
| Foreground recovery | `handleApplicationForegrounded()` — debounced reload + Realtime reconnect |

**Startup hydration sequence:**

```
initializeData()
    │
    ├─ restoreCachedSnapshot()          ← CachedAppSnapshot from TokenStorage
    ├─ restoreActiveHubs()
    ├─ startPendingConnectionSync()
    ├─ startNetworkConnectivityObserver()
    └─ loadAllData() [async]
           ├─ authRepository.getCurrentUser() / LocalSessionCache fallback
           ├─ startBeaconPrefetch()      ← parallel, skipped in ghost mode
           ├─ fetchUserConnectionsSnapshot()
           ├─ fetchUserById + interests
           ├─ applyFetchedConnectionSnapshot()
           ├─ _isDataLoaded = true
           ├─ persistSnapshot()
           ├─ startSilentChatPrefetch()
           └─ background: refreshConnectedUsers, push token upload
```

`primeOfflineBootCache()` (called from auth fast-path) runs only `restoreCachedSnapshot()` + `restoreActiveHubs()` so the dashboard renders before `loadAllData()` completes.

### CachedAppSnapshot persistence

`CachedAppSnapshot` (`LocalStateModels.kt`) is a kotlinx-serialization DTO persisted as JSON in `TokenStorage.saveCachedAppSnapshot()`:

- `currentUser`, `connections`, `connectedUsers`
- `archivedConnectionIds`, `hiddenConnectionIds`, `coreConnectionIds`
- `cachedChatThreads`, `cachedHubThreads`, `inboxFeedChats`
- `cachedUserPublicProfiles`, `cachedProfileTimelines`
- `cachedMapBeacons`, `cachedCommunityHubs`
- `locationPreferences`

`restoreCachedSnapshot()` decodes on cold start; `persistSnapshot()` writes after successful loads and incremental merges. Offline-first: if the server returns an empty connection list but local cache has rows, local rows are preserved.

### Repositories

| Repository | Role |
|------------|------|
| `ConnectionRepository` | Connection CRUD, archive/hide/core pins, proximity bind, pending connection queue sync |
| `SupabaseRepository` | User profiles, interests, availability, map beacons, community hubs, search supplements |
| `SupabaseChatRepository` | Chat messages, Realtime Postgres changes, presence channel, encrypted media, reactions, read receipts |
| `AuthRepository` | Session + OAuth (see auth README) |
| `ChatRepository` | Interface implemented by `SupabaseChatRepository` |
| `MapBeaconRepository` | Beacon RSVP persistence |
| `PushTokenRepository` | FCM/APNs token upload |
| `NotificationPreferencesRepository` | Remote notification toggles |

`SupabaseChatRepository` owns `room:presence` Realtime channel health and `onlineUsers` set.

### Models.kt domain entities

`data/models/Models.kt` and sibling files define the domain layer:

- **`User`**, **`UserPublicProfile`** — profile surfaces
- **`Connection`**, **`ConnectionInsert`** — connection rows + insert DTO
- **`ChatWithDetails`**, **`Message`**, **`MessageReaction`** — inbox + timeline
- **`ConnectionEncounter`** — encounter enrichment rows (sensor fields, weather, GPS)
- **`MemoryCapsule`** — UI-facing encounter summary
- **`MapBeacon`**, **`AvailabilityModels`**, **`IcebreakerModels`**
- **`AuthModels`**, **`ProfileCompletion`**, **`ReconnectModels`**

Wire serializers handle Supabase JSON quirks (`SemanticLocationWireSerializer`, `FlexibleWeatherSnapshotSerializer`).

### ContextTagTaxonomy

`ContextTagTaxonomy` (`data/ContextTagTaxonomy.kt`) is the canonical list of subjective proximity tags (lecture, cafe, party, gym, etc.). `suggest(locationName, hourOfDay)` ranks tags from reverse-geocoded place names and time of day. Tags flow from `ConnectionState.TaggingContext` into `connections.context_tags` after creation — they are **not** staged in `CachedAppSnapshot`.

### Ghost mode

`toggleGhostMode()` flips `_ghostModeEnabled`. When enabled:

- Background data refresh, beacon prefetch, presence heartbeat, and foreground recovery are **skipped**
- Map renders grayscale and hides user location dot
- Cached data remains visible but goes stale
- Ghost mode **resets on app restart** for safer privacy defaults

Core connections remain map-visible when ghosted off-map (server-side `connection_core` rows).

### Sync epochs

Invalidation uses explicit epoch counters rather than polling:

| Epoch | Trigger | Consumer |
|-------|---------|----------|
| `chatListRefreshEpoch` | `bumpChatListRefresh()` after group creation | `ConnectionsListView` forces `ChatViewModel.loadChats()` |
| `_foregroundRealtimeRecovery` | `handleApplicationForegrounded()` | `ChatViewModel` re-attaches Postgres channels |
| `discoveryMapPrefetchComplete` | Beacon prefetch finishes | `MapViewModel` seeds from prefetch |

Pending connection / proximity handshake queues sync on a `PENDING_SYNC_RETRY_MS` (15s) loop with network connectivity observer.

### Hydration sequence (detailed)

1. **Auth fast-path** → `primeOfflineBootCache()` restores snapshot
2. **`loadAllData()`** fetches server snapshot; merges with local SSOT
3. **Parallel beacon prefetch** seeds map before user opens Map tab
4. **Silent chat prefetch** loads top N thread timelines in background
5. **Profile prefetch** hydrates peer avatars for connection list rows
6. **`persistSnapshot()`** writes durable cache after each successful merge

---

## Constraints

- **Singleton lifecycle** — one `AppDataManager`; ViewModels must not duplicate connection state.
- **Ghost mode is session-scoped** — not persisted; resets on cold start.
- **30s refresh cooldown** — `REFRESH_COOLDOWN_MS` prevents refresh storms.
- **15s startup timeout** — `loadAllData()` wrapped in `withTimeout(STARTUP_TIMEOUT_MS)`.
- **Offline merge policy** — server-empty + local-nonempty preserves local connections.
- **Lazy repository init** — repositories use `lazy` so JVM tests can load without Supabase/crypto.
- **No secrets in logs** — errors use `redactedRestMessage()`.

---

## Related Files

| File | Role |
|------|------|
| `data/AppDataManager.kt` | Central SSOT singleton |
| `data/models/LocalStateModels.kt` | `CachedAppSnapshot`, `OnboardingState`, pending queues |
| `data/models/Models.kt` | Core domain entities |
| `data/models/MemoryCapsule.kt` | Encounter capsule model |
| `data/models/ConnectionEncounter.kt` | DB encounter row + merge helpers |
| `data/ContextTagTaxonomy.kt` | Subjective tag taxonomy + suggestions |
| `data/SupabaseConfig.kt` | Supabase client factory |
| `data/SupabaseForegroundRecovery.kt` | Background → foreground socket recovery |
| `data/repository/ConnectionRepository.kt` | Connection operations |
| `data/repository/SupabaseRepository.kt` | Profiles, hubs, beacons |
| `data/repository/SupabaseChatRepository.kt` | Chat + Realtime |
| `data/storage/TokenStorage.kt` | Snapshot + queue persistence |
| `data/api/ApiClient.kt` | REST Edge Function client |
| `viewmodel/*ViewModel.kt` | UI state consumers |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module powers.

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
