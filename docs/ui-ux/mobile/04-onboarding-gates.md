# Click Mobile — Onboarding Gates

**Product:** Click — Anti-doomscrolling · Stop scrolling, start living.  
**Scope:** Kotlin Multiplatform mobile (`click/`) — Android + iOS Compose UI only.  
**Out of scope:** Web companion, permissions hub in Settings (successor to legacy permission screens).  
**Source of truth:** `App.kt` gate order, `OnboardingViewModel.kt`, screen composables listed below.  
**Date:** 2026-07-16  

**Visual system:** Functional Clarity (neo-brutalist) — opaque surfaces, 2px `#000` borders, primary `#630ed4`, no glass/blur/gradients. Design-asset mock: invented from design system.

---

## Active gate order (post-auth)

After authentication, `App.kt` evaluates gates in this order:

```
Authenticated
      │
      ▼
 profileGatePending? ──yes──► AppShimmerScreen
      │ no
      ▼
 profileGateActive? ──yes──► ProfileBasicsGateScreen
      │ no
      ▼
 onboardingStep == "loading"? ──yes──► AppShimmerScreen
      │ no
      ▼
 onboardingStep != "complete"? ──yes──► Welcome → Interests → Avatar
      │ no
      ▼
 onboardingHandoff shimmer (600ms) → Home reveal overlay → Main shell (Home)
```

**OnboardingViewModel step order:** `Loading → Welcome → Interests → Avatar → Complete`

Permissions (`PermissionsOnboardingScreen`, `LocationOnboardingScreen`) are **legacy** — not wired in active `App.kt` Phase 2 gate. Documented below for revamp awareness.

### ASCII flowchart (active path)

```
                    ┌─────────────────┐
                    │  Auth success   │
                    └────────┬────────┘
                             │
              ┌──────────────▼──────────────┐
              │  Missing first name and/or  │
              │  birthday on public.users?  │
              └──────┬──────────────┬───────┘
                  yes│              │no
         ┌───────────▼───┐          │
         │ ProfileBasics │          │
         │     Gate      │          │
         └───────┬───────┘          │
                 │ Save and continue│
                 └────────┬─────────┘
                          │
              ┌───────────▼───────────┐
              │ welcomeSeen == false? │
              └───────┬───────┬───────┘
                   yes│       │no
         ┌────────────▼──┐    │
         │    Welcome    │    │
         │    Screen     │    │
         └───────┬───────┘    │
                 │ Let's get started
                 └──────┬─────┘
                        │
              ┌─────────▼─────────┐
              │ interestsCompleted│
              │    == false?      │
              └─────┬──────┬──────┘
                 yes│      │no
       ┌────────────▼──┐   │
       │   Interests   │   │
       │  (min 5 tags) │   │
       │  canSkip=false│   │
       └───────┬───────┘   │
               │ Continue  │
               └─────┬─────┘
                     │
              ┌──────▼──────┐
              │ avatar not  │
              │ set/skipped │
              │ & no remote │
              │   avatar?   │
              └──┬──────┬───┘
              yes│      │no
    ┌────────────▼──┐   │
    │    Avatar     │   │
    │  (skippable)  │   │
    └───────┬───────┘   │
            │ upload/skip
            └─────┬─────┘
                  │
         ┌────────▼────────┐
         │ Handoff shimmer │
         │ + home reveal   │
         └────────┬────────┘
                  ▼
            ┌──────────┐
            │   Home   │
            └──────────┘

  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
  │ LEGACY (not in App.kt Phase 2 gate):   │
  │ PermissionsOnboardingScreen            │
  │ LocationOnboardingScreen               │
  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
```

**Animated transitions:** `AnimatedContent` slide/fade (280ms slide, 180ms fade) between Welcome, Interests, Avatar.

---

## ProfileBasicsGateScreen

