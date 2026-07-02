# `viewmodel/` — Screen state & orchestration

> **Anti-doomscrolling · Stop scrolling, start living.**  
> ViewModels translate user intent into repository calls, subscribe to realtime, and expose immutable UI state — keeping Compose screens thin and testable.

---

## Purpose

The `viewmodel/` package hosts **AndroidX `ViewModel` classes** for each major app surface. ViewModels:

- Own **coroutine scope** (`viewModelScope`) for async work.
- Read and write through **`AppDataManager`** (app-wide SSOT) and **repositories** (`ConnectionRepository`, `ChatRepository`, `SupabaseRepository`, etc.).
- Subscribe to **Supabase Realtime** channels where needed (chat messages, typing, connection updates).
- Bridge **platform services** (location, proximity, calls, push) without importing UI composables.

ViewModels are the **orchestration layer** between `ui/` and `data/`.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        ui/screens/*                          │
│              collectAsStateLifecycleAware()                  │
└──────────────────────────┬──────────────────────────────────┘
                           │ intents ↑  state ↓
┌──────────────────────────▼──────────────────────────────────┐
│                     viewmodel/*                              │
│  ConnectionViewModel  ChatViewModel  GlobalSearchViewModel   │
│  MapViewModel  HubChatViewModel  AuthViewModel  …            │
└──────────┬───────────────────────────────┬──────────────────┘
           │                               │
           ▼                               ▼
┌──────────────────────┐      ┌────────────────────────────┐
│   AppDataManager     │      │  data/repository/*          │
│   (StateFlow SSOT)   │◄────►│  SupabaseChatRepository     │
│                      │      │  ConnectionRepository       │
└──────────────────────┘      │  MapBeaconRepository        │
                              └─────────────┬──────────────┘
                                            │
                                            ▼
                              ┌────────────────────────────┐
                              │  data/api/ApiClient         │
                              │  ChatApiClient              │
                              │  Supabase KMP + Realtime    │
                              └────────────────────────────┘
```

### ViewModel inventory

| ViewModel | Primary surface | Key responsibilities |
|-----------|-----------------|----------------------|
| `ConnectionViewModel` | Add Click, NFC, proximity | Tri-Factor handshake, QR redemption, Multi-Tap cliques, sensor capture, offline proximity queue |
| `ChatViewModel` | Connections tab, ChatView | E2EE encrypt/decrypt, message send queue, reactions, typing, read receipts, media vault, archive, calls entry |
| `GlobalSearchViewModel` | GlobalSearchScreen | Unified haystack search across connections, messages, beacons, intents |
| `MapViewModel` | MapScreen | Beacon/hub discovery, layer filters, ghost mode, telemetry pan events |
| `HubChatViewModel` | HubChatScreen | Ephemeral hub messages (non-E2EE venue chat) |
| `HomeViewModel` | HomeScreen | Dashboard aggregation, event reminders, availability |
| `AuthViewModel` | Login/SignUp | Supabase auth, session boot, onboarding gate |
| `OnboardingViewModel` | Onboarding flow | Permission + profile completion state machine |
| `AvailabilityViewModel` | Availability sheet | Intent CRUD, calendar overlap hints |
| `ConnectivityViewModel` | App shell | Online/offline banner coordination |
| `TestingViewModel` | TestingScreen | Internal diagnostics |

### AppDataManager integration pattern

`AppDataManager` is a **singleton object** holding hot `StateFlow`s for connections, chats, messages, beacons, hubs, ghost mode, archives, and core pins. ViewModels follow this contract:

1. **Read** — `AppDataManager.connections.collect { … }` or one-shot `.value` for synchronous decisions.
2. **Write** — prefer repository success → `AppDataManager` optimistic/local update helpers (`markConnectionArchivedLocally`, `upsertConnection`, etc.).
3. **Refresh** — `AppDataManager.refreshAll(userId)` on foreground/network regain; **skipped when ghost mode is on**.
4. **Boot** — `AppDataManager.hydrateFromCache(tokenStorage)` for offline-first cold start before network.

`ChatViewModel` is the deepest integrator: it mirrors realtime message streams into per-chat caches while respecting `AppDataManager` connection and archive sets.

### Global search architecture

`GlobalSearchViewModel` builds a **unified result list** from local SSOT first, then supplements with remote search:

```kotlin
sealed class SearchResult {
    data class ActiveConnection(...)
    data class ArchivedConnection(...)
    data class Clique(...)
    data class IntentMatch(...)
    data class BeaconMatch(...)
    // ...
}
```

Filter chips map to `SearchResultCategory` (Active, Archived, Cliques, Nearby, Beacons, Intents). Local matching uses `util/ConnectionSearchHaystack.kt`; remote supplement via `SupabaseRepository.unifiedSearch`.

### ConnectionViewModel state machine

Proximity connection is modeled as a sealed `ConnectionState`:

| State | Meaning |
|-------|---------|
| `Idle` / `Loading` | Initial / in-flight |
| `ProximityFetchingLocation` | GPS warm-up |
| `ProximityHandshaking` | BLE + ultrasonic active |
| `PendingConfirmation` | Peer list ready for user confirm |
| `ProximityCapturedOfflineSyncing` | Tokens saved locally; sync when online |
| `ProximityHandshakePendingMatch` | HTTP 202 — waiting for peer online |
| `TaggingContext` | Post-connect subjective tag fan-out |
| `Success` | Connection row created |

Multi-Tap emits `VerifiedCliqueProximityIntent` for UI autofill into group member picker.

### ChatViewModel highlights

- **Inbox freshness** — `loadChats(isForced = false)` skips network when `AppDataManager.isInboxFeedFresh()` (30s SSOT window); forced reloads and `chatListRefreshEpoch` bypass.
- **Chat open fast path** — `resolveCachedChatPayload` + `isChatThreadCacheFresh`; stale threads refresh via `buildChatPayloadWithRetry` / `scheduleBackgroundChatPayloadRefresh`.
- **E2EE** — `MessageCrypto` + `AttachmentCrypto` for text and media; group threads use wrapped master keys via `VerifiedCliqueCreation`.
- **Realtime** — `SupabaseChatRepository` subscriptions for messages, reactions, typing, connection `last_message_at`.
- **Offline queue** — `PendingMessageQueue` retries sends; `NetworkConnectivityMonitor` triggers flush.
- **Media pipeline** — encrypt → upload via `ChatApiClient` → decrypt to `ChatMediaVault` on download.
- **Teardown** — `util/teardownBlocking` in `onCleared()` for bounded realtime unsubscribe.

---

## Constraints

1. **ViewModels must not import Compose UI** (except `@Composable` helpers like `SecureChatMediaHost` where unavoidable).
2. **Never clear SSOT on transient network errors** — use `util/isOfflineNetworkFailure()` to preserve local state.
3. **Ghost mode** — do not trigger `AppDataManager.refreshAll` or location/beacon uploads when ghost mode is enabled.
4. **Token access** — use `createTokenStorage()` or Supabase session; never log raw JWTs (`util/redactedRestMessage`).
5. **Threading** — heavy crypto and media on `Dispatchers.Default` / `chatMediaDispatcher`; UI state updates on Main via `StateFlow`.
6. **Testability** — repositories are constructor-injectable in tests (see `androidUnitTest/.../ChatViewModelTest.kt`).

---

## Related files

| Path | Relationship |
|------|--------------|
| `data/AppDataManager.kt` | App-wide SSOT; ghost mode, archives, cores |
| `data/repository/SupabaseChatRepository.kt` | Chat CRUD + Realtime |
| `data/repository/ConnectionRepository.kt` | Proximity bind, QR, connections |
| `data/repository/SupabaseRepository.kt` | User profiles, unified search |
| `data/api/ApiClient.kt` | REST BFF (profiles, beacons, LiveKit token) |
| `data/api/ChatApiClient.kt` | Chat media upload, click-web message tunnel |
| `domain/VerifiedCliqueCreation.kt` | Group clique key wrapping |
| `proximity/ProximityManager.kt` | Tri-Factor hardware orchestration |
| `calls/CallCoordinator.kt` | LiveKit token fetch |
| `collaboration/CollaborationSessionManager.kt` | Disposable roll sessions |
| `network/NetworkConnectivityMonitor.kt` | Online/offline signals |
| `util/NetworkFailureUtil.kt` | Offline detection |
| `util/ViewModelTeardown.kt` | Bounded `onCleared` cleanup |

### Supporting types in `viewmodel/`

| File | Role |
|------|------|
| `GlobalSearchMatch.kt` | Local search scoring helpers |
| `MapLayerFilter.kt` | Map layer enum for `MapViewModel` |
| `SecureChatMediaHost.kt` | Composable bridge for secure media preview host |

---

## What Click Users Experience

ViewModels power every interactive feature; users never see them directly but feel their effects.

### Connect in person (Tri-Factor)
`ConnectionViewModel` runs the full proximity pipeline: permission checks, `ProximityManager` listen/advertise, GPS refinement, sensor JSON attachment, and `POST /api/connections/proximity` bind — with offline queue fallback.

### Scan QR
QR token redemption and legacy payload parsing route through `ConnectionRepository` invoked by `ConnectionViewModel`.

### Group connect (Multi-Tap)
Verified clique intents from proximity bind autofill group picker; `VerifiedCliqueCreation` + `ChatViewModel` create E2EE group master keys.

### Private encrypted chat
`ChatViewModel` encrypts outbound messages, decrypts inbound, manages per-chat realtime subscriptions, and surfaces delivery states.

### Send photos / files / voice notes
Attachment validation, encryption, multipart upload (`ChatApiClient`), and vault write-back on download — all in `ChatViewModel`.

### Emoji reactions
Realtime reaction events merged into message state; optimistic add/remove.

### Typing & read receipts
Typing channel per chat; read cursor updates persisted and broadcast.

### Voice & video calls
`ChatViewModel` initiates calls via `CallCoordinator` → `ApiClient.postLiveKitToken`; state observed from `CallSessionManager`.

### Memory Capsules
`ConnectionViewModel` attaches `AmbientNoiseMonitor` + `BarometricHeightMonitor` snapshots to encounter metadata when opted in.

### 48-hour gentle archive
`ChatViewModel` / `AppDataManager` track `archivedConnectionIds`; archive warnings computed from connection timestamps.

### Connection map & timeline
Profile timeline payloads fetched via `ApiClient` and surfaced in chat/profile ViewModel state.

### Rate the vibe
`ChatViewModel` drives vibe check prompts (`VibeCheckAndIcebreaker` UI); mutual keep updates connection status.

### QR identity card
Auth session provides user id for QR URL generation (UI renders; ViewModel supplies profile).

### Availability intents
`AvailabilityViewModel` CRUD on intent rows; overlap with calendar via `util/AvailabilityIntentOverlap`.

### Match alerts
`ConnectionViewModel` `ProximityHandshakePendingMatch` state; push notifiers fire on server match.

### Community Hubs
`MapViewModel` + `AppDataManager` hub pin merge; `HubChatViewModel` for venue chat.

### Map beacons
`MapViewModel` loads/refreshes beacons; RSVP state persisted via `TokenStorage` beacon snapshot.

### Global search
`GlobalSearchViewModel` — debounced query, category filters, local+remote merge, distance-sorted beacon matches.

### Core connections
`AppDataManager` core pin set sorted first in `ChatViewModel` connection list flows.

### Collaboration sessions & disposable rolls
`ConnectionViewModel` activates `CollaborationSessionManager` on re-encounter bumps; `ChatViewModel` exposes roll window for camera entry.

### Ghost mode
`AppDataManager.toggleGhostMode()` — all ViewModels respect ghost gate on refresh/location.

### Block & report
`ChatViewModel` / repository calls hide connections and insert `user_blocks` rows.

### Profile & interests
`AuthViewModel` + `OnboardingViewModel` for profile basics; interests synced via `ApiClient` patch.

### Onboarding
`OnboardingViewModel` state machine persisted in `TokenStorage.saveOnboardingState`.

### Google / email auth
`AuthViewModel` → `AuthRepository` → Supabase Auth OAuth + email flows.

### Push notifications
ViewModels register/unregister push tokens via `PushTokenRepository` on login/logout.

### Deep links & App Clip
`ConnectionDeepLinkRouter` consumed at App level; `ConnectionViewModel` handles pending deep-link connections.

### Web dashboard
No ViewModel — Settings opens URL; session JWT used by web companion.

### Business insights
B2B venue flows use `ApiClient` hub/beacon endpoints from `MapViewModel`.

### Event reminders
`HomeViewModel` reads `EventReminderCoordinator.homeReminders` for dashboard cards.

### Achievements & stats
`HomeViewModel` / testing surfaces expose connection counts; `ClicktivitiesScreen` is mostly static UI today.
