# `events/` — Event schedules, validation & reminders

> **Anti-doomscrolling · Stop scrolling, start living.**  
> Map **event beacons** are time-bounded real-world gatherings. The `events/` package models their schedules, validates user input, and coordinates **gentle reminders** so users show up without notification spam.

---

## Purpose

`events/` provides **pure Kotlin** (no UI, no I/O) utilities for:

1. **Event schedule modeling** — start/end instants, metadata JSON serialization.
2. **Validation** — prevent impossible or abusive event windows.
3. **Reminder scheduling logic** — which reminder kinds are due in a cron/window.
4. **Coordinator** — in-memory index of event beacons for Home + notification surfaces.

Event beacons are a `MapBeaconKind.EVENT` variant — geographic pins with a structured schedule in beacon metadata.

---

## Architecture

```
ui/screens/BeaconDropSheet ──► EventSchedule + validateEventSchedule()
        │
        ▼
MapBeaconRepository ──► beacon metadata JSON
        │
        ▼
events/EventReminderCoordinator.syncBeacons()
        │
        ├──► HomeViewModel / HomeScreen (reminder cards)
        └──► Push / local notification (future hooks)

events/EventSchedule.kt ── pure functions
events/EventReminderCoordinator.kt ── singleton index
```

### `EventSchedule`

```kotlin
data class EventSchedule(
    val startEpochMs: Long,
    val endEpochMs: Long,
)
```

| Helper | Role |
|--------|------|
| `validateEventSchedule(...)` | Returns `EventScheduleValidationError?` |
| `eventScheduleMetadata(schedule)` | JSON keys `event_start_at`, `event_end_at` (ISO-8601) |
| `parseEventScheduleFromMetadata(raw)` | Reverse parse from beacon metadata |
| `isEnded(now)` / `isVisible(now)` | Lifecycle checks |
| `formatEventScheduleRange(...)` | UI string e.g. `Jun 12, 7:00 PM – 9:00 PM` |
| `defaultEventSchedule(...)` | Next whole hour + 2h block, ≥45 min lead |
| `roundEpochToNextWholeHour(...)` | Picker-friendly start times |

### Validation rules (`EventScheduleValidationError`)

| Error | Rule |
|-------|------|
| `EndBeforeStart` | `endEpochMs <= startEpochMs` |
| `StartInPast` | Start more than 60s in the past |
| `DurationExceedsOneMonth` | Duration > 30 days (`MAX_EVENT_DURATION_MS`) |

### Reminder kinds (`EventReminderKind`)

| Kind | When due |
|------|----------|
| `DayOf` | Start of local day containing event start (15-min window) |
| `OneHourBefore` | 60 minutes before start (15-min window) |

`eventReminderKindsDue(schedule, now, windowMs = 15min)` returns the set of kinds that should fire — designed for periodic cron or app foreground ticks.

Copy helpers:

- `eventReminderTitle(kind, description)`
- `eventReminderBody(kind, description, eventTitle?)`

### `EventReminderCoordinator`

Singleton in-memory `linkedMapOf<String, MapBeacon>` of **EVENT** beacons.

| API | Role |
|-----|------|
| `syncBeacons(beacons)` | Bulk ingest from map refresh |
| `rememberBeacon(beacon)` | Single beacon upsert |
| `homeReminders(rsvpIds, userId, dismissedKeys)` | Build sorted `HomeEventReminder` list |

**Eligibility for reminders:**

- Beacon kind is `EVENT`
- Schedule not ended
- User RSVP'd **or** user created the beacon
- Reminder kind due per `eventReminderKindsDue`
- Not in `dismissedKeys` set (`"{beaconId}:{kind}"`)

### `MapBeacon` extensions (same file)

```kotlin
fun MapBeacon.eventSchedule(): EventSchedule?
fun MapBeacon.isVisibleEventBeacon(now): Boolean
fun MapBeacon.isActiveForDiscoveryFeed(now): Boolean
```

Events remain visible until **schedule end**, not merely raw beacon TTL — preventing premature disappearance of multi-hour gatherings.

---

## Constraints

1. **Pure schedule logic** — no network, no `TokenStorage`; coordinator holds only in-memory beacon copies (rehydrated on map sync).
2. **15-minute reminder window** — tolerant to background execution jitter; do not tighten without platform notification audit.
3. **UTC day bucketing for DayOf** — `startOfLocalDayEpochMs` uses UTC day boundaries (documented as sufficient for cron + in-app reminders).
4. **Dismissed keys are client-owned** — persist dismissal in UI layer / `TokenStorage` if needed across restarts.
5. **Max duration 30 days** — aligns with map abuse prevention; changing requires backend agreement.

---

## Related files

| Path | Relationship |
|------|--------------|
| `events/EventSchedule.kt` | Models, validation, formatting, reminder due logic |
| `events/EventReminderCoordinator.kt` | Beacon index + `homeReminders` |
| `data/models/MapBeacon.kt` | `MapBeaconKind.EVENT`, metadata |
| `viewmodel/MapViewModel.kt` | Loads beacons → `syncBeacons` |
| `viewmodel/HomeViewModel.kt` | Surfaces `HomeEventReminder` cards |
| `ui/screens/HomeScreen.kt` | Renders event reminder UI |
| `ui/screens/BeaconDropSheet.kt` | Event creation picker |
| `ui/components/EventDateTimePicker.kt` | Schedule selection UI |
| `calendar/CalendarProvider.kt` | Optional overlap with personal calendar (separate module) |
| `data/storage/TokenStorage.kt` | `saveBeaconRsvpSnapshot` for RSVP persistence |

---

## What Click Users Experience

### Connect in person (Tri-Factor) / Scan QR / Group connect / Chat features
Not directly in `events/` — but event beacons may be discovered **after** connecting at a venue.

### Private encrypted chat / Media / Reactions / Typing / Calls
Standard chat — event beacons link users to map location, not E2EE threads.

### Memory Capsules
Separate feature — events are scheduled map objects, not memory capsules.

### 48-hour gentle archive
Applies to **connections**, not event beacons. Events use schedule end visibility.

### Connection map & timeline
Event attendance may appear in social context; timeline is connection-scoped.

### Rate the vibe
Independent of event scheduling.

### QR identity card
Organizers share profile QR; event beacon is a separate map drop.

### Availability intents
Complementary — intents are personal free-time signals; events are public map objects.

### Match alerts
Not event-specific.

### Community Hubs
Hubs may host events; beacon `venue_id` in QR can tie to hub coordinates.

### Map beacons
**Primary UX surface.** Users create events via `BeaconDropSheet` with validated `EventSchedule`, see `formatEventScheduleRange` on cards, and discover active events via `isActiveForDiscoveryFeed`.

### Global search
`GlobalSearchViewModel` includes beacon matches with distance sorting.

### Core connections / Collaboration / Ghost mode / Block & report
Unrelated to event module.

### Profile & interests / Onboarding / Auth
Standard app flows.

### Push notifications
`EventReminderKind` titles/bodies feed notification copy; delivery wired at app shell.

### Deep links & App Clip
Event deep links (if configured) route to map beacon id — router outside `events/`.

### Web dashboard
Business users may create events on web; mobile consumes synced beacons.

### Business insights
Event RSVP counts and attendance analytics on web/backend.

### Event reminders
**Core feature.** Users who RSVP or create an event see:

- **"Event today"** — morning of the event day
- **"Event starting soon"** — ~1 hour before start

Reminders appear on **Home** and can extend to push. Tapping navigates to map beacon detail.

### Achievements & stats
Future: attend N events — not implemented in `events/` today.
