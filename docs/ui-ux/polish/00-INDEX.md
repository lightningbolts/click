# Click Mobile — Interaction Polish Brief (for Claude Fable 5)

**Product:** Click KMP mobile (`click/composeApp`) — Android + iOS Compose  
**Date:** 2026-07-18  
**Audience:** Claude Fable 5 (planning) → Cursor Grok 4.5 (implementation)  
**Prerequisite:** Functional Clarity **theme revamp is done**. Do **not** redesign colors, typography, or card chrome from scratch. Polish **interaction quality**, **motion**, **perceived speed**, **nav chrome**, and **component reuse**.

**Related (read only if needed):**  
- Theme / feature specs: [`../mobile/00-INDEX.md`](../mobile/00-INDEX.md)  
- What already shipped / known flicker fixes: [`../../handoff/functional-clarity-continuation.md`](../../handoff/functional-clarity-continuation.md)  
- Tokens: [`../../design-assets/functional_clarity/DESIGN.md`](../../design-assets/functional_clarity/DESIGN.md)

---

## 0. Mission (one paragraph)

Make every user-facing interaction in Click feel **instant, continuous, and satisfying** — no scroll stutter, no keyboard jank, no remount flashes after swipe-back, fluid send/call/handshake moments, Click-branded liquid-glass tab chrome, consistent physics/haptics, and less redundant UI code. Theme stays Functional Clarity; polish is motion, continuity, and craft.

### P0 — still broken in latest build (must be in the plan)

**Home flickers on interactive back-gesture** when returning to the Home tab / Home underlay (continuation `#26` class). Still reproduces on the latest app version — **unacceptable**. Prior Home-underlay overlay approach was reverted because it caused Map→Home flash and broke tab crossfade. Fable must plan a **correct** fix that eliminates the flicker **without** regressing tab `AnimatedContent` transitions. Treat as the first continuity item in the comprehensive plan (before delight work).

---

## 1. Hard constraints (Fable must obey)

| # | Constraint |
|---|------------|
| 1 | **Theme tokens stay.** Primary `#630ed4`, hard borders, Manrope, opaque product surfaces. Liquid-glass is an intentional **nav/chrome exception**, themed to Click — not a full glass redesign. |
| 2 | **One-shot plan, don’t implement.** Fable’s job is a **single, highly comprehensive plan** covering all polish parts (01–05) in one response. Grok codes from that plan. Do not drip-feed partial plans unless the user explicitly splits budget. |
| 3 | **No backend / API / RLS / egress redesign** unless a polish item is blocked by an existing client bug (then flag, don’t expand scope). |
| 4 | **Preserve engineering invariants** from continuation handoff: chat disk/hot cache, ViewModels/BLE/Realtime/LiveKit untouched except when required for UI continuity. |
| 5 | **Home back-gesture flicker is P0.** Fix it in the plan. Do **not** revive the reverted Home-underlay pattern that broke Map→Home / tab crossfade (`#26`) — invent a continuity approach that keeps `AnimatedContent` tab transitions intact. |
| 6 | **Prefer reuse** over new parallel components. Dedup before inventing. |
| 7 | **Platform parity with deltas:** shared Compose motion where possible; iOS CallKit / keyboard curves / swipe-back stay platform-native. |
| 8 | **Plan is comprehensive; Grok ships in slices.** Fable’s one shot must still be breakable into ordered Grok PR slices (see §4) — completeness of *planning* is mandatory; big-bang *coding* is not. |

---

## 2. Document map (read order)

| Part | File | Covers |
|------|------|--------|
| 00 | This file | Mission, constraints, **Fable prompt recipes**, slice order |
| 01 | [01-motion-performance.md](01-motion-performance.md) | Scroll, lists, IME/keyboard, springs, remount/flicker, frame budget |
| 02 | [02-shell-nav-chrome.md](02-shell-nav-chrome.md) | Tab shell, **liquid glass nav**, swipe-back continuity, overlays |
| 03 | [03-chat-messaging.md](03-chat-messaging.md) | Send/receive motion, composer, bubbles, chat element interactions |
| 04 | [04-calls-connect.md](04-calls-connect.md) | Call UX delight, handshake / reconnect satisfaction |
| 05 | [05-consistency-reuse-flow.md](05-consistency-reuse-flow.md) | Theme consistency, component unification, flow efficiency |

