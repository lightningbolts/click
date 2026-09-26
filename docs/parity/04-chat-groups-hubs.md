# 04: Chat, groups and hubs

This sheet covers the remaining chat gaps. Scheduling is in [01](01-message-scheduling.md) and plans are in [03](03-hangouts.md). The work is ordered by priority.

The following are already at parity and need no work:

- **Inbox:** tab structure, Core strip, swipe actions, mark unread, archive, report/remove/block.
- **Chat features:** icebreakers, Vibe Check, Click Drop camera/filters/reveal, media validation, timeline cache, older-page pagination.
- **Groups and hubs:** clique eligibility RPC, group realtime/rename/members/avatar upload, hub create/join/E2EE/media/reactions.
- **Other:** swipe-to-reply, beacon share-to-chat, direct-chat receipts.

---

## 1. Per-chat notification mutes

- **iOS reference:**
  - `iOS/Features/Chat/ChatView.swift:615-641,663`
  - `iOS/Features/Clicks/ConversationListModel.swift`: `mutes`, `isMuted`, `setMuted`
  - `ClicksView.swift`: row bell icon
  - `iOS/Core/Me/MeRepository.swift`: `chatMutes()`, `setChatMute()`
- **Backend:**
  - `GET/PUT /api/chat/notifications` (`web/app/api/chat/notifications`)
  - table `chat_mutes(user_id, chat_id, muted_until)` (`20260926120000_chat_mutes.sql`)
  - `send-push-notification` already enforces mutes server-side, so an iOS mute already silences Android pushes.
- **Android changes:**
  - API client: fetch all mutes and set/clear a mute. Confirm the exact PUT body against the route (for example `{chat_id, muted_until | null}`).
  - Put mute state in the inbox ViewModel as `StateFlow<Map<chatId, mutedUntil>>`. Update optimistically and roll back on failure.
  - **Chat header overflow → "Mute notifications"** with four options: For 1 hour, For 8 hours, For 1 week, Until I turn it back on. When muted, show "Unmute" instead.
  - The same menu goes in the inbox long-press `ConnectionActionSheet.kt` and in the hub settings menu, since hubs can be muted too.
  - Show a bell-slash icon on muted inbox rows (`ConnectionsListView.kt`).
  - **FCM:** as defense in depth, `ClickFirebaseMessagingService` also drops a message push if its chat is muted locally and not yet expired.
- **Acceptance:**
  - [ ] Mute and unmute on Android persist server-side.
  - [ ] A mute set on iOS shows the bell icon on Android.
  - [ ] Pushes stop while muted and resume after expiry.

## 2. Push previews: E2EE v2 decryption and group presentation

- **iOS reference:**
  - `iOS/Core/Crypto/SharedEpochKeyStore.swift`
  - `ChatRepository.swift`, `shareEpochKeysForPreviews` (around line 520)
  - `NotificationService/NotificationService.swift`
- **Android changes:**
  - Android can decrypt inside the FCM service; it needs no extension.
  - Persist the current epoch keys per chat to encrypted storage (`EncryptedSharedPreferences` via `TokenStorage`) whenever `E2eeV2SessionCache` learns a key. Keep the last N epochs.
  - Decrypt `e2e2:` content in `ClickFirebaseMessagingService` using those keys.
  - Map media types to labels: 📷 Photo, 🎥 Video, 🎤 Voice message, 📎 File, 📍 Beacon, 📅 Plan.
  - **Never** display `preview_text` from the server (P0-2).
  - For group pushes, use `NotificationCompat.MessagingStyle`:
    - `conversationTitle = group_name`
    - `isGroupConversation = true`
    - one notification per `chat_id`, appending lines
  - Wipe the stored epoch keys on sign-out.
- **Acceptance:**
  - [ ] v2 direct and group pushes show decrypted text.
  - [ ] Undecryptable pushes show a generic label.
  - [ ] Group pushes are titled with the group name and threaded per chat.

## 3. Read cursors: group "seen by"

- **iOS reference:**
  - `ChatRepository.readCursors`
  - `ChatRealtimeManager` (`chat_read_cursors`)
  - `ConversationModel.swift:188-217`
  - `ChatMessageSheets.swift:673` (`SeenByAvatars`)
- **Backend:**
  - `GET /api/chat/messages/read?chatId=` returns cursors.
  - Table `chat_read_cursors(read_through)` is in the realtime publication (migration `20260925120000`).
  - Check the route for how a client posts its own cursor. If it is via `POST /api/chat/messages/read`, call it on read in groups.
