# External Setup

This repo now contains the app-side wiring for push notifications, the Supabase delivery path, the LiveKit token endpoint, and a real Android LiveKit client. The remaining work is service configuration outside the repo.

The iOS call  path still requires one manual Xcode package step because the LiveKit Swift SDK is not vendored into this repository. Everything else is ready for configuration.

## 1. Supabase Database

Run these SQL files in the Supabase SQL editor, in this order:

1. `click/database/add_push_tokens.sql`
2. `click/database/add_push_notification_trigger.sql`
3. `click/database/fix_message_push_trigger_resilience.sql`

Before running the trigger SQL, enable the required extension:

1. In Supabase Dashboard, open `Database > Extensions`.
2. Enable `pg_net`.

You can also do that in SQL:

```sql
create extension if not exists pg_net;
```

Validation:

```sql
select * from pg_extension where extname = 'pg_net';
select * from pg_policies where tablename = 'push_tokens';
```

The resilience migration keeps message delivery working even if push delivery is misconfigured or temporarily unavailable.

## 2. Supabase Edge Function For Push Delivery

From the `click` folder, deploy the function:

```bash
supabase functions deploy send-push-notification
```

Set the required secrets:

```bash
supabase secrets set SUPABASE_URL="https://YOUR_PROJECT.supabase.co"
supabase secrets set SUPABASE_SERVICE_KEY="YOUR_SERVICE_ROLE_KEY"
supabase secrets set FCM_SERVICE_ACCOUNT_JSON='{"type":"service_account",...}'
supabase secrets set APNS_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
supabase secrets set APNS_KEY_ID="YOUR_APNS_KEY_ID"
supabase secrets set APNS_TEAM_ID="YOUR_APPLE_TEAM_ID"
supabase secrets set APNS_BUNDLE_ID="compose.project.click.click"
```

Notes:

1. The function now prefers `SUPABASE_SERVICE_KEY`.
2. It still accepts `SUPABASE_KEY` as a fallback so your current environment does not break.
3. `FCM_SERVICE_ACCOUNT_JSON` must be the full Firebase service-account JSON, serialized onto one line.

Local test:

```bash
supabase functions serve send-push-notification --no-verify-jwt
```

HTTP test:

```bash
curl -i http://127.0.0.1:54321/functions/v1/send-push-notification \
   -H "Content-Type: application/json" \
   -d '{
      "recipient_user_id": "USER_UUID",
      "title": "New message from Alice",
      "body": "Hey, are you free later?",
      "data": {
         "chat_id": "CHAT_UUID",
         "message_id": "MESSAGE_UUID",
         "sender_user_id": "SENDER_UUID"
      }
   }'
```

Expected result: JSON with `success`, `sent`, and optional `errors`.

## 3. Android Firebase And FCM

### Firebase console

1. Open Firebase Console.
2. Create or reuse a project.
3. Add an Android app with package name `compose.project.click.click`.
4. Download `google-services.json`.
5. Place it at `click/composeApp/google-services.json`.
6. In `Project settings > Cloud Messaging`, verify Firebase Cloud Messaging is enabled.

### Service account for Supabase push sender

1. In Firebase Console, open `Project settings > Service accounts`.
2. Click `Generate new private key`.
3. Use that JSON as the value for `FCM_SERVICE_ACCOUNT_JSON` in Supabase secrets.

### Android runtime validation

1. Install the Android app.
2. Sign in.
3. Accept notification permission.
4. Confirm a row appears in `push_tokens`:

```sql
select user_id, platform, token, updated_at
from push_tokens
order by created_at desc;
```

Expected result: one row with `platform = 'android'`.

## 4. Apple Push Notifications For iOS

### Apple Developer portal

1. Open Apple Developer.
2. Go to `Certificates, Identifiers & Profiles`.
3. Open your App ID for this app.
4. Enable `Push Notifications`.
5. Create an APNs Auth Key.
6. Download the `.p8` file once. Apple will not let you download it again.

Store these values for Supabase:

1. `APNS_KEY`: contents of the `.p8` file
2. `APNS_KEY_ID`: the key ID shown by Apple
3. `APNS_TEAM_ID`: your Apple team ID
4. `APNS_BUNDLE_ID`: the bundle ID used by the iOS target

### Xcode capabilities

