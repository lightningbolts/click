import { describe, expect, it } from "vitest";
import {
  parseAgentDecision,
  resolveBeaconId,
  stepToToolArgs,
} from "../src/decision.js";
import {
  extractListedBeaconIds,
  formatEventListSms,
  formatToolResultSms,
} from "../src/format.js";

describe("parseAgentDecision", () => {
  it("parses a list_nearby decision", () => {
    const d = parseAgentDecision(
      JSON.stringify({
        steps: [{ tool: "list_nearby_events", limit: 5 }],
        message: null,
      }),
    );
    expect(d.steps).toHaveLength(1);
    expect(d.steps[0].tool).toBe("list_nearby_events");
  });

  it("parses clarifying message with empty steps", () => {
    const d = parseAgentDecision(
      JSON.stringify({
        steps: [],
        message: "What's your ZIP?",
      }),
    );
    expect(d.steps).toEqual([]);
    expect(d.message).toBe("What's your ZIP?");
  });

  it("parses set_zip then list chain", () => {
    const d = parseAgentDecision(
      JSON.stringify({
        steps: [
          { tool: "set_zip", zip: "98021" },
          { tool: "list_nearby_events", limit: null },
        ],
        message: null,
      }),
    );
    expect(d.steps.map((s) => s.tool)).toEqual([
      "set_zip",
      "list_nearby_events",
    ]);
  });
});

describe("resolveBeaconId", () => {
  const ids = [
    "11111111-1111-4111-8111-111111111111",
    "22222222-2222-4222-8222-222222222222",
  ];

  it("resolves 1-based event_index", () => {
    expect(resolveBeaconId({ event_index: 2 }, ids)).toEqual({
      beacon_id: ids[1],
    });
  });

  it("prefers explicit beacon_id", () => {
    expect(
      resolveBeaconId({ event_index: 1, beacon_id: ids[1] }, ids),
    ).toEqual({ beacon_id: ids[1] });
  });

  it("errors on missing index", () => {
    expect(resolveBeaconId({ event_index: 9 }, ids)).toMatchObject({
      error: expect.stringContaining("No event #9"),
    });
  });
});

describe("stepToToolArgs", () => {
  it("builds share args", () => {
    const json = stepToToolArgs(
      {
        tool: "share_event",
        to_phone: "5551234567",
        event_index: 1,
        beacon_id: null,
        note: null,
      },
      "11111111-1111-4111-8111-111111111111",
    );
    expect(JSON.parse(json)).toEqual({
      beacon_id: "11111111-1111-4111-8111-111111111111",
      to_phone: "5551234567",
    });
  });
});

describe("formatToolResultSms", () => {
  it("formats an event list with links", () => {
    const sms = formatToolResultSms(
      "list_nearby_events",
      JSON.stringify({
        zip: "94107",
        count: 1,
        events: [
          {
            index: 1,
            title: "Rooftop Jazz",
            start: "2030-06-01T20:00:00Z",
            location: "SF",
            distance_km: 1.2,
            share_url: "https://joinclick.co/e/11111111-1111-4111-8111-111111111111",
          },
        ],
      }),
    );
    expect(sms).toContain("Near 94107");
    expect(sms).toContain("1. Rooftop Jazz");
    expect(sms).toContain("https://joinclick.co/e/");
  });

  it("asks for ZIP on missing location error", () => {
    const sms = formatToolResultSms(
      "list_nearby_events",
      JSON.stringify({
        error: "No ZIP on file. Ask the user for their ZIP, then call set_zip.",
      }),
    );
    expect(sms).toMatch(/ZIP/i);
  });
});

describe("extractListedBeaconIds", () => {
  it("pulls ids from list payload", () => {
    expect(
      extractListedBeaconIds(
        JSON.stringify({
          events: [
            { beacon_id: "11111111-1111-4111-8111-111111111111" },
            { beacon_id: "22222222-2222-4222-8222-222222222222" },
          ],
        }),
      ),
    ).toEqual([
      "11111111-1111-4111-8111-111111111111",
      "22222222-2222-4222-8222-222222222222",
    ]);
  });
});

describe("formatEventListSms", () => {
  it("handles empty search", () => {
    expect(
      formatEventListSms({ query: "xyz", count: 0, events: [] }),
    ).toContain("xyz");
  });
});
