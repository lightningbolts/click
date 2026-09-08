/** Deterministic SMS bodies from tool JSON — LLM does not invent event copy. */

export type FormattedEvent = {
  index?: number | null;
  title?: string | null;
  start?: string | null;
  location?: string | null;
  distance_km?: number | null;
  share_url?: string | null;
  description?: string | null;
};

function shortWhen(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("en-US", {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
    timeZoneName: "short",
  });
}

export function formatEventLines(e: FormattedEvent, index?: number): string[] {
  const n = index ?? e.index;
  const title = (e.title ?? "Untitled event").trim() || "Untitled event";
  const head = n != null ? `${n}. ${title}` : title;
  const lines = [head];
  const when = shortWhen(e.start ?? null);
  const place = e.location?.trim();
  const dist =
    e.distance_km != null ? `${e.distance_km} km away` : null;
  const meta = [when, place, dist].filter(Boolean).join(" · ");
  if (meta) lines.push(meta);
  if (e.share_url) lines.push(e.share_url);
  return lines;
}

export function formatEventListSms(payload: {
  zip?: string;
  query?: string;
  radius_km?: number;
  count: number;
  events: FormattedEvent[];
}): string {
  if (!payload.events.length) {
    if (payload.query) {
      return `No public events matched "${payload.query}". Try another search or ask for events near you.`;
    }
    const zip = payload.zip ? ` near ${payload.zip}` : "";
    return `No upcoming public events${zip}. Try a different ZIP or search.`;
  }
  const header = payload.query
    ? `Found ${payload.count} for "${payload.query}":`
    : payload.zip
      ? `Near ${payload.zip}:`
      : "Upcoming events:";
  const blocks = payload.events.map((e, i) =>
    formatEventLines(e, e.index ?? i + 1).join("\n"),
  );
  return [header, ...blocks].join("\n\n");
}

export function formatToolResultSms(
  tool: string,
  rawJson: string,
): string | null {
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(rawJson) as Record<string, unknown>;
  } catch {
    return "Something went wrong. Try again in a moment.";
  }

  if (typeof body.error === "string") {
    return formatToolError(tool, body.error);
  }

  switch (tool) {
    case "set_zip": {
      const zip = String(body.zip ?? "");
      const place = body.place ? ` (${String(body.place)})` : "";
      return `Got it — saved ZIP ${zip}${place}. Text "events near me" anytime.`;
    }
    case "set_display_name":
      return `Thanks, ${String(body.display_name)}. You can RSVP now (e.g. RSVP 1).`;
    case "list_nearby_events":
    case "search_events":
      return formatEventListSms({
        zip: typeof body.zip === "string" ? body.zip : undefined,
        query: typeof body.query === "string" ? body.query : undefined,
        radius_km:
          typeof body.radius_km === "number" ? body.radius_km : undefined,
        count: typeof body.count === "number" ? body.count : 0,
        events: Array.isArray(body.events)
          ? (body.events as FormattedEvent[])
          : [],
      });
    case "get_event":
      return formatEventLines(body as FormattedEvent).join("\n");
    case "rsvp_event": {
      const url = typeof body.share_url === "string" ? body.share_url : "";
      const name = String(body.name ?? "you");
      return [
        `You're RSVP'd as ${name}.`,
        url ? url : null,
      ]
        .filter(Boolean)
        .join("\n");
    }
    case "share_event": {
      const to = String(body.to ?? "");
      const url = typeof body.url === "string" ? body.url : "";
      return [`Shared with ${to}.`, url].filter(Boolean).join("\n");
    }
    case "get_profile":
      return null;
    default:
      return null;
  }
}

function formatToolError(tool: string, error: string): string {
  if (/no zip/i.test(error) || /ask the user for their zip/i.test(error)) {
    return "What's your US ZIP code? I'll find events near you.";
  }
  if (/ask the user for a name/i.test(error) || /display name/i.test(error)) {
    return "What name should I put on the RSVP?";
  }
  if (/need a 5-digit/i.test(error)) {
    return "Please send a 5-digit US ZIP (e.g. 98021).";
  }
  if (/invalid recipient/i.test(error)) {
    return "That phone number doesn't look right. Use something like +15551234567.";
  }
  if (tool === "share_event" || tool === "rsvp_event") {
    return `Couldn't finish that: ${error}`;
  }
  return error.length > 160 ? `${error.slice(0, 159)}…` : error;
}

export function extractListedBeaconIds(rawJson: string): string[] | null {
  try {
    const body = JSON.parse(rawJson) as {
      events?: Array<{ beacon_id?: string }>;
    };
    if (!Array.isArray(body.events)) return null;
    const ids = body.events
      .map((e) => e.beacon_id)
      .filter((id): id is string => typeof id === "string" && id.length > 0);
    return ids.length ? ids : [];
  } catch {
    return null;
  }
}

export const DEFAULT_HELP =
  "I can find public Click events near your ZIP, search, RSVP, or share a link by text. Try: events near me";
