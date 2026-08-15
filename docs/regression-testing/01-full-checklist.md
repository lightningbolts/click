# Click — Full Regression Checklist

Run on **both iOS and Android** unless a section is platform-tagged.  
**Canonical QA doc** for major changes. Source promoted from the platform-native UI plan checklist; annotated with `[KNOWN-N]` where the [known-issues audit](03-known-issues-audit.md) applies.

**Legend:** `[UI]` = platform-native interaction · `[E2EE]` = crypto/data · `[P]` = platform-specific · `[KNOWN-N]` = see [03-known-issues-audit.md](03-known-issues-audit.md)

**Index:** [00-INDEX.md](00-INDEX.md) · **Smoke:** [02-smoke-10min.md](02-smoke-10min.md) · **Android:** [04-android-focus.md](04-android-focus.md)

---

## 0. Automated gates (CI — run first)

- [ ] `./gradlew :composeApp:compileDebugKotlinAndroid`
- [ ] `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
- [ ] `./gradlew :composeApp:testDebugUnitTest`
- [ ] `./gradlew :composeApp:iosSimulatorArm64Test` (or `iosSimulatorArm64Test` target)
- [ ] `bash scripts/maestro-smoke-android.sh` (`click/`; rebuilds and reinstalls debug APK, then smoke)
- [ ] `click-web`: `npm run test:e2e` with the app on `:3000`
- [ ] `ChatSwipeMathTest` — swipe-to-reply inverse math
- [ ] `ChatViewModelTest` — send/receive/session
- [ ] `GlassCardUiTest` — bordered card rendering (iOS sim; Functional Clarity)
- [ ] `UnifiedToastTokensTest` — toast timing constants
- [ ] `QrCodeViewUiTest` — QR display (iOS sim)
- [ ] `ConnectionEncounterMergeTest` — encounter merge logic
- [ ] `HomeRecentConnectionsTest` — home routing helpers
- [ ] `HubChatSettingsMenuTest` — hub settings menu
- [ ] `DiscoveryFeedSectionsTest` — map discovery sections
- [ ] `OfflineBootTest` — offline-first auth boot
- [ ] `SwipeBackCommitMathTest` — horizontal dismiss thresholds (if present)
- [ ] `PlatformNativeAuditTest` — iOS actuals use UIKit; Android sheet uses `ModalBottomSheet` (if present)
- [ ] `click-web`: `npm test` — proximity route + matching tests green

---

## 1. App shell & navigation

- [ ] Cold start → `AppShimmerScreen` while auth/onboarding resolves
- [ ] Bottom tab bar: Home, Add Click, Clicks, Map, Settings — all five navigate correctly
- [ ] `[P] iOS` Native `UITabBar` renders as opaque solid bar (Functional Clarity; no liquid glass translucency); inset behavior correct (no black gap)
- [ ] `[P] Android` M3 `NavigationBar` renders with correct selection state
- [ ] Tab re-tap returns to tab root (no duplicate stack entries)
- [ ] `AnimatedContent` tab transitions: slide + fade on tap navigation
- [ ] `[P] iOS` Swipe-back from overlay screens (QR, NFC, My QR, hub chat, non-root tabs)
- [ ] `[P] Android` System back / toolbar back dismisses overlays in correct order
- [ ] `OfflineStatusBanner` appears when offline; hides when online
- [ ] `routeHistory` back stack: navigate deep → back returns through history
- [ ] `isConnectionsChatOpen` disables primary-tab swipe-back while chat overlay open
- [ ] Global overlays z-order: calls > disposable camera > reveal > tether > sheets

---

## 2. Platform-native UI (all features)

- [ ] `[UI] iOS` Sheets use `UISheetPresentationController` system glass + detents (not a Compose-only sheet); begin a dismiss swipe from the body, cards, and blank sheet regions — not only the grabber
- [ ] `[UI] Android` Sheets use the themed adaptive Material host drag physics; begin a dismiss swipe from the body as well as the grabber
- [ ] `[UI]` `ClickActionBottomSheet` opens at partial/medium height; `ClickFormBottomSheet` expands for tall content
- [ ] `[UI] iOS` Simple confirms use `UIAlertController` (block, remove, report, delete message, leave hub)
- [ ] `[UI] Android` Simple confirms use M3 `BasicAlertDialog` / `AlertDialog`
- [ ] `[UI] iOS` Primary/secondary buttons use `UIButton` highlight (no Compose scale bounce)
- [ ] `[UI] Android` Buttons show bounded ripple; pills/circles fully rounded ripple
- [ ] `[UI] iOS` No Material ripple on any surface
- [ ] `[UI]` Context menus: Android `DropdownMenu`; iOS `UIMenu` or approved Functional Clarity bordered popup for floating menus
- [ ] `[UI]` Toasts: Android `SnackbarHost` motion; iOS banner slide without bouncy spring
- [ ] `[UI]` Swipe-back / swipe-to-reply settle: no overshoot bounce; platform easing only
- [ ] `[UI]` Haptics: selection tick on chips/toggles; heavy on long-press/destructive; success on connect/NFC
- [ ] `[UI]` Functional Clarity solid pills (`LiquidGlassPill` API) used only for static decoration (no press on non-interactive header pills)
- [ ] `[KNOWN-10]` Spot-check visual polish on Home, Clicks, Map, chat composer, sheets (misc UI bugs)

---

## 3. Auth & account

- [ ] `LoginScreen` — email/password sign-in succeeds
- [ ] `LoginScreen` — Google OAuth sign-in succeeds (`click://login` deep link)
- [ ] Forgot Password opens browser to `/forgot-password` (not `/reset-password`); email form sends reset link; link completes on `/reset-password`; can sign in with new password
- [ ] `SignUpScreen` — new account creation
- [ ] Invalid credentials show error (no crash; no raw Bearer in message)
- [ ] Sign out from Settings clears session and returns to login
- [ ] Offline boot with valid refresh token → main shell without network (`OfflineBootTest` parity)
- [ ] Expired access + valid refresh → silent refresh or offline admission
- [ ] Session persists across process death

