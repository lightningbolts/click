-- Migration: Add server-side expiry state tracking to connections
-- Safe to run on live instance — all statements use IF NOT EXISTS / ADD COLUMN IF NOT EXISTS
-- Run this in the Supabase SQL Editor

-- =============================================================================
-- 1. Add new columns to connections table
-- =============================================================================

-- expiry_state tracks the lifecycle: 'pending' → 'active' → 'kept' or 'expired'
ALTER TABLE connections ADD COLUMN IF NOT EXISTS expiry_state TEXT DEFAULT 'pending';

-- last_message_at tracks when the most recent message was sent in this connection's chat
ALTER TABLE connections ADD COLUMN IF NOT EXISTS last_message_at BIGINT;

-- Create index for expiry queries (the Edge Function filters on these)
CREATE INDEX IF NOT EXISTS idx_connections_expiry_state ON connections(expiry_state);
CREATE INDEX IF NOT EXISTS idx_connections_last_message_at ON connections(last_message_at);

-- =============================================================================
-- 2. Backfill existing connections with correct expiry_state
-- =============================================================================

-- Connections where both users opted to keep → 'kept'
UPDATE connections
SET expiry_state = 'kept'
WHERE should_continue[1] = true
  AND should_continue[2] = true
  AND (expiry_state IS NULL OR expiry_state = 'pending');

-- Connections where chat has begun (messages exchanged) → 'active'
UPDATE connections
SET expiry_state = 'active'
WHERE has_begun = true
  AND (expiry_state IS NULL OR expiry_state = 'pending');

-- Everything else stays 'pending' (the default)

-- =============================================================================
-- 3. Trigger: auto-update last_message_at when a message is inserted
-- =============================================================================

-- Function to update the parent connection's last_message_at timestamp
CREATE OR REPLACE FUNCTION update_connection_last_message_at()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE connections
    SET last_message_at = NEW.time_created
    WHERE id = (
        SELECT c.connection_id
        FROM chats c
        WHERE c.id = NEW.chat_id
        LIMIT 1
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Drop existing trigger if present to avoid duplicates
DROP TRIGGER IF EXISTS trigger_update_last_message_at ON messages;

-- Create trigger on messages table
CREATE TRIGGER trigger_update_last_message_at
    AFTER INSERT ON messages
    FOR EACH ROW
    EXECUTE FUNCTION update_connection_last_message_at();

-- =============================================================================
-- 4. Backfill last_message_at from existing messages
-- =============================================================================

UPDATE connections c
SET last_message_at = sub.max_time
FROM (
    SELECT ch.connection_id, MAX(m.time_created) AS max_time
    FROM messages m
    JOIN chats ch ON m.chat_id = ch.id
    GROUP BY ch.connection_id
) sub
WHERE c.id = sub.connection_id
  AND c.last_message_at IS NULL;

-- =============================================================================
-- 5. RLS: Allow service_role to delete expired connections
--    (Edge Functions use service_role key, which bypasses RLS by default,
--     but we add an explicit DELETE policy for completeness)
-- =============================================================================

DROP POLICY IF EXISTS "Users can delete their own connections" ON connections;
CREATE POLICY "Users can delete their own connections"
    ON connections FOR DELETE
    USING (auth.uid()::text = ANY(user_ids));

-- =============================================================================
-- 6. pg_cron schedule (requires pg_cron extension enabled in Supabase Dashboard)
--    Uncomment after enabling pg_cron in Database → Extensions
-- =============================================================================

-- To enable: go to Supabase Dashboard → Database → Extensions → search "pg_cron" → Enable
--
-- SELECT cron.schedule(
--     'expire-connections-job',
--     '*/15 * * * *',  -- Every 15 minutes
--     $$
--     SELECT net.http_post(
--         url := current_setting('app.settings.supabase_url') || '/functions/v1/expire-connections',
--         headers := jsonb_build_object(
--             'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key'),
--             'Content-Type', 'application/json'
--         ),
--         body := '{}'::jsonb
--     );
--     $$
-- );

-- =============================================================================
-- 7. Grant permissions
-- =============================================================================

GRANT ALL ON connections TO authenticated;

-- Comments for clarity
COMMENT ON COLUMN connections.expiry_state IS 'Connection lifecycle state: pending | active | kept | expired';
COMMENT ON COLUMN connections.last_message_at IS 'Unix timestamp (ms) of the most recent message in this connection chat';
