# 09: Parity audit and closure process (2026-10-10)

**Status:** Plan · **Date:** 2026-10-10 · **Builds on:** [08](08-gap-closure-v2-2026-10-06.md) and [07](07-gap-closure-2026-10.md)

**Baselines:**

| Repo | SHA | Notes |
|---|---|---|
| `click-ios` | `b1177d1` | PR #80 "a pending drop opens in the story" |
| `click` (Android) `main` | `b461f6b4` | Unchanged since 2026-10-06. Last feature merge: #125 on 2026-09-29. |
| `click` worktrees | `wt/android-*` (`parity08/*`) | Work in progress on sheet 08; mostly **uncommitted** |
| `click-web` | `993a0f0` | #153 "keep the wallet open to ticket holders" |

This sheet is the master index. It answers "how far ahead is iOS?", lists every feature iOS has that Android `main` lacks, and sets the process for closing the gap. The detailed specs are in:

| Sheet | Scope |
|---|---|
| **09** (this one) | Gap size, the master feature list, process, phases, schedule and decisions |
| [10](10-post-10-06-ios-features.md) | Full specs for iOS #68–#80 (ticketing, Home Upcoming and Recommended, map stacks, Core ring, drops v3, Devices, owner authentication, auto-unlock) |
| [11](11-platform-quality-blind-spots.md) | Gaps that no earlier sheet lists: accessibility, tab badge, paste, autofill, audio focus, transport, haptics, the phantom Instant activity |
| [12](12-worktree-landing-plan.md) | How to land the in-flight `parity08/*` worktrees safely (merge order, conflict map, fixes before merge) |

Conventions follow the [README](README.md): `KMP/` = `composeApp/src/commonMain/kotlin/compose/project/click/click/`, `AM/` = `androidMain`, `iOS/` = `click-ios/Click/`, `web/` = `click-web/`.

---

## 1. How far ahead is iOS?

### 1.1 In time and volume

