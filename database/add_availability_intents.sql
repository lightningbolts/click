-- Intent-based availability windows ([public.availability_intents]).
-- Run in Supabase SQL Editor if the app shows errors when posting "Share availability".
--
-- App inserts: user_id, intent_tag, timeframe, starts_at, ends_at, expires_at
-- (expires_at mirrors ends_at; a trigger below keeps them aligned if expires_at is omitted.)

CREATE TABLE IF NOT EXISTS availability_intents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    intent_tag TEXT NOT NULL,
    timeframe TEXT NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Align legacy or partial tables with the app.
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS intent_tag TEXT;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS timeframe TEXT;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS starts_at TIMESTAMPTZ;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ;
ALTER TABLE availability_intents ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ DEFAULT NOW();

UPDATE availability_intents
SET starts_at = COALESCE(starts_at, created_at, NOW())
WHERE starts_at IS NULL;

UPDATE availability_intents
SET ends_at = COALESCE(ends_at, starts_at + interval '3 hours', NOW() + interval '3 hours')
WHERE ends_at IS NULL;

UPDATE availability_intents
SET expires_at = COALESCE(expires_at, ends_at)
WHERE expires_at IS NULL;

UPDATE availability_intents
SET intent_tag = 'Available'
WHERE intent_tag IS NULL OR trim(intent_tag) = '';

UPDATE availability_intents
SET timeframe = COALESCE(NULLIF(trim(timeframe), ''), '3 hours')
WHERE timeframe IS NULL OR trim(timeframe) = '';

ALTER TABLE availability_intents ALTER COLUMN intent_tag SET NOT NULL;
ALTER TABLE availability_intents ALTER COLUMN timeframe SET NOT NULL;
ALTER TABLE availability_intents ALTER COLUMN starts_at SET NOT NULL;
ALTER TABLE availability_intents ALTER COLUMN ends_at SET NOT NULL;
ALTER TABLE availability_intents ALTER COLUMN expires_at SET NOT NULL;

-- Only set NOT NULL on user_id when no row is missing it (new column on a legacy table may be all NULL).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'availability_intents'
      AND column_name = 'user_id'
  ) AND NOT EXISTS (SELECT 1 FROM availability_intents WHERE user_id IS NULL) THEN
    ALTER TABLE availability_intents ALTER COLUMN user_id SET NOT NULL;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION public.availability_intents_default_expires_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  IF NEW.expires_at IS NULL AND NEW.ends_at IS NOT NULL THEN
    NEW.expires_at := NEW.ends_at;
  END IF;
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_availability_intents_default_expires_at ON public.availability_intents;
CREATE TRIGGER trg_availability_intents_default_expires_at
  BEFORE INSERT OR UPDATE OF ends_at, expires_at ON public.availability_intents
  FOR EACH ROW
  EXECUTE FUNCTION public.availability_intents_default_expires_at();

CREATE INDEX IF NOT EXISTS idx_availability_intents_user_id ON availability_intents(user_id);
CREATE INDEX IF NOT EXISTS idx_availability_intents_ends_at ON availability_intents(ends_at);
CREATE INDEX IF NOT EXISTS idx_availability_intents_expires_at ON availability_intents(expires_at);

ALTER TABLE availability_intents ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "Users insert own availability intents" ON availability_intents;
DROP POLICY IF EXISTS "Users select own availability intents" ON availability_intents;
DROP POLICY IF EXISTS "Users update own availability intents" ON availability_intents;
DROP POLICY IF EXISTS "Users delete own availability intents" ON availability_intents;

CREATE POLICY "Users insert own availability intents"
    ON availability_intents FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users select own availability intents"
    ON availability_intents FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Users update own availability intents"
    ON availability_intents FOR UPDATE
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users delete own availability intents"
    ON availability_intents FOR DELETE
    USING (auth.uid() = user_id);

GRANT ALL ON availability_intents TO authenticated;

COMMENT ON TABLE availability_intents IS 'Short-lived intent tags with time windows (Share availability in app settings)';
COMMENT ON COLUMN availability_intents.expires_at IS 'When the intent is no longer shown; app sets equal to ends_at';
