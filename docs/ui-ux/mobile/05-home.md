# 05 — Home

**Scope:** Kotlin Multiplatform mobile — `HomeScreen`, `HomeComponents`, `ConnectionArchiveWarningBanner`, `AvailabilitySheet` entry from Home.  
**Source:** `ui/screens/HomeScreen.kt`, `ui/components/HomeComponents.kt`, `ui/components/ConnectionArchiveWarningBanner.kt`, `ui/components/AppScreenScaffold.kt`, `viewmodel/HomeViewModel.kt`  
**Out of scope:** Web, backend APIs.

**Visual system:** Functional Clarity — opaque surfaces, 2dp `clickBorderColor()`, scheme primary, no glass/blur/gradients. Design-asset mock (hierarchy only): `click/docs/design-assets/home/` — see that folder’s README; product truth is this doc.

**Track C (2026-07-17):** Discovery-first IA — greeting + search pill + Featured Event + dynamic nearby explore; availability + reconnect remain first-class above Explore. Greeting uses the same floating `LiquidGlassPageHeader` overlay as other tab roots (not an in-feed item).

---

## ASCII hierarchy

```
HomeScreen (organism)
├── AppShimmerScreen                    [Loading]
├── Error column                        [Error]
│   ├── Icon ErrorOutline
│   ├── "Error loading home data"
│   ├── state.message (dynamic)
│   └── Button "Retry"
└── AppScreenScaffold (showFloatingHeader = true)   [Success]
    ├── FloatingHeaderOverlay → LiquidGlassPageHeader
    │   ├── title: homeGreetingTitle(firstName)  // "Good morning|afternoon|evening|Hello, {name}."
    │   └── subtitle: HomeGreetingSubtitle       // "Ready to connect today?"
    └── LazyColumn (24dp spacing, 20dp horizontal; top inset clears floating header)
        ├── HomeSearchPill              // → onOpenSearch / UnifiedSearchSheet
        ├── FeaturedEventSection (conditional)  // first HomeEventReminder
        │   └── FeaturedEventCard → "View on Map"
        ├── HomeAvailabilityIntentsRow
        │   ├── SectionHeader "I'm down for…"
        │   ├── AssistChip[] per active intent
        │   ├── AssistChip "Set what you're down for" | "Edit intents"
        │   └── overlap lines (scheme tertiary)
        ├── ExploreNearbyBeaconsSection (conditional)
        │   └── tiles for nearby MapBeaconKind / Hub counts only
        ├── ConnectionArchiveWarningBanner (conditional)  // stay-in-touch after explore
        ├── PollPairCard (conditional)
        ├── Reconnect section (conditional)
        │   ├── SectionHeader "Reconnect"
        │   ├── subtitle "Connections you haven't talked to in a while"
        │   └── ReconnectReminderCard[]  // "Message" / "Dismiss"; ConnectionListUserAvatarFace
        ├── Event reminders (conditional, deduped vs Featured)
        │   └── HomeEventReminderCard[]  // Dismiss + View on Map
        ├── Recent Connections | Empty
        │   ├── SectionHeader "Recent Connections"
        │   ├── LocationGroupCard[] (expandable; no redundant location pin)
        │   │   └── ConnectionRowItem[]
        │   └── bordered card empty state
        ├── ConnectionInsightsCard (conditional; label/value columns)
        └── Your Stats
            ├── SectionHeader "Your Stats"
            └── HomeStatCard × 2 ("Total Clicks", "Locations")
├── AvailabilitySheet (modal overlay, conditional)
└── SnackbarHost (nudge / icebreaker feedback)
```

---

## 1. Layout / Container

### HomeScreen

| Property | Value |
|----------|-------|
| Background | `MaterialTheme.colorScheme.background` |
| Horizontal padding | 20dp |
| Section spacing | 24dp (`CardSpacing`) |
| Header | Floating `LiquidGlassPageHeader` via `AppScreenScaffold` — same status-bar overlay level as Clicks/Map/etc.; title = `homeGreetingTitle`, subtitle = `"Ready to connect today?"` (no `"Home"` title) |
| Header → search | **8dp** under greeting (`HeaderToSearchGap`; compensates list `spacedBy` after header inset) |
| List | `LazyColumn` when `HomeState.Success` |
| Bottom chrome | Transparent nav overlay + `rememberBottomChromePadding()` |

### HomeComponents (shared molecules)

| Component | Role on Home |
|-----------|----------------|
| `homeGreetingTitle` / `HomeGreetingSubtitle` | Time-of-day salutation strings for the floating header |
| `HomeSearchPill` | Bordered pill → `onOpenSearch()` |
| `FeaturedEventSection` / `FeaturedEventCard` | Upcoming **event** hero from `homeEventReminders.firstOrNull()` |
| `ExploreNearbyBeaconsSection` | Dynamic tiles from `prefetchedMapBeacons` + hubs (count > 0 only) |
| `PollPairCard` | Urgency reconnect suggestion (oldest stale 1:1) |
| `StatCard` | Legacy; Home uses `HomeStatCard` |

### Explore categories (not hardcoded mock labels)

Tiles use the **beacon create taxonomy** (`MapBeaconKind` labels / Hub), grouped from live nearby data:

- Soundtrack, Hazard, Utility, SOS, Study, Event, Social vibe, Beacon — only if nearby count > 0
- Hub — if `prefetchedCommunityHubs` is non-empty

