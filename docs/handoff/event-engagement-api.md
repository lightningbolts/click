# Handoff — Event engagement API (bookmarks, shares, check-ins, connection integration)

**Date:** 2026-07-18  
**Product:** Click KMP mobile (`click/composeApp`) + click-web API  
**Audience:** Backend / full-stack implementer  
**UX truth:** [../ui-ux/mobile/10-map-beacons-hubs.md](../ui-ux/mobile/10-map-beacons-hubs.md) (§ Event detail + §7 Future)  
**Client placeholders today:** `EventLocalFlagsStore` (in-memory bookmark / check-in), `shareText` (OS share sheet only), RSVP already on click-web

Read this before designing schema or routes. Prefer **one vertical slice** (e.g. bookmarks end-to-end) before stacking check-in + encounter linking.

---

## 0. Why this exists

Event detail UI already ships:

| Action | Client today | Server today |
|--------|--------------|--------------|
| **RSVP / Sign Up** | `MapViewModel` → `ApiClient.get/post/deleteBeaconRsvp` | **Done** — `/api/beacons/{id}/rsvp` |
| **Share** | `shareText(...)` system sheet (title + schedule + maps HTTPS URL) | No analytics / deep-link short URL / in-app share-to-connection |
| **Bookmark** | `EventLocalFlagsStore.toggleBookmark` — **process-local**, lost on kill | **None** |
| **Check in** | `EventLocalFlagsStore.toggleCheckIn` — **process-local** | **None** |
| **Event ↔ encounter** | Documented intent only ([10-map-beacons-hubs.md](../ui-ux/mobile/10-map-beacons-hubs.md) §7) | **None** |

Goal: persist engagement, keep mobile UX unchanged (same hero buttons), and unlock Home “saved events”, attendance signals, and Timeline/encounter context.

---

## 1. Product semantics (do not collapse these)

| Concept | User meaning | Distinct from |
|---------|--------------|---------------|
| **RSVP** | “I’m going / signed up” ahead of or during the event | Bookmark; check-in |
| **Bookmark** | “Save for later” — private interest, not attendance | RSVP; does not imply going |
| **Check-in** | “I’m here now” — on-site presence signal (geofence recommended) | RSVP alone; may require live window |
| **Share** | Distribute event to OS / eventually a Click connection | Not a server flag on the beacon for the sharer unless analytics desired |
| **Encounter link** | Handshake/QR at a live event attaches `beacon_id` to the connection moment | Not an RSVP; both users need not have RSVP’d (v1) |

Client UI already treats bookmark and check-in as independent toggles; API should too.

---

## 2. Existing contracts to reuse

### 2.1 Auth

Same click-web session / JWT as beacon RSVP (`ensureClickWebAuthReady` on mobile). All new routes: authenticated user required unless noted.

### 2.2 Beacon identity

- Primary key: `beacon_id` (string UUID / existing map beacon id).
- Events are `MapBeaconKind.EVENT` with schedule in metadata (`event_start_at` / `event_end_at` or legacy TTL).
- Mobile resolves beacons via map feed + `EventReminderCoordinator` (in-memory index).

### 2.3 RSVP (already shipped — mirror patterns)

| Method | Path | Body / notes |
|--------|------|----------------|
| GET | `/api/beacons/{beaconId}/rsvp` | Returns `attendees[]`, `current_user_signed_up` |
| POST | `/api/beacons/{beaconId}/rsvp` | Optional `{ latitude, longitude }` |
| DELETE | `/api/beacons/{beaconId}/rsvp` | Cancel |

DTOs: `BeaconAttendeeDto` (`user_id`, `name`, `avatar_url`), `BeaconRsvpGetResponseDto`, etc. in `ApiClient.kt`.

**New endpoints should follow the same JSON style** (`snake_case` serial names, `ok` on mutating responses where used).

---

## 3. Proposed API — Bookmarks

### 3.1 Behavior

- Per-user, per-beacon boolean (or soft-delete row).
- Idempotent PUT/POST toggle or explicit set.
- List endpoint for Home / “Saved events” (not built in UI yet — design for it).

