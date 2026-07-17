# 17 — Global Feedback (Toasts, Banners, Shimmer, Tether)

**Scope:** `UnifiedToastHost`, `UnifiedToastOverlay`, `GlassSnackbarHost`, `OfflineStatusBanner`, `AppShimmerScreen`, `TetherCompassToast`, motion tokens, z-index stack.  
**Source:** `ui/components/UnifiedToast.kt`, `ui/components/GlassSnackbarHost.kt`, `ui/components/GlassmorphicOverlay.kt`, `ui/components/OfflineStatusBanner.kt`, `ui/components/AppShimmerScreen.kt`, `ui/components/ClickLogoPulse.kt`, `ui/components/TetherCompassToast.kt`, `encounter/TetherCompass.kt`, `ui/components/AppScreenScaffold.kt`, `viewmodel/ConnectivityViewModel.kt`, `App.kt`  
**Out of scope:** Web (`aria-live` patterns), backend, redesign proposals.

---

## ASCII hierarchy

```
App shell (Box wrapper)
├── OfflineStatusBanner (z=10, top-center)
├── Scaffold
│   ├── Tab content / overlays
│   ├── snackbarHost (M3 SnackbarHost — session errors, Click Drops open fail)
│   └── PlatformBottomBar
├── GlobalTetherOverlay (z=70)
│   └── TetherCompassToast (30s auto-dismiss)
├── UnifiedPopup overlays (z=80)
├── ConnectionRevealOverlay (z=10000)
├── DisposableCameraView (z=10500)
└── Call overlays (z=11000)

Per-screen overlays:
├── ConnectionsListView / ChatView → GlassToastHost (z=50)
├── ChatView → TetherCompassToast inbound (z=60) + sender ack (z=61)
├── MapScreen → UnifiedToastOverlay grass nudge (no z-index)
└── Auth/onboarding gates → AppShimmerScreen (full-screen blocking)
```

---

## 1. Layout

### UnifiedToastHost (compact glass pill)

| Property | Value |
|----------|-------|
| Container | `Box(modifier, contentAlignment)` |
| Alignment | `CenterEnd` default; `Center` when `opaque = true` |
| Shape | `RoundedCornerShape(14.dp)` |
| Max width | `300.dp` |
| Text padding | horizontal `14.dp`, vertical `10.dp` |
| Typography | `bodyMedium` |
| Text color | `GlassSheetTokens.OnOled` (white @ 92%) |
| Variant glass | `background(GlassSheetTokens.GlassSurface)` — white @ 5% |
| Variant opaque | `background(GlassSheetTokens.OledBlack)` + `border(1.dp, GlassSheetTokens.GlassBorder)` |

### UnifiedToastOverlay ("Got it" pill)

| Property | Value |
|----------|-------|
| Container | `Box(modifier.fillMaxSize(), Alignment.Center)` |
| Pill | `LiquidGlassPill(cornerRadiusDp=28, backgroundStrength=0.85f)` |
| Pill horizontal padding | `24.dp` |
| Column padding | horizontal `8.dp`, vertical `4.dp` |
| Message | `bodyLarge`, `onSurface`, `TextAlign.Center` |
| Dismiss | `TextButton` with `dismissLabel` param |
| Default dismiss label | `"Got it"` |

### GlassSnackbarHost

Typealias wrapper — identical to `UnifiedToastHost` with `opaque = false`. **Never called in production**; callers use `GlassToastHost` directly.

### OfflineStatusBanner

| Property | Value |
|----------|-------|
| Shape | `RoundedCornerShape(999.dp)` (full pill) |
| Surface | `surfaceContainerHighest.copy(alpha = 0.92f)` |
| Elevation | tonal `2.dp`, shadow `2.dp` |
| Row padding | horizontal `12.dp`, vertical `6.dp` |
| Icon | `Icons.Default.CloudOff`, `14.dp`, `onSurfaceVariant` |
| Spacer | `6.dp` between icon and text |
| Text | `labelSmall`, `onSurfaceVariant` |
| Default message | `"Offline"` |
| Position | `TopCenter`, `statusBars` inset, `padding(top = 4.dp)`, `zIndex(10f)` |

