# Track C continuation — next revamps

**Date:** 2026-07-17  
**Product:** Click KMP mobile (`click/composeApp`)  
**Previous chat:** Track C Add Click hero (minimal reorder + hub labels)

Read this before writing code. Prefer **one primary revamp per chat**.

---

## 0. Just finished (do not redo)

### Add Click hero — landed (minimal)

- `AddClickContent` order: **Tap to Connect** → My Code | Scan → hub links
- Hub labels: **Create hub** / **Join hub** (was Create ephemeral hub / Join community hub)
- Card sizes, header, icons, padding, typography **unchanged** (hierarchy only; no mock restyle)
- Callbacks / sheets / `#12` interactive-back underlay untouched

**Docs:** `docs/ui-ux/mobile/06-connect-handshake.md`, design-asset `add_click_streamlined_header/` (hierarchy reference).

**Still open:** device smoke for Add Click order + hub labels.

### Inbox Remember Me + polish — landed (partial Inbox density)

- Active-tab **Remember Me** horizontal strip: Core-pinned **1:1** only (`coreConnectionIds`)
- Chips: 56dp avatar + compact time badge (`formatRememberMeBadge`) + first name; tap opens chat
- Section labels **Remember Me** / **Clicks**; hide when searching or core empty
- Core people still appear in the normal list; **row spacing / ConnectionItem chrome unchanged**
- Avatar hit target is **circular** (`CoreConnectionAvatarFrame` + `clip(CircleShape)`); name is a separate text tap — do not reintroduce full-column square `clickable`
- Not done: overlapping rolodex card stack from `add_click_fixed_navigation/`

**Docs:** `docs/ui-ux/mobile/07-connections-inbox.md`, handoff §3 Inbox row.

**Still open:** device smoke for Remember Me visual confirm; Track B `[KNOWN-N]` device verify.

### Home greeting header chrome — landed

- Floating `LiquidGlassPageHeader` via `AppScreenScaffold(showFloatingHeader = true)` — **same status-bar overlay level** as Clicks / Map / other tab roots
- Title = `homeGreetingTitle(firstName)`; subtitle = `HomeGreetingSubtitle` (`"Ready to connect today?"`)
- Not an in-feed list item (avoids sitting too low under status-bar + spacer)

**Docs:** `docs/ui-ux/mobile/05-home.md`.

### Settings grouping — landed

- Profile header first: avatar, display name, email (when present), **Edit Profile** → name dialog
- Preference clusters: **Availability** → **Alerts** (notifications + ambient) → **Privacy & data** (Your Data toggles + Permissions Hub) → **Interests** → **Appearance**
- Standalone bordered **Sign out** at bottom (Account section removed)
- 24dp between clusters; layout/IA only — ViewModels / toggles unchanged

**Docs:** `docs/ui-ux/mobile/14-settings-privacy.md`, handoff §3 Settings row.

**Still open:** device smoke for Settings visual confirm.

### Home IA — landed

- Greeting-first feed; search pill; Featured **Event**
- Explore nearby = live `MapBeaconKind` / Hub counts only (no mock Networking/Workshop tiles)
- Feed order (current): greeting → search → Featured Event → **I'm down for…** → **Explore** → archive/Poll-Pair → Reconnect → reminders → recent → insights → stats
- Insights row: label/value columns; location cards without redundant pin icon
- Reconnect cards: `ConnectionListUserAvatarFace`
- Create verified click + unified search: single-line search fields with normal caret

**Docs:** `docs/ui-ux/mobile/05-home.md`, `docs/design-assets/home/README.md`.

**Still open:** device smoke for Home IA visual confirm.

### Track A / B

- Track A dark/light: **DONE** — do not redo unless regressing
- Track B P0 + B+ UI: **code landed** — device verify before false-passing `[KNOWN-N]`
- Keep transparent nav chrome; preserve chat disk/hot cache; reuse `clickBorderColor()` / `LocalIsDarkMode` / `GlassSheetTokens`

Source of truth for prior work: [`functional-clarity-continuation.md`](functional-clarity-continuation.md)

---

## 1. Recommended next (pick one)

