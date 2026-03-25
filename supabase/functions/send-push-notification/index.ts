import { createClient } from "https://esm.sh/@supabase/supabase-js@2.83.0";
import { SignJWT, importPKCS8 } from "https://esm.sh/jose@5.9.6";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY =
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
  Deno.env.get("SUPABASE_SERVICE_KEY") ??
  Deno.env.get("SUPABASE_KEY")!;
const FCM_TOKEN_URL = "https://oauth2.googleapis.com/token";
const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const APNS_URL = "https://api.push.apple.com/3/device";

interface PushRequestBody {
  recipient_user_id?: string;
  title?: string;
  body?: string;
  data?: Record<string, unknown>;
}

interface ResolvedPushRequestBody {
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
  token_type?: "standard" | "voip";
  updated_at: number;
}

interface NotificationPreferenceRow {
  message_push_enabled: boolean;
  call_push_enabled: boolean;
}

interface UserProfileRow {
  name?: string | null;
  email?: string | null;
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

  const base64Key = apnsKey
    .replace(/-----BEGIN[^-]*-----/gi, "")
    .replace(/-----END[^-]*-----/gi, "")
    .replace(/\\n/g, "")
    .replace(/[^A-Za-z0-9+/=]/g, "");

  if (base64Key.length < 100) {
    throw new Error(
      `APNS_KEY appears truncated (${base64Key.length} base64 chars, expected ~200). ` +
      "Ensure the key is on a single line in .env and re-set the Supabase secret.",
    );
  }

