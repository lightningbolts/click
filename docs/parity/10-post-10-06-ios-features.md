# 10: iOS #68–#80, Android port specs

**Status:** Plan · **Date:** 2026-10-10 · **iOS range:** `093ed67..b1177d1` (PRs #68–#80) · **Web:** `e9fb51a..993a0f0` · Index: [09](09-parity-audit-2026-10-10.md)

None of this is on Android `main`. Each section gives:
- the rules, with iOS copy quoted exactly
- the backend contract
- iOS references
- current Android status
- the Android approach
- the size
- the tests to port

Conventions follow the [README](README.md).

## 0. Summary

| § | Feature | iOS PR | Android status | Size (days) | Phase |
|---|---|---|---|---|---|
| A1 | Owner authentication before approving a device | #73, #75 | Missing | 1–1.5 | P1 |
| A2 | Approved immediately, history shared in the background | #75 | Partial (worktree `devices`) | 0.5 | P1 |
| A3 | Locked history unlocks by itself | #71 | Missing | 1.5 | P1 |
| A4 | Duplicate approval notifications collapse | #72 | Missing | 0.5 | P1 |
| A5 | Settings › Devices: "Waiting for approval" section and copy parity | #79 | Partial (worktree `devices`) | 1–1.5 | P1 |
| A6 | `device_label` rule vs. web passkey recovery | web #150 | Check only | 0.1 | P1 |
| T1–T7 | Ticketing, buyer side | #74, #77; web #148, #153 | Missing | 10–12 | P5 |
| D1 | Rolling-day drop strip | #78 | Partial (old rules, worktree `drops`) | 0.5 | P2 |
| D2 | Generic `DropStoryViewer` | #78 | Missing | 2 | P2 |
| D3 | Event recap grid and story | #78 | Missing | 3 | P2 |
| D4 | Pending drops open with a countdown | #80 | Missing | 1–1.5 | P2 |
| H1 | Home "Upcoming" and View all | #69 | Missing | 2 | P4 |
| H2 | Home "Recommended for you" | #69, #70 | Missing | 2 | P4 |
| H3 | Core ring (avatar) at every call site | #69, #70 | Partial | 2 | P4 |
| H4 | Profiles of people you haven't Clicked with | #70 | Unverified | 0.5 | P4 |
| H5 | Nudge layout | #70 | Partial | 0.5 | P4 |
| M1 | Luma-style map stacks | #69 | Partial (old clustering) | 3–4 | P4 |
| M2 | Place events merge into Place pins | #68 | Missing (needs G5) | 0.5 | P7 |
| M3 | Explore chips with face piles; New Event type chips | #69 | Missing | 1 | P4 |
| E4 | Flyer share without the caption for Instagram | #68 | Missing (with G3-24) | 0.5 | P4 |
| E5 | Event picture in the ongoing notification; dismissal survives updates | #75, #76 | Missing (with G3-17) | 0.75 | P3 |
| **Total** | | | | **≈ 38–45** | |

**Changes to sheet-08 rows**

| Row | Change |
|---|---|
| G3-17 | Adds E5 (artwork, delete-intent-only dismissal). Feeds from the same `myEvents` source as H1. |
| G3-20 | Clustering is replaced by M1. |
| G3-21 | NearbyRow is reused in the M1 stack sheet. Lists keep Place-hosted events (M2). |
| G3-24 | Adds E4. |
| G5 | Place-pin merge rule (M2). |
| G7-1 | Adds A1–A4. |
| G7-6 | Adds A5. |
| G2-3, G2-4, G2-6 | Superseded by D1–D4. |

---

## A. Account and devices

### A1. Owner authentication before approving (iOS #73)

**Rules** (`iOS/Features/Chat/DeviceApprovalViews.swift:147-192`)
- **When it asks:** tapping **Approve** asks for owner authentication **every time**. **Deny** needs none.
- **Policy:** `.deviceOwnerAuthentication`, meaning biometrics with a passcode fallback.
- **Prompt reason:** "Confirm that you want to share Click message history with this device."
- **No owner authentication possible** (no screen lock): fail closed with "Device authentication is required to approve this request."
- **Cancelled** by the user or the system: go back to the asking state, with no error and nothing sent.
- **Account check:** after authentication, if the signed-in user ID changed since the sheet opened, treat it as a cancel.
- **Other authentication errors:** "Device authentication is required to approve this request."
- **Network errors:** "Couldn't reach Click. Check your connection and try again."

**Android status:** Missing. `wt/android-devices` `chat/DeviceApproval.kt` and `DeviceApprovalViewModel.kt` approve without authentication.

**Approach**
- **commonMain:** `expect suspend fun authenticateDeviceOwner(reason: String): OwnerAuthResult`, with the results `Success`, `Cancelled` and `Unavailable`.
- **androidMain:** `androidx.biometric` `BiometricPrompt`.
  - **API 30+:** `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`.
  - **API 24–29:** `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`. androidx falls back to `KeyguardManager` on ≤28.
  - Before prompting, `BiometricManager.canAuthenticate(...) != BIOMETRIC_SUCCESS` gives `Unavailable`.
  - `ERROR_USER_CANCELED`, `ERROR_CANCELED` and `ERROR_NEGATIVE_BUTTON` give `Cancelled`.
- **Gotcha:** `BiometricPrompt` needs a `FragmentActivity`, but `MainActivity` extends `ComponentActivity` (`AM/MainActivity.kt:38`). Either switch the base class (check Compose and edge-to-edge setup still work) or host the prompt in a small transparent `FragmentActivity`.
- **Ordering (#75):** start the challenge fetch with `async {}` while the prompt is showing. Post the proof only after `Success` and the user-ID re-check.

**Tests (new commonTest):** Cancelled goes back to Asking; Unavailable shows the copy; a user switch never posts; the challenge is fetched in parallel but the decision is never posted before authentication.

**Size:** 1–1.5 days.

### A2. "Approved" right away (iOS #75)

- **Rule:** the sheet shows "Approved" as soon as the decision POST succeeds. `shareHistoryWithApprovedDevices` runs **detached**, and the next foreground's `syncDeviceHistory` picks up anything left (`ChatRepository.swift:644`, `AppEnvironment.swift:479`).
- **Android:** `wt/android-devices` sets `Phase.Approved` first, but runs the share in the ViewModel's own scope, so it is cancelled when the dialog closes. Move it to `applicationScope`.
- **Size:** 0.5 day.

### A3. Locked history unlocks by itself (iOS #71)

**Rules**
- **Locked rows:** a row is locked history when its content is `"Encrypted message unavailable on this device"` (`iOS/Core/Chat/ChatMessage.swift:124`).
- **Retries:** after each successful message load, if the chat is **visible** and any row is locked, reload with force after **2, 5, 10, 20 s** (at most 4 attempts). The counter resets when nothing is locked, or when the chat disappears; disappearing also cancels the retry.
- **Cooldown:** the per-chat session-refresh cooldown for shared history drops from **60 s to 2 s** (`ChatRepository.swift:484`).
- **iOS refs:** `ConversationModel.swift:72`, `:451`, `:466-483`.

**Android status:** Missing. Rows that can't be decrypted render as a generic "New message" (`KMP/data/repository/SupabaseChatRepositoryCrypto.kt:616-700`), and there is no locked-history marker.

**Approach**
- Add a `lockedHistory` flag on the message model, kept separate from the "New message" placeholder.
- Add a visible-only retry coroutine in `ChatViewModel` with that schedule.
- Invalidate that chat's V2 epoch cache with a 2 s throttle.
- **Copy:** use iOS's string for the locked row.

**Tests:** a scheduler test with a fake clock (2/5/10/20, reset, cancel on stop).

**Size:** 1.5 days.

### A4. Duplicate approval notifications collapse (iOS #72)

- **Rule:** keep one notification per `request_id`, the newest. A decision removes that request's notifications. Different requests are never merged (`iOS/App/ClickApp.swift:65,115,338`).
- **Android approach:** post `device_approval` notifications with tag `"device_approval:{request_id}"` and id 0. A same-tag notify replaces the old one, so no reconcile pass is needed. After a decision, call `NotificationManagerCompat.cancel(tag, 0)` through an expect/actual. Today they go through the chat path with `chatNotificationTag(messageId)` (`AM/.../ClickFirebaseMessagingService.kt:154-204`), so they stack.
- **Size:** 0.5 day. **Test:** a tag-builder unit test.

### A5. Settings › Devices, iOS parity (iOS #79)

**Rules** (`iOS/Features/Me/DevicesSettingsView.swift`)

- **Entry:** Me › Settings row "Devices", placed above Permissions.
- **Loading:**
  - `GET /api/me/devices` and `GET /api/chat/devices/history-requests?device_id=` load in parallel. If the approvals call fails, the device list still shows.
  - Reload on appear, on pull-to-refresh, when coming back online with no list yet, and when the review sheet is dismissed.
- **"Waiting for approval" section** (only when `incoming` is non-empty):
  - Row: "{A/An label} wants your chat history", with the subtitle "Signed in {relative}" (or "Signed in recently") and the trailing text "Review".
  - Footer: "Only approve a sign-in if it's you."
  - The section is disabled while offline.
  - **Review** opens the A1 sheet and **defers the global approval prompt** for that request, so it doesn't pop up twice. A decision removes the row.
- **Device list:**
  - Order: this device first, then the server's order.
  - Name: the label, else the device model (this device only), else "Unnamed device".
  - Subtitle: "Active {relative}", else "Added {relative}".
  - This device shows a "This device" capsule. Other devices get a destructive **Remove** button plus a swipe action, both disabled offline.
- **Icons:**
  - a label containing "ipad": tablet
  - a browser label ("Web browser", or contains " on "): laptop, except when it ends " on iphone" or " on android", which gets the phone icon
  - anything else: phone
- **Empty state:** "No chat devices yet" / "Devices show up here once you open a chat on them."
- **Error states:**
  - "Couldn't load your devices" / "Check your connection and try again."
  - Offline: "You're offline" / "Your devices show up here once you're back online."
  - Both use the button "Try Again".
- **Footer:**
  - "Messages are end-to-end encrypted, and each device holds its own key. Removing a device stops it reading new messages; it doesn't sign it out."
  - While offline, add "You're offline. Changes wait until you're back."
- **Remove dialog:**
  - Title: "Remove {label ?? "this device"}?"
  - Message: "It stops getting new messages until it's approved again. It stays signed in until it signs out."
  - Buttons: "Remove" and "Keep Device".
- **Remove behaviour:** optimistic. On failure, put the row back and show "Couldn't remove {label ?? "that device"}. {error}". The client never removes its own device ID.

**Android divergences to fix** (in `wt/android-devices` `SettingsDevicesPage.kt` / `DevicesSettingsViewModel.kt`):

| Android today | Change to |
|---|---|
| "Signed-in devices" header | Remove it |
| "Last active" | "Active" |
| "Unknown device" | "Unnamed device" |
| Footer says removal "signs it out of secure messages" | iOS footer (removal doesn't sign the device out) |
| "Cancel" | "Keep Device" |
| No incoming section | Add the "Waiting for approval" section |
| No optimistic rollback | Optimistic remove with rollback |
| No offline or empty states | Add both |
| No "Added" fallback | Add it |

**Tests:** port `Tests/ClickTests/ChatDevicesTests.swift`. Parse rows, skipping any without an ID, and accept null label and `last_seen_at`. Icon cases: iPhone, iPad, "Chrome on Mac", "Safari on iPhone", "Web browser", plus "Chrome on Android" → phone. Also test that this device sorts first.

**Size:** 1–1.5 days.

### A6. `device_label` must never look like a browser (web #150)

Web skips the approval request for **browser** labels when the account has a passkey recovery vault (`web/lib/chat/deviceLabel.ts:43`). Android's label (`DeviceLabel.android.kt`, manufacturer + model) must therefore never contain " on " and never equal "Web browser". Add a unit test that asserts this for the mapping table. **Size:** 0.1 day.

---

## T. Ticketing, buyer side (iOS #74, #77; web #148, #153)

Organizer tools (tiers, sales, refunds, cancel, payouts) are **web-only on every platform**. Nothing here sends a push.

### T1. Payment model and policy

- **How payment works** (`web/docs/ticketing.md`, `web/lib/server/ticketing/checkout.ts`):
  - Stripe Connect destination charges, with a 5% application fee that the **organizer absorbs**. Buyers see "Fees None".
  - **Stripe-hosted Checkout only.** Sessions last 30 min, inventory is held for 32 min, and `success_url`/`cancel_url` point to `/e/{id}/tickets/return?order={id}[&canceled=1]`.
  - Fulfillment happens only through the webhook.
  - There are no transfers or resale.
- **Google Play:** tickets to **in-person** events count as a physical service, which Play's Payments policy exempts from Play Billing. Stripe Checkout in a Custom Tab is allowed. **Confirm against the live policy page before launch.** If Click ever sells tickets to online-only events, those must be hidden on Android or sold through Play Billing.
- **Data safety (G7-4):** declare purchase history. Payment details are entered on Stripe's page; the app never sees them.

### T2. Gating

- **Sales:** gated by the `TICKETING_ENABLED` env var. When it is off, sales routes return 403 `{code:"ticketing_disabled"}`.
- **Wallet:** `ticket_wallet` is on when sales are on, **or** when the user holds any ticket.
- **Flags:** `/api/me/features` exposes `ticket_sales:{enabled}` and `ticket_wallet:{enabled}`. Add both to `FeatureKeys` (they are missing from `wt/android-foundations` `FeatureFlags.kt`).
- **Event payload:** `beacon.ticketing` is null unless sales are enabled **and** `admission_type == 'ticketed'`.
- **iOS #77 behaviour:**
  - Me shows "Tickets" only when `ticket_wallet` is enabled (`iOS/Features/Me/MeView.swift:280`).
  - A 403 maps to `ticketing_disabled`.
  - When the error code is `not_found` or `ticketing_disabled` (`isUnavailable`), the wallet shows its **empty state** and refreshes the flags. It doesn't show an error.

### T3. Contracts (Bearer or cookie)

| Method · path | Gate | Request | Response / errors |
|---|---|---|---|
| GET `/api/beacons/{id}` | — | — | `beacon.ticketing: {status: draft\|ready\|sales_open\|sales_paused\|sales_closed, cancelled, from_amount (minor units, null when none), currency, available, my_ticket_count}` or null |
| GET `/api/beacons/{id}/tickets/tiers` | sales | — | `{tiers:[{id, name, description, unit_amount, currency, availability: on_sale\|sold_out\|not_started\|ended\|paused, remaining (only when ≤10), max_quantity, sales_start_at, sales_end_at}]}` |
| POST `/api/beacons/{id}/tickets/checkout` | sales | `{items:[{ticket_tier_id, quantity 1..20}] (1..5)}`. **Omit `client`** until TW-1 (`"android"` returns 400 today). | Free: `{order_id, status:"fulfilled"}`. Paid: `{order_id, checkout_url}`.<br>400: `invalid_items`, `mixed_order`, `duplicate_tier`.<br>404: `event_not_found`, `tier_not_found`.<br>409: `event_cancelled`, `sales_not_open`, `sales_not_started`, `sales_ended`, `organizer_not_ready`, `tier_inactive`, `currency_mismatch`, `price_changed`, `over_order_limit`, `over_user_limit`, `insufficient_inventory` (+`remaining`), `not_free`.<br>502: "Checkout failed". |
| GET `/api/orders/{id}` | sales | — | `{order:{id, beacon_id, currency, subtotal_amount, total_amount, order_state, fulfillment_state, checkout_expires_at, paid_at, created_at, platform_fee_amount, ticket_count}}`. 404 also for someone else's order. |
| DELETE `/api/orders/{id}` | sales | — | `{order_state:"canceled"}` releases the holds. Refused once paid. |
| GET `/api/beacons/{id}/tickets` | wallet | — | `{tickets:[{id, beacon_id, order_id, status: valid\|checked_in\|refunded\|void, tier_name, ticket_number, checked_in_at, credential_url, code}]}`. The credential is present only for valid or checked-in tickets. |
| GET `/api/me/tickets?scope=upcoming\|past` | wallet | — | `{groups:[{event:{beacon_id, title, start_at, end_at, timezone, location_name, image_url, visual_seed, cancelled}, tickets:[…]}]}`. Undated events go in Upcoming. |
| POST `/api/beacons/{id}/pass/scan` | manager | `{credential}` or `{ticket_id}` | adds `result: refunded\|event_cancelled` and `tier_name` |
| DELETE `/api/beacons/{id}` | — | — | **New** 409 `has_ticket_orders`: "This event has ticket orders. Cancel it instead." Android's delete-event flow must map this. |

**Removed routes:** `POST /tickets/check-in` and `?include_credential=1`. `GET /pass` now returns an RSVP pass only for RSVP attendance, not for ticket holders.

### T4. Event page button and picker (`TicketButton.swift`, `TicketPickerModel.swift`, `TicketPickerSheet.swift`)

**Button** (replaces RSVP on ticketed events)
- **Label:**
  - "Event cancelled" (disabled) when the event is cancelled.
  - Available: "Get tickets", "Claim free tickets" (`from_amount` = 0), or "Get tickets · from $12".
  - Unavailable, by status: "Sold out" (`sales_open`), "Sales ended" (`sales_closed`), "Sales paused" (`sales_paused`), otherwise "Not on sale yet". These draw as a status capsule, not a dead button.
- **Cancelled banner:** "This event was cancelled. Paid tickets are being refunded."
- **Pass card:** "Your tickets · {n}" when `my_ticket_count > 0`. Otherwise an RSVP from before tickets went on sale keeps "Your Click Pass".

**Picker**
- **Header:** title "Tickets", with a "Close" button.
- **Row detail:** "{price}" or "{price} · {n} left". Otherwise "{price} · Sold out" / "· Sales ended" / "· Sales paused" / "· On sale {MMM d}" / "· Not on sale yet".
- **Stepper:** can increase only while the tier is `on_sale`, the quantity is below `max_quantity`, and no submit is in flight. Picking a **free** tier clears paid tiers, and the other way round. When both kinds exist, show "Free and paid tickets are separate orders."
- **Accessibility:** "Fewer {name}" / "More {name}".
- **Summary:** "{q} × {name}", then "Fees None", then "Total".
- **CTA:** "Get tickets" (disabled), "Claim free ticket(s)", or "Checkout · $X.XX".
- **Load failure:** "Tickets didn't load" + message + "Try again".
- **Submit:**
  - A coded refusal sets a notice, refreshes the tiers, keeps only lines that are on sale, and clamps each to `max_quantity`.
  - Network errors keep the selection.
  - A second tap is ignored.
- **Money:**
  - 0 shows as "Free"; anything else uses 2 decimals.
  - The "from" price drops ".00".
  - An unknown `availability` value decodes as `paused`.

**Error copy** (`iOS/Core/Beacons/Ticketing.swift:193-220`)

| Code | Copy |
|---|---|
| network | "Check your connection and try again." |
| price_changed | "The price changed. Check the new total." |
| sales_ended, sales_not_open | "Sales for this event have closed." |
| sales_not_started | "Sales haven't started yet." |
| over_user_limit | "You've reached the ticket limit for this event." |
| over_order_limit | "That's more tickets than one order allows." |
| tier_inactive, tier_not_found | "That ticket isn't available anymore." |
| event_cancelled | "This event was cancelled." |
| organizer_not_ready | "Tickets aren't on sale yet." |
| mixed_order | "Get free and paid tickets in separate orders." |
| ticketing_disabled | "Tickets aren't available right now." |
| insufficient_inventory | "Only {n} left. We updated your selection.", or "Sold out." when n ≤ 0 |
| 401 | "Sign in to continue." |
| other | the server's `error`, else "Something went wrong. Try again." |

iOS uses typographic apostrophes (’). Copy the exact characters from the Swift source.

### T5. Checkout and confirmation

**Android flow** (it differs from iOS's `ASWebAuthenticationSession`)
1. **Open:** open `checkout_url` in a **Custom Tab** (`androidx.browser`). Google Pay shows up inside Stripe's page by itself.
2. **Return:** **don't depend on the return URL.** `assetlinks.json` doesn't verify (W-1), and Chrome may not hand the post-payment redirect to the app even after it does.
   - Keep the `order_id`.
   - When the activity resumes, call `GET /api/orders/{id}` once.
   - If the order is pending or paid, start confirmation polling.
   - If it is still reserved or `checkout_created` with no payment, treat it as **canceled**: call `DELETE /api/orders/{id}`, keep the selection and refresh.
3. **Router:** teach `AppDeepLinkRouter` to recognise `/e/{id}/tickets/return?order=` (today it matches the event route), for when App Links work.

**Order outcome mapping**
- paid, partially refunded or disputed → `confirmed` if fulfilled, otherwise `pending`
- refunded → `refunded`; canceled → `canceled`; payment_failed → `failed`; expired → `expired`
- anything else → `pending`

**Polling** (`OrderConfirmationModel`)
- Every **1 s** for the first 10 s, then every **3 s**. Give up at **60 s** with `stillConfirming`.
- 404 or 403 stops polling at once. Other errors are retried.
- Free claims skip polling.

| State | Title | Message | Button |
|---|---|---|---|
| confirming | "Confirming your order…" | "This usually takes a few seconds." | — |
| confirmed | "You're in" | "Your ticket is ready." / "Your {n} tickets are ready." | "See your ticket(s)" |
| canceled | "No charge was made" | "You left checkout before paying." | "Try again" |
| failed | "Payment didn't go through" | "No charge was made. You can try again with another card." | "Try again" |
| expired | "Checkout timed out" | "Your tickets were released and no charge was made." | "Try again" |
| refunded | "Your payment was refunded" | "We couldn't issue these tickets, so the charge was reversed in full." | "Done" |
| stillConfirming | "Still confirming…" | "Your payment is still processing. Your tickets will show up on the event page as soon as it clears." | "Done" |

### T6. Ticket pass and Me › Tickets

**Pass screen.** This extends the G3-15 Click Pass screen in `wt/android-events`.
- **Which screen shows:** tickets if there are any, else the RSVP pass, else "noTickets" (on a ticketed event) or "noPass", else the failed or loading state.
- **Title:** "Your Ticket" / "Your Tickets", or "Click Pass".
- **noTickets state:** "No tickets yet" / "Get tickets on the event page and they show up here." with a "View Event" button.
- **Layout:** a horizontal pager ("{i} of {n}") of cards. Each card has a QR on a white tile (max 300 dp), the tier name and the ticket number.
- **Per-ticket display:**
  - cancelled event: "Event cancelled" / "Tickets for this event no longer admit anyone."
  - valid without a credential: "No longer valid" / "This ticket can't be used."
  - checked in: "You're in" / "Checked in at {time}."
  - refunded: "Refunded" / "This ticket was refunded."
- **Staleness line** after 60 s: "Updated {m} min ago" / "{h} hr ago" / "{MMM d}".
- **Order details:** Ticket, Type, and Order (the first 8 characters of the order ID, uppercase).
- **Footer:** "Your host scans this at the door. Each ticket admits one person."
- **Behaviour:**
  - Brightness and keep-awake (G3-29).
  - Reload on foreground.
  - Cache per user, per event and per scope (keys `events.tickets`, `events.ticketWallet`), so it works offline. Wipe the cache at sign-out.
- **Wallet button:** hidden on Android until W-7/TW-2. **Never download `.pkpass`.**

**Me › Tickets** (`TicketsView.swift`)
- **Visibility:** the row exists only while `ticket_wallet` is on.
- **Tabs:** a segmented "Upcoming" / "Past" control. Upcoming is soonest first; Past is most recent first; undated events go last.
- **Row:** a 50 dp picture, the title, then "{EEE MMM d h:mm} · {place}" **in the event's own time zone**. A "Cancelled" capsule when cancelled. "{n} ticket(s)", plus " · {tier}" when every ticket has the same tier.
- **Empty states:**
  - Upcoming: "No tickets yet" / "Events you get tickets for show up here."
  - Past: "No past tickets" / "Tickets for events that have ended show up here."
- **Error:** "Couldn't load your tickets" + "Try Again".

### T7. Scanner additions

| `result` | Title | Detail | Haptic |
|---|---|---|---|
| `refunded` | "Refunded" | "This ticket was refunded." | error |
| `event_cancelled` | "Event cancelled" | "Tickets for this event no longer admit anyone." | error |

- Show `tier_name` under the holder's name.
- Unknown results decode as `invalid`.
- Extend `PassScanResult` in `wt/android-events` `events/ClickPass.kt`.

### T: Approach, size and tests

**Approach**
- **commonMain:** `TicketingModels.kt`, `TicketingApi.kt` and `TicketingRepository`, which uses the G1-5 `UserDiskCache`.
- **ViewModels:** `TicketPickerViewModel`, `OrderConfirmationController` (inject the clock and sleep) and `TicketsViewModel`.
- **androidMain:** the Custom Tab launcher.

**Size**

| Piece | Days |
|---|---|
| Models, API and error map | 1.5 |
| Button and picker | 2 |
| Checkout and confirmation | 2 |
| Pass pager and cache | 2 |
| Me › Tickets and gating | 1 |
| Scanner | 0.5 |
| Tests | 1.5–2 |
| **Total** | **10–12** (Google Wallet adds 1–2 after W-7) |

**Tests to port** (`click-ios/Tests/ClickTests/`)

| File | Covers |
|---|---|
| `TicketingTests.swift` | decode, availability fallback, the outcome table, the error copy (including the 403), `isUnavailable`, wallet grouping and sorting, per-user cache |
| `TicketPickerTests.swift` | clamps, sold out, totals and CTA, free vs paid exclusivity, refresh on refusal, network keeps the selection, double tap, the button state table |
| `TicketCheckoutTests.swift` | the polling schedule, 404 stop, retried polls, ending copy. Swap the return-URL parsing cases for resume-poll cases. |
| `TicketPassTests.swift` | display per status, the staleness line, screen precedence |
| `EventDetailTests.swift:7-24` | scan decode |

**Acceptance:** a Stripe **test-mode** purchase on Android ends with "You're in". The ticket shows offline. An iOS host scans it as `checked_in`, and a second scan says `already_checked_in`. After an organizer refund on web, a scan says `refunded`.

---

## D. Click Drops v3 (iOS #78, #80)

These rows supersede the strip, viewer and archive behaviour that `wt/android-drops` implements (iOS as of #67). **Land that worktree first** (sheet 12), then apply D1–D4.

### D1. Rolling-day strip (`iOS/Core/Media/SharedDrops.swift:83-107`)

- **Window and cap:** 24 h (86,400 s), at most **5 drops per person**.
- **Grouping:**
  1. Sort every drop newest first, and drop any without `createdAt`.
  2. Skip drops older than now − 24 h.
  3. A person's stack position is set by their first (newest) appearance; keep only their first five drops.
  4. Each stack plays **oldest first**.
  5. Stacks are ordered by their newest drop. The old "mine first" and "unwatched first" rules are **gone**.
- **Redraw:** `nextExpiry` = the oldest drop's `createdAt` + 24 h. Sleep until then + 0.5 s and redraw.
- **Snapshot:** an open story keeps the people and drops it opened with.
- **Overflow drop:** a drop that isn't on the strip (past someone's five) opens in the archive playlist.
- **Archive:** section headers are not pinned and have no opaque background.
- **Backend:** unchanged. The server window is min 25, max 150, and the client filters on top of it.
- **Android:** rewrite `SharedDropGroup.group(list, now)` in `wt/android-drops` `collaboration/SharedDrops.kt:81-112`.

### D2. Generic story viewer

- **iOS:** `Features/Drops/DropStoryViewer.swift` is generic over `DropStorySource`. The source supplies:
  - chapters: `chapterKeys`, `chapter(key)`, `chapterStart`, `nextChapterLabel` ("Next person" / "Next drop")
  - pages and status: `page`, `status`, `isDeveloped`, `developsAt`
  - photos: `cachedPhoto`, `fullPhoto`
  - developing: `open`, `prepare`, `tapDevelops`, `develop`
  - delete and report, and the footer
- **Android:** extract a `DropStorySource` interface in commonMain. Refactor `SharedDropStoryViewer` into `DropStoryViewer(source)`, with `SharedDropStorySource` as the first implementation.

### D3. Event recap grid and story (`EventRecapModel.swift`, `EventRecapView.swift`)

- **Grid:** 3 columns, in the order the drops were taken (`createdAt`, then the original index).
- **Story:** one chapter per person, **yours first**, then the others by their first drop. A chapter starts at that person's first undeveloped drop.
- **Opening:** the recap opens on the story when anything is undeveloped, otherwise on the grid.
- **Toolbar:** a "Natural" toggle, and "Play" ("Play the story"), which reopens at the last drop.
- **Tap to develop:**
  - Status text: "Tap to develop" / "Developing…" / "Opening…" / "Couldn't develop. Tap to retry." / "Couldn't load it. Tap to retry."
  - The original is prefetched as bytes but **never decoded before the tap**.
  - The tap posts `POST /api/drops/develop {drops:[{kind:"event",id}]}` in parallel, and uses the fresh signed URL if the cached one has lapsed (signed URLs last 10 min).
  - A drop counts as developed if the server says so **or** the photo is on disk (per-user `DropPhotoCache`).
- **Image caches:** decoded LRUs at 1600 px (story) and 400 px (grid). The look and natural versions are decoded together, so the toggle is instant.
- **Copy:**
  - Title: the event title, or "What you missed" for people who weren't there.
  - Empty: "No drops from this one" / "Nobody added photos to this event."
  - Error: "Couldn't load the recap" + "Try Again".
  - Pending: "Still developing" + "Everyone's drops develop together {revealPhrase}."
  - "See who was there".
  - Delete: "It's removed from the recap for everyone."
- **Telemetry:** `recap_opened` once, when revealed and non-empty (G1-6).
- **Android:** an `EventRecapViewModel`, an expect/actual file cache, an `ImageBitmap` LRU, and `LazyVerticalGrid(3)`. This is also the 08 G3-2 recap UI, so build them together.

### D4. Pending drops open in the story (iOS #80)

- **Cover and start:**
  - The stack cover is the **newest** drop, pixelated while pending.
  - The story starts at the first ready drop, else at the cover.
  - Stacks that hold only pending drops still open, and tiles are never disabled.
- **Every drop plays,** pending ones included (the `viewable` filter is gone). The archive also opens pending drops.
- **Status while pending:** "Develops in {mm:ss}", or "Develops {relative}" with an hourglass. Taps still advance.
- **Waiting to develop:** `open(id)` sleeps until `revealAt` + 0.5 s, then develops the drop. Nothing is requested before then.
- **Develop happens elsewhere:** the viewer re-runs `open` when `isDeveloped` flips (key `"{id}|{developed}"`), so a develop done elsewhere unveils the page. Exactly one unveil animation plays.
- **Replies:** the reply field is hidden on pending drops.
- **Uploads:** an upload's preview is prefetched before its tile becomes the drop.
- **Accessibility:** "…, develops {relative}. Opens it." on archive tiles, and "… Opens them." on strip tiles.
- **Android:** a `produceState` ticker that recomposes only the label.

**Size (D1–D4):** 6.5–8 days.

**Tests to port**
- `SharedDropsTests.swift:79-153`: no drops; the rolling 24 h; 1, 5 and more than 5 per person; newest ordering; the start rule; pending cover and start.
- `EventRecapTests.swift`: grid and chapter order; first undeveloped start; no photo before develop; LRU recency.

---

## H. Home and profile

### H1. Upcoming (iOS #69; `HomeFeedModel.swift:116-150,345`; `HomeComponents.swift:400,621-700`)

- **Contents:**
  - From `GET /api/beacons/mine`: role `creator` shows the badge "Hosting"; any other role shows "Going".
  - Plus saved events not already in that list, badged "Saved" (title fallback "Saved event").
  - Ended events and the event already promoted in the Home hero are dropped.
- **Order:** start ascending, then title.
- **Placement:** Home shows the first **3**, directly under the drops strip.
- **Reload:** on refresh, and after an RSVP, a cancel or a new event.
- **Prefetch:** the RSVP state of the first 6 is fetched ahead.
- **Cache:** key `my-events` per user, read disk-first.
- **Row:**
  - A 50 dp picture, the title, then "when · where" (red when live).
  - Badge colours: accent for Hosting and Going, grey for Saved.
  - Section header "Upcoming", with "View all ›" only when the list is non-empty.
- **States:**
  - Empty: "Events you're going to show up here." when there are no recommendations either, otherwise "Nothing on your calendar yet.". Both offer "Find events", which opens the Map's Events layer.
  - Error: "Couldn't load your events." + Retry.
- **View all:**
  - Title "Upcoming", grouped "Today" / "Tomorrow" / "Thu, Oct 8". A live event sits under Today.
  - Pull to refresh forces a reload.
  - Empty: "Nothing coming up" / "Events you host, RSVP to or save appear here." / "Find Events".
  - Error: "Couldn't load your events" / {message} / "Try Again".
- **Backend:** `/beacons/mine` returns `{events:[{id, title, image_url, event_start_at, event_end_at, location_name, role}]}`. It depends on web `1b5d6a7` (#139), which is already merged.
- **Android:**
  - Add `ApiClient.myEvents()`, shared with G3-17.
  - Add pure `mergeUpcoming(mine, saved, promotedId, now)` and `groupByDay` functions.
  - Expose the result from `HomeViewModel` as a StateFlow. Replace the Home `SavedEventsSection` (`KMP/ui/components/HomeComponents.kt:657`).
- **Tests:** `HomeFeedTests.swift`, suite "Home upcoming and recommended events".
- **Size:** 2 days.

### H2. Recommended for you (iOS #69, #70; `HomeFeedModel.swift:157-280,354`)

**Candidates:** event beacons that pass all of these:
- scheduled and not ended
- start ≤ now + **14 days**
- not in Upcoming, not promoted, not created by you

**Score** = interest + near + soon + hot + rising:

| Term | Formula |
|---|---|
| interest | 3 if it matches one of your interests |
| near | `2.5·e^(−meters/3000)` (0 without location) |
| soon | 2 if live, else `2·e^(−hoursAway/36)` |
| hot | `min(1.5, ln(1+going)·0.6)` |
| rising | `min(1.5, rising·3)`, where `rising = going / (hoursSinceCreated+2)^1.5` |

**Selection:** sort by score, breaking ties by the earlier start. De-dupe by lowercased title. Keep at most **8**.

**Interest match** (case-insensitive) against `profile.interests` (the `tags` key of `GET /api/users/{me}/profile`). A tag matches if any of these is true:
- it is one of the event's `event_categories`
- it is a whole word of the title (split on non-alphanumerics, so "Art" matches "Art Walk" but not "Startup")
- it is a multi-word tag found as a substring of the title

**Reason pill** (the first rule that applies):

| # | Rule | Pill |
|---|---|---|
| 1 | interest match | "Because you like {Tag}" |
| 2 | going ≥ 3 and rising ≥ 0.25 | "Trending · {n} going" |
| 3 | live | "Live now" |
| 4 | starts today | "Today" |
| 5 | starts tomorrow | "Tomorrow" |
| 6 | ≤1,500 m away | "{distance} away" |
| 7 | going ≥ 5 | "{n} going" |
| 8 | otherwise | "Coming up" |

**Card** (shared with the Happening-now hero)
- Layout: a 148 dp picture with the pill at the top left, the title (2 lines), then "{when and where} · {N going}".
- The " · " separator uses a non-breaking space before the dot, so a wrapped line never starts with "·".
- Buttons: "View details" and "Map".
- The cards sit in a horizontal snapping row, each `min(width−32, 460)` wide, with equal heights.
- Section label: "Recommended for you". Hidden when empty.

**Gate (#70):** return nothing while Upcoming or Saved is still loading, so an event you've RSVPed to is never shown.

**Android**
- Add `rsvp_count` to `MapBeacon`; it isn't decoded today.
- Check that profile `tags` are decoded.
- Add a pure `rankRecommendations(...)` with these constants.

**Tests:**
- "Recommendations skip your events and your own, and say why they were picked"
- "A repeating event is recommended once"
- "A recommendation reads like Happening now…"

**Size:** 2 days.

### H3. Core ring (iOS #69, #70)

- **What Core is:** a hand-picked list (there is no threshold).
  - Read it from `core[]` in `GET /api/connections?bundle=dashboard`, or `GET /api/connections/core`.
  - Change it with POST and DELETE on `/api/connections/core`.
  - Android already reads it (`ApiClientConnectionEndpoints.kt:77`).
- **Ring:**
  - A sweep gradient `#F5C451 → #E39A3B → #A855F7 → #7C3AED → #F5C451` starting at −90° (`iOS/DesignSystem/Colors.swift:71`).
  - Width `max(2, round(size·0.045))`, gap `max(1.5, round(size·0.03))`.
  - The face shrinks to `size − 2·(ring+gap)`, so **the outer size never changes** (`AvatarView.swift:42-60`).
- **Call sites:**
  - Clicks list, Home people and reconnect, profile hero (112), search, chat-target and group-member pickers, Me (64), Nearby people, map pins.
  - In chats (#70): the direct-chat header; **sender avatars in group and hub chats**; the reactions sheet (42); hub members (36).
  - In-chat lookups go **by user ID** (`coreUserIDs`), not by connection ID.
- **Map:** Core pins rank 6 in M1 and drop the white edge.
- **Android today:** `CoreConnectionAvatarFrame` (`KMP/ui/components/AvatarWithOnlineIndicator.kt:207-260`) draws a solid blue 2.5 dp border **outside** the avatar, which shifts layouts. It is used at 6 call sites.
- **Android approach:**
  - Replace it with `drawWithContent { rotate(-90f) { drawCircle(Brush.sweepGradient(...), style = Stroke(w)) } }` drawn inside the avatar's size.
  - Expose `coreUserIds: StateFlow<Set<String>>`.
  - Add the missing call sites.
- **Test:** ring metrics.
- **Size:** 2 days.

### H4. Profiles of people you haven't Clicked with (iOS #70)

- **No connection:** with no `connectionID`, skip the journal request (the server refuses it) and show "Journal notes appear once you're connected."
- **Tabs:** each tab shows one note, "{What} show up here after you Click in person.", using these subjects:
  - "Your Clicks and journal notes with {name}"
  - "Messages you pin with {name}"
  - "Events and beacons you share with {name}"
  - "Photos and voice notes you share with {name}"
  - "Links you share with {name}"
  - "Files you share with {name}"
- **Your own profile:** shows no relationship tabs.
- **iOS refs:** `PeerProfileModel.swift:221`, `ProfileView.swift:76-88,387-405`.
- **Android:** check `ProfileBottomSheet.kt` and `ProfileSheetModels.kt:140` against these rules.
- **Size:** 0.5 day.

### H5. Nudge layout (iOS #70)

The second nudge under Recent connections and the Reconnect card share one layout: who and why on top, then the action and **"Not now"** side by side ("Not us" for a hangout confirm). Android uses "Dismiss" (`InboxNudge.kt:85`). **Size:** 0.5 day.

---

## M. Map

### M1. Luma-style stacks (iOS #69; `MapFeatureModel.swift:745-890`, `ClickMapView.swift:285-525`)

This replaces the old clustering and the "Which pin?" chooser.

**Zoom**
- iOS uses the true Web-Mercator zoom, floored to quarter steps.
- On Android, `CameraPositionState.position.zoom` already is that, so floor it to 0.25.
- Recompute on camera settle. Memoize by the items and the zoom step.

**Stack rank**

| Rank | Kind |
|---|---|
| −1 | hazard or SOS |
| 0 | live event |
| 1 | other events |
| 2 | Places |
| 3 | hubs |
| 4 | other beacons |
| 5 | plans |
| 6 | Core people |
| 7 | everyone else |

Ties break by event start, then title, then ID.

**Algorithm**
1. Sort by rank and project to screen points: `x = (lon+180)/360·256·2^z`, and the standard Mercator `y`.
2. Bucket into a grid of **60 dp** cells.
3. In order, each unclaimed item claims every unclaimed item in its 3×3 neighbouring cells whose centre is under 60 dp away.
4. The stack sits **at its lead's coordinate**, with ID `"stack.{leadId}"`. There are no never-cluster kinds.

**Stack pin**
- 40 dp tiles: the top 3 fanned at `(0,0°)`, `(−13,−9°)` and `(13,9°)`, with the back tiles scaled to 0.88.
- Caption (max 140 dp): a "LIVE" pill when the lead is live, the lead's title, then "+{n−1} more".
- TalkBack: "{lead} and {n−1} more", hint "Shows everything here".

**Tap** → a bottom sheet of Nearby rows (G3-21) in stack order.
- Title: "5 Events" / "3 Clicks", or "4 Here" when the kinds are mixed.
- "Zoom In" shows only when some member is more than **20 m** from the lead. It fits the members' bounds ×1.8, with a minimum span of 0.003°.
- "Close" is always shown.
- If Nearby is open, close it first.

**Android**
- Delete `clusterPoints`, `determineMapRenderData` and the overlap radius (`KMP/ui/utils/MapUtils.kt:356-545`), and the "Which pin?" chooser (`MapScreenContent.kt:200`).
- Retire `MapRenderDataTest`.
- Draw with `MarkerComposable`. **Don't** use the maps-compose-utils clustering: its algorithm differs, and it moves the lead.

**Tests:** port `NearbyMapTests.swift`:
- "Pins stack exactly when they would overlap on screen"
- "A stack leads with what matters most… and sits on its lead"
- "Zoom in is offered only when a stack's members can be drawn apart…"

**Size:** 3–4 days.

### M2. Place events merge into Place pins (iOS #68; `MapFeatureModel.swift:322,358-369`)

- **Where it applies:** only in the pin pass.
- **Rule:** an event pin is removed when its `venue_id` names a Place that is **currently in the items**, and it is ≤ `place.radiusMeters + 100 m` from that Place.
- **Lists, Nearby and counts keep every event.** An event a Place hosts somewhere else keeps its own pin.
- **Tests:** port `PlaceFilterTests.swift` (5 cases).
- **Needs:** G5.
- **Size:** 0.5 day.

### M3. Explore chips and New Event type chips (iOS #69)

- **Face piles:** the Explore-nearby chips show up to 3 overlapping pictures of active beacons of that kind, those with pictures first (22 dp, −36% overlap; `HomeView.swift:606-612`). The Hubs chip uses the first 3 hubs.
- **Create sheet:** a horizontal type-chip bar over the form, reusing Nearby's chip.
- **Size:** 1 day.

---

## E. Event additions to existing 08 rows

### E4. Flyer: no caption for Instagram (iOS #68; `ClickFlyerSheet.swift:49-53,171,194`)

- **iOS:** Share sends the PNG plus `https://joinclick.co/e/{id}`, but sends the image only to Instagram (any `com.burbn.instagram*` target). The QR on the flyer carries the link. Share stays disabled until the bitmap has rendered.
- **Android:** `ACTION_SEND image/png` with a FileProvider URI, `ClipData` (for the thumbnail), `EXTRA_TEXT` set to the URL and `EXTRA_TITLE` set to the title. Wrap it in `createChooser`, adding `EXTRA_REPLACEMENT_EXTRAS = {"com.instagram.android": {EXTRA_TEXT: null}}`. **Device-verify** that the Android 14+ chooser honours replacement extras for Instagram's direct-share targets.
- **Size:** 0.5 day on top of G3-24.

### E5. Ongoing event notification: artwork and update survival (iOS #75, #76)

**Artwork**
- Centre-crop the event `image_url` to a 156 px square (Android may use 256 px) and set it with `setLargeIcon`.
- Checked in: a 45% black overlay with a check.
- No picture: the generated gradient with a calendar icon.
- The file name hashes `beaconId|url`, so a new picture gets a new file. Delete the files for events no longer shown, and all of them at sign-out.

**Dismissal**
- Record a dismissal **only** from the `setDeleteIntent` receiver (already in 08 G3-17). Never infer it from a notification that is missing. Then app updates, process death and the system clearing notifications can't suppress an event, which is the bug iOS #76 fixed.
- If a "started" set is persisted anyway, stamp it with `BuildConfig.VERSION_CODE` and treat a mismatch as empty.

**Size:** 0.75 day on top of G3-17.
