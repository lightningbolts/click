# 07: Android ⇄ iOS gap closure (October 2026)

> **Superseded by [08](08-gap-closure-v2-2026-10-06.md)** as the working plan (verified against `click-ios@093ed67`, `click@b461f6b4`, `click-web@e9fb51a`). Rows that 08 marks "= 07" are still specified here.

**Status:** Plan · **Date:** 2026-10-06 · **Baselines (fetched 2026-10-06):** `click-ios@2161bba`
(origin/main, PR #60) · `click@53b93685` (origin/main) · `click-web@7555bce` (origin/main, PR #131)

Sheets 00–05 closed the gap measured on 2026-09-26. Since then, iOS has merged **74 first-parent
commits (PRs #13–#60)** and click-web has merged **about 41 PRs and 29 migrations**. In the same
period, Android merged one fix (`fa6f26ac`, email-approved device history), a doc (sheet 06) and
recurring events (`a7b7120d`). Sheet 06 is still "not started", and iOS has gone well past it.

This sheet replaces 06 as the working plan. Sheet 06 stays as the detailed spec for its features,
and each row here links back to it where it applies.

Conventions follow the [README](README.md): `KMP/` is commonMain, `AM/` is androidMain, `iOS/` is
`click-ios/Click/` and `web/` is `click-web/`. Each workstream has stable IDs (`G0-1`, `G3-4`, …) for PR
titles and the ledger.

---

## 0. The gap at a glance

| Area | iOS has (since 9/26) | Android today | Size |
|---|---|---|---|
| Foundations | Feature flags, Live updates (realtime topics), disk-first caches, pilot analytics | None | M |
| Connect | Warm high-accuracy capture, `sensor_observation` v3, late altitude follow-up, "Who's in this Click?" review, encounter details, late joiners | Host selection only; lat/lon/altitude only | L |
| Click Drops | Develop state, gated originals, Develop all, shared-drops strip, captions, stories, per-person tiles, archive, release push | Legacy 24 h time lock | XL |
| Events / beacons | Event drops, recap, History, Events together, alert confirmations, Listening now, song search, Nearby sort/counts/paging, planned hangouts on map, extras cache | Recurring events only (on origin/main) | XL |
| Click Places | I1–I4: models, map layer, filters, Place page, check-in, Pulse, QR, My Places, privacy toggle | None (writes `venue_id` only) | XL |
| Home / social | Activity inbox, reconnect near here, location nudges, Find friends by phone, smart icebreakers, 3-step onboarding | Inbox nudge banner, contact hash discovery | L |
| Chat | GIFs (KLIPY), pins, mute menu, encounter backdrops, calmer media, drop replies, hub preview decrypt, short-chat anchor | Mutes and reactions present; the rest absent | L |
| Account / devices | Approve a new device from an existing phone, in-app account deletion, phone claim, legal links | Email approval only; web deletion | M |
| Platform | `click.caf` sound, App Clip previews, privacy manifest | No custom channel sound; App Links broken | M |

Sizes: S ≤ 2 engineer-days, M 3–5, L 6–10, XL 10+. The total is **about 90–120 engineer-days** of
Android work, plus about 5 days of click-web work and owner decisions. Section 11 has the breakdown.

---

## 1. Ground rules

1. **Server state is truth; flags gate everything new.** Every feature behind a flag in
   `web/lib/server/featureFlags.ts` ships **dark** on Android and turns on by cohort. A flag that is
   off returns **404 `{error:"Not found"}`**. Never surface that 404 as an error. Read
   `/api/me/features` first.
2. **Wire compatibility beats UI parity.** Some items let iOS write data that Android misreads today:
   gated drops, group-tap selection, typing payloads. These ship first (G0), even ahead of
   flagged features.
3. **Logic in `commonMain`.** Platform code only for sensors, notification channels, the camera, the
   photo picker and secure storage (`AI.md` §1). ViewModels expose `StateFlow`, and nested state is
   updated with deep copies.
4. **Port tests together with the code.** Each iOS unit test named in a row is ported to
   `commonTest` with the same fixture values. Golden fixtures come from click-web or iOS and are
   not re-derived.
5. **Done means:** acceptance boxes checked, unit tests green, a ledger row added (§10), and a
   device check recorded if the row says one is needed. A green CI run is not a device check.

---

## 2. Phase G0: stop the bleeding (week 1, before any feature work)

These are bugs, interop breaks and repo hygiene. Small diffs; ship them as **one PR per row**.

| ID | Problem | Evidence | Fix | Accept |
|---|---|---|---|---|
| G0-1 | **The working branch is behind origin/main.** `fix/e2ee-history-and-chat-stability` lacks recurring events (`a7b7120d`), the `EventShareRoute.kt` spotless fix, sheet 06 and the push-function rewrite (`8900a338`). | `git diff HEAD origin/main` | Start all G-work from `origin/main`. Delete or rebase the stale local branches (`feat/android-parity-*`). | New branches are cut from `origin/main`. |
| G0-2 | **`messaging-agent/node_modules/` is committed** (thousands of files, `c0bd263d "test"`). | `git diff --stat` | `git rm -r --cached messaging-agent/node_modules`, add it to `.gitignore`, and check CI time. | Repo size back to normal; the drift check passes. |
| G0-3 | **Typing from iOS and web never decodes on Android.** `TypingBroadcastPayload(val userId)` has no `@SerialName("user_id")`, and there is no JSON naming strategy. The logcat shows `MissingFieldException: Field 'userId'` about 12 times. | `KMP/data/repository/SupabaseChatRepository.kt:122`, decoded at `SupabaseChatRepositoryPresence.kt:266` | Add `@SerialName("user_id") val userId` and `@SerialName("is_typing") val isTyping: Boolean = true`. Check what Android **sends**, so iOS decodes it too. Add a `commonTest` that decodes the exact iOS payload `{"is_typing":true,"user_id":"…"}`. | Android shows "typing…" for an iOS peer, and the reverse. |
| G0-4 | **Group taps stall on Android (web #126).** Any 3+ person tap, including one for an existing group, now returns `awaiting_selection` with `existing_connection_id` and `is_new_connection:false`. Android assumes an existing group binds directly, and has no removals. | `ConnectionViewModelConfirm.kt:296` | Minimum fix: handle `awaiting_selection` when `existing_connection_id` is set ("Save this Click"), and call `POST /api/connections/proximity/selection {pending_handshake_id, excluded_member_ids}` for removals. The full review UI is G4-4. | 3 phones (2 iOS + 1 Android) tap an existing group; Android shows Save and confirms, and everyone ends up in the same group. |
| G0-5 | **iOS gated Click Drops look broken on Android.** With `drops_develop` on for an iOS sender, the message media is a **12-block pixelated preview** and the original is stored separately (`drop_gated:true`, `metadata.drop_original`). Android shows the preview as if it were the photo, forever. | 06 §1; web gotcha 5 | Receive side only, regardless of Android's own flag: recognize `drop_gated`. While the drop is pending or ready, show the preview with the "develops in …" state. After reveal, `POST /api/drops/develop {drops:[{kind:"chat",id}]}`, download the signed URL, and decrypt with `metadata.drop_original` (v2) or legacy keys (v1). Cache it as `<messageId>-original`. The full UX is G2-1. | An iOS-sent drop develops on Android with the real photo; the preview is never saved as the photo. |
| G0-6 | **Android App Links never verify.** `web/public/.well-known/assetlinks.json` names `com.click.app` with the placeholder fingerprint `REPLACE_WITH_RELEASE_SHA256_HEX_COLON_SEPARATED`. The app ID is `compose.project.click.click`. | click-web origin/main | **click-web PR:** the correct package plus the release **and** Play App Signing SHA-256 values (both, from Play Console → App integrity). Android: add `/p` to the `autoVerify` intent filter (with G5). | `adb shell pm get-app-links compose.project.click.click` reports `verified` for joinclick.co; tapping a `/c/…` link in Gmail opens the app. |
| G0-7 | **The push function contradicts the Android docs.** origin/main's `send-push-notification` (`8900a338`) re-accepts `incoming_call` (VoIP fan-out). `AI.md` §3 says calls are retired, and Android drops them. `click.caf` was added (`85b25dcd`) and then reverted to `"default"`. | `supabase/functions/send-push-notification/index.ts:191-384` | **Owner decision D1** (§12). Until it is decided, make sure Android still ignores `incoming_call` (`ClickFirebaseMessagingService.kt:46`), and that the `chat_mutes` check (line 634) is still deployed. Redeploy the function after the decision. | The function source, `AI.md` and both clients agree. |
| G0-8 | **The migration mirror is 29 files behind** (`20260930…` → `20261021010000_live_notify_per_recipient`). CI's drift check will fail. | `supabase/migrations` | `bash scripts/sync-supabase-from-click-web.sh`, then `scripts/check-supabase-drift.sh`. Also sync `bind-proximity-connection`. | The drift check is green. |
| G0-9 | **The bind path loses the new encounter fields.** The `bind-proximity-connection` edge function does not parse the `gps_*`, `barometric_*` or `sensor_observation` keys. | web gotcha 2 | Confirm that Android binds through `POST /api/connections/proximity`, not the edge function, wherever G4 sends observation keys. If any path uses the edge function, route it to the API. | Grep plus a staging encounter row with `horizontal_accuracy_m` set from an Android tap. |
| G0-10 | **Device-history approval was returning 500** (PGRST201, fixed by web #130 on 10/05). Android's email path was tested against the broken server. | `AppDataManagerDeviceHistory.kt` | Retest email approval end to end against production. | A new Android device gets older history after email approval. |
| G0-11 | **Say-hi countdown semantics.** iOS #57 stopped keying on `has_begun` ("which only Android sets") and now stops once anyone has written. Android's vibe timer stops only on `isMutuallyKept()`. | `ChatViewModelVibeIcebreakers.kt:21` | Stop the countdown and banner once **either** side has sent a message, and keep writing `has_begun` (iOS no longer depends on it). | Same chat, both platforms: the banner disappears after the first message from either person. |

| G0-12 | **Group chat after a group tap (iOS #60).** A 3+ person tap creates one group connection plus pairwise connections, but the **group chat is a verified clique that a client must create**. iOS now does it automatically on every member's phone: sort the member IDs (viewer included); the **lowest ID creates right away**; the others look for an existing clique with exactly that member set, waiting **2 s, 3 s, then 4 s**, before creating it themselves. If a create fails, look again (`create_verified_clique` rejects a second group for the same members). Then open the group and reconcile the membership epoch. The iOS code notes that Android "only offers a prefilled group sheet", so a group whose lowest ID is on Android waits about 9 s on every iOS phone. | `iOS/Features/Connect/PostConnectModel.prepareGroup`, `GroupRepository.pairConnectionIDs`; Android `VerifiedCliqueCreation.createVerifiedCliqueWithWrappedKeys` | Port `prepareGroup` to `commonMain` and run it from the post-connect result, replacing the prefilled sheet. Use the same lexicographic sort (UUIDs, so Kotlin `sorted()` equals Swift), the same waits, the same find-by-exact-member-set, and the name "first names, sorted, comma-joined". Write group tags and sensor context to the shared group connection **once**. Confirm the inbox still skips group connections (`isOneToOnePairEdge`), which iOS copied from KMP. | A mixed 2 iOS + 1 Android tap ends with **exactly one** group chat that everyone is in, whichever phone has the lowest ID. No duplicate DMs. |

**Exit G0:** CI green on `origin/main`, the drift check clean, and cross-platform smoke tests for typing,
group tap and gated drop receive done on devices.

---

## 3. Phase G1: foundations (week 2)

Everything after this depends on these pieces. Build them in `KMP/core/…`, mirroring the iOS file names
where that helps.

### G1-1 Feature flags (`FeatureFlags`): S
- `GET /api/me/features` → `{features:{key:{enabled, config}}}` (`Cache-Control: private, max-age=60`).
- Store per user in encrypted prefs. Restore at launch so flagged screens paint on the first frame (iOS
  `e07d9df`). Refresh on sign-in, on foreground, and on the Live updates event `kind: "flags"`, if the
  server ever adds it. Clear at sign-out. **Unknown or failed flags are off.**
- Keys: `drops_develop`, `alert_confirmations`, `soundtrack_presence`, `event_drops`,
  `event_history`, `shared_drops`, `reconnect_nearby`, `click_places`. `pilot_analytics` is
  server-only.
- Expose `StateFlow<FeatureFlags>`; `config` stays a `JsonObject` with typed accessors
  (`develop_hours`, `recap_card_hours`, `heartbeat_seconds`).
- Tests: off by default, reset on sign-out, persisted round-trip, malformed JSON gives all off.

### G1-2 Live updates (realtime topics): M
Mirror `iOS/App/LiveUpdates.swift` (#59). The server side is web #131 (migrations
`20261021000000_live_updates`, `20261021010000_live_notify_per_recipient`):
- Statement-level triggers broadcast content-free hints.
- Joining a topic is authorized by RLS on `realtime.messages`, so the socket must carry the user's JWT.
- Hint failures never fail the write.

**Realtime only; no new polling.**
- Join private broadcast topics `realtime:user:<id>` and `realtime:beacons` (`private:true`) on the
  existing Supabase socket, and rebind them with the socket after a token refresh
  (`rebindRealtimeSocket()`).
- The `changed` event carries `{kind, lat?, lng?}` with kinds `drops | beacons | nudges | availability |
  activity | connections`. Coalesce bursts **per kind over 600 ms** into one refetch.
- Beacon hints are 0.1° cells. Refetch only if the cell is within the discovery radius + 10 km of Home's last fix
  or the Map's last fetch center.
- While backgrounded, accumulate hints and flush on `ON_START`. A socket rejoin catches up on home,
  drops, activity and connections.
- Reset the realtime backoff only after a heartbeat ack (iOS #33).
- Tests: coalescing, the geo filter, flush on foreground, and rejoin catch-up (fake clock).

### G1-3 Push routing table: S
Extend `PushRoutes` with every type iOS routes (iOS `ClickNotificationCoordinator.tapRoute`). New since
9/26:

| Type | Route |
|---|---|
| `event_drop_recap`, `event_recap` | Recap (G3-2) |
| `shared_drop_released` `{drop_id, poster_id, drop_count}` | Home → that drop's story (G2-4) |
| `device_approval` `{request_id}` | Approval sheet (G7-1) |
| `reaction` | `target_kind == shared_drop` → story; otherwise beacon |
| `event_rsvp`, `event_rsvp_request`, `friends_going`, `event_trending` | Event |
| `new_connection`, `wave` | Chat |
| `prior_connection_accepted` | Profile |
| `prior_connection_request` | Activity (G6-1) |
| `reconnect_nearby` | Chat |
| `disposable_reveal` (now batched, `drop_count`) | Chat |

- The `new_message` alias stays alongside `chat_message`.
- Unknown types open the app without navigating.
- Missing IDs never navigate.
- Tests: one per type, mirroring iOS F106.

### G1-4 Notification channels and sound: S
- Add `res/raw/click.ogg` (convert `click-ios/Resources/Sounds/click.caf`).
- **Android channels are immutable once created.** Create new channel IDs (for example
  `messages_v2`) with the sound and delete the old ones. Keep the user's importance choice where you
  can.
- Clear delivered notifications for a conversation when it opens (iOS `96e7172`).
- Depends on D1 (the server sends `"default"`, so Android uses the channel sound regardless).

### G1-5 Disk-first caches and instant paint: M
iOS `MemoryCache` and `CacheStore` (`544f4c4`, #39) paint Home, profile and edit screens on the first frame.
- Add a per-user JSON cache (`noBackupFilesDir/<uid>/cache/*.json`) for: home feed modules, shared
  drops strip (≤240 items), beacon extras, RSVP/saved state, activity, flags.
- ViewModels seed `StateFlow` synchronously from the cache, then revalidate.
- Wipe at sign-out (reuse the local message store wipe hook).

### G1-6 Pilot analytics: S
- `POST /api/telemetry/events {event, props?, platform:"android", app_version, occurred_at}`, sent
  through the existing telemetry batcher.
- Only three events: `install` (once per install), `app_open` (once per local day),
  `recap_opened` (once per recap visit, prop `access`). The server always answers 202.
- Tests: once per day across process death, and no PII.

### G1-7 Generic reactions client: S
- `GET/PUT /api/reactions/{soundtrack|shared_drop}/{id}`, with PUT `{emoji|null}` →
  `{mine, reactions, is_owner}`. 403 when reacting to your own.
- One `ReactionBar` composable reused by stories and soundtracks.

### G1-8 Docs and contract sync: S
- Update `click-ios/Docs/BACKEND_CONTRACT_MATRIX.md` as part of the iOS docs PR (§10):
  - set `sensor_observation` schema_version to 3
  - Listening is not location-gated
  - `/api/me/history`, not `/event-history`
- click-web README drift: `lib/drops/README.md` route name; `lib/notifications/README.md` should say
  `chat_message`, not `new_message`.

---

## 4. Phase G2: Click Drops (weeks 3–4)

Spec: sheet 06 §1 and §5, extended by everything iOS shipped after 9/29.

| ID | Feature | Android work | iOS reference | Backend / flag |
|---|---|---|---|---|
| G2-1 | **Develop state and gated send** | Put the state machine in `commonMain`: `pending → ready → developed` (per viewer, server-side); mirror `web/lib/drops/developState.ts`. **Send** (flag on): the preview is ≤480 px and pixelated at 12 blocks per side, and goes in as the message media. The original is E2EE-encrypted and goes to `/api/chat/media` with `drop_original:true` under its own cmid `"<cmid>.original"`. The metadata adds `drop_original_path` and `drop_original {epoch, sender_device_id, client_message_id, media_ciphertext_sha256}`. In v1 chats, use legacy keys and no `drop_original`. Replace `computeClickDropRevealTtlIso` and `Message.isDisposableRollLocked()` with the state machine. Chat drops still develop in 24 h. | `ClickDropDevelop.swift`, `ClickDropPixelation.previewJPEG`, `ChatRepository.sendMedia(gated)` | `drops_develop`; `POST /api/drops/develop` (≤50), `GET /api/drops/views?kind&ids` (≤100) |
| G2-2 | **Tap to develop, live develop, Develop all** | A ready drop develops on tap. A drop reaching zero while its chat is visible develops on its own. Show "Develop all (N)" when N > 1. Animation: layers at 12/24/48/96 px bloom from the center over about 0.9 s with a light haptic. With animations off (`ANIMATOR_DURATION_SCALE == 0`), cross-fade instead. Use a plain Compose `Canvas` with `FilterQuality.None` scaling, not RenderEffect (iOS dropped Metal for the same reasons). Save/share only after developing, and save the original. | `ClickDropBubble.swift`, `ClickDropDevelopEffect.swift` (#36, #38) | — |
| G2-3 | **Shared drops strip (Home)** | A bounded strip, never a feed. Share tile → Click Drop camera → audience sheet (All / Core) with the not-E2EE disclosure copy → `POST /api/me/shared-drops {client_drop_id, audience, mime_type, original_b64, preview_b64, width, height, caption?}`. Retry with the same `client_drop_id`. 409 `cap_reached` means 3 per 24 h. **Develops in `develop_hours` (1 h)**. Strip rule (server): the last 24 h if there are more than 25 drops, otherwise the newest 25, capped at 150. | `SharedDropsStrip.swift`, `SharedDrops.swift`, `SharedDropsStore.swift` | `shared_drops`; `GET/POST /api/me/shared-drops`, `DELETE …/{id}` |
| G2-4 | **Stories viewer** | Full-screen viewer with progress segments: tap left/right, hold to pause, swipe down to close. Shared-element zoom from and to the tile (Compose `SharedTransitionLayout`). Glass controls, `ReactionBar` (G1-7), "Reply to {name}" (opens the 1:1 chat via `connection_id` with `metadata.drop_reply`), Delete (own), quiet Report (`POST /api/drops/report`). No likes, views or counts. | `SharedDropStoryViewer.swift` (#31, #32, #42, #43, #48) | `/api/reactions/shared_drop/{id}` |
| G2-5 | **Captions** | ≤100 grapheme clusters on the camera preview, Locket-style. Others see the caption only after the drop develops. | `ClickDropCameraView.swift` (#16) | migration `…shared_drop_captions` |
| G2-6 | **One tile per person, plus View all** | Tile order: yours, then unwatched, then the rest. A story starts at the first unseen drop. A cube transition between people is **P2**: use a horizontal pager first. "View all" opens a paged grid with day headers via `GET /api/me/shared-drops/archive?before&limit` (default 30, max 60). | `SharedDropsArchiveView.swift` (#47) | — |
| G2-7 | **Release push and setting** | The `shared_drop_released` push opens the story (G1-3). Add a Settings → Alerts toggle "Click Drops" bound to `drop_release_push_enabled` through `PATCH /api/user/preferences`. | #32 | migration `…shared_drop_release_push` |
| G2-8 | **Drop replies in chat** | Render `metadata.drop_reply` as a "Replied to your drop" quote with a thumbnail. Reactions to a drop go to the 1:1 chat. | `ChatDropReply.swift`, `DropReplyViews.swift` | — |
| G2-9 | **Instant paint** | The strip and developed photos come from the disk cache (G1-5). State survives scrolling (keyed `LazyRow` items with `rememberSaveable` state). | `5a89b59`, `b646a27` | — |

**Acceptance:**
- A pending drop's network payload never contains the original.
- Developed state syncs across devices, and across platforms (iOS ↔ Android both ways).
- A ready drop never shows the original until it is tapped or develops live.
- Reduced motion turns the animation into a cross-fade.

**Tests:** state transitions, preview size and block count, gated metadata shape (golden JSON from
iOS), and strip ordering.

---

## 5. Phase G3: events, beacons, map (weeks 4–6)

| ID | Feature | Android work | Backend / flag | iOS ref |
|---|---|---|---|---|
| G3-1 | **Event drops** | An event-detail section from `GET /api/beacons/{id}/drops` → `state` (before/open/developing/revealed), `access`, `can_post`, `remaining`, `show_to_absentees`, drops. Hide it when there is nothing for this viewer. The camera shoots Natural (no looks). Posting: `POST /api/beacons/{id}/drops`. Keep the photo and `client_drop_id` on failure and offer Retry. Errors: 403 `not_checked_in` / `window_closed`, 409 `cap_reached`, 413 `invalid_media`. Own drops: pixelated thumbnails, long-press Delete, a "What you missed" toggle (`PUT …/drops/settings`). Copy: "Develops tomorrow at 10:00". | `event_drops` | `EventDropsSection.swift`, `EventDrops.swift` |
| G3-2 | **Recap** | Every drop opens pixelated and develops on tap (`POST /api/drops/develop kind:event`, even though `original_url` now arrives inline (web #129)). Each drop's look is the server `filter_seed` → `recapLook(seed)` (every look except Natural, index `seed mod 9`, in `DisposableRollFilters` order), with a Natural toggle. Playback: tap left/right, hold to pause, about 4 s each, own drops first and outlined. It ends with "Who was there", which includes you (pinned) and whose count matches "N going". Reveal copy reads "today/tomorrow/Monday at 10:00 AM". Delete own; Report others' drops. Send `recap_opened` (G1-6). | `event_drops` | `EventRecapView.swift`, `ClickDropFilter.recapLook` (#14, #54) |
| G3-3 | **History** | Me → History with tabs All / Events / Beacons / Hangouts by month, plus Saved. Data: `GET /api/me/history?kind=all\|events\|beacons\|hangouts&cursor&limit≤100`. **The real route is `/api/me/history`; sheet 06 has it wrong.** Merge past plans from the local message store (05 D6). A Home recap card comes from `GET /api/me/event-history/recap-card` (about 48 h). Profiles get "Events together" from `GET /api/users/{id}/events-together`. | `event_history` | `HistoryView.swift`, `EventHistoryView.swift`, `EventHistoryCards.swift` |
| G3-4 | **Alert confirmations** | "Is it still there?" with Still here / Cleared; the creator gets "Take it down". Show "Last seen X ago", never counts. `GET/POST /api/beacons/{id}/confirm {status, lat, lng}`. Map 400 `location_required`, 403 `out_of_range` ("You need to be near this alert"), 409 `already_voted`, 410 `expired`. A quiet Report in the beacon menu for non-creators (`POST /api/beacons/{id}/report`). New alerts default to 2 h. | `alert_confirmations` | `AlertConfirmationSection.swift` |
| G3-5 | **Listening now** | Under the soundtrack card, show the count and connections' names ("Maya and 3 others are listening"). An "I'm listening here" toggle heartbeats `POST /api/beacons/{id}/listening` every `heartbeat_seconds` (at least 60 s, default 360 s) while the screen is resumed; `DELETE` turns it off. **It works without location since iOS #15.** Send lat/lng only if the server asks; reconcile the 403 copy with the server. Soundtrack reactions use G1-7. | `soundtrack_presence` | `ListeningNowSection.swift`, `SoundtrackPresence.swift` |
| G3-6 | **Song search** | Search the iTunes Search API (`itunes.apple.com/search?media=music`) for an Apple Music `music_url`; pasting a link stays as the fallback and is still checked against the server allowlist. | — | `SoundtrackResolver.swift` (`13223b1`) |
| G3-7 | **Nearby: paging, sort, counts** | `GET /api/beacons` now pages by keyset: follow `next_cursor`. **A short page doesn't mean the end.** Use a 50 km radius, a ceiling of 25 × 200 beacons, and refetch when the center moves about 5 km. Rows show "N going" (`rsvp_count`) or "N here" (Places). Sort options: relevance, distance, rising, new, alphabetical (remembered). Move the distance origin only every 50 m. Add inline search. | — | `MapFeatureModel.swift (NearbySort)`, `NearbySheet.swift` (#41, `783f49a`) |
| G3-8 | **Planned hangouts on the map** | Chat plans with a place (`messages.metadata.plan`, already read for profiles) appear as a `.hangouts` map layer and in Nearby until they end. | — | `7d6395e` |
| G3-9 | **Beacon extras cache and preloaded RSVP** | Prefetch and cache per beacon: listening, reactions, alert state, drops. RSVP and saved state persist across launches (G1-5). | — | `BeaconExtrasCache.swift` (#16, #39) |
| G3-10 | **Event reminders: align** | iOS now keeps **only the T-60 reminder** and removes existing T-15s (#32). Android schedules T-60 and T-15 (05 C3). Drop T-15 and cancel the `event.<id>.15` work on upgrade. Decision D4 covers server-versus-local duplicates. | — | `EventReminderScheduler.swift` |
| G3-11 | **Recurring events: verify** | Already on origin/main (`a7b7120d`). Check the request against web: `recurrence {frequency, count 2–26}`, with the response `series_count`. Add the discard-changes dialog for half-filled forms (iOS `450cc4e`). | — | `CreateBeaconSheet.swift` |
| G3-12 | **Calendar free/busy on plans and events** | Reuse `KMP/calendar/CalendarFreeBusy` (Android is ahead here). Show "You're free then" or "Busy on your calendar then" with a move to the next free time on plan cards and event details. On-device, read-only, one week ahead. | — | `CalendarAvailability.swift`, `PlanViews.swift` (#36) |

---

## 6. Phase G4: connect pipeline (weeks 5–7; can run in parallel with G3)

The physical-world core. It needs the 4–6-device test matrix (§9).

| ID | Feature | Android work |
|---|---|---|
| G4-1 | **Warm capture session** | A `ConnectionCaptureSession` (common interface; `actual` in `AM/` using `FusedLocationProviderClient` with `PRIORITY_HIGH_ACCURACY` plus `SensorManager` `TYPE_PRESSURE`). It warms up while the Tap or QR screen is visible and **never prompts**: location is read only if Location snap is on **and** permission is already granted. Accuracy tiers: excellent ≤5 m, good ≤10, usable ≤25, coarse ≤50, unusable above that. **Never attach a fix worse than 50 m.** Pick the best fix by `accuracy + 1.5 m/s × \|Δt\|`. Timings: min 1.5 s, cold 3 s, hard cap 8 s, in-flow grace 1 s, max warm 120 s. |
| G4-2 | **Top-level quality keys** | On `/api/connections/proximity`, the `/api/qr` redeem (which also sends `gps_lat`/`gps_lon`) and `POST /api/connections`, send the keys below. Send absent values as missing, never as negative numbers. **Android sources:** `Location.getVerticalAccuracyMeters()` (API 26+), `getMslAltitudeMeters()` (API 34+), `isMock` / `isFromMockProvider` → `gps_simulated`. Derive pressure altitude with `SensorManager.getAltitude(PRESSURE_STANDARD_ATMOSPHERE, hPa)`. Pressure is in hPa: kPa = hPa / 10. |
| G4-3 | **`sensor_observation` v3** | A snake_case JSON object with **`schema_version: 3`** (the server accepts integers 1–100). Keep it under **56 KB**, dropping motion samples first; the server silently drops anything over 64 KB. Port the shape from `iOS/…/ConnectionSensorObservation.swift`, filling what Android can measure and **omitting** the rest. `uwb` is always null. Build it in `commonMain` from a platform `SensorSnapshot`. |
| G4-4 | **"Who's in this Click?" review** | The full UI that G0-4 starts. Vertical rows with Remove / Add back and a Connecting / Not connecting split. Each removal saves immediately (`/proximity/selection`). An existing group shows "Save this Click". Removing everyone skips the tap. Poll the tap row so another phone's confirm or a late joiner updates the screen (`GET /api/connections/proximity?pending_handshake_id`). |
| G4-5 | **Late joiners and pending-match recovery** | If the result was 1:1 and the server later reports `group_connection_id`, switch to "Group created" and offer Open Groups. When confirm fails because another phone confirmed first, recover the pending match instead of erroring. |
| G4-6 | **Encounter details on the result screen** | Show When / Where / With / Event / Weather ("12°C · Clear") / Verified by ("Bluetooth · Sound · Location ±8 m" or "QR code · Location ±m"). Re-fetch the place name once if it's missing. Tags apply to the group **and** each peer connection. |
| G4-7 | **Late altitude follow-up** | If the tap or scan finishes before the first absolute fix, wait up to 20 s, then `POST /api/connections/encounter-altitude {connection_ids (≤20), connection_moment, observed_at, exact_barometric_elevation_m, barometric_accuracy_m?, barometric_precision_m?}`, only for connections confirmed within 120 s. The server never overwrites. Use WorkManager with an expedited request so it survives backgrounding. |
| G4-8 | **Connect telemetry** | Add `qr_connect_started/succeeded/failed/reconnect_rate_limited`. The `capture_quality` aggregates (`connection_method`, `activity_available`, `pedometer_available`, accuracy bucket, `sensor_failure`) must stay inside the allowlist in `web/lib/server/telemetry/connectionFlowEvents.ts`. Keep `ConnectionFlowTelemetry.ALLOWED_EVENTS` in sync with it. |
| G4-9 | **Permissions** | Activity recognition (`ACTIVITY_RECOGNITION`, API 29+) is needed only if the pedometer and activity blocks are filled. **Never prompt for it during a tap.** Fill them only if it was already granted; otherwise mark them unavailable. |

Top-level quality keys for G4-2:
- Location: `gps_horizontal_accuracy_m`, `gps_vertical_accuracy_m` (>0), `gps_altitude_m`, `gps_ellipsoidal_altitude_m`, `gps_observed_at`, `gps_floor`, `gps_full_accuracy`.
- Velocity: `gps_speed_mps`, `gps_speed_accuracy_mps`, `gps_course_deg`, `gps_course_accuracy_deg`, `gps_simulated`, `gps_external_accessory`.
- Barometer: `exact_barometric_elevation_m`, `barometric_accuracy_m`, `barometric_precision_m`, `barometric_relative_altitude_m`, `barometric_pressure_kpa`.

Top-level `sensor_observation` blocks for G4-3:
- Header: `schema_version`, `method` (tap/qr/link), `captured_at`, `connection_moment`, `capture_duration_ms`.
- Sensor blocks: `location` (with `fused` and `trail[≤8]`), `barometer` (`samples[≤12]`, `started_t_ms`), `motion`, `heading`, `activity`, `pedometer`, `bluetooth`, `acoustic`, `device`.
- Clock: `clock {sent_at, time_zone_offset_s}`.

**Acceptance:**
- An Android tap with Location snap on writes `horizontal_accuracy_m` and `location_observed_at` on the reporter's own encounter row.
- `sensor_observation` is stored (server-stamped `server_received_at`).
- No permission prompt appears during capture.
- Mixed groups (iOS + Android) review, remove and confirm consistently.

**Tests:** tier boundaries, best-fix scoring, size cap trimming order, key golden (exact JSON
from an iOS fixture, minus the iOS-only fields), and the follow-up window.

---

## 7. Phase G5: Click Places (weeks 6–8; flag `click_places`)

Spec: `click-web/CLICK_PLACES_SPEC.md` §5–6 and `click-ios/Docs/CLICK_PLACES_SPEC.md`. These are consumer
features only; manager and self-serve tools stay on web.

| ID | Increment | Android work |
|---|---|---|
| G5-1 | **I1 Models, repository, routes** | `PlaceSummary` / `PlaceDetail` per spec §5.1 with lenient decoding (unknown category → `other`, unknown Pulse state → `none`; categories include `event_space` and `office`). Add `PlaceRepository` (`GET /api/places/nearby`, `GET /api/places/{id\|slug}?source=`) and route `AppRoute.Place(idOrSlug, anchorToken?)` from `click://p/{slug}?t=` and `https://joinclick.co/p/{slug}?t=`, with `/p` added to the verified intent filter (G0-6). The in-app QR scanner routes `/p/` only when the flag is on. `NearbyDiscovery.places` is a non-failing enrichment, so old caches still decode. |
| G5-2 | **I2 Map layer and filters** | A Places layer with a 34 dp rounded-square pin showing the category icon or photo. An energy ring (chill blue, steady green, lively orange, packed purple) appears **only for a live Pulse**, with a LIVE or start-time badge. A beacon whose `venue_id`/`place.id` matches a Place is drawn inside the Place pin. Nearby gets a Places section sorted live event, then live Pulse, then distance. Filter chips: Pulse now, Lively+, Quiet, Events today, Open now, Been here, Clicks have been, plus a More… sheet with categories and min reports (0/2/5/10), persisted under `places.filters.v1`. **Filter semantics must pass `web/__tests__/fixtures/places/filters.json`**: copy the fixture into `commonTest/resources`. |
| G5-3 | **I3 Place page** | Sections: Header, Now, Your visit, Happening here, Your Clicks, Your history, Place Hub, About, manager note. **Check-in** is "I'm here" with one foreground location read and a "Let my Clicks see I'm here" toggle (default off). It calls `POST /api/places/{id}/check-in {latitude, longitude, accuracy_meters, anchor_token?, share_with_connections, platform:"android", app_version}`. Map 422 codes to copy: `no_location`, `low_accuracy`, `out_of_bounds` (+distance), `invalid_anchor`. **Leave** shows a would-return question for 30 s. **Pulse:** one tap on energy, optional follow-ups, cooldown copy (`pulse_cooldown` + `cooldown_until`), hidden for managers. A QR token always asks "Check in at {name}?" first; **never check in automatically.** Refresh every 60 s only while checked in and resumed. Keep a session cache so the page reopens instantly. |
| G5-4 | **I4 Surfaces** | Event detail shows an "At {place}" row. Privacy gets "Show my Place visits to my Clicks" (`users.place_visits_visible_to_connections`) with the updated Business-insights footnote. Me → Places → My Places (`GET /api/me/places`: "4 visits · Last …", "Met N people here"). The reconnect card links to the Place. Global search finds Places, and Place hubs link to their Place. |
| G5-5 | **Venue writes** | Android still writes `venue_id` (`ConnectionRepositoryCreate.kt:196,451`, `QRModels.kt:45`). That keeps working through the compatibility views, but **INSERT into `places`/`venues` is revoked and `venue_check_ins` is read-only.** Audit every PostgREST write to these tables and move check-ins to `/api/places/{id}/check-in`. Handle `POST /api/hub/create` 409 `place_hub_exists {place_id, slug, hub_id}` by opening the existing Place hub. |

---

## 8. Phase G6: Home, social and chat (weeks 7–9)

| ID | Feature | Android work | Backend / iOS ref |
|---|---|---|---|
| G6-1 | **Activity inbox** | The Home bell opens Activity: every alert plus friend requests to answer, cached, paged by `before`. An item's `data` is the push payload, so taps reuse `PushRoutes` (G1-3). Mark seen with `POST /api/activity/seen {seen_at}`; the bell badge counts items newer than `seen_at`. Live updates `kind: activity` refreshes it. In-app-only types: `reaction`, `event_rsvp`, `event_rsvp_request`, `new_connection`, `friends_going`, `event_trending`. | `GET /api/activity?before`; `ActivityView.swift` (#34, #35) |
| G6-2 | **Reconnect near here** | On Home appear, **only if location is already granted**, round to 3 decimals on the device and call `GET /api/nudges/reconnect?lat&lng` → at most one card a day ("You met Maya near here in June"). "Say hi" opens the chat with a starter in the composer (never auto-sent) and posts `{action:"acted"}`. The overflow offers Not now / Don't remind me about {name} (`mute:"person"`) / Not at this place (`mute:"place"`). It links to the Place if `place_slug` is set. **No push; rate-limited like a write.** | flag `reconnect_nearby`; `ReconnectNearbyCard.swift` |
| G6-3 | **Past meeting spots (background)** | **Blocked on D2.** iOS uses `CLVisit` with **Always** location, which contradicts its own privacy notes. The Android equivalent (geofencing plus `ACCESS_BACKGROUND_LOCATION`) needs a Play background-location declaration and review. Do not build it until D2 is decided. | `VisitMonitor.swift` (#15) |
| G6-4 | **Find friends by phone** | A "Let friends find you" card, and "Find me by phone" in Privacy & data, using `GET/PUT/DELETE /api/me/phone`. Errors: 400 `invalid_phone`, 409 `phone_taken` (offer "That's my number" → `POST /api/me/phone/report`), 429 `phone_rate_limited` (3 changes per 24 h). Contacts discovery reads `known[]` and shows an "Already on Click" list (connected / pending), with rows that open profiles. **Hashes must match the server normalization:** SHA-256 of E.164 where 10 digits become `+1…` and anything else becomes `+digits` (`web/lib/connections/priorConnections.ts`); add a golden test with the web fixture. Show "Invite friends" only when the Play Store URL is configured. | `/api/contacts/discover`; `FindFriendsView.swift`, `MyPhoneEditor.swift` (#40, #41) |
| G6-5 | **Onboarding: 3 steps** | Welcome → Interests → Photo. Sign-up asks only for email and password; name and birthday move to an About you step. Forgot password opens the web reset. Personality and Find friends become Home setup cards. **Owner check D5** before changing the funnel. | `Features/Onboarding/*`, `HomeSetupCard.swift` (#34) |
| G6-6 | **Location nudges; remove Memory Map** | One-time cards: Location snap after a Click, Location snap on a timeline with no places, Business insights after a Place or event check-in. After "Not now", show once more 30 or more days later, then never. **Delete the Memory Map toggle** (`SettingsScreenComponents.kt:105`) and stop writing `location_show_on_map_enabled`. | `LocationNudge.swift` (#45) |
| G6-7 | **Smart icebreakers** | Deterministic, no AI. Sources, in order: the event you met at, shared interests (one per category), context tag and venue, repeat encounters, recent rain; a plain hello as fallback. "New ideas" pages through them. Show while the chat has fewer than 5 messages, with a 15 s cooldown. Replaces the keyword matching in `IcebreakerRepository.getPromptsForContext`. **Keep poll-pair icebreakers** (Android is ahead there). | `Core/Chat/Icebreakers.swift` (#55) |
| G6-8 | **GIFs (KLIPY)** | A picker that calls `api.klipy.com/api/v1` with the public app key (store it in `local.properties`/BuildConfig) and a hashed customer ID. Send `message_type:"text"` with the KLIPY `static*` URL as content and `metadata.gif {provider:"klipy", width, height}`. Render animated GIFs with Coil's `AnimatedImageDecoder` (API 28+) / `GifDecoder`, and reserve the size from the metadata. **Inside E2EE chats the URL is encrypted content.** Only render URLs from `*.klipy.com`. | `KlipyClient.swift`, `GifViews.swift` |
| G6-9 | **Pinned messages** | A banner above the timeline; each tap jumps to the next pin. A pins list on the profile; long-press to Pin/Unpin. Realtime `message_pins` (unpin is an UPDATE with `unpinned_at`). | `GET/POST/DELETE /api/chat/pins` (bearer-only) |
| G6-10 | **Mute menu durations** | Android has mutes (sheet 04). Add 1 h / 8 h / 1 week / until turned back on, from inbox rows and the chat header, with a bell icon on muted rows. | `PUT /api/chat/notifications {muted_until}` |
| G6-11 | **Encounter backdrops** | A chat background generated from the encounter signature (venue, event, night out, contours, streets) with seeded palettes, manual override, stored per conversation. Port the seed → pattern mapping with golden tests (as was done for card visuals). **P2.** | `ChatBackground.swift` |
| G6-12 | **Calmer chat media** | Photos carry `media_aspect`, so the bubble reserves its final size; fade in, and show a spinner only after about 350 ms. A message from before this device was added shows a lock instead of retry; the inbox preview reads "🔒 Message from before this device". Write `media_aspect` on send. | #35 |
| G6-13 | **Hub previews in Groups** | Decrypt hub last-message previews in memory using derived v1 keys, a cached v2 session or the device copy. **Never fetch keys** to build a preview. `ActiveHubEntry` needs a last-message field. | #58 |
| G6-14 | **Short chats bottom-anchored** | Short timelines sit just above the composer so the say-hi panel and banners never cover them (`LazyColumn(reverseLayout = true)` or `verticalArrangement = Arrangement.Bottom`). Show the history loader row only after the user scrolls to the top. | #56, #31 |
| G6-15 | **Per-tab root menus** | Each tab's top-bar menu holds its own actions; only Settings is shared. | `RootMenu.swift` (#35) |

---

## 9. Phase G7: account, devices, compliance (weeks 2–4, in parallel; small)

| ID | Feature | Android work |
|---|---|---|
| G7-1 | **Approve a new device from an existing phone** | Send `device_label` (≤64 chars, e.g. `Build.MODEL` → "Pixel 9") on `POST /api/chat/devices`. On the **existing** device, a `device_approval` push or Activity item opens an "Approve new sign-in?" sheet with Approve and "This wasn't me". Flow: `POST …/history-requests/{id}/challenge {approving_device_id}`; unwrap the envelope like an epoch key (`chat_id:"approval:{id}"`, `epoch:1`, `sender_device_id:"click-device-approval"`) to get a 32-byte nonce; then `POST …/{id} {decision, approving_device_id, challenge_id, proof: base64(nonce)}`. Map 403 `EMAIL_PROOF_REQUIRED` / `DEVICE_PROOF_INVALID` / `APPROVER_NOT_ALLOWED`, 409 `HISTORY_REQUEST_DECIDED`, and 410 `HISTORY_REQUEST_EXPIRED` / `HISTORY_REQUEST_DEVICE_GONE`. On the **waiting** device, show a banner "Unlock older messages" with "Ask again" (`reopen:true`) and "Email me a link instead" (`POST …/{id}/email`; otherwise it goes out automatically after 3 min). Users with only Android devices otherwise always fall back to email. |
| G7-2 | **In-app account deletion** | `DELETE /api/user/delete` now accepts bearer tokens (web #124). Replace the Custom Tab (`SettingsScreen.kt:152`) with an in-app confirmation "Delete your Click account?" using the iOS copy. On success, sign out locally and wipe all stores. No `apple_authorization_code` (that's iOS only). Keep the web link as a fallback on error. This satisfies the Play account-deletion policy in-app. |
| G7-3 | **Legal links and terms agreement** | Sign-in shows agreement to Terms and Privacy (zero tolerance for objectionable content; reports reviewed within 24 h, web #122). Add `joinclick.co/terms` and `/privacy` to the Me menu and to Privacy & data. |
| G7-4 | **Play Data safety form** | Mirror iOS `PrivacyInfo.xcprivacy` (12 collected types, as of #49) in the Play Console Data safety form. It is an owner task, but list the new types: phone number, precise location, photos (drops), product interaction. Revisit after G4 (sensor data) and G6-3. |
| G7-5 | **Permission strings / rationale** | Align the rationale copy for location, activity recognition and calendar with the rewritten iOS purpose strings (#44). |

---

## 10. Phase G8: verification, ledger and keeping it closed

### G8-1 Device matrix (required before calling anything "at parity")
| Scenario | Devices | Covers |
|---|---|---|
| Tap 1:1 iOS ↔ Android, Location snap on/off | 2 | G4-1..3, G0-9 |
| Group tap 2 iOS + 2 Android, with one removal and one late joiner | 4–6 | G0-4, G4-4, G4-5 |
| QR redeem both directions, plus the altitude follow-up arriving late | 2 | G4-2, G4-7 |
| Gated drop iOS → Android and Android → iOS, both v1 and v2 chats | 2 | G0-5, G2-1 |
| Shared drop: post, develop, release push, story, reply, react | 2 | G2-3..8 |
| Event drops: check in, post, recap after reveal | 3 | G3-1, G3-2 |
| Place check-in through QR poster, Pulse, leave → would return | 1–2 | G5-3 |
| Device approval: second Android device, approve from the first; then deny | 2 | G7-1 |
| Typing, mutes, pins, GIFs across platforms | 2 | G0-3, G6-8..10 |
| Doze: event reminder (T-60) and altitude follow-up worker | 1 | G3-10, G4-7 |
| Live updates: a beacon created on iOS appears on the Android map within about 1 s, no polling | 2 | G1-2 |

Record results in `docs/regression-testing/` as a dated table.

### G8-2 Cross-client fixtures
- Shared golden JSON lives in `click-web/__tests__/fixtures/mobile/` (owned by click-web; copied
  into `commonTest/resources` by the sync script). It covers:
  - the `sensor_observation` v3 example
  - gated drop message metadata
  - typing payload
  - KLIPY GIF metadata
  - Places filters
  - contact hash normalization
  - push payload per type
- iOS and Android both decode the fixtures in unit tests. **A wire change that isn't in the fixtures fails review.**

### G8-3 One ledger
- Add F124+ rows to `click-ios/Docs/PARITY_LEDGER.md` for every iOS feature since `2ddb4a9`. It has
  none today. Give each row an **Android** column (`Code` / `Device` / `n/a` / `Retired`).
- Fix the iOS doc drift:
  - schema_version 2 → 3
  - shared drops develop in 1 h, not 24 h
  - Listening isn't location-gated
  - the "no background location" claim versus `VisitMonitor`
- The scoreboard in `docs/parity/README.md` gets a section for this sheet.

### G8-4 Keeping the gap from reopening
1. **PR template, both mobile repos:** "Other platform impact: none / ledger row F### / follow-up issue
   link". iOS PRs that change a wire format must link the fixture change (G8-2).
2. **Flags are the sync point.** A feature's flag goes above 0% only when both clients are at least at
   "Code" in the ledger, or the owner explicitly accepts a single-platform rollout. The server already
   supports `allow_user_ids`, so cohorts can be platform-aware if click-web adds a `platforms` field
   to the flag config. That is a small web change, worth doing (**W-1**).
3. **Weekly drift report** (scheduled job): list iOS and click-web first-parent merges since the last
   baseline whose titles start with `feat`, and any ledger rows with an empty Android column. Post it to
   the team. The baseline SHAs go at the top of this sheet.
4. **Contract-first for click-web:** new `/api/*` routes add a row to `BACKEND_CONTRACT_MATRIX.md` in
   the same PR.

---

## 11. Schedule and staffing (indicative)

Assumes **2 Android engineers** (A, B) plus part-time help from one web engineer (W). Weeks start
Mon 2026-10-05.

| Week | A | B | W |
|---|---|---|---|
| 1 (10/05) | G0-1..5, G0-11, G0-12 | G0-7..10, G0-3 cross-check, G1-1 | G0-6 assetlinks, README drift (G1-8) |
| 2 (10/12) | G1-2 Live updates, G1-5 caches | G1-3 push routes, G1-4 channels, G1-6, G1-7 | W-1 flag platforms, mobile fixtures (G8-2) |
| 3 (10/19) | G2-1, G2-2 (drops develop) | G7-1 device approval, G7-2 deletion, G7-3 | tombstone `before` cursor (old open item) |
| 4 (10/26) | G2-3..5 (shared drops) | G3-4, G3-5, G3-6, G3-10, G3-11 | — |
| 5 (11/02) | G2-6..9 | G4-1, G4-2 (capture + keys) | — |
| 6 (11/09) | G3-1, G3-2 (event drops, recap) | G4-3..5 (observation v3, review, late joiners) | — |
| 7 (11/16) | G3-3, G3-7..9, G3-12 | G4-6..9; **device matrix: connect** | — |
| 8 (11/23) | G5-1, G5-2 (Places models, map) | G6-1 Activity, G6-2 reconnect, G6-6 | — |
| 9 (11/30) | G5-3..5 (Place page, surfaces) | G6-4 Find friends, G6-7 icebreakers, G6-9 pins, G6-10 | — |
| 10 (12/07) | G6-8 GIFs, G6-12..15 | G6-13, G6-5 (if D5 approves), G6-11 (P2) | — |
| 11 (12/14) | **G8 device matrix**, bug fixes, ledger | **G8 device matrix**, Play Data safety, release | — |

With one engineer, the order holds but the timeline roughly doubles (about 20 weeks). In that case, cut
G6-11, G6-5 and G2-6's cube transition first.

**Priority if time is short:**
1. G0
2. G1-1..3
3. G7-1, G7-2
4. G2-1/2
5. G4
6. G3-1..5
7. G2-3..9
8. G5
9. G6

The reasons: interop and data integrity first, then features that are already rolled out to iOS
cohorts, then new surfaces.

---

## 12. Decisions needed from the owner (blocking where noted)

| # | Decision | Blocks | Recommendation |
|---|---|---|---|
| D1 | **Calls:** `8900a338` re-enabled `incoming_call` with VoIP in the push function, but `AI.md` and Android say calls are retired. Also: should pushes use `click.caf`? | G0-7, G1-4 | If calls are not shipping on mobile, revert the `incoming_call` path in the function and keep Android ignoring it. Ship the custom sound through the Android channel either way. |
| D2 | **Background location for "Past meeting spots"** (iOS uses Always + `CLVisit`; the privacy notes say no background location) | G6-3 | Don't build it on Android. Ask iOS to reconcile its privacy notes or pull the feature. Play's background-location review is costly. |
| D3 | **Plan metadata stays plaintext** (carried over from README decision 1) | — | Unchanged; joint follow-up. |
| D4 | **Event reminder duplicates** (server `event_reminder` push plus local T-60) | G3-10 | Keep local T-60 only when the server push is off for that event. Otherwise suppress local. |
| D5 | **3-step onboarding** on Android (a funnel change) | G6-5 | Adopt it, behind a local A/B only if pilot analytics needs the comparison. |
| D6 | **App Clip equivalent** | — | No Instant App. Make sure `/c`, `/e` and `/p` web pages serve a good install page for Android visitors (the web already has an "Android coming soon" message; replace it with the Play link at launch). |
| D7 | **Shared-drops cube transition and encounter backdrops** (polish) | G2-6, G6-11 | P2; after the device matrix passes. |

---

## 13. Android-ahead items (keep; offer to iOS)

All still present and outside this sheet's scope. List them in the ledger so iOS can decide:
- encounter tether widget (`AM/encounter/EncounterTetherWidgetBridge.android.kt`)
- calendar overlap card
- beacon impression telemetry (`/api/beacons/{id}/impressions`)
- WorkManager handshake flush
- availability intents in search
- Home "Your Stats"
- poll-pair icebreakers

Still-open Android items from sheets 00–05, folded in here:
- push rendering and the ultrasonic matrix: G8-1
- Doze reminders: G8-1
- older-page tombstones: web week 3
- group-space prefetch on chat open: tack onto G6-13
- outbox persistence for media: P2, unscheduled
- every acceptance box in sheets 01–04 still unchecked: run them during G8-1

---

## 14. Definition of done for this sheet

- [ ] Every G-row is "Code" in the ledger with unit tests, or explicitly deferred by a D-decision.
- [ ] The G8-1 device matrix has passed on at least one current iOS build and one Android release candidate.
- [ ] Every flag in §3 G1-1 is readable on Android, and each flagged feature has been exercised with its flag on, in a
      staging cohort.
- [ ] Drift check green; cross-client fixtures (G8-2) decoded by both apps in CI.
- [ ] The weekly drift report (G8-4) is running, and the first report shows no unowned iOS `feat` merges.
