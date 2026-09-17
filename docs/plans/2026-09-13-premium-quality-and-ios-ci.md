# Premium visual quality and iOS CI plan

Date: 2026-09-13  
Repository: `click` KMP mobile app  
Branch observed: `fix/ios-release-native-oom`  
Artifact type: implementation and validation plan

## Snapshot and scope

This plan covers the next small, reviewable slices for premium visual quality and the iOS CI feedback loop. It is based on the current source tree and the supplied premium audit notes. The current checkout has a pre-existing dirty `gradle.properties`; that file is outside this plan and must remain untouched. No app, Gradle, workflow, or test implementation is changed by this document.

The product direction remains Functional Clarity: opaque product surfaces, Manrope typography, existing content hierarchy, and native UIKit navigation/tab chrome on iOS. The work is a quality and continuity pass across the existing information architecture. It does not redesign the product, add calls or CallKit, change backend/E2EE/BLE/Realtime behavior, split large files, or revive the reverted Home underlay approach. `NfcScanning` is excluded from the P2 motion fixes because its guarded state has no active animated children.

Current evidence is source review only. The user will exercise the resulting artifacts and report visual findings. Screenshots or UI automation are useful optional evidence, but no agent UI access or screenshot is a prerequisite for implementing the safe, source-confirmed fixes. A manual report is accepted without images.

### Status legend

| Label | Meaning |
| --- | --- |
| **Confirmed defect** | Reproducible from current source/state wiring; implementation is in scope. |
| **Defined improvement** | A bounded quality target from the current design/audit material; not a claim that an unseen screen is broken. |
| **User-feedback-dependent** | Keep as a review prompt until a current device/simulator report supplies observed behavior and repro details. |
| **Already-fixed; preserve** | Current behavior was repaired in an earlier slice; do not regress or reimplement it. |

## Confirmed P2 work

These are the first implementation slice. Keep the changes local and preserve existing enabled-motion values and timings.

### P2-A: Reduce Motion must stop unnecessary looping work — confirmed defects

Owners:

- [`QRScannerScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/QRScannerScreen.kt), `ScannerLensOverlay`.
- [`AddClickScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/AddClickScreen.kt), Tap-to-Connect card.
- [`ClickPlatformListRow.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickPlatformListRow.kt), `ClickListRowShimmer`.
- Shared motion helpers in [`MotionTokens.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/MotionTokens.kt) only when needed for the local contract.

Evidence and intended change:

- `QRScannerScreen` reads `reduceMotion` for rendered alpha, but still creates an infinite transition and animated scan-line value unconditionally. Create the transition/animated value only for the motion-enabled searching path, or supply a static value for the other paths. Preserve the current searching animation spec when motion is enabled.
- `AddClickScreen` always allocates the Tap-to-Connect pulse and only forces its rendered alpha to `1f`. Gate transition creation on Reduce Motion and use a static alpha when enabled. This is pre-existing and should be labeled as such in the implementation PR.
- `ClickPlatformListRow` always allocates a shimmer transition for each loading row, even when Reduce Motion is enabled. Use a static alpha under Reduce Motion. Do not require a broad shimmer refactor or shared transition rewrite as part of this P2.
- Judge the result by active animated children and frame work. The existence of an `InfiniteTransition` object alone is not a defect when it has no active animated children; retain the `NfcStateContent` exemption.

Expected behavior: normal motion is visually unchanged; Reduce Motion removes looping scan/pulse/shimmer work while retaining readable loading/search state and all text/state meaning. Dependencies are limited to Compose animation APIs and the existing `rememberReduceMotionEnabled()` contract. Main risk is accidentally changing normal-motion timing or making a loading state disappear; manual acceptance must cover both settings.

Targeted checks:

