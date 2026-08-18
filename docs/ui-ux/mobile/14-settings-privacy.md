# 14 — Settings & Privacy

**Scope:** `SettingsScreen` hub + subpages, `SettingsInterestsCard`, `SettingsPersonalityCard`, `AvailabilitySheet` (modal from Settings), `Edit name` dialog, `PermissionDisplayState` hints, `InterestEditor` / `InterestTaxonomy`, `PersonalityEditor` / `PersonalityTaxonomy`.  
**Source:** `ui/screens/SettingsScreen.kt`, `ui/screens/SettingsInterestsCard.kt`, `ui/screens/SettingsPersonalityCard.kt`, `ui/screens/PermissionDisplayState.kt`, `ui/components/AvailabilitySheet.kt`, `ui/components/InterestEditor.kt`, `ui/components/InterestTaxonomy.kt`, `ui/components/PersonalityEditor.kt`, `ui/components/PersonalityTaxonomy.kt`, `viewmodel/AvailabilityViewModel.kt`  
**Out of scope:** Web, backend APIs, onboarding permission screens (`PermissionsOnboardingScreen`, `LocationOnboardingScreen`), redesign proposals.

**Visual system:** Functional Clarity — opaque surfaces, 1dp quiet borders, primary `#630ed4`, secondary `#224CFF`. Design-asset mock: `click/docs/design-assets/settings/`.

---

## ASCII hierarchy

```
SettingsScreen (tab route "settings")
├── Hub (AppScreenScaffold title: "Settings")
│   ├── SettingsProfileHeader (avatar, name, @handle, outlined Edit Profile)
│   ├── Nav rows → Availability / Alerts / Privacy / Interests / Personality / Saved events / Appearance
│   └── SettingsSignOutButton
├── Subpages (back → hub): Availability, Alerts, Privacy & data, Interests, Personality, Saved events, Appearance
├── AvailabilitySheet (modal)
├── Remove availability? dialog
├── Edit name dialog (from Edit Profile)
└── Local SnackbarHost (avatar, interests, personality, media errors)
```

**Navigation:** Bottom tab `"Settings"` → route `NavigationItem.Settings.route` (`"settings"`). Hub stays mounted; subpages overlay with a horizontal slide + `InteractiveSwipeBackContainer` (same shared swipe-back as the rest of the app). **The hub parallaxes as a subpage is pushed away**, via the shared `InteractiveBackHostState` + `Modifier.interactiveSwipeBackUnderlay` (`ui/components/InteractiveBackPersonality.kt`) — the one parallax implementation in the app, also used by Connections chat, the Map events overlay, and the Add-Click overlay in `App.kt`. The hub native header stays **bound** while a subpage is open and is clipped to the uncovered leading strip during interactive-back (same as Clicks under chat). Do not flip `LocalNativeChromeActive` off for the hub or the Settings title remounts / pops in after dismiss. After a completed swipe, keep that full-width hub mask and do not snap the subpage overlay header back to identity (`LaunchedEffect` `backHost.reset()` still runs while the overlay chrome is composed). Tapping the header back button drives the *same* offset animation through `backHost.dismiss()` rather than a separate `slideOutHorizontally` exit, so tap-back and gesture-back look identical (subpage `exit` is `ExitTransition.None` for both). Hub rows use `ClickNavRow` so press/ripple clips to compact rounded corners. System / edge / hardware back pops **one level** (subpage → hub, then hub → Home) — never skips the hub (`onSubpageOpenChanged` disables tab-level swipe while a subpage is open). Availability uses a dedicated `AvailabilityViewModel` key (`settings-availability`) so visiting Settings does not leak sheet state into Home.

---

## 1. Layout

### Screen shell

| Property | Value |
|----------|-------|
| Root | `AdaptiveBackground` + `AppScreenScaffold` |
| Page title | `"Settings"` |
| Header | Floating solid header bar with 2dp bottom `#000` border; lazy scroll content |
| Cluster spacing | 24dp between preference clusters; 8dp between cluster header and card |
| Search | Header magnifier → `onOpenSearch` → `UnifiedSearchSheet` |
| Local snackbar | Bottom-centered, 24dp padding — avatar, interests, media errors |
| Global snackbar | App-level `Scaffold.snackbarHost` — name-update errors, notification save errors |

### Profile header

