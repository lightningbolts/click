# 06: Post-9/29 features (drops, beacons, events, nudges)

**Status:** Backend and iOS implemented; Android not started · **Date:** 2026-09-29 ·
**Source spec:** "Click Feature Spec — Post-9/29/2026 Ideas"

Every feature below ships **dark** behind a server flag. Android reads the same flags as iOS and
must render server state only: the server decides who may see or do anything. Conventions (`KMP/`,
`AM/`, `iOS/`, `web/`) are in the [README](README.md).

## 0. Before any Android work

1. **Sync migrations** once the click-web branches merge: `scripts/sync-supabase-from-click-web.sh`
   then `scripts/check-supabase-drift.sh`. New migrations (all additive):
   `20260930000000_feature_flags_and_drop_develop`, `20261001000000_alert_confirmations`,
   `20261002000000_soundtrack_presence`, `20261003000000_event_drops_and_history`,
   `20261004000000_shared_drops`, `20261005000000_reconnect_nearby`,
   `20261006000000_pilot_product_events`.
2. **Feature flags.** Add a `FeatureFlags` store in `KMP/` (mirror `iOS/Core/Config/FeatureFlags.swift`):
   `GET /api/me/features` → `{ features: { key: { enabled, config } } }`. Refresh on sign-in and
   foreground, reset on sign-out; unknown or failed flags are **off**. Keys: `drops_develop`,
   `alert_confirmations`, `soundtrack_presence`, `event_drops`, `event_history`, `shared_drops`,
   `reconnect_nearby`, `pilot_analytics`.
3. Contracts for every route are in `click-ios/Docs/BACKEND_CONTRACT_MATRIX.md` and the module
   READMEs `web/lib/drops/README.md`, `web/lib/map/README.md`, `web/lib/nudges/README.md`.

## 1. Click Drop develop state (spec §2, flag `drops_develop`)

**State machine** (put in `commonMain`, mirror `web/lib/drops/developState.ts` and
`iOS/Core/Media/ClickDropDevelop.swift`): `pending` (before reveal: pixelated + countdown) →
`ready` (reveal passed, not developed: quiet, **no badge**) → `developed` (per viewer, server-side).

| Item | Android work | iOS reference |
|---|---|---|
| Develop API | `POST /api/drops/develop {drops:[{kind,id}]}` (≤50) and `GET /api/drops/views?kind&ids` | `ClickDropService` |
| Gated send (flag on) | Chat drop sends a **small pixelated preview** (≤480 px, 12 blocks per side) as the message media, and the E2EE original via `POST /api/chat/media` with `drop_original: true` (response `url: null`). The original uses its own client message ID `"<cmid>.original"`. Message metadata adds `drop_original_path` and `drop_original {epoch, sender_device_id, client_message_id, media_ciphertext_sha256}`; the server stores `drop_gated: true` instead of the path. v1 chats: same, legacy keys, no `drop_original` fields. | `ChatRepository.sendMedia` (`gated`), `ClickDropPixelation.previewJPEG` |
| Receive | `drop_gated == true` ⇒ media is only the preview. After develop, download the signed URL and decrypt with `drop_original` (v2) or legacy keys (v1); cache as `<messageId>-original`. | `ChatRepository.loadDropOriginal`, `ConversationModel.developedDropURL` |
| Tap to develop | Ready drop: tap → develop → pixels resolve (~0.4 s: 24/48/96 blocks, light haptic); Reduce Motion (`ANIMATOR_DURATION_SCALE == 0` / accessibility) → cross-fade. | `ClickDropBubble.swift` |
| Live develop | A drop reaching zero while its chat is open develops automatically. | `ChatView` reveal task |
| Develop all | One "Develop all (N)" action when more than one drop is ready. | `ChatView` overlay |
| Legacy | Flag off **and** not gated ⇒ keep today's auto-reveal unchanged. | `ConversationModel.dropState` |
| Save/share | Only once developed, and save the original. | `ChatView.saveOrShare` |
| Push | `disposable_reveal` now also arrives batched (`drop_count`), still routed by `chat_id`. | — |

Acceptance: a ready drop never shows the original until tapped or live; developed state syncs across
devices; a pending drop's network payload never contains the original.

## 2. Alert confirmations (F4, flag `alert_confirmations`)

On a hazard beacon's detail: "Is it still there?" → **Still here** / **Cleared** (creator: **Take it
down**), plus "Last seen X ago" — never counts. `GET|POST /api/beacons/{id}/confirm {status, lat, lng}`;
map 403 → "You need to be near this alert", 409 → "already confirmed recently", 410 → "already
ended", 400 → "location needed". Location is sent for the range check only. Add a quiet **Report**
(`POST /api/beacons/{id}/report {reason}`) to the beacon menu for non-creators. Default a new
alert's duration slider to 2 h when the flag is on. iOS: `AlertConfirmationSection.swift`,
`Core/Beacons/AlertConfirmations.swift`.

