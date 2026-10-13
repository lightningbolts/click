package compose.project.click.click.chat // pragma: allowlist secret

import compose.project.click.click.crypto.MessageCryptoV2 // pragma: allowlist secret
import compose.project.click.click.data.api.ClickWebCodedException // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalChallengeDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApproverDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceHistoryRequestDto // pragma: allowlist secret
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/*
 * Approving a new sign-in from a device already on the account (iOS `DeviceApprovalViews.swift`,
 * click-web `lib/server/deviceApproval.ts`). The server wraps a one-time 32-byte nonce to this
 * device's identity key in the same envelope as an epoch key; unwrapping it and sending it back
 * proves this device holds the key. Copy and label rules match iOS.
 */

/** Wrap metadata the server binds a challenge to (`APPROVAL_SENDER_DEVICE_ID`, `APPROVAL_EPOCH`). */
const val DEVICE_APPROVAL_SENDER_DEVICE_ID = "click-device-approval"
const val DEVICE_APPROVAL_EPOCH = 1
const val DEVICE_APPROVAL_NONCE_BYTES = 32

fun deviceApprovalChatId(requestId: String): String = "approval:$requestId"

/**
 * The metadata to unwrap [challenge] for [requestId] on [myDeviceId]. Always the expected values,
 * so a challenge for another request never unwraps; a server echo that disagrees is rejected.
 */
fun deviceApprovalWrapMetadata(
    requestId: String,
    myDeviceId: String,
    challenge: DeviceApprovalChallengeDto,
): MessageCryptoV2.EpochKeyWrapMetadata {
    val chatId = deviceApprovalChatId(requestId)
    require(challenge.chatId == null || challenge.chatId == chatId) { "approval challenge is for another request" }
    require(challenge.epoch == null || challenge.epoch == DEVICE_APPROVAL_EPOCH) { "unexpected approval epoch" }
    require(challenge.senderDeviceId == null || challenge.senderDeviceId == DEVICE_APPROVAL_SENDER_DEVICE_ID) {
        "unexpected approval sender"
    }
    return MessageCryptoV2.EpochKeyWrapMetadata(
        chatId = chatId,
        epoch = DEVICE_APPROVAL_EPOCH,
        senderDeviceId = DEVICE_APPROVAL_SENDER_DEVICE_ID,
        recipientDeviceId = myDeviceId,
    )
}

/** `proof`: standard (padded) base64 of the unwrapped nonce, which must be exactly 32 bytes. */
@OptIn(ExperimentalEncodingApi::class)
fun encodeDeviceApprovalProof(nonce: ByteArray): String {
    require(nonce.size == DEVICE_APPROVAL_NONCE_BYTES) { "approval nonce must be $DEVICE_APPROVAL_NONCE_BYTES bytes" }
    return Base64.encode(nonce)
}

/** Label rules (iOS #66): browsers name themselves ("Chrome on Mac"); the apps name the device. */
object DeviceLabels {
    enum class Icon { Phone, Tablet, Laptop }

    /** Exactly "Web browser", or anything containing " on ". */
    fun isBrowser(label: String?): Boolean = label != null && (label == "Web browser" || label.contains(" on "))

    /**
     * A label containing "ipad" (or an Android tablet) is a tablet; browsers are a laptop, except a
     * phone's browser (" on iphone" / " on android"); anything else is a phone (sheet 10 §A5).
     */
    fun icon(label: String?): Icon {
        val lower = label?.lowercase().orEmpty()
        if (lower.contains("ipad") || lower.contains("tablet") || lower.startsWith("galaxy tab")) return Icon.Tablet
        if (lower == "web browser" || lower.contains(" on ")) {
            return if (lower.endsWith(" on iphone") || lower.endsWith(" on android")) Icon.Phone else Icon.Laptop
        }
        return Icon.Phone
    }

    /** "An iPhone", "A web browser", "Chrome on Mac" as-is, or "A new device" when empty. */
    fun withArticle(label: String?): String {
        if (label.isNullOrEmpty()) return "A new device"
        if (label.contains(" on ")) return label
        val name = if (label == "Web browser") "web browser" else label
        return (if (label.first().lowercaseChar() in "aeiou") "An " else "A ") + name
    }

    /** "browser" for browsers, the label for apps, or "device" when unknown. */
    private fun noun(label: String?): String = if (isBrowser(label)) "browser" else label ?: "device"

    fun askingDetail(
        label: String?,
        signedInAgo: String? = null,
    ): String {
        val whenText = signedInAgo?.let { " $it" }.orEmpty()
        return "${withArticle(label)} signed in to your Click account$whenText. Approve it to let it read your past messages."
    }

    /** "Your past messages will appear in Chrome on your Mac in a moment." */
    fun approvedDetail(label: String?): String {
        if (isBrowser(label) && label != null && label.contains(" on ")) {
            return "Your past messages will appear in ${label.replace(" on ", " on your ")} in a moment."
        }
        return "Your past messages will appear on your ${noun(label)} in a moment."
    }

