# `ui/` — Compose screens & glass design system

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Click is a people-first social utility. The `ui/` package is where every user-visible surface is composed: connection discovery, encrypted chat, maps, hubs, onboarding, and the liquid-glass chrome that ties them together.

---

## Purpose

The `ui/` module is the **presentation layer** of the Click KMP mobile app. It contains:

- **Full-screen destinations** (`screens/`) — Connections, Chat, Map, Home, Settings, onboarding, search, QR/NFC, hubs, and App Clip handshake.
- **Reusable glass UI primitives** (`components/`) — bottom sheets, cards, scaffolds, map wrappers, availability editors, QR views.
- **Chat-specific UI** (`chat/`) — message bubbles, composer, media pickers, vibe check, delivery receipts, swipe gestures.
- **Theme & tokens** (`theme/`) — colors, typography, platform-adaptive styling.
- **Platform permission bridges** (`utils/`) — location, calendar, microphone, proximity hardware requesters.

`ui/` is deliberately **stateless at the leaf level**: screens collect `StateFlow` from ViewModels and `AppDataManager`, emit user intents upward, and never own long-lived business logic or network I/O.

---

## Architecture

```
App.kt (navigation shell)
    │
    ├── screens/ConnectionsScreen ──► ChatView (inline push)
    ├── screens/MapScreen ──────────► MapView + beacon sheets
    ├── screens/HomeScreen ─────────► availability, reminders, quick actions
    ├── screens/SettingsScreen ─────► profile, privacy, ghost mode, interests
    ├── screens/GlobalSearchScreen ─► unified search chips + results
    ├── screens/HubChatScreen ──────► community hub ephemeral chat
    └── onboarding/* ───────────────► Welcome → Login → Permissions → Profile
            │
            ▼
    viewmodel/* + data/AppDataManager (SSOT)
            │
            ▼
    data/repository/* + data/api/* + Supabase Realtime
```

### Layering conventions

| Layer | Responsibility | Examples |
|-------|----------------|----------|
| `screens/` | Route-level layout, navigation callbacks, ViewModel wiring | `ConnectionsScreen`, `ChatView`, `MapScreen` |
| `components/` | Cross-screen glass primitives | `GlassCard`, `GlassModalBottomSheet`, `AppScreenScaffold` |
| `chat/` | Chat-only composables (keeps `ChatView` readable) | `ChatMessageBubble`, `ConnectionChatMessageComposer` |
| `theme/` | Design tokens | `Color.kt`, `Typography.kt`, `PlatformTheme.kt` |
| `utils/` | Composable-side platform hooks | `LocationPermissionRequester`, `MapUtils` |

### Glass UI system

Click's visual language is **liquid glass**: frosted surfaces, grabbers, adaptive bottom sheets, and OLED-aware dark sheets.

| Component | Role |
|-----------|------|
| `GlassCard` / `AdaptiveCard` | Bento tiles, settings rows, discovery cards |
| `GlassModalBottomSheet` / `GlassAdaptiveBottomSheet` | Connection context, availability, beacon detail |
| `GlassSheetTokens` / `GlassSheetGesturePhysics` | Shared corner radii, drag thresholds, spring physics |
| `GlassFullscreenMediaOverlay` | Full-bleed photo/video preview in chat |
| `GlassSnackbarHost` / `UnifiedToast` | Non-blocking feedback |
| `AppScreenScaffold` / `ScreenChrome` | Safe-area + keyboard-aware chrome for chat and sheets |
| `LiquidGlassPill` / `BentoGlassOptionRow` | Segmented controls and option lists |

### Key screen flows

**Connections hub (`ConnectionsScreen`)**  
Hosts the connection list (`ConnectionsListView`), inline chat push (`ChatView` with iOS swipe-back parallax), member picker sheets for verified cliques, profile bottom sheets, and deep-link `initialChatId` routing.

**Chat (`ChatView` + `chat/*`)**  
End-to-end encrypted thread UI: text composer, photo/file/voice pickers, emoji reactions, typing indicators, read receipts, voice/video call entry points, vibe check banner, icebreaker prompts, archive warning banner, and collaboration-session disposable-roll entry.

**Map (`MapScreen` + `MapView`)**  
Discovery map with beacon pins, community hub overlays, ghost-mode grayscale styling, tether compass toast, beacon drop sheets, and `MapDiscoveryLayout` for feed/map split.

**Settings (`SettingsScreen`)**  
Profile editing, interests card, availability intents sheet, notification toggles, memory-capsule sensor opt-ins, ghost mode, calendar permissions, web dashboard link, and sign-out.