- Existing [`MotionTokensTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/theme/MotionTokensTest.kt) for token invariants.
- Add one focused source or behavior contract for conditional transition creation if the existing harness can observe it; do not create a style-only unit test.
- Android and iOS manual pass for QR searching/idle/success/error, Tap-to-Connect, and multiple loading rows with OS Reduce Motion on and off.

### P2-B: Encrypted lightbox failure state — confirmed PR95 regression

Owners:

- [`ChatExpandedPhotoPreview.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatExpandedPhotoPreview.kt).
- Secure-media state and retry entry points in [`ChatViewModel.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModel.kt) and [`HubChatViewModel.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/HubChatViewModel.kt).
- Bubble open paths in [`ChatMessageBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatMessageBubble.kt).
- Existing overlay and zoom behavior in [`GlassFullscreenMediaOverlay.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/GlassFullscreenMediaOverlay.kt) and [`ClickZoomableMedia.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickZoomableMedia.kt).

The trigger is precise: an encrypted photo is opened, there is no decoded/cached bitmap available to the expanded host, and the secure-media state reports an error. Producers already publish an error such as `Could not load image`, while the lightbox currently checks only for a bitmap and can remain on `Preparing photo…`. A retained bitmap/cache can make the error unreachable after opening; the plan does not claim every media failure can enter the lightbox.

Pass the loading/error/retry state into the current lightbox host, or give that host an explicit retry callback. When there is no bitmap and `secureState.error` is present, render a readable failure state with Retry and Close actions. Retry should re-enter loading once through the existing media request path and must not create a retry loop. Preserve the existing close control, locked Drop rules, pinch/double-tap zoom, share/save actions, and dismiss behavior. Keep 1:1 and hub producers behaviorally aligned.

Targeted checks:

- Extend the existing [`ChatMessageBubbleMediaTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/chat/ChatMessageBubbleMediaTest.kt) or add a focused state contract for cancellation/dismiss, loading to error, retry to success, and no-bitmap error branching.
- Use existing Android [`ChatViewModelTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/viewmodel/ChatViewModelTest.kt) and [`HubChatViewModelTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/viewmodel/HubChatViewModelTest.kt) selectors for producer parity where state is testable.
- Preserve and extend existing iOS/Android [`StabilizationAccessibilityUiTest.kt`](../../composeApp/src/iosSimulatorArm64Test/kotlin/compose/project/click/click/ui/chat/StabilizationAccessibilityUiTest.kt) and its Android counterpart only if a meaningful lightbox state assertion fits; do not mirror visual styling.
- Manually force a decrypt/download failure in 1:1 and hub chat, confirm the preparing state exits, Retry enters loading once, success opens zoomable media, and Close always dismisses.

Risk is state ownership: the retry must reuse the existing secure-media request and avoid changing transport or cache semantics. If no clean callback exists, keep the adapter local to the expanded host and document the chosen owner in the PR.

## Defined quality work packages

The following packages are bounded improvements. They become implementation work after the P2 slice, with current manual reports used to prioritize polish within each package.

### 1. Native shell, navigation, and continuity

Owners:

- [`App.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/App.kt), [`AppPrimaryTabsHost.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/AppPrimaryTabsHost.kt).
- [`InteractiveSwipeBackContainer.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/InteractiveSwipeBackContainer.kt), [`InteractiveBackPersonality.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/InteractiveBackPersonality.kt), [`RouteHistory.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/navigation/RouteHistory.kt), and [`HomeContinuityPolicy.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/navigation/HomeContinuityPolicy.kt).
- [`NativeCollapsingScaffold.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/NativeCollapsingScaffold.kt), [`NativeCollapsingScaffold.ios.kt`](../../composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/NativeCollapsingScaffold.ios.kt), [`IosHostNavBarLayerViews.ios.kt`](../../composeApp/src/iosMain/kotlin/compose/project/click/click/ui/components/IosHostNavBarLayerViews.ios.kt), [`ScreenChrome.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ScreenChrome.kt), and [`GlassFullscreenMediaOverlay.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/GlassFullscreenMediaOverlay.kt).
- [`AppScreenChromeState.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AppScreenChromeState.kt) and [`OverlayExclusiveBindPolicy.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/OverlayExclusiveBindPolicy.kt).

Implementation steps:

1. Record the current route/history and tab ownership before editing. Keep UIKit `UITabBar` and `UINavigationBar` intrinsically constrained to the host edges. On iOS 26+, preserve system Liquid Glass by avoiding custom `UITabBarAppearance` and `UINavigationBarAppearance`.
2. Define and document one z-order: base tabs, route overlays, sheets/popups, then global feedback. Ensure chat covers the tab bar while its underlying tab remains composed; search preserves the underlying tab and scroll state; media and map/event overlays do not reveal an opaque band.
3. Use existing route/history/continuity policy and transition tokens for drag, cancel, commit, rapid tab switching, and search open/close. Do not restore a persistent Home underlay or force a remount to hide a flash.

Expected behavior: Home, Add Click, Clicks, Map, Settings, onboarding, search, profile sheets, event details, chat, hubs, and media return to the prior route/scroll state with stable chrome and no visible remount flash. Risk is composition gating that causes a Home remount; the `HomeContinuityPolicyTest` and manual drag/cancel/commit matrix are required.

Checks: [`HomeContinuityPolicyTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/navigation/HomeContinuityPolicyTest.kt), [`RouteHistoryTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/navigation/RouteHistoryTest.kt), [`OverlayExclusiveBindPolicyTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/components/OverlayExclusiveBindPolicyTest.kt), then platform packaging checks required by the owning PR. Device/simulator visual evidence is user-owned acceptance evidence; it is optional during planning and is not inferred from source.