---

## 4. Onboarding & profile gate

- [ ] `ProfileBasicsGateScreen` blocks main shell until display name / basics complete
- [ ] Onboarding flow: Welcome → Interests (`InterestTaggingScreen`) → Avatar (`AvatarScreen`)
- [ ] Onboarding back: Avatar → Interests → Welcome; Welcome has no back; saved interests are not wiped
- [ ] 3-step onboarding progress visible on Welcome / Interests / Avatar
- [ ] Login ↔ Sign Up uses the same slide+fade as onboarding (Maestro smoke `login_signup_toggle`)
- [ ] Returning user with an avatar: cold start never flashes Avatar (`onboarding-avatar` not visible)
- [ ] Skip avatar advances onboarding
- [ ] Onboarding state persists in `TokenStorage` across kill
- [ ] Onboarding handoff shimmer → Home without flash of wrong tab
- [ ] Runtime permissions serialize through `PermissionRequestQueue` + prime sheet (Continue / Not now); camera does not auto-prompt on mount
- [ ] `PermissionsOnboardingScreen` — location, notifications, microphone prompts as applicable
- [ ] `LocationOnboardingScreen` — location permission flow

---

## 5. Connect & handshake

### 5.1 Add Click hub (`AddClickScreen`)

- [ ] Tap-to-Connect opens `NfcScreen`
- [ ] Scan QR opens `QRScannerScreen`
- [ ] My QR opens `MyQRCodeScreen`
- [ ] Community hub create entry (if shown) opens `CreateHubModal`
- [ ] `[UI]` Entry cards use platform buttons / clickables

### 5.2 QR scan (`QRScannerScreen`)

- [ ] Camera preview renders `[P] Android` CameraX · `[P] iOS` AVFoundation
- [ ] Valid token QR → `ConnectionContextSheet` (QrFlow)
- [ ] Valid legacy/user QR → context sheet or connect path
- [ ] Invalid QR shows error without crash
- [ ] Scanner dismiss (back / close) returns to Add Click
- [ ] `[UI]` Lens pulse uses `ClickPlatformHandshakePulse` (no bouncy spring)

### 5.3 My QR (`MyQRCodeScreen` + `QrCodeView`)

- [ ] QR encodes correct Universal Link / user id
- [ ] Share action works
- [ ] `[UI]` Share button platform-native

### 5.4 Tap / Tri-Factor (`NfcScreen`)

- [ ] Idle → Fetching location → Scanning/handshaking states render
- [ ] BLE + ultrasonic + GPS handshake progresses (`ConnectionViewModel` state machine); ~**5s** listen window
- [ ] Single-peer match → 1:1 DM connection (`is_group=false`) — `[KNOWN-3]` verify DM path, not group-only UX
- [ ] Multi-peer (3+) → `awaiting_selection` → host **People multi-select above tags** (not `"Connect with everyone"`) → `confirmProximitySelection` (≤12)
- [ ] `[KNOWN-1]` 3-phone group registration: selected participants registered on the group connection
- [ ] `[KNOWN-2]` Re-tap same pair: no duplicate 1:1 connection rows in inbox / DB; reconnect uses `ReconnectEncounter`
- [ ] `[KNOWN-2]` Re-tap same pair: **no duplicate map pin** (single pin per peer)
- [ ] Confirm creates connection; offline capture queues sync snackbar
- [ ] `TaggingContext` → `ConnectionContextSheet` (in-screen or after dismiss)
- [ ] Error states (`NfcErrorContent`) recoverable
- [ ] Background/foreground: `tryFlushPendingProximityHandshakes` recovers
- [ ] `[UI]` State transitions use platform motion (not bouncy `AnimatedContent` spring)
- [ ] `[P] iOS` NFC read path functional
- [ ] `[P] Android` Proximity/BLE permissions and handshake — also [04-android-focus.md](04-android-focus.md)

