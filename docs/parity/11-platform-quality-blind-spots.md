# 11: Platform and quality gaps no sheet lists

**Status:** Plan · **Date:** 2026-10-10 · Index: [09](09-parity-audit-2026-10-10.md)

These came from mapping every `click-ios/Docs/PARITY_LEDGER.md` row (F01–F127) and every iOS feature folder against sheets 00–08 and Android `main @ b461f6b4`. Every ledger row maps to a sheet row, is iOS-only, or is one of the items below.

| ID | Item | Size (days) | Priority | Phase |
|---|---|---|---|---|
| B8 | Phantom Instant activity in the manifest | 0.25 | **P0** | P0 |
| B1 | Accessibility pass (ledger F108) | 4–5 | P1 | P8 (rule applies from P0) |
| B2 | Unread badge on the Clicks tab | 0.5 | P1 | P1 |
| B6 | Transport timeouts and retry (ledger F52) | 0.5–1 | P2 | P1 |
| B3 | Paste an image or GIF into the composer | 0.5–1 | P2 | P6 |
| B4 | Login autofill semantics | 0.5 | P2 | P8 |
| B5 | Audio focus for voice notes | 0.5 | P2 | P6 |
| B7 | Haptic vocabulary | 0.5–1 | P3 | P8 |
| B9 | Connection debug log (debug builds) | 0.5 | P3 | P0 (helps the device matrix) |
| B10 | Visual QA: tab bar on push/pop, equal inbox rows, Home entrance | 0.5 | P3 | P8 |

---

## B8. Phantom Instant activity (P0)

- **Problem:** `AM/AndroidManifest.xml:187-210` declares an **exported** `.instant.InstantProfileActivity` with `autoVerify` filters on `/connect` and `/c`. **The class doesn't exist** (no file under `composeApp/src`; it came in with `d3e4b31b`).
- **Effects:**
  - It duplicates MainActivity's `/c` claim (08 G0-6).
  - Once App Links verify, a resolver that picks it will crash with `ClassNotFoundException`.
- **Fix:** delete the `<activity>` block in the G0-6 PR. 08 G0-6 says "InstantProfileActivity routes through MainActivity". **Correct that to "remove"**: there is nothing to route.
- **Acceptance:**
  - `aapt dump xmltree` (or the merged manifest) has no `instant` activity.
  - `adb shell pm get-app-links compose.project.click.click` shows one owner for `/c`.

## B1. Accessibility pass (P1)

- **iOS:** about 130 `accessibilityLabel`, 52 `accessibilityElement` groupings, 48 hidden decorations, 41 traits, Dynamic Type clamps, and `UIAccessibility.post` announcements (ledger F108).
- **Android today:**
  - 189 `contentDescription = null` against 105 labelled.
  - 4 `semantics {}` blocks.
  - No font-scale handling.
  - No sheet mentions TalkBack.
- **Work:**
  1. Add a rule to the per-row definition of done (09 §3.3): new interactive elements carry labels or roles.
  2. Audit the top 10 screens: Home, Clicks, chat, Map, Nearby, event page, Add Click, profile, Me, Settings.
     - `Modifier.semantics(mergeDescendants = true)` on rows.
     - `Role.Button` / `Role.Tab`.
     - `stateDescription` for toggles.
     - `liveRegion = Polite` for send status, "typing…" and scan results.
     - `contentDescription = null` only on true decoration.
  3. Port the iOS strings where they exist (for example the M1 "{lead} and {n−1} more" / "Shows everything here", and the D4 "develops {relative}. Opens it.").
  4. Font scale: check that layouts survive 200% (`fontScale` 2.0) on a 360 dp device. Clamp only chrome (tab labels, pills), never body text.
- **Acceptance:**
  - The Play Console pre-launch report shows no accessibility errors.
  - A TalkBack walk of the 10 screens is recorded in `docs/regression-testing/`.

## B2. Unread badge on the Clicks tab (P1)

- **iOS:** `iOS/App/RootGateView.swift:169`, `.badge(conversations.unreadTotal)`.
- **Android:** `AM/ui/components/BottomBar.android.kt` has no badge.
- **Fix:**
  - Put a `BadgedBox` on the Clicks item, fed from the inbox ViewModel's unread sum (exclude muted chats if iOS does; check `ConversationListModel.unreadTotal`).
  - Show "99+" above 99.
  - TalkBack: "Clicks, {n} unread".

## B3. Paste an image or GIF into the composer

