-- Migration: Wingman introduction suggestions via graph query
-- Returns pairs of users (A, C) who are both connected to the current user (B)
-- but NOT connected to each other, filtering by at least 2 shared interest tags.
--
-- Usage:  SELECT * FROM get_wingman_suggestions('current-user-uuid');

CREATE OR REPLACE FUNCTION public.get_wingman_suggestions(current_user_id UUID)
RETURNS TABLE (
    user_a_id   UUID,
    user_a_name TEXT,
    user_c_id   UUID,
    user_c_name TEXT,
    shared_tags TEXT[]
)
LANGUAGE sql
STABLE
SECURITY DEFINER
AS $$
    WITH my_connections AS (
        -- All user IDs directly connected to the current user (B)
        SELECT UNNEST(c.user_ids)::UUID AS peer_id
        FROM   public.connections c
        WHERE  current_user_id = ANY(c.user_ids)
          AND  c.expiry_state IN ('active', 'kept')
    ),
    peers AS (
        -- Deduplicated peer list excluding B themselves
        SELECT DISTINCT peer_id
        FROM   my_connections
        WHERE  peer_id <> current_user_id
    ),
    peer_pairs AS (
        -- Every unordered pair (A, C) among B's peers
        SELECT a.peer_id AS a_id,
               c.peer_id AS c_id
        FROM   peers a
        JOIN   peers c ON a.peer_id < c.peer_id   -- unordered: avoid duplicates
    ),
    not_connected AS (
        -- Keep only pairs that do NOT already share a connection row
        SELECT pp.a_id, pp.c_id
        FROM   peer_pairs pp
        WHERE  NOT EXISTS (
            SELECT 1
            FROM   public.connections cx
            WHERE  pp.a_id = ANY(cx.user_ids)
              AND  pp.c_id = ANY(cx.user_ids)
        )
    ),
    with_tags AS (
        -- Join interest tags for each side of the pair
        SELECT nc.a_id,
               nc.c_id,
               COALESCE(ia.tags, ARRAY[]::TEXT[]) AS a_tags,
               COALESCE(ic.tags, ARRAY[]::TEXT[]) AS c_tags
        FROM   not_connected nc
        LEFT JOIN public.user_interests ia ON ia.user_id = nc.a_id
        LEFT JOIN public.user_interests ic ON ic.user_id = nc.c_id
    ),
    filtered AS (
        -- Compute shared tags and keep pairs with >= 2
        SELECT wt.a_id,
               wt.c_id,
               (SELECT ARRAY_AGG(t) FROM UNNEST(wt.a_tags) AS t WHERE t = ANY(wt.c_tags)) AS shared
        FROM   with_tags wt
    )
    SELECT f.a_id                          AS user_a_id,
           COALESCE(ua.name, 'Connection') AS user_a_name,
           f.c_id                          AS user_c_id,
           COALESCE(uc.name, 'Connection') AS user_c_name,
           f.shared                        AS shared_tags
    FROM   filtered f
    LEFT JOIN public.users ua ON ua.id = f.a_id
    LEFT JOIN public.users uc ON uc.id = f.c_id
    WHERE  COALESCE(ARRAY_LENGTH(f.shared, 1), 0) >= 2
    ORDER BY ARRAY_LENGTH(f.shared, 1) DESC;
$$;

COMMENT ON FUNCTION public.get_wingman_suggestions(UUID) IS
    'Wingman graph query: finds pairs among the caller''s connections who are NOT '
    'connected to each other and share >= 2 interest tags.';

GRANT EXECUTE ON FUNCTION public.get_wingman_suggestions(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.get_wingman_suggestions(UUID) TO service_role;
