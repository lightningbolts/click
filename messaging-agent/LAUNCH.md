# Launch the Click messaging agent (SMS / RCS)

Step-by-step to run the agent locally, expose it, and wire Twilio so phones can text it.

## Prerequisites

- Node.js 20+
- Twilio account with an SMS (or RCS-capable Messaging Service) number
- OpenAI API key
- A tunnel so Twilio can reach your machine (ngrok, Cloudflare Tunnel, etc.)

## 1. Install dependencies

```bash
cd messaging-agent
npm install
```

## 2. Create `.env`

```bash
cp .env.example .env
```

Fill in at least:

| Variable | What to put |
|----------|-------------|
| `TWILIO_ACCOUNT_SID` | Twilio Console → Account info |
| `TWILIO_AUTH_TOKEN` | Twilio Console → Account info |
| `TWILIO_PHONE_NUMBER` **or** `TWILIO_MESSAGING_SERVICE_SID` | E.164 From number (`+1…`) **or** Messaging Service SID (prefer this for RCS) |
| `PUBLIC_BASE_URL` | Public HTTPS origin of this service (set after the tunnel is up; no trailing slash) |
| `OPENAI_API_KEY` | OpenAI key |
| `CLICK_WEB_BASE_URL` | Usually `https://joinclick.co` (default) |

Optional: `LLM_MODEL` (default `gpt-4o-mini`), `NEARBY_RADIUS_KM` (default `40`), `PORT` (default `8787`), `SQLITE_PATH`.

## 3. Start the agent

```bash
npm run dev
```

You should see: `click-messaging-agent listening on :8787`

Check health:

```bash
curl -s localhost:8787/health
# {"ok":true,"service":"click-messaging-agent"}
```

## 4. Expose the port (tunnel)

Example with ngrok:

```bash
ngrok http 8787
```

Copy the HTTPS forwarding URL (e.g. `https://abc123.ngrok-free.app`).

1. Put that origin in `.env` as `PUBLIC_BASE_URL` (no trailing slash).
2. Restart `npm run dev` so the process reloads env (needed for Twilio signature checks).

`PUBLIC_BASE_URL` must match exactly what Twilio calls (scheme + host).

## 5. Point Twilio at the webhook

In Twilio Console → your Phone Number **or** Messaging Service → inbound messages:

- **A message comes in** → `POST https://<PUBLIC_BASE_URL>/webhooks/twilio`
- Method: **HTTP POST**

Save.

## 6. Smoke-test without Twilio (optional)

With the agent running:

```bash
ALLOW_DEV_CHAT=1 npm run dev
```

In another terminal:

```bash
curl -s localhost:8787/dev/chat \
  -H 'content-type: application/json' \
  -d '{"from":"+15551234567","body":"my zip is 98021 — what events are near me?"}'
```

Replies should list events with `https://joinclick.co/e/{beacon_id}` links when events are mentioned.

For webhook debugging only (never production): `SKIP_TWILIO_SIGNATURE=1`.

## 7. Text from a real phone

1. Text the Twilio number from your phone.
2. Try: `events near me` → give a ZIP when asked (e.g. `98021` for the sample events around Bothell/WA).
3. Confirm each listed event includes a Click link like  
   `https://joinclick.co/e/212dd018-668b-4aea-8c97-e388fceeff16`
4. Optional: `RSVP 1` (agent will ask for a name), or `share 1 to +1…`.

## Event links

Every public event page is:

```text
https://joinclick.co/e/{beacon_id}
```

Example: [Test 2](https://joinclick.co/e/212dd018-668b-4aea-8c97-e388fceeff16)

The agent’s tools always return `share_url` in that form; the system prompt requires pasting it whenever an event is mentioned.

## Useful commands

```bash
npm run dev        # watch mode
npm start          # run once
npm test           # unit tests
npm run typecheck
```

## Troubleshooting

| Symptom | Check |
|---------|--------|
| Twilio signature failures | `PUBLIC_BASE_URL` matches tunnel URL; restart after changing `.env` |
| Empty nearby list | ZIP geocoded? Events within `NEARBY_RADIUS_KM` of that ZIP? |
| Dev chat 403 | `ALLOW_DEV_CHAT=1` |
| RSVP fails | Guest RSVP may be denied for invite-only events; need a display name first |
