# Navigation Module

## Module Purpose

The `navigation` package defines **tab-root routes and chrome** for Click's main shell. Primary routing logic lives in `App.kt` (AnimatedContent transitions, auth/onboarding gates, manual route stack, iOS swipe-back). This package supplies `NavigationItem` sealed types and `bottomNavItems` consumed by `PlatformBottomBar` and screen scaffolds.

---

## Architecture

### App.kt AnimatedContent routing

`App.kt` is the root navigation controller. It layers gates in order:

```
App()
 ├─ AuthState.Loading        → AppShimmerScreen
 ├─ !isAuthenticated         → LoginScreen / SignUpScreen
 ├─ profileGatePending       → AppShimmerScreen
 ├─ profileGateActive        → ProfileBasicsGateScreen
 ├─ onboardingStep != complete → AnimatedContent(Welcome | Interests | Avatar)
 ├─ onboardingHandoffActive  → AppShimmerScreen
 └─ Main shell
      ├─ PlatformBottomBar (bottomNavItems)
      ├─ AnimatedContent(activeScreenKey) — tab + overlay screens
      ├─ Modal overlays: NFC, QR scanner, My QR, Hub chat, Search sheet
      └─ Global overlays: ActiveCallOverlay, ConnectionRevealOverlay, GlobalTetherOverlay
```

**AuthState gate:** `authViewModel.authState` must be `AuthState.Success` before any post-login surface renders.

**OnboardingState gate:** `OnboardingViewModel.step` drives `onboardingStep` (`loading` → `welcome` → `interests` → `avatar` → `complete`). Persisted `OnboardingState` in `TokenStorage` survives process death.

### Manual route stack

Inside the main shell, navigation uses a **manual history stack** rather than Navigation Compose:

```kotlin
var currentRoute by remember { mutableStateOf("home") }
val routeHistory = remember { mutableStateListOf("home") }

fun navigateTo(route: String) {
    if (route != currentRoute) {
        routeHistory.add(route)
        previousRoute = currentRoute
        currentRoute = route
        transitionMode = NavigationTransitionMode.Tap
    }
}

fun navigateBack() {
    if (routeHistory.size > 1) {
        routeHistory.removeAt(routeHistory.lastIndex)
        currentRoute = routeHistory.last()
        transitionMode = NavigationTransitionMode.GestureBack
    }
}
```

`activeScreenKey` composes the effective screen: base tab route plus overlay flags (`showNfcScreen`, `hubChatArgs`, `showMyQRCode`, etc.).

### iOS swipe-back

On iOS (`isIOS`), eligible screens wrap content in `InteractiveSwipeBackContainer`:

- Enabled when `isSwipeBackScreen(screenKey)` and not on root tabs
- `NavigationTransitionMode.GestureBack` defers resetting to `Tap` mode by 80ms to avoid double animation
- `HubChatScreen` registers `InteractiveSwipeBackRightToLeftPeek` for timestamp peek during horizontal drag
- Chat threads inside Connections use swipe-back to dismiss chat overlay

Android uses system back / explicit back buttons without edge swipe.

### AnimatedContent transition modes

| Mode | Enter | Exit |
|------|-------|------|
| `Tap` | Slide in from right + fade | Slide out left + fade |
| `GestureBack` | Slide in from left | Slide out right |

Onboarding uses its own `AnimatedContent` with `targetState = onboardingStep`.

### NavigationItem

```kotlin
sealed class NavigationItem(route, title, icon, sfSymbol)
```

| Object | Route | Tab label | SF Symbol |
|--------|-------|-----------|-----------|
| `Home` | `home` | Home | `house.fill` |
| `AddClick` | `add_click` | Add Click | `plus.circle.fill` |
| `Connections` | `connections` | Clicks | `person.2.fill` |
| `Map` | `map` | Map | `location.fill` |
| `Settings` | `settings` | Settings | `gearshape.fill` |
| `Search` | `search` | Search | `magnifyingglass` |

`bottomNavItems` = Home, AddClick, Connections, Map, Settings (Search opens as a sheet, not a tab).

Each item carries a Material `ImageVector` for Android/Compose and an `sfSymbol` string for iOS native tab bar parity.

### AppScreenScaffold chrome

`ui/components/AppScreenScaffold.kt` is the standard layout for tab-root screens:

- **Floating liquid-glass header** — collapses on scroll (`headerCollapseFraction`)
- **LazyColumn body** — extends under bottom nav with `rememberBottomChromePadding()`
- **Optional search action** — `onOpenSearch` opens unified search sheet
- **Encounter tether banner** — `EncounterTetherManager` compass message when active
- **Presence indicator** — `presenceOnline` dot on header

