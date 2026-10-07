# 03: Hangouts

On iOS, "hangouts" covers two separate systems. Android has **neither**.

- **A. Chat plans.** A future hangout is proposed as a chat message. People RSVP with ✅ or ❌ reactions, and a local reminder fires. The server only stores the message metadata.
- **B. Logged hangouts.** A past hangout is logged and both people confirm it (`/api/hangouts`). This part also covers:
  - opt-in "Hanging out?" detection (`/api/me/presence`)
  - waves
  - relationship-moment nudges and pushes

Friendship stats, stories and souvenirs are computed from encounters. They are specced in [02](02-profile-stories-recaps.md).

Android is ahead of iOS on **availability intents** and **calendar free/busy**. These are unchanged here.

---

## A. Chat plans

**iOS reference:**
- `iOS/Features/Chat/PlanViews.swift`
- `iOS/Core/Chat/PlanReminders.swift`
- `iOS/Core/Chat/ChatMessage.swift:254-305` (`HangoutPlan`)
- `ConversationModel.swift:544-565,641-664`
- `MessageBubbleView.swift:181-183,363-366`
- `ChatView.swift:161-163,237-239,600,645-660`
- `GroupSharedContent.swift:281-287`

### A1. Wire format (must match iOS)

A plan is a normal **text** message: `POST /api/chat/messages` with `message_type: "text"`.

```
content  = E2EE ciphertext of "📅 {title} · {EEE, MMM d, h:mm a}[–{h:mm a}][ · 📍 {place}]"
metadata = { crypto_version, epoch, sender_device_id, client_message_id,
             plan: { title: String(≤80), starts_at: Long ms, ends_at?: Long ms (> starts_at),
                     place_name?: String, lat?: Double, lon?: Double } }   // lat/lon only as a pair
```

- RSVPs are ordinary reactions: Going = `✅`, Can't = `❌`. The two are mutually exclusive per user.
- ⚠️ `metadata.plan` is **plaintext** on the server. See README decision #1. Match the format anyway.
- Clients that don't parse plans still show the summary text, so older Android builds degrade gracefully.

### A2. Model and send path

**New `KMP/data/models/HangoutPlan.kt`:**
- `@Serializable HangoutPlan(title, startsAtMs, endsAtMs?, placeName?, lat?, lon?)`
- `Message.planOrNull()` parses `metadata.plan`
- `HangoutPlan.summary()` produces the same text as iOS
- `endsOrAssumedEnd` is `endsAtMs ?: startsAtMs + 3h`
- Constants `GOING = "✅"`, `DECLINED = "❌"`

**`KMP/viewmodel/ChatViewModelPlans.kt`:**
- `sendPlan(plan)`:
  - create an optimistic row
  - send through the shared encrypt-to-wire builder (01 §3.3) with `extraMetadata = {plan}`
  - E2EE is required: do **not** use the plaintext beacon path
  - on success, auto-RSVP Going
- `rsvp(messageId, going)`: remove the opposite reaction, add the chosen one, then call `PlanReminderScheduler.update(...)`.
- Scope: direct and group chats only (`supportsPlans`). Not `HubChatViewModel`.

### A3. Planner sheet: `KMP/ui/chat/PlanHangoutSheet.kt`

**What**
- Title field, max 80 characters.
- Idea chips: ☕️ Coffee, 🍜 Dinner, 🍻 Drinks, 🚶 Walk, 🎬 Movie, 🏋️ Workout.
- "＋ Add your own" custom ideas:
  - max 40 characters each
  - listed first
  - long-press to remove
  - stored in `TokenStorage` under `plan_custom_ideas`

**When**
- Default: 7 PM today, or tomorrow if that is less than 2 hours away.
- Day chips: Today, Tomorrow, then the next 5 weekdays. Picking a chip keeps the time and length.
- Start: future times only.
- Optional "End time" toggle. The end must be at least 15 minutes after the start. The length is preserved when the start moves.