### 5.5 Deep links & App Clip

- [ ] `https://joinclick.co/c/{uuid}` → `ConnectionDeepLinkRouter` → context sheet
- [ ] `click://c/{uuid}` and legacy `click://connect/{uuid}` parse correctly
- [ ] Cold start deep link routes after auth
- [ ] Warm start deep link while app open
- [ ] `AppClipHandshakeScreen` loads profile from invocation URL
- [ ] Malformed UUID rejected silently (no crash)

### 5.6 Context tags (`ConnectionContextSheet`)

- [ ] QrFlow vs NewSpark modes show correct copy
- [ ] Multi-peer: **People** section appears **above** Suggested / All tags; Connect disabled until ≥1 peer selected
- [ ] Filter chips / tags selectable
- [ ] Save applies tags; skip/cancel dismisses without corrupting state (host abandon does not create group)
- [ ] Reconnect encounter path (`saveReconnectEncounter` / `ReconnectEncounter` presentation)
- [ ] After BLE reconnect / encounter save, peer **Our timeline** shows the new encounter **without** clearing app cache
- [ ] `at_event` attachment per reporting user who has RSVP **and** active check-in (not all-or-nothing); non-engaged viewers do not see event title on timeline / Beacons tab
- [ ] Memory capsule sensor capture when opted in (noise, barometric)
- [ ] `[UI]` Save uses `ClickPlatformButton`; chips use `ClickPlatformSegmentedControl`

### 5.7 Connection reveal (`ConnectionRevealOverlay`)

- [ ] Overlay plays after successful connect
- [ ] Haptic sequence fires (heavy → success)
- [ ] Dismiss navigates to Connections tab
- [ ] `[UI]` Entry animation platform settle (no bouncy spring)

---

## 6. Connections (Clicks) inbox

### 6.1 List & tabs (`ConnectionsListView`, `ConnectionsTabControls`)

- [ ] Active / Groups / Archived tabs switch; list filters correctly
- [ ] `[KNOWN-2]` Bluetooth reconnect does **not** create a duplicate 1:1 Active chat row (collapse/upsert by peer)
- [ ] Sort/filter dropdown works
- [ ] Search within list (if present) filters rows
- [ ] Core connections pinned when marked core
- [ ] Archived tab shows archived threads only
- [ ] Groups tab shows verified cliques + community hub feed rows
- [ ] Empty states render for each tab
- [ ] `[UI]` Tab segments platform-native; sort menu `ClickPlatformDropdownMenu`

### 6.2 Connection rows (`ConnectionItem`, `ConnectionRowGestures`)

- [ ] Tap row opens chat
- [ ] Long-press opens `ConnectionActionSheet`
- [ ] 1:1 avatar tap → `TabbedUserProfileSheet`
- [ ] Group avatar tap → group members / `TabbedGroupProfileSheet`
- [ ] Unread badge / preview text accurate
- [ ] Online indicator on avatar when peer online (dot outside circular clip; visible on list rows **and** Remember Me strip)
- [ ] `[UI]` Row press: full-row `MotionTokens.PressScale` + glass border pressed alpha (no ripple)

### 6.3 Chat push (`ConnectionsScreen`)

- [ ] List stays mounted; chat overlays; parallax on swipe-back (iOS **and** Android)
- [ ] Swipe-back dismisses chat; `ChatTransitionMode.Gesture` clears correctly
- [ ] Timestamp peek via `rightToLeftPeek` during horizontal drag
- [ ] `[P] Android` `PlatformBackHandler` closes chat (system back)
- [ ] Tab bar stays composed under opaque chat (Scaffold zIndex cover — no remount pop on dismiss)
- [ ] `initialChatId` / `pendingChatId` opens correct thread from push/deep link/Home/Map
- [ ] Scroll position on list preserved after chat dismiss

### 6.4 Connection actions (`ConnectionActionSheet`, `ConnectionSheetDialogs`)

