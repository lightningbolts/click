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
```

High-signal unit tests to keep green: `ChatSwipeMathTest`, `ChatViewModelTest`, `ConnectionEncounterMergeTest`, `DiscoveryFeedSectionsTest`, `OfflineBootTest`, proximity codec/matching tests under `composeApp` and `click-web/__tests__/`.

**Last §0 run (Track C events polish: pins/swipe/theme/nudge, 2026-07-17):** Android compile PASS · iOS Simulator compile PASS. Device smoke still required on hardware.
---

## Platforms & builds

- **Android:** physical device preferred for BLE, LiveKit calls, FCM, `MediaRecorder`
- **iOS:** device preferred for CallKit/PushKit, CoreBluetooth, NFC; simulator OK for UI shell
- Use debug builds with logging enabled when investigating `[KNOWN]` items; capture Logcat / Xcode console for crashes (#7, #8)