### AppShimmerScreen

| Property | Value |
|----------|-------|
| Full bleed | `Box.fillMaxSize()` |
| Background | `BackgroundDark` (`#09090B`) or `BackgroundLight` (`#FAFAFA`) from `isDarkMode` |
| Content | `ClickLogoPulse(Modifier.fillMaxSize())` centered |

### ClickLogoPulse (child of shimmer)

| Property | Value |
|----------|-------|
| Logo size | `88.dp` default |
| Asset | `Res.drawable.click_logo` |
| Pulse duration | `2400ms` |
| Alpha range | `0.42f` ↔ `1f` |
| `contentDescription` | `"Loading"` |

### TetherCompassToast

| Property | Value |
|----------|-------|
| Modifier default | `fillMaxWidth()`, `padding(horizontal=16.dp, vertical=8.dp)` |
| Background | Horizontal gradient `#1D4ED8` → `#2563EB` → `#1D4ED8` |
| Shape | `RoundedCornerShape(28.dp)` (`GlassSheetTokens.BentoExteriorCorner`) |
| Inner padding | horizontal `18.dp`, vertical `16.dp` |
| Row | `Explore` icon (white) + `12.dp` gap + message text |
| Typography | `titleMedium`, `FontWeight.Bold`, white |
| Default visible duration | `30_000ms` (30 seconds) |

### Z-index stack (relative to shell)

| z-index | Component |
|---------|-----------|
| `11000` | Call preview + `ActiveCallOverlay` |
| `10500` | `DisposableCameraView` / Click Drops |
| `10000` | `ConnectionRevealOverlay` |
| `80` | `UnifiedPopup` overlay (`UnifiedPopupTokens.OverlayZIndex`) |
| `70` | `GlobalTetherOverlay` / `TetherCompassToast` |
| `61` | Chat tether sender ack |
| `60` | Chat tether inbound |
| `50` | `GlassToastHost` compact (Chat, Connections) |
| `40` | Map expanded chrome row |
| `10` | `OfflineStatusBanner` |
| base | Scaffold content, `PlatformBottomBar`, tab `AnimatedContent` |

**Notes:**

- `OfflineStatusBanner` overlays tab content; does not shift layout.
- M3 `SnackbarHost` in `Scaffold.snackbarHost` uses Material elevation, no explicit z-index.
- Map `UnifiedToastOverlay` (grass nudge) has no z-index; sits below map chrome (40).
- `UnifiedSearchSheet` is sibling `Box` outside scaffold — no fixed z-index.

---

## 2. Interactive

### UnifiedToastHost

| Behavior | Detail |
|----------|--------|
| Show | `state.show(scope, text, durationMs = 2_400L)` — cancels prior hide job |
| Dismiss | `state.dismiss()` — cancels job, clears message |
| Auto-dismiss | After `durationMs` (default **2400 ms**); only clears if message still matches |
| Replace | New message cancels previous timer |
| Tap | **Non-interactive** — timer-only |
| Tab obscure | `ConnectionsListView` calls `toastState.dismiss()` when list obscured by chat |

### UnifiedToastOverlay

| Behavior | Detail |
|----------|--------|
| Show | Controlled by `visible` param |
| Dismiss | `"Got it"` `TextButton` → `onDismiss` |
| Auto-dismiss | **None** — persists until user taps |
| Map grass nudge | `onDismiss` → `TelemetryBatcher.dismissGrassNudge()` |

### OfflineStatusBanner

| Behavior | Detail |
|----------|--------|
| Show | After `700ms` debounce when offline (`ConnectivityViewModel`) |
| Hide | Immediate when back online |
| Tap | **Non-interactive** — display-only |

### AppShimmerScreen

| Behavior | Detail |
|----------|--------|
| Display | Full-screen blocking loader |
| Dismiss | Controlled by parent gate state |
| Gestures | Blocked — no user dismiss |

### TetherCompassToast

| Behavior | Detail |
|----------|--------|
| Show | Non-null `displayMessage` → `visible = true` |
| Auto-dismiss | `delay(visibleDurationMs)` → fade out → `onDismissed()` |
| Global default | 30 seconds |
| Chat sender ack | **2400 ms** duration |
| Tap | **Non-interactive** — timer only |
| Global clear | `EncounterTetherManager.clearActiveTetherPayload()` on dismiss |