- [ ] Nudge sends nudge
- [ ] Archive / Unarchive (including server-lifecycle archived)
- [ ] Add to Core / Remove from Core
- [ ] Mark as Unread (1:1 and group)
- [ ] Remove connection → confirm → `deleteConnectionPermanentlyById`
- [ ] Report → confirm → `reportConnectionForConnection`
- [ ] Block → confirm → `blockUserForConnection`
- [ ] Leave group → confirm → `leaveVerifiedClique`
- [ ] Delete group → confirm → `deleteVerifiedClique`
- [ ] `[UI]` Action sheet rows `ClickPlatformListRow`; confirms `ClickPlatformAlertDialog`

### 6.5 Verified clique flows

- [ ] FAB on Active tab → `ConnectionMemberPickerSheet`
- [ ] `[KNOWN-8]` Create clique with eligible members → group chat opens **without crash**
- [ ] New group chat appears on the other member's inbox without pull-to-refresh
- [ ] Proximity autofill (`verifiedCliqueFromProximity`) pre-selects friends
- [ ] Add members from group profile → eligibility mask respected
- [ ] Remove member from group
- [ ] Group chat shows peer avatars on incoming bubbles

### 6.6 Community hub rows (`ActiveHubFeedRow`, `HubActionSheet`)

- [ ] Tap hub row opens hub chat overlay
- [ ] Long-press hub actions (leave, etc.)
- [ ] `[UI]` Hub row press feedback: same PressScale + border as connection rows

### 6.7 Profiles (`ProfileBottomSheet`, `UserProfileBottomSheet`)

- [ ] Tabbed user profile: Message opens chat
- [ ] Tabbed group profile: members list, add member
- [ ] Connection moment / encounter metrics display (noise, elevation, wind, etc.)
- [ ] Memories section (`MemoriesListSection`) when present
- [ ] Timeline journal: bordered **Add** button; timeline bullet aligned
- [ ] After BLE reconnect, open peer profile — encounter appears on timeline without app cache clear

---

## 7. Chat (1:1 & verified group)

### 7.1 Core messaging `[E2EE]`

- [ ] Thread loads encrypted history; messages decrypt for display
- [ ] Send text message → appears in thread → delivers to peer
- [ ] Offline send queues locally; sends on reconnect
- [ ] Typing indicator shows / clears
- [ ] Read receipts update (DM / group)
- [ ] Delivery receipt states (sending, sent, failed)
- [ ] Hub chat does **not** show a fake blue Read receipt
- [ ] Near-bottom inbound messages animate to latest; initial thread paint still snaps (no history-row `animateItem`)
- [ ] No plaintext or key material in logs

### 7.2 Composer (`ConnectionChatMessageComposer`, `ChatKeyboardDock`)

- [ ] Text input, send button
- [ ] `[P] iOS` Composer sizing / corner radii / circle send button
- [ ] `[P] Android` Composer layout and send affordance
- [ ] Keyboard opens/closes lockstep with composer (iOS Animatable + UIKit curve; no ~200ms lag)
- [ ] Reply quote bar when replying; cancel collapses with AnimatedVisibility (no abrupt snap)
- [ ] Edit mode: cancel edit, save edit
- [ ] Attachment picker entry (photo, file, voice)

### 7.3 Gestures & chrome

- [ ] Swipe-to-reply on message bubble (L→R received, R→L sent)
- [ ] Swipe threshold haptic fires once per gesture
- [ ] Reply composer pre-fills quoted message
- [ ] Long-press message → `MessageActionSheet` (no native word-select competing with sheet)
- [ ] `[P] iOS` Timestamp gutter peek (horizontal drag while chat embedded in Connections)
- [ ] `[UI]` Swipe settle uses platform easing (no bubble jump mid-settle — `ChatSwipeMathTest`)
- [ ] `[UI]` Interactive-back: header chrome uses `interactiveBackPersonality` (subtle scale/offset)

### 7.4 Message actions (`MessageActionSheet`)

- [ ] Reply
- [ ] Emoji reaction picker + toggle reaction on bubble
- [ ] Copy text
- [ ] Edit own text message
- [ ] Delete message (confirm → delete)
- [ ] Save image to gallery (photos)
- [ ] Share image
- [ ] Forward message → pick target chat → forward succeeds
- [ ] `[UI]` Sheet + rows platform-native

### 7.5 Media & attachments `[E2EE]`

- [ ] Send photo from gallery/camera
- [ ] Send file attachment
- [ ] `[KNOWN-7]` Voice message record (`VoiceRecordDialog` / picker) — record, preview, send **without crash** `[P] Android`
- [ ] Photo bubble tap → fullscreen preview (`ChatExpandedPhotoPreview`, `GlassFullscreenMediaOverlay`)
- [ ] `[KNOWN-7]` Audio bubble play/pause (`ChatAudioBubble`) **without crash** `[P] Android`
- [ ] Inbound media decrypts to vault before display
- [ ] Roll-locked / privacy blur on sensitive media when applicable