| Element | String / style |
|---------|----------------|
| Composable | `SettingsProfileHeader` — first LazyColumn item |
| Avatar | Centered tap target; initials fallback `"?"`; camera badge |
| Display name | First + last (fallback `User.name`, else `"—"`) |
| Email | Shown when `User.email` non-blank |
| Avatar helper | `"Tap photo to change · auto-compressed if needed"` |
| Primary CTA | `"Edit Profile"` → existing Edit name dialog |

### Cluster: Availability

| Element | Position / style |
|---------|------------------|
| Section header | `"Availability"` |
| Card row 1 | Toggle — `"Free currently"`; `EventAvailable` icon tint `PrimaryBlue` when on, else `onSurfaceVariant` |
| Card row 2 | Full-width button — `"Share intent & timeframe"` |
| Subsection title | `"Active availability post"` |
| Per-intent row | Tag label (fallback `"—"`), detail `{timeframe} · {activeUntilLabel}`, actions `"Edit"` / `"Remove"` |
| `activeUntilLabel` formats | `"Today · HH:MM"`, `"Tomorrow · HH:MM"`, `"M/D · HH:MM"` |

### Cluster: Alerts

| Element | String |
|---------|--------|
| Section header | `"Alerts"` |
| Toggle 1 | `"Message notifications"` (no subtitle) |
| Toggle 2 | `"Call alerts"` (no subtitle) |
| Toggle 3 | `"Event reminders"` |
| Toggle 4 | `"Availability matches"` |
| Toggle 5 | `"Hub messages"` |
| Toggle 6 | `"Ambient sound enrichment"` |
| Subtitle 3 | `"Short mic sample at connect time for a noise category only. No recordings stored."` |
| Conditional error (mic off + opt-in on) | `"Microphone access is off — enable it in system settings to use ambient enrichment."` |

### Cluster: Privacy & data

| Element | String |
|---------|--------|
| Section header | `"Privacy & data"` |
| Toggle 1 | `"Ghost Mode"` |
| Subtitle 1 | `"Go off the grid — hide your location, pause matching, and mute presence."` |
| Active banner (ghost on) | `"Ghost mode is on — location not shared."` |
| Toggle 2 | `"Location snap"` |
| Subtitle 2 | `"GPS recorded at moment of tap"` |
| Toggle 3 | `"Memory Map"` |
| Subtitle 3 | `"Personal only, never shared"` |
| Toggle 4 | `"Business insights"` |
| Subtitle 4 | `"Anonymized venue trends"` |
| Collapsible hub header | `"Permissions Hub"` |
| Hub subtitle | `"Review & fix microphone, location, and Bluetooth access."` |
| Chevron a11y | `"Collapse"` / `"Expand"` |

**Location snap hints** (shown only when snap ON):

| Permission state | Hint string |
|------------------|-------------|
| Not set | `"Location isn't enabled yet — tap Allow location in Permissions Hub when you connect."` |
| Denied | `"Location access is off — open System Settings to capture connection snaps."` |
| Granted | No hint |

Hint colors: error (denied), amber `#F59E0B` (not set).

**Note:** Unlike onboarding, Settings does not disable Memory Map / Business insights when Location snap is off — all four toggles are independently enabled.

**Inline panel rows** (`InlinePermissionsPanel`):

| Permission | Title | Description | Badge labels | Primary CTA |
|------------|-------|-------------|--------------|-------------|
| Mic | `"Microphone"` | `"Short ambient sample during handshake."` | `"Granted"`, `"Not set"`, `"Denied"`, `"System-managed"` | `"Allow microphone"` or `"Open settings"` |
| Location | `"Location"` | `"One pin at the moment of a connection."` | same | `"Allow location"` or `"Open settings"` |
| Bluetooth | `"Bluetooth"` | `"Used for nearby tap handshake."` | `"System-managed"` only | none |

Bottom button: `"System Settings"`.

### Cluster: Interests

| Element | Detail |
|---------|--------|
| Section header | `"Interests"` — always shown |
| Card | `SettingsInterestsCard` — rendered only when `userId` non-blank; if no user, header appears with no card |

### Cluster: Personality

| Element | String |
|---------|--------|
| Card title | `"My personality"` |
| Helper | `"Pick exactly 5 traits."` — nothing else. The old `"Existing accounts can skip this — it is not a login gate."` carve-out is gone; it described gate mechanics the user does not need at edit time. Mirrored in click-web `SettingsView.tsx`. |

