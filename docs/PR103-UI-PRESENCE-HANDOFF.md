# PR 103 UI and presence handoff

## Follow-up: remaining media/header regressions

- Fixed UIKit object comparisons in native chrome: `===` compared Kotlin wrappers, causing the real Search carrier to be missed and arranged controls to be removed/reinserted. Native equality now preserves controls through pop and root reconciliation.
- Fixed media host reattachment: UIKit removes sibling constraints when views leave a host. The chrome row is now constrained again on every attachment, using the active controller's safe-area edges, including horizontal insets.
- Title transition snapshots now retain the measured label frame instead of recentering an intrinsic-width label on the screen. Cached root geometry adapts when the covered route is resized.
- Added the shared glass-fade extension to hub content clearance so the tap-to-connect banner starts below the header material.
- Regression tests cover six widths (320, 375/390, 430, 768, 844, 1024 points), resize while the root is covered, repeated host attachment, carrier/menu/callback continuity, and gesture reversal.
- The original right-carrier regression failed before the native-equality fix, and the strengthened reattachment regression failed before the host-constraint fix. Both now pass. Targeted native-header and QR UI tests passed together: `/tmp/click-pr103-resize-final.log`.
- A temporary UIWindow in the earlier regression test caused UIKit scene invalidation to crash the test process during later QR tests. Removed that test-window setup; host geometry is tested directly without creating window scenes.
- Final full validation PASSED: 643 Android tests and 591 iOS simulator tests, zero failures/errors/skips; Android debug assembly, iOS device compilation, Spotless, and `git diff --check` passed. Log: `/tmp/click-pr103-complete-validation.log`. No click-web changes were needed for these mobile layout fixes.
- Changes remain local on PR #103's branch. Physical-device visual acceptance of profile media, slow/reversed swipes, and the hub banner remains to be checked. The five-hour usage reading reached 0% at the final validation check; no further implementation or publishing was performed.

## Checkout

- Work directly in `/Users/timberlake2025/Code/Click Platforms/click`.
- Branch: `fix/ios-media-event-chrome-20260915`, PR https://github.com/lightningbolts/click/pull/103.
- Started with a paused rebase of prior commit `309a0866` onto remote head `870312ae`. Both conflict regions were reconciled; the preserved prior changes are now local commit `6e53e85`. No new checkout was created.
- New changes remain in the working tree. No push or merge performed. Existing untracked `docs/plans/` was preserved.

## Changes

- Trailing icons use persistent image views, matching the leading chevron rather than replacing UIButton configuration images. Immediate semantic changes share the same compressed midpoint for both sides.
- Root menu remains visible during gesture-to-root reconciliation; the reused Search button receives the latest callback. Existing menu and carrier identities are retained.
- Preserved and reconciled compact root-title parallax from the left, using the glyph width and final screen center.
- Preserved the branch's real full-screen UIKit presentation for profile media. Header placement follows that controller's safe area, including controller-specific insets, rather than a detached view or window-only guide.
- Presence subtitles share one implementation across Compose, native chat, and profile media. Stored `last_polled` values combine with live session observations, elapsed labels refresh every minute, and group status excludes the viewer.
- Presence tracks session references so one device leaving or a heartbeat replacement does not incorrectly mark a peer offline. Local teardown does not fabricate a departure time; session observations clear on stop/account replacement.

## Validation

- `rtk proxy ./gradlew spotlessApply :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:compileKotlinIosArm64` passed. Android: 643 tests, 113 suites, zero failures/errors/skips. Log: `/tmp/click-pr103-validation.log`.
- Initial simulator test compilation found a missing `platform.UIKit.additionalSafeAreaInsets` import in the new test; fixed.
- Final iOS run: `rtk proxy ./gradlew spotlessApply :composeApp:iosSimulatorArm64Test :composeApp:compileKotlinIosArm64 spotlessCheck`. Log: `/tmp/click-pr103-ios-tests.log`. At handoff, simulator test compilation passed and `linkDebugTestIosSimulatorArm64` was running (exec session `71198`). Check the final result before calling simulator validation complete.
- Final `git diff --check` passed and the source scan found no conflict markers.
- New regression coverage: multi-device presence, simultaneous join/leave replacement, elapsed-time boundaries, unknown timestamps, native button/menu identity and callback continuity, reversed gestures, and safe-area placement.
- Existing compiler warnings include Skiko dependency mismatch and deprecated/experimental Kotlin APIs. No dependency upgrades were introduced.

## Remaining acceptance

1. Finish simulator validation and fix any failures; rerun `git diff --check` and confirm no conflict markers.
2. Visually check completed and cancelled swipes on all root tabs, especially collapsed Click/Clicks titles entering from the left. Check right ellipsis/search and left chevron/menu through slow reversals, rapid repeated navigation, and Reduce Motion.
3. Open profile media from chat and from other entry points: close/save/share below the status bar, title truncation between controls, correct return to the sheet, rotation and light/dark appearance.
4. With two accounts, check Online → Last seen, elapsed text aging while staying on the screen, group exclusion of self, and two devices for one peer. No live two-account verification has been performed.
5. Review and commit only this task's files if desired. Push only when requested. No hosted CI or physical-device visual acceptance has been claimed. Repository instructions require screenshots/recordings before merging native chrome changes.

User requested stopping implementation at 1% remaining in the five-hour usage window and providing this handoff. Do not redeem a reset credit without explicit permission.

Implementation stopped when the usage reading moved from 2% to 0% between checks. The already-running Gradle validation was left running; no further implementation should be inferred as complete beyond the evidence above.