### 2. Motion, performance, and continuity of interaction

Owners:

- [`MotionTokens.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/MotionTokens.kt), [`InteractiveSwipeBackContainer.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/InteractiveSwipeBackContainer.kt), and [`InteractiveBackPersonality.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/InteractiveBackPersonality.kt).
- [`ChatPrimitives.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatPrimitives.kt), [`ChatAmbientMeshBackground.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatAmbientMeshBackground.kt), [`HomeScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HomeScreen.kt), [`HomeScreenCards.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HomeScreenCards.kt), [`MapScreenContent.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MapScreenContent.kt), [`MemoriesListSection.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MemoriesListSection.kt), [`CommunitySoundtrackBeaconDetail.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/CommunitySoundtrackBeaconDetail.kt), [`MomentKit.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/MomentKit.kt), and [`ClickLogoPulse.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickLogoPulse.kt).

After P2-A, inspect active looping children in these surfaces and converge press, sheet, swipe-back, toast, tab, and success motion on the existing tokens. Keep layout-affecting animation out of lazy lists, defer map/heavy-image work until after enter, and keep one IME-lift path. Reduce Motion should use static poses or short fades while retaining status meaning. Do not turn this into a blanket animation ban or a visual redesign.

Acceptance: normal motion stays responsive during rapid send, scroll, map fling, and tab switching; Reduce Motion has no looping motion and retains status text; any thermal or memory claim is recorded only when the user observes it. Use `MotionTokensTest` and add tests only for changed state contracts.

### 3. Chat, groups, hubs, media, and voice messages

Owners:

