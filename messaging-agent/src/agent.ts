import OpenAI from "openai";
import type { AppConfig } from "./config.js";
import { ClickApi } from "./clickApi.js";
import {
  AGENT_DECISION_JSON_SCHEMA,
  parseAgentDecision,
  resolveBeaconId,
  stepToToolArgs,
  type DecisionStep,
} from "./decision.js";
import {
  DEFAULT_HELP,
  extractListedBeaconIds,
  formatToolResultSms,
} from "./format.js";
import {
  lastEventsContextBlock,
  profileContextBlock,
  ROUTER_PROMPT,
} from "./prompts.js";
import type { SmsSender } from "./sendSms.js";
import type { AgentStore } from "./store.js";
import { runTool, type ToolDeps } from "./tools.js";

export type AgentRuntime = {
  config: AppConfig;
  store: AgentStore;
  click: ClickApi;
  sms: SmsSender;
  openai: OpenAI;
};

/**
 * Route (structured JSON) → run tools → format SMS from tool results.
 * The model picks tools; it does not free-write event replies.
 */
export async function handleInboundSms(
  runtime: AgentRuntime,
  phoneE164: string,
  userText: string,
): Promise<string> {
  const { store, config, openai } = runtime;
  const profile = store.getProfile(phoneE164);
  store.appendTurn(phoneE164, "user", userText);

  const history = store.recentTurns(phoneE164, 12);
  const lastIds = store.getLastListedEvents(phoneE164);

  const messages: OpenAI.Chat.ChatCompletionMessageParam[] = [
    { role: "system", content: ROUTER_PROMPT },
    { role: "system", content: profileContextBlock(profile) },
    { role: "system", content: lastEventsContextBlock(lastIds) },
    ...history.map((t) => ({
      role: t.role as "user" | "assistant",
      content: t.content,
    })),
  ];

  const deps: ToolDeps = {
    store,
    click: runtime.click,
    sms: runtime.sms,
    phoneE164,
    nearbyRadiusKm: config.NEARBY_RADIUS_KM,
  };

  let finalText = DEFAULT_HELP;

  try {
    const completion = await openai.chat.completions.create({
      model: config.LLM_MODEL,
      messages,
      temperature: 0.2,
      max_tokens: 400,
      response_format: {
        type: "json_schema",
        json_schema: AGENT_DECISION_JSON_SCHEMA,
      },
    });

    const raw = completion.choices[0]?.message?.content?.trim() ?? "";
    const decision = parseAgentDecision(raw || '{"steps":[],"message":null}');

    if (!decision.steps.length) {
      finalText =
        (decision.message?.trim() || DEFAULT_HELP).slice(0, 1500);
    } else {
      finalText = await executeSteps(decision.steps, deps, store, phoneE164);
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.error("agent route/execute failed:", message);
    finalText =
      "Sorry — I couldn't process that. Try again in a moment.";
  }

  store.appendTurn(phoneE164, "assistant", finalText);
  return finalText;
}

async function executeSteps(
  steps: DecisionStep[],
  deps: ToolDeps,
  store: AgentStore,
  phoneE164: string,
): Promise<string> {
  const replyParts: string[] = [];
  let lastIds = store.getLastListedEvents(phoneE164);

  for (let i = 0; i < steps.length; i++) {
    const step = steps[i]!;
    const next = steps[i + 1];
    let beaconId: string | undefined;
    if (
      step.tool === "get_event" ||
      step.tool === "rsvp_event" ||
      step.tool === "share_event"
    ) {
      const resolved = resolveBeaconId(step, lastIds);
      if ("error" in resolved) {
        replyParts.push(resolved.error);
        break;
      }
      beaconId = resolved.beacon_id;
    }

    const argsJson = stepToToolArgs(step, beaconId);
    const raw = await runTool(step.tool, argsJson, deps);

    let toolError: string | undefined;
    try {
      const parsed = JSON.parse(raw) as { error?: string };
      if (parsed.error) toolError = parsed.error;
    } catch {
      replyParts.push("Something went wrong. Try again in a moment.");
      break;
    }

    if (
      !toolError &&
      (step.tool === "list_nearby_events" || step.tool === "search_events")
    ) {
      const ids = extractListedBeaconIds(raw);
      if (ids) {
        store.setLastListedEvents(phoneE164, ids);
        lastIds = ids;
      }
    }

    // Skip redundant "saved ZIP" when the next step lists events.
    const skipSms =
      !toolError &&
      step.tool === "set_zip" &&
      (next?.tool === "list_nearby_events" || next?.tool === "search_events");

    if (!skipSms) {
      const sms = formatToolResultSms(step.tool, raw);
      if (sms) replyParts.push(sms);
    }

    if (toolError) break;
  }

  if (!replyParts.length) return DEFAULT_HELP;
  return replyParts.join("\n\n").trim();
}
