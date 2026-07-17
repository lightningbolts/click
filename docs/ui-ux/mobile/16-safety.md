# 16 — Safety (Connection & Message Actions)

**Scope:** `ConnectionActionSheet`, `ConnectionSheetDialogs`, `MessageActionSheet` (two-step delete), map beacon delete dialog, group member removal.  
**Source:** `ui/chat/ConnectionActionSheet.kt`, `ui/chat/ConnectionSheetDialogs.kt`, `ui/chat/MessageActionSheet.kt`, `ui/screens/ChatView.kt`, `ui/screens/ConnectionsListView.kt`, `ui/screens/ConnectionsScreen.kt`, `ui/screens/MapScreen.kt`, `ui/chat/ChatMessageBubble.kt`, `ui/components/GlassAlertDialog.kt`, `ui/components/AnimatedClickDialog.kt`  
**Out of scope:** Web, backend moderation APIs, redesign proposals.

---

## ASCII hierarchy

```
Safety entry points
├── Chat header ⋮ (MoreVert)
│   └── ConnectionActionSheet → ConnectionSheetDialogs
├── Connections inbox long-press
│   └── ConnectionActionSheet → ConnectionSheetDialogs
├── Message long-press
│   └── MessageActionSheet → two-step delete dialogs
├── Group profile → Members tab → Remove
│   └── ConnectionSheetDialogs (Remove member)
└── Map beacon detail (creator)
    └── Delete icon → AnimatedClickDialog
```

**Pattern:** Sheet → dismiss → dialog. `ConnectionActionSheet.pick()` calls `onMenuAction(action)` then `onDismiss()` so bottom sheet scrim never blocks confirm dialogs.

---

## 1. Layout

### ConnectionActionSheet shell

| Property | Value |
|----------|-------|
| Container | `ClickActionBottomSheet` → `ClickPlatformSheet` → `MapBeaconSheetRoot` |
| Background | `GlassSheetTokens.OledBlack` (`#000000`) |
| Scrim | α = 0.55 |
| Column | `fillMaxWidth()`, `fillMaxHeight()`, `padding(bottom = 32.dp)` |
| Header title | Centered, `titleMedium`, `GlassSheetTokens.OnOled`, `padding(horizontal = 20.dp, vertical = 12.dp)` |
| Header divider | `HorizontalDivider`, `GlassSheetTokens.GlassBorder` @ 50% alpha |
| Rows | `BentoGlassOptionRow` (default bordered; safety rows `showBorder = false`) |
| Bottom | `Spacer(weight = 1f)` pushes content up |

**Header title strings:**

| Context | String |
|---------|--------|
| 1:1 | `details.otherUser.name` or fallback `"Connection"` |
| Group | `details.groupClique?.name` (trimmed) or fallback `"Verified click"` |

### ConnectionActionSheet — 1:1 rows (above safety divider)

| Title | Subtitle | Icon tint |
|-------|----------|-----------|
| `"Nudge"` | `"Send a quick ping"` | default |
| `"Add to Core"` | `"Pin to the top and unlock core-only features"` | default |
| `"Remove from Core"` | `"Unpin from your core connections"` | default |
| `"Unarchive"` | `"Move this connection back to Active"` or `"Remove from your Archived tab (server-archived connections stay read-only)"` | default |
| `"Archive"` | `"Hide this connection (recoverable)"` | default |
| `"Mark as Unread"` | `"Show this conversation as unread on all devices"` | default |

**Safety divider:** `HorizontalDivider` @ 35% alpha, `padding(vertical = 6.dp)`.

### ConnectionActionSheet — 1:1 safety rows

| Title | Subtitle | Icon | Row styling |
|-------|----------|------|-------------|
| `"Remove Connection"` | `"Permanently remove this chat"` | `PersonRemove`, tint `#FF6B6B` | `destructive = true`, `showBorder = false` |
| `"Report"` | `"Flag for review"` | `Flag`, tint `#FF8C00` | `titleColor = #FF8C00`, `showBorder = false` |
| `"Block"` | `"They can no longer reach you"` | `Block`, tint `#FF6B6B` | `destructive = true`, `showBorder = false` |

### ConnectionActionSheet — group rows

**No** Remove / Report / Block for groups.

| Title | Subtitle | Icon | Condition |
|-------|----------|------|-----------|
| `"Mark as Unread"` | `"Show this verified click as unread on all devices"` | `MarkEmailUnread` | `canMarkUnread` |
| `"Leave Group"` | `"Lose access to this verified click"` | `Logout` (auto-mirrored) | always |
| `"Delete Group"` | `"Remove for everyone"` | `Delete`, tint `#FF6B6B` | `isGroupCreator` only |