### Tether ping (sender)

| Behavior | Detail |
|----------|--------|
| Trigger | `onPingTether` in chat composer |
| Ack toast | `"Ping tether sent"` for 2.4s |
| Loading | `pingTetherLoading = tetherSenderAck != null` |
| Cooldown | `PING_COOLDOWN_MS = 900L` in manager |

---

## 3. States

### UnifiedToastHost visibility

| State | UI |
|-------|-----|
| Hidden | `state.message == null` |
| Visible | Pill with message text; enter/exit animation |
| Replacing | Prior timer cancelled; new message shown |

### UnifiedToastOverlay (map grass nudge)

| State | UI |
|-------|-----|
| Hidden | `frictionUi.showGrassNudge == false` OR `!mapGesturesEnabled` |
| Visible | Full-screen centered pill with grass nudge copy + `"Got it"` |

**Grass nudge trigger conditions** (`TelemetryBatcher`):

- `GRASS_NUDGE_MIN_DURATION_SEC = 240` (4 min session)
- `ACTIVE_PAN_WINDOW_MS = 45_000`
- Shows when: `actionTakenCount == 0`, `mapPanCount > 0`, elapsed ≥ 4 min, pan within last 45s

### OfflineStatusBanner

| State | UI |
|-------|-----|
| Online | Hidden |
| Offline (< 700ms) | Hidden (debouncing) |
| Offline (≥ 700ms) | Pill `"Offline"` below status bar |

### AppShimmerScreen gates

| Gate | Condition |
|------|-----------|
| Auth loading | `authState is Loading` OR `authShimmerVisible` |
| Auth shimmer tail | After loading ends, `delay(340)` before hiding |
| Profile gate | `profileGatePending` |
| Onboarding loading | `onboardingStep == "loading"` |
| Onboarding handoff | `onboardingHandoffActive \|\| shouldStartOnboardingHandoff` |
| Handoff timing | 600ms shimmer → 380ms `homeRevealAlpha` overlay |
| Initial home reveal | 180ms reveal overlay |
| Home tab loading | `HomeState.Loading` |

### TetherCompassToast

| State | Message template |
|-------|------------------|
| With receiver GPS | `"{senderName} is {distance} {direction}"` |
| Without receiver location | `"{senderName} pinged their tether"` |
| Sender ack (chat) | `"Ping tether sent"` |
| Peer name fallback (1:1) | `"Friend"` |
| Peer name fallback (group) | `"Someone"` |

**Distance formatting** (`formatDistanceFeet`):

| Range | Format |
|-------|--------|
| < 100 ft | `"{feet} ft"` (e.g. `"47 ft"`) |
| < 1000 ft | Round to nearest 10 ft (e.g. `"120 ft"`) |
| ≥ 1000 ft | Round to nearest 100 ft (e.g. `"1200 ft"`) |

**Direction labels** (`compassDirectionLabel`):

`"North"`, `"Northeast"`, `"East"`, `"Southeast"`, `"South"`, `"Southwest"`, `"West"`, `"Northwest"`

**Example:** `"Alex is 120 ft Northeast"`

### Chat tether filtering

- Ignores own pings (`senderId == currentUserId`)
- 1:1: only peer pings
- Group: all member pings
- Two stacked toasts at same position (z 60 vs 61) may visually overlap

---

## 4. Micro-copy

### UnifiedToastOverlay

| Key | String |
|-----|--------|
| Dismiss default | `"Got it"` |
| Map grass nudge | `"Looking for the right vibe? Try dropping a 'Looking for Coffee' intent and let the map come to you. Put your phone in your pocket and we'll vibrate when a match is nearby."` |

### OfflineStatusBanner

| Key | String |
|-----|--------|
| Default message | `"Offline"` |

### AppShimmerScreen / ClickLogoPulse

| Key | String |
|-----|--------|
| Logo a11y | `"Loading"` |

### TetherCompassToast templates

