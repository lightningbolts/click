# Functional Clarity Revamp — Continuation Handoff

**Date:** 2026-07-17  
**Product:** Click KMP mobile (`click/composeApp`) + proximity server (`click-web`)  
**Audience:** Next chat / agent continuing this work  

This document is the source of truth for **what has been addressed**, **what still needs fixing**, and **future revamps**. Read it before writing code.

---

## 0. Track status (read this first)

| Track | Status | What it covers |
|-------|--------|----------------|
| **A — Dark/light + theme hardening** | **DONE** (2026-07-16) | `LocalIsDarkMode`, `clickBorderColor()` / `clickCardSurface()`, theme-aware sheets/tokens, tab theme, map basemap policy |
| **B — Known issues P0/P1** | **P0 + B+ UI + #4 code landed — device verify next** | Handshake/calls/voice/group create + chat/profile/nav UI + events list/map parity through 2026-07-17 |
| **C — Mock layout redesigns** | **Deferred** (separate chat) | Design-asset IA/layout from `docs/design-assets/*` |

**Do not redo Track A** unless a regression is found.  
**Do not mark `[KNOWN-N]` checklist rows pass** until verified on device.

Also merged earlier: **PR #44 `map_color_android`** — Android light-mode map uses default colorful Google tiles. Combined with Track A: light = color, dark = `DARK_MAP_STYLE`, ghost = grayscale.

---

## 1. What has been addressed

### 1.1 Track A — dark/light (2026-07-16)

- Theme helpers: `LocalIsDarkMode`, `clickBorderColor()`, `clickCardSurface()`, sheet on-surface helpers.
- Sheets/tokens follow app dark/light (`OledSheetTheme`, `GlassSheetTokens`).
- Android/iOS tab bars theme-aware (no forced white iOS bar).
- Map basemap: light → color; dark → dark style; ghost → grayscale/muted.
- Intentional always-dark overlays (calls, unified search, connection reveal) keep dark chrome.

### 1.2 Track B — P0 product bugs (code 2026-07-16)

| # | Issue | Code outcome |
|---|--------|--------------|
| **1** | Incomplete group registration (handshake) | Don’t mark handshakes matched on connection failure; pairwise failure → 503; 1:1 recent-lock; pair cardinality; confirm uses `preflightConnectionId` |
| **2** | Duplicate connections | Dedup / idempotency hardening on server + client paths |
| **3** (side effect) | Auto-clique on 1:1 | Auto-clique only when `tagging.isGroup && memberUserIds.size >= 3` |
| **6** | Android calls | Pending-call queue + Activity Result permission retry; group call >8 fails cleanly |
| **7** | Voice message crash (Android) | Guard `MediaRecorder` prepare/start; block during active call |
| **8** | Group create crash | Harden `createVerifiedClique`; promote proximity pairwise edges `forceActive` |

### 1.3 Track B+ — UI / chat / profile (code 2026-07-16 → 2026-07-17)

| # | Issue | Code outcome |
|---|--------|--------------|
| **12** | Add Click interactive-back card flicker | Persistent underlay + overlay for My Code / Scan / Tap |
| **13** | Profile sheets dark/light inconsistency | OLED sheet tokens; theme-aware search; iOS sheet chrome follows app theme |
| **14** | Create verified click scroll/measure crash | `ClickFormBottomSheet` inner column `fillMaxHeight()` so `LazyColumn(weight)` is bounded |
| **15** | Segment tabs cover selected text | Selected text → `onPrimaryContainer` |
| **16** | Dark mode text contrast | Brighter dark `onSurfaceVariant` (`#D6D9D9`); header subtitle / offline alpha bumps |
| **17** | Chat swipe-back duplicate separator keys | Separator keys `separator-nf-$seq-$dayKey` + `ensureUniqueTimelineKeys` |
| **18** | Verified click picker duplicate UUID keys | Candidates `distinctBy { id }`; Lazy keys `picker-${id}-$index` |
| **19** | Chat scroll lag / teleport | Hub-style scroll (open once + peer-newest near bottom); prefetch merges live timeline (disk/hot cache kept — no extra Supabase egress) |
| **20** | Dark surfaces too light | Deeper grays: bg `#101212`, surface `#1A1C1C`, containers `#242626` / `#2A2C2C` |
| **21** | Chat out-of-order days + duplicate bubbles | Sort before day separators; `normalizeChatTimeline`; optimistic/server dedupe by `localSentAt`; `sendMessage` passes `optimisticTempId` |
| **22** | Profile sheet OLED / avatar / top spacing | OLED body; `ConnectionListUserAvatarFace`; remove safe-area + 24dp spacer above title |
| **23** | Nav bar opaque band / materials differ under nav | Bottom bar **overlay** (not Scaffold `bottomBar`); **fully transparent** chrome so page materials look identical under icons |
| **24** | Connections list flicker after chat interactive-back | Defer `leaveChatRoom` + tab-bar restore after gesture settle; suppress inbox reorder scroll-to-top; don’t re-run `setCurrentUser` when `pendingChatId` clears |
| **25** | Inbox preview jumps to old (“12w ago”) after scroll-up in chat | Monotonic inbox bump; global realtime Insert-only for list events; pagination merges hot cache; leave repairs preview from newest; ephemeral join no longer holds mutex during 8s subscribe |

