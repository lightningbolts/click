# App Shell & Navigation

Target-state specification for the Click mobile app shell, gate stack, tab navigation, overlays, and back behavior. Sources: `click/composeApp/src/commonMain/kotlin/compose/project/click/click/App.kt`, `navigation/NavigationItem.kt`, `ui/components/*`, `ui/screens/UnifiedSearchSheet.kt`, `calls/CallOverlays.kt`.

**Visual system:** Functional Clarity (neo-brutalist) — opaque surfaces, 2px `#000` borders, primary `#630ed4`, no glass/blur/gradients. Design-asset mock: invented from design system.

---

## Gate Stack Overview

The root composable `App()` wraps all UI in `PlatformThemeProvider` → `ConnectionSensorMonitorsProvider` → full-screen `Box` (flat `background` `#f9f9f9` / dark inverse — no radial glow).

### ASCII: Authentication & Post-Login Gate Order

```
┌─────────────────────────────────────────────────────────────────┐
│ App() — PlatformThemeProvider                                   │
└────────────────────────────┬────────────────────────────────────┘
                             │
              ┌──────────────▼──────────────┐
              │ AuthState.Loading OR        │
              │ authShimmerVisible?         │
              └──────┬──────────────┬───────┘
                  YES│              │NO
                     ▼              ▼
            ┌──────────────┐  ┌────────────────────┐
            │ AppShimmer   │  │ !isAuthenticated?  │
            │ Screen       │  └───┬────────────┬───┘
            └──────────────┘   YES│            │NO
                                  ▼            ▼
                          ┌─────────────┐  ┌──────────────────────────┐
                          │ LoginScreen │  │ AUTHENTICATED GATE CHAIN │
                          │ or SignUp   │  └────────────┬─────────────┘
                          └─────────────┘               │
                                                        ▼
                                          ┌─────────────────────────┐
                                          │ profileGatePending?     │
                                          │ (cache check !ready)    │
                                          └────┬──────────────┬─────┘
                                            YES│              │NO
                                               ▼              ▼
                                    ┌──────────────┐   ┌──────────────────┐
                                    │ AppShimmer   │   │ profileGateActive?│
                                    │ Screen       │   │ (missing bday/   │
                                    └──────────────┘   │  first name)     │
                                                       └───┬──────────┬───┘
                                                        YES│          │NO
                                                           ▼          ▼
                                              ┌─────────────────┐  ┌────────────────────┐
                                              │ ProfileBasics   │  │ onboardingStep     │
                                              │ GateScreen      │  │ == "loading"?      │
                                              └─────────────────┘  └───┬────────────┬───┘
                                                                    YES│            │NO
                                                                       ▼            ▼
                                                            ┌──────────────┐  ┌─────────────────┐
                                                            │ AppShimmer   │  │ step !=         │
                                                            │ Screen       │  │ "complete"?     │
                                                            └──────────────┘  └───┬─────────┬───┘
                                                                             YES│         │NO
                                                                                ▼         ▼
                                                                   ┌──────────────────┐ │
                                                                   │ AnimatedContent:   │ │
                                                                   │ Welcome →          │ │
                                                                   │ Interests →        │ │
                                                                   │ Avatar             │ │
                                                                   └──────────────────┘ │
                                                                                        ▼
                                                                          ┌─────────────────────────┐
                                                                          │ onboardingHandoffActive │
                                                                          │ OR shouldStartHandoff?  │
                                                                          └────┬──────────────┬─────┘
                                                                            YES│              │NO
                                                                               ▼              ▼
                                                                    ┌──────────────┐   ┌─────────────────┐
                                                                    │ AppShimmer   │   │ MAIN SCAFFOLD   │
                                                                    │ (600 ms)     │   │ (tabs + overlays)│
                                                                    └──────────────┘   └─────────────────┘
```

### Gate State Table

| Order | Condition | Screen / Surface | Source |
|-------|-----------|------------------|--------|
| 1 | `authState is Loading` OR `authShimmerVisible` | `AppShimmerScreen` | `App.kt` ~515 |
| 2 | `!isAuthenticated` | `LoginScreen` / `SignUpScreen` (fade+scale in) | `App.kt` ~517–581 |
| 3 | `profileGatePending` | `AppShimmerScreen` | `App.kt` ~769–770 |
| 4 | `profileGateActive` | `ProfileBasicsGateScreen` | `App.kt` ~771–784 |
| 5 | `onboardingStep == "loading"` | `AppShimmerScreen` | `App.kt` ~785–786 |
| 6 | `onboardingStep != "complete"` | `WelcomeScreen` / `InterestTaggingScreen` / `AvatarScreen` | `App.kt` ~787–870 |
| 7 | `onboardingHandoffActive` OR `shouldStartOnboardingHandoff` | `AppShimmerScreen` (600 ms delay) | `App.kt` ~871–872 |
| 8 | Else | Main `Scaffold` + overlays | `App.kt` ~873+ |

