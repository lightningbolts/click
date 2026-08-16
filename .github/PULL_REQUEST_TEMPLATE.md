## Summary

<!-- What changed and why. -->

## Visual proof (required — do not merge without this)

Attach a screen recording **or** screenshots covering **every tab and its nested screens**, showing real content (not empty black bodies). CI green is not sufficient.

- [ ] Home (pile or list with visible cards)
- [ ] Add Click (+ My QR or Tap if opened)
- [ ] Clicks (+ an open chat if reachable)
- [ ] Map (+ at least one uploaded-photo beacon pin, clipped to the marker shape)
- [ ] Settings (+ one sub-screen)

## Native chrome (iOS)

Host-view `UITabBar` + `UINavigationBar` siblings on `ComposeUIViewController.view`. **Never** `UIKitViewController(fillMaxSize)` / `UIKitView` overlays for chrome — that paints an opaque full-screen layer while touches pass through.

Future native-header / Liquid Glass work stays on a branch (or behind a flag) until the visual-proof checklist above is attached.

## Test plan

- [ ] `./gradlew spotlessCheck :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :composeApp:assembleDebug`
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64` and `:composeApp:iosSimulatorArm64Test`
- [ ] Device/simulator: body content visible on Home / Clicks / Settings; iOS nav bar is system glass; tab bar unchanged