### 1.4 Engineering invariants preserved

- Chat disk/hot timeline cache still used on re-entry (egress-conscious).
- ViewModels / BLE / Realtime / LiveKit untouched except for bugfixes above.
- §0 automated gates (Android compile + unit tests, iOS sim compile) were green after these landings — **re-run after further merges**.

---

## 2. What still needs fixing

All “code landed” rows below still require **device smoke**. Do not false-pass `[KNOWN-N]`.

### 2.1 Device verification (immediate)

| Priority | Items | Action |
|----------|-------|--------|
| **P0 device** | #1, #2, #6, #7, #8, #14, #17, #18, #19, #21, #24, #25 | Multi-phone / Android hardware where noted |
| **P1 device** | #12, #13, #15, #16, #22, #23 | iOS + Android visual/UX confirm |
| **P2 device** | #5, #9, #20 | Map color, hazard pin, dark surface depth |

### 2.2 Still open in code / product

| # | Title | Pri | Status | Notes |
|---|--------|-----|--------|-------|
| **3** | Handshake → clear 1:1 DM UX | P1 | Partial | Auto-clique gated; polish/device UX still open |
| **4** | Events missing from list view | P1 | Code fix landed — needs device repro | `EVENT` in `standaloneKinds`; ALL-layer `isVisibleEventBeacon` |
| **5** | Map not rendering in color | P2 | Basemap code fixed | Confirm on Android light device; leave open if still wrong |
| **9** | Hazard beacon icon oversized | P2 | Confirmed | Pin size inconsistency |
| **10** | General visual bugs | P2 | Open | Spot-check after smoke |
| **11** | Android-specific roll-up | P0–P2 | Open | See `04-android-focus.md` |
| **23** | Transparent nav underlay | P1 | Code landed | Confirm no seam / material mismatch on device; icons readable over varied Home content |
| — | iOS Keychain `status: -50` | P1 | Open if blocks auth | `IosTokenStorage` set failures — separate from LazyColumn crashes |

### 2.3 Regression debt (not run on device)

| Gate | Status |
|------|--------|
| §0 automated (Gradle + `npm test`) | Re-check after merge; was green post Track B / B+ |
| Smoke [`02-smoke-10min.md`](../regression-testing/02-smoke-10min.md) | **Not run on device** |
| Full checklist [`01-full-checklist.md`](../regression-testing/01-full-checklist.md) | **Not run** |
| Android focus [`04-android-focus.md`](../regression-testing/04-android-focus.md) | **Not run** |

### 2.4 Recommended next engineering order