## 3. Listening now (F5, flag `soundtrack_presence`)

A soundtrack beacon is a song link pinned at a place (30 s preview). Under the soundtrack card:
the count and connections' names ("Maya and 3 others are listening"), and an "I'm listening here"
toggle that heartbeats `POST /api/beacons/{id}/listening {lat,lng}` every `heartbeat_seconds` while
the screen is open; toggle off → `DELETE`. 403 → "Get closer to listen here". **Save is not built**
(open decision: the only beacon bookmark feeds Saved events and reminders). iOS:
`ListeningNowSection.swift`, `Core/Beacons/SoundtrackPresence.swift` (summary copy is unit-tested).

## 4. Event drops, recap and history (F1, F2; flags `event_drops`, `event_history`)

| Item | Android work |
|---|---|
| Event detail section | `GET /api/beacons/{id}/drops` → `state` (before/open/developing/revealed), `access` (participant/absentee/none), `can_post`, `remaining`, own pixelated drops. Hide the section when there's nothing for this viewer. |
| Posting | Camera shoots **natural** (no looks); `POST /api/beacons/{id}/drops {client_drop_id, mime_type, original_b64, preview_b64, width, height}`. Keep the photo and `client_drop_id` on failure and offer Retry (same ID ⇒ same drop). 403 → not checked in / window closed; 409 → cap. |
| Own drops | Pixelated thumbnails, long-press Delete (`DELETE …/drops/{id}`), toggle "Show to people who RSVP'd but couldn't make it" (`PUT …/drops/settings`). |
| Recap screen | Opening develops everything (`kind: event`), then plays drops in sequence (own first, outlined); tap left/right, hold to pause, ~4 s each; ends with "See who was there". Each drop's look = `recapLook(seed)`: every look except Natural, index `seed mod 9` in `DisposableRollFilters` order (see `ClickDropFilter.recapLook`). "Natural" toggle shows all untouched. Delete own / quiet Report others' (`POST /api/drops/report`). |
| Push | `event_drop_recap {beacon_id}` → recap screen. |
| History | Me → "Past events": `GET /api/me/event-history?filter=all|went|rsvpd|saved|hosted&cursor`, rows link to the event and (if any) the recap. |
| Home card | `GET /api/me/event-history/recap-card` → one card for an event you were at in the last ~48 h. |
| Profile | "Events together": `GET /api/users/{id}/events-together` (only shared attendance). |

iOS: `EventDropsSection.swift`, `EventRecapView.swift`, `EventHistoryView.swift`,
`EventHistoryCards.swift`, `Core/Beacons/EventDrops.swift`.

## 5. Shared drops (F3, flag `shared_drops`)

A bounded Home strip (never a feed): share tile → Click Drop camera → audience sheet (All / Core
connections) with the copy "Shared drops aren't end-to-end encrypted like chats…" →
`POST /api/me/shared-drops` (retry-safe by `client_drop_id`; 409 → daily cap). Tiles: pending
(pixelated teaser + countdown), ready ("Tap to develop", live develop at zero), developed (photo).
Developed opens a viewer with **Reply to {name}** (opens the 1-1 chat via `connection_id`),
Delete (own) or Report (others'). No likes, views or counts. iOS: `Home/SharedDropsStrip.swift`,
`Core/Media/SharedDrops.swift`.

## 6. Reconnect near here (F6, flag `reconnect_nearby`)

On Home appear, **only if location is already granted** (never prompt): round to 3 decimals on the
device, `GET /api/nudges/reconnect?lat&lng` → at most one card ("You met Maya near here in June").
"Say hi" opens the chat with a starter in the composer (never auto-sent) and posts
`{action: acted}`; the overflow offers Not now / Don't remind me about {name} (`mute: person`) /
Not at this place (`mute: place`). No push (spec 8b is an open decision); **no background location**.
iOS: `Home/ReconnectNearbyCard.swift`, `Core/Connections/ReconnectNearby.swift`.

## 7. Pilot analytics (§11, flag `pilot_analytics` on the server)

Send only `install` (once per install), `app_open` (once per local day) and `recap_opened` to
`POST /api/telemetry/events {event, platform: "android", occurred_at, app_version}` through the
existing telemetry batcher. Everything else is recorded server-side. iOS: `ProductTelemetry.swift`,
`click-ios/Docs/TELEMETRY.md`.

## 8. Not built (needs a decision first)

- **F7** study/social tags on hangouts and **F8** engagement layer: after pilot data (spec §13).
- Soundtrack **Save**, reconnection **push**, and the open decisions listed in the click-web PR notes.

## Test checklist (Android)

Unit (commonMain): develop state transitions; recap look determinism (never Natural, same per seed);
listening summary copy; coarse rounding before send; flag store off-by-default and reset on sign-out.
Manual on 4–6 devices for anything touching check-in (event drops window) and Bluetooth.
