# Collaboration Module

## Module Purpose

The `collaboration` package implements **re-engagement sessions** when existing friends bump phones again (`is_new_connection = false`). It powers two user-facing features: **Disposable Roll** (time-locked photo capture that reveals after TTL) and **Squad Map Drops** (temporary map multiplier for the friend group). Client state is held in `CollaborationSessionManager`; durable records live in Supabase `collaboration_sessions` with hourly cron reveal.

---

## Architecture

### CollaborationSessionManager

In-memory `StateFlow<Map<String, CollaborationSession>>` keyed by `connectionId` or `chat:{chatId}`:

```kotlin
data class CollaborationSession(
    val encounterId: String,       // collaboration_sessions.id
    val connectionId: String,
    val chatId: String?,
    val collaborationTtlIso: String,
    val createdAtEpochMs: Long,
)
```

**Activation:** `bind-proximity-connection` Edge Function returns `collaboration_ttl` when `is_new_connection == false`. `ConnectionViewModel` / `App.kt` calls `CollaborationSessionManager.activate(session)`.

**Lookup helpers:**
- `forConnection(connectionId)` — 1:1 disposable roll entry point
- `forChat(chatId)` — group chat disposable roll
- `activeMapDropSession()` — first session where `isMapDropEligible()` (within 15 min of bump)

**TTL helpers:**
- `isRollActive(now)` — `now < collaborationTtlInstant()`
- `isMapDropEligible(now)` — roll active AND age `< MAP_DROP_WINDOW_MS` (15 minutes)

### Disposable roll TTL

Disposable Roll photos are messages with metadata:

```json
{
  "disposable_roll": "true",
  "encounter_id": "{collaboration_sessions.id}",
  "collaboration_ttl": "{ISO8601}"
}
```

**Client TTL computation** (`ClickDropReveal.kt`):

- `computeClickDropRevealTtlIso()` — **24 hours** after send (`now + 24h`)
- `clickDropRevealDelayMs()` — millis until reveal for UI countdown

Server `collaboration_sessions.collaboration_ttl` is set at bump time by `bind-proximity-connection` (typically aligned with the disposable window).

While `isRollActive()`:
- `DisposableCameraView` is available from Connections chat action sheet
- Map shows squad drop multiplier icon when `activeMapDropSession()` is non-null

### `collaboration_sessions` schema

```sql
CREATE TABLE public.collaboration_sessions (
  id UUID PRIMARY KEY,
  connection_id UUID NOT NULL REFERENCES connections(id),
  chat_id UUID REFERENCES chats(id),
  collaboration_ttl TIMESTAMPTZ NOT NULL,
  participant_user_ids TEXT[] NOT NULL,
  notification_sent BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

- RLS: participants may `SELECT` rows where `auth.uid()` ∈ `participant_user_ids`
- Index `collaboration_sessions_reveal_cron_idx` on `collaboration_ttl WHERE notification_sent = FALSE`

Inserted by:
- `bind-proximity-connection` Edge Function (existing-friend bump)
- `click-web/lib/collaboration/createCollaborationSession.ts` (web parity)

### `cron-hourly-maintenance` reveal

Hourly pg_cron invokes `cron-hourly-maintenance` Edge Function:

```
1. SELECT collaboration_sessions WHERE collaboration_ttl <= now() AND notification_sent = false
2. For each session:
   a. Check messages WHERE metadata.disposable_roll = true
      AND metadata.encounter_id = session.id
      AND metadata.collaboration_ttl <= now()
   b. If revealed → send-push-notification per participant:
      { type: "disposable_reveal", encounter_id, connection_id, chat_id }
   c. UPDATE notification_sent = true
```

Also runs: event beacon day-of / one-hour reminders, expired availability intent friction logging.

**Client handling:** `App.kt` observes disposable reveal pushes → navigates to chat → message unblurs in timeline.

### End-to-end flow

```
Existing friends bump (Tri-Factor)
        │
        ▼
bind-proximity-connection → is_new_connection = false
        ├─ INSERT collaboration_sessions
        └─ Return collaboration_ttl + encounter_id
        │
        ▼
CollaborationSessionManager.activate()
        │
        ├─ User opens Disposable Roll camera
        │     └─ Photo sent as encrypted message with collaboration metadata
        │
        ├─ Map: squad drop eligible for 15 min
        │
        └─ After collaboration_ttl:
              cron-hourly-maintenance → disposable_reveal push
              └─ Photo visible in chat timeline