**Where (optional)**
- Place search through `KMP/utils/GeocodingService.searchAddresses`, biased to the last location fix.
- In direct chats, also show "Places you've met" chips: the top 8 named `FriendshipSpot`s (02 §1).

**Send** is enabled only when the title is non-empty, the start is in the future, and the end (if set) is after the start.

**Entry points**
- Composer "+" menu → "Plan a hangout" (`ConnectionChatMessageComposer.kt:~284`)
- Chat header overflow (`ChatViewHeader.kt:269`)
- Group revival banner (A7)
- Profile "Plan" / "Plan next" / "Plan something"
  - Hand off through a `pendingPlanChatKey` in the app-level state (for example `AppDataManager` or the nav state).
  - The chat screen consumes the key on open and shows the sheet.

### A4. Plan card: `KMP/ui/chat/PlanCard.kt`

- Dispatch it in `ChatMessageBubble.kt` before the text branch, next to the `isBeaconChatMessage()` branch at `:139`.
- Content:
  - "PLAN" label and the title.
  - When-text:
    - "Today · 7:00 PM–9:00 PM"
    - "Tomorrow"
    - a weekday name if within 7 days
    - otherwise "Sat, Oct 4"
  - The place is tappable and opens `geo:{lat},{lon}?q={place}`, or a Maps search URL.
- **Going** and **Can't** buttons. Disable them while the message is sending or failed.
- "N going · M can't" opens the reactors sheet (04 §5).
- After `endsOrAssumedEnd`, replace the buttons with "N went" or "This plan has passed".
- Hide ✅ and ❌ from the normal reaction row on plan messages.
- No edit, no cancel, no "propose a new time". Deleting the message is the cancel.

### A5. Local reminders (Phase 1 foundation: shared with event reminders, 05 §C3)

**Scheduler**
- `expect object LocalReminderScheduler { schedule(id, atMs, title, body, deepLink); cancel(id) }`
- Android actual in `AM/notifications/LocalReminderScheduler.android.kt`:
  - `WorkManager` `OneTimeWorkRequest`
  - `setInitialDelay`
  - unique name `plan.<messageId>` with `ExistingWorkPolicy.REPLACE`
- The worker posts a `NotificationCompat` notification:
  - on a new **"Plans & events"** channel (today only `click_messages` exists)
  - its deep link is `MainActivity.createChatDeepLinkIntent(chatId, connectionId)`

**`PlanReminderScheduler.update(message, myRsvp)` rules** (same as iOS)
- Always cancel any existing reminder first.
- Schedule only when:
  - I am Going,
  - the start is more than 5 minutes away,
  - `POST_NOTIFICATIONS` is granted (never prompt from here).
- Fire at start − 60 min, or now + 1 min if the start is closer than that.
- Title: the plan title.
- Body: "Starts at {h:mm a}[ · {place}] with {chatName}".

**Improvements over iOS** (allowed):
- Also cancel when the message is deleted and when the RSVP changes to Can't.
- Re-evaluate reminders for loaded plans on app start.

### A6. Upcoming plans list

- Android has no local message DB, so read plans server-side: PostgREST `messages?chat_id=eq.{id}&metadata->plan=not.is.null&order=time_created.desc&limit=100`. Verify that RLS permits this. Fall back to scanning messages already loaded in the thread.
- Keep plans that haven't ended and aren't deleted. Sort them soonest first.
- Render up to 3 rows: title, "when · place", and "N going".
- Used by the peer "Together" section (02 §2) and the group section (02 §7).

### A7. Group revival banner

- Show it in a group chat when there are at least 10 messages and the last message is 21 or more days old.
- Buttons: **Plan** (opens the planner) and **Dismiss**. Dismissal lasts for the current view session only, as on iOS.
- Mount it in `ui/screens/ChatView.kt` or `ChatViewOverlays.kt`.

---

## B. Logged hangouts, waves, detection, moments