**Source:** `ui/screens/ProfileBasicsGateScreen.kt`  
**When shown:** `profileGateActive` — OAuth or other accounts missing `firstName` and/or `birthday` on `public.users`.  
**Blocking:** Yes — no back navigation; must save to continue.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Column` full-screen | `background`, `safeDrawing` top inset |
| Scroll | `verticalScroll` | Horizontal 24dp, vertical 32dp, 16dp spaced |
| Header | Title + body copy | Left-aligned headline |
| Form | First name, Last name | Full-width `OutlinedTextField` |
| Form | Birthday (conditional) | When `requireBirthday = true` |
| Error | Inline `Text` | Server save failure |
| Primary | `Button` full width | Save action |

Birthday field: `testTag = "profile-gate-birthday-field"`. Date picker matches Sign Up opaque bordered sheet styling.

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| First / Last name | Type | Required non-blank |
| Birthday | Type / calendar | `normalizeBirthdayInput`; `DatePickerDialog` |
| Save and continue | Tap | `SupabaseRepository.updateUserProfileBasics` or `updateUserProfileNames` |
| Date picker OK / Cancel | Tap | Set date or dismiss |

`requireBirthday` false when only name missing — birthday field hidden.

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Button disabled until names + birthday valid (when required) |
| **Birthday validation** | Same rules as Sign Up: required, valid ISO date, age ≥13 |
| **Saving** | Button label `"Saving…"`, fields disabled |
| **Error** | Server message (trimmed 200 chars), else `"Could not save profile."` |
| **Success** | `onCompleted()` → refresh `AppDataManager`, clear gate flags |

### Micro-copy

| Context | String |
|---------|--------|
| Title | `"Complete your profile"` |
| Body | `"We need your name and date of birth to continue. This keeps Click safe and age-appropriate."` |
| First name | `"First name"` |
| Last name | `"Last name"` |
| Birthday label | `"Birthday"` |
| Birthday placeholder | `"YYYY-MM-DD"` |
| Calendar CD | `"Open birthday calendar"` |
| Birthday helper (empty) | `"Required — type YYYY-MM-DD or use calendar"` |
| Birthday helper (invalid) | `"Enter a valid date (YYYY-MM-DD)"` |
| Birthday helper (under 13) | `"You must be at least 13 years old"` |
| Primary CTA (idle) | `"Save and continue"` |
| Primary CTA (saving) | `"Saving…"` |
| Save error fallback | `"Could not save profile."` |
| Date picker OK | `"OK"` |
| Date picker Cancel | `"Cancel"` |

### Flow Sequence

1. User lands after auth when `birthdayMissing || firstNameMissing`.
2. Fields pre-filled from `appDataUser` when available.
3. Save → API update → `AppDataManager.refresh(force = true)` → proceeds to onboarding or home.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Scroll | `verticalScroll` for small screens |
| Safe area | `WindowInsets.safeDrawing` top only |
| Button | Default Material `Button` (no explicit iOS/Android corner override in source) |
| Birthday | `testTag` for UI tests |

---

## WelcomeScreen

**Source:** `ui/screens/WelcomeScreen.kt`  
**When shown:** `OnboardingViewModel.Step.Welcome` (`welcomeSeen == false`).  
**Persistence:** `onboardingVm.onWelcomeAcknowledged()` sets `welcomeSeen = true`.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Box` full-screen | Flat `background` `#f9f9f9` |
| Scroll | `Column` centered | Status bar + 40dp top, 24dp horizontal |
| Hero | Circle bordered badge | `"C"` letter, 92dp; solid `primary-container` fill, 2dp `#000` border |
| Copy | Headline + subhead | Personalized if `firstName` present |
| Pills | 3× `WelcomePill` rows | Icon + title + body, 16dp rounded surface |
| CTA | Primary `Button` | 52dp, 16dp corners, arrow icon |
| Hint | Footer label | Below CTA |

Entry animation: 420ms fade + 24dp upward translate (`FastOutSlowInEasing`).

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Let's get started | Tap | `onContinue()` → `onWelcomeAcknowledged()` |

No back navigation. No skip.

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Single CTA, always enabled |
| **Personalized headline** | `"Welcome, {firstName}."` when name non-blank |
| **Generic headline** | `"Welcome to Click."` when no name |
| **Enter** | Animated alpha/offset on first composition |

### Micro-copy

| Context | String |
|---------|--------|
| Headline (named) | `"Welcome, {firstName}."` |
| Headline (generic) | `"Welcome to Click."` |
| Subhead | `"Real connections with the people around you — without the feed, ads, or the performance."` |
| Pill 1 title | `"In-person first"` |
| Pill 1 body | `"Nearby people, verified encounters, and no algorithmic timeline."` |
| Pill 2 title | `"End-to-end encrypted"` |
| Pill 2 body | `"Messages, photos, and files are encrypted on your device — we can't read them."` |
| Pill 3 title | `"Your tribe, not a network"` |
| Pill 3 body | `"Click builds around the interests you pick next — small circles, not reach."` |
| Primary CTA | `"Let's get started"` |
| Footer hint | `"Next — pick a few interests and add a photo."` |

### Flow Sequence

1. First onboarding screen after profile gate (if any).
2. Tap CTA → persist `welcomeSeen` → advance to Interests.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Pill icons | `contentDescription = null` (decorative; title/body carry meaning) |
| CTA arrow icon | `contentDescription = null` |
| Scroll | `verticalScroll` for short devices |
| Button shape | Fixed 16dp corners (both platforms) |