### 3.2 Suggested routes

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/me/event-bookmarks` | Paginated list of bookmarked event beacons (id + denormalized title/start/end/lat/lon for offline-ish Home) |
| GET | `/api/beacons/{beaconId}/bookmark` | `{ bookmarked: boolean }` for detail sheet hydrate |
| PUT | `/api/beacons/{beaconId}/bookmark` | Body `{ bookmarked: true \| false }` — idempotent set |
| DELETE | `/api/beacons/{beaconId}/bookmark` | Equivalent to `bookmarked: false` |

### 3.3 Schema sketch

```text
event_bookmarks
  user_id     uuid  PK (or composite)
  beacon_id   text  PK
  created_at  timestamptz
  updated_at  timestamptz
```

Indexes: `(user_id, created_at DESC)`, unique `(user_id, beacon_id)`.

### 3.4 Mobile wiring (after API)

1. Replace `EventLocalFlagsStore` bookmark path with repository + `StateFlow` cache (keep local optimistic toggle).
2. Hydrate on `EventBeaconDetail` open (parallel to `loadBeaconRsvp`).
3. Persist across process death (TokenStorage snapshot optional, same pattern as `BeaconRsvpPersistence`).
4. Optional: Home section “Saved events” using GET list.

---

## 4. Proposed API — Check-ins

### 4.1 Behavior

- Means **on-site now**, not “interested.”
- Prefer requiring:
  - Event schedule `isLive` (or within a short grace after start), **and**
  - Client GPS within beacon radius (reuse hub/event geofence distance; RSVP POST already accepts lat/lon).
- Server should **validate** lat/lon when provided (reject far check-ins); do not trust client-only toggles for social proof.
- Check-in may auto-clear when event ends (or keep historical `checked_in_at` for Timeline).

### 4.2 Suggested routes

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/beacons/{beaconId}/check-in` | `{ checked_in: boolean, checked_in_at?, nearby_count? }` |
| POST | `/api/beacons/{beaconId}/check-in` | Body `{ latitude, longitude }` — create/refresh check-in |
| DELETE | `/api/beacons/{beaconId}/check-in` | Leave / undo |

Optional aggregate for detail UI: include `check_in_count` on GET RSVP or a small `GET .../engagement` bundle to avoid N+1.

### 4.3 Schema sketch

```text
event_check_ins
  user_id        uuid
  beacon_id      text
  checked_in_at  timestamptz
  latitude       double precision  null
  longitude      double precision  null
  source         text  -- 'mobile' | …
  PRIMARY KEY (user_id, beacon_id)
```

### 4.4 Errors (mobile copy hooks)

| Status | Meaning | Client hint |
|--------|---------|-------------|
| 400 | Missing / invalid coords | Keep local UI; snackbar “Location required to check in” |
| 403 | Outside geofence | “Move closer to the event to check in” |
| 409 | Event not live | “Check-in opens when the event starts” |
| 404 | Unknown beacon | Dismiss sheet / refresh map |

### 4.5 Mobile wiring

- Optimistic UI OK; rollback on 403/409.
- Do **not** treat check-in as RSVP; if product wants “check-in implies RSVP,” do that explicitly server-side and document it.

---

## 5. Proposed API — Shares

### 5.1 Current

`shareText` builds a plain-text blob (title, schedule range, distance optional, Google/Apple maps HTTPS). No server round-trip.

### 5.2 Optional server pieces (pick what you need)

| Capability | Suggestion |
|------------|------------|
| **Analytics** | `POST /api/beacons/{id}/share-events` `{ channel: "system_sheet" \| "connection", target_user_id? }` — fire-and-forget |
| **Deep link** | Canonical HTTPS `https://…/e/{beaconId}` that opens app / web event page; include in share text instead of raw maps URL only |
| **In-app share to connection** | Reuse chat send with a structured event attachment (future); needs message type + decrypt story — **out of scope for v1** unless product insists |

Minimum for v1 API work: **deep-link URL format + docs**; analytics optional.

---

## 6. Connection / encounter integration

See UX §7. Implementation outline:

### 6.1 Trigger (client + server)

On successful Tap / QR / App Clip handshake:

1. Client has GPS + timestamp.
2. Query “live events near me” (existing map/discovery feed or `GET /api/events/live?lat=&lon=&radius_m=`).
3. If exactly one strong match (or user confirms), attach `beacon_id` to the new encounter / connection moment.

### 6.2 Persist

| Field on encounter / connection_moment | Notes |
|----------------------------------------|-------|
| `event_beacon_id` | FK / text |
| `event_title` | Denormalized for Timeline if beacon expires |
| `event_start_at` / `event_end_at` | Denormalized |
| `linked_at` | Handshake time |

