# 04 — Calls & Connect / Reconnect

**Role:** High-emotion, lower-frequency moments. Make them feel premium and trustworthy.  
**Code hotspots:** `calls/CallOverlays.kt`, `CallSessionManager`, `CallOverlayTransitionPolicy`, `PlatformIncomingCallUi*`, ringtone; connect: `AnimatedClickDialog`, `ConnectionRevealOverlay`, `ConnectionContextSheet`, Add Click / Tap / QR / NFC / App Clip flows, reconnect UI on Home / inbox.

---

## 1. Goals

| Goal | User feel |
|------|-----------|
| Satisfying calls | Ring → connect → in-call → end is clear, calm, and responsive |
| Trustworthy controls | Mute/cam/speaker/end are obvious under stress |
| Memorable connect | New click / reconnect has a short delightful beat — then gets out of the way |
| Continuity | Returning from CallKit / app background doesn’t shatter Compose overlays |

---

## 2. Non-goals

- Rewriting LiveKit / signaling  
- New call features (screen share, effects, etc.)  
- Changing Tri-Factor / BLE protocol — only UX around it  

---

## 3. Call experience polish

### 3.1 State choreography

Polish transitions across:

`Idle → Outgoing/Incoming → Connecting → Connected → Ended`

### Required outcomes

- Preview card enter/exit: spring + fade; no pop-cut.  
- Pulsing avatar/ring: GPU-cheap; pause when app backgrounded.  
- Connecting spinner: deterministic; never stuck if state moved on.  
- Hand-off preview → active overlay: **one** continuous story (use/extend `CallOverlayTransitionPolicy`).  
- Ended: brief confirmation then graceful dismiss; don’t block navigation longer than needed.  
- Failures (permission, full room, network): human copy + calm motion; easy retry/dismiss.  

### 3.2 Active call UI

- Drag/reposition card (if present): follows finger with damping; snaps to safe insets.  
- Control row: pressed states + haptics on mute/end; end is visually distinct.  
- Video stage: black flash avoided on cam toggle; pip/layout changes animated.  
- Voice-only: status text doesn’t jitter width; timer (if any) tabular/stable.  

### 3.3 Platform incoming

| Platform | Polish focus |
|----------|----------------|
| iOS CallKit / PushKit | Accept/decline → in-app overlay aligns; no double UI fight |
| Android CallStyle notification | Full-screen incoming → Compose overlay continuity |

### 3.4 Audio / haptics

- Ringtone start/stop clean (no bleed after accept).  
- Soft haptic on accept/connect; stronger on end optional.  
- Respect silent/DND expectations already in product.  

### 3.5 Success criteria

- Outgoing video + voice: connect path feels intentional on device.  
- Incoming while on another tab: overlay wins; dismiss returns to same tab state.  
- Kill permissions mid-call: recovery UI doesn’t freeze shell.  

---

## 4. Forming a new connection

### 4.1 Moments to polish (not redesign)

| Moment | Feel |
|--------|------|
| Add Click hub choices | Clear hierarchy; press feedback consistent |
| QR show / scan | Camera open smooth; success beat obvious |
| Tap / NFC proximity | Waiting → found → confirm without anxiety flicker |
| Context sheet | Sheet physics + confirm CTA satisfying |
| Reveal overlay | Short celebration; then land in chat/inbox predictably |
| App Clip path | Same success language as full app |

### 4.2 Required outcomes

- Waiting states pulse/breathe **gently**; never imply brokenness.  
- Success: haptic + short motion + clear next step (message / done).  
- Failure / timeout: actionable, not scary; easy retry.  
- Group vs 1:1 confirmations: shared dialog/sheet components.  
- Do not remount Add Click cards under swipe-back (`#12` pattern stays).  

---

## 5. Reconnection

### 5.1 Required outcomes

- Reconnect entry points (Home, inbox, Remember Me, banners): same interaction language.  
- Confirming a reconnect feels like a **lighter echo** of first connect — related motion, shorter.  
- Archive / 48h gentle archive warnings: serious tone; motion not playful.  
- After reconnect: chat open or inbox update without flash.  

---

## 6. Shared delight kit (plan this once)

Propose a tiny reusable “moment” API, e.g.:

- `SuccessBeat` (haptic + scale pulse + optional confetti-less spark)  
- `WaitingPulse` (avatar/ring)  
- `StateCardTransition` (call preview / connect cards)

Used by calls + connect so they feel related without copy-paste animation blocks.

---

## 7. Acceptance pack (device)

- [ ] Outgoing + incoming call motion continuous on iOS and Android  
- [ ] In-call controls responsive; end clean  
- [ ] CallKit/notification handoff doesn’t double-show or blank  
- [ ] QR / Tap success beat satisfying; failure clear  
- [ ] Reveal → chat/inbox landing smooth  
- [ ] Reconnect feels related but shorter than first connect  
