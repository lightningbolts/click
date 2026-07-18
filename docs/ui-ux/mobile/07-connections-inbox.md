# 07 — Connections Inbox (Clicks)

**Scope:** `ConnectionsScreen`, `ConnectionsListView`, `ConnectionItem`, `RememberMeStrip`, `ConnectionsTabControls` / `ConnectionsFloatingHeader`, `ConnectionActionSheet`, `ConnectionSheetDialogs`, verified-click FAB, hub feed rows.  
**Source:** `ui/screens/ConnectionsScreen.kt`, `ConnectionsListView.kt`, `ui/chat/ConnectionItem.kt`, `ui/chat/RememberMeStrip.kt`, `ui/components/ConnectionsTabControls.kt`, `ui/chat/ConnectionActionSheet.kt`, `ui/chat/ConnectionSheetDialogs.kt`  
**Out of scope:** Web, backend, full `ChatView` spec (see [08-chat.md](08-chat.md)).

**Visual system:** Functional Clarity (neo-brutalist) — opaque surfaces, 2px `#000` borders, primary `#630ed4`, no glass/blur/gradients. Design-asset mock: `click/docs/design-assets/chat/`.

**Track C (2026-07-17):** Remember Me horizontal strip for Core-pinned 1:1s on Active tab; list row spacing left unchanged. Avatar hit = circle; name hit = pill.

---

## ASCII hierarchy

```
ConnectionsScreen (organism — tab shell + chat overlay)
├── ConnectionsListView (persistent base on iOS)
│   ├── ConnectionsFloatingHeader
│   │   ├── title: "Clicks"
│   │   ├── subtitle (dynamic count / search)
│   │   ├── ConnectionsSegmentBar: Active | Groups | Archived
│   │   └── compact: ConnectionsTabFilterMenuChip dropdown
│   ├── LazyColumn
│   │   ├── RememberMeStrip (Active tab, no search, Core 1:1s non-empty)
│   │   │   ├── label: "Remember Me"
│   │   │   ├── LazyRow chips (avatar + time badge + first name)
│   │   │   └── label: "Clicks"
│   │   ├── ActiveHubFeedRow[] (Groups tab only)
│   │   └── ConnectionItem[] per chat
│   ├── Empty / Loading / Error states
│   ├── FloatingActionButton (Active + Groups) — "Create verified click"
│   ├── GlassToastHost (action feedback)
│   ├── ConnectionMemberPickerSheet — verified click create
│   ├── ConnectionActionSheet (long-press)
│   ├── ConnectionSheetDialogs (confirm alerts)
│   └── HubActionSheet (hub long-press / ⋮)
└── ChatView overlay (iOS swipe-back) | AnimatedContent (Android)
```

---

## 1. Layout / Container

### ConnectionsScreen

| Property | Value |
|----------|-------|
| Root | `Box(fillMaxSize)` |
| iOS | Persistent `ConnectionsListView` + `AnimatedVisibility` chat overlay + parallax peek |
| Android | `AnimatedContent` list ↔ chat horizontal slide |
| Profile | `TabbedUserProfileSheet` from avatar tap |
| Group profile | `TabbedGroupProfileSheet` from group avatar |

### ConnectionsListView

| Property | Value |
|----------|-------|
| Background | `AdaptiveBackground` |
| List padding | 20dp horizontal; top = floating header collapse padding; bottom = `rememberBottomChromePadding()` |
| Row spacing | 10dp vertical between items |
| Header | `ConnectionsFloatingHeader` zIndex 1, `floatingHeaderStatusBarPadding` |
| FAB | 56dp `ClickCircularGlassIconButton` (same as map Drop beacon: `LiquidGlassPill` + `clickBorderColor()`), bottom-end above nav (`rememberFabAboveNavPadding`) |
| Toast | `GlassToastHost` left of FAB on Active / Groups |

### RememberMeStrip

| Property | Value |
|----------|-------|
| Membership | Core-pinned **1:1** only (`connection.id in coreConnectionIds`, `groupClique == null`) |
| Visibility | Active tab; hide when searching or core empty |
| Sort | Activity desc (`connectionListActivityTs`) |
| Avatar | 56dp `ConnectionListUserAvatarFace` in `CoreConnectionAvatarFrame`; 2dp `clickBorderColor()` |
| Time chip | Primary fill; compact `formatRememberMeBadge` (`12h` / `2d` / …); omit if no last activity |
| Name | First name (`firstName` or first token of display name) |
| Labels | `"Remember Me"` then optional `"Clicks"` above the list |
| Hit target | Circular on avatar (`CoreConnectionAvatarFrame` + `clip(CircleShape)`); name label **pill** (`clip(RoundedCornerShape(999.dp))` + horizontal padding — not full-width square `clickable`) |
| Duplication | Core people still appear in `ConnectionItem` rows below |

