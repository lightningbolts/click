# 02: Richer profile info, stories and connection recaps

**iOS reference:**
- `iOS/Core/Connections/FriendshipStats.swift`
- `iOS/Features/Profile/{FriendshipViews,ProfileView,PeerProfileModel,PublicProfileView}.swift`
- `iOS/Features/Connect/PostConnect{View,Model}.swift`
- `iOS/Features/Home/{HomeFeedModel,HomeView,HomeComponents}.swift`
- `iOS/Core/Me/MeRepository.swift`
- Golden tests: `click-ios/Tests/ClickTests/Round7Tests.swift:284-330,457+`

**Android today:**
- Profile sheet: `KMP/ui/components/ProfileBottomSheet.kt`, `ProfileSheetPanels.kt`, `ProfileLegacyTimeline.kt`, `ProfileTimelineMetrics.kt`.
- Journal: at parity.
- Home recap: present, but it shows fake zeros.
- Everything else in this sheet is missing.

**Backend:**
- Nothing new is needed for stats, story, souvenir or map. They are computed on-device from `connection_encounters`, which Android already loads with `gps_lat`, `gps_lon`, `weather_snapshot` and `semantic_location` (`KMP/data/models/ConnectionEncounter.kt:22-25`).
- Bio: `users.bio` (≤ 160 chars), read and written through `/api/users/{id}/profile`.
- Pending hangouts, log hangout and wave come from sheet 03 §B.

---

## 1. `FriendshipStats` port (Phase 1 foundation)

New pure-Kotlin files `KMP/data/models/FriendshipStats.kt` and `GroupHangout.kt`. Port the algorithms **exactly**.

| Item | Rule |
|---|---|
| Hangouts | Count of `connection_encounters` rows. |
| Levels | New Click (1) · Familiar (3) · Regulars (6) · Close (12) · Inseparable (25). The level is the last one whose threshold ≤ count, with a floor of New Click. |
| Progress | `(count − lvl.threshold) / (next.threshold − lvl.threshold)`, clamped to 0–1, and 1 at the top level. `toNextLevel = max(0, next.threshold − count)`. |
| Place name | `semantic_location.name`, else the first comma-separated part of `location_name`, else `display_location`. |
| Spot key | `"name:" + placeName.trim().lowercase()` if there is a name, else `"geo:%.3f,%.3f"` from lat/lon, else none. Reject (0,0) and out-of-range coordinates. |
| Spots | Sort ascending by time. The first occurrence of a key creates the spot (with first visit and coordinate); later occurrences increment `visits`. `topSpot` is the spot with the most visits, and exists only if that is more than 1. |
| Neighborhoods | Distinct lowercased values of `address.neighbourhood \| neighborhood \| suburb`, falling back to `city \| town \| village`. Reuse `neighbourhoodFromEncounterSemanticLocation` (`ProfileConnectionMoment.kt:110`) and add a city extractor. |
| Week streak | Bucket encounters by week start (locale first day of week, kotlinx-datetime). Current streak: start at this week if it has an encounter, otherwise last week, and walk back while weeks are present. Longest streak: the longest run of consecutive weeks. |
| Favorite time | Only when there are at least 3 hangouts. Buckets: morning 5–11, afternoon 12–16, evening 17–21, otherwise night. Break ties deterministically in the order morning, afternoon, evening, night; iOS is non-deterministic here, so document the difference. |
| Coldest / warmest | Min and max of `weather_snapshot.temperatureCelsius`, only when at least 2 encounters have a temperature. |
| `HangoutHighlights` (latest encounter) | `ordinal = count`. `isNewSpot` = count > 1 and the latest spot key is not among earlier keys. `leveledUpTo` = the new level if the rank increased and count > 1. `isMilestone` = count ∈ {5,10,25,50,75,100,150,200,250,365,500}. `weekStreak`. |
| `GroupHangout.clusters` | Sort your encounters with group members by time. Start a new cluster when an encounter is more than 2 h after the cluster's first encounter. Keep clusters with at least 2 distinct members. The representative is the first encounter in the cluster that has a location. |

**Tests:** in `commonTest`, port the `Round7Tests.swift` cases one-for-one for levels, spots and streaks, and highlights.

## 2. Peer profile "Together" section