| Priority | Revamp | Mock / source | Screen | Intent |
|----------|--------|---------------|--------|--------|
| **1** | **Events discovery** | `events_discovery_with_real_mini_map/` | `MapDiscoveryLayout` | Events-for-you + mini-map PiP |
| **2** | **Full-map events** | `map_events_full_screen_map/` | `MapScreen` | Full-map + event pin sheet |
| **3** | **Event detail** | `event_details_expanded_dark/` | Beacon/event sheets | Expanded detail over map |
| Later | Inbox dense / rolodex stack | `add_click_fixed_navigation/` | `ConnectionsListView` | Only if product wants overlapping cards (spacing currently intentional) |

Optional later (after related device OK): Nav chrome v2, Chat composer polish, Profile memories IA — see handoff §3.

**Suggested default for next chat:** Events discovery.

### Events discovery — brief for next chat

**Target (mock):** [`docs/design-assets/events_discovery_with_real_mini_map/`](../design-assets/events_discovery_with_real_mini_map/) → `MapDiscoveryLayout`. Events-for-you list + mini-map PiP. Use HTML/`screen.png` for hierarchy/spacing intent only — do not copy markup.

**Keep:** ViewModels / map / beacon data paths; transparent nav; `clickBorderColor()` / `LocalIsDarkMode` / `GlassSheetTokens`.

**Update when shipping:** [`docs/ui-ux/mobile/10-map-beacons-hubs.md`](../ui-ux/mobile/10-map-beacons-hubs.md) + this handoff §0 / §4.

---

## 2. Prompt template for the next chat

```text
Continue Click Track C using:
click/docs/handoff/track-c-next-revamps.md
and click/docs/handoff/functional-clarity-continuation.md

DONE — do not redo unless regressing:
- Home IA + Home greeting LiquidGlassPageHeader
- Settings grouping
- Inbox Remember Me (Core 1:1s; circular avatar hit targets)
- Add Click hero (Tap first; Create hub / Join hub; dimensions unchanged)
Track A done. Track B code landed — device verify only; do not false-pass [KNOWN-N].

Scope for THIS chat (default):
Events discovery — docs/design-assets/events_discovery_with_real_mini_map/ → MapDiscoveryLayout
(Events-for-you + mini-map PiP. Layout/IA only.)

Alternate (say which if not Events discovery):
2) Full-map events / event detail — map stack

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Layout/IA only unless fixing a clear bug; keep ViewModels / BLE / Realtime / LiveKit.
- Keep nav bar chrome transparent (no opaque fill band under icons).
- Reuse clickBorderColor() / LocalIsDarkMode / GlassSheetTokens.*().
- Use design-asset HTML for hierarchy/spacing intent only — do not copy markup.
- Update docs/ui-ux/mobile/10-map-beacons-hubs.md + this handoff when shipping.
- After changes: run regression-testing §0 automated gates.
```

---

## 3. Key references

| Concern | Path |
|---------|------|
| Prior handoff | `docs/handoff/functional-clarity-continuation.md` |
| Home (done) | `docs/ui-ux/mobile/05-home.md` |
| Settings (done) | `docs/ui-ux/mobile/14-settings-privacy.md`, `docs/design-assets/settings/` |
| Inbox (Remember Me done) | `docs/ui-ux/mobile/07-connections-inbox.md`, `docs/design-assets/chat/` |
| Add Click (done) | `docs/ui-ux/mobile/06-connect-handshake.md`, `docs/design-assets/add_click_streamlined_header/` |
| **Map / events (next)** | `docs/ui-ux/mobile/10-map-beacons-hubs.md`, design-assets `events_*` / `map_events_*` |
| Regression | `docs/regression-testing/00-INDEX.md` (§0 gates) |

---

## 4. Done when (this handoff’s job)

- [x] Home polish (order, insights, location pin, verified-click search) shipped
- [x] Docs updated for Home order + verified-click search
- [x] Settings grouping (profile header + preference clusters + standalone Sign out) shipped
- [x] Inbox Remember Me strip (Core 1:1s) shipped; list spacing left as-is
- [x] Remember Me circular hit targets + Home greeting `LiquidGlassPageHeader` polish shipped
- [x] Next-chat handoff prepared (Add Click hero brief + §2 prompt)
- [x] Add Click hero (Tap first; Create hub / Join hub; dimensions unchanged) shipped
- [ ] Next Track C revamp (Events discovery) started in a dedicated chat using §2 prompt
