**Status:** archived (as of 2026-08-11) — not authoritative; see `docs/archive/README.md`.

# Handoff — Event engagement API (bookmarks, shares, check-ins, connection integration)

**Date:** 2026-07-18  
**Product:** Click KMP mobile (`click/composeApp`) + click-web API  
**Audience:** Backend / full-stack / insights  
**UX truth:** [../ui-ux/mobile/10-map-beacons-hubs.md](../ui-ux/mobile/10-map-beacons-hubs.md)  
**Status:** **Shipped** — bookmarks, check-ins, impressions, RSVP telemetry, venue-scale geofence, click-web Event engagement charts. Migration applied on Supabase project `click` (`event_engagement`).

Share deep links and encounter↔event linking remain future (see § Still future).

---

## 0. Semantics (do not collapse)

| Concept | User meaning | Distinct from |
|---------|--------------|---------------|
| **RSVP** | “I’m going / signed up” | Bookmark; check-in |
| **Bookmark** | “Save for later” — private | RSVP; not attendance |
| **Check-in** | “I’m here now” — on-site + geofence | RSVP alone |
| **Share** | OS share sheet (no deep link yet) | Not a server flag unless analytics `share` event |
| **Impression** | Detail sheet opened (`event_view`) | Not interest/attendance |

---

## 1. Shipped API surface

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/me/event-bookmarks` | Caller’s list + denorm title/schedule/`location_name`/`formatted_address`/`event_categories`/lat/lon |
| GET/PUT/DELETE | `/api/beacons/{id}/bookmark` | Idempotent set; telemetry `bookmark_set` / `bookmark_unset` |
| GET/POST/DELETE | `/api/beacons/{id}/check-in` | GPS + live window + venue-scale fence; `check_in` / `check_out` / `check_in_rejected` |
| GET | `/api/beacons/{id}/engagement` | `{ bookmarked, checked_in, checked_in_at, check_in_count }` |
| POST | `/api/beacons/{id}/impressions` | Fire-and-forget `event_view` (2s debounce) |
| GET/POST/DELETE | `/api/beacons/{id}/rsvp` | Unchanged UX; emits `rsvp_set` / `rsvp_unset` + richer attendee dims |
| GET | `/api/beacons/{id}/attendees/directory` | Enriched people directory for every authenticated viewer (distance, shared interests, relationship, FoF `mutual_via`). `mutual_connection_count` = friends-in-common with the viewer (FoF via length, or shared peers for direct Connections). RSVP/check-in only control engagement CTAs; `mutuals_section_unlocked` is available to signed-in directory viewers. |
| GET | `/api/connections/{id}/event-recommendation` | One upcoming event the peer RSVP’d that the viewer hasn’t — for new-connection “Go together?” card |
| GET | `/api/insights/[venueId]/event-engagement` | Aggregates only (website insights) |

Auth: same click-web JWT as RSVP. JSON: `snake_case`, `{ ok: true }` on mutations where applicable.

**Migration:** `click-web/supabase/migrations/20260718140000_event_engagement.sql` (applied remotely as `event_engagement`).

Tables: `event_bookmarks`, `event_check_ins`, `event_engagement_events` (+ RSVP columns on `beacon_attendees`).

---

## 2. Venue scale (check-in geofence)

Creator picks **Check-in area** on event drop (not topic categories):

| `venue_scale` | Radius | Examples |
|---------------|--------|----------|
| `intimate` | 75 m | restaurant, small meetup |
| `neighborhood` | 250 m | park party (default) |
| `venue` | 750 m | club, arena hall |
| `campus` | 2500 m | large campus / festival |

Explicit `check_in_radius_meters` clamped **[25, 5000]**. Event **creation** may use a geocoded address (`location_name` / `formatted_address` in metadata) so creators need not be on-site; pin coords still live in PostGIS. **Check-in** remains GPS + geofence at the venue. Live window = schedule `isLive` + **24 h** early grace. Errors: 400 location / 403 out of bounds / 409 not live.

**Location denied:** no optimistic check-in; snackbar “Location access is required to check in”; optional reject POST for funnel analytics.

Check-in visibility: **public count only** (no attendee identity list). Bookmarks private.

---

## 3. Telemetry / KPIs

Append-only `event_engagement_events` (service-role). Current-state rows stay rich (dwell, accuracy, fence snapshot, `had_rsvp` / `had_bookmark`, platform, etc.).

| KPI | Source |
|-----|--------|
| Impressions / unique viewers | `event_view` |
| Interest rate | bookmarks / impressions |
| RSVP conversion | `rsvp_set` / impressions |
| No-show / walk-up | RSVP vs `check_in` + `had_rsvp` |
| Arrival curve / dwell | `minutes_after_start`, `checked_out_at` |
| Geofence / perm friction | `check_in_rejected` + `reject_reason` |

**Website charts:** `/insights/event-engagement` — funnel, arrival histogram, rejects, dwell. See [click-web `lib/insights/README.md`](../../../click-web/lib/insights/README.md).

---

## 4. Mobile wiring

| Area | Path |
|------|------|
| API + DTOs | `composeApp/.../data/api/ApiClient.kt` |
| Repository | `MapBeaconRepository.kt` |
| Disk cache | `BeaconEngagementPersistence.kt` + `TokenStorage` |
| VM | `MapViewModel` — `loadBeaconEngagement`, `toggleBeaconBookmark`, `toggleBeaconCheckIn`, `recordEventImpression` |
| Detail UI | `MapScreen.kt` → `EventBeaconDetail` / `EventHeroActions` |
| Venue scale | `EventVenueScale.kt` + `BeaconDropSheet` chips |
| Removed | `EventLocalFlagsStore` |

---

## 5. Follow-ups shipped (2026-07-18 continuation)

| Item | Notes |
|------|-------|
| Home “Saved events” | `GET /api/me/event-bookmarks` → Home section after Featured Event |
| Share deep-link | `https://joinclick.co/e/{beaconId}` in share text; `POST /api/beacons/{id}/share`; Universal Links; insights Shares |
| Encounter ↔ event | Per reporting user: RSVP **+ active check-in** + live geofence → `event_beacon_id` + denorm title + `at_event` tag + Timeline / Beacons tab; strip for non-engaged viewers |

## 5b. Still future

- Mobile operator charts (intentionally website-only)

---

## 6. Gates / acceptance

```bash
cd click-web && npm test && npm run build
cd click && ./gradlew :composeApp:compileDebugKotlinAndroid
```

**Last green (2026-07-18):** engagement API base + follow-ups. Apply migration `20260718200000_encounter_event_beacon` on remote Supabase `click` if not yet applied.

**Device smoke (human):** bookmark survives force-kill; far check-in reverts with snackbar; venue-scale on create; location-denied snackbar; insights page loads in demo mode; Saved events on Home; share opens `/e/` link; Timeline / Beacons show event only for viewers with RSVP **and** active check-in (per-person).