**Profile gate pending**: `currentUser.id` non-blank, `appDataUser != null`, `!profileGateCheckReady`.

**Profile gate active**: check ready AND (`birthdayMissing` OR `firstNameMissing`).

**Onboarding steps** (`onboardingStep`): `"loading"` until `onboardingState`, `appDataUser`, `interestsRemoteResolved`, `hasCompletedOnboarding` ready; then `"welcome"` | `"interests"` | `"avatar"` | `"complete"` from `OnboardingViewModel.Step`.

**Handoff**: After onboarding completes, 600 ms shimmer → `showHomeRevealOverlay` 380 ms → main surface fades in (`homeSurfaceAlpha`).

---

## 1. Main Scaffold Shell

### 1.1 Layout / Container

- Outer `Box(fillMaxSize)` wraps offline banner, `Scaffold`, **overlay** `PlatformBottomBar`, search sheet, tether overlay.
- `Scaffold`: `contentWindowInsets = WindowInsets(0)`, `snackbarHost = { SnackbarHost(snackbarHostState) }` — **no `bottomBar` slot** (content is full-bleed under the tab icons).
- Content `Box`: top padding from scaffold; `graphicsLayer { alpha = homeSurfaceAlpha }` for entrance fade.
- Inner `Surface(background)` hosts `AnimatedContent(screenKey)` + hub chat overlay + modals + z-indexed globals.
- Tab-root lists use `rememberBottomChromePadding()` so the last controls clear hit targets while still scrolling under the icons.

### 1.2 Interactive Elements

- `PlatformBottomBar` as a **bottom overlay** (`zIndex(5f)`), not Scaffold `bottomBar`.
- Chrome is **fully transparent** — page materials under the icons must match materials above (no fill/blur/seam). See known issue `#23`.
- Tab selection calls `navigateTo(item.route)` and clears overlay flags (`hubChatArgs`, QR, NFC).
- **Add Click tab**: `PlatformHapticsPolicy.heavyImpact()` + `successNotification()` before navigate.
- `SnackbarHost` for `connectionViewModel.transientNotice` and `AppDataManager.transientUserMessages`.

### 1.3 States

| State | Bottom bar | Content |
|-------|------------|---------|
| **Default** | Visible on primary tabs | Current route screen |
| **Pressed/Highlighted** | Tab selection haptics on Add Click | N/A |
| **Active** | `currentRoute` highlighted | `activeScreenKey` screen |
| **Focus** | N/A | Search field when sheet open |
| **Disabled** | `visible = false` when hidden | Underlay still composed |
| **Loading** | `isInitialLoading` shows sync banner card | Shimmer on gate paths |
| **Empty** | N/A | Per-screen |
| **Error** | Snackbar error messages | `appError` top card |
| **Success** | Snackbar success (connections) | Reveal overlay |

### 1.4 Micro-copy

- Snackbar (connections): `"Connected with {name}!"`, `"Connection saved offline. It will sync automatically when you're back online."`, connection errors from view model.
- Sync banner: `"{n} connection(s) queued for sync."`, `appError` text.

### 1.5 Flow Sequence

1. Gate chain completes → `homeSurfaceVisible` true.
2. `Scaffold` renders bottom bar + content.
3. `navigateTo` pushes route history; `AnimatedContent` transitions.
4. Overlays (hub chat, camera, calls) layer above content by z-index.

### 1.6 A11y & Responsive

- Scaffold does not consume system insets on content window (manual inset handling per screen).
- Snackbar host at bottom; not duplicated on onboarding (separate host, bottom 24 dp padding).

---

## 2. NavigationItem & Bottom Tabs

### 2.1 Layout / Container

`NavigationItem` sealed class (`navigation/NavigationItem.kt`):

| Route | Title | Material Icon | SF Symbol |
|-------|-------|---------------|-----------|
| `home` | Home | `Icons.Filled.Home` | `house.fill` |
| `add_click` | Add Click | `Icons.Filled.Add` | `plus.circle.fill` |
| `connections` | Clicks | `Icons.Filled.Person` | `person.2.fill` |
| `map` | Map | `Icons.Filled.LocationOn` | `location.fill` |
| `settings` | Settings | `Icons.Filled.Settings` | `gearshape.fill` |
| `search` | Search | `Icons.Filled.Search` | `magnifyingglass` |

`bottomNavItems` = Home, Add Click, Connections, Map, Settings (Search is not a tab; opens sheet).

### 2.2 Interactive Elements

**Android** (`BottomBar.android.kt`):

