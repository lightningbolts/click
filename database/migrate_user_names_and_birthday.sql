-- Migration: split display name into first_name / last_name and add birthday (date).
-- Run in Supabase SQL Editor after review.
--
-- 1) Adds columns on public.users
-- 2) Backfills first_name / last_name by splitting legacy COALESCE(full_name, name)
-- 3) Replaces handle_new_user + trigger to read first_name, last_name, birthday from raw_user_meta_data
-- 4) Refreshes get_user_display_names to prefer concatenated first/last

-- ---------------------------------------------------------------------------
-- Schema: ensure full_name exists (used by app / RPC fallbacks)
-- ---------------------------------------------------------------------------
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS full_name TEXT;

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS first_name TEXT,
    ADD COLUMN IF NOT EXISTS last_name TEXT,
    ADD COLUMN IF NOT EXISTS birthday DATE;

COMMENT ON COLUMN public.users.first_name IS 'Given name; mirrored from auth user_metadata.first_name.';
COMMENT ON COLUMN public.users.last_name IS 'Family name; mirrored from auth user_metadata.last_name.';
COMMENT ON COLUMN public.users.birthday IS 'Date of birth from signup metadata; NULL for legacy/OAuth users.';

-- ---------------------------------------------------------------------------
-- Backfill: split existing single-string name (prefer full_name when set)
-- ---------------------------------------------------------------------------
UPDATE public.users u
SET
    first_name = v.first_name,
    last_name = v.last_name
FROM (
    SELECT
        id,
        CASE
            WHEN src IS NULL OR src = '' THEN NULL
            WHEN position(' ' IN src) = 0 THEN src
            ELSE split_part(src, ' ', 1)
        END AS first_name,
        CASE
            WHEN src IS NULL OR src = '' THEN NULL
            WHEN position(' ' IN src) = 0 THEN NULL
            ELSE NULLIF(
                BTRIM(substring(src FROM length(split_part(src, ' ', 1)) + 2)),
                ''
            )
        END AS last_name
    FROM (
        SELECT
            id,
            NULLIF(
                BTRIM(COALESCE(NULLIF(BTRIM(full_name), ''), NULLIF(BTRIM(name), ''))),
                ''
            ) AS src
        FROM public.users
    ) s
) v
WHERE u.id = v.id
  AND (u.first_name IS DISTINCT FROM v.first_name OR u.last_name IS DISTINCT FROM v.last_name);

-- Keep legacy name / full_name columns populated for older clients until cleanup.
UPDATE public.users u
SET
    name = COALESCE(NULLIF(BTRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.name),
    full_name = COALESCE(NULLIF(BTRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.full_name, u.name)
WHERE u.first_name IS NOT NULL
   OR u.last_name IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Auth → public.users sync (Supabase: trigger on auth.users)
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    meta    jsonb := COALESCE(NEW.raw_user_meta_data, '{}'::jsonb);
    v_first text := NULLIF(BTRIM(meta ->> 'first_name'), '');
    v_last  text := NULLIF(BTRIM(meta ->> 'last_name'), '');
    v_birth date;
    v_display text;
BEGIN
    IF meta ? 'birthday'
       AND (meta ->> 'birthday') IS NOT NULL
       AND (meta ->> 'birthday') ~ '^\d{4}-\d{2}-\d{2}$'
    THEN
        v_birth := (meta ->> 'birthday')::date;
    ELSE
        v_birth := NULL;
    END IF;

    v_display := NULLIF(BTRIM(CONCAT_WS(' ', v_first, v_last)), '');
    IF v_display IS NULL THEN
        v_display := COALESCE(
            NULLIF(BTRIM(meta ->> 'full_name'), ''),
            NULLIF(BTRIM(meta ->> 'name'), ''),
            NULLIF(BTRIM(split_part(NEW.email, '@', 1)), ''),
            'User'
        );
    END IF;

    INSERT INTO public.users (
        id,
        email,
        name,
        full_name,
        first_name,
        last_name,
        birthday,
        created_at
    )
    VALUES (
        NEW.id,
        NEW.email,
        v_display,
        v_display,
        v_first,
        v_last,
        v_birth,
        (EXTRACT(EPOCH FROM clock_timestamp()) * 1000)::bigint
    )
    ON CONFLICT (id) DO UPDATE SET
        email      = EXCLUDED.email,
        name       = EXCLUDED.name,
        full_name  = EXCLUDED.full_name,
        first_name = COALESCE(EXCLUDED.first_name, public.users.first_name),
        last_name  = COALESCE(EXCLUDED.last_name, public.users.last_name),
        birthday   = COALESCE(EXCLUDED.birthday, public.users.birthday);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE PROCEDURE public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Display name RPC: prefer first + last (table then auth metadata)
-- ---------------------------------------------------------------------------
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
            NULLIF(
                NULLIF(BTRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                'Connection'
            ),
            NULLIF(NULLIF(BTRIM(to_jsonb(u) ->> 'full_name'), ''), 'Connection'),
            NULLIF(NULLIF(BTRIM(to_jsonb(u) ->> 'name'), ''), 'Connection'),
            NULLIF(
                NULLIF(
                    BTRIM(
                        CONCAT_WS(
                            ' ',
                            NULLIF(BTRIM(au.raw_user_meta_data ->> 'first_name'), ''),
                            NULLIF(BTRIM(au.raw_user_meta_data ->> 'last_name'), '')
                        )
                    ),
                    ''
                ),
                'Connection'
            ),
            NULLIF(NULLIF(BTRIM(au.raw_user_meta_data ->> 'full_name'), ''), 'Connection'),
            NULLIF(NULLIF(BTRIM(au.raw_user_meta_data ->> 'name'), ''), 'Connection'),
            NULLIF(BTRIM(split_part(COALESCE(to_jsonb(u) ->> 'email', au.email, ''), '@', 1)), ''),
            'Connection'
        ) AS display_name,
        COALESCE(to_jsonb(u) ->> 'email', au.email) AS email,
        to_jsonb(u) ->> 'image' AS image,
        CASE
            WHEN (to_jsonb(u) ->> 'last_polled') IS NULL THEN NULL
            ELSE (to_jsonb(u) ->> 'last_polled')::bigint
        END AS last_polled
    FROM UNNEST(user_ids) AS requested(user_id)
    LEFT JOIN public.users u ON u.id::text = requested.user_id
    LEFT JOIN auth.users au ON au.id::text = requested.user_id;
END;
$$;

REVOKE ALL ON FUNCTION public.get_user_display_names(text[]) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_user_display_names(text[]) TO authenticated;
