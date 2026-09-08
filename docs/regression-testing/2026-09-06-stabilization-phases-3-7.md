# Click Mobile Stabilization — Phases 3–7 Execution Record

**Status:** automated stabilization complete; full device release matrix pending  
**Date:** 2026-09-06  
**Baseline commit:** `0eb29cc4a9d69c6513aa8b14622a3fe4b12b4ed4`  
**Scope:** KMP mobile app (`composeApp`) on iOS and Android  
**Predecessor:** `docs/regression-testing/2026-09-05-stabilization-phases-1-2.md`

This document completes the execution record for Phases 3–7 of the
stabilization plan. It records only evidence actually obtained. A passing
compile, unit test, or simulator smoke flow is not treated as proof that an
authenticated or hardware-dependent device journey passes.


**September 7 follow-up:** [PR #95 review repairs and current phase gates](2026-09-07-pr95-review-repairs.md). The results below are historical evidence for the earlier branch state.

## 3. Phase status

| Phase | Status | Evidence and disposition |
|---|---|---|
| 0 — Baseline | Complete for automated gates | Baseline SHA recorded; clean-`HEAD` parity compilation and the current-tree build/test gates pass. The comprehensive device-video baseline remains a release activity. |
| 1 — Auth/session | Complete | See the predecessor execution record. Session coordination, stale-token behavior, auth-gate continuity, and deterministic hub-token injection are covered by automated tests. |
| 2 — Navigation/lifecycle/overlays | Complete | See the predecessor execution record. Overlay ownership, interactive-back underlay stability, native tab-bar covering, and iOS media-chrome binding were repaired without changing the navigation model. |
| 3 — Runtime performance | Complete for evidenced code defects | Static ownership and recomposition review found and repaired repeated iOS media-chrome rebinding. No synthetic performance claim is made for unprofiled device journeys. |
| 4 — Motion system | Complete for the affected regression surface | Shared motion roles remain centralized in `MotionTokens`; hub verification now removes infinite decorative animation under reduced motion. |
| 5 — Feature regression sweep | Automated portion complete | KMP common, Android unit, iOS simulator, UI-component, formatting, packaging, and native iOS build gates pass. Authenticated, hardware, push, and real-device rows remain open below. |
| 6 — Premium interaction pass | Complete for evidenced defects | Cached availability survives transient auth/network failure; failure is no longer presented as valid empty data; overlay and verification behavior no longer perform avoidable work. |
| 7 — Release-candidate audit | Complete for source and automated gates | No P0 was found. The fresh review found and the branch repaired three P1s: hub overlay lifetime, logout retention of v2 epoch keys, and unsafe iOS Keychain fallback. Release approval remains blocked on the explicit device matrix. |

## 4. Trustworthy baseline and verification

The original branch was compared with a detached worktree at the baseline
commit. The baseline and edited tree both compile, preventing pre-existing
compiler failures from being attributed to the stabilization changes.

Current-tree gates:

- `spotlessCheck` — PASS
- `compileKotlinAndroid` — PASS
- `commonTest` — PASS
- `testDebugUnitTest` — PASS
- `iosSimulatorArm64Test` — PASS
- `linkDebugFrameworkIosSimulatorArm64` — PASS
- `assembleDebug` — PASS
- Android Maestro smoke suite — PASS on the available emulator
- Xcode `iosApp` Debug simulator build — PASS

The iOS Maestro smoke attempt was not counted as a product failure: Maestro
did not recognize the booted simulator requested by the run. Native Xcode
build success is recorded independently. The iOS smoke suite must be rerun
after the runner/device connection is restored.

Still required before release:

1. authenticated test accounts with representative 1:1, group, hub, map,
   media, availability, and offline data;
2. physical iOS and Android devices for camera, microphone, notification,
   BLE, NFC, location, lifecycle, and thermal/frame-behavior checks;
3. cold/warm/process-death, offline/online, token-expiry, deep-link, and push
   recordings;
4. before/after traces for any journey that still exhibits visible latency;
5. a recorded PASS, FAIL, or justified N/A for every applicable row in the
   canonical checklist.

## 5. Auth/session/JWT closure

Phase 1 remains closed with the following invariants:

- auth truth is owned below the UI;
- one coordinated refresh operation serves concurrent consumers;
- transient refresh/network failure is not definitive logout;
- stale access tokens cannot silently produce a valid-looking empty state;
- persisted admissible sessions can retain offline continuity;
- hub operations obtain a fresh JWT through an injectable provider;
- tests do not depend on mutable global SDK auth state;
- logout and terminal refresh failure cannot leave the shell in an
  intermediate authenticated state.

No token lifetime, refresh semantics, 401 handling, E2EE policy, or offline
admission rule was weakened.

The remaining OAuth, reset-password, process-death, simultaneous-expiry,
offline-admission, deep-link-during-auth, and push-during-auth cases are
device/backend verification rows. They are not implicitly passed by unit
coverage.

## 6. Navigation, lifecycle, and overlay closure

Phase 2 remains closed with these repaired invariants:

- media lightboxes request native tab-bar cover only on iOS;
- each acquired native cover has one corresponding release;
- iOS media chrome is bound from stable remembered values rather than
  rebound by every parent recomposition;
- removal of the repeated bind side effect does not alter the shared
  navigation model;
- interactive-back underlays remain owned by the route stack rather than
  being remounted for animation;
- reduced-motion behavior does not create the hub verification infinite
  transition;
- dismissal and native chrome restoration remain deterministic.

Hardware camera, push/deep-link routing, App Clip handoff, and
process-background overlay behavior still require device execution. Voice and
video calls are N/A because they are not shipped on mobile (`AI.md` §3).

## 7. Runtime performance investigation

### Proven category and repair

The proven performance category was **unstable native/Compose
synchronization** in iOS media chrome. Parent recomposition could repeatedly
rebind navigation-bar media content because lambdas and wrapper objects did
not have stable identity. The repair remembers stable trailing/media chrome
values and removes the unconditional rebinding side effect.

Expected observable result:

- unrelated parent recomposition does not recreate native media chrome;
- opening or closing the lightbox still updates the native bar once;
- native tab-bar cover remains paired to overlay lifetime;
- Android performs no irrelevant native tab-bar-cover work.

### Performance claims intentionally not made

No source-only review can certify frame pacing, image decode cost, map marker
update cost, chat crypto latency, BLE state latency, keyboard synchronization,
or thermal behavior. The following remain device-profile requirements:

- cold start and primary-tab switching;
- Clicks fling, chat open/back, long history, pagination, send/receive, and
  keyboard-open updates;
- map initial load, pan/zoom, pin updates, and discovery sheets;
- media decode/upload/download and audio playback;
- QR camera startup and Tap/BLE/NFC state transitions;
- offline-to-online refresh fan-out.

For any observed lag, collect a trace and assign one dominant root-cause
category before editing. Do not change durations or add debounce/delay to
mask work.

## 8. Feature-flow matrix disposition

`docs/regression-testing/01-full-checklist.md` remains the canonical detailed
matrix. The approved product intent is a **linear-only Home feed**; the
removed Home photo-pile/layout toggle is not a release requirement. The
checklist and mobile design-system wording now reflect that decision.

The complete §8 inventory from the stabilization plan remains mandatory. Use
the ledger in §16 to summarize execution; attach individual recordings,
traces, logs, or test reports to the corresponding checklist rows. A broad
simulator launch cannot replace authenticated, hardware, offline, push, or
accessibility validation.

## 9. Motion architecture closure

The affected motion surface follows these invariants:

- canonical duration/easing roles stay in `MotionTokens`;
- touch acknowledgement is not delayed by backend completion;
- gesture-driven navigation remains interruptible;
- initial or refreshed data is not animated as a series of new insertions;
- repeated composition does not restart native chrome work;
- infinite hub-verification motion is absent when reduced motion is enabled;
- platform-specific native feedback remains platform-specific.

No visual language, navigation transition family, spring behavior, or global
duration was redesigned during stabilization.

## 10. Loading, empty, error, and success closure

Availability refresh now distinguishes:

- successful non-empty data;
- successful empty data;
- failed initial resolution;
- failed refresh with usable cached data.

On a transient token or repository failure, previously resolved availability
intents remain visible and a failure can be surfaced without converting
cached content to an empty success. Cancellation remains cancellation. The
same principle governs the remaining device sweep:

> preserve usable content, scope refresh/error state to the failed operation,
> and never represent backend failure as “no results.”

No inline fixture data, silent fallback dataset, or swallowed repository
exception was introduced.

## 11. Premium-flow closure

The completed premium repairs solve concrete user-perceived deficiencies:

- **Availability continuity:** valid content no longer disappears during a
  transient refresh/auth failure.
- **Overlay continuity:** media presentation no longer causes irrelevant
  Android chrome work or repeated iOS native-bar binding.
- **Reduced-motion quality:** hub verification avoids infinite decorative
  pulsing rather than merely reducing its amplitude.
- **E2EE continuity on iOS:** process-lifetime identity retention prevents a
  temporary Keychain persistence failure from regenerating identity on every
  subsequent access in the same process.
- **Deterministic hub actions:** fresh-token acquisition is explicit and
  testable instead of coupled to mutable global SDK state.
- **Hub overlay isolation:** closing a hub chat clears its overlay-scoped
  `ViewModelStore`, which tears down realtime presence and prevents state from
  crossing overlay or account lifetimes.
- **Logout key isolation:** cached E2EE v2 epoch keys are zero-filled and
  removed with the legacy session caches; hub-owned epoch keys are zero-filled
  on replacement, failure, and overlay teardown.
- **iOS identity integrity:** a new E2EE identity is created only when Keychain
  reports item-not-found. Other read failures fail closed, and an identity is
  never cached or used if persistence fails.

These changes preserve the approved design. They do not add decoration,
routes, confirmations, loading screens, retries, or artificial delay.

## 12. Session structure used

The work was separated into evidence gathering, auth/session repair,
navigation/overlay repair, focused regression additions, automated
verification, and release-ledger documentation. Future work should continue
with fresh contexts for device profiling, chat, map, proximity/hardware,
accessibility, and final external review.

## 13. Reusable prompts

The root-cause, proven-repair, performance, premium-flow, and external-review
prompts in the governing stabilization plan remain current. Device follow-up
must name one journey and its evidence; prompts such as “make the app faster”
or “polish everything” are not actionable acceptance criteria.

## 14. Commit and PR discipline

Split the current work by root cause before merging:

1. auth/hub JWT isolation and availability failure semantics;
2. overlay/native-chrome lifecycle repair;
3. reduced-motion and iOS identity retention;
4. regression coverage;
5. documentation/product-intent alignment.

Each PR must state reproduction, invariant, minimality, automated evidence,
device evidence, and residual risk. Do not combine unrelated visual changes
or broad cleanup with these repairs.

## 15. Release gates

Automated source gates are green. Release gates are **not fully green** until:

- iOS and Android physical-device matrices pass;
- auth/session backend cases pass with controlled expired/invalid tokens;
- deep links and push route once through auth/onboarding;
- QR, BLE, NFC, camera, microphone, location, and notification cases pass;
- authenticated chat/group/hub ordering, deduplication, media, and offline
  behavior pass;
- map interaction and marker stability are profiled with representative data;
- VoiceOver, TalkBack, font scaling, gesture alternatives, touch targets,
  color-independent status, and reduced motion pass;
- every shipped critical ledger row has non-unknown iOS and Android status.

No known P0 was found in the audited changes. This statement is not a waiver
for unexecuted release gates.

## 16. Final feature coverage ledger

Legend: **PASS** = directly executed evidence; **AUTO** = automated
unit/component coverage only; **BUILD** = platform build only; **PENDING** =
required device/backend execution; **N/A** = not shipped or reachable, with
reason required.

| Flow | iOS | Android | Correctness | Perf | Motion | Premium | A11y | Evidence / notes |
|---|---|---|---|---|---|---|---|---|
| Boot/auth | BUILD | PASS smoke | AUTO | PENDING | AUTO | AUTO | PENDING | Session/auth tests pass; full login/OAuth/expiry/offline device matrix pending. |
| Onboarding | BUILD | PASS smoke | AUTO | PENDING | AUTO | AUTO | PENDING | Component tests/build; persistence and permission handoff pending. |
| Home | BUILD | PASS smoke | AUTO | PENDING | AUTO | AUTO | PENDING | Linear-only product intent confirmed; authenticated section stability pending. |
| Add Click | BUILD | PASS smoke | AUTO | PENDING | AUTO | AUTO | PENDING | Shell smoke passes; nested methods require device execution. |
| QR scanner | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Camera and repeated-scan lifecycle require physical devices. |
| My QR | BUILD | BUILD | AUTO | PENDING | AUTO | AUTO | PENDING | QR component tests pass; share-sheet device behavior pending. |
| Tap/Tri-Factor | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Proximity math/state tests pass; BLE/NFC/audio/location hardware pending. |
| Context tagging | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Repository/ViewModel coverage; complete save/abandon journey pending. |
| Connection reveal | BUILD | BUILD | AUTO | PENDING | AUTO | AUTO | PENDING | Reveal logic covered; haptic/reduced-motion device journey pending. |
| Clicks inbox | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Dedupe/key tests pass; realtime list and fling profiling pending. |
| Connection actions | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Destructive and server-error device cases pending. |
| Verified cliques | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Duplicate-key and realtime multi-account journey pending. |
| 1:1 chat | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | State/order/gesture tests pass; authenticated E2EE device thread pending. |
| Group chat | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Automated model coverage; realtime multi-member device flow pending. |
| Hub chat | BUILD | BUILD | AUTO | PENDING | AUTO | AUTO | PENDING | JWT injection is deterministic; overlay-scoped teardown and key zeroization are enforced; backend device flow pending. |
| Chat media | BUILD | BUILD | AUTO | PENDING | AUTO | AUTO | PENDING | Media component tests pass; upload/decode/encryption/audio profiling pending. |
| Calls | N/A | N/A | N/A | N/A | N/A | N/A | N/A | Voice/video calls are not shipped on mobile (`AI.md` §3). |
| Profiles | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Component/model tests pass; async enrichment and detents pending. |
| Map | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Geo/dedupe tests pass; representative pin/update profiling pending. |
| Beacons | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Creation/model tests pass; map/discovery synchronization pending. |
| Community hubs | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Access/lifecycle tests pass; create/join/map/chat synchronization pending. |
| Global search | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Result-state tests pass; stale request and route restoration pending. |
| Availability/intents | BUILD | BUILD | PASS automated | PENDING | AUTO | PASS automated | PENDING | Failed refresh preserves cached data; realtime/offline device flow pending. |
| Settings | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Full row/subscreen and rollback matrix pending. |
| Clicktivities | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Shipped reachability and device state sweep pending. |
| Memories/media | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Model/component coverage; heavy-media responsiveness pending. |
| Push/background | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Requires signed services and physical-device lifecycle execution. |
| Offline/sync | BUILD | BUILD | AUTO | PENDING | AUTO | AUTO | PENDING | Queue/session coverage exists; end-to-end reconnect/conflict flow pending. |
| Permissions | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Queue logic covered; denied/revoked/Settings-return device matrix pending. |
| Deep links/App Clip | BUILD | BUILD | AUTO | PENDING | AUTO | PENDING | PENDING | Routing/build coverage; cold/warm/auth/App Clip handoff pending. |

The ledger deliberately contains pending cells. Stabilization must not be
declared release-complete until each shipped critical row is executed.

## 17. Definition of premium applied

For this branch, premium means immediate intent acknowledgement, deterministic
state, preserved navigation continuity, stable lists/maps, restrained and
interruptible motion, explicit recoverable errors, designed offline behavior,
native platform semantics, and first-class accessibility. It does not mean
more animation.

## 18. Remaining execution order

1. Restore iOS simulator-runner connectivity and rerun Maestro smoke.
2. Provision representative authenticated test accounts and backend data.
3. Execute auth/session expiry, offline, process-death, deep-link, and push
   cases.
4. Profile Clicks/chat, Home/tab switching, and Map on physical devices.
5. Execute QR/Tap/BLE/NFC and permission matrices on supported hardware.
6. Run the full §8 journey sweep on iOS and Android.
7. Run VoiceOver, TalkBack, font-scaling, alternate-action, and reduced-motion
   sweeps.
8. Perform a fresh-context external release-candidate review.
9. Fix only evidence-backed findings and rerun their complete affected flows.
10. Replace every PENDING ledger cell with PASS, FAIL, or justified N/A.

## 19. Governing instruction

> Do not optimize Click for how impressive the diff looks. Optimize for how
> little the user notices the implementation. Preserve the approved product
> and visual system, prove root causes before changing architecture, and
> treat every shipped user flow as part of the regression surface.

