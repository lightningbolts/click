# Click — Regression Testing

**Product:** Click mobile (Kotlin Multiplatform) — Android + iOS  
**Purpose:** Verify that **all** shipped features still work after major UI, platform, crypto, proximity, or map changes.  
**Date:** 2026-07-17  

---

## When to run

| Change type | Minimum bar | Full bar |
|-------------|-------------|----------|
| Small UI / copy | [02-smoke-10min.md](02-smoke-10min.md) | Affected checklist sections |
| Nav / sheets / platform UI | Smoke + §1–2, §6–7 | [01-full-checklist.md](01-full-checklist.md) |
| Proximity / connections / E2EE | Smoke + §5–7, §20 | Full checklist + [03-known-issues-audit.md](03-known-issues-audit.md) |
| Map / beacons / events | Smoke + §10–12 | Full + known issues #4, #5, #9 |
| Android media / calls | Smoke + [04-android-focus.md](04-android-focus.md) | Full + known issues #6, #7, #11 |
| Pre-merge / release | Automated gates (§0) + smoke | Full checklist on **both** platforms |

---

## Document map

| File | Role |
|------|------|
| [01-full-checklist.md](01-full-checklist.md) | Canonical ~200-item manual QA matrix (25 sections) |
| [02-smoke-10min.md](02-smoke-10min.md) | Fast pre-merge sanity (~10 minutes) |
| [03-known-issues-audit.md](03-known-issues-audit.md) | Issue sheet #1–23 with code evidence and status |
| [04-android-focus.md](04-android-focus.md) | Android-only failure matrix (calls, voice, BLE, map) |

**Continuation / what’s next:** [../handoff/functional-clarity-continuation.md](../handoff/functional-clarity-continuation.md) — addressed, still open, Track C revamps.

**Expected UX (not a test plan):** [../ui-ux/mobile/00-INDEX.md](../ui-ux/mobile/00-INDEX.md) — feature blueprints 01–17.

**Legacy stub:** [`../../REGRESSION_CHECKLIST.md`](../../REGRESSION_CHECKLIST.md) redirects here.

---

## Recommended order

1. **Automated gates** — checklist §0 (compile + unit tests)
2. **Smoke** — [02-smoke-10min.md](02-smoke-10min.md) on device/simulator
3. **Full checklist** — [01-full-checklist.md](01-full-checklist.md) for the areas you touched (or all 25 for release)
4. **Known issues** — re-verify items in [03-known-issues-audit.md](03-known-issues-audit.md); do not mark `[KNOWN-N]` rows as pass unless the bug is fixed

---

## Legend

| Tag | Meaning |
|-----|---------|
| `[UI]` | Platform-native interaction check (sheets, buttons, haptics, settle) |
| `[E2EE]` | Crypto / encrypted data path must remain correct |
| `[P]` | Platform-specific path (iOS or Android only) |
| `[KNOWN-N]` | Currently failing or at-risk; see [03-known-issues-audit.md](03-known-issues-audit.md) issue **N** — do not false-pass |

Run on **both iOS and Android** unless a row is tagged `[P]`.

---

## Automated gates (quick reference)

From the `click/` directory:

```bash
./gradlew :composeApp:compileDebugKotlinAndroid
./gradlew :composeApp:compileKotlinIosSimulatorArm64
./gradlew :composeApp:testDebugUnitTest
./gradlew :composeApp:iosSimulatorArm64Test
```

From `click-web/` (API / proximity server contracts):

```bash
npm test
npm run build
```

High-signal unit tests to keep green: `ChatSwipeMathTest`, `ChatViewModelTest`, `ChatTimestampPeekTest`, `ChatInteractionPolicyTest`, `HomeContinuityPolicyTest`, `BeaconCheckInOptimismTest`, `EventAttendeeDirectoryTest`, `ConnectionEncounterMergeTest`, `NominatimSearchParseTest`, `ProximityConnectionChangeTargetsTest`, `DiscoveryFeedSectionsTest`, `OfflineBootTest`, proximity codec/matching tests under `composeApp` and `click-web/__tests__/` (incl. `attendeeDirectory.test.ts`, `eventEngagement.test.ts`, `event-engagement.route.contract.test.ts`).

**Last verification (sheet dismiss + drop IME + date range, 2026-08-10):** Directory/search/profile
on UIKit scroll-host (Column bodies; no surface-drag flicker) · drop-beacon keyboard expands to
large detent + Never content-inset with `sheetImePadding` · End date picker opens with start-only
selection so one tap paints the range · profile media preview clears on tab change · Android+iOS
compile PASS.

**Last verification (production regression sweep follow-up, 2026-08-10):** LazyColumn/pager
sheets (`useUiKitScrollHost=false`) for search / profile / verified-click picker /
availability IME · saved-event host hydration via live map+prefetch+GET · optimistic
beacon/audio/file send animations · Android+iOS compile PASS · high-signal unit tests PASS
(`EnsureFreshAccessTokenTest`, `GeocodingServiceCacheTest`, `BeaconPreviewModelEnrichmentTest`,
`NetworkFailureUtilTest`, `GlobalSearchResultsTest`).