### 7.6 Chat header & calls entry

- [ ] Back closes chat (platform-appropriate)
- [ ] Peer name / avatar / presence (online dot overlays avatar outside clip; subtitle Online/Offline matches `onlineUsers` ∪ `isPeerOnline`)
- [ ] Call menu: voice call, video call
- [ ] `[UI]` Call menu `ClickPlatformDropdownMenu`
- [ ] Overflow → `ConnectionActionSheet` (same actions as list)

### 7.7 Vibe check & icebreakers (`VibeCheckAndIcebreaker`)

- [ ] `VibeCheckBanner` shows for new connection window
- [ ] Keep / pass mutual opt-in (30-minute expiry unchanged)
- [ ] `IcebreakerPanel` shows when < 5 messages; prompt tap sends icebreaker
- [ ] Icebreaker cooldown respected

### 7.8 Archive warning (`ConnectionArchiveWarningBanner`)

- [ ] Banner shows for at-risk archived connection
- [ ] Dismiss and navigate actions work

### 7.9 Collaboration & disposable roll

- [ ] Re-encounter opens disposable roll window (`CollaborationSessionManager`)
- [ ] `DisposableCameraView` opens from chat/App
- [ ] Capture photo with optional filters
- [ ] Send roll photo to encrypted chat
- [ ] Dismiss camera without send
- [ ] Session expiry closes roll UI

### 7.10 Encounter tether (`EncounterTetherManager`, `TetherCompassToast`)

- [ ] Tether compass toast shows when navigating to encounter
- [ ] `GlobalTetherOverlay` on map/home when active
- [ ] Widget bridge updates (platform)

### 7.11 Chat loading & errors (`ChatLoadingAndDialogs`)

- [ ] Loading state while thread hydrates
- [ ] Error dialogs use platform alerts post-overhaul

---

## 8. Voice & video calls

- [ ] Outgoing voice call from chat → `CallPreviewOverlay` → connected
- [ ] Outgoing video call
- [ ] Incoming call UI → accept / decline
- [ ] `ActiveCallOverlay` in-call controls (mute, speaker, end); control bar clears nav-bar / home-indicator inset
- [ ] End call returns to chat; overlay dismisses cleanly
- [ ] Group video 5+: layout stays Grid (all remotes visible); layout override resets on next call
- [ ] `[P] iOS` CallKit integration; VoIP push path (no Firebase for VoIP)
- [ ] `[KNOWN-6]` `[P] Android` LiveKit — outgoing voice/video after granting mic/camera (permission retry)
- [ ] `[P] Android` Incoming call intent / notification (`POST_NOTIFICATIONS`)
- [ ] Call push toggle in Settings respected
- [ ] Blocked user cannot call

---

## 9. Home dashboard (`HomeScreen`)

- [ ] Time-of-day greeting + first name visible (no competing `"Home"` title)
- [ ] Search pill opens unified search
- [ ] Featured Event (when reminder exists) → View on Map focuses beacon
- [ ] Explore nearby tiles only for kinds/hubs with nearby count > 0 (no fake Networking/Workshop tiles)
- [ ] "I'm down for…" availability intents strip (after Featured Event, before Explore)
- [ ] Post new availability intent from Home
- [ ] Reconnect reminders: Message + dismiss
- [ ] Recent connections grouped by location; tap opens chat
- [ ] Event reminder cards (day-of, one-hour-before) — dismiss + View on Map; featured beacon not duplicated
- [ ] Connection insights card when data available
- [ ] `ConnectionArchiveWarningBanner` / Poll-Pair from home path if applicable
- [ ] Pull-to-refresh or refresh affordance updates data

---

## 10. Map & discovery (`MapScreen`, `MapDiscoveryLayout`)

- [ ] Map loads user location (permission granted)
- [ ] `[KNOWN-5]` Map basemap renders with intended color styling (not stuck grayscale unless ghost mode)
- [ ] Connection pins render; tap opens connection marker sheet
- [ ] Memory Map toggle off still shows all connection pins (not core-only)
- [ ] `[KNOWN-2]` Bluetooth reconnect → **single pin per peer** (no duplicate connection pins)
- [ ] Community hub pins; tap → hub detail sheet → join geofence flow
- [ ] Beacon pins; tap → beacon detail
- [ ] `[KNOWN-4]` Events visible on map also appear in discovery list view (and vice versa for in-range events)
- [ ] Event encounter attach (`at_event`): per reporting user with RSVP **and** active check-in; exact title on Timeline; Beacons tab opens event for eligible viewers only
- [ ] Map layer filter dropdown (connections, hubs, beacons / events)
- [ ] Zoom controls (`MapZoomGlassControls`)
- [ ] `[UI]` Map overlay icon buttons platform-native
- [ ] Ghost mode: grayscale map, no user dot, reduced discovery (parity `[P] iOS` vs Android)
- [ ] Map PIP / discovery split layout (`MapDiscoveryLayout`) when collapsed
- [ ] Proximity match toast / vibration when intent match nearby
- [ ] Navigate to chat from map connection sheet

