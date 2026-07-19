# 01 — Motion & Performance Foundations

**Role:** Non-negotiable feel layer. If this fails, higher-level polish is wasted.  
**Code hotspots:** `LazyColumn`/`LazyRow` screens, `ChatMessageTimeline`, `App.kt` `AnimatedContent`, `InteractiveSwipeBackContainer`, `ChatKeyboardDock`, sheet gesture physics, `GlassSheetGesturePhysics`.

---

## 1. Goals

| Goal | User feel |
|------|-----------|
| Zero scroll stutter | Finger and content stay glued; no hitch on fling, image decode, or realtime inserts |
| Continuous keyboard | Composer + thread + sheets rise/fall with the keyboard — no snap, lag, or double-lift |
| Stable identity | Returning from a gesture (swipe-back, sheet dismiss, tab return) does **not** look like a cold remount |
| **P0 Home back-gesture** | Back onto Home never flickers (still broken on latest build — must fix) |
| Shared physics language | Springs, dismiss thresholds, press translate, and haptics feel like one product |
| Frame honesty | Animations never mask jank; prefer fewer, cheaper layers over decorative blur during scroll |

---

## 2. Non-goals

- New visual theme or illustration system  
- Rewriting networking / pagination architecture (fix UI symptoms only; flag deep data issues)  
- Adding heavy shared-element systems app-wide unless a single high-value transition needs it  

---

## 3. Scroll & list performance

### 3.1 Surfaces to audit (all must feel continuous)

| Surface | Likely entry |
|---------|----------------|
| Home feed | `HomeScreen` / home components |
| Clicks inbox | `ConnectionsScreen` / `ConnectionItem` / Remember Me strip |
| Chat timeline | `ChatMessageTimeline` / bubbles / media |
| Hub chat | `HubChatScreen` |
| Map discovery / events list | `MapScreen` / discovery layouts |
| Search results | `UnifiedSearchSheet` |
| Profile / memories timelines | profile sheets |
| Settings long scroll | Settings screens |

### 3.2 Required outcomes

- Stable **item keys**; never key by index for dynamic lists.  
- Images/avatars: decode/size appropriately; placeholders must not cause layout thrash.  
- Realtime / optimistic inserts: **no teleport** to wrong offset; preserve anchor (see prior chat scroll work — extend, don’t regress).  
- Avoid recomposing entire rows on unrelated state (selection, typing, online dots) — isolate high-frequency state.  
- Prefer `graphicsLayer` / draw for transient effects over layout-affecting modifiers during scroll.  
- Long-press / swipe row gestures must not fight vertical scroll (clear touch slop / orientation lock).  

### 3.3 Success criteria

- Slow fling on a 200+ item inbox and a media-heavy chat: no visible hitch on mid Android + recent iPhone.  
- Open chat → scroll up history → send → return to inbox: list position and preview stay calm (no jump-to-top, no flash).  

---

## 4. Keyboard / IME continuity

### 4.1 Problem class

Keyboard toggles that: snap the composer, leave a gap above the IME, double-apply insets, desync iOS UIKit curve vs Compose tween, or resize the whole screen in a way that reflows chat mid-frame.

### 4.2 Surfaces

| Surface | Notes |
|---------|--------|
| 1:1 / group chat composer | `ChatKeyboardDock`, `rememberChatNativeKeyboardInsets`, `ConnectionChatMessageComposer` |
| Hub chat | Same keyboard primitives — stay unified |
| Search field in sheets | Focus → IME → results list padding |
| Beacon / hub / availability forms | `BeaconDropSheet` already tracks IME padding — pattern to standardize |
| Auth / onboarding fields | Must match product IME feel |

### 4.3 Required outcomes

- **One** lift authority per screen (native provider **or** WindowInsets — not both fighting).  
- iOS: animation duration + curve stay matched to system keyboard notifications.  
- Android: IME insets animate smoothly with thread dock; no clip under nav/gesture bar.  
- Focusing composer scrolls/anchors latest messages appropriately without fighting user scroll.  
- Dismiss-on-scroll (where present) feels intentional, not accidental.  

### 4.4 Success criteria

- Toggle keyboard 10× rapidly in chat: composer never desyncs; no residual padding when IME gone.  
- Rotate / multitask / return from call overlay: keyboard state recovers without jump.  

---

## 5. Navigation continuity & remount prevention

### 5.0 P0 — Home flickers on back-gesture (OPEN on latest build)

**Status:** Still reproduces on the latest app version. **Unacceptable.** Must lead the comprehensive Fable plan and Grok S1.