### ConnectionItem

| Property | Value |
|----------|-------|
| Shape | 16dp `RoundedCornerShape` (Bento exterior) |
| Border | 2dp `#000` hard border; pressed = 2dp translate + instant darken |
| Padding | 16dp horizontal, 10dp vertical |
| Avatar | 44dp; group uses `GroupAvatar` cluster |
| Core pin | `CoreConnectionAvatarFrame` when in core set |

### ConnectionsSegmentBar (`ConnectionsTabControls`)

Three equal segments: `"Active ({n})"`, `"Groups ({n})"`, `"Archived ({n})"`. Compact scroll uses dropdown chip with same labels.

---

## 2. Interactive Elements

### List

| Gesture | Target | Action |
|---------|--------|--------|
| Tap chip | `RememberMeStrip` chip | `onChatSelected(chatId)` |
| Tap row | `ConnectionItem` | `onChatSelected(chatId)` |
| Long-press row | `ConnectionItem` | Open `ConnectionActionSheet` |
| Tap 1:1 avatar | Avatar | `TabbedUserProfileSheet` |
| Tap group avatar | Group cluster | `TabbedGroupProfileSheet` |
| Scroll near end | LazyColumn | `loadMoreConnectionsPage()` (paginated, not during search) |
| Tab segment tap | Active / Groups / Archived | Filter list + reset display limit |
| Search (header) | Magnifier | `UnifiedSearchSheet` (parent) |
| FAB tap | Groups icon FAB | Open verified-click `ConnectionMemberPickerSheet` |

### ConnectionActionSheet

Each `BentoGlassOptionRow` emits `ConnectionMenuAction` then dismisses. Destructive actions open `ConnectionSheetDialogs` after dismiss.

### Hub rows (Groups tab)

| Gesture | Action |
|---------|--------|
| Tap row | `onHubSelected(hub)` |
| Long-press / ⋮ | `HubActionSheet` |

### Verified click create sheet

| Control | Action |
|---------|--------|
| Toggle candidates | Updates eligibility mask (server graph check) |
| `"Create"` | `createVerifiedClique` |
| Dismiss | Clear selection |

---

## 3. States

### ChatListState (list root)

| State | UI |
|-------|-----|
| **Loading** (empty inbox) | Center `AdaptiveCircularProgressIndicator` |
| **Error** | `"Error loading chats"` + message + `"Retry"` |
| **Success** | List or empty state per tab |

Header subtitle while loading empty: `"Loading…"`

### Tab empty states

| Tab / condition | Title | Body |
|-----------------|-------|------|
| Active, no search | `"No connections yet"` | `"Start clicking with people nearby!"` |
| Groups | `"No group chats"` | `"Group clicks will appear here"` |
| Archived | `"No archived connections"` | `"Archived chats will appear here"` |
| Search no hits | `"No matches found"` | `"Try a different search term"` |

**Empty icon:** `ChatBubbleOutline` (tabs) or `SearchOff` (search).

### Header subtitle (populated)

| Condition | Format |
|-----------|--------|
| Default | `"{count} {active\|group\|archived} {connection\|connections}"` |
| Search | `"{count} result(s) for \"{query}\""` |

### ConnectionItem row

| Field | States |
|-------|--------|
| Headline | Peer `name` / `"Unknown"` / group `name` / `"Verified click"` |
| Time | Formatted timestamp / `"No messages"` |
| Preview | `"Start a conversation"` / `"New message"` / decrypted preview / `previewLabel()` |
| Subtitle loading | `LoadingSubtitlePlaceholder` when resolving peer |
| Unread | Blue gradient badge with count |
| Availability bolt | Gold bolt icon when mutual intent overlap |
| Core | Star frame on avatar |

**Preview strings:**

| Case | Text |
|------|------|
| No messages | `"Start a conversation"` |
| Activity without decrypted body | `"New message"` |
| Shimmer loading | animated placeholder (no static copy) |

### FAB visibility

| Condition | FAB |
|-----------|-----|
| Active (`selectedTabIndex == 0`) && logged in | Shown |
| Groups (`selectedTabIndex == 1`) && logged in | Shown |
| Archived | Hidden |

FAB `contentDescription`: `"Create verified click"` — styled like map Drop beacon (bordered glass circle, not solid purple Material FAB).

