-- ====================================================================
-- Fix 4 + Fix 6: venue_connection_stats view, claimed_venues table,
--                user_blocks table, connection_reports table
-- Safe to run on live instance (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS)
-- ====================================================================

-- 1) claimed_venues — venues that have claimed their Insights dashboard
CREATE TABLE IF NOT EXISTS claimed_venues (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    venue_name TEXT NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES auth.users(id),
    semantic_location TEXT,
    created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

ALTER TABLE claimed_venues ENABLE ROW LEVEL SECURITY;

-- Owners can read/manage their own venues
CREATE POLICY IF NOT EXISTS "venue_owner_select" ON claimed_venues
    FOR SELECT USING (auth.uid() = owner_user_id);

CREATE POLICY IF NOT EXISTS "venue_owner_insert" ON claimed_venues
    FOR INSERT WITH CHECK (auth.uid() = owner_user_id);

-- 2) Aggregated view for Insights — NO user IDs exposed
CREATE OR REPLACE VIEW venue_connection_stats AS
SELECT
    c.semantic_location AS venue_name,
    COUNT(*) AS total_connections,
    COUNT(*) FILTER (WHERE c.expiry_state = 'kept') AS kept_connections,
    ROUND(
        COUNT(*) FILTER (WHERE c.expiry_state = 'kept')::numeric
        / NULLIF(COUNT(*), 0), 2
    ) AS kept_ratio,
    -- Connections per day (last 30 days)
    jsonb_agg(
        DISTINCT jsonb_build_object(
            'date', to_char(to_timestamp(c.created / 1000), 'YYYY-MM-DD'),
            'count', 1
        )
    ) FILTER (
        WHERE c.created > (EXTRACT(EPOCH FROM NOW()) * 1000 - 30 * 86400000)::BIGINT
    ) AS daily_raw,
    -- Peak hour
    MODE() WITHIN GROUP (
        ORDER BY EXTRACT(HOUR FROM to_timestamp(c.created / 1000))
    )::int AS peak_hour,
    -- Hourly distribution (0-23)
    jsonb_object_agg(
        EXTRACT(HOUR FROM to_timestamp(c.created / 1000))::text,
        1
    ) AS hourly_raw
FROM connections c
WHERE c.semantic_location IS NOT NULL
GROUP BY c.semantic_location;

-- 3) user_blocks — block list (Fix 6)
CREATE TABLE IF NOT EXISTS user_blocks (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES auth.users(id),
    blocked_id UUID NOT NULL REFERENCES auth.users(id),
    created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    UNIQUE(blocker_id, blocked_id)
);

ALTER TABLE user_blocks ENABLE ROW LEVEL SECURITY;

CREATE POLICY IF NOT EXISTS "blocker_select" ON user_blocks
    FOR SELECT USING (auth.uid() = blocker_id);

CREATE POLICY IF NOT EXISTS "blocker_insert" ON user_blocks
    FOR INSERT WITH CHECK (auth.uid() = blocker_id);

CREATE POLICY IF NOT EXISTS "blocker_delete" ON user_blocks
    FOR DELETE USING (auth.uid() = blocker_id);

-- Blocked users cannot SELECT rows where they are blocked_id (RLS above is blocker-only).
-- For client snapshots that must hide connections when the peer blocked you, apply
-- add_blockers_for_blocked_user_rpc.sql (SECURITY DEFINER RPC blockers_for_blocked_user).

-- 4) connection_reports — report system (Fix 6)
CREATE TABLE IF NOT EXISTS connection_reports (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    connection_id UUID NOT NULL,
    reporter_id UUID NOT NULL REFERENCES auth.users(id),
    reason TEXT NOT NULL,
    created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

ALTER TABLE connection_reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY IF NOT EXISTS "reporter_insert" ON connection_reports
    FOR INSERT WITH CHECK (auth.uid() = reporter_id);

-- Service role can read reports for moderation
CREATE POLICY IF NOT EXISTS "service_read_reports" ON connection_reports
    FOR SELECT USING (auth.role() = 'service_role');

-- 5) waitlist table — for Fix 5 deep-link waitlist capture
CREATE TABLE IF NOT EXISTS waitlist (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    source TEXT DEFAULT 'website',
    referrer_user_id UUID,
    created_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

ALTER TABLE waitlist ENABLE ROW LEVEL SECURITY;

-- Anyone can insert (public form), only service role reads
CREATE POLICY IF NOT EXISTS "waitlist_public_insert" ON waitlist
    FOR INSERT WITH CHECK (true);

CREATE POLICY IF NOT EXISTS "waitlist_service_select" ON waitlist
    FOR SELECT USING (auth.role() = 'service_role');