---

## InterestTaggingScreen

**Source:** `ui/screens/InterestTaggingScreen.kt`, `ui/components/InterestEditor.kt`  
**When shown:** `OnboardingViewModel.Step.Interests` (`interestsCompleted == false`).  
**App wiring:** `canSkip = false` — Skip UI not rendered.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Surface` full-screen | Background color |
| Scroll | `Column` | Status bar inset, 20dp horizontal |
| Header | Title + subtitle | Center-aligned |
| Body | `InterestEditor` | Category accordion + subcategory chips |
| Primary | Continue `Button` | 56dp full width |
| Skip | `TextButton` | **Hidden** when `canSkip = false` (active App path) |

Minimum tags: `INTEREST_ONBOARDING_MIN_TAGS = 5`.

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Category row | Tap | Toggle category tag |
| Expand chevron | Tap | Expand/collapse subcategories |
| Subcategory chip | Tap | Toggle sub-tag (respects max if set) |
| Continue | Tap | `onTagsSelected(selectedTags)` when ≥5 selected |
| Skip for now | Tap | `onSkip()` — **only when `canSkip = true`** (not in App) |

**App.kt save path:** `supabaseRepo.updateUserInterests` → on success sets `interestsCompleted`, `flowVersion`, `completedAt`; on failure posts snackbar.

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Continue disabled until `selectedTags.size >= 5` |
| **Selection counter** | See InterestEditor counter below |
| **Category selected** | Primary border, check icon, tinted background |
| **Subcategory expanded** | `FlowRow` of `FilterChip` items |
| **Save failure** | Snackbar: error message or `"Couldn't save interests. Check your connection and try again."` |

### Micro-copy

| Context | String |
|---------|--------|
| Title | `"What are you into?"` |
| Subtitle | `"Pick at least 5 interests to help find common ground with your connections"` |
| Continue CTA | `"Continue"` |
| Skip CTA (component default, inactive in App) | `"Skip for now"` |

**InterestEditor counter** (`minTags = 5`):

| Selection count | Counter text |
|-----------------|--------------|
| `< 5` | `"{n} selected · need {5-n} more"` |
| `≥ 5` | `"{n} selected ✓"` (PrimaryBlue) |

### Flow Sequence

1. User selects ≥5 tags across categories/subcategories.
2. Continue → async save to `user_interests`.
3. Success → `AppDataManager.refresh`, onboarding advances to Avatar.
4. Failure → bottom snackbar (transient message).

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Scroll | Full column scroll for long taxonomy |
| Continue button corners | **iOS 14dp** / **Android 28dp** pill |
| Continue elevation | iOS flat; Android default |
| FilterChip borders | iOS 0.5dp / Android 1dp |
| Skip | Not shown in production App gate |

---

## AvatarScreen

**Source:** `ui/screens/AvatarScreen.kt`  
**When shown:** `OnboardingViewModel.Step.Avatar` — `avatarSetOrSkipped == false` and no remote avatar URL.  
**Skippable:** Yes — `"Skip for now"` calls `onAvatarSetOrSkipped()` without upload.

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Box` full-screen | Flat `background` `#f9f9f9` |
| Scroll | `Column` centered | 24dp horizontal, status bar + 28dp top |
| Header | Title + body | Center-aligned |
| Preview | 168dp circle | Placeholder person icon, existing URL, or selected bytes |
| Sources | Two `AvatarSourceButton` columns | Library / Camera side by side |
| Error | Inline `Text` | Upload or permission errors |
| Primary | Upload `Button` | 52dp, 16dp corners |
| Secondary | Skip `TextButton` | Full width |

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| From library | Tap | `mediaPickers.openPhotoLibrary()` |
| Take photo | Tap | `mediaPickers.openCamera()` |
| Use this photo / Choose a photo | Tap | Upload bytes via `onUploadBytes` |
| Skip for now | Tap | `onSkip()` → `onAvatarSetOrSkipped()` |

Upload success → `AppDataManager.refresh(force = true)` → advance onboarding.

### States

| State | Visual / behavior |
|-------|-------------------|
| **Empty preview** | Person outline icon in bordered circle (`surface-container`, 2dp `#000`) |
| **Existing avatar** | `AsyncImage` from `existingAvatarUrl` |
| **Selected local** | `AsyncImage` from bytes |
| **No selection** | Primary label `"Choose a photo"`, button disabled |
| **Has selection** | Primary label `"Use this photo"`, enabled |
| **Uploading** | Spinner + `"Uploading…"`, sources and skip disabled |
| **Upload error** | First line of error (max 180 chars), else `"Could not upload photo. Try again."` |
| **Media blocked** | Platform picker message inline |

