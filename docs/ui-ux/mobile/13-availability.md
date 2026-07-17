# 13 — Availability

**Scope:** Kotlin Multiplatform mobile — `AvailabilitySheet`, `AvailabilityComponents` (`AvailabilityToggle`, `MutualAvailabilityCard`, `AvailabilityIndicator`, plus `DaySelectionRow` / `ActivitySelectionRow` for settings surfaces).  
**Source:** `ui/components/AvailabilitySheet.kt`, `ui/components/AvailabilityComponents.kt`, `viewmodel/AvailabilityViewModel.kt`, `data/models/AvailabilityModels.kt`  
**Out of scope:** Web, backend APIs, redesign.

**Visual system:** Functional Clarity (neo-brutalist) — opaque surfaces, 2px `#000` borders, primary `#630ed4`, no glass/blur/gradients. Design-asset mock: invented from design system.

---

## ASCII hierarchy

```
AvailabilitySheet (modal — ClickFormBottomSheet)
├── Title + explainer
├── "Timeframe" FilterChip grid (AvailabilityIntentDuration)
├── "Intent tag" OutlinedTextField
├── submitError (conditional)
└── Cancel | Post/Save

AvailabilityComponents (molecules — used on profile/settings/home surfaces)
├── AvailabilityToggle — "Free this week" switch card
├── MutualAvailabilityCard — match row + Coffee? / Message
├── AvailabilityIndicator — compact chat header chip
├── DaySelectionRow — Mon–Sun chips
└── ActivitySelectionRow — preset activity chips
```

**On Home, the strip sits after Featured Event and immediately before Explore nearby** (archive / Poll-Pair stay-in-touch cards come after Explore). See [05-home.md](05-home.md).

**Entry:** Home `HomeAvailabilityIntentsRow` chip tap → `AvailabilitySheet` (see [05-home.md](05-home.md)).

---

## 1. Layout

### AvailabilitySheet

| Property | Value |
|----------|-------|
| Shell | `ClickFormBottomSheet` on opaque `surface` + 2dp `#000` border |
| Padding | 24dp horizontal, 16dp vertical |
| Section spacing | 16dp vertical |
| Timeframe chips | `FlowRow`, 8dp gaps |
| Intent field | Full-width `OutlinedTextField`, single line |
| Supporting text | `"{length}/{max}"` under tag field |
| Footer actions | `TextButton` `"Cancel"` + `Button` — equal weight row |

### AvailabilityToggle

| Property | Value |
|----------|-------|
| Shape | 16dp rounded `Surface` |
| Padding | 16dp |
| Leading icon | 48dp circle — `EventAvailable` / `EventBusy` |
| Trailing | `Switch` or 24dp progress when loading |

### MutualAvailabilityCard

| Property | Value |
|----------|-------|
| Shape | 12dp rounded surface, primary-tinted border |
| Padding | 12dp |
| Avatar | 40dp circle with initial |
| Match badge | Primary pill — check icon + `"Match!"` |
| Actions | Two equal-width buttons: outlined `"Coffee?"`, filled `"Message"` |

### AvailabilityIndicator (compact)

12dp icon + `labelSmall` text inline row. Hidden when `AvailabilityStatus.NOT_SET`.

---

## 2. Interactive

### AvailabilitySheet

| Control | Action |
|---------|--------|
| Duration chip | `viewModel.setIntentDuration(option)` |
| Tag field | `viewModel.updateIntentTagInput` (max length enforced in VM) |
| `"Cancel"` | `clearIntentSubmitError()` + `onDismiss()` |
| `"Post"` / `"Save"` | `submitAvailabilityIntent(onSuccess = dismiss)` |
| Dismiss sheet | `onDismissRequest` |

### AvailabilityToggle

| Gesture | Action |
|---------|--------|
| Tap row or switch | `onToggle()` when not loading |

### MutualAvailabilityCard

| Button | Prefill message |
|--------|-----------------|
| `"Coffee?"` | `ActivitySuggestions.getSuggestedMessage(first common activity)` — random from coffee/study/generic pools |
| `"Message"` | Fixed: `"Hey! I saw we're both free this week. Want to hang out?"` |

### DaySelectionRow / ActivitySelectionRow

Toggle chips; callback updates selected days/activities lists (settings flows).

---

## 3. States

### AvailabilitySheet modes

| Mode | Title | Body | Primary CTA |
|------|-------|------|-------------|
| Create | `"Share availability"` | `"Pick how long you're open, and a short tag so connections know what you're up for."` | `"Post"` |
| Edit (`editingAvailabilityIntentId` set) | `"Edit availability"` | `"Time window starts again from now with the length you pick. Update your tag or timeframe below."` | `"Save"` |
| Submitting | Same | — | `"Saving…"` (button label) |

### Submit gating

- `canSubmit = tag.trim().isNotEmpty() && !submitting`
- Chips and field disabled while `submitting`
- `submitError` shown in `error` color above footer

### AvailabilityToggle states

| `isFreeThisWeek` | Headline | Subcopy |
|------------------|----------|---------|
| `true` | `"Free this week!"` | `"Others can see you're open to hanging out"` |
| `false` | `"Set as available"` | `"Let your connections know you're free"` |
| `isLoading` | Same headlines | Switch replaced by spinner |

### AvailabilityIndicator