- [`ChatMessageBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatMessageBubble.kt), [`ChatPhotoBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatPhotoBubble.kt), [`ChatExpandedPhotoPreview.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatExpandedPhotoPreview.kt), and [`ClickZoomableMedia.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickZoomableMedia.kt).
- [`ChatViewTimelinePane.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatViewTimelinePane.kt), [`ChatView.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatView.kt), [`HubChatScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HubChatScreen.kt), [`ChatComposerStrip.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatComposerStrip.kt), [`ConnectionChatMessageComposer.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ConnectionChatMessageComposer.kt), [`ChatKeyboardDock.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatKeyboardDock.kt), and [`ChatLoadingAndDialogs.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatLoadingAndDialogs.kt).
- [`ChatViewModelState.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModelState.kt), [`ChatViewModelTimeline.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModelTimeline.kt), [`ChatViewModelSend.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModelSend.kt), [`ChatViewModelSendMedia.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModelSendMedia.kt), [`ChatViewModelSecureMedia.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/viewmodel/ChatViewModelSecureMedia.kt), and the corresponding hub secure-media/state files.
- [`SecureChatAudioFiles.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/SecureChatAudioFiles.kt), [`ChatAudioBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatAudioBubble.kt), and [`VoiceMessageRecordDialogLayout.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/VoiceMessageRecordDialogLayout.kt).

Use one interaction contract for 1:1, group, and hub chat: optimistic send without duplicate acknowledgement flash; stable keys and viewport during inbound messages/pagination; composer, audio/voice-message controls, and timeline move together with the IME; reply/edit strips collapse without holes; long press owns its action sheet without a native selection flash. Make media states explicit as loading, decoded, failure/retry, and dismiss, while preserving encrypted transport/cache behavior. Preserve pinch/double-tap and return to the prior scroll offset.

Acceptance covers five rapid sends, history plus inbound, pagination, keyboard mode switches, long press/copy/delete, audio recording/playback and scrub, encrypted success/failure/retry, pinch/dismiss, and hub parity. A jump, duplicate, black flash, stale state, or stuck preparation state is a user-feedback-dependent finding until reproduced on the current artifact.

Use existing [`ChatViewModelStateTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/viewmodel/ChatViewModelStateTest.kt), [`ChatMessageBubbleMediaTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/chat/ChatMessageBubbleMediaTest.kt), Android [`ChatViewModelTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/viewmodel/ChatViewModelTest.kt), Android [`HubChatViewModelTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/viewmodel/HubChatViewModelTest.kt), and the existing iOS chat UI selectors when they assert behavior rather than styling.

### 4. Onboarding, primary tabs, search, profiles, groups, hubs, map, events, QR, and Tap

The implementation must cover every current primary tab and the routes reached from them:

| Surface | Existing owners | Quality contract |
| --- | --- | --- |
| Onboarding/auth gates | [`AppAuthGate.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/AppAuthGate.kt), [`OnboardingShellChrome.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/OnboardingShellChrome.kt), [`PermissionsOnboardingScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/PermissionsOnboardingScreen.kt), [`LocationOnboardingScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/LocationOnboardingScreen.kt), [`ProfileBasicsGateScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ProfileBasicsGateScreen.kt) | Cold boot, permission denial, retry, completion, and back behavior are readable and preserve safe insets and progress. |
| Home | [`HomeScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HomeScreen.kt), [`HomeScreenCards.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HomeScreenCards.kt) | Generated/photo content remains legible; empty/loading/error/success states have distinct recovery; large text does not clip. |
| Add Click / Tap | [`AddClickScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/AddClickScreen.kt), [`AvailabilitySheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AvailabilitySheet.kt) | Tap-to-Connect retains normal pulse and stops it under Reduce Motion; open, save, success, error, and offline paths are recoverable. |
| Clicks / groups / hubs | [`ConnectionsTabControls.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ConnectionsTabControls.kt), [`ClickPlatformListRow.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickPlatformListRow.kt), [`CreateHubModal.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/CreateHubModal.kt), [`ConnectionsHubSheets.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ConnectionsHubSheets.kt) | Loading rows, empty inbox, group/hub creation, membership errors, and success remain distinct and stable during scroll. |
| Map / events / beacons | [`MapScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MapScreen.kt), [`MapScreenContent.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MapScreenContent.kt), [`CommunitySoundtrackBeaconDetail.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/CommunitySoundtrackBeaconDetail.kt), [`EventDirectoryUserProfileSheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/EventDirectoryUserProfileSheet.kt) | Map enter/fling does not hitch visibly; permission/offline/retry states are clear; event/profile overlays retain map continuity and safe insets. |
| QR / scanner | [`QRScannerScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/QRScannerScreen.kt), [`QRScanner.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/QRScanner.kt), [`MyQRCodeScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MyQRCodeScreen.kt) | Searching, idle, success, invalid, camera denial, and retry states are readable; scanner motion follows Reduce Motion. |
| Search | [`UnifiedSearchSheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/UnifiedSearchSheet.kt), [`GlobalSearchScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/GlobalSearchScreen.kt), [`ChatSearchAnchor.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatSearchAnchor.kt) | Empty, loading, failed, offline, and result states are distinct; keyboard and sheet transitions preserve the underlying route and scroll. |
| Profiles and memories | [`ProfileBottomSheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ProfileBottomSheet.kt), [`ProfileSheetPanels.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ProfileSheetPanels.kt), [`ProfileTabbedSheets.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ProfileTabbedSheets.kt), [`ProfileSheetMediaPanels.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ProfileSheetMediaPanels.kt), [`MemoriesListSection.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MemoriesListSection.kt) | Avatar/media fallbacks, tab semantics, loading/empty/error, and large type remain understandable. Do not label historical unlabeled-image concerns as current regressions without a report. |
| Settings/privacy | [`SettingsScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/SettingsScreen.kt), [`SettingsScreenComponents.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/SettingsScreenComponents.kt) | Toggles, destructive actions, network errors, confirmation, and return-to-settings preserve selected state, role, and insets. |

Keep these flows on existing route/state contracts. Any behavior that changes domain semantics, E2EE, permissions, or navigation IA must be split out of the visual-quality slices.

### 5. Layout, typography, spacing, safe insets, and reuse

Owners:

- [`PlatformTheme.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/PlatformTheme.kt), [`Typography.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/Typography.kt), and [`Color.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/Color.kt).
- [`ClickLaunchPrimitives.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickLaunchPrimitives.kt), [`AdaptiveCard.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AdaptiveCard.kt), [`GlassCard.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/GlassCard.kt), [`ScreenChrome.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ScreenChrome.kt), and [`ClickPlatformListRow.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickPlatformListRow.kt).

Keep Manrope/M3 typography and the existing Functional Clarity scale. Extend shared spacing, radius, border, button, card, sheet, toast, loading, empty, avatar, and semantics primitives only after confirming their current design contract. Search callers before deleting wrappers; keep variants parameterized. Preserve 44/48dp target guidance, safe insets, multiline/dynamic type, and hierarchy on narrow and large text settings. Do not silently introduce a third radius standard or glassify product cards/sheets/buttons.

Targeted check: [`ClickLaunchPrimitivesTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/components/ClickLaunchPrimitivesTest.kt), Android [`GlassCardTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/ui/components/GlassCardTest.kt), and iOS [`GlassCardUiTest.kt`](../../composeApp/src/iosSimulatorArm64Test/kotlin/compose/project/click/click/ui/components/GlassCardUiTest.kt) where changed behavior is asserted. Visual-only spacing changes receive manual review and no mirrored unit test.

### 6. Theme, contrast, generated content, and native chrome

Owners:

- [`Contrast.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/Contrast.kt), [`CardVisualSurface.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/CardVisualSurface.kt), [`LiquidGlassPill.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/LiquidGlassPill.kt), [`GlassSheetTokens.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/GlassSheetTokens.kt), and [`UnifiedPopup.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/UnifiedPopup.kt).
- [`PlatformTheme.android.kt`](../../composeApp/src/androidMain/kotlin/compose/project/click/click/ui/theme/PlatformTheme.android.kt), [`PlatformTheme.ios.kt`](../../composeApp/src/iosMain/kotlin/compose/project/click/click/ui/theme/PlatformTheme.ios.kt), and the native scaffold files listed above.
- [`DisposableCameraShared.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/camera/DisposableCameraShared.kt).

Keep navigation accent and generated-card identity separate. Route generated surfaces through `CardVisualSurface` and its scrim/contrast helper. Remove newly introduced hard-coded color/radius/border drift through existing tokens, while keeping product cards/sheets/buttons opaque and gradients absent except for the documented native navigation-chrome behavior. Audit camera and dark/light assumptions in both schemes. Check status colors, focus/selected rings, map/beacon labels, scrims, and disabled states in light/dark and high contrast.

Acceptance: content remains readable over every generated visual; dark mode surfaces are intentional; selected/focus state is not color-only; token changes do not alter content identity or IA. Use existing token tests where behavior is affected and manual theme/type reports for visual changes.

### 7. Accessibility, haptics, keyboard, and alternate input

Owners:

- [`ClickLaunchPrimitives.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickLaunchPrimitives.kt), [`ClickCircularGlassIconButton.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickCircularGlassIconButton.kt), [`ClickPlatformListRow.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ClickPlatformListRow.kt), [`InterestEditor.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/InterestEditor.kt), and [`AvatarWithOnlineIndicator.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AvatarWithOnlineIndicator.kt).
- [`ChatMessageBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatMessageBubble.kt), [`ChatPhotoBubble.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatPhotoBubble.kt), [`ChatKeyboardDock.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatKeyboardDock.kt), and the iOS native scaffold files.

Label unlabeled controls and avatars; expose button/tab roles, selected/toggle state, headings, and live announcements for async message/error/success changes. Give archive, audio scrub, and other gesture-only actions equivalent accessible actions. Keep meaningful Home photo fallback descriptions. Increase hit regions without distorting visuals where needed. Verify VoiceOver/TalkBack, keyboard/switch input, large text, Reduce Motion, and haptics preserve equivalent outcomes and do not make feedback color-only.

Use existing [`StabilizationAccessibilityUiTest.kt`](../../composeApp/src/androidUnitTest/kotlin/compose/project/click/click/ui/chat/StabilizationAccessibilityUiTest.kt) and iOS counterpart for covered photo click/long click/keyboard/chip behavior. Add a focused test only for a changed semantic/state contract; do not create tests that assert a visual-only style. Manual acceptance must include Home → Clicks → Chat with VoiceOver and TalkBack, every primary action without a gesture, and online/offline plus delivered/failed text/state announcements.

### 8. Loading, empty, error, offline, and success feedback

Owners:

- [`AppAuthGate.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/AppAuthGate.kt), [`AppShimmerScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AppShimmerScreen.kt), [`AppEmptyState.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AppEmptyState.kt), [`OfflineStatusBanner.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/OfflineStatusBanner.kt).
- [`ChatLoadingAndDialogs.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/chat/ChatLoadingAndDialogs.kt), [`ChatView.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/ChatView.kt), [`HubChatScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/HubChatScreen.kt), [`MapScreen.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/MapScreen.kt), [`UnifiedSearchSheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/screens/UnifiedSearchSheet.kt), [`AvailabilitySheet.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/AvailabilitySheet.kt), and [`ProfileSheetPanels.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/ProfileSheetPanels.kt).
- [`NetworkFailureUtil.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/util/NetworkFailureUtil.kt), [`UnifiedToast.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/components/UnifiedToast.kt), and success/motion primitives in [`MotionTokens.kt`](../../composeApp/src/commonMain/kotlin/compose/project/click/click/ui/theme/MotionTokens.kt).

