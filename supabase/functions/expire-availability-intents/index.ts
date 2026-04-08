// Clears profile-mirrored availability intents older than 24h (users.last_intent_update_at).
// Schedule with pg_cron or Supabase cron; invoke with service role.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000;

Deno.serve(async (req: Request) => {
  try {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const cutoffIso = new Date(Date.now() - TWENTY_FOUR_HOURS_MS).toISOString();

    const { data: stale, error: selErr } = await supabase
      .from("users")
      .select("id")
      .not("last_intent_update_at", "is", null)
      .lt("last_intent_update_at", cutoffIso);

    if (selErr) {
      console.error("expire-availability-intents select:", selErr.message);
      return new Response(
        JSON.stringify({ success: false, error: selErr.message }),
        { status: 500, headers: { "Content-Type": "application/json" } },
      );
    }

    const ids = (stale ?? []).map((r: { id: string }) => r.id).filter(Boolean);
    let cleared = 0;

    for (const id of ids) {
      // Guard: only clear if last_intent_update_at hasn't been refreshed since our SELECT.
      const { error: updErr } = await supabase
        .from("users")
        .update({
          availability_intents: [],
          last_intent_update_at: null,
        })
        .eq("id", id)
        .lt("last_intent_update_at", cutoffIso);

      if (updErr) {
        console.error("expire-availability-intents update", id, updErr.message);
      } else {
        cleared += 1;
      }

      // Only delete intents created before the cutoff to avoid racing with concurrent inserts.
      await supabase.from("availability_intents").delete().eq("user_id", id).lt("created_at", cutoffIso);
    }

    const body = {
      success: true,
      timestamp: new Date().toISOString(),
      candidates: ids.length,
      users_cleared: cleared,
    };

    return new Response(JSON.stringify(body), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });
  } catch (e) {
    console.error("expire-availability-intents fatal:", e);
    return new Response(
      JSON.stringify({ success: false, error: String(e) }),
      { status: 500, headers: { "Content-Type": "application/json" } },
    );
  }
});