| | |
|--|--|
| **Symptom** | Interactive back-gesture returning to **Home** flashes / remounts / shows a discontinuous frame (blank, wrong content, or “fresh open” look). |
| **Related** | Continuation `#26` — Home flicker after Map / Settings / Add Click interactive-back. A Home-underlay overlay was tried and **reverted** (caused Map→Home flash + broke primary tab crossfade). |
| **Constraint** | Fix continuity **without** that reverted pattern. Preserve `AnimatedContent` tab transitions. Study working patterns: Add Click persistent underlay (`#12`), chat leave deferred teardown (`#24`). |
| **Likely files** | `App.kt` (tab `AnimatedContent`, Home composition lifetime), `HomeScreen` / home scaffold, `InteractiveSwipeBackContainer`, any tab-underlay helpers |
| **Acceptance** | From Map, Settings, Add Click overlays (and any other route that interactively backs onto Home): drag + commit + cancel each look continuous — **zero** flash. Round-trip tab switches Map↔Home still crossfade cleanly with no wrong-frame flash. |

Fable must rank root-cause hypotheses (e.g. Home disposed while off-tab; crossfade restarting enter transition; scaffold/header remount; shimmer gate; state reset on resume) and pick an approach that keeps Home **painted and identity-stable** during the gesture without breaking other tabs.

### 5.1 Problem class (general)

After interactive swipe-back or tab return, underlays **flash**, lists **reload shimmer**, headers **pop**, or `AnimatedContent` **recreates** children so the user sees a “new screen” instead of the same place they left.

### 5.2 Known history (do not regress)

Continuation handoff already fixed or deferred several cases:

- Add Click overlay underlay (`#12`) — keep persistent underlay pattern.  
- Chat leave / inbox flicker (`#24`) — defer teardown until gesture settles.  
- Home-underlay attempt (`#26`) — **reverted**; flicker **still open** — see §5.0 for the required correct fix.  

### 5.3 Required outcomes

- Swipe-back: underlay stays **alive and painted** during drag; parallax consistent (`InteractiveSwipeBackContainer`).  
- Commit vs cancel: spring settle feels physical; cancel restores exact prior UI.  
- Tab switches: crossfade/spring without destroying off-tab state needed for instant return (balance memory).  
- Overlays (search, sheets, chat on iOS): dismiss does not remount the whole tab root.  
- No “sudden rerender” of theme/scaffold when only route progress changed.  
- **Home specifically:** never looks newly composed when revealed by back-gesture (P0).  

### 5.4 Success criteria

- **P0:** Any interactive back onto Home — no flicker (device, latest build).  
- Chat → swipe back → inbox looks continuous (no blank frame, no scroll jump).  
- My Code / Scan / Tap → swipe back → Add Click cards do not remount-flicker.  
- Map ↔ Home ↔ Settings round-trip: no flash of wrong tab content.  

---

## 6. Shared motion language

Define (in plan, then implement once) a small vocabulary reused everywhere:

| Token idea | Use |
|------------|-----|
| Press | Existing 2dp translate + darken — keep; apply consistently |
| Sheet dismiss | Existing glass gesture commit/flick constants — unify callers |
| Soft enter/exit | Low/medium spring pair already used in `App.kt` / toasts — catalog + reuse |
| Emphasized success | Short overshoot spring + light haptic (connect, send ack, call connect) |
| Destructive | Snappier, no playful overshoot |

**Haptics:** map to meaningful moments only (send, connect success, call end/accept, destructive confirm). Avoid haptic spam on scroll.

---

## 7. Physics / “fun” motion (bounded)

Allowed to feel playful **without** toyish chaos:

- Sheet grabber drag with resistance + snap  
- Swipe-back parallax + dimming  
- Message send: bubble appears with light scale/fade + list anchor (see part 03)  
- Connect reveal / reconnect: existing overlays — enrich timing, don’t rebuild product flow  
- Tab selection: icon/indicator response with spring, not instant hard cut  

Disallow: endless ambient particle systems on scroll surfaces; multi-second celebration blocking input; motion that delays time-to-interactive.

---

## 8. Performance budget checklist (Fable → Grok)

For each heavy screen, plan should answer:

1. What recomposes on every frame of scroll / keyboard / drag?  
2. What can be `remember`d / hoisted / deferred?  
3. Are images and blur layers active during scroll? (blur off while dragging if needed)  
4. Are there duplicate collectors / LaunchedEffects restarting on unrelated keys?  
5. Does back-swipe dispose ViewModel work that should wait until settle?  

---

## 9. Acceptance pack (device)

- [ ] **P0:** Back-gesture onto Home — no flicker (Map / Settings / Add Click paths)  
- [ ] Inbox fling + return from chat: no stutter, no jump  
- [ ] Chat IME open/close: lockstep composer  
- [ ] Interactive back from chat & Add Click overlays: no remount flash  
- [ ] Tab crossfade: no blank/wrong frame (especially Map↔Home)  
- [ ] Sheet drag dismiss: consistent physics across sheets  
