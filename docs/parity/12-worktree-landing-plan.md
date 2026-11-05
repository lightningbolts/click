# 12: Landing the parity-08 worktrees

**Status:** Plan · **Date:** 2026-10-10 · Index: [09](09-parity-audit-2026-10-10.md)

About half of sheet 08 is written, but **almost none of it is committed**. This sheet lists what each worktree holds, what must be fixed before it merges, and the order in which to merge them. Phase P0 in sheet 09 is this sheet.

## 1. State on 2026-10-10

All branches are cut from `origin/main @ b461f6b4`. Paths are under `wt/`.

| Worktree · branch | Committed | Uncommitted | Rows | Ready? |
|---|---|---|---|---|
| `android-ci` · `parity08/ci` | 5 commits, in integration | — | G0-2, G0-7, G0-8 (pin `.click-web-ref`), G8-4 | Yes. CI green not yet verified (never pushed). |
| `android-devices` · `parity08/devices` | 4 commits, in integration | — | G1-10, G7-1, G7-2, G7-3, G7-5, G7-6 | Yes. Needs a follow-up for the A1/A2/A5 items in sheet 10, and a `DeviceLabel` test. |
| `android-integration` · `parity08/integration` | 08 doc + ci + devices merges | — | — | The merge target |
| `android-connect` · `parity08/connect` | 1 commit (G0-4, G0-12) | 18 modified, 20 new (~2.7k lines) | G0-15, G4-1…G4-5, G4-7, G4-8, G4-10…G4-12 | After fixes (§2) |
| `android-chat-e2ee` · `parity08/chat-e2ee` | — | 16 modified, 6 new | G0-3, G0-13, G0-14, G3-27, G6-17 | Yes, after commit |
| `android-foundations` · `parity08/foundations` | — | 21 modified, ~25 new | G0-11, G1-1, G1-2, G1-3 (partial), G1-4, G1-5 (infra only), G1-6, G1-9 | After fixes (§2) |
| `android-drops` · `parity08/drops` | — | ~29 modified, 21 new (~3.9k lines) | G0-5, G1-7, G2-1…G2-9, G2-4+ | After fixes (§2) |
| `android-events` · `parity08/events` | — | 8 modified, 13 new (~1.9k lines) | G3-15, G3-16, G3-19, G3-29 done; G3-20 and G3-22 partial; G3-17 stub | After fixes (§2) |
| `android-social` · `parity08/social` | — | — | none started | Delete or reuse for P6 |

> ⚠️ **Do this first, today:** commit every worktree's working tree as a WIP commit on its own branch and push the branches. Nothing else in this plan matters if a `git clean`, a disk failure or a stray `git checkout -- .` loses ~10k lines of uncommitted work.

## 2. Fixes before each merge

| Worktree | Fix | Why |
|---|---|---|
| connect | Move `composeApp/src/commonMain/commonTest/.../ConnectionFlowAllowlistTest.kt` to `composeApp/src/commonTest/...` | It's outside any source set, so it never compiles or runs (G4-8) |
| connect | Add tests for G4-1 (warm capture lifecycle) and G4-10 (API path; the kill switch `connect_postgrest_fallback` falls back) | The definition of done requires them |
| foundations | Route the new `PushRoute`s to real screens: `DeviceApproval` → `AppRoute.DeviceApproval` (devices), `SharedDrop` → the story (drops), `EventRecap` → the recap (after D3), `Activity` → stay on "open app" until G6-1 | G1-3. Today they all fall back to `OpenApp`. |
| foundations | Sound: `res/raw/click.wav` → `click.ogg` (or document why `.wav`) | 08 G1-4 |
| drops | Replace `TODO(G1-1)` in `ClickDropDevelop.kt:25` with `FeatureFlags.config("shared_drops").develop_hours` once foundations is merged | G2-3 |
| drops | Update `collaboration/README.md` (it still describes the removed `ClickDropReveal`) | Doc drift |
| chat-e2ee | Update `chat/README.md` (it still describes `TypingBroadcastPayload`) | Doc drift |
| events | Add tests: QR matrix, `PassScanResult` decode (including the T7 values), calendar link builders (Google and Outlook URL encoding, UTC `dates=`), scanner re-read debounce (3 s), pass polling window | It has no tests at all |
| events | Move `EventsDiskStore` / `ClickPassStore` onto foundations' `UserDiskCache` (D17), or at least wipe them in `AppSessionHooks.onSessionEnded` | Two cache layers; data could survive sign-out |
| events | Leave the G3-17 stub out of the merge, or keep it behind the flag | It's a 20-line stub |
| all | `core/FeatureFlagGate.kt` exists in three versions (connect and foundations: 3 lines; drops: 9 lines with KDoc). Keep **foundations'** version, which is wired to the real store. | Add/add conflict |