- Android `main` matches iOS as it stood on **2026-09-26** (`click-ios@2ddb4a9`, the "Round 7" baseline of sheets 00–05). Sheets 00–05 are code-complete on `main`.
- Since then iOS has merged **108 commits (PRs #13–#80)**. They change **215 files in `Click/` (+28,740 / −4,281 lines)**.
- **None** of that is on Android `main`. Android's last feature merge was 2026-09-29, and the sheet-08 work since then sits in worktrees, most of it uncommitted.
- iOS is shipping about **3 feature PRs a day** (13 PRs between 10-06 and 10-09). A parity plan that is not run continuously falls behind within a week (§5.4).

### 1.2 In features

| Bucket | Feature rows | Android `main` | Android worktrees |
|---|---|---|---|
| Sheet 08 rows (iOS ≤ #67) | 96 (G0–G7) | 0 done | ≈ 52 done or in progress, ≈ 44 not started |
| New since sheet 08 (iOS #68–#80), sheet 10 | 24 | 0 | 2 partial (Devices page, old drop strip) |
| Blind spots, sheet 11 | 10 | 0 | 0 |
| **Total** | **≈ 130** | **0** | **≈ 54 touched** |

### 1.3 In effort (Android engineer-days)

| Work | Estimate | Source |
|---|---|---|
| Land the worktrees: finish, add the missing tests, merge, device-check | 6–9 | Sheet 12 |
| Sheet 08 rows not started (mostly G3 events, G5 Places, G6 Home/social/chat) | 70–85 | 08 §14 scaled to the remaining rows |
| Sheet 10 (iOS #68–#80) | 38–45 | Sheet 10 §0 |
| Sheet 11 (blind spots) | 7–10 | Sheet 11 |
| **Total remaining** | **≈ 120–150** | |

With two Android engineers that is **about 13–16 weeks** if iOS stops adding features. iOS will not stop, so §5.4 (keeping it closed) is part of the plan, not an afterthought.

### 1.4 Ship-blocking interop defects (still live on `main`)

These break **existing** cross-platform features. They are not missing features. Fix them first (Phase P0).

| Defect | Evidence on `main` | Fix row | Fix status |
|---|---|---|---|
| Typing never shows between iOS and Android (key mismatch; stop is never sent) | `SupabaseChatRepository.kt:122-124`, `:589` | 08 G0-3 | Done, uncommitted (`android-chat-e2ee`) |
| Epoch rotation fails when two accounts share a device ID | `SupabaseChatRepositoryCrypto.kt:529` (no de-dup) | 08 G0-13 | Done, uncommitted (`android-chat-e2ee`) |
| One failed rotation blocks every send | send path propagates rotation errors | 08 G0-14 | Done, uncommitted (`android-chat-e2ee`) |
| iOS gated Click Drops show as a 12-block pixelated photo forever | no `drop_gated` handling | 08 G0-5 | Done, uncommitted (`android-drops`) |
| Group-tap tags reach only the group connection | `ConnectionViewModelConfirm.kt` | 08 G0-15 | Done, uncommitted (`android-connect`) |
| QR and manual connects skip server enrichment (direct PostgREST inserts) | `ConnectionRepositoryCreate.kt:239-240`, `ConnectionRepositoryEncounters.kt:91` | 08 G4-10 | Done, uncommitted (`android-connect`) |
| App Links never verify (`assetlinks.json` names `com.click.app` with a placeholder fingerprint) | `web/public/.well-known/assetlinks.json:6-7` | 08 G0-6 + W-1 | **Not started** |
| 🆕 The manifest declares an exported `.instant.InstantProfileActivity` with `autoVerify` on `/c` and `/connect`, **but the class does not exist** | `AM/AndroidManifest.xml:187-210`; no source file | Sheet 11 B8 | **Not started** |
| CI is red against click-web `main` (migration drift) | — | 08 G0-8 | Done, committed (`parity08/ci`, in integration) |

---

## 2. Master list: iOS features Android `main` lacks

Status legend:
- **WT✓**: done in a worktree (code and tests), not merged
- **WT~**: partial in a worktree
- **WT·**: stub
- **—**: not started

The "Spec" column names the row that holds the detailed spec.

### 2.1 Foundations and platform

| Feature | iOS ref | Spec | Status |
|---|---|---|---|
| Server feature flags (`/api/me/features`) with typed config | `Core/Config/FeatureFlags.swift` | 08 G1-1 | WT✓ foundations |
| + `ticket_sales` / `ticket_wallet` derived flags | `FeatureFlags.swift:21` | 10 §T6 | — |
| Live updates (private `user:<uid>` and `beacons` realtime topics) | `Core/Services` | 08 G1-2 | WT~ (supabase-kt 3.0.2 private channels unverified) |
| Push routes for new types (`device_approval`, `shared_drop_released`, `event_drop_recap`, `event_recap`, `prior_connection_*`, `reconnect_nearby`) | `App/ClickApp.swift` | 08 G1-3 | WT~ (new routes fall back to "open app") |
| Click sound and v2 notification channels | `Resources/Sounds/click.caf` | 08 G1-4 | WT✓ (`.wav`, not `.ogg`) |
| Disk-first caches per user | `Core/Persistence` | 08 G1-5 | WT~ (infrastructure only; two competing stores) |
| Pilot analytics sink (`/api/telemetry/events`) | `Core/Telemetry` | 08 G1-6 | WT✓ foundations |
| Generic reactions client | — | 08 G1-7 | WT✓ drops |
| Clean session end on a rejected refresh | `SessionController.swift:462` | 08 G1-9 | WT✓ foundations |
| Device re-registration with `device_label` | — | 08 G1-10 | WT✓ devices (in integration) |
| Transport timeouts and one retry for idempotent requests | `ClickAPIClient.swift:53-61` | 11 B6 | — |

### 2.2 Click Drops

| Feature | Spec | Status |
|---|---|---|
| Develop state, gated send, tap to develop, Develop all | 08 G2-1, G2-2 | WT✓ drops |
| Shared drops strip on Home | 08 G2-3 | WT~ (`develop_hours` hard-coded; **pre-#78 grouping**) |
| Stories viewer, reaction tray, captions, one tile per person, release push, replies, instant paint | 08 G2-4…G2-9, G2-4+ | WT✓ drops (pre-#78/#80 behaviour) |
| 🆕 Rolling-day strip (24 h, ≤5 per person, newest stack first) | 10 §D1 | — |
| 🆕 Generic `DropStoryViewer` and the event recap grid + story | 10 §D2–D3 | — |
| 🆕 Pending drops open in the story with a "Develops in mm:ss" countdown | 10 §D4 | — |

### 2.3 Events, beacons and map

| Feature | Spec | Status |
|---|---|---|
| Event drops, recap, history, alert confirmations, Listening now, song search | 08 G3-1…G3-6 | — |
| Nearby paging, sort, counts; Nearby rows | 08 G3-7, G3-21 | — |
| Planned hangouts on the map; beacon extras cache; reminders align; recurring verify; calendar free/busy | 08 G3-8…G3-12 | — |
| Luma-layout event page | 08 G3-13 | — |
| Event weather | 08 G3-14 | — |
| Click Pass and door scanner | 08 G3-15, G3-16 | WT✓ events (**no tests**) |
| Event ongoing notification (Live Activity equivalent) | 08 G3-17 + 10 §E5 | WT· (20-line stub) |
| Google Wallet | 08 G3-18 | blocked on W-7 |
| Add to Calendar menu | 08 G3-19 | WT✓ events (no tests) |
| Maps choice and map memory | 08 G3-20 | WT~ (directions only) |
| Place as host | 08 G3-22 | WT~ (model only) |
| Event markdown: **align**, not port (Android already has `EventMarkdownText.kt`) | 08 G3-23 (corrected) | — |
| Click Flyer | 08 G3-24 + 10 §E4 | — |
| Event drops develop in place; beacons drop where you are; event-chat retention; host shape changes | 08 G3-25…G3-28 | G3-27 WT✓ chat-e2ee; others — |
| QR brightness and keep-awake | 08 G3-29 | WT✓ events |
| Share-events picker in chat (×10) | 08 G3-30 | — |
| 🆕 **Ticketing**: buy and claim, Stripe checkout, order confirmation, ticket pass pager, Me › Tickets wallet, scanner results | 10 §T1–T7 | — |
| 🆕 Luma-style map stacks (screen-space, replaces zoom clustering and "Which pin?") | 10 §M1 | — |
| 🆕 Place-hosted events merge into Place pins only | 10 §M2 | — (needs G5) |
| 🆕 Explore-nearby chips with face piles; New Event type chips | 10 §M3 | — |

### 2.4 Connect pipeline

| Feature | Spec | Status |
|---|---|---|
| Warm capture; quality keys; `sensor_observation` v3; "Who's in this Click?" review; late joiners | 08 G4-1…G4-5 | WT✓ connect (G4-1 no test) |
| Encounter details on the result screen | 08 G4-6 | — |
| Encounter follow-up (location + altitude) | 08 G4-7 | WT✓ connect |
| Connect telemetry | 08 G4-8 | WT~ (test in a non-source-set folder) |
| Permissions | 08 G4-9 | — |
| QR and manual connect via the API | 08 G4-10 | WT✓ connect (no test) |
| Tag suggestions v2; encounter pills | 08 G4-11, G4-12 | WT✓ connect |
| Group tap: existing group, per-removal selection, group-chat election, tags to pairs | 08 G0-4, G0-12, G0-15 | WT✓ connect (G0-4/12 committed) |

### 2.5 Click Places (flag `click_places`)

| Feature | Spec | Status |
|---|---|---|
| Models and routes; map layer and filters; Place page; surfaces; `/api/hub/create` 409 | 08 G5-1…G5-5 | — |

### 2.6 Home, social and chat

| Feature | Spec | Status |
|---|---|---|
| Activity inbox with inline Accept | 08 G6-1 | — |
| Reconnect near here | 08 G6-2 | — |
| Find friends by phone (`known[]`, `/api/me/phone`) | 08 G6-4 | — |
| 3-step onboarding | 08 G6-5 (D5) | — |
| Location nudges; remove Memory Map | 08 G6-6 | — |
| Smart icebreakers | 08 G6-7 | — |
| GIFs (KLIPY); pinned messages | 08 G6-8, G6-9 | — |
| Encounter backdrops; calmer chat media; hub previews in Groups; short chats bottom-anchored; per-tab root menus | 08 G6-11…G6-15 | — |
| Attachment polish; keyboard on push; arc loader | 08 G6-16, G6-18, G6-19 | — |
| Tombstones on older pages | 08 G6-17 | WT✓ chat-e2ee |
| 🆕 Home **Upcoming** (hosting, going, saved) + View all | 10 §H1 | — |
| 🆕 Home **Recommended for you** (ranked, with reason pills) | 10 §H2 | — |
| 🆕 **Core ring**: gold-to-violet ring inside the avatar's size, at every avatar call site, including group and hub senders | 10 §H3 | Partial on `main` (solid blue ring *outside* the avatar, a few sites) |
| 🆕 Profiles of people you haven't Clicked with (no journal request; per-tab notes) | 10 §H4 | Unverified |
| 🆕 Nudge layout ("Not now") | 10 §H5 | Partial |
| 🆕 Unread badge on the Clicks tab | 11 B2 | — |
| 🆕 Paste an image or GIF into the composer | 11 B3 | — |

### 2.7 Account, devices and compliance

| Feature | Spec | Status |
|---|---|---|
| Device approval on both sides | 08 G7-1 | WT✓ devices (in integration) |
| In-app account deletion; legal links; rationale copy | 08 G7-2, G7-3, G7-5 | WT✓ devices (in integration) |
| Play Data safety | 08 G7-4 | — (Play Console; now also tickets and payments) |
| Settings › Devices | 08 G7-6 | WT~ (copy diverges; no "Waiting for approval" section) |
| 🆕 Owner authentication (biometric or screen lock) before approving | 10 §A1 | — |
| 🆕 Approved immediately; history shared in the background | 10 §A2 | WT~ (share isn't detached) |
| 🆕 Locked history unlocks by itself after approval | 10 §A3 | — |
| 🆕 Duplicate device-approval notifications collapse to one | 10 §A4 | — |
| 🆕 Settings › Devices "Waiting for approval" section and copy parity | 10 §A5 | — |
| 🆕 Accessibility pass (TalkBack, font scale) | 11 B1 | — |
| 🆕 Login autofill semantics | 11 B4 | — |

### 2.8 Out of scope (decided or iOS-only)

| Item | Why |
|---|---|
| App Clip | 07 D6 / 05 D7. The Android stand-in is the web fallback. Delete the phantom Instant activity (11 B8). |
| Apple Wallet `.pkpass` | iOS-only. Android uses Google Wallet after W-7. |
| Background visit monitoring (`VisitMonitor`) | D2: don't build it on Android. |
| Say-hi countdown | D10: no. Fix only the `has_begun` semantics. |
| Passkey history recovery (web #150) | Web-only; iOS doesn't have it. Android only has to keep `device_label` free of " on " (10 §A6). |
| Organizer ticketing (tiers, refunds, payouts) | Web-only on every platform. |

### 2.9 Android is ahead; don't regress

- MessagingStyle conversation notifications
- lux, compass, battery and weather on connect
- WorkManager handshake retry
- Sign in with Apple on Android
- mute durations
- the encrypted outbox
- the per-user FTS message store
- recurring events
- the calendar overlap card
- Reduce Motion support (keep it in G6-19 and the develop animations)

---

## 3. Process

### 3.1 Principles (adds to 08 §1)

1. **Land before you build.** No new parity row starts until the in-flight worktrees are merged (sheet 12). About 20k lines of uncommitted work is one `git clean` away from being lost.
2. **Interop before features.** Phase P0 ships first. It fixes things users already hit across platforms.
3. **Server contract first.** Any row that needs a web change (W-items) waits for the web PR and a fixture in `web/__tests__/fixtures/mobile/` (08 W-3).
4. **Port tests with the code.** A row isn't done without its ported tests. Sheet 12 lists the worktree rows that are missing them.
5. **Flags cover the gap.** A flag above 0% needs both clients at "Code", or a platform restriction (08 W-4). Until W-4 lands, the owner should not raise iOS-only flags (`ticket_sales`, `shared_drops`, `event_drops`, `click_places`) for mixed-platform cohorts without accepting that Android users will see degraded fallbacks.

### 3.2 Per-row workflow

1. **Pick.** Take the row from this sheet's phase table.
2. **Re-verify.** Re-read the iOS reference at the current iOS HEAD (iOS may have changed it since this sheet), and the web route.
3. **Branch.** Cut a branch from `origin/main`, named `parity/<row-id>-<slug>`.
4. **Implement.** Logic goes in `commonMain`; port the iOS tests with iOS fixture values.
5. **Gate.** `./gradlew :composeApp:testDebugUnitTest`, `assembleDebug`, and `spotlessCheck` on the touched files.
6. **PR.** Fill in the template's "Other platform impact" section, and add a ledger row (`click-ios/Docs/PARITY_LEDGER.md` Android column, 08 I-1).
7. **Device check.** Record it in `docs/regression-testing/2026-MM-DD-parity-09.md` where the row says so.
8. **Close.** Mark the row in the README scoreboard.

### 3.3 Definition of done (per row)

- [ ] The spec's acceptance rules are met, and the copy matches iOS character for character (or the divergence is written down).
- [ ] The ported unit tests pass. New pure logic has golden tests.
- [ ] Offline, error and empty states are implemented.
- [ ] TalkBack labels are set on new interactive elements (sheet 11 B1 rule).
- [ ] There is a ledger row and a scoreboard update.

---

## 4. Phases

Phases P0–P2 are in the order to work them. Within a phase, the rows can run in parallel.

| Phase | Contents | Est. days | Exit criteria |
|---|---|---|---|
| **P0: Land and unblock** | Sheet 12 (merge every worktree, in order). 08 G0-1 and G0-6 + W-1. 11 B8 (delete the phantom activity). Fix the misplaced G4-8 test. Add the missing tests for G3-15/16/19 and G1-10. | 8–11 | CI green on `main`. Typing, rotation, gated drops and group tap all pass the 08 G8-1 matrix rows. `adb shell pm get-app-links` shows `verified`. |
| **P1: Finish the foundations** | 08 G1-3 (route every new push), G1-5 (one disk cache), G1-2 (private-channel check). 10 §A1–A5 (owner authentication, auto-unlock, deduped alerts, Devices parity). 11 B2, B6. | 7–9 | Every server push type opens its screen. Approving needs biometrics or the screen lock. A newly approved device unlocks with no navigation. |
| **P2: Drops v3** | 10 §D1–D4 on top of the merged drops work. 08 G2-3 `develop_hours` from the flag. | 6–8 | Strip, story, recap and countdown match iOS on the same data (side-by-side check). |
| **P3: Events core** | 08 G3-1…G3-5, G3-13, G3-14, G3-17 (+10 §E5), G3-21, G3-23 (align), G3-25…G3-28, G3-30. | 22–26 | 08 G8-1 event rows pass. |
| **P4: Home and map** | 10 §H1–H5, §M1, §M3. 08 G3-7…G3-12, G3-20 (map memory), G3-24 (+10 §E4). | 14–17 | Home is section-for-section the same as iOS. Map stacks match iOS on the same region. |
| **P5: Ticketing** | 10 §T1–T7, + web asks TW-1/TW-2. | 10–12 | A test-mode Stripe purchase from Android ends with a scannable ticket that an iOS host can scan. |
| **P6: Social and chat** | 08 G6-1, G6-2, G6-4, G6-6…G6-9, G6-11…G6-16, G6-18, G6-19. 11 B3, B5. | 18–22 | — |
| **P7: Places** | 08 G5-1…G5-5 + 10 §M2. | 12–15 | Flag `click_places` can go to 100% on both platforms. |
| **P8: Quality and release** | 11 B1 (accessibility), B4, B7, B9, B10. 08 G6-5 (if D5 says yes), G7-4 (Data safety incl. payments), G3-18 (if W-7 lands). Full G8 matrix. | 10–12 | The Play pre-launch report has no accessibility errors. The G8 matrix passes. |

### 4.1 Schedule (two Android engineers, A and B; web engineer W part-time)

Week 1 starts **Monday 2026-10-12**.

| Week | A | B | W |
|---|---|---|---|
| 1 (10/12) | P0: merge chain (sheet 12 steps 1–6) | P0: missing tests; G0-6 manifest + B8 | W-1 assetlinks, TW-1 `client:'android'` |
| 2 (10/19) | P1: G1-3, G1-5, B6 | P1: 10 §A1–A5, B2 | W-2, W-3 fixtures, W-4 platforms |
| 3 (10/26) | P2: drops v3 | P3: G3-13, G3-14, G3-23 | W-7 (Google Wallet), if D11 |
| 4 (11/02) | P2 finish; G3-25 | P3: G3-17 + E5, G3-21 | — |
| 5 (11/09) | P3: G3-1, G3-2, G3-3 | P4: §H1, §H2, §H3 | — |
| 6 (11/16) | P3: G3-4, G3-5, G3-26…28 | P4: §M1 map stacks, §M3 | — |
| 7 (11/23) | P3: G3-30; P4: G3-7…G3-12 | P5: T1–T3 | — |
| 8 (11/30) | P4: G3-20 memory, G3-24 + E4 | P5: T4–T7 | — |
| 9 (12/07) | P6: G6-1, G6-2, G6-4 | P6: G6-8, G6-9, G6-13 | — |
| 10 (12/14) | P6: G6-6, G6-7, G6-11, G6-12 | P6: G6-14…16, G6-18, G6-19, B3, B5 | — |
| 11 (12/21) | P7: G5-1, G5-2 | P7: G5-3…G5-5, §M2 | — |
| 12 (1/04) | P8: B1 accessibility | P8: G8 matrix, Data safety | — |
| 13 (1/11) | Buffer: sheet 13 (iOS PRs after #80) | Buffer: release | — |

**One engineer:** about 26 weeks. If time is short, cut in this order: P7 Places (keep the flag off on Android via W-4), §M3 chips, G3-24 flyer, G6-11 backdrops, G6-5 onboarding.

---

## 5. Decisions and keeping it closed

### 5.1 New decisions (08's D1–D13 still open)

| # | Decision | Blocks | Recommendation |
|---|---|---|---|
| D14 | Ship ticket **buying** on Android, or show "Get tickets on the web" until P5? | 10 §T | Ship it in P5. Play policy allows non-Play payment for tickets to live events (physical services). Until then, Android shows the wallet and passes (read-only) and links the buy button to `joinclick.co/e/{id}`. |
| D15 | Instagram one-tap Story share (needs a Meta App ID) | 10 §E4 | No. Match iOS: the share sheet minus the caption. |
| D16 | Owner authentication when the device has **no** screen lock | 10 §A1 | Fail closed, as iOS does: "Device authentication is required to approve this request." |
| D17 | Unify the two disk-cache layers (foundations `UserDiskCache` vs events `EventsDiskStore`) | 12, G1-5 | Keep `UserDiskCache`. Port the events keys onto it before merging events. |
| D18 | Passkey recovery flag defaults to **on** in web (`recoveryFeature.ts`), despite the docs saying off | web | Web owner to confirm. Android is unaffected either way. |

### 5.2 Web asks added by this sheet

| ID | Item | For |
|---|---|---|
| TW-1 | Add `'android'` to `checkoutBodySchema.client` (`web/lib/api/schemas/ticketing.ts:14`). Today, sending `"android"` returns 400; Android must omit the field until then. | 10 §T3 |
| TW-2 | Google Wallet route supports `?ticket=` (extends 08 W-7). | 10 §T5 |
| TW-3 | `/api/beacons/{id}/tickets/return` deep link documented as navigation-only. Android relies on resume polling. | 10 §T3 |

### 5.3 iOS asks

| ID | Item |
|---|---|
| I-5 | Ledger rows F128+ for #68–#80, plus the Android column (08 I-1, now 13 PRs larger). |
| I-6 | Port Android's `device_label` rule to the ledger: labels with " on " count as browsers, so native labels must never contain it. |

### 5.4 Keeping it closed (adds to 08 G8-4)

1. **A weekly parity diff.** Every Monday, list the iOS first-parent merges since the last sheet's baseline, then add each as a row to the next sheet (09 → 13) **the same week**. 08 skipped this, and the gap grew by 13 PRs in four days.
2. **A merge window for worktrees.** No parity work stays uncommitted overnight. Push the branch at the end of each session (sheet 12 §4).
3. **iOS PR template.** Make "Android impact" a required field naming the target sheet row, so the spec is written while the iOS author still has the context.
4. **Flags.** The 08 rule stands: a flag above 0% needs both clients at "Code", or W-4.
