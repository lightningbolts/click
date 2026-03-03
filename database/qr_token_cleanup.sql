-- ============================================================================
-- QR Token Cleanup — Run as Supabase Cron Job
-- Schedule: Every hour (or daily)
-- Deletes expired/redeemed tokens older than 24 hours
-- ============================================================================

DELETE FROM public.qr_tokens
WHERE expires_at < ((EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - (24 * 60 * 60 * 1000));

-- To set up as a Supabase cron job, run in SQL Editor:
--
-- SELECT cron.schedule(
--   'cleanup-qr-tokens',
--   '0 * * * *',  -- Every hour
--   $$DELETE FROM public.qr_tokens
--     WHERE expires_at < ((EXTRACT(EPOCH FROM now()) * 1000)::BIGINT - (24 * 60 * 60 * 1000))$$
-- );