### Micro-copy

| Context | String |
|---------|--------|
| Title | `"Add a photo"` |
| Body | `"A real face goes a long way — but you can skip and add it later from Settings."` |
| From library | `"From library"` |
| Take photo | `"Take photo"` |
| Primary (no selection) | `"Choose a photo"` |
| Primary (has selection) | `"Use this photo"` |
| Primary (uploading) | `"Uploading…"` |
| Skip | `"Skip for now"` |
| Upload error fallback | `"Could not upload photo. Try again."` |
| Selected preview CD | `"Selected avatar"` |
| Current preview CD | `"Current avatar"` |

### Flow Sequence

**Upload path**
1. Pick photo → preview → Use this photo.
2. Upload via `AuthRepository.uploadProfilePicture`.
3. Success → refresh profile → `onAvatarSetOrSkipped()` → Complete.

**Skip path**
1. Skip for now → `onAvatarSetOrSkipped()` → Complete (no network).

**Handoff to Home**
- Onboarding complete → 600ms `AppShimmerScreen` → 380ms home reveal overlay → main shell.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Source buttons | Icon `contentDescription = null`; label text visible |
| Primary button | Fixed 16dp corners (both platforms) |
| Disabled primary | PrimaryBlue @ 32% alpha container |
| Scroll | `verticalScroll` for keyboard/small screens |

---

## PermissionsOnboardingScreen — LEGACY

> **Status:** Not in active `App.kt` Phase 2 onboarding gate. Retained in codebase; planned relocation to Settings Permissions Hub (C9). Documented for revamp awareness.

**Source:** `ui/screens/PermissionsOnboardingScreen.kt`

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `AdaptiveBackground` | Full screen |
| Phase 1 | `PickPreferences` column | `PageHeader` + `AdaptiveCard` toggle list |
| Phase 2 | `MicrophoneExplainer` column | Shown when ambient sound on + no mic permission |
| Footer | Continue / Allow microphone | Platform-styled primary button |

Two internal phases: `PickPreferences` → optional `MicrophoneExplainer`.

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Toggle rows | Switch | Updates preference; some depend on Connection location snap |
| Connection location snap OFF | Toggle off | Cascades: disables Show on map, Insights, Movement, Ambient sound |
| Continue | Tap | May trigger location permission flow, then mic explainer or `onContinue(selection)` |
| Allow microphone | Tap | System mic permission dialog |
| Open Settings | Tap | `openApplicationSystemSettings()` |

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Toggles reflect initial props |
| **Dependent disabled** | Child toggles grayed when Connection snap off |
| **Location permission running** | Continue shows spinner |
| **Mic permission running** | Allow microphone shows spinner |
| **Loading** | `isLoading` disables Continue, shows spinner |

### Micro-copy

**Page header**
| Field | String |
|-------|--------|
| Title | `"Set up your permissions"` |
| Subtitle | `"Choose how Click works before your first connection. You can change these anytime in Settings."` |

**Toggle rows**
| Title | Description |
|-------|-------------|
| `"Connection location snap"` | `"One GPS point when you connect so your Memory Map and connection context stay accurate. No background tracking—the system permission dialog appears when you continue if this is on."` |
| `"Show on my Memory Map"` | `"Save your own connections to a private map you can revisit later."` |
| `"Movement & elevation context"` | `"During a connection, optionally read barometric pressure once to infer a coarse height band. No continuous fitness or health tracking."` |
| `"Enable ambient sound enrichment"` | `"Store only a 2-second sound category for each encounter. No raw audio is saved."` |
| `"Include in business insights"` | `"Share only anonymized venue and campus trends. Never your identity or raw path."` |
| `"Allow message and call alerts"` | `"Get notified when connections message or call you."` |

**Info row (non-toggle)**
| Title | Description |
|-------|-------------|
| `"Bluetooth for nearby Connect"` | `"Tap Connect uses Bluetooth Low Energy to prove you are in the same room. Keep Bluetooth on; the system will ask for permission when you start your first handshake."` |

**Footer note**
| String |
|--------|
| `"Next you'll pick at least 5 interests so Click can personalize your connections."` |

**Microphone explainer phase**
| Field | String |
|-------|--------|
| Title | `"Ambient sound"` |
| Subtitle | `"A short mic sample at connect time helps categorize background noise. No recordings are stored—only a rough category."` |
| Row title | `"Microphone permission"` |
| Row description | `"Tap the button below to open the system dialog. You can change this anytime in Settings."` |
| Primary CTA | `"Allow microphone"` |
| Secondary | `"Open Settings"` |

