-- Optional client heartbeat field (presence / polling). Safe to run on existing projects.
-- Run in Supabase SQL editor or via migration if the app PATCHes users.last_polled.
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS last_polled bigint;

COMMENT ON COLUMN public.users.last_polled IS 'Epoch ms of last client presence/heartbeat update';