### 6.3 Surface (mobile — already scoped in UX)

- `ProfileConnectionMoment` / Timeline: event title, time range, “View on map” → `pendingBeaconId` + Map tab (same as Featured Event).
- Non-goals v1: auto-RSVP; requiring mutual RSVP.

### 6.4 API sketch

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/encounters/{id}/event-link` | Body `{ beacon_id }` — validate live+geo server-side |
| GET | encounter payloads | Include optional `event` object in existing moment DTO |

Coordinate with handshake APIs in click-web; do not invent a second encounter store on mobile.

---

## 7. Bundle endpoint (optional, recommended for mobile)

To avoid three round-trips when opening `EventBeaconDetail`:

```http
GET /api/beacons/{beaconId}/engagement
```

```json
{
  "beacon_id": "...",
  "bookmarked": false,
  "checked_in": false,
  "checked_in_at": null,
  "rsvp": {
    "current_user_signed_up": false,
    "attendees": []
  },
  "check_in_count": 0
}
```

Mobile can keep calling RSVP separately initially; migrate when ready.

---

## 8. Client migration plan (ordered)

1. **Bookmarks API + hydrate/toggle** — replace `EventLocalFlagsStore` bookmark; keep check-in local until step 2.
2. **Check-in API + geofence errors** — replace local check-in; wire snackbars.
3. **Share deep link** — update `buildEventShareText` only.
4. **Engagement bundle** — collapse loads.
5. **Encounter event-link** — handshake + Timeline (separate chat/PR; depends on moment schema).

Do not remove `EventLocalFlagsStore` until both bookmark and check-in have server backing **or** split the store into two facades.

---

## 9. Security / privacy checklist

- [ ] Bookmarks private to user (no public “X bookmarked this”).
- [ ] Check-in attendees: decide visibility (creator only vs RSVP peers vs public count-only). Default recommendation: **count public, identity same as RSVP attendee rules**.
- [ ] Rate-limit check-in POST; require finite lat/lon; reject (0,0).
- [ ] Soft-delete beacons: engagement rows should 404 or cascade cleanly.
- [ ] RLS / authz: user can only mutate own bookmark/check-in rows.

---

## 10. Test plan (API + mobile)

**API**

- [ ] Bookmark set/unset idempotent; list returns only caller’s rows
- [ ] Check-in rejected when far / not live; accepted when inside radius during live window
- [ ] RSVP unchanged (regression on existing `/rsvp` tests)
- [ ] Auth missing → 401

**Mobile (device)**

- [ ] Event detail: bookmark survives force-kill after API
- [ ] Check-in far from pin → error copy, toggle reverts
- [ ] Share still opens system sheet; deep link opens Map focus when implemented
- [ ] Featured Event / search → map still pans/zooms and **stays** on event after settle
- [ ] Timeline shows linked event only after encounter-link ships

**Gates:** `./gradlew :composeApp:compileDebugKotlinAndroid` + relevant click-web `npm test` for new routes.

---

## 11. Out of scope / non-goals

- Redesigning Event detail hero layout
- Replacing RSVP with check-in
- Full social graph “who bookmarked”
- Push notifications for bookmarks (can follow later)
- Editing `InteractiveSwipeBackContainer` for vertical dismiss (Events list uses vertical **enter/exit animation**; dismiss gesture remains horizontal swipe-back)

---

## 12. File pointers (mobile)

| Area | Path |
|------|------|
| Local flags | `composeApp/.../events/EventLocalFlagsStore.kt` |
| Detail UI | `MapScreen.kt` → `EventBeaconDetail` / `EventHeroActions` |
| Share | `platform/ShareText.kt` + `buildEventShareText` in `MapScreen.kt` |
| RSVP client | `data/api/ApiClient.kt` (`get/post/deleteBeaconRsvp`) |
| RSVP cache | `data/storage/BeaconRsvpPersistence.kt` |
| Home → map focus | `App.kt` `pendingBeaconId`, `MapViewModel.focusBeaconOnMap` |
| Encounter UX intent | `docs/ui-ux/mobile/10-map-beacons-hubs.md` §7 |

---

## 13. Acceptance for “API done”

- Documented OpenAPI or click-web route handlers for bookmark + check-in (and optional engagement GET).
- Mobile can delete local-only bookmark/check-in persistence for those two actions.
- One integration test per mutating route.
- Product sign-off on check-in geofence radius and attendee visibility.
