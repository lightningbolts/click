import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";
import { SignJWT, importPKCS8 } from "https://esm.sh/jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY =
  Deno.env.get("SUPABASE_SERVICE_KEY") ??
  Deno.env.get("SUPABASE_KEY")!;
const FCM_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const APNS_URL = "https://api.push.apple.com/3/device";

interface PushRequestBody {
  recipient_user_id: string;
  title: string;
  body: string;
  data?: Record<string, unknown>;
}

interface PushTokenRow {
  id: string;
  user_id: string;
  token: string;
  platform: "android" | "ios";
  updated_at: number;
}

interface NotificationPreferenceRow {
  message_push_enabled: boolean;
  call_push_enabled: boolean;
}

interface FcmServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

type PushError = {
  token: string;
  platform: string;
  error: string;
};

type PushCategory = "chat_message" | "incoming_call";

function normalizePrivateKey(value: string): string {
  return value.replace(/\\n/g, "\n");
}

async function getFcmAccessToken(serviceAccountJson: string): Promise<{ accessToken: string; projectId: string }> {
  const serviceAccount = JSON.parse(serviceAccountJson) as FcmServiceAccount;
  const privateKey = await importPKCS8(normalizePrivateKey(serviceAccount.private_key), "RS256");
  const issuedAt = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: FCM_SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(serviceAccount.client_email)
    .setSubject(serviceAccount.client_email)
    .setAudience(FCM_TOKEN_URL)
    .setIssuedAt(issuedAt)
    .setExpirationTime(issuedAt + 3600)
    .sign(privateKey);

  const response = await fetch(FCM_TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!response.ok) {
    throw new Error(`Failed to obtain FCM access token: ${response.status} ${await response.text()}`);
  }

  const payload = await response.json();
  return {
    accessToken: payload.access_token as string,
    projectId: serviceAccount.project_id,
  };
}

async function getApnsJwt(): Promise<string> {
  const apnsKey = Deno.env.get("APNS_KEY");
  const apnsKeyId = Deno.env.get("APNS_KEY_ID");
  const apnsTeamId = Deno.env.get("APNS_TEAM_ID");

  if (!apnsKey || !apnsKeyId || !apnsTeamId) {
    throw new Error("Missing APNS_KEY, APNS_KEY_ID, or APNS_TEAM_ID secret");
  }

  const privateKey = await importPKCS8(normalizePrivateKey(apnsKey), "ES256");
  return new SignJWT({})
    .setProtectedHeader({ alg: "ES256", kid: apnsKeyId })
    .setIssuer(apnsTeamId)
    .setIssuedAt()
    .sign(privateKey);
}

async function sendAndroidPush(
  pushToken: PushTokenRow,
  requestBody: PushRequestBody,
  accessToken: string,
  projectId: string,
): Promise<void> {
  const category = getPushCategory(requestBody);
  const data = Object.fromEntries(
    Object.entries(requestBody.data ?? {}).map(([key, value]) => [key, String(value)])
  );

  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: pushToken.token,
          ...(category === "incoming_call"
            ? {}
            : {
                notification: {
                  title: requestBody.title,
                  body: requestBody.body,
                },
              }),
          data,
          android: {
            priority: "high",
            ...(category === "incoming_call"
              ? {}
              : {
                  notification: {
                    channel_id: "click_messages",
                    sound: "default",
                  },
                }),
          },
        },
      }),
    }
  );

  if (!response.ok) {
    throw new Error(`FCM send failed: ${response.status} ${await response.text()}`);
  }
}

async function sendIosPush(
  pushToken: PushTokenRow,
  requestBody: PushRequestBody,
  apnsJwt: string,
): Promise<void> {
  const category = getPushCategory(requestBody);
  const bundleId = Deno.env.get("APNS_BUNDLE_ID");
  if (!bundleId) {
    throw new Error("Missing APNS_BUNDLE_ID secret");
  }

  const response = await fetch(`${APNS_URL}/${pushToken.token}`, {
    method: "POST",
    headers: {
      authorization: `bearer ${apnsJwt}`,
      "apns-topic": bundleId,
      "apns-push-type": "alert",
      "apns-priority": category === "incoming_call" ? "10" : "10",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      aps: {
        alert: {
          title: requestBody.title,
          body: requestBody.body,
        },
        sound: "default",
        ...(category === "incoming_call"
          ? {
              category: "CLICK_INCOMING_CALL",
              "content-available": 1,
              "interruption-level": "time-sensitive",
            }
          : {}),
      },
      ...(requestBody.data ?? {}),
    }),
  });

  if (!response.ok) {
    throw new Error(`APNs send failed: ${response.status} ${await response.text()}`);
  }
}

