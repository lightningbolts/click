# 01: Message scheduling ("Send Later")

**iOS reference:** `iOS/Features/Chat/ChatComposerView.swift:25,116-120`, `ChatMessageSheets.swift:564-669`, `ChatView.swift:96-108,212-217,599`, `ConversationModel.swift:219-253,1163-1168`, `iOS/Core/Chat/ChatRepository.swift:4-17,90-93,860-947`
**Android today:** nothing exists. There is no UI, ViewModel, repository or API client code.

## 1. Product behavior (match iOS)

**Entry point**
- Long-press the send button to get a menu with **"Send Later"** (clock icon).
- It appears only when all of these hold:
  - the composer has sendable text (non-blank, ≤ 1000 characters);
  - you are not editing a message;
  - no attachments or beacons are staged (the feature is text only);
  - the chat is **direct or group**, not a hub or event hub.

**Picker sheet** (ModalBottomSheet, full height)
- Preview of the message, capped at 4 lines.
- Date and time picker.
- Valid range: **now + 60 s … now + 365 days**.
- Default: the next quarter hour that is at least 15 minutes away.
- No presets.
- Buttons: Cancel and Schedule.

**On success**
- Clear the composer only if its text is unchanged since the sheet opened.
- Clear the reply target only if it is still the same one.
- Show the toast "Scheduled for {abbrev date, short time}".

**Pending indicator**
- A capsule bar above the composer: "1 scheduled message" or "N scheduled messages", followed by "· next {relative date}".
- Animate it in and out.
- Scheduled messages never appear in the timeline, and there is no indicator in the inbox.

**Scheduled list sheet** (opened by tapping the bar; medium or large)
- One row per message, sorted soonest first: send time (abbreviated date, short time) and the decrypted content.
- Swipe (or overflow) to **Cancel**.
- Empty state: "Nothing scheduled".
- There is no edit and no "send now"; this matches iOS.

**Delivery**
- The server delivers on a one-minute cron.
- The delivered message arrives as a normal realtime `messages` INSERT, timestamped at delivery time.

**Errors**
- Schedule failure: show the error inline or as a toast, and leave the composer untouched.
- Cancel failure (404 because the message was already delivered): reload the list silently.

## 2. Backend contract (already live, `web/app/api/chat/scheduled/route.ts`)

| Call | Request | Response |
|---|---|---|
| `GET /api/chat/scheduled?chatId=<uuid>` | Bearer | `{ scheduled: [{ id, chat_id, content, message_type, metadata, send_at }] }`. Returns only the caller's own rows, ordered by `send_at` ascending. |
| `POST /api/chat/scheduled` | Same body as `POST /api/chat/messages`, plus `send_at` (epoch ms). `{ chat_id, connection_id?, content: "<e2e2:…\|e2e:…>", message_type: "text", metadata: { crypto_version, epoch, sender_device_id, client_message_id, reply_to_id? }, send_at }` | `201 { scheduled: row }`. Returns `400` if `send_at` is not in `(now, now+365d]`. Runs the same E2EE v2 gate as the send endpoint. |
| `DELETE /api/chat/scheduled?id=<uuid>` | Bearer | `204`, or `404` if the message was already delivered or does not belong to the caller. |

Table: `scheduled_messages` (migration `20260925120000`). Delivery: pg_cron → edge function `cron-scheduled-messages` → `web/app/api/cron/scheduled-messages/route.ts`.

## 3. Android implementation

### 3.1 Models and API: `KMP/data/api/`

- `ChatApiClientModels.kt`:
  - add `ClickWebScheduleMessageBody` (the send body plus `send_at: Long`);
  - add `ScheduledMessageDto(id, chatId, content, messageType, metadata, sendAt: Long)`;
  - add the envelopes `{scheduled: dto}` and `{scheduled: [dto]}`.
- `ChatApiClient.kt`:
  - add `scheduleMessage(body)`, `getScheduledMessages(chatId)` and `cancelScheduledMessage(id)`;
  - reuse the token resolution and 401-refresh logic from `sendMessage` (`:547`).

### 3.2 Repository: `KMP/data/repository/`

- `ChatRepository.kt`: add
  - `scheduleMessage(chatId, connectionId?, content, replyToId?, sendAtMs): Result<ScheduledMessage>`
  - `fetchScheduledMessages(chatId): Result<List<ScheduledMessage>>`
  - `cancelScheduledMessage(id): Result<Unit>`
