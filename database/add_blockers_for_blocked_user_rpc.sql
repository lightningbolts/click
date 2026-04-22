-- Companion to click-web/supabase/migrations/20260416120000_blockers_for_blocked_user_rpc.sql
-- Apply on projects that ship SQL from this repo without Supabase CLI migrations.

CREATE OR REPLACE FUNCTION public.blockers_for_blocked_user()
RETURNS TABLE (blocker_id uuid)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT ub.blocker_id
  FROM public.user_blocks ub
  WHERE ub.blocked_id::text = (SELECT auth.uid())::text;
$$;

COMMENT ON FUNCTION public.blockers_for_blocked_user() IS
  'Returns user IDs that have blocked the current user (blocked_id = auth.uid()). Caller identity only; no parameters.';

GRANT EXECUTE ON FUNCTION public.blockers_for_blocked_user() TO authenticated;
GRANT EXECUTE ON FUNCTION public.blockers_for_blocked_user() TO service_role;