1. **Device smoke** Track B P0s + chat timeline (#21) + verified click (#14/#18) + transparent nav (#23) + events (#4).
2. **P1 polish:** #3 1:1 handshake UX on device; Keychain -50 if auth flakes.
3. **P2:** #5 confirm, #9 hazard size, #10 visual sweep.
4. **Track C** mock layout redesigns (separate chat) — see §3.

---

## 3. Future revamps (Track C + follow-ons)

These are **layout / IA** workstreams, not bugfixes. Use design-asset HTML for hierarchy/spacing intent only — do not copy markup; keep ViewModels; reuse `clickBorderColor()` / scheme surfaces.

| Revamp | Mock / source | Counterpart screens | Intent |
|--------|---------------|---------------------|--------|
| **Home IA** | `docs/design-assets/home/` | `HomeScreen` | Featured Click, explore categories, greeting hierarchy |
| **Settings grouping** | `settings/` | `SettingsScreen` | Profile header + preference clusters |
| **Inbox density** | `chat/` + `add_click_fixed_navigation/` | `ConnectionsListView` | Card stack / dense bordered list; rolodex name stack |
| **Add Click hero** | `add_click_streamlined_header/` | `AddClickScreen` | Large Tap-to-Connect hero |
| **Events discovery** | `events_discovery_with_real_mini_map/` | `MapDiscoveryLayout` | Events-for-you + mini-map PiP |
| **Full-map events** | `map_events_full_screen_map/` | `MapScreen` | Full-map + event pin sheet |
| **Event detail** | `event_details_expanded_dark/` | Beacon/event sheets | Expanded detail over map |
| **Nav chrome v2** (optional) | — | `BottomBar.*`, `App.kt` | After #23 device OK: decide if floating icon-only chrome needs hit-target / readability polish without reintroducing a fill band |
| **Chat composer / timeline polish** | `docs/ui-ux/mobile/08-chat.md` | `ChatView`, `ChatTimeline` | After #21 device OK: receipt placement, load-older UX, icebreaker density |
| **Profile memories IA** | `12-profile-memories.md` + mocks | `ProfileBottomSheet` | Timeline / media / members density after #22 device OK |

**Track C instruction:** Prefer a dedicated chat. Do not conflate with P0/P1 bugfix unless explicitly scoped.

---

## 4. Dark / light — acceptance (Track A)

- [x] Theme helpers + `LocalIsDarkMode`
- [x] Sheets / tokens follow dark/light
- [x] Tab bars theme-aware (now: transparent overlay chrome — #23)
- [x] Borders: black light / white dark via `clickBorderColor()`
- [x] Map basemap policy (incl. PR #44 color in light)
- [x] Deeper dark surfaces (#20) + brighter muted text (#16)
- [ ] Device smoke dark ↔ light both platforms *(manual — still open)*

Key APIs (reuse; do not reinvent):

```kotlin
LocalIsDarkMode.current
clickBorderColor()
clickCardSurface()
GlassSheetTokens.GlassSurface() // @Composable
GlassSheetTokens.GlassBorder()
GlassSheetTokens.OledBlack() / OnOled()
```

---

## 5. Prompt template for a new chat

```text
Continue Click Functional Clarity work using the handoff doc:
click/docs/handoff/functional-clarity-continuation.md

Track A (dark/light) is DONE — do not redo unless regressing.
Track B P0 + B+ UI code landed 2026-07-16/17 — device verify before false-passing [KNOWN-N].

Scope for THIS chat (pick one primary):
1) Device smoke + fix remaining fails from known-issues audit
2) P1 code: events list (#4)
3) Track C: design-asset LAYOUT redesigns from click/docs/design-assets/*
   — do not conflate with bugfix unless I say so.

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Preserve ViewModels / BLE / Realtime / LiveKit behavior unless fixing a known bug.
- Keep nav bar chrome transparent (no opaque/translucent fill band under icons).
- Preserve chat disk/hot cache (no unnecessary Supabase egress).
- Reuse clickBorderColor() / LocalIsDarkMode / GlassSheetTokens.*().
- After changes: run regression-testing §0 automated gates.
- Do not false-pass [KNOWN-N] rows.

Read first:
- click/docs/handoff/functional-clarity-continuation.md
- click/docs/regression-testing/03-known-issues-audit.md
```

### Recommended chat split

1. ~~**Chat A — Dark/light**~~ **DONE**
2. ~~**Chat B — Known issues P0 + B+ UI code**~~ **DONE (device verify remains)**
3. **Chat B device / P1 #4** — smoke + events list
4. **Chat C — Mock layout redesigns** — after B device verify or parallel owner

---

## 6. Key file index

| Concern | Paths |
|---------|--------|
| Theme / colors | `ui/theme/PlatformTheme.kt`, `Color.kt` |
| Sheet tokens | `OledSheetTheme.kt`, `GlassSheetTokens.kt` |
| App shell / nav overlay | `App.kt` (`PlatformBottomBar` overlay, not Scaffold `bottomBar`) |
| Tab bars | `BottomBar.android.kt`, `BottomBar.ios.kt` (transparent chrome) |
| Chat timeline / scroll | `ChatTimeline.kt`, `ChatView.kt`, `ChatViewModel.kt` |
| Chat cache | `ChatTimelineCache.kt`, prefetch merge in `ChatViewModel` |
| Verified click picker | `ConnectionListSheets.kt`, `ConnectionsListView.kt`, `ClickBottomSheet.kt` |
| Profile sheet | `ProfileBottomSheet.kt`, `MapScreen.kt` (`buildProfileSheetState`) |
| Calls / voice | `CallManager.android.kt`, `MainActivity.kt`, `ChatMediaPickers.android.kt` |
| Proximity server | `click-web/lib/server/proximity/bindProximityHandshake.ts` |
| Known issues | `docs/regression-testing/03-known-issues-audit.md` |
| Design mocks | `docs/design-assets/*/code.html` |

---

## 7. Done when

- [x] Dark/light consistent in code (Track A); deeper dark + contrast bumps landed.
- [x] P0 known issues (#1, #2, #6, #7, #8) **code** landed.
- [x] Chat timeline order/dupes, picker keys, profile sheet, transparent nav **code** landed (#17–#23).
- [x] P1 #4 events list/map parity **code** landed (device verify still open).
- [ ] **Device verification** for Track B / B+ P0–P1 rows (incl. #4).
- [ ] Smoke checklist on Android + iOS.
- [ ] Track C layout redesigns completed or explicitly scheduled.

**Next:** device smoke for chat timeline + transparent nav + Track B P0s + events (#4).