Adopt shared state primitives where the current flow is bespoke without masking domain errors. Every primary CTA gives immediate disabled/progress or safe optimistic feedback; long uploads are determinate where possible; retry is actionable; offline is global and non-blocking; empty is distinct from failed; success cannot race with stale error. Keep ViewModel/network contracts unchanged except the explicit encrypted-lightbox presentation state and separately approved error-surfacing slices.

Manual acceptance: cold boot, empty and failed search, offline shell, chat load/send/media failure, map retry, availability save, profile load, and success/undo each show a distinct readable state and recover without duplicate submission. Existing [`NetworkFailureUtilTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/util/NetworkFailureUtilTest.kt), [`UnifiedToastTokensTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/components/UnifiedToastTokensTest.kt), and [`UnifiedPopupTokensTest.kt`](../../composeApp/src/commonTest/kotlin/compose/project/click/click/ui/components/UnifiedPopupTokensTest.kt) are the nearest focused checks.

## Ordered PR slices and gates

Keep each slice small enough for a reviewer to inspect and for the user to exercise.

1. **Baseline and inventory.** Record branch/SHA, dirty-file checksum, exact source/test anchors, and the current CI baseline when supplied. Do not edit `gradle.properties`.
2. **P2 motion.** Fix conditional transition lifetime in QR, Add Click, and list-row shimmer. Preserve enabled-motion specs. Add only focused behavior/source contracts.
3. **P2 lightbox error.** Wire no-bitmap plus secure-media-error state to an explicit error/retry/close branch, with cancellation/dismiss and 1:1/hub parity coverage.
4. **Shared contracts.** Ratchet motion, spacing, typography, chrome, semantics, and loading/error/empty/success contracts before migrating repeated callers.
5. **Native shell.** Review host-bar and overlay ownership, then apply continuity/layering changes with route history and saveable state intact.
6. **Chat/media.** Apply timeline/composer/IME/media parity after shell ownership is stable; include voice-message interactions.
7. **Theme and alternate input.** Apply contrast/token, dynamic type, safe inset, a11y, and haptic improvements; keep visual-only changes free of mirrored unit tests.
8. **Manual acceptance and follow-up.** The user tests the current artifacts and files reports using the template below. Only confirmed current repros become follow-up defects; historical or static-only concerns stay backlog items.

