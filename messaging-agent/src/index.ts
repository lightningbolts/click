import { serve } from "@hono/node-server";
import { Hono } from "hono";
import OpenAI from "openai";
import { pathToFileURL } from "node:url";
import { handleInboundSms, type AgentRuntime } from "./agent.js";
import { ClickApi } from "./clickApi.js";
import { loadConfig } from "./config.js";
import { createSmsSender } from "./sendSms.js";
import { AgentStore } from "./store.js";
import { twilioWebhookHandler } from "./twilioWebhook.js";

export function buildRuntime(): AgentRuntime {
  const config = loadConfig();
  return {
    config,
    store: new AgentStore(config.SQLITE_PATH),
    click: new ClickApi(config.clickWebBaseUrl),
    sms: createSmsSender(config),
    openai: new OpenAI({ apiKey: config.OPENAI_API_KEY }),
  };
}

export function createApp(runtime: AgentRuntime): Hono {
  const app = new Hono();

  app.get("/health", (c) =>
    c.json({ ok: true, service: "click-messaging-agent" }),
  );

  app.post("/webhooks/twilio", (c) => twilioWebhookHandler(c, runtime));

  /**
   * Local/dev helper: POST { "from": "+1…", "body": "…" } → { "reply": "…" }
   * Does not send via Twilio. Requires ALLOW_DEV_CHAT=1.
   */
  app.post("/dev/chat", async (c) => {
    if (process.env.ALLOW_DEV_CHAT !== "1") {
      return c.json({ error: "Set ALLOW_DEV_CHAT=1 to enable" }, 403);
    }
    const body = await c.req.json<{ from?: string; body?: string }>();
    if (!body.from || !body.body) {
      return c.json({ error: "from and body required" }, 400);
    }
    const reply = await handleInboundSms(runtime, body.from, body.body);
    return c.json({ reply });
  });

  return app;
}

function isDirectRun(): boolean {
  const entry = process.argv[1];
  if (!entry) return false;
  try {
    return import.meta.url === pathToFileURL(entry).href;
  } catch {
    return false;
  }
}

if (isDirectRun()) {
  const runtime = buildRuntime();
  const app = createApp(runtime);
  const port = runtime.config.PORT;
  console.log(`click-messaging-agent listening on :${port}`);
  serve({ fetch: app.fetch, port });
}
