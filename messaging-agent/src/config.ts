import { z } from "zod";

const envSchema = z.object({
  TWILIO_ACCOUNT_SID: z.string().min(1),
  TWILIO_AUTH_TOKEN: z.string().min(1),
  TWILIO_PHONE_NUMBER: z.string().min(1).optional(),
  TWILIO_MESSAGING_SERVICE_SID: z.string().min(1).optional(),
  PUBLIC_BASE_URL: z.string().url(),
  OPENAI_API_KEY: z.string().min(1),
  LLM_MODEL: z.string().default("gpt-4o-mini"),
  CLICK_WEB_BASE_URL: z.string().url().default("https://joinclick.co"),
  NEARBY_RADIUS_KM: z.coerce.number().positive().default(40),
  PORT: z.coerce.number().int().positive().default(8787),
  SQLITE_PATH: z.string().default("./data/agent.sqlite"),
  SKIP_TWILIO_SIGNATURE: z
    .enum(["0", "1", "true", "false"])
    .optional()
    .transform((v) => v === "1" || v === "true"),
});

export type AppConfig = z.infer<typeof envSchema> & {
  clickWebBaseUrl: string;
};

let cached: AppConfig | null = null;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  if (cached) return cached;
  const parsed = envSchema.safeParse(env);
  if (!parsed.success) {
    const detail = parsed.error.issues
      .map((i) => `${i.path.join(".")}: ${i.message}`)
      .join("; ");
    throw new Error(`Invalid messaging-agent env: ${detail}`);
  }
  const data = parsed.data;
  if (!data.TWILIO_PHONE_NUMBER && !data.TWILIO_MESSAGING_SERVICE_SID) {
    throw new Error(
      "Set TWILIO_PHONE_NUMBER or TWILIO_MESSAGING_SERVICE_SID",
    );
  }
  cached = {
    ...data,
    clickWebBaseUrl: data.CLICK_WEB_BASE_URL.replace(/\/$/, ""),
  };
  return cached;
}

/** Test helper — clear memoized config. */
export function resetConfigCache(): void {
  cached = null;
}