- `NavigationBar`: solid `surface` fill, **2dp** top `#000` border, **0 elevation** (no shadow).
- Items: icon only (`alwaysShowLabel = false`).
- Selected: `primary` icon/text, `primaryContainer` indicator.
- Unselected: `onSurfaceVariant`.
- Chrome height: `AndroidNavBarContentHeight` (80 dp) + navigation bar inset.

**iOS** (`BottomBar.ios.kt`):

- Native `UITabBar` subview on `UIViewController` — **solid bordered bar** (not liquid glass).
- Items use SF Symbols + titles; active tab = solid `#630ed4` circle behind icon.
- Tab bar pinned to view bottom; measured clearance → `AppScreenChromeState`.
- Placeholder `Box` mirrors tab bar frame for Compose layout sync.

### 2.3 States

| State | Android | iOS |
|-------|---------|-----|
| **Default** | Nav bar visible | UITabBar visible |
| **Pressed/Highlighted** | Ripple (M3) | Native tab highlight |
| **Active** | `currentRoute == item.route` | `selectedItem` synced |
| **Focus** | N/A | N/A |
| **Disabled** | `visible = false` → bar not rendered | `tabBar.hidden = true`, alpha 0 |
| **Loading** | N/A | N/A |
| **Empty** | N/A | N/A |
| **Error** | N/A | N/A |
| **Success** | Add Click haptics | Add Click haptics |

### 2.4 Micro-copy

- Tab labels: `"Home"`, `"Add Click"`, `"Clicks"`, `"Map"`, `"Settings"`.
- Icon `contentDescription` = item title (Android).

### 2.5 Flow Sequence

`onItemSelected` → optional haptics → `navigateTo` → clear overlays → `focusManager.clearFocus()`.

### 2.6 A11y & Responsive

- iOS measures real tab bar height including home indicator (solid bordered bar).
- Android falls back to 80 dp + nav inset when not visible.

---

## 3. Bottom Bar Hide Rules

### 3.1 Layout / Container

`hideMainBottomBar` boolean passed to `PlatformBottomBar(visible = !hideMainBottomBar)`.

### 3.2 Interactive Elements

- When hidden, iOS still updates `AppScreenChromeState` to safe area + 16 dp (scroll padding only).
- Android chrome height collapses to navigation bar inset only.

### 3.3 States

| State | `hideMainBottomBar` | Reason |
|-------|---------------------|--------|
| **Default** | `false` | Primary tab visible |
| **Connections chat open** | `true` | `isConnectionsChatOpen` |
| **Hub chat open** | `true` | `hubChatArgs != null` |
| **Click Drops camera** | `true` | `showConnectionDisposableRoll` OR `disposableRollOpening` |

### 3.4 Micro-copy

- None at chrome layer.

### 3.5 Flow Sequence

```
ConnectionsScreen.onChatOpenStateChanged(true)  → hide
hubChatArgs = HubChatNavArgs(...)               → hide
openConnectionDisposableRoll / opening flag     → hide
Dismiss chat / hub / camera                     → show
```

### 3.6 A11y & Responsive

- Hidden bar does not remove back affordance; chat screens provide explicit back.

---

## 4. Route Model & activeScreenKey

### 4.1 Layout / Container

- `currentRoute` state default `"home"`; `routeHistory` stack for back.
- Overlay routes encoded in `activeScreenKey`:

```
activeScreenKey =
  if (showMyQRCode)     "my_qr"
  else if (showQRScanner) "qr_scanner"
  else if (showNfcScreen) "nfc"
  else currentRoute
```

### 4.2 Interactive Elements

- `navigateTo(route)`: sets `transitionMode = Tap`, pushes history.
- `navigateBack(mode)`: pops history.
- `navigatePrimaryRouteBackHome(mode)`: resets to Home for iOS swipe on non-Home primary tabs.

### 4.3 States

| State | `screenKey` |
|-------|-------------|
| **Default** | `home` |
| **Add Click overlay** | `my_qr` / `qr_scanner` / `nfc` flags |
| **Active** | Current tab route |

### 4.4 Micro-copy

- None.

### 4.5 Flow Sequence

See §6 Screen Transitions.

### 4.6 A11y & Responsive

- Route order for transition direction: Home → Add Click → Connections → Map → Settings → my_qr → qr_scanner → nfc.

---

## 5. AppScreenScaffold (Tab Root Chrome)

### 5.1 Layout / Container

- `LazyColumn` / `verticalScroll` with horizontal padding `AppScreenDefaults.HorizontalPadding` (20 dp).
- Bottom `contentPadding` = `rememberBottomChromePadding()` (tab overlay + 16 dp).
- Native collapsing top bar (`NativeCollapsingScaffold`): Android `LargeTopAppBar` + `exitUntilCollapsed`; iOS host-view `UINavigationBar` (same mounting as `UITabBar` — sibling of the Compose canvas, top-pinned, never a full-screen `UIKitViewController` overlay). Compact chrome stays visible. iOS 26 uses system Liquid Glass (no custom `UINavigationBarAppearance`).