- **Android changes:**
  - Load cursors on chat open.
  - Subscribe to `chat_read_cursors` for the chat in `RealtimeCoordinator`.
  - Render small stacked avatars under the newest message each member has read. Exclude yourself. Show at most 5 avatars plus "+N".
  - Tapping the avatars shows a "Seen by" list.
- **Acceptance:**
  - [ ] Group members' avatars move as they read.
  - [ ] Works across platforms.

## 4. Forwarding (rebuild)

- **iOS reference:**
  - `iOS/Features/Chat/ChatTargetPicker.swift:6-172`
  - `ConversationModel.swift:1069-1102`
  - `MessageBubbleView.swift:190,226,261`
- **Remove:**
  - the stub `ChatApiClient.forwardMessage`
  - the single-target `ForwardDialog` (P0-7)
- **Target picker:** `KMP/ui/chat/ChatTargetPicker.kt`
  - Searchable, multi-select list of Clicks and groups.
  - Maximum 5 targets.
  - Excludes the source chat.
- **Send:**
  - Text is re-encrypted per target through the shared outbound builder (01 §3.3), with `metadata.forwarded = true`.
  - Media is re-uploaded from the locally decrypted copy, using the target chat's keys.
- **`canForward`** is false for:
  - deleted messages
  - beacons
  - `call_log`
  - sending or failed rows
  - Click Drops
  - plans (to match iOS behavior)
- **Display:** show a "Forwarded" label above forwarded bubbles.
- **Entry point:** add a Forward row in `MessageActionSheet`. Wire `onForward` in `ChatMessageBubble.kt:104`.
- **Hubs:** keep `onForward = {}` in hubs unless iOS supports it there.
- **Acceptance:**
  - [ ] Forwarding text or a photo to 3 targets delivers to each, E2EE.
  - [ ] The "Forwarded" label shows on both platforms.
  - [ ] Disallowed types have no Forward action.

## 5. Reactions: details sheet and double-tap ❤️

- **iOS reference:**
  - `ChatMessageSheets.swift:52-240` (`ReactorsSheet`)
  - `MessageBubbleView.swift:151-155`
- **Details sheet** (`KMP/ui/chat/ReactorsSheet.kt`):
  - Opened by **tapping a reaction chip**. This replaces today's toggle-on-tap.
  - Tabs: "All" followed by one tab per emoji with its count.
  - Rows show avatar and name, with you listed first.
  - Tapping your own row removes your reaction ("Tap to remove").
  - A "+" button opens the emoji picker.
  - The same sheet serves the plan "N going · M can't" list (03 §A4).
- **Double-tap:**
  - Double-tapping a bubble toggles ❤️.
  - Add `onDoubleTap` to the bubble's `pointerInput` in `ChatMessageBubble.kt:347-373`, alongside long-press.
  - Suppress taps briefly after a long-press. iOS calls this `BubbleTapGate`.
- **Acceptance:**
  - [ ] The sheet lists the correct reactors per emoji.
  - [ ] Self-removal works.
  - [ ] Double-tap toggles a heart without opening menus.

## 6. Deleted-message placeholders and delta sync

- **iOS reference:**
  - `ChatRepository.tombstones(...)`
  - `ConversationModel.swift:366-400` (`fetchDeltas`, `syncNewer`)
- **Backend:**
  - Table `message_tombstones` (migration `20260924121000`).
  - `GET /api/chat/messages?chatId=…&include_tombstones=1&since=<ms>` and paging variants.
- **Android changes:**
  - **Catch-up sync:** on chat open and on resume, fetch deltas since the newest cached timestamp. Apply inserts, edits and tombstones. This handles deletes made while the app was closed.
  - **History paging:** request tombstones with each older page and clip them to the page's time span. An old tombstone must never end pagination early.
  - **Display:** render a tombstone as "Message deleted", in italics and muted, with no actions. Apply the same treatment to a realtime DELETE instead of removing the row.
  - **Transport:** move history reads from direct PostgREST (`SupabaseChatRepositoryMessages.kt:30-84`) to the click-web route. Alternatively, keep PostgREST and query `message_tombstones` separately if RLS allows.
- **Acceptance:**
  - [ ] Delete on iOS while Android is killed → Android shows "Message deleted" on reopen without a full reload.
  - [ ] Pagination continues past tombstones.

## 7. Timeline navigation

- **Unread divider:**
  - Capture the first unread message ID when the chat opens.
  - Render a "New messages" divider before it by adding a `ChatTimeline` entry type (`ui/chat/ChatTimeline.kt:26-34`).
  - The divider stays until the chat is left.