**Do not rewrite** `docs/ui-ux/mobile/0x-*.md` as part of this work — those are target-state feature specs. This polish set is the **interaction quality** overlay.

---

## 3. How to use Fable with minimal tokens

### 3.1 Default: **one-shot comprehensive plan** (required)

Attach **all five parts** (01–05) + this INDEX. Paste **Prompt A** once.  
Fable must return **one** highly comprehensive plan that covers **every** polish domain in parts 01–05 — not a teaser, not “phase 1 only.” Depth: enough that Grok can implement without re-planning. Breadth: every item class in the docs, prioritized, with file paths + approach sketches + acceptance criteria + ordered PR slices.

**Do not** ask Fable for a second planning round unless something was truly missing from attachments. Split prompts (B/C/D) are **fallback only** when the one-shot context window cannot fit 00–05.

Never give Fable the entire `docs/ui-ux/mobile/*` feature set unless a specific screen is blocking — those files are huge. The polish set is the planning corpus.

### 3.2 Fallback only: three prompts max

Use only if Prompt A cannot load all attachments:

1. **Prompt B** + `00`, `01`, `02` → Shell / motion / nav (must include Home back-gesture P0)  
2. **Prompt C** + `00`, `03`, `04` → Chat / calls / connect  
3. **Prompt D** + `00`, `05` + prior outputs → Merge into **one** final Grok backlog (mandatory merge step)

### 3.3 What Fable must return (output contract)

**One response**, this skeleton (keeps Grok prompts short later):

```markdown
# Click Mobile — Interaction Polish Plan (comprehensive)

## Goal (1–2 sentences)
## Non-goals
## P0 — Home back-gesture flicker (must lead)
- Root-cause hypotheses (ranked)
- Chosen approach sketch (and why not the reverted #26 underlay)
- Files, risks, acceptance criteria
## Priority ordered work items (ALL domains: motion, shell/nav, chat, calls/connect, consistency)
For each item:
- Outcome / feel
- Likely files (paths only)
- Approach sketch (3–8 bullets, no full code)
- Risks / regressions to avoid
- Acceptance criteria (device-checkable)
## Dedup / reuse list
## Suggested Grok PR slices (S0–Sn) — ordered, non-overlapping
## Explicit non-work
## Open questions (only if truly blocking — prefer deciding)
```

### 3.4 Copy-paste prompts

#### Prompt A — One-shot full plan (USE THIS)

```
You are planning Click mobile interaction polish for a KMP Compose app in ONE response.
Theme (Functional Clarity) is DONE — do not redesign the visual system.
Read ALL attached polish docs (00–05). Obey hard constraints in 00-INDEX §1.
Produce ONE highly comprehensive plan using the output contract in 00-INDEX §3.3.
Cover EVERY domain in parts 01–05. Do not defer whole sections to a “later plan.”
P0 first: Home still flickers on interactive back-gesture to Home (latest build; #26 class).
Plan a real fix that does NOT reintroduce the reverted Home-underlay that broke Map→Home / tab crossfade.
Then prioritize: (1) zero scroll stutter + IME continuity, (2) all swipe-back/remount continuity,
(3) chat send + keyboard motion, (4) Click-branded liquid-glass tab bar,
(5) calls + handshake/reconnect satisfaction, (6) component dedup / theme consistency / flow efficiency.
Be specific about files under composeApp/.../ui/ and calls/, but leave detailed code to Grok.
Do not invent new product features. Polish existing interactions only.
End with an ordered Grok PR slice list so implementation can ship incrementally from this single plan.
```

#### Prompt B — Motion + shell only (fallback)

```
Plan Click mobile polish for motion/performance + shell/nav only.
Read attached 00-INDEX, 01-motion-performance, 02-shell-nav-chrome.
Obey §1 constraints. Use §3.3 output contract (adapt to this scope).
P0: Home back-gesture flicker still present — must plan correct fix without #26 regression.
Also: scroll jank, IME lift, all swipe-back remounts, liquid-glass tab bar themed to Click.
No chat/call deep dive. Note this is a FALLBACK fragment to be merged via Prompt D.
```

#### Prompt C — Chat + calls + connect only (fallback)

