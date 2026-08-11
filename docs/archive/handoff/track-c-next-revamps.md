**Status:** archived (as of 2026-08-11) — not authoritative; see `docs/archive/README.md`.

# Track C continuation — next revamps

**Date:** 2026-07-18  
**Product:** Click KMP mobile (`click/composeApp`) + click-web  
**Previous chat:** Track C event detail revamp + event engagement API (bookmarks / check-ins / insights)

Read this before writing code. Prefer **one primary revamp per chat**.

---

## 0. Just finished (do not redo)

### Event detail — landed

- `EventBeaconDetail`: LIVE badge, start/end bento (**date + time**), category chips, host card (reuses connected/current user avatar when available), overlapping Active Clicks stack
- Hero: **Share** + **Bookmark** + **Check in** + creator **⋯** last (Edit/Delete themed dropdown; bookmark/check-in **server-backed**)
- Drop sheet: **Check-in area** venue-scale chips
- Server: [event-engagement-api.md](event-engagement-api.md) + click-web `/insights/event-engagement` charts
- CTAs: **Join Event Route** (HTTPS maps; not `geo:` primary) + **RSVP / Sign Up** / **Cancel RSVP**
- Drop sheet: event category multi-select → metadata `event_categories`
- Map pins/clusters: uniform **44dp/pt** circular markers when scrunched

**Docs:** `docs/ui-ux/mobile/10-map-beacons-hubs.md`, design-asset `event_details_expanded_dark/`.

**Still open / follow-ups:**
- Device smoke for expanded event sheet + share/route/bookmark/check-in on hardware (API landed — verify on device)
- Host avatar when creator is not current user and not in `connectedUsers` (API has no `creator_avatar_url` yet)
- Device smoke: Home Saved events; share `/e/` Universal Link; Timeline event after RSVP-gated connect at live event

**Shipped follow-ups (event engagement):** Home Saved events · share deep-link + analytics · RSVP-gated encounter `event_beacon_id` + Timeline — see [event-engagement-api.md](event-engagement-api.md).

### Map-first events discovery — landed

- Map tab opens to **full interactive map** + **Events for you** peek chip (not a modal sheet)
- Tap peek → **full-screen** Events (slide-in + interactive swipe-back); headers say **Events**; map layer isolated (gestures stay on; overlay eats touches; stable map callbacks)
- **Pins ~44dp/pt**; denser event cards (titleMedium / bodySmall like Connections)

**Docs:** `docs/ui-ux/mobile/10-map-beacons-hubs.md`.

**Still open:** device smoke for full-screen events (bottom-up enter/exit + horizontal swipe-back) + pin photo confirm on hardware.

### Remember Me name pill + Tap how-it-works gate — landed

- Remember Me **name** hit target is a **pill** (`clip(RoundedCornerShape(999.dp))` + padding); avatar stays **circular** — do not reintroduce full-column or full-width square `clickable` on the name
- Tap to Connect idle: **"How Tap to Connect works"** card only when `MockProximityManager` / `isSimulatorOrEmulatorRuntime()` — hidden on real devices

**Docs:** `docs/ui-ux/mobile/07-connections-inbox.md`, `docs/ui-ux/mobile/06-connect-handshake.md`.

### Add Click hero — landed (minimal)

- `AddClickContent` order: **Tap to Connect** → My Code | Scan → hub links
- Hub labels: **Create hub** / **Join hub** (was Create ephemeral hub / Join community hub)
- Card sizes, header, icons, padding, typography **unchanged** (hierarchy only; no mock restyle)
- Callbacks / sheets / `#12` interactive-back underlay untouched

**Docs:** `docs/ui-ux/mobile/06-connect-handshake.md`, design-asset `add_click_streamlined_header/` (hierarchy reference).

**Still open:** device smoke for Add Click order + hub labels; Remember Me pills; Tap how-it-works on hardware.

### Inbox Remember Me + polish — landed (partial Inbox density)

- Active-tab **Remember Me** horizontal strip: Core-pinned **1:1** only (`coreConnectionIds`)
- Chips: 56dp avatar + compact time badge (`formatRememberMeBadge`) + first name; tap opens chat
- Section labels **Remember Me** / **Clicks**; hide when searching or core empty
- Core people still appear in the normal list; **row spacing / ConnectionItem chrome unchanged**
- Avatar hit target is **circular**; name hit target is **pill** (see above)
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
| **1** | **Full-map polish** | `map_events_full_screen_map/` | `MapScreen` | Remaining full-map chrome / pin sheet polish (map-first already shipped) |
| Later | Inbox dense / rolodex stack | `add_click_fixed_navigation/` | `ConnectionsListView` | Only if product wants overlapping cards (spacing currently intentional) |