Every implementation PR runs the narrowest relevant existing check: `MotionTokensTest`, `HomeContinuityPolicyTest`, `RouteHistoryTest`, `OverlayExclusiveBindPolicyTest`, `ClickLaunchPrimitivesTest`, focused Android unit tests, focused iOS simulator tests, and required packaging checks. A compile alone is not release evidence. The release still requires Android/iOS platform gates and real web integration; this plan does not provide release evidence.

## Coverage manifest

This manifest defines required behavior coverage and the evidence type. It does not assert that all rows are currently covered.

| Area | Critical paths | Automated evidence | Manual evidence |
| --- | --- | --- | --- |
| Shell/continuity | Five tabs, nested screens, search, sheet, drag cancel/commit, keyboard | `HomeContinuityPolicyTest`, `RouteHistoryTest`, `OverlayExclusiveBindPolicyTest` | Android/iOS route and scroll continuity |
| Onboarding | Cold boot, permissions, profile gate, location, completion/back | Existing focused tests where present; add only state contracts | Android/iOS with denied/granted permissions |
| Motion/a11y | QR, Tap, shimmer, Reduce Motion, large type, VoiceOver/TalkBack | `MotionTokensTest`, existing stabilization a11y tests, focused P2 contracts | OS settings and frame/energy observations only when observed |
| Chat | 1:1, group, hub, five sends, inbound/pagination, long press, voice messages | Chat state/media/viewmodel selectors listed above | Keyboard, audio, media, offline, parity |
| Lightbox | Loading, no-bitmap error, retry, success, cancel/Close, zoom/dismiss | `ChatMessageBubbleMediaTest` plus focused state contract | Forced decrypt/download failure on 1:1 and hub |
| Map/events/QR | Map permission/offline/retry, event/profile overlay, QR success/error | Existing route/state tests where applicable | Map fling, overlay continuity, camera denial |
| Theme/layout | Light/dark, high contrast, Manrope, dynamic type, safe insets, 44/48dp targets | Token/card tests where semantics/state changes | Narrow/large type and all primary tabs |
| Feedback states | Loading, empty, error, offline, success/undo, retry/no duplicate submission | `NetworkFailureUtilTest`, toast/popup tests, focused state contracts | Cold boot, search, chat, map, availability, profile |