**iOS reference:**
- `iOS/Core/Connections/RelationshipRepository.swift`
- `FriendshipViews.swift:118-162,281-366`
- `PeerProfileModel.swift:116-180`
- `HomeFeedModel.swift:22-80,261-300`
- `HomeComponents.swift:243-350`
- `SettingsPages.swift:195-266`
- `AppEnvironment.swift:337-348`
- `ClickApp.swift:291-299`

**Backend:**
- Migration `20260926090000_relationship_moments.sql` (tables `hangout_confirmations` and `presence_pings`, plus the widened `nudges.nudge_type`)
- `web/lib/hangouts/hangouts.ts`
- `web/lib/nudges/moments.ts`
- `web/lib/cron/relationshipMoments.ts`

### B1. API client (Phase 1): `KMP/data/api/ApiClientRelationshipEndpoints.kt`

| Call | Request | Response |
|---|---|---|
| `logHangout` | `POST /api/hangouts {connection_id, occurred_at ISO, lat?, lon?, location_name?}` | `{hangout}`. `409 pending_exists`. `occurred_at` must be within the last 7 days. |
| `pendingHangouts` | `GET /api/hangouts` | `{hangouts:[{id, connection_id, peer_user_id, source: manual\|nearby, status, occurred_at, location_name, confirmed_by_me, requested_by_me, expires_at, encounter_id}]}` (max 20) |
| `confirmHangout` | `POST /api/hangouts/{id}/confirm` | `{status: confirmed\|pending, already_logged, hangout}` |
| `declineHangout` | `POST /api/hangouts/{id}/decline` | 2xx |
| `wave` | `POST /api/connections/{id}/wave` | `{sent, already_waved_today}`. Limited to one wave per day. |
| `reportPresence` | `POST /api/me/presence {lat, lon}` | `{prompted}` |
| `clearPresence` | `DELETE /api/me/presence` | 2xx |

Follow the style of `ApiClientBeaconEndpoints.kt`. Wrap the calls in a `RelationshipRepository`.

### B2. Pending hangout rows

These appear in the peer profile Together section (02 §2) and in the Home opportunity card (B5).

| Case | Row text | Actions |
|---|---|---|
| The peer logged the hangout | "{name} logged a hangout" | Confirm / Not us |
| Detected nearby | "Hanging out with {name}?" | Confirm / Not us |
| I requested it and am waiting | "Waiting for {name} to confirm" | none |

After a confirm, refresh the encounters so that the stats update. Toast "Already logged" when `already_logged` is true.

### B3. Log-hangout sheet: `KMP/ui/components/LogHangoutSheet.kt`

- Date and time, going back at most 7 days.
- Place: search, or "Use my current location" (only when the chosen time is within 3 hours of now).
- A 409 shows "A hangout with {name} is already waiting to be confirmed."

### B4. Wave

- In `ProfileActionGrid` (`ProfileSheetPanels.kt:167-228`), replace **Nudge** with **Wave**. The old Nudge sends a chat message; Wave calls `/wave` instead.
- Toasts: "You waved at {name} 👋", or "You already waved today".
- Remove `HomeViewModel.sendNudgeByConnectionId` (`:710-728`) once nothing references it.

### B5. Nudges: typed model and Home opportunity card

**Typed DTO**
- Make `InboxNudgeDto` (`KMP/data/api/ApiClientModels.kt:181-190`) typed with `enum NudgeKind`:
  - `reconnect_lull`, `shared_upcoming_event`, `anniversary`, `memory_prompt`, `group_revival`, `wave`, `hangout_confirm`
- Add payload accessors for `peer_user_id`, `chat_id`, `group_id`, `group_name`, `confirmation_id`, `place_name`.
- **Skip unknown kinds.**

**Home opportunity card** (`HomeViewModel` + `HomeScreen.kt`)
- Shows one card. Pick it by the iOS priority order:
  1. saved event, live now
  2. saved event, today
  3. nearby event, live now
  4. **hangout_confirm**
  5. shared event
  6. **wave**
  7. say-hi deadline < 12 h
  8. **anniversary**
  9. reconnect
  10. **memory_prompt**
  11. **group_revival**
  12. nearby event today