Optional later (after related device OK): Nav chrome v2, Chat composer polish, Profile memories IA — see handoff §3.

**Suggested default for next chat:** Full-map polish.

### Full-map polish — brief for next chat

**Current:** Map-first discovery + expanded event detail already shipped.

**Target (mock):** [`docs/design-assets/map_events_full_screen_map/`](../design-assets/map_events_full_screen_map/) — remaining full-map chrome / pin sheet polish only. Use HTML for hierarchy/spacing intent — do not copy markup, blur, or glass.

**Keep:** Events peek → full-screen list; `EventBeaconDetail` hierarchy; transparent nav; `clickBorderColor()` / `LocalIsDarkMode` / `GlassSheetTokens`. Do not redo event detail in the same chat.

**Update when shipping:** [`docs/ui-ux/mobile/10-map-beacons-hubs.md`](../ui-ux/mobile/10-map-beacons-hubs.md) + this handoff §0 / §4.

---

## 2. Prompt template for the next chat

```text
Continue Click Track C using:
click/docs/archive/handoff/track-c-next-revamps.md
and click/docs/archive/handoff/functional-clarity-continuation.md

DONE — do not redo unless regressing:
- Event detail (LIVE, bento, categories, host, Active Clicks, Share/Bookmark/Check in, Route + RSVP)
- Map-first events discovery (full map + peek → full-screen Events list; search only in list; avatar pins)
- Home IA + Home greeting LiquidGlassPageHeader
- Settings grouping
- Inbox Remember Me (Core 1:1s; circular avatar; pill name hit targets)
- Add Click hero (Tap first; Create hub / Join hub; dimensions unchanged)
- Tap how-it-works card simulator-only
Track A done. Track B code landed — device verify only; do not false-pass [KNOWN-N].

Scope for THIS chat (default):
Full-map polish — docs/design-assets/map_events_full_screen_map/ → remaining MapScreen chrome / pin sheet
(Layout/IA only.)

Rules:
- Do NOT edit the neo-brutalist plan file under .cursor/plans.
- Layout/IA only unless fixing a clear bug; keep ViewModels / BLE / Realtime / LiveKit.
- Keep nav bar chrome transparent (no opaque fill band under icons).
- Reuse clickBorderColor() / LocalIsDarkMode / GlassSheetTokens.*().
- Use design-asset HTML for hierarchy/spacing intent only — do not copy markup.
- Do not redo EventBeaconDetail unless regressing.
- Update docs/ui-ux/mobile/10-map-beacons-hubs.md + this handoff when shipping.
- After changes: run regression-testing §0 automated gates.
```

---

## 3. Key references

| Concern | Path |
|---------|------|
| Prior handoff | `docs/archive/handoff/functional-clarity-continuation.md` |
| Home (done) | `docs/ui-ux/mobile/05-home.md` |
| Settings (done) | `docs/ui-ux/mobile/14-settings-privacy.md`, `docs/design-assets/settings/` |
| Inbox (Remember Me done) | `docs/ui-ux/mobile/07-connections-inbox.md`, `docs/design-assets/chat/` |
| Add Click (done) | `docs/ui-ux/mobile/06-connect-handshake.md`, `docs/design-assets/add_click_streamlined_header/` |
| **Map / events (map-first + event detail done)** | `docs/ui-ux/mobile/10-map-beacons-hubs.md`, `MapDiscoveryLayout.kt`, `EventBeaconDetail`, design-assets `events_*` / `map_events_*` / `event_details_*` |
| Regression | `docs/regression-testing/00-INDEX.md` (§0 gates) |

---

## 4. Done when (this handoff’s job)

- [x] Home polish (order, insights, location pin, verified-click search) shipped
- [x] Docs updated for Home order + verified-click search
- [x] Settings grouping (profile header + preference clusters + standalone Sign out) shipped
- [x] Inbox Remember Me strip (Core 1:1s) shipped; list spacing left as-is
- [x] Remember Me circular avatar + **pill name** hit targets + Home greeting `LiquidGlassPageHeader` polish shipped
- [x] Next-chat handoff prepared (Add Click hero brief + §2 prompt)
- [x] Add Click hero (Tap first; Create hub / Join hub; dimensions unchanged) shipped
- [x] Tap how-it-works card gated to simulator / emulator only
- [x] Next-chat handoff refreshed for **Events discovery** (§1 brief + §2 prompt)
- [x] Map-first events discovery (full map + peek → full-screen Events list + avatar pins) shipped
- [x] Event detail revamp (LIVE, bento, categories, host, attendees, Share/Bookmark/Check in, Route + RSVP) shipped
- [x] Next-chat handoff refreshed for **Full-map polish** (§1 brief + §2 prompt)
