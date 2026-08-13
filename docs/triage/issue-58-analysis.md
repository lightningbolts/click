# Issue #58 ("Bugs") — Triage & Root-Cause Analysis

**Status:** durable (S1–S3 bugs/polish landed 2026-08; keep this file as the original diagnosis)
**Issue:** https://github.com/lightningbolts/click/issues/58
**Analysed at:** `click` @ `6977c67a` (main), `click-web` @ `fce5fb9` (main)
**Scope:** 20 reported items spanning `click` (KMP app), `click-web` (Next.js), Supabase edge functions and SQL.

## Landed S1 (2026-08)

Tracker [#58](https://github.com/lightningbolts/click/issues/58) stays open. Child issues:

| Issue | Status |
|---|---|
| [#62](https://github.com/lightningbolts/click/issues/62) push uniqueness | Landed — unique `(user_id, device_id, token_type)`; prune dead tokens; VoIP-then-standard fallback per device |
| [#63](https://github.com/lightningbolts/click/issues/63) event reminders | Landed — 30 min + full local day; due-by-timestamp; Edge Function HTTP-calls Next.js |
| [#64](https://github.com/lightningbolts/click/issues/64) realtime | Landed — hub `HubRealtimeState`; `chats` + `group_members` inserts; subscribe-first on open thread |
| [#65](https://github.com/lightningbolts/click/issues/65) map pins | Landed — all connection pins; Memory Map does not filter; single `updateRenderData` writer |
| [#68](https://github.com/lightningbolts/click/issues/68) root SSR | Already fixed on main; SSR regression test added |
| [#61](https://github.com/lightningbolts/click/issues/61) LiveKit calls | Landed (env plumbing) — token route reads LiveKit via `runtimeEnv` + URL normalize; `GET /api/health/env` reports presence flags. Production Worker vars still operator-owned; authenticated token mint after deploy is the remaining probe. |
| [#66](https://github.com/lightningbolts/click/issues/66) prefs/senders | Landed — per-type columns + Settings toggles; availability-match cron; hub message fan-out; `send-push-notification` category gates |
| [#69](https://github.com/lightningbolts/click/issues/69) recap/settings | Landed — `GET /api/me/recap`; Home recap card; Settings hub + subpages including saved events |
| Personality picker | Landed — `users.personality_tags`; exactly 5 from curated taxonomy; onboarding for **new** signups only; Settings editor for everyone |

## Landed S2–S3 remainder (2026-08-12)

Tracker [#58](https://github.com/lightningbolts/click/issues/58) stays open until child comments exist. **Out of scope** still: [#54](https://github.com/lightningbolts/click/issues/54) `ClickButton`. **Now landed in this remainder:** [#61](https://github.com/lightningbolts/click/issues/61) LiveKit env plumbing, [#66](https://github.com/lightningbolts/click/issues/66) prefs/senders, [#69](https://github.com/lightningbolts/click/issues/69) recap/settings, personality picker.

| Item | Status |
|---|---|
| [#67](https://github.com/lightningbolts/click/issues/67) avatar re-prompt | Landed — tri-state `userHasAvatar: () -> Boolean?`; unknown holds shimmer, never flashes Avatar |
| [#67](https://github.com/lightningbolts/click/issues/67) permission stacking | Landed — FIFO `PermissionRequestQueue` + prime sheet; camera no longer auto-launches |
| [#67](https://github.com/lightningbolts/click/issues/67) onboarding + login↔signup | Landed — back, 3-step progress, Welcome unblocked from interests fetch, `AnimatedContent` login↔signup |
| Hub fake Read receipts | Landed — honest UI only (`newestSentMessage = null`; do not force `isRead`). No hub receipt schema |
| Chat inbound scroll snap | Landed — initial paint still `scrollToItem(0)`; near-bottom inbound follow uses `animateScrollToItem(0)` |
| Maestro E2E | Landed — mobile smoke `login_signup_toggle`; auth tabs assert no Avatar gate + map chrome; web landing asserts not `LoadingScreen` |

Historical sections below are the original diagnosis and may describe pre-fix code.

---

## 0. How to read this

Issue #58 is a single body of ~20 unrelated items with three labels (`bug`, `enhancement`, `help wanted`)
and no acceptance criteria. It mixes **production outages**, **silent data bugs**, **missing features** and
**redesign requests**. It cannot be closed as one unit of work; §7 proposes a split.

Every claim below carries a confidence marker:

| Marker | Meaning |
|---|---|
| **[LIVE]** | Verified by executing something against a running system during this analysis. |
| **[CODE]** | Verified by reading the code at the cited `file:line`. |
| **[UNPROVEN]** | Plausible but not established. Diagnostic step given. Do not action as fact. |

Items are ordered by severity, not by their order in the issue.

---

## 1. The five findings that should change your priorities

These emerged from verification and are **not** what the issue text implies.

### 1.1 The LiveKit "env vars are unset" theory is unproven — the endpoint is alive **[LIVE]**

An unauthenticated probe of the production token endpoint returns **401**, not 500:

```
$ curl -s -X POST https://joinclick.co/api/livekit/token \
    -H "Content-Type: application/json" -d '{"room":"probe","identity":"probe"}' -w "%{http_code}"
{"error":"Unauthorized"}   401
```

This is easy to misread. In `click-web/app/api/livekit/token/route.ts` the **auth check runs before the
env check**:

- `:55-58` → `getSupabaseFromRouteRequest` → returns 401 if unauthenticated.
- `:71-75` → `LIVEKIT_API_KEY` / `LIVEKIT_API_SECRET` / `LIVEKIT_WS_URL` check → 500 if unset.

Because auth short-circuits first, **a 401 proves the route is deployed and reachable but reveals nothing
about the LiveKit environment.** The endpoint being "up" is therefore not evidence that calls work, and
the 401 is not evidence that they are broken.

Also refuted: there is no client/server path mismatch. The client posts to
`"$CLICK_WEB_BASE_URL/api/livekit/token"` (`ApiClient.kt:898-905`) with the base hardcoded to
`https://joinclick.co` (`QRModels.kt:8`), and that route exists. The old LAN/Flask base is gone
(`ApiConfig.kt:9`).

**Next diagnostic (do this before writing any code):**

```bash
# with a real user access token
curl -i -X POST https://joinclick.co/api/livekit/token \
  -H "Authorization: Bearer <SUPABASE_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"connection_id":"<real-id>","room_name":"click-<real-id>-x","participant_name":"probe"}'
```

- `500 {"error":"LiveKit environment is not configured"}` → deployment env is the cause; fix config, no code change.
- `200` + JWT → the server is fine and the fault is client-side (connect/publish/permissions); reopen the
  investigation at `CallManager.android.kt:152-208` and `ClickLiveKitBridge.swift:76-113`.

Until this returns, "calls are broken" has **no** established root cause. Note the issue says calls fail on
*both* DM and group and on *both* platforms — a shared server-side dependency fits that shape better than
two independent client regressions, which is why the env hypothesis is attractive. It is still unproven.

### 1.2 Double notifications on iOS are partly **by design** **[CODE]**

The push sender fans out to **every** `push_tokens` row for the recipient
(`supabase/functions/send-push-notification/index.ts:714-717`, `select("*").eq("user_id", ...)`).
`shouldSendToToken` then filters — and for calls it deliberately does not:

```kotlin
// index.ts:285-287
// incoming_call: send to every iOS token. VoIP wakes CallKit; standard APNs adds banner + sound
// if VoIP fails or is delayed.
if (category === "incoming_call") { return true; }
```

So an incoming call on iOS sends **both** a VoIP push (CallKit UI) **and** a standard alert push
(`aps.alert`, `index.ts:213-233`) to the same device, with no de-duplication. When VoIP is *not* delayed —
the normal case — the user gets CallKit **and** a banner. That is a double notification, and it is the
documented intent of the fallback.

This is a real design trade-off, not an accident: removing the standard push reintroduces the missed-call
risk the comment guards against. The fix is conditional suppression (see §3.2), not deletion.

**Second, independent mechanism — stale tokens are never pruned:**

- `push_tokens` is unique on `token` only, with **no device identity column**
  (`database/add_push_tokens.sql:1-8`, plus `token_type` in `add_push_token_types.sql`).
- A grep for `410`, `Unregistered`, `BadDeviceToken`, `delete()` in the sender returns **zero matches** —
  failed deliveries never remove the row.

So one physical device that rotates its token (reinstall, restore from backup, APNs refresh, clearing app
data) leaves its **old row in place forever** and accumulates a new one. Every subsequent message fans out
to both rows. This explains "some devices" precisely: it only affects devices that have re-registered, which
is why it looks random. It affects Android equally, which matches the issue's "Android unclear".

The classic Android cause (OS auto-displaying a `notification` payload *and* the app posting its own) is
**refuted**: chat FCM payloads are data-only (`index.ts:144-177`) and only
`ClickFirebaseMessagingService.kt:41-188` displays them.

### 1.3 Event reminders silently miss ~75% of events **[CODE]**

This is a latent bug the issue did not report — it only asked to change 1 hour → 30 minutes.

Production scheduling is Supabase pg_cron → `cron-hourly-maintenance`, **hourly at `0 * * * *`**
(`click-web/app/api/cron/hourly/route.ts:5-9`; schedule documented in
`click-web/supabase/migrations/20260607120000_pg_cron_hourly_maintenance.sql:18-21`).

But the matching window is **15 minutes** (`click-web/supabase/functions/cron-hourly-maintenance/index.ts:258`,
mirrored in `click-web/lib/cron/eventReminders.ts:26`):

```ts
const windowMs = 15 * 60 * 1000;
const oneHourBefore = startMs - 60 * 60 * 1000;
if (nowMs >= oneHourBefore && nowMs < oneHourBefore + windowMs) kinds.push('one_hour');
```

**A 15-minute detection window sampled once per hour catches 25% of cases.** With the sweep at `HH:00`, the
`one_hour` branch (`index.ts:286-287`) is true only for events starting in `[HH+1:00, HH+1:15)`. An event starting at 14:30 is
evaluated at 13:00 (too early) and 14:00 (too late) and **never** gets its reminder. No error is logged
because nothing failed — the condition was simply false both times.

Two further defects in the same function:

- **`day_of` fires at midnight UTC**, not in the user's timezone (`index.ts:283-285` floors to a UTC day
  boundary). Users west of UTC get "Event today" the evening before.
- **The logic is duplicated** in `click-web/lib/cron/eventReminders.ts` and
  `click-web/supabase/functions/cron-hourly-maintenance/index.ts`. Only the second is the production path.
  Editing the first — the more discoverable one — changes nothing in production.

**And the schedule may not exist at all.** `20260607120000_pg_cron_hourly_maintenance.sql` is
**entirely commented out**; its closing line reads:

> `-- This migration is documentation-only; pg_cron must be configured in the SQL Editor`

So whether *any* reminder has ever fired cannot be determined from the repository. Verify before building
anything on top:

```sql
SELECT jobid, jobname, schedule, active FROM cron.job WHERE jobname = 'click-hourly-maintenance';
```

### 1.4 The root-route SEO bug — **fixed**

**Was:** SSR always rendered `LoadingScreen` ("Loading your connections...") because
`app/page.tsx` gated on `useAuth().loading`, which stays `true` until a client `useEffect`
runs. Crawlers got ~199 characters of chrome and **zero** marketing copy (no H1, waitlist, or
feature text). Title/meta still rendered.

**Fix:** `app/page.tsx` is now a Server Component that resolves the cookie session via
`createSupabaseServerClient().auth.getUser()`. Anonymous requests render
`components/landing/LandingPage.tsx` (no auth-`loading` gate), so SSR includes the marketing
hero. Authenticated sessions render `components/HomeAuthenticated.tsx` (`DashboardView` with
`ssr: false`, same Cloudflare Worker bundle constraint as before). After client login on the
landing page, `LandingPage` swaps to the dashboard and calls `router.refresh()`.

**Verify:** crawler HTML should contain marketing copy and **0** occurrences of
`Loading your connections` (see §6).
### 1.5 The "unify textboxes" complaint is largely already solved — buttons are the real gap **[CODE]**

The issue singles out textboxes. Measured, text inputs are in decent shape:

| Component | Call sites |
|---|---|
| `ClickOutlinedTextField` (canonical, `ui/components/ClickOutlinedTextField.kt:49`) | **29** |
| raw `TextField(` | 4 |
| raw `BasicTextField(` (excluding the one inside the wrapper) | 4 |
| raw `OutlinedTextField(` | **0** |

That is ~78% adoption with **8 outliers** — a contained cleanup, not "fragmented across surfaces".

The genuinely unstandardised control is the **button**: there is no `ClickButton` at all, against
**157** raw `Button` / `TextButton` / `OutlinedButton` / `FilledTonalButton` call sites. That finding, with
the corresponding work package (WP5), is documented in the issue #54 audit —
`docs/ui-ux/audit/00-ui-audit-and-plan.md`, currently on the unmerged branch `docs/ui-audit-issue-54`.

**Recommendation:** delete this item from #58 and let issue #54 own component unification. Splitting it
across two issues guarantees divergent conventions — the exact problem the item complains about.

---

## 2. Item-by-item triage

Severity: **S1** user-visible breakage / data loss · **S2** significant defect · **S3** polish · **F** new feature.

| # | Item (abbreviated) | Verdict | Severity | Confidence |
|---|---|---|---|---|
| 1 | Double notifications on some devices | Two causes found (§1.2) | S1 | **[CODE]** |
| 2 | Delivered/read receipts | Implemented for DM/group; **absent for hubs** | S3 | **[CODE]** |
| 3 | Permission requests clear / no stacking | No coordinator exists; several auto-fire sites | S2 | **[CODE]** |
| 4 | Onboarding intuitive/clear/fast | 3 screens; no back nav, no progress, blocking fetch | S3 | **[CODE]** |
| 5 | Handle sign-up / logging back in | Works; screen swap is unanimated | S3 | **[CODE]** |
| 6 | Name, birthday, email, interests | **All four already collected** | — | **[CODE]** |
| 6b | Personality picker (pick five) | **Does not exist** — no UI, no column | F | **[CODE]** |
| 7 | Transitions never abrupt | Onboarding animated; **login↔signup is not** | S3 | **[CODE]** |
| 8 | Asked for profile pic when one exists (Android) | Race on local cache (§3.1) | S2 | **[CODE]** |
| 9 | Map shows only a subset of pins | Narrowing by `showOnMapEnabled`; two competing writers (§3.3) | S1 | **[CODE]** |
| 10 | LiveKit calls don't work at all | **Root cause unproven** (§1.1) | S1 | **[UNPROVEN]** |
| 11 | New DM/GC must sync in real time | No consumer subscribes to `chats` inserts (§3.4) | S1 | **[CODE]** |
| 12 | Chats lag behind notification previews | Subscription starts after heavy load (§3.4) | S2 | **[CODE]** |
| 13 | Chat animations smoother | No item animation; `scrollToItem(0)` snaps | S3 | **[CODE]** |
| 14 | Notifications for beacons/availability/GC/hubs | Beacons broken (§1.3); GC works; **availability + hubs missing** | S1/F | **[CODE]** |
| 15 | Per-type notification toggles in settings | Only 2 booleans exist | F | **[CODE]** |
| 16 | Unify UI components (textboxes) | Largely done; buttons are the gap (§1.5) | S3 | **[CODE]** |
| 17 | Restructure home page (recap) | Data mostly available; needs aggregation | F | **[CODE]** |
| 18 | Saved events in settings; revamp settings | Saved events on Home only; settings = 1114 lines | F | **[CODE]** |
| 19 | Root route shows app loading state | **Fixed** — RSC + LandingPage SSR (§1.4) | S2 | **[LIVE]** → fixed |

### Items in the issue that are already satisfied

Worth striking from the issue to reduce noise:

- **"Name, birthday, email, interests"** — all collected today: names + birthday at `SignUpScreen.kt:71-124`
  (schema `database/migrate_user_names_and_birthday.sql:10-20`), email at sign-up/login, interests via
  `InterestTaggingScreen.kt` into `public.user_interests` (`database/add_user_interests.sql:1-18`).
  Only the **personality picker** is genuinely missing.
- **"Group chats notify for all message types"** — the insert trigger already fires for every `messages`
  row, excluding only `call_log` and E2EE `e2e:` ciphertext
  (`database/add_message_types_and_metadata.sql:46-52`, `database/skip_e2e_duplicate_message_push.sql:1-48`).
- **Read/delivered receipts for DMs and groups** — both columns, both routes and the client batching all
  exist (`supabase/migrations/20260421120000_messages_delivered_at.sql`,
  `click-web/app/api/chat/messages/{read,delivered}/route.ts`, `ChatViewModel.kt:2648-2679`).
  Hubs are the only gap.

---

## 3. Root causes worth stating precisely

### 3.1 Profile picture re-prompt on Android **[CODE]**

The onboarding gate is computed from the **local cache** before the remote profile is confirmed
(`App.kt:663-664`):

```kotlin
// Gate on the merged local user only. A background profile fetch must not reopen
// ProfileBasics for web-complete accounts when remote briefly looks incomplete.
remoteAvatarPresent = !localUser.image.isNullOrBlank()
profileGateCheckReady = true
```

The comment shows this is deliberate — it defends against the *opposite* bug (remote briefly looking
incomplete). But it conflates **"unknown"** with **"absent"**: when the local cache has no image yet,
`remoteAvatarPresent` becomes `false`, `userHasAvatar` is `false` (`App.kt:692`), and
`OnboardingViewModel.computeStep()` returns the Avatar step.

The background fetch can only flip the flag to `true`, never `false` (`App.kt:667-668`), and the view model
is keyed on `userHasAvatar` (`App.kt:694`), so it **self-corrects**. The user therefore sees a *flash* of the
avatar step rather than a permanent block — which fits the report and explains why it is intermittent and
Android-first (timing-dependent).

**Fix direction:** make the flag tri-state (`null` = unknown) and hold the gate while unknown, instead of
defaulting unknown to "no avatar". `remoteAvatarPresent` is already nullable — the bug is the
`?: !appDataUser?.image.isNullOrBlank()` fallback at `App.kt:692` collapsing unknown into false.

### 3.2 Double notifications — fix direction **[CODE]**

Two independent changes; do both:

1. **Stop the deliberate double-ring.** Suppress the standard alert push when a VoIP push to the same
   device succeeded, rather than sending both unconditionally (`index.ts:285-287`). Requires knowing which
   rows belong to the same device — which needs (2).
2. **Give tokens a device identity and prune them.** Add a `device_id` to `push_tokens`, make the
   uniqueness `(user_id, device_id, token_type)` so iOS can keep both VoIP and standard on one device,
   re-registration *replaces* rather than accumulates, and delete rows on APNs `410 Unregistered` /
   FCM `UNREGISTERED` responses. Without this, duplicates persist for every user who has ever reinstalled.

   **Landed 2026-08:** uniqueness is `(user_id, device_id, token_type)`; sender prunes dead tokens and
   sends VoIP then standard fallback per device.

### 3.3 Map pins reducing to a subset **[CODE]**

Two things are true:

**(a) Deliberate narrowing.** Both render paths narrow the visible set when the user's location sharing is
off (`MapViewModel.kt:821-824` and again at `:864-869`):

```kotlin
val visible = if (prefs.showOnMapEnabled) mapVisible else mapVisible.filter { it.id in coreIds }
```

If `showOnMapEnabled` is false, only "core" connections are drawn. Recomputation is triggered by any change
to `locationPrefs`, `coreIds`, `hiddenIds` or zoom — so pins can shrink mid-session with no user action, which
matches "same build suddenly decides to only show a subset".

**(b) Two collectors race to write the same output.** `updateRenderData(...)` is called from **two**
independent `collectLatest` blocks (`MapViewModel.kt:827` and `:871`, definition at `:885`) fed by different
`combine` sources.
Whichever emits last wins. This is the structural defect worth fixing regardless of (a): a single source of
truth for the visible pin set makes the behaviour explainable.

**Refuted:** the "broad initial load overwritten by a narrower refresh" hypothesis for *beacons* — those
merge via `mergeMapBeaconLists(...)` (`MapViewModel.kt:744`, `:2774`, `:2985`). No TTL/staleness filter hides
connection pins over time.

**Open question for the owner:** is narrowing to `coreIds` when `showOnMapEnabled == false` the intended
product rule? If yes, this is a **UX/communication** bug (the map silently changes with no explanation) and
the fix is an on-map indicator, not a logic change. Confirm before touching the filter.

### 3.4 Realtime chat sync **[CODE]**

**New conversations don't appear for the other participant.** `chats` *is* in the realtime publication
(`database/chat_schema.sql:186-263`), but nothing subscribes to its INSERTs. `RealtimeCoordinator` listens
only to `messages` inserts (`:88-124`) and connection junction tables (`:126-157`). So a chat created via
handshake, QR or the create-group dialog is invisible to the other user until something else forces a
refresh — typically the first *message*, which does fan out. Publishing the table without a consumer is the
gap.

**Hub realtime failures are masked.** `HubChatViewModel.kt:279-292` reports success in two ways it should
not:

```kotlin
val realtimeJob = launch { runRealtimeSession() }
loadInitialMessages()
_channelReady.value = true      // ← ready before the subscription is confirmed
realtimeJob.join()
} catch (e: Exception) {
    _channelReady.value = true  // ← ready *because* it failed
```

A hub whose subscription never established is indistinguishable from a healthy one. Any investigation into
"hubs don't sync" will be misled until this is fixed; it should be the **first** change in that area.
(Confirmed absent from `ChatViewModel`.)

**Push beats the in-app thread** because the push path is local and immediate
(`ChatViewModel.kt:520-541`) while opening a thread resolves the chat row, warms caches, fetches and merges
payloads, and only then subscribes (`ChatViewModel.kt:1615-1910`). The race is structural: subscribe first,
then reconcile.

---

## 4. What is missing entirely (new build, not repair)

| Capability | Current state | Notes |
|---|---|---|
| Availability-intent mutual-match notifications | **No sender exists** | Intents are only read/written and shown on profiles. Needs a matcher (interest category/text **and** time overlap) plus a schedule. |
| Community-hub message notifications | **No sender exists** | No trigger or route for hub message inserts, unlike `messages`. |
| Per-type notification preferences | 2 booleans: `message_push_enabled`, `call_push_enabled` (`database/add_notification_preferences.sql:3-4`) | Needs schema columns, KMP repo + web route changes, settings UI, **and** every sender taught to consult them. |
| Personality picker (pick five) | Nothing | No UI, no column. |
| Home recap (day/week) | Home has no recap section | Data mostly derivable: `connections.created`, `messages.created_at`, bookmark `bookmarkedAt`. Beacon/RSVP/check-in tables were **not** locatable in this repo — confirm ownership before scoping. |
| Saved events in settings | Rendered on Home only (`HomeScreen.kt:376-387`) | Data already loaded via `HomeViewModel:475-490`; surfacing it is cheap. |

Note the ordering constraint: **per-type preferences (§15) should land before or with the new senders
(§14)**, otherwise every new notification class ships with no way to turn it off — which is how the current
2-toggle limitation arose.

---

## 5. Recommended sequence

**Phase A — establish facts (hours, no code)**
1. Run the authenticated LiveKit probe (§1.1). Calls cannot be scoped until this returns.
2. Run the `cron.job` query (§1.3). Reminders cannot be scoped until this returns.

**Phase B — stop the bleeding (S1)**
3. Hub fake-success (`HubChatViewModel.kt:279-292`) — one-line class of fix, unblocks all hub diagnosis.
4. `push_tokens` device identity + stale-token pruning (§3.2).
5. Event-reminder window vs. sweep interval (§1.3) — and de-duplicate the two implementations.
6. Realtime consumer for `chats` inserts (§3.4).
7. Calls — scope only after Phase A.

**Phase C — correctness (S2)**
8. Map pin single source of truth (§3.3), after confirming the product rule.
9. Avatar gate tri-state (§3.1).
10. ~~Root-route server-rendered marketing hero (§1.4).~~ **Done.**
11. Permission coordinator + priming prompts.

**Phase D — features & polish**
12. Per-type notification preferences → then availability-intent and hub senders.
13. Home recap, settings reorganisation, saved events.
14. Onboarding polish, transitions, chat animations.
15. Component unification — **via issue #54**, not here.

---

## 6. Verification commands

```bash
# §1.1 calls — is it env, or the client?
curl -i -X POST https://joinclick.co/api/livekit/token \
  -H "Authorization: Bearer <SUPABASE_ACCESS_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"connection_id":"<id>","room_name":"click-<id>-x","participant_name":"probe"}'

# §1.3 does the schedule even exist?
#   SELECT jobid, jobname, schedule, active FROM cron.job WHERE jobname = 'click-hourly-maintenance';

# §1.2 stale-token pruning absent (expect 0)
grep -c "410\|Unregistered\|BadDeviceToken" supabase/functions/send-push-notification/index.ts

# §3.4 hub fake-success present (expect 2 assignments in the block)
sed -n '279,292p' composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/HubChatViewModel.kt

# §3.3 competing render writers (expect 2 call sites; a 3rd hit is the definition at :885)
grep -n "updateRenderData(" composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/MapViewModel.kt \
  | grep -v "private fun"

# §1.5 text-input adoption (expect 29 canonical vs 8 raw)
cd composeApp/src/commonMain/kotlin/compose/project/click/click
grep -rn "ClickOutlinedTextField(" --include=*.kt ui | grep -v "fun ClickOutlinedTextField" | wc -l

# §1.4 crawler-visible body (expect 0 loading string; marketing copy present)
#   requires NEXT_PUBLIC_SUPABASE_URL / _ANON_KEY set to any non-empty value
cd ../click-web && npm run dev &
curl -s http://localhost:3000/ -H "User-Agent: Googlebot/2.1" | tee /tmp/root.html \
  | grep -c "Loading your connections"   # expect 0
grep -ciE "waitlist|From Handshake|Why Click" /tmp/root.html   # expect >0
```

---

## 7. Proposed issue split

#58 should become a tracking issue linking these, so the S1 work is not blocked behind redesign debate:

| New issue | Contents | Severity |
|---|---|---|
| Calls outage triage | §1.1 — diagnose first, then fix | S1 |
| Push delivery correctness | Double notifications, token identity/pruning (§1.2) | S1 |
| Scheduled notifications are unreliable | Reminder window, UTC day boundary, duplicated logic, unverifiable cron (§1.3) | S1 |
| Realtime sync gaps | `chats` consumer, hub fake-success, open-thread race (§3.4) | S1 |
| Map pin visibility | §3.3 — needs a product decision first | S1 |
| Notification preferences & new senders | Per-type prefs, then availability + hub notifications (§4) | F |
| Onboarding & permissions | Avatar race, permission coordinator, priming, transitions | S2 |
| ~~Marketing root route SSR~~ | §1.4 — **fixed** in `click-web` | S2 |
| Home recap + settings reorganisation | §4 | F |
| *(fold into #54)* | Component unification (§1.5) | S3 |

---

## 8. Caveats

- **No Android SDK on the analysis machine**, so nothing in `click` was compiled or executed. All mobile
  findings are static-analysis only; none were reproduced on a device.
- **Device-only symptoms cannot be confirmed here** — "double notifications on some devices", call failure
  modes and animation smoothness need a physical device and production credentials.
- **Production configuration was not inspected.** Supabase/Cloudflare env vars and the pg_cron job list are
  outside this repository; §1.1 and §1.3 both bottom out in checks only a maintainer can run.
- The `click-web` findings are from a sibling checkout at `fce5fb9` and may not match what is deployed at
  `joinclick.co`.