---

## 11. Beacons (`BeaconDropSheet`, beacon detail)

- [ ] Drop beacon FAB / entry opens drop sheet
- [ ] Drop sheet: switch Hub ↔ Event tabs without full-height flash / duplicated offset sheet
- [ ] Drop sheet: text fields open keyboard flush to sheet bottom (no black gap)
- [ ] Create verified click: tap Search connections stays in sheet (does not exit to Home)
- [ ] Connection map pins stay at first-meet location after later community-beacon encounters
- [ ] Home Saved events section loads after cold start / sign-in (retries when auth warms)
- [ ] Group chat with blank `chat.id`: send creates group chat row then delivers message
- [ ] Create event / vibe / social / hazard beacon with required fields
- [ ] Event create: **Check-in area** chips set venue scale (`intimate` / `neighborhood` / `venue` / `campus`)
- [ ] Event create: **address search** or “Use my location” required; can create event without being at the venue
- [ ] Event detail shows address label when `location_name` / `formatted_address` set
- [ ] Submit success adds pin; failure toast
- [ ] Beacon detail sheet: RSVP, share, navigate, bookmark, labeled check-in CTA
- [ ] Chat timeline event beacon card (1:1 and group): detail opens with bookmarked / RSVPed / checked-in state synced
- [ ] Profile Beacons tab: tap still focuses map / opens event if on map (not the chat detail sheet)
- [ ] Event people directory: every signed-in viewer can open Directory → sort A–Z / Interests / Mutuals; relationship / sort-aware metrics; list scrolls to top on chip change; no large blank band at full sheet height
- [ ] Event mutuals: “Mutuals here” section uses the active sort and is not duplicated under Everyone; Connections show friends-in-common count; view-only FoF profiles (no Connect)
- [ ] Bookmark toggle survives app force-kill (server-backed)
- [ ] Check-in is a full-width labeled button (`Check in here` / `Checked in`) — not a hero icon circle
- [ ] Check-in CTA updates immediately on tap (optimistic); pending blocks double-tap
- [ ] Check-in far from pin → snackbar + state reverts (geofence)
- [ ] Check-in with location denied → snackbar, stays unchecked
- [ ] Check-in before live window → “Check-in opens when the event starts”
- [ ] Events list from map: drop beacon / layer controls stay under overlay (no alpha remount on back)
- [ ] `initialBeaconId` focuses correct pin on load
- [ ] Event reminders sync (`EventReminderCoordinator`)
- [ ] `[KNOWN-9]` Hazard beacon icon sized consistently with other pin icons (not oversized)
- [ ] `[UI]` Beacon sheets use `PlatformSheetRoot`
- [ ] Website: `/insights/event-engagement` loads funnel / arrival / rejects (demo or live)

---

## 12. Community hubs

- [ ] `CreateHubModal` — create hub with name/category (permanent; no 24h expiry)
- [ ] Created hubs stay on map / Groups after creation day (not removed by TTL)
- [ ] Map join hub → proximity verify → `HubChatScreen`
- [ ] Deep link `click://hub/{id}` / universal link opens hub
- [ ] `HubChatScreen` — realtime messages in lobby + unlocked chat
- [ ] Hub send cooldown 5s — composer placeholder `Wait {N}s…`; server enforces 429 `HUB_MESSAGE_COOLDOWN`
- [ ] Outside geofence send → “No longer near hub…” (not expired copy)
- [ ] Hub settings menu: leave, edit (owner), delete (owner)
- [ ] Leave hub confirm → `leaveActiveHub`
- [ ] Delete hub confirm → `deleteActiveHub`
- [ ] Hub chat overlay dismiss: `[P] iOS` swipe-back · `[P] Android` back
- [ ] Empty lobby copy when first arrival
- [ ] `[UI]` Hub input bar, settings dropdown platform-native

---

## 13. Global search (`UnifiedSearchSheet`, `GlobalSearchScreen`)

- [ ] Open search from header magnifying glass
- [ ] Filter chips: Active, Archived, Cliques, Nearby, Beacons, Intents, etc.
- [ ] Query returns results; tap navigates to chat/map/beacon/target
- [ ] Empty query / no results states
- [ ] Dismiss sheet preserves tab underneath
- [ ] `[UI]` Sheet uses `PlatformSheetRoot`; chips platform-native

