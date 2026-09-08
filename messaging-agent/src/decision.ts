import { z } from "zod";

/**
 * Structured router output: pick tools (and args) only.
 * The model must not write the final SMS body for event data —
 * code formats replies from tool results.
 */
export const DecisionStepSchema = z.discriminatedUnion("tool", [
  z.object({
    tool: z.literal("set_zip"),
    zip: z.string().min(1),
  }),
  z.object({
    tool: z.literal("set_display_name"),
    name: z.string().min(1).max(80),
  }),
  z.object({
    tool: z.literal("list_nearby_events"),
    limit: z.number().int().min(1).max(8).nullable().optional(),
  }),
  z.object({
    tool: z.literal("search_events"),
    query: z.string().min(1),
    near_me: z.boolean().nullable().optional(),
    limit: z.number().int().min(1).max(8).nullable().optional(),
  }),
  z.object({
    tool: z.literal("get_event"),
    event_index: z.number().int().min(1).max(8).nullable().optional(),
    beacon_id: z.string().uuid().nullable().optional(),
  }),
  z.object({
    tool: z.literal("rsvp_event"),
    event_index: z.number().int().min(1).max(8).nullable().optional(),
    beacon_id: z.string().uuid().nullable().optional(),
  }),
  z.object({
    tool: z.literal("share_event"),
    to_phone: z.string().min(7),
    event_index: z.number().int().min(1).max(8).nullable().optional(),
    beacon_id: z.string().uuid().nullable().optional(),
    note: z.string().max(200).nullable().optional(),
  }),
]);

export const AgentDecisionSchema = z.object({
  steps: z.array(DecisionStepSchema).max(4),
  /** Clarifying / help / greeting only — used when steps is empty. */
  message: z.string().nullable(),
});

export type DecisionStep = z.infer<typeof DecisionStepSchema>;
export type AgentDecision = z.infer<typeof AgentDecisionSchema>;

/** OpenAI strict JSON schema for chat.completions response_format. */
export const AGENT_DECISION_JSON_SCHEMA = {
  name: "agent_decision",
  strict: true,
  schema: {
    type: "object",
    additionalProperties: false,
    required: ["steps", "message"],
    properties: {
      steps: {
        type: "array",
        maxItems: 4,
        items: {
          anyOf: [
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "zip"],
              properties: {
                tool: { type: "string", enum: ["set_zip"] },
                zip: { type: "string" },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "name"],
              properties: {
                tool: { type: "string", enum: ["set_display_name"] },
                name: { type: "string" },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "limit"],
              properties: {
                tool: { type: "string", enum: ["list_nearby_events"] },
                limit: { type: ["integer", "null"] },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "query", "near_me", "limit"],
              properties: {
                tool: { type: "string", enum: ["search_events"] },
                query: { type: "string" },
                near_me: { type: ["boolean", "null"] },
                limit: { type: ["integer", "null"] },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "event_index", "beacon_id"],
              properties: {
                tool: { type: "string", enum: ["get_event"] },
                event_index: { type: ["integer", "null"] },
                beacon_id: { type: ["string", "null"] },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "event_index", "beacon_id"],
              properties: {
                tool: { type: "string", enum: ["rsvp_event"] },
                event_index: { type: ["integer", "null"] },
                beacon_id: { type: ["string", "null"] },
              },
            },
            {
              type: "object",
              additionalProperties: false,
              required: ["tool", "to_phone", "event_index", "beacon_id", "note"],
              properties: {
                tool: { type: "string", enum: ["share_event"] },
                to_phone: { type: "string" },
                event_index: { type: ["integer", "null"] },
                beacon_id: { type: ["string", "null"] },
                note: { type: ["string", "null"] },
              },
            },
          ],
        },
      },
      message: { type: ["string", "null"] },
    },
  },
} as const;

export function parseAgentDecision(raw: string): AgentDecision {
  let json: unknown;
  try {
    json = JSON.parse(raw);
  } catch {
    throw new Error("Router returned non-JSON");
  }
  return AgentDecisionSchema.parse(json);
}

export function resolveBeaconId(
  step: {
    event_index?: number | null;
    beacon_id?: string | null;
  },
  lastBeaconIds: string[],
): { beacon_id: string } | { error: string } {
  if (step.beacon_id) return { beacon_id: step.beacon_id };
  if (step.event_index != null) {
    const id = lastBeaconIds[step.event_index - 1];
    if (!id) {
      return {
        error: `No event #${step.event_index} from the last list. Ask for nearby events first, or pick a number from that list.`,
      };
    }
    return { beacon_id: id };
  }
  return {
    error:
      "Need event_index (from the last numbered list) or beacon_id.",
  };
}

/** Build runTool argument JSON from a resolved decision step. */
export function stepToToolArgs(
  step: DecisionStep,
  beaconId?: string,
): string {
  switch (step.tool) {
    case "set_zip":
      return JSON.stringify({ zip: step.zip });
    case "set_display_name":
      return JSON.stringify({ name: step.name });
    case "list_nearby_events":
      return JSON.stringify(
        step.limit != null ? { limit: step.limit } : {},
      );
    case "search_events":
      return JSON.stringify({
        query: step.query,
        ...(step.near_me != null ? { near_me: step.near_me } : {}),
        ...(step.limit != null ? { limit: step.limit } : {}),
      });
    case "get_event":
    case "rsvp_event":
      return JSON.stringify({ beacon_id: beaconId });
    case "share_event":
      return JSON.stringify({
        beacon_id: beaconId,
        to_phone: step.to_phone,
        ...(step.note ? { note: step.note } : {}),
      });
  }
}
