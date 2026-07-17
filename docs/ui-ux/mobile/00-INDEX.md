# Click Mobile — Consumer UI/UX Blueprint Index

**Product:** Click — Anti-doomscrolling · Stop scrolling, start living.  
**Scope:** Kotlin Multiplatform mobile app (`click/`) — Android + iOS Compose UI, App Clip handshake, CallKit/PushKit overlays.  
**Out of scope:** Web companion (`click-web/`), B2B Insights, Admin, business signup, backend/APIs/Edge Functions/RLS. Network and permission failures appear only as **user-visible** states.  
**Source of truth:** As-built Compose screens and components (not a redesign).  
**Date:** 2026-07-16  

**Regression / QA:** After major changes, run [../../regression-testing/00-INDEX.md](../../regression-testing/00-INDEX.md) (full checklist, smoke, known-issues audit). These UI/UX files describe expected behavior; they are not a test plan.

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
| [01-design-system.md](01-design-system.md) | Color, type, glass tokens, sheets, popups, toasts, adaptive primitives |
| [02-shell-navigation.md](02-shell-navigation.md) | App gates, 5-tab shell, swipe-back, global overlays |
| [03-auth.md](03-auth.md) | Login, Sign Up, OAuth, validation, auth errors |
| [04-onboarding-gates.md](04-onboarding-gates.md) | Profile basics, Welcome, Interests, Avatar; legacy permission screens |
| [05-home.md](05-home.md) | Home feed, reconnect, archive banner, stats, availability entry |
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
| Tab bar | Material3 `NavigationBar`, 80dp + inset | Native `UITabBar`, 49dp; iOS 26+ liquid glass pin |
| Ripple | Enabled | Disabled |
| Card border | 1dp, lower glass alpha | 0.5dp, higher glass alpha |
| Button corners | Often 12–16dp / 28dp pill | Often 12–14dp, flat elevation |
| Modal back | System back / `BackHandler` | Edge swipe (`InteractiveSwipeBackContainer`) |
| Chat open | `AnimatedContent` push | Overlay + swipe-back with list peek |
| Incoming calls | App overlay (+ FCM) | CallKit / PushKit native UI |
| Camera / photos | Photos & videos permission copy | Photos / iCloud-specific copy |

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