**Placement:** new file `KMP/ui/components/ProfileFriendshipSection.kt`, inserted into `ProfileBottomSheet.kt` between `ProfileActionGrid` and the tab row. Show it only for non-self, non-group profiles that have a `connectionId`.

**Empty state:** "Together", then "Log a hangout with {name} to start your shared story."

**Header**
- Level icon and name, and "Since {MMM yyyy}".
- Progress bar with "N more hangout(s) to {next}", or at the top level "The highest level. You two are inseparable."

**Stats row**
- `hangouts` and `spots`.
- Then either "🔥 N week streak" (if the streak is at least 2) or "N neighborhoods" (if there is more than 1).

**Pending hangout rows** (data from 03 §B2)
- Confirm and "Not us" buttons, or "Waiting for {name} to confirm".

**Encounter map preview** (see §3).

**Upcoming plans list** (up to 3; data from 03 §A6)
- Tapping a row opens the chat anchored on that message.

**Pills**
- "Log hangout" (03 §B3).
- "Plan" (opens the chat with the planner, 03 §A3).
- "Your story" (only when hangouts ≥ 2).

**ViewModel:** extract a `PeerProfileViewModel` that owns `encounters`, `pendingHangouts`, `upcomingPlans`, toasts, and refresh after confirm or log. Do not add more ad-hoc `LaunchedEffect` state to the sheet.

## 3. Encounter map card

- **Pins:** one per encounter with a valid coordinate, numbered 1…n in time order. Each pin is the peer's avatar with a number badge. The newest pin gets an orange "NEW" badge when `isNewSpot` is true.
- **Card:** a static 180 dp map (`PlatformMap`, `KMP/ui/components/MapView.kt:297`, with gestures disabled) and an "N spots" capsule.
- **On tap:** open a full-screen dialog titled "Where you've met" with an interactive map.

## 4. "Your story" sheet

**File:** `KMP/ui/components/FriendshipStorySheet.kt`. It is a `HorizontalPager` of 4:5 cards.

**Card background:** a gradient from `SouvenirPalette`, using the seed `peerSeed + pageIndex`, djb2 hash, modulo 5. The pairs are:
- `#6D28D9 / #DB2777`
- `#1D4ED8 / #0EA5E9`
- `#047857 / #65A30D`
- `#B45309 / #DC2626`
- `#0F766E / #4F46E5`

**Pages.** Each page is shown only when its data exists.
1. **Where it started:** the first meeting date, and "You first Clicked at X."
2. **{Level}:** "N hangouts", then "N spots across M neighborhoods."
3. **Your spot:** the top spot's name, then "N times and counting." Shown only when the top spot has more than 1 visit and a name.
4. **Your rhythm:** "You're {morning|…} people." and/or "Longest run: N weeks in a row." Shown when the longest streak is at least 2 or a favorite time exists.
5. **Rain or shine:** "low° to high°". Shown when the spread is at least 5 °C.

**Actions**
- **Share:** render the current card to a bitmap (`GraphicsLayer.toImageBitmap()`, 340 dp wide, 3× scale, initials instead of the avatar photo). Share it through a new expect/actual `shareImage(ImageBitmap, filename)`. The Android actual uses a `FileProvider` URI with `ACTION_SEND`, patterned on `AM/ui/chat/ChatImageGallerySave.android.kt:81`.
- **Plan next:** dismiss the sheet and open the chat planner.

## 5. Souvenir card after a tap

**File:** `KMP/ui/components/SouvenirCard.kt`.

**Content**
- "First Click" or "Hangout #N", then "with {name}".
- Place, then weekday, date and time, then "temp° · condition".
- Badges: level-up, "New spot", "N-week streak" (if at least 2), and "Nth hangout" (milestone).
- Gradient from `SouvenirPalette(peerSeed)`.

**Where it shows**
- In the post-connect flow for **one-to-one** taps only (`ConnectionContextSheet.kt`, the analogue of iOS PostConnectView), once encounters have loaded. Load them with `fetchSharedConnectionBetween(..., forceNetwork = true)`.
- Include a "Share souvenir" button that reuses `shareImage`.
- iOS shows the reveal before the tags; Android shows tags before the reveal (F70). Adopt iOS order in this work: reveal and souvenir first, tags inline below.