### ConnectionSheetDialogs shell

| Property | Value |
|----------|-------|
| Container | `GlassAlertDialog` → `UnifiedPopupAlert` |
| Scrim | α = 0.58 |
| Card | Centered OLED card, animated scale+fade |
| Button order | Dismiss (left) → Confirm (right) |
| Dismiss animation | `LocalGlassAlertAnimatedDismiss` fade-out before `onDismissRequest` |

### ConnectionSheetDialogs — per dialog

| Dialog | Title | Body | Confirm | Dismiss |
|--------|-------|------|---------|---------|
| Remove | `"Remove Connection?"` | `"This will permanently remove this connection and all messages. This cannot be undone."` | `"Remove"` (error color) | `"Cancel"` (muted) |
| Block | `"Block User?"` | `"They won't be able to contact you and this connection will be removed. This cannot be undone."` | `"Block"` (error color) | `"Cancel"` (muted) |
| Report | `"Report User"` | `"Please describe the issue:"` + field | `"Submit"` (`#FF8C00`) | `"Cancel"` (muted) |
| Report placeholder | — | `"Reason for report..."` | — | — |
| Leave group | `"Leave group?"` | `"You will lose access to this verified click and its messages."` | `"Leave"` (`#FF4444`) | `"Cancel"` (muted) |
| Delete group | `"Delete group?"` | `"Permanently deletes this verified click for everyone. This cannot be undone."` | `"Delete"` (`#FF4444`) | `"Cancel"` (muted) |
| Remove member | `"Remove from group?"` | `"{memberName} will be removed from this verified click and lose access to its messages."` | `"Remove"` (error color) | `"Cancel"` (muted) |

**Report field:** `OutlinedTextField`, full width; focused border/cursor `PrimaryBlue`. Confirm enabled when `reportReason.isNotBlank()`.

**Remove member fallback name:** `"This member"` when name blank.

### MessageActionSheet shell

| Property | Value |
|----------|-------|
| Container | `ClickActionBottomSheet`, OLED black, `padding(bottom = 32.dp)` |
| Rows | `BentoGlassOptionRow`: `showBorder = false`, `horizontalInset = 0.dp`, `cornerRadius = 0.dp` |
| Emoji strip | 6 quick reactions: `👍` `❤️` `😂` `😮` `😢` `😡` @ 28sp, evenly spaced |
| More emojis | `TextButton` `"More emojis…"` (PrimaryBlue) → `emojiPickMode` |

### MessageActionSheet — delete entry (sent messages only)

| Property | Value |
|----------|-------|
| Row title | `"Delete"` |
| Icon | `Delete`, tint `#FF4444`, `contentDescription = "Delete message"` |
| Style | `destructive = true` |
| onClick | Sets `showDeleteMessageConfirm = true` (sheet stays open) |

### MessageActionSheet — delete step 1

| Field | String |
|-------|--------|
| Title | `"Delete Message?"` |
| Body | `"This message will be permanently deleted. This cannot be undone."` |
| Confirm | `"Delete"` (error color) |
| Dismiss | `"Cancel"` |

### MessageActionSheet — delete step 2

| Field | String |
|-------|--------|
| Title | `"Delete Message Permanently?"` |
| Body | `"This action is permanent and cannot be undone."` |
| Confirm | `"Yes, Delete"` (error color) |
| Dismiss | `"Cancel"` |

### Map beacon delete dialog

| Property | Value |
|----------|-------|
| Container | `AnimatedClickDialog` → `UnifiedPopupFormDialog` |
| Entry | Top-right `IconButton`, `Icons.Filled.Delete`, creator only |
| Entry a11y | `"Delete beacon"` |
| Title | `"Delete beacon?"` |
| Body | `"This removes the pin from the map for everyone nearby."` (muted) |
| Confirm | `"Delete"` (`GlassSheetTokens.OnOled` — white, not error red) |
| Dismiss | `"Cancel"` (muted) |
| Motion | Scale+fade overlay (320ms in / 220ms out) |

### Group member removal (profile)

| Property | Value |
|----------|-------|
| Control | `TextButton` `"Remove"` (creator only, not self) |
| Alt UI | `GroupMembersPickerSheet` — `IconButton` + `Close` icon, `contentDescription = "Remove member"` |

---

## 2. Interactive

### ConnectionActionSheet