**Global search (`GlobalSearchScreen` + `UnifiedSearchSheet`)**  
Filter chips (Active, Archived, Cliques, Nearby, Beacons, Intents), lazy result list, and navigation into chat/map/beacon targets.

**Hub chat (`HubChatScreen`)**  
Venue-scoped ephemeral chat for Community Hubs — lighter chrome than 1:1 E2EE threads.

**Onboarding**  
`WelcomeScreen` → `LoginScreen` / `SignUpScreen` → `PermissionsOnboardingScreen` → `LocationOnboardingScreen` → `ProfileBasicsGateScreen` → `InterestTaggingScreen`.

**Proximity & QR**  
`NfcScreen`, `QRScannerScreen`, `MyQRCodeScreen`, `QrCodeView`, `AppClipHandshakeScreen` (stripped App Clip surface).

**Collaboration camera**  
`camera/DisposableCameraView`, `DisposableRollCapturedPreview`, filters — UI for disposable rolls during collaboration sessions.

---

## Constraints

1. **No business logic in composables** — mutations go through ViewModels or `AppDataManager`; UI only renders state and fires callbacks.
2. **Offline-first rendering** — screens must render cached `AppDataManager` snapshots when network is down; use `OfflineStatusBanner` where appropriate.
3. **Ghost mode awareness** — map and discovery UIs must respect `AppDataManager.ghostModeEnabled` (grayscale map, no location dot).
4. **Platform splits** — map rendering, date/time pickers, back handling, and permission requesters use `expect`/`actual` in `androidMain`/`iosMain`; keep `commonMain` composables platform-agnostic.
5. **E2EE media never logs raw bytes** — chat media flows through vault helpers in `util/ChatMediaVault.kt`; UI reads `file://` URIs only after decryption.
6. **Accessibility & keyboard** — chat uses `ScreenChrome` keyboard lift; prefer `collectAsStateLifecycleAware()` (from `util/`) for tab destinations that can be off-screen while composed.
7. **Minimal scope** — new UI should extend existing glass primitives; do not introduce parallel design systems.

---

## Related files

### Inside `ui/`

| Path | Description |
|------|-------------|
| `screens/ConnectionsScreen.kt` | Main connections tab; hosts list + inline chat |
| `screens/ChatView.kt` | Full chat thread surface |
| `screens/MapScreen.kt` | Map discovery + beacon interactions |
| `screens/HomeScreen.kt` | Dashboard, event reminders, quick entry |
| `screens/SettingsScreen.kt` | Profile, privacy, preferences |
| `screens/GlobalSearchScreen.kt` | Unified search destination |
| `screens/HubChatScreen.kt` | Community Hub chat |
| `screens/QRScannerScreen.kt` / `MyQRCodeScreen.kt` | QR scan + identity card |
| `screens/AppClipHandshakeScreen.kt` | iOS App Clip handshake |
| `screens/MemoriesListSection.kt` | Memory capsule list UI |
| `components/MapView.kt` | Map wrapper (expect/actual) |
| `components/Glass*.kt` | Glass design system primitives |
| `components/AvailabilitySheet.kt` | Availability intents editor |
| `components/CreateHubModal.kt` | Community Hub creation |
| `chat/ChatMessageBubble.kt` | Message rendering (text/media/audio) |
| `chat/VibeCheckAndIcebreaker.kt` | Rate the vibe + icebreaker prompts |
| `theme/Color.kt` | Brand palette |

### Outside `ui/` (dependencies)

| Path | Relationship |
|------|--------------|
| `viewmodel/ChatViewModel.kt` | Chat state, send/receive, realtime |
| `viewmodel/ConnectionViewModel.kt` | Proximity handshake, QR, cliques |
| `viewmodel/GlobalSearchViewModel.kt` | Search indexing |
| `data/AppDataManager.kt` | App-wide SSOT flows |
| `calls/CallSessionManager.kt` | In-call UI state |
| `collaboration/CollaborationSessionManager.kt` | Disposable roll windows |
| `deeplink/ConnectionDeepLinkRouter.kt` | Deep link parsing |

---

## What Click Users Experience

Every feature below has a primary UI entry point in this module.

### Connect in person (Tri-Factor)
Users open the Add Click flow (`AddClickScreen`, `NfcScreen`) to start a **simultaneous BLE + ultrasonic + progressive GPS** handshake. The UI shows proximity states (fetching location, handshaking, pending match, offline sync) via `ConnectionViewModel` state surfaced in connection sheets and toasts.

### Scan QR
`QRScannerScreen` and `QRScanner` component scan token-based or legacy QR payloads. `MyQRCodeScreen` + `QrCodeView` display the user's **QR identity card** encoding a Universal Link to `CLICK_WEB_BASE_URL/c/{userId}`.

