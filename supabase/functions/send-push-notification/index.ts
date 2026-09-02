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

const corsHeaders: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

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
  device_id?: string | null;
  updated_at: number;
}

interface NotificationPreferenceRow {
  message_push_enabled: boolean;
  event_reminder_push_enabled?: boolean;
  event_teaser_push_enabled?: boolean;
  reconnect_nudge_push_enabled?: boolean;
  availability_match_push_enabled?: boolean;
  hub_message_push_enabled?: boolean;
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
  platform: string;
  code: "delivery_failed";
};

type PushCategory =
  | "chat_message"
  | "archive_warning"
  | "disposable_reveal"
  | "event_reminder"
  | "event_teaser"
  | "reconnect_nudge"
  | "shared_upcoming_event"
  | "availability_match"
  | "hub_message";

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
  const category = getPushCategory(requestBody);
  const data: Record<string, string> = Object.fromEntries(
    Object.entries(requestBody.data ?? {}).map(([key, value]) => [key, String(value)])
  );

  if (category === "chat_message") {
    // Data-only: Android client decrypts when possible; preview_text is always safe to show if decrypt fails.
    delete data.title;
    delete data.body;
  } else if (category === "archive_warning") {
    if (requestBody.title && !data.title) data.title = requestBody.title;
    if (requestBody.body && !data.body) data.body = requestBody.body;
  } else {
    if (requestBody.title && !data.title) data.title = requestBody.title;
    if (requestBody.body && !data.body) data.body = requestBody.body;
  }

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
  if (tokenType === "voip") {
    return;
  }

  const headers: Record<string, string> = {
    authorization: `bearer ${apnsJwt}`,
    "content-type": "application/json",
    "apns-priority": "10",
    "apns-topic": bundleId,
    "apns-push-type": "alert",
  };

  const body = {
    aps: {
      alert: {
        title: requestBody.title,
        body: requestBody.body,
      },
      sound: "default",
      // Lets the Notification Service Extension decrypt E2EE `encrypted_content` for the banner body.
      ...(category === "chat_message" ? { "mutable-content": 1 } : {}),
    },
    ...(requestBody.data ?? {}),
  };

  const apnsRequestUrl = `${APNS_URL}/${pushToken.token}`;

  const response = await fetch(apnsRequestUrl, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  const responseText = await response.text();
  if (!response.ok) {
    throw new Error(`APNs send failed: ${response.status} ${responseText}`);
  }
}

function getPushCategory(requestBody: PushRequestBody): PushCategory {
  const t = requestBody.data?.type;
  if (t === "archive_warning") return "archive_warning";
  if (t === "disposable_reveal") return "disposable_reveal";
  if (t === "event_reminder") return "event_reminder";
  if (t === "event_teaser") return "event_teaser";
  if (t === "reconnect_nudge") return "reconnect_nudge";
  if (t === "shared_upcoming_event") return "shared_upcoming_event";
  if (t === "availability_match") return "availability_match";
  if (t === "hub_message") return "hub_message";
  return "chat_message";
}

function shouldSendToToken(
  _requestBody: ResolvedPushRequestBody,
  pushToken: PushTokenRow,
): boolean {
  if (pushToken.platform !== "ios") {
    return true;
  }

  const tokenType = pushToken.token_type ?? "standard";
  return tokenType != "voip";
}

function isDeadPushTokenError(error: unknown): boolean {
  const text = String(error).toLowerCase();
  return (
    text.includes(" 410") ||
    text.includes("unregistered") ||
    text.includes("baddevicetoken") ||
    text.includes("notfound") ||
    text.includes("not_found") ||
    text.includes("invalidregistration")
  );
}

