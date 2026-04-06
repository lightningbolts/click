-- Migration: Canonical connection `status`, archive (no hard-delete) sweeps, 12h warning dedupe
-- Safe for idempotent apply: IF NOT EXISTS / OR REPLACE where applicable.
--
-- Status values: pending | active | kept | archived | removed
-- Legacy `expiry_state` is kept in sync from `status` for older clients / SQL views.

-- =============================================================================
-- 1. Columns
-- =============================================================================

ALTER TABLE public.connections
    ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'pending';

ALTER TABLE public.connections
    ADD COLUMN IF NOT EXISTS archive_warning_pending_sent_at BIGINT;

ALTER TABLE public.connections
    ADD COLUMN IF NOT EXISTS archive_warning_idle_sent_at BIGINT;

-- Backfill status from legacy expiry_state when status still default-only
UPDATE public.connections
SET status = CASE
    WHEN expiry_state = 'kept' THEN 'kept'
    WHEN expiry_state = 'active' THEN 'active'
    WHEN expiry_state = 'expired' THEN 'archived'
    ELSE 'pending'
END
WHERE status = 'pending'
  AND expiry_state IS NOT NULL;

COMMENT ON COLUMN public.connections.status IS
    'Lifecycle: pending (Say Hi window), active (7-day rolling), kept (mutual), archived (auto-idle), removed (user soft-delete)';
COMMENT ON COLUMN public.connections.archive_warning_pending_sent_at IS
    'Unix ms when 12h pending-archive warning push was last sent (dedupe)';
COMMENT ON COLUMN public.connections.archive_warning_idle_sent_at IS
    'Unix ms when 12h idle-archive warning push was last sent (dedupe)';

CREATE INDEX IF NOT EXISTS idx_connections_status ON public.connections(status);

-- =============================================================================
-- 2. Keep expiry_state aligned for legacy readers
-- =============================================================================

CREATE OR REPLACE FUNCTION public.sync_connection_expiry_state_from_status()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('pending', 'active', 'kept') THEN
        NEW.expiry_state := NEW.status;
    ELSIF NEW.status IN ('archived', 'removed') THEN
        NEW.expiry_state := 'expired';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_expiry_from_status ON public.connections;
CREATE TRIGGER trg_sync_expiry_from_status
    BEFORE INSERT OR UPDATE OF status ON public.connections
    FOR EACH ROW
    EXECUTE FUNCTION public.sync_connection_expiry_state_from_status();

-- =============================================================================
-- 3. Insights view: prefer canonical `status` for “kept” counts (trigger still syncs expiry_state)
-- =============================================================================

CREATE OR REPLACE VIEW venue_connection_stats AS
SELECT
    c.semantic_location AS venue_name,
    COUNT(*) AS total_connections,
    COUNT(*) FILTER (WHERE c.status = 'kept') AS kept_connections,
    ROUND(
        COUNT(*) FILTER (WHERE c.status = 'kept')::numeric
        / NULLIF(COUNT(*), 0), 2
    ) AS kept_ratio,
    jsonb_agg(
        DISTINCT jsonb_build_object(
            'date', to_char(to_timestamp(c.created / 1000), 'YYYY-MM-DD'),
            'count', 1
        )
    ) FILTER (
        WHERE c.created > (EXTRACT(EPOCH FROM NOW()) * 1000 - 30 * 86400000)::BIGINT
    ) AS daily_raw,
    MODE() WITHIN GROUP (
        ORDER BY EXTRACT(HOUR FROM to_timestamp(c.created / 1000))
    )::int AS peak_hour,
    jsonb_object_agg(
        EXTRACT(HOUR FROM to_timestamp(c.created / 1000))::text,
        1
    ) AS hourly_raw
FROM connections c
WHERE c.semantic_location IS NOT NULL
GROUP BY c.semantic_location;

-- =============================================================================
-- 4. pg_cron: schedule archive sweep (enable extension in Dashboard first)
-- =============================================================================

-- SELECT cron.schedule(
--     'archive-connections-job',
--     '*/15 * * * *',
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
