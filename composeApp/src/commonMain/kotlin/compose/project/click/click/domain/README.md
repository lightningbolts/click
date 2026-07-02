# `domain/` — Pure business rules & domain entities

> **Anti-doomscrolling · Stop scrolling, start living.**  
> The `domain/` package holds **framework-free** business logic: rules that would remain true even if the UI or database technology changed.

---

## Purpose

`domain/` is intentionally small today. It exists to isolate **cryptographic and graph-theoretic connection rules** from ViewModels and repositories, keeping them:

- **Testable** without Android/iOS/Supabase dependencies (beyond injected repository interfaces).
- **Reusable** across connection flows (proximity Multi-Tap, manual clique creation from chat).
- **Documented** as the canonical expression of product invariants.

Current contents center on **Verified Clique (group E2EE) creation**.

---

## Architecture

```
viewmodel/ConnectionViewModel ──┐
viewmodel/ChatViewModel ────────┼──► domain/VerifiedCliqueCreation
                                │           │
                                │           ▼
                                │    crypto/MessageCrypto
                                │    data/repository/ChatRepository
                                │    data/models/Connection
                                └──────────────────────────────►
```

### `VerifiedCliqueCreateResult`

```kotlin
data class VerifiedCliqueCreateResult(
    val groupId: String,
    val masterKey32: ByteArray,  // 32-byte symmetric group master
)
```

Returned after successful server-side `create_verified_clique` — the master key is cached locally for decrypting future group messages.

### `VerifiedCliqueCreation` object

| Function | Role |
|----------|------|
| `findActiveEdge(a, b, connections)` | Locates active 1:1 edge between two members |
| `findActiveGroup(members, connections)` | Locates existing group connection row if any |
| `createVerifiedCliqueWithWrappedKeys(...)` | Full create pipeline |

#### Create pipeline (step by step)

1. **Validate members** — at least 2 distinct users; caller must be in the set.
2. **Pick anchor** — first member ≠ creator; used for per-member key wrapping convention (matches `ChatViewModel` 1:1 channel rules).
3. **Generate master key** — `MessageCrypto.generateGroupMasterKeyAsync()`.
4. **Wrap for each member** — `MessageCrypto.wrapGroupMasterKeyForMembers` resolves each member's active 1:1 edge to the anchor peer and seals the group key for that channel.
5. **Server create** — `chatRepository.createVerifiedClique(members, encrypted, label)`.
6. **Return** — `VerifiedCliqueCreateResult` with `groupId` + raw master for local cache.

### Domain invariants

| Invariant | Enforcement |
|-----------|-------------|
| Clique requires verified pairwise edges | `findActiveEdge` must succeed for every member↔anchor wrap |
| No duplicate active group | `findActiveGroup` detects existing clique row |
| Member list canonical | `distinct().sorted()` before server call |
| Connection status gate | Only `"active"` or `"kept"` edges qualify |

These rules mirror the backend `create_verified_clique` RPC expectations — client and server must agree on anchor wrapping semantics.

---

## Constraints

1. **No Compose, ViewModel, or Ktor imports** in `domain/` — only models, crypto, and repository interfaces.
2. **Repository calls are suspend** — domain functions are `suspend` where I/O is required.
3. **ByteArray equality** — `VerifiedCliqueCreateResult.masterKey32` uses `ByteArray` (not content-based `equals` in data class — callers must treat keys as sensitive buffers).
4. **Grow deliberately** — new domain entities (e.g. archive policy, beacon eligibility) belong here when logic exceeds a single ViewModel helper.

---

## Related files

| Path | Relationship |
|------|--------------|
| `crypto/MessageCrypto.kt` | Group master generation + per-member wrapping |
| `data/models/Connection.kt` | `user_ids`, `isGroup`, `normalizedConnectionStatus()` |
| `data/repository/ChatRepository.kt` | `createVerifiedClique` RPC abstraction |
| `viewmodel/ChatViewModel.kt` | Invokes clique creation from group picker |
| `viewmodel/ConnectionViewModel.kt` | Multi-Tap proximity → clique intent |
| `data/repository/SupabaseChatRepository.kt` | Concrete `createVerifiedClique` implementation |

### Future domain candidates (not yet extracted)

| Concept | Current location |
|---------|------------------|
| 48-hour archive eligibility | `data/models/Connection.kt` helpers + `AppDataManager` |
| Beacon schedule validation | `events/EventSchedule.kt` |
| Collaboration session TTL | `collaboration/CollaborationSession.kt` |

---

## What Click Users Experience

Domain rules shape trust and security users feel but rarely see by name.

### Connect in person (Tri-Factor)
Domain does not run the handshake — but clique creation **after** Multi-Tap depends on verified edges existing in the connection graph.

### Scan QR
QR creates 1:1 edges that later serve as wrap channels for group keys.

### Group connect (Multi-Tap)
**Core domain feature.** When 3+ users connect simultaneously, `VerifiedCliqueCreation` ensures every participant has an active verified edge to the anchor before the E2EE group chat is created. Users land in a **real clique** — fully connected subgraph — not independent pairwise guesses.

### Private encrypted chat
Group threads decrypt with the master key returned by `VerifiedCliqueCreateResult`; 1:1 threads use pairwise channel keys (crypto layer, not domain).

### Send photos / files / voice notes
Group attachments encrypt with the group master key cached after domain create.

### Emoji reactions / Typing & read receipts / Voice & video calls
Not domain concerns — handled in chat ViewModel + repository.

### Memory Capsules
Subjective + sensor metadata attached at connection time (ConnectionViewModel), not domain package.

### 48-hour gentle archive
Archive policy operates on connection rows in repository/AppDataManager — potential future domain extraction.

### Connection map & timeline
Timeline is a presentation concern; domain ensures connection graph integrity underneath.

### Rate the vibe
Mutual "keep" transitions connection status to `"kept"` — qualifying edge for future clique wraps.

### QR identity card
Establishes identity for later verified edge creation.

### Availability intents / Match alerts / Community Hubs / Map beacons / Global search
Outside current `domain/` scope.

### Core connections
Pinning is a user preference layer, not a graph invariant.

### Collaboration sessions & disposable rolls
`CollaborationSession` TTL logic lives in `collaboration/` — candidate for domain extraction.

### Ghost mode / Block & report
Privacy and safety policies — repository + AppDataManager.

### Profile & interests / Onboarding / Google-email auth
Auth and profile — `AuthRepository`.

### Push notifications / Deep links & App Clip / Web dashboard / Business insights
Infrastructure surfaces.

### Event reminders
`events/EventSchedule` validation — parallel to domain style.

### Achievements & stats
Presentation layer.