- `SupabaseChatRepository.kt`: implement them.
- Domain model: `data/models/ScheduledMessage.kt` with `(id, content /* decrypted */, sendAtMs)`.

### 3.3 Shared "encrypt to wire" helper (a Phase 1 foundation)

Factor `SupabaseChatRepositoryChatEnsure.kt:233` `sendMessageImpl` into:

- `buildOutboundText(chat, plaintext, extraMetadata): OutboundWire`, which returns the wire content plus metadata (`crypto_version`, `epoch`, `sender_device_id`, `client_message_id`); and
- the transport step.

The schedule path reuses the builder with these rules:
- Use a fresh `client_message_id` for every scheduled message.
- **Omit `local_sent_at`.**
- Metadata carries only `reply_to_id` (see P0-1).
- Mirror the `refreshV2WireContent` retry when the server answers "E2EE v2 required". Scheduling hits the same gate as sending, so the content must be re-encrypted and resent.

The same builder is reused by **plans** (03 §A2) and **forwarding** (04 §4).

### 3.4 Decrypting the pending list

Use `decryptMessage` (`SupabaseChatRepositoryCrypto.kt:582`) with **no epoch upgrade or lifecycle side effects**. iOS uses `allowUpgrade: false`. Do not call `resolveE2eeV2ChatCrypto(..., allowLifecycle = true)` for listing.

### 3.5 ViewModel: `KMP/viewmodel/`

- `ChatViewModelState.kt`: add `_scheduled: MutableStateFlow<List<ScheduledMessage>>` exposed as `StateFlow`, and `val supportsScheduling` (true for direct and group, false for hubs).
- `ChatViewModel.kt` / new `ChatViewModelSchedule.kt`:
  - `loadScheduled()`: call on chat open, in parallel with the initial load.
  - `schedule(text, sendAtMs, replyTarget): Boolean`: insert the returned row and re-sort. On error, set `_messageSendError` and return false.
  - `cancelScheduled(id)`: remove the row optimistically. On failure, reload.
- Do **not** call `activateConnectionIfPending` from the schedule path (iOS does not).
- `ChatViewModelRealtimeSync.kt` `Insert` branch (`:89-100`): when an outgoing message is inserted that doesn't replace an optimistic row, prune entries with `sendAtMs <= now`. This mirrors `iOS ConversationModel.swift:1167`.

### 3.6 UI: `KMP/ui/chat/`

- `ChatComposerStrip.kt:66,221-255`:
  - add `onScheduleSend: (() -> Unit)? = null`;
  - change the send `Box` to `combinedClickable(onLongClick = …)`, which opens a `DropdownMenu` with "Send Later";
  - use a haptic on long-press.
- `ConnectionChatMessageComposer.kt:251-264`: pass `onScheduleSend` only when there is no edit in progress and nothing is staged.
- `ui/screens/HubChatScreen.kt:902-917`: pass `null`.
- New `ScheduleSendSheet.kt`: a Material3 `DatePicker` followed by a `TimePicker`.
  - Convert with kotlinx-datetime: `LocalDateTime.toInstant(TimeZone.currentSystemDefault())`.
  - Clamp to the valid range and disable Schedule outside it.
- New `ScheduledMessagesBar.kt` and `ScheduledMessagesSheet.kt`: mount the bar in the bottom inset above the composer in `ui/screens/ChatView.kt`.

## 4. Known server-side caveats (document; don't work around)

- **Frozen ciphertext.** Content is encrypted under the epoch current at schedule time. Members who join later may not be able to decrypt it. This is accepted on iOS.
- **Silent drop.** If the sender loses access, or the insert fails after the claim, the row disappears without a message being sent.
- **Coarse timing.** Delivery can be up to about a minute late.

## 5. Acceptance

- [ ] Long-press send shows "Send Later" in direct and group chats. It never appears in hubs, while editing, or with staged media.
- [ ] Out-of-range times cannot be scheduled. The default is the next quarter hour at least 15 minutes out.
- [ ] The scheduled row is created server-side with E2EE content and metadata that has no `reply_to_content` and no `local_sent_at`.
- [ ] The bar and sheet show decrypted pending messages. Cancel works, and a 404 triggers a reload.
- [ ] At `send_at`, the message appears for both parties, and the pending list prunes itself on the realtime insert.
- [ ] A message scheduled on iOS shows up in the Android pending list for the same user, and the reverse also holds.
- [ ] Unit tests cover the default-time and clamp logic, the DTO (de)serialization, and the pruning rule.