### Verified click picker states

| State | UI |
|-------|-----|
| Eligibility loading | `"Checking who can join…"` |
| Proximity autofill | `"Loading your tap group in Clicks…"` |
| Duplicate group error | `"You already have a verified click with this group."` |
| Proximity hint chips | `"People from your tap (profiles may still sync)"` + name chips (`"Friend"` fallback) |
| Primary disabled | Until graph valid + selection non-empty |
| Create success toast | `"Click created"` |
| Create failure | `"Couldn't create click"` or server message |

**Sheet copy:**

| Element | String |
|---------|--------|
| Title | `"Create verified click"` |
| Subtitle | `"Pick friends who are all connected to each other. Eligibility is verified on the server."` |
| Search placeholder | `"Search connections"` — vertically centered in `ConnectionPickerSearchBar` (`BasicTextField`, not clipped) |
| Primary | `"Create"` |
| Add-to-group variant title | `"Add to group"` |
| Add subtitle | `"Choose verified connections who are connected to everyone in this click."` |
| Add primary (0–1 selected) | `"Add"` |
| Add primary (N selected) | `"Add {N}"` |

### ConnectionActionSheet — 1:1 actions

| Title | Subtitle |
|-------|----------|
| `"Nudge"` | `"Send a quick ping"` |
| `"Add to Core"` | `"Pin to the top and unlock core-only features"` |
| `"Remove from Core"` | `"Unpin from your core connections"` |
| `"Unarchive"` | `"Move this connection back to Active"` or `"Remove from your Archived tab (server-archived connections stay read-only)"` |
| `"Archive"` | `"Hide this connection (recoverable)"` |
| `"Mark as Unread"` | `"Show this conversation as unread on all devices"` |
| `"Remove Connection"` | `"Permanently remove this chat"` |
| `"Report"` | `"Flag for review"` |
| `"Block"` | `"They can no longer reach you"` |

**Sheet title:** peer `name` or `"Connection"`; group `name` or `"Verified click"`.

### ConnectionActionSheet — group actions

| Title | Subtitle |
|-------|----------|
| `"Mark as Unread"` | `"Show this verified click as unread on all devices"` |
| `"Leave Group"` | `"Lose access to this verified click"` |
| `"Delete Group"` | `"Remove for everyone"` (creator only) |

### ConnectionSheetDialogs

| Dialog | Title | Body | Confirm | Dismiss |
|--------|-------|------|---------|---------|
| Remove | `"Remove Connection?"` | `"This will permanently remove this connection and all messages. This cannot be undone."` | `"Remove"` (error color) | `"Cancel"` |
| Block | `"Block User?"` | `"They won't be able to contact you and this connection will be removed. This cannot be undone."` | `"Block"` | `"Cancel"` |
| Report | `"Report User"` | `"Please describe the issue:"` + field | `"Submit"` (orange) | `"Cancel"` |
| Report placeholder | — | `"Reason for report..."` | — | — |
| Leave group | `"Leave group?"` | `"You will lose access to this verified click and its messages."` | `"Leave"` | `"Cancel"` |
| Delete group | `"Delete group?"` | `"Permanently deletes this verified click for everyone. This cannot be undone."` | `"Delete"` | `"Cancel"` |
| Remove member | `"Remove from group?"` | `"{memberName} will be removed from this verified click and lose access to its messages."` | `"Remove"` | `"Cancel"` |

### HubActionSheet

| Row | Title | Subtitle |
|-----|-------|----------|
| Leave | `"Leave Hub"` | `"Remove this hub from your list"` |
| Edit (creator) | `"Edit Hub"` | `"Update name and category"` |
| Delete (creator) | `"Delete Hub"` | `"Kick all users and delete history"` |
| Loading | — | `"Loading hub options…"` |

**Hub confirm dialogs:**

| Title | Body | Confirm |
|-------|------|---------|
| `"Leave hub?"` | `"You will leave this community hub and lose quick access from your Groups list."` | `"Leave"` |
| `"Delete hub?"` | `"Are you sure? This will kick all users and delete the history."` | `"Delete"` |

**Edit hub dialog:** title `"Edit Hub"`, fields `"Hub name"`, `"Category"`, confirm `"Save"`.

### ActiveHubFeedRow

| Element | String |
|---------|--------|
| Subtitle | `"{occupantCount} person"` / `"{occupantCount} people" • Community Hub` |
| Menu a11y | `"Hub options"` |

### Glass toast feedback (`ChatViewModel.nudgeResult`)

