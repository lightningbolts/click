# Functional Clarity Revamp — Continuation Handoff

**Date:** 2026-07-16 (updated after Track B P0 code fixes)  
**Product:** Click KMP mobile (`click/composeApp`)  
**Audience:** Next chat / agent continuing this work  

This document is the source of truth for **what remains**. Read it before writing code.

---

## 0. Track status (read this first)

| Track | Status | What it covers |
|-------|--------|----------------|
| **A — Dark/light + theme hardening** | **DONE** (2026-07-16) | `LocalIsDarkMode`, `clickBorderColor()` / `clickCardSurface()`, theme-aware `GlassSheetTokens` / `OledSheetTheme`, tab bars, hotspot `BorderHard` sweep, map basemap policy |
| **B — Known issues P0/P1** | **P0 code DONE — device verify + P1 next** | P0 #1/#2/#6/#7/#8 code landed; P1 #3/#4 + device smoke still open |
| **C — Mock layout redesigns** | Deferred (separate chat) | Design-asset IA/layout from `docs/design-assets/*` |

**Do not redo Track A** unless a regression is found. Prefer a dedicated Track B chat.

Also merged: **PR #44 `map_color_android`** — Android light-mode map uses default colorful Google tiles (`null` style). Combined with Track A: light = color, dark = `DARK_MAP_STYLE`, ghost = grayscale.

---

## 1. What is already done (do not redo)