### Cluster: Saved events

| Element | Detail |
|---------|--------|
| Section header | `"Saved events"` |
| Empty | `"No saved events yet. Bookmark events from Home or the map."` |
| Tiles | Same `SavedEventsSection` as Home |
| Tile tap | Opens `SavedEventDetailSheet` — the same `EventBeaconDetail` bottom sheet Home uses |

### Cluster: Appearance

| Element | String |
|---------|--------|
| Section header | `"Appearance"` |
| Toggle 1 | `"Dark mode"` |
| Toggle 2 | `"Photo pile home"` — one draggable stack of Polaroids per Home section; off = linear list (better with TalkBack / VoiceOver) |

### Sign out

| Element | String / style |
|---------|----------------|
| Composable | `SettingsSignOutButton` — standalone after Appearance |
| Label | `"Sign out"` — iOS: error tint on light error background + error border; Android: solid error fill + `clickBorderColor()` border |

### SettingsInterestsCard layout

| Element | Detail |
|---------|--------|
| Card title | `"My Interests"` |
| Subtitle | `"Select categories and subcategories. Changes power Common Ground with your connections."` |
| Selected tags | Blue chip pills above editor |
| Editor | `InterestEditor` — expandable category rows with subcategory chips |
| Selection count | `"{N} selected"` |
| Primary button | `"Save Interests"` (dirty) or `"Saved"` (clean) |
| Save button radius | iOS 12dp / Android 28dp |

### AvailabilitySheet layout (modal)

| Element | Create mode | Edit mode |
|---------|-------------|-----------|
| Title | `"Share availability"` | `"Edit availability"` |
| Body | `"Pick how long you're open, and a short tag so connections know what you're up for."` | `"Time window starts again from now with the length you pick. Update your tag or timeframe below."` |
| Section label | `"Timeframe"` | `"Timeframe"` |
| Duration chips | `"15 min"`, `"30 min"`, `"45 min"`, `"1 hour"`, `"90 min"`, `"2 hours"`, `"3 hours"`, `"6 hours"`, `"24 hours"` | same |
| Field label | `"Intent tag"` | `"Intent tag"` |
| Placeholder | `"Coffee, study, walk…"` | `"Coffee, study, walk…"` |
| Char counter | `"{n}/25"` | `"{n}/25"` |
| Cancel | `"Cancel"` | `"Cancel"` |
| Submit | `"Post"` | `"Save"` |
| Submit (loading) | `"Saving…"` | `"Saving…"` |

### Edit name dialog layout

| Element | String |
|---------|--------|
| Title | `"Edit name"` |
| Fields | `"First name"`, `"Last name"` |
| Save | `"Save"` (enabled only when first name non-blank) |
| Cancel | `"Cancel"` |

### Remove availability dialog

| Element | String |
|---------|--------|
| Title | `"Remove availability?"` |
| Body | `Stop showing "{label}" as your active availability.` (fallback label: `"this intent"`) |
| Confirm | `"Remove"` |
| Dismiss | `"Cancel"` |

---

## 2. Interactive

