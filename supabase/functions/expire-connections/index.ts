// Supabase Edge Function: expire-connections
// Scheduled via pg_cron every 15 minutes to enforce server-side connection expiry.
//
// Logic:
//   1. Pending connections with no messages after 48h → DELETE
//   2. Active connections with no messages in 7 days and not mutually kept → DELETE  
//   3. Log connections approaching expiry (48h warning) for future push notification

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

interface ConnectionRow {
  id: string;
  created: number;
  expiry_state: string;
  last_message_at: number | null;
  should_continue: boolean[];
  user_ids: string[];
}

Deno.serve(async (req: Request) => {
  try {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const now = Date.now();
    const FORTY_EIGHT_HOURS_MS = 48 * 60 * 60 * 1000;
    const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

    let pendingDeleted = 0;
    let activeDeleted = 0;
    let warningCount = 0;

    // =========================================================================
    // 1. Pending connections: no message sent within 48 hours → DELETE
    // =========================================================================
    const pendingCutoff = now - FORTY_EIGHT_HOURS_MS;

    const { data: pendingExpired, error: pendingError } = await supabase
      .from("connections")
      .select("id, created, last_message_at")
      .eq("expiry_state", "pending")
      .is("last_message_at", null)
      .lt("created", pendingCutoff);

    if (pendingError) {
      console.error("Error querying pending connections:", pendingError.message);
    } else if (pendingExpired && pendingExpired.length > 0) {
      const idsToDelete = pendingExpired.map((c: { id: string }) => c.id);
      const { error: deleteError } = await supabase
        .from("connections")
        .delete()
        .in("id", idsToDelete);

      if (deleteError) {
        console.error("Error deleting pending connections:", deleteError.message);
      } else {
        pendingDeleted = idsToDelete.length;
        console.log(`Deleted ${pendingDeleted} expired pending connections`);
      }
    }

    // =========================================================================
    // 2. Active connections: no message in 7 days AND not mutually kept → DELETE
    // =========================================================================
    const activeCutoff = now - SEVEN_DAYS_MS;

    const { data: activeExpired, error: activeError } = await supabase
      .from("connections")
      .select("id, last_message_at, should_continue, user_ids")
      .eq("expiry_state", "active")
      .lt("last_message_at", activeCutoff);

    if (activeError) {
      console.error("Error querying active connections:", activeError.message);
    } else if (activeExpired && activeExpired.length > 0) {
      // Filter out mutually kept connections (both should_continue = true)
      const toDelete = activeExpired.filter((c: ConnectionRow) => {
        const sc = c.should_continue;
        if (!sc || sc.length < 2) return true;
        // If both users said keep, don't delete — upgrade to 'kept' instead
        return !(sc[0] === true && sc[1] === true);
      });

      const toUpgrade = activeExpired.filter((c: ConnectionRow) => {
        const sc = c.should_continue;
        return sc && sc.length >= 2 && sc[0] === true && sc[1] === true;
      });

      // Upgrade mutually-kept connections to 'kept' state
      if (toUpgrade.length > 0) {
        const upgradeIds = toUpgrade.map((c: ConnectionRow) => c.id);
        const { error: upgradeError } = await supabase
          .from("connections")
          .update({ expiry_state: "kept" })
          .in("id", upgradeIds);

        if (upgradeError) {
          console.error("Error upgrading connections to kept:", upgradeError.message);
        } else {
          console.log(`Upgraded ${upgradeIds.length} connections to 'kept'`);
        }
      }

      // Delete non-kept expired active connections
      if (toDelete.length > 0) {
        // Mark as expired first for audit trail, then delete
        const deleteIds = toDelete.map((c: ConnectionRow) => c.id);
        const { error: deleteError } = await supabase
          .from("connections")
          .delete()
          .in("id", deleteIds);

        if (deleteError) {
          console.error("Error deleting active connections:", deleteError.message);
        } else {
          activeDeleted = deleteIds.length;
          console.log(`Deleted ${activeDeleted} expired active connections`);
        }
      }
    }

    // =========================================================================
    // 3. Pre-expiry warning: active connections approaching 7-day cutoff
    //    Log for now — push notification can be wired up later
    // =========================================================================
    const warningCutoff = now - SEVEN_DAYS_MS + FORTY_EIGHT_HOURS_MS; // 5 days

    const { data: warningConnections, error: warningError } = await supabase
      .from("connections")
      .select("id, user_ids, last_message_at")
      .eq("expiry_state", "active")
      .lt("last_message_at", warningCutoff)
      .gte("last_message_at", activeCutoff);

    if (warningError) {
      console.error("Error querying warning connections:", warningError.message);
    } else if (warningConnections && warningConnections.length > 0) {
      warningCount = warningConnections.length;
      console.log(
        `[PUSH STUB] ${warningCount} connections approaching expiry — ` +
        `would send push notifications to users: ${warningConnections
          .map((c: { user_ids: string[] }) => c.user_ids.join(","))
          .join(" | ")}`
      );
    }

    // =========================================================================
    // Response
    // =========================================================================
    const result = {
      success: true,
      timestamp: new Date().toISOString(),
      pending_deleted: pendingDeleted,
      active_deleted: activeDeleted,
      warning_notifications: warningCount,
    };

    console.log("Expire-connections run complete:", JSON.stringify(result));

    return new Response(JSON.stringify(result), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    console.error("Fatal error in expire-connections:", error);
    return new Response(
      JSON.stringify({ success: false, error: String(error) }),
      { headers: { "Content-Type": "application/json" }, status: 500 }
    );
  }
});
