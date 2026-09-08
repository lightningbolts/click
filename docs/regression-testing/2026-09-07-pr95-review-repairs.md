# PR #95 review repairs and phase 0–7 verification

Reviewed PR: [#95 — Stabilize mobile auth, lifecycle, motion, and premium flows](https://github.com/lightningbolts/click/pull/95)
Starting head: `59c9d1a5ec16e4ff8c50735e704bf4b32376fa02`
PR base: `514afcdfabb05fe4acdce2444e729b2658c76fb0`
Date: 2026-09-07
Governing review criteria: the user-provided **Click PR Review Spec.pdf**.

These are focused repairs to the reviewed branch. They preserve the approved design,
navigation model, auth policy, encryption protocol, and enabled-motion timings.
The PDF supplies review criteria; its release instructions are not authorization to
merge, deploy, publish a review, or contact other people. No such actions were taken.

## Phase disposition

| Phase | Checks and repairs | Remaining evidence |
|---|---|---|
| 0 — Baseline/reproduction | Confirmed the open PR and exact head/base; compared the reviewed code with its parent behavior and repository requirements. Recorded the original key-aliasing, credential-cooldown, removed-control, and semantics defects. Added deterministic race/component coverage. | The initial local baseline attempt hit host setup errors. A separate untouched-head checkout subsequently passed all 609 existing Android JVM tests with the corrected test environment. This is an automated baseline, not an independently established last-stable device build. Before/after device recordings remain pending. |
| 1 — Auth/session | Scoped refresh failures to the credential used for that flight. A different credential waits for an older flight, then executes its own refresh. Same-credential cooldown and process-wide serialization remain intact. | Live expired-token, offline/reconnect, OAuth, account-switch, and process-death matrix on both platforms. |
| 2 — Navigation/lifecycle/overlays | Rechecked the existing hub overlay owner/disposal and shared navigation guidance. Send-owned key cleanup now covers completion, exception, and cancellation. Photo pointer gestures remain intact while alternate actions are restored. | Interactive back, background/resume, tab-chrome restoration, camera/lightbox dismissal, and push/deep-link routing recordings. No speculative navigation changes were made. |
| 3 — Runtime performance | Audited key and animation lifetime; found unnecessary infinite decorative work under reduced motion. No arbitrary delays, retry changes, or unmeasured performance tuning were added. | Representative Clicks/chat/map/Home traces, frame timing, memory, and thermal behavior. No FPS or latency improvement is claimed. |
| 4 — Motion | Removed infinite transition creation under reduced motion from typing dots, subtitle shimmer, map ring, memory indicator, soundtrack live indicator, and Tap idle halo. Unsupported Tap also avoids its unused halo animation. Shared primitives honor platform ripple policy. | Device toggling of reduced motion, interrupted gestures, VoiceOver/TalkBack, and platform press-feedback screenshots. |
| 5 — Feature regression sweep | Restored the creator-only event guest-list paste control. Added auth/key ownership and photo/chip component regressions; inventoried every spec feature group below. | All authenticated/hardware/backend device journeys in the ledger. Unit tests do not pass these rows. |
| 6 — Premium interaction | Restored photo open/message-action semantics and keyboard opening; exposed chip selection and disabled state, with a 48dp minimum chip target. Preserved existing pointer handling and visual tokens. | Real-device font scaling, contrast, touch target, keyboard, switch access, and full loading/empty/error/offline/success flow sweeps. |
| 7 — Release-candidate audit | Reviewed the focused diff and recorded verification below. The original six review findings have code repairs; the motion audit yielded one additional class of repair. | Fresh external review plus complete iOS/Android release evidence. **Release approval is not granted.** |

## Repairs, root causes, and regression coverage

### Auth refresh failures crossing session boundaries

Reproduction: a burned refresh token fails, then the user establishes a new session
within the old 15-minute cooldown. Previously the coordinator returned the old failure
before considering the new session. It now keys the cached failure and in-flight
identity by a SHA-256 digest of the refresh credential, so the cache does not retain
the credential itself. The credential is neither logged nor persisted by the
coordinator. A differently keyed caller waits for the current flight and retries its
own block; it does not join the old result. Existing cooldown durations are unchanged.

Owners: `SessionRefreshCoordinator.singleFlightRefresh` and `AuthRepository.refreshSession`.
Tests: `newCredentialDoesNotInheritBurnedTokenCooldown`,
`newCredentialWaitsForOldFlightButRunsItsOwnRefresh`, and the existing coordinator suite.

### Hub encryption keys cleared during a suspended upload

Reproduction: begin a hub photo/Drop upload; while suspended, another hub load/send
replaces the cached E2EE session. Cache cleanup zeroed the arrays that the upload's
session still referenced, so its later message body could be encrypted with zero bytes.

Freshly resolved send keys are now owned by the send operation. The read cache receives
independent arrays before they are published. All three send paths use a scope that
clears operation keys in `finally`, including failure and cancellation. Clearing or
replacing the read cache cannot mutate a send's key. Partial keys are also cleared
when the current epoch cannot be resolved. This changes key ownership, not the wire
format, epoch rotation policy, or authorization rules.

Owners: `HubChatViewModelE2eeV2.kt` and the text/photo/Drop send paths in `HubChatViewModel.kt`.
Tests: `HubE2eeV2SessionTest` suspends an upload, clears the independent cache, then
authenticates the media and message with the original recipient key; it also checks
key erasure on failure/cancellation.

### Event creator control

Restored `EventGuestListPasteCard` at its original detail-screen location and with its
creator guard. The supported control was removed from the call site while its
implementation and requirement in `docs/ui-ux/mobile/10-map-beacons-hubs.md` remained.
No guest-list API or visibility policy changed. Creator/non-creator and backend-error
device checks remain required.

### Photo and chip accessibility/platform feedback

The photo's raw pointer detector lacked the semantics previously provided by
`combinedClickable`. Added open-photo and message-action semantics and keyboard
activation while retaining the existing tap/long-press gesture detector and locked
Drop guard. Chips now use selection-aware interaction and retain disabled behavior;
their minimum target is 48dp. List rows, chips, and action icons honor the existing
platform ripple setting while retaining press-scale feedback.

Tests: `StabilizationAccessibilityUiTest` is supplied for Android/Robolectric and
iOS Simulator. Android test dependencies include the Compose UI test harness.

### Reduced-motion work

Several indicators already chose static output values but unconditionally created
`rememberInfiniteTransition`. Their reduced-motion branches now return the same
static values before creating a transition. Normal-motion values, curves, durations,
and layout are unchanged. This is a source-level lifetime repair; power savings and
frame behavior have not been measured on devices.

## Verification obtained for these edits

- `spotlessApply` and `spotlessCheck`: **PASS**.
- `:composeApp:testDebugUnitTest`: **PASS**, 616 tests, 0 failures, 0 skipped.
- Targeted suites: auth coordinator (13 tests), hub key lifetime (2 tests), and
  Android photo/chip accessibility including keyboard activation (3 tests): **PASS**.
- `:composeApp:assembleDebug`: **PASS**; debug APK built locally.
- Untouched reviewed head in a separate checkout: **PASS**, 609 existing Android JVM tests.
- `git diff --check` / staged diff whitespace checks: **PASS**.
- Repository documentation validator applied to the repair record: **PASS**.

Local command used:

```text
gradlew.bat spotlessCheck :composeApp:testDebugUnitTest :composeApp:assembleDebug -Pkotlin.compiler.execution.strategy=in-process --init-script ../verification.init.gradle --max-workers=2 --console=plain
```

The local-only init script redirects the test JVM's home, temporary files, and
Maven cache into writable workspace directories. The two official Robolectric Android
runtime artifacts (13 and 15, instrumentation version i7) were downloaded from Maven
Central, checked against its SHA-512 checksums, and resolved locally in offline mode
because Robolectric's dynamic download-directory creation failed on this host.
`GRADLE_USER_HOME` is also workspace-local. These environment adaptations
are not changes to application behavior or checked-in CI configuration. Early runs
failed on host cache/temporary-directory creation; the final run above uses the
corrected environment. Android crypto contract tests use Robolectric because the
platform identity implementation calls `android.util.Base64`.

The Compose activity manifest is a debug-only dependency, so the UI test host is
available without changing release builds. The equivalent iOS test sources are
included but have not been run on this host.

Existing CI and the September 6 execution records describe earlier commits. They
are historical evidence and are not represented as tests of these new edits.
This Windows host has no Xcode/iOS simulator, and `adb devices` reported no attached
Android device. Consequently no new physical-device or iOS execution is claimed.

## Complete spec feature inventory

Every feature group from section 8 is represented. **Pending** means the complete
device journey has not been executed in this repair session. Source/component
coverage is recorded separately and does not substitute for device evidence.
Performance, motion, premium-flow, and accessibility device gates remain pending
for every shipped group, even where targeted code repairs exist.

| Spec | Feature group | iOS full flow | Android full flow | Targeted repair/evidence |
|---|---|---|---|---|
| 8.1 | App boot, shell, and global navigation | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.2 | Platform-native interaction layer | Pending | Pending | Platform ripple policy; chip targets. |
| 8.3 | Authentication and account | Pending | Pending | Credential-scoped refresh failure regressions. |
| 8.4 | Onboarding and profile gate | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.5 | Add Click hub | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.6 | QR scanner | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.7 | My QR | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.8 | Tap / Tri-Factor handshake | Pending | Pending | Tap idle animation lifetime; hardware verification still required. |
| 8.9 | Deep links and App Clip | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.10 | Connection context/tagging | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.11 | Connection reveal | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.12 | Clicks inbox | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.13 | Connection rows and gestures | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.14 | Chat push/navigation | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.15 | Connection actions and safety | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.16 | Verified clique/group creation and management | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.17 | Community hub rows and hub chat | Pending | Pending | Hub send/cache key ownership. |
| 8.18 | Profiles and profile sheets | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.19 | Core chat messaging | Pending | Pending | Hub text send key ownership; static reduced-motion typing dots. |
| 8.20 | Chat composer and keyboard | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.21 | Chat gestures, search, reply, reactions, message actions | Pending | Pending | Photo semantic open/message actions. |
| 8.22 | Chat media and attachments | Pending | Pending | Suspended hub upload regression; photo semantics. |
| 8.23 | Voice and video calls | N/A | N/A | Calls were removed from shipped mobile, per `AI.md` section 3. |
| 8.24 | Home dashboard | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.25 | Map and discovery | Pending | Pending | Reduced-motion map ring. |
| 8.26 | Beacons | Pending | Pending | Creator guest-list control; static soundtrack live indicator. |
| 8.27 | Community hubs | Pending | Pending | Hub send/cache key ownership. |
| 8.28 | Global search | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.29 | Availability and intents | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.30 | Settings and account preferences | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.31 | Clicktivities and gamification | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.32 | Memories and profile media | Pending | Pending | Reduced-motion memory indicator. |
| 8.33 | Push notifications and background behavior | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.34 | Offline, sync, and connectivity | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.35 | Security and data integrity | Pending | Pending | Operation-scoped encryption key lifetime and cleanup. |
| 8.36 | Permissions and platform services | Pending | Pending | Existing checklist retained; no new full-flow evidence. |
| 8.37 | Accessibility and alternate input | Pending | Pending | Photo/chip component regression coverage; reduced-motion audit. |

## Release follow-up

Run the targeted iOS component tests and the complete affected flows on both platforms,
then execute the remaining canonical checklist with representative accounts and data.
Attach every-tab/nested-screen screenshots required by `AGENTS.md`. Verify creator
guest-list persistence, account switching during refresh, overlapping hub sends and
epoch rotation, locked Drops, touch/keyboard/screen-reader actions, and live changes
to reduced-motion settings. Collect traces before making further performance edits.

The earlier phase records remain historical. This record supersedes their blanket
phase-closure wording for the defects and new edits described here.

## Delivery

The repairs are committed locally in six focused commits: auth, hub key ownership,
event control, accessibility/platform feedback, reduced motion, and verification
documentation. They have not been pushed to GitHub or merged. The accompanying
`PR95-review-repairs.patch` is a `git am` patch series based on the reviewed head
above. Apply it to a checkout at that head with:

```text
git am PR95-review-repairs.patch
```