## User manual test matrix

The user can run a representative matrix against each PR slice. Images and recordings are optional.

| ID | Setup | Action | Expected result | Report if |
| --- | --- | --- | --- | --- |
| M1 | Android and iOS, light theme | Visit Home → Add Click → Clicks → Map → Settings; open and close a nested screen | Native chrome, selected state, safe insets, and prior scroll remain stable | remount, flash, band, clipped title, or lost position |
| M2 | Reduce Motion on/off | QR search idle/success/error; Tap-to-Connect; loading Click rows | Normal motion retains current timing; Reduce Motion has no looping motion and retains status meaning | pulse/scan/shimmer loops, disappears, or changes normal motion |
| M3 | Large text and keyboard | Search, chat composer, reply/edit strip, settings toggle | Text wraps; IME lifts the active content once; buttons remain operable | overlap, jump, hidden action, or lost focus |
| M4 | 1:1 chat | Five rapid sends, inbound message, scroll/pagination, long press | No duplicate flash; viewport remains stable; actions are discoverable | duplicate, jump, stale action sheet, or native selection flash |
| M5 | Group and hub chat | Repeat M4 plus voice-message record/play/scrub and media open | Behavior matches 1:1; audio and media states are readable | parity break, stuck state, or hub-only failure |
| M6 | Forced encrypted media failure | Open photo with no decoded/cached bitmap; force decrypt/download error; Retry; Close | Preparing exits to error; Retry loads once; success zooms; Close dismisses | infinite preparing, retry loop, black flash, or missing Close |
| M7 | Offline/empty/error | Cold boot offline; empty and failed search; chat/map/media failure; availability save | Empty is distinct from error; retry/recovery is actionable; offline shell remains usable | stale error, duplicate submit, or no recovery |
| M8 | Light/dark/high contrast | Home generated visual, map labels, profile/media, sheets and popups | Text, focus, selected, disabled, and scrim states remain readable | color-only state or insufficient contrast |
| M9 | VoiceOver/TalkBack and switch/keyboard input | Home → Clicks → Chat; activate primary actions without gestures | Roles, labels, selected state, announcements, and equivalent actions are exposed | unlabeled control, gesture-only outcome, or missing status |
| M10 | Map/events/QR/profile | Fling map, open event/profile sheet, QR success/error, dismiss overlay | Underlying route remains composed; overlay z-order and safe insets are stable | opaque band, lost route, or inaccessible dismissal |