- **Jump to latest:**
  - A floating ↓ button appears when scrolled up more than one screen.
  - It shows a count of unseen incoming messages.
  - Tapping it scrolls to the bottom. After a search jump it returns to the live window.
- **In-conversation search** (direct and group chats):
  - A header search bar shows "n of m" with ▲/▼ stepping.
  - Search runs server-side via `/api/chat/search` scoped to the chat, or decrypted locally.
  - Jumping loads a window around the target (`aroundMessageId`) and does not replace the whole timeline.
  - Retire the unused `searchMessagesImpl` full-history `contains` scan (`ChatViewModelInteractions.kt:249-269`, `SupabaseChatRepositoryMessages.kt:336-346`).
- **Group typing names:**
  - Resolve typing user IDs to first names: "Lena is typing…", "Lena and Sam are typing…", "3 people are typing…".
  - Replaces "Someone is typing" (`ChatView.kt:427-434`).

## 8. Message action overlay polish

- **Current Android:** a bottom sheet (`MessageActionSheet.kt`).
- **iOS:** lifts the bubble in place and shows a reaction capsule above it and an action panel below it.
- **Target:**
  - A full-screen scrim.
  - A copy of the pressed bubble drawn at its measured bounds, via `onGloballyPositioned`.
  - An emoji capsule with 6 quick emoji plus "+".
  - An action list: Reply, Forward, Copy, Edit, Save, Delete.
  - Keep reply thumbnails visible in the lifted bubble.
- **Rules:**
  - Action availability follows P0-8 and §4 `canForward`.
  - This item is UX-only and lower priority than §1–§7.

## 9. Emoji picker: search and recents

- **iOS reference:** `EmojiKeyboardPicker.swift:9-167`.
- **Changes:**
  - Add a search field backed by a name index built off the main thread (`Dispatchers.Default`) from an emoji-name table.
  - Add a "Recently used" section: the last 24, stored in `TokenStorage`.
  - Applies to `ui/components/EmojiCatalog.kt` and `MessageActionSheet.kt:194`.

## 10. Pending-send store (retry and discard)

- **iOS reference:**
  - `PendingSendStore.swift`
  - `ConversationModel.swift:454-470`
- **Changes:**
  - Wire `data/chat/PendingMessageQueue.kt` into the live send flow and persist it to disk per user.
  - Sends and uploads continue after the chat closes.
  - On reopen, restore optimistic rows.
  - Failed rows get **Retry**, which reuses the same `client_message_id`, and **Discard**.

## 11. Group profile

- **iOS reference:**
  - `iOS/Features/Groups/GroupProfileView.swift`
  - `GroupRepository.swift:200-201`
  - `ChatRepository.swift:536-556`
- **Photo permissions:**
  - Avatar change and **Remove group photo** (`DELETE /api/groups/{id}/avatar`) are creator-only.
  - Today Android lets any member use the picker (`ProfileTabbedSheets.kt:348`).
- **Epoch rotation:**
  - After adding or removing a member, **rotate the epoch immediately** via the reconcile path, rather than lazily on the next send (`ChatViewModelCliques.kt:281-305`).
  - If rotation fails, show a **"Finish securing group"** row that retries the reconcile.
- **Shared content paging:**
  - `/api/connections/{chatId}/tabs?chatId=&limit=60&before=` with `hasMore`, decrypting media per row with infinite scroll.
  - Stop decrypting the whole group history in `ProfileBottomSheet.kt:205-275`.
  - Pass `chatId` in `ApiClientProfileEndpoints.kt:193-200`.
- **Other sections:**
  - Common interests.
  - Journal (already present).
  - Group "Together" (02 §7).
- **Prefetch:** prefetch group-space data when the group chat opens.

## 12. Hub Info

- **iOS reference:** `iOS/Features/Hubs/HubInfoView.swift`.
- **New `HubInfoSheet`, reached from the hub header:**
  - Kind, radius and "here now" occupancy.
  - Members list from `participant_ids` (already parsed at `ChatApiClient.kt:144-146`).
  - Owner category **picker**, using the fixed iOS category set instead of free text.

## 13. Minor

- **Prior connections:** add Accept/Decline to the inbox row action sheet (`ConnectionActionSheet.kt`).
- **Report reasons:** use the fixed reason list to match iOS, keeping "Other" with free text.
- **Clique eligibility:** name who you haven't Clicked with ("You haven't Clicked with Sam yet").
