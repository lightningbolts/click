# 02 — Shell, Navigation & Chrome

**Role:** App-level continuity and the always-visible chrome users judge subconsciously.  
**Code hotspots:** `App.kt`, tab/navigation items, platform tab bar actuals, `AppScreenScaffold`, `LiquidGlassPageHeader` / `PageHeader`, `InteractiveSwipeBackContainer`, global overlays (search, calls, toasts), `UnifiedSearchSheet`.

---

## 1. Goals

| Goal | User feel |
|------|-----------|
| Click-branded liquid glass tab bar | Modern material chrome that still reads as **Click** (purple accent, FC borders/energy) — not stock iOS generic glass |
| Seamless shell | Tab content, floating headers, and overlays share one spatial model |
| Predictable back | System back + interactive swipe-back + sheet dismiss never surprise |
| **P0 Home continuity** | Back-gesture onto Home is rock-solid — **no flicker** (still broken on latest build) |
| Overlay etiquette | Search, calls, toasts, popups layer cleanly without fighting gestures |

---

## 2. Non-goals

- Redesigning IA or tab count / destinations  
- Replacing Functional Clarity cards/sheets app-wide with glass  
- Reviving the **reverted** Home persistent-underlay hack that broke tab transitions (`#26`) — find a different correct fix  

---

## 3. Liquid glass navigation bar (priority chrome)

### 3.1 Intent

Evolve the **bottom tab bar** into a **liquid-glass material** that:

- Feels premium and fluid (blur/refraction appropriate to platform capabilities)  
- Is **custom-themed** to Click: primary `#630ed4` active treatment, hard-edge or FC-aligned selected state, light/dark awareness  
- Keeps **content visible beneath** icons (continuation `#23` — transparent chrome so page materials match under the bar)  
- Remains readable and tappable (44pt targets, clear selected vs unselected)  

### 3.2 Design reconciliation

Functional Clarity docs currently describe solid bordered bars. This polish track **intentionally updates tab chrome** toward liquid glass **while keeping** FC product surfaces (cards, sheets, buttons) as they are. Document the exception in the plan; do not silently glass-ify every `Glass*` API.

### 3.3 Platform notes

| Platform | Direction |
|----------|-----------|
| iOS | Prefer system-capable material / blur APIs behind Compose tab content; customize selected indicator to Click (solid primary disc or FC-aligned mark — avoid rainbow/generic) |
| Android | Approximate with theme-aware scrim + controlled blur/tonal elevation if true liquid glass isn’t available; same interaction model as iOS |
| Both | Active tab response: springy icon/indicator; light haptic on select (optional, once) |

### 3.4 Required outcomes

- Light and dark both look intentional (no white bar in dark, no muddy smear).  
- Ghost mode / special map states: bar stays legible.  
- Keyboard open on non-chat tabs: bar policy explicit (hide vs stay — pick one product rule).  
- No opaque “band” that makes under-bar content a different color than above-bar content.  

### 3.5 Success criteria

- Side-by-side light/dark screenshots: bar clearly Click, not default OS.  
- Scroll content under bar: material samples background without muddy text collision on icons.  
- Rapid tab switching: indicator motion continuous; content transition matches existing shell springs.  

---

## 4. Tab content transitions

### 4.1 Required outcomes

- `AnimatedContent` (or successor) keeps **primary tab** continuity; overlays for Add Click flows stay mounted as today.  
- Crossfade/spring timings feel related to toast/sheet springs (shared motion tokens from part 01).  
- Heavy tabs (Map) must not hitch the transition — defer expensive work until after enter.  
- Returning to a tab restores scroll position and ephemeral UI (segment selection) unless product says otherwise.  

### 4.2 Anti-patterns

- Destroying and recreating Map/Home solely to “refresh”  
- Flash of AppShimmer on ordinary tab return  
- Different transition per tab with no shared language  

---

## 5. Interactive swipe-back & underlays

### 5.0 P0 — Back-gesture to Home flickers (OPEN)

Same bug as [01-motion-performance.md §5.0](01-motion-performance.md). Shell-level ownership lives in `App.kt` tab hosting + whatever route uses `InteractiveSwipeBackContainer` with Home as the underlay/previous tab.

**Plan requirements:**

1. Enumerate every gesture path that reveals Home (Map, Settings, Add Click overlays, events full-screen if applicable).  
2. Explain why Home visually restarts today (dispose, enter transition replay, header remount, etc.).  
3. Propose a fix that keeps Home identity stable **and** preserves primary-tab `AnimatedContent` crossfade.  
4. Explicitly reject re-applying the reverted `#26` underlay unless reinvented in a form that does not break Map→Home.  
5. Device acceptance: drag, cancel, and commit onto Home — all flicker-free.

### 5.1 Required outcomes

- One physics model: commit fraction, flick velocity, parallax peek ratio — already centralized; **all** routes use it.  
- During drag: previous content visible and interactive-looking (not empty/white) — **especially Home**.  
- On cancel: zero visual discontinuity.  
- On commit: teardown of disposed route **after** settle (chat leave pattern).  
- Right-to-left peek integrations (chat timestamps) must not break back gesture — keep cooperative API.  

### 5.2 Surfaces using swipe-back today

Chat (iOS overlay), Add Click My Code / Scan / Tap, Map events full-screen, **returns that reveal Home**, other `InteractiveSwipeBackContainer` callers — **audit all call sites** in the one-shot plan.

---

## 6. Headers & scaffolds

### 6.1 Required outcomes

- Floating headers (`LiquidGlassPageHeader` / scaffold flags) share vertical alignment with status bar across Home / Clicks / Map / Settings.  
- Header material: either stay FC-solid or adopt a **subtle** glass treatment consistent with tab bar — pick one rule in the plan; no random mix per tab.  
- Title/subtitle changes (`AnimatedContent` online status, greetings) should crossfade without layout jump.  

---

## 7. Global overlays & layering

Z-order and gesture ownership must stay obvious:

| Layer (conceptual) | Examples |
|--------------------|----------|
| Base tabs | Home, Add Click, Clicks, Map, Settings |
| Route overlays | Chat, events list, My Code, Scan |
| Sheets / popups | Profile, search, forms, action sheets |
| Feedback | Toasts, offline banner, tether |
| Calls | Preview + active call (highest) |

### Required outcomes

- Opening search does not reset tab scroll.  
- Toast does not block tab bar taps after dismiss animation.  
- Call overlay appearance uses polished enter/exit (part 04) without dropping the shell underneath.  

---

## 8. Consistency with sheets & chrome primitives

Align naming vs rendering:

- Many `Glass*` / `LiquidGlass*` APIs render brutalist today — liquid glass **nav** is the new exception.  
- Plan should list which chrome pieces become glass vs stay opaque.  
- Prefer extending `AppScreenScaffold` / shared header / shared icon button rather than per-screen copies.  

---

## 9. Acceptance pack (device)

- [ ] **P0:** Back-gesture onto Home — no flicker (all entry paths)  
- [ ] Tab bar reads as Click liquid glass in light + dark  
- [ ] Content color continuous under bar (`#23` preserved)  
- [ ] Tab switch + return: no flash, scroll preserved (Map↔Home crossfade intact)  
- [ ] Swipe-back on chat + Add Click overlays + events: continuous underlay  
- [ ] Search / toast / call layering: no stuck gesture sinks  
