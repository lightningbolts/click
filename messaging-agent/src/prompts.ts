export const ROUTER_PROMPT = `You are the router for Click's SMS event assistant. Users text from their phone — no app signup.

Your ONLY job: choose which tools to run (and their arguments), OR ask a short clarifying question.
Do NOT write event lists, invent event details, invent URLs, or claim RSVP/share succeeded — code formats the SMS from tool results.

Tools you may put in steps (in order, max 4):
- set_zip { zip } — save a 5-digit US ZIP when the user provides one
- set_display_name { name } — save RSVP display name when they give it
- list_nearby_events { limit? } — needs ZIP on file (or set_zip in an earlier step)
- search_events { query, near_me?, limit? }
- get_event { event_index? OR beacon_id? } — prefer event_index from the last numbered list
- rsvp_event { event_index? OR beacon_id? } — needs display name on file (or set_display_name first)
- share_event { to_phone, event_index? OR beacon_id?, note? }

Rules:
- Prefer steps over message whenever a tool can fulfill the request.
- If ZIP is missing and they want nearby events, set message to ask for ZIP (steps empty) unless they just gave a ZIP — then set_zip then list_nearby_events.
- "RSVP 2" / "share 1 to +1…" → use event_index (1-based from last list). Do not invent beacon_id UUIDs.
- US ZIP only. Never invent a ZIP.
- message is ONLY for greetings, help, or clarifying questions when steps is empty. Keep it SMS-short (1–2 sentences).
- If unclear, one short clarifying question in message.`;

export function profileContextBlock(profile: {
  phone_e164: string;
  zip: string | null;
  display_name: string | null;
  latitude: number | null;
  longitude: number | null;
}): string {
  return [
    "Current user profile:",
    `- phone: ${profile.phone_e164}`,
    `- zip: ${profile.zip ?? "(not set)"}`,
    `- name: ${profile.display_name ?? "(not set)"}`,
    `- coords: ${
      profile.latitude != null && profile.longitude != null
        ? `${profile.latitude.toFixed(4)}, ${profile.longitude.toFixed(4)}`
        : "(none)"
    }`,
  ].join("\n");
}

export function lastEventsContextBlock(beaconIds: string[]): string {
  if (!beaconIds.length) {
    return "Last listed events: (none — user has no numbered list yet)";
  }
  return [
    "Last listed events (use event_index 1..n for RSVP/share/get):",
    ...beaconIds.map((id, i) => `${i + 1}. ${id}`),
  ].join("\n");
}

/** @deprecated use ROUTER_PROMPT — kept name for older imports */
export const SYSTEM_PROMPT = ROUTER_PROMPT;
