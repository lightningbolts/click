# 05 — Consistency, Reuse & Flow Efficiency

**Role:** Cross-cutting craft. Remove rough edges that remain after motion/shell/chat/call polish.  
**Code hotspots:** `ui/components/*`, duplicated headers/buttons/sheets across screens, settings/profile/search patterns, any parallel “glass” vs “brutalist” helpers.

---

## 1. Goals

| Goal | User feel |
|------|-----------|
| One product | Same press, sheet, toast, dialog, and empty states everywhere |
| Less code for same job | One primitive per purpose; screens parameterize |
| Faster flows | Fewer taps / waits for common tasks without changing product rules |
| Theme fidelity | Lingering pre-FC or mismatched chrome cleaned up |

---

## 2. Non-goals

- Large IA redesigns or new tabs  
- Renaming every `Glass*` symbol for purity (rename only when it unlocks clarity during touch)  
- Drive-by refactors unrelated to interaction polish  

---

## 3. Theme consistency sweep

### 3.1 Audit for

- Hard-coded colors bypassing `MaterialTheme` / `clickBorderColor()` / `clickCardSurface()`  
- Light-only assumptions in dark mode (sheets, search, map chrome)  
- Mixed corner radii / border widths on sibling components  
- Gradient/glow leftovers on product surfaces (nav glass exception is intentional — see part 02)  
- Typography that isn’t Manrope scale  
- Inconsistent icon button sizes (40dp chat vs random others)  

### 3.2 Required outcomes

- Screen chrome (headers, icon buttons, segment controls) share tokens.  
- Sheets use one gesture physics + grabber + scrim policy.  
- Toasts/snackbars: one host behavior.  
- Destructive vs primary actions visually consistent across safety sheets.  

---

## 4. Component unification (dedup)

### 4.1 Likely duplicate classes (Fable must verify in tree)

Plan should explicitly search and propose merges for patterns like:

| Pattern | Examples to hunt |
|---------|------------------|
| Circular icon buttons | Chat header, map glass icon, settings rows |
| Page headers | `PageHeader`, `LiquidGlassPageHeader`, per-screen title rows |
| Bottom sheets | `GlassModalBottomSheet`, `GlassAdaptiveBottomSheet`, `ClickBottomSheet`, form sheets |
| Cards / bento rows | `GlassCard`, `AdaptiveCard`, bento option rows |
| Empty / error / loading | Chat loaders, inbox empty, map empty, search empty |
| Avatars | Online indicator frames, group clusters, profile faces |
| Segmented controls | Inbox segments vs other tabs |
| Keyboard inset helpers | Chat vs hub vs forms |

### 4.2 Merge rules

1. Keep the API that already has the most call sites / tests.  
2. Parameterize variants (hub vs 1:1) instead of fork files.  
3. Delete dead wrappers in the same PR slice when safe.  
4. Do not unify across fundamentally different gesture owners (e.g. map gesture layer vs sheet).  

---

## 5. Flow efficiency (speed without new features)

Polish **time-to-outcome** for existing flows:

| Flow | Efficiency lens |
|------|-----------------|
| Open chat from inbox | Instant paint from cache; defer non-critical work |
| Send photo | Fewer intermediate blocking screens; clear pending state |
| Add Click → success → chat | Minimal mandatory beats; skippable celebration |
| Search → profile → message | Preserve back stack; don’t reload search |
| Set availability | Sheet open focused; save feedback immediate |
| Settings toggles (ghost, etc.) | Optimistic UI where safe; rollback on failure |
| Map → event detail → RSVP | No extra round-trips in UI; busy states on the button |

### Required outcomes

- Every primary CTA shows **immediate** feedback (disable + progress or optimistic).  
- Double-tap does not double-submit.  
- Long operations (upload, connect) show determinate progress when possible.  
- Avoid mandatory multi-screen confirmations where a single sheet suffices (only where product already allows).  

---

## 6. Interaction consistency matrix

Fable plan should fill this (short table) so Grok can implement systematically:

| Interaction | Standard |
|-------------|----------|
| Tap press | 2dp translate + darken (existing) |
| Long press | Haptic + sheet/menu |
| Swipe back | Shared container physics |
| Sheet dismiss | Shared commit/flick |
| Success | Shared success beat (part 04) |
| Error | Shared popup/toast |
| Toggle | Immediate thumb + commit |
| Tab select | Shared indicator spring |

Any screen violating the matrix gets a fix item.

---

## 7. Accessibility & reduce-motion

- Honor platform reduce-motion: replace large springs with short fades where feasible.  
- Keep 44dp targets; don’t shrink hit areas for aesthetics.  
- Motion must not convey **only** meaning (always have text/icon state).  
- Contrast remains FC-compliant on new glass nav.  

---

## 8. Documentation hygiene (light)

After slices land, Grok (or Fable follow-up) should add a short pointer in `docs/ui-ux/mobile/00-INDEX.md` or continuation handoff: **“Interaction polish track”** + which nav chrome exception exists. Do not rewrite all feature docs unless behavior meaningfully changed.

---

## 9. Acceptance pack (initiative-level)

- [ ] No obvious one-off chrome for shared jobs on primary tabs  
- [ ] Dark/light parity on polished surfaces  
- [ ] Common flows feel shorter / clearer without feature creep  
- [ ] Reduce-motion / a11y smoke pass  
- [ ] Dead duplicate components removed or scheduled with owners  

---

## 10. Final Grok backlog shape (Fable one-shot output)

When finishing **Prompt A** (or fallback D), emit:

```markdown
## Backlog
0. P0 Home back-gesture flicker …
1. S2 …
…

## Dedup list
- Merge X into Y (call sites: …)

## Explicit non-work
- …
```

This becomes the only artifact Grok needs day-to-day — keep it short enough to paste, but **complete** enough that no second Fable planning pass is required.
