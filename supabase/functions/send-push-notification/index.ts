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

/** Per-token failures. Device tokens are credentials: never in responses or logs. */
type PushError = {
  platform: string;
  error: string;
};

type PushCategory = "chat_message" | "incoming_call" | "archive_warning" | "disposable_reveal";

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
  const isVoipToken = tokenType === "voip";
  const isIncomingCall = category === "incoming_call";
  // Apple VoIP pushes require push-type voip, topic <bundleId>.voip, and apns-expiration 0 (no delay).
  const isVoipIncomingCall = isVoipToken && isIncomingCall;

  const headers: Record<string, string> = {
    authorization: `bearer ${apnsJwt}`,
    "content-type": "application/json",
    "apns-priority": "10",
  };

  if (isVoipIncomingCall) {
    headers["apns-topic"] = `${bundleId}.voip`;
    headers["apns-push-type"] = "voip";
    headers["apns-expiration"] = "0";
  } else {
    headers["apns-topic"] = bundleId;
    headers["apns-push-type"] = "alert";
  }

  const body = isVoipIncomingCall
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
          // Lets the Notification Service Extension decrypt E2EE `encrypted_content` for the banner body.
          ...(category === "chat_message" ? { "mutable-content": 1 } : {}),
          ...(isIncomingCall
            ? {
                category: "CLICK_INCOMING_CALL",
                "interruption-level": "time-sensitive",
              }
            : {}),
        },
        ...(requestBody.data ?? {}),
      };

  const response = await fetch(`${APNS_URL}/${pushToken.token}`, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });

  const responseText = await response.text();
  if (isVoipIncomingCall) {
    console.log(`[APNs VoIP] Apple response: status=${response.status}, body=${responseText || "(empty)"}`);
  }

  if (!response.ok) {
    throw new Error(`APNs send failed: ${response.status} ${responseText}`);
  }
}

function getPushCategory(requestBody: PushRequestBody): PushCategory {
  const t = requestBody.data?.type;
  if (t === "incoming_call") return "incoming_call";
  if (t === "archive_warning") return "archive_warning";
  if (t === "disposable_reveal") return "disposable_reveal";
  return "chat_message";
}

function shouldSendToToken(
  requestBody: ResolvedPushRequestBody,
  pushToken: PushTokenRow,
): boolean {
  if (pushToken.platform !== "ios") {
    return true;
  }
  // incoming_call: send to every iOS token. VoIP wakes CallKit; standard APNs adds banner + sound if VoIP fails or is delayed.
  if (getPushCategory(requestBody) === "incoming_call") {
    return true;
  }
  return (pushToken.token_type ?? "standard") !== "voip";
}