### 5.2 Interactive Elements

- `HeaderSearchIconButton` when `onOpenSearch` provided → opens `UnifiedSearchSheet`.
- Header collapses over `HeaderCollapseScrollThreshold` (20 dp, converted to px). Compact chrome stays visible.

### 5.3 States

| State | Header |
|-------|--------|
| **Default** | Expanded large title (platform native) |
| **Active scroll** | Large → inline / collapsed app bar |
| **Scrolled past first item** | Fully collapsed compact bar (not hidden) |

### 5.4 Micro-copy

- Per-screen `title` / `subtitle` props.

### 5.5 Flow Sequence

Scroll → native collapse → compact bar remains; content scrolls under translucent chrome.

### 5.6 A11y & Responsive

- Header measures expanded height on first frame for multi-line subtitles / large fonts.

---

## 6. Screen Transitions (AnimatedContent)

### 6.1 Layout / Container

- `AnimatedContent(targetState = screenKey, label = "app_screen_transition")`.
- `NavigationTransitionMode`: `Tap` | `GestureBack`.

### 6.2 Interactive Elements

- Tab taps set `Tap` mode.
- iOS swipe-back sets `GestureBack` → **no** enter/exit animation (`EnterTransition.None togetherWith ExitTransition.None`).
- After gesture back, 80 ms delay before resetting to `Tap` (prevents double animation on Home).
- Primary tabs (Home / Add Click / Connections / Map / Settings) always use **AnimatedContent** with the **280ms crossfade** when switching via the tab bar. Do **not** put Map/Add Click/Settings in a separate Home-underlay overlay — that regressed tab motion (Home flash, corner slides).
- iOS swipe-to-Home on Add Click / Map / Settings: `InteractiveSwipeBackContainer` with `previousContent = Home` inside the AnimatedContent child (pre-existing).

### 6.3 States

| Mode | Transition |
|------|------------|
| **GestureBack** | None |
| **Primary tab crossfade** | Fade 280 ms both ways |
| **Forward** | Slide in from right + fade 300/320 ms; slide out left |
| **Backward** | Slide in from left; slide out right |

**Primary tabs** (crossfade set): home, add_click, connections, map, settings.

### 6.4 Micro-copy

- None.

### 6.5 Flow Sequence

```
navigateTo ──► Tap mode ──► AnimatedContent spec
swipe back ──► GestureBack ──► instant removal
       └──► delay(80) ──► Tap mode
```

### 6.6 A11y & Responsive

- `SizeTransform(clip = true)` on non-crossfade slides.

### ASCII: Transition Decision

```
transitionMode == GestureBack? ──YES──► No animation
        │
        NO
        ▼
both routes in primary tabs? ──YES──► crossfade 280ms
        │
        NO
        ▼
targetIndex >= initialIndex? ──YES──► slide forward + fade
        │
        NO──► slide backward + fade
```

---

## 7. InteractiveSwipeBackContainer (iOS)

### 7.1 Layout / Container

- 3-layer stack: (1) previous route parallax peek 30% width, (2) scrim opacity `0.5 × (1 - progress)`, (3) current route `translationX`.
- Default `edgeSwipeWidth` = 24 dp; **App uses 44 dp** for all shell swipe surfaces.
- Commit: offset > 50% width OR velocity > 800 px/s.
- Overlay native chrome translates with the drag. The **live** tab-root header is **clipped to the uncovered leading strip** for the whole gesture (not gated on the commit midpoint) so the underlay title is visible in the peek without showing through translucent overlay glass. Tab chrome is never unhidden unless that tab layer is the current underlay — Map has no tab header, so starting a back gesture on Map must not paint Add Click / Home titles over the map.
- Destination chrome for overlay covers (Add Click under QR/NFC/Tap, Settings hub under a subpage, Map floating controls under Nearby) stays **bound**. Clip it; do not flip `LocalNativeChromeActive` off or toggle `hidden` — that remounts liquid glass and the destination header/controls pop in after the swipe completes. Overlay hide uses `CATransaction.setDisableActions(true)` and does not restack the tab glass plate.
- After a **completed** swipe, do not clear a full-width underlay mask (`clipLeadingUnderlay(null)` only when the destination is still covered, e.g. tap-dismiss at rest). Do not apply overlay `slideOffset = 0` identity while that chrome is still composed — `reset()` after land would snap My QR / Settings subpage titles back on-screen. Reset the overlay transform only after it is `hidden`. Unsuppressing tab chrome toggles hit-testing only; it must not re-set `glassPlate.hidden`.
- Finger tracking is 1:1. On lift, cancel and commit springs are slightly under-damped (`0.82` / `0.78`) and may overshoot rest / the trailing edge so both paths land with a small settle jiggle. Do not clamp settle to `0..width` or use `DampingRatioNoBouncy` on commit — that killed the landing bounce.

