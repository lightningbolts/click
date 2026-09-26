# 05: App-wide remainder

This sheet covers everything outside chat, scheduling, hangouts and profile. References are to `click-ios/Docs/PARITY_LEDGER.md` (the F-numbers) and to `iOS/` sources.

---

## A. Parity removals (features iOS retired but Android still ships)

| # | Item | Android location | Action |
|---|---|---|---|
| A1 | **Ghost Mode** (F48; it is local-only on Android and never written to the server) | `KMP/ui/screens/SettingsScreenComponents.kt:516-519`; `KMP/data/AppDataManager.kt:520-555`; map usages in `MapScreen.kt`, `MapViewModelCamera.kt` | Remove the toggle and state. On launch, clear the server flag once (`PATCH /api/user/ghost-mode {enabled:false}`), as iOS does (`MeRepository.swift:293-296`). |
| A2 | **Seed-a-Room / event teasers** (spec §56.2.2) | Toggle `SettingsScreen.kt:504-510`; chip `EventBeaconDetail.kt:330-335,540-556`; endpoint `ApiClientBeaconEndpoints.kt:429-436`; push `ClickFirebaseMessagingService.kt:47,108` | Remove the toggle, chip and endpoint. Keep a defensive handler that ignores `event_teaser` pushes. |
| A3 | Call leftovers | `call_notifications_enabled`/`callPushEnabled` (`AM/data/storage/TokenStorage.android.kt:66,145-155`; `KMP/data/AppDataManagerSnapshot.kt:311,337`); "Missed Voice Call" label (`KMP/ui/chat/ChatFormatting.kt:127`, keep it only for rendering legacy `call_log` rows); README mentions | Remove the prefs and stop call prefs from triggering the push-permission request. Update READMEs. |
| A4 | Dead code | Photo pile (`HomePhotoPile.kt`, `PileCluster.kt`, `PilePhysics.kt`, `cardstack/*`), `HomeLayoutMode`, `ClicktivitiesScreen.kt`, `WaitlistDialog.kt`, `GlobalSearchScreen.kt`, `PermissionsOnboardingScreen`, `LocationOnboardingScreen` | Delete. Change the Appearance subtitle to "Theme" (it currently says "Dark mode and home layout"). Optionally rename `NfcScreen` to `TapConnectScreen`. |

## B. Me tab and Settings

| # | iOS feature | Android today | Spec |
|---|---|---|---|
| B1 | Me root (F24; `iOS/Features/Me/MeView.swift:65-345`) | Header and settings hub only; the screen is titled "Settings" | Title the screen "Me". Add to the root: identity header, Clicks count, availability status, **Core strip**, **My QR** and **Edit profile** shortcuts, a **Calendar** row, and a version/build footer. |
| B2 | Alerts page (F25) | Toggles present, plus an extra teaser toggle; no OS-permission section | Show a per-toggle pending state and revert on failure. Add a system-permission section: if `POST_NOTIFICATIONS` is denied, show "Notifications are off" with a button to open app notification settings. Only request the permission after the user acts. Event-reminder copy: "Day-of and 1 hour before events you're going to" (see C3). Rename "Reconnect nudges" to "Relationship moments" (03 §B6). |
| B3 | Privacy → Encounter context (F110) | Ambient sound sits under Alerts; there is no barometric toggle | Add an **Encounter context** group under Privacy with Ambient sound (moved here) and Barometric context (new, P0-6). Add Hangout detection (03 §B7) to the same page. |
| B4 | Permissions hub (F27): 8 permissions | Microphone, Location, Bluetooth only | Add Camera, Photos/Media, Contacts, Calendar and Notifications. Each row shows its state with an **Allow** button (while it can still be requested) or **Settings** (once permanently denied). |
| B5 | Blocked people (F67) | Missing | Add a Privacy → Blocked people list (`GET /api/safety/block`) with Unblock (`DELETE /api/safety/block`). |
| B6 | Edit profile: bio and Remove photo (F78) | Name and photo change only | Add Bio with a 160-character limit (02 §6). Add **Remove photo** (`DELETE /api/user/avatar`). |
| B7 | Appearance: System, Light or Dark (F105) | Dark toggle only | Replace it with a 3-way segmented control that defaults to System. |
| B8 | Web dashboard and **Delete account** (F30) | Missing | Add rows that open `https://joinclick.co` (dashboard) and the delete-account page in a Custom Tab. **Required by Play Store policy.** |
| B9 | Calendar page (F29) | Permission is only requested in the context sheet | Add a status page with the permission state, the connected calendars, and a Connect/Disconnect button. Reuse `KMP/calendar/*`. |
| B10 | Saved events page (F22) | Flat list | Split into Upcoming and Past/unavailable sections. Show a stale-data notice when rendering from cache, and a Retry row on failure. |
| B11 | Location snap zero-row check (F26) | Not verified | If a `users.location_*` write updates zero rows, treat it as a failure and revert the toggle. |

## C. Events, beacons and map

