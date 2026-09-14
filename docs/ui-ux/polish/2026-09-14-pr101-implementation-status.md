# PR #101 implementation and acceptance status

Branch: `fix/pr99-acceptance-recovery-20260913`.

This completes the remaining deterministic implementation slices from the supplied PR #101 plan. Physical-device acceptance is still pending. The developer will run the app through Xcode/TestFlight; automated validation does not establish motion, gesture, keyboard, or visual quality.

## Implementation map

| Plan area | Production implementation |
| --- | --- |
| Server-confirmed presence | `MapViewModel.toggleBeaconCheckIn` uses `MapViewModelServerAuthoritativeCheckIn`; pending preserves confirmed presence, HTTP 409 remains failure, checkout waits for success, and legacy early-state persistence is neutralized. |
| Event hub authorization | `AppMainShell` reauthorizes hub opens, including cached/search/list/deep-link routes. RSVP cancellation invalidates cached access; physical checkout does not revoke it. Host access is resolved separately. |
| Chat startup | `HubChatViewModel` loads history and realtime independently after authorized navigation. `HubChatScreen` renders its shell and local retry/reconnect states. |
| Compact native chrome | `NativeChromeTransition`, `NativeHeaderMetrics`, and iOS host layers bind title opacity to destination progress, use a single compact title row, and preserve trailing native containers across action updates. The Me tab avatar remains unchanged. |
| Nearby gestures | `NearbyAnchoredSheet` owns the lip and nested list scrolling, tracks vertical displacement, and settles at collapsed/half/expanded anchors. Its content is bounded by the visible viewport and its surface sits above map controls. |
| Chat responsiveness and cleanup | Existing stable message IDs, reserved reaction rows, bounded avatars, and shared composer/bubble tokens are retained. Reaction overflow scrolls within its slot. Staged photos use asynchronous, constraint-sized [Coil loading](https://coil-kt.github.io/coil/compose/) rather than synchronous bitmap decoding during composition. |
| Search | Local results update immediately; remote work debounces 200 ms, cancels the previous job, and checks a monotonic query version before publishing. |
| Event detail hierarchy | Title/status, schedule/location, RSVP, check-in, people, and event chat precede description/host/secondary content. Location reserves two lines. Chat exposes preparing/open/retry/RSVP-required states. |
| Connect | Fresh Tap entry resets stale state. Screen disposal cancels handshake and pending-match polling and releases hardware; cancelled work cannot publish a late error or success. Completed context handoff remains intact. QR camera start/stop is serialized and follows active state. QR and Tap retain their existing shared result presentation. |
| Me | Compact 60 dp profile summary and inline Edit profile action; existing grouped settings rows and route identities remain. |
| Home / Updates | Recap uses identical stat rows during loading, empty, and populated states. This checkout has no standalone Updates route; its inbox nudges already have stable IDs and fixed row structure. |

Existing token-refresh coalescing and message/thumbnail caches remain in use. No backend deployment or migration was performed in this continuation. Event opens use click-web `/api/hub/join`; the earlier mobile-owned event hub verifier source alignment also remains part of this branch. Confirm deployed authorization behavior in the event-chat acceptance pass.

## Automated validation

Final local verification passed on 2026-09-14:

- JVM: 641 tests, zero failures/errors/skips.
- iOS simulator: 580 tests, zero failures/errors/skips.
- Android debug build and iOS device-target compilation: passed.
- Spotless and `git diff --check`: passed.

These totals include the final RSVP exception cleanup. Auth-refresh exceptions/cancellation now restore unconfirmed RSVP snapshots and always clear pending flags; RSVP success haptics follow server confirmation.

Commands:

```sh
./gradlew spotlessCheck
./gradlew :composeApp:testDebugUnitTest :composeApp:assembleDebug :composeApp:compileKotlinIosArm64
./gradlew :composeApp:iosSimulatorArm64Test
```

Spotless runs in a separate ordinary Git clone because its Git ratchet cannot resolve this linked worktree. Formatted source is synchronized back. Kotlin compiler execution uses `-Pkotlin.compiler.execution.strategy=in-process` to avoid a stale daemon's filesystem permissions. No test selectors are omitted from the final JVM or simulator suites.

New regression cases cover Nearby anchor/velocity settling, late search responses after clearing, cancellation during a handshake, pending polling teardown, and preservation of completed connection context.

## NEEDS PHYSICAL-DEVICE VALIDATION

- Before start, outside radius, auth failure, and network failure: no check-in success; 409 says “Check-in opens when the event starts”. Restart must preserve this result.
- Successful check-in/checkout: state and success haptics follow the response; failed checkout retains checked-in state.
- RSVP then open event chat through detail, list, search, notifications, and cold-start links. Cancel RSVP and retry each route; host remains eligible. Checkout with accepted RSVP retains access.
- Slow/failed history and realtime reconnect: chat shell remains open, with retry/reconnect feedback and retained messages.
- Push/pop, long titles, rapid navigation, and Reduce Motion: compact titles and glass controls stay aligned without flashes. Check the Me tab avatar.
- Nearby: slow upward drag, fast flick, downward collapse at list top, inner list scrolling, and map pan outside the sheet.
- Chat: cold/warm entry, immediate keyboard, back with keyboard visible, send, photos, reactions from another client, and scrolling during realtime updates.
- Search: rapidly type/delete/change queries; cached results should appear immediately and old remote results must not replace the current query.
- Connect: switch QR/Tap, deny/allow permissions, leave during preparation/listening/pending match, re-enter, and finish a connection. Camera/radios must stop on exit and completed context must survive its navigation handoff.
- Home and inbox nudges: cold/warm/slow-network rendering; recap hydration must not move subsequent sections. Review event/chat/Me spacing and accessibility text sizes.

Do not mark the PR accepted or merge it until the developer completes the applicable manual checklist and supplies the repository-required visual evidence.