### 7.2 Interactive Elements

- Full-width horizontal drag (`useFullWidthHorizontalDrag = true` default).
- `rightToLeftPeek` optional (hub chat timestamps).
- `opaquePreviousBackground = false` for hub chat and Add Click overlays (list / tab persists underneath).
- Primary tab swipe-to-Home uses `previousContent = Home` inside `InteractiveSwipeBackContainer` (standard AnimatedContent child — not a persistent underlay shell). That underlay is composed with `LocalNativeChromeActive = false` so it never binds the shared tab `UINavigationBar`. Map in particular has no tab header; a stale Add Click title must not appear when the back gesture starts.

### 7.3 States

| State | iOS | Android |
|-------|-----|---------|
| **Swipe-back enabled** | See table below | **Never** for shell routes |
| **Gesture active** | Previous layer visible | N/A |
| **Settling** | Spring animation to commit or cancel | N/A |

### 7.4 Micro-copy

- None.

### 7.5 Flow Sequence

**iOS swipe-back destinations**:

| Screen | Swipe back target | Enabled when |
|--------|-------------------|--------------|
| `my_qr` | Previous `currentRoute` | `isSwipeBackScreen` |
| `qr_scanner` | Previous route | same |
| `nfc` | Previous route | same |
| Primary tabs (not Home, **not Connections**) | **Home** | `isPrimaryNavRoute` && not Connections |
| Connections tab | **No** swipe to Home | excluded |
| Home | **No** swipe | excluded |
| Hub chat | Dismiss hub (`hubChatArgs = null`) | iOS only |
| Connections in-chat | Chat overlay swipe | Connections screen internal |

**Android**: no `InteractiveSwipeBackContainer` on primary tabs; hub chat full screen without swipe container.

### 7.6 A11y & Responsive

- `PlatformBackHandler` disabled when `iOSSwipeOwnsBack` true (iOS handles edge swipe via notification bridge).
- Android uses `BackHandler` only.

### ASCII: iOS Swipe-Back Matrix

```
                    ┌─────────┬─────────┬─────────┬─────────┬──────────┐
                    │  Home   │ AddClick│  Clicks │   Map   │ Settings │
┌───────────────────┼─────────┼─────────┼─────────┼─────────┼──────────┤
│ Swipe back to Home│   NO    │   YES   │   NO    │   YES   │   YES    │
└───────────────────┴─────────┴─────────┴─────────┴─────────┴──────────┘

Overlays my_qr / qr_scanner / nfc  →  swipe back to tab route before open
Hub chat (iOS)                     →  swipe dismiss hub overlay
```

---

## 8. PlatformBackHandler

### 8.1 Layout / Container

- `expect/actual` in `ui/components/PlatformBackHandler.kt`.
- Android: `androidx.activity.compose.BackHandler`.
- iOS: `NSNotificationCenter` observer for `"ClickIOSBackSwipe"`.

### 8.2 Interactive Elements

Enabled in main scaffold when ANY:

- `mapPipExpanded`
- `showUnifiedSearchSheet`
- `hubChatArgs != null`
- `showMyQRCode` / `showQRScanner` / `showNfcScreen`
- Connection context sheet states
- `currentRoute != "home"`
- AND `!iOSSwipeOwnsBack`

**Back priority** (first match wins):

1. `mapPipExpanded` → false
2. `showUnifiedSearchSheet` → false
3. `hubChatArgs` → null
4. `showMyQRCode` → false
5. `showQRScanner` → false
6. `showNfcScreen` → false
7. Connection tagging/QR context → reset connection VM
8. `pendingChatId` → null
9. Else → `navigateBack(GestureBack)`

`UnifiedPopupOverlay` also registers back to animated dismiss.

### 8.3 States

| Platform | Behavior |
|----------|----------|
| **Android** | System back button/gesture |
| **iOS** | Native edge swipe notification when enabled |

### 8.4 Micro-copy

- None.

### 8.5 Flow Sequence

Back event → cascade dismiss overlays → pop route history.

### 8.6 A11y & Responsive

- iOS defers to swipe when `iOSSwipeOwnsBack` to avoid duplicate handlers.

---

## 9. UnifiedSearchSheet Entry

### 9.1 Layout / Container

- **Not** a route; `showUnifiedSearchSheet` boolean in `App.kt`.
- Rendered sibling to `Scaffold` inner `Box` (true screen bottom).
- `GlassAdaptiveBottomSheet` + `rememberGlassAdaptiveSheetState(skipPartiallyExpanded = false)` (opaque bordered sheet).
- Auto `sheetState.show()` after 32 ms delay.
- Content: opaque `surface` column, 12 dp horizontal padding, 2dp top border.

