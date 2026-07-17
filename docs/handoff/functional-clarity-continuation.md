# Functional Clarity Revamp — Continuation Handoff

**Date:** 2026-07-16  
**Product:** Click KMP mobile (`click/composeApp`)  
**Audience:** Next chat / agent continuing this work  

This document is the source of truth for **what remains after the first Functional Clarity pass**. Read it before writing code.

---

## 1. What the first pass already did (do not redo)

- Target-state UI specs under [`../ui-ux/mobile/`](../ui-ux/mobile/) (Functional Clarity banners + design-system rewrite).
- Theme foundation: [`Color.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/Color.kt) primary `#630ed4`, light-first default in `App.kt`, removed root radial gradient.
- Shared primitives restyled to opaque + hard borders (API names often still say `Glass*`): `GlassCard`, `AdaptiveCard`/`AdaptiveButton`, `LiquidGlassPill`, `GlassSheetTokens`, chat ambient/chrome plates, Android tab bar.
- Broad gradient purge across many screens (home, connect, map, settings, calls, chat bubbles/composer, etc.).
- Regression checklist wording partially updated for solid chrome.
- **Automated §0 gates were green** at end of first pass (Android compile/unit tests, iOS sim compile/UI tests, `click-web` `npm test`). Re-run after every wave.

**What it did *not* do:** fix preexisting product bugs, finish dark-mode parity, or implement mockup **layouts** (IA / structure) from design-asset HTML.

---

## 2. Priority order for the next chat

Execute in this order. Do **not** start mock layout redesigns in the same chat as bugfixes unless the user explicitly asks.

| Priority | Workstream | Why |
|----------|------------|-----|
| **P0** | Preexisting known issues #1–8, #11 | Product-breaking; untouched by the visual pass |
| **P0** | Dark / light mode consistency | Current brutalist borders/sheets often assume light; dark mode is broken or inconsistent |
| **P1** | Known issues #4, #5, #9 + remaining visual polish (#10) | Map/events correctness + hazard icon |
| **P2** | Device regression (smoke + full checklist) | Manual; requires hardware |
| **Separate chat** | Design-asset **screen redesigns** (layout/IA from HTML mocks) | Explicitly deferred — see §5 |

---

## 3. Dark / light mode inconsistencies (must fix)

### Symptoms / root causes

1. **Hard-coded light borders** — Many call sites use `BorderHard` (`#000000`) unconditionally. In dark mode this is wrong or invisible; should resolve via theme (e.g. `BorderHard` in light, `BorderHardDark` / `onSurface` in dark) or a single `LocalPlatformStyle` / theme border color.
2. **`OledSheetTheme` forces light surfaces** — [`OledSheetTheme.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/OledSheetTheme.kt) always copies `SurfaceLight` / `OnSurfaceLight` into the sheet scheme, so sheets ignore app dark mode.
3. **`GlassSheetTokens` are light-biased** — `GlassSurface = SurfaceLight`, `OnOled = OnSurfaceLight`, while `OledBlack` was remapped to near-black. Sheets/overlays mixing these tokens look wrong in one or both modes.
4. **iOS tab bar forced white** — [`BottomBar.ios.kt`](../../composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/BottomBar.ios.kt) sets `UIColor.whiteColor` / opaque white appearance regardless of `isDarkMode`.
5. **Android tab bar** — Uses `MaterialTheme` (good) but top `BorderHard` may still be pure black on dark backgrounds inconsistently vs content.
6. **Scattered `BorderHardDark` vs `BorderHard`** — e.g. `LocationOnboardingScreen` uses `BorderHardDark`; most screens use `BorderHard`. No single rule.
7. **Legacy aliases** — `GlassWhite` → `SurfaceLight`, `GlassBorder` → translucent black; call sites that still think “glass” may paint light panels on dark backgrounds.
8. **Auth / date picker** — `SignUpScreen` still references `GlassSheetTokens.OledBlack` for some containers while surrounding chrome uses light borders.
9. **Map style vs app theme** — Android map still uses dark zinc JSON style (`DARK_MAP_STYLE`) even when app is light-first (ties into known issue #5).

### Acceptance criteria for dark/light

- [x] Toggle dark ↔ light in Settings: every tab root, sheet, dialog, toast, tab bar, and chat chrome updates correctly. *(Track A 2026-07-16: helpers + sheet tokens + tab bars + hotspot sweep; device smoke still pending)*
- [x] Borders remain visible in both modes (2dp hard edge; black on light, white or high-contrast on dark). *(via `clickBorderColor()` / `LocalIsDarkMode`)*
- [x] No forced-white sheets or forced-black text on dark surfaces (except intentional inverse chips). *(`OledSheetTheme` + `GlassSheetTokens` theme-aware; call/search overlays stay intentional dark)*
- [x] Primary `#630ed4` CTAs readable in both modes (`onPrimary` white).
- [x] Map basemap policy decided and documented (light = color/default; dark = dark tiles; ghost = grayscale/muted).

### Suggested implementation approach

1. Add theme-aware border/surface helpers (e.g. `clickBorderColor()`, `clickCardSurface()`) reading `MaterialTheme.colorScheme` / `isSystemInDarkTheme` or app `isDarkMode`.
2. Fix `OledSheetTheme` + `GlassSheetTokens` to branch on dark/light (or delete “Oled” naming and use Functional Clarity sheet tokens).
3. Pass `isDarkMode` into iOS `PlatformBottomBar` (or read from CompositionLocal) and set tab bar appearance accordingly.
4. Grep `BorderHard` / `SurfaceLight` / `OledBlack` / `UIColor.white` and replace hard-codes with theme helpers.
5. Re-run §0 automated gates + visual spot-check both modes on Android + iOS.

---

## 4. Preexisting issues — still open (fix these)

**None of [`../regression-testing/03-known-issues-audit.md`](../regression-testing/03-known-issues-audit.md) #1–11 were fixed in the visual pass.** Treat that audit as the bug backlog. Summary:

| # | Title | Pri | Area | Start here |
|---|--------|-----|------|------------|
| **1** | Incomplete group registration (BLE handshake) | P0 | Proximity / `click-web` | `bindProximityHandshake.ts`, coalesce window, mark-matched without connection |
| **2** | Duplicate connections | P0 | Connections | Client re-tap + server `ensureConnectionForMemberSet` idempotency |
| **3** | Handshake → 1:1 DM (not group-only) | P1 | Proximity UX | Two-person tap should yield 1:1; see audit “partial/misfiled” |
| **4** | Events missing from list view | P1 | Map | `MapUtils.determineMapRenderData` EVENT clustering; discovery feed parity |
| **5** | Map not rendering in color | P2 | Map | `MapView.android.kt` always `DARK_MAP_STYLE`; align with Functional Clarity + ghost mode |
| **6** | Android calls not working | P0 | Calls | `CallManager.android.kt`: after permission grant, **retry** call (no `Ended` without resume) |
| **7** | Voice message crashes Android | P0 | Chat media | `MediaRecorder` / player / mic contention after ultrasonic |
| **8** | Group chat creation crashes | P0 | Connections | FAB create-group path; needs device repro + fix |
| **9** | Hazard beacon icon oversized | P2 | Map | Pin size vs other kinds |
| **10** | General visual bugs | P2 | UI | Spot-check after dark/light + mock layouts |
| **11** | Android-specific roll-up | P0–P2 | Android | Permissions, BLE, LiveKit, voice — see [`../regression-testing/04-android-focus.md`](../regression-testing/04-android-focus.md) |

### Fix rules for known issues

- Do **not** mark `[KNOWN-N]` checklist rows as pass until the bug is actually fixed.
- Prefer fixing server + client together for #1–3 (`click-web` proximity routes + mobile `ProximityManager` / `ConnectionViewModel`).
- After each fix: relevant checklist sections + smoke steps that cite that `[KNOWN-N]`.
- Update status in `03-known-issues-audit.md` when confirmed fixed (with evidence).

---

## 5. Design-asset layout redesigns — **separate chat**

The first pass mostly **restyled** existing Compose structure (borders, fills, no gradients). It did **not** fully implement mock **layouts / IA** from [`../design-assets/`](../design-assets/).

| Mock folder | Intended layout changes (not done / incomplete) | Counterpart |
|-------------|--------------------------------------------------|-------------|
| `home/` | Featured Click, explore categories, greeting hierarchy | `HomeScreen` |
| `settings/` | Profile header + preferences grouping per mock | `SettingsScreen` |
| `chat/` | Inbox card stack / dense bordered list metaphor | `ConnectionsListView` |
| `add_click_streamlined_header/` | Large Tap-to-Connect hero card, streamlined header | `AddClickScreen` |
| `add_click_fixed_navigation/` | Rolodex / oversized name stack for “Your Clicks” | Connections inbox |
| `events_discovery_with_real_mini_map/` | Events-for-you + mini-map PiP composition | `MapDiscoveryLayout` |
| `map_events_full_screen_map/` | Full-map + event pin sheet chrome | `MapScreen` |
| `event_details_expanded_dark/` | Expanded event detail over map | Beacon/event sheets |
| `functional_clarity/DESIGN.md` | Token bible (partially applied) | Theme |

**Instruction for a dedicated UI-layout chat:** Use HTML only for hierarchy/spacing intent; do not copy markup; keep ViewModels; finish dark/light helpers first or in parallel so new layouts are not light-only.

---

## 6. Regression debt

| Gate | Status after first pass |
|------|-------------------------|
| §0 automated (Gradle + `npm test`) | Track A 2026-07-16 PASS — Android compile + `testDebugUnitTest` · iOS sim compile + `iosSimulatorArm64Test` · `click-web` `npm test` 22 suites / 145 tests |
| Smoke [`02-smoke-10min.md`](../regression-testing/02-smoke-10min.md) | **Not run on device** |
| Full checklist [`01-full-checklist.md`](../regression-testing/01-full-checklist.md) | **Not run** |
| Known-issues audit | **Bugs still open** |
| Android focus [`04-android-focus.md`](../regression-testing/04-android-focus.md) | **Not run** |

Pre-merge bar for this continuation: §0 green + smoke both platforms + fixed P0 known issues verified on Android device.

---

## 7. Prompt template for a new chat

Copy-paste and adjust:

```text
Continue Click Functional Clarity work using the handoff doc:
click/docs/handoff/functional-clarity-continuation.md

Scope for THIS chat (pick one primary track):
A) Fix dark/light mode inconsistencies end-to-end (theme helpers, OledSheetTheme, tab bars, BorderHard).
B) Fix preexisting known issues from click/docs/regression-testing/03-known-issues-audit.md
   — start with P0: #6 Android calls, #7 voice messages, #1/#2 handshake, #8 group create.
C) (Separate chat only) Implement design-asset LAYOUT redesigns from click/docs/design-assets/*
   — do not conflate with bugfix unless I say so.

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Preserve ViewModels / BLE / Realtime / LiveKit behavior unless fixing a known bug.
- No gradients / glass blur / ambient mesh on product chrome.
- After changes: run click/docs/regression-testing §0 automated gates.
- Do not false-pass [KNOWN-N] rows.
- Subagents: use composer-2.5 only if spawning agents.

Read first:
- click/docs/handoff/functional-clarity-continuation.md (this file)
- click/docs/design-assets/functional_clarity/DESIGN.md
- click/docs/regression-testing/03-known-issues-audit.md
- click/docs/ui-ux/mobile/01-design-system.md
```

### Recommended chat split

1. **Chat A — Dark/light + theme hardening**  
2. **Chat B — Known issues P0/P1 (calls, voice, proximity, events)**  
3. **Chat C — Mock layout redesigns** (home / add click / connections rolodex / map event detail)

---

## 8. Key file index

| Concern | Paths |
|---------|--------|
| Colors / platform style | `composeApp/.../ui/theme/Color.kt`, `PlatformTheme.kt` |
| Sheet theme | `ui/components/OledSheetTheme.kt`, `GlassSheetTokens.kt` |
| Cards / buttons | `GlassCard.kt`, `AdaptiveCard.kt` |
| Tab bars | `BottomBar.android.kt`, `BottomBar.ios.kt` |
| App dark default | `App.kt` (`isDarkMode`) |
| Known issues | `docs/regression-testing/03-known-issues-audit.md` |
| Android calls | `calls/CallManager.android.kt` |
| Proximity server | `click-web/lib/server/proximity/bindProximityHandshake.ts` |
| Map style | `ui/components/MapView.android.kt` |
| Design mocks | `docs/design-assets/*/code.html` |

---

## 9. Done when

- [ ] Dark and light modes are visually consistent across shell, sheets, chat, map chrome, auth.
- [ ] P0 known issues (#1, #2, #6, #7, #8, relevant #11) fixed and regression-checked on device.
- [ ] P1 #3–#4 addressed; #5/#9 decided or fixed.
- [ ] §0 automated green; smoke checklist completed on Android + iOS.
- [ ] Layout redesigns either completed in a dedicated chat or explicitly scheduled with owners.
