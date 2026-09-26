# Android ⇄ iOS Parity Program

**Status:** Draft spec · **Date:** 2026-09-26 · **Baseline:** `click-ios@2ddb4a9` vs `click@1bc926bc`

The native iOS app (`click-ios`) has moved ahead of the Android KMP app (`click`). Most of the new
work landed in iOS "Round 7" (commits `7857837`, `e097d7e`, `7b58ddf`, `2ddb4a9`, 2026-09-25). This
folder specifies everything Android needs to close that gap.

The backend is **already shipped** in `click-web` for every feature below:

| Migration | Adds |
|---|---|
| `20260924120000_users_bio.sql` | Profile bio |
| `20260924121000_message_tombstones.sql` | Deleted-message placeholders |
| `20260925120000_scheduled_messages_read_cursors.sql` | Send Later and group read receipts |
| `20260926090000_relationship_moments.sql` | Hangouts, presence and new nudge kinds |
| `20260926120000_chat_mutes.sql` | Per-chat mutes |

Almost everything here is therefore Android client work.

## Spec sheets

| # | Sheet | Scope |
|---|---|---|
| 00 | [P0 fixes](00-p0-fixes.md) | Bugs and privacy leaks found during the audit. Ship first; independent of features. |
| 01 | [Message scheduling](01-message-scheduling.md) | "Send Later" in direct and group chats. |
| 02 | [Profile, stories and recaps](02-profile-stories-recaps.md) | Friendship stats and levels, "Together" section, encounter map, "Your story", souvenirs, bio, Home recap fixes. |
| 03 | [Hangouts](03-hangouts.md) | Chat plans (propose, RSVP, reminders), logged hangouts (log, confirm), waves, hangout detection, relationship-moment nudges and pushes. |
| 04 | [Chat, groups and hubs](04-chat-groups-hubs.md) | Mutes, forwarding, reaction details, read cursors, tombstones, search, group profile, Hub Info, and more. |
| 05 | [App-wide remainder](05-app-wide.md) | Me/Settings, events, map, deep links, push, telemetry, and retired-feature cleanup. |

## Conventions used in every sheet

- `KMP/` = `click/composeApp/src/commonMain/kotlin/compose/project/click/click/`
- `AM/` = `click/composeApp/src/androidMain/kotlin/compose/project/click/click/`
- `iOS/` = `click-ios/Click/`
- `web/` = `click-web/`
- All code follows `click/AI.md`:
  - Logic lives in `commonMain`.
  - ViewModels expose `StateFlow`.
  - Nested state is updated with deep copies.
  - New features never use `auth.users` metadata.
- Every sheet has an **Acceptance** section. A feature is "at parity" only when every acceptance box is checked and its unit tests pass (`./gradlew :composeApp:testDebugUnitTest`).

## Phasing

| Phase | Contents | Rationale |
|---|---|---|
| **0: Fixes** | All of sheet 00 | Privacy leaks, a broken feature and a silent data-loss bug. Small diffs. |
| **1: Foundations** | Relationship API client (03 §B1). `FriendshipStats` port with golden tests (02 §1). Typed nudge DTO plus push routing for new types (03 §B5–B6). Shared "encrypt to wire" helper (01 §3.3). Local notification infrastructure: channels plus WorkManager scheduler (03 §A5). | Shared building blocks for phases 2–4. |
| **2: Focus features** | Sheet 01 (scheduling). Sheet 03 (plans, logged hangouts, waves, detection). Sheet 02 (Together, map, story, souvenir, bio, recap). | The user-requested focus areas. |
| **3: Chat parity** | Sheet 04 | Mutes and forwarding first, then read cursors and tombstones, then UX polish. |
| **4: App-wide** | Sheet 05 | Settings, events, deep links, cleanup of retired features. |

## Cross-cutting decisions: need an owner call before Phase 2

1. **Plan metadata is plaintext.**
   - iOS stores `metadata.plan` (title, time, place, lat/lon) unencrypted, next to E2EE `content` (`iOS/Core/Chat/ChatRepository.swift:884`).
   - Android should match the wire format for interop.
   - Moving the plan into the encrypted envelope would be a breaking change on both clients.
   - **Recommendation:** ship Android on the current format and file a joint follow-up.
2. **Retired features still on Android.** Ghost Mode (removed on iOS in F48) and Seed-a-Room / event teasers (spec §56.2.2).
   - **Recommendation:** remove from Android in Phase 4 (05 §A).
3. **Home nudges.** iOS surfaces server nudges as a single priority "opportunity" card on Home. Android shows them only as an inbox banner.
   - **Recommendation:** move to Home per iOS priority (03 §B5) and keep the inbox banner as fallback.
4. **Supabase mirror.** `click/supabase/migrations` stops at `20260919120000`. Android talks only to click-web, so the mirror is not required at runtime, but `scripts/check-supabase-drift.sh` will flag it.
   - **Recommendation:** run `scripts/sync-supabase-from-click-web.sh` as part of Phase 1.