### 9.2 Interactive Elements

- Search `TextField` in 8 dp rounded bordered surface (`surface-container`, 2dp `#000`).
- Filter chips: All + per `SearchResultCategory`.
- Results list navigates to chat, map, beacon, settings (dismisses sheet).

### 9.3 States

| State | UI |
|-------|-----|
| **Default** | Sheet hidden |
| **Active** | Sheet expanded, focus requested @ 120 ms |
| **Loading** | `ClickLogoPulse` 72 dp center |
| **Empty query** | `EmptySearchHint` with search icon |
| **No results** | `"No results for \"{query}\""` |
| **Filtered empty** | Filter mismatch hint |

### 9.4 Micro-copy

- Placeholder: **"Search people, places, beacons, intents…"**
- Empty hint: **"Search for people, cliques, beacons,\navailability intents, messages, or places"**
- No results: **"No results for \"{query}\""**
- Filter empty: **"No results match the selected filters.\nTry another pill above."**
- Chip: **"All"**

### 9.5 Flow Sequence

```
HeaderSearchIconButton / onOpenSearch
    → showUnifiedSearchSheet = true
    → GlassAdaptiveBottomSheet
    → focus + search
    → onNavigate* → dismiss + route change
PlatformBackHandler / scrim → showUnifiedSearchSheet = false
```

**Entry points**: Home, Connections, Map, Settings (`onOpenSearch`).

### 9.6 A11y & Responsive

- `imePadding` on sheet; results `consumeWindowInsets(ime)`.
- List bottom pad = nav inset + 12 dp.

---

## 10. Hub Chat Overlay

### 10.1 Layout / Container

- `AnimatedVisibility(hubChatArgs != null)` full screen above tab content.
- Enter: slide from right 300 ms + fade 220 ms.
- Exit: slide right + fade unless `hubChatTransitionMode == GestureBack` (then none).

### 10.2 Interactive Elements

- iOS: `InteractiveSwipeBackContainer` wrapping `HubChatScreen`.
- Android: `HubChatScreen` only.
- Bottom bar hidden while open.

### 10.3 States

| State | Behavior |
|-------|----------|
| **Default** | Hidden |
| **Active** | Hub chat full screen |
| **Gesture back** | iOS dismiss without slide exit |
| **Verify loading** | Separate full-screen "Joining hub…" pulse overlay |

### 10.4 Micro-copy

- Hub verify: **"Joining hub…"**
- Snackbar errors: **"Location permission is required to join this hub."**, **"Could not read your location. Try again in an open area."**, **"Please sign in again to join the hub."**

### 10.5 Flow Sequence

Map/Connections hub select → `hubChatArgs = HubChatNavArgs(...)` → overlay → back clears args.

### 10.6 A11y & Responsive

- Timestamp peek integrates via `rightToLeftPeek` on iOS.

---

## 11. Call Overlay Z-Index Stack

### 11.1 Layout / Container

- Parent `Box` **`zIndex(11_000f)`** above disposable camera (10_500) and tether (70).
- `CallPreviewOverlay`: top card max width 324 dp, surface `#08101F` 94%, corner 28 dp.
- `ActiveCallOverlay`: max width 380 dp (94% fill), draggable on iOS.

### 11.2 Interactive Elements

**Preview**:

- Outgoing/Connecting: red end-call cancel.
- Incoming: decline (red) + accept (gradient).
- Ended: check dismiss.

**Active**:

- Mute, speaker, camera toggles; red end call.
- Video: remote full + local PiP 96×136 dp.

### 11.3 States

| Layer | Visible when |
|-------|--------------|
| **CallPreviewOverlay** | Outgoing, Incoming, Connecting, Ended (unless suppressed after active call) |
| **ActiveCallOverlay** | Connected or Ended tail while not preview-only |

Alpha animations: 420 ms `LinearOutSlowInEasing` on preview scale 0.96↔1 and active alpha.

### 11.4 Micro-copy

**CallPreviewOverlay labels**:

- **"Starting video ring"** / **"Starting voice ring"**
- **"Incoming video call"** / **"Incoming voice call"**
- **"Joining video call"** / **"Joining voice call"**
- Ended: `overlayState.reason`
- Subtitle: **"Video call"** / **"Voice call"**
- Default name: **"Connection"**

**ActiveCallOverlay labels**:

- **"Connecting video…"** / **"Connecting…"**
- **"Video call"** / **"Voice call"** (connected)
- Ended: `state.reason` or **"Call ended"**
- **"Waiting for remote video…"**
- **"Local preview"**
- **"Connecting audio…"** / **"Voice call in progress"**

**Content descriptions**: `"Cancel call"`, `"Decline call"`, `"Accept call"`, `"Dismiss"`, `"Mute"` / `"Unmute"`, speaker, camera, `"End call"`.