| Action | Behavior |
|--------|----------|
| Tap any row | `pick(action)` → sheet dismisses → parent sets `pendingConnectionDialog` for deferred actions |
| Immediate actions | Nudge, Archive, Unarchive, Core, Mark unread — no dialog |
| Deferred actions | Remove, Block, Report, Leave group, Delete group → `ConnectionSheetDialogs` |
| Sheet dismiss | Scrim tap / drag / back via `onDismissRequest` |

### ConnectionSheetDialogs

| Confirm action | Result |
|----------------|--------|
| Remove | `viewModel.deleteConnectionPermanently` — navigates back on success (chat context) |
| Block | `viewModel.blockUser` — navigates back on success (chat context) |
| Report | `viewModel.reportConnection(reason)` — stays in chat |
| Leave group | `viewModel.leaveVerifiedClique` — navigates back on success |
| Delete group | `viewModel.deleteVerifiedClique` — navigates back on success |
| Remove member | `viewModel.removeMemberFromVerifiedClique` — stays on profile |

**Inbox context:** Remove/Block/Report/Leave/Delete group — no navigation; toast feedback only.

### MessageActionSheet

| Action | Behavior |
|--------|----------|
| Quick emoji tap | Sends reaction |
| `"More emojis…"` | Opens emoji picker mode |
| Reply | Disabled for `messageType == "call_log"` |
| Edit | Sent messages only |
| Copy / Copy caption & link | Text/image actions |
| Image: Save to gallery / Share image | Encrypted images only |
| Delete (step 1) | `"Delete"` → opens step 2 (sheet still open) |
| Delete (step 2) | `"Yes, Delete"` → `viewModel.deleteMessage(id)` + sheet dismiss |

**Haptics:** `lightImpact()` on sheet open; `heavyImpact()` on bubble long-press.

### Map beacon delete

| Action | Behavior |
|--------|----------|
| Delete icon tap | `showDeleteConfirm = true` |
| Confirm `"Delete"` | `viewModel.deleteOwnedBeacon(beacon.id)` |
| Dismiss | `showDeleteConfirm = false` |

### Visibility flags (ConnectionActionSheet)

| Flag | Effect |
|------|--------|
| `isGroup` | Switches 1:1 vs group action sets |
| `isGroupCreator` | Shows `"Delete Group"` |
| `canMarkUnread` | `lastMessage != null OR connection.last_message_at != null` AND `unreadCount == 0` |
| `isArchived` | Shows `"Unarchive"` instead of `"Archive"` |
| `isServerLifecycleArchived` | Hides `"Archive"`; Unarchive subtitle changes |
| `isCore` | `"Remove from Core"` vs `"Add to Core"` |

---

## 3. States

### ConnectionActionSheet

| State | UI |
|-------|-----|
| Open | Sheet visible with context-appropriate rows |
| Dismissed | Parent shows dialog if deferred action pending |
| 1:1 | Safety rows visible below divider |
| Group | No Remove/Report/Block; Leave + optional Delete Group |

### ConnectionSheetDialogs

| State | UI |
|-------|-----|
| None | No dialog |
| Remove / Block / Leave / Delete / Remove member | Confirm dialog with destructive confirm color |
| Report | Dialog + text field; Submit disabled until non-blank |
| Dismissing | Animated fade-out via `LocalGlassAlertAnimatedDismiss` |

### MessageActionSheet delete

| State | UI |
|-------|-----|
| Main menu | Delete row visible for sent messages only |
| Step 1 | `"Delete Message?"` overlay |
| Step 2 | `"Delete Message Permanently?"` overlay |
| Complete | Message removed, sheet dismissed |

### Post-action toast feedback (via `ChatViewModel.nudgeResult`)

| Action | Success | Failure |
|--------|---------|---------|
| Remove connection | `"Connection removed"` | `"Failed to remove connection"` |
| Block | `"User blocked"` | `"Could not block user"` |
| Report | `"Report submitted"` | `"Failed to submit report"` |
| Leave group | `"You left the group"` | `"Could not leave group"` |
| Delete group | `"Group deleted"` | `"Could not delete group"` |

### Map beacon delete

| State | UI |
|-------|-----|
| Default | Delete icon visible (creator only) |
| Confirm open | `"Delete beacon?"` dialog |
| After confirm | Beacon removed from map |

### Hub chat exception

`HubChatScreen` sets `enableMessageContextMenu = false` — no message long-press menu in hub chats.

---

## 4. Micro-copy

### ConnectionActionSheet — 1:1 non-safety

