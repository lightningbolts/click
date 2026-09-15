# PR #102 Connections Inbox Acceptance

Conversation rows use a 72pt minimum hit surface with 48pt one-to-one avatars and an immediate touch-down wash independent of platform ripple support. This is intentionally roomier than generic settings/search rows and restores the interaction signifier that regressed during the launch-polish pass.

A row press must become visible before route navigation begins, clear if the gesture is cancelled or leaves the row, and remain a single full-row interaction target. Avatar/group affordances may retain their own secondary actions, but they must not remove the parent row's press feedback.

The target is WhatsApp-like information density: compact enough to scan quickly, but not vertically compressed. Name, preview, timestamp, unread state, and avatar should remain visually separated without turning each row into a card.