| Key | String |
|-----|--------|
| With location | `"{senderName} is {distance} {direction}"` |
| Without location | `"{senderName} pinged their tether"` |
| Sender ack | `"Ping tether sent"` |
| Peer fallback | `"Friend"` |
| Group fallback | `"Someone"` |

### Tether direction labels

| Key | String |
|-----|--------|
| North | `"North"` |
| Northeast | `"Northeast"` |
| East | `"East"` |
| Southeast | `"Southeast"` |
| South | `"South"` |
| Southwest | `"Southwest"` |
| West | `"West"` |
| Northwest | `"Northwest"` |

### UnifiedToastHost example strings (via `ChatViewModel.nudgeResult` and callers)

| Key | String |
|-----|--------|
| Nudge sent | `"Nudge sent to {name}! 👋"` |
| Nudge failed | `"Failed to send nudge"` |
| Click created | `"Click created"` |
| Click create fail | `"Couldn't create click"` |
| Duplicate group | `"You already have a verified click with this group."` |
| Connection archived | `"Connection archived"` |
| Connection unarchived | `"Connection unarchived"` |
| Added to Core | `"Added to Core"` |
| Add Core fail | `"Couldn't add to Core"` |
| Removed from Core | `"Removed from Core"` |
| Remove Core fail | `"Couldn't remove from Core"` |
| Connection removed | `"Connection removed"` |
| Remove fail | `"Failed to remove connection"` |
| User blocked | `"User blocked"` |
| Block fail | `"Could not block user"` |
| Report submitted | `"Report submitted"` |
| Report fail | `"Failed to submit report"` |
| Left group | `"You left the group"` |
| Leave fail | `"Could not leave group"` |
| Group deleted | `"Group deleted"` |
| Delete fail | `"Could not delete group"` |
| Hub left | `"You left the hub"` |
| Hub deleted | `"Hub deleted"` |
| Click Drops open fail | `"Couldn't open Click Drops"` |
| Media blocked (Android) | `"Couldn't read that photo. If access was denied, enable Photos & videos permission for Click in Settings."` |
| Media blocked (iOS) | `"Couldn't read that photo. Enable Photos access for Click in Settings."` |

### Motion token labels (animation debug)

| Key | String |
|-----|--------|
| Compact toast | `"unified_toast_compact"` |
| Overlay toast | `"unified_toast_overlay"` |

---

## 5. Flow

### Compact toast (Connections / Chat)

```
User action (nudge, archive, core, media error, etc.)
  → ChatViewModel sets nudgeResult / caller invokes toastState.show()
  → UnifiedToastHost visible (z=50)
  → Enter: slide up 1/3 + fade (240ms iOS 280ms)
  → Auto-dismiss after 2400ms
  → Exit: slide down + fade (180ms)
  → Connections: dismissed early if list obscured by chat overlay
```

**Chat opaque variant:** `opaque = true` (OLED black + border) for composer-adjacent feedback.

### Map grass nudge overlay

```
Map session ≥ 4 min + pans within 45s + no actions taken
  → frictionUi.showGrassNudge = true
  → UnifiedToastOverlay centered pill
  → User taps "Got it"
  → TelemetryBatcher.dismissGrassNudge()
  → Overlay hidden (no auto-dismiss)
```

### Offline banner

```
Connectivity lost
  → delay 700ms (debounce)
  → Re-check still offline
  → OfflineStatusBanner appears (z=10, top-center)
Connectivity restored
  → Banner hidden immediately
```

### App shimmer gates

```
Auth Loading / profile gate / onboarding loading
  → AppShimmerScreen full-screen (#09090B or #FAFAFA)
  → ClickLogoPulse "Loading"
Gate clears
  → Optional tail delay (340ms auth, 600ms handoff)
  → homeRevealAlpha fade (180–380ms)
  → Main content revealed
```

### Global tether toast

```
Realtime tether_ping on room:encounter_{id}
  → EncounterTetherManager.activeTetherPayload set
  → GlobalTetherOverlay (z=70, top padding statusBar+64dp)
  → TetherCompassToast: "{name} is {distance} {direction}"
  → 30s timer
  → Fade out (180ms) → clearActiveTetherPayload()
```

### Chat tether toasts