5. **Items where Android is ahead of iOS.** Keep these; they are out of scope:
   - encounter tether
   - calendar overlap card
   - beacon impression telemetry
   - WorkManager handshake flush
   - availability intents in search
   - Home "Your Stats"
   - poll-pair icebreakers

## Parity scoreboard (fill in as work lands)

"Code" means implemented with unit tests; device checks are still open.

| Area | Items | Done |
|---|---|---|
| 00 P0 fixes | 8 | 8 (code). Device checks open: in-person ultrasonic matrix, push rendering. |
| 01 Scheduling | 1 feature (7 acceptance criteria) | Code-complete. Cross-platform check with iOS still open. |
| 02 Profile / stories / recaps | 8 sections | 7 of 8. §6 is partial: bio, relationship line, tag editing and the post-connect "Reconnected · Nth time" subtitle are done; the "Extended Hangout" title, merged common-ground layout and public-profile check are still open. |
| 03 Hangouts | 14 (A1–A7, B1–B7) | 14 (code). Reminder delivery under Doze has not been verified. |
| 04 Chat / groups / hubs | 13 sections | 10 of 13 (code) plus a partial §10. Still open: §8 lifted-bubble action overlay (UX only); §11 shared-content paging (group photo rules and immediate re-securing are done); §13 clique-eligibility naming; §10 persisting the failed-send queue across restarts (retry and discard are done). |
| 05 App-wide | 34 rows (A1–E3) | 29 (code): A1–A4, B1–B11, C1–C5, C7–C9, D1–D5, E1. Partial: E2 (Nth-time copy done, Extended Hangout open), E3 (souvenir and "Go together?" sit above the tags, but the reveal step itself is unchanged). Deferred: C6 soundtrack resolver (optional), D6 local message store (optional, large). Out of scope: D7 App Clip. |

Android-specific notes from Phase 2:
- **Upcoming plans (03 §A6)** are read from `messages.metadata.plan` through PostgREST, because Android has no on-device message store.
- **Tapping a plan** in the profile "Coming up" list opens the chat, but does not jump to the plan message.
- **Encounter-tag edits** show immediately in the timeline and persist to `connection_encounters`.

Android-specific notes from Phase 3:
- **Mutes are now enforced by this repo's `send-push-notification`.** Its source didn't check `chat_mutes`, even though the migration comment says it does. The function needs a deploy.
- **v2 push previews** use recent epoch keys kept in the app's encrypted prefs: at most 3 epochs per chat and 200 chats, wiped at sign-out.
- **"Message deleted" placeholders** cover the latest window and realtime deletes. Older history pages don't show tombstones.
- **In-chat search** covers loaded (decrypted) messages only. Android has no on-device full-text store yet (05 D6).
- **Forwarding** supports text and photos, not voice or files.

Android-specific notes from Phase 4:
- **Ghost Mode** is gone. Each sign-in also sends `PATCH /api/user/ghost-mode {enabled:false}`, as iOS does, so an older client can't leave someone hidden.
- **Event teasers** are gone. Their preference, and the call-notification preference, are no longer sent; the server treats both fields as optional. `event_teaser` and `incoming_call` pushes are dropped.
- **Appearance** defaults to System. It is stored in the old dark-mode pref, where "absent" means System, so existing Light/Dark choices carry over.
- **Delete account** opens `joinclick.co/?tab=settings` in a Custom Tab (two taps from Me), matching iOS. The app never calls `DELETE /api/user/delete`.
- **Calendar → Disconnect** is a local switch that stops Click reading the calendar; Android can't revoke a runtime permission from inside the app.
- **Photos permission** shows "Not needed", because Android uses the system photo picker.
- **Push taps** go through one table (`PushRoutes`) that follows iOS: `archive_warning` opens the person's profile (or the Clicks list) rather than the Archived tab named in 05 §D2.
- **Music links** are checked against the server's exact allowlist (https Spotify, Apple Music, YouTube). SoundCloud isn't on it, so it isn't accepted, despite 05 §C5.
- **Event reminders** are rebuilt from the RSVP cache and saved events on start, after RSVP/save changes, and when the Alerts toggle changes. RSVPs for events the app hasn't loaded yet are picked up once the event is seen.
- **"Couldn't refresh"** appears only when the network is up and `/api/ping` answers. Otherwise the banner says "You're offline".

After each item lands, add a row to `click-ios/Docs/PARITY_LEDGER.md` (or a mirrored Android ledger) so both repos agree on status. `click-ios/Docs/BACKEND_CONTRACT_MATRIX.md` is also stale: it lacks `/api/hangouts*`, `/api/me/presence`, `/api/connections/{id}/wave`, `/api/chat/scheduled`, `/api/chat/notifications` and `/api/chat/messages/read`. Update it alongside Phase 1.
