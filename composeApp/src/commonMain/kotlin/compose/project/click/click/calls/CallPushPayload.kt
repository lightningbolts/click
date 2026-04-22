package compose.project.click.click.calls

/**
 * Pure parser for the `incoming_call` push payload.
 *
 * Accepts the flat string-to-string map that both FCM (Android) and APNs
 * (iOS, when we forward the payload through Kotlin) hand us, and produces
 * a [CallInvite] if all required fields are present.
 *
 * Required keys: `call_id`, `connection_id`, `room_name`, `caller_id`,
 * `caller_name`, `callee_id`, `callee_name`.
 *
 * Optional keys: `video_enabled` (strict boolean; defaults to `false`),
 * `created_at` (long; defaults to `0`).
 *
 * Returns `null` when any required key is missing or blank so callers can
 * safely ignore malformed or stale payloads without throwing.
 */
fun parseIncomingCallPayload(data: Map<String, String>): CallInvite? {
    val callId = data["call_id"]?.takeIf { it.isNotBlank() } ?: return null
    val connectionId = data["connection_id"]?.takeIf { it.isNotBlank() } ?: return null
    val roomName = data["room_name"]?.takeIf { it.isNotBlank() } ?: return null
    val callerId = data["caller_id"]?.takeIf { it.isNotBlank() } ?: return null
    val callerName = data["caller_name"]?.takeIf { it.isNotBlank() } ?: return null
    val calleeId = data["callee_id"]?.takeIf { it.isNotBlank() } ?: return null
    val calleeName = data["callee_name"]?.takeIf { it.isNotBlank() } ?: return null

    return CallInvite(
        callId = callId,
        connectionId = connectionId,
        roomName = roomName,
        callerId = callerId,
        callerName = callerName,
        calleeId = calleeId,
        calleeName = calleeName,
        videoEnabled = data["video_enabled"]?.toBooleanStrictOrNull() ?: false,
        createdAt = data["created_at"]?.toLongOrNull() ?: 0L,
    )
}