| Control | Action | Side effects |
|---------|--------|--------------|
| **Free currently** toggle | `availabilityViewModel.toggleFreeThisWeek()` | Persists to local + Supabase |
| **Share intent & timeframe** | Opens `AvailabilitySheet` (reset first) | Sheet modal |
| **Edit** (active intent) | Opens sheet prefilled | Time window resets from now on save |
| **Remove** (active intent) | Opens confirm dialog | Deletes intent on confirm |
| **Message notifications** toggle | `AppDataManager.setMessageNotificationsEnabled` | Reverts + global snackbar on save fail |
| **Call alerts** toggle | `AppDataManager.setCallNotificationsEnabled` | Reverts + global snackbar on save fail |
| **Ambient sound enrichment** toggle | Saves opt-in; requests mic permission if enabling | Default loads from token storage (`true`) |
| **Ghost Mode** toggle | `AppDataManager.toggleGhostMode()` | Session-scoped; halts sync/presence |
| **Location snap** toggle | `AppDataManager.setConnectionSnapEnabled`; requests location if enabling | Shows permission hints when ON |
| **Memory Map** toggle | `AppDataManager.setShowOnMapEnabled` | Does **not** hide non-core pins; list-sort / Remember Me only |
| **Business insights** toggle | `AppDataManager.setIncludeInInsightsEnabled` | — |
| **Permissions Hub** row | Expand/collapse inline panel | Chevron toggles `"Collapse"` / `"Expand"` |
| **Allow microphone** / **Allow location** | OS permission dialogs | — |
| **Open settings** (denied location) | `openApplicationSystemSettings()` | Deep link to system settings |
| **System Settings** button | Same deep link | — |
| Interest category row | Tap toggles category expand | Subcategory chips toggle selection |
| Subcategory chip | Tap toggles tag in/out of selection | Dirty state enables Save |
| **Save Interests** | `supabaseRepository.updateUserInterests` + `AppDataManager.applyInterestTags` | Snackbar on success/fail |
| **Dark mode** toggle | `onToggleDarkMode` → `tokenStorage.saveDarkModeEnabled` | Immediate theme switch |
| Avatar tap / camera FAB | `mediaPickers.openPhotoLibrary()` | Upload via `AuthRepository.uploadProfilePicture` |
| **Edit name** pencil | Opens dialog | Save calls `AppDataManager.updateProfileName` |
| **Sign out** | `authViewModel.signOut()` | Auth cleared, returns to login |
| Header search | Opens `UnifiedSearchSheet` | — |

**Interest taxonomy filtering:** Only predefined taxonomy tags kept; custom/legacy tags dropped. No custom-interest input on mobile.

---

## 3. States

### Availability section

| State | UI |
|-------|-----|
| Loading intents | `"Loading…"` |
| Empty active list | `"Nothing active yet. Post above to show connections what you're up for and for how long."` |
| Has active intent(s) | Tag + timeframe + Edit/Remove per row |
| Delete fail | Inline red error via `formatAvailabilityIntentSaveError` |

### Alerts / Privacy & data / Appearance

| State | UI |
|-------|-----|
| Default | Toggle reflects persisted preference |
| Notification save fail | Global snackbar; toggle reverts |
| Ghost on | Banner `"Ghost mode is on — location not shared."`; icon tint `PrimaryBlue` |
| Location snap hints | Conditional amber/error helper text |

### Permissions Hub

| Badge | Meaning |
|-------|---------|
| `"Granted"` | OS permission granted |
| `"Not set"` | Not yet requested |
| `"Denied"` | User denied; shows `"Open settings"` CTA |
| `"System-managed"` | Bluetooth only; no CTA |

### SettingsInterestsCard

| State | UI |
|-------|-----|
| **No userId** | Entire card hidden (early return) |
| **Loading** | `CircularProgressIndicator` when cache empty, no tags, no error |
| **Loaded empty** | Editor shown, no tag chips, `"0 selected"`, button `"Saved"` disabled |
| **Loaded with tags** | Blue chip pills + editor |
| **Dirty** | Button `"Save Interests"`, enabled |
| **Saving** | Button shows `CircularProgressIndicator`, disabled |
| **Save success** | Button → `"Saved"`, snackbar `"Saved {N} interests"`, `loadError` cleared |
| **Save error** | Inline red `"Could not save interests"` + snackbar with same message |
| **Load error** | Inline red `"Could not load interests"` |

**Save rules:** No min/max tag count (`minTags=null`, `maxTags=null`). Save enabled only when `tagsDirty && !tagsSaving`.

### Profile header / Account

| State | UI |
|-------|-----|
| Default | Display name or `"—"`; email when present |
| Photo uploading | Spinner overlay on avatar |
| Photo success | Local snackbar `"Profile photo updated"` |
| Photo fail | Server msg or `"Could not update profile photo"` |
| Media blocked | Platform-specific photo access string |
| Name save | Silent success (optimistic UI); errors via global snackbar |
| Name save fail | `"Couldn't update your profile. Please try again."` or server message |

### AvailabilitySheet submit errors

| Error | String |
|-------|--------|
| Not signed in | `"Sign in to share availability."` |
| Empty tag | `"Add a short intent tag."` |
| Migration missing | `"Availability isn't set up on the server yet. Ask your admin to run the database migration."` |
| Permissions | `"Couldn't save (permissions). Sign out, sign in again, then retry."` |
| Session | `"Session issue. Sign in again, then retry."` |
| Generic | `"Could not save. Try again."` |
| Server | Raw server message (truncated 240 chars) |