---

## 14. Availability & intents (`AvailabilitySheet`, `AvailabilityViewModel`)

- [ ] Post availability intent (coffee, study, gym, etc.)
- [ ] Edit existing intent
- [ ] Delete intent with confirm
- [ ] "Free this week" toggle in Settings
- [ ] Active intent list in Settings reflects server state
- [ ] 24-hour TTL behavior (intents expire)
- [ ] Match alerts when overlapping availability (push + in-app if applicable)

---

## 15. Settings (`SettingsScreen`, `SettingsInterestsCard`)

- [ ] Profile: display name, avatar edit
- [ ] Interests card edit → `InterestTaggingScreen` / inline editor
- [ ] Dark mode toggle
- [ ] Notification toggles: messages, calls
- [ ] Ghost mode toggle → map/chat behavior changes `[KNOWN-5]`
- [ ] Memory capsule sensor opt-ins (ambient noise, barometric height)
- [ ] Calendar permission + linked calendar features
- [ ] Open web dashboard link (`CLICK_WEB_BASE_URL`)
- [ ] Sign out
- [ ] `[UI]` All toggles `AdaptiveSwitch`; destructive confirms platform alerts
- [ ] App system settings deep link `[P] iOS` Settings app · `[P] Android` app info

---

## 16. Clicktivities & gamification (`ClicktivitiesScreen`, `ClicktivityCard`)

- [ ] Clicktivities list loads
- [ ] Activity card tap / navigation
- [ ] Stats / achievements presentation

---

## 17. Memories & profile media

- [ ] `MemoriesListSection` in profile
- [ ] Memory capsule data shows sensor metrics when captured
- [ ] Profile media vault read paths `[E2EE]`

---

## 18. Push notifications & background

- [ ] Message push tap → opens correct chat (`pendingChatId`)
- [ ] Call push → incoming call UI
- [ ] Incoming call: one alert per physical device (VoIP first; standard only if VoIP failed)
- [ ] Hub notification → opens hub
- [ ] `[P] iOS` Standard APNs + VoIP PushKit separate paths
- [ ] `[P] Android` FCM message and call channels — `[KNOWN-11]`
- [ ] `ChatNotificationDismisser` clears notification when thread read
- [ ] Notification preferences in Settings gate delivery

---

## 19. Offline, sync & connectivity

- [ ] `OfflineStatusBanner` when airplane mode
- [ ] Queued chat messages send on reconnect
- [ ] Offline proximity handshake queues; sync worker flushes `[P] Android` WorkManager
- [ ] Cached `AppDataManager` snapshot renders Home/Map/Connections offline
- [ ] Offline connection save snackbar after tap connect
- [ ] No data corruption after offline→online transition

---

## 20. Security & data layer (always verify)

- [ ] `ConnectionInsert` fields unchanged (user_id_1, user_id_2, location_id, context_tag, initiated_by, expires_at)
- [ ] No `Map<String, Any>` in repositories
- [ ] `@Serializable` on new models
- [ ] `redeem_qr_token` RPC unchanged
- [ ] Proximity score calculation unchanged
- [ ] QR token expiry (90s) unchanged
- [ ] Tap → `ConnectionInsert` still fires
- [ ] 30-minute Vibe Check expiry unchanged
- [ ] Keep/expire mutual opt-in logic unchanged
- [ ] `[P] iOS` No `Any` types in iosMain

---

## 21. Permissions & platform services

- [ ] Location — map, handshake, hub verify
- [ ] Camera — QR scan, disposable roll, chat photo
- [ ] Microphone — voice messages, calls, ultrasonic path `[KNOWN-7]` `[KNOWN-6]`
- [ ] Bluetooth / proximity — tap connect `[KNOWN-1]`
- [ ] Calendar — event reminders, overlap card
- [ ] Notifications — push registration `[KNOWN-11]`
- [ ] Photo library — save image, pick media
- [ ] Denied permission shows rationale / settings redirect (no crash)
- [ ] Overlapping permission requests do not stack OS dialogs (prime sheet FIFO)

---

## 22. Accessibility & input

- [ ] VoiceOver / TalkBack: buttons have content descriptions
- [ ] Dynamic type: critical screens remain readable
- [ ] Keyboard: IME doesn't cover composer send button
- [ ] `[P] iOS` Keyboard animation follows `KeyboardHeightProvider` / split dock
- [ ] Touch targets ≥ platform minimum on platform buttons

---

## 23. Business / waitlist / venue

- [ ] `WaitlistDialog` submit (if entry point exists)
- [ ] Venue QR with `venue_id` parameter parses correctly

---

## 24. Testing & internal screens