**Continue CTA (phase 1):** `"Continue"`

### Flow Sequence

1. User sets toggles → Continue.
2. If connection snap on and no location permission → system location dialog.
3. If ambient sound on and no mic permission → Microphone explainer phase.
4. Else → `onContinue(PermissionsOnboardingSelection)`.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Toggle rows | `AdaptiveSwitch` with PrimaryBlue checked colors |
| Icons | Decorative (`contentDescription = null`) |
| Continue button corners | **iOS 14dp** / **Android 28dp** pill |
| Scroll | `verticalScroll` on both phases |

---

## LocationOnboardingScreen — LEGACY

> **Status:** Not in active `App.kt` Phase 2 onboarding gate. Full-screen dark explainer before OS location dialog in legacy flow.

**Source:** `ui/screens/LocationOnboardingScreen.kt`, `LocationOnboardingMapPreview`

### Layout / Container

| Layer | Element | Notes |
|-------|---------|-------|
| Root | `Box` | `BackgroundDark` full screen |
| Scroll | `Column` centered | 24dp padding |
| Header | Headline + subhead | White text |
| Teaser | 200dp `Surface` | Map preview slot (`mapPreviewContent`) |
| Bullets | Two icon rows | Location + Map icons |
| CTAs | Primary + TextButton | Bottom-weighted via `Spacer(weight=1f)` |

Map preview: real connection pins or placeholder dots (`LocationOnboardingMapPreview`).

### Interactive Elements

| Element | Action | Result |
|---------|--------|--------|
| Build my map | Tap | `onBuildMyMap()` — triggers location permission flow (caller) |
| Not now | Tap | `onNotNow()` — skip location setup |

### States

| State | Visual / behavior |
|-------|-------------------|
| **Default** | Dark theme, violet `PrimaryBlue` accents |
| **Map preview empty** | Canvas placeholder circles |
| **Map preview with data** | Up to 12 geo pins from active connections |

### Micro-copy

| Context | String |
|---------|--------|
| Headline | `"Remember where you met"` |
| Subhead | `"Your personal Memory Map shows every connection as a pin—only you see it."` |
| Bullet 1 | `"We capture a single GPS snapshot at the moment you tap—no continuous tracking."` |
| Bullet 2 | `"Anonymous venue and campus trends are included by default, and you can opt out anytime in Your Data."` |
| Primary CTA | `"Build my map"` |
| Secondary CTA | `"Not now"` |

### Flow Sequence

1. Legacy pre-permission explainer.
2. Build my map → caller requests OS location permission.
3. Not now → caller skips map setup.

### A11y & Responsive

| Topic | Detail |
|-------|--------|
| Icons | Decorative (`contentDescription = null`) |
| Build my map corners | **iOS 14dp** / **Android 28dp** pill |
| Scroll | `verticalScroll` for small screens |
| Theme | Fixed dark (`BackgroundDark`, white copy) — not theme-token adaptive |

---

## Platform button corner deltas (onboarding)

| Screen / control | iOS | Android |
|------------------|-----|---------|
| InterestTagging Continue | 14dp | 28dp (pill) |
| PermissionsOnboarding Continue / Allow mic | 14dp | 28dp (pill) |
| LocationOnboarding Build my map | 14dp | 28dp (pill) |
| Welcome Let's get started | 16dp | 16dp |
| Avatar Use this photo | 16dp | 16dp |
| ProfileBasicsGate Save and continue | Material default | Material default |

Elevation: iOS primary buttons use `0.dp` elevation where `LocalPlatformStyle.isIOS`; Android uses Material defaults.

---

## Snackbar messages (onboarding shell)

**Source:** `App.kt` — `AppDataManager.transientUserMessages` collected into `SnackbarHost` during onboarding.

| Trigger | Message |
|---------|---------|
| Interest save failure | Repository error, else `"Couldn't save interests. Check your connection and try again."` |
| Avatar upload (signup path) | See `03-auth.md` AuthViewModel avatar messages |
| Other transient posts | Dynamic string from `AppDataManager.postTransientUserMessage` |

---

## Complete onboarding handoff

| Step | Duration / behavior |
|------|---------------------|
| `onboardingStep` → `"complete"` | OnboardingViewModel reports Complete |
| Handoff shimmer | `AppShimmerScreen` ~600ms |
| Home reveal overlay | Alpha animation 380ms |
| Main shell | `hasPlayedHomeEntrance = true` — user on Home tab graph |

Users who already completed Phase 1 with avatar may fast-forward per `OnboardingViewModel.computeStep` legacy rules.
