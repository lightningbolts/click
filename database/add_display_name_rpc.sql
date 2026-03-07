-- Resolve display names without relying on the Python backend.
-- This function prefers public.users.full_name/name, then falls back to auth.users metadata.

CREATE OR REPLACE FUNCTION public.get_user_display_names(user_ids text[])
RETURNS TABLE (
    id text,
    display_name text,
    email text,
    image text,
    last_polled bigint
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
BEGIN
    RETURN QUERY
    SELECT
        requested.user_id,
        COALESCE(
            NULLIF(NULLIF(BTRIM(u.full_name), ''), 'Connection'),
            NULLIF(NULLIF(BTRIM(u.name), ''), 'Connection'),
            NULLIF(NULLIF(BTRIM(au.raw_user_meta_data ->> 'full_name'), ''), 'Connection'),
            NULLIF(NULLIF(BTRIM(au.raw_user_meta_data ->> 'name'), ''), 'Connection'),
            NULLIF(BTRIM(split_part(COALESCE(u.email, au.email, ''), '@', 1)), ''),
            'Connection'
        ) AS display_name,
        COALESCE(u.email, au.email) AS email,
        u.image,
        u.last_polled
    FROM UNNEST(user_ids) AS requested(user_id)
    LEFT JOIN public.users u ON u.id::text = requested.user_id
    LEFT JOIN auth.users au ON au.id::text = requested.user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.get_user_display_names(text[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_user_display_names(text[]) TO authenticated;