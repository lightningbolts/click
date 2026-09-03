# Click messaging agent (Twilio SMS)

SMS agent for Click public events: nearby discovery by ZIP, search, guest RSVP, and share-via-text. Identity is the sender’s phone number — no Click signup.

## Features

- **Nearby events** — user texts a US ZIP once; remembered until they update it
- **Search** — filter public upcoming events by text
- **RSVP** — `POST /api/beacons/{id}/rsvp/guest` with name + phone
- **Share** — outbound SMS to another number with `https://joinclick.co/e/{id}`

Uses **gpt-4o-mini** by default (`LLM_MODEL` overrideable). Talks to click-web public APIs only.

## Setup

```bash
cd messaging-agent
cp .env.example .env
# fill TWILIO_*, OPENAI_API_KEY, PUBLIC_BASE_URL
npm install
npm run dev
```

Expose locally (example):

```bash
ngrok http 8787
# set PUBLIC_BASE_URL=https://<id>.ngrok-free.app
```

In Twilio Console → Phone Number (or Messaging Service) → webhook:

- **A message comes in** → `POST https://<PUBLIC_BASE_URL>/webhooks/twilio`
- Method: HTTP POST

`PUBLIC_BASE_URL` must match the URL Twilio calls (scheme + host + no trailing slash), or signature checks fail.

### Dev chat (no Twilio)

```bash
ALLOW_DEV_CHAT=1 npm run dev
curl -s localhost:8787/dev/chat \
  -H 'content-type: application/json' \
  -d '{"from":"+15551234567","body":"events near me"}'
```

Optional: `SKIP_TWILIO_SIGNATURE=1` for local webhook tests only — never in production.

## Env

| Variable | Required | Notes |
|----------|----------|--------|
| `TWILIO_ACCOUNT_SID` | yes | |
| `TWILIO_AUTH_TOKEN` | yes | |
| `TWILIO_PHONE_NUMBER` | one of | E.164 From number |
| `TWILIO_MESSAGING_SERVICE_SID` | one of | Prefer for RCS-capable Messaging Service |
| `PUBLIC_BASE_URL` | yes | Public origin of this service |
| `OPENAI_API_KEY` | yes | |
| `LLM_MODEL` | no | default `gpt-4o-mini` |
| `CLICK_WEB_BASE_URL` | no | default `https://joinclick.co` |
| `NEARBY_RADIUS_KM` | no | default `40` |
| `PORT` | no | default `8787` |
| `SQLITE_PATH` | no | default `./data/agent.sqlite` |

## Scripts

```bash
npm run dev        # watch server
npm start          # run once
npm test           # vitest
npm run typecheck
```

## Architecture

```
Twilio → POST /webhooks/twilio → LLM + tools → click-web public APIs
                              ↘ SQLite phone profile (ZIP, name, chat turns)
                              ↘ Twilio REST reply / share SMS
```

Outbound replies are sent asynchronously via the Twilio REST API so LLM latency does not hit Twilio’s webhook timeout.

## Notes

- Guest RSVPs are not linked to Click accounts (same as the web guest form).
- Invite-only events still use email guest lists on the backend; phone guest RSVPs may be denied for those.
- ZIP geocoding uses OpenStreetMap Nominatim (cached on the profile after first success).
