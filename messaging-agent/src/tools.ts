import { z } from "zod";
import type { ClickApi, PublicEventListItem } from "./clickApi.js";
import { filterWithinRadiusKm } from "./haversine.js";
import { geocodeUsZip, normalizeUsZip } from "./geocode.js";
import type { AgentStore, PhoneProfile } from "./store.js";
import type { SmsSender } from "./sendSms.js";

export type ToolDeps = {
  store: AgentStore;
  click: ClickApi;
  sms: SmsSender;
  phoneE164: string;
  nearbyRadiusKm: number;
};

type OpenAiTool = {
  type: "function";
  function: {
    name: string;
    description: string;
    parameters: Record<string, unknown>;
  };
};

export const AGENT_TOOLS: OpenAiTool[] = [
  {
    type: "function",
    function: {
      name: "get_profile",
      description: "Get the user's saved ZIP, name, and phone.",
      parameters: { type: "object", properties: {}, additionalProperties: false },
    },
  },
  {
    type: "function",
    function: {
      name: "set_zip",
      description:
        "Save or update the user's US ZIP code. Geocodes and remembers it until changed.",
      parameters: {
        type: "object",
        properties: {
          zip: { type: "string", description: "5-digit US ZIP" },
        },
        required: ["zip"],
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "set_display_name",
      description: "Save the name to use on guest RSVPs.",
      parameters: {
        type: "object",
        properties: {
          name: { type: "string", description: "Display name, 1–80 chars" },
        },
        required: ["name"],
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "list_nearby_events",
      description:
        "List upcoming public Click events near the user's saved ZIP. Requires ZIP.",
      parameters: {
        type: "object",
        properties: {
          limit: {
            type: "integer",
            description: "Max events to return (default 5, max 8)",
          },
        },
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "search_events",
      description:
        "Search public upcoming events by title/description/location text. Optionally restrict to the user's ZIP radius.",
      parameters: {
        type: "object",
        properties: {
          query: { type: "string", description: "Search text" },
          near_me: {
            type: "boolean",
            description: "If true, only events near saved ZIP",
          },
          limit: { type: "integer" },
        },
        required: ["query"],
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "get_event",
      description: "Get details and share URL for one event by beacon id.",
      parameters: {
        type: "object",
        properties: {
          beacon_id: { type: "string" },
        },
        required: ["beacon_id"],
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "rsvp_event",
      description:
        "Guest-RSVP the user to an event using their phone as contact. Needs display name (set_display_name first if missing).",
      parameters: {
        type: "object",
        properties: {
          beacon_id: { type: "string" },
        },
        required: ["beacon_id"],
        additionalProperties: false,
      },
    },
  },
  {
    type: "function",
    function: {
      name: "share_event",
      description:
        "Text another phone number a short message with the Click event link.",
      parameters: {
        type: "object",
        properties: {
          beacon_id: { type: "string" },
          to_phone: {
            type: "string",
            description: "Recipient phone in E.164 (+1…)",
          },
          note: {
            type: "string",
            description: "Optional short personal note",
          },
        },
        required: ["beacon_id", "to_phone"],
        additionalProperties: false,
      },
    },
  },
];

function formatEventBrief(
  e: PublicEventListItem & { distance_km?: number },
  shareUrl: string,
  index?: number,
): Record<string, unknown> {
  return {
    index: index ?? null,
    beacon_id: e.beacon_id,
    title: e.title,
    start: e.event_start_at,
    end: e.event_end_at,
    location: e.location_name,
    host: e.host_name,
    rsvp_count: e.rsvp_count,
    rsvp_enabled: e.rsvp_enabled,
    distance_km:
      e.distance_km != null ? Math.round(e.distance_km * 10) / 10 : null,
    share_url: shareUrl,
  };
}

function matchesQuery(e: PublicEventListItem, query: string): boolean {
  const q = query.trim().toLowerCase();
  if (!q) return false;
  const hay = [e.title, e.description, e.location_name, e.host_name]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return hay.includes(q);
}

function clampLimit(raw: unknown, fallback = 5): number {
  const n = typeof raw === "number" ? raw : Number(raw);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(1, Math.min(8, Math.floor(n)));
}

const setZipSchema = z.object({ zip: z.string().min(1) });
const setNameSchema = z.object({ name: z.string().trim().min(1).max(80) });
const listSchema = z.object({ limit: z.number().optional() }).partial();
const searchSchema = z.object({
  query: z.string().min(1),
  near_me: z.boolean().optional(),
  limit: z.number().optional(),
});
const beaconSchema = z.object({ beacon_id: z.string().uuid() });
const shareSchema = z.object({
  beacon_id: z.string().uuid(),
  to_phone: z.string().min(7),
  note: z.string().max(200).optional(),
});

export function normalizeE164(raw: string): string | null {
  const digits = raw.replace(/[^\d+]/g, "");
  const only = digits.replace(/\D/g, "");
  if (only.length < 10 || only.length > 15) return null;
  if (digits.startsWith("+")) return `+${only}`;
  if (only.length === 10) return `+1${only}`;
  return `+${only}`;
}

export async function runTool(
  name: string,
  argsJson: string,
  deps: ToolDeps,
): Promise<string> {
  let args: unknown = {};
  try {
    args = argsJson ? JSON.parse(argsJson) : {};
  } catch {
    return JSON.stringify({ error: "Invalid tool arguments JSON" });
  }

  try {
    switch (name) {
      case "get_profile":
        return JSON.stringify(deps.store.getProfile(deps.phoneE164));
      case "set_zip": {
        const { zip } = setZipSchema.parse(args);
        const normalized = normalizeUsZip(zip);
        if (!normalized) {
          return JSON.stringify({ error: "Need a 5-digit US ZIP." });
        }
        const geo = await geocodeUsZip(normalized);
        if ("error" in geo) return JSON.stringify(geo);
        const profile = deps.store.setZip(
          deps.phoneE164,
          normalized,
          geo.latitude,
          geo.longitude,
        );
        return JSON.stringify({
          ok: true,
          zip: profile.zip,
          place: geo.displayName,
          latitude: profile.latitude,
          longitude: profile.longitude,
        });
      }
      case "set_display_name": {
        const { name: displayName } = setNameSchema.parse(args);
        const profile = deps.store.setDisplayName(deps.phoneE164, displayName);
        return JSON.stringify({ ok: true, display_name: profile.display_name });
      }
      case "list_nearby_events": {
        const parsed = listSchema.parse(args ?? {});
        const profile = requireLocatedProfile(deps);
        if ("error" in profile) return JSON.stringify(profile);
        const events = await deps.click.listPublicEvents();
        const nearby = filterWithinRadiusKm(
          events,
          profile.latitude!,
          profile.longitude!,
          deps.nearbyRadiusKm,
        ).sort((a, b) => {
          const as = a.event_start_at ?? "";
          const bs = b.event_start_at ?? "";
          return as.localeCompare(bs);
        });
        const limit = clampLimit(parsed.limit);
        const sliced = nearby.slice(0, limit);
        return JSON.stringify({
          zip: profile.zip,
          radius_km: deps.nearbyRadiusKm,
          count: sliced.length,
          events: sliced.map((e, i) =>
            formatEventBrief(e, deps.click.eventShareUrl(e.beacon_id), i + 1),
          ),
        });
      }
      case "search_events": {
        const parsed = searchSchema.parse(args);
        let events = await deps.click.listPublicEvents();
        events = events.filter((e) => matchesQuery(e, parsed.query));
        if (parsed.near_me) {
          const profile = requireLocatedProfile(deps);
          if ("error" in profile) return JSON.stringify(profile);
          events = filterWithinRadiusKm(
            events,
            profile.latitude!,
            profile.longitude!,
            deps.nearbyRadiusKm,
          );
        }
        events = [...events].sort((a, b) =>
          (a.event_start_at ?? "").localeCompare(b.event_start_at ?? ""),
        );
        const limit = clampLimit(parsed.limit);
        const sliced = events.slice(0, limit);
        return JSON.stringify({
          query: parsed.query,
          count: sliced.length,
          events: sliced.map((e, i) =>
            formatEventBrief(
              e as PublicEventListItem & { distance_km?: number },
              deps.click.eventShareUrl(e.beacon_id),
              i + 1,
            ),
          ),
        });
      }
      case "get_event": {
        const { beacon_id } = beaconSchema.parse(args);
        const event = await deps.click.getPublicEvent(beacon_id);
        return JSON.stringify({
          ...formatEventBrief(event, deps.click.eventShareUrl(beacon_id)),
          description: event.description,
        });
      }
      case "rsvp_event": {
        const { beacon_id } = beaconSchema.parse(args);
        const profile = deps.store.getProfile(deps.phoneE164);
        if (!profile.display_name) {
          return JSON.stringify({
            error: "Ask the user for a name, then call set_display_name.",
          });
        }
        await deps.click.guestRsvp(
          beacon_id,
          profile.display_name,
          deps.phoneE164,
        );
        return JSON.stringify({
          ok: true,
          beacon_id,
          name: profile.display_name,
          contact: deps.phoneE164,
          share_url: deps.click.eventShareUrl(beacon_id),
        });
      }
      case "share_event": {
        const parsed = shareSchema.parse(args);
        const to = normalizeE164(parsed.to_phone);
        if (!to) {
          return JSON.stringify({
            error: "Invalid recipient phone. Use E.164 like +15551234567.",
          });
        }
        const event = await deps.click.getPublicEvent(parsed.beacon_id);
        const url = deps.click.eventShareUrl(parsed.beacon_id);
        const title = event.title?.trim() || "a Click event";
        const note = parsed.note?.trim();
        const body = note
          ? `${note}\n\n${title}\n${url}`
          : `Someone shared ${title} with you on Click:\n${url}`;
        await deps.sms.send(to, body);
        return JSON.stringify({ ok: true, to, beacon_id: parsed.beacon_id, url });
      }
      default:
        return JSON.stringify({ error: `Unknown tool: ${name}` });
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    return JSON.stringify({ error: message });
  }
}

function requireLocatedProfile(
  deps: ToolDeps,
): PhoneProfile | { error: string } {
  const profile = deps.store.getProfile(deps.phoneE164);
  if (
    !profile.zip ||
    profile.latitude == null ||
    profile.longitude == null
  ) {
    return {
      error:
        "No ZIP on file. Ask the user for their ZIP, then call set_zip.",
    };
  }
  return profile;
}
