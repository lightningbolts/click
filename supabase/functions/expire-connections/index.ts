// Supabase Edge Function: expire-connections
// Scheduled via pg_cron (e.g. every 15 minutes).
//
// - Pending + no messages after 48h → status = 'archived' (not DELETE)
// - Active + no message in 7d + not mutually kept → status = 'archived'
// - Active + mutually kept → status = 'kept'
// - 12h before archive: send push via send-push-notification (deduped per connection)

import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_KEY")!;

const FORTY_EIGHT_HOURS_MS = 48 * 60 * 60 * 1000;
const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;
const TWELVE_HOURS_MS = 12 * 60 * 60 * 1000;

interface ConnectionRow {
  id: string;
  created: number;
  status: string;
  last_message_at: number | null;
  should_continue: boolean[];
  user_ids: string[];
  archive_warning_pending_sent_at: number | null;
  archive_warning_idle_sent_at: number | null;
}

function isMutuallyKept(sc: boolean[] | null | undefined): boolean {
  return !!(sc && sc.length >= 2 && sc[0] === true && sc[1] === true);
}

async function sendArchiveWarningPushes(
  supabase: ReturnType<typeof createClient>,
  connections: ConnectionRow[],
  kind: "pending" | "idle",
): Promise<number> {
  if (connections.length === 0) return 0;

  const secret =
    Deno.env.get("ARCHIVE_WARNING_PUSH_SECRET") ?? SUPABASE_SERVICE_KEY;
  const pushUrl = `${SUPABASE_URL}/functions/v1/send-push-notification`;

  let pushAttempts = 0;

  const now = Date.now();

  for (const c of connections) {
    const anchor = kind === "pending" ? c.created : (c.last_message_at ?? c.created);
    const deadline = anchor + (kind === "pending" ? FORTY_EIGHT_HOURS_MS : SEVEN_DAYS_MS);
    const remainingMs = Math.max(0, deadline - now);
    const remainingHours = Math.round(remainingMs / (60 * 60 * 1000));
    const timeLabel = remainingHours >= 1 ? `~${remainingHours} hour${remainingHours === 1 ? "" : "s"}` : "less than an hour";

    const title =
      kind === "pending"
        ? "Say hi before this connection archives"
        : "Reconnect soon";
    const body =
      kind === "pending"
        ? `You have ${timeLabel} left to send a first message.`
        : `No messages in a week — reply within ${timeLabel} to keep this chat active.`;

    for (const uid of c.user_ids) {
      try {
        const res = await fetch(pushUrl, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${SUPABASE_SERVICE_KEY}`,
            "x-archive-warning-secret": secret,
          },
          body: JSON.stringify({
            recipient_user_id: uid,
            title,
            body,
            data: {
              type: "archive_warning",
              connection_id: c.id,
              warning_kind: kind,
            },
          }),
        });
        pushAttempts += 1;
        if (res.ok) {
          await res.json().catch(() => ({}));
        } else {
          console.error(
            "send-push-notification failed:",
            res.status,
            await res.text(),
          );
        }
      } catch (e) {
        console.error("send-push-notification error:", e);
      }
    }

    const col =
      kind === "pending"
        ? "archive_warning_pending_sent_at"
        : "archive_warning_idle_sent_at";
    const now = Date.now();
    await supabase.from("connections").update({ [col]: now }).eq("id", c.id);
  }

  return pushAttempts;
}

Deno.serve(async (req: Request) => {
  try {
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const now = Date.now();
    let pendingArchived = 0;
    let activeArchived = 0;
    let upgradedKept = 0;
    let pendingWarnings = 0;
    let idleWarnings = 0;

    const pendingCutoff = now - FORTY_EIGHT_HOURS_MS;
    const activeIdleCutoff = now - SEVEN_DAYS_MS;
    const pendingWarnStart = pendingCutoff + TWELVE_HOURS_MS;
    const idleWarnStart = activeIdleCutoff + TWELVE_HOURS_MS;

    // -------------------------------------------------------------------------
    // 0) Pending: 12h warning (no first message yet)
    // -------------------------------------------------------------------------
    const { data: pendingWarnRows, error: pwErr } = await supabase
      .from("connections")
      .select(
        "id, created, status, last_message_at, should_continue, user_ids, archive_warning_pending_sent_at, archive_warning_idle_sent_at",
      )
      .eq("status", "pending")
      .is("last_message_at", null)
      .gte("created", pendingCutoff)
      .lt("created", pendingWarnStart);

    if (pwErr) {
      console.error("pending warning query:", pwErr.message);
    } else if (pendingWarnRows && pendingWarnRows.length > 0) {
      const toWarn = (pendingWarnRows as ConnectionRow[]).filter((c) =>
        !c.archive_warning_pending_sent_at
      );
      pendingWarnings = await sendArchiveWarningPushes(supabase, toWarn, "pending");
    }

    // -------------------------------------------------------------------------
    // 1) Pending: archive after 48h with no messages
    // -------------------------------------------------------------------------
    const { data: pendingExpired, error: pendingError } = await supabase
      .from("connections")
      .select("id")
      .eq("status", "pending")
      .is("last_message_at", null)
      .lt("created", pendingCutoff);

    if (pendingError) {
      console.error("Error querying pending connections:", pendingError.message);
    } else if (pendingExpired && pendingExpired.length > 0) {
      const ids = pendingExpired.map((c: { id: string }) => c.id);
      const { error: updErr } = await supabase
        .from("connections")
        .update({ status: "archived" })
        .in("id", ids);
      if (updErr) {
        console.error("Error archiving pending:", updErr.message);
      } else {
        pendingArchived = ids.length;
        console.log(`Archived ${pendingArchived} pending connections (48h no message)`);
      }
    }

    // -------------------------------------------------------------------------
    // 2) Active: 12h warning before 7-day idle archive
    // -------------------------------------------------------------------------
    const { data: idleWarnRows, error: iwErr } = await supabase
      .from("connections")
      .select(
        "id, created, status, last_message_at, should_continue, user_ids, archive_warning_pending_sent_at, archive_warning_idle_sent_at",
      )
      .eq("status", "active")
      .not("last_message_at", "is", null)
      .gte("last_message_at", activeIdleCutoff)
      .lt("last_message_at", idleWarnStart);

    if (iwErr) {
      console.error("idle warning query:", iwErr.message);
    } else if (idleWarnRows && idleWarnRows.length > 0) {
      const filtered = (idleWarnRows as ConnectionRow[]).filter((c) => {
        if (isMutuallyKept(c.should_continue)) return false;
        return !c.archive_warning_idle_sent_at;
      });
      idleWarnings = await sendArchiveWarningPushes(supabase, filtered, "idle");
    }

    // -------------------------------------------------------------------------
    // 3) Active: 7d idle → archive OR mutually kept → kept
    // -------------------------------------------------------------------------
    const { data: activeExpired, error: activeError } = await supabase
      .from("connections")
      .select("id, last_message_at, should_continue, user_ids")
      .eq("status", "active")
      .lt("last_message_at", activeIdleCutoff);

    if (activeError) {
      console.error("Error querying active connections:", activeError.message);
    } else if (activeExpired && activeExpired.length > 0) {
      const rows = activeExpired as ConnectionRow[];
      const toUpgrade = rows.filter((c) => isMutuallyKept(c.should_continue));
      const toArchive = rows.filter((c) => !isMutuallyKept(c.should_continue));

      if (toUpgrade.length > 0) {
        const upgradeIds = toUpgrade.map((c) => c.id);
        const { error: upgradeError } = await supabase
          .from("connections")
          .update({ status: "kept" })
          .in("id", upgradeIds);
        if (upgradeError) {
          console.error("Error upgrading to kept:", upgradeError.message);
        } else {
          upgradedKept = upgradeIds.length;
          console.log(`Set ${upgradedKept} connections to kept`);
        }
      }

      if (toArchive.length > 0) {
        const archiveIds = toArchive.map((c) => c.id);
        const { error: archErr } = await supabase
          .from("connections")
          .update({ status: "archived" })
          .in("id", archiveIds);
        if (archErr) {
          console.error("Error archiving idle active:", archErr.message);
        } else {
          activeArchived = archiveIds.length;
          console.log(`Archived ${activeArchived} idle active connections`);
        }
      }
    }

    const result = {
      success: true,
      timestamp: new Date().toISOString(),
      pending_archived: pendingArchived,
      active_archived: activeArchived,
      upgraded_kept: upgradedKept,
      pending_warning_pushes: pendingWarnings,
      idle_warning_pushes: idleWarnings,
    };

    console.log("expire-connections complete:", JSON.stringify(result));

    return new Response(JSON.stringify(result), {
      headers: { "Content-Type": "application/json" },
      status: 200,
    });
  } catch (error) {
    console.error("Fatal error in expire-connections:", error);
    return new Response(
      JSON.stringify({ success: false, error: String(error) }),
      { headers: { "Content-Type": "application/json" }, status: 500 },
    );
  }
});
