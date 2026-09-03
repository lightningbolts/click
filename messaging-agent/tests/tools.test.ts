import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ClickApi, PublicEventListItem } from "../src/clickApi.js";
import { AgentStore } from "../src/store.js";
import { runTool, type ToolDeps } from "../src/tools.js";

const events: PublicEventListItem[] = [
  {
    beacon_id: "11111111-1111-4111-8111-111111111111",
    title: "Rooftop Jazz",
    description: "Live music",
    image_url: null,
    host_name: "Ada",
    event_start_at: "2030-06-01T20:00:00Z",
    event_end_at: null,
    location_name: "SF",
    latitude: 37.78,
    longitude: -122.41,
    rsvp_count: 3,
    rsvp_enabled: true,
    timezone: "America/Los_Angeles",
  },
  {
    beacon_id: "22222222-2222-4222-8222-222222222222",
    title: "LA Picnic",
    description: "Parks",
    image_url: null,
    host_name: "Bob",
    event_start_at: "2030-06-02T18:00:00Z",
    event_end_at: null,
    location_name: "Los Angeles",
    latitude: 34.05,
    longitude: -118.25,
    rsvp_count: 1,
    rsvp_enabled: true,
    timezone: "America/Los_Angeles",
  },
];

function makeDeps(overrides: Partial<ToolDeps> = {}): ToolDeps {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "click-agent-"));
  const store = new AgentStore(path.join(dir, "t.sqlite"));
  const click = {
    listPublicEvents: vi.fn(async () => events),
    getPublicEvent: vi.fn(async (id: string) => {
      const e = events.find((x) => x.beacon_id === id);
      if (!e) throw new Error("not found");
      return e;
    }),
    guestRsvp: vi.fn(async () => undefined),
    eventShareUrl: (id: string) => `https://joinclick.co/e/${id}`,
  } as unknown as ClickApi;
  const sms = { send: vi.fn(async () => ({ sid: "SM1" })) };
  return {
    store,
    click,
    sms,
    phoneE164: "+15550001111",
    nearbyRadiusKm: 40,
    ...overrides,
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("runTool", () => {
  it("lists nearby events after zip is set", async () => {
    const deps = makeDeps();
    deps.store.setZip(deps.phoneE164, "94107", 37.77, -122.42);
    const raw = await runTool("list_nearby_events", "{}", deps);
    const body = JSON.parse(raw) as {
      count: number;
      events: Array<{ title: string }>;
    };
    expect(body.count).toBe(1);
    expect(body.events[0].title).toBe("Rooftop Jazz");
  });

  it("searches by query without near_me", async () => {
    const deps = makeDeps();
    const raw = await runTool(
      "search_events",
      JSON.stringify({ query: "picnic" }),
      deps,
    );
    const body = JSON.parse(raw) as { events: Array<{ title: string }> };
    expect(body.events).toHaveLength(1);
    expect(body.events[0].title).toBe("LA Picnic");
  });

  it("rsvps with display name and phone contact", async () => {
    const deps = makeDeps();
    deps.store.setDisplayName(deps.phoneE164, "Sam");
    const raw = await runTool(
      "rsvp_event",
      JSON.stringify({ beacon_id: events[0].beacon_id }),
      deps,
    );
    expect(JSON.parse(raw)).toMatchObject({ ok: true });
    expect(deps.click.guestRsvp).toHaveBeenCalledWith(
      events[0].beacon_id,
      "Sam",
      "+15550001111",
    );
  });

  it("shares an event via SMS", async () => {
    const deps = makeDeps();
    const raw = await runTool(
      "share_event",
      JSON.stringify({
        beacon_id: events[0].beacon_id,
        to_phone: "5559998888",
      }),
      deps,
    );
    expect(JSON.parse(raw)).toMatchObject({ ok: true, to: "+15559998888" });
    expect(deps.sms.send).toHaveBeenCalled();
    const body = (deps.sms.send as ReturnType<typeof vi.fn>).mock.calls[0][1] as string;
    expect(body).toContain("https://joinclick.co/e/");
  });
});