| Key | String |
|-----|--------|
| Nudge | `"Nudge"` |
| Nudge subtitle | `"Send a quick ping"` |
| Add to Core | `"Add to Core"` |
| Add to Core subtitle | `"Pin to the top and unlock core-only features"` |
| Remove from Core | `"Remove from Core"` |
| Remove from Core subtitle | `"Unpin from your core connections"` |
| Unarchive | `"Unarchive"` |
| Unarchive subtitle (normal) | `"Move this connection back to Active"` |
| Unarchive subtitle (server) | `"Remove from your Archived tab (server-archived connections stay read-only)"` |
| Archive | `"Archive"` |
| Archive subtitle | `"Hide this connection (recoverable)"` |
| Mark as Unread | `"Mark as Unread"` |
| Mark as Unread subtitle | `"Show this conversation as unread on all devices"` |

### ConnectionActionSheet — 1:1 safety

| Key | String |
|-----|--------|
| Remove Connection | `"Remove Connection"` |
| Remove subtitle | `"Permanently remove this chat"` |
| Report | `"Report"` |
| Report subtitle | `"Flag for review"` |
| Block | `"Block"` |
| Block subtitle | `"They can no longer reach you"` |

### ConnectionActionSheet — group

| Key | String |
|-----|--------|
| Mark as Unread | `"Mark as Unread"` |
| Mark as Unread subtitle | `"Show this verified click as unread on all devices"` |
| Leave Group | `"Leave Group"` |
| Leave subtitle | `"Lose access to this verified click"` |
| Delete Group | `"Delete Group"` |
| Delete subtitle | `"Remove for everyone"` |

### ConnectionActionSheet — headers

| Key | String |
|-----|--------|
| 1:1 fallback | `"Connection"` |
| Group fallback | `"Verified click"` |

### ConnectionSheetDialogs

| Key | String |
|-----|--------|
| Remove title | `"Remove Connection?"` |
| Remove body | `"This will permanently remove this connection and all messages. This cannot be undone."` |
| Remove confirm | `"Remove"` |
| Block title | `"Block User?"` |
| Block body | `"They won't be able to contact you and this connection will be removed. This cannot be undone."` |
| Block confirm | `"Block"` |
| Report title | `"Report User"` |
| Report prompt | `"Please describe the issue:"` |
| Report placeholder | `"Reason for report..."` |
| Report confirm | `"Submit"` |
| Leave title | `"Leave group?"` |
| Leave body | `"You will lose access to this verified click and its messages."` |
| Leave confirm | `"Leave"` |
| Delete group title | `"Delete group?"` |
| Delete group body | `"Permanently deletes this verified click for everyone. This cannot be undone."` |
| Delete group confirm | `"Delete"` |
| Remove member title | `"Remove from group?"` |
| Remove member body | `"{memberName} will be removed from this verified click and lose access to its messages."` |
| Remove member confirm | `"Remove"` |
| Remove member fallback | `"This member"` |
| Cancel (all) | `"Cancel"` |

### MessageActionSheet

| Key | String |
|-----|--------|
| More emojis | `"More emojis…"` |
| Delete | `"Delete"` |
| Delete step 1 title | `"Delete Message?"` |
| Delete step 1 body | `"This message will be permanently deleted. This cannot be undone."` |
| Delete step 1 confirm | `"Delete"` |
| Delete step 2 title | `"Delete Message Permanently?"` |
| Delete step 2 body | `"This action is permanent and cannot be undone."` |
| Delete step 2 confirm | `"Yes, Delete"` |
| Cancel | `"Cancel"` |

### Map beacon

| Key | String |
|-----|--------|
| Delete a11y | `"Delete beacon"` |
| Dialog title | `"Delete beacon?"` |
| Dialog body | `"This removes the pin from the map for everyone nearby."` |
| Confirm | `"Delete"` |
| Cancel | `"Cancel"` |

### Group profile member removal

| Key | String |
|-----|--------|
| Remove button | `"Remove"` |
| Remove member a11y (picker) | `"Remove member"` |

### Toast feedback (post-confirm)

| Key | String |
|-----|--------|
| Connection removed | `"Connection removed"` |
| Remove failed | `"Failed to remove connection"` |
| User blocked | `"User blocked"` |
| Block failed | `"Could not block user"` |
| Report submitted | `"Report submitted"` |
| Report failed | `"Failed to submit report"` |
| Left group | `"You left the group"` |
| Leave failed | `"Could not leave group"` |
| Group deleted | `"Group deleted"` |
| Delete failed | `"Could not delete group"` |

---

## 5. Flow

### Chat header ⋮ → connection safety