## 6. Profile identity upgrades

- **Bio**
  - Parse `user.bio` in `ApiClientProfileEndpoints.kt:24-40`, `User` and `UserPublicProfile`, and render it under the name in `ProfileSheetHeader`.
  - Edit it in Settings → Edit profile, with a 160-character counter, via `PATCH /api/users/{id}/profile {bio}`.
- **Relationship line:** "Clicked {MMM d} at {place} · N encounters" in the header, replacing the separate moment card if redundant.
- **Per-encounter "Edit tags"**
  - Add an overflow action on each encounter row in `OurTimelineSection` (`ProfileTimelineMetrics.kt:255`). It opens the tag picker, seeded from `KMP/data/ContextTagTaxonomy.kt` suggestions.
  - New repo call `setEncounterTags(encounterId, tags)`: PostgREST `PATCH connection_encounters?id=eq.{id} {context_tags}`, preserving `at_event`. Mirror `iOS/Core/Connections/EncounterContextRepository.swift:140-150`.
- **Encounter titles:** "First Clicked at X", "Reconnected at X", or the event title. Include the "Reconnected · Nth time" and "Extended Hangout" copy (F71).
- **Common ground:** a single section with shared interests first and highlighted, then the peer's other interests outlined, then personality. Android currently has three separate sections; merging them is a layout change.
- **Public profile (not connected):** confirm that `EventDirectoryUserProfileSheet.kt` shows the aura-colour ring, "You haven't Clicked yet", and an "Open Add Click" CTA. Add any that are missing.

## 7. Group "Together" section

- Extend `TabbedGroupProfileSheet` (`ProfileTabbedSheets.kt:219`) with a group version of §2.
- Inputs are one encounter read per member connection, clustered with `GroupHangout.clusters`.
- Content:
  - Level, "N group hangouts · M spots".
  - "Most often with A, B, C" (the top 3 members).
  - Streak, map and upcoming plans.
  - "Plan something" (no plans yet) or "Plan another".

## 8. Home "Your recap": fix semantics to match iOS

Relevant files: `KMP/viewmodel/HomeViewModel.kt:71-75,830-939`, `KMP/ui/components/HomeComponents.kt:483-620`.

| Change | Detail |
|---|---|
| Explicit state | Replace the placeholder DTO (`since == ""`) with a sealed `RecapState { Loading; Loaded(dto, stale); Failed(cached?) ; Empty }`. |
| Never show fake zeros | Show a loading row while loading. On failure with no cache, show "Couldn't load your recap." with Retry. On failure with a cache, show the cached value and "Showing your last saved recap." |
| Disk cache | Persist per user and per window (`recap.day` / `recap.week`) through AppDataManager's disk snapshot, like the profile timeline caches. Seed both windows from cache on start. |
| Fetch policy | Refresh reloads only the current window and keeps the other window's cache. Toggling fetches only if that window hasn't loaded this session. Remove the prefetch of both windows. |
| Rows | Show only rows with a value > 0. Order and labels: **New Clicks, Messages sent, Messages received, Events RSVP'd, Check-ins, Events saved, Beacons dropped**. |
| Empty copy | "No activity this {day/week} yet." with a "Make your first Click" CTA. |

Note: the server (`web/app/api/me/recap/route.ts`) returns zeros with HTTP 200 on internal errors, so the client can't tell those apart from real zeros. Tracked as ledger F21, backend follow-up.

## Acceptance

- [ ] The `FriendshipStats` Kotlin port passes the ported `Round7Tests` golden cases.
- [ ] The peer profile shows the Together section with the correct level, progress, stats, map, pending rows, plans and pills. The empty state shows when there are 0 encounters.
- [ ] The map card shows numbered pins and the NEW badge. The full-screen map is interactive.
- [ ] The story sheet shows only the pages whose data exists. Share produces a PNG through the Android share sheet. Plan next opens the planner.
- [ ] A one-to-one tap shows the souvenir card with correct highlights and a working share.
- [ ] Bio displays and can be edited (≤ 160). Per-encounter tag edit persists, and `at_event` is preserved.
- [ ] Group profile shows group hangouts clustered per the 2 h rule.
- [ ] Recap never shows zeros before data arrives or after a failure. The cache survives a relaunch. Rows and labels match iOS.