### Snackbar / feedback messages (complete)

| Trigger | Message | Host |
|---------|---------|------|
| Avatar success | `"Profile photo updated"` | Settings local |
| Avatar failure | Server msg or `"Could not update profile photo"` | Settings local |
| Media blocked (Android) | `"Couldn't read that photo. If access was denied, enable Photos & videos permission for Click in Settings."` | Settings local |
| Media blocked (iOS) | `"Couldn't read that photo. Enable Photos access for Click in Settings."` | Settings local |
| Interests save success | `"Saved {N} interests"` | Settings local |
| Interests save fail | Server msg or `"Could not save interests"` | Inline + local |
| Interests load fail | Server msg or `"Could not load interests"` | Inline only |
| Name update fail | `"Couldn't update your profile. Please try again."` or server msg | App global |
| Notification save fail | `"Couldn't save notification settings. Please try again."` or server msg | App global |

---

## 4. Micro-copy

### Screen chrome

| Key | String |
|-----|--------|
| Tab label | `"Settings"` |
| Page title | `"Settings"` |
| Header search a11y | `"Search"` |

### Availability

| Key | String |
|-----|--------|
| Section | `"Availability"` |
| Free toggle | `"Free currently"` |
| Share button | `"Share intent & timeframe"` |
| Subsection | `"Active availability post"` |
| Loading | `"Loading…"` |
| Empty | `"Nothing active yet. Post above to show connections what you're up for and for how long."` |
| Tag fallback | `"—"` |
| Edit | `"Edit"` |
| Remove | `"Remove"` |
| Sheet create title | `"Share availability"` |
| Sheet edit title | `"Edit availability"` |
| Sheet create body | `"Pick how long you're open, and a short tag so connections know what you're up for."` |
| Sheet edit body | `"Time window starts again from now with the length you pick. Update your tag or timeframe below."` |
| Timeframe label | `"Timeframe"` |
| Intent tag label | `"Intent tag"` |
| Intent placeholder | `"Coffee, study, walk…"` |
| Cancel | `"Cancel"` |
| Post | `"Post"` |
| Save | `"Save"` |
| Saving | `"Saving…"` |
| Remove dialog title | `"Remove availability?"` |
| Remove dialog body | `Stop showing "{label}" as your active availability.` |
| Remove confirm | `"Remove"` |

### Alerts

| Key | String |
|-----|--------|
| Section | `"Alerts"` |
| Messages | `"Message notifications"` |
| Calls | `"Call alerts"` |
| Ambient toggle | `"Ambient sound enrichment"` |
| Ambient subtitle | `"Short mic sample at connect time for a noise category only. No recordings stored."` |
| Mic off error | `"Microphone access is off — enable it in system settings to use ambient enrichment."` |

### Privacy & data

| Key | String |
|-----|--------|
| Section | `"Privacy & data"` |
| Ghost Mode | `"Ghost Mode"` |
| Ghost subtitle | `"Go off the grid — hide your location, pause matching, and mute presence."` |
| Ghost banner | `"Ghost mode is on — location not shared."` |
| Location snap | `"Location snap"` |
| Location snap subtitle | `"GPS recorded at moment of tap"` |
| Memory Map | `"Memory Map"` |
| Memory Map subtitle | `"Personal only, never shared"` |
| Business insights | `"Business insights"` |
| Business insights subtitle | `"Anonymized venue trends"` |
| Hub title | `"Permissions Hub"` |
| Hub subtitle | `"Review & fix microphone, location, and Bluetooth access."` |
| Microphone | `"Microphone"` |
| Microphone desc | `"Short ambient sample during handshake."` |
| Location | `"Location"` |
| Location desc | `"One pin at the moment of a connection."` |
| Bluetooth | `"Bluetooth"` |
| Bluetooth desc | `"Used for nearby tap handshake."` |
| Allow microphone | `"Allow microphone"` |
| Allow location | `"Allow location"` |
| Open settings | `"Open settings"` |
| System Settings | `"System Settings"` |
| Badge granted | `"Granted"` |
| Badge not set | `"Not set"` |
| Badge denied | `"Denied"` |
| Badge system | `"System-managed"` |

