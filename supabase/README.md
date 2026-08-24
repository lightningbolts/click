# Supabase in the mobile repo

**Source of truth for shared schema + `bind-proximity-connection`:** the sibling **`click-web`** repository (`click-web/supabase/`).

This tree keeps:

| Path | Ownership |
|------|-----------|
| `functions/send-push-notification` | **Mobile** (APNs VoIP + FCM) |
| `functions/expire-availability-intents` | **Mobile** |
| `functions/expire-connections` | **Mobile** |
| `functions/verify-hub-proximity` | **Mobile** |
| `functions/bind-proximity-connection` | **Mirror of click-web** — must stay identical |
| `migrations/*.sql` | **Mirror subset** of click-web migrations — must stay identical when filenames overlap |

### Sync / drift

```bash
# Pull shared artifacts from ../click-web
bash scripts/sync-supabase-from-click-web.sh

# Fail CI/local if mirrors drifted
bash scripts/check-supabase-drift.sh
```

Deploy shared migrations and `bind-proximity-connection` from **click-web**. Deploy mobile-only functions from this repo.

Event-scaling migrations (`20260824010000`–`20260824060000`, plus guest RSVPs `20260823180000`) live only in **click-web** and are **already applied** on the live database (2026-08-23). The click-web public-event microsite app was reverted on 2026-08-24 so the split-large-files refactor could merge; the additive schema remains. This sync script copies overlapping filenames only — do not add those files here unless they already exist in both trees.
