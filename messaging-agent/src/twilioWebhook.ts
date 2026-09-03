import { createHmac, timingSafeEqual } from "node:crypto";
import type { Context } from "hono";
import type { AppConfig } from "./config.js";
import type { AgentRuntime } from "./agent.js";
import { handleInboundSms } from "./agent.js";
import { truncateSms } from "./sendSms.js";

export type TwilioForm = {
  From?: string;
  Body?: string;
  MessageSid?: string;
};

/**
 * Validate Twilio request signature.
 * @see https://www.twilio.com/docs/usage/security#validating-requests
 */
export function validateTwilioSignature(
  authToken: string,
  signature: string | undefined,
  url: string,
  params: Record<string, string>,
): boolean {
  if (!signature) return false;
  const data =
    url +
    Object.keys(params)
      .sort()
      .map((k) => k + params[k])
      .join("");
  const expected = createHmac("sha1", authToken).update(Buffer.from(data, "utf-8")).digest("base64");
  try {
    const a = Buffer.from(expected);
    const b = Buffer.from(signature);
    return a.length === b.length && timingSafeEqual(a, b);
  } catch {
    return false;
  }
}

export function webhookUrl(config: AppConfig, path = "/webhooks/twilio"): string {
  return `${config.PUBLIC_BASE_URL.replace(/\/$/, "")}${path}`;
}

export async function twilioWebhookHandler(
  c: Context,
  runtime: AgentRuntime,
): Promise<Response> {
  const config = runtime.config;
  const form = await c.req.parseBody();
  const params: Record<string, string> = {};
  for (const [k, v] of Object.entries(form)) {
    if (typeof v === "string") params[k] = v;
  }

  const signature = c.req.header("X-Twilio-Signature") ?? undefined;
  const url = webhookUrl(config);
  if (
    !config.SKIP_TWILIO_SIGNATURE &&
    !validateTwilioSignature(config.TWILIO_AUTH_TOKEN, signature, url, params)
  ) {
    return c.text("Invalid signature", 403);
  }

  const from = params.From?.trim();
  const body = (params.Body ?? "").trim();
  if (!from) {
    return c.text("Missing From", 400);
  }

  // Acknowledge immediately; reply via REST so LLM latency doesn't time out Twilio.
  void processInbound(runtime, from, body || "hi").catch((err) => {
    console.error("inbound SMS handling failed:", err);
  });

  // Empty TwiML — outbound reply is sent asynchronously via REST.
  return c.text("<Response></Response>", 200, {
    "Content-Type": "text/xml",
  });
}

async function processInbound(
  runtime: AgentRuntime,
  from: string,
  body: string,
): Promise<void> {
  try {
    const reply = await handleInboundSms(runtime, from, body);
    await runtime.sms.send(from, truncateSms(reply));
  } catch (err) {
    console.error("agent error:", err);
    try {
      await runtime.sms.send(
        from,
        "Sorry — something went wrong on my side. Try again in a bit.",
      );
    } catch (sendErr) {
      console.error("fallback SMS failed:", sendErr);
    }
  }
}