### 11.5 Flow Sequence

```
CallSessionManager.overlayState / callState
    → compute callPreviewVisible vs activeCallVisible
    → animate alpha/scale
    → preview OR active in z-index 11000 box
Ended tail: suppressEndedPreviewAfterActiveCall coordinates dismiss
```

### 11.6 A11y & Responsive

- Status bar top padding on cards.
- Active call card draggable within horizontal/vertical bounds.

---

## 12. GlobalTetherOverlay

### 12.1 Layout / Container

- Mounted in scaffold wrapper `Box`, **`zIndex(70f)`**, `Alignment.TopCenter`.
- `TetherCompassToast` with `padding(top = statusBarTop + 64.dp)`.
- Solid `primary` `#630ed4` banner, 16dp corners, 2dp `#000` border, Explore icon.

### 12.2 Interactive Elements

- Auto-visible 30_000 ms per payload; then fade + `EncounterTetherManager.clearActiveTetherPayload()`.
- No tap dismiss on global overlay (timer-driven).

### 12.3 States

| State | Message |
|-------|---------|
| **Location available** | Compass string |
| **No location** | Fallback ping string |
| **Dismissed** | Hidden |

### 12.4 Micro-copy

- With location: **`"{senderName} is {distance} {direction}"`** — e.g. `"Alex is 120 ft Northeast"` (`tetherCompassMessage`).
- Without location: **`"{senderName} pinged their tether"`**
- Directions: North, Northeast, East, Southeast, South, Southwest, West, Northwest.
- Distances: feet rounded per `formatDistanceFeet`.

### 12.5 Flow Sequence

```
EncounterTetherManager.activeTetherPayload
    → compute message (GPS)
    → TetherCompassToast 30s
    → clear payload
```

Chat thread also shows same toast locally in `ChatView` for peer pings.

### 12.6 A11y & Responsive

- Full width with 16 dp horizontal margin; bold `titleMedium` white on solid `primary` `#630ed4`.

---

## 13. OfflineStatusBanner

### 13.1 Layout / Container

- `Alignment.TopCenter`, **`zIndex(10f)`**, below status bar + 4 dp top padding.
- Pill shape 999 dp radius; `surfaceContainerHighest` 92% alpha; elevation 2 dp.
- CloudOff icon 14 dp + label.

### 13.2 Interactive Elements

- Non-interactive chip; driven by `connectivityViewModel.showOfflineBanner`.

### 13.3 States

| State | Visible |
|-------|---------|
| **Online** | Hidden |
| **Offline** | Banner shown |

### 13.4 Micro-copy

- Default message: **"Offline"**

### 13.5 Flow Sequence

`showOfflineBanner` true → render above scaffold (does not shift main content).

### 13.6 A11y & Responsive

- `labelSmall` onSurfaceVariant; icon decorative (`contentDescription = null`).

---

## 14. Snackbar Hosts

### 14.1 Layout / Container

| Host | Location |
|------|----------|
| Main | `Scaffold.snackbarHost` → `SnackbarHost(snackbarHostState)` |
| Onboarding | `Box` bottom center, 24 dp padding |

### 14.2 Interactive Elements

- Standard M3 `SnackbarHostState.showSnackbar(message)`.
- Sources: connection VM notices, `AppDataManager.transientUserMessages`, hub join errors, Click Drops open failures.

### 14.3 States

| State | Behavior |
|-------|----------|
| **Default** | Empty |
| **Active** | Snackbar visible with action optional (default none) |

### 14.4 Micro-copy

- Examples: **"Couldn't open Click Drops"**, hub permission/location errors, connection success/error strings.

### 14.5 Flow Sequence

Coroutine collectors → `showSnackbar` → auto-dismiss per M3.

### 14.6 A11y & Responsive

- Main host respects scaffold insets; onboarding host fixed bottom offset.

---

## 15. Click Drops Camera Overlay

### 15.1 Layout / Container

- `DisposableCameraView` in `AnimatedVisibility`, **`zIndex(10_500f)`**.
- Enter: fade 120 ms + scale from 0.08 spring; exit fade 140 ms + scale out 220 ms.
- Hides bottom bar while `showConnectionDisposableRoll || disposableRollOpening`.

### 15.2 Interactive Elements

- Photo confirm → send disposable roll via `chatViewModel`; dismiss.
- `onDismiss` clears roll state.

### 15.3 States

| State | UI |
|-------|-----|
| **Closed** | Tab bar visible |
| **Opening** | `disposableRollOpening` hides bar |
| **Open** | Full-screen camera |
| **Error** | Snackbar (see §14) |

### 15.4 Micro-copy

- Snackbar fallback: **"Couldn't open Click Drops"**

### 15.5 Flow Sequence

Connections/Map open roll → opening flag → camera visible → capture or dismiss.