```
Plan Click mobile polish for chat, calls, and connect/handshake interactions.
Read attached 00-INDEX, 03-chat-messaging, 04-calls-connect.
Obey §1 constraints. Use §3.3 output contract (adapt to this scope).
Assume shell motion foundations from Prompt B will merge later.
Focus: send animations, keyboard↔composer, bubble/actions feel, call overlays, handshake/reconnect delight.
Note this is a FALLBACK fragment to be merged via Prompt D.
```

#### Prompt D — Consistency merge (fallback → still end at one plan)

```
Merge prior polish plan fragments into ONE final comprehensive Grok-ready backlog.
Read attached 00-INDEX + 05-consistency-reuse-flow + my prior Fable outputs.
Deduplicate overlapping items. Lead with Home back-gesture P0 if not already solved in the merge.
Output the full §3.3 contract. Flag redundant components to unify. No new visual theme.
```

#### Prompt E — Hand off one slice to Grok (after the one-shot plan)

```
Implement ONLY slice "<name>" from the comprehensive Interaction Polish Plan. Do not expand scope.
Follow Click Functional Clarity tokens. Prefer reuse over new components.
Home back-gesture flicker is P0 when in scope: fix without reintroducing reverted #26 Home-underlay / broken tab crossfade.
Preserve working swipe-back underlays (Add Click #12, chat leave #24) and tab AnimatedContent.
Return: files changed, how to device-verify acceptance criteria.
```

---

## 4. Suggested execution order (for Grok after Fable’s one-shot plan)

| Slice | Name | Why first |
|-------|------|-----------|
| S0 | Audit pass (no code) | Already inside Fable’s one-shot — hotspots + duplicates listed |
| **S1** | **P0: Home back-gesture flicker** | Still broken on latest build; blocks “continuous app” feel |
| S2 | Remaining nav continuity (chat/Add Click/events swipe-back) | Same bug class as S1 |
| S3 | Scroll + list frame budget | Foundation — everything feels bad if lists jank |
| S4 | IME / keyboard continuity | Chat + sheets + search |
| S5 | Liquid-glass tab bar (Click-themed) | Highest chrome visibility |
| S6 | Chat send + timeline motion | Daily driver delight |
| S7 | Chat element interactions | Long-press, reactions, media, timestamps |
| S8 | Calls polish | High emotion, lower frequency |
| S9 | Connect / reconnect delight | Brand moment |
| S10 | Component unification + theme consistency sweep | Cleanup last so APIs stabilize |

Fable plans **all** of the above in one shot. Grok may merge small slices; never ship delight (S6+) before S1 is green on device. Never reverse S1–S4 before S6+.

---

## 5. Definition of done (whole initiative)

- **P0:** Interactive back-gesture onto Home is **visually continuous** — no flash, blank frame, remount shimmer, or wrong-tab frame (latest build currently fails this). Tab crossfade Map↔Home↔Settings remains intact.  
- Scrolling primary surfaces (Home, Clicks inbox, Chat timeline, Map lists, Search) feels **60fps-continuous** on mid devices; no teleport jumps on open/return.  
- Keyboard show/hide moves composer + dependent chrome **in lockstep** with system animation (iOS curve-matched; Android IME-inset smooth).  
- Edge swipe-back / interactive back **never** blank-flashes or fully recomposes the underlay as if freshly opened (Home, inbox, Add Click, events).  
- Tab bar reads as **Click liquid glass** (branded blur/material + FC accent), consistent light/dark.  
- Sending a message, placing/receiving a call, and completing a connect/reconnect each have a **clear, satisfying motion + haptic story**.  
- Redundant chrome/buttons/sheets reduced via shared primitives; no one-off copies for the same job.  
- Regression smoke: Home back-gesture, chat send, swipe-back from chat/Add Click overlays, tab switches, incoming call, Tap/QR connect — see [`../../regression-testing/02-smoke-10min.md`](../../regression-testing/02-smoke-10min.md).

---

## 6. Source roots (quick map)

```
click/composeApp/src/commonMain/kotlin/compose/project/click/click/
  App.kt                          # shell, tabs, overlays, AnimatedContent
  ui/theme/                       # tokens (do not redesign)
  ui/components/                  # sheets, glass→brutalist APIs, swipe-back, toasts
  ui/screens/                     # Home, Map, Add Click, Settings, ChatView, …
  ui/chat/                        # timeline, bubbles, composer, keyboard dock
  calls/                          # overlays, session, platform incoming UI
  navigation/
androidMain|iosMain/.../ui/       # platform tab bar, keyboard height, CallKit
```
