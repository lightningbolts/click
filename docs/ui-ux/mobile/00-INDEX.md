# Click Mobile — Consumer UI/UX Blueprint Index

**Product:** Click — Anti-doomscrolling · Stop scrolling, start living.  
**Scope:** Kotlin Multiplatform mobile app (`click/`) — Android + iOS Compose UI, App Clip handshake, CallKit/PushKit overlays.  
**Out of scope:** Web companion (`click-web/`), B2B Insights, Admin, business signup, backend/APIs/Edge Functions/RLS. Network and permission failures appear only as **user-visible** states.  
**Source of truth:** Functional Clarity target-state Compose UI (neo-brutalist revamp) — opaque surfaces, 2px `#000` borders, primary `#630ed4`; not as-built glass. Design tokens: [../../design-assets/functional_clarity/DESIGN.md](../../design-assets/functional_clarity/DESIGN.md).  
**Date:** 2026-07-17  

**Regression / QA:** After major changes, run [../../regression-testing/00-INDEX.md](../../regression-testing/00-INDEX.md) (full checklist, smoke, known-issues audit). These UI/UX files describe expected behavior; they are not a test plan.

**Continuation status (addressed / open / Track C):** [../../handoff/functional-clarity-continuation.md](../../handoff/functional-clarity-continuation.md).

---

## How to read this set

Every feature file follows this order:

1. **Layout / Container** — molecule → organism hierarchy + source paths  
2. **Interactive Elements** — tap, long-press, drag, keyboard, swipe, haptics  
3. **States** — Default | Pressed/Highlighted | Active | Focus | Disabled | Loading | Empty | Error | Success  
4. **Micro-copy** — labels, placeholders, validation, toasts (quoted from code)  
5. **Flow Sequence** — happy path + exception flows + back / deep-link / overlay dismiss  
6. **A11y & Responsive** — semantics, focus order, iOS vs Android deltas  

Touch platforms have no Hover; **Pressed/Highlighted** stands in for Hover.

---

## Document map

| File | Covers |
|------|--------|
| [01-design-system.md](01-design-system.md) | Functional Clarity tokens, bordered cards/sheets, popups, toasts, adaptive primitives |
| [02-shell-navigation.md](02-shell-navigation.md) | App gates, 5-tab shell, swipe-back, global overlays |
| [03-auth.md](03-auth.md) | Login, Sign Up, OAuth, validation, auth errors |
| [04-onboarding-gates.md](04-onboarding-gates.md) | Profile basics, Welcome, Interests, Avatar; legacy permission screens |
| [05-home.md](05-home.md) | Home IA: greeting, Featured Event, dynamic explore, reconnect, availability, stats |
| [06-connect-handshake.md](06-connect-handshake.md) | Add Click, QR, Tap/NFC, App Clip, context sheet, reveal |
| [07-connections-inbox.md](07-connections-inbox.md) | Clicks inbox, segments, action sheets, verified click create |
| [08-chat.md](08-chat.md) | 1:1 & group chat, composer, bubbles, icebreaker, vibe check |
| [09-calls.md](09-calls.md) | Preview & active call overlays, CallKit handoff |
| [10-map-beacons-hubs.md](10-map-beacons-hubs.md) | Discovery, map, beacons, hubs, hub chat |
| [11-search.md](11-search.md) | Unified search sheet |
| [12-profile-memories.md](12-profile-memories.md) | Profile sheets, timeline, media, memories list |
| [13-availability.md](13-availability.md) | Intent sheet, toggles, mutual match cards |
| [14-settings-privacy.md](14-settings-privacy.md) | Settings sections, ghost mode, permissions hub |
| [15-collaboration-drops.md](15-collaboration-drops.md) | Click Drops disposable camera roll |
| [16-safety.md](16-safety.md) | Block, report, remove, message delete |
| [17-global-feedback.md](17-global-feedback.md) | Toasts, offline banner, shimmer, tether toast |

---

## Product map (mobile)

Click is a proximity-first social utility. Users form **in-person connections** via Tri-Factor handshake (BLE + ultrasonic + GPS), QR, or App Clip; then chat (E2EE), call, drop map beacons, join ephemeral venue hubs, and manage a 48-hour gentle archive.

### Primary user goals

1. Sign in / complete onboarding  
2. Connect in person (Tap, QR, App Clip)  
3. Message / react / call  
4. Discover hubs & beacons on map  
5. Set availability intents  
6. Manage privacy (ghost mode, permissions)  
7. Block / report when needed  