**Last verification (production regression sweep, 2026-08-10):** UIKit scroll-host default for
Column wrap-content sheets (which-pin / view-event path) + global-search IME/`sheetImePadding` + grabber
top inset · shared `EnsureFreshAccessToken` for chat/presence/Realtime/pending sync · soft
`JWT expired` no longer hard-logout · Home reminders rebind on prefetch + engagement ·
`ensureEventBeaconDetail` host-name hydration · chat beacon card enrichment · geocode LRU ·
read-heavy rate limit GET-only (RSVP mutations excluded).
Android `compileDebugKotlinAndroid` + iOS `compileKotlinIosArm64` PASS ·
`testDebugUnitTest` high-signal PASS (`NetworkFailureUtilTest`, `EnsureFreshAccessTokenTest`,
`GeocodingServiceCacheTest`, `BeaconPreviewModelEnrichmentTest`, `ResolveChatBeaconForDetailTest`,
`BeaconCheckInOptimismTest`) · click-web `readHeavyRateLimit` Jest PASS.

**Last §0 run (Events / check-in / presence / BLE timeline, 2026-08-01):** Android `compileDebugKotlinAndroid` PASS · iOS `compileKotlinIosSimulatorArm64` PASS · `testDebugUnitTest` PASS · `iosSimulatorArm64Test` PASS · click-web `npm test` PASS (175) · `npm run build` PASS.

**Last verification (auth refresh storm / web chat+profile / fill-sheet dismiss, 2026-08-10):**
Android `compileDebugKotlinAndroid` + `testDebugUnitTest` PASS (`SessionRefreshCoordinatorTest`,
`MessageCryptoTest`, `GlassSheetGesturePhysicsTest`) · iOS `compileKotlinIosSimulatorArm64` PASS ·
click-web Jest PASS (`supabaseAuth`, `freshAuthHeaders`, `resolveChatForTabsParam`).
`SessionRefreshCoordinator` single-flights JWT refresh across AuthRepository + ApiClient;
chat auth-failure retry no longer cascades restore+second refresh; group unwrap soft-fails
on `e2e:` ciphertext. click-web restores `getSupabaseFromRouteRequest` auth (no cookie-as-JWT),
`getFreshAuthHeaders` + tabs `group_id` resolution + profile group master key. iOS fill sheets
restore `ProvideSheetSurfaceDrag` + `SheetFingerDismissHost` with keyboard block and
same-gesture scroll gate; create-group uses `sheetImePadding`.

**Last verification (chat/map-pin/sheet/home-bookmark regressions, 2026-08-10):** Android
`compileDebugKotlinAndroid` + high-signal `testDebugUnitTest` PASS (`ConnectionMapGeoTest`,
`ChatViewModelTest` group ensure, `CallLayoutPolicyTest`, `ResolveChatBeaconForDetailTest`) ·
iOS `compileKotlinIosSimulatorArm64` (run in §0 gate) · click-web Jest targets PASS
(`connectionMapPinGeo`, `event-bookmarks.route`, `hubMessageCooldown`, `callLayoutPolicy`).
Fixes: origin map pins; form sheets Compose-owned IME (no UIKit scroll-host recreate);
group `ensureChatForGroup`; Home bookmarks auth-ready retry; beacon POST never 500s after
successful insert.

**Last verification (chat-not-found / RSVP / bookmark / Home / sheets, 2026-08-10 follow-up):**
Reject temp/`non-UUID` `chat_id` client+server; message POST falls through to `connection_id`
on stale chat UUID; RSVP DELETE uses admin client; bookmark/check-in pending sets split;
Home requests map discovery prefetch; sheets stay `expandable=true` with Compose scroll;
Hub↔Event clears focus before category swap; event date-range re-applies selection span.

**Last verification (native sheets / event directory, 2026-08-07):** Android
`compileDebugKotlinAndroid` + `testDebugUnitTest` PASS · iOS
`compileKotlinIosSimulatorArm64` PASS · `iosSimulatorArm64Test` blocked because
`xcrun xcodebuild -version` is unavailable in the local Xcode command-line-tools
configuration · click-web `attendeeDirectory` Jest target PASS (13) and `npm run build`
PASS. The unrelated full click-web Jest suite remains red in telemetry skip-reason and
Dashboard `ThemeProvider` tests.

**Last §0 run (UI interaction polish, 2026-07-19):** Android `compileDebugKotlinAndroid` PASS · iOS `compileKotlinIosSimulatorArm64` PASS · `testDebugUnitTest` high-signal subset PASS (`BeaconCheckInOptimismTest`, `HomeContinuityPolicyTest`, `ChatTimestampPeekTest`, `ChatSwipeMathTest`, `ChatInteractionPolicyTest`). Device smoke: chat row press; chat/events overlay continuity; optimistic check-in; reply cancel; long-press without native selection; IME lockstep.

**Last §0 run (Home interactive-back underlay anti-flicker, 2026-07-18):** Android compile PASS · iOS Simulator compile PASS. Device smoke: iOS swipe Map/Settings/Add Click → Home should not remount/flicker Home.

**Last §0 run (Event engagement API, 2026-07-18):** Android `compileDebugKotlinAndroid` PASS · click-web `npm test` PASS (157) · `npm run build` PASS · Supabase migration `event_engagement` applied on project `click`. Device smoke: bookmark survives force-kill; far check-in reverts; venue-scale on create; location-denied snackbar; `/insights/event-engagement` demo.

**Event engagement API handoff:** [../handoff/event-engagement-api.md](../handoff/event-engagement-api.md)
---

## Platforms & builds

- **Android:** physical device preferred for BLE, LiveKit calls, FCM, `MediaRecorder`
- **iOS:** device preferred for CallKit/PushKit, CoreBluetooth, NFC; simulator OK for UI shell
- Use debug builds with logging enabled when investigating `[KNOWN]` items; capture Logcat / Xcode console for crashes (#7, #8)
