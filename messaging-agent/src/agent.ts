import OpenAI from "openai";
import type { AppConfig } from "./config.js";
import { ClickApi } from "./clickApi.js";
import { profileContextBlock, SYSTEM_PROMPT } from "./prompts.js";
import type { SmsSender } from "./sendSms.js";
import type { AgentStore } from "./store.js";
import { AGENT_TOOLS, runTool, type ToolDeps } from "./tools.js";

const MAX_TOOL_ROUNDS = 6;

export type AgentRuntime = {
  config: AppConfig;
  store: AgentStore;
  click: ClickApi;
  sms: SmsSender;
  openai: OpenAI;
};

export async function handleInboundSms(
  runtime: AgentRuntime,
  phoneE164: string,
  userText: string,
): Promise<string> {
  const { store, config, openai } = runtime;
  const profile = store.getProfile(phoneE164);
  store.appendTurn(phoneE164, "user", userText);

  const history = store.recentTurns(phoneE164, 12);
  const messages: OpenAI.Chat.ChatCompletionMessageParam[] = [
    { role: "system", content: SYSTEM_PROMPT },
    { role: "system", content: profileContextBlock(profile) },
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

  let finalText =
    "Sorry — I couldn't process that. Try again in a moment.";

  for (let round = 0; round < MAX_TOOL_ROUNDS; round++) {
    const completion = await openai.chat.completions.create({
      model: config.LLM_MODEL,
      messages,
      tools: AGENT_TOOLS,
      tool_choice: "auto",
      temperature: 0.4,
      max_tokens: 400,
    });

    const choice = completion.choices[0]?.message;
    if (!choice) break;

    const toolCalls = choice.tool_calls;
    if (toolCalls && toolCalls.length > 0) {
      messages.push({
        role: "assistant",
        content: choice.content,
        tool_calls: toolCalls,
      });
      for (const call of toolCalls) {
        if (call.type !== "function") continue;
        const result = await runTool(
          call.function.name,
          call.function.arguments ?? "{}",
          deps,
        );
        messages.push({
          role: "tool",
          tool_call_id: call.id,
          content: result,
        });
      }
      continue;
    }

    finalText =
      (choice.content ?? "").trim() ||
      "Got it. Text me about nearby events, RSVP, or sharing.";
    break;
  }

  store.appendTurn(phoneE164, "assistant", finalText);
  return finalText;
}