| Action | Message |
|--------|---------|
| Nudge sent | `"Nudge sent to {name}! 👋"` |
| Nudge failed | `"Failed to send nudge"` |
| Selection blocked | `"That friend isn't connected to everyone already selected."` |
| Archive | `"Connection archived"` |
| Unarchive | `"Connection unarchived"` |
| Add core | `"Added to Core"` / `"Couldn't add to Core"` |
| Remove core | `"Removed from Core"` / `"Couldn't remove from Core"` |
| Remove connection | `"Connection removed"` / `"Failed to remove connection"` |
| Block | `"User blocked"` / `"Could not block user"` |
| Report | `"Report submitted"` / `"Failed to submit report"` |
| Leave group | `"You left the group"` / `"Could not leave group"` |
| Delete group | `"Group deleted"` / `"Could not delete group"` |
| Hub leave | `"You left the hub"` |
| Hub delete | `"Hub deleted"` |

---

## 4. Micro-copy index (header & chrome)

| Key | String |
|-----|--------|
| Screen title | `"Clicks"` |
| Tab labels | `"Active"`, `"Groups"`, `"Archived"` |
| Tab with counts | `"Active ({n})"`, `"Groups ({n})"`, `"Archived ({n})"` |
| Filter chip a11y | `"Change filter"` |
| Error title | `"Error loading chats"` |
| Retry | `"Retry"` |
| Loading subtitle | `"Loading…"` |

---

## 5. Flow Sequence

### Open chat (Android)

```
Tap ConnectionItem
  → AnimatedContent slide: list out, ChatView in
  → Back / onBackPressed: slide reverse, finalizeChatClose after 300ms
```

### Open chat (iOS)

```
Tap ConnectionItem
  → Chat overlay slide in; list parallax peeks underneath
  → Edge swipe back: Gesture close (no message surface flash)
  → Tap back: Tap close with 300ms cleanup
```

### Long-press connection actions

```
Long-press ConnectionItem
  → ConnectionActionSheet
  → Tap action
    → Immediate: Nudge, Archive, Unarchive, Core, Mark unread
    → Deferred dialog: Remove, Block, Report, Leave/Delete group
  → ConnectionSheetDialogs confirm
  → GlassToastHost feedback
```

### Create verified click

```
Active tab → FAB
  → ConnectionMemberPickerSheet
  → Select friends (eligibility RPC)
  → Create
  → Success: "Click created" toast, sheet dismiss
  → Failure: error toast
```

### Proximity autofill (deep link / tap group)

```
verifiedCliqueProximityAutofill intent
  → Switch to Active tab, open create sheet
  → Preselect matched users, show "Loading your tap group in Clicks…"
  → Consume intent after inbox catches up (≤4.5s)
```

### Hub from Groups tab

```
Groups tab → ActiveHubFeedRow tap → hub chat (parent)
Long-press / ⋮ → HubActionSheet → Leave / Edit / Delete flows
```

---

## 6. A11y & Responsive

| Area | Behavior |
|------|----------|
| Connection rows | `connectionRowPressGestures` — tap vs long-press; iOS uses `detectTapGestures` + heavy haptic on long-press |
| Group avatar tap | Separate clickable target from row (opens members sheet) |
| Availability overlap | Bolt icon `contentDescription = "Shared availability"` |
| Header collapse | At `collapseFraction > 0.42` → compact pill with dropdown filter |
| Tab switch animation | 36dp horizontal offset + alpha fade (260ms / 220ms) |
| FAB + toast | Toast aligned end, FAB fixed 56dp; toast dismissed when list obscured by chat |
| List obscured (iOS) | Full-screen pointer consumer blocks list interaction under chat |
| Search | Filters `name` on peer or group; empty state copy distinct from tab empty |
| Pagination | Infinite scroll when within 4 items of end; disabled during active search |
| Sort order | Core connections pinned first, then `connectionListActivityTs` descending. New activity **reorders list data** (row moves toward top via stable keys). Do **not** call `animateScrollToItem(0)` on reorder — that cancelled fling; the viewport stays where the user scrolled |

**Focus order:** Floating header (title → search → tabs) → scrollable list → FAB.

**Screen reader:** Unread count spoken as part of row content (badge text). Dialogs use `GlassAlertDialog` with standard title/text/button roles.

---

## Related documents

- [05-home.md](05-home.md) — home nudges and recent connections
- [06-connect-handshake.md](06-connect-handshake.md) — how connections are created
- [08-chat.md](08-chat.md) — `ChatView` composer, bubbles, calls
- [10-map-beacons-hubs.md](10-map-beacons-hubs.md) — hub chat detail