/** APNs/FCM answers meaning the token will never work again (app deleted, token rotated). */
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
  token: PushTokenRow,
): Promise<void> {
  const { error } = await supabase.from("push_tokens").delete().eq("id", token.id);
  if (error) {
    console.error("Failed to prune push token", { pushTokenId: token.id, error: error.message });
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

function buildMessagePreview(content: string | null, messageType: string | null = null): string {
  switch (messageType) {
    case "image":
      return "📷 Photo";
    case "audio":
      return "🎤 Voice message";
    case "file":
      return "📎 File";
    case "beacon":
      return "📍 Shared an event";
  }
  const normalized = content?.trim();
  if (!normalized) {
    return "Open Click to view the latest message";
  }
  // Any E2EE wire format (v1 `e2e:`, group `e2e_grp:`, v2 `e2e2:`): never echo ciphertext.
  if (/^e2e[a-z0-9_]*:/i.test(normalized)) {
    return "Sent you a message";
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

  const data = requestBody.data ?? {};
  const connectionId = typeof data.connection_id === "string" ? data.connection_id : null;
  const callerId = typeof data.caller_id === "string" ? data.caller_id : null;
  const calleeId = typeof data.callee_id === "string" ? data.callee_id : null;

  if (!connectionId || !callerId || !calleeId) {
    throw new Error("incoming_call pushes require connection_id, caller_id, and callee_id");
  }

  // Independent reads, together.
  const [{ data: authData, error: authError }, { data: connection, error: connectionError }] = await Promise.all([
    supabase.auth.getUser(token),
    supabase.from("connections").select("id, user_ids").eq("id", connectionId).maybeSingle(),
  ]);

  if (authError || !authData.user) {
    throw new Error(`Unable to authenticate incoming call push: ${authError?.message ?? "missing user"}`);
  }

  if (authData.user.id !== callerId) {
    throw new Error("Authenticated user does not match caller_id");
  }

  if (requestBody.recipient_user_id !== calleeId) {
    throw new Error("recipient_user_id must match callee_id for incoming_call pushes");
  }

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
    const chatId = asNonEmptyString(data.chat_id);
    const providedConnectionId = asNonEmptyString(data.connection_id);

    // Sender name, ciphertext and connection are independent reads: issue them together.
    const [senderName, encryptedContent, connectionId] = await Promise.all([
      senderUserId
        ? supabase.from("users").select("name, email").eq("id", senderUserId).maybeSingle<UserProfileRow>()
            .then(({ data: profile }) => resolveUserDisplayName(profile))
        : Promise.resolve("Someone"),
      messageId
        ? supabase.from("messages").select("content").eq("id", messageId).maybeSingle()
            .then(({ data: msg }) => (msg?.content as string | undefined) ?? "")
        : Promise.resolve(""),
      !providedConnectionId && chatId
        ? supabase.from("chats").select("connection_id").eq("id", chatId).maybeSingle()
            .then(({ data: chat }) => (chat?.connection_id as string | undefined) ?? null)
        : Promise.resolve(providedConnectionId),
    ]);

    const clientPreview = asNonEmptyString(data.message_preview);
    const previewText = clientPreview ?? buildMessagePreview(encryptedContent, asNonEmptyString(data.message_type));
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

  const data = requestBody.data ?? {};
  const chatId = asNonEmptyString(data.chat_id);
  const senderUserId = asNonEmptyString(data.sender_user_id);
  const messageId = asNonEmptyString(data.message_id);
  const clientMessagePreview = asNonEmptyString(data.message_preview);

  // Trusted service callers (scheduled-message delivery) send on the sender's behalf: no
  // sender JWT, but the message, chat and membership checks below still apply.
  const isService = isServiceSecretRequest(req);
  const token = isService ? null : getBearerToken(req);
  if (!isService && !token) {
    throw new Error("Authorization header is required for direct chat message pushes");
  }

  if (!chatId || !senderUserId) {
    throw new Error("chat_message pushes require chat_id and sender_user_id");
  }

  // Every read below depends only on the request, so they run together (was ~6 sequential
  // round trips); each result is still validated in the original order.
  const [authResult, messageResult, chatResult, senderProfileResult] = await Promise.all([
    token ? supabase.auth.getUser(token) : Promise.resolve(null),
    messageId
      ? supabase.from("messages").select("id, chat_id, user_id, content, message_type").eq("id", messageId).maybeSingle()
      : Promise.resolve(null),
    supabase.from("chats").select("id, connection_id").eq("id", chatId).maybeSingle(),
    supabase.from("users").select("name, email").eq("id", senderUserId).maybeSingle<UserProfileRow>(),
  ]);

  if (authResult) {
    const { data: authData, error: authError } = authResult;
    if (authError || !authData.user) {
      throw new Error(`Unable to authenticate chat message push: ${authError?.message ?? "missing user"}`);
    }
    if (authData.user.id !== senderUserId) {
      throw new Error("Authenticated user does not match sender_user_id");
    }
  }

  let messageContent = providedBody;
  let messageType: string | null = null;
  if (messageResult) {
    const { data: message, error: messageError } = messageResult;
    if (messageError || !message) {
      throw new Error(`Unable to validate chat message push message: ${messageError?.message ?? "missing message"}`);
    }

    if (message.chat_id !== chatId || message.user_id !== senderUserId) {
      throw new Error("Message does not belong to the provided chat_id and sender_user_id");
    }

    messageContent = asNonEmptyString(message.content) ?? messageContent;
    messageType = asNonEmptyString(message.message_type);
  }

  const { data: chat, error: chatError } = chatResult;
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

  const { data: senderProfile, error: senderProfileError } = senderProfileResult;
  if (senderProfileError) {
    throw new Error(`Unable to resolve sender display name: ${senderProfileError.message}`);
  }

  const senderDisplayName = resolveUserDisplayName(senderProfile);
  const resolvedTitle = providedTitle ?? `New message from ${senderDisplayName}`;

  const rawContent = messageContent ?? "";
  const previewText = clientMessagePreview ?? buildMessagePreview(rawContent, messageType);
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
      ...(messageType ? { message_type: messageType } : {}),
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

function isServiceSecretRequest(req: Request): boolean {
  const bearer = getBearerToken(req)?.trim();
  const expected = serviceRoleOrCronSecret();
  return !!bearer && !!expected && bearer === expected;
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

/** Service pushes that carry their own recipient, title and body (no lookups needed). */
function requirePassThrough(requestBody: PushRequestBody, label: string): ResolvedPushRequestBody {
  const recipientUserId = asNonEmptyString(requestBody.recipient_user_id);
  const title = asNonEmptyString(requestBody.title);
  const body = asNonEmptyString(requestBody.body);
  if (!recipientUserId || !title || !body) {
    throw new Error(`${label} pushes require recipient_user_id, title, and body`);
  }
  return { recipient_user_id: recipientUserId, title, body, data: requestBody.data };
}

async function resolvePushRequest(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: PushRequestBody,
): Promise<ResolvedPushRequestBody> {
  if (isArchiveWarningServiceRequest(req, requestBody)) {
    return requirePassThrough(requestBody, "archive_warning");
  }

  if (isDisposableRevealServiceRequest(req, requestBody)) {
    return requirePassThrough(requestBody, "disposable_reveal");
  }

  if (getPushCategory(requestBody) === "incoming_call") {
    await validateIncomingCallRequest(req, supabase, requestBody);
    return requirePassThrough(requestBody, "incoming_call");
  }

  return resolveChatMessageRequest(req, supabase, requestBody);
}

/**
 * Alert pushes that also land in the recipient's in-app activity inbox (`activity_items`), whether
 * or not push reaches them. Messages, calls and device approvals have their own surfaces; pending
 * prior-connection requests are shown live from the inbox, not as history.
 */
const ACTIVITY_TYPES = new Set([
  "event_reminder",
  "event_recap",
  "event_teaser",
  "event_drop_recap",
  "shared_upcoming_event",
  "availability_match",
  "reconnect_nudge",
  "reconnect_lull",
  "reconnect_nearby",
  "anniversary",
  "memory_prompt",
  "group_revival",
  "wave",
  "hangout_confirm",
  "shared_drop_released",
  "disposable_reveal",
  "archive_warning",
  "prior_connection_accepted",
]);

/** Records an alert in the activity inbox. Service callers only (they own title and body). */
async function recordActivity(
  req: Request,
  supabase: ReturnType<typeof createClient>,
  requestBody: ResolvedPushRequestBody,
): Promise<void> {
  const data = requestBody.data ?? {};
  const type = asNonEmptyString(data.type);
  if (!type || !ACTIVITY_TYPES.has(type) || !isServiceSecretRequest(req)) return;
  const actorId = asNonEmptyString(data.peer_user_id) ?? asNonEmptyString(data.sender_user_id) ??
    asNonEmptyString(data.poster_id);
  const { error } = await supabase.rpc("record_activity", {
    p_user_id: requestBody.recipient_user_id,
    p_type: type,
    p_title: requestBody.title,
    p_body: requestBody.body,
    p_data: Object.fromEntries(Object.entries(data).map(([key, value]) => [key, String(value)])),
    p_actor_id: actorId && actorId !== requestBody.recipient_user_id ? actorId : null,
    p_group_key: null,
  });
  // Never fails the push: the inbox is best effort next to delivery.
  if (error) console.error("Failed to record activity", { type, error: error.message });
}

/** True while the recipient has muted this conversation (message pushes only). */
async function isConversationMuted(
  supabase: ReturnType<typeof createClient>,
  requestBody: ResolvedPushRequestBody,
): Promise<boolean> {
  const type = requestBody.data?.type;
  if (type !== "chat_message" && type !== "new_message" && type !== "hub_message") return false;
  const chatKey = asNonEmptyString(requestBody.data?.chat_id) ?? asNonEmptyString(requestBody.data?.hub_id);
  if (!chatKey) return false;
  const { data } = await supabase
    .from("chat_mutes")
    .select("muted_until")
    .eq("user_id", requestBody.recipient_user_id)
    .eq("chat_id", chatKey)
    .maybeSingle<{ muted_until: string | null }>();
  if (!data) return false;
  return data.muted_until === null || Date.parse(data.muted_until) > Date.now();
}

async function preferencesAllowPush(
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
  if (getPushCategory(requestBody) === "incoming_call") {
    return data.call_push_enabled !== false;
  }
  return data.message_push_enabled !== false;
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json", ...corsHeaders },
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 200, headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ success: false, sent: 0, error: "Method not allowed" }, 405);
  }

  try {
    const requestBody = await req.json() as PushRequestBody;
    // Routing metadata only: title/body/data can carry message previews.
    console.log("Received push request:", JSON.stringify({
      category: getPushCategory(requestBody),
      has_recipient: !!requestBody.recipient_user_id,
      has_title: !!requestBody.title,
      has_body: !!requestBody.body,
    }));

    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY, {
      auth: { autoRefreshToken: false, persistSession: false },
    });

    const resolvedRequestBody = await resolvePushRequest(req, supabase, requestBody);

    // Mute, preferences and tokens are independent reads of the recipient: fetch together. The
    // activity record rides along (before the push gates, so it lands even with push off).
    const [muted, allowed, { data: tokens, error }] = await Promise.all([
      isConversationMuted(supabase, resolvedRequestBody),
      preferencesAllowPush(supabase, resolvedRequestBody),
      supabase.from("push_tokens").select("*").eq("user_id", resolvedRequestBody.recipient_user_id),
      recordActivity(req, supabase, resolvedRequestBody).catch((activityError) =>
        console.error("Failed to record activity", String(activityError))
      ),
    ]);

    if (muted || !allowed) {
      return json({ success: true, sent: 0, skipped: true });
    }

    if (error) {
      throw new Error(`Failed to fetch recipient push tokens: ${error.message}`);
    }

    const pushTokens = ((tokens ?? []) as PushTokenRow[]).filter((token) => shouldSendToToken(resolvedRequestBody, token));
    if (pushTokens.length === 0) {
      return json({ success: true, sent: 0 });
    }

    // Provider credentials are created at most once per request, shared by every token.
    let fcmAuth: Promise<{ accessToken: string; projectId: string }> | null = null;
    let apnsJwt: Promise<string> | null = null;
    const ensureFcm = () => {
      if (!fcmAuth) {
        const serviceAccountJson = Deno.env.get("FCM_SERVICE_ACCOUNT_JSON");
        fcmAuth = serviceAccountJson
          ? getFcmAccessToken(serviceAccountJson)
          : Promise.reject(new Error("Missing FCM_SERVICE_ACCOUNT_JSON secret"));
      }
      return fcmAuth;
    };
    const ensureApns = () => (apnsJwt ??= getApnsJwt());

    const errors: PushError[] = [];
    let sent = 0;

    // Every device at once: one slow or dead token no longer delays the others.
    await Promise.all(pushTokens.map(async (token) => {
      try {
        if (token.platform === "android") {
          const { accessToken, projectId } = await ensureFcm();
          await sendAndroidPush(token, resolvedRequestBody, accessToken, projectId);
        } else {
          await sendIosPush(token, resolvedRequestBody, await ensureApns());
        }
        sent += 1;
      } catch (tokenError) {
        const dead = isDeadPushTokenError(tokenError);
        // Device tokens are credentials: identify the row by ID only.
        console.error("Push send failed", { platform: token.platform, pushTokenId: token.id, deadToken: dead });
        errors.push({ platform: token.platform, error: dead ? "dead_token" : "delivery_failed" });
        if (dead) await pruneDeadToken(supabase, token);
      }
    }));

    return json({ success: errors.length === 0, sent, errors });
  } catch (error) {
    console.error("Fatal error in send-push-notification", error instanceof Error ? error.message : String(error));
    return json({ success: false, sent: 0, error: String(error) }, 500);
  }
});