## 3. Merge order (into `parity08/integration`, then one PR to `main`)

Each step: merge, resolve, `./gradlew :composeApp:testDebugUnitTest assembleDebug`, then commit.

| Step | Merge | Main conflicts to expect | Resolution |
|---|---|---|---|
| 0 | (done) ci, devices | — | — |
| 1 | **foundations** | `ApiClient.kt` (platform header), `AppDataManager.kt`, `SettingsScreen.kt` (vs devices) | Keep both. The `X-Click-Platform: android` header goes on every Ktor client. |
| 2 | **chat-e2ee** | `ChatRepository.kt`, `SupabaseChatRepository.kt`, `HubChatViewModel.kt`, `ChatApiClient.kt` (vs devices) | Additive. Keep both sets of interface methods. |
| 3 | **connect** | `ApiClient.kt`, `ConnectionRepository.kt`, `ConnectionFlowTelemetry.kt`, `AppDataManager.kt`, `AppMainShell.kt`, `FeatureFlagGate.kt` | Take foundations' `FeatureFlagGate`. Keep connect's larger edits in the repository and telemetry, and re-apply foundations' header install. |
| 4 | **drops** | **`PushRoutes.kt`** (both add `PushRoute.SharedDrop` with different shapes), **`ClickFirebaseMessagingService.kt`**, **`MainActivity.kt`**, `SettingsScreen.kt`, `ChatApiClientModels.kt`, `ChatRepository.kt`, `HomeScreen.kt`, `FeatureFlagGate.kt` | Take foundations' `SharedDrop(dropId, posterId, dropCount)` and repoint drops' call sites. In the FCM `when (route)` block, take foundations' channel constants plus drops' deep-link intent. Put drops' release toggle into devices' rewritten Settings. |
| 5 | **events** | `AppMainShell.kt` (overlay host block), `AppDeepLinkRouter.kt`, `AndroidManifest.xml` (`click://pass`), `EventBeaconDetail.kt` | In the manifest, also delete the phantom `InstantProfileActivity` (sheet 11 B8) and make MainActivity the only `/c` owner (G0-6). |
| 6 | **PR `parity08/integration` → `main`** | — | Run the full `testDebugUnitTest` once. Run the 08 G8-1 rows for G0-3, G0-13/14, G0-5, G0-12/15 and G4-10 on devices before merging. |

**Recommended PR shape:** one PR per step, opened against `main` in this order, rather than one giant integration PR, so each can be reviewed and reverted on its own. Only fall back to the single integration PR if reviews are the bottleneck.

## 4. Rules for the rest of the program

1. **No uncommitted work at the end of a session.** Commit WIP and push the branch. This also covers agents: add it to `wt/BRIEF.md` under "Git" (today it says "Do NOT push"; change that to "push your branch; do not open PRs").
2. **Fix `wt/BRIEF.md`:**
   - It says sheet 08 is in the main checkout, but it lives only on `parity08/integration`. Merge the docs (08–12) to `main` first, so every agent reads the same sheet.
   - Point it at sheets 09–12.
3. **One owner per shared file per week.** `PushRoutes.kt`, `ClickFirebaseMessagingService.kt`, `AppMainShell.kt`, `ApiClientModels.kt`, `SettingsScreen.kt` and `FeatureFlags.kt` caused most of the conflicts above. Assign an owner in the weekly plan. Other branches add hooks (a new file plus a one-line registration) rather than editing those files inline.
4. **Delete stale branches** (08 G0-1): `chore/remove-ios`, `feat/android-parity-phase-*`, `fix/e2ee-history-and-chat-stability` and `refactor/split-large-files`, after `git branch --merged origin/main` confirms them.
5. **Remove `android-social`** or reassign it to P6 with a fresh branch.

## 5. Acceptance for Phase P0

- [ ] Every worktree's work is on a pushed branch.
- [ ] Steps 1–6 merged to `main`; CI is green against the pinned click-web SHA.
- [ ] The §2 fixes are done, including the events tests and the moved connect test.
- [ ] Device matrix: typing in 3 directions; a shared-device rotation; an iOS gated drop develops on Android; a 2 iOS + 1 Android group tap gives one group with tags on every pair; a QR connect makes no PostgREST insert.
- [ ] The README scoreboard has rows for sheets 08–12.