Open `click/iosApp/iosApp.xcodeproj` and verify the `iosApp` target has:

1. `Push Notifications` capability enabled
2. `Background Modes` enabled with `Audio, AirPlay, and Picture in Picture`
3. `Background Modes` enabled with `Remote notifications` if you want background notification handling later

### iOS runtime validation

1. Build to a real iPhone. APNs device tokens do not fully validate on the simulator.
2. Sign in.
3. Accept notification permission.
4. Confirm a row appears in `push_tokens` with `platform = 'ios'`.

## 5. Triggering Pushes On New Messages

After the trigger SQL and function are deployed, sending a new message should invoke the Edge Function automatically.

Manual verification SQL:

```sql
insert into messages (id, chat_id, user_id, content, time_created)
values (
   gen_random_uuid(),
   'CHAT_UUID',
   'SENDER_USER_UUID',
   'Test push',
   (extract(epoch from now()) * 1000)::bigint
);
```

Then inspect:

1. Supabase function logs
2. The target device notification tray

## 6. LiveKit Cloud Or Self-Hosted

### Create the project

If using LiveKit Cloud:

1. Create a LiveKit Cloud project.
2. Copy these values:
    - WebSocket URL
    - API key
    - API secret

If self-hosting, you need the same three concepts:

1. `LIVEKIT_WS_URL`
2. `LIVEKIT_API_KEY`
3. `LIVEKIT_API_SECRET`

## 7. Next.js Token Endpoint

From `click-web`, install dependencies:

```bash
npm install
```

Create `click-web/.env.local` with:

```bash
LIVEKIT_API_KEY=your_livekit_api_key
LIVEKIT_API_SECRET=your_livekit_api_secret
LIVEKIT_WS_URL=wss://your-project.livekit.cloud
NEXT_PUBLIC_SUPABASE_URL=https://YOUR_PROJECT.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=YOUR_SUPABASE_ANON_KEY
```

Run the app:

```bash
cd click-web
npm run dev
```

Validation:

1. Authenticate in the web app or call the route with a valid Supabase bearer token.
2. Test the route:

```bash
curl -i http://localhost:3000/api/livekit/token \
   -X POST \
   -H "Authorization: Bearer SUPABASE_JWT" \
   -H "Content-Type: application/json" \
   -d '{
      "roomName": "chat-123",
      "participantName": "alice",
      "userId": "USER_UUID"
   }'
```

Expected result: JSON with `token` and `wsUrl`.

## 8. Android Voice And Video Calls

The Android app now includes a real LiveKit room client.

What you still need to do:

1. Sync Gradle so the new `livekit-android` dependency downloads.
2. Build and install the Android app.
3. Open a chat.
4. Tap the phone or camera icon.
5. Confirm:
    - the token request succeeds
    - the call overlay opens
    - microphone toggle works
    - camera toggle works
    - local preview appears when camera is on
    - remote video appears once the other participant joins with video

## 9. iOS Voice And Video Calls

The repo is prepared for iOS call permissions and call UI state, but native media transport still requires the LiveKit Swift SDK to be added in Xcode.

This is the remaining manual step:

1. Open `click/iosApp/iosApp.xcodeproj` in Xcode.
2. Select `File > Add Package Dependencies...`
3. Add:

```text
https://github.com/livekit/client-sdk-swift
```

4. Link the package to the `iosApp` target.
5. Build the app again.

Reason this is still manual:

1. The iOS project uses Xcode-managed Swift packages.
2. The package is not vendored in this repo.
3. Kotlin shared code cannot complete that Xcode package installation step on its own.

## 10. End-To-End Checklist

1. Run the push-token SQL.
2. Enable `pg_net`.
3. Deploy `send-push-notification`.
4. Set all Supabase secrets.
5. Add Firebase Android config and service-account JSON.
6. Add Apple APNs key and capabilities.
7. Set `click-web/.env.local` for LiveKit and Supabase.
8. Run `npm install` in `click-web`.
9. Sync Gradle in `click`.
10. Add the LiveKit Swift package in Xcode.
11. Verify Android push token upload.
12. Verify iOS push token upload.
13. Send a message and confirm push delivery.
14. Start an Android voice call.
15. Start an Android video call.
16. Validate iOS call flow after the Xcode package step.