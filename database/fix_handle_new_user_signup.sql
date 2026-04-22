-- Fix: signup failed with "database error saving new user" because handle_new_user()
-- referenced public.users.created_at (bigint), which does not exist — the table uses
-- createdAt timestamp with DEFAULT now().
-- Run this in the Supabase SQL Editor once to update the live trigger function.

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
        birthday
    )
    VALUES (
        NEW.id,
        NEW.email,
        v_display,
        v_display,
        v_first,
        v_last,
        v_birth
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
