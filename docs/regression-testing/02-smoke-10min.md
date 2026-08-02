# Click — 10-Minute Smoke Test

**When:** Before merging any non-trivial change; after fixing a `[KNOWN]` bug.  
**Platforms:** Run on **Android device** and **iOS device/sim** (Android required for `[KNOWN-6]` / `[KNOWN-7]`).  
**Pass rule:** All unchecked items must pass **or** be explicitly waived as known (`[KNOWN-N]`) with a link to the audit.

Full matrix: [01-full-checklist.md](01-full-checklist.md) · Known issues: [03-known-issues-audit.md](03-known-issues-audit.md)

---

## Preflight (≤ 2 min)

- [ ] App cold-starts to shimmer → auth or main shell (no crash)
- [ ] Sign in if needed; land on Home (greeting visible; availability or reconnect still reachable)
- [ ] All five tabs open: Home, Add Click, Clicks, Map, Settings

---

## Connect (≤ 2 min)

- [ ] Add Click → My QR renders a scannable code
- [ ] Add Click → Scan QR (or second device) → context sheet → connection created
- [ ] New connection context sheet may show event recommendation (“Go together?”) → Dismiss works
- [ ] Add Click → Tap-to-Connect starts scanning (BLE/location prompts OK)
- [ ] `[KNOWN-3]` Two-person tap produces a **1:1** chat (not only a group) when only two phones tap
- [ ] New connection appears in Clicks → Active
- [ ] After BLE reconnect, peer profile **Our timeline** shows the new encounter without clearing app cache

---

## Chat & media (≤ 2 min)

- [ ] Open 1:1 chat → send text → message appears / delivers
- [ ] Chat row press shows scale/border impact; back from chat does not remount tab bar
- [ ] `[KNOWN-7]` Record and send a voice message (Android: must not crash)
- [ ] `[KNOWN-7]` Play the voice bubble (Android: must not crash)
- [ ] `[KNOWN-6]` Start outgoing voice call (Android: after granting mic, call must proceed — not end immediately)
- [ ] End call → return to chat cleanly

---

## Groups (≤ 1 min)

- [ ] `[KNOWN-8]` Clicks FAB → pick ≥2 eligible members → create group → chat opens without crash

---

## Map & beacons (≤ 2 min)

- [ ] Map tab loads location + pins
- [ ] `[KNOWN-5]` Basemap not stuck full grayscale unless Ghost Mode is on
- [ ] `[KNOWN-4]` Create or find an event beacon → appears on map **and** in discovery list
- [ ] Events list from map: map controls stay under overlay (revealed on back, no pop-in)
- [ ] Drop event → pick **Check-in area** scale + **address** (or Use my location) → pin appears → open detail
- [ ] Event detail: Bookmark toggle survives app force-kill (server + disk cache)
- [ ] Event detail: labeled **Check in here** CTA (not hero circle); far from pin → snackbar + toggle reverts
- [ ] Event detail: after RSVP, People → Directory opens sortable attendee list
- [ ] Event detail: Check-in with location denied → snackbar, stays unchecked
- [ ] Online green dot visible on Clicks list avatar when peer is online
- [ ] `[KNOWN-9]` Hazard beacon icon not wildly oversized vs other pins

---

## Settings wrap-up (≤ 1 min)

- [ ] Toggle Ghost Mode on → map dims/grayscale as designed → toggle off
- [ ] Forgot Password from Login opens `/forgot-password` in browser (request form, not set-password error)
- [ ] Sign out → Login screen → sign back in

---

## Fail / waive log

| # | Step | Result (pass / fail / waived KNOWN-N) | Notes |
|---|------|----------------------------------------|-------|
| 1 | | | |
| 2 | | | |
| 3 | | | |

If any **non-waived** item fails → block merge and file/update the audit.
