# 05 — Home

**Scope:** Kotlin Multiplatform mobile — `HomeScreen`, `HomeComponents`, `ConnectionArchiveWarningBanner`, `AvailabilitySheet` entry from Home.  
**Source:** `ui/screens/HomeScreen.kt`, `ui/components/HomeComponents.kt`, `ui/components/ConnectionArchiveWarningBanner.kt`, `viewmodel/HomeViewModel.kt`  
**Out of scope:** Web, backend APIs, redesign.

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
└── AppScreenScaffold                   [Success]
    ├── title: "Home"
    ├── subtitle: "Welcome back, {name}!"
    ├── search action (optional)
    └── LazyColumn (24dp vertical spacing, 20dp horizontal via scaffold)
        ├── ConnectionArchiveWarningBanner (conditional)
        ├── PollPairCard (conditional)
        ├── HomeAvailabilityIntentsRow
        │   ├── GradientSectionHeader "I'm down for…"
        │   ├── AssistChip[] per active intent
        │   ├── AssistChip "Set what you're down for" | "Edit intents"
        │   └── overlap lines (gold #FFE8A8)
        ├── Reconnect section (conditional)
        │   ├── GradientSectionHeader "Reconnect"
        │   ├── subtitle "Connections you haven't talked to in a while"
        │   └── ReconnectReminderCard[]
        ├── Event reminders (conditional)
        │   ├── GradientSectionHeader "Event reminders"
        │   └── HomeEventReminderCard[]
        ├── Recent Connections | Empty
        │   ├── GradientSectionHeader "Recent Connections"
        │   ├── LocationGroupCard[] (expandable)
        │   │   └── ConnectionRowItem[]
        │   └── GlassCard empty state
        ├── ConnectionInsightsCard (conditional)
        └── Your Stats
            ├── GradientSectionHeader "Your Stats"
            └── GlassStatCard × 2 ("Total Clicks", "Locations")
├── AvailabilitySheet (modal overlay, conditional)
└── SnackbarHost (nudge / icebreaker feedback)
```

---

## 1. Layout / Container

### HomeScreen

| Property | Value |
|----------|-------|
| Background | `MaterialTheme.colorScheme.background` (Zinc-950 family) |
| Horizontal padding | 20dp (`ScreenPaddingHorizontal`) |
| Section spacing | 24dp (`CardSpacing`) |
| Header | `AppScreenScaffold` — title `"Home"`, subtitle dynamic |
| List | `LazyColumn` inside scaffold when `HomeState.Success` |

### HomeComponents (shared molecules)

| Component | Role on Home |
|-----------|----------------|
| `PollPairCard` | Hero reconnect suggestion (oldest stale 1:1 chat) |
| `StatCard` | Legacy stat card (not used on Home; Home uses inline `GlassStatCard`) |
| `OnlineFriendItem` | Not mounted on current HomeScreen |
| `RecentClickCard` | Not mounted on current HomeScreen |

### ConnectionArchiveWarningBanner

Glass + gradient border card (matches `PollPairCard` visual language). 24dp outer radius, 18dp inner padding. Merged semantics: `"{headline}. {body}. Open chat. Send icebreaker."`

### AvailabilitySheet entry

Triggered from `HomeAvailabilityIntentsRow` chip taps. Full-screen `ClickFormBottomSheet` overlay; see [13-availability.md](13-availability.md) for sheet internals. Home only opens it and refreshes intents on dismiss.

---

## 2. Interactive Elements

| Element | Gesture | Result |
|---------|---------|--------|
| Scaffold search icon | Tap | `onOpenSearch()` → `UnifiedSearchSheet` |
| Archive banner | Tap `"Open chat"` | `onNavigateToChat(connectionId)` |
| Archive banner | Tap `"Icebreaker"` | `sendArchiveBannerIcebreaker` (15s cooldown shared with Poll-Pair) |
| Poll-Pair card | Tap `"Open chat"` | Navigate to chat |
| Poll-Pair card | Tap `"Icebreaker"` | Send contextual icebreaker message |
| Availability chips | Tap any chip | `resetAvailabilityIntentSheet()` → show `AvailabilitySheet` |
| `"Edit intents"` / `"Set what you're down for"` chip | Tap | Same as above |
| Reconnect card | Tap `"Say hi"` | Open chat for that connection |
| Reconnect card | Tap `"Dismiss"` | Remove reminder from list |
| Event reminder card | Tap `"Dismiss"` | Dismiss beacon reminder |
| Location group card | Tap header | Toggle expand/collapse (`toggleLocationExpanded`) |
| Connection row | Tap row / chat icon | Open chat |
| Connection row | Tap nudge icon | Send nudge (`sendNudgeByConnectionId`) |
| Connection Insights card | Tap anywhere on card | Toggle expanded insights panel |
| Error state | Tap `"Retry"` | `viewModel.refresh()` → `AppDataManager.refresh(force = true)` |
| Snackbar | Auto-dismiss | After `nudgeResult` shown and cleared |

**Haptics:** None on Home except system defaults on buttons.

**Icebreaker cooldown:** 15 seconds after successful send; button shows `"Icebreaker ({n}s)"` and is disabled.

---

## 3. States

### HomeScreen root (`HomeState`)

| State | UI | Entry condition |
|-------|-----|-----------------|
| **Loading** | `AppShimmerScreen` (dark/light from background luminance) | `!isDataLoaded && isLoading` and no cached active connections |
| **Error** | Centered error column + `"Retry"` | No render-ready user data; message from `AppDataManager.error` or session |
| **Success** | Full `AppScreenScaffold` feed | Authenticated user + data hydrated |

#### Error micro-copy (dynamic `state.message`)

| Condition | Quoted string |
|-----------|---------------|
| Offline | `"No internet connection. Your data will appear when you're back online."` |
| Session | `"Session expired. Please log in again."` |

Static error chrome: `"Error loading home data"` + primary `"Retry"` button.

### Success sub-states (sections independent)

| Section | Empty | Populated |
|---------|-------|-----------|
| Archive banner | Hidden (`mostUrgentArchiveNotice` null) | Single most-urgent connection warning |
| Poll-Pair | Hidden | One `PollPairSuggestion` |
| I'm down for… | Only `"Set what you're down for"` chip | Intent chips + `"Edit intents"` |
| Overlap lines | Hidden | `"You and {firstName} are both available right now!"` per peer |
| Reconnect | Hidden | Up to 3 `ReconnectReminderCard` |
| Event reminders | Hidden | `HomeEventReminderCard` per due RSVP beacon |
| Recent Connections | `GlassCard` empty state | Up to 5 connections grouped by location |
| Connection Insights | Hidden if `totalConnections == 0` or null insights | Collapsed/expanded card |
| Stats | Always shown in Success | Two `GlassStatCard` values |

### LocationGroupCard

| State | Visual |
|-------|--------|
| Collapsed | Chevron 0°, primary border off |
| Expanded | Chevron 90°, `usePrimaryBorder = true`, animated child rows |

### ConnectionArchiveWarningBanner urgency

| `notice.urgent` | Gradient border alpha |
|-----------------|----------------------|
| `true` | 0.85 |
| `false` | 0.65 |

### Poll-Pair / Icebreaker button

| State | Label |
|-------|-------|
| Enabled | `"Icebreaker"` |
| Cooldown | `"Icebreaker ({sec}s)"` |
| Disabled | Same as cooldown (outlined, dimmed icon) |

---

## 4. Micro-copy (quoted from code)

### Scaffold & headers

| Key | String |
|-----|--------|
| Tab route title | `"Home"` |
| Welcome subtitle | `"Welcome back, {user.name ?: "User"}!"` |
| Section: availability | `"I'm down for…"` |
| Section: reconnect | `"Reconnect"` |
| Reconnect helper | `"Connections you haven't talked to in a while"` |
| Section: events | `"Event reminders"` |
| Section: recent | `"Recent Connections"` |
| Section: stats | `"Your Stats"` |
| Stat label 1 | `"Total Clicks"` |
| Stat label 2 | `"Locations"` |

### Empty recent connections

| Element | String |
|---------|--------|
| Title | `"No Connections Yet"` |
| Body | `"Start making connections by tapping Add Click"` |

### Availability strip

| Element | String |
|---------|--------|
| Empty CTA chip | `"Set what you're down for"` |
| With intents | `"Edit intents"` |
| Fallback intent label | `"Intent"` |
| Overlap line | `"You and {name} are both available right now!"` |
| Peer fallback | `"them"` |

### Poll-Pair card (`PollPairCard`)

| Element | String |
|---------|--------|
| Eyebrow | `"Poll-Pair"` |
| Headline | `"It's been a while! Say hi to {displayName}"` |
| `displayName` fallback | `"your click"` |
| Subtitle (0 days) | `"No recent messages — say hi?"` |
| Subtitle (1 day) | `"1 day since you last chatted"` |
| Subtitle (N days) | `"{N} days since you last chatted"` |
| Primary CTA | `"Open chat"` |
| Secondary CTA | `"Icebreaker"` / `"Icebreaker ({n}s)"` |

### ConnectionArchiveWarningBanner

| Element | String |
|---------|--------|
| Headline (pending, urgent) | `"Say hi soon"` |
| Headline (pending, normal) | `"New connection"` |
| Headline (active idle, urgent) | `"Reconnect soon"` |
| Headline (active idle, normal) | `"Stay in touch"` |
| Title line | `"Check in with {chatLabel}"` |
| Body template (pending) | `"About {time} left until your connection with {who} is archived if no one sends a message."` |
| Body template (idle) | `"About {time} left until your chat with {who} may be archived without new messages."` |
| `who` fallback | `"this connection"` |
| Primary CTA | `"Open chat"` |
| Secondary CTA | `"Icebreaker"` / `"Icebreaker ({n}s)"` |

### ReconnectReminderCard

| Element | String |
|---------|--------|
| Name fallback | `"Someone"` |
| Subtitle | `"{daysSinceContact} days since last chat"` |
| Dismiss | `"Dismiss"` |
| Primary | `"Say hi"` |

### HomeEventReminderCard

| Kind | Title (`eventReminderTitle`) | Body (`eventReminderBody`) |
|------|------------------------------|----------------------------|
| Day-of | `"Event today"` | `"{label} starts today — tap to view on the map."` |
| One hour before | `"Event starting soon"` | `"{label} starts in about an hour."` |
| Label fallback | — | `"Your event"` |
| Dismiss | `"Dismiss"` | — |

### LocationGroupCard

| Element | String |
|---------|--------|
| Connection count | `"{n} connection"` / `"{n} connections"` |
| Location fallback | `"Somewhere New"` |
| Chevron a11y | `"Collapse"` / `"Expand"` |

### ConnectionRowItem

| Element | String |
|---------|--------|
| Time: &lt;1 min | `"Just now"` |
| Time: minutes | `"{n}m ago"` |
| Time: hours | `"{n}h ago"` |
| Time: days (&lt;7) | `"{n}d ago"` |
| Time: older | `"{Mon} {day}"` |
| Name fallback | `"Connection"` |
| Nudge target fallback | `"them"` |
| Nudge icon a11y | `"Nudge"` |
| Chat icon a11y | `"Open chat"` |

### ConnectionInsightsCard

| Element | String |
|---------|--------|
| Title | `"Connection Insights"` |
| Collapse a11y | `"Collapse"` / `"Expand"` |
| Quick stat labels | `"Keep Rate"`, `"Active"`, `"Need Attention"` |
| Detail rows | `"Total Connections"`, `"Connections Kept"`, `"Longest Connection"`, `"New This Week"`, `"New This Month"` |
| Longest value | `"{days} days"` optional `" ({name})"` |

### Snackbar / toast messages (`HomeViewModel.nudgeResult`)

| Trigger | String |
|---------|--------|
| Nudge success | `"Nudge sent to {otherUserName}! 👋"` |
| Nudge fail | `"Failed to send nudge"` |
| Chat not found | `"Unable to send nudge — chat not found"` |
| Icebreaker success | `"Icebreaker sent to {name}!"` |
| Icebreaker fail | `"Failed to send icebreaker"` |
| Icebreaker cooldown | `"Icebreaker on cooldown — {n}s"` |
| Chat open fail | `"Couldn't open chat"` |

### AvailabilitySheet entry (from Home)

Home opens sheet with strings documented in AvailabilitySheet: `"Share availability"` / `"Edit availability"`, `"Post"` / `"Save"` / `"Saving…"`, etc.

---

## 5. Flow Sequence

### Happy path — returning user

```
Open Home tab
  → Loading shimmer (brief)
  → Success scaffold: "Welcome back, {name}!"
  → Scroll feed: archive banner → poll-pair → intents → reconnect → events → recent → insights → stats
```

### Availability intent edit

```
Tap any "I'm down for…" chip or "Edit intents"
  → availabilityViewModel.resetAvailabilityIntentSheet()
  → AvailabilitySheet modal
  → User posts/edits intent → dismiss
  → refreshHomeAvailabilityIntents()
  → Chips + overlap lines update
```

### Nudge from recent connection row

```
Expand location group → tap Nudge icon
  → sendNudgeByConnectionId
  → Snackbar: "Nudge sent to {name}! 👋"
  → Message content in chat: "👋 {currentUser.name} nudged you!"
```

### Icebreaker from Poll-Pair or archive banner

```
Tap "Icebreaker" (cooldown == 0)
  → Fetch/ensure chat → pick contextual prompt → sendMessage
  → Snackbar: "Icebreaker sent to {name}!"
  → 15s cooldown arms on both Poll-Pair and archive banner buttons
```

### Error recovery

```
HomeState.Error
  → User reads message (offline or session)
  → Tap "Retry"
  → AppDataManager.refresh(force = true)
  → Re-enter Loading → Success when data returns
```

### Archive banner priority

```
Every 60s tick + connection list change
  → mostUrgentArchiveNotice among active connections
  → Show single banner for shortest remaining archive window
  → urgent if ≤12h remaining
```

---

## 6. A11y & Responsive

| Area | Behavior |
|------|----------|
| Archive banner | `semantics(mergeDescendants = true)` with full action summary |
| Section headers | Gradient text; no separate heading role — rely on visual hierarchy |
| Location expand | `contentDescription` on chevron: `"Expand"` / `"Collapse"` |
| Nudge / chat icons | Explicit `contentDescription` on `ConnectionRowItem` |
| Error icon | `contentDescription = null` (decorative; title carries meaning) |
| Empty state icon | `contentDescription = null` |
| Snackbar | Material `SnackbarHost` at bottom center, 16dp above safe area |
| iOS vs Android | `LocalPlatformStyle` on row corners (14dp iOS / 12dp Android); archive/Poll-Pair shadow elevation Android-only |
| Horizontal scroll | Availability chips use `horizontalScroll` — swipe accessible on both platforms |
| Reduced motion | Chevron and expand animations use standard Compose tweens; no reduced-motion gate in code |

**Focus order (Success):** Top scaffold actions → scrollable list top-to-bottom → floating snackbar when shown.

**Screen reader:** Welcome subtitle is visible text in scaffold header. Dynamic counts in location groups are plain text, not live regions.

---

## Related documents

- [06-connect-handshake.md](06-connect-handshake.md) — Add Click entry from empty state CTA
- [07-connections-inbox.md](07-connections-inbox.md) — chat destination from row taps
- [13-availability.md](13-availability.md) — full AvailabilitySheet spec
