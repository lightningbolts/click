# `calendar/` — Device calendar read & availability overlap

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Click reads the **device calendar** (never writes to it) to help users find mutual free time with connections — powering availability intents and smart scheduling without becoming another calendar app.

---

## Purpose

The `calendar/` package implements a **read-only free/busy pipeline**:

1. **Platform calendar access** via `expect class CalendarProvider`.
2. **Busy block models** for serialization and overlap math.
3. **Availability overlap** helpers combining local busy data with connection intent windows.
4. **Intent lock** semantics to avoid calendar permission prompt spam.

Calendar data stays **on-device** unless the user explicitly broadcasts availability through Click's own intent system.

---

## Architecture

```
ui/components/AvailabilitySheet
        │
        ▼
viewmodel/AvailabilityViewModel
        │
        ├── calendar/CalendarProvider (expect/actual)
        │       Android: CalendarContract
        │       iOS: EventKit
        │
        ├── calendar/CalendarAvailability.kt
        ├── util/AvailabilityIntentOverlap.kt
        └── util/AvailabilityOverlapCache.kt

calendar/CalendarModels.kt ── BusyBlock, CalendarFreeBusy, gaps
calendar/CalendarSyncSession.kt ── rolling window sync orchestration
calendar/CalendarIntentLock.kt ── permission prompt debounce
```

### `CalendarProvider` (expect/actual)

```kotlin
expect class CalendarProvider() {
    fun getAccessStatus(): CalendarAccessStatus
    fun requestReadAccess()
    suspend fun fetchBusyBlocks(windowStartEpochMs, daysAhead = 7): CalendarFreeBusy?
}
```

| `CalendarAccessStatus` | Behavior |
|------------------------|----------|
| `Granted` | `fetchBusyBlocks` returns data |
| `NotDetermined` | Call `requestReadAccess()` first |
| `Denied` / `Restricted` | Returns `null`; UI shows settings CTA |

### Models (`CalendarModels.kt`)

| Type | Description |
|------|-------------|
| `BusyBlock` | `{ start_ms, end_ms }` contiguous busy interval |
| `CalendarFreeBusy` | Window + list of busy blocks |
| `AvailabilityOverlapGap` | Mutual free gap ≥ `minDurationMs` |
| `CalendarFreeBusyBroadcast` | Serializable shape for optional server broadcast |

### Supporting files

| File | Role |
|------|------|
| `CalendarAvailability.kt` | High-level "is user free at T?" helpers |
| `CalendarSyncSession.kt` | Manages rolling 7-day fetch lifecycle |
| `CalendarIntentLock.kt` | Prevents repeated permission modals per session |

### Overlap with `util/`

| Util file | Role |
|-----------|------|
| `AvailabilityIntentOverlap.kt` | Intersect intent windows with busy blocks |
| `AvailabilityOverlapCache.kt` | Memoize overlap results per connection pair |

### Relationship to `events/`

| Module | Scope |
|--------|-------|
| `events/` | **Public** map event beacons — scheduled gatherings |
| `calendar/` | **Private** device calendar — personal busy time |

Event reminders (`events/EventReminderCoordinator`) do not read the device calendar; calendar module helps **availability intents** and "find time to meet" flows.

---

## Constraints

1. **Read-only** — never create/modify OS calendar events from Click (product trust boundary).
2. **Permission-gated** — `fetchBusyBlocks` returns `null` without grant; UI must degrade gracefully.
3. **7-day default window** — balances usefulness vs battery; extending requires platform audit.
4. **Epoch milliseconds UTC** — `BusyBlock` uses UTC ms; overlap math must not mix time zones incorrectly.
5. **No raw calendar titles on wire** — only busy intervals leave the provider; event titles stay on-device.
6. **Ghost mode** — calendar reads are local-only and unaffected, but broadcasting intents may be paused upstream.

---

## Related files

| Path | Relationship |
|------|--------------|
| `calendar/CalendarProvider.kt` | expect declaration |
| `androidMain/.../CalendarProvider.android.kt` | Android actual |
| `iosMain/.../CalendarProvider.ios.kt` | iOS actual |
| `ui/utils/CalendarPermissionRequester.kt` | Composable permission hook |
| `ui/components/CalendarOverlapBentoCard.kt` | UI showing mutual free gaps |
| `ui/components/AvailabilitySheet.kt` | Intent editor with calendar hints |
| `viewmodel/AvailabilityViewModel.kt` | Orchestrates fetch + intent save |
| `events/EventSchedule.kt` | Event beacon scheduling (separate) |
| `events/EventReminderCoordinator.kt` | Event reminders (not device calendar) |

---

## What Click Users Experience

### Connect in person (Tri-Factor) / Scan QR / Group connect
Calendar not involved at handshake time.

### Private encrypted chat / Media / Reactions / Typing / Calls
Standard chat — calendar may suggest meeting times in availability flows from chat profile.

### Memory Capsules
Independent of calendar.

### 48-hour gentle archive
Connection lifecycle — not calendar.

### Connection map & timeline
Timeline shows past meets; calendar helps **future** scheduling.

### Rate the vibe
Unrelated.

### QR identity card
Unrelated.

### Availability intents
**Primary calendar UX.** When editing availability (`AvailabilitySheet`), users with calendar permission see **overlap hints** — when both parties are free based on device busy blocks + declared intents. `CalendarOverlapBentoCard` visualizes mutual gaps.

### Match alerts
Not calendar-driven.

### Community Hubs / Map beacons
Event beacons use `events/` schedules, not device calendar reads.

### Global search
Intent matches appear in search; calendar overlap is behind the scenes.

### Core connections / Collaboration / Ghost mode / Block & report
Unrelated or gated upstream.

### Profile & interests
Interests inform intent labels; calendar informs timing.

### Onboarding / Google-email auth
Calendar permission requested in Settings or first availability edit — `CalendarPermissionRequester`.

### Push notifications
No calendar-triggered pushes today; event reminders are separate (`events/`).

### Deep links & App Clip / Web dashboard / Business insights
Outside calendar module.

### Event reminders
**Distinct feature.** `events/EventReminderCoordinator` fires for **map event beacons** the user RSVP'd to — not for personal calendar entries. Users may receive both:

- Click event beacon reminder (from `events/`)
- Their own OS calendar alert (if they manually added an event)

Click does not sync these automatically.

### Achievements & stats
Potential future "scheduled N hangouts" — not in calendar module today.