- **iOS:** `iOS/Features/Chat/ChatMediaViews.swift:704,789-795` (pasteboard images and GIFs).
- **Android:** no `contentReceiver` anywhere.
- **Fix:** `Modifier.contentReceiver` on the composer `BasicTextField` for `image/*`. It stages the content into the existing attachment tray. This also enables keyboard GIF and sticker insertion (Gboard), with no extra work.

## B4. Login autofill semantics

- **iOS:** `AuthView.swift:88,104` and `ProfileBasicsGateView.swift:39,44` set the content types.
- **Android:** `LoginScreen.kt:118,160` sets only the keyboard type.
- **Fix:** `Modifier.semantics { contentType = ContentType.EmailAddress / Password / NewPassword / PersonFirstName / PersonLastName }`. Optionally, offer a Credential Manager password save after sign-up.

## B5. Audio focus for voice notes

- **iOS:** `iOS/Core/Media/AudioSessionController.swift:19-22` (separate playback, recording and measurement modes).
- **Android:** `AM/media/ChatAudioPlayer.android.kt` plays with a bare `MediaPlayer`, so music keeps playing over voice notes.
- **Fix:** request `AudioFocusRequest(AUDIOFOCUS_GAIN_TRANSIENT)` for playback and recording. Pause on loss and abandon focus on stop. Use `USAGE_VOICE_COMMUNICATION` for recording.

## B6. Transport hardening (ledger F52)

- **iOS:** `iOS/Core/API/ClickAPIClient.swift:53-61,154` sets request timeouts and one retry for idempotent requests on transient errors.
- **Android:** `KMP/data/api/ApiClient.kt:56-75` installs neither `HttpTimeout` nor `HttpRequestRetry` (only the QR and soundtrack clients do).
- **Fix:**
  - `HttpTimeout`: request 15 s, connect 10 s.
  - `HttpRequestRetry`: GET/HEAD only, once, on `IOException` or 502/503/504, with a short delay.
  - **Never retry POSTs** (sends, checkouts, scans).
  - Cancellation is not failure: rethrow `CancellationException` everywhere (ledger F51; today only about 4 files do).
- **Test:** a MockEngine test, where a 503 on GET is retried once and a 503 on POST is not.

## B7. Haptic vocabulary

- **iOS** (`iOS/DesignSystem/Haptics.swift`): selection ×55, success ×55, impact ×34, error ×17, warning ×7.
- **Android:** `PlatformHapticsPolicy` has only light, heavy and success.
- **Fix:**
  - Add `selection` (`SEGMENT_TICK` / `CLOCK_TICK`), `warning` and `error` (`REJECT` on API 30+, otherwise a short double pulse).
  - Map the call sites the specs name: scanner results (T7, G3-16), stack tap (M1), pickers, toggles.

## B9. Connection debug log (debug builds only)

- **iOS:** `iOS/Core/Debug/ConnectionDebugLog.swift:67` (`-connection-log`) keeps a copyable log of each connect attempt's requests, for in-person testing.
- **Android:** no equivalent.
- **Fix:**
  - A ring buffer (200 entries) of connect-flow requests and responses with tokens redacted, in `BuildConfig.DEBUG` only.
  - A "Copy log" action on the Add Click screen.
  - This makes the 08 G8-1 in-person matrix much faster to debug.

## B10. Visual QA items (ledger F62, F109, F115, F120)

These are visual-only checks during P8:
- the tab bar fades on push and pop
- inbox rows have equal height
- Home doesn't jump on entrance

No spec. Fix whatever the side-by-side check finds.

---

## Re-checks of sheet 08 (main @ b461f6b4)

| 08 claim | Result |
|---|---|
| G1-1, G3-15/16, G5, G6-1, G6-8/9, G1-10/G7-6, G7-2, G1-4 missing | Still true (0 hits for each) |
| G0-3 typing mismatch | True: `SupabaseChatRepository.kt:122-124`; stop never sent (`:589`) |
| G4-10 PostgREST inserts | True: `ConnectionRepositoryCreate.kt:239-240,385,616`; `ConnectionRepositoryEncounters.kt:91` |
| G0-6 assetlinks placeholder | True: `web/public/.well-known/assetlinks.json:6-7`. Plus B8. |
| G0-11 line refs | Drifted: the write is at `ChatViewModelTimeline.kt:605-606` |
| G0-13 no de-dup | True: `SupabaseChatRepositoryCrypto.kt:529` |
| **G3-23 "port MarkdownText"** | **Overstated.** Android already has `KMP/ui/components/EventMarkdownText.kt`, used at `EventBeaconDetail.kt:550`. Re-scope to "align with iOS/web": check list numbering, `>` quotes without a space, strikethrough, and the http/https/mailto link allowlist. Size S. |