function getPushCategory(requestBody: PushRequestBody): PushCategory {
  return requestBody.data?.type === "incoming_call" ? "incoming_call" : "chat_message";
}

async function validateIncomingCallRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<void> {
  if (getPushCategory(requestBody) !== "incoming_call") return;

  const authHeader = req.headers.get("authorization") ?? req.headers.get("Authorization");
  const token = authHeader?.replace(/^Bearer\s+/i, "");
  if (!token) {
    throw new Error("Authorization header is required for incoming call pushes");
  }

  const { data: authData, error: authError } = await supabase.auth.getUser(token);
  if (authError || !authData.user) {
    throw new Error(`Unable to authenticate incoming call push: ${authError?.message ?? "missing user"}`);
  }

  const data = requestBody.data ?? {};
  const connectionId = typeof data.connection_id === "string" ? data.connection_id : null;
  const callerId = typeof data.caller_id === "string" ? data.caller_id : null;
  const calleeId = typeof data.callee_id === "string" ? data.callee_id : null;

  if (!connectionId || !callerId || !calleeId) {
    throw new Error("incoming_call pushes require connection_id, caller_id, and callee_id");
  }

  if (authData.user.id !== callerId) {
    throw new Error("Authenticated user does not match caller_id");
  }

  if (requestBody.recipient_user_id !== calleeId) {
    throw new Error("recipient_user_id must match callee_id for incoming_call pushes");
  }

  const { data: connection, error: connectionError } = await supabase
    .from("connections")
    .select("id, user_ids")
    .eq("id", connectionId)
    .maybeSingle();

  if (connectionError || !connection) {
    throw new Error(`Unable to validate incoming call connection: ${connectionError?.message ?? "missing connection"}`);
  }

  const userIds = Array.isArray(connection.user_ids) ? connection.user_ids.map(String) : [];
  if (!userIds.includes(callerId) || !userIds.includes(calleeId)) {
    throw new Error("Connection does not contain caller/callee users");
  }
}

async function recipientAllowsPush(
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<boolean> {
  const { data, error } = await supabase
    .from("notification_preferences")
    .select("message_push_enabled, call_push_enabled")
    .eq("user_id", requestBody.recipient_user_id)
    .maybeSingle<NotificationPreferenceRow>();

  if (error || !data) {
    return true;
  }

  return getPushCategory(requestBody) === "incoming_call"
    ? data.call_push_enabled !== false
    : data.message_push_enabled !== false;
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ success: false, sent: 0, error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const requestBody = await req.json() as PushRequestBody;
    if (!requestBody.recipient_user_id || !requestBody.title || !requestBody.body) {
      return new Response(
        JSON.stringify({ success: false, sent: 0, error: "recipient_user_id, title, and body are required" }),
        { status: 400, headers: { "Content-Type": "application/json" } }
      );
    }

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    await validateIncomingCallRequest(req, supabase, requestBody);

    if (!(await recipientAllowsPush(supabase, requestBody))) {
      return new Response(JSON.stringify({ success: true, sent: 0, skipped: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    const { data: tokens, error } = await supabase
      .from("push_tokens")
      .select("id, user_id, token, platform, updated_at")
      .eq("user_id", requestBody.recipient_user_id);

    if (error) {
      throw new Error(`Failed to fetch recipient push tokens: ${error.message}`);
    }

    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ success: true, sent: 0 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    let fcmAccessToken: string | null = null;
    let fcmProjectId: string | null = null;
    let apnsJwt: string | null = null;
    const errors: PushError[] = [];
    let sent = 0;

    for (const token of tokens as PushTokenRow[]) {
      try {
        if (token.platform === "android") {
          if (!fcmAccessToken || !fcmProjectId) {
            const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
            if (!serviceAccountJson) {
              throw new Error("Missing FCM_SERVICE_ACCOUNT_JSON secret");
            }
            const fcmAuth = await getFcmAccessToken(serviceAccountJson);
            fcmAccessToken = fcmAuth.accessToken;
            fcmProjectId = fcmAuth.projectId;
          }

          await sendAndroidPush(token, requestBody, fcmAccessToken, fcmProjectId);
        } else {
          if (!apnsJwt) {
            apnsJwt = await getApnsJwt();
          }

          await sendIosPush(token, requestBody, apnsJwt);
        }

        sent += 1;
      } catch (tokenError) {
        console.error("Push send failed", token.platform, token.token, tokenError);
        errors.push({
          token: token.token,
          platform: token.platform,
          error: String(tokenError),
        });
      }
    }

    return new Response(JSON.stringify({
      success: errors.length === 0,
      sent,
      errors,
    }), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (error) {
    console.error("Fatal error in send-push-notification", error);
    return new Response(JSON.stringify({ success: false, sent: 0, error: String(error) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});