    fun deniedDetail(label: String?): String =
        "That ${noun(label)} can't read your past messages. If you didn't sign in, change your password in Settings."
}

/** What went wrong deciding a request, from the route's `{ error, code }` (and status). */
enum class DeviceApprovalError(
    val message: String,
    /** False when the request can no longer be decided from here: the sheet only offers Done. */
    val retryable: Boolean,
) {
    EmailProofRequired("Open the link from your email to approve this device.", false),
    DeviceProofInvalid("This device couldn't be verified. Try again.", true),
    ApproverNotAllowed("This device can't approve that sign-in.", false),
    NotFound("This sign-in is no longer waiting for approval.", false),
    AlreadyDecided("This sign-in was already approved or denied.", false),
    Expired("This request expired. The new device can ask again.", false),
    DeviceGone("That device is no longer signed in.", false),

    /** No screen lock to confirm with, or the confirmation failed (iOS: fail closed). */
    AuthenticationRequired("Device authentication is required to approve this request.", true),
    Unknown("Couldn't reach Click. Check your connection and try again.", true),
    ;

    companion object {
        fun from(
            status: Int?,
            code: String?,
        ): DeviceApprovalError =
            when (code) {
                "EMAIL_PROOF_REQUIRED" -> EmailProofRequired
                "DEVICE_PROOF_INVALID" -> DeviceProofInvalid
                "APPROVER_NOT_ALLOWED" -> ApproverNotAllowed
                "NOT_FOUND" -> NotFound
                "HISTORY_REQUEST_DECIDED" -> AlreadyDecided
                "HISTORY_REQUEST_EXPIRED" -> Expired
                "HISTORY_REQUEST_DEVICE_GONE" -> DeviceGone
                else ->
                    when (status) {
                        404 -> NotFound
                        409 -> AlreadyDecided
                        410 -> Expired
                        else -> Unknown
                    }
            }

        fun from(error: Throwable): DeviceApprovalError =
            (error as? ClickWebCodedException)?.let { from(it.statusCode, it.code) } ?: Unknown
    }
}

/** "2 min ago" style, for "active …" and "signed in …". */
internal fun deviceActivityAgo(
    iso: String?,
    nowMs: Long,
): String? {
    val thenMs = iso?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: return null
    val elapsed = (nowMs - thenMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hr ago" else "$hours hr ago"
        days == 1L -> "yesterday"
        else -> "$days days ago"
    }
}

/** "Approve on your Pixel 9 (active 2 min ago)", "Approve in Chrome on your Mac (…)". */
fun approverLine(
    approver: DeviceApproverDto,
    nowMs: Long,
): String {
    val label = approver.label?.trim()?.takeIf { it.isNotEmpty() }
    val where =
        when {
            label == null -> "Approve on your other device"
            label == "Web browser" -> "Approve in your web browser"
            label.contains(" on ") -> "Approve in ${label.replace(" on ", " on your ")}"
            else -> "Approve on your $label"
        }
    val active = deviceActivityAgo(approver.lastSeenAt, nowMs)?.let { " (active $it)" }.orEmpty()
    return where + active
}

/** The "Unlock older messages" banner on a device waiting for approval (iOS `DeviceHistoryBanner`). */
data class DeviceHistoryBannerState(
    val title: String,
    val subtitle: String,
    val approverLines: List<String>,
    val action: Action?,
    val denied: Boolean,
) {
    enum class Action(
        val title: String,
    ) {
        AskAgain("Ask again"),
        EmailLink("Email me a link instead"),
    }
}

/**
 * The banner for this device's own request, or null when there's none or it was approved.
 * With no device to approve on, it goes straight to the email copy.
 */
fun deviceHistoryBannerState(
    own: DeviceHistoryRequestDto?,
    approvers: List<DeviceApproverDto>,
    emailedHere: Boolean,
    thisDevice: String?,
    nowMs: Long,
): DeviceHistoryBannerState? {
    if (own == null || own.isApproved) return null
    val denied = own.isDenied
    val emailSent = own.emailSent || emailedHere
    val device = thisDevice?.trim()?.takeIf { it.isNotEmpty() } ?: "phone"
    val title = if (denied) "Older messages stay locked" else "Unlock older messages"
    val (subtitle, action) =
        when {
            denied -> "Your other device didn't approve this one." to DeviceHistoryBannerState.Action.AskAgain
            own.expired -> "The approval request expired." to DeviceHistoryBannerState.Action.AskAgain
            emailSent -> "Approve it in Click on a device you already use, or with the link we emailed you." to null
            approvers.isEmpty() ->
                "Approve this $device with a link we email you." to DeviceHistoryBannerState.Action.EmailLink
            else -> "Approve this $device in Click on a device you already use." to DeviceHistoryBannerState.Action.EmailLink
        }
    val lines = if (denied || own.expired) emptyList() else approvers.take(3).map { approverLine(it, nowMs) }
    return DeviceHistoryBannerState(title, subtitle, lines, action, denied)
}
