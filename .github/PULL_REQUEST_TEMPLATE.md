## Summary

<!-- What changed and why. -->

## Visual proof (required — do not merge without this)

Attach a screen recording **or** screenshots covering **every tab and its nested screens**, showing real content (not empty black bodies). CI green is not sufficient.

- [ ] iOS — Home (pile or list with visible cards)
- [ ] iOS — Add Click (+ My QR or Tap if opened)
- [ ] iOS — Clicks, including an open chat if reachable
- [ ] iOS — Map, including at least one uploaded-photo beacon pin clipped to the marker shape
- [ ] iOS — Settings, including a nested sub-screen
- [ ] iOS — UIKit `UITabBar` and `UINavigationBar` are siblings of `ComposeUIViewController.view`
- [ ] iOS — No full-screen `UIKitViewController` / `UIKitView` overlay is used for native chrome, and touches reach Compose content

## Native chrome (iOS)

Host-view `UITabBar` + `UINavigationBar` siblings on `ComposeUIViewController.view`. **Never** `UIKitViewController(fillMaxSize)` / `UIKitView` overlays for chrome — that paints an opaque full-screen layer while touches pass through. The iOS evidence above must show both the sibling relationship and the absence of an overlay.

Future native-header / Liquid Glass work stays on a branch (or behind a flag) until the visual-proof checklist above is attached.

## Test plan

- [ ] `./gradlew spotlessCheck :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug`
- [ ] Android release-equivalent artifact: `./gradlew :composeApp:assembleRelease :composeApp:bundleRelease`
- [ ] iOS Kotlin: `./gradlew :composeApp:compileKotlinIosSimulatorArm64 :composeApp:iosSimulatorArm64Test :composeApp:compileKotlinIosArm64`
- [ ] iOS Xcode: Debug simulator and Release generic-device builds both pass with code signing disabled for compile verification
- [ ] `REQUIRE_CLICK_WEB=1 CLICK_WEB_ROOT=../click-web bash scripts/check-supabase-drift.sh`
- [ ] If migrations changed: `REQUIRE_CLICK_WEB=1 CLICK_WEB_ROOT=../click-web bash scripts/test_map_beacons_hub_id.sh`
- [ ] Device/simulator: body content visible on Home / Clicks / Settings; iOS nav bar is system glass; tab bar unchanged
- [ ] Android and iOS validation covers changed loading, empty, offline, error, permission-denied, keyboard, back-navigation, and restoration states

## Release and review gates

- [ ] Related documentation/runbook and release notes are updated.
- [ ] Any Supabase migration is additive, mirrored from `click-web`, and has fresh + upgrade-path evidence.
- [ ] No migration was applied from this PR branch; deployment follows the canonical runbook after final CI passes.
- [ ] CodeRabbit comments are classified as valid, false positive with evidence, or out of scope; valid P0/P1 comments are resolved with tests.
- [ ] No required check is skipped, muted, quarantined, or marked `continue-on-error`.