### Interests

| Key | String |
|-----|--------|
| Section | `"Interests"` |
| Card title | `"My Interests"` |
| Card subtitle | `"Select categories and subcategories. Changes power Common Ground with your connections."` |
| Selection count | `"{N} selected"` |
| Save dirty | `"Save Interests"` |
| Save clean | `"Saved"` |
| Load error | `"Could not load interests"` |
| Save error | `"Could not save interests"` |
| Save success snackbar | `"Saved {N} interests"` |

**Interest taxonomy categories** (emoji + label + subcategories):

| Emoji | Category | Subcategories |
|-------|----------|---------------|
| 🎵 | `"Music"` | `"Live Shows"`, `"DJing"`, `"Producing"`, `"Guitar"`, `"Piano"`, `"Singing"`, `"Alto Sax"`, `"Tenor Sax"`, `"Drums"`, `"Violin"`, `"Bass"`, `"Songwriting"` |
| 🎼 | `"Instruments"` | `"Alto Sax"`, `"Tenor Sax"`, `"Trumpet"`, `"Clarinet"`, `"Cello"`, `"Flute"`, `"Ukulele"`, `"Synth"`, `"Beat Making"` |
| 🥾 | `"Hiking"` | `"Day Hikes"`, `"Backpacking"`, `"Trail Running"`, `"Rock Climbing"`, `"Scrambling"`, `"Nature Walks"` |
| ☕ | `"Coffee"` | `"Espresso"`, `"Pour Over"`, `"Cafe Hopping"`, `"Latte Art"`, `"Home Brewing"` |
| 🎮 | `"Gaming"` | `"PC"`, `"Console"`, `"Indie"`, `"Board Games"`, `"VR"`, `"Competitive"`, `"Co-op"`, `"RPG"`, `"Strategy"` |
| 📚 | `"Reading"` | `"Fiction"`, `"Non-Fiction"`, `"Sci-Fi"`, `"Fantasy"`, `"Book Clubs"`, `"Poetry"` |
| 💪 | `"Fitness"` | `"Gym"`, `"Yoga"`, `"CrossFit"`, `"Running"`, `"Swimming"`, `"Martial Arts"`, `"Pilates"`, `"Cycling"` |
| 💻 | `"Tech"` | `"AI/ML"`, `"Web Dev"`, `"Mobile Dev"`, `"Cybersecurity"`, `"Hardware"`, `"Open Source"`, `"Cloud"`, `"Data Science"` |
| 🎨 | `"Art"` | `"Painting"`, `"Sketching"`, `"Digital Art"`, `"Sculpture"`, `"Ceramics"`, `"Street Art"`, `"Calligraphy"`, `"Graphic Design"` |
| 🎬 | `"Film"` | `"Indie Film"`, `"Horror"`, `"Documentaries"`, `"Animation"`, `"Film Making"` |
| 🍕 | `"Food"` | `"Cooking"`, `"Baking"`, `"Food Trucks"`, `"Fine Dining"`, `"Vegan"`, `"Meal Prep"` |
| ✈️ | `"Travel"` | `"Backpacking"`, `"Road Trips"`, `"City Breaks"`, `"Solo Travel"`, `"Camping"`, `"Digital Nomad"`, `"Hostels"` |
| ⚽ | `"Sports"` | `"Basketball"`, `"Soccer"`, `"Baseball"`, `"Football"`, `"Softball"`, `"Ultimate"`, `"Tennis"`, `"Volleyball"`, `"Skiing"`, `"Surfing"` |
| 🏃 | `"Outdoor Sports"` | `"Running"`, `"Cycling"`, `"Triathlon"`, `"Climbing"`, `"Skiing"`, `"Snowboarding"`, `"Surfing"` |
| 🤝 | `"Volunteering"` | `"Environment"`, `"Education"`, `"Community"`, `"Animal Welfare"`, `"Mentoring"` |
| 📸 | `"Photography"` | `"Street"`, `"Portrait"`, `"Landscape"`, `"Film Photography"`, `"Drone"`, `"Concert Photography"`, `"Editing"` |
| 🧘 | `"Wellness"` | `"Meditation"`, `"Mindfulness"`, `"Breathwork"`, `"Journaling"`, `"Mental Health"` |
| 🗣️ | `"Languages"` | `"Spanish"`, `"French"`, `"Mandarin"`, `"Japanese"`, `"Korean"`, `"Language Exchange"` |
| 🎭 | `"Performing Arts"` | `"Theater"`, `"Improv"`, `"Acting"`, `"Stand-up Comedy"`, `"Dance"` |
| 🐶 | `"Animals"` | `"Dogs"`, `"Cats"`, `"Birds"`, `"Animal Rescue"`, `"Pet Training"` |
| 🧩 | `"Puzzles & Strategy"` | `"Chess"`, `"Sudoku"`, `"Escape Rooms"`, `"Crosswords"`, `"Go"` |