Used by `HomeScreen`, `ConnectionsScreen` list mode, `MapScreen`, `SettingsScreen`.

`ScreenChrome.kt` / `PageHeader.kt` provide secondary header patterns with `AnimatedContent` title transitions.

### Deep link + push navigation hooks

`App.kt` observes:
- `ConnectionDeepLinkRouter.pendingConnectionUserId` → Add Click / handshake flow
- `ChatDeepLinkManager.pendingConnectionId` → Connections tab + `pendingChatId`
- `ChatDeepLinkManager.pendingCommunityHubId` → hub join flow

---

## Constraints

- **Single-activity shell** — all post-auth navigation is in-memory state; no Navigation Graph XML.
- **Route history is manual** — deep links must explicitly call `navigateTo` or set overlay flags.
- **iOS swipe-back only** — `InteractiveSwipeBackContainer` is gated on `isIOS`.
- **Bottom bar hidden** — when chat overlay, hub chat, NFC, or QR scanner is open.
- **Onboarding blocks tabs** — `bottomNavItems` not rendered until `onboardingStep == "complete"`.
- **Search is a sheet** — `NavigationItem.Search` exists for icon/symbol parity but opens `showUnifiedSearchSheet`.

---

## Related Files

| File | Role |
|------|------|
| `navigation/NavigationItem.kt` | Tab route definitions + `bottomNavItems` |
| `App.kt` | Root routing, AnimatedContent, route stack, gates |
| `ui/components/AppScreenScaffold.kt` | Tab-root layout chrome |
| `ui/components/PlatformBottomBar.kt` | Glass bottom navigation bar |
| `ui/components/InteractiveSwipeBackContainer.kt` | iOS edge swipe back |
| `ui/components/ScreenChrome.kt` | Secondary screen chrome |
| `viewmodel/OnboardingViewModel.kt` | Onboarding step machine |
| `viewmodel/AuthViewModel.kt` | Auth gate |
| `deeplink/ConnectionDeepLinkRouter.kt` | Connection URL routing |
| `notifications/ChatDeepLinkManager.kt` | Push → chat routing |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is reachable through the navigation shell this module defines.

### Connection & Discovery
- **Connect in person (Tri-Factor)** — Bump phones using ultrasonic audio tokens + BLE + sensor context to verify co-presence.
- **Scan QR** — Scan a peer's QR code to initiate a connection handshake.
- **Group connect (Multi-Tap)** — Several people tap together to form a verified group ("click").
- **QR identity card** — Share your personal QR / link so others can connect without bumping.
- **Community Hubs** — Join ephemeral venue chats tied to a place.
- **Map beacons** — Discover people and events pinned on the social map.
- **Match alerts** — Get notified when availability and interests align with someone nearby.
- **Availability intents** — Signal when you're free this week; others can lock overlapping gaps.
- **Core connections** — Pin your most important people; they stay visible even in ghost mode on the map.

### Messaging & Calls
- **Private encrypted chat** — End-to-end encrypted direct and group threads.
- **Send photos/files/voice notes** — Rich media in chat with encrypted upload.
- **Emoji reactions** — React to individual messages.
- **Typing & read receipts** — Live presence indicators in conversations.
- **Voice & video calls** — In-app WebRTC calls with push-wake on incoming rings.

### Memory & Context
- **Memory Capsules** — Rich encounter records: place, weather, noise, elevation, motion, lux.
- **48-hour gentle archive** — Connections softly archive after 48 hours unless both parties continue.
- **Connection map & timeline** — See where and when you met someone on a map and timeline.
- **Rate the vibe** — Post-connection sentiment feedback.

### Collaboration
- **Collaboration sessions & disposable rolls** — Re-bump existing friends to open a time-locked Disposable Roll camera window and squad map drops.

### Privacy & Safety
- **Ghost mode** — Pause location sharing and background sync for a private session.
- **Block & report** — Block users and report abusive behavior.

### Profile & Account
- **Profile & interests** — Avatar, name, birthday, and interest tags power discovery and matching.
- **Onboarding** — Welcome → interests → avatar flow for new accounts.
- **Google/email auth** — Sign in with Google OAuth or email/password via Supabase Auth.
- **Push notifications** — Message, call, archive, and reveal alerts.
- **Deep links & App Clip** — Open connections via `https://click-us.vercel.app/c/{id}` or `click://` URLs.
- **Web dashboard** — Manage connections and insights from the browser.
- **Business insights** — Opt-in analytics for venue/business partners.
- **Event reminders** — Day-of and one-hour-before beacon reminders.
- **Achievements & stats** — Connection streaks, stats, and milestones.

### Discovery & Search
- **Global search** — Search people, chats, hubs, and map content from one entry point.
