# Click Mobile — UI/UX Audit & Execution Plan

**Status:** durable
**Date:** 2026-08-11
**Issue:** [#54 — UI Audit](https://github.com/lightningbolts/click/issues/54)
**Scope:** KMP mobile app only — `composeApp/src/commonMain/.../ui/` (153 files), `viewmodel/` (16 files), `App.kt`. Web companion, Insights, and backend are out of scope.
**Target design system:** Functional Clarity — [`../mobile/01-design-system.md`](../mobile/01-design-system.md), tokens in [`../../design-assets/functional_clarity/DESIGN.md`](../../design-assets/functional_clarity/DESIGN.md)
**Method:** static analysis of the working tree at `b6bde68d`. Every finding cites `file:line`. **No device testing was performed** — items needing device verification are marked ⚠️ DEVICE.

---

## How to use this document

This is written to be **executed by coding agents**, one work package at a time.

- §1–§2 = the audit (what works, what doesn't), with baseline metrics.
- §3 = ordered **work packages (WP1–WP12)**. Each has a goal, exact file paths, steps, acceptance criteria, and a **verification command** that must pass.
- §4 = the baseline metrics table. Re-run those commands after each WP to prove movement.

**Rules for agents working this plan:**

1. **One work package per PR.** Do not batch. Each WP is independently revertable.
2. **Do not redesign.** The Functional Clarity theme is settled. This plan enforces the existing spec; it does not invent a new one.
3. **Run the verification command** in the WP before opening the PR, and paste the before/after numbers into the PR description.
4. **Do not change ViewModel/BLE/Realtime/LiveKit behavior** except where a WP explicitly says to (WP2 only).
5. If a WP's premise turns out to be wrong (e.g. a "dead" file is actually referenced), **stop and report** rather than forcing the change.

---

## 1. What works right now

Credit where due — several things are in good shape and must not be regressed.

| Area | Evidence |
|---|---|
| **No gradients anywhere** | 0 matches for `Brush.*Gradient(` in `ui/`. The "no gradient" rule is fully met. Only residue is two unused constants `GradientTextStart`/`GradientTextEnd` at `ui/theme/Color.kt:63-64`. |
| **Live blur is nearly eliminated** | Only **2** `Modifier.blur` call sites remain (`ProfileBottomSheet.kt:2039,2057`). `chat/ChatPhotoBubble.kt:73,300,382` documents the correct pattern — blur pre-baked into bitmaps, never live. |
| **Typography discipline** | 569 uses of `MaterialTheme.typography.*` vs 40 ad-hoc `fontSize` — ~93% conforming. |
| **Border color helper is adopted** | `clickBorderColor()` (`ui/theme/PlatformTheme.kt:64`) is referenced across 38 files. |
| **Home & Map state handling is exemplary** | Both use a sealed state (`HomeViewModel.kt:60`, `MapViewModel.kt:107`) driving Loading → Error → Retry. `HomeScreen.kt:274,278,310` is the reference implementation every other screen should copy. |
| **P0 back-gesture flicker has a real fix** | `navigation/HomeContinuityPolicy.kt` extracts the underlay rule, is wired at `App.kt:1609`, and is **unit-tested** (`commonTest/.../HomeContinuityPolicyTest.kt`). Correct pattern: pure policy function, testable without a UI harness. ⚠️ DEVICE to confirm the flicker is gone. |
| **Reduce-motion primitive exists** | `platform/ReduceMotion.kt:7` with Android + iOS actuals, correctly used in `MomentKit.kt` and `ClickLogoPulse.kt`. |
| **Offline banner is global, not per-screen** | Single mount at `App.kt:1362-1368` covers every screen. |
| **Dialogs funnel to one base** | Despite multiple wrapper names, all dialogs bottom out in `UnifiedPopup*`; **0** raw `AlertDialog(` bypasses were found. |
| **Design tokens have a unit test** | `androidUnitTest/.../GlassCardTest.kt` asserts corner radii and scrim alpha against the spec — no Compose harness needed. **This is the pattern WP1 and WP5 extend.** |

---

## 2. What doesn't work

Ordered by user impact.

### 2.1 🔴 Silent failures — users see "empty", not "broken"

**26 of 81 `catch` blocks** in `ui/` + `viewmodel/` log and set no user-visible state.

The worst is a **fake success**, `viewmodel/HubChatViewModel.kt:287-291`:

```kotlin
} catch (e: Exception) {
    _channelReady.value = true          // ← reports success on failure
    println("HubChatViewModel: session error: ${e.redactedRestMessage()}")
}
```

`HubChatScreen.kt:257-260` hides its loading view when `channelReady` is true, so a hub whose channel failed to join renders as a **normal, empty, idle chat**. The user has no way to know it is broken and no way to retry.

Same class, elsewhere:

| Location | Symptom |
|---|---|
| `viewmodel/GlobalSearchViewModel.kt:369-372` | Search backend failure clears the spinner only → `GlobalSearchScreen.kt:225` renders "No results", so an outage is indistinguishable from a genuine zero-result search. |
| `viewmodel/ChatViewModel.kt` (11 silent catches, e.g. `:692`) | Realtime subscription can drop silently; user believes chat is live when it is not. |
| `ui/screens/ConnectionsListView.kt:401-407` | Availability fetch failure → bubbles just don't render; looks like "nobody is free". |
| `ui/components/UserProfileBottomSheet.kt:161` | `catch (_: Exception)` — swallowed entirely. |
| `viewmodel/HomeViewModel.kt` (7 silent catches) | Auxiliary home widgets degrade to empty with no partial-failure messaging. |

`ui/screens/NfcScreen.kt` (1,582 lines) contains **zero** `catch` blocks — NFC read/write failure has no surfacing path at this layer at all.

### 2.2 🔴 Accessibility is largely absent

The team's own doc standard requires an "A11y & Responsive" section per feature ([`../mobile/00-INDEX.md`](../mobile/00-INDEX.md)), but the implementation does not meet it.

| Signal | Count | Meaning |
|---|---|---|
| `Role.Button` / `Role.Tab` / etc. | **0** | No custom control declares its role |
| `stateDescription` | **0** | Toggles/selection states unannounced |
| `heading()` | **0** | No screen-reader heading navigation |
| `liveRegion` | **0** | Async changes (message delivered, call incoming) never announced |
| `Modifier.semantics { }` | **6**, in 2 files only | `chat/ChatDeliveryReceipt.kt:40`, `components/ConnectionArchiveWarningBanner.kt:59` |
| Gesture call sites with no semantics | **26** across 11 files | Swipe-to-archive rows, swipe-back, audio scrubber are inoperable via TalkBack/VoiceOver |
| `Icon(contentDescription = null)` | **179 of 271** | Many are legitimately decorative; see below for the confirmed failures |

**Confirmed unlabeled control:** `components/InterestEditor.kt:142-149` — an `IconButton` with `ExpandLess`/`ExpandMore`, `contentDescription = null`, **no adjacent text**. Announced as an unlabeled button.

**Confirmed color-only signal:** `components/AvatarWithOnlineIndicator.kt:229-251` — online presence is a green dot with no text, no shape difference, no content description. Invisible to color-blind and screen-reader users.

**Chat avatars unlabeled:** `chat/ChatMessageBubble.kt:364` passes `contentDescription = null` for the peer avatar, so "message from X" is never announced — even though `AvatarWithOnlineIndicator.kt:153` already implements the correct pattern (`"$name profile photo"`).

**Touch targets under 48dp** (raw `.clickable`, not `IconButton`): `screens/SettingsScreen.kt:898` at **30dp** (change-photo badge) is the worst; `camera/DisposableCameraShared.kt:453` at 46dp.

**Reduce-motion is inconsistently applied:** the primitive exists but **7 files** run `infiniteRepeatable`/`rememberInfiniteTransition` without it — `chat/ChatPrimitives.kt`, `screens/MapScreen.kt`, `screens/MemoriesListSection.kt`, `screens/ConnectionsListView.kt`, `screens/QRScannerScreen.kt`, `screens/ChatView.kt`, `screens/NfcScreen.kt`.

### 2.3 🟠 Design-token drift

| Rule | Conforming | Violating | Detail |
|---|---|---|---|
| Radius 8dp/16dp | 72 | **219** | 291 `RoundedCornerShape(` sites across **19 distinct** argument values. 12dp appears **50×** — an unofficial de-facto standard nearly as common as 16dp. |
| Borders 2dp | 26 | **25** | 13× 1dp, 1× 0.5dp, plus ad-hoc widths. |
| Colors from tokens | — | **63** literals, **33 distinct** | `Color(0x…)` outside `ui/theme/`. Top offender `components/AvatarWithOnlineIndicator.kt` (11). |
| Spacing tokens | — | **no token object exists** | 461 raw `.padding(` calls; only `chat/ChatMessageListSpacing.kt` exists and is chat-scoped. |

The colour literals include **seven purple variants** that are near-misses of the official `#630ed4` primary (`0xFF7C3AED`, `0xFF9D4EDD`, `0xFFC4A8FF`, `0xFFD2BBFF`, `0xFFEDE0FF`, `0xFFB39DDB`, `0xFF4A4455`). This is textbook token drift: each looks right in isolation, collectively the brand smears.

**Dark-mode blindness:** `camera/DisposableCameraShared.kt:139-155` hardcodes `Color.Black` surface, `Color.White` text and `BorderHardDark` regardless of theme, so it always renders dark-mode chrome. This corroborates open known issues [#13, #16, #20](../../regression-testing/03-known-issues-audit.md).

### 2.4 🟠 No canonical button, and four competing component families

**There is no `ClickButton`.** Verified: `grep "fun ClickButton|fun ClickPrimaryButton|fun PrimaryButton"` → **0 matches**. Instead there are **157 raw button call sites** (`Button(`, `TextButton(`, `OutlinedButton(`, `FilledTonalButton(`), each styling itself. The nearest thing, `AdaptiveButton`, is buried inside `components/AdaptiveCard.kt:92` — a button hidden in a card file — with 5 call sites.

Every screen therefore re-derives the primary-action look. This is the single largest source of visual inconsistency and the reason token drift (§2.3) keeps recurring.

Other parallel families:

| Concern | Competing implementations |
|---|---|
| Cards | `GlassCard` (19 sites) **and** `AdaptiveCard` (19 sites) — two generic cards, no canonical one. Plus 45 raw `Surface(` and 5 raw `Card(` in `screens/`. |
| Toasts | `UnifiedToastHost` (5) **and** `GlassToastHost` (2, a pure alias — `rememberGlassToastState` literally returns `rememberUnifiedToastState()`) **and** `TetherCompassToast` (3). |
| Sheets | `Click*BottomSheet` family (20+ sites, dominant) **and** a legacy `Glass*Sheet*` family backing exactly one caller (`MapBeaconSheetRoot`, Android). |
| Avatars | 1 shared `AvatarWithOnlineIndicator` (4 sites) vs **8 files** hand-rolling `AsyncImage` + `CircleShape`. |
| Profile sheets | `ProfileBottomSheet.kt` (3,293 lines) + `EventDirectoryUserProfileSheet.kt` (203) both live; `UserProfileBottomSheet.kt` (1,018) dead. |

### 2.5 🟠 Shared state primitives exist but are barely used

`AppEmptyState`, `AppShimmerScreen`, and `UnifiedToastHost` were built to unify these states, then not adopted:

- `AppEmptyState` — used by **3** screens (`MapDiscoveryLayout.kt:494`, `ConnectionsListView.kt:730`, `GlobalSearchScreen.kt:274`). Every other empty case is a bespoke inline `if (x.isEmpty())`.
- `AppShimmerScreen` — used in exactly **1** place (`HomeScreen.kt:274`). ~22 other files use a raw spinner.
- **9 screens have no loading affordance at all**: `ConnectionsScreen`, `UnifiedSearchSheet`, `LocationOnboardingScreen`, `ProfileBasicsGateScreen`, `InterestTaggingScreen`, `WelcomeScreen`, `MyQRCodeScreen`, `ClicktivitiesScreen`, and `GlobalSearchScreen` (which uses a bespoke `ClickLogoPulse` instead).

### 2.6 🟠 1,361 lines of dead UI code

Independently verified — all have **zero** callers across `commonMain`, `androidMain`, and `iosMain`:

| File | Lines | Note |
|---|---|---|
| `ui/components/UserProfileBottomSheet.kt` | 1,018 | Abandoned refactor; duplicates `ProfileBottomSheet.kt` |
| `ui/components/WaitlistDialog.kt` | 198 | No callers |
| `ui/components/ClickLogoFingerOutline.kt` | 145 | Unused point data |
| `ui/components/ClickLogoOutlinePoints.kt` | **0** | Empty file |

Dead code is a specific hazard for agents: it is indistinguishable from live code during retrieval, so agents "fix" files that ship to nobody.

### 2.7 🔴 UI correctness is verified almost entirely by hand

| Signal | Value |
|---|---|
| Manual regression checklist items | **387** (`docs/regression-testing/01-full-checklist.md`) |
| Compose UI tests | **5**, all in `iosSimulatorArm64Test` (macOS-only CI) |
| `testTag` usages across 153 UI files | **20** |
| Screenshot/snapshot testing | **none** (no Paparazzi, no Roborazzi) |

The Android CI job runs **zero** UI tests. Every visual regression in §2.3 is invisible to CI, which is precisely why token drift accumulated. For an agentic workflow this is the binding constraint: **an agent cannot run a 387-item manual checklist**, so it cannot self-verify UI work.

---

## 3. Execution plan

Ordered so that cheap, non-visual, high-certainty work lands first, and so guard rails exist *before* the large migrations.

### Phase A — Zero-risk cleanup and guard rails (WP1–WP4)

---

#### WP1 — Delete dead UI code
**Why:** removes 1,361 lines that mislead agents and humans. Zero user-visible change.
**Risk:** none (verified zero callers).

**Files to delete:**
- `composeApp/src/commonMain/.../ui/components/UserProfileBottomSheet.kt`
- `composeApp/src/commonMain/.../ui/components/WaitlistDialog.kt`
- `composeApp/src/commonMain/.../ui/components/ClickLogoFingerOutline.kt`
- `composeApp/src/commonMain/.../ui/components/ClickLogoOutlinePoints.kt`

**Steps:**
1. Re-confirm zero callers for each (command below). **If any has a caller, stop and report.**
2. Delete the files. Remove any now-unused imports flagged by the compiler.
3. Also delete the unused `GradientTextStart`/`GradientTextEnd` constants at `ui/theme/Color.kt:63-64`.

**Acceptance criteria:** app compiles; no behavior change; 4 files gone.

**Verification:**
```bash
for f in UserProfileBottomSheet WaitlistDialog ClickLogoFingerOutline ClickLogoOutlinePoints; do
  echo "$f: $(grep -rn "$f(" --include=*.kt composeApp/src | grep -vc "components/$f.kt")"
done   # every count must be 0 BEFORE deleting
./gradlew :composeApp:assembleDebug
```

---

#### WP2 — Eliminate silent failures
**Why:** §2.1. Highest user-facing impact in this plan. A user currently cannot distinguish "broken" from "empty".
**Risk:** low — additive error state only. **This is the only WP permitted to change ViewModel logic.**

**Steps:**
1. **Fix the fake success first.** `viewmodel/HubChatViewModel.kt:287-291`: do **not** set `_channelReady.value = true` in the `catch`. Add a `_channelError: MutableStateFlow<String?>` and set it. Render it in `ui/screens/HubChatScreen.kt` (near the loading view at `:257-260`) with a Retry action.
2. `viewmodel/GlobalSearchViewModel.kt:369-372`: add an error field; `GlobalSearchScreen.kt:225` must distinguish "search failed / Retry" from "no results".
3. `ui/screens/ConnectionsListView.kt:401-407` and `ui/components/UserProfileBottomSheet.kt:161` *(note: the latter dies in WP1 — skip if already deleted)*: surface a non-blocking toast.
4. Audit the remaining silent catches (11 in `ChatViewModel`, 7 in `HomeViewModel`, 4 in `HubChatViewModel`). For each, choose deliberately and leave a one-line comment stating the choice:
   - user-visible error state, **or**
   - explicitly-acceptable silent degradation (`// silent by design: auxiliary widget, absence is not an error`).
5. Follow the `HomeState` sealed-class pattern (`viewmodel/HomeViewModel.kt:60`) — do not invent a new state shape.

**Acceptance criteria:**
- No `catch` block sets a readiness/success flag to `true`. This is a hard rule.
- Every silent catch is either fixed or carries an explicit justification comment.
- Hub chat join failure shows an error + Retry.
- Search failure is visually distinct from zero results.

**Verification:**
```bash
# Must return 0 — no catch block may fake success:
grep -rn -A4 "catch (e: Exception)" --include=*.kt \
  composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel \
  | grep -c "Ready.value = true"
./gradlew :composeApp:testDebugUnitTest
```
Add a regression test asserting `channelReady == false` and `channelError != null` when the hub session throws.

---

#### WP3 — Lock the design system with unit tests
**Why:** §2.7. Without this, WP5–WP8 will drift back. Build the ratchet *before* the migration.
**Risk:** none (test-only).

**Steps:**
1. Extend the existing pattern in `composeApp/src/androidUnitTest/.../ui/components/GlassCardTest.kt` — it already asserts tokens without a Compose harness.
2. Add `ui/theme/DesignTokenConformanceTest.kt` asserting the Functional Clarity spec: primary is exactly `0xFF630ED4`; `BorderHard` is `#000000`; `BorderHardDark` is `#FFFFFF`; canonical radii are 8dp/16dp.
3. Add a **source-scanning guard test** (plain JVM test reading files under `ui/`, in the spirit of click-web's `routeAuth.contract.test.ts`) that fails when:
   - a `Color(0x…)` literal appears outside `ui/theme/` beyond an explicit, shrinking allowlist (seed the allowlist at today's **63**);
   - any `Brush.*Gradient(` appears (currently 0 — keep it 0);
   - `Modifier.blur(` appears beyond the 2 known sites.
4. Seed each threshold at **today's numbers** so the test passes on `main` immediately. This is a ratchet, not a cleanup mandate.

**Acceptance criteria:** tests pass unmodified on `main`; adding a new hardcoded colour or a gradient makes CI red.

**Verification:** `./gradlew :composeApp:testDebugUnitTest`

---

#### WP4 — Make the Android CI run UI tests
**Why:** the 5 existing Compose UI tests run only on macOS, so Android CI has no UI signal at all.
**Risk:** low (CI config + test infra).

**Steps:**
1. Move the platform-agnostic UI tests from `iosSimulatorArm64Test` into `commonTest` where they don't depend on iOS specifics, so both CI jobs run them.
2. If a Compose test harness is not available for the Android JVM target, document that explicitly in `AGENTS.md` and keep the guard tests from WP3 (which need no harness) as the Android-side signal.
3. Add `testTag`s to the primary interactive elements of the five highest-traffic screens (Home, Connections, Chat, Map, Settings). Baseline is **20** tags across 153 files.

**Acceptance criteria:** `android-ci.yml` executes at least one UI-behaviour test; `testTag` count rises above 60.

**Verification:**
```bash
grep -rc "testTag" --include=*.kt composeApp/src/commonMain/kotlin/compose/project/click/click/ui | awk -F: '{s+=$2} END {print s}'
```

---

### Phase B — Consolidation (WP5–WP8)

---

#### WP5 — Introduce the canonical button
**Why:** §2.4. The root cause of recurring visual drift. **Do this before WP6/WP7.**
**Risk:** medium — touches many screens. Migrate incrementally.

**Steps:**
1. Create `ui/components/ClickButton.kt` with `ClickButton` (primary, solid `#630ed4`, 8dp radius), `ClickSecondaryButton` (white fill, 2dp `#000` border), and `ClickTextButton`. Pull styling from [`../mobile/01-design-system.md`](../mobile/01-design-system.md) §1.2 — do not invent values.
2. Implement the spec'd pressed state: **2dp translate down-right + instant 10% darken, no animation curve** (§1.3 of the design doc).
3. Migrate call sites **screen by screen, one PR per screen**, highest-traffic first: Home → Connections → Chat → Map → Settings.
4. Fold `AdaptiveButton` (`components/AdaptiveCard.kt:92`) into the new component and delete it — a button must not live in a card file.
5. Add a WP3-style guard test capping raw `Button(`/`TextButton(`/`OutlinedButton(` counts, ratcheting down as screens migrate.

**Acceptance criteria:** `ClickButton` exists and is the documented default; the 5 named screens use it exclusively; raw button count falls monotonically.

**Verification:**
```bash
grep -rEc "\bButton\(|\bTextButton\(|\bOutlinedButton\(|\bFilledTonalButton\(" --include=*.kt \
  composeApp/src/commonMain/kotlin/compose/project/click/click/ui | awk -F: '{s+=$2} END {print s}'
# Baseline 161 — must decrease every PR
```

---

#### WP6 — One card, one toast
**Why:** §2.4. Two card primitives and three toast systems.
**Risk:** medium (cards), trivial (toasts).

**Steps:**
1. **Toasts (do first — nearly free):** `GlassToastHost`/`rememberGlassToastState` are pure aliases of the Unified versions. Replace the 2 call sites (`ConnectionsListView.kt`, `ChatView.kt`) with `UnifiedToastHost` and delete the aliases.
2. Decide `TetherCompassToast`'s fate (3 sites): either express it as a *style* of `UnifiedToastHost`, or document in `ui/README.md` why it is deliberately separate. Either outcome is acceptable; leaving it undocumented is not.
3. **Cards:** pick `AdaptiveCard` or `GlassCard` as canonical (they have 19 sites each — choose whichever better matches Functional Clarity today), migrate the other's call sites, delete the loser.
4. Rename `AdaptiveComponents.kt` → `PlatformBottomBar.kt`. It contains no cards and its name collides confusingly with `AdaptiveCard.kt`.

**Acceptance criteria:** one card composable; one general toast host; `GlassToastHost` gone.

**Verification:** `grep -rn "GlassToastHost\|rememberGlassToastState" --include=*.kt composeApp/src | wc -l` → 0

---

#### WP7 — Adopt the shared state primitives
**Why:** §2.5. The primitives exist; they just aren't used.
**Risk:** low.

**Steps:**
1. Replace bespoke inline empty blocks with `AppEmptyState`: `ChatView.kt:1115`, `HubChatScreen.kt:208,258`, `UnifiedSearchSheet.kt:267,276`, `ConnectionContextSheet.kt:404,422`, `MemoriesListSection.kt:63`, `ProfileBottomSheet.kt` (multiple).
2. Give the 9 loading-less screens (§2.5) a loading affordance; prefer `AppShimmerScreen` for list/content screens, spinner only for short blocking actions.
3. Standardise on `UnifiedToastHost` for transient errors (depends on WP6).

**Acceptance criteria:** `AppEmptyState` usage rises from 3 to ≥10; no user-facing list screen renders a bare blank on empty.

**Verification:**
```bash
grep -rln "AppEmptyState" --include=*.kt composeApp/src/commonMain/kotlin/compose/project/click/click/ui \
  | grep -v "AppEmptyState.kt" | wc -l   # baseline 3 → target >=10
```

---

#### WP8 — Retire the legacy sheet family & duplicate avatars
**Why:** §2.4.
**Risk:** medium — `MapBeaconSheetRoot` has platform-specific wiring; sheet gesture physics are easy to regress. ⚠️ DEVICE verification required.

**Steps:**
1. Migrate `MapBeaconSheetRoot` (Android) from `GlassAdaptiveBottomSheet` to `ClickFormBottomSheet` (the dominant primitive, 12 sites).
2. Once the last caller is gone, delete `GlassAdaptiveBottomSheet.kt`, `GlassSheetGesturePhysics.kt`, `GlassSheetGrabber.kt`. **Keep `GlassSheetTokens.kt`** — it is a widely-imported token file (334 refs).
3. Replace the 8 inline `AsyncImage` + `CircleShape` avatar renderers with `AvatarWithOnlineIndicator` (this also fixes 8 a11y gaps for free — see WP9).

**Acceptance criteria:** one sheet family; one avatar renderer. Sheet drag/dismiss feel unchanged on device.

**Verification:** `grep -rn "GlassAdaptiveBottomSheet" --include=*.kt composeApp/src | wc -l` → 0

---

### Phase C — Accessibility & token conformance (WP9–WP12)

---

#### WP9 — Accessibility: labels and roles
**Why:** §2.2. The app is currently not operable with a screen reader.
**Risk:** low (additive semantics).

**Steps:**
1. Fix the confirmed unlabeled control: `components/InterestEditor.kt:142-149` — give the expand/collapse `IconButton` a real `contentDescription`.
2. Label avatars: `chat/ChatMessageBubble.kt:364` and the other 9 null-description `Image`/`AsyncImage` sites. Copy the existing correct pattern from `AvatarWithOnlineIndicator.kt:153`.
3. Add `Role.Button` / `Role.Tab` to custom clickables — especially the tab bar and any `Modifier.clickable` container acting as a button. Baseline is **0**.
4. Add `stateDescription` to toggles and selected states (segment bars, ghost mode, availability toggles).
5. Add `liveRegion` for async announcements: message delivered/failed, incoming call, connection made.
6. Fix the color-only online dot (`AvatarWithOnlineIndicator.kt:229-251`) — add a `contentDescription` conveying online/offline. Do not rely on colour alone.

**Acceptance criteria:** `Role.` usages > 0; `liveRegion` > 0; zero interactive `Icon`s with `contentDescription = null` and no adjacent text label.

**Verification:**
```bash
grep -rn "Role\.\|stateDescription\|liveRegion\|\.heading()" --include=*.kt \
  composeApp/src/commonMain/kotlin/compose/project/click/click/ui | wc -l   # baseline 0
```
⚠️ DEVICE: a TalkBack + VoiceOver pass over Home → Connections → Chat is required to close this WP.

---

#### WP10 — Accessibility: gestures, targets, motion
**Why:** §2.2.
**Risk:** low–medium.

**Steps:**
1. Add semantics + accessibility actions to the 26 gesture sites so swipe actions are reachable without gestures — priority: `chat/ConnectionRowGestures.kt` (swipe-to-archive/delete), `chat/ChatAudioBubble.kt` (audio scrubber), `components/InteractiveSwipeBackContainer.kt`.
2. Raise sub-48dp targets: `screens/SettingsScreen.kt:898` (**30dp** — worst), `camera/DisposableCameraShared.kt:453` (46dp). Increase the touch target without necessarily enlarging the visual (padding, or `Modifier.minimumInteractiveComponentSize()`).
3. Gate the **7** ungated files' `infiniteRepeatable` animations behind the existing `rememberReduceMotionEnabled()` — see the WP10 verification command for the current list.
4. Address dynamic-type clipping on the 15 fixed-height text containers — 11 are in `screens/NfcScreen.kt`. Prefer `defaultMinSize(minHeight=)` over `height()`.

**Acceptance criteria:** every `infiniteRepeatable` is reduce-motion gated; no raw `.clickable` target below 48dp; swipe actions have equivalent accessible actions.

**Verification:**
```bash
# Every file using infiniteRepeatable must also reference reduce motion:
for f in $(grep -rl "infiniteRepeatable\|rememberInfiniteTransition" --include=*.kt composeApp/src/commonMain); do
  grep -q "ReduceMotion\|reduceMotion" "$f" || echo "UNGATED: $f"
done   # must print nothing
```

---

#### WP11 — Converge radii, borders, and colours
**Why:** §2.3. Do this **after** WP5/WP6, since shared components absorb most call sites automatically.
**Risk:** medium — visual change; needs screenshot review. ⚠️ DEVICE.

**Steps:**
1. Add `ui/theme/Dimens.kt` with radius and spacing tokens. **No such token object exists today** — this is new. Encode the canonical set: radius 8/16, and the spacing scale actually in use (4/8/12/16/24/32).
2. Resolve the 12dp radius question explicitly: 50 sites use it. Either add 12dp to the official spec (and update [`../mobile/01-design-system.md`](../mobile/01-design-system.md)) or migrate them to 8/16. **Do not leave it undecided** — silent third standards are how drift restarts.
3. Replace the 63 hardcoded colours with tokens. Start with the 7 near-miss purples (§2.3) — those are brand-damaging. Add semantic tokens for the repeated status colours (`0xFFFF6B6B` ×8, `0xFF4CAF50` ×6) as e.g. `StatusDanger`/`StatusSuccess` in `ui/theme/Color.kt`.
4. Normalise the 25 non-conforming `BorderStroke` widths to 2dp, pairing with `clickBorderColor()`.
5. Fix theme-blind chrome at `camera/DisposableCameraShared.kt:139-155`.
6. Tighten the WP3 allowlist as each batch lands.

**Acceptance criteria:** `Color(0x` outside `ui/theme/` trends to 0; radius values reduce from 17 distinct to the documented set; `Dimens.kt` exists and is used.

**Verification:**
```bash
cd composeApp/src/commonMain/kotlin/compose/project/click/click
grep -rn "Color(0x" --include=*.kt ui | grep -v "ui/theme/" | wc -l   # baseline 63 → target 0
```

---

#### WP12 — Close out remaining blur + document the system
**Why:** finish the Functional Clarity migration and make it durable for future agents.
**Risk:** low.

**Steps:**
1. Remove or pre-bake the last 2 live blurs (`ProfileBottomSheet.kt:2039,2057`), following the documented bitmap approach in `chat/ChatPhotoBubble.kt:73`.
2. Write `ui/README.md` (the package-README convention is already established across 25 packages) stating the canonical choice for each concern: **button → `ClickButton`; card → `<winner>`; sheet → `ClickFormBottomSheet`/`ClickActionBottomSheet`; dialog → `GlassAlertDialog`; toast → `UnifiedToastHost`; empty → `AppEmptyState`; loading → `AppShimmerScreen`; avatar → `AvatarWithOnlineIndicator`.**
3. Update [`../mobile/01-design-system.md`](../mobile/01-design-system.md) to match the resolved reality (radius decision from WP11, new `ClickButton`, `Dimens.kt`).
4. Refresh [`../../regression-testing/03-known-issues-audit.md`](../../regression-testing/03-known-issues-audit.md) — issues #13/#16/#20 are corroborated by this audit's findings and should reference the WPs that fix them.

**Acceptance criteria:** `ui/README.md` exists and names one canonical component per concern; design-system doc matches code; 0 live blur outside documented exceptions.

---

## 4. Baseline metrics

Snapshot at `b6bde68d` (2026-08-11). Re-run after each WP; every number should move in the stated direction.

| # | Metric | Baseline | Target | WP |
|---|---|---:|---:|---|
| 1 | Dead UI files | 4 (1,361 lines) | 0 | WP1 |
| 2 | Silent catch blocks | 26 / 81 | 0 unjustified | WP2 |
| 3 | `catch` blocks setting success=true | 1 | **0** | WP2 |
| 4 | Raw button call sites | 157 | ↓ each PR | WP5 |
| 5 | Card primitives | 2 | 1 | WP6 |
| 6 | Toast systems | 3 | 1 (+1 documented) | WP6 |
| 7 | Inline avatar renderers | 8 | 0 | WP8 |
| 8 | `AppEmptyState` adoption | 3 screens | ≥10 | WP7 |
| 9 | Screens with no loading state | 9 | 0 | WP7 |
| 10 | `Color(0x` outside theme | 63 (33 distinct) | 0 | WP11 |
| 11 | Distinct `RoundedCornerShape` values | 19 | ≤4 | WP11 |
| 12 | Non-2dp `BorderStroke` | 25 | 0 | WP11 |
| 13 | Live `Modifier.blur` | 2 | 0 | WP12 |
| 14 | `Role.`/`stateDescription`/`liveRegion`/`heading()` | 0 | >0 all | WP9 |
| 15 | Gesture sites without semantics | 26 | 0 | WP10 |
| 16 | Ungated `infiniteRepeatable` files | 7 | 0 | WP10 |
| 17 | Raw `.clickable` targets <48dp | 5 | 0 | WP10 |
| 18 | `testTag` usages | 20 | >60 | WP4 |
| 19 | Compose UI tests running in Android CI | 0 | ≥1 | WP4 |
| 20 | Ad-hoc `fontSize` | 40 | ↓ | WP11 |

**Collect them all:**

```bash
cd composeApp/src/commonMain/kotlin/compose/project/click/click
echo "hardcoded colors: $(grep -rn 'Color(0x' --include=*.kt ui | grep -v 'ui/theme/' | wc -l)"
echo "raw buttons:      $(grep -rEc '\bButton\(|\bTextButton\(|\bOutlinedButton\(|\bFilledTonalButton\(' --include=*.kt ui | awk -F: '{s+=$2} END {print s}')"
echo "AppEmptyState:    $(grep -rln 'AppEmptyState' --include=*.kt ui | grep -v 'AppEmptyState.kt' | wc -l)"
echo "a11y semantics:   $(grep -rn 'Role\.\|stateDescription\|liveRegion\|\.heading()' --include=*.kt ui | wc -l)"
echo "live blur:        $(grep -rn 'Modifier\.blur(' --include=*.kt ui | wc -l)"
echo "testTags:         $(grep -rc 'testTag' --include=*.kt ui | awk -F: '{s+=$2} END {print s}')"
```

---

## 5. Explicitly out of scope

To prevent scope creep by agents picking this up:

- **No new visual design.** Functional Clarity is settled; this plan enforces it.
- **No backend/API/RLS changes.** If a UI fix is blocked by a data bug, flag it — do not expand scope.
- **No architectural rewrite of ViewModels, BLE, Realtime, or LiveKit.** WP2 adds error state only.
- **Splitting the god files** (`ChatViewModel.kt` 5,155 lines; `MapScreen.kt` 3,415; `ProfileBottomSheet.kt` 3,293) is tracked in [`../../engineering-evaluation.md`](../../engineering-evaluation.md) §2.7, not here. It will make these WPs easier but is not a prerequisite.
- **Device-only issues** (`[KNOWN-N]` in [`../../regression-testing/03-known-issues-audit.md`](../../regression-testing/03-known-issues-audit.md)) still need a device pass; static analysis cannot close them.

---

## 6. Suggested order

```
WP1  Delete dead code            ← start here, zero risk
WP2  Fix silent failures         ← highest user impact
WP3  Design-token guard tests    ← build the ratchet BEFORE migrating
WP4  UI tests in Android CI
──────────────── Phase A done: safe to refactor ────────────────
WP5  ClickButton                 ← root cause of drift
WP6  One card, one toast
WP7  Adopt AppEmptyState/Shimmer
WP8  Retire legacy sheets + avatars
──────────────── Phase B done: one component per concern ───────
WP9  A11y labels & roles
WP10 A11y gestures, targets, motion
WP11 Converge radii/borders/colours
WP12 Remove blur + document
```

**Rationale:** WP3 before WP5–WP8 is deliberate. Migrating 161 button call sites without a guard test would simply re-scatter the drift. Build the ratchet first, then migrate against it.
