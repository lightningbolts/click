-- Migration: normalized user interest tags in public.user_interests
-- Run in the Supabase SQL Editor after reviewing. Replaces reliance on users.tags / auth metadata for onboarding gating.
--
-- Semantics:
--   - No row  = user has not completed the interest onboarding flow on a client that writes this table.
--   - Row exists (tags empty or not) = flow completed (including "skip" on web); clients should not re-prompt.
--   - Non-empty tags = interests for Common Ground / settings.

CREATE TABLE IF NOT EXISTS public.user_interests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL UNIQUE REFERENCES auth.users (id) ON DELETE CASCADE,
    tags text[] NOT NULL DEFAULT '{}',
    updated_at bigint NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_user_interests_user_id ON public.user_interests (user_id);

COMMENT ON TABLE public.user_interests IS 'Canonical interest tags and onboarding completion for interest selection (one row per auth user).';
COMMENT ON COLUMN public.user_interests.tags IS 'Self-selected interest labels; empty array is valid after skip.';
COMMENT ON COLUMN public.user_interests.updated_at IS 'Unix epoch milliseconds; client-written.';

-- RLS: users may only read/write their own row
ALTER TABLE public.user_interests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users can select own user_interests" ON public.user_interests;
DROP POLICY IF EXISTS "Users can insert own user_interests" ON public.user_interests;
DROP POLICY IF EXISTS "Users can update own user_interests" ON public.user_interests;

CREATE POLICY "Users can select own user_interests"
    ON public.user_interests
    FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own user_interests"
    ON public.user_interests
    FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update own user_interests"
    ON public.user_interests
    FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

GRANT SELECT, INSERT, UPDATE ON public.user_interests TO authenticated;
GRANT ALL ON public.user_interests TO service_role;

-- ---------------------------------------------------------------------------
-- Backfill from public.users.tags (non-empty)
-- ---------------------------------------------------------------------------
INSERT INTO public.user_interests (id, user_id, tags, updated_at)
SELECT gen_random_uuid(),
       u.id,
       u.tags,
       (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
FROM public.users u
WHERE u.tags IS NOT NULL
  AND cardinality(u.tags) > 0
  AND NOT EXISTS (
      SELECT 1 FROM public.user_interests ui WHERE ui.user_id = u.id
  );

-- ---------------------------------------------------------------------------
-- Backfill from auth.users.raw_user_meta_data->tags (json array) when no row yet
-- ---------------------------------------------------------------------------
INSERT INTO public.user_interests (id, user_id, tags, updated_at)
SELECT gen_random_uuid(),
       au.id,
       ARRAY(SELECT jsonb_array_elements_text(au.raw_user_meta_data -> 'tags')),
       (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
FROM auth.users au
WHERE jsonb_typeof(au.raw_user_meta_data -> 'tags') = 'array'
  AND jsonb_array_length(au.raw_user_meta_data -> 'tags') > 0
  AND NOT EXISTS (
      SELECT 1 FROM public.user_interests ui WHERE ui.user_id = au.id
  );

-- ---------------------------------------------------------------------------
-- Users who finished or skipped interest UI (tags_initialized) but have no row yet — insert empty row
-- ---------------------------------------------------------------------------
INSERT INTO public.user_interests (id, user_id, tags, updated_at)
SELECT gen_random_uuid(),
       u.id,
       COALESCE(u.tags, ARRAY[]::text[]),
       (EXTRACT(EPOCH FROM NOW()) * 1000)::bigint
FROM public.users u
WHERE COALESCE(u.tags_initialized, false) = true
  AND NOT EXISTS (
      SELECT 1 FROM public.user_interests ui WHERE ui.user_id = u.id
  );