### 15.6 A11y & Responsive

- Full-screen; above tabs, below calls (11_000).

---

## 16. Z-Index & Overlay Stack (Complete)

### ASCII: Main Authenticated Layer Cake (bottom → top)

```
┌─────────────────────────────────────────────── z = 11000 ─── Call preview + active call
├─────────────────────────────────────────────── z = 10500 ─── Click Drops camera
├─────────────────────────────────────────────── z = 80 ───── UnifiedPopup (when used)
├─────────────────────────────────────────────── z = 70 ───── GlobalTetherOverlay
├─────────────────────────────────────────────── z = 10 ───── OfflineStatusBanner
├─ Hub chat AnimatedVisibility (full screen, no explicit z)
├─ ConnectionRevealOverlay / context sheets
├─ AnimatedContent (tab + QR/NFC/MyQR screens)
├─ PlatformBottomBar (overlay, transparent chrome — z ≈ 5)
└─ Scaffold content / background (full-bleed under tab icons)
     UnifiedSearchSheet (sibling Box, glass sheet scrim)
```

### Z-Index State Table

| z-index | Component | Hides bottom bar? |
|---------|-----------|-------------------|
| 0 (base) | Tab content | — |
| 10 | Offline banner | No |
| 70 | Tether toast | No |
| 80 | Unified popup | No |
| 10_500 | Disposable camera | **Yes** |
| 11_000 | Call overlays | No (bar may show under preview card) |

---

## 17. Onboarding AnimatedContent (Pre-Main)

### 17.1 Layout / Container

- `AnimatedContent(onboardingStep)` horizontal slide 280 ms + fade 180 ms.
- Steps: welcome → interests (`else` branch) → avatar.

### 17.2 Interactive Elements

- Welcome: continue → VM advance.
- Interests: tag selection → Supabase save.
- Avatar: upload or skip.

### 17.3 States

| Step | Screen |
|------|--------|
| loading | Shimmer (outside AnimatedContent) |
| welcome | `WelcomeScreen` |
| interests | `InterestTaggingScreen` |
| avatar | `AvatarScreen` |
| complete | Exits to handoff/main |

### 17.4 Micro-copy

- Interest save error: **"Couldn't save interests. Check your connection and try again."**

### 17.5 Flow Sequence

Welcome acknowledged → Interests saved → Avatar set/skipped → `onboardingStep == complete"` → handoff shimmer → main.

### 17.6 A11y & Responsive

- Onboarding snackbar host at bottom (24 dp).

---

## 18. Home Entrance Animation

### 18.1 Layout / Container

- `homeRevealAlpha` animates shimmer overlay 360 ms.
- `homeSurfaceAlpha` fades main scaffold 320 ms after reveal.

### 18.2 Interactive Elements

- Non-interactive shimmer during `showHomeRevealOverlay`.

### 18.3 States

| Path | Timing |
|------|--------|
| Post-onboarding handoff | Shimmer 600 ms → reveal 380 ms |
| Initial complete (no prior step) | Reveal 180 ms |

### 18.4 Micro-copy

- None.

### 18.5 Flow Sequence

`hasPlayedHomeEntrance` latches true after first reveal.

### 18.6 A11y & Responsive

- Brief full-screen flash; content becomes interactive when `homeSurfaceVisible`.

---

## Source File Index

| Concern | Path |
|---------|------|
| Root gate + scaffold | `click/composeApp/src/commonMain/kotlin/compose/project/click/click/App.kt` |
| Tab definitions | `click/composeApp/src/commonMain/kotlin/compose/project/click/click/navigation/NavigationItem.kt` |
| Android bottom bar | `click/composeApp/src/androidMain/kotlin/.../BottomBar.android.kt` |
| iOS bottom bar | `click/composeApp/src/iosMain/kotlin/.../BottomBar.ios.kt` |
| Screen chrome | `click/composeApp/src/commonMain/kotlin/.../ScreenChrome.kt` |
| Tab scaffold | `click/composeApp/src/commonMain/kotlin/.../AppScreenScaffold.kt` |
| Swipe back | `click/composeApp/src/commonMain/kotlin/.../InteractiveSwipeBackContainer.kt` |
| Back handler | `click/composeApp/src/commonMain/kotlin/.../PlatformBackHandler.kt` |
| Search sheet | `click/composeApp/src/commonMain/kotlin/.../UnifiedSearchSheet.kt` |
| Call UI | `click/composeApp/src/commonMain/kotlin/.../calls/CallOverlays.kt` |
| Tether message | `click/composeApp/src/commonMain/kotlin/.../encounter/TetherCompass.kt` |
| Offline chip | `click/composeApp/src/commonMain/kotlin/.../OfflineStatusBanner.kt` |

---

*Document reflects as-built mobile shell only. No web. No backend. No redesign proposals.*