| `AvailabilityStatus` | Label |
|----------------------|-------|
| `FREE_NOW` | `"Free now"` |
| `FREE_THIS_WEEK` | `"Free this week"` |
| `BUSY` | `"Busy"` |
| `NOT_SET` | (hidden) |

### MutualAvailabilityCard

- Shown when `mutualAvailability.hasMutualAvailability()` (caller responsibility)
- Suggested subtitle from `getSuggestedMeetupMessage()` (dynamic templates below)
- Common days (max 3) and activities (max 2) as chips

---

## 4. Micro-copy

### AvailabilitySheet

**Titles & body**

- `"Share availability"`
- `"Pick how long you're open, and a short tag so connections know what you're up for."`
- `"Edit availability"`
- `"Time window starts again from now with the length you pick. Update your tag or timeframe below."`

**Sections & fields**

- `"Timeframe"`
- `"Intent tag"` (label)
- `"Coffee, study, walk…"` (placeholder)
- `"{n}/{max}"` (supporting — max from `AVAILABILITY_INTENT_TAG_MAX_LENGTH` in VM)

**Duration chips (`AvailabilityIntentDuration`)**

- `"15 min"`
- `"30 min"`
- `"45 min"`
- `"1 hour"`
- `"90 min"`
- `"2 hours"`
- `"3 hours"`
- `"6 hours"`
- `"24 hours"`

**Footer**

- `"Cancel"`
- `"Post"` (create)
- `"Save"` (edit)
- `"Saving…"` (in-flight)

### AvailabilityToggle

- `"Free this week!"`
- `"Others can see you're open to hanging out"`
- `"Set as available"`
- `"Let your connections know you're free"`

### MutualAvailabilityCard

**Badge**

- `"Match!"`

**Name fallback**

- `"Someone"` (when `otherUserName` null)

**Suggested meetup lines (`getSuggestedMeetupMessage`)**

- `"Both free on {day} for {activity lowercase}?"`
- `"You're both up for {activity lowercase}!"`
- `"Both available on {day}!"`
- `"You're both free this week!"` (fallback)

**Buttons**

- `"Coffee?"`
- `"Message"`

**Message prefill (`"Message"` button)**

- `"Hey! I saw we're both free this week. Want to hang out?"`

**`"Coffee?"` prefill pools (`ActivitySuggestions`)**

Coffee (when activity contains `"coffee"`):

- `"Hey! Want to grab coffee sometime this week?"`
- `"I'm free this week - coffee? ☕"`
- `"Let's catch up over coffee!"`
- `"Free for coffee this week if you are!"`

Study:

- `"Want to study together sometime?"`
- `"I could use a study buddy this week!"`
- `"Library session soon?"`
- `"Let's hit the books together!"`

Generic:

- `"Hey, I'm free this week! Want to hang out?"`
- `"I've got some free time - want to meet up?"`
- `"Let's hang out soon!"`
- `"Free this week if you want to do something!"`

### ActivitySelectionRow presets

- `"☕ Grab coffee"`
- `"📚 Study together"`
- `"🍕 Get lunch/dinner"`
- `"🚶 Go for a walk"`
- `"🎮 Play games"`
- `"🏃 Work out"`
- `"🎬 Watch something"`
- `"💬 Just chat"`

### DaySelectionRow

- `"Mon"`, `"Tue"`, `"Wed"`, `"Thu"`, `"Fri"`, `"Sat"`, `"Sun"`

### AvailabilityIndicator

- `"Free now"`
- `"Free this week"`
- `"Busy"`

---

## 5. Flow

```mermaid
flowchart TD
    A[Home intent chip / Edit intents] --> B[AvailabilitySheet opens]
    B --> C[Pick duration chip]
    C --> D[Enter intent tag]
    D --> E{Edit mode?}
    E -->|no| F[Post]
    E -->|yes| G[Save — window resets from now]
    F --> H[VM submitAvailabilityIntent]
    G --> H
    H -->|success| I[Dismiss + refresh intents on Home]
    H -->|error| J[submitError inline]

    K[Profile / settings surface] --> L[AvailabilityToggle]
    L --> M[Toggle free-this-week status]

    N[Mutual match detected] --> O[MutualAvailabilityCard]
    O --> P{Coffee? | Message}
    P --> Q[onSendMessage prefilled text]
    Q --> R[Open chat composer with text]
```

**Edit path:** Tapping an existing intent chip on Home calls `resetAvailabilityIntentSheet()` with intent id — sheet opens in edit mode with tag/duration prefilled.

**Overlap hints on Home:** Separate gold overlap lines from `HomeViewModel` (not in this sheet) — see [05-home.md](05-home.md).

---

## 6. A11y

| Element | Behavior |
|---------|----------|
| Duration chips | `FilterChip` — label text is duration string; `selected` state announced |
| Intent field | `label` + `placeholder` + `supportingText` for character count |
| Cancel / Post | `TextButton` / `Button` with visible text labels |
| AvailabilityToggle | Entire row clickable; switch duplicates toggle action |
| MutualAvailabilityCard buttons | `"Coffee?"` and `"Message"` — icon + text (icons decorative) |
| Match badge | `"Match!"` text inside colored pill |
| AvailabilityIndicator | Icon + text pair; 12dp icon may be below minimum touch target (display-only) |
| Day / activity chips | Standard `FilterChip` semantics |

**Keyboard:** Intent field uses `ImeAction.Done`; capitalization `Words`.

**Error announcement:** `submitError` text uses `bodySmall` + error color — ensure TalkBack focus on submit failure if enhancing.