| # | iOS feature | Android today | Spec |
|---|---|---|---|
| C1 | Guest list UI (F85; `iOS/Features/Events/GuestListView.swift`) | The API exists (`ApiClientBeaconEndpoints.kt:447-510`) but there is no UI | Add a creator-only Guest list screen reached from event detail. Show counts (matched vs pending), a **paste import** field (one name, email or phone per line), and **Re-match**. |
| C2 | Full creator edit (F83) | Description only | Add an edit sheet covering title, place, photo, schedule, categories, visibility, capacity, approval-required and guest-list visibility, saved with `PATCH /api/beacons/{id}`. Reuse the `BeaconDropSheet` form components. |
| C3 | Local event reminders at T-60 and T-15 (F84; `iOS/Core/Beacons/EventReminderScheduler.swift`) | In-app Home cards only | Use `LocalReminderScheduler` (03 §A5) with IDs `event.<id>.60` and `event.<id>.15`. Schedule on RSVP Going or on bookmark, according to the Alerts preference. Cancel on un-RSVP, event deletion and sign-out. Re-sync on app start. |
| C4 | Join-request watcher (F86) | Missing | While a join request is pending, poll `GET /api/beacons/{id}/rsvp` every 30 s, but only while the detail screen is visible. On approval or denial, show a toast or banner and refresh the detail. |
| C5 | Custom categories (up to 3, free text) and music-link validation (F81) | Presets only | Add a "＋ Custom" chip (max 3 categories in total, ≤ 24 characters each). Validate music URLs against the server's allowlist (https Spotify, Apple Music, YouTube; SoundCloud is not on it) before submitting. |
| C6 | On-device soundtrack resolver (`iOS/Core/Beacons/SoundtrackResolver.swift`) | Depends on the server's `preview_url` | Optional fallback: when `preview_url` is missing, resolve through oEmbed and an iTunes Search lookup for preview audio and artwork. Cache the result per URL. Low priority. |
| C7 | Map person callout "first met at {place}" (F69) | Not verified | Add it to the person pin callout, using `latestEncounter()`. |
| C8 | Post-connect "Go together?" event recommendation (F73) | Shown in the profile sheet only | Also show it in the post-connect sheet (`/api/connections/{id}/event-recommendation`). |
| C9 | Post-connect "Send a Click Drop" CTA (F98) | Missing | Add a CTA that opens the Click Drop camera, targeting the new chat. |

## D. Shell, deep links, push, networking

| # | iOS feature | Android today | Spec |
|---|---|---|---|
| D1 | Deep links (`iOS/App/AppRouter.swift:308-366`) | `/c`, `/connect`, `/e`, `/hub` and login only | Add the routes `click://chat/{chatId}`, `click://profile/{userId}` (alias `u`), `myqr`, `scan`, `tap` and `search?q=`, plus `https://joinclick.co/search`. Add manifest intent filters and `KMP/deeplink/*` parsing. Add a unit test for each route. |
| D2 | Push tap routing for every type (F106) | Partial | Add routing for the moment types (03 §B6) and for `archive_warning` (opens the person's profile, or the Clicks list without one, as on iOS). Add a single `PushRoute` mapping table with tests. |
| D3 | Offline vs refresh-failed (F50) | Offline banner only | Distinguish "You're offline" from "Couldn't refresh · Retry". Use the currently unused `/api/ping` helper (`ApiClient.kt:78-82`) to confirm reachability. |
| D4 | Persisted connection-flow telemetry queue (`iOS/Core/Telemetry/TelemetryQueue.swift`) | Fire-and-forget | Persist the queue to disk (bounded to 200 events) and flush it on foreground and when the network returns. Keep the 10% sampling. |
| D5 | Batched display-name cache (F54: `POST /api/users/display-names`, 1 h TTL) | Uses the `connectedUsers` map | Add an `IdentityCache` that batches unknown user IDs (debounced by 100 ms) and caches names for 1 hour. It resolves names for group typing, reactors, seen-by and hub members. |
| D6 | Per-user local message store with offline full-text search (`iOS/Core/Persistence/LocalStore.swift`) | In-memory `ChatTimelineCache` | **Optional, large.** Adopt SQLDelight (KMP) for per-user decrypted history and FTS, wiped on sign-out. This would enable offline search, reliable upcoming plans (03 §A6) and faster cold opens. Decide after Phase 3; not required for feature parity. |
| D7 | App Clip (F06) | n/a | Out of scope. Android Instant Apps are deprecated. `/c/{uuid}` web fallback already covers the case. |

## E. Proximity

| # | Item | Spec |
|---|---|---|
| E1 | Ultrasonic decoder and safe tokens | See P0-5. |
| E2 | "Reconnected · Nth time" and "Extended Hangout" copy (F71) | See 02 §6. |
| E3 | Post-connect ordering (F70) | See 02 §5: reveal and souvenir first, tags inline. |

## Acceptance

- [ ] Every row in sections A–D is implemented or explicitly marked out of scope in the parity ledger.
- [ ] Delete-account entry is reachable within 2 taps of the Me tab.
- [ ] Deep-link unit tests cover every route in D1.
- [ ] Event reminders fire at T-60 and T-15 for Going events and respect the Alerts preference.
