# External Setup

This repository now contains the app-side and backend-side code for push notifications and chat call scaffolding. The remaining work is environment setup outside the repo.

## 1. Android Firebase Setup

1. Create or reuse a Firebase project.
2. Add the Android app with package name `compose.project.click.click`.
3. Download `google-services.json` and place it at `click/composeApp/google-services.json`.
4. In Firebase Console, enable Cloud Messaging.
5. Build the Android app once to confirm the Google Services Gradle plugin resolves correctly.

## 2. iOS Push Setup

1. In Apple Developer, enable Push Notifications for the iOS app identifier used by this project.
2. Create or reuse an APNs Auth Key.
3. Keep the following values available for Supabase secrets:
   - `APNS_KEY`
   - `APNS_KEY_ID`
   - `APNS_TEAM_ID`
   - `APNS_BUNDLE_ID`
4. In Xcode, verify the app target has Push Notifications and Background Modes enabled if you want background remote notification handling later.

## 3. Supabase Database Setup

Run these SQL files in Supabase SQL Editor:

1. `click/database/add_push_tokens.sql`
2. `click/database/add_push_notification_trigger.sql`

Before the trigger migration will work, enable these extensions in Supabase Dashboard:

1. `pg_net`
2. `pg_cron` if you also want scheduled jobs like `expire-connections`

## 4. Supabase Edge Function Setup

1. Deploy the function in `click/supabase/functions/send-push-notification`.
2. Set the following secrets for the function runtime:
   - `SUPABASE_URL`
   - `SUPABASE_SERVICE_KEY`
   - `FCM_SERVICE_ACCOUNT_JSON`
   - `APNS_KEY`
   - `APNS_KEY_ID`
   - `APNS_TEAM_ID`
   - `APNS_BUNDLE_ID`
3. Confirm the function is reachable at `/functions/v1/send-push-notification`.
4. Test it directly with a POST body shaped like:

```json
{
  "recipient_user_id": "<user-uuid>",
  "title": "New message from Alice",
  "body": "Hey, are you free later?",
  "data": {
    "chat_id": "<chat-uuid>",
    "message_id": "<message-uuid>",
    "sender_user_id": "<sender-uuid>"
  }
}
```

## 5. Web Backend Setup For Calls

From `click-web`:

1. Run `npm install` to install `livekit-server-sdk`.
2. Set these environment variables for the Next.js app:
   - `LIVEKIT_API_KEY`
   - `LIVEKIT_API_SECRET`
   - `LIVEKIT_WS_URL`
   - `NEXT_PUBLIC_SUPABASE_URL`
   - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
3. Deploy or restart the web app after adding the variables.
4. Verify `POST /api/livekit/token` returns a token when called with a valid Supabase bearer token.

## 6. LiveKit Native SDK Setup

### Android

The current Android call manager in the repo is a scaffold around permissions and call state. To complete native media transport:

1. Add the LiveKit Android dependency to `composeApp/build.gradle.kts`.
2. Replace the scaffolded Android `CallManager` implementation with real `Room.connect(...)` logic.
3. Bind mute/camera UI controls to the LiveKit local participant.
4. Render remote and local video using the SDK’s renderer APIs.

### iOS

The Xcode project currently has no Swift package references. To complete native media transport:

1. Open `click/iosApp/iosApp.xcodeproj` in Xcode.
2. Add Swift Package dependency:
   - `https://github.com/livekit/client-sdk-swift`
3. Link it to the `iosApp` target.
4. Add a Swift bridge class that wraps the LiveKit room object and exposes connect/disconnect hooks usable from the shared module.
5. Replace the scaffolded iOS `CallManager` implementation with that bridge.

## 7. Manual Verification Checklist

1. Log in on Android and confirm a row appears in `push_tokens` after FCM token registration.
2. Log in on iOS, allow notifications, and confirm the APNs token is uploaded after user hydration.
3. Insert a message and confirm the DB trigger invokes the push function.
4. Call the push function manually and confirm delivery to both Android and iOS tokens.
5. Open a chat and press the phone or camera icon.
6. Confirm the app fetches a LiveKit token and shows the call overlay.
7. After native SDK setup, confirm actual media connect/disconnect works on both platforms.