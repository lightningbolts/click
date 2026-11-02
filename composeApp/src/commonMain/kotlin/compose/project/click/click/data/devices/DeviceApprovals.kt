package compose.project.click.click.data.devices // pragma: allowlist secret

import compose.project.click.click.data.api.DeviceApprovalDto // pragma: allowlist secret
import compose.project.click.click.data.api.DeviceApprovalsEnvelope // pragma: allowlist secret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

/** A device of this account waiting for (or decided on) permission to read chat history. */
data class DeviceApproval(
    val id: String,
    val status: Status,
    val deviceLabel: String?,
    val createdAt: Instant?,
    val expired: Boolean,
    val emailSent: Boolean,
) {
    enum class Status { PENDING, APPROVED, DENIED }

    companion object {
        internal fun from(dto: DeviceApprovalDto?): DeviceApproval? {
            dto ?: return null
            val id = dto.id.trim().takeIf { it.isNotEmpty() } ?: return null
            val status =
                when (dto.status) {
                    "pending" -> Status.PENDING
                    "approved" -> Status.APPROVED
                    "denied" -> Status.DENIED
                    else -> return null
                }
            return DeviceApproval(
                id = id,
                status = status,
                deviceLabel = dto.deviceLabel?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = dto.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
                expired = dto.expired,
                emailSent = dto.emailSent,
            )
        }
    }
}

data class DeviceApprovals(
    /** Other devices of this account this one can approve. */
    val incoming: List<DeviceApproval> = emptyList(),
    /** This device's own request, if it has asked. */
    val own: DeviceApproval? = null,
) {
    companion object {
        internal fun from(envelope: DeviceApprovalsEnvelope): DeviceApprovals =
            DeviceApprovals(
                incoming =
                    envelope.incoming
                        .mapNotNull(DeviceApproval::from)
                        .filter { it.status == DeviceApproval.Status.PENDING && !it.expired },
                own = DeviceApproval.from(envelope.own),
            )
    }
}

/** Copy for the approval sheet and the waiting device's banner (iOS `DeviceApprovalViews`). */
object DeviceApprovalCopy {
    /** Browsers name themselves ("Chrome on Mac"); the apps name the device ("iPhone"). */
    fun isBrowser(label: String?): Boolean = label != null && (label == "Web browser" || label.contains(" on "))

    /** "An iPhone", "A web browser", "Chrome on Mac", or "A new device" when an older build didn't say. */
    fun withArticle(label: String?): String {
        val first = label?.firstOrNull() ?: return "A new device"
        if (label.contains(" on ")) return label
        val name = if (label == "Web browser") "web browser" else label
        return (if (first.lowercaseChar() in "aeiou") "An " else "A ") + name
    }

    fun askingDetail(label: String?): String =
        "${withArticle(label)} signed in to your Click account. Approve it to let it read your past messages."

    fun approvedDetail(label: String?): String {
        if (label != null && label.contains(" on ")) {
            return "Your past messages will appear in ${label.replace(" on ", " on your ")} in a moment."
        }
        return "Your past messages will appear on your ${if (isBrowser(label)) "browser" else label ?: "device"} in a moment."
    }

    fun deniedDetail(label: String?): String =
        "That ${if (isBrowser(label)) "browser" else label ?: "device"} can't read your past messages. " +
            "If you didn't sign in, change your password in Settings."

    /** Subtitle on the waiting device. */
    fun waitingSubtitle(
        own: DeviceApproval,
        emailSent: Boolean,
        thisDevice: String,
    ): String =
        when {
            own.status == DeviceApproval.Status.DENIED -> "Your other device didn't approve this one."
            own.expired -> "The approval request expired."
            emailSent -> "Approve it in Click on a device you already use, or with the link we emailed you."
            else -> "Approve this ${thisDevice.removePrefix("Android ")} in Click on a device you already use."
        }

    enum class WaitingAction { ASK_AGAIN, EMAIL_LINK }

    fun waitingAction(
        own: DeviceApproval,
        emailSent: Boolean,
    ): WaitingAction? =
        when {
            own.status == DeviceApproval.Status.DENIED || own.expired -> WaitingAction.ASK_AGAIN
            !emailSent -> WaitingAction.EMAIL_LINK
            else -> null
        }
}

/** Latest approvals for the signed-in device; the shell shows the sheet and banner from this. */
object DeviceApprovalStore {
    private val _state = MutableStateFlow(DeviceApprovals())
    val state: StateFlow<DeviceApprovals> = _state.asStateFlow()

    /** Requests swiped away this session ("decide later"); they come back on next launch. */
    private val _deferred = MutableStateFlow<Set<String>>(emptySet())
    val deferred: StateFlow<Set<String>> = _deferred.asStateFlow()

    fun set(approvals: DeviceApprovals) {
        _state.value = approvals
    }

    fun defer(id: String) = _deferred.update { it + id }

    /** Drops a decided request at once rather than waiting for the next refresh. */
    fun decided(id: String) = _state.update { it.copy(incoming = it.incoming.filterNot { a -> a.id == id }) }

    fun reset() {
        _state.value = DeviceApprovals()
        _deferred.value = emptySet()
    }
}
