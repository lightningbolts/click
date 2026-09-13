# Stabilization Phases 1–2 — execution record

Scope: mobile auth/session and navigation/lifecycle correctness only. Starting HEAD:
`40d93dfaf74e7bc300eeff2beeca9e9ad1a17ae9`. The checkout already contained substantial
uncommitted changes; those are preserved and are not attributed to this work.
The requested `GPT6_STABILIZATION_AND_PREMIUM_FLOW.md` was supplied as an attachment,
not a repository file. This record follows that attachment's Phase 1–2 scope.

## Context and historical interpretation

Reviewed the canonical regression checklist/known-issues audit, UI audit plan,
mobile index/design-system/shell specifications, motion/shell/chat/consistency
polish specifications, and the relevant package and implementation owners.
Older references to calls and opaque iOS tab chrome are superseded by `AI.md`,
`AGENTS.md`, and later native-chrome decisions; no removed features were restored.

No commit is claimed to be a **verified stable pre-redesign baseline**. Relevant
history: `43a8a769` introduced motion/Home continuity work; `39bc0ed8` split the
shell while preserving behavior; `bdf9392e`, `d2a96f84`, `63e5753a`, and `8d012acd`
changed Map/native-chrome hiding, clipping, and settle behavior intentionally.
The route-history defect traces back to `ca3d45ef` and was carried through the
shell split; it is a pre-existing correctness bug, not a newly proven redesign
regression. Home remount and Map-alpha observations remain runtime hypotheses.
No dead-code deletion or visual/architectural cleanup was authorized by those
hypotheses.

## Actual auth ownership and required transitions

| Concern | Actual owner / contract |
| --- | --- |
| Live access/refresh tokens, expiry, current SDK user | Supabase Auth (`auth-kt` 3.0.2), backed by secure SettingsSessionManager |
| Persisted mirror and offline identity | `TokenStorage`, `LocalSessionCache`, `SupabaseSettingsSessionReader` |
| UI admission and terminal failure | `AuthViewModel`, observed by `App`/`AppAuthGate` |
| Refresh in flight / cooldown | `SessionRefreshCoordinator`; network entry via `AuthRepository.refreshSession` |
| Foreground recovery | `SupabaseForegroundRecovery` and `SessionResumeGate` |
| Logout | `AuthViewModel.signOut` clears app data; `AuthRepository.signOut` owns SDK/token cleanup |

The application still has dual stores and separate UI admission state. These
repairs do not claim to replace that architecture with one new state machine.

| Transition | Actual behavior / required invariant |
| --- | --- |
| Loading → authenticated | Offline fast path admits a parsed local identity with a refresh token, then refreshes in the background; network is not required for admission |
| Loading → unauthenticated | No restorable session leads to Idle/sign-in |
| Authenticated → refreshing → authenticated | Shared refresh updates SDK and mirror; transient refresh must not flash sign-in |
| Expired access + valid refresh | Refresh obtains usable credentials; offline admission does not authorize sending expired access tokens |
| Definitive refresh failure → unauthenticated | Hard-auth failure path requests re-login; cancellation is not a terminal/network failure |
| Offline persisted session → admitted offline | Existing offline policy is preserved, including expired access with a refresh token |
| Foreground → validate/refresh | Existing foreground/resume gate remains responsible; full rapid-resume behavior needs device coverage |
| Logout → cleanup → unauthenticated | Local SDK and mirror must clear even if remote logout fails; remote error remains distinguishable |

Pinned SDK inspection showed remote sign-out can throw before local `clearSession`.
It also showed `refreshCurrentSession` rechecks for a session after its network
response; a broad refresh-resurrection claim was rejected rather than used to
justify a new session-generation subsystem.

## Phase 2 ownership

| Surface | Owner and preservation contract |
| --- | --- |
| Primary tabs / tab re-tap / history | `AppMainShell`; repeated current destination does not push; back pops to immediate previous route; iOS primary-back resets Home |
| Tab composition / saved scroll | `AppPrimaryTabsHost`, `AnimatedContent`, `SaveableStateHolder`, movable Home content |
| Interactive back | `InteractiveSwipeBackContainer`; back callback follows settle; drag/cancel/commit visual acceptance remains a device gate |
| Connection chat / Settings subpages / Map events | Nested screen owners preserve their local dismissal contracts |
| QR / My QR / NFC | Shell flags and `AppPrimaryTabsHost`; external connection handoff must close NFC before showing QR context |
| Hub chat | `AppHubChatHost` and shell hub arguments; existing teardown/settle ownership retained |
| Profiles / beacon sheets / camera / connection reveal | Existing sheet and `AppConnectionOverlays` owners; no blanket dismissal of camera work |
| Search / dialogs / toasts / tether | `AppChromeOverlays` and shared overlay hosts; z-order untouched |
| Deep links / push / App Clip | Platform routers retain pending intent through gates; shell consumes after admission. Chat/event destinations remain pending behind existing modals |
| Calls | Removed from mobile per `AI.md`; N/A, not restored from stale documentation |

## Defects addressed and evidence

