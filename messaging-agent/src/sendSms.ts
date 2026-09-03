import twilio from "twilio";
import type { AppConfig } from "./config.js";

export type SmsSender = {
  send(toE164: string, body: string): Promise<{ sid: string }>;
};

export function createSmsSender(config: AppConfig): SmsSender {
  const client = twilio(config.TWILIO_ACCOUNT_SID, config.TWILIO_AUTH_TOKEN);

  return {
    async send(toE164: string, body: string) {
      const payload: {
        to: string;
        body: string;
        from?: string;
        messagingServiceSid?: string;
      } = {
        to: toE164,
        body: truncateSms(body),
      };
      if (config.TWILIO_MESSAGING_SERVICE_SID) {
        payload.messagingServiceSid = config.TWILIO_MESSAGING_SERVICE_SID;
      } else if (config.TWILIO_PHONE_NUMBER) {
        payload.from = config.TWILIO_PHONE_NUMBER;
      }
      const msg = await client.messages.create(payload);
      return { sid: msg.sid };
    },
  };
}

/** SMS practical limit; keep a small buffer under 1600 for concatenated SMS. */
export function truncateSms(body: string, max = 1500): string {
  const trimmed = body.trim();
  if (trimmed.length <= max) return trimmed;
  return `${trimmed.slice(0, max - 1)}…`;
}