**Per-kind behavior**

| Kind | Title | Primary action | Secondary |
|---|---|---|---|
| hangout_confirm | "Hanging out?" | **Confirm** → `/confirm` | **Not us** → `/decline` |
| wave | "Wave" | **Wave back** → `/wave` | Dismiss |
| anniversary | "Anniversary" | **Say hi** → open chat | Dismiss |
| memory_prompt | "Memories" | **Add memory** → mark acted and open the peer profile (journal) | Dismiss |
| group_revival | "Your groups" | **Plan** → mark acted and open the group chat by `payload.chat_id` | Dismiss |
| reconnect / shared event | existing | **Say hi** | Dismiss |

- Hide the card optimistically after an action (keep a `resolvedNudgeIds` set). Show the result as a toast.
- Keep `InboxNudgeBanner` in `ConnectionsListView` as a fallback, and give it the same per-kind dispatch. This fixes P0-3.

### B6. Push handling: `AM/notifications/ClickFirebaseMessagingService.kt`

**Payload** (`moments.ts:201-209`): `{type, nudge_id, connection_id?, chat_id?, group_id?, peer_user_id?, confirmation_id?}` plus `title` and `body`.

**Gating**
- Cron moments (`anniversary`, `memory_prompt`, `group_revival`) use the **"Relationship moments"** preference (`reconnect_nudge_push_enabled`).
- User-triggered moments (`wave`, `hangout_confirm`) use the message preference.
- This matches the server.

**Rendering**
- Use `showSimplePush` with `data.title` and `data.body`.
- Never use the chat-message path.

**Tap routing**

| Type | Opens |
|---|---|
| `anniversary`, `memory_prompt`, `hangout_confirm` | The peer profile. Needs a new `MainActivity.createProfileDeepLinkIntent(userId)`. |
| `wave` | The chat, by `connection_id`. |
| `group_revival` | The group chat, by `chat_id`. |

**Settings copy:** rename the "Reconnect nudges" preference label to **"Relationship moments"**.

### B7. Hangout detection (opt-in)

**Setting**
- Privacy page (`SettingsScreen.kt:564+`) toggle: "Hangout detection".
- Explainer: when you and a Click are near each other, Click asks you both if you're hanging out.
- Turning it on requests location permission if needed.
- Store the opt-in locally under `hangout_detection_opt_in` in `TokenStorage`. There is no server column.
- Turning it off calls `DELETE /api/me/presence`.

**Ping**
- `POST /api/me/presence {lat, lon}` on app foreground (`MainActivity.onResume`, or the `App.kt` lifecycle observer).
- Throttle to at most once every 10 minutes.
- Only send when there is a location fix no older than 120 s with accuracy ≤ 100 m.
- Never send in the background.

---

## Acceptance

- [ ] A plan sent from Android renders as a plan card on iOS, and a plan sent from iOS renders as a plan card on Android. Content is E2EE and `metadata.plan` matches A1 byte-for-byte in structure.
- [ ] Going and Can't are mutually exclusive, and the counts are correct. Past plans show "N went".
- [ ] The reminder fires 60 minutes before start for Going plans. It is cancelled on Can't or on delete, and is not scheduled when notification permission is missing.
- [ ] The planner defaults, chips, custom ideas, end-time rules and met-spot chips behave as in A3.
- [ ] Upcoming plans appear on the peer and group profiles.
- [ ] Log hangout → the peer confirms on either platform → the encounter appears and the stats update. A 409 is handled.
- [ ] Wave works and respects the one-per-day limit. The Nudge button is gone.
- [ ] Hangout detection pings only when opted in, fresh and accurate, and at most every 10 minutes. Opting out clears presence.
- [ ] All 7 nudge kinds render and act correctly on Home and in the inbox. Unknown kinds are ignored.
- [ ] All 5 moment push types show the server title and body, route correctly, and never touch chat inbox state.