## Manual report template

```text
Device / OS / accessibility settings:
App build + git SHA / source of artifact:
Account and data setup:
Feature path and matrix ID:
Theme (light/dark), dynamic type, Reduce Motion, VoiceOver/TalkBack:
Network condition:
Expected:
Observed:
Severity (P0/P1/P2), frequency, and exact repro steps:
State (loading/empty/error/offline/success):
Evidence (optional screenshot/video/log):
Suspected owner path (if known):
```

## CI plan status and evidence boundary

The CI section is intentionally finalized only after the investigator supplies the current run history, URLs, commit evidence, and the measured cause timeline. The required final section must distinguish measured evidence from hypotheses and must not claim a speedup from caching or LTO changes without a benchmark.

The final CI plan will target **10–15 minutes for the full required critical path**, including required feedback, with no coverage reduction, no moving required tests/releases to nights, and no “first check green” shortcut. It must include:

- historical versus current wall-time and coverage table with run URLs, commit SHAs, queue time, runner minutes, p50/p95, and all-success gate;
- a cause timeline that separates measured bottlenecks from hypotheses;
- experiments for LTO OOM/memory/profile, native and Gradle caching, exact-input build artifact reuse across smoke/test workflows, parallel independent full release/test work, and compiler/runner benchmarks if needed;
- cold, warm, and source-changing benchmark matrix;
- proof that tests actually executed, rather than only consuming a cached result;
- cache invalidation rules for source, architecture, toolchain, build type, configuration, and exact artifact inputs;
- explicit treatment of the dirty heap change as unverified/pre-existing until measured;
- version/capability validation before recommending release-binary-cache or other new flags.

Kotlin Native references to carry into that evidence-backed section: [Improving compilation time](https://kotlinlang.org/docs/native-improving-compilation-time.html) documents the cost of whole-module Release/LTO optimization and the need to benchmark cold and warm builds; [Kotlin/Native binary options](https://kotlinlang.org/docs/native-binary-options.html) describes `enableReleaseBinaryCache` as experimental from Kotlin 2.4.20, so it is not a drop-in recommendation for a 2.3.x project without installed-version validation. For Gradle CI caching, use the [Gradle setup action guidance](https://github.com/gradle/actions/blob/main/docs/setup-gradle.md): choose the provider/version deliberately, avoid double-caching Gradle home, and account for configuration-cache encryption when persisting it.

Until those notes arrive, there is no measured CI baseline, cause attribution, or completed optimization recommendation in this document.

## Explicit non-work and preserve list

- Do not add or restore calls, VoIP, PushKit, or CallKit.
- Do not redesign Functional Clarity, change IA, glassify product cards/sheets/buttons, or add broad gradients.
- Do not change backend/E2EE/BLE/Realtime behavior, split god files, or make release claims from a compile.
- Do not revive reverted Home underlay code or label stale July audit assertions as current bugs.
- Preserve existing lightbox Close behavior and locked Drop rules.
- Preserve already-landed auth/session, hub lifetime, creator guest-list, photo/chip semantics, and reduced-motion repairs recorded in [`2026-09-07-pr95-review-repairs.md`](../regression-testing/2026-09-07-pr95-review-repairs.md).
- Keep profile image labels, camera dark-mode assumptions, chrome polish, and historical known issues as backlog items until current manual feedback confirms them.