| Priority | Defect | Repair / evidence |
| --- | --- | --- |
| P1 | Cancelled refresh poisons network-failure cooldown | Noncancellable shared-state completion, cancellation propagation, no cancellation cooldown. New tests failed twice against original coordinator before repair |
| P1 | Fresh stored token can bypass newer live credentials | Session-selection repair and focused token-selection tests; existing hydration policy must remain authoritative for legitimate newer stored rotation |
| P1 | Failed remote logout leaves SDK locally authenticated | Unconditional local cleanup with preserved failure result; focused cleanup tests |
| P1 | Android back skips immediate previous tab | Production `RouteHistory` appends destinations; tests cover repeated tab, sequential back, and Home reset |
| P1 | Connection deep link during NFC shows blank content | Close NFC alongside existing QR/MyQR closures before `QrAwaitingContext`; authenticated device reproduction still pending |

## Verification ledger

Baseline Android and iOS Kotlin compilation passed. Fresh iOS Xcode simulator
build, non-wiping install, and launch passed on iPhone 17 Pro / iOS 26.0.
The existing five focused policy classes passed 27 tests before repairs.
The cancellation regression class had 10 tests / 2 failures before repair and
10 / 0 after its first repair. Route-history tests passed 3 / 0.
State-preserving Maestro sign-in → Create Account → sign-in assertions passed;
no credentials, account creation, or permission grants were submitted.

Final policy verification: **45 distinct tests passed on Android/JVM and 45 on
iOS Simulator**, zero failures/errors/skips. The final JVM run repeated only
the two classes changed after the preceding eight-class pass (20 tests);
the other six classes remained unchanged. The iOS run exercised all eight
selectors together. No global unit suite was substituted for this scope.

| Selector | Tests |
| --- | ---: |
| `SessionRefreshCoordinatorTest` | 11 |
| `EnsureFreshAccessTokenTest` | 9 |
| `AuthRepositorySignOutTest` | 4 |
| `SessionHydrationPolicyTest` | 5 |
| `OfflineBootTest` | 4 |
| `RouteHistoryTest` | 3 |
| `HomeContinuityPolicyTest` | 2 |
| `OverlayExclusiveBindPolicyTest` | 7 |

Reproducible focused invocation (substitute `testDebugUnitTest` for the JVM):

```sh
rtk proxy env DEVELOPER_DIR=/Applications/Xcode-beta.app/Contents/Developer ./gradlew \
  :composeApp:iosSimulatorArm64Test \
  --tests '*SessionRefreshCoordinatorTest' --tests '*EnsureFreshAccessTokenTest' \
  --tests '*AuthRepositorySignOutTest' --tests '*SessionHydrationPolicyTest' \
  --tests '*OfflineBootTest' --tests '*RouteHistoryTest' \
  --tests '*HomeContinuityPolicyTest' --tests '*OverlayExclusiveBindPolicyTest' \
  --console=plain
```

Run-local evidence: `/private/tmp/click-auth-cancellation-red.log`,
`/private/tmp/click-stabilization-final-jvm-ios-compile.log`,
`/private/tmp/click-final-auth-jvm.log`, `/private/tmp/click-final-ios-tests.log`,
and `composeApp/build/test-results/{testDebugUnitTest,iosSimulatorArm64Test}`.
The rejected-import race and cancellation-continuation issues found during
adversarial review were repaired; their regression tests pass. The coordinator
has no new global test hook. The NFC handoff remains source-reviewed and
compiled, without an authenticated UI reproduction.

Final app packaging also passed: `:composeApp:assembleDebug` (24 seconds) and
Xcode `iosApp` Debug for the booted iPhone 17 Pro simulator. The final iOS build
was installed without clearing data, explicitly launched, and passed the
repository login/signup toggle assertions with only its destructive launch/
permission setup omitted. Logs: `/private/tmp/click-final-android-build.log`,
`/private/tmp/click-final-ios-build.log`, and
`/private/tmp/click-final-smoke-result.log`. Initial smoke attempts with an
incorrect temporary assertion or the app not foregrounded were not counted
as app regressions; the corrected final flow passed. No test account was used.

| Required matrix area | Evidence status |
| --- | --- |
| Signed-out launch; sign-in/sign-up navigation | iOS simulator smoke passed; no Android device connected |
| Email/Google login, invalid credentials, sign-up submission, forgot/reset password | NOT RUN: no test credentials; no remote account mutation |
| Cold authenticated launch, process death/relaunch, offline admission/restoration | Unit policy coverage only; authenticated device matrix NOT RUN |
| Valid/invalid refresh, temporary offline refresh, concurrent requests | Coordinator/token policy tests only; live service matrix NOT RUN |
| Logout during request/refresh, rapid foreground/background | Focused local cleanup/cancellation evidence only; device lifecycle matrix NOT RUN |
| Deep link/push during auth, onboarding after refresh, no auth/shell flash | Source ownership review; authenticated device acceptance NOT RUN |
| Android back, iOS drag at 49% cancel/commit, nested overlays, App Clip handoff | Route unit coverage and source review; full device acceptance NOT RUN |
| Measurable performance regression | No authenticated frame trace or before/after timing obtained; no performance improvement claimed |

Phases 3–7, broad redesign, motion tuning, E2EE, database/backend changes, and
unrelated working-tree edits remain outside this work. This is **not release
approval or a claim that the complete Phase 1–2 device matrix passes**.