- [ ] `TestingScreen` (if enabled in build) doesn't break release paths
- [ ] `BirthdayPickerUiTest` parity on iOS date pickers (if present)

---

## 25. Post-change smoke (cross-ref)

Prefer the dedicated smoke doc for a timed pass: [02-smoke-10min.md](02-smoke-10min.md).

- [ ] Connect via QR → chat → send message → swipe-back to list
- [ ] Long-press connection → archive → find in Archived tab
- [ ] Drop beacon on map → see on map → open detail
- [ ] Join hub from map → send hub message → leave hub
- [ ] Hub message cooldown: after send, composer shows `Wait {N}s…` for 5s; rapid sends return 429 / restore draft
- [ ] Beacon detail sheet: RSVP, share, navigate, bookmark, labeled check-in CTA
- [ ] Event people directory: open Directory before RSVP/check-in → sort A–Z / Interests / Mutuals; relationship / sort-aware metrics show **N mutuals** (friends-in-common); list scrolls to top on chip change; no large blank band at full sheet height
- [ ] Event mutuals: “Mutuals here” section lists FoF; Connections show friends-in-common count; Mutuals not duplicated under Everyone
- [ ] Share to chat: opens with fade/scale enter (not an abrupt popup)
- [ ] Saved events detail: location/address visible on first open (no Map navigation required); Share shows Share link / Share to chat
- [ ] Non-event beacon detail (soundtrack / community): Share link / Share to chat available; soundtrack create persists track/artist/preview/art
- [ ] Nearby search field: typing does not compress the text box
- [ ] Beacon drop soundtrack URL: field stays **one row tall** and the caret is vertically centered in it. Root cause both prior times was the placeholder wrapping to two lines, which grows the decoration box past its 56dp single-line height and leaves the centered caret floating mid-field — plain hints must be passed as `ClickOutlinedTextField(placeholderText = …)`, not a `placeholder = { Text(...) }` slot
- [ ] Beacon drop photo is **optional for every category** (soundtrack, event, community, hazard, SOS, utility, study, social): submit with no photo succeeds and the beacon renders with its generated gradient on the pin, in lists, and in its detail sheet
- [ ] Beacon drop photo section shows two real bordered buttons (`Take photo` / `Photo library`), not highlighted text links; attaching one shows a thumbnail with Replace / Remove
- [ ] Home photo pile: availability pill + recap/insights/stats render outside the pile; one unified roughly-square stack (~half screen height, 3 visible layers with vertical peek and interleaved category markers); drag tracks finger 1:1; swipe past 200 dp or 800 dp/s throws off with velocity; swipe-down recalls last dismissed card; tap jiggles ±5° / 1.05× (no fan carousel); below threshold springs back; stacked layers show scale 1.0/0.95/0.90, elevation 16/8/4 dp, ±15° rest tilt; Reduce Motion replaces tilt/spring with a plain rest pose
- [ ] Settings → Saved events: tap a card → same event detail bottom sheet as Home (not a no-op)
- [ ] Map pin profile: Timeline / Beacons / Media / Links match the Clicks-list profile for the same connection (`TabbedUserProfileSheet`)
- [ ] Settings → My personality helper reads exactly `Pick exactly 5 traits.` (no login-gate sentence), on mobile and web
- [ ] Generated visuals: the same beacon shows the same gradient + pattern on its map pin, home pile card, Events/Explore tile, share-to-chat card, search row, profile Beacons row, and detail header — and the same on click-web
- [ ] Detail sheet headers show no duplicated text: the gradient band carries only a category chip, with title / schedule / location appearing exactly once in the section below
- [ ] Event schedule picker: opening date/time popup does not shift form layout; hour/minute tumblers snap to nearest option after scroll
- [ ] Birthday onboarding: typing digits auto-inserts dashes; calendar picker syncs with typed date
- [ ] Bottom sheets: from scroll top, slow drag follows the finger then springs back or commits; flick dismisses; mid-list downward drag scrolls first without same-gesture dismiss
- [ ] New connection context sheet: optional event recommendation card → RSVP or Dismiss
- [ ] Incoming call → accept → end
- [ ] Toggle ghost mode → map grayscales
- [ ] Global search → open result
- [ ] Settings → toggle notification → sign out → sign in

---

## Safety (block / report) — cross-ref ui-ux §16

Covered primarily under §6.4 and message delete §7.4. Spot-check:

- [ ] Block user from connection sheet → peer cannot message/call
- [ ] Report connection completes without crash
- [ ] Delete own message removes from thread for self

---

## NASA Power of 10 (engineering guardrails)

- Platform actuals ≤ 60 lines per function; extract helpers when needed
- No unbounded loops in gesture frame callbacks
- Fixed commit thresholds as named constants