async function pruneDeadToken(
  supabase: ReturnType<typeof createClient>,
  token: string,
): Promise<void> {
  const { error } = await supabase.from("push_tokens").delete().eq("token", token);
  if (error) {
    console.error("Failed to prune push token", error.message);
  }
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

/** FCM data payload limits — oversized ciphertext breaks client-side decrypt; omit and rely on preview_text. */
const MAX_ENCRYPTED_CONTENT_FCM_CHARS = 3500;

function encryptedContentForFcmPayload(raw: string): string {
  if (!raw || raw.length <= MAX_ENCRYPTED_CONTENT_FCM_CHARS) {
    return raw;
  }
  return "";
}

function getBearerToken(req: Request): string | null {
  const authHeader = req.headers.get("authorization") ?? req.headers.get("Authorization");
  return authHeader?.replace(/^Bearer\s+/i, "") ?? null;
}

function isServiceSecretRequest(req: Request): boolean {
  const bearer = getBearerToken(req)?.trim();
  const expected = serviceRoleOrCronSecret();
  return !!bearer && !!expected && bearer === expected;
}

async function resolveChatMessageRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<ResolvedPushRequestBody> {
  const providedRecipientUserId = asNonEmptyString(requestBody.recipient_user_id);
  const providedTitle = asNonEmptyString(requestBody.title);
  const providedBody = asNonEmptyString(requestBody.body);

  // Full-payload pushes (arbitrary recipient/title/body) are reserved for trusted
  // service callers (pg_cron maintenance, internal jobs). User-originated requests
  // must go through the validated chat-message path below, which authenticates the
  // sender and verifies connection membership.
  if (providedRecipientUserId && providedTitle && providedBody && isServiceSecretRequest(req)) {
    const data = requestBody.data ?? {};
    const senderUserId = asNonEmptyString(data.sender_user_id);
    const messageId = asNonEmptyString(data.message_id);
    const chatId = asNonEmptyString(data.chat_id);

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

    let connectionId = asNonEmptyString(data.connection_id);
    if (!connectionId && chatId) {
      const { data: chat } = await supabase
        .from("chats")
        .select("connection_id")
        .eq("id", chatId)
        .maybeSingle();
      connectionId = chat?.connection_id ?? null;
    }

    const clientPreview = asNonEmptyString(data.message_preview);
    const previewText = clientPreview ?? buildMessagePreview(encryptedContent);
    const encryptedForFcm = encryptedContentForFcmPayload(encryptedContent);

    return {
      recipient_user_id: providedRecipientUserId,
      title: providedTitle,
      body: clientPreview ?? providedBody,
      data: {
        ...data,
        sender_name: senderName,
        encrypted_content: encryptedForFcm,
        preview_text: previewText,
        recipient_user_id: providedRecipientUserId,
        ...(connectionId ? { connection_id: connectionId } : {}),
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
  const clientMessagePreview = asNonEmptyString(data.message_preview);

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

  const rawContent = messageContent ?? "";
  const previewText = clientMessagePreview ?? buildMessagePreview(rawContent);
  const encryptedForFcm = encryptedContentForFcmPayload(rawContent);

  return {
    recipient_user_id: recipientUserId,
    title: resolvedTitle,
    body: previewText,
    data: {
      ...(requestBody.data ?? {}),
      chat_id: chatId,
      connection_id: chat.connection_id,
      sender_name: senderDisplayName,
      encrypted_content: encryptedForFcm,
      preview_text: previewText,
      recipient_user_id: recipientUserId,
    },
  };
}

function serviceRoleOrCronSecret(): string | undefined {
  return (
    Deno.env.get("CRON_SECRET") ??
    Deno.env.get("ARCHIVE_WARNING_PUSH_SECRET") ??
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
    Deno.env.get("SUPABASE_SERVICE_KEY") ??
    Deno.env.get("SUPABASE_KEY")
  );
}

function isArchiveWarningServiceRequest(req: Request, requestBody: PushRequestBody): boolean {
  if (requestBody.data?.type !== "archive_warning") return false;
  const provided = req.headers.get("x-archive-warning-secret");
  const expected = serviceRoleOrCronSecret();
  return !!provided && !!expected && provided === expected;
}

function isDisposableRevealServiceRequest(req: Request, requestBody: PushRequestBody): boolean {
  if (requestBody.data?.type !== "disposable_reveal") return false;
  const authHeader = req.headers.get("authorization");
  const provided = authHeader?.replace(/^Bearer\s+/i, "").trim();
  const expected = serviceRoleOrCronSecret();
  return !!provided && !!expected && provided === expected;
}

async function resolvePushRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<ResolvedPushRequestBody> {
  if (requestBody.data?.type === "incoming_call") {
    throw new Error("incoming_call pushes are no longer supported");
  }

  if (isArchiveWarningServiceRequest(req, requestBody)) {
    const recipientUserId = asNonEmptyString(requestBody.recipient_user_id);
    const title = asNonEmptyString(requestBody.title);
    const body = asNonEmptyString(requestBody.body);
    if (!recipientUserId || !title || !body) {
      throw new Error("archive_warning pushes require recipient_user_id, title, and body");
    }
    return {
      recipient_user_id: recipientUserId,
      title,
      body,
      data: requestBody.data,
    };
  }

  if (isDisposableRevealServiceRequest(req, requestBody)) {
    const recipientUserId = asNonEmptyString(requestBody.recipient_user_id);
    const title = asNonEmptyString(requestBody.title);
    const body = asNonEmptyString(requestBody.body);
    if (!recipientUserId || !title || !body) {
      throw new Error("disposable_reveal pushes require recipient_user_id, title, and body");
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
    .select(
      "message_push_enabled, event_reminder_push_enabled, event_teaser_push_enabled, reconnect_nudge_push_enabled, availability_match_push_enabled, hub_message_push_enabled",
    )
    .eq("user_id", requestBody.recipient_user_id)
    .maybeSingle<NotificationPreferenceRow>();

  if (error || !data) {
    return true;
  }

  const cat = getPushCategory(requestBody);
  if (cat === "event_reminder") {
    return data.event_reminder_push_enabled !== false;
  }
  if (cat === "event_teaser") {
    return data.event_teaser_push_enabled !== false;
  }
  if (cat === "reconnect_nudge" || cat === "shared_upcoming_event") {
    return data.reconnect_nudge_push_enabled !== false;
  }
  if (cat === "availability_match") {
    return data.availability_match_push_enabled !== false;
  }
  if (cat === "hub_message") {
    return data.hub_message_push_enabled !== false;
  }
  if (cat === "disposable_reveal" || cat === "chat_message" || cat === "archive_warning") {
    return data.message_push_enabled !== false;
  }
  return data.message_push_enabled !== false;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return new Response(JSON.stringify({ success: false, sent: 0, error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }

  try {
    const requestBody = await req.json() as PushRequestBody;
    // Log routing metadata only — title/body/data may contain message previews.
    console.log(
      "Received push request:",
      JSON.stringify({
        category: getPushCategory(requestBody),
        has_recipient: !!requestBody.recipient_user_id,
        has_title: !!requestBody.title,
        has_body: !!requestBody.body,
      }),
    );

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const resolvedRequestBody = await resolvePushRequest(req, supabase, requestBody);

    if (!(await recipientAllowsPush(supabase, resolvedRequestBody))) {
      return new Response(JSON.stringify({ success: true, sent: 0, skipped: true }), {
        status: 200,
        headers: { "Content-Type": "application/json", ...corsHeaders },
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
        headers: { "Content-Type": "application/json", ...corsHeaders },
      });
    }

    let fcmAccessToken: string | null = null;
    let fcmProjectId: string | null = null;
    let apnsJwt: string | null = null;
    const errors: PushError[] = [];
    let sent = 0;

    const pushTokens = (tokens ?? []) as PushTokenRow[];

    const ensureFcm = async () => {
      if (!fcmAccessToken || !fcmProjectId) {
        const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
        if (!serviceAccountJson) {
          throw new Error("Missing FCM_SERVICE_ACCOUNT_JSON secret");
        }
        const fcmAuth = await getFcmAccessToken(serviceAccountJson);
        fcmAccessToken = fcmAuth.accessToken;
        fcmProjectId = fcmAuth.projectId;
      }
    };

    const ensureApns = async () => {
      if (!apnsJwt) {
        apnsJwt = await getApnsJwt();
      }
    };

    const deliverOne = async (token: PushTokenRow): Promise<boolean> => {
      if (!shouldSendToToken(resolvedRequestBody, token)) return false;
      try {
        if (token.platform === "android") {
          await ensureFcm();
          await sendAndroidPush(token, resolvedRequestBody, fcmAccessToken!, fcmProjectId!);
        } else {
          await ensureApns();
          await sendIosPush(token, resolvedRequestBody, apnsJwt!);
        }
        sent += 1;
        return true;
      } catch (tokenError) {
        // Device tokens are credentials. Keep the caller response and logs free of them.
        console.error("Push send failed", {
          platform: token.platform,
          pushTokenId: token.id,
          deadToken: isDeadPushTokenError(tokenError),
        });
        errors.push({
          platform: token.platform,
          code: "delivery_failed",
        });
        if (isDeadPushTokenError(tokenError)) {
          await pruneDeadToken(supabase, token.token);
        }
        return false;
      }
    };

    for (const token of pushTokens) {
      await deliverOne(token);
    }

    return new Response(JSON.stringify({
      success: errors.length === 0,
      sent,
      failed: errors.length,
    }), {
      status: 200,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  } catch (error) {
    // The caller only needs a stable error; provider/database details stay out of the response.
    console.error("Fatal error in send-push-notification", { type: error instanceof Error ? error.name : "unknown" });
    return new Response(JSON.stringify({ success: false, sent: 0, error: "PUSH_DELIVERY_UNAVAILABLE" }), {
      status: 500,
      headers: { "Content-Type": "application/json", ...corsHeaders },
    });
  }
});