### First Functional Clarity visual pass
- Target-state UI specs under [`../ui-ux/mobile/`](../ui-ux/mobile/).
- Theme foundation: [`Color.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/Color.kt) primary `#630ed4`, light-first default in `App.kt`, no root radial gradient.
- Shared primitives restyled opaque + hard borders (`Glass*` API names may remain).
- Broad gradient purge across many screens.

### Track A — dark/light (completed)
- [`PlatformTheme.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/PlatformTheme.kt): `LocalIsDarkMode`, `clickBorderColor()`, `clickCardSurface()`, sheet on-surface helpers.
- [`OledSheetTheme.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/OledSheetTheme.kt) follows app dark/light (no forced light surfaces).
- [`GlassSheetTokens.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/GlassSheetTokens.kt): theme-aware `@Composable` accessors.
- Android/iOS tab bars theme-aware; iOS no longer forced white.
- Map basemap policy: light → color; dark → dark style; ghost → grayscale/muted.
- Design-system structural borders documented for both modes.
- Intentional always-dark overlays (calls, unified search, connection reveal) keep `BackgroundDark` + `BorderHardDark`.

**Still not done by Track A:** preexisting product bugs, device smoke, mock **layouts** / IA.

---

## 2. Priority order for the next chat

| Priority | Workstream | Why |
|----------|------------|-----|
| **P0 device** | Verify #1, #2, #6, #7, #8 on Android hardware | Code landed; do not false-pass `[KNOWN-N]` |
| **P1** | Known issues #3 polish, #4 + remaining #9, #10 | Events list, proximity UX, polish |
| **P2** | Device regression (smoke + full checklist) | Manual; requires hardware |
| **Separate chat** | Design-asset **screen redesigns** (layout/IA) | Track C — see §5 |

~~Dark / light mode consistency~~ — **Track A complete**; only spot-test regressions if found during smoke.
~~P0 code fixes (#1/#2/#6/#7/#8)~~ — **Track B code landed 2026-07-16**; device smoke still open.

---

## 3. Dark / light — acceptance (Track A)

- [x] Theme helpers + `LocalIsDarkMode`
- [x] Sheets / tokens follow dark/light
- [x] Tab bars theme-aware
- [x] Borders: black light / white dark via `clickBorderColor()`
- [x] Map basemap policy (incl. PR #44 color in light)
- [ ] Device smoke dark ↔ light both platforms *(manual — still open)*

Key APIs (reuse; do not reinvent):

```kotlin
LocalIsDarkMode.current
clickBorderColor()
clickCardSurface()
GlassSheetTokens.GlassSurface() // @Composable
GlassSheetTokens.GlassBorder()
```

---

## 4. Preexisting issues — still open (**Track B backlog**)

Treat [`../regression-testing/03-known-issues-audit.md`](../regression-testing/03-known-issues-audit.md) as the bug backlog.

| # | Title | Pri | Area | Notes |
|---|--------|-----|------|-------|
| **1** | Incomplete group registration (BLE handshake) | P0 | Proximity / `click-web` | Start: `bindProximityHandshake.ts` |
| **2** | Duplicate connections | P0 | Connections | Client re-tap + server idempotency |
| **3** | Handshake → 1:1 DM (not group-only) | P1 | Proximity UX | Two-person tap → 1:1 |
| **4** | Events missing from list view | P1 | Map | `MapUtils.determineMapRenderData` EVENT clustering |
| **5** | Map not rendering in color | P2 | Map | **Basemap color fixed** (PR #44 + Track A). Leave open only if device still wrong; clustering ≠ this item |
| **6** | Android calls not working | P0 | Calls | `CallManager.android.kt` — retry after permission |
| **7** | Voice message crashes Android | P0 | Chat media | `MediaRecorder` / mic contention |
| **8** | Group chat creation crashes | P0 | Connections | FAB create-group path |
| **9** | Hazard beacon icon oversized | P2 | Map | Pin size |
| **10** | General visual bugs | P2 | UI | Spot-check after smoke |
| **11** | Android-specific roll-up | P0–P2 | Android | See [`../regression-testing/04-android-focus.md`](../regression-testing/04-android-focus.md) |

### Fix rules

- Do **not** mark `[KNOWN-N]` checklist rows as pass until the bug is actually fixed on device.
- Prefer fixing server + client together for #1–3.
- Do **not** false-pass #5 solely from code merge — confirm color map on Android device in light mode.

---

## 5. Design-asset layout redesigns — **Track C (separate chat)**

| Mock folder | Intended layout changes | Counterpart |
|-------------|-------------------------|-------------|
| `home/` | Featured Click, explore categories, greeting hierarchy | `HomeScreen` |
| `settings/` | Profile header + preferences grouping | `SettingsScreen` |
| `chat/` | Inbox card stack / dense bordered list | `ConnectionsListView` |
| `add_click_streamlined_header/` | Large Tap-to-Connect hero | `AddClickScreen` |
| `add_click_fixed_navigation/` | Rolodex / oversized name stack | Connections inbox |
| `events_discovery_with_real_mini_map/` | Events-for-you + mini-map PiP | `MapDiscoveryLayout` |
| `map_events_full_screen_map/` | Full-map + event pin sheet | `MapScreen` |
| `event_details_expanded_dark/` | Expanded event detail over map | Beacon/event sheets |

**Instruction:** Use HTML only for hierarchy/spacing intent; do not copy markup; keep ViewModels; Track A theme helpers already exist — new layouts must use `clickBorderColor()` / scheme surfaces (not light-only hard-codes).

---

## 6. Regression debt

| Gate | Status |
|------|--------|
| §0 automated (Gradle + `npm test`) | Post-merge 2026-07-16 PASS — Android compile + `testDebugUnitTest` · `click-web` 22/145 · iOS sim compile (re-check after merge) |
| Smoke [`02-smoke-10min.md`](../regression-testing/02-smoke-10min.md) | **Not run on device** |
| Full checklist [`01-full-checklist.md`](../regression-testing/01-full-checklist.md) | **Not run** |
| Known-issues audit | **Bugs still open** (Track B) |
| Android focus [`04-android-focus.md`](../regression-testing/04-android-focus.md) | **Not run** |

Pre-merge bar: §0 green + smoke both platforms + fixed P0 known issues verified on Android device.

---

## 7. Prompt template for a new chat

Copy-paste and adjust:

```text
Continue Click Functional Clarity work using the handoff doc:
click/docs/handoff/functional-clarity-continuation.md

Track A (dark/light) is DONE — do not redo unless regressing.

Scope for THIS chat (pick one primary track):
B) Fix preexisting known issues from click/docs/regression-testing/03-known-issues-audit.md
   — start with P0: #6 Android calls, #7 voice messages, #1/#2 handshake, #8 group create.
C) (Separate chat only) Implement design-asset LAYOUT redesigns from click/docs/design-assets/*
   — do not conflate with bugfix unless I say so.

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Preserve ViewModels / BLE / Realtime / LiveKit behavior unless fixing a known bug.
- No gradients / glass blur / ambient mesh on product chrome.
- Reuse clickBorderColor() / LocalIsDarkMode / GlassSheetTokens.*() — do not hard-code BorderHard.
- After changes: run click/docs/regression-testing §0 automated gates.
- Do not false-pass [KNOWN-N] rows.
- Subagents: use composer-2.5 only if spawning agents.

Read first:
- click/docs/handoff/functional-clarity-continuation.md (this file)
- click/docs/regression-testing/03-known-issues-audit.md
- click/docs/regression-testing/04-android-focus.md (for #6/#7/#11)
```

### Recommended chat split

1. ~~**Chat A — Dark/light + theme hardening**~~ **DONE**
2. ~~**Chat B — Known issues P0 code**~~ **DONE (device verify + P1 remain)**
3. **Chat C — Mock layout redesigns** (after B device verify or parallel owner)

---

## 8. Key file index

| Concern | Paths |
|---------|--------|
| Theme helpers / dark flag | `ui/theme/PlatformTheme.kt` (`LocalIsDarkMode`, `clickBorderColor`) |
| Colors | `ui/theme/Color.kt` |
| Sheet theme | `ui/components/OledSheetTheme.kt`, `GlassSheetTokens.kt` |
| Cards / buttons | `GlassCard.kt`, `AdaptiveCard.kt` |
| Tab bars | `BottomBar.android.kt`, `BottomBar.ios.kt` |
| App dark default | `App.kt` (`isDarkMode`) |
| Known issues | `docs/regression-testing/03-known-issues-audit.md` |
| Android calls | `calls/CallManager.android.kt` |
| Proximity server | `click-web/lib/server/proximity/bindProximityHandshake.ts` |
| Map style | `ui/components/MapView.android.kt` / `MapView.ios.kt` |
| Design mocks | `docs/design-assets/*/code.html` |

---

## 9. Done when

- [x] Dark and light modes consistent in code (Track A); device smoke still pending.
- [x] P0 known issues (#1, #2, #6, #7, #8) **code fixes landed** (Track B 2026-07-16); **device verification still required**.
- [ ] P1 #3–#4 addressed; #5 confirmed on device / #9 decided or fixed.
- [x] §0 automated green (Track B re-run); smoke checklist still open on Android + iOS.
- [ ] Layout redesigns either completed in Track C or explicitly scheduled with owners.

Next: device smoke for Track B P0s, then P1 #4 (events list).
