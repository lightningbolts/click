# 03 — Chat & Messaging Interactions

**Role:** Highest daily-use delight surface. Theme is done; make sending, reading, and acting on messages feel effortless and alive.  
**Code hotspots:** `ChatView`, `ui/chat/*` (timeline, bubbles, composer, keyboard dock, action sheets, icebreaker/vibe, media, voice), `HubChatScreen`, inbox → chat navigation in `ConnectionsScreen` / `App.kt`.

---

## 1. Goals

| Goal | User feel |
|------|-----------|
| Fluid send | Tapping send is instantly rewarding; bubble lands without stutter or jump |
| Keyboard harmony | Composer + timeline + accessory chrome move as one with IME |
| Readable timeline | Scroll is buttery; media/voice don’t hitch; day separators calm |
| Satisfying message actions | Long-press, react, copy, delete, forward, edit feel crisp and consistent |
| Parity | 1:1, group, and hub chat share motion/composer patterns |

---

## 2. Non-goals

- Redesigning E2EE, sync protocol, or cache strategy (preserve disk/hot cache)  
- New message types / product features  
- Web chat parity work  

---

## 3. Send / receive motion

### 3.1 Required outcomes

**Outbound**

- Optimistic bubble appears in the correct place immediately (no blank gap, no duplicate flash when server ack arrives).  
- Light enter motion (scale/fade/slide — choose one shared spec) that does **not** block scrolling.  
- Composer clears with a snappy field animation; send button state transitions clearly (idle → sending → idle).  
- Delivery receipts update without reflowing the whole bubble.  
- Failed send: clear affordance + retry; error motion is sober, not playful.  

**Inbound**

- Insert near bottom when user is pinned to latest; do not yank scroll if user is reading history.  
- Typing indicator appears/disappears with the same soft spring language as toasts.  
- Group messages: avatar/name clustering stays stable during inserts.  

### 3.2 Success criteria

- Spam-send 5 short texts: list stays glued to bottom; no duplicate bubbles; no frame drop.  
- Scroll up 50 messages, receive inbound: no forced jump; “new messages” affordance if product already has one — polish it, don’t invent heavy UI.  

---

## 4. Composer & keyboard

### 4.1 Required outcomes

- Single IME lift path (see part 01); hub + connection chat share helpers.  
- Attachment / emoji / voice affordances: open/close without fighting IME; mode switches feel intentional.  
- Multilines grow smoothly up to max; timeline padding tracks height.  
- Edit strip / reply context (if present): enter/exit animated; cancel is obvious.  
- Focus transitions from search sheets or headers into chat don’t leave stale insets.  

### 4.2 Voice / media composer paths

- Record dialog: start/stop feels physical; cancel vs send are distinct.  
- Photo/file pick → pending chip → send: no layout explosion; progress determinate when possible.  

---

## 5. Timeline & scroll physics

### 5.1 Required outcomes

- Reverse LazyColumn (or equivalent) keeps prior anti-teleport behavior; polish any remaining open-jump.  
- `animateItem` / placement animations only where cheap; disable under accessibility “reduce motion” if available.  
- Timestamp peek (RTL with swipe-back): spring settle; never stuck half-open; doesn’t break interactive back.  
- Day separators: stable keys (already hardened) — no flicker on pagination merge.  
- Pagination older messages: spinner/skeleton at edge without shifting visible viewport.  

---

## 6. Bubbles & chat elements

Polish **interaction**, not brand-new bubble skins (FC tokens stay).

| Element | Polish focus |
|---------|----------------|
| Text bubble | Press state, selection/long-press, link tap feedback |
| Photo / gallery | Hero open/close; pinch/dismiss; no black flash |
| Audio | Play/pause motion; waveform/scrub if present; lock screen interruption calm |
| Attachments / files | Clear tap target; download progress |
| Call log system rows | Tappable → call; subtle, not noisy |
| Reactions / receipts | Appear near bubble without shoving neighbors hard |
| Icebreaker / vibe panels | Enter once; dismiss without leaving hole |
| Tether / compass toasts | Coordinate with global toast physics |

### Required outcomes

- Long-press → `MessageActionSheet`: sheet physics match app sheets; haptics on open.  
- Destructive actions confirm with FC popups — consistent copy + motion.  
- Expanded photo preview: gesture dismiss smooth; returns to same scroll offset.  

---

## 7. Entering & leaving chat

| Platform | Expectation |
|----------|-------------|
| iOS | List underlay + overlay chat; swipe-back continuous; leave-room deferred until settle |
| Android | `AnimatedContent` push/pop feels same family as iOS springs |

### Required outcomes

- Open chat: header + first paint fast (warm cache); loading states are calm, not flashy.  
- Close chat: inbox row preview/time does not regress to stale “12w ago” (prior fix — don’t break).  
- Deep link / pending chat open: same motion as manual open.  

---

## 8. Consistency across chat surfaces

Unify (plan should name concrete duplicate APIs):

- Connection chat vs hub chat keyboard dock / composer chrome  
- Header icon buttons / back treatment  
- Empty/loading/error panels  
- Action sheets for message vs connection  

Goal: one “Chat chrome kit,” parameterized for hub vs 1:1 vs group.

---

## 9. Acceptance pack (device)

- [ ] Send animation smooth; no duplicate/jump on ack  
- [ ] IME ↔ composer lockstep on iOS + Android  
- [ ] History scroll + pagination: no stutter/teleport  
- [ ] Timestamp peek + swipe-back coexist  
- [ ] Long-press actions + media lightbox feel native-smooth  
- [ ] Hub chat matches connection chat motion quality  