  const formattedKey = `-----BEGIN PRIVATE KEY-----\n${base64Key}\n-----END PRIVATE KEY-----`;
  const privateKey = await importPKCS8(formattedKey, "ES256");
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
  const data: Record<string, string> = Object.fromEntries(
    Object.entries(requestBody.data ?? {}).map(([key, value]) => [key, String(value)])
  );
  if (requestBody.title && !data.title) data.title = requestBody.title;
  if (requestBody.body && !data.body) data.body = requestBody.body;

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
          data,
          android: {
            priority: "high",
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

  const tokenType = pushToken.token_type ?? "standard";
  const isVoipToken = tokenType === "voip";
  const isIncomingCall = category === "incoming_call";

  const headers: HeadersInit = {
    authorization: `bearer ${apnsJwt}`,
    "apns-topic": isVoipToken ? `${bundleId}.voip` : bundleId,
    "apns-push-type": isVoipToken && isIncomingCall ? "voip" : "alert",
    "apns-priority": "10",
    "content-type": "application/json",
  };

  const body = isVoipToken && isIncomingCall
    ? {
        aps: {
          "content-available": 1,
        },
        ...(requestBody.data ?? {}),
      }
    : {
        aps: {
          alert: {
            title: requestBody.title,
            body: requestBody.body,
          },
          sound: "default",
          ...(isIncomingCall
            ? {
                category: "CLICK_INCOMING_CALL",
                "content-available": 1,
                "interruption-level": "time-sensitive",
              }
            : { "mutable-content": 1 }),
        },
        ...(requestBody.data ?? {}),
      };

  const response = await fetch(`${APNS_URL}/${pushToken.token}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    throw new Error(`APNs send failed: ${response.status} ${await response.text()}`);
  }
}

function getPushCategory(requestBody: PushRequestBody): PushCategory {
  return requestBody.data?.type === "incoming_call" ? "incoming_call" : "chat_message";
}

function shouldSendToToken(
  requestBody: ResolvedPushRequestBody,
  pushToken: PushTokenRow,
  hasVoipIosToken: boolean,
): boolean {
  const category = getPushCategory(requestBody);

  if (pushToken.platform !== "ios") {
    return true;
  }

  const tokenType = pushToken.token_type ?? "standard";
  if (category === "chat_message") {
    return tokenType != "voip";
  }

  if (hasVoipIosToken) {
    return tokenType === "voip";
  }

  return tokenType != "voip";
}

function asNonEmptyString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : null;
}

function resolveUserDisplayName(profile: UserProfileRow | null | undefined): string {
  const candidates = [profile?.name, profile?.email?.split("@")[0]];
  for (const candidate of candidates) {
    if (typeof candidate === "string" && candidate.trim().length > 0) {
      return candidate.trim();
    }
  }
  return "Someone";
}

function buildMessagePreview(content: string | null): string {
  const normalized = content?.trim();
  if (!normalized) {
    return "Open Click to view the latest message";
  }
  if (normalized.startsWith("e2e:")) {
    return "Tap to view message";
  }
  return normalized.slice(0, 120);
}

function getBearerToken(req: Request): string | null {
  const authHeader = req.headers.get("authorization") ?? req.headers.get("Authorization");
  return authHeader?.replace(/^Bearer\s+/i, "") ?? null;
}

async function validateIncomingCallRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<void> {
  if (getPushCategory(requestBody) !== "incoming_call") return;

  const token = getBearerToken(req);
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

async function resolveChatMessageRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<ResolvedPushRequestBody> {
  const providedRecipientUserId = asNonEmptyString(requestBody.recipient_user_id);
  const providedTitle = asNonEmptyString(requestBody.title);
  const providedBody = asNonEmptyString(requestBody.body);

  if (providedRecipientUserId && providedTitle && providedBody) {
    const data = requestBody.data ?? {};
    const senderUserId = asNonEmptyString(data.sender_user_id);
    const messageId = asNonEmptyString(data.message_id);

    let senderName = "Someone";
    if (senderUserId) {
      const { data: senderProfile } = await supabase
        .from("users")
        .select("name, email")
        .eq("id", senderUserId)
        .maybeSingle<UserProfileRow>();
      senderName = resolveUserDisplayName(senderProfile);
    }

    let encryptedContent = "";
    if (messageId) {
      const { data: msg } = await supabase
        .from("messages")
        .select("content")
        .eq("id", messageId)
        .maybeSingle();
      encryptedContent = msg?.content ?? "";
    }

    return {
      recipient_user_id: providedRecipientUserId,
      title: providedTitle,
      body: providedBody,
      data: {
        ...data,
        sender_name: senderName,
        encrypted_content: encryptedContent,
        recipient_user_id: providedRecipientUserId,
      },
    };
  }

  const token = getBearerToken(req);
  if (!token) {
    throw new Error("Authorization header is required for direct chat message pushes");
  }

  const { data: authData, error: authError } = await supabase.auth.getUser(token);
  if (authError || !authData.user) {
    throw new Error(`Unable to authenticate chat message push: ${authError?.message ?? "missing user"}`);
  }

  const data = requestBody.data ?? {};
  const chatId = asNonEmptyString(data.chat_id);
  const senderUserId = asNonEmptyString(data.sender_user_id);
  const messageId = asNonEmptyString(data.message_id);

  if (!chatId || !senderUserId) {
    throw new Error("chat_message pushes require chat_id and sender_user_id");
  }

  if (authData.user.id !== senderUserId) {
    throw new Error("Authenticated user does not match sender_user_id");
  }

  let messageContent = providedBody;
  if (messageId) {
    const { data: message, error: messageError } = await supabase
      .from("messages")
      .select("id, chat_id, user_id, content")
      .eq("id", messageId)
      .maybeSingle();

    if (messageError || !message) {
      throw new Error(`Unable to validate chat message push message: ${messageError?.message ?? "missing message"}`);
    }

    if (message.chat_id !== chatId || message.user_id !== senderUserId) {
      throw new Error("Message does not belong to the provided chat_id and sender_user_id");
    }

    messageContent = asNonEmptyString(message.content) ?? messageContent;
  }

  const { data: chat, error: chatError } = await supabase
    .from("chats")
    .select("id, connection_id")
    .eq("id", chatId)
    .maybeSingle();

  if (chatError || !chat?.connection_id) {
    throw new Error(`Unable to validate chat message push chat: ${chatError?.message ?? "missing chat"}`);
  }

  const { data: connection, error: connectionError } = await supabase
    .from("connections")
    .select("id, user_ids")
    .eq("id", chat.connection_id)
    .maybeSingle();

  if (connectionError || !connection) {
    throw new Error(`Unable to validate chat message push connection: ${connectionError?.message ?? "missing connection"}`);
  }

  const connectionUserIds = Array.isArray(connection.user_ids) ? connection.user_ids.map(String) : [];
  if (!connectionUserIds.includes(senderUserId)) {
    throw new Error("Chat connection does not contain sender_user_id");
  }

  const recipientUserId = providedRecipientUserId ?? connectionUserIds.find((id: string) => id !== senderUserId) ?? null;
  if (!recipientUserId) {
    throw new Error("Unable to determine recipient_user_id for chat message push");
  }

  if (!connectionUserIds.includes(recipientUserId)) {
    throw new Error("recipient_user_id does not belong to the chat connection");
  }

  const { data: senderProfile, error: senderProfileError } = await supabase
    .from("users")
    .select("name, email")
    .eq("id", senderUserId)
    .maybeSingle<UserProfileRow>();

  if (senderProfileError) {
    throw new Error(`Unable to resolve sender display name: ${senderProfileError.message}`);
  }

  const senderDisplayName = resolveUserDisplayName(senderProfile);

  let resolvedTitle = providedTitle;
  if (!resolvedTitle) {
    resolvedTitle = `New message from ${senderDisplayName}`;
  }

  return {
    recipient_user_id: recipientUserId,
    title: resolvedTitle,
    body: buildMessagePreview(messageContent),
    data: {
      ...(requestBody.data ?? {}),
      connection_id: chat.connection_id,
      sender_name: senderDisplayName,
      encrypted_content: messageContent ?? "",
      recipient_user_id: recipientUserId,
    },
  };
}

async function resolvePushRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<ResolvedPushRequestBody> {
  if (getPushCategory(requestBody) === "incoming_call") {
    await validateIncomingCallRequest(req, supabase, requestBody);

    const recipientUserId = asNonEmptyString(requestBody.recipient_user_id);
    const title = asNonEmptyString(requestBody.title);
    const body = asNonEmptyString(requestBody.body);
    if (!recipientUserId || !title || !body) {
      throw new Error("incoming_call pushes require recipient_user_id, title, and body");
    }

    return {
      recipient_user_id: recipientUserId,
      title,
      body,
      data: requestBody.data,
    };
  }

  return resolveChatMessageRequest(req, supabase, requestBody);
}

async function recipientAllowsPush(
  supabase: ReturnType<typeof createClient>,
  requestBody: ResolvedPushRequestBody,
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

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const resolvedRequestBody = await resolvePushRequest(req, supabase, requestBody);

    if (!(await recipientAllowsPush(supabase, resolvedRequestBody))) {
      return new Response(JSON.stringify({ success: true, sent: 0, skipped: true }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      });
    }

    const { data: tokens, error } = await supabase
      .from("push_tokens")
      .select("*")
      .eq("user_id", resolvedRequestBody.recipient_user_id);

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

    const pushTokens = (tokens ?? []) as PushTokenRow[];
    const hasVoipIosToken = getPushCategory(resolvedRequestBody) === "incoming_call" &&
      pushTokens.some((token) => token.platform === "ios" && token.token_type === "voip");

    for (const token of pushTokens) {
      try {
        if (!shouldSendToToken(resolvedRequestBody, token, hasVoipIosToken)) {
          continue;
        }

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

          await sendAndroidPush(token, resolvedRequestBody, fcmAccessToken, fcmProjectId);
        } else {
          if (!apnsJwt) {
            apnsJwt = await getApnsJwt();
          }

          await sendIosPush(token, resolvedRequestBody, apnsJwt);
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