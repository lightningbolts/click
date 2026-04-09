-- Auto-archive stale connections via per-user junction rows (connection_archives).
-- Run after connection_archives and connection_hidden exist.
-- If ALTER PUBLICATION fails because a table is already in supabase_realtime, drop that line and re-run.
-- Does NOT set connections.status to 'archived'.
--
-- Rules:
--   • Only connections with status IN ('pending', 'active') — excludes 'kept', 'archived', 'removed'.
--   • Rule A: last_message_at IS NULL AND age since created > 48 hours.
--   • Rule B: last_message_at IS NOT NULL AND age since last_message_at > 7 days.
--   • Inserts one connection_archives row per user in connections.user_ids (both users).
--
-- Realtime: expose junction tables to clients (idempotent if already added).

ALTER PUBLICATION supabase_realtime ADD TABLE public.connection_archives;
ALTER PUBLICATION supabase_realtime ADD TABLE public.connection_hidden;

-- Return type (or body) changes are not applied by CREATE OR REPLACE; drop first.
DROP FUNCTION IF EXISTS public.auto_archive_stale_connections();

CREATE FUNCTION public.auto_archive_stale_connections()
RETURNS INTEGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    inserted integer;
BEGIN
    WITH stale AS (
        SELECT c.id,
               c.user_ids
        FROM public.connections c
        WHERE c.status IN ('pending', 'active')
          AND (
              (
                  c.last_message_at IS NULL
                  AND c.created < (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT - (48 * 3600 * 1000)
              )
              OR (
                  c.last_message_at IS NOT NULL
                  AND c.last_message_at < (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::BIGINT - (7 * 24 * 3600 * 1000)
              )
          )
    ),
    pairs AS (
        SELECT s.id AS connection_id,
               u.uid AS user_id
        FROM stale s
        CROSS JOIN LATERAL (
            SELECT unnest(s.user_ids)::uuid AS uid
        ) u
    )
    INSERT INTO public.connection_archives (user_id, connection_id)
    SELECT p.user_id, p.connection_id
    FROM pairs p
    ON CONFLICT (user_id, connection_id) DO NOTHING;

    GET DIAGNOSTICS inserted = ROW_COUNT;
    RETURN inserted;
END;
$$;

COMMENT ON FUNCTION public.auto_archive_stale_connections() IS
    'Inserts connection_archives for both users on stale pending/active connections (48h no message or 7d idle).';

GRANT EXECUTE ON FUNCTION public.auto_archive_stale_connections() TO service_role;