```
ChatView header → IconButton (MoreVert, "More options")
  → showConnectionSheet = true
  → ConnectionActionSheet
  → Tap safety action (Remove / Report / Block / Leave / Delete group)
    → pick() → sheet dismisses
    → pendingConnectionDialog set
  → ConnectionSheetDialogs
  → Confirm:
      Remove  → deleteConnectionPermanently → back on success
      Block   → blockUser → back on success
      Report  → reportConnection(reason) → stays in chat
      Leave   → leaveVerifiedClique → back on success
      Delete  → deleteVerifiedClique → back on success
  → GlassToastHost feedback
```

### Connections inbox long-press

```
Long-press ConnectionItem (heavyImpact)
  → pendingMenuChat = chatDetails
  → ConnectionActionSheet (same copy/visibility)
  → ConnectionSheetDialogs
  → Confirm:
      Remove/Block/Report → viewModel.*ById(connId) — NO navigation
      Leave/Delete group → leave/deleteVerifiedClique — NO navigation
  → GlassToastHost feedback
```

### Message long-press → two-step delete

```
Long-press ChatMessageBubble (heavyImpact)
  OR long-press image / photo
  → contextMenuMessage = MessageWithUser
  → MessageActionSheet
  → [Delete on sent message only]
    Step 1: "Delete Message?" → Confirm "Delete"
    Step 2: "Delete Message Permanently?" → "Yes, Delete"
    → viewModel.deleteMessage(id) + sheet dismiss
```

**Gesture note:** Vertical scroll cancels long-press (`detectTapGestures`).

### Group member removal (not from ⋮ or message long-press)

```
Chat header group avatar tap
  → TabbedGroupProfileSheet → Members tab
  → TextButton "Remove" (creator only, not self)
  → pendingRemoveGroupMember = RemoveGroupMember(id, name)
  → ConnectionSheetDialogs "Remove from group?"
  → viewModel.removeMemberFromVerifiedClique(groupId, memberId)
```

### Map beacon delete

```
Map → tap owned beacon → BeaconDetailSheetContent
  → Creator sees Delete icon ("Delete beacon")
  → showDeleteConfirm = true
  → "Delete beacon?" dialog
  → "Delete" → viewModel.deleteOwnedBeacon(beacon.id)
```

### Visibility matrix

| Action | 1:1 chat ⋮ | Group chat ⋮ | Inbox long-press | Group profile |
|--------|-----------|--------------|------------------|---------------|
| Remove Connection | ✓ | ✗ | ✓ (1:1) | ✗ |
| Report | ✓ | ✗ | ✓ | ✗ |
| Block | ✓ | ✗ | ✓ | ✗ |
| Leave Group | ✗ | ✓ | ✓ (group) | ✗ |
| Delete Group | ✗ | ✓ (creator) | ✓ (creator) | ✗ |
| Remove member | ✗ | ✗ | ✗ | ✓ (creator) |

---

## 6. A11y

| Element | `contentDescription` / semantics |
|---------|----------------------------------|
| Chat ⋮ button | `"More options"` |
| ConnectionActionSheet icons | `null` (decorative; title/subtitle carry meaning) |
| MessageActionSheet Reply | `"Reply"` |
| MessageActionSheet Back (emoji) | `"Back"` |
| MessageActionSheet Save / Share / Copy / Edit | respective labels |
| MessageActionSheet Delete icon | `"Delete message"` |
| Quick emoji `Text` | none |
| Map beacon Delete | `"Delete beacon"` |
| GroupMembersPicker Remove | `"Remove member"` |
| Dialogs | `GlassAlertDialog` / `UnifiedPopupAlert` — `focusable = true`; standard title/text/button roles |

**Gaps:**

- ConnectionActionSheet rows have no explicit `semantics` / `testTag`.
- Destructive row styling is visual only (red/orange tints) — no `role` or state announcement.
- Two-step delete does not announce step transition to screen readers.
- Report text field has no explicit error semantics on submit failure.

**Haptics:** Long-press uses `heavyImpact()` on connection rows and message bubbles; sheet open uses `lightImpact()`.

**Focus order (sheet):** Header title → action rows top-to-bottom → (dialogs overlay when open).

---

## Related documents

- [07-connections-inbox.md](07-connections-inbox.md) — inbox long-press, `ConnectionSheetDialogs` copy index
- [08-chat.md](08-chat.md) — `ChatView` header, message gestures, `MessageActionSheet`
- [10-map-beacons-hubs.md](10-map-beacons-hubs.md) — beacon detail sheet context
