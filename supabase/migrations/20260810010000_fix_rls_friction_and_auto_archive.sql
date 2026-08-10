-- Mirror of click-web migration: friction grants + auto_archive orphan-safe insert.
-- See click-web/supabase/migrations/20260810010000_fix_rls_friction_and_auto_archive.sql

REVOKE ALL ON TABLE public.system_friction_logs FROM PUBLIC;
REVOKE ALL ON TABLE public.system_friction_logs FROM anon;
REVOKE ALL ON TABLE public.system_friction_logs FROM authenticated;
GRANT SELECT, INSERT ON TABLE public.system_friction_logs TO service_role;

CREATE OR REPLACE FUNCTION public.auto_archive_stale_connections()
RETURNS integer
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO 'public'
AS $function$
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
    INNER JOIN public.users usr ON usr.id = p.user_id
    ON CONFLICT (user_id, connection_id) DO NOTHING;

    GET DIAGNOSTICS inserted = ROW_COUNT;
    RETURN inserted;
END;
$function$;