Tap → Map with `MapLayerFilter` preset (`applyHomeLayerPreset`). Never pad with Networking / Workshop / Co-working.

### ConnectionArchiveWarningBanner

Bordered card: 16dp radius, 16dp padding, `clickCardSurface()` + 2dp `clickBorderColor()`. Merged semantics: `"{headline}. {body}. Open chat. Send icebreaker."`

### AvailabilitySheet entry

Triggered from `HomeAvailabilityIntentsRow` chip taps. See [13-availability.md](13-availability.md). Strip sits **after Featured Event**, **immediately before Explore nearby**. Archive / Poll-Pair stay-in-touch cards sit **after Explore**.

---

## 2. Interactive Elements

| Element | Gesture | Result |
|---------|---------|--------|
| Search pill | Tap | `onOpenSearch()` → `UnifiedSearchSheet` |
| Featured Event / `"View Map"` / `"View on Map"` | Tap | `onNavigateToMap(beaconId)` → Map focuses beacon |
| Explore tile | Tap | `onNavigateToMapLayer(filter)` → Map applies layer preset |
| Archive banner | Tap `"Open chat"` | `onNavigateToChat(connectionId)` |
| Archive banner | Tap `"Icebreaker"` | `sendArchiveBannerIcebreaker` (15s cooldown shared with Poll-Pair) |
| Poll-Pair card | Tap `"Open chat"` / `"Icebreaker"` | Chat or icebreaker |
| Availability chips | Tap any chip | Open `AvailabilitySheet` |
| Reconnect card | Tap `"Message"` | Open chat |
| Reconnect card | Tap `"Dismiss"` | Remove reminder |
| Event reminder | Tap `"Dismiss"` / `"View on Map"` | Dismiss or map focus |
| Location group / connection row | Tap | Expand / open chat / nudge |
| Connection Insights | Tap | Toggle expand |
| Error `"Retry"` | Tap | `viewModel.refresh()` |

**Icebreaker cooldown:** 15s; button shows `"Icebreaker ({n}s)"` when disabled.

---

## 3. States

### HomeScreen root (`HomeState`)

| State | UI | Entry condition |
|-------|-----|-----------------|
| **Loading** | `AppShimmerScreen` | Loading without cached connections |
| **Error** | Centered error + `"Retry"` | No render-ready user data |
| **Success** | Feed (no floating Home title) | Authenticated + data hydrated |

### Success sub-states

| Section | Empty | Populated |
|---------|-------|-----------|
| Archive / Poll-Pair | Hidden | Urgency cards above greeting |
| Greeting / search | Always (search if `onOpenSearch` set) | — |
| Featured Event | Hidden | First `HomeEventReminder` |
| I'm down for… | `"Set what you're down for"` chip | Intent chips + `"Edit intents"` |
| Reconnect | Hidden | Up to 3 cards with `ConnectionListUserAvatarFace` (same as Clicks inbox) |
| Explore nearby | Hidden | One tile per kind/hub with count > 0 |
| Event reminders | Hidden / deduped vs featured | Remaining reminders |
| Recent Connections | Empty bordered card | Location groups |
| Insights | Hidden if no connections | Collapsed/expanded |
| Stats | Always | Two stat cards |

---

## 4. Micro-copy

| Surface | Copy |
|---------|------|
| Greeting | `"Good morning|afternoon|evening|Hello, {firstName}."` |
| Greeting subtitle | `"Ready to connect today?"` |
| Search pill | `"Search people, places, events…"` |
| Featured section | `"Featured Event"`, `"View Map"`, `"View on Map"` |
| Explore | `"Explore nearby"`, `"{n} nearby"` / `"1 nearby"` |
| Availability | `"I'm down for…"`, `"Set what you're down for"`, `"Edit intents"` |
| Reconnect | `"Reconnect"`, `"Connections you haven't talked to in a while"`, `"Message"`, `"Dismiss"` |
| Empty connections | `"No Connections Yet"`, `"Start making connections by tapping Add Click"` |
| Stats | `"Your Stats"`, `"Total Clicks"`, `"Locations"` |

---

## 5. Flow Sequence

### Cold open → Success

```
App loads AppDataManager
  → Loading shimmer (if needed)
  → Success: greeting + search (+ Featured Event if reminder)
  → Availability + Reconnect remain reachable without scrolling past Explore when present
```

### Featured Event → Map

```
Tap View on Map
  → pendingBeaconId = beaconId
  → navigate Map tab
  → MapScreen focuses beacon pin
```

### Explore tile → Map layer

```
Tap kind/hub tile
  → pendingMapLayerFilter = matching MapLayerFilter
  → navigate Map
  → MapViewModel.applyHomeLayerPreset(filter)
```

### Availability

```
Tap chip → AvailabilitySheet → dismiss → refreshHomeAvailabilityIntents()
```

---

## 6. A11y & Responsive

- Archive banner merges semantics for VoiceOver/TalkBack.
- Borders use `clickBorderColor()` (black light / white dark).
- Transparent bottom nav: content uses `rememberBottomChromePadding()`.
- iOS vs Android: `LocalPlatformStyle` on nested connection row corners.

---

## Related

- [13-availability.md](13-availability.md) — intent sheet
- [10-map-beacons-hubs.md](10-map-beacons-hubs.md) — map layers / beacon kinds
- [11-search.md](11-search.md) — unified search
- Design mock note: [../../design-assets/home/README.md](../../design-assets/home/README.md)