### Group connect (Multi-Tap)
When 3+ people handshake together, `ConnectionMemberPickerSheet` / `GroupMembersPickerSheet` coordinate verified clique creation. Success routes into a group chat with E2EE group master key handling (domain + ViewModel).

### Private encrypted chat
`ChatView` renders **E2EE 1:1 and group threads** with liquid-glass chrome, swipe-back navigation (iOS), and ambient mesh backgrounds. Messages decrypt client-side before display.

### Send photos / files / voice notes
`ChatMediaPickers`, `ChatFilePickers`, `VoiceMessageRecordDialogLayout`, and attachment bubbles handle outbound media. Inbound media downloads decrypt to the on-device vault before preview.

### Emoji reactions
`MessageActionSheet` + reaction overlays on `ChatMessageBubble`; emoji catalog in `EmojiCatalog.kt`.

### Typing & read receipts
`ChatDeliveryReceipt` and realtime-driven typing state in `ChatView` / `ChatMessageTimeline`.

### Voice & video calls
Call entry from chat chrome and connection sheets; in-call UI bridges to `CallSessionManager` (platform LiveKit in `calls/` and `iosApp`).

### Memory Capsules
`MemoriesListSection`, `ConnectionContextSheet`, and sensor opt-in toggles in Settings capture **barometric height + ambient noise + subjective tags** when the user opts in.

### 48-hour gentle archive
`ConnectionArchiveWarningBanner` warns before auto-archive. Archived connections move to the Archived tab/filter; `ConnectionActionSheet` supports manual archive/unarchive.

### Connection map & timeline
`ProfileConnectionMoment`, connection context sheets, and profile bottom sheets show **where/when/how** users met, including encounter timeline payloads.

### Rate the vibe
`VibeCheckAndIcebreaker.kt` — mutual vibe check banner and keep/pass dialog after new connections.

### QR identity card
`MyQRCodeScreen` — shareable profile QR with branded glass frame.

### Availability intents
`AvailabilitySheet`, `AvailabilityComponents`, `SettingsScreen` availability section — users broadcast **when they're free and for what** (coffee, study, gym, etc.).

### Match alerts
Home and connections surfaces show pending match / proximity-sync states; push notifications (see `notifications/`) deliver match alerts off-app.

### Community Hubs
`CreateHubModal`, `HubChatScreen`, hub pins on map — venue-scoped discovery and ephemeral hub chat.

### Map beacons
`BeaconDropSheet`, `MapBeaconSheetRoot`, `MapScreen` — users drop and discover **event, vibe, and social beacons** on the map.

### Global search
`GlobalSearchScreen` + `UnifiedSearchSheet` — search connections, messages, beacons, intents, and archived threads with category chips.

### Core connections
`ConnectionsTabControls` — pin core connections to the top of the list; cores stay map-visible when not ghosted.

### Collaboration sessions & disposable rolls
`DisposableCameraView` + camera filters — after re-encounter bumps, a time-boxed **Disposable Roll** window opens for collaborative photo capture.

### Ghost mode
`SettingsScreen` toggle → `AppDataManager.toggleGhostMode()` — map goes grayscale, location sharing stops, background sync halts for the session.

### Block & report
`ConnectionActionSheet` — Report and Block actions route to repository APIs; blocked users disappear from discovery.

### Profile & interests
`ProfileBottomSheet`, `TabbedUserProfileSheet`, `InterestEditor`, `SettingsInterestsCard`, `InterestTaggingScreen`.

### Onboarding
`WelcomeScreen` → auth → `PermissionsOnboardingScreen` → `LocationOnboardingScreen` → `ProfileBasicsGateScreen` → interests.

### Google / email auth
`LoginScreen`, `SignUpScreen` — Supabase Auth with Google OAuth and email/password.

### Push notifications
UI dismiss hooks (`ChatNotificationDismisser`) and Settings notification toggles; delivery handled in `notifications/`.

### Deep links & App Clip
`AppClipHandshakeScreen` loads profile from invocation URL. `ConnectionDeepLinkRouter` parses `/c/{userId}` Universal Links.

### Web dashboard
Settings "Open dashboard" link to `CLICK_WEB_BASE_URL` for business/account management on web.

### Business insights
Business-tier surfaces (waitlist dialog, venue QR with `venue_id`) in `WaitlistDialog`, hub creation flows.

### Event reminders
`HomeScreen` surfaces day-of and one-hour-before reminders from `EventReminderCoordinator` (see `events/`).

### Achievements & stats
`ClicktivitiesScreen` + `ClicktivityCard` — gamified activity suggestions and connection stats presentation.
