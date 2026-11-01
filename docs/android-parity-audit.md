# Android parity audit

**Date:** 2026-11-01
**Baselines:** `click@b461f6b4` (origin/main) · `click-ios@9aed125` (origin/main, PR #84) · `click-web@f0c99bc` (origin/main, PR #167)

This audit compares the Android app (`click`, Kotlin Multiplatform + Compose, Android only) with the native iOS app (`click-ios`). `click-web` and its Supabase migrations are the source of truth for backend behaviour.

It builds on the earlier program in [`docs/parity/`](parity/README.md):
- Sheets 00–05 were closed on 2026-09-27.
- [Sheet 07](parity/07-gap-closure-2026-10.md) (2026-10-06) planned the next round. **None of its G-rows have landed:** no Android code has been merged since 2026-09-29.
- Since sheet 07, iOS has merged PRs #61–#84 and click-web PRs #132–#167. This audit adds those features and restates every gap as one status matrix plus a PR-sized task list.

Where a sheet-07 row already specifies a task in detail, this document links to its ID (`G2-1`, `G4-3`, …) instead of repeating it.

**Path conventions**
- `KMP/` = `click/composeApp/src/commonMain/kotlin/compose/project/click/click/`
- `AM/` = `click/composeApp/src/androidMain/kotlin/compose/project/click/click/`
- `iOS/` = `click-ios/Click/`
- `web/` = `click-web/`

**Status key**

| Status | Meaning |
|---|---|
| **Done** | Matches iOS behaviour against the same server contract. |
| **Partial** | Exists, but iOS behaviour or states are missing. |
| **Missing** | No Android implementation. |
| **Broken** | Exists but misbehaves: a wrong wire format, a crash path, or an interop failure with iOS. |
| **Retired** | Removed on both platforms on purpose. |

---

## 1. Summary

| Area | Done | Partial | Missing | Broken | Retired |
|---|---|---|---|---|---|
| Shell, auth, onboarding | 3 | 3 | 2 | 1 | 2 |
| Home | 6 | 2 | 6 | 0 | 0 |
| Add Click / Connect | 7 | 3 | 4 | 1 | 0 |
| Clicks inbox and profile | 8 | 3 | 1 | 0 | 0 |
| Map | 3 | 2 | 2 | 0 | 0 |
| Me / Settings | 7 | 3 | 4 | 0 | 0 |
| Chat | 13 | 2 | 6 | 2 | 0 |
| Hubs and groups | 6 | 2 | 0 | 0 | 0 |
| Beacons | 4 | 1 | 4 | 0 | 0 |
| Events | 7 | 2 | 12 | 0 | 0 |
| Click Drops | 1 | 1 | 5 | 1 | 0 |
| Places | 0 | 0 | 5 | 0 | 0 |
| Platform (push, flags, live updates, caches, telemetry) | 3 | 2 | 4 | 0 | 1 |
| **Total (157 rows)** | **68** | **26** | **55** | **5** | **3** |

**Headline findings**

1. **The Broken items are cheap to fix and should ship first** (§6, P0):
   - Typing indicators never cross between iOS and Android (wrong JSON keys).
   - The manifest declares an activity class that does not exist.
   - Group taps ignore removals saved by other phones.
   - Gated Click Drops sent from iOS show forever as a pixelated preview on Android.
2. **Android has no feature-flag client.** Every flag-gated iOS feature (drops, alert confirmations, listening now, event drops, history, shared drops, reconnect nearby, Places, ticket wallet) needs `GET /api/me/features` first.
3. **Paths and HTTP methods line up.** All ~110 `/api/*` calls Android makes exist in click-web with the method Android uses. The breaks are in field names, response handling, and endpoints Android never calls (§4).
4. **The biggest visual gap is the theme.** Android uses `#630ED4` (iOS's "brand mark only" colour) as the primary fill and Manrope for all text. iOS and web use `#7C3AED` for actions and Manrope only for headings (§3).
5. **`./gradlew assembleDebug` cannot run in the cloud sandbox this audit was written in,** because the network policy blocks `dl.google.com` (Android SDK). This blocks Phase 3 PRs until it is allowed (§8).

---

## 2. Feature matrix

### 2.1 App shell, auth, onboarding

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| 5 tabs: Home, Add Click, Clicks, Map, Me | `iOS/App/RootGateView.swift` (`MainTabShellView`) | `KMP/navigation/NavigationItem.kt`, `KMP/AppMainShell.kt`, `KMP/AppPrimaryTabsHost.kt` | Done | Android's Me tab uses an icon; iOS uses the user's avatar. `NavigationItem.sfSymbol` is an unused iOS leftover. |
| Per-tab back stack, re-tap pops to root | `iOS/App/AppRouter.swift` | `KMP/navigation/RouteHistory.kt` (one global string stack) | Partial | There is no per-tab stack, so a tab switch loses its depth. Re-tap to root is not implemented. |
| Per-tab root "…" menus | `iOS/Features/Shell/RootMenu.swift` | Shared top-bar actions | Missing | Sheet 07 G6-15. |
| Deep links: `click://` and universal links for `/c`, `/connect`, `/e`, `/hub`, `/search` | `AppRouter.parseIncomingURL` | `KMP/deeplink/*`, `AM/MainActivity.kt`, `AndroidManifest.xml` | **Broken** | The manifest declares `.instant.InstantProfileActivity` for `https://…/connect` and `/c` with `autoVerify`, but **the class does not exist**: a launch through that filter crashes. `pathPrefix="/c"` also swallows `/careers` and `/chat`. `web/public/.well-known/assetlinks.json` names `com.click.app` with a placeholder fingerprint, so App Links never verify (G0-6). |
| Deep links for `/p/{slug}` (Places) and `click://pass/{id}[/scan]` | `AppRouter.swift:412+` | — | Missing | Depends on Places and Click Pass. |
| Sign in: email/password, Google, Apple | `iOS/Features/Auth/AuthView.swift` | `KMP/ui/screens/LoginScreen.kt`, `SignUpScreen.kt`, `AM/auth/GoogleSignInHelper.android.kt` | Done | Apple sign-in is iOS-only by platform. |
| Sign out ends only this device's session | `SupabaseAuthService` `logout?scope=local` (#83) | `KMP/data/repository/AuthRepository.kt:227` (`supabase.auth.signOut()`, default scope LOCAL in supabase-kt 3) | Done | Verify on a device that a second phone stays signed in. |
| Onboarding: Welcome → Interests (≥5) → Avatar | `iOS/Features/Onboarding/*` | `KMP/viewmodel/OnboardingViewModel.kt` (adds Personality and Prior connections as gates) | Partial | iOS moved Personality and Find friends into a Home "Get started" card (G6-5, owner decision D5). |
| Terms/Privacy on auth; Forgot password goes to the web | `AuthView.swift`, `iOS/Core/Config/LegalLinks.swift` | `LoginScreen.kt` | Partial | Check that the Terms/Privacy links and agreement copy match (G7-3). |
| Ghost Mode | Removed; clears legacy flag on sign-in | `KMP/AppMainShell.kt:362`, `ApiClientAccountEndpoints.kt:62` | Retired | Same behaviour on both platforms. |
| Calls (VoIP) | Removed; `call_log` renders read-only | `ChatMessageType.CALL_LOG`, `hapticCallAccept/End` | Retired | Remove dead call haptics (cleanup). |

### 2.2 Home

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Header, greeting, search launch | `iOS/Features/Home/HomeView.swift` | `KMP/ui/screens/HomeScreen.kt`, `HomeSearchPill` | Done | |
| "I'm down for…" availability intents and overlaps | `iOS/Features/Availability/AvailabilitySheet.swift` | `KMP/viewmodel/AvailabilityViewModel.kt`, `HomeAvailabilityIntentsRow` (PostgREST `availability_intents`, RPC `get_availability_overlaps`) | Done | Android writes the table directly; iOS uses `/api/user/availability-intents`. Both are server-valid (RLS). Prefer the API on the next touch so validation (≤25 chars, 15 min–24 h) is enforced server-side. |
| One priority "social opportunity" card (nudges, wave back, hangout confirm) | `HomeFeedModel.swift` | `InboxNudgeBanner.kt`, `HomeScreenCards.kt` | Done | Delivered in sheet 03. |
| Reconnect near here | `ReconnectNearbyCard.swift`; `GET /api/nudges/reconnect`, `POST /api/nudges/reconnect/{id}` | — | Missing | Flag `reconnect_nearby` (G6-2). |
| Shared Click Drops strip | `SharedDropsStrip.swift` | — | Missing | Flag `shared_drops` (G2-3). |
| Upcoming (hosting/going/saved) and Recommended for you | `HomeComponents.swift` (`UpcomingEventsView`, `HomeEventCard`); `GET /api/beacons/mine` | `FeaturedEventSection`, `SavedEventsSection`, `ExploreNearbyBeaconsSection` | Partial | Android doesn't call `/api/beacons/mine`, so hosted and RSVP'd events are missing from Upcoming. There is no on-device "because you like…" ranking. |
| "Get started" setup card | `HomeSetupCard.swift` | — | Missing | Pairs with G6-5. |
| Recent connections strip with Core ring | `HomeComponents.swift`, `iOS/DesignSystem/Colors.swift` (`coreRing`) | `RecentClickCard`, `OnlineFriendItem` | Partial | Core ring gradient (`#F5C451 → #E39A3B → #A855F7 → #7C3AED`) is missing on avatars. |
| Event recap card | `GET /api/me/event-history/recap-card` | — | Missing | Flag `event_history` (G3-3). |
| Your recap (day/week) | `GET /api/me/recap` | `ActivityRecapSection`, `KMP/viewmodel/ActivityRecapController` | Done | |
| Explore nearby | `GET /api/beacons`, `/api/hub/nearby`, `/api/places/nearby` | `ExploreNearbyBeaconsSection` | Done | Places count is absent until Places lands. |
| Insights tiles | `HomeComponents.swift` (`InsightTile`) | `HomeStatCard`, "Your Stats" | Done | |
| Activity bell and Activity inbox | `iOS/Features/Activity/ActivityView.swift`; `GET /api/activity`, `POST /api/activity/seen` | — | Missing | G6-1. |
| Location nudge cards | `iOS/DesignSystem/Components/LocationNudgeCard.swift` | — | Missing | G6-6. |

### 2.3 Add Click / Connect

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Tap to connect: BLE + 18.5 kHz ultrasonic + progressive GPS | `iOS/Features/AddClick/TapConnectView.swift`, `iOS/Core/Proximity/*` | `KMP/ui/screens/NfcScreen.kt` (misnamed), `AM/proximity/AndroidProximityManager.kt`, `KMP/proximity/*`, `KMP/utils/ProgressiveLocationSession.kt` | Done | Hardware path needs a device check (§7). |
| Offline tap queue and flush | Per-user queue | `ConnectionRepositoryPendingQueue.kt`, WorkManager `PendingHandshakeSyncWorker` | Done | Android also flushes in the background (ahead of iOS). |
| "Who's in this Click?" review (remove / add back, existing-group "Save this Click", late joiners) | `TapConnectView.swift:290`; `POST /api/connections/proximity/selection`, poll `GET /api/connections/proximity?pending_handshake_id` | `ConnectionViewModelConfirm.kt` host-selection picker; `POST /api/connections/proximity/confirm` | **Broken** | `ProximityBindOkResponseDto` (`KMP/data/api/ApiClientModels.kt:342`) drops `existing_connection_id`. Android never calls `/proximity/selection`, so **a removal made on Android is lost if another phone confirms first**, and removals made on iOS are not reflected on the Android screen. No late-joiner poll. P0-3, then G4-4 and G4-5. |
| Multi-Tap verified clique creation (lowest-ID creates, others wait 2/3/4 s) | `iOS/Features/Connect/PostConnectModel.swift` (`prepareGroup`) | `KMP/domain/VerifiedCliqueCreation.kt` (prefilled group sheet) | Partial | G0-12: an Android lowest-ID member makes every iOS phone wait about 9 s. |
| Post-connect reveal: You Clicked / Reconnected / Extended Hangout / Group created | `iOS/Features/Connect/PostConnectView.swift` | `KMP/ui/components/ConnectionRevealOverlay.kt`, `ConnectionContextSheet.kt` | Done | |
| Encounter details (when, where, weather, verified-by with accuracy) | `PostConnectView.swift` (#51) | — | Missing | G4-6. |
| Context tags (14 plus custom ≤25) with 30-min merge | `ContextTagPicker.swift` | `KMP/data/ContextTagTaxonomy.kt`, `ConnectionContextSheet.kt` | Done | |
| "Go together?" event recommendation | `GET /api/connections/{id}/event-recommendation` | `ConnectionEventRecommendationCard.kt` | Done | |
| GPS quality keys, `sensor_observation` v3, late altitude follow-up, `encounter-location` | `ConnectionCaptureSession.swift`, `ConnectionSensorObservation.swift`, `EncounterFollowUp.swift` | lat/lon/altitude only | Missing | G4-1, G4-2, G4-3, G4-7. Add `POST /api/connections/encounter-location`, which is new since sheet 07. |
| My QR: 90 s single-use token, refreshes about 10 s early | `AddClickView.swift` (`MyClickCodeView`, `QRCodeStore`) | `KMP/ui/screens/MyQRCodeScreen.kt`, `KMP/ui/components/QrCodeView.kt`; `GET /api/qr` | Partial | No keep-screen-on or brightness ramp (#63). Check the refresh-before-expiry timing. |
| Scan QR and redeem, with fallback `POST /api/connections` | `ScanClickCodeView` | `KMP/ui/screens/QRScannerScreen.kt`, `AM/ui/components/QRScanner.android.kt` | Done | Camera needs a device check. |
| Find friends: contacts hash discovery and prior request | `FindFriendsView.swift` | `KMP/ui/screens/PriorConnectionsScreen.kt`; `POST /api/contacts/discover` | Partial | Onboarding only. No "Already on Click" list, no re-entry from Add Click, no own phone number (G6-4). |
| Own phone number (`/api/me/phone`) | `MyPhoneEditor.swift` | — | Missing | G6-4. |
| "How Click works" sheet | `AddClickView.swift` | — | Missing | Small; copy is in iOS. |
| Telemetry for the connection flow | `/api/telemetry/connection-flow` | `KMP/telemetry/ConnectionFlowTelemetry.kt` | Done | |

### 2.4 Clicks inbox and connection profile

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Active / Groups / Archived, Core "Remember" strip | `iOS/Features/Clicks/ClicksView.swift` | `KMP/ui/screens/ConnectionsListView.kt`, `ConnectionsTabControls.kt`, `RememberMeStrip.kt` | Done | |
| Swipe and menu actions (archive, unread, report, remove, block, prior accept/decline) | `ConversationActions.swift` | `ConnectionRowGestures.kt`, `ConnectionActionSheet.kt` | Done | |
| Mute durations (1 h / 8 h / 1 week / until on) with bell on row | `MuteMenu`; `PUT /api/chat/notifications` | `KMP/data/ChatMuteStore.kt` (sheet 04) | Partial | Check the duration menu and row bell against iOS (G6-10). |
| Global search: People / Messages / Groups / Events / Places / Hubs | `iOS/Features/Search/GlobalSearchView.swift`; `GET /api/search`, fallback `/api/chat/search` | `KMP/viewmodel/GlobalSearchViewModel.kt`, `UnifiedSearchSheet.kt`; `/api/chat/search` only | Partial | Android never calls `/api/search`, so it has no server-side people or event results. |
| Profile identity, Core pill, bio, actions (Message, Wave, Core, Click Drop) | `iOS/Features/Profile/ProfileView.swift` | `KMP/ui/components/ProfileBottomSheet.kt`, `ProfileTabbedSheets.kt` | Done | |
| Friendship levels, streak, encounter map, story, souvenir | `FriendshipViews.swift` | `KMP/ui/components/FriendshipViews.kt`, `KMP/data/models/FriendshipStats.kt` | Done | |
| Hangouts: log / confirm / decline / upcoming | `/api/hangouts*` | `LogHangoutSheet.kt`, `PendingHangoutRow` | Done | |
| Timeline tabs (Timeline, Pinned, Beacons, Media, Links, Files) and journal | `ProfileView.swift`, `JournalEditor.swift` | `ProfileSheetPanels.kt`, `ProfileLegacyTimeline.kt`; `/api/profile/timeline`, `/api/connections/{id}/tabs` | Partial | There is no **Pinned** tab (pins are missing on Android). |
| Connection tagging on past encounters ("Edit tags") | `PATCH /rest/v1/connection_encounters` | `ConnectionRepositoryEncounters.kt` | Done | |
| Reconnect merging ("Extended Hangout", same place within 12 h folded) | Server merge and client label | `KMP/ui/components/ProfileTimelineMetrics.kt:260`, `ReconnectModels.kt` | Done | |
| Events together | `GET /api/users/{id}/events-together` | — | Missing | Flag `event_history` (G3-3). |
| Non-connection profile states (#70) | `PublicProfileView.swift` | `UserProfileBottomSheet.kt`, `/api/users/{id}/public-profile` | Done | Check the per-tab empty copy. |

### 2.5 Map

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Layers: people, events, places, hangouts, social, soundtracks, alerts, hubs | `iOS/Features/Map/MapFeatureModel.swift` | `KMP/viewmodel/MapLayerFilter.kt` (no Places or Hangouts layer) | Partial | Hangouts layer is G3-8; Places is G5-2. |
| Pin stacking (Luma-style, ranked, stack sheet) | `MapFeatureModel.swift`, `ClickMapView.swift` (#69) | `KMP/ui/components/MapView.kt` + `AM/ui/components/MapView.android.kt` (individual markers) | Missing | Use the same collision rule; maps-compose clustering is an option, but the ranking must match. |
| Nearby sheet: sort (relevance, distance, rising, new, A–Z), search, keyset paging | `NearbySheet.swift` | `KMP/ui/screens/MapDiscoveryLayout.kt` (Distance/Recent only) | Partial | G3-7. `GET /api/beacons` pages by `next_cursor`; a short page doesn't mean the end. |
| Map reopens where left; refetch only after moving ≥1 km | `ClickMapView.swift` | `MapViewModelCamera.kt` | Done | |
| Create beacon: kinds, photo, soundtrack, recurring events, drops at current location | `CreateBeaconSheet.swift` | `KMP/ui/components/BeaconDropSheet.kt`, `CreateBeaconViewModel.kt`, `EventEditSheet.kt`, `EventRepeatPicker.kt` | Done | Recurring shipped in `a7b7120d`; check the discard-changes dialog (G3-11). |
| Place-hosted events merged into the Place pin | #68 | — | Missing | After Places. |
| Map friction telemetry | `/api/telemetry/friction` | `KMP/telemetry/TelemetryBatcher.kt` | Done | |

### 2.6 Me / Settings

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Me root: identity, Core, social, upcoming hangouts | `iOS/Features/Me/MeView.swift` | `KMP/ui/screens/SettingsScreen.kt`, `MeSections.kt` | Done | |
| Alerts toggles via `PATCH /api/user/preferences` | `AlertsSettingsView` | `NotificationPreferencesRepository` | Partial | The **Click Drops** toggle (`drop_release_push_enabled`) is missing (G2-7). |
| Privacy & data: Location snap, Business insights, encounter sensors, Hangout detection | `PrivacySettingsView` | `SettingsSubpages.kt` | Done | The Memory Map toggle should go (G6-6). Show my Place visits comes with G5-4. |
| Permissions hub | `PermissionsSettingsView` | `KMP/ui/utils/AppPermissions.kt`, Permissions page | Done | Android shows Photos as "Not needed" (system picker). That's correct. |
| Blocked people | `GET/DELETE /api/safety/block` | Blocked page | Done | |
| Calendar free/busy | `CalendarSettingsView` | `KMP/calendar/*` | Done | Android is ahead (overlap card). |
| Interests / Personality / Edit profile (bio ≤160) | `SettingsPages.swift` | `SettingsInterestsCard.kt`, `SettingsPersonalityCard.kt` | Done | |
| Appearance: System / Light / Dark | `MeView.swift` | `KMP/ui/theme/AppAppearance.kt` | Done | |
| **Devices** (list, remove, pending approvals) | `DevicesSettingsView.swift` (#79); `GET /api/me/devices`, `DELETE /api/chat/devices` | — | Missing | New since sheet 07. |
| Account deletion in the app | `DELETE /api/user/delete` | Opens `joinclick.co/?tab=settings` in a Custom Tab | Partial | G7-2. The Play policy is met by the web path, but iOS is native. |
| History (All / Events / Beacons / Hangouts / Saved) | `HistoryView.swift`; `GET /api/me/history` | Saved page only (`SavedEventDetailSheet.kt`) | Missing | Flag `event_history` (G3-3). |
| Tickets list | `GET /api/me/tickets` | — | Missing | Flag `ticket_wallet`. |
| My Places | `MyPlacesView.swift` | — | Missing | Flag `click_places`. |
| Legal links in Me menu | `LegalLinks.swift` | — | Partial | G7-3. |

### 2.7 Chat

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| E2EE v1 and v2 (device keys, epochs) | `iOS/Core/Crypto/*`, `ChatRepository.swift` | `KMP/crypto/MessageCrypto*.kt`, `SupabaseChatRepositoryCrypto.kt` | Done | Golden vectors are tested (`E2eeV2GoldenVectorTest`). |
| Re-key after `409 DEVICE_REVOKED` (#81); sends re-check devices (#82) | `ChatRepository.swift`, `DeviceIdentityVault.swift` | — | Missing | Without this, a removed Android device fails every send until reinstall. |
| History approval from another phone (challenge, proof, decision) | `DeviceApprovalViews.swift`; `/api/chat/devices/history-requests*` | Email approval only (`ChatApiClientDeviceHistory.kt`) | Missing | G7-1. Also send `device_label` on `POST /api/chat/devices`. |
| History backfill and key transfer after approval | `/api/chat/devices/history-backfill`, `/api/chat/key-transfer` | `AppDataManagerDeviceHistory.kt` | Done | |
| Messages: fetch, send, edit, delete with tombstones | `/api/chat/messages` | `ChatApiClient.kt`, `ChatViewModelModeration.kt`, `MessageTombstones.kt` | Done | |
| Reactions and who-reacted | `/api/chat/reactions` | `ReactorsSheet.kt` | Done | |
| Read receipts (`PATCH /api/chat/messages/read`) and delivered | | `ChatApiClient.kt:752`, `SeenByAvatars.kt` | Done | Method matches (PATCH). |
| Typing indicator | Sends and reads `{"user_id", "is_typing"}` (`iOS/Core/Chat/ChatRealtimeManager.swift:225,520`) | Sends and reads `{"userId"}` (`KMP/data/repository/SupabaseChatRepository.kt:121,592`); web also uses `userId` (`web/components/chat/useMessageActions.ts:271`) | **Broken** | Typing never shows between iOS and Android in either direction. P0-1. |
| Pins (banner, list, long-press) | `/api/chat/pins`, realtime `message_pins` | — | Missing | G6-9. |
| Forwarding | `ChatTargetPicker.swift` | `ChatViewModelForward.kt` | Done | |
| Send Later | `/api/chat/scheduled` | `ChatViewModelSchedule.kt`, `ScheduledSendViews.kt` | Done | |
| Plans / hangouts in chat, 1 h reminder | `PlanViews.swift`, `PlanReminders` | `PlanHangoutSheet.kt`, `PlanViews.kt`, `LocalReminders.kt` | Done | |
| Photos (≤10), voice (hold, lock, review), files, paste image | `ChatMediaViews.swift` | `ChatPhotoBubble.kt`, `ChatAudioBubble.kt`, `ChatAttachmentBubble.kt` | Done | |
| Photo `media_aspect` reserved size, delayed spinner, "before this device" lock | #35 | — | Partial | G6-12. |
| GIFs (KLIPY) | `KlipyClient.swift`, `GifViews.swift` | — | Missing | G6-8. A received GIF currently renders as a bare URL text message. |
| Click Drop in chat, gated original, develop | `ClickDropBubble.swift`; `/api/drops/develop`, `/api/drops/views` | Legacy 24 h time lock (`KMP/collaboration/ClickDropReveal.kt`) | **Broken** | An iOS gated drop (`drop_gated:true`, `metadata.drop_original`) shows on Android as the 12-block preview forever. P0-4, then G2-1 and G2-2. |
| Drop replies ("Replied to your drop") | `DropReplyViews.swift` | — | Missing | G2-8. |
| Icebreakers (deterministic, under 5 messages, 15 s cooldown) and say-hi deadline | `iOS/Core/Chat/Icebreakers.swift`, `SayHiPanel` | `VibeCheckAndIcebreaker.kt`, `ChatViewModelVibeIcebreakers.kt` | Partial | Say-hi stops only on `isMutuallyKept()`; iOS stops when anyone writes (G0-11). The sources are keyword-based (G6-7). |
| In-chat search and jump | `ChatView.swift` | `ConversationSearch.kt` | Done | |
| Chat backgrounds (encounter backdrops) | `iOS/DesignSystem/Components/ChatBackground.swift` | — | Missing | G6-11 (P2). |
| Header presence (last active) | #83 | `PresenceSessionTracker.kt` | Done | |
| Outbox / pending sends | `PendingSendStore` | `PendingSendStore`, outbox | Done | |
| Clear delivered notifications when a chat opens | `ClickApp.swift` | `KMP/notifications/ChatNotificationDismisser.kt` | Done | |

### 2.8 Hubs and verified groups

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Create / join (geofence edge function) / leave / delete hub | `iOS/Features/Hubs/*`; `DELETE /api/hub/{id}/participants/me` | `KMP/data/hub/HubConnectionManager.kt`, `CreateHubModal.kt`; `POST /api/hub/leave` | Done | Different leave routes; both exist server-side. |
| Hub chat with E2EE v2, reactions, photos and drops | `HubChatView.swift` | `KMP/ui/screens/HubChatScreen.kt`, `HubChatViewModel*` | Done | |
| Hub Info | `HubInfoView.swift` | `HubInfoSheet.kt` | Done | |
| Hub last-message previews decrypted in Groups | #58 | — | Partial | G6-13. |
| Event chat resolver (403/404/409/410) | `GET /api/beacons/{id}/event-chat` | `KMP/AppEventHubChatSheet.kt`, `KMP/events/EventHubAccess.kt` | Done | |
| Verified groups: create, rename, leave, delete, members, avatar | `iOS/Core/Groups/GroupRepository.swift` | `ChatViewModelCliques.kt`, `/api/cliques/members`, `/api/groups/{id}/avatar` | Done | |
| Group profile (shared media, journal, Together) | `GroupProfileView.swift` | `GroupTogetherSection`, `GroupTogetherViewModel` | Done | |
| Typing names in groups ("Lena and 2 others…") | `ChatRealtimeManager.swift` | — | Partial | Depends on P0-1. |

### 2.9 Beacons (non-event)

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Kinds: soundtrack, sos, hazard, utility, study, social_vibe, event, other | `iOS/Core/Beacons/MapBeacon.swift` | `MapBeaconKind` | Done | |
| Detail, share (link, chat), edit, delete | `BeaconDetailView.swift` | `ChatBeaconDetailSheet.kt`, `ShareBeaconBottomSheet.kt` | Done | |
| Report beacon | `POST /api/beacons/{id}/report` | — | Missing | Small. |
| Alert confirmations: Still here / Cleared / Take it down | `AlertConfirmationSection.swift`; `/api/beacons/{id}/confirm` | — | Missing | Flag `alert_confirmations` (G3-4). |
| Soundtrack preview and resolver | `SoundtrackPreviewCard.swift`, `SoundtrackResolver.swift` | `CommunitySoundtrackBeaconDetail.kt`, `KMP/util/SoundtrackResolver.kt` | Done | |
| Listening now | `ListeningNowSection.swift`; `/api/beacons/{id}/listening` | — | Missing | Flag `soundtrack_presence` (G3-5). |
| Reactions on soundtracks | `GET/PUT /api/reactions/soundtrack/{id}` | — | Missing | G1-7. |
| Engagement, bookmark, impressions | | `/engagement`, `/bookmark`, `/impressions` | Done | Impressions telemetry: Android is ahead. |
| Song search (iTunes) | `SoundtrackResolver.swift` | Link paste only | Partial | G3-6. |

### 2.10 Events

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Event page (hero, colours, host, tiles, location, going pile, markdown About) | `BeaconDetailView.swift`, `iOS/DesignSystem/Components/MarkdownText.swift` | `EventBeaconDetail*.kt` | Partial | No markdown rendering of the description (#67), no weather, no still-map location card. |
| Create / edit, including recurring | `CreateBeaconSheet.swift` | `EventEditSheet.kt`, `KMP/events/EventRecurrence.kt` | Done | |
| RSVP states: request, waitlist, declined, withdraw | `/api/beacons/{id}/rsvp` | `BeaconRsvpPersistence` | Done | Check the 30 s request-status watch. |
| Save / bookmark | `PUT /api/beacons/{id}/bookmark` | Same | Done | |
| Check-in / out with geofence reasons | `POST/DELETE /api/beacons/{id}/check-in` | `KMP/events/BeaconCheckInUi.kt` | Done | GPS needs a device check. |
| People directory | `/attendees/directory` | `EventPeopleDirectory.kt` | Done | |
| Guest list (host) | `/guest-list`, `/guest-list/match` | `EventGuestListSheet.kt` | Done | |
| **Click Pass** (QR credential, offline cache, "You're in") | `ClickPassView.swift`; `GET /api/beacons/{id}/pass` | — | Missing | New since sheet 07. |
| **Door scanner** (host) | `PassScannerView.swift`; `POST /api/beacons/{id}/pass/scan` | — | Missing | New. Reuse `QRScanner.android.kt`. |
| **Ticketing** (tiers, free issue, Stripe checkout, order poll, Me → Tickets) | `iOS/Features/Events/Tickets/*`; `/api/beacons/{id}/tickets*`, `/api/orders/{id}`, `/api/me/tickets` | — | Missing | New. Checkout in a Custom Tab returning to `/e/{id}/tickets/return`. |
| Wallet pass | `/api/beacons/{id}/pass/wallet` (`.pkpass`) | — | Missing | Apple Wallet only. **Android equivalent:** Google Wallet needs a server-issued JWT, which click-web does not provide. Mark n/a until web adds it. |
| Click Flyer (9:16 / 4:5 share image with QR) | `ClickFlyerSheet.swift` | — | Missing | New. |
| Add to Calendar (system, Google, Outlook) | `EventCalendar.swift` | — | Missing | New. Android: `Intent.ACTION_INSERT` on `CalendarContract.Events` (no permission) plus Google/Outlook web URLs. |
| Directions choice | `DirectionsDialog.swift` | — | Missing | New. Small. |
| Event weather | `EventWeather.swift`; `GET /api/geo/weather` | — | Missing | New. Android has `WeatherService.kt` (Open-Meteo) but doesn't use the server route. |
| Reminders (T-60 only) | `EventReminderScheduler.swift` | `KMP/events/EventReminders.kt` (T-60 **and** T-15) | Partial | G3-10: drop T-15. |
| Live Activity (lock screen countdown) | `ClickWidgets/EventLiveActivity.swift` | — | Missing | Android equivalent is an ongoing notification or Live Update (API 36 `Notification.ProgressStyle`). Owner call (§6, P3). |
| Event drops and recap | `EventDropsSection.swift`, `EventRecapView.swift` | — | Missing | Flag `event_drops` (G3-1, G3-2). |
| History / past events | `HistoryView.swift`; `/api/me/history` | Saved "Past or unavailable" only | Missing | See Me. |
| Share to chat (beacon card) | `ShareToChatSheet` | `BeaconShareToChatDialog.kt` | Done | |
| Business-insights nudge after check-in | `LocationNudgeCard` | — | Missing | G6-6. |

### 2.11 Click Drops (shared and story)

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Click Drop camera with filter looks | `ClickDropCameraView.swift`, `ClickDropFilter.swift` | `KMP/ui/camera/*`, `AM/ui/camera/*` (gpuimage) | Done | |
| Develop state machine (pending → ready → developed) | `iOS/Core/Media/ClickDropDevelop.swift` | `isDisposableRollLocked` 24 h lock | **Broken** for received gated drops (P0-4); Missing for send (G2-1) | |
| Develop all, live develop, bloom animation | `ClickDropDevelopEffect.swift` | — | Missing | G2-2. |
| Shared drops: post, strip, audience, caption | `SharedDrops.swift`, `SharedDropsStrip.swift` | — | Missing | G2-3, G2-5. |
| Story viewer, including pending drops with countdown (#80) | `iOS/Features/Drops/DropStoryViewer.swift` | — | Missing | G2-4. |
| Archive ("View all") | `SharedDropsArchiveView.swift` | — | Missing | G2-6. |
| Reactions and report | `/api/reactions/shared_drop/{id}`, `/api/drops/report` | — | Missing | G1-7. |
| Release push | `shared_drop_released` | — | Partial | Push route missing (§2.13). |

### 2.12 Click Places (flag `click_places`)

| Feature | iOS | Android | Status |
|---|---|---|---|
| Models and repository (`/api/places/nearby`, `/api/places/{idOrSlug}`) | `iOS/Core/Places/*` | — | Missing (G5-1) |
| Map layer and filters | `PlacePinView.swift`, `PlaceFilters.swift` | — | Missing (G5-2) |
| Place page, check-in, Pulse | `PlaceDetailView.swift` | — | Missing (G5-3) |
| My Places, privacy toggle, search | `MyPlacesView.swift` | — | Missing (G5-4) |
| Venue writes moved to Places APIs | — | `ConnectionRepositoryCreate.kt` writes `venue_id`; reads `hub_venues` | Missing (G5-5). Compatibility views keep this working for now. |

### 2.13 Platform

| Feature | iOS | Android | Status | Notes |
|---|---|---|---|---|
| Push registration | `POST /api/user/push-tokens` | `PushTokenRepository` | Done | |
| Push tap routes | `ClickApp.swift` `tapRoute` (§14 of the iOS map) | `KMP/notifications/PushRoutes.kt` | Partial | Missing routes: `event_rsvp`, `event_rsvp_request`, `friends_going`, `event_trending`, `event_drop_recap`, `event_recap`, `new_connection`, `prior_connection_accepted`, `prior_connection_request`, `device_approval`, `shared_drop_released`, `reaction`, `reconnect_nearby` (G1-3). |
| Rich push previews (v2 decryption) | `NotificationService` extension | `ClickFirebaseMessagingService.kt`, `PushPreviewKeyStore.kt` | Done | |
| Custom notification sound | `click.caf` | Default sound | Missing | G1-4. |
| Feature flags | `iOS/Core/Config/FeatureFlags.swift`; `GET /api/me/features` | — | Missing | G1-1. **Blocks all flagged rows.** Add `ticket_wallet` to sheet 07's key list. |
| Live updates (`realtime:user:{id}`, `realtime:beacons`) | `iOS/App/LiveUpdates.swift` | Per-table postgres listeners only (`RealtimeCoordinator.kt`) | Missing | G1-2. |
| Disk-first caches | `CacheStore.swift`, `FreshnessCache.swift` | `LocalMessageStore`, `ChatTimelineCache`, `IdentityCache` | Partial | G1-5. |
| Product telemetry (`install`, `app_open`, `recap_opened`) | `POST /api/telemetry/events` | — | Missing | G1-6. |
| Offline / "Couldn't refresh" banners | `OfflineNotice` | `OfflineStatusBanner.kt` | Done | |
| App Clip previews | `ClickClip/*` | — | Retired | Android equivalent is the web install page (D6). |

---

## 3. Visual and UX gaps

The references are `web/DESIGN.md` ("Quiet Presence") and `iOS/DesignSystem/*`, which agree with each other. Android's `KMP/ui/theme/*` still describes itself as "Functional Clarity (neo-brutalist)".

| Token | iOS / web | Android today | Gap |
|---|---|---|---|
| Action fill (primary button, selected chip, outgoing bubble) | `#7C3AED`; dark hover `#8655F4` | `colorScheme.primary = PrimaryBlue = #630ED4` (`KMP/ui/theme/Color.kt`, `PlatformTheme.kt:127`) | **Wrong purple.** `#630ED4` is iOS's brand-mark-only colour. Map `primary` to `#7C3AED` and keep `#630ED4` for the logo. |
| Accent text / links | light `#5A00C6`, dark `#C3A6FF` | `DeepBlue = #5A00C6` (light); no dark accent | Add a dark accent `#C3A6FF`. |
| Selection wash | light `#EEE5FF`, dark `#24133F` | `SoftBlue = #EDE0FF` | Close; align exactly. |
| Outgoing / incoming bubbles | `#5B21B6` / `#E9E9EB` (dark `#4A1FA6` / `#232326`) | `ChatBubbleTokens` | Check and align. |
| Page / card / raised surfaces | `#F6F6F8` / `#FFF` / `#F2F2F5`; dark `#0B0B0D` / `#1C1C1E` / `#2C2C2E` | `#F9F9F9` / `#FFF` / `#EEEEEE`; dark `#101212` / `#1A1C1C` / `#242626` (green-grey cast) | Neutral greys differ visibly in dark mode. |
| Text | `#111113`, secondary `#55555C`, tertiary `#6C6C71` (dark `#F5F5F7` / `#AEAEB2` / `#98989F`) | `#1A1C1C`, `#4A4455` (purple cast) | Align. |
| Destructive / online / live | `#D70015` / `#1FA855` / `#E5251B` | `error = #BA1A1A` | Align. |
| Hard borders | None; "surfaces separate by tone" | `BorderHard = #000000` (neo-brutalist), 1 dp card borders, `pressOffset 2.dp` | Remove the neo-brutalist borders and press offset. |
| Font | Manrope 600–800 for **headings only**; system face (Roboto on Android) for body | Manrope for **every** style (`KMP/ui/theme/Typography.kt:35`) | Body, label and meta should use the platform face. Keep Manrope for display/headline/title. |
| Type scale | display 34/40·800, title-1 28/34·800, title-2 24/30·800, title-3 22/28·800, headline 17/24·600, body 15/22, meta 13/18, badge 12/16·600; floor 12 | display 48/40/36, body 18/16/14 | Remap the M3 roles to the shared scale. Nothing below 12 sp. |
| Radii | 6 / 10 / 14 / 20 (cards) / 26 (sheets, surfaces); capsule buttons and chips; bubble 18 | `cardCornerRadius 16`, `buttonCornerRadius 8`; 249 ad-hoc `RoundedCornerShape(…)` (12 dp ×55, 16 dp ×43) | Add shape tokens and use capsules for buttons and chips. |
| Spacing | 4-pt rhythm 2/4/8/16/24/32/48; gutter 16 | `ClickScreenSpacing.Horizontal = 20.dp` | Gutter 20 → 16. |
| Hit targets | 44 min; row 54; primary action 50; chip 36 | Mixed | Audit against 48 dp (Android minimum) or 44. |
| Motion | One curve `cubic-bezier(.4,0,.2,1)`; 120 / 160 / 240 / 360 ms; press scale 0.97 | `MotionTokens.kt`: 90 / 140 / 190 / 240 / 320 ms; press scale 0.95–0.985 | Close. Align durations and press scale. Reduce motion is already respected. |
| Core ring | Angular gradient `#F5C451 → #E39A3B → #A855F7 → #7C3AED → #F5C451` | — | Missing on avatars (Home, Clicks, chat header). |
| Avatar fallback palette | `#4F46E5 #7C3AED #0D9488 #2563EB #BE185D #B45309 #0F766E #4338CA #15803D #92400E`, hash `31*h + char` | Shared with KMP originally | Check with a golden test. |
| Loading | Breathing Click mark with appear delay, so fast loads never flash; shimmer only on media | `AppShimmerScreen`, `ClickListRowShimmer`, `AdaptiveCircularProgressIndicator` ×12 | Add the appear delay (about 300 ms) and use shimmer only for media. |
| Empty states | `ContentUnavailableView` (icon, title, body, action) | `AppEmptyState(icon, body, title?)` | Add an action slot. Copy should match iOS per screen. |
| Error states | "Offline — showing saved …" vs "Couldn't refresh · Retry"; cancellation never shown as failure | `OfflineStatusBanner`, per-screen `PageLoad.Failed` | Mostly aligned. Add inline Retry rows per module on Home. |
| Toasts / confirm | One glass capsule toast at a time (3 s, errors 6 s); confirm dialog with a highlighted "keep" choice | `UnifiedToast`, `GlassSnackbarHost` | Close. |

The tokens are concentrated in `Color.kt`, `PlatformTheme.kt`, `Typography.kt` and `MotionTokens.kt`. Correcting those files fixes most of the gap without touching screens; the ad-hoc radii need a follow-up sweep.

---

## 4. Backend contract mismatches

**Method and route check (scripted).** Every `.get/.post/.put/.patch/.delete("…/api/…")` in commonMain was matched against click-web `app/api/**/route.ts` exports. All of them resolve to an existing route that exports the method Android uses. The breaks are in payloads and missing calls:

| # | Endpoint or channel | Mismatch | Effect | Fix |
|---|---|---|---|---|
| C1 | Realtime `chat:{id}` broadcast `typing` | Android sends and reads `{userId}`. iOS sends and reads `{user_id, is_typing}`. Web uses `{userId}`. | No typing indicator between iOS and Android. | Android sends `{userId, user_id, is_typing}` and accepts either key (P0-1). File a web follow-up to do the same. |
| C2 | `POST /api/connections/proximity` (200, `awaiting_selection`) | The response carries `existing_connection_id` and `is_new_connection:false` for an existing group. `ProximityBindOkResponseDto` drops `existing_connection_id`. | The review screen can't say "Save this Click" or tag the existing group. | Add the field and copy (P0-3). |
| C3 | `POST /api/connections/proximity/selection {pending_handshake_id, excluded_member_ids}` | Never called. | Removals are lost whenever another phone confirms first. | Call it on each removal (P0-3). |
| C4 | `messages.metadata.drop_gated`, `metadata.drop_original`, `/api/drops/develop` | Not read. | iOS gated drops never develop on Android. | P0-4. |
| C5 | `POST /api/chat/devices` → `409 DEVICE_REVOKED` | Not handled. | A device removed from another phone can't send again. | P1. |
| C6 | `POST /api/chat/devices {device_label}` | Not sent. | iOS and web approval sheets can't name the Android device. | Send `Build.MODEL`, ≤64 chars (part of G7-1). |
| C7 | `GET /api/me/features` | Never called. | All flagged features stay off; Android can't follow cohorts. | G1-1. |
| C8 | `GET /api/beacons` keyset paging (`next_cursor`) | Android reads one page. | Busy areas truncate Nearby and the map. | G3-7. |
| C9 | `web/public/.well-known/assetlinks.json` | Package `com.click.app` with a placeholder SHA-256. | App Links never verify. | click-web PR (G0-6). |
| C10 | PostgREST writes to `availability_intents`, `connection_encounters` and `users` | Android writes tables directly where iOS uses `/api/user/availability-intents` and `/api/user/availability`. | Validation is enforced only by RLS. Works today. | Move to the API on the next touch (no urgent change). |
| C11 | `venue_id` writes, `hub_venues` reads | Places renamed venues. INSERT on `places` and `venues` is revoked; `venue_check_ins` is read-only. | Works through compatibility views. | G5-5. |
| C12 | `send-push-notification` source in this repo | Re-accepts `incoming_call` (`8900a338`), which contradicts `AI.md`. | None while Android drops it. | Owner decision D1 (sheet 07). |
| C13 | `supabase/migrations` mirror | Stops at `20260919…`; click-web has 156 files up to `20261030000000`. | `check-supabase-drift.sh` fails. | `scripts/sync-supabase-from-click-web.sh` (G0-8). |

---

## 5. Leftover iOS-era code in `click`

The Gradle config is clean (one `androidTarget`; no iOS targets or cocoapods). Every `expect` has exactly one Android `actual`. These are leftovers rather than defects; clean them up only when a task already touches the file (no broad refactor):

- **Dead files:** `KMP/Greeting.kt`, `KMP/schema.kt` (comment only), `KMP/apicalls.kt` (unused `Api` for `https://click.com/api/`).
- **Empty files:** `KMP/ui/screens/TestingScreen.kt`, `KMP/viewmodel/TestingViewModel.kt`, `KMP/testing/TestDataGenerator.kt`, `KMP/ui/components/ClickLogoOutlinePoints.kt`, `AM/ui/components/AdaptiveButton.android.kt`, `AM/ui/theme/PlatformTheme.android.kt`, `commonTest/.../UserSerializationTest.kt`.
- **Trivial expect/actual pairs that could be plain common code:** `PlatformForeground`, `AvailabilityOverlapCache`, `CollectAsStateLifecycleAware`, `ChatMediaDispatcher`, `PlatformScreenSize`, `MaestroSemantics`, `SupabaseHttpEngine`, `NativeCollapsingScaffold`, `PlatformBottomBar`.
- **"Platform" and "Native" naming** (`PlatformThemeProvider`, `PlatformStyle`) and Calf `Adaptive*` wrappers (13 files) that existed to switch to UIKit.
- `NavigationItem.sfSymbol`; `getPlatform()` (used only to produce `"android"`).
- `EncounterTetherWidgetBridge` writes widget state but no widget exists. The `glance-appwidget` dependency is unused.
- `AuthViewModel` still uses `mutableStateOf` (`authState`, `isAuthenticated`), against `AI.md` §2.
- `NfcScreen` is the BLE and ultrasonic tap flow; no NFC API is used.
- `ChatMessageType.CALL_LOG` rendering is fine (iOS does the same), but `hapticCallAccept/End` are dead.

---

## 6. Prioritized task list

Each task is one PR, cut from `origin/main`, with `./gradlew :composeApp:assembleDebug :composeApp:testDebugUnitTest` green.
- "Touches" lists the expected files.
- Hardware-dependent code goes behind an interface with a fake in `commonTest`.
- Server-authoritative logic (matching, permissions, membership, flag evaluation) is never reimplemented.

### P0: interop breaks and crashes (ship first; each is small)

| ID | Task | Touches | Tests |
|---|---|---|---|
| **P0-1** | **Typing interop.** Send `{userId, user_id, is_typing}`. Decode `user_id` or `userId` and honour `is_typing:false` (clear). | `KMP/data/repository/SupabaseChatRepository.kt` (`TypingBroadcastPayload`, `sendTypingIndicator`), `SupabaseChatRepositoryPresence.kt` | `commonTest`: decode the exact iOS payload, the legacy Android/web payload, and `is_typing:false`; the encoded payload contains both keys. |
| **P0-2** | **Remove the phantom `InstantProfileActivity`.** Delete the manifest entry. Add `/connect` to MainActivity's https filter. Narrow `/c` to `/c/` so `/careers` and `/chat` stay in the browser. Check that `ConnectionDeepLinkRouter` parses `/connect/{id}`. | `composeApp/src/androidMain/AndroidManifest.xml`, `KMP/deeplink/ConnectionDeepLinkRouter.kt` (+ test) | Router unit test for `/connect/{uuid}` and `/c/{uuid}`; a manifest lint check that every declared activity class exists (Robolectric `PackageManager` resolve). |
| **P0-3** | **Group-tap review sync.** Decode `existing_connection_id`. Show "Save this Click" and the existing-group copy. Each removal or add-back posts `POST /api/connections/proximity/selection` (debounced; it's the server's job to apply exclusions). Treat `409 Handshake already finalized` as "another phone confirmed": recover through `GET /api/connections/proximity?pending_handshake_id`. | `KMP/data/api/ApiClientModels.kt`, `ApiClientProximityEndpoints.kt`, `KMP/data/repository/ConnectionRepository.kt` / `ConnectionRepositoryProximity.kt`, `KMP/viewmodel/ConnectionViewModelConfirm.kt`, `KMP/ui/components/ConnectionContextSheet.kt` (copy) | DTO decode with and without `existing_connection_id`; a ViewModel test with a fake repository that removal triggers a selection call with the right exclusions; 409 path recovers. |
| **P0-4** | **Receive gated drops.** Recognize `drop_gated` / `metadata.drop_original`. Show the preview with "Develops in …". After the reveal, `POST /api/drops/develop` and decrypt the original (v2 `drop_original` keys, v1 legacy). Never save or share the preview as the photo. | `KMP/data/models/Message*.kt`, `KMP/collaboration/ClickDropReveal.kt`, `KMP/viewmodel/ChatViewModelSecureMedia.kt`, `KMP/data/api/ChatApiClientMedia.kt`, `KMP/ui/chat/ChatPhotoBubble.kt` | Golden metadata JSON from iOS; state transitions with a fake clock; "save" is disabled until developed. |
| **P0-5** | **Say-hi countdown semantics (G0-11).** Stop the deadline banner once either side has written. | `KMP/viewmodel/ChatViewModelVibeIcebreakers.kt` | Unit test. |
| **P0-6** | **Migration mirror sync (G0-8).** Run the sync script and commit; CI drift check goes green. | `supabase/migrations/*`, `supabase/functions/bind-proximity-connection` | `scripts/check-supabase-drift.sh` |

### P1: foundations and visual baseline (unblock everything after)

| ID | Task | Touches | Tests |
|---|---|---|---|
| **P1-1** | **Feature flags client (G1-1)**, including `ticket_wallet`. `StateFlow<FeatureFlags>`, per-user encrypted persistence, refresh on sign-in and foreground, cleared at sign-out, unknown or failed means off. | new `KMP/core/FeatureFlags.kt`, `FeatureFlagsRepository.kt`; `KMP/data/api/ApiClient*.kt`; hook in `KMP/data/AppDataManager.kt` and sign-out | Off by default, malformed JSON gives all off, persistence round-trip, reset on sign-out. |
| **P1-2** | **Theme tokens to Quiet Presence (colour).** Map M3 roles to the iOS/web table in §3, light and dark. Drop the hard borders and press offset from `PlatformStyle`. Keep `#630ED4` as `BrandMark`. | `KMP/ui/theme/Color.kt`, `PlatformTheme.kt` | Contrast tests in `Contrast.kt` (existing helpers) for action-on-white and text roles; Compose screenshot tests of a token gallery (light and dark). |
| **P1-3** | **Typography: Manrope for headings only.** Display/headline/title use Manrope ExtraBold/Bold; body/label use `FontFamily.Default`. Remap sizes to the shared scale (floor 12 sp). | `KMP/ui/theme/Typography.kt` | Unit test that every style is at least 12 sp; screenshot test of the type ramp. |
| **P1-4** | **Shape, spacing and motion tokens.** Add `ClickShapes` (6/10/14/20/26, capsule, bubble 18) and spacing (2/4/8/16/24/32/48, gutter 16). Align `MotionTokens` durations to 120/160/240/360 and press scale to 0.97. Do not sweep screens in this PR. | `KMP/ui/theme/` (new `Shapes.kt`), `KMP/ui/components/ClickLaunchPrimitives.kt`, `MotionTokens.kt` | Token unit tests. |
| **P1-5** | **Screenshot test harness.** Add Roborazzi (Robolectric-based, runs in `testDebugUnitTest`) with a baseline for the token gallery and two core components. | `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `androidUnitTest/.../screenshots/*` | Record and verify tasks in CI. |
| **P1-6** | **Re-key after `409 DEVICE_REVOKED`, and re-check devices before the first send of a session.** | `KMP/data/repository/SupabaseChatRepositoryCrypto.kt`, `KMP/crypto/DeviceIdentityStorage.kt` (behind the existing expect) | A fake API returns 409 once: a new identity is generated once, and a second 409 surfaces an error. |
| **P1-7** | **Push routes (G1-3).** Add the 13 missing types; unknown types open the app; missing IDs never navigate. | `KMP/notifications/PushRoutes.kt` | One test per type. |
| **P1-8** | **Product telemetry (G1-6).** | `KMP/telemetry/` | Once per day across restarts; no PII. |
| **P1-9** | **Core ring on avatars.** | `KMP/ui/components/` avatar composable, `KMP/ui/theme/ClickAccent.kt` | Screenshot test. |

### P2: high-value user features (flagged; ship dark)

| ID | Task | Touches |
|---|---|---|
| P2-1 | Event reminders: drop T-15 and cancel existing T-15 work (G3-10) | `KMP/events/EventReminders.kt`, `EventReminderCoordinator.kt` |
| P2-2 | Upcoming on Home uses `/api/beacons/mine` plus saved, tagged Hosting / Going / Saved | `KMP/viewmodel/HomeViewModel.kt`, `KMP/data/api/ApiClientBeaconEndpoints.kt`, `HomeComponents.kt` |
| P2-3 | Add to Calendar (system intent, Google, Outlook) and directions choice | new `KMP/events/EventCalendarLinks.kt` (pure URL builders + tests), `EventBeaconDetail*.kt`, `AM/` intent launcher |
| P2-4 | Event weather via `GET /api/geo/weather` | `KMP/data/api/`, `EventBeaconDetail*.kt` |
| P2-5 | Markdown event descriptions (headings, lists, quotes, links) | new `KMP/ui/components/MarkdownText.kt` (parser + tests) |
| P2-6 | Click Pass screen (`GET /api/beacons/{id}/pass`) with offline cache and brightness | new `KMP/ui/screens/ClickPassScreen.kt`, `KMP/events/ClickPass.kt`; `AM/` window brightness |
| P2-7 | Door scanner (`POST /api/beacons/{id}/pass/scan`) with verdict sheet | new screen; reuses `QRScanner.android.kt` |
| P2-8 | Settings → Devices (`GET /api/me/devices`, `DELETE /api/chat/devices`) | `KMP/ui/screens/SettingsSubpages.kt`, `KMP/data/api/ChatApiClientDeviceHistory.kt` |
| P2-9 | Approve new device from this phone (G7-1), including `device_label` | as G7-1 |
| P2-10 | In-app account deletion (G7-2) | `KMP/ui/screens/SettingsScreen.kt`, `ApiClientAccountEndpoints.kt` |
| P2-11 | Alert confirmations and beacon report (G3-4) | beacon detail and repo |
| P2-12 | Listening now and soundtrack reactions (G3-5, G1-7) | |
| P2-13 | Activity inbox (G6-1) | |
| P2-14 | Click Drops develop and send (G2-1, G2-2), then shared drops and story (G2-3 to G2-9) | |
| P2-15 | Nearby paging, sort and search (G3-7); map pin stacking | `MapViewModel*`, `MapDiscoveryLayout.kt`, `MapView.android.kt` |
| P2-16 | Ticketing (tiers, free issue, Stripe checkout in a Custom Tab, order poll, Me → Tickets) | new `KMP/events/tickets/*` |
| P2-17 | Pins, GIFs, mute menu (G6-8 to G6-10) | |
| P2-18 | Connect quality: capture session, quality keys, `sensor_observation` v3, altitude and location follow-ups (G4-1 to G4-9) | |
| P2-19 | Multi-Tap `prepareGroup` port (G0-12) | |
| P2-20 | Click Places I1–I4 (G5-1 to G5-5) | |

### P3: polish and owner calls

- Click Flyer (share image), Live Activity equivalent (ongoing notification), encounter backdrops (G6-11), per-tab root menus (G6-15), 3-step onboarding (D5), notification sound (G1-4).
- Screen sweep to the shape and spacing tokens, in batches of 3–5 screens per PR.
- Dead-file cleanup from §5, folded into PRs that already touch those packages.
- Google Wallet: blocked on a click-web issuer endpoint.

---

## 7. Can't be verified in a sandbox

These need physical devices. The code goes behind interfaces with fakes so the logic is unit-tested, but behaviour must be recorded in `docs/regression-testing/` as a dated device run:

| Item | Why | Covered by |
|---|---|---|
| BLE advertise/scan and detected-device lists | Radio hardware; no emulator BLE | `ProximityManager` interface, `MockProximityManager` |
| 18.5 kHz ultrasonic chirp detection | Speaker/mic frequency response varies by device | `UltrasonicTokenCodec` unit tests only |
| Progressive GPS accuracy, check-in geofence, Places check-in | Real fixes and accuracy tiers | `LocationService` fake |
| Barometer, ambient noise, `sensor_observation` content | Sensors | Sensor provider fakes |
| Camera capture, QR scanning, Click Drop camera filters | CameraX and ML Kit on hardware | `QRScanner` expect; payload parsing tests |
| FCM delivery, rich previews, notification channels and sound, tap routing from a cold start | Needs Firebase project and device | `PushRoutes` unit tests |
| Doze behaviour of event and plan reminders (WorkManager) | OS scheduling | `LocalReminderScheduler` fake |
| App Links verification | Needs release signing and the click-web assetlinks deploy | `adb shell pm get-app-links` |
| Cross-platform interop: typing, group tap with mixed iOS/Android, gated drops both ways, device approval | Needs an iOS device and an Android device on production or staging | sheet 07 G8-1 matrix |
| Screen brightness for QR and Click Pass | Window attributes on device | Manual |
| Stripe checkout return | External browser and redirect | Order-poll logic unit-tested with a fake |

---

## 8. Environment note for Phase 3

`./gradlew assembleDebug` needs the Android SDK (platform 36, build-tools). The cloud environment used for this audit denies `dl.google.com`, so the SDK can't be installed and no build or unit test can run here. To run Phase 3 in that environment, add `dl.google.com` to the environment's allowed domains (see https://code.claude.com/docs/en/cloud-environments#network-access). Locally and in GitHub CI (`.github/workflows/android-ci.yml`) the build works as documented in `AGENTS.md`.
