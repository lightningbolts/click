-- Profile mirror of active availability intents + timestamp for 24h server expiry.
-- The app updates these when the user saves intents; Edge Function expire-availability-intents clears stale rows.

ALTER TABLE users ADD COLUMN IF NOT EXISTS last_intent_update_at timestamptz;
ALTER TABLE users ADD COLUMN IF NOT EXISTS availability_intents jsonb DEFAULT '[]'::jsonb;

COMMENT ON COLUMN users.last_intent_update_at IS 'When the user last changed availability intents; used to expire profile intents after 24h.';
COMMENT ON COLUMN users.availability_intents IS 'Denormalized active intent bubbles for profile discovery (JSON array of {intent_tag, timeframe, expires_at}).';

CREATE INDEX IF NOT EXISTS idx_users_last_intent_update_at
    ON users(last_intent_update_at)
    WHERE last_intent_update_at IS NOT NULL;
