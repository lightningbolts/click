import UserNotifications

/// Modifies chat push notifications: decrypts E2EE payloads (same derivation as KMP `MessageCrypto` / Android FCM).
final class NotificationService: UNNotificationServiceExtension {

    private var contentHandler: ((UNNotificationContent) -> Void)?
    private var bestAttemptContent: UNMutableNotificationContent?

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        self.contentHandler = contentHandler
        guard let mutable = request.content.mutableCopy() as? UNMutableNotificationContent else {
            contentHandler(request.content)
            return
        }
        bestAttemptContent = mutable

        let userInfo = request.content.userInfo
        if (userInfo["type"] as? String) == "incoming_call" {
            contentHandler(mutable)
            return
        }

        let body = ChatPushNotificationBodyResolver.resolveBody(
            userInfo: userInfo,
            originalAlertBody: mutable.body
        )
        mutable.body = body

        contentHandler(mutable)
    }

    override func serviceExtensionTimeWillExpire() {
        if let contentHandler, let bestAttemptContent {
            contentHandler(bestAttemptContent)
        }
    }
}

// MARK: - Preview text (aligned with Android `ClickFirebaseMessagingService`)

private enum ChatPushNotificationBodyResolver {

    private static let decryptFailureFallback = "Open Click to view it"

    static func resolveBody(userInfo: [AnyHashable: Any], originalAlertBody: String) -> String {
        let previewFromServer: String? = {
            guard let raw = userInfo["preview_text"] as? String else { return nil }
            let t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }()

        let encrypted = (userInfo["encrypted_content"] as? String) ?? ""
        let connectionId = (userInfo["connection_id"] as? String) ?? ""
        let senderUserId = (userInfo["sender_user_id"] as? String) ?? ""
        let recipientUserId = (userInfo["recipient_user_id"] as? String) ?? ""

        let decryptedAttempt = decryptPreview(
            encryptedContent: encrypted,
            connectionId: connectionId,
            senderUserId: senderUserId,
            recipientUserId: recipientUserId
        )

        if decryptedAttempt != decryptFailureFallback {
            return decryptedAttempt
        }
        return previewFromServer ?? originalAlertBody
    }

    private static func decryptPreview(
        encryptedContent: String,
        connectionId: String,
        senderUserId: String,
        recipientUserId: String
    ) -> String {
        if encryptedContent.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return decryptFailureFallback
        }

        if !E2EChatMessageCrypto.isEncrypted(encryptedContent) {
            return String(encryptedContent.prefix(120))
        }

        if connectionId.isEmpty || senderUserId.isEmpty || recipientUserId.isEmpty {
            return decryptFailureFallback
        }

        let keys = E2EChatMessageCrypto.deriveKeys(
            connectionId: connectionId,
            userIds: [senderUserId, recipientUserId]
        )
        let decrypted = E2EChatMessageCrypto.decryptContent(encryptedContent, keys: keys)
        if E2EChatMessageCrypto.isEncrypted(decrypted) {
            return decryptFailureFallback
        }
        return String(decrypted.prefix(120))
    }
}
