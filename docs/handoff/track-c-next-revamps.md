# Track C continuation — next revamps

**Date:** 2026-07-17  
**Product:** Click KMP mobile (`click/composeApp`)  
**Previous chat:** Track C Settings grouping (landed)  

Read this before writing code. Prefer **one primary revamp per chat**.

---

## 0. Just finished (do not redo)

### Settings grouping — landed

- Profile header first: avatar, display name, email (when present), **Edit Profile** → name dialog
- Preference clusters: **Availability** → **Alerts** (notifications + ambient) → **Privacy & data** (Your Data toggles + Permissions Hub) → **Interests** → **Appearance**
- Standalone bordered **Sign out** at bottom (Account section removed)
- 24dp between clusters; layout/IA only — ViewModels / toggles unchanged

**Docs:** `docs/ui-ux/mobile/14-settings-privacy.md`, handoff §3 Settings row.

**Still open:** device smoke for Settings visual confirm; Track B `[KNOWN-N]` device verify.

### Home IA — landed

- Greeting-first feed (no floating `"Home"` title); search pill; Featured **Event**
- Explore nearby = live `MapBeaconKind` / Hub counts only (no mock Networking/Workshop tiles)
- Feed order (current): greeting → search → Featured Event → **I'm down for…** → **Explore** → archive/Poll-Pair → Reconnect → reminders → recent → insights → stats
- Insights row: label/value columns (Longest Connection no longer overlaps)
- Location group cards: no redundant location-pin icon
- Reconnect cards: `ConnectionListUserAvatarFace` (same avatar as Clicks inbox)
- Create verified click + unified search: single-line search fields with normal caret (no tall multi-line cursor / clipped placeholder)

**Docs:** `docs/ui-ux/mobile/05-home.md`, `docs/design-assets/home/README.md`, handoff §3 Home row, regression §9 / smoke, `13-availability.md`, `07-connections-inbox.md` search note.

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
| **1** | **Inbox density** | `chat/` + `add_click_fixed_navigation/` | `ConnectionsListView` | Card stack / dense bordered list; rolodex name stack |
| **2** | **Add Click hero** | `add_click_streamlined_header/` | `AddClickScreen` | Large Tap-to-Connect hero |
| **3** | **Events discovery** | `events_discovery_with_real_mini_map/` | `MapDiscoveryLayout` | Events-for-you + mini-map PiP |
| **4** | **Full-map events** | `map_events_full_screen_map/` | `MapScreen` | Full-map + event pin sheet |
| **5** | **Event detail** | `event_details_expanded_dark/` | Beacon/event sheets | Expanded detail over map |

Optional later (after related device OK): Nav chrome v2, Chat composer polish, Profile memories IA — see handoff §3.

**Suggested default for next chat:** Inbox density (highest-traffic remaining surface).

---

## 2. Prompt template for the next chat

```text
Continue Click Track C using:
click/docs/handoff/track-c-next-revamps.md
and click/docs/handoff/functional-clarity-continuation.md

Home IA + Settings grouping are DONE — do not redo unless regressing.
Track A done. Track B code landed — device verify only; do not false-pass [KNOWN-N].

Scope for THIS chat (pick one primary):
1) Inbox density — docs/design-assets/chat/ + add_click_fixed_navigation/ → ConnectionsListView
2) Add Click hero — docs/design-assets/add_click_streamlined_header/ → AddClickScreen
3) Events discovery / full-map / event detail — map stack (say which)

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Layout/IA only unless fixing a clear bug; keep ViewModels / BLE / Realtime / LiveKit.
- Keep nav bar chrome transparent (no opaque fill band under icons).
- Reuse clickBorderColor() / LocalIsDarkMode / GlassSheetTokens.*().
- Use design-asset HTML for hierarchy/spacing intent only — do not copy markup.
- Update relevant docs/ui-ux + handoff when shipping.
- After changes: run regression-testing §0 automated gates.
```

---

## 3. Key references

| Concern | Path |
|---------|------|
| Prior handoff | `docs/handoff/functional-clarity-continuation.md` |
| Home (done) | `docs/ui-ux/mobile/05-home.md` |
| Settings (done) | `docs/ui-ux/mobile/14-settings-privacy.md`, `docs/design-assets/settings/` |
| Inbox | `docs/ui-ux/mobile/07-connections-inbox.md`, `docs/design-assets/chat/` |
| Add Click | `docs/ui-ux/mobile/06-connect-handshake.md`, `docs/design-assets/add_click_streamlined_header/` |
| Map / events | `docs/ui-ux/mobile/10-map-beacons-hubs.md`, design-assets `events_*` / `map_events_*` |
| Regression | `docs/regression-testing/00-INDEX.md` (§0 gates) |

---

## 4. Done when (this handoff’s job)

- [x] Home polish (order, insights, location pin, verified-click search) shipped
- [x] Docs updated for Home order + verified-click search
- [x] Settings grouping (profile header + preference clusters + standalone Sign out) shipped
- [ ] Next Track C revamp started in a dedicated chat using §2 prompt