```
Inbound peer ping (not own, filtered by chat type)
  → TetherCompassToast z=60, 30s duration

Sender taps ping tether
  → "Ping tether sent" ack z=61, 2400ms duration
  → EncounterTetherManager.pingTether()
```

### M3 Snackbar (app scaffold — distinct from GlassToastHost)

```
Session errors, Click Drops open fail, name update fail (some)
  → Scaffold.snackbarHost → SnackbarHost
  → Material elevation, no explicit z-index
```

**Map/Home note:** Some screens use M3 snackbar for `nudgeResult`; Connections/Chat prefer `GlassToastHost`.

---

## 6. A11y

### Per-component audit

| Component | Live region | Semantics | Icon CD | Announced on appear |
|-----------|-------------|-----------|---------|---------------------|
| `UnifiedToastHost` | ❌ | ❌ | N/A | Unreliable |
| `UnifiedToastOverlay` | ❌ | ❌ (button only) | N/A | Unreliable |
| `GlassSnackbarHost` | ❌ | ❌ | N/A | Same as host |
| `OfflineStatusBanner` | ❌ | ❌ | `null` | Text visible only |
| `AppShimmerScreen` | ❌ | ❌ | **`"Loading"`** on logo | Static CD only |
| `TetherCompassToast` | ❌ | ❌ | `null` | Text visible only |

### Confirmed gaps (no live region)

**Repo-wide:** No `liveRegion`, `aria-live`, or `LiveRegion` in any mobile Compose toast/banner file.

**Web parity gap:** Web uses `aria-live="polite"` in components like `LiveConnectionTicker.tsx` — mobile has no equivalent.

**Contrast pattern elsewhere:** `ConnectionArchiveWarningBanner` uses `.semantics(mergeDescendants = true) { contentDescription = summary }` — feedback components do **not** follow this pattern.

### Screen reader impact

- Toasts appear/disappear via `AnimatedVisibility` without polite/assertive announcements.
- Auto-dismiss (2.4s compact, 30s tether) may occur before exploration completes.
- Decorative icons explicitly silenced (`contentDescription = null`).
- `"Got it"` overlay button is the only explicit interactive a11y target in feedback overlays.
- `ClickLogoPulse` announces `"Loading"` once — pulse animation not described.
- Tether countdown/distance updates not announced.
- Two stacked tether toasts (z 60 + 61) may confuse focus order.

### Motion tokens (for reference)

| Token | Value |
|-------|-------|
| `UnifiedToastTokens.EnterMillis` | `240` (iOS compact: `280`) |
| `UnifiedToastTokens.ExitMillis` | `180` |
| `UnifiedToastTokens.DefaultDurationMs` | `2_400L` |
| `UnifiedToastTokens.CompactCornerDp` | `14` |
| `UnifiedToastTokens.OverlayCornerDp` | `28` |
| `UnifiedToastTokens.MaxWidthDp` | `300` |
| Overlay enter spring | `DampingRatioLowBouncy`, `StiffnessMediumLow`, scale `0.92f` |
| Overlay exit spring | `StiffnessMedium`, scale `0.96f` |
| `ClickLogoPulse` duration | `2400ms`, alpha `0.42f` ↔ `1f` |
| `TETHER_TOAST_VISIBLE_MS` | `30_000L` |
| Offline debounce | `700ms` |
| Grass nudge min session | `240s` |
| Grass nudge pan window | `45_000ms` |

### Test guardrails (`UnifiedToastTokensTest`)

- Enter: 200–300 ms
- Exit: 150–220 ms
- DefaultDuration ≥ 2000 ms

---

## Related documents

- [01-design-system.md](01-design-system.md) — `GlassSheetTokens`, motion §13, z-index §16
- [02-shell-navigation.md](02-shell-navigation.md) — shell mount, z-index stack §12–16, snackbar host
- [07-connections-inbox.md](07-connections-inbox.md) — `GlassToastHost` on FAB row
- [08-chat.md](08-chat.md) — chat tether toasts, opaque toast variant
- [10-map-beacons-hubs.md](10-map-beacons-hubs.md) — map grass nudge context
- [15-collaboration-drops.md](15-collaboration-drops.md) — `"Couldn't open Click Drops"` snackbar