```

---

## Constraints

- **In-memory sessions** — `CollaborationSessionManager` resets on process death; re-hydrate from server on next bump or chat open.
- **Participant-only RLS** — users cannot read sessions they don't belong to.
- **Reveal is server-driven** — client does not unblur until cron confirms TTL + message exists.
- **One notification per session** — `notification_sent` prevents duplicate reveal pushes.
- **Map drop window** — 15 minutes from `createdAtEpochMs`, independent of 24h reveal TTL.
- **Group support** — `chat_id` column added in `20260628163100_collaboration_sessions_chat_id.sql` for verified cliques.

---

## Related Files

| File | Role |
|------|------|
| `collaboration/CollaborationSessionManager.kt` | In-memory session store |
| `collaboration/ClickDropReveal.kt` | 24h TTL helpers |
| `ui/camera/DisposableCameraView.kt` | Roll capture UI |
| `ui/components/ConnectionRevealOverlay.kt` | Reveal animation |
| `viewmodel/ConnectionViewModel.kt` | Activates session on re-bump |
| `App.kt` | `openConnectionDisposableRoll()`, reveal routing |
| `supabase/functions/bind-proximity-connection/index.ts` | Session insert |
| `supabase/migrations/20260605120000_collaboration_sessions.sql` | Schema |
| `click-web/supabase/functions/cron-hourly-maintenance/index.ts` | Hourly reveal cron |
| `click-web/lib/collaboration/createCollaborationSession.ts` | Web session creation |

---

## What Click Users Experience

Click is a proximity-first social app for real-world connection. Every feature below is part of the product surface this module enables.

### Connection & Discovery
- **Connect in person (Tri-Factor)** — Bump phones using ultrasonic audio tokens + BLE + sensor context to verify co-presence.
- **Scan QR** — Scan a peer's QR code to initiate a connection handshake.
- **Group connect (Multi-Tap)** — Several people tap together to form a verified group ("click").
- **QR identity card** — Share your personal QR / link so others can connect without bumping.
- **Community Hubs** — Join ephemeral venue chats tied to a place.
- **Map beacons** — Discover people and events pinned on the social map.
- **Match alerts** — Get notified when availability and interests align with someone nearby.
- **Availability intents** — Signal when you're free this week; others can lock overlapping gaps.
- **Core connections** — Pin your most important people; they stay visible even in ghost mode on the map.

### Messaging & Calls
- **Private encrypted chat** — End-to-end encrypted direct and group threads.
- **Send photos/files/voice notes** — Rich media in chat with encrypted upload.
- **Emoji reactions** — React to individual messages.
- **Typing & read receipts** — Live presence indicators in conversations.
- **Voice & video calls** — In-app WebRTC calls with push-wake on incoming rings.

### Memory & Context
- **Memory Capsules** — Rich encounter records: place, weather, noise, elevation, motion, lux.
- **48-hour gentle archive** — Connections softly archive after 48 hours unless both parties continue.
- **Connection map & timeline** — See where and when you met someone on a map and timeline.
- **Rate the vibe** — Post-connection sentiment feedback.

### Collaboration
- **Collaboration sessions & disposable rolls** — Re-bump existing friends to open a time-locked Disposable Roll camera window and squad map drops.

### Privacy & Safety
- **Ghost mode** — Pause location sharing and background sync for a private session.
- **Block & report** — Block users and report abusive behavior.

### Profile & Account
- **Profile & interests** — Avatar, name, birthday, and interest tags power discovery and matching.
- **Onboarding** — Welcome → interests → avatar flow for new accounts.
- **Google/email auth** — Sign in with Google OAuth or email/password via Supabase Auth.
- **Push notifications** — Message, call, archive, and reveal alerts.
- **Deep links & App Clip** — Open connections via `https://click-us.vercel.app/c/{id}` or `click://` URLs.
- **Web dashboard** — Manage connections and insights from the browser.
- **Business insights** — Opt-in analytics for venue/business partners.
- **Event reminders** — Day-of and one-hour-before beacon reminders.
- **Achievements & stats** — Connection streaks, stats, and milestones.

### Discovery & Search
- **Global search** — Search people, chats, hubs, and map content from one entry point.