### Appearance

| Key | String |
|-----|--------|
| Section | `"Appearance"` |
| Toggle | `"Dark mode"` |
| Toggle | `"Photo pile home"` |

### Profile header / Sign out

| Key | String |
|-----|--------|
| Edit Profile CTA | `"Edit Profile"` |
| Avatar helper | `"Tap photo to change · auto-compressed if needed"` |
| Empty name | `"—"` |
| Sign out | `"Sign out"` |
| Edit dialog title | `"Edit name"` |
| First name | `"First name"` |
| Last name | `"Last name"` |
| Initials fallback | `"?"` |

---

## 5. Flow

### Toggle availability status

```
Settings tab → Availability section
  → Toggle "Free currently" → immediate persist
  → Optional: tap "Share intent & timeframe"
    → AvailabilitySheet
    → Pick duration chip → enter intent tag → "Post"
    → Sheet dismisses → active list refreshes
```

### Manage active availability post

```
View "Active availability post"
  → "Edit" → sheet prefilled → "Save" (resets window from now)
  → "Remove" → "Remove availability?" dialog → "Remove"
    → Inline error if delete fails
```

### Privacy / location

```
Privacy & data cluster
  → Toggle Ghost Mode / Location snap / Memory Map / Business insights
  → Enabling Location snap without permission → OS location dialog
  → Location snap hints guide to Permissions Hub or System Settings
```

### Permissions Hub

```
Privacy & data → tap "Permissions Hub" to expand
  → Review badge status per permission
  → "Allow microphone" / "Allow location" → OS dialogs
  → Denied location → "Open settings" on row or "System Settings" button
```

### Edit interests

```
Interests section → wait for load (spinner if cold)
  → Expand categories, tap chips to select/deselect
  → "Save Interests" when dirty
  → Success snackbar "Saved {N} interests" + button shows "Saved"
```

### Profile / Sign out

```
Tap avatar or camera icon → photo library → upload → "Profile photo updated"
Tap "Edit Profile" → "Edit name" dialog → "Save" (first name required)
"Sign out" → auth cleared, returns to login
```
### Appearance

```
Toggle "Dark mode" → persisted to token storage
```

---

## 6. A11y

| Element | `contentDescription` / semantics |
|---------|----------------------------------|
| Header search | `"Search"` |
| Permissions chevron | `"Collapse"` / `"Expand"` |
| Profile photo (image) | `"Profile photo"` |
| Change photo FAB | `"Change profile photo"` |
| Edit Profile button | Visible label `"Edit Profile"` |
| Sign out icon | `null` (decorative) |
| Section row icons (toggles, permissions) | `null` |
| InterestEditor expand icons | `null` |
| InterestEditor check icons | `null` |
| `AdaptiveSwitch` | No custom semantics — relies on platform switch behavior |

**Gaps:**

- Toggle rows lack explicit `contentDescription` combining title + checked state.
- Permission status badges are visual only (icon + colored text, no a11y description).
- Category rows use clickable `Surface` with emoji + text — no explicit a11y label beyond visible text.
- Interest chips have no individual semantics.

**Focus order:** Header (title → search) → scrollable sections top-to-bottom → inline dialogs when open.

**Platform notes:** Sign out button colors/elevation differ iOS vs Android. Interest save button corner radius differs iOS (12dp) vs Android (28dp).

---

## Related documents

- [04-onboarding-gates.md](04-onboarding-gates.md) — permission onboarding variants (`"Connection location snap"`, `"Show on my Memory Map"`, etc.)
- [07-connections-inbox.md](07-connections-inbox.md) — availability bolt on connection rows
- [11-search.md](11-search.md) — `UnifiedSearchSheet` from header