```
                    ┌──────────────┐
                    │ Cold boot    │
                    │ AppShimmer   │
                    └──────┬───────┘
           unauthenticated │ authenticated
              ┌────────────┴────────────┐
              ▼                         ▼
         Login/SignUp            Profile gate?
              │                    │ yes → ProfileBasicsGate
              └────────┬───────────┘
                       ▼
              Onboarding incomplete?
                 Welcome → Interests → Avatar
                       ▼
                 ┌─────────────┐
                 │ Main shell  │
                 │ 5 tabs +    │
                 │ overlays    │
                 └─────────────┘
```

---

## Bottom tabs

| Route | Title | Material icon | iOS SF Symbol |
|-------|-------|---------------|---------------|
| `home` | Home | Home | `house.fill` |
| `add_click` | Add Click | Add | `plus.circle.fill` |
| `connections` | Clicks | Person | `person.2.fill` |
| `map` | Map | LocationOn | `location.fill` |
| `settings` | Settings | Settings | `gearshape.fill` |

Search is **not** a tab; it opens as `UnifiedSearchSheet` from headers.

---

## Android vs iOS — surface deltas (summary)

| Area | Android | iOS |
|------|---------|-----|
| Tab bar | Material3 `NavigationBar`, 80dp + inset; **solid bordered bar**, 2dp top `#000` border | Native `UITabBar`, 49dp; **solid bordered bar** (not liquid glass); active tab = solid `#630ed4` circle behind icon |
| Ripple | Enabled on M3 buttons | Disabled |
| Card border | **2dp** `#000` hard border | **2dp** `#000` hard border |
| Button corners | **8dp** radius, solid primary fill | **8dp** radius, solid primary fill |
| Press feedback | 2dp translate + instant darken; ripple on buttons | 2dp translate + instant darken only |
| Modal back | System back / `BackHandler` | Edge swipe (`InteractiveSwipeBackContainer`) |
| Chat open | `AnimatedContent` push | Overlay + swipe-back with list peek |
| Incoming calls | App overlay (+ FCM) | CallKit / PushKit native UI |
| Camera / photos | Photos & videos permission copy | Photos / iCloud-specific copy |

---

## Design-asset mock mapping

HTML/PNG mocks under `click/docs/design-assets/` map to feature docs as visual references for the Functional Clarity revamp. Where no mock exists, screens are **invented from the design system**.

| Design-asset folder | Feature doc(s) | Notes |
|---------------------|----------------|-------|
| `functional_clarity/` | [01-design-system.md](01-design-system.md) | Token source of truth (`DESIGN.md`) |
| `home/` | [05-home.md](05-home.md) | Greeting, Featured Event, dynamic explore; mock is hierarchy-only (see `home/README.md`) |
| `settings/` | [14-settings-privacy.md](14-settings-privacy.md) | Settings sections, toggles |
| `chat/` | [07-connections-inbox.md](07-connections-inbox.md), [08-chat.md](08-chat.md) | Inbox rows + thread chrome |
| `add_click_streamlined_header/`, `add_click_fixed_navigation/` | [06-connect-handshake.md](06-connect-handshake.md) | Add Click hub, QR/Tap entry |
| `map_events_full_screen_map/`, `events_discovery_with_real_mini_map/`, `event_details_expanded_dark/` | [10-map-beacons-hubs.md](10-map-beacons-hubs.md) | Discovery feed, map PiP, events |
| *(invented)* | [02-shell-navigation.md](02-shell-navigation.md) | Tab shell, gate stack, overlays |
| *(invented)* | [03-auth.md](03-auth.md) | Login / Sign Up |
| *(invented)* | [04-onboarding-gates.md](04-onboarding-gates.md) | Welcome, interests, avatar gates |
| *(invented)* | [09-calls.md](09-calls.md) | Call preview / active overlays |
| *(invented)* | [11-search.md](11-search.md) | Unified search sheet |
| *(invented)* | [12-profile-memories.md](12-profile-memories.md) | Profile sheets, memories |
| *(invented)* | [13-availability.md](13-availability.md) | Availability intent sheet |
| *(invented)* | [15-collaboration-drops.md](15-collaboration-drops.md) | Click Drops camera |
| *(invented)* | [16-safety.md](16-safety.md) | Block / report / delete sheets |
| *(invented)* | [17-global-feedback.md](17-global-feedback.md) | Toasts, offline banner, shimmer |

---

## Source root

```
click/composeApp/src/commonMain/kotlin/compose/project/click/click/
  App.kt
  ui/theme/
  ui/components/
  ui/screens/
  ui/chat/
  ui/camera/
  calls/
  navigation/
```

Platform actuals: `androidMain/.../ui/`, `iosMain/.../ui/`.

**Note:** Compose source may still reference `GlassCard`, `LiquidGlassPill`, `GlassAdaptiveBottomSheet`, etc. In the Functional Clarity target, these APIs render as **opaque bordered surfaces** — see [01-design-system.md](01-design-system.md) for token mapping.